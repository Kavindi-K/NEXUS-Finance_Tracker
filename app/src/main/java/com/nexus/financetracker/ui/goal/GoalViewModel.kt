package com.nexus.financetracker.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.data.util.NetworkMonitor
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.GoalPriority
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

enum class GoalStatusFilter {
    ALL,
    ONGOING,
    ACHIEVED,
    COULD_NOT_ACHIEVE
}

/**
 * ViewModel for Savings Goals screens (List, Add, Edit).
 * Implements MVVM pattern, input validation, and Firestore repository persistence.
 * Re-calculates estimated savings and required monthly rates on every transaction update (FR15).
 */
@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val auth: FirebaseAuth,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(AddEditGoalState())
    val formState: StateFlow<AddEditGoalState> = _formState.asStateFlow()

    init {
        loadGoals()
        observeNetworkStatus()
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    fun onFilterSelected(filter: GoalStatusFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun onPrioritySelected(priority: String?) {
        _uiState.value = _uiState.value.copy(selectedPriority = priority)
    }

    fun loadGoals() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            combine(
                goalRepository.getGoals(userId),
                incomeRepository.getIncomes(userId).catch { emit(emptyList()) },
                expenseRepository.getExpenses(userId).catch { emit(emptyList()) }
            ) { goals, incomes, expenses ->
                val calculations = goals.associate { goal ->
                    goal.id to GoalCalculation.calculate(
                        goal = goal,
                        incomes = incomes,
                        expenses = expenses
                    )
                }
                Triple(goals, calculations, null as String?)
            }.catch { e ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.localizedMessage ?: "Failed to load goals",
                    isLoading = false
                )
            }.collect { (goals, calculations, error) ->
                _uiState.value = _uiState.value.copy(
                    goals = goals,
                    calculations = calculations,
                    isLoading = false,
                    errorMessage = error
                )
            }
        }
    }

    /**
     * Initializes the form for adding a new goal or editing an existing goal.
     */
    fun initForm(goalId: String? = null) {
        if (goalId.isNullOrEmpty()) {
            val now = System.currentTimeMillis()
            // Default deadline: 12 months from today (matches Kavindu's MacBook goal scenario)
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, 12)
            _formState.value = AddEditGoalState(
                startDate = now,
                targetDate = cal.timeInMillis,
                priority = GoalPriority.MEDIUM,
                isEditing = false,
                existingGoal = null
            )
        } else {
            val cachedGoal = _uiState.value.goals.find { it.id == goalId }
            if (cachedGoal != null) {
                populateForm(cachedGoal)
            } else {
                val userId = auth.currentUser?.uid ?: return
                _formState.value = _formState.value.copy(isSaving = true)
                viewModelScope.launch {
                    val goal = goalRepository.getGoalById(userId, goalId)
                    if (goal != null) {
                        populateForm(goal)
                    } else {
                        _formState.value = _formState.value.copy(
                            isSaving = false,
                            generalError = "Goal not found"
                        )
                    }
                }
            }
        }
    }

    private fun populateForm(goal: Goal) {
        val targetStr = if (goal.targetAmountLKR % 1.0 == 0.0) {
            goal.targetAmountLKR.toLong().toString()
        } else {
            goal.targetAmountLKR.toString()
        }
        val savingsStr = if (goal.openingSavingsLKR % 1.0 == 0.0) {
            goal.openingSavingsLKR.toLong().toString()
        } else {
            goal.openingSavingsLKR.toString()
        }

        _formState.value = AddEditGoalState(
            title = goal.title,
            targetAmount = targetStr,
            openingSavings = savingsStr,
            startDate = goal.startDate.time,
            targetDate = goal.targetDate.time,
            priority = goal.priority,
            isEditing = true,
            existingGoal = goal,
            isSaving = false,
            generalError = null
        )
    }

    fun onPriorityChanged(value: String) {
        _formState.value = _formState.value.copy(
            priority = value.lowercase(),
            generalError = null
        )
    }

    fun onTitleChanged(value: String) {
        _formState.value = _formState.value.copy(
            title = value,
            titleError = null,
            generalError = null
        )
    }

    fun onTargetAmountChanged(value: String) {
        _formState.value = _formState.value.copy(
            targetAmount = value,
            targetAmountError = null,
            generalError = null
        )
    }

    fun onOpeningSavingsChanged(value: String) {
        _formState.value = _formState.value.copy(
            openingSavings = value,
            openingSavingsError = null,
            generalError = null
        )
    }

    fun onStartDateSelected(dateMillis: Long) {
        _formState.value = _formState.value.copy(
            startDate = dateMillis,
            dateError = null,
            generalError = null
        )
    }

    fun onTargetDateSelected(dateMillis: Long) {
        _formState.value = _formState.value.copy(
            targetDate = dateMillis,
            dateError = null,
            generalError = null
        )
    }

    fun clearGeneralError() {
        _formState.value = _formState.value.copy(generalError = null)
    }

    /**
     * Validates input and persists goal to Firestore.
     */
    fun saveGoal(onSuccess: () -> Unit) {
        val current = _formState.value
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _formState.value = current.copy(generalError = "User session expired. Please sign in again.")
            return
        }

        val trimmedTitle = current.title.trim()
        val targetAmount = current.targetAmount.trim().toDoubleOrNull()
        val openingSavings = if (current.openingSavings.trim().isEmpty()) {
            0.0
        } else {
            current.openingSavings.trim().toDoubleOrNull()
        }

        var hasError = false
        var titleErr: String? = null
        var targetErr: String? = null
        var savingsErr: String? = null
        var dateErr: String? = null

        if (trimmedTitle.isEmpty()) {
            titleErr = "Please enter a goal title"
            hasError = true
        }

        if (targetAmount == null || targetAmount <= 0.0) {
            targetErr = "Please enter a target amount greater than 0"
            hasError = true
        }

        if (openingSavings == null || openingSavings < 0.0) {
            savingsErr = "Opening savings cannot be negative"
            hasError = true
        }

        if (targetAmount != null && openingSavings != null && openingSavings >= targetAmount) {
            targetErr = "Target amount must be greater than opening savings"
            hasError = true
        }

        val startDate = Date(current.startDate)

        if (current.targetDate <= current.startDate) {
            dateErr = "Target date must be in the future"
            hasError = true
        }

        if (hasError) {
            _formState.value = current.copy(
                titleError = titleErr,
                targetAmountError = targetErr,
                openingSavingsError = savingsErr,
                dateError = dateErr
            )
            return
        }

        _formState.value = current.copy(isSaving = true, generalError = null)

        viewModelScope.launch {
            try {
                if (current.isEditing && current.existingGoal != null) {
                    val updated = current.existingGoal.copy(
                        title = trimmedTitle,
                        targetAmountLKR = targetAmount!!,
                        openingSavingsLKR = openingSavings ?: 0.0,
                        startDate = startDate,
                        targetDate = Date(current.targetDate),
                        priority = current.priority
                    )
                    goalRepository.updateGoal(userId, updated)
                } else {
                    val newGoal = Goal(
                        userId = userId,
                        title = trimmedTitle,
                        targetAmountLKR = targetAmount!!,
                        openingSavingsLKR = openingSavings ?: 0.0,
                        startDate = startDate,
                        targetDate = Date(current.targetDate),
                        isActive = true,
                        createdAt = Date(),
                        priority = current.priority
                    )
                    goalRepository.addGoal(userId, newGoal)
                }
                _formState.value = _formState.value.copy(isSaving = false, saveSuccess = true)
                onSuccess()
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isSaving = false,
                    generalError = e.localizedMessage ?: "Failed to save goal"
                )
            }
        }
    }

    /**
     * Deletes a savings goal by ID.
     */
    fun deleteGoal(goalId: String, onSuccess: () -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        _formState.value = _formState.value.copy(isDeleting = true, generalError = null)
        viewModelScope.launch {
            try {
                goalRepository.deleteGoal(userId, goalId)
                _formState.value = _formState.value.copy(isDeleting = false, deleteSuccess = true)
                onSuccess()
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isDeleting = false,
                    generalError = e.localizedMessage ?: "Failed to delete goal"
                )
            }
        }
    }
}

