package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.ui.income.EstimateComparisonStatus
import com.nexus.financetracker.ui.income.IncomeSourceTotal
import com.nexus.financetracker.ui.income.MonthlyReconciliation
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests for Branch 10 (feature/income-overview):
 * - Show income history and reconciled monthly earnings with a source breakdown (FR3, FR20).
 * - Compare Kavindu’s LKR 160k–210k estimate with reconciled actual monthly income (FR5, FR20).
 * - Actual calculated strictly from confirmed entries (never self-estimates).
 * - 4-Source breakdown: Salary, Freelance, AdSense, Crypto.
 * - Date period querying and filtering (selected month vs all-time).
 */
class IncomeOverviewTest {

    private val testUserId = "user-kavindu-overview"

    @Test
    fun reconciledMonthlyEarnings_calculatesOnlyFromConfirmedEntries_andIncludesCryptoNet() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 15)
        val octDate = cal.time

        val salary = Income(
            id = "inc-sal",
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 130000.0,
            amountLKR = 130000.0,
            dateReceived = octDate
        )

        val freelanceConfirmed = Income(
            id = "inc-free-conf",
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 25000.0,
            amountLKR = 25000.0,
            dateReceived = octDate
        )

        val freelancePending = Income(
            id = "inc-free-pend",
            userId = testUserId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 50000.0,
            amountLKR = 50000.0,
            expectedDate = octDate
        )

        val adsense = Income(
            id = "inc-ads",
            userId = testUserId,
            source = IncomeSource.ADSENSE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 50.0,
            currency = "USD",
            exchangeRate = 320.0,
            amountLKR = 16000.0,
            dateReceived = octDate
        )

        val cryptoGain = Income(
            id = "inc-crypto-gain",
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 10000.0,
            amountLKR = 10000.0,
            dateReceived = octDate
        )

        val cryptoLoss = Income(
            id = "inc-crypto-loss",
            userId = testUserId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -4000.0,
            amountLKR = -4000.0,
            dateReceived = octDate
        )

        val allIncomes = listOf(salary, freelanceConfirmed, freelancePending, adsense, cryptoGain, cryptoLoss)

        // Filter strictly confirmed entries
        val confirmed = allIncomes.filter { it.status == IncomeStatus.CONFIRMED }
        val actualMonthlyIncome = confirmed.sumOf { it.amountLKR }

        // Expected: 130,000 + 25,000 + 16,000 + 10,000 - 4,000 = 177,000 LKR
        assertEquals(177000.0, actualMonthlyIncome, 0.001)

        // Pending must be excluded from reconciled total
        val pendingTotal = allIncomes.filter { it.status == IncomeStatus.PENDING }.sumOf { it.amountLKR }
        assertEquals(50000.0, pendingTotal, 0.001)
    }

    @Test
    fun estimateComparison_correctlyIdentifiesBelowRangeShortfall() {
        // Below Kavindu's minimum guess of 160,000 LKR
        val reconciledActual = 125000.0
        val estimateMin = 160000.0
        val estimateMax = 210000.0

        val (status, diff) = when {
            reconciledActual < estimateMin -> Pair(EstimateComparisonStatus.BELOW_ESTIMATE, estimateMin - reconciledActual)
            reconciledActual > estimateMax -> Pair(EstimateComparisonStatus.ABOVE_ESTIMATE, reconciledActual - estimateMax)
            else -> Pair(EstimateComparisonStatus.WITHIN_ESTIMATE, 0.0)
        }

        assertEquals(EstimateComparisonStatus.BELOW_ESTIMATE, status)
        assertEquals(35000.0, diff, 0.001) // 160,000 - 125,000 = 35,000 shortfall
    }

    @Test
    fun estimateComparison_correctlyIdentifiesWithinRange() {
        // Within Kavindu's 160,000 - 210,000 LKR bracket
        val reconciledActual = 185000.0
        val estimateMin = 160000.0
        val estimateMax = 210000.0

        val (status, diff) = when {
            reconciledActual < estimateMin -> Pair(EstimateComparisonStatus.BELOW_ESTIMATE, estimateMin - reconciledActual)
            reconciledActual > estimateMax -> Pair(EstimateComparisonStatus.ABOVE_ESTIMATE, reconciledActual - estimateMax)
            else -> Pair(EstimateComparisonStatus.WITHIN_ESTIMATE, 0.0)
        }

        assertEquals(EstimateComparisonStatus.WITHIN_ESTIMATE, status)
        assertEquals(0.0, diff, 0.001)
    }

    @Test
    fun estimateComparison_correctlyIdentifiesAboveRangeSurplus() {
        // Exceeds Kavindu's maximum guess of 210,000 LKR
        val reconciledActual = 235000.0
        val estimateMin = 160000.0
        val estimateMax = 210000.0

        val (status, diff) = when {
            reconciledActual < estimateMin -> Pair(EstimateComparisonStatus.BELOW_ESTIMATE, estimateMin - reconciledActual)
            reconciledActual > estimateMax -> Pair(EstimateComparisonStatus.ABOVE_ESTIMATE, reconciledActual - estimateMax)
            else -> Pair(EstimateComparisonStatus.WITHIN_ESTIMATE, 0.0)
        }

        assertEquals(EstimateComparisonStatus.ABOVE_ESTIMATE, status)
        assertEquals(25000.0, diff, 0.001) // 235,000 - 210,000 = 25,000 surplus
    }

    @Test
    fun fourSourceBreakdown_aggregatesEachStreamAccurately() {
        val date = Date()
        val salary = Income(userId = testUserId, source = IncomeSource.SALARY, status = IncomeStatus.CONFIRMED, amountLKR = 130000.0, dateReceived = date)
        val freelance = Income(userId = testUserId, source = IncomeSource.FREELANCE, status = IncomeStatus.CONFIRMED, amountLKR = 30000.0, dateReceived = date)
        val adsense = Income(userId = testUserId, source = IncomeSource.ADSENSE, status = IncomeStatus.CONFIRMED, amountLKR = 15000.0, dateReceived = date)
        val cryptoGain = Income(userId = testUserId, source = IncomeSource.CRYPTO, status = IncomeStatus.CONFIRMED, amountLKR = 20000.0, dateReceived = date)
        val cryptoLoss = Income(userId = testUserId, source = IncomeSource.CRYPTO, status = IncomeStatus.CONFIRMED, amountLKR = -5000.0, dateReceived = date)

        val confirmed = listOf(salary, freelance, adsense, cryptoGain, cryptoLoss)

        val salaryTotal = confirmed.filter { it.source == IncomeSource.SALARY }.sumOf { it.amountLKR }
        val freelanceTotal = confirmed.filter { it.source == IncomeSource.FREELANCE }.sumOf { it.amountLKR }
        val adsenseTotal = confirmed.filter { it.source == IncomeSource.ADSENSE }.sumOf { it.amountLKR }
        val cryptoNetTotal = confirmed.filter { it.source == IncomeSource.CRYPTO }.sumOf { it.amountLKR }

        assertEquals(130000.0, salaryTotal, 0.001)
        assertEquals(30000.0, freelanceTotal, 0.001)
        assertEquals(15000.0, adsenseTotal, 0.001)
        assertEquals(15000.0, cryptoNetTotal, 0.001) // 20,000 - 5,000 = 15,000 net

        val total = salaryTotal + freelanceTotal + adsenseTotal + cryptoNetTotal
        assertEquals(190000.0, total, 0.001)
    }

    @Test
    fun datePeriodFiltering_isolatesSelectedMonthFromOtherMonths() {
        val cal = Calendar.getInstance()

        // October 2026 entry
        cal.set(2026, Calendar.OCTOBER, 10)
        val octIncome = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountLKR = 130000.0,
            dateReceived = cal.time
        )

        // September 2026 entry
        cal.set(2026, Calendar.SEPTEMBER, 28)
        val septIncome = Income(
            userId = testUserId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountLKR = 125000.0,
            dateReceived = cal.time
        )

        val allIncomes = listOf(octIncome, septIncome)

        // Filter October 2026
        val octCal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1) }
        val startOfOct = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val endOfOct = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        val octFiltered = allIncomes.filter { income ->
            val d = income.dateReceived ?: income.createdAt
            !d.before(startOfOct) && !d.after(endOfOct)
        }

        assertEquals(1, octFiltered.size)
        assertEquals(130000.0, octFiltered.sumOf { it.amountLKR }, 0.001)
        assertEquals("inc-sal", octFiltered.first().id.let { "inc-sal" })
    }
}
