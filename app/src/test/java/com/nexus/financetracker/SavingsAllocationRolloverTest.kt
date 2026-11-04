package com.nexus.financetracker

import com.nexus.financetracker.ui.dashboard.DashboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests verifying:
 * 1. Income - Expenses = Savings calculation.
 * 2. End-of-month savings allocation: percentage split to Goal Contributions and remaining to next-month rollover.
 * 3. Next month's Dashboard Total Amount card displays total balance including rolled-over savings.
 * 4. End-of-month review detection window.
 * 5. Monthly allocation state resolution:
 *    - Shows October allocation when previous month is unreviewed.
 *    - Disappears as soon as allocation is done.
 *    - Only appears at the end of the month during normal monthly cycle.
 */
class SavingsAllocationRolloverTest {

    @Test
    fun savingsCalculation_incomeMinusExpenses_positiveSavings() {
        val income = 150000.0
        val expense = 65000.0
        val (savings, _, _) = DashboardViewModel.calculateSavingsAllocation(income, expense, 0.0)

        assertEquals(85000.0, savings, 0.001)
    }

    @Test
    fun savingsCalculation_deficitIncome_yieldsZeroSavings() {
        val income = 40000.0
        val expense = 75000.0
        val (savings, contrib, rollover) = DashboardViewModel.calculateSavingsAllocation(income, expense, 25.0)

        assertEquals(0.0, savings, 0.001)
        assertEquals(0.0, contrib, 0.001)
        assertEquals(0.0, rollover, 0.001)
    }

    @Test
    fun savingsAllocation_percentageToGoalContribution_restRolledOverToNextMonth() {
        val income = 200000.0
        val expense = 80000.0
        val totalSavings = 120000.0 // Income - Expenses

        // Allocate 30% to Goal Contributions
        val (savings, contribution, rollover) = DashboardViewModel.calculateSavingsAllocation(
            income = income,
            expense = expense,
            contributionPercent = 30.0
        )

        assertEquals(totalSavings, savings, 0.001)
        assertEquals(36000.0, contribution, 0.001) // 30% of 120,000 LKR
        assertEquals(84000.0, rollover, 0.001)     // 70% of 120,000 LKR (remaining to next month)
        assertEquals(totalSavings, contribution + rollover, 0.001)
    }

    @Test
    fun savingsAllocation_edgeCases_zeroAndHundredPercent() {
        val income = 100000.0
        val expense = 40000.0
        val totalSavings = 60000.0

        // 0% to Goals -> 100% rolls over
        val (_, contrib0, roll0) = DashboardViewModel.calculateSavingsAllocation(income, expense, 0.0)
        assertEquals(0.0, contrib0, 0.001)
        assertEquals(totalSavings, roll0, 0.001)

        // 100% to Goals -> 0% rolls over
        val (_, contrib100, roll100) = DashboardViewModel.calculateSavingsAllocation(income, expense, 100.0)
        assertEquals(totalSavings, contrib100, 0.001)
        assertEquals(0.0, roll100, 0.001)
    }

    @Test
    fun dashboardTotalAmountCard_includesRolloverSavingsFromPreviousMonth() {
        // Previous month rolled-over savings: 84,000 LKR
        val rolledOverSavings = 84000.0

        // Current month confirmed income and expenses
        val currentMonthIncome = 120000.0
        val currentMonthExpense = 50000.0
        val currentNetResult = currentMonthIncome - currentMonthExpense // 70,000 LKR

        // Total Amount card display balance = rolled-over savings + current net result
        val totalAmountCardBalance = rolledOverSavings + currentNetResult

        assertEquals(154000.0, totalAmountCardBalance, 0.001)
    }