/**
 * State representation for the goals list screen with real-time financial calculations (FR15).
 */
data class GoalUiState(
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val goals: List<Goal> = emptyList(),
    val calculations: Map<String, GoalCalculation> = emptyMap(),
    val selectedFilter: GoalStatusFilter = GoalStatusFilter.ALL,
    val selectedPriority: String? = null,
    val errorMessage: String? = null
) {
    val activeGoal: Goal? get() = goals.firstOrNull { it.isActive }
    val activeGoalCalculation: GoalCalculation? get() = activeGoal?.let { calculations[it.id] }

    val highPriorityCount: Int
        get() = goals.count { it.priority.equals(GoalPriority.HIGH, ignoreCase = true) }

    val mediumPriorityCount: Int
        get() = goals.count {
            it.priority.equals(GoalPriority.MEDIUM, ignoreCase = true) ||
                (!it.priority.equals(GoalPriority.HIGH, ignoreCase = true) && !it.priority.equals(GoalPriority.LOW, ignoreCase = true))
        }

    val lowPriorityCount: Int
        get() = goals.count { it.priority.equals(GoalPriority.LOW, ignoreCase = true) }

    private fun isAchieved(goal: Goal): Boolean {
        val calc = calculations[goal.id]
        return calc?.isCompleted ?: (goal.openingSavingsLKR >= goal.targetAmountLKR)
    }

    private fun isOverdue(goal: Goal): Boolean {
        val calc = calculations[goal.id]
        return calc?.isOverdue ?: (Date().after(goal.targetDate) && goal.openingSavingsLKR < goal.targetAmountLKR)
    }

    private fun matchesPriority(goal: Goal): Boolean {
        if (selectedPriority == null) return true
        val target = selectedPriority.lowercase()
        return when (target) {
            GoalPriority.HIGH -> goal.priority.equals(GoalPriority.HIGH, ignoreCase = true)
            GoalPriority.LOW -> goal.priority.equals(GoalPriority.LOW, ignoreCase = true)
            GoalPriority.MEDIUM -> goal.priority.equals(GoalPriority.MEDIUM, ignoreCase = true) ||
                (!goal.priority.equals(GoalPriority.HIGH, ignoreCase = true) && !goal.priority.equals(GoalPriority.LOW, ignoreCase = true))
            else -> goal.priority.equals(target, ignoreCase = true)
        }
    }

    val filteredGoals: List<Goal>
        get() = when (selectedFilter) {
            GoalStatusFilter.ALL -> goals.filter { matchesPriority(it) }
            GoalStatusFilter.ONGOING -> goals.filter { goal -> !isAchieved(goal) && !isOverdue(goal) && matchesPriority(goal) }
            GoalStatusFilter.ACHIEVED -> goals.filter { goal -> isAchieved(goal) && matchesPriority(goal) }
            GoalStatusFilter.COULD_NOT_ACHIEVE -> goals.filter { goal -> isOverdue(goal) && !isAchieved(goal) && matchesPriority(goal) }
        }

    fun getCountForFilter(filter: GoalStatusFilter): Int = when (filter) {
        GoalStatusFilter.ALL -> goals.count { matchesPriority(it) }
        GoalStatusFilter.ONGOING -> goals.count { goal -> !isAchieved(goal) && !isOverdue(goal) && matchesPriority(goal) }
        GoalStatusFilter.ACHIEVED -> goals.count { goal -> isAchieved(goal) && matchesPriority(goal) }
        GoalStatusFilter.COULD_NOT_ACHIEVE -> goals.count { goal -> isOverdue(goal) && !isAchieved(goal) && matchesPriority(goal) }
    }
}

/**
 * State representation for the add / edit goal form.
 */
data class AddEditGoalState(
    val title: String = "",
    val targetAmount: String = "",
    val openingSavings: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val targetDate: Long = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000,
    val priority: String = GoalPriority.MEDIUM,
    val titleError: String? = null,
    val targetAmountError: String? = null,
    val openingSavingsError: String? = null,
    val dateError: String? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveSuccess: Boolean = false,
    val deleteSuccess: Boolean = false,
    val generalError: String? = null,
    val isEditing: Boolean = false,
    val existingGoal: Goal? = null
)
