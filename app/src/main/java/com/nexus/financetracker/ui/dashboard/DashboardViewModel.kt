package com.nexus.financetracker.ui.dashboard



import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.google.firebase.auth.FirebaseAuth

import com.nexus.financetracker.data.repository.AuthRepository

import com.nexus.financetracker.data.repository.ContributionManager

import com.nexus.financetracker.data.repository.CategoryRepository

import com.nexus.financetracker.data.repository.ExpenseRepository

import com.nexus.financetracker.data.repository.GoalRepository

import com.nexus.financetracker.data.repository.IncomeRepository

import com.nexus.financetracker.data.util.NetworkMonitor

import com.nexus.financetracker.domain.model.Category

import com.nexus.financetracker.domain.model.Expense

import com.nexus.financetracker.domain.model.ExpenseClassification

import com.nexus.financetracker.domain.model.Goal

import com.nexus.financetracker.domain.model.GoalCalculation

import com.nexus.financetracker.domain.model.GoalPriority

import com.nexus.financetracker.domain.model.Income

import com.nexus.financetracker.domain.model.IncomeSource

import com.nexus.financetracker.domain.model.IncomeStatus

import com.nexus.financetracker.domain.model.UserProfile

import com.nexus.financetracker.ui.expense.DEFAULT_EXPENSE_CATEGORIES

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

    private val authRepository: AuthRepository,

    private val auth: FirebaseAuth,

    private val networkMonitor: NetworkMonitor,

    private val contributionManager: ContributionManager

) : ViewModel() {



    private val _uiState = MutableStateFlow(DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()



    init {

        loadDashboardSummary()

        observeNetworkStatus()

    }



    private fun observeNetworkStatus() {

        viewModelScope.launch {

            networkMonitor.isOnline.collect { online ->

                _uiState.value = _uiState.value.copy(isOffline = !online)

            }

        }

        viewModelScope.launch {

            contributionManager.savingsUpdateTrigger.collect {

                loadDashboardSummary()

            }

        }

    }



    fun loadDashboardSummary() {

        val user = auth.currentUser

        val userId = user?.uid ?: return

        val currentGreeting = getGreeting()

        val displayName = UserProfile.formatCapitalizedName(user.displayName?.trim()?.split("\\s+".toRegex())?.firstOrNull { it.isNotBlank() } ?: "Kavindu")

        val (startOfMonth, endOfMonth) = getMonthRange()

        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(startOfMonth)



        _uiState.value = _uiState.value.copy(

            isLoading = true,

            errorMessage = null,

            greeting = currentGreeting,

            userName = displayName,

            selectedMonthLabel = monthLabel

        )



        viewModelScope.launch {

            combine(

                incomeRepository.getIncomes(userId).catch { emit(emptyList()) },

                expenseRepository.getExpenses(userId).catch { emit(emptyList()) },

                goalRepository.getGoals(userId).catch { emit(emptyList()) },

                categoryRepository.getCategories(userId).catch { emit(emptyList()) },

                authRepository.getUserProfile(userId).catch { emit(null) }

            ) { incomes, expenses, goals, categories, userProfile ->

                val rawName = userProfile?.displayName?.takeIf { it.isNotBlank() }

                    ?: user.displayName?.takeIf { it.isNotBlank() }

                    ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }

                    ?: "User"

                val resolvedName = UserProfile.formatCapitalizedName(rawName.trim().split("\\s+".toRegex()).firstOrNull { it.isNotBlank() } ?: "User")



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



                // 3. Category Estimation Budget Deduction logic:

                // Estimated values are reduced from income at beginning of the month.

                // For categories where actual expenses exceed estimation, the excess is also reduced from total income.

                val estimatedCategories = categories.filter { it.estimatedAmount != null && (it.estimatedAmount ?: 0.0) > 0.0 }

                val estimatedCategoryIds = estimatedCategories.map { it.id }.toSet()



                var totalEstimatedBudgetDeduction = 0.0

                for (cat in estimatedCategories) {

                    val est = cat.estimatedAmount ?: 0.0

                    val actualForCat = monthExpenses.filter { it.categoryId == cat.id }.sumOf { it.amountLKR }

                    val deduction = if (actualForCat > est) {

                        est + (actualForCat - est)

                    } else {

                        est

                    }

                    totalEstimatedBudgetDeduction += deduction

                }



                val nonEstimatedExpenses = monthExpenses.filter { it.categoryId !in estimatedCategoryIds }.sumOf { it.amountLKR }

                val effectiveDeductions = if (estimatedCategories.isNotEmpty()) {

                    totalEstimatedBudgetDeduction + nonEstimatedExpenses

                } else {

                    totalExpense

                }



                // Net result for the month: Total Income minus effective category deductions

                val netResult = totalIncome - totalExpense



                // Monthly savings: Income - Expenses = Savings (FR - Savings Allocation & Rollover)

                val monthlySavings = maxOf(0.0, totalIncome - totalExpense)



                val nextMonthCal = Calendar.getInstance().apply {

                    time = startOfMonth

                    add(Calendar.MONTH, 1)

                }

                val nextMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(nextMonthCal.time)



                val prevMonthCal = Calendar.getInstance().apply {

                    time = startOfMonth

                    add(Calendar.MONTH, -1)

                }

                val prevMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(prevMonthCal.time)



                // Rolled over savings from previous month(s)

                val explicitRollover = contributionManager.getRolloverSavings(userId, monthLabel)

                val isPrevMonthReviewed = contributionManager.isMonthlySavingsReviewed(userId, prevMonthLabel)

                val (prevStart, prevEnd) = getMonthRange(prevMonthCal)

                val prevConfirmedIncomes = incomes.filter { income ->

                    income.status == IncomeStatus.CONFIRMED &&

                        income.dateReceived != null &&

                        !income.dateReceived.before(prevStart) &&

                        !income.dateReceived.after(prevEnd)

                }.sumOf { it.amountLKR }

                val prevExpensesSum = expenses.filter { expense ->

                    !expense.date.before(prevStart) &&

                        !expense.date.after(prevEnd)

                }.sumOf { it.amountLKR }

                val autoRollover = if (explicitRollover == 0.0 && !isPrevMonthReviewed) {

                    maxOf(0.0, prevConfirmedIncomes - prevExpensesSum)

                } else {

                    0.0

                }

                val rolledOverSavings = if (explicitRollover > 0.0) explicitRollover else autoRollover

                val totalBalance = rolledOverSavings + netResult

                val isCurrentMonthReviewed = contributionManager.isMonthlySavingsReviewed(userId, monthLabel)

                val isEndOfMonthWindow = isEndOfMonth()

                val allocationState = resolveSavingsAllocationState(
                    isPrevMonthReviewed = isPrevMonthReviewed,
                    prevMonthLabel = prevMonthLabel,
                    prevIncomeLKR = prevConfirmedIncomes,
                    prevExpenseLKR = prevExpensesSum,
                    isCurrentMonthReviewed = isCurrentMonthReviewed,
                    currentMonthLabel = monthLabel,
                    currentIncomeLKR = totalIncome,
                    currentExpenseLKR = totalExpense,
                    nextMonthLabel = nextMonthLabel,
                    isEndOfMonthWindow = isEndOfMonthWindow
                )



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



                // 5. Active priority goal summary (FR15, FR17)

                // Only high-priority ONGOING goals are displayed on the dashboard (achieved and overdue goals excluded)

                val priorityGoals = goals.filter { it.isActive && it.priority.equals(GoalPriority.HIGH, ignoreCase = true) }

                val priorityGoalItems = priorityGoals.map { goal ->

                    PriorityGoalItem(

                        goal = goal,

                        calculation = GoalCalculation.calculate(

                            goal = goal,

                            incomes = incomes,

                            expenses = expenses

                        )

                    )

                }.filter { item ->

                    val isAchieved = item.calculation.isCompleted ||

                        item.calculation.remainingAmountLKR <= 0.0 ||

                        item.goal.openingSavingsLKR >= item.goal.targetAmountLKR

                    val isOverdue = item.calculation.isOverdue && item.calculation.daysRemaining < 0L

                    !isAchieved && !isOverdue

                }

                val activeGoal = priorityGoalItems.firstOrNull()?.goal

                val goalCalculation = priorityGoalItems.firstOrNull()?.calculation



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

                    greeting = getGreeting(),

                    userName = resolvedName,

                    userPhotoUrl = userProfile?.photoUrl,

                    selectedMonthLabel = monthLabel,

                    nextMonthLabel = nextMonthLabel,

                    totalConfirmedIncomeLKR = totalIncome,

                    totalExpensesLKR = totalExpense,

                    netResultLKR = netResult,

                    monthlySavingsLKR = monthlySavings,

                    rolledOverSavingsLKR = rolledOverSavings,

                    totalBalanceLKR = totalBalance,

                    isEndOfMonth = isEndOfMonthWindow,

                    isCurrentMonthSavingsReviewed = isCurrentMonthReviewed,

                    showSavingsAllocationBanner = allocationState.showBanner,

                    allocationMonthLabel = allocationState.allocationMonthLabel,

                    allocationTargetMonthLabel = allocationState.targetMonthLabel,

                    allocationSavingsLKR = allocationState.savingsLKR,

                    allocationIncomeLKR = allocationState.incomeLKR,

                    allocationExpensesLKR = allocationState.expenseLKR,

                    committedExpensesLKR = committed,

                    discretionaryExpensesLKR = discretionary,

                    committedPercentage = committedPercent,

                    discretionaryPercentage = discretionaryPercent,

                    activeGoal = activeGoal,

                    activeGoalCalculation = goalCalculation,

                    priorityGoals = priorityGoalItems,

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



    /**

     * Allocates a percentage of monthly savings to the Goal Contribution list,

     * and rolls over the remaining savings into the subsequent month's Total Amount card.

     */

    fun allocateMonthlySavings(

        percent: Double,

        onSuccess: (contributionAmount: Double, rolloverAmount: Double) -> Unit = { _, _ -> }

    ) {

        val user = auth.currentUser ?: return

        val userId = user.uid

        val savings = if (_uiState.value.allocationSavingsLKR > 0.0) {
            _uiState.value.allocationSavingsLKR
        } else {
            _uiState.value.monthlySavingsLKR
        }

        if (savings <= 0.0) return



        val validPercent = percent.coerceIn(0.0, 100.0)

        val contributionAmount = savings * (validPercent / 100.0)

        val rolloverAmount = savings - contributionAmount



        val (startOfMonth, _) = getMonthRange()

        val currentMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(startOfMonth)

        val prevMonthCal = Calendar.getInstance().apply {

            time = startOfMonth

            add(Calendar.MONTH, -1)

        }

        val prevMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(prevMonthCal.time)

        val nextMonthCal = Calendar.getInstance().apply {

            time = startOfMonth

            add(Calendar.MONTH, 1)

        }

        val nextMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(nextMonthCal.time)



        val monthLabelToRecord = _uiState.value.allocationMonthLabel.ifBlank {
            if (!contributionManager.isMonthlySavingsReviewed(userId, prevMonthLabel)) prevMonthLabel else currentMonthLabel
        }

        val targetMonthLabelToRecord = _uiState.value.allocationTargetMonthLabel.ifBlank {
            if (monthLabelToRecord == prevMonthLabel) currentMonthLabel else nextMonthLabel
        }



        contributionManager.recordMonthlySavingsAllocation(

            userId = userId,

            monthLabel = monthLabelToRecord,

            totalSavingsLKR = savings,

            allocatedPercent = validPercent,

            contributionAmountLKR = contributionAmount,

            rolloverAmountLKR = rolloverAmount,

            nextMonthLabel = targetMonthLabelToRecord

        )

        loadDashboardSummary()

        onSuccess(contributionAmount, rolloverAmount)

    }



    companion object {

        /**

         * Resolves the greeting string based on the current hour of the day:

         * - 00:00 - 11:59 -> "Good Morning,"

         * - 12:00 - 16:59 -> "Good Afternoon,"

         * - 17:00 - 23:59 -> "Good Evening,"

         */

        /**

         * Resolves whether the current date falls within the end-of-the-month review window (last 5 days).

         */

        fun isEndOfMonth(calendar: Calendar = Calendar.getInstance()): Boolean {

            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            return day >= (maxDay - 4)

        }



        /**

         * Calculates monthly savings and splits between goal contributions and next-month rollover.

         * Savings = max(0.0, Income - Expenses)

         * Contribution = Savings * (Percent / 100)

         * Rollover = Savings - Contribution

         */

        /**
         * Resolves whether the savings allocation banner should be shown, and for which month.
         *
         * Rules:
         * 1. Appears ONLY at the end of the month (or for preceding month if not yet reviewed).
         * 2. When allocation is completed for a month, it disappears completely.
         * 3. Priority:
         *    - If previous month (e.g. October 2026) has not been reviewed yet:
         *      Show previous month's allocation (rolling over into current month).
         *    - If previous month is reviewed, but current month is within the end-of-month window
         *      and current month is not reviewed and has savings:
         *      Show current month's allocation (rolling over into next month).
         *    - Otherwise (middle of the month, or allocation completed):
         *      Do not show banner (banner is disappeared).
         */
        fun resolveSavingsAllocationState(
            isPrevMonthReviewed: Boolean,
            prevMonthLabel: String,
            prevIncomeLKR: Double,
            prevExpenseLKR: Double,
            isCurrentMonthReviewed: Boolean,
            currentMonthLabel: String,
            currentIncomeLKR: Double,
            currentExpenseLKR: Double,
            nextMonthLabel: String,
            isEndOfMonthWindow: Boolean
        ): MonthAllocationState {
            val prevSavings = maxOf(0.0, prevIncomeLKR - prevExpenseLKR)
            val currentSavings = maxOf(0.0, currentIncomeLKR - currentExpenseLKR)

            // 1. Previous month allocation (e.g. October 2026 in early November):
            val hasPrevSavings = prevSavings > 0.0 || (prevIncomeLKR == 0.0 && prevExpenseLKR == 0.0 && currentSavings > 0.0)
            if (!isPrevMonthReviewed && hasPrevSavings) {
                val income = if (prevIncomeLKR > 0.0 || prevExpenseLKR > 0.0) prevIncomeLKR else currentIncomeLKR
                val expense = if (prevIncomeLKR > 0.0 || prevExpenseLKR > 0.0) prevExpenseLKR else currentExpenseLKR
                val savings = if (prevSavings > 0.0) prevSavings else currentSavings
                return MonthAllocationState(
                    showBanner = true,
                    allocationMonthLabel = prevMonthLabel,
                    targetMonthLabel = currentMonthLabel,
                    savingsLKR = savings,
                    incomeLKR = income,
                    expenseLKR = expense
                )
            }

            // 2. Current month allocation (only at the end of the month):
            if (isPrevMonthReviewed && isEndOfMonthWindow && !isCurrentMonthReviewed && currentSavings > 0.0) {
                return MonthAllocationState(
                    showBanner = true,
                    allocationMonthLabel = currentMonthLabel,
                    targetMonthLabel = nextMonthLabel,
                    savingsLKR = currentSavings,
                    incomeLKR = currentIncomeLKR,
                    expenseLKR = currentExpenseLKR
                )
            }

            // 3. Otherwise (middle of month, or review already done): Disappeared!
            return MonthAllocationState(
                showBanner = false,
                allocationMonthLabel = "",
                targetMonthLabel = "",
                savingsLKR = 0.0,
                incomeLKR = 0.0,
                expenseLKR = 0.0
            )
        }



        fun calculateSavingsAllocation(

            income: Double,

            expense: Double,

            contributionPercent: Double

        ): Triple<Double, Double, Double> {

            val savings = maxOf(0.0, income - expense)

            val validPercent = contributionPercent.coerceIn(0.0, 100.0)

            val contribution = savings * (validPercent / 100.0)

            val rollover = savings - contribution

            return Triple(savings, contribution, rollover)

        }



        fun getGreeting(calendar: Calendar = Calendar.getInstance()): String {

            return when (calendar.get(Calendar.HOUR_OF_DAY)) {

                in 0..11 -> "Good Morning,"

                in 12..16 -> "Good Afternoon,"

                else -> "Good Evening,"

            }

        }



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

                resolveCategoryName(expense.categoryId, categoryMap)

            }.mapValues { (_, list) -> list.sumOf { it.amountLKR } }



            val maxSpending = categoryTotals.values.maxOrNull() ?: 1.0

            val totalExpenses = monthExpenses.sumOf { it.amountLKR }



            return categoryTotals.map { (catName, sum) ->

                val pct = if (totalExpenses > 0) ((sum / totalExpenses) * 100.0).roundToInt() else 0

                CategorySpending(

                    name = catName,

                    amountLKR = sum,

                    fraction = if (maxSpending > 0) (sum / maxSpending).toFloat().coerceIn(0.05f, 1f) else 0f,

                    percentageOfTotal = pct

                )

            }.sortedByDescending { it.amountLKR }

        }



        /**

         * Resolves category ID to a human-readable display name.

         * Checks user categories, falls back to default categories, and cleans raw IDs (e.g. cat_food -> Food & Dining).

         */

        fun resolveCategoryName(categoryId: String, categoryMap: Map<String, String>): String {

            // 1. Check user custom categories

            categoryMap[categoryId]?.takeIf { it.isNotBlank() }?.let { return it }



            // 2. Check standard default categories

            DEFAULT_EXPENSE_CATEGORIES.find {

                it.id.equals(categoryId, ignoreCase = true) ||

                it.id.replace("_", "-").equals(categoryId, ignoreCase = true)

            }?.name?.let { return it }



            // 3. Fallback: clean up raw ID (strip "cat_" or "cat-", replace underscores/hyphens with spaces)

            val cleaned = categoryId.trim()

                .removePrefix("cat_")

                .removePrefix("cat-")

                .removePrefix("CAT_")

                .removePrefix("CAT-")

                .replace('_', ' ')

                .replace('-', ' ')

                .trim()



            if (cleaned.isBlank()) return "Other"



            return cleaned.split("\\s+".toRegex()).joinToString(" ") { word ->

                word.replaceFirstChar {

                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()

                }

            }

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

                val name = when (source.lowercase().trim()) {

                    IncomeSource.SALARY, "salary" -> "Base Salary"

                    IncomeSource.FREELANCE, "freelance" -> "Freelance"

                    IncomeSource.CRYPTO, "crypto" -> "Crypto"

                    IncomeSource.ADSENSE, "adsense" -> "AdSense (USD)"

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

 * Priority goal item containing Goal and real-time calculation.

 */

data class PriorityGoalItem(

    val goal: Goal,

    val calculation: GoalCalculation

)



/**

 * Immutable UI state for the Dashboard screen (FR17-FR20, FR22).

 */

/**
 * State for end-of-month savings allocation review.
 */
data class MonthAllocationState(
    val showBanner: Boolean = false,
    val allocationMonthLabel: String = "",
    val targetMonthLabel: String = "",
    val savingsLKR: Double = 0.0,
    val incomeLKR: Double = 0.0,
    val expenseLKR: Double = 0.0
)



data class DashboardUiState(

    val isLoading: Boolean = true,

    val isOffline: Boolean = false,

    val greeting: String = DashboardViewModel.getGreeting(),

    val userName: String = "User",

    val userPhotoUrl: String? = null,

    val selectedMonthLabel: String = "",

    val nextMonthLabel: String = "",

    val totalConfirmedIncomeLKR: Double = 0.0,

    val totalExpensesLKR: Double = 0.0,

    val netResultLKR: Double = 0.0,

    val monthlySavingsLKR: Double = 0.0,

    val rolledOverSavingsLKR: Double = 0.0,

    val totalBalanceLKR: Double = 0.0,

    val isEndOfMonth: Boolean = false,

    val isCurrentMonthSavingsReviewed: Boolean = false,

    val showSavingsAllocationBanner: Boolean = false,

    val allocationMonthLabel: String = "",

    val allocationTargetMonthLabel: String = "",

    val allocationSavingsLKR: Double = 0.0,

    val allocationIncomeLKR: Double = 0.0,

    val allocationExpensesLKR: Double = 0.0,

    val committedExpensesLKR: Double = 0.0,

    val discretionaryExpensesLKR: Double = 0.0,

    val committedPercentage: Int = 0,

    val discretionaryPercentage: Int = 0,

    val activeGoal: Goal? = null,

    val activeGoalCalculation: GoalCalculation? = null,

    val priorityGoals: List<PriorityGoalItem> = emptyList(),

    val categorySpendings: List<CategorySpending> = emptyList(),

    val incomeSources: List<IncomeSourceSpending> = emptyList(),

    val hasExpenseRecords: Boolean = false,

    val hasIncomeRecords: Boolean = false,

    val errorMessage: String? = null

) {

    // Backward-compatible properties

    val totalIncome: Double get() = totalConfirmedIncomeLKR

    val totalExpense: Double get() = totalExpensesLKR

    val balance: Double get() = totalBalanceLKR

}

