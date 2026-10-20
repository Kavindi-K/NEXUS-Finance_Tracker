package com.nexus.financetracker.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pace status of a savings goal relative to its target deadline (FR16).
 */
enum class PaceStatus {
    INSUFFICIENT_HISTORY,
    AHEAD_OF_PACE,
    ON_TRACK,
    BEHIND_PACE,
    COMPLETED,
    OVERDUE
}

/**
 * Domain model representing a goal's projected completion date and observed savings pace (FR16).
 *
 * Conforms to Technical Document Sections 2.3, 6.5 and Implementation Guide Member 3 - Order 5:
 * - Trailing actual savings rate derived from transaction history since goal startDate
 * - Projected completion date = currentDate + (remainingAmount / monthlySavingsRate)
 * - Flags whether projected date falls before, on, or after deadline in neutral language
 * - Handles insufficient history, zero/negative rate, and overdue targets
 * - Clearly marked as an estimate
 */
data class GoalProjection(
    val monthlySavingsRateLKR: Double,
    val projectedDate: Date?,
    val paceStatus: PaceStatus,
    val statusMessage: String,
    val monthsDifference: Int, // positive = months late, negative = months ahead, 0 = on track
    val hasSufficientHistory: Boolean,
    val isEstimate: Boolean = true
) {
    val formattedProjectedDate: String?
        get() = projectedDate?.let {
            SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(it)
        }

    companion object {
        /**
         * Projects goal completion from observed transaction history.
         *
         * @param goal The savings goal with targetAmount, openingSavings, startDate, and targetDate.
         * @param cumulativeConfirmedIncomeLKR Total confirmed income on or after goal startDate.
         * @param cumulativeExpenseLKR Total expenses on or after goal startDate.
         * @param remainingAmountLKR Remaining amount needed to reach target.
         * @param hasTransactions Whether any confirmed incomes or expenses exist since goal startDate.
         * @param currentDate Reference evaluation date (defaults to now).
         */
        fun project(
            goal: Goal,
            cumulativeConfirmedIncomeLKR: Double,
            cumulativeExpenseLKR: Double,
            remainingAmountLKR: Double,
            hasTransactions: Boolean,
            currentDate: Date = Date()
        ): GoalProjection {
            // Case 1: Goal already completed
            if (remainingAmountLKR <= 0.0) {
                val netSavings = maxOf(0.0, cumulativeConfirmedIncomeLKR - cumulativeExpenseLKR)
                return GoalProjection(
                    monthlySavingsRateLKR = netSavings,
                    projectedDate = currentDate,
                    paceStatus = PaceStatus.COMPLETED,
                    statusMessage = "Goal Achieved!",
                    monthsDifference = 0,
                    hasSufficientHistory = true
                )
            }

            val isOverdue = currentDate.after(goal.targetDate)

            // Case 2: Insufficient history (no transactions recorded yet since startDate)
            if (!hasTransactions) {
                return GoalProjection(
                    monthlySavingsRateLKR = 0.0,
                    projectedDate = null,
                    paceStatus = if (isOverdue) PaceStatus.OVERDUE else PaceStatus.INSUFFICIENT_HISTORY,
                    statusMessage = if (isOverdue) {
                        "Target date passed — Needs transaction history"
                    } else {
                        "Needs transaction history to project completion (estimate)"
                    },
                    monthsDifference = 0,
                    hasSufficientHistory = false
                )
            }

            // Calculate observation elapsed duration since goal startDate
            val elapsedMillis = maxOf(0L, currentDate.time - goal.startDate.time)
            val elapsedDays = maxOf(1L, elapsedMillis / (1000L * 60 * 60 * 24))
            val elapsedMonths = maxOf(1.0, elapsedDays / 30.4375)

            // Net savings observed during observation period
            val netSavingsObserved = cumulativeConfirmedIncomeLKR - cumulativeExpenseLKR
            val monthlySavingsRate = netSavingsObserved / elapsedMonths

            // Case 3: Zero or negative savings rate (expenses >= income)
            if (monthlySavingsRate <= 0.0) {
                val message = if (isOverdue) {
                    "Target date passed — Spending exceeds income"
                } else if (netSavingsObserved == 0.0) {
                    "Zero net savings observed — At this rate, goal cannot be reached"
                } else {
                    "Spending exceeds income — At this rate, goal cannot be reached"
                }
                return GoalProjection(
                    monthlySavingsRateLKR = monthlySavingsRate,
                    projectedDate = null,
                    paceStatus = if (isOverdue) PaceStatus.OVERDUE else PaceStatus.BEHIND_PACE,
                    statusMessage = message,
                    monthsDifference = 0,
                    hasSufficientHistory = true
                )
            }

            // Case 4: Positive savings rate -> Project completion date
            val monthsToComplete = remainingAmountLKR / monthlySavingsRate
            val daysToComplete = (monthsToComplete * 30.4375).toLong()

            val cal = Calendar.getInstance().apply {
                time = currentDate
                add(Calendar.DAY_OF_MONTH, maxOf(1, daysToComplete.toInt()))
            }
            val projectedDate = cal.time

            // Calculate month difference relative to target deadline
            val targetCal = Calendar.getInstance().apply { time = goal.targetDate }
            val projCal = Calendar.getInstance().apply { time = projectedDate }

            val monthsDiff = (projCal.get(Calendar.YEAR) - targetCal.get(Calendar.YEAR)) * 12 +
                (projCal.get(Calendar.MONTH) - targetCal.get(Calendar.MONTH))

            val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(projectedDate)

            val (status, message) = when {
                isOverdue -> {
                    PaceStatus.OVERDUE to "Target date passed — Projected completion: $dateStr (estimate)"
                }
                monthsDiff > 0 -> {
                    val lateStr = if (monthsDiff == 1) "1 month late" else "$monthsDiff months late"
                    PaceStatus.BEHIND_PACE to "Slightly behind pace — At this rate: $dateStr, $lateStr (estimate)"
                }
                monthsDiff < 0 -> {
                    val monthsAhead = -monthsDiff
                    val aheadStr = if (monthsAhead == 1) "1 month ahead" else "$monthsAhead months ahead"
                    PaceStatus.AHEAD_OF_PACE to "Ahead of pace — At this rate: $dateStr ($aheadStr) (estimate)"
                }
                else -> {
                    PaceStatus.ON_TRACK to "On track — Projected completion: $dateStr (estimate)"
                }
            }

            return GoalProjection(
                monthlySavingsRateLKR = monthlySavingsRate,
                projectedDate = projectedDate,
                paceStatus = status,
                statusMessage = message,
                monthsDifference = monthsDiff,
                hasSufficientHistory = true
            )
        }
    }
}
