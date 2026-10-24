package com.nexus.financetracker.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.ui.dashboard.IncomeSourceSpending
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MonthlyGoalReport(
    val goalId: String = "",
    val goalTitle: String = "",
    val priority: String = "medium",
    val isActive: Boolean = true,
    val targetAmountLKR: Double = 0.0,
    val assignedThisMonthLKR: Double = 0.0,
    val targetMonthlyLKR: Double = 0.0,
    val assignedFractionOfMonthlyTarget: Float = 0f,
    val cumulativeSavingsLKR: Double = 0.0,
    val remainingAmountLKR: Double = 0.0,
    val overallProgressFraction: Float = 0f,
    val overallProgressPercentage: Double = 0.0,
    val hasActiveGoal: Boolean = false,
    val isCompleted: Boolean = false,
    val daysRemaining: Long = 0L,
    val daysRemainingText: String = "",
    val targetDate: Date? = null
)

data class MonthlyIncomeReport(
    val monthKey: String,
    val monthLabel: String,
    val totalIncomeLKR: Double = 0.0,
    val incomeSources: List<IncomeSourceSpending> = emptyList(),
    val totalExpensesLKR: Double = 0.0,
    val netResultLKR: Double = 0.0,
    val hasIncomeRecords: Boolean = false,
    val hasExpenseRecords: Boolean = false,
    val goalReports: List<MonthlyGoalReport> = emptyList(),
    val goalReport: MonthlyGoalReport = goalReports.firstOrNull() ?: MonthlyGoalReport()
)

