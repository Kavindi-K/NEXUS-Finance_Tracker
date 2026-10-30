package com.nexus.financetracker

import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit tests for Branch 08 feature/income-freelance:
 * "Purpose: Differentiate unpaid invoices from received freelance income.
 *  Code / check: For pending: store status=pending, optional expectedDate/note, no dateReceived.
 *                On confirmation: set confirmed and dateReceived; remove expectedDate, keep same income ID.
 *  Done when: Pending shown separately, excluded from earned totals; confirmation adds it exactly once."
 */
class IncomeFreelanceTest {

    private val testUserId = "freelancer-user-789"

    /**
     * Fake repository tracking documents in memory to verify exact count and totals.
     */
    private class FakeIncomeRepository : IncomeRepository {
        val documents = mutableMapOf<String, Income>()
        private val _incomesFlow = MutableStateFlow<List<Income>>(emptyList())

        private fun updateFlow() {
            _incomesFlow.value = documents.values.toList()
        }

        override fun getIncomes(userId: String): Flow<List<Income>> = _incomesFlow.asStateFlow()

        override fun getConfirmedIncomes(userId: String): Flow<List<Income>> =
            _incomesFlow.map { list -> list.filter { it.status == IncomeStatus.CONFIRMED } }

        override fun getTotalConfirmedIncomeLKR(userId: String): Flow<Double> =
            getConfirmedIncomes(userId).map { list -> list.sumOf { it.amountLKR } }

        override fun getIncomesByDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<Income>> =
            _incomesFlow.map { list -> list.filter { (it.dateReceived ?: it.createdAt) in startDate..endDate } }

        override fun getPendingIncomes(userId: String): Flow<List<Income>> =
            _incomesFlow.map { list -> list.filter { it.status == IncomeStatus.PENDING } }

        override suspend fun getIncomeById(incomeId: String): Income? = documents[incomeId]

        override suspend fun addIncome(income: Income): String {
            val id = if (income.id.isNotBlank()) income.id else "doc-${documents.size + 1}"
            val saved = income.copy(id = id)
            documents[id] = saved
            updateFlow()
            return id
        }

        override suspend fun updateIncome(income: Income) {
            require(income.id.isNotBlank()) { "Income ID cannot be blank for update" }
            documents[income.id] = income
            updateFlow()
        }

        override suspend fun deleteIncome(incomeId: String) {
            documents.remove(incomeId)
            updateFlow()
        }

        override suspend fun clearLocalCache(userId: String) {
            documents.clear()
            updateFlow()
        }
    }

    @Test
    fun pendingFreelanceIncome_storesCorrectAttributes_withoutDateReceived() {
        val expectedDueDate = Date(System.currentTimeMillis() + 86400000L * 7) // 7 days from now
        val pendingIncome = Income(
            id = "freelance-inv-001",
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 85000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 85000.0,
            dateReceived = null,
            expectedDate = expectedDueDate,
            note = "Invoice #1042 - Mobile App UI Redesign Milestone 1"
        )

        assertNull("Pending income should pass validation", pendingIncome.validate())
        assertEquals(IncomeStatus.PENDING, pendingIncome.status)
        assertNull(pendingIncome.dateReceived)
        assertNotNull(pendingIncome.expectedDate)
        assertEquals("Invoice #1042 - Mobile App UI Redesign Milestone 1", pendingIncome.note)

        // Verify Firestore serialization
        val firestoreMap = pendingIncome.toFirestoreMap()
        assertFalse("Pending income must NOT contain dateReceived in Firestore", firestoreMap.containsKey("dateReceived"))
        assertTrue("Pending income must contain expectedDate in Firestore", firestoreMap.containsKey("expectedDate"))
        assertEquals(expectedDueDate, firestoreMap["expectedDate"])
        assertEquals("freelance", firestoreMap["source"])
        assertEquals("pending", firestoreMap["status"])
    }

