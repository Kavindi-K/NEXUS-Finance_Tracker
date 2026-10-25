package com.nexus.financetracker.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.data.repository.RecurringItemRepository
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.GoalPriority
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.PaceStatus
import com.nexus.financetracker.domain.model.RecurringItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class BillAlert(
    val item: RecurringItem,
    val isDueToday: Boolean
)

data class IncomeAlert(
    val income: Income,
    val isExpectedToday: Boolean
)

data class GoalAlert(
    val goal: Goal,
    val title: String,
    val message: String,
    val type: GoalAlertType,
    val priority: String
)

enum class GoalAlertType {
    ACHIEVED, DEADLINE_SOON, PACE_BEHIND, MILESTONE
}

data class AlertsUiState(
    val isLoading: Boolean = true,
    val billAlerts: List<BillAlert> = emptyList(),
    val incomeAlerts: List<IncomeAlert> = emptyList(),
    val goalAlerts: List<GoalAlert> = emptyList(),
    val isProcessing: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val recurringItemRepository: RecurringItemRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val goalRepository: GoalRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        loadAlerts()
    }

    fun loadAlerts() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Please sign in to view alerts"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            combine(
                recurringItemRepository.getRecurringItems(userId),
                incomeRepository.getPendingIncomes(userId),
                incomeRepository.getIncomes(userId),
                expenseRepository.getExpenses(userId),
                goalRepository.getGoals(userId)
            ) { allRecurring, allPendingIncomes, allIncomes, allExpenses, allGoals ->
                val activeItems = allRecurring.filter { it.isActive }
                val now = Calendar.getInstance()

                val todayStart = now.cloneCalendar().apply { setStartOfDay() }.time
                val todayEnd = now.cloneCalendar().apply { setEndOfDay() }.time

                val tomorrowCal = now.cloneCalendar().apply { add(Calendar.DAY_OF_YEAR, 1) }
                val tomorrowStart = tomorrowCal.cloneCalendar().apply { setStartOfDay() }.time
                val tomorrowEnd = tomorrowCal.cloneCalendar().apply { setEndOfDay() }.time

                // 1. Bill Reminders (FR23)
                val billList = mutableListOf<BillAlert>()
                for (item in activeItems) {
                    val due = item.nextDueDate
                    when {
                        due in todayStart..todayEnd -> billList.add(BillAlert(item, isDueToday = true))
                        due in tomorrowStart..tomorrowEnd -> billList.add(BillAlert(item, isDueToday = false))
                    }
                }

                // 2. Pending Income Arrival Alerts (FR8)
                val incomeList = mutableListOf<IncomeAlert>()
                for (income in allPendingIncomes) {
                    val expected = income.expectedDate ?: continue
                    when {
                        expected in todayStart..todayEnd -> incomeList.add(IncomeAlert(income, isExpectedToday = true))
                        expected in tomorrowStart..tomorrowEnd -> incomeList.add(IncomeAlert(income, isExpectedToday = false))
                    }
                }

                // 3. Goal Alerts (FR15–FR17)
                val goalList = mutableListOf<GoalAlert>()
                val activeGoals = allGoals.filter { it.isActive }
                for (goal in activeGoals) {
                    val calc = GoalCalculation.calculate(goal, allIncomes, allExpenses, now.time)
                    val priorityName = GoalPriority.getDisplayName(goal.priority)

                    when {
                        calc.isCompleted -> {
                            goalList.add(
                                GoalAlert(
                                    goal = goal,
                                    title = "🎉 Goal Achieved: ${goal.title}!",
                                    message = "Congratulations! You've reached 100% of your target (LKR %,.0f)!".format(goal.targetAmountLKR),
                                    type = GoalAlertType.ACHIEVED,
                                    priority = priorityName
                                )
                            )
                        }
                        calc.daysRemaining in 1L..7L -> {
                            val daysText = if (calc.daysRemaining == 1L) "1 day" else "${calc.daysRemaining} days"
                            goalList.add(
                                GoalAlert(
                                    goal = goal,
                                    title = "🎯 $priorityName Priority: ${goal.title}",
                                    message = "You have $daysText left to achieve your target (LKR %,.0f).".format(goal.targetAmountLKR),
                                    type = GoalAlertType.DEADLINE_SOON,
                                    priority = priorityName
                                )
                            )
                        }
                        (calc.paceStatus == PaceStatus.BEHIND_PACE || calc.paceStatus == PaceStatus.OVERDUE) && calc.requiredMonthlySavingsLKR > 0 -> {
                            goalList.add(
                                GoalAlert(
                                    goal = goal,
                                    title = "⚠️ ${goal.title}: Target Pace Alert",
                                    message = "You need LKR %,.0f saved this month to stay on track for your target date.".format(calc.requiredMonthlySavingsLKR),
                                    type = GoalAlertType.PACE_BEHIND,
                                    priority = priorityName
                                )
                            )
                        }
                        calc.progressPercentage >= 25.0 -> {
                            val milestone = when {
                                calc.progressPercentage >= 75.0 -> 75
                                calc.progressPercentage >= 50.0 -> 50
                                else -> 25
                            }
                            goalList.add(
                                GoalAlert(
                                    goal = goal,
                                    title = "🏆 Milestone: ${goal.title}",
                                    message = "Congratulations! You've reached $milestone% of your ${goal.title} goal!",
                                    type = GoalAlertType.MILESTONE,
                                    priority = priorityName
                                )
                            )
                        }
                    }
                }

                Triple(
                    billList.sortedBy { it.item.nextDueDate },
                    incomeList.sortedBy { it.income.expectedDate },
                    goalList
                )
            }
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Failed to load alerts"
                    )
                }
                .collect { (bills, incomes, goals) ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        billAlerts = bills,
                        incomeAlerts = incomes,
                        goalAlerts = goals,
                        errorMessage = null
                    )
                }
        }
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun Calendar.cloneCalendar(): Calendar = clone() as Calendar

    private fun Calendar.setStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.setEndOfDay() {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
}