data class ReportsUiState(
    val isLoading: Boolean = true,
    val availableMonths: List<String> = emptyList(),
    val selectedMonthLabel: String = "",
    val monthlyReports: Map<String, MonthlyIncomeReport> = emptyMap(),
    val currentReport: MonthlyIncomeReport? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val goalRepository: GoalRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    fun selectMonth(monthLabel: String) {
        val report = _uiState.value.monthlyReports[monthLabel] ?: MonthlyIncomeReport(
            monthKey = "",
            monthLabel = monthLabel,
            totalIncomeLKR = 0.0,
            incomeSources = emptyList()
        )
        _uiState.value = _uiState.value.copy(
            selectedMonthLabel = monthLabel,
            currentReport = report
        )
    }

    fun selectPreviousMonth() {
        val months = _uiState.value.availableMonths
        val currentIndex = months.indexOf(_uiState.value.selectedMonthLabel)
        if (currentIndex in 0 until months.lastIndex) {
            selectMonth(months[currentIndex + 1])
        }
    }

    fun selectNextMonth() {
        val months = _uiState.value.availableMonths
        val currentIndex = months.indexOf(_uiState.value.selectedMonthLabel)
        if (currentIndex > 0) {
            selectMonth(months[currentIndex - 1])
        }
    }

    fun loadReports() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }

        viewModelScope.launch {
            combine(
                incomeRepository.getIncomes(userId).catch { emit(emptyList()) },
                expenseRepository.getExpenses(userId).catch { emit(emptyList()) },
                goalRepository.getGoals(userId).catch { emit(emptyList()) }
            ) { incomes, expenses, goals ->
                val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
                val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)

                // 1. Generate rolling 24 months (current month + past 23 months)
                val rollingMonths = (0 downTo -23).map { offset ->
                    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
                    monthFormat.format(cal.time)
                }

                // 2. Also include any historical months present in transactions
                val incomeMonths = incomes.map { monthFormat.format(it.dateReceived ?: it.createdAt) }
                val expenseMonths = expenses.map { monthFormat.format(it.date) }

                // Merge and sort chronologically descending (newest first)
                val allMonths = (rollingMonths + incomeMonths + expenseMonths).distinct().sortedByDescending { label ->
                    try {
                        monthFormat.parse(label)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }

                val allTargetGoals = goals.sortedWith(compareByDescending<Goal> { it.isActive }.thenBy { it.targetDate })
                val reports = mutableMapOf<String, MonthlyIncomeReport>()

                allMonths.forEach { label ->
                    val (startOfMonth, endOfMonth) = getMonthDateRange(label, monthFormat)

                    // Confirmed incomes in this month
                    val monthConfirmedIncomes = incomes.filter { income ->
                        income.status == IncomeStatus.CONFIRMED &&
                            (income.dateReceived ?: income.createdAt).let { !it.before(startOfMonth) && !it.after(endOfMonth) }
                    }
                    val totalIncome = monthConfirmedIncomes.sumOf { it.amountLKR }
                    val sources = calculateSourcesForIncomes(monthConfirmedIncomes)

                    // Expenses in this month
                    val monthExpenses = expenses.filter { expense ->
                        !expense.date.before(startOfMonth) && !expense.date.after(endOfMonth)
                    }
                    val totalExpense = monthExpenses.sumOf { it.amountLKR }
                    val netResult = totalIncome - totalExpense
                    val netSavingsThisMonth = maxOf(0.0, netResult)

                    // Goal assignment calculations for all goals for this specific month
                    val goalReports = allTargetGoals.map { goal ->
                        val goalCalc = GoalCalculation.calculate(goal, incomes, expenses, endOfMonth)
                        val targetMonthly = goalCalc.requiredMonthlySavingsLKR
                        val fractionOfTarget = if (targetMonthly > 0) {
                            (netSavingsThisMonth / targetMonthly).toFloat().coerceIn(0f, 1f)
                        } else if (netSavingsThisMonth > 0) {
                            1f
                        } else {
                            0f
                        }

                        MonthlyGoalReport(
                            goalId = goal.id,
                            goalTitle = goal.title,
                            priority = goal.priority,
                            isActive = goal.isActive,
                            targetAmountLKR = goal.targetAmountLKR,
                            assignedThisMonthLKR = netSavingsThisMonth,
                            targetMonthlyLKR = targetMonthly,
                            assignedFractionOfMonthlyTarget = fractionOfTarget,
                            cumulativeSavingsLKR = goalCalc.estimatedCurrentSavingsLKR,
                            remainingAmountLKR = goalCalc.remainingAmountLKR,
                            overallProgressFraction = goalCalc.progressFraction,
                            overallProgressPercentage = goalCalc.progressPercentage,
                            hasActiveGoal = true,
                            isCompleted = goalCalc.isCompleted,
                            daysRemaining = goal.daysRemaining,
                            daysRemainingText = goal.daysRemainingText,
                            targetDate = goal.targetDate
                        )
                    }

                    reports[label] = MonthlyIncomeReport(
                        monthKey = monthKeyFormat.format(startOfMonth),
                        monthLabel = label,
                        totalIncomeLKR = totalIncome,
                        incomeSources = sources,
                        totalExpensesLKR = totalExpense,
                        netResultLKR = netResult,
                        hasIncomeRecords = monthConfirmedIncomes.isNotEmpty(),
                        hasExpenseRecords = monthExpenses.isNotEmpty(),
                        goalReports = goalReports,
                        goalReport = goalReports.firstOrNull() ?: MonthlyGoalReport(hasActiveGoal = false)
                    )
                }

                val currentMonthLabel = monthFormat.format(Date())
                val previouslySelected = _uiState.value.selectedMonthLabel
                val selected = when {
                    previouslySelected.isNotBlank() && allMonths.contains(previouslySelected) -> previouslySelected
                    allMonths.contains(currentMonthLabel) -> currentMonthLabel
                    allMonths.isNotEmpty() -> allMonths.first()
                    else -> currentMonthLabel
                }

                _uiState.value = ReportsUiState(
                    isLoading = false,
                    availableMonths = allMonths,
                    selectedMonthLabel = selected,
                    monthlyReports = reports,
                    currentReport = reports[selected],
                    errorMessage = null
                )
            }.collect()
        }
    }

    private fun getMonthDateRange(monthLabel: String, monthFormat: SimpleDateFormat): Pair<Date, Date> {
        val cal = Calendar.getInstance()
        try {
            val parsed = monthFormat.parse(monthLabel)
            if (parsed != null) {
                cal.time = parsed
            }
        } catch (_: Exception) {}

        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.time

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfMonth = cal.time

        return Pair(startOfMonth, endOfMonth)
    }

    companion object {
        fun calculateSourcesForIncomes(confirmedIncomes: List<Income>): List<IncomeSourceSpending> {
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