    @Test
    fun pendingFreelanceIncome_excludedFromConfirmedEarnedTotals() = runBlocking {
        val repo = FakeIncomeRepository()

        // 1 Confirmed salary income: 130,000 LKR
        val salary = Income(
            id = "salary-001",
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 130000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 130000.0,
            dateReceived = Date()
        )
        repo.addIncome(salary)

        // 1 Pending freelance invoice: 75,000 LKR
        val pendingFreelance = Income(
            id = "freelance-001",
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 75000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 75000.0,
            dateReceived = null,
            expectedDate = Date(System.currentTimeMillis() + 86400000L * 14),
            note = "Client: TechCorp - Backend API Sprint 2"
        )
        repo.addIncome(pendingFreelance)

        // Earned total must ONLY include the confirmed salary (130,000), excluding the 75,000 pending invoice
        val confirmedTotal = repo.documents.values
            .filter { it.status == IncomeStatus.CONFIRMED }
            .sumOf { it.amountLKR }
        assertEquals(130000.0, confirmedTotal, 0.01)

        val pendingTotal = repo.documents.values
            .filter { it.status == IncomeStatus.PENDING }
            .sumOf { it.amountLKR }
        assertEquals(75000.0, pendingTotal, 0.01)

        assertEquals("Total document count should be 2", 2, repo.documents.size)
    }

    @Test
    fun confirmFreelanceIncome_setsConfirmedAndDateReceived_removesExpectedDate_keepsSameId() = runBlocking {
        val repo = FakeIncomeRepository()
        val originalCreatedAt = Date(1704067200000L) // Fixed timestamp

        val pendingInvoice = Income(
            id = "invoice-nexus-99",
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 95000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 95000.0,
            dateReceived = null,
            expectedDate = Date(1705276800000L),
            note = "Fintech Client - Security Audit Milestone",
            createdAt = originalCreatedAt
        )
        repo.addIncome(pendingInvoice)

        assertEquals(1, repo.documents.size)
        val initialEarnedTotal = repo.documents.values
            .filter { it.status == IncomeStatus.CONFIRMED }
            .sumOf { it.amountLKR }
        assertEquals(0.0, initialEarnedTotal, 0.01)

        // User confirms payment: set status=confirmed, set dateReceived, remove expectedDate, keep same ID
        val paymentReceivedDate = Date()
        val confirmedIncome = pendingInvoice.copy(
            status = IncomeStatus.CONFIRMED,
            dateReceived = paymentReceivedDate,
            expectedDate = null,
            updatedAt = Date()
        )

        assertNull("Confirmed income must pass validation", confirmedIncome.validate())
        repo.updateIncome(confirmedIncome)

        // Verification 1: Same ID is kept, NO extra document created
        assertEquals("Total documents must remain 1 (no duplicates)", 1, repo.documents.size)
        assertTrue("Document ID must be preserved", repo.documents.containsKey("invoice-nexus-99"))

        val stored = repo.documents["invoice-nexus-99"]!!
        assertEquals("invoice-nexus-99", stored.id)
        assertEquals(IncomeStatus.CONFIRMED, stored.status)
        assertEquals(paymentReceivedDate, stored.dateReceived)
        assertNull("expectedDate must be removed on confirmation", stored.expectedDate)
        assertEquals(originalCreatedAt, stored.createdAt)
        assertEquals(95000.0, stored.amountLKR, 0.01)

        // Verification 2: Added to earned totals EXACTLY ONCE
        val updatedEarnedTotal = repo.documents.values
            .filter { it.status == IncomeStatus.CONFIRMED }
            .sumOf { it.amountLKR }
        assertEquals(95000.0, updatedEarnedTotal, 0.01)

        val updatedPendingTotal = repo.documents.values
            .filter { it.status == IncomeStatus.PENDING }
            .sumOf { it.amountLKR }
        assertEquals(0.0, updatedPendingTotal, 0.01)

        // Verification 3: Firestore schema after confirmation strictly includes dateReceived and omits expectedDate
        val confirmedMap = stored.toFirestoreMap()
        assertTrue("Firestore map must contain dateReceived", confirmedMap.containsKey("dateReceived"))
        assertFalse("Firestore map must NOT contain expectedDate", confirmedMap.containsKey("expectedDate"))
        assertEquals("confirmed", confirmedMap["status"])
    }

    @Test
    fun freelanceValidationRules_enforceStrictConsistency() {
        // Pending cannot have dateReceived
        val invalidPending = Income(
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 50000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 50000.0,
            dateReceived = Date() // invalid!
        )
        assertEquals("Pending income cannot have a date received", invalidPending.validate())

        // Confirmed must have dateReceived
        val invalidConfirmed = Income(
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 50000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 50000.0,
            dateReceived = null // invalid!
        )
        assertEquals("Confirmed income must have a date received", invalidConfirmed.validate())
    }
}
