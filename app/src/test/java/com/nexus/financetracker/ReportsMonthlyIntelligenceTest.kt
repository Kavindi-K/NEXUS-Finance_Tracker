package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.ui.dashboard.CategorySpending
import com.nexus.financetracker.ui.reports.DiscretionaryCategoryComparison
import com.nexus.financetracker.ui.reports.MonthlyGoalReport
import com.nexus.financetracker.ui.reports.MonthlyIncomeReport
import com.nexus.financetracker.ui.reports.RecommendationType
import com.nexus.financetracker.ui.reports.ReportsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for:
 * 1. Month-over-Month (MoM) variance calculations.
 * 2. Rule-based offline Financial Health Summary & Grade assignment.
 * 3. Algorithmic Next-Month Recommendations generation.
 */
class ReportsMonthlyIntelligenceTest {

    @Test
    fun momComparison_computesAccurateVariancesAndPercentages() {
        val prevReport = MonthlyIncomeReport(
            monthKey = "2026-09",
            monthLabel = "September 2026",
            totalIncomeLKR = 150000.0,
            totalExpensesLKR = 100000.0,
            netResultLKR = 50000.0
        )

        val currentReport = MonthlyIncomeReport(
            monthKey = "2026-10",
            monthLabel = "October 2026",
            totalIncomeLKR = 180000.0,
            totalExpensesLKR = 90000.0,
            netResultLKR = 90000.0
        )

        val comp = ReportsViewModel.calculateMonthOverMonthComparison(
            currentReport = currentReport,
            previousReport = prevReport,
            previousMonthLabel = "September 2026"
        )

        assertTrue(comp.hasPreviousMonth)
        assertEquals("September 2026", comp.previousMonthLabel)

        // Income: 180k - 150k = +30k (+20%)
        assertEquals(30000.0, comp.incomeDiffLKR, 0.01)
        assertEquals(20.0, comp.incomeChangePercent, 0.01)
        assertTrue(comp.isIncomeIncreased)

        // Expense: 90k - 100k = -10k (-10%)
        assertEquals(-10000.0, comp.expenseDiffLKR, 0.01)
        assertEquals(-10.0, comp.expenseChangePercent, 0.01)
        assertTrue(comp.isExpenseDecreased)

        // Savings: 90k - 50k = +40k (+80%)
        assertEquals(40000.0, comp.savingsDiffLKR, 0.01)
        assertEquals(80.0, comp.savingsChangePercent, 0.01)
        assertTrue(comp.isSavingsIncreased)

        // Savings Rate: (50/150 = 33.33%) -> (90/180 = 50.0%)
        assertEquals(50.0, comp.currentSavingsRatePercent, 0.01)
        assertEquals(33.33, comp.previousSavingsRatePercent, 0.05)
    }

    @Test
    fun momComparison_whenNoPreviousMonth_returnsBaseline() {
        val currentReport = MonthlyIncomeReport(
            monthKey = "2026-10",
            monthLabel = "October 2026",
            totalIncomeLKR = 100000.0,
            totalExpensesLKR = 60000.0,
            netResultLKR = 40000.0
        )

        val comp = ReportsViewModel.calculateMonthOverMonthComparison(
            currentReport = currentReport,
            previousReport = null,
            previousMonthLabel = null
        )

        assertFalse(comp.hasPreviousMonth)
        assertEquals(100000.0, comp.currentIncomeLKR, 0.01)
        assertEquals(40.0, comp.currentSavingsRatePercent, 0.01)
    }

    @Test
    fun healthSummary_withHighSavingsRate_awardsGradeAPlusAndGoalAction() {
        val report = MonthlyIncomeReport(
            monthKey = "2026-10",
            monthLabel = "October 2026",
            totalIncomeLKR = 200000.0,
            totalExpensesLKR = 100000.0,
            netResultLKR = 100000.0, // 50% savings rate -> Grade A+
            categorySpendings = listOf(
                CategorySpending(
                    name = "Food & Dining",
                    amountLKR = 40000.0,
                    fraction = 0.4f
                )
            ),
            discretionaryComparisons = listOf(
                DiscretionaryCategoryComparison(
                    categoryId = "cat_food",
                    categoryName = "Food & Dining",
                    estimatedAmountLKR = 50000.0,
                    actualExpenseLKR = 40000.0 // Within budget
                )
            ),
            goalReports = listOf(
                MonthlyGoalReport(
                    goalId = "goal_mac",
                    goalTitle = "MacBook Pro M4",
                    targetAmountLKR = 250000.0,
                    targetMonthlyLKR = 50000.0,
                    isActive = true,
                    isCompleted = false
                )
            )
        )

        val health = ReportsViewModel.generateHealthSummary(
            report = report,
            comparison = null,
            categories = emptyList()
        )

        assertEquals("A+", health.healthGrade)
        assertTrue(health.headline.contains("Exceptional") || health.headline.contains("Savings"))
        assertEquals("Food & Dining", health.topExpenseCategory)
        assertEquals(0, health.discretionaryOverspendCount)

        // Goal allocation recommendation should be generated
        val goalRec = health.recommendations.find { it.impactArea == "Goals" }
        assertNotNull(goalRec)
        assertEquals(RecommendationType.ACTION, goalRec?.type)
        assertTrue(goalRec?.title?.contains("MacBook Pro M4") == true)
    }

