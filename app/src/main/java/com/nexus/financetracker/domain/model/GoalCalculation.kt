package com.nexus.financetracker.domain.model

import java.util.Calendar
import java.util.Date

/**
 * Represents the calculated financial progress and required savings rate for a savings goal (FR15).
 */
data class GoalCalculation(
    val goal: Goal,
    val estimatedCurrentSavingsLKR: Double,
    val remainingAmountLKR: Double,
    val progressFraction: Float,
    val progressPercentage: Double,
    val monthsRemaining: Int,
    val requiredMonthlySavingsLKR: Double,
    val cumulativeConfirmedIncomeLKR: Double,
    val cumulativeExpenseLKR: Double,
    val isCompleted: Boolean,
    val isOverdue: Boolean,
    val daysRemaining: Long = 0L,
    val projection: GoalProjection = GoalProjection.project(
        goal = goal,
        cumulativeConfirmedIncomeLKR = cumulativeConfirmedIncomeLKR,
        cumulativeExpenseLKR = cumulativeExpenseLKR,
        remainingAmountLKR = remainingAmountLKR,
        hasTransactions = (cumulativeConfirmedIncomeLKR > 0.0 || cumulativeExpenseLKR > 0.0)
    )
) {
    val actualMonthlyAverageLKR: Double get() = projection.monthlySavingsRateLKR
    val projectedCompletionDate: Date? get() = projection.projectedDate
    val paceStatus: PaceStatus get() = projection.paceStatus
    val paceMessage: String get() = projection.statusMessage
    val daysRemainingText: String
        get() = when {
            daysRemaining > 1L -> "$daysRemaining days left"
            daysRemaining == 1L -> "1 day left"
            daysRemaining == 0L -> "Due today"
            else -> "${-daysRemaining} days overdue"
        }
    companion object {
        /**
         * Computes the real-time financial trajectory for the given goal against the user's
         * recorded incomes and expenses.
         */
        fun calculate(
            goal: Goal,
            incomes: List<Income>,
            expenses: List<Expense>,
            currentDate: Date = Date()
        ): GoalCalculation {
            val goalStartTime = goal.startDate.time

            // 1. Cumulative confirmed income on or after goal startDate (pending strictly excluded)
            val relevantIncomes = incomes.filter { income ->
                income.status == IncomeStatus.CONFIRMED &&
                    income.dateReceived != null &&
                    income.dateReceived.time >= goalStartTime
            }
            val cumulativeConfirmedIncome = relevantIncomes.sumOf { it.amountLKR }

            // 2. Cumulative expenses on or after goal startDate (pre-goal expenses excluded)
            val relevantExpenses = expenses.filter { expense ->
                expense.date.time >= goalStartTime
            }
            val cumulativeExpense = relevantExpenses.sumOf { it.amountLKR }

            // 3. FR15: Estimated current savings
            val estimatedCurrentSavings = goal.openingSavingsLKR + cumulativeConfirmedIncome - cumulativeExpense

            // 4. Remaining amount needed (cannot be negative)
            val remainingAmount = maxOf(0.0, goal.targetAmountLKR - estimatedCurrentSavings)

            // 5. Months remaining until targetDate
            val monthsRemaining = calculateMonthsRemaining(currentDate, goal.targetDate)
            val isOverdue = currentDate.after(goal.targetDate) && remainingAmount > 0

            // 6.required monthly savings = max(0, target - estimatedCurrentSavings) / monthsRemaining
            val requiredMonthlySavings = if (remainingAmount <= 0.0) {
                0.0
            } else if (monthsRemaining > 0) {
                remainingAmount / monthsRemaining
            } else {
                remainingAmount
            }

            // 7. Progress metrics
            val progressFraction = if (goal.targetAmountLKR > 0) {
                (estimatedCurrentSavings / goal.targetAmountLKR).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            val progressPercentage = if (goal.targetAmountLKR > 0) {
                ((estimatedCurrentSavings / goal.targetAmountLKR) * 100.0).coerceAtLeast(0.0)
            } else {
                0.0
            }

            val hasTransactions = relevantIncomes.isNotEmpty() || relevantExpenses.isNotEmpty()
            val projection = GoalProjection.project(
                goal = goal,
                cumulativeConfirmedIncomeLKR = cumulativeConfirmedIncome,
                cumulativeExpenseLKR = cumulativeExpense,
                remainingAmountLKR = remainingAmount,
                hasTransactions = hasTransactions,
                currentDate = currentDate
            )

            val daysRemaining = calculateDaysRemaining(currentDate, goal.targetDate)

            return GoalCalculation(
                goal = goal,
                estimatedCurrentSavingsLKR = estimatedCurrentSavings,
                remainingAmountLKR = remainingAmount,
                progressFraction = progressFraction,
                progressPercentage = progressPercentage,
                monthsRemaining = monthsRemaining,
                requiredMonthlySavingsLKR = requiredMonthlySavings,
                cumulativeConfirmedIncomeLKR = cumulativeConfirmedIncome,
                cumulativeExpenseLKR = cumulativeExpense,
                isCompleted = estimatedCurrentSavings >= goal.targetAmountLKR,
                isOverdue = isOverdue,
                daysRemaining = daysRemaining,
                projection = projection
            )
        }

        /**
         * Calculates full/calendar months remaining between referenceDate and targetDate.
         * If targetDate is in the future but less than 1 month away, returns at least 1 month
         * to reflect the current active period and avoid division by zero.
         * If targetDate is at or before referenceDate, returns 0.
         */
        fun calculateMonthsRemaining(referenceDate: Date, targetDate: Date): Int {
            if (targetDate.time <= referenceDate.time) {
                return 0
            }

            val startCal = Calendar.getInstance().apply { time = referenceDate }
            val targetCal = Calendar.getInstance().apply { time = targetDate }

            var months = (targetCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)) * 12 +
                (targetCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH))

            if (targetCal.get(Calendar.DAY_OF_MONTH) < startCal.get(Calendar.DAY_OF_MONTH)) {
                months--
            }

            return maxOf(1, months)
        }

        /**
         * Calculates full days remaining between referenceDate and targetDate at midnight boundary.
         * Returns positive number if targetDate is in future, 0 if due today, negative if overdue.
         */
        fun calculateDaysRemaining(referenceDate: Date, targetDate: Date): Long {
            val refCal = Calendar.getInstance().apply {
                time = referenceDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val targetCal = Calendar.getInstance().apply {
                time = targetDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffHours = (targetCal.timeInMillis - refCal.timeInMillis).toDouble() / (1000.0 * 60 * 60)
            return kotlin.math.round(diffHours / 24.0).toLong()
        }
    }
}
