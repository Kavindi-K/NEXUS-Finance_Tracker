package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.GoalPriority
import com.nexus.financetracker.ui.goal.GoalSortOrder
import com.nexus.financetracker.ui.goal.GoalStatusFilter
import com.nexus.financetracker.ui.goal.GoalUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests verifying Goal priority breakdown and Status Filtering (All, Ongoing, Achieved, Couldn't Achieved).
 */
class GoalStatusFilterTest {

    @Test
    fun goalUiState_priorityBreakdownCalculatesCorrectly() {
        val goals = listOf(
            Goal(id = "1", title = "MacBook", priority = GoalPriority.HIGH),
            Goal(id = "2", title = "Emergency Fund", priority = GoalPriority.HIGH),
            Goal(id = "3", title = "Bike", priority = GoalPriority.MEDIUM),
            Goal(id = "4", title = "Headphones", priority = "custom_unknown"), // Defaults to medium
            Goal(id = "5", title = "Book", priority = GoalPriority.LOW)
        )

        val state = GoalUiState(goals = goals)

        assertEquals(2, state.highPriorityCount)
        assertEquals(2, state.mediumPriorityCount)
        assertEquals(1, state.lowPriorityCount)
        assertEquals(5, state.goals.size)
    }

    @Test
    fun goalUiState_filterStatusSegregatesOngoingAchievedAndOverdueCorrectly() {
        val now = Date()
        val futureDate = Calendar.getInstance().apply {
            time = now
            add(Calendar.MONTH, 6)
        }.time
        val pastDate = Calendar.getInstance().apply {
            time = now
            add(Calendar.MONTH, -2)
        }.time

        // 1. Achieved goal: reached target amount
        val achievedGoal = Goal(
            id = "goal-achieved",
            title = "Completed Goal",
            targetAmountLKR = 100000.0,
            openingSavingsLKR = 100000.0,
            targetDate = futureDate
        )
        val calcAchieved = GoalCalculation.calculate(
            goal = achievedGoal,
            incomes = emptyList(),
            expenses = emptyList(),
            currentDate = now
        )

        // 2. Overdue goal: targetDate has passed and remaining > 0 (Couldn't Achieved)
        val overdueGoal = Goal(
            id = "goal-overdue",
            title = "Missed Goal",
            targetAmountLKR = 100000.0,
            openingSavingsLKR = 20000.0,
            targetDate = pastDate
        )
        val calcOverdue = GoalCalculation.calculate(
            goal = overdueGoal,
            incomes = emptyList(),
            expenses = emptyList(),
            currentDate = now
        )

        // 3. Ongoing goal: deadline is future and target not yet reached
        val ongoingGoal = Goal(
            id = "goal-ongoing",
            title = "Active Goal",
            targetAmountLKR = 200000.0,
            openingSavingsLKR = 50000.0,
            targetDate = futureDate
        )
        val calcOngoing = GoalCalculation.calculate(
            goal = ongoingGoal,
            incomes = emptyList(),
            expenses = emptyList(),
            currentDate = now
        )

        val allGoals = listOf(achievedGoal, overdueGoal, ongoingGoal)
        val calculations = mapOf(
            achievedGoal.id to calcAchieved,
            overdueGoal.id to calcOverdue,
            ongoingGoal.id to calcOngoing
        )

        val stateAll = GoalUiState(goals = allGoals, calculations = calculations, selectedFilter = GoalStatusFilter.ALL)
        assertEquals(3, stateAll.filteredGoals.size)
        assertEquals(3, stateAll.getCountForFilter(GoalStatusFilter.ALL))

        val stateOngoing = stateAll.copy(selectedFilter = GoalStatusFilter.ONGOING)
        assertEquals(1, stateOngoing.filteredGoals.size)
        assertEquals("Active Goal", stateOngoing.filteredGoals.first().title)
        assertEquals(1, stateAll.getCountForFilter(GoalStatusFilter.ONGOING))

        val stateAchieved = stateAll.copy(selectedFilter = GoalStatusFilter.ACHIEVED)
        assertEquals(1, stateAchieved.filteredGoals.size)
        assertEquals("Completed Goal", stateAchieved.filteredGoals.first().title)
        assertEquals(1, stateAll.getCountForFilter(GoalStatusFilter.ACHIEVED))

        val stateCouldNotAchieve = stateAll.copy(selectedFilter = GoalStatusFilter.COULD_NOT_ACHIEVE)
        assertEquals(1, stateCouldNotAchieve.filteredGoals.size)
        assertEquals("Missed Goal", stateCouldNotAchieve.filteredGoals.first().title)
        assertEquals(1, stateAll.getCountForFilter(GoalStatusFilter.COULD_NOT_ACHIEVE))
    }

    @Test
    fun goalUiState_selectedPriorityFiltersCorrectly() {
        val goals = listOf(
            Goal(id = "1", title = "MacBook", priority = GoalPriority.HIGH),
            Goal(id = "2", title = "Emergency Fund", priority = GoalPriority.HIGH),
            Goal(id = "3", title = "Bike", priority = GoalPriority.MEDIUM),
            Goal(id = "4", title = "Book", priority = GoalPriority.LOW)
        )

        val stateHigh = GoalUiState(goals = goals, selectedPriority = GoalPriority.HIGH)
        assertEquals(2, stateHigh.filteredGoals.size)
        assertEquals(listOf("MacBook", "Emergency Fund"), stateHigh.filteredGoals.map { it.title })
        assertEquals(2, stateHigh.getCountForFilter(GoalStatusFilter.ALL))

        val stateMed = GoalUiState(goals = goals, selectedPriority = GoalPriority.MEDIUM)
        assertEquals(1, stateMed.filteredGoals.size)
        assertEquals("Bike", stateMed.filteredGoals.first().title)

        val stateLow = GoalUiState(goals = goals, selectedPriority = GoalPriority.LOW)
        assertEquals(1, stateLow.filteredGoals.size)
        assertEquals("Book", stateLow.filteredGoals.first().title)

        val stateAll = GoalUiState(goals = goals, selectedPriority = null)
        assertEquals(4, stateAll.filteredGoals.size)
    }

    @Test
    fun goalUiState_sortOrder_byDateDescendingAndAscending() {
        val now = System.currentTimeMillis()
        val d1 = Date(now + 10_000_000L) // earliest deadline
        val d2 = Date(now + 20_000_000L) // mid deadline
        val d3 = Date(now + 30_000_000L) // latest deadline

        val goals = listOf(
            Goal(id = "1", title = "Goal Early", targetDate = d1),
            Goal(id = "2", title = "Goal Latest", targetDate = d3),
            Goal(id = "3", title = "Goal Mid", targetDate = d2)
        )

        // DATE_DESC: latest/furthest deadline first
        val stateDesc = GoalUiState(goals = goals, selectedSortOrder = GoalSortOrder.DATE_DESC)
        assertEquals(listOf("Goal Latest", "Goal Mid", "Goal Early"), stateDesc.filteredGoals.map { it.title })

        // DATE_ASC: earliest/nearest deadline first
        val stateAsc = GoalUiState(goals = goals, selectedSortOrder = GoalSortOrder.DATE_ASC)
        assertEquals(listOf("Goal Early", "Goal Mid", "Goal Latest"), stateAsc.filteredGoals.map { it.title })
    }

    @Test
    fun goalUiState_sortOrder_byPriorityDescendingAndAscending() {
        val now = System.currentTimeMillis()
        val d1 = Date(now + 10_000_000L)
        val d2 = Date(now + 20_000_000L)

        val goals = listOf(
            Goal(id = "1", title = "Low Goal", priority = GoalPriority.LOW, targetDate = d1),
            Goal(id = "2", title = "High Goal 1", priority = GoalPriority.HIGH, targetDate = d2),
            Goal(id = "3", title = "Medium Goal", priority = GoalPriority.MEDIUM, targetDate = d1),
            Goal(id = "4", title = "High Goal 2", priority = GoalPriority.HIGH, targetDate = d1)
        )

        // PRIORITY_DESC: High -> Medium -> Low (with secondary sort by targetDate)
        val stateDesc = GoalUiState(goals = goals, selectedSortOrder = GoalSortOrder.PRIORITY_DESC)
        assertEquals(listOf("High Goal 2", "High Goal 1", "Medium Goal", "Low Goal"), stateDesc.filteredGoals.map { it.title })

        // PRIORITY_ASC: Low -> Medium -> High (with secondary sort by targetDate)
        val stateAsc = GoalUiState(goals = goals, selectedSortOrder = GoalSortOrder.PRIORITY_ASC)
        assertEquals(listOf("Low Goal", "Medium Goal", "High Goal 2", "High Goal 1"), stateAsc.filteredGoals.map { it.title })
    }
}
