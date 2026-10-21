package com.nexus.financetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.IncomeStatus
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
import kotlin.math.roundToInt

/**
 * ViewModel for the Dashboard screen (Member 3 - Order 7: feature/dashboard-summary).
 *
 * Implements Technical Document Section 4.2.2 & 6.2:
 * - Combines confirmed income, expenses, net result and active goal summary for the selected month in a single combine()
 * - Income is reconciled exclusively from confirmed entries (FR5, FR20)
 * - Computes committed vs discretionary spending ratio (FR18)
 * - Summarizes active goal progress (FR15, FR17)
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val goalRepository: GoalRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardSummary()
    }

    fun loadDashboardSummary() {
        val user = auth.currentUser
        val userId = user?.uid ?: return
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Kavindu"
        val (startOfMonth, endOfMonth) = getMonthRange()
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(startOfMonth)

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
            userName = displayName,
            selectedMonthLabel = monthLabel
        )

        viewModelScope.launch {
            combine(
                incomeRepository.getIncomes(userId).catch { emit(emptyList()) },
                expenseRepository.getExpenses(userId).catch { emit(emptyList()) },
                goalRepository.getGoals(userId).catch { emit(emptyList()) }
            ) { incomes, expenses, goals ->
                // 1. Current-month confirmed incomes only (FR5, FR20 - pending strictly excluded)
                val monthConfirmedIncomes = incomes.filter { income ->
                    income.status == IncomeStatus.CONFIRMED &&
                        income.dateReceived != null &&
                        !income.dateReceived.before(startOfMonth) &&
                        !income.dateReceived.after(endOfMonth)
                }
                val totalIncome = monthConfirmedIncomes.sumOf { it.amountLKR }

                // 2. Current-month expenses
                val monthExpenses = expenses.filter { expense ->
                    !expense.date.before(startOfMonth) &&
                        !expense.date.after(endOfMonth)
                }
                val totalExpense = monthExpenses.sumOf { it.amountLKR }

                // 3. Net result for the month
                val netResult = totalIncome - totalExpense

                // 4. Committed vs Discretionary split (FR18)
                val committed = monthExpenses
                    .filter { it.classification == ExpenseClassification.COMMITTED }
                    .sumOf { it.amountLKR }
                val discretionary = monthExpenses
                    .filter { it.classification == ExpenseClassification.DISCRETIONARY }
                    .sumOf { it.amountLKR }

                val committedPercent = if (totalExpense > 0) {
                    ((committed / totalExpense) * 100.0).roundToInt().coerceIn(0, 100)
                } else 0

                val discretionaryPercent = if (totalExpense > 0) {
                    (100 - committedPercent).coerceIn(0, 100)
                } else 0

                // 5. Active goal summary (FR15, FR17)
                val activeGoal = goals.firstOrNull { it.isActive }
                val goalCalculation = activeGoal?.let { goal ->
                    GoalCalculation.calculate(
                        goal = goal,
                        incomes = incomes,
                        expenses = expenses
                    )
                }

                DashboardUiState(
                    isLoading = false,
                    userName = displayName,
                    selectedMonthLabel = monthLabel,
                    totalConfirmedIncomeLKR = totalIncome,
                    totalExpensesLKR = totalExpense,
                    netResultLKR = netResult,
                    committedExpensesLKR = committed,
                    discretionaryExpensesLKR = discretionary,
                    committedPercentage = committedPercent,
                    discretionaryPercentage = discretionaryPercent,
                    activeGoal = activeGoal,
                    activeGoalCalculation = goalCalculation,
                    errorMessage = null
                )
            }.catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to load dashboard summary"
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    companion object {
        /**
         * Returns the start (1st day 00:00:00) and end (last day 23:59:59) of the current month.
         */
        fun getMonthRange(calendar: Calendar = Calendar.getInstance()): Pair<Date, Date> {
            val startCal = calendar.clone() as Calendar
            startCal.set(Calendar.DAY_OF_MONTH, 1)
            startCal.set(Calendar.HOUR_OF_DAY, 0)
            startCal.set(Calendar.MINUTE, 0)
            startCal.set(Calendar.SECOND, 0)
            startCal.set(Calendar.MILLISECOND, 0)
            val startDate = startCal.time

            val endCal = calendar.clone() as Calendar
            endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            endCal.set(Calendar.HOUR_OF_DAY, 23)
            endCal.set(Calendar.MINUTE, 59)
            endCal.set(Calendar.SECOND, 59)
            endCal.set(Calendar.MILLISECOND, 999)
            val endDate = endCal.time

            return Pair(startDate, endDate)
        }
    }
}

/**
 * Immutable UI state for the Dashboard screen.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val userName: String = "Kavindu",
    val selectedMonthLabel: String = "",
    val totalConfirmedIncomeLKR: Double = 0.0,
    val totalExpensesLKR: Double = 0.0,
    val netResultLKR: Double = 0.0,
    val committedExpensesLKR: Double = 0.0,
    val discretionaryExpensesLKR: Double = 0.0,
    val committedPercentage: Int = 0,
    val discretionaryPercentage: Int = 0,
    val activeGoal: Goal? = null,
    val activeGoalCalculation: GoalCalculation? = null,
    val errorMessage: String? = null
) {
    // Backward-compatible properties
    val totalIncome: Double get() = totalConfirmedIncomeLKR
    val totalExpense: Double get() = totalExpensesLKR
    val balance: Double get() = netResultLKR
}
