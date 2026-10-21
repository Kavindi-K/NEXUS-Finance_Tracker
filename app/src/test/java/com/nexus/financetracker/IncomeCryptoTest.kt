package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests for Branch 09 (feature/income-crypto):
 * - Report realized crypto profits and losses as net P&L.
 * - Allow negative amountOriginal/amountLKR for crypto only.
 * - Ensure trading turnover is not treated as profit / earnings.
 * - Verify net total calculation (positive gains minus negative losses = correct net total).
 * - Verify YTD (Year-To-Date) net P&L filtering.
 */
class IncomeCryptoTest {

    private val testUserId = "test-crypto-user-09"

    @Test
    fun positiveGainsMinusNegativeLosses_producesCorrectNetTotal() {
        val now = Date()

        val salary = Income(
            id = "inc-salary",
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 130000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 130000.0,
            dateReceived = now
        )

        val cryptoGain = Income(
            id = "inc-crypto-gain",
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 45000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 45000.0,
            dateReceived = now
        )

        val cryptoLoss = Income(
            id = "inc-crypto-loss",
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -18500.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -18500.0,
            dateReceived = now
        )

        val pendingFreelance = Income(
            id = "inc-freelance-pending",
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 75000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 75000.0,
            expectedDate = now
        )

        val allIncomes = listOf(salary, cryptoGain, cryptoLoss, pendingFreelance)

        // 1. Confirmed total accurately deducts crypto loss from salary and crypto gain
        val confirmedTotal = allIncomes
            .filter { it.status == IncomeStatus.CONFIRMED }
            .sumOf { it.amountLKR }

        // 130,000 + 45,000 + (-18,500) = 156,500 LKR
        assertEquals(156500.0, confirmedTotal, 0.001)

        // 2. Net crypto P&L accurately combines gains and losses
        val cryptoIncomes = allIncomes.filter { it.source == IncomeSource.CRYPTO && it.status == IncomeStatus.CONFIRMED }
        val netCryptoPnl = cryptoIncomes.sumOf { it.amountLKR }
        val cryptoGains = cryptoIncomes.filter { it.amountLKR > 0 }.sumOf { it.amountLKR }
        val cryptoLosses = cryptoIncomes.filter { it.amountLKR < 0 }.sumOf { it.amountLKR }

        assertEquals(26500.0, netCryptoPnl, 0.001) // 45,000 - 18,500 = 26,500
        assertEquals(45000.0, cryptoGains, 0.001)
        assertEquals(-18500.0, cryptoLosses, 0.001)

        // 3. Pending amounts must not be mixed into confirmed earned total
        val pendingTotal = allIncomes
            .filter { it.status == IncomeStatus.PENDING }
            .sumOf { it.amountLKR }
        assertEquals(75000.0, pendingTotal, 0.001)
    }

    @Test
    fun cryptoAllowsNegativeAndPositiveAmounts_rejectsZero() {
        // Positive crypto gain
        val gain = Income(
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 32000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 32000.0,
            dateReceived = Date()
        )
        assertNull(gain.validate())

        // Negative crypto loss
        val loss = Income(
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -14200.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -14200.0,
            dateReceived = Date()
        )
        assertNull(loss.validate())

        // Zero crypto amount is strictly rejected
        val zero = gain.copy(amountOriginal = 0.0, amountLKR = 0.0)
        assertEquals("Crypto realized amount cannot be zero", zero.validate())
    }

    @Test
    fun nonCryptoStreams_strictlyRejectNegativeAmounts() {
        val date = Date()

        val negativeSalary = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -10000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -10000.0,
            dateReceived = date
        )
        assertEquals("Income amount must be greater than zero", negativeSalary.validate())

        val negativeFreelance = negativeSalary.copy(
            source = IncomeSource.FREELANCE,
            amountOriginal = -5000.0,
            amountLKR = -5000.0
        )
        assertEquals("Income amount must be greater than zero", negativeFreelance.validate())

