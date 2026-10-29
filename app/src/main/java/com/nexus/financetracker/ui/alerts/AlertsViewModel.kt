package com.nexus.financetracker.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.CategoryRepository
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.data.repository.RecurringItemRepository
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseClassification
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

data class SpendingLeakAlert(
    val id: String,
    val categoryName: String,
    val amountLKR: Double,
    val title: String,
    val message: String,
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
    val leakAlerts: List<SpendingLeakAlert> = emptyList(),
    val quickLogAlert: QuickLogAlert? = null,
    val quickLogIncomeAlert: QuickLogIncomeAlert? = null,
    val hasUnreadAlerts: Boolean = false,
    val isProcessing: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

private data class FinanceContext(
    val incomes: List<Income>,
    val expenses: List<Expense>,
    val categories: List<Category>
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val recurringItemRepository: RecurringItemRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
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
                expenseRepository.getExpenses(userId),
                categoryRepository.getCategories(userId)
            ) { incomes, expenses, categories ->
                FinanceContext(incomes, expenses, categories)
            }

            val readStateFlow = combine(
                alertsReadManager.readIds,
                alertsReadManager.seenIds,
                alertsReadManager.deletedIds
            ) { readIds, seenIds, deletedIds ->
                AlertMetaState(readIds, seenIds, deletedIds)
            }

            combine(
                recurringItemRepository.getRecurringItems(userId),
                incomeRepository.getPendingIncomes(userId),
                goalRepository.getGoals(userId),
                financeFlow,
                readStateFlow
            ) { allRecurring, allPendingIncomes, allGoals, finance, metaState ->
                val (allIncomes, allExpenses, allCategories) = finance
                val (readIds, seenIds, deletedIds) = metaState
                val activeItems = allRecurring.filter { it.isActive }
                val now = Calendar.getInstance()
                val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now.time)
                val weekStr = SimpleDateFormat("yyyy_'w'ww", Locale.getDefault()).format(now.time)

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
                val userActiveGoals = allGoals.filter { it.isActive }
                for (goal in userActiveGoals) {
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
                                    message = "Congratulations! You've reached 100% of your target (${"LKR %,.0f".format(goal.targetAmountLKR)})!",
                                    type = GoalAlertType.ACHIEVED,
                                    priority = priorityName,
                                    isUnread = !readIds.contains(alertId)
                                )
                            )
                        }
                        calc.daysRemaining in 1L..7L -> {
                            val alertId = "goal_deadline_${goal.id}"
                            val dayText = if (calc.daysRemaining == 1L) "1 day" else "${calc.daysRemaining} days"
                            goalList.add(
                                GoalAlert(
                                    id = alertId,
                                    goal = goal,
                                    title = "⏳ Deadline Soon: ${goal.title}",
                                    message = "Reminder: You have $dayText left to reach your ${goal.title} goal!",
                                    type = GoalAlertType.DEADLINE_SOON,
                                    priority = priorityName,
                                    isUnread = !readIds.contains(alertId)
                                )
                            )
                        }
                        calc.paceStatus == PaceStatus.BEHIND_PACE || calc.paceStatus == PaceStatus.OVERDUE -> {
                            val alertId = "goal_pace_${goal.id}"
                            val needed = calc.requiredMonthlySavingsLKR
                            val paceMessage = if (needed > 0) {
                                "You need LKR %,.0f saved this month to stay on track for your target date.".format(needed)
                            } else {
                                "Your savings are currently behind schedule. Review your contributions to stay on track."
                            }
                            goalList.add(
                                GoalAlert(
                                    id = alertId,
                                    goal = goal,
                                    title = "⚠️ ${goal.title}: Target Pace Alert",
                                    message = paceMessage,
                                    type = GoalAlertType.PACE_BEHIND,
                                    priority = priorityName,
                                    isUnread = !readIds.contains(alertId)
                                )
                            )
                        }
                    }

                    // Milestone Celebrations (25%, 50%, 75%)
                    val currentPercent = calc.progressPercentage.toInt()
                    val milestone = when {
                        currentPercent in 75..99 -> 75
                        currentPercent in 50..74 -> 50
                        currentPercent in 25..49 -> 25
                        else -> null
                    }
                    if (milestone != null) {
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

                // 4. Discretionary Spending / Category Leak Alerts (FR18–FR19)
                val weekStart = now.cloneCalendar().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    setStartOfDay()
                }.time

                val weekExpenses = allExpenses.filter { !it.date.before(weekStart) }
                val discretionaryExpenses = weekExpenses.filter {
                    it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true)
                }
                val totalWeeklyExpenses = weekExpenses.sumOf { it.amountLKR }
                val categoryMap = allCategories.associate { it.id to it.name }
                val leakList = mutableListOf<SpendingLeakAlert>()

                val groupedDiscretionary = discretionaryExpenses.groupBy { expense ->
                    categoryMap[expense.categoryId]?.takeIf { it.isNotBlank() }
                        ?: expense.categoryId.takeIf { it.isNotBlank() }
                        ?: "Dining & Coffee"
                }.mapValues { (_, list) -> list.sumOf { it.amountLKR } }

                for ((catName, sum) in groupedDiscretionary) {
                    val isLeak = sum >= 15000.0 || (totalWeeklyExpenses > 0 && (sum / totalWeeklyExpenses) >= 0.40 && sum >= 5000.0)
                    if (isLeak) {
                        val safeCat = catName.lowercase().replace(" ", "_")
                        val alertId = "leak_${safeCat}_$weekStr"
                        val isUnread = !readIds.contains(alertId)
                        val formattedCat = catName.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }
                        leakList.add(
                            SpendingLeakAlert(
                                id = alertId,
                                categoryName = formattedCat,
                                amountLKR = sum,
                                title = "⚠️ Notice: High Spending on $formattedCat",
                                message = "Notice: You've spent LKR %,.0f on %s this week.".format(sum, formattedCat.lowercase()),
                                isUnread = isUnread
                            )
                        )
                    }
                }

                // 5. Daily Quick-Log Expense Reminder (FR9)
                val quickLogId = "quick_log_$todayStr"
                val quickLogAlert = QuickLogAlert(
                    id = quickLogId,
                    title = "Did you spend any cash today?",
                    message = "3 taps is all it takes to keep your tracker accurate.",
                    isUnread = !readIds.contains(quickLogId)
                )

                // 6. Daily Quick-Log Income Reminder (delivered at 6:00 PM only)
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

                // Filter out user-deleted alerts
                val activeBills = billList.filter { !deletedIds.contains(it.id) }
                val activeIncomes = incomeList.filter { !deletedIds.contains(it.id) }
                val activeGoalAlerts = goalList.filter { !deletedIds.contains(it.id) }
                val activeLeaks = leakList.filter { !deletedIds.contains(it.id) }
                val activeQuickLog = quickLogAlert?.takeIf { !deletedIds.contains(it.id) }
                val activeQuickLogIncome = quickLogIncomeAlert?.takeIf { !deletedIds.contains(it.id) }

                // Compute whether any alert has not yet been seen by visiting the Alerts screen
                val allCurrentIds = activeBills.map { it.id } +
                    activeIncomes.map { it.id } +
                    activeGoalAlerts.map { it.id } +
                    activeLeaks.map { it.id } +
                    listOfNotNull(activeQuickLog?.id, activeQuickLogIncome?.id)

                val hasUnseenAlerts = allCurrentIds.any { !seenIds.contains(it) }

                AlertsData(
                    bills = activeBills.sortedBy { it.item.nextDueDate },
                    incomes = activeIncomes.sortedBy { it.income.expectedDate },
                    goals = activeGoalAlerts,
                    leaks = activeLeaks.sortedByDescending { it.amountLKR },
                    quickLog = activeQuickLog,
                    quickLogIncome = activeQuickLogIncome,
                    hasUnseen = hasUnseenAlerts
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
                        leakAlerts = data.leaks,
                        quickLogAlert = data.quickLog,
                        quickLogIncomeAlert = data.quickLogIncome,
                        hasUnreadAlerts = data.hasUnseen,
                        errorMessage = null
                    )
                }
        }
    }

    /**
     * Marks all currently active alerts as seen when the user views the Alerts screen.
     * This immediately clears the red badge dot on the bottom navigation Alerts icon.
     */
    fun markAlertsAsSeen() {
        val state = _uiState.value
        val allIds = state.billAlerts.map { it.id } +
            state.incomeAlerts.map { it.id } +
            state.goalAlerts.map { it.id } +
            state.leakAlerts.map { it.id } +
            listOfNotNull(state.quickLogAlert?.id, state.quickLogIncomeAlert?.id)
        alertsReadManager.markAllAsSeen(allIds)
    }

    fun markAsRead(alertId: String) {
        alertsReadManager.markAsRead(alertId)
    }

    fun markAllAsRead() {
        val allIds = mutableListOf<String>()
        _uiState.value.billAlerts.forEach { allIds.add(it.id) }
        _uiState.value.incomeAlerts.forEach { allIds.add(it.id) }
        _uiState.value.goalAlerts.forEach { allIds.add(it.id) }
        _uiState.value.leakAlerts.forEach { allIds.add(it.id) }
        _uiState.value.quickLogAlert?.let { allIds.add(it.id) }
        _uiState.value.quickLogIncomeAlert?.let { allIds.add(it.id) }
        alertsReadManager.markAllAsRead(allIds)
    }

    fun deleteAlerts(alertIds: Collection<String>) {
        alertsReadManager.deleteAlerts(alertIds)
    }

    fun deleteAlert(alertId: String) {
        alertsReadManager.deleteAlert(alertId)
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

private data class AlertMetaState(
    val readIds: Set<String>,
    val seenIds: Set<String>,
    val deletedIds: Set<String>
)

private data class AlertsData(
    val bills: List<BillAlert>,
    val incomes: List<IncomeAlert>,
    val goals: List<GoalAlert>,
    val leaks: List<SpendingLeakAlert>,
    val quickLog: QuickLogAlert?,
    val quickLogIncome: QuickLogIncomeAlert?,
    val hasUnseen: Boolean
)
