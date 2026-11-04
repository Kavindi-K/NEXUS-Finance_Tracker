package com.nexus.financetracker.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.CategoryRepository
import com.nexus.financetracker.data.repository.ContributionManager
import com.nexus.financetracker.domain.model.Contribution
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.ui.dashboard.CategorySpending
import com.nexus.financetracker.ui.dashboard.DashboardViewModel
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


data class MonthOverMonthComparison(
    val previousMonthLabel: String = "",
    val currentIncomeLKR: Double = 0.0,
    val previousIncomeLKR: Double = 0.0,
    val incomeDiffLKR: Double = 0.0,
    val incomeChangePercent: Double = 0.0,
    val currentExpensesLKR: Double = 0.0,
    val previousExpensesLKR: Double = 0.0,
    val expenseDiffLKR: Double = 0.0,
    val expenseChangePercent: Double = 0.0,
    val currentSavingsLKR: Double = 0.0,
    val previousSavingsLKR: Double = 0.0,
    val savingsDiffLKR: Double = 0.0,
    val savingsChangePercent: Double = 0.0,
    val currentSavingsRatePercent: Double = 0.0,
    val previousSavingsRatePercent: Double = 0.0
) {
    val hasPreviousMonth: Boolean get() = previousMonthLabel.isNotBlank()
    val isIncomeIncreased: Boolean get() = incomeDiffLKR >= 0
    val isExpenseDecreased: Boolean get() = expenseDiffLKR <= 0
    val isSavingsIncreased: Boolean get() = savingsDiffLKR >= 0
}

enum class RecommendationType {
    POSITIVE,
    WARNING,
    INFO,
    ACTION
}

data class FinancialRecommendation(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val type: RecommendationType = RecommendationType.INFO,
    val impactArea: String = ""
)

data class MonthlyHealthSummary(
    val headline: String = "",
    val healthGrade: String = "",
    val overview: String = "",
    val savingsRatePercent: Double = 0.0,
    val topExpenseCategory: String = "",
    val topExpenseAmountLKR: Double = 0.0,
    val discretionaryOverspendCount: Int = 0,
    val recommendations: List<FinancialRecommendation> = emptyList()
)

data class DiscretionaryCategoryComparison(
    val categoryId: String = "",
    val categoryName: String = "",
    val estimatedAmountLKR: Double = 0.0,
    val actualExpenseLKR: Double = 0.0
) {
    val differenceLKR: Double get() = estimatedAmountLKR - actualExpenseLKR
    val isWithinBudget: Boolean get() = actualExpenseLKR <= estimatedAmountLKR
}

data class DailyCumulativePoint(
    val day: Int,
    val currentMonthCumulativeLKR: Double,
    val previousMonthCumulativeLKR: Double
)

data class MonthToDateComparison(
    val cutoffDay: Int = 1,
    val currentMonthLabel: String = "",
    val previousMonthLabel: String = "",
    val currentIncomeMtdLKR: Double = 0.0,
    val previousIncomeMtdLKR: Double = 0.0,
    val currentExpensesMtdLKR: Double = 0.0,
    val previousExpensesMtdLKR: Double = 0.0,
    val dailyIncomePoints: List<DailyCumulativePoint> = emptyList(),
    val dailyExpensePoints: List<DailyCumulativePoint> = emptyList()
) {
    val hasPreviousMonth: Boolean get() = previousMonthLabel.isNotBlank()
    val incomeMtdDiffLKR: Double get() = currentIncomeMtdLKR - previousIncomeMtdLKR
    val incomeMtdChangePercent: Double get() = if (previousIncomeMtdLKR > 0) ((currentIncomeMtdLKR - previousIncomeMtdLKR) / previousIncomeMtdLKR) * 100.0 else 0.0
    val expenseMtdDiffLKR: Double get() = currentExpensesMtdLKR - previousExpensesMtdLKR
    val expenseMtdChangePercent: Double get() = if (previousExpensesMtdLKR > 0) ((currentExpensesMtdLKR - previousExpensesMtdLKR) / previousExpensesMtdLKR) * 100.0 else 0.0
    val isIncomeIncreased: Boolean get() = incomeMtdDiffLKR >= 0
    val isExpenseDecreased: Boolean get() = expenseMtdDiffLKR <= 0
}