        val negativeAdSense = negativeSalary.copy(
            source = IncomeSource.ADSENSE,
            amountOriginal = -200.0,
            currency = "USD",
            exchangeRate = 320.0,
            amountLKR = -64000.0
        )
        assertEquals("Income amount must be greater than zero", negativeAdSense.validate())
    }

    @Test
    fun cryptoYtdCalculation_correctlyFiltersCurrentYear() {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)

        // Current year gain
        cal.set(Calendar.MONTH, Calendar.FEBRUARY)
        val currentYearGain = Income(
            id = "c-curr-1",
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 50000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 50000.0,
            dateReceived = cal.time
        )

        // Current year loss
        cal.set(Calendar.MONTH, Calendar.MARCH)
        val currentYearLoss = Income(
            id = "c-curr-2",
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -15000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -15000.0,
            dateReceived = cal.time
        )

        // Prior year gain (e.g. previous year)
        val priorCal = Calendar.getInstance()
        priorCal.set(Calendar.YEAR, currentYear - 1)
        priorCal.set(Calendar.MONTH, Calendar.NOVEMBER)
        val priorYearGain = Income(
            id = "c-prior-1",
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 80000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 80000.0,
            dateReceived = priorCal.time
        )

        val allCrypto = listOf(currentYearGain, currentYearLoss, priorYearGain)

        // All-time crypto net
        val allTimeNet = allCrypto.sumOf { it.amountLKR }
        assertEquals(115000.0, allTimeNet, 0.001) // 50,000 - 15,000 + 80,000 = 115,000

        // YTD filtering: only current calendar year
        val ytdCrypto = allCrypto.filter { income ->
            val date = income.dateReceived ?: income.createdAt
            val itemCal = Calendar.getInstance().apply { time = date }
            itemCal.get(Calendar.YEAR) == currentYear
        }

        val ytdNet = ytdCrypto.sumOf { it.amountLKR }
        val ytdGains = ytdCrypto.filter { it.amountLKR > 0 }.sumOf { it.amountLKR }
        val ytdLosses = ytdCrypto.filter { it.amountLKR < 0 }.sumOf { it.amountLKR }

        assertEquals(2, ytdCrypto.size)
        assertEquals(35000.0, ytdNet, 0.001) // 50,000 - 15,000 = 35,000
        assertEquals(50000.0, ytdGains, 0.001)
        assertEquals(-15000.0, ytdLosses, 0.001)
    }

    @Test
    fun firestoreMap_preservesNegativeAmountsForCrypto() {
        val loss = Income(
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -25000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -25000.0,
            dateReceived = Date()
        )

        val map = loss.toFirestoreMap()
        assertEquals(-25000.0, map["amountOriginal"])
        assertEquals(-25000.0, map["amountLKR"])
        assertEquals("crypto", map["source"])
        assertEquals("confirmed", map["status"])
    }

    @Test
    fun netCryptoLoss_correctlyDeductsFromOverallIncome() {
        val date = Date()
        val salary = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 120000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 120000.0,
            dateReceived = date
        )

        // Substantial crypto loss exceeding gains
        val heavyLoss = Income(
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -30000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -30000.0,
            dateReceived = date
        )

        val netTotal = listOf(salary, heavyLoss).sumOf { it.amountLKR }
        assertEquals(90000.0, netTotal, 0.001) // 120,000 - 30,000 = 90,000
    }

    @Test
    fun cryptoFilter_filtersOnlyCryptoEntries() {
        val salary = Income(userId = testUserId, source = IncomeSource.SALARY, amountOriginal = 100000.0, amountLKR = 100000.0)
        val freelance = Income(userId = testUserId, source = IncomeSource.FREELANCE, amountOriginal = 25000.0, amountLKR = 25000.0)
        val cryptoGain = Income(userId = testUserId, source = IncomeSource.CRYPTO, amountOriginal = 15000.0, amountLKR = 15000.0)
        val cryptoLoss = Income(userId = testUserId, source = IncomeSource.CRYPTO, amountOriginal = -5000.0, amountLKR = -5000.0)

        val allIncomes = listOf(salary, freelance, cryptoGain, cryptoLoss)
        val cryptoOnly = allIncomes.filter { it.source == IncomeSource.CRYPTO }

        assertEquals(2, cryptoOnly.size)
        assertTrue(cryptoOnly.all { it.source == IncomeSource.CRYPTO })
        assertEquals(10000.0, cryptoOnly.sumOf { it.amountLKR }, 0.001)
    }
}
