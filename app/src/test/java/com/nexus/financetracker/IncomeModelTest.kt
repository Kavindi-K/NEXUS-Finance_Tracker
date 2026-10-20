package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit tests verifying Income domain model validation and Firestore schema serialization
 * as specified in FR3-FR8 and firestore.rules validIncome constraints.
 */
class IncomeModelTest {

    private val testUserId = "test-user-123"

    @Test
    fun validConfirmedSalary_passesValidation() {
        val income = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 150000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 150000.0,
            dateReceived = Date()
        )
        assertNull(income.validate())
    }

    @Test
    fun validForeignCurrencyFreelance_passesValidation() {
        val income = Income(
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 250.0,
            currency = "USD",
            exchangeRate = 320.0,
            amountLKR = 80000.0,
            dateReceived = Date()
        )
        assertNull(income.validate())
    }

    @Test
    fun cryptoRealizedIncome_allowsGainAndLoss_butRejectsZero() {
        val cryptoGain = Income(
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 25000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 25000.0,
            dateReceived = Date()
        )
        assertNull(cryptoGain.validate())

        val cryptoLoss = Income(
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -8500.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -8500.0,
            dateReceived = Date()
        )
        assertNull(cryptoLoss.validate())

        val cryptoZero = cryptoGain.copy(amountOriginal = 0.0, amountLKR = 0.0)
        assertEquals("Crypto realized amount cannot be zero", cryptoZero.validate())
    }

    @Test
    fun nonCryptoIncome_rejectsNegativeOrZeroAmount() {
        val negativeSalary = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -5000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -5000.0,
            dateReceived = Date()
        )
        assertEquals("Income amount must be greater than zero", negativeSalary.validate())

        val zeroAdsense = negativeSalary.copy(source = IncomeSource.ADSENSE, amountOriginal = 0.0, amountLKR = 0.0)
        assertEquals("Income amount must be greater than zero", zeroAdsense.validate())
    }

    @Test
    fun currencyValidation_requiresRateOneForLKR() {
        val invalidLkrRate = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 50000.0,
            currency = "LKR",
            exchangeRate = 1.5,
            amountLKR = 75000.0,
            dateReceived = Date()
        )
        assertEquals("Exchange rate for LKR must be 1.0", invalidLkrRate.validate())
    }

    @Test
    fun statusDateConsistency_confirmedRequiresDateReceived_pendingForbidsIt() {
        val missingDateConfirmed = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 50000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 50000.0,
            dateReceived = null
        )
        assertEquals("Confirmed income must have a date received", missingDateConfirmed.validate())

        val invalidPending = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.PENDING,
            amountOriginal = 50000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 50000.0,
            dateReceived = Date()
        )
        assertEquals("Pending income cannot have a date received", invalidPending.validate())
    }

    @Test
    fun toFirestoreMap_strictlyConformsToSecurityRulesSchema() {
        val receivedDate = Date()
        val confirmed = Income(
            id = "income-doc-id",
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 120000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 120000.0,
            dateReceived = receivedDate,
            expectedDate = null,
            note = "Monthly salary"
        )

        val confirmedMap = confirmed.toFirestoreMap()
        assertFalse("Document map must not include id field", confirmedMap.containsKey("id"))
        assertTrue("Confirmed must include dateReceived", confirmedMap.containsKey("dateReceived"))
        assertFalse("Confirmed must not include expectedDate", confirmedMap.containsKey("expectedDate"))
        assertEquals("Monthly salary", confirmedMap["note"])

        val expectedDate = Date()
        val pending = Income(
            id = "pending-id",
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 500.0,
            currency = "USD",
            exchangeRate = 320.0,
            amountLKR = 160000.0,
            dateReceived = null,
            expectedDate = expectedDate
        )

        val pendingMap = pending.toFirestoreMap()
        assertFalse("Pending must not contain dateReceived", pendingMap.containsKey("dateReceived"))
        assertTrue("Pending must contain expectedDate", pendingMap.containsKey("expectedDate"))
        assertFalse("Map must never include id", pendingMap.containsKey("id"))
    }
}
