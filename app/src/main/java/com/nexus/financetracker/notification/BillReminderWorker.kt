package com.nexus.financetracker.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.data.repository.RecurringItemRepository
import com.nexus.financetracker.domain.model.IncomeSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Date

/**
 * Background worker (WorkManager) that checks for recurring bills due today/tomorrow (FR23)
 * and pending income arrivals expected today (FR8), then fires local notifications.
 *
 * Runs daily via [NotificationScheduler]. Injected by Hilt through [@HiltWorker].
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurringItemRepository: RecurringItemRepository,
    private val incomeRepository: IncomeRepository,
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

        // 2. Check pending incomes expected today (FR8)
        val pendingIncomes = incomeRepository.getPendingIncomes(userId).first()
        pendingIncomes.forEach { income ->
            val expectedDate = income.expectedDate ?: return@forEach
            if (expectedDate.isInRange(todayStart, todayEnd)) {
                val notificationId = ("income_" + income.id).hashCode()
                val sourceName = IncomeSource.getDisplayName(income.source)
                NotificationHelper.postPendingIncomeReminder(
                    context = context,
                    notificationId = notificationId,
                    sourceName = sourceName,
                    amountLKR = income.amountLKR
                )
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
