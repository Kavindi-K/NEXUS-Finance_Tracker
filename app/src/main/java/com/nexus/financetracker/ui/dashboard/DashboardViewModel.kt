package com.nexus.financetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.CategoryRepository
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
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
 * ViewModel for Dashboard Summary & Charts (Member 3 - Order 8: feature/dashboard-charts).
 *
 * Implements Technical Document Section 4.2.2 & 6.2:
 * - Real-time category-wise spending chart highlighting leaks (FR19)
 * - Committed vs Discretionary spending visualization (FR18)
 * - Income by source breakdown visualization (FR3, FR20)
 * - Safe handling of empty states with zero crashes (FR22)
 * - Combines observable repository streams: income, expense, goal, category (FR5, FR15, FR17, FR20)
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
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
                goalRepository.getGoals(userId).catch { emit(emptyList()) },
                categoryRepository.getCategories(userId).catch { emit(emptyList()) }
            ) { incomes, expenses, goals, categories ->
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
                val (committedPercent, discretionaryPercent) = calculateCommittedDiscretionarySplit(
                    expenses = monthExpenses,
                    startOfMonth = startOfMonth,
                    endOfMonth = endOfMonth
                )
                val committed = monthExpenses
                    .filter { it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }
                    .sumOf { it.amountLKR }
                val discretionary = monthExpenses
                    .filter { it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true) }
                    .sumOf { it.amountLKR }

                // 5. Active goal summary (FR15, FR17)
                val activeGoal = goals.firstOrNull { it.isActive }
                val goalCalculation = activeGoal?.let { goal ->
                    GoalCalculation.calculate(
                        goal = goal,
                        incomes = incomes,
                        expenses = expenses
                    )
                }

                // 6. Category spending visualization with ID-to-name resolution (FR19 / Order 8)
                val categorySpendings = calculateCategorySpending(
                    expenses = monthExpenses,
                    categories = categories,
                    startOfMonth = startOfMonth,
                    endOfMonth = endOfMonth
                )

                // 7. Income sources breakdown visualization (FR3, FR20)
                val incomeSources = calculateIncomeSources(monthConfirmedIncomes)

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
                    categorySpendings = categorySpendings,
                    incomeSources = incomeSources,
                    hasExpenseRecords = monthExpenses.isNotEmpty(),
                    hasIncomeRecords = monthConfirmedIncomes.isNotEmpty(),
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

        /**
         * Resolves category IDs to names, aggregates month spending, and ranks categories (FR19).
         * Top category receives fraction 1.0f; other categories are scaled proportionally.
         */
        fun calculateCategorySpending(
            expenses: List<Expense>,
            categories: List<Category>,
            startOfMonth: Date,
            endOfMonth: Date
        ): List<CategorySpending> {
            val monthExpenses = expenses.filter {
                !it.date.before(startOfMonth) && !it.date.after(endOfMonth)
            }
            if (monthExpenses.isEmpty()) return emptyList()

            val categoryMap = categories.associate { it.id to it.name }
            val categoryTotals = monthExpenses.groupBy { expense ->
                categoryMap[expense.categoryId]?.takeIf { it.isNotBlank() }
                    ?: expense.categoryId.takeIf { it.isNotBlank() }
                    ?: "Other"
            }.mapValues { (_, list) -> list.sumOf { it.amountLKR } }

            val maxSpending = categoryTotals.values.maxOrNull() ?: 1.0
            val totalExpenses = monthExpenses.sumOf { it.amountLKR }

            return categoryTotals.map { (catName, sum) ->
                val pct = if (totalExpenses > 0) ((sum / totalExpenses) * 100.0).roundToInt() else 0
                val formattedName = catName.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
                CategorySpending(
                    name = formattedName,
                    amountLKR = sum,
                    fraction = if (maxSpending > 0) (sum / maxSpending).toFloat().coerceIn(0.05f, 1f) else 0f,
                    percentageOfTotal = pct
                )
            }.sortedByDescending { it.amountLKR }
        }

        /**
         * Computes committed vs discretionary ratio (FR18) with division-by-zero protection.
         */
        fun calculateCommittedDiscretionarySplit(
            expenses: List<Expense>,
            startOfMonth: Date,
            endOfMonth: Date
        ): Pair<Int, Int> {
            val monthExpenses = expenses.filter {
                !it.date.before(startOfMonth) && !it.date.after(endOfMonth)
            }
            val totalExpense = monthExpenses.sumOf { it.amountLKR }
            if (totalExpense <= 0) return Pair(0, 0)

            val committed = monthExpenses
                .filter { it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }
                .sumOf { it.amountLKR }

            val committedPercent = ((committed / totalExpense) * 100.0).roundToInt().coerceIn(0, 100)
            val discretionaryPercent = (100 - committedPercent).coerceIn(0, 100)
            return Pair(committedPercent, discretionaryPercent)
        }

        /**
         * Groups confirmed monthly income by source (Salary, Freelance, AdSense, Crypto) (FR3, FR20).
         */
        fun calculateIncomeSources(confirmedIncomes: List<Income>): List<IncomeSourceSpending> {
            if (confirmedIncomes.isEmpty()) return emptyList()
            val sourceTotals = confirmedIncomes.groupBy { it.source }
                .mapValues { (_, list) -> list.sumOf { it.amountLKR } }
            val maxIncome = sourceTotals.values.maxOrNull() ?: 1.0

            return sourceTotals.map { (source, sum) ->
                val name = when (source) {
                    IncomeSource.SALARY -> "Base Salary"
                    IncomeSource.FREELANCE -> "Freelance"
                    IncomeSource.ADSENSE -> "AdSense (USD)"
                    IncomeSource.CRYPTO -> "Crypto Net P&L"
                    else -> source.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }
                }
                IncomeSourceSpending(
                    source = name,
                    amountLKR = sum,
                    fraction = if (maxIncome > 0) (sum / maxIncome).toFloat().coerceIn(0.05f, 1f) else 0.05f
                )
            }.sortedByDescending { it.amountLKR }
        }
    }
}

/**
 * Category spending summary for visualization (FR19).
 */
data class CategorySpending(
    val name: String,
    val amountLKR: Double,
    val fraction: Float,
    val percentageOfTotal: Int = 0
)

/**
 * Income source summary for visualization (FR3, FR20).
 */
data class IncomeSourceSpending(
    val source: String,
    val amountLKR: Double,
    val fraction: Float
)

/**
 * Immutable UI state for the Dashboard screen (FR17-FR20, FR22).
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
    val categorySpendings: List<CategorySpending> = emptyList(),
    val incomeSources: List<IncomeSourceSpending> = emptyList(),
    val hasExpenseRecords: Boolean = false,
    val hasIncomeRecords: Boolean = false,
    val errorMessage: String? = null
) {
    // Backward-compatible properties
    val totalIncome: Double get() = totalConfirmedIncomeLKR
    val totalExpense: Double get() = totalExpensesLKR
    val balance: Double get() = netResultLKR
}