    @Test
    fun healthSummary_withDeficitSpending_warnsAndFlagsOverspending() {
        val report = MonthlyIncomeReport(
            monthKey = "2026-10",
            monthLabel = "October 2026",
            totalIncomeLKR = 100000.0,
            totalExpensesLKR = 140000.0,
            netResultLKR = -40000.0, // Deficit
            categorySpendings = listOf(
                CategorySpending(
                    name = "Gaming",
                    amountLKR = 50000.0,
                    fraction = 0.5f
                )
            ),
            discretionaryComparisons = listOf(
                DiscretionaryCategoryComparison(
                    categoryId = "cat_gaming",
                    categoryName = "Gaming",
                    estimatedAmountLKR = 20000.0,
                    actualExpenseLKR = 50000.0 // Over budget by 30k
                )
            )
        )

        val health = ReportsViewModel.generateHealthSummary(
            report = report,
            comparison = null,
            categories = emptyList()
        )

        assertTrue(health.healthGrade == "C" || health.healthGrade == "D")
        assertTrue(health.headline.contains("Deficit"))
        assertEquals(1, health.discretionaryOverspendCount)

        val discRec = health.recommendations.find { it.impactArea == "Discretionary" }
        assertNotNull(discRec)
        assertEquals(RecommendationType.WARNING, discRec?.type)
        assertTrue(discRec?.title?.contains("Gaming") == true)
    }

    @Test
    fun mtdComparison_calculatesCumulativeIncomeAndExpensesUpToCutoffDay() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // As of November 5, 2026 -> Cutoff day is 5
        val asOfDate = dateFormat.parse("2026-11-05")!!

        // Current month (November 2026):
        // Income on Nov 2 (day 2): 100,000
        // Income on Nov 4 (day 4): 50,000
        // Income on Nov 10 (day 10): 80,000 (after cutoff, should not be included in MTD)
        val currentIncomes = listOf(
            Income(id = "inc1", amountLKR = 100000.0, status = IncomeStatus.CONFIRMED, dateReceived = dateFormat.parse("2026-11-02")),
            Income(id = "inc2", amountLKR = 50000.0, status = IncomeStatus.CONFIRMED, dateReceived = dateFormat.parse("2026-11-04")),
            Income(id = "inc3", amountLKR = 80000.0, status = IncomeStatus.CONFIRMED, dateReceived = dateFormat.parse("2026-11-10"))
        )

        // Previous month (October 2026):
        // Income on Oct 3 (day 3): 120,000
        // Income on Oct 8 (day 8): 60,000 (after cutoff, should not be included in MTD)
        val previousIncomes = listOf(
            Income(id = "pinc1", amountLKR = 120000.0, status = IncomeStatus.CONFIRMED, dateReceived = dateFormat.parse("2026-10-03")),
            Income(id = "pinc2", amountLKR = 60000.0, status = IncomeStatus.CONFIRMED, dateReceived = dateFormat.parse("2026-10-08"))
        )

        // Current month expenses:
        // Expense on Nov 1 (day 1): 2,000
        // Expense on Nov 3 (day 3): 3,000
        // Expense on Nov 8 (day 8): 5,000 (after cutoff)
        val currentExpenses = listOf(
            Expense(id = "exp1", amountLKR = 2000.0, date = dateFormat.parse("2026-11-01")!!),
            Expense(id = "exp2", amountLKR = 3000.0, date = dateFormat.parse("2026-11-03")!!),
            Expense(id = "exp3", amountLKR = 5000.0, date = dateFormat.parse("2026-11-08")!!)
        )

        // Previous month expenses:
        // Expense on Oct 2 (day 2): 4,000
        // Expense on Oct 5 (day 5): 6,000
        // Expense on Oct 12 (day 12): 10,000 (after cutoff)
        val previousExpenses = listOf(
            Expense(id = "pexp1", amountLKR = 4000.0, date = dateFormat.parse("2026-10-02")!!),
            Expense(id = "pexp2", amountLKR = 6000.0, date = dateFormat.parse("2026-10-05")!!),
            Expense(id = "pexp3", amountLKR = 10000.0, date = dateFormat.parse("2026-10-12")!!)
        )

