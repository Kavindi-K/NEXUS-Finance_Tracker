package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Contribution
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.ui.reports.ReportsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Unit tests verifying:
 * 1. If a goal is completed in a specific month, it ONLY displays in the report of the goal completed month.
 * 2. It does NOT display in current month (or any subsequent month) after being completed in a previous month.
 * 3. Ongoing (uncompleted) goals continue displaying in active months.
 */
class ReportsGoalCompletionTest {

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    @Test
    fun goalCompletedInPreviousMonth_onlyDisplaysInCompletedMonth_notInCurrentMonth() {
        val octCal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 15) }
        val novCal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15) }
        val sepCal = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 15) }

        val goal = Goal(
            id = "goal_macbook",
            title = "MacBook Pro M4",
            targetAmountLKR = 200000.0,
            openingSavingsLKR = 200000.0, // Reached target in October
            startDate = sepCal.time,
            targetDate = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 31) }.time,
            createdAt = octCal.time
        )

        val contributions = listOf(
            Contribution(
                id = "contrib_1",
                userId = "user_1",
                categoryId = "cat_transport",
                categoryName = "Monthly Savings Surplus",
                amountLKR = 15000.0,
                monthLabel = "October 2026",
                isAllocated = true,
                allocatedGoalId = "goal_macbook",
                allocatedGoalTitle = "MacBook Pro M4",
                createdAt = octCal.time
            )
        )

        // 1. Completion month resolution
        val completionMonth = ReportsViewModel.resolveGoalCompletionMonth(
            goal = goal,
            incomes = emptyList(),
            expenses = emptyList(),
            contributions = contributions,
            monthFormat = monthFormat
        )
        assertEquals("October 2026", completionMonth)

        // 2. Display check in October 2026 (Completed Month) -> MUST DISPLAY
        val octEndOfMonth = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 31, 23, 59, 59)
        }.time
        val displaysInOctober = ReportsViewModel.shouldDisplayGoalInMonth(
            goal = goal,
            monthLabel = "October 2026",
            endOfMonth = octEndOfMonth,
            completionMonth = completionMonth
        )
        assertTrue("Goal completed in October MUST display in October report", displaysInOctober)

        // 3. Display check in November 2026 (Current Month) -> MUST NOT DISPLAY
        val novEndOfMonth = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 30, 23, 59, 59)
        }.time
        val displaysInNovember = ReportsViewModel.shouldDisplayGoalInMonth(
            goal = goal,
            monthLabel = "November 2026",
            endOfMonth = novEndOfMonth,
            completionMonth = completionMonth
        )
        assertFalse("Goal completed in October MUST NOT display in November current month report", displaysInNovember)

        // 4. Display check in September 2026 (Before Completion) -> MUST NOT DISPLAY
        val sepEndOfMonth = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 30, 23, 59, 59)
        }.time
        val displaysInSeptember = ReportsViewModel.shouldDisplayGoalInMonth(
            goal = goal,
            monthLabel = "September 2026",
            endOfMonth = sepEndOfMonth,
            completionMonth = completionMonth
        )
        assertFalse("Goal completed in October MUST NOT display in September report", displaysInSeptember)
    }

    @Test
    fun ongoingGoal_notCompleted_displaysInCurrentMonth() {
        val sepCal = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 1) }
        val ongoingGoal = Goal(
            id = "goal_emergency",
            title = "Emergency Fund",
            targetAmountLKR = 500000.0,
            openingSavingsLKR = 100000.0, // Not completed yet
            startDate = sepCal.time,
            targetDate = Calendar.getInstance().apply { set(2027, Calendar.MAY, 1) }.time,
            createdAt = sepCal.time
        )

        val completionMonth = ReportsViewModel.resolveGoalCompletionMonth(
            goal = ongoingGoal,
            incomes = emptyList(),
            expenses = emptyList(),
            contributions = emptyList(),
            monthFormat = monthFormat
        )
        assertNull("Ongoing goal must not have a completion month", completionMonth)

        val novEndOfMonth = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 30, 23, 59, 59)
        }.time
        val displaysInNovember = ReportsViewModel.shouldDisplayGoalInMonth(
            goal = ongoingGoal,
            monthLabel = "November 2026",
            endOfMonth = novEndOfMonth,
            completionMonth = completionMonth
        )
        assertTrue("Ongoing goal must display in current month report", displaysInNovember)
    }

    @Test
    fun goalCompletedByTransactionProgression_identifiesCorrectMonth() {
        val sepStart = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0) }.time
        val octDate = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 20, 10, 0, 0) }.time

        val goal = Goal(
            id = "goal_travel",
            title = "Japan Trip",
            targetAmountLKR = 150000.0,
            openingSavingsLKR = 50000.0,
            startDate = sepStart,
            targetDate = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 31) }.time,
            createdAt = sepStart
        )

        val incomes = listOf(
            Income(
                id = "inc_1",
                userId = "user_1",
                source = IncomeSource.SALARY,
                amountLKR = 120000.0,
                status = IncomeStatus.CONFIRMED,
                dateReceived = octDate
            )
        )
        val expenses = listOf(
            Expense(
                id = "exp_1",
                userId = "user_1",
                categoryId = "cat_rent",
                amountLKR = 10000.0,
                date = octDate
            )
        )
        // Savings = 50,000 + 120,000 - 10,000 = 160,000 >= 150,000 (Crossed in October)

        val completionMonth = ReportsViewModel.resolveGoalCompletionMonth(
            goal = goal,
            incomes = incomes,
            expenses = expenses,
            contributions = emptyList(),
            monthFormat = monthFormat
        )
        assertEquals("October 2026", completionMonth)

        // Displays in October (completed month)
        val octEnd = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 31, 23, 59, 59) }.time
        assertTrue(ReportsViewModel.shouldDisplayGoalInMonth(goal, "October 2026", octEnd, completionMonth))

        // Does NOT display in November (current month)
        val novEnd = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 30, 23, 59, 59) }.time
        assertFalse(ReportsViewModel.shouldDisplayGoalInMonth(goal, "November 2026", novEnd, completionMonth))
    }
}

