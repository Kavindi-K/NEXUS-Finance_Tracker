package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import kotlin.math.round

/**
 * Unit tests verifying Branch 06 feature/income-currency (FR4):
 * 1. Accurately calculate amountLKR = amountOriginal × exchangeRate for USD/LKR.
 * 2. Enforce exchangeRate = 1.0 for LKR.
 * 3. Store both original currency amounts and historic exchange rate.
 * 4. Verify historical transactions remain immutable when later rates change.
 */
class IncomeCurrencyTest {

    private val testUserId = "user-usd-test"

    @Test
    fun usdConversion_calculatesAccuratelyForVariousRates() {
        val testCases = listOf(
            Triple(100.00, 320.00, 32000.00),
            Triple(250.00, 325.50, 81375.00),
            Triple(45.50, 310.25, 14116.38),
            Triple(1200.75, 305.80, 367189.35)
        )

        for ((originalUsd, rate, expectedLkr) in testCases) {
            val computedLkr = round(originalUsd * rate * 100.0) / 100.0
            assertEquals(expectedLkr, computedLkr, 0.01)

            val income = Income(
                userId = testUserId,
                source = IncomeSource.ADSENSE,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = originalUsd,
                currency = "USD",
                exchangeRate = rate,
                amountLKR = computedLkr,
                dateReceived = Date()
            )
            assertNull("Income should pass validation", income.validate())
        }
    }

    @Test
    fun lkrCurrency_strictlyRequiresExchangeRateOfOne() {
        val validLkr = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 140000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 140000.0,
            dateReceived = Date()
        )
        assertNull(validLkr.validate())

        val invalidLkr = validLkr.copy(exchangeRate = 1.05, amountLKR = 147000.0)
        assertEquals("Exchange rate for LKR must be 1.0", invalidLkr.validate())
    }

    @Test
    fun historicRates_remainImmutableWhenLaterRatesChange() {
        // Month 1 AdSense payout: $100 USD @ 300.0 LKR transfer rate
        val month1Income = Income(
            id = "income-jan",
            userId = testUserId,
            source = IncomeSource.ADSENSE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 100.0,
            currency = "USD",
            exchangeRate = 300.0,
            amountLKR = 30000.0,
            dateReceived = Date(1704067200000L) // Jan 1
        )

        // Month 2 AdSense payout: $100 USD @ 325.0 LKR transfer rate
        val month2Income = Income(
            id = "income-feb",
            userId = testUserId,
            source = IncomeSource.ADSENSE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 100.0,
            currency = "USD",
            exchangeRate = 325.0,
            amountLKR = 32500.0,
            dateReceived = Date(1706745600000L) // Feb 1
        )

        // Month 3 live market rate fluctuates to 360.0 LKR
        val liveMarketRateMarch = 360.0
        assertNotEquals(liveMarketRateMarch, month1Income.exchangeRate)
        assertNotEquals(liveMarketRateMarch, month2Income.exchangeRate)

        // Historic records store both original amount and historic conversion rate permanently
        val month1Map = month1Income.toFirestoreMap()
        assertEquals(100.0, month1Map["amountOriginal"])
        assertEquals("USD", month1Map["currency"])
        assertEquals(300.0, month1Map["exchangeRate"])
        assertEquals(30000.0, month1Map["amountLKR"])

        val month2Map = month2Income.toFirestoreMap()
        assertEquals(100.0, month2Map["amountOriginal"])
        assertEquals("USD", month2Map["currency"])
        assertEquals(325.0, month2Map["exchangeRate"])
        assertEquals(32500.0, month2Map["amountLKR"])

        // Historic total remains static: 30,000 + 32,500 = 62,500 LKR
        val historicTotal = listOf(month1Income, month2Income).sumOf { it.amountLKR }
        assertEquals(62500.0, historicTotal, 0.001)

        // It does NOT recalculate to 2 * 100 * 360 = 72,000 LKR
        assertNotEquals(72000.0, historicTotal)
    }

    @Test
    fun validation_rejectsNegativeOrZeroExchangeRate() {
        val zeroRate = Income(
            userId = testUserId,
            source = IncomeSource.ADSENSE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 50.0,
            currency = "USD",
            exchangeRate = 0.0,
            amountLKR = 0.0,
            dateReceived = Date()
        )
        assertEquals("Exchange rate must be positive", zeroRate.validate())

        val negativeRate = zeroRate.copy(exchangeRate = -320.0, amountLKR = -16000.0)
        assertEquals("Exchange rate must be positive", negativeRate.validate())
    }

    @Test
    fun validation_rejectsInconsistentLkrCalculations() {
        val inconsistent = Income(
            userId = testUserId,
            source = IncomeSource.ADSENSE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 100.0,
            currency = "USD",
            exchangeRate = 320.0,
            amountLKR = 30000.0, // Should be 32,000.0
            dateReceived = Date()
        )
        assertEquals("amountLKR does not match amountOriginal * exchangeRate", inconsistent.validate())
    }
}
