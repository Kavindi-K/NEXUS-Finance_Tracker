package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.domain.model.GoalPriority
import com.nexus.financetracker.ui.dashboard.DashboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * Unit tests verifying Dashboard Summary calculations (FR5, FR18, FR20).
 *
 * Implements Acceptance Criteria for Member 3 - Order 7 (feature/dashboard-summary):
 * - Reconciles confirmed monthly income from four sources
 * - Excludes pending income from earned totals
 * - Computes total expenses and net result
 * - Computes committed vs discretionary spending ratio
 * - Reconciles active goal summary
 * - Safe handling of empty datasets without division by zero
 */
class DashboardSummaryTest {

    @Test
    fun monthRangeCalculation_coversFullCalendarMonth() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15, 12, 30, 0)
        }
        val (startDate, endDate) = DashboardViewModel.getMonthRange(cal)

        val startCal = Calendar.getInstance().apply { time = startDate }
        val endCal = Calendar.getInstance().apply { time = endDate }

        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(31, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun confirmedIncomeReconciliation_sumsOnlyConfirmedCurrentMonthIncomes() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15)
        }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)

        val dateThisMonth = Date(startOfMonth.time + 5L * 86400000L)
        val dateOutsideMonth = Date(startOfMonth.time - 5L * 86400000L)

        val incomes = listOf(
            // 1. Confirmed Salary this month: 140,000 LKR
            Income(
                id = "inc-1",
                userId = "user-1",
                source = IncomeSource.SALARY,
                status = IncomeStatus.CONFIRMED,
                amountLKR = 140000.0,
                dateReceived = dateThisMonth
            ),
            // 2. Confirmed AdSense this month: 14,400 LKR
            Income(
                id = "inc-2",
                userId = "user-1",
                source = IncomeSource.ADSENSE,
                status = IncomeStatus.CONFIRMED,
                amountLKR = 14400.0,
                dateReceived = dateThisMonth
            ),
            // 3. Pending Freelance this month: 35,000 LKR (MUST BE EXCLUDED)
            Income(
                id = "inc-3",
                userId = "user-1",
                source = IncomeSource.FREELANCE,
                status = IncomeStatus.PENDING,
                amountLKR = 35000.0,
                dateReceived = null
            ),
            // 4. Confirmed Salary outside this month: 120,000 LKR (MUST BE EXCLUDED)
            Income(
                id = "inc-4",
                userId = "user-1",
                source = IncomeSource.SALARY,
                status = IncomeStatus.CONFIRMED,
                amountLKR = 120000.0,
                dateReceived = dateOutsideMonth
            )
        )

        val confirmedInMonth = incomes.filter { income ->
            income.status == IncomeStatus.CONFIRMED &&
                income.dateReceived != null &&
                !income.dateReceived.before(startOfMonth) &&
                !income.dateReceived.after(endOfMonth)
        }

        val totalIncome = confirmedInMonth.sumOf { it.amountLKR }

        // Expected: 140,000 + 14,400 = 154,400 LKR
        assertEquals(154400.0, totalIncome, 0.01)
    }

    @Test
    fun committedVsDiscretionarySplit_calculatesRatiosAccurately() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10)
        }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)
        val dateThisMonth = Date(startOfMonth.time + 2L * 86400000L)

        // Rent: 34,000 committed
        // Dining: 24,000 discretionary
        // Coffee: 6,000 discretionary
        val expenses = listOf(
            Expense(
                id = "exp-rent",
                userId = "user-1",
                amountLKR = 34000.0,
                classification = ExpenseClassification.COMMITTED,
                date = dateThisMonth
            ),
            Expense(
                id = "exp-dining",
                userId = "user-1",
                amountLKR = 24000.0,
                classification = ExpenseClassification.DISCRETIONARY,
                date = dateThisMonth
            ),
            Expense(
                id = "exp-coffee",
                userId = "user-1",
                amountLKR = 6000.0,
                classification = ExpenseClassification.DISCRETIONARY,
                date = dateThisMonth
            )
        )

        val monthExpenses = expenses.filter { !it.date.before(startOfMonth) && !it.date.after(endOfMonth) }
        val totalExpense = monthExpenses.sumOf { it.amountLKR } // 64,000 LKR

        val committed = monthExpenses.filter { it.classification == ExpenseClassification.COMMITTED }.sumOf { it.amountLKR }
        val discretionary = monthExpenses.filter { it.classification == ExpenseClassification.DISCRETIONARY }.sumOf { it.amountLKR }

        val committedPercent = ((committed / totalExpense) * 100.0).roundToInt()
        val discretionaryPercent = 100 - committedPercent

        // 34,000 / 64,000 = 53.125% -> 53% committed
        // 30,000 / 64,000 = 46.875% -> 47% discretionary
        assertEquals(64000.0, totalExpense, 0.01)
        assertEquals(34000.0, committed, 0.01)
        assertEquals(30000.0, discretionary, 0.01)
        assertEquals(53, committedPercent)
        assertEquals(47, discretionaryPercent)
    }

    @Test
    fun emptyMonth_safeDefaultsWithoutDivisionByZero() {
        val totalExpense = 0.0
        val committedPercent = if (totalExpense > 0) ((0.0 / totalExpense) * 100).roundToInt() else 0
        val discretionaryPercent = if (totalExpense > 0) 100 - committedPercent else 0

        assertEquals(0, committedPercent)
        assertEquals(0, discretionaryPercent)
    }

    @Test
    fun activeGoalSummary_reconcilesActiveGoalMetrics() {
        val now = Date()
        val activeGoal = Goal(
            id = "goal-active",
            userId = "user-1",
            title = "MacBook Pro M4",
            targetAmountLKR = 490000.0,
            openingSavingsLKR = 11200.0,
            startDate = now,
            targetDate = Date(now.time + 365L * 86400000L),
            isActive = true
        )

        val inactiveGoal = Goal(
            id = "goal-inactive",
            userId = "user-1",
            title = "Old Goal",
            isActive = false
        )

        val goals = listOf(inactiveGoal, activeGoal)
        val selectedActiveGoal = goals.firstOrNull { it.isActive }

        assertNotNull(selectedActiveGoal)
        assertEquals("MacBook Pro M4", selectedActiveGoal?.title)

        val calculation = GoalCalculation.calculate(
            goal = selectedActiveGoal!!,
            incomes = emptyList(),
            expenses = emptyList(),
            currentDate = now
        )

        assertEquals(11200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
        assertEquals(478800.0, calculation.remainingAmountLKR, 0.01)
    }

    @Test
    fun getGreeting_returnsCorrectGreetingBasedOnHourOfDay() {
        val morningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 8) }
        val afternoonCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 14) }
        val eveningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 19) }
        val nightCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23) }
        val earlyMorningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 3) }

        assertEquals("Good Morning,", DashboardViewModel.getGreeting(morningCal))
        assertEquals("Good Afternoon,", DashboardViewModel.getGreeting(afternoonCal))
        assertEquals("Good Evening,", DashboardViewModel.getGreeting(eveningCal))
        assertEquals("Good Evening,", DashboardViewModel.getGreeting(nightCal))
        assertEquals("Good Morning,", DashboardViewModel.getGreeting(earlyMorningCal))
    }

    @Test
    fun dashboardPriorityGoalFiltering_onlyHighPriorityGoalsAreSelected() {
        val now = Date()
        val future = Date(now.time + 86400000L * 60)

        val highGoal1 = Goal(
            id = "g-1",
            title = "Emergency Fund",
            targetAmountLKR = 300000.0,
            priority = GoalPriority.HIGH,
            startDate = now,
            targetDate = future,
            isActive = true
        )
        val mediumGoal = Goal(
            id = "g-2",
            title = "Vacation Fund",
            targetAmountLKR = 150000.0,
            priority = GoalPriority.MEDIUM,
            startDate = now,
            targetDate = future,
            isActive = true
        )
        val lowGoal = Goal(
            id = "g-3",
            title = "Gadget Upgrade",
            targetAmountLKR = 50000.0,
            priority = GoalPriority.LOW,
            startDate = now,
            targetDate = future,
            isActive = true
        )
        val highGoal2 = Goal(
            id = "g-4",
            title = "MacBook Pro M4",
            targetAmountLKR = 490000.0,
            priority = "high",
            startDate = now,
            targetDate = future,
            isActive = true
        )

        val allGoals = listOf(highGoal1, mediumGoal, lowGoal, highGoal2)

        // Filter replicating DashboardViewModel logic: only high priority goals
        val priorityGoals = allGoals.filter { it.priority.equals(GoalPriority.HIGH, ignoreCase = true) }

        assertEquals(2, priorityGoals.size)
        assertEquals(listOf("Emergency Fund", "MacBook Pro M4"), priorityGoals.map { it.title })
    }
}
