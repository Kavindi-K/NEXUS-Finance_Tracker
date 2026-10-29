package com.nexus.financetracker.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.data.repository.RecurringItemRepository
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.GoalPriority
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.PaceStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Date

/**
 * Background worker (WorkManager) that runs daily at 8:00 AM to check:
 * 1. Recurring bills due today or tomorrow (FR23)
 * 2. Pending income arrivals expected today (FR8)
 * 3. Savings goals achievements, 1-week deadline reminders, and pace alerts (FR15–FR17)
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurringItemRepository: RecurringItemRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val goalRepository: GoalRepository,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val userId = auth.currentUser?.uid
            ?: return Result.success() // No logged-in user — nothing to check

        // Ensure the notification channel exists before posting
        NotificationHelper.createNotificationChannel(context)

        val now = Calendar.getInstance()
        val todayStart = now.startOfDay()
        val todayEnd = now.endOfDay()
        val tomorrowStart = now.also { it.add(Calendar.DAY_OF_YEAR, 1) }.startOfDay()
        val tomorrowEnd = now.endOfDay()

        // 1. Check recurring items due today or tomorrow (FR23)
        val items = recurringItemRepository.getRecurringItems(userId).first()
            .filter { it.isActive }

        items.forEach { item ->
            val dueDate = item.nextDueDate
            val isDueToday = dueDate.isInRange(todayStart, todayEnd)
            val isDueTomorrow = dueDate.isInRange(tomorrowStart, tomorrowEnd)

            if (isDueToday || isDueTomorrow) {
                val notificationId = item.id.hashCode()
                NotificationHelper.postBillReminder(
                    context = context,
                    notificationId = notificationId,
                    title = item.title,
                    amountLKR = item.amountLKR,
                    isDueToday = isDueToday
                )
            }
        }

        // (Pending income arrival notifications are handled at 6:00 PM daily by IncomeReminderWorker)

        // 2. Check Savings Goals: Achieved, 1-week deadline, and pace alerts (FR15–FR17)
        val goals = goalRepository.getGoals(userId).first().filter { it.isActive }
        if (goals.isNotEmpty()) {
            val allIncomes = incomeRepository.getIncomes(userId).first()
            val allExpenses = expenseRepository.getExpenses(userId).first()

            goals.forEach { goal ->
                val calc = GoalCalculation.calculate(goal, allIncomes, allExpenses, now.time)
                val priorityName = GoalPriority.getDisplayName(goal.priority)

                when {
                    // Achieved goal
                    calc.isCompleted -> {
                        NotificationHelper.postGoalReminder(
                            context = context,
                            notificationId = ("goal_achieved_" + goal.id).hashCode(),
                            title = "🎉 Goal Achieved: ${goal.title}!",
                            message = "Congratulations! You've reached 100% of your ${goal.title} goal!"
                        )
                    }
                    // 1 week before deadline reminder
                    calc.daysRemaining in 1L..7L -> {
                        val daysText = if (calc.daysRemaining == 1L) "1 day" else "${calc.daysRemaining} days"
                        NotificationHelper.postGoalReminder(
                            context = context,
                            notificationId = ("goal_deadline_" + goal.id).hashCode(),
                            title = "🎯 $priorityName Priority: ${goal.title}",
                            message = "You have $daysText left to achieve your target (LKR %,.0f).".format(goal.targetAmountLKR)
                        )
                    }
                    // Pace alert (behind pace)
                    (calc.paceStatus == PaceStatus.BEHIND_PACE || calc.paceStatus == PaceStatus.OVERDUE) && calc.requiredMonthlySavingsLKR > 0 -> {
                        NotificationHelper.postGoalReminder(
                            context = context,
                            notificationId = ("goal_pace_" + goal.id).hashCode(),
                            title = "⚠️ ${goal.title}: Target Pace Alert",
                            message = "You need LKR %,.0f saved this month to stay on track for your target date.".format(calc.requiredMonthlySavingsLKR)
                        )
                    }
                    // Milestone threshold (e.g. 25%, 50%, 75%)
                    calc.progressPercentage >= 25.0 -> {
                        val milestone = when {
                            calc.progressPercentage >= 75.0 -> 75
                            calc.progressPercentage >= 50.0 -> 50
                            else -> 25
                        }
                        NotificationHelper.postGoalReminder(
                            context = context,
                            notificationId = ("goal_milestone_${milestone}_" + goal.id).hashCode(),
                            title = "🏆 Milestone: ${goal.title}",
                            message = "Congratulations! You've reached $milestone% of your ${goal.title} goal!"
                        )
                    }
                }
            }
        }

        return Result.success()
    }

    // --- Private calendar helpers ---

    private fun Calendar.startOfDay(): Date {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        return time
    }

    private fun Calendar.endOfDay(): Date {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
        return time
    }

    private fun Date.isInRange(start: Date, end: Date): Boolean =
        !before(start) && !after(end)
}