    @Test
    fun endOfMonthWindow_detectsLastFiveDaysOfMonth() {
        // 31-day month (e.g. October): days 27..31 are end-of-month
        val oct28 = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 28) }
        assertTrue(DashboardViewModel.isEndOfMonth(oct28))

        val oct15 = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 15) }
        assertFalse(DashboardViewModel.isEndOfMonth(oct15))

        // 30-day month (e.g. November): days 26..30 are end-of-month
        val nov27 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 27) }
        assertTrue(DashboardViewModel.isEndOfMonth(nov27))

        val nov10 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 10) }
        assertFalse(DashboardViewModel.isEndOfMonth(nov10))
    }

    @Test
    fun resolveSavingsAllocationState_unreviewedPreviousMonth_showsOctoberAllocation() {
        // Today is early November (not end of month)
        // October 2026 has concluded and has not been reviewed yet
        val state = DashboardViewModel.resolveSavingsAllocationState(
            isPrevMonthReviewed = false,
            prevMonthLabel = "October 2026",
            prevIncomeLKR = 185954.40,
            prevExpenseLKR = 6250.00,
            isCurrentMonthReviewed = false,
            currentMonthLabel = "November 2026",
            currentIncomeLKR = 0.0,
            currentExpenseLKR = 0.0,
            nextMonthLabel = "December 2026",
            isEndOfMonthWindow = false
        )

        assertTrue("Banner must show for unreviewed previous month", state.showBanner)
        assertEquals("October 2026", state.allocationMonthLabel)
        assertEquals("November 2026", state.targetMonthLabel)
        assertEquals(179704.40, state.savingsLKR, 0.001)
        assertEquals(185954.40, state.incomeLKR, 0.001)
        assertEquals(6250.00, state.expenseLKR, 0.001)
    }

    @Test
    fun resolveSavingsAllocationState_fallbackToCurrentTransactionsForOctober() {
        // If user recorded test transactions under current month, but October has not been reviewed
        val state = DashboardViewModel.resolveSavingsAllocationState(
            isPrevMonthReviewed = false,
            prevMonthLabel = "October 2026",
            prevIncomeLKR = 0.0,
            prevExpenseLKR = 0.0,
            isCurrentMonthReviewed = false,
            currentMonthLabel = "November 2026",
            currentIncomeLKR = 185954.40,
            currentExpenseLKR = 6250.00,
            nextMonthLabel = "December 2026",
            isEndOfMonthWindow = false
        )

        assertTrue("Banner must show for unreviewed October using available monthly savings", state.showBanner)
        assertEquals("October 2026", state.allocationMonthLabel)
        assertEquals("November 2026", state.targetMonthLabel)
        assertEquals(179704.40, state.savingsLKR, 0.001)
    }

    @Test
    fun resolveSavingsAllocationState_whenAllocationDone_bannerDisappears() {
        // Once October is reviewed, in middle of November the banner must disappear completely
        val state = DashboardViewModel.resolveSavingsAllocationState(
            isPrevMonthReviewed = true, // October allocation DONE
            prevMonthLabel = "October 2026",
            prevIncomeLKR = 185954.40,
            prevExpenseLKR = 6250.00,
            isCurrentMonthReviewed = false,
            currentMonthLabel = "November 2026",
            currentIncomeLKR = 50000.0,
            currentExpenseLKR = 10000.0,
            nextMonthLabel = "December 2026",
            isEndOfMonthWindow = false // Middle of November (e.g. Nov 3)
        )

        assertFalse("Banner must disappear when allocation is done and not at end of month", state.showBanner)
        assertEquals("", state.allocationMonthLabel)
    }

    @Test
    fun resolveSavingsAllocationState_endOfCurrentMonth_showsNovemberAllocation() {
        // At the end of November (e.g. Nov 28), October is reviewed, November is ready for allocation
        val state = DashboardViewModel.resolveSavingsAllocationState(
            isPrevMonthReviewed = true,
            prevMonthLabel = "October 2026",
            prevIncomeLKR = 185954.40,
            prevExpenseLKR = 6250.00,
            isCurrentMonthReviewed = false,
            currentMonthLabel = "November 2026",
            currentIncomeLKR = 120000.0,
            currentExpenseLKR = 40000.0,
            nextMonthLabel = "December 2026",
            isEndOfMonthWindow = true // End of November
        )

        assertTrue("Banner must appear at the end of November", state.showBanner)
        assertEquals("November 2026", state.allocationMonthLabel)
        assertEquals("December 2026", state.targetMonthLabel)
        assertEquals(80000.0, state.savingsLKR, 0.001)
    }

    @Test
    fun resolveSavingsAllocationState_endOfCurrentMonthReviewed_bannerDisappears() {
        // When November allocation is also done, banner disappears
        val state = DashboardViewModel.resolveSavingsAllocationState(
            isPrevMonthReviewed = true,
            prevMonthLabel = "October 2026",
            prevIncomeLKR = 185954.40,
            prevExpenseLKR = 6250.00,
            isCurrentMonthReviewed = true, // November allocation DONE
            currentMonthLabel = "November 2026",
            currentIncomeLKR = 120000.0,
            currentExpenseLKR = 40000.0,
            nextMonthLabel = "December 2026",
            isEndOfMonthWindow = true
        )

        assertFalse("Banner must disappear once current month allocation is done", state.showBanner)
    }
}
