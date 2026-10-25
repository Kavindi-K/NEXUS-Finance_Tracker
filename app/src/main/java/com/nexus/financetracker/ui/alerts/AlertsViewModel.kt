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
import com.nexus.financetracker.notification.AlertsReadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BillAlert(
    val id: String,
    val item: RecurringItem,
    val isDueToday: Boolean,
    val isUnread: Boolean
)

data class IncomeAlert(
    val id: String,
    val income: Income,
    val isExpectedToday: Boolean,
    val isUnread: Boolean
)

data class GoalAlert(
    val id: String,
    val goal: Goal,
    val title: String,
    val message: String,
    val type: GoalAlertType,
    val priority: String,
    val isUnread: Boolean
)

data class QuickLogAlert(
    val id: String,
    val title: String,
    val message: String,
    val isUnread: Boolean
)

data class QuickLogIncomeAlert(
    val id: String,
    val title: String,
    val message: String,
    val isUnread: Boolean
)

enum class GoalAlertType {
    ACHIEVED, DEADLINE_SOON, PACE_BEHIND, MILESTONE
}

data class AlertsUiState(
    val isLoading: Boolean = true,
    val billAlerts: List<BillAlert> = emptyList(),
    val incomeAlerts: List<IncomeAlert> = emptyList(),
    val goalAlerts: List<GoalAlert> = emptyList(),
    val quickLogAlert: QuickLogAlert? = null,
    val quickLogIncomeAlert: QuickLogIncomeAlert? = null,
    val hasUnreadAlerts: Boolean = false,
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
    private val alertsReadManager: AlertsReadManager,
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

            val financeFlow = combine(
                incomeRepository.getIncomes(userId),
                expenseRepository.getExpenses(userId)
            ) { incomes, expenses -> Pair(incomes, expenses) }

            combine(
                recurringItemRepository.getRecurringItems(userId),
                incomeRepository.getPendingIncomes(userId),
                goalRepository.getGoals(userId),
                financeFlow,
                alertsReadManager.readIds
            ) { allRecurring, allPendingIncomes, allGoals, finance, readIds ->
                val (allIncomes, allExpenses) = finance
                val activeItems = allRecurring.filter { it.isActive }
                val now = Calendar.getInstance()
                val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now.time)

                val todayStart = now.cloneCalendar().apply { setStartOfDay() }.time
                val todayEnd = now.cloneCalendar().apply { setEndOfDay() }.time

                val tomorrowCal = now.cloneCalendar().apply { add(Calendar.DAY_OF_YEAR, 1) }
                val tomorrowStart = tomorrowCal.cloneCalendar().apply { setStartOfDay() }.time
                val tomorrowEnd = tomorrowCal.cloneCalendar().apply { setEndOfDay() }.time

                // 1. Bill Reminders (FR23)
                val billList = mutableListOf<BillAlert>()
                for (item in activeItems) {
                    val due = item.nextDueDate
                    val alertId = "bill_${item.id}"
                    val isUnread = !readIds.contains(alertId)
                    when {
                        due in todayStart..todayEnd -> billList.add(BillAlert(alertId, item, isDueToday = true, isUnread = isUnread))
                        due in tomorrowStart..tomorrowEnd -> billList.add(BillAlert(alertId, item, isDueToday = false, isUnread = isUnread))
                    }
                }

                // 2. Pending Income Arrival Alerts (FR8)
                val incomeList = mutableListOf<IncomeAlert>()
                for (income in allPendingIncomes) {
                    val expected = income.expectedDate ?: continue
                    val alertId = "income_${income.id}"
                    val isUnread = !readIds.contains(alertId)
                    when {
                        expected in todayStart..todayEnd -> incomeList.add(IncomeAlert(alertId, income, isExpectedToday = true, isUnread = isUnread))
                        expected in tomorrowStart..tomorrowEnd -> incomeList.add(IncomeAlert(alertId, income, isExpectedToday = false, isUnread = isUnread))
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
                            val alertId = "goal_achieved_${goal.id}"
                            goalList.add(
                                GoalAlert(
                                    id = alertId,
                                    goal = goal,
                                    title = "🎉 Goal Achieved: ${goal.title}!",
                                    message = "Congratulations! You've reached 100% of your target (LKR %,.0f)!".format(goal.targetAmountLKR),
                                    type = GoalAlertType.ACHIEVED,
                                    priority = priorityName,
                                    isUnread = !readIds.contains(alertId)
                                )
                            )
                        }
                        calc.daysRemaining in 1L..7L -> {
                            val alertId = "goal_deadline_${goal.id}"
                            val daysText = if (calc.daysRemaining == 1L) "1 day" else "${calc.daysRemaining} days"
                            goalList.add(
                                GoalAlert(
                                    id = alertId,
                                    goal = goal,
                                    title = "🎯 $priorityName Priority: ${goal.title}",
                                    message = "You have $daysText left to achieve your target (LKR %,.0f).".format(goal.targetAmountLKR),
                                    type = GoalAlertType.DEADLINE_SOON,
                                    priority = priorityName,
                                    isUnread = !readIds.contains(alertId)
                                )
                            )
                        }
                        (calc.paceStatus == PaceStatus.BEHIND_PACE || calc.paceStatus == PaceStatus.OVERDUE) && calc.requiredMonthlySavingsLKR > 0 -> {
                            val alertId = "goal_pace_${goal.id}"
                            goalList.add(
                                GoalAlert(
                                    id = alertId,
                                    goal = goal,
                                    title = "⚠️ ${goal.title}: Target Pace Alert",
                                    message = "You need LKR %,.0f saved this month to stay on track for your target date.".format(calc.requiredMonthlySavingsLKR),
                                    type = GoalAlertType.PACE_BEHIND,
                                    priority = priorityName,
                                    isUnread = !readIds.contains(alertId)
                                )
                            )
                        }
                        calc.progressPercentage >= 25.0 -> {
                            val milestone = when {
                                calc.progressPercentage >= 75.0 -> 75
                                calc.progressPercentage >= 50.0 -> 50
                                else -> 25
                            }
                            val alertId = "goal_milestone_${milestone}_${goal.id}"
                            goalList.add(
                                GoalAlert(
                                    id = alertId,
                                    goal = goal,
                                    title = "🏆 Milestone: ${goal.title}",
                                    message = "Congratulations! You've reached $milestone% of your ${goal.title} goal!",
                                    type = GoalAlertType.MILESTONE,
                                    priority = priorityName,
                                    isUnread = !readIds.contains(alertId)
                                )
                            )
                        }
                    }
                }

                // 4. Daily Quick-Log Expense Reminder (FR9)
                val quickLogId = "quick_log_$todayStr"
                val quickLogAlert = QuickLogAlert(
                    id = quickLogId,
                    title = "Did you spend any cash today?",
                    message = "3 taps is all it takes to keep your tracker accurate.",
                    isUnread = !readIds.contains(quickLogId)
                )

                // 5. Daily Quick-Log Income Reminder (delivered at 6:00 PM only)
                val currentHour = now.get(Calendar.HOUR_OF_DAY)
                val quickLogIncomeAlert = if (currentHour >= 18) {
                    val quickLogIncomeId = "quick_log_income_$todayStr"
                    QuickLogIncomeAlert(
                        id = quickLogIncomeId,
                        title = "Did you receive any income today?",
                        message = "Don't forget to log freelance, side gigs, or cash earnings to keep your cash flow accurate.",
                        isUnread = !readIds.contains(quickLogIncomeId)
                    )
                } else null

                val anyUnread = billList.any { it.isUnread } ||
                    incomeList.any { it.isUnread } ||
                    goalList.any { it.isUnread } ||
                    quickLogAlert.isUnread ||
                    (quickLogIncomeAlert?.isUnread == true)

                AlertsData(
                    bills = billList.sortedBy { it.item.nextDueDate },
                    incomes = incomeList.sortedBy { it.income.expectedDate },
                    goals = goalList,
                    quickLog = quickLogAlert,
                    quickLogIncome = quickLogIncomeAlert,
                    hasUnread = anyUnread
                )
            }
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Failed to load alerts"
                    )
                }
                .collect { data ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        billAlerts = data.bills,
                        incomeAlerts = data.incomes,
                        goalAlerts = data.goals,
                        quickLogAlert = data.quickLog,
                        quickLogIncomeAlert = data.quickLogIncome,
                        hasUnreadAlerts = data.hasUnread,
                        errorMessage = null
                    )
                }
        }
    }

    fun markAsRead(alertId: String) {
        alertsReadManager.markAsRead(alertId)
    }

    fun markAllAsRead() {
        val allIds = mutableListOf<String>()
        _uiState.value.billAlerts.forEach { allIds.add(it.id) }
        _uiState.value.incomeAlerts.forEach { allIds.add(it.id) }
        _uiState.value.goalAlerts.forEach { allIds.add(it.id) }
        _uiState.value.quickLogAlert?.let { allIds.add(it.id) }
        _uiState.value.quickLogIncomeAlert?.let { allIds.add(it.id) }
        alertsReadManager.markAllAsRead(allIds)
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

private data class AlertsData(
    val bills: List<BillAlert>,
    val incomes: List<IncomeAlert>,
    val goals: List<GoalAlert>,
    val quickLog: QuickLogAlert?,
    val quickLogIncome: QuickLogIncomeAlert?,
    val hasUnread: Boolean
)