data class MonthlyIncomeReport(
    val monthKey: String,
    val monthLabel: String,
    val totalIncomeLKR: Double = 0.0,
    val incomeSources: List<IncomeSourceSpending> = emptyList(),
    val totalExpensesLKR: Double = 0.0,
    val netResultLKR: Double = 0.0,
    val categorySpendings: List<CategorySpending> = emptyList(),
    val discretionaryComparisons: List<DiscretionaryCategoryComparison> = emptyList(),
    val hasIncomeRecords: Boolean = false,
    val hasExpenseRecords: Boolean = false,
    val goalReports: List<MonthlyGoalReport> = emptyList(),
    val goalReport: MonthlyGoalReport = goalReports.firstOrNull() ?: MonthlyGoalReport(),
    val comparison: MonthOverMonthComparison? = null,
    val healthSummary: MonthlyHealthSummary? = null,
    val monthToDateComparison: MonthToDateComparison? = null
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
    private val categoryRepository: CategoryRepository,
    private val contributionManager: ContributionManager,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    fun downloadMonthlyReport(context: android.content.Context, monthLabel: String) {
        val report = _uiState.value.monthlyReports[monthLabel] ?: _uiState.value.currentReport ?: return
        MonthlyReportPdfGenerator.generateAndOpen(context, report)
    }

    fun selectMonth(monthLabel: String) {
        val report = _uiState.value.monthlyReports[monthLabel] ?: MonthlyIncomeReport(
            monthKey = "",
            monthLabel = monthLabel,
            totalIncomeLKR = 0.0,
            incomeSources = emptyList(),
            categorySpendings = emptyList()
        )
        _uiState.value = _uiState.value.copy(
            selectedMonthLabel = monthLabel,
            currentReport = report
        )
    }

    fun selectPreviousMonth() {
        val months = _uiState.value.availableMonths
        if (_uiState.value.selectedMonthLabel == "All Time") {
            if (months.isNotEmpty()) selectMonth(months.first())
            return
        }
        val currentIndex = months.indexOf(_uiState.value.selectedMonthLabel)
        if (currentIndex in 0 until months.lastIndex) {
            selectMonth(months[currentIndex + 1])
        }
    }

    fun selectNextMonth() {
        val months = _uiState.value.availableMonths
        if (_uiState.value.selectedMonthLabel == "All Time") {
            if (months.isNotEmpty()) selectMonth(months.first())
            return
        }
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
                goalRepository.getGoals(userId).catch { emit(emptyList()) },
                categoryRepository.getCategories(userId).catch { emit(emptyList()) },
                contributionManager.contributions
            ) { incomes, expenses, goals, categories, contributions ->
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
                    
                                // Category spendings for this specific month
            val categorySpendings = DashboardViewModel.calculateCategorySpending(
                expenses = monthExpenses,
                categories = categories,
                startOfMonth = startOfMonth,
                endOfMonth = endOfMonth
            )

                  // Goal assignment calculations for goals that belong to this specific month.
                    // Completed goals ONLY display in their completed month (not in subsequent/current months).
                    val goalReports = allTargetGoals.mapNotNull { goal ->
                        val completionMonth = resolveGoalCompletionMonth(
                            goal = goal,
                            incomes = incomes,
                            expenses = expenses,
                            contributions = contributions,
                            monthFormat = monthFormat
                        )

                        val shouldDisplay = shouldDisplayGoalInMonth(
                            goal = goal,
                            monthLabel = label,
                            endOfMonth = endOfMonth,
                            completionMonth = completionMonth
                        )

                        if (!shouldDisplay) {
                            null
                        } else {
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
                                isCompleted = completionMonth != null || goalCalc.isCompleted,
                                daysRemaining = goal.daysRemaining,
                                daysRemainingText = goal.daysRemainingText,
                                targetDate = goal.targetDate
                            )
                        }
                    }

                    // Discretionary categories comparison for this month
                    val allCombinedCategories = (categories + com.nexus.financetracker.ui.expense.DEFAULT_EXPENSE_CATEGORIES).distinctBy { it.id }
                    val discretionaryCats = allCombinedCategories.filter {
                        it.classification.equals(com.nexus.financetracker.domain.model.ExpenseClassification.DISCRETIONARY, ignoreCase = true) &&
                            (it.mergingInto.isNullOrBlank() || it.mergingInto.startsWith("dur:"))
                    }
                    val discretionaryComparisons = discretionaryCats.map { cat ->
                        val spent = monthExpenses.filter { it.categoryId == cat.id }.sumOf { it.amountLKR }
                        val est = cat.estimatedAmount ?: 0.0
                        DiscretionaryCategoryComparison(
                            categoryId = cat.id,
                            categoryName = cat.name,
                            estimatedAmountLKR = est,
                            actualExpenseLKR = spent
                        )
                    }

                    reports[label] = MonthlyIncomeReport(
                        monthKey = monthKeyFormat.format(startOfMonth),
                        monthLabel = label,
                        totalIncomeLKR = totalIncome,
                        incomeSources = sources,
                        totalExpensesLKR = totalExpense,
                        netResultLKR = netResult,
                        categorySpendings = categorySpendings,
                        discretionaryComparisons = discretionaryComparisons,
                        hasIncomeRecords = monthConfirmedIncomes.isNotEmpty(),
                        hasExpenseRecords = monthExpenses.isNotEmpty(),
                        goalReports = goalReports,
                        goalReport = goalReports.firstOrNull() ?: MonthlyGoalReport(hasActiveGoal = false)
                    )
                }

                // All-Time Report
                val allConfirmedIncomes = incomes.filter { it.status == IncomeStatus.CONFIRMED }
                val allTotalIncome = allConfirmedIncomes.sumOf { it.amountLKR }
                val allSources = calculateSourcesForIncomes(allConfirmedIncomes)
                val allTotalExpense = expenses.sumOf { it.amountLKR }
                val allNetResult = allTotalIncome - allTotalExpense
                val allNetSavings = maxOf(0.0, allNetResult)
                val allCategorySpendings = DashboardViewModel.calculateCategorySpending(
                    expenses = expenses,
                    categories = categories,
                    startOfMonth = Date(0),
                    endOfMonth = Date(Long.MAX_VALUE - 1000000000L)
                )
                val allGoalReports = allTargetGoals.map { goal ->
                    val goalCalc = GoalCalculation.calculate(goal, incomes, expenses, Date())
                    val targetMonthly = goalCalc.requiredMonthlySavingsLKR
                    val fractionOfTarget = if (targetMonthly > 0) {
                        (allNetSavings / targetMonthly).toFloat().coerceIn(0f, 1f)
                    } else if (allNetSavings > 0) {
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
                        assignedThisMonthLKR = allNetSavings,
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

                val allDiscretionaryComparisons = (categories + com.nexus.financetracker.ui.expense.DEFAULT_EXPENSE_CATEGORIES)
                    .distinctBy { it.id }
                    .filter {
                        it.classification.equals(com.nexus.financetracker.domain.model.ExpenseClassification.DISCRETIONARY, ignoreCase = true) &&
                            (it.mergingInto.isNullOrBlank() || it.mergingInto.startsWith("dur:"))
                    }
                    .map { cat ->
                        val spent = expenses.filter { it.categoryId == cat.id }.sumOf { it.amountLKR }
                        val est = cat.estimatedAmount ?: 0.0
                        DiscretionaryCategoryComparison(
                            categoryId = cat.id,
                            categoryName = cat.name,
                            estimatedAmountLKR = est,
                            actualExpenseLKR = spent
                        )
                    }

                reports["All Time"] = MonthlyIncomeReport(
                    monthKey = "all_time",
                    monthLabel = "All Time",
                    totalIncomeLKR = allTotalIncome,
                    incomeSources = allSources,
                    totalExpensesLKR = allTotalExpense,
                    netResultLKR = allNetResult,
                    categorySpendings = allCategorySpendings,
                    discretionaryComparisons = allDiscretionaryComparisons,
                    hasIncomeRecords = allConfirmedIncomes.isNotEmpty(),
                    hasExpenseRecords = expenses.isNotEmpty(),
                    goalReports = allGoalReports,
                    goalReport = allGoalReports.firstOrNull() ?: MonthlyGoalReport(hasActiveGoal = false)
                )

                // Enrich monthly reports with Month-over-Month comparison and rule-based Health Summaries
                allMonths.forEachIndexed { i, label ->
                    val baseReport = reports[label] ?: return@forEachIndexed
                    val prevMonthLabel = if (i + 1 < allMonths.size) allMonths[i + 1] else null
                    val prevReport = prevMonthLabel?.let { reports[it] }

                    val comparison = calculateMonthOverMonthComparison(
                        currentReport = baseReport,
                        previousReport = prevReport,
                        previousMonthLabel = prevMonthLabel
                    )

                    val healthSummary = generateHealthSummary(
                        report = baseReport,
                        comparison = comparison,
                        categories = categories
                    )

                    val prevIncomes = if (prevMonthLabel != null) {
                        val (pStart, pEnd) = getMonthDateRange(prevMonthLabel, monthFormat)
                        incomes.filter { income ->
                            income.status == IncomeStatus.CONFIRMED &&
                                (income.dateReceived ?: income.createdAt).let { !it.before(pStart) && !it.after(pEnd) }
                        }
                    } else emptyList()

                    val prevExpenses = if (prevMonthLabel != null) {
                        val (pStart, pEnd) = getMonthDateRange(prevMonthLabel, monthFormat)
                        expenses.filter { expense ->
                            !expense.date.before(pStart) && !expense.date.after(pEnd)
                        }
                    } else emptyList()

                    val (cStart, cEnd) = getMonthDateRange(label, monthFormat)
                    val currIncomes = incomes.filter { income ->
                        income.status == IncomeStatus.CONFIRMED &&
                            (income.dateReceived ?: income.createdAt).let { !it.before(cStart) && !it.after(cEnd) }
                    }
                    val currExpenses = expenses.filter { expense ->
                        !expense.date.before(cStart) && !expense.date.after(cEnd)
                    }

                    val mtdComparison = calculateMonthToDateComparison(
                        currentMonthLabel = label,
                        previousMonthLabel = prevMonthLabel,
                        currentIncomes = currIncomes,
                        previousIncomes = prevIncomes,
                        currentExpenses = currExpenses,
                        previousExpenses = prevExpenses,
                        monthFormat = monthFormat
                    )

                    reports[label] = baseReport.copy(
                        comparison = comparison,
                        healthSummary = healthSummary,
                        monthToDateComparison = mtdComparison
                    )
                }

                // All Time report health summary
                val baseAllTime = reports["All Time"]
                if (baseAllTime != null) {
                    reports["All Time"] = baseAllTime.copy(
                        healthSummary = generateHealthSummary(
                            report = baseAllTime,
                            comparison = null,
                            categories = categories
                        )
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
        /**
         * Resolves the month in which a goal was completed (if completed).
         * Priority:
         * 1. Latest allocated contribution's monthLabel (e.g. surplus allocated in October 2026).
         * 2. Transaction history when cumulative confirmed savings reached or exceeded target amount.
         * 3. Fallback: goal createdAt month if created already completed.
         * Returns null if goal is ongoing.
         */
        fun resolveGoalCompletionMonth(
            goal: Goal,
            incomes: List<Income>,
            expenses: List<Expense>,
            contributions: List<Contribution> = emptyList(),
            monthFormat: SimpleDateFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        ): String? {
            val currentCalc = GoalCalculation.calculate(goal, incomes, expenses, Date())
            val isCompleted = currentCalc.isCompleted || goal.openingSavingsLKR >= goal.targetAmountLKR
            if (!isCompleted) return null

            // 1. Check if contributions allocated to this goal specify completion month
            val goalContributions = contributions.filter {
                it.isAllocated && (it.allocatedGoalId == goal.id || it.allocatedGoalTitle.equals(goal.title, ignoreCase = true))
            }.sortedBy { it.createdAt }

            if (goalContributions.isNotEmpty()) {
                val latest = goalContributions.last()
                return if (latest.monthLabel.isNotBlank()) latest.monthLabel else monthFormat.format(latest.createdAt)
            }

            // 2. Chronological progression from incomes & expenses
            val relevantIncomes = incomes.filter {
                it.status == IncomeStatus.CONFIRMED &&
                    (it.dateReceived ?: it.createdAt).time >= goal.startDate.time
            }.sortedBy { (it.dateReceived ?: it.createdAt).time }

            for (income in relevantIncomes) {
                val incDate = income.dateReceived ?: income.createdAt
                val incTime = incDate.time
                val incSum = relevantIncomes.filter { (it.dateReceived ?: it.createdAt).time <= incTime }.sumOf { it.amountLKR }
                val expSum = expenses.filter { it.date.time >= goal.startDate.time && it.date.time <= incTime }.sumOf { it.amountLKR }
                if (goal.openingSavingsLKR + incSum - expSum >= goal.targetAmountLKR) {
                    return monthFormat.format(incDate)
                }
            }

            // 3. Fallback: creation month
            return monthFormat.format(goal.createdAt)
        }

        /**
         * Determines if a goal should be displayed in a given month's report.
         * Completed goals ONLY display in their goal completed month.
         * Ongoing goals display if started by this month.
         */
        fun shouldDisplayGoalInMonth(
            goal: Goal,
            monthLabel: String,
            endOfMonth: Date,
            completionMonth: String?
        ): Boolean {
            return if (completionMonth != null) {
                completionMonth.equals(monthLabel, ignoreCase = true)
            } else {
                !goal.startDate.after(endOfMonth)
            }
        }

        fun calculateSourcesForIncomes(confirmedIncomes: List<Income>): List<IncomeSourceSpending> {
            if (confirmedIncomes.isEmpty()) return emptyList()
            val sourceTotals = confirmedIncomes.groupBy { it.source }
                .mapValues { (_, list) -> list.sumOf { it.amountLKR } }
            val maxIncome = sourceTotals.values.maxOrNull() ?: 1.0

            return sourceTotals.map { (source, sum) ->
                val name = when (source.lowercase().trim()) {
                    IncomeSource.SALARY, "salary" -> "Salary"
                    IncomeSource.FREELANCE, "freelance" -> "Freelance"
                    IncomeSource.CRYPTO, "crypto" -> "Crypto"
                    IncomeSource.ADSENSE, "adsense" -> "AdSense"
                    "other", "other_income", "other income" -> "Other"
                    else -> source.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }
                }
                IncomeSourceSpending(
                    source = name,
                    amountLKR = sum,
                    fraction = if (maxIncome > 0) (sum / maxIncome).toFloat().coerceIn(0.05f, 1f) else 0.05f
                )
            }.sortedWith(
                compareBy<IncomeSourceSpending> { IncomeSource.getSourceOrder(it.source) }
                    .thenByDescending { it.amountLKR }
            )
        }

        fun calculateMonthOverMonthComparison(
            currentReport: MonthlyIncomeReport,
            previousReport: MonthlyIncomeReport?,
            previousMonthLabel: String?
        ): MonthOverMonthComparison {
            if (previousReport == null || previousMonthLabel.isNullOrBlank()) {
                val currSavings = maxOf(0.0, currentReport.netResultLKR)
                val currSavingsRate = if (currentReport.totalIncomeLKR > 0) (currSavings / currentReport.totalIncomeLKR) * 100 else 0.0
                return MonthOverMonthComparison(
                    previousMonthLabel = "",
                    currentIncomeLKR = currentReport.totalIncomeLKR,
                    previousIncomeLKR = 0.0,
                    incomeDiffLKR = currentReport.totalIncomeLKR,
                    incomeChangePercent = 0.0,
                    currentExpensesLKR = currentReport.totalExpensesLKR,
                    previousExpensesLKR = 0.0,
                    expenseDiffLKR = currentReport.totalExpensesLKR,
                    expenseChangePercent = 0.0,
                    currentSavingsLKR = currSavings,
                    previousSavingsLKR = 0.0,
                    savingsDiffLKR = currSavings,
                    savingsChangePercent = 0.0,
                    currentSavingsRatePercent = currSavingsRate,
                    previousSavingsRatePercent = 0.0
                )
            }

            val incomeDiff = currentReport.totalIncomeLKR - previousReport.totalIncomeLKR
            val incomeChangePercent = if (previousReport.totalIncomeLKR > 0) (incomeDiff / previousReport.totalIncomeLKR) * 100 else 0.0

            val expenseDiff = currentReport.totalExpensesLKR - previousReport.totalExpensesLKR
            val expenseChangePercent = if (previousReport.totalExpensesLKR > 0) (expenseDiff / previousReport.totalExpensesLKR) * 100 else 0.0

            val currSavings = maxOf(0.0, currentReport.netResultLKR)
            val prevSavings = maxOf(0.0, previousReport.netResultLKR)
            val savingsDiff = currSavings - prevSavings
            val savingsChangePercent = if (prevSavings > 0) (savingsDiff / prevSavings) * 100 else 0.0

            val currSavingsRate = if (currentReport.totalIncomeLKR > 0) (currSavings / currentReport.totalIncomeLKR) * 100 else 0.0
            val prevSavingsRate = if (previousReport.totalIncomeLKR > 0) (prevSavings / previousReport.totalIncomeLKR) * 100 else 0.0

            return MonthOverMonthComparison(
                previousMonthLabel = previousMonthLabel,
                currentIncomeLKR = currentReport.totalIncomeLKR,
                previousIncomeLKR = previousReport.totalIncomeLKR,
                incomeDiffLKR = incomeDiff,
                incomeChangePercent = incomeChangePercent,
                currentExpensesLKR = currentReport.totalExpensesLKR,
                previousExpensesLKR = previousReport.totalExpensesLKR,
                expenseDiffLKR = expenseDiff,
                expenseChangePercent = expenseChangePercent,
                currentSavingsLKR = currSavings,
                previousSavingsLKR = prevSavings,
                savingsDiffLKR = savingsDiff,
                savingsChangePercent = savingsChangePercent,
                currentSavingsRatePercent = currSavingsRate,
                previousSavingsRatePercent = prevSavingsRate
            )
        }

        fun generateHealthSummary(
            report: MonthlyIncomeReport,
            comparison: MonthOverMonthComparison?,
            categories: List<Category>
        ): MonthlyHealthSummary {
            val currencyFormat = java.text.NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            fun cf(amt: Double) = currencyFormat.format(amt)

            val income = report.totalIncomeLKR
            val expense = report.totalExpensesLKR
            val net = report.netResultLKR
            val hasData = income > 0.0 || expense > 0.0

            if (!hasData) {
                return MonthlyHealthSummary(
                    headline = "No Recorded Transactions",
                    healthGrade = "N/A",
                    overview = "No income or expense records found for this period. Add income sources or log transactions to generate detailed analytics.",
                    savingsRatePercent = 0.0,
                    topExpenseCategory = "",
                    topExpenseAmountLKR = 0.0,
                    discretionaryOverspendCount = 0,
                    recommendations = listOf(
                        FinancialRecommendation(
                            id = "rec_log_income",
                            title = "Record your income sources",
                            description = "Log your salary, freelance earnings, or investments to start tracking monthly surplus.",
                            type = RecommendationType.INFO,
                            impactArea = "Income Tracking"
                        ),
                        FinancialRecommendation(
                            id = "rec_log_expense",
                            title = "Track routine expenditures",
                            description = "Categorize your daily spending as committed or discretionary to build accurate budgets.",
                            type = RecommendationType.ACTION,
                            impactArea = "Budgeting"
                        )
                    )
                )
            }

            val savingsRate = if (income > 0) (maxOf(0.0, net) / income) * 100 else 0.0
            val topExpense = report.categorySpendings.maxByOrNull { it.amountLKR }
            val topCatName = topExpense?.name ?: "General"
            val topCatAmt = topExpense?.amountLKR ?: 0.0

            val overspentDisc = report.discretionaryComparisons.filter { !it.isWithinBudget && it.estimatedAmountLKR > 0 }
            val totalDiscSurplus = report.discretionaryComparisons.sumOf { it.differenceLKR }

            val (headline, grade, overview) = when {
                net < 0 -> {
                    val deficit = Math.abs(net)
                    val isSevere = deficit > income * 0.25
                    Triple(
                        "Deficit Spending Warning",
                        if (isSevere) "D" else "C",
                        "Expenditures exceeded confirmed earnings by LKR ${cf(deficit)} this month. Non-essential discretionary spending should be reviewed immediately to restore balanced cashflow."
                    )
                }
                savingsRate >= 40.0 -> {
                    Triple(
                        "Exceptional Savings Performance",
                        "A+",
                        "Outstanding financial discipline! You saved ${String.format(Locale.US, "%.1f", savingsRate)}% of your income (LKR ${cf(net)} surplus), comfortably outperforming standard savings benchmarks."
                    )
                }
                savingsRate >= 20.0 -> {
                    Triple(
                        "Healthy Savings Surplus",
                        "A",
                        "Strong financial control! You retained ${String.format(Locale.US, "%.1f", savingsRate)}% of your earnings (LKR ${cf(net)} surplus) after satisfying all fixed and flexible expenses."
                    )
                }
                savingsRate > 5.0 -> {
                    Triple(
                        "Moderate Savings Buffer",
                        "B",
                        "You closed the month with positive savings of LKR ${cf(net)} (${String.format(Locale.US, "%.1f", savingsRate)}% savings rate). Trimming discretionary categories will help grow your buffer toward the 20% mark."
                    )
                }
                else -> {
                    Triple(
                        "Tight Operating Margin",
                        "B-",
                        "Your expenses matched your earnings closely this month. Look for opportunities to lower variable spending to build an emergency reserve."
                    )
                }
            }

            val recs = mutableListOf<FinancialRecommendation>()

            // 1. Discretionary overspending or discipline
            if (overspentDisc.isNotEmpty()) {
                overspentDisc.take(2).forEach { disc ->
                    val overage = Math.abs(disc.differenceLKR)
                    recs.add(
                        FinancialRecommendation(
                            id = "rec_disc_${disc.categoryId}",
                            title = "Cap '${disc.categoryName}' spending",
                            description = "Actual spending exceeded budget by LKR ${cf(overage)}. Aim for a weekly target of LKR ${cf(disc.estimatedAmountLKR / 4)} next month.",
                            type = RecommendationType.WARNING,
                            impactArea = "Discretionary"
                        )
                    )
                }
            } else if (report.discretionaryComparisons.isNotEmpty()) {
                recs.add(
                    FinancialRecommendation(
                        id = "rec_disc_ok",
                        title = "Excellent budget discipline",
                        description = "All discretionary categories remained within budget, generating LKR ${cf(Math.max(0.0, totalDiscSurplus))} in cumulative surplus.",
                        type = RecommendationType.POSITIVE,
                        impactArea = "Discretionary"
                    )
                )
            }

            // 2. Goal assignment recommendations
            val activeGoal = report.goalReports.firstOrNull { it.isActive && !it.isCompleted }
            if (activeGoal != null) {
                if (net > 0) {
                    val coverage = if (activeGoal.targetMonthlyLKR > 0) {
                        (net / activeGoal.targetMonthlyLKR * 100).toInt()
                    } else 100
                    recs.add(
                        FinancialRecommendation(
                            id = "rec_goal_alloc",
                            title = "Fund '${activeGoal.goalTitle}'",
                            description = "Your monthly surplus of LKR ${cf(net)} can satisfy ${coverage}% of this goal's target monthly allocation.",
                            type = RecommendationType.ACTION,
                            impactArea = "Goals"
                        )
                    )
                } else {
                    recs.add(
                        FinancialRecommendation(
                            id = "rec_goal_warning",
                            title = "Keep '${activeGoal.goalTitle}' on track",
                            description = "Target is approaching (${activeGoal.daysRemainingText}). Plan an allocation from upcoming confirmed incomes.",
                            type = RecommendationType.INFO,
                            impactArea = "Goals"
                        )
                    )
                }
            } else {
                recs.add(
                    FinancialRecommendation(
                        id = "rec_goal_create",
                        title = "Establish a savings target",
                        description = "Create a goal in the Goals tab to systematically allocate your end-of-month surplus toward high-priority milestones.",
                        type = RecommendationType.INFO,
                        impactArea = "Goals"
                    )
                )
            }

            // 3. Month-over-Month Trend
            if (comparison != null && comparison.hasPreviousMonth) {
                if (comparison.expenseDiffLKR > 0 && comparison.expenseChangePercent >= 10.0) {
                    recs.add(
                        FinancialRecommendation(
                            id = "rec_mom_exp_up",
                            title = "Expenses increased by ${String.format(Locale.US, "%.1f", comparison.expenseChangePercent)}%",
                            description = "Expenditure rose by LKR ${cf(comparison.expenseDiffLKR)} vs ${comparison.previousMonthLabel}. Audit '${topCatName}' to eliminate recurring cost increases.",
                            type = RecommendationType.WARNING,
                            impactArea = "MoM Trend"
                        )
                    )
                } else if (comparison.expenseDiffLKR < 0) {
                    recs.add(
                        FinancialRecommendation(
                            id = "rec_mom_exp_down",
                            title = "Spending reduced by ${String.format(Locale.US, "%.1f", Math.abs(comparison.expenseChangePercent))}%",
                            description = "You spent LKR ${cf(Math.abs(comparison.expenseDiffLKR))} less than in ${comparison.previousMonthLabel}. Excellent spending restraint!",
                            type = RecommendationType.POSITIVE,
                            impactArea = "MoM Trend"
                        )
                    )
                }
            }

            // 4. Surplus Distribution & Rollover
            if (net > 0) {
                recs.add(
                    FinancialRecommendation(
                        id = "rec_surplus_rollover",
                        title = "End-of-month surplus allocation",
                        description = "Use the End-of-Month savings dialog to assign your target percentage to Goal Contributions; any unallocated surplus safely rolls over to next month's dashboard.",
                        type = RecommendationType.ACTION,
                        impactArea = "Rollover & Surplus"
                    )
                )
            }

            return MonthlyHealthSummary(
                headline = headline,
                healthGrade = grade,
                overview = overview,
                savingsRatePercent = savingsRate,
                topExpenseCategory = topCatName,
                topExpenseAmountLKR = topCatAmt,
                discretionaryOverspendCount = overspentDisc.size,
                recommendations = recs
            )
        }

        fun calculateMonthToDateComparison(
            currentMonthLabel: String,
            previousMonthLabel: String?,
            currentIncomes: List<Income>,
            previousIncomes: List<Income>,
            currentExpenses: List<Expense>,
            previousExpenses: List<Expense>,
            monthFormat: SimpleDateFormat = SimpleDateFormat("MMMM yyyy", Locale.US),
            asOfDate: Date = Date()
        ): MonthToDateComparison {
            val calCurrent = Calendar.getInstance().apply {
                try {
                    val p = monthFormat.parse(currentMonthLabel)
                    if (p != null) time = p
                } catch (_: Exception) {}
            }
            val calAsOf = Calendar.getInstance().apply { time = asOfDate }

            val isCurrentMonth = calCurrent.get(Calendar.YEAR) == calAsOf.get(Calendar.YEAR) &&
                calCurrent.get(Calendar.MONTH) == calAsOf.get(Calendar.MONTH)

            val maxDaysInCurrentMonth = calCurrent.getActualMaximum(Calendar.DAY_OF_MONTH)
            val cutoffDay = if (isCurrentMonth) {
                calAsOf.get(Calendar.DAY_OF_MONTH).coerceIn(1, maxDaysInCurrentMonth)
            } else {
                maxDaysInCurrentMonth
            }

            fun getDay(date: Date): Int {
                val c = Calendar.getInstance().apply { time = date }
                return c.get(Calendar.DAY_OF_MONTH)
            }

            val currConfirmed = currentIncomes.filter { it.status == IncomeStatus.CONFIRMED }
            val prevConfirmed = previousIncomes.filter { it.status == IncomeStatus.CONFIRMED }

            val dailyIncomePoints = mutableListOf<DailyCumulativePoint>()
            val dailyExpensePoints = mutableListOf<DailyCumulativePoint>()

            for (day in 1..cutoffDay) {
                val currInc = currConfirmed.filter { getDay(it.dateReceived ?: it.createdAt) <= day }.sumOf { it.amountLKR }
                val prevInc = prevConfirmed.filter { getDay(it.dateReceived ?: it.createdAt) <= day }.sumOf { it.amountLKR }
                dailyIncomePoints.add(
                    DailyCumulativePoint(
                        day = day,
                        currentMonthCumulativeLKR = currInc,
                        previousMonthCumulativeLKR = prevInc
                    )
                )

                val currExp = currentExpenses.filter { getDay(it.date) <= day }.sumOf { it.amountLKR }
                val prevExp = previousExpenses.filter { getDay(it.date) <= day }.sumOf { it.amountLKR }
                dailyExpensePoints.add(
                    DailyCumulativePoint(
                        day = day,
                        currentMonthCumulativeLKR = currExp,
                        previousMonthCumulativeLKR = prevExp
                    )
                )
            }

            val currentIncomeMtd = dailyIncomePoints.lastOrNull()?.currentMonthCumulativeLKR ?: 0.0
            val previousIncomeMtd = dailyIncomePoints.lastOrNull()?.previousMonthCumulativeLKR ?: 0.0
            val currentExpensesMtd = dailyExpensePoints.lastOrNull()?.currentMonthCumulativeLKR ?: 0.0
            val previousExpensesMtd = dailyExpensePoints.lastOrNull()?.previousMonthCumulativeLKR ?: 0.0

            return MonthToDateComparison(
                cutoffDay = cutoffDay,
                currentMonthLabel = currentMonthLabel,
                previousMonthLabel = previousMonthLabel ?: "",
                currentIncomeMtdLKR = currentIncomeMtd,
                previousIncomeMtdLKR = previousIncomeMtd,
                currentExpensesMtdLKR = currentExpensesMtd,
                previousExpensesMtdLKR = previousExpensesMtd,
                dailyIncomePoints = dailyIncomePoints,
                dailyExpensePoints = dailyExpensePoints
            )
        }
    }
}
