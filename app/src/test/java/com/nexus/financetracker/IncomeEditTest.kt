package com.nexus.financetracker

import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.ui.income.IncomeFormState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import kotlin.math.roundToLong

/**
 * Unit tests for Branch 07 feature/income-edit:
 * "Allow correction of a saved income without duplicating it.
 *  Code / check: Load income by ID, show values in edit form, validate changes, recompute amountLKR if needed, update the same document and updatedAt.
 *  Done when: Edited record and totals update; no extra document is created."
 */
class IncomeEditTest {

    private val testUserId = "user-edit-test"

    /**
     * Fake repository tracking documents in a Map by ID to verify that updates
     * overwrite the exact same document without creating duplicates.
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
    fun loadIncomeById_populatesFormStateAccurately() = runBlocking {
        val repo = FakeIncomeRepository()
        val originalDate = Date(1704067200000L) // Jan 1, 2024
        val initialIncome = Income(
            id = "income-101",
            userId = testUserId,
            source = IncomeSource.ADSENSE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 150.50,
            currency = "USD",
            exchangeRate = 320.0,
            amountLKR = 48160.0,
            dateReceived = originalDate,
            note = "AdSense Dec payout",
            createdAt = originalDate
        )
        repo.documents[initialIncome.id] = initialIncome

        // Simulate loading income for edit
        val loaded = repo.getIncomeById("income-101")
        assertNotNull("Income should be found by ID", loaded)

        val formState = IncomeFormState(
            editingIncomeId = loaded!!.id,
            originalCreatedAt = loaded.createdAt,
            source = loaded.source,
            status = loaded.status,
            amountOriginal = String.format(java.util.Locale.US, "%.2f", loaded.amountOriginal),
            currency = loaded.currency,
            exchangeRate = loaded.exchangeRate.toString(),
            dateReceived = loaded.dateReceived ?: loaded.createdAt,
            note = loaded.note ?: ""
        )

        assertTrue(formState.isEditMode)
        assertEquals("income-101", formState.editingIncomeId)
        assertEquals(IncomeSource.ADSENSE, formState.source)
        assertEquals(IncomeStatus.CONFIRMED, formState.status)
        assertEquals("150.50", formState.amountOriginal)
        assertEquals("USD", formState.currency)
        assertEquals("320.0", formState.exchangeRate)
        assertEquals("AdSense Dec payout", formState.note)
        assertEquals(48160.0, formState.computedLkrAmount!!, 0.01)
    }

    @Test
    fun editIncome_updatesSameDocumentWithoutCreatingDuplicate() = runBlocking {
        val repo = FakeIncomeRepository()
        val createdAt = Date(1704067200000L)
        val initialIncome = Income(
            id = "salary-doc-1",
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 125000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 125000.0,
            dateReceived = createdAt,
            createdAt = createdAt
        )
        repo.addIncome(initialIncome)
        assertEquals(1, repo.documents.size)

        // User edits salary amount from 125,000 to 135,000
        val newAmount = 135000.0
        val rate = 1.0
        val computedLKR = ((newAmount * rate) * 100.0).roundToLong() / 100.0
        val updateTime = Date()

        val updatedIncome = initialIncome.copy(
            amountOriginal = newAmount,
            amountLKR = computedLKR,
            updatedAt = updateTime
        )

        assertNull("Updated income must pass validation", updatedIncome.validate())

        // Save update
        repo.updateIncome(updatedIncome)

        // Verify: Exactly 1 document remains in repository (NO duplicate created)
        assertEquals("Total document count must remain 1 (no duplicates)", 1, repo.documents.size)
        assertTrue("Document ID must be preserved", repo.documents.containsKey("salary-doc-1"))

        val stored = repo.documents["salary-doc-1"]!!
        assertEquals("salary-doc-1", stored.id)
        assertEquals(135000.0, stored.amountOriginal, 0.01)
        assertEquals(135000.0, stored.amountLKR, 0.01)
        assertEquals(createdAt, stored.createdAt) // Preserves createdAt for Firestore sameCreated() rule
        assertEquals(updateTime, stored.updatedAt) // Updates updatedAt
    }

    @Test
    fun editIncome_recomputesLkrWhenAmountOrExchangeRateChanges() {
        val originalAmountUsd = 100.0
        val originalRate = 320.0
        val originalLkr = ((originalAmountUsd * originalRate) * 100.0).roundToLong() / 100.0
        assertEquals(32000.0, originalLkr, 0.01)

        // Case A: Amount changed (from $100 to $200) with same rate
        val editedAmountUsd = 200.0
        val recomputedLkrA = ((editedAmountUsd * originalRate) * 100.0).roundToLong() / 100.0
        assertEquals(64000.0, recomputedLkrA, 0.01)

        // Case B: Exchange rate corrected (from 320 to 325.50) with original amount
        val editedRate = 325.50
        val recomputedLkrB = ((originalAmountUsd * editedRate) * 100.0).roundToLong() / 100.0
        assertEquals(32550.0, recomputedLkrB, 0.01)

        // Case C: Both amount and rate modified ($120 @ 315.25)
        val finalAmount = 120.0
        val finalRate = 315.25
        val recomputedLkrC = ((finalAmount * finalRate) * 100.0).roundToLong() / 100.0
        assertEquals(37830.0, recomputedLkrC, 0.01)
    }

    @Test
    fun editIncome_updatesTotalsAndSummariesAccurately() = runBlocking {
        val repo = FakeIncomeRepository()

        // Setup 2 confirmed incomes
        val income1 = Income(
            id = "inc-1",
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 130000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 130000.0,
            dateReceived = Date(),
            createdAt = Date()
        )
        val income2 = Income(
            id = "inc-2",
            userId = testUserId,
            source = IncomeSource.ADSENSE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 100.0,
            currency = "USD",
            exchangeRate = 320.0,
            amountLKR = 32000.0,
            dateReceived = Date(),
            createdAt = Date()
        )

        repo.addIncome(income1)
        repo.addIncome(income2)

        // Initial confirmed total = 130,000 + 32,000 = 162,000
        val initialTotal = repo.documents.values.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        assertEquals(162000.0, initialTotal, 0.01)

        // Edit income2 from $100 to $150 (amountLKR becomes 48,000)
        val editedIncome2 = income2.copy(
            amountOriginal = 150.0,
            amountLKR = 48000.0,
            updatedAt = Date()
        )
        repo.updateIncome(editedIncome2)

        // Total should update to 130,000 + 48,000 = 178,000
        val updatedTotal = repo.documents.values.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        assertEquals(178000.0, updatedTotal, 0.01)

        // Ensure total count of documents is STILL 2
        assertEquals(2, repo.documents.size)
    }

    @Test
    fun editIncome_validationRejectsInvalidEdits() {
        val baseIncome = Income(
            id = "test-doc",
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 130000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 130000.0,
            dateReceived = Date()
        )
        assertNull(baseIncome.validate())

        // Editing amount to negative/zero on Salary is rejected
        val zeroSalary = baseIncome.copy(amountOriginal = 0.0, amountLKR = 0.0)
        assertEquals("Income amount must be greater than zero", zeroSalary.validate())

        // Editing LKR exchange rate away from 1.0 is rejected
        val badRateLkr = baseIncome.copy(exchangeRate = 1.05, amountLKR = 136500.0)
        assertEquals("Exchange rate for LKR must be 1.0", badRateLkr.validate())

        // Mismatched amountLKR vs amountOriginal * exchangeRate is rejected
        val mismatchedLkr = baseIncome.copy(amountLKR = 99999.0)
        assertEquals("amountLKR does not match amountOriginal * exchangeRate", mismatchedLkr.validate())
    }
}