        val mtd = ReportsViewModel.calculateMonthToDateComparison(
            currentMonthLabel = "November 2026",
            previousMonthLabel = "October 2026",
            currentIncomes = currentIncomes,
            previousIncomes = previousIncomes,
            currentExpenses = currentExpenses,
            previousExpenses = previousExpenses,
            monthFormat = monthFormat,
            asOfDate = asOfDate
        )

        assertEquals(5, mtd.cutoffDay)
        assertEquals("November 2026", mtd.currentMonthLabel)
        assertEquals("October 2026", mtd.previousMonthLabel)
        assertTrue(mtd.hasPreviousMonth)

        // MTD Income:
        // Nov 1..5: 100k + 50k = 150k
        // Oct 1..5: 120k
        assertEquals(150000.0, mtd.currentIncomeMtdLKR, 0.01)
        assertEquals(120000.0, mtd.previousIncomeMtdLKR, 0.01)
        assertEquals(30000.0, mtd.incomeMtdDiffLKR, 0.01)
        assertEquals(25.0, mtd.incomeMtdChangePercent, 0.01)
        assertTrue(mtd.isIncomeIncreased)

        // MTD Expenses:
        // Nov 1..5: 2k + 3k = 5k
        // Oct 1..5: 4k + 6k = 10k
        assertEquals(5000.0, mtd.currentExpensesMtdLKR, 0.01)
        assertEquals(10000.0, mtd.previousExpensesMtdLKR, 0.01)
        assertEquals(-5000.0, mtd.expenseMtdDiffLKR, 0.01)
        assertEquals(-50.0, mtd.expenseMtdChangePercent, 0.01)
        assertTrue(mtd.isExpenseDecreased)

        // Daily trajectory points count must equal cutoffDay
        assertEquals(5, mtd.dailyIncomePoints.size)
        assertEquals(5, mtd.dailyExpensePoints.size)

        // Day 1:
        assertEquals(0.0, mtd.dailyIncomePoints[0].currentMonthCumulativeLKR, 0.01)
        assertEquals(2000.0, mtd.dailyExpensePoints[0].currentMonthCumulativeLKR, 0.01)

        // Day 3:
        assertEquals(100000.0, mtd.dailyIncomePoints[2].currentMonthCumulativeLKR, 0.01)
        assertEquals(120000.0, mtd.dailyIncomePoints[2].previousMonthCumulativeLKR, 0.01)
        assertEquals(5000.0, mtd.dailyExpensePoints[2].currentMonthCumulativeLKR, 0.01)
        assertEquals(4000.0, mtd.dailyExpensePoints[2].previousMonthCumulativeLKR, 0.01)

        // Day 5 (endpoint):
        assertEquals(150000.0, mtd.dailyIncomePoints[4].currentMonthCumulativeLKR, 0.01)
        assertEquals(120000.0, mtd.dailyIncomePoints[4].previousMonthCumulativeLKR, 0.01)
        assertEquals(5000.0, mtd.dailyExpensePoints[4].currentMonthCumulativeLKR, 0.01)
        assertEquals(10000.0, mtd.dailyExpensePoints[4].previousMonthCumulativeLKR, 0.01)
    }

    @Test
    fun mtdComparison_whenNoPreviousMonth_handlesBaseline() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val asOfDate = dateFormat.parse("2026-11-03")!!

        val currentIncomes = listOf(
            Income(id = "inc1", amountLKR = 50000.0, status = IncomeStatus.CONFIRMED, dateReceived = dateFormat.parse("2026-11-02"))
        )
        val currentExpenses = listOf(
            Expense(id = "exp1", amountLKR = 1500.0, date = dateFormat.parse("2026-11-01")!!)
        )

        val mtd = ReportsViewModel.calculateMonthToDateComparison(
            currentMonthLabel = "November 2026",
            previousMonthLabel = null,
            currentIncomes = currentIncomes,
            previousIncomes = emptyList(),
            currentExpenses = currentExpenses,
            previousExpenses = emptyList(),
            monthFormat = monthFormat,
            asOfDate = asOfDate
        )

        assertEquals(3, mtd.cutoffDay)
        assertFalse(mtd.hasPreviousMonth)
        assertEquals(50000.0, mtd.currentIncomeMtdLKR, 0.01)
        assertEquals(0.0, mtd.previousIncomeMtdLKR, 0.01)
        assertEquals(1500.0, mtd.currentExpensesMtdLKR, 0.01)
        assertEquals(0.0, mtd.previousExpensesMtdLKR, 0.01)
    }
}

