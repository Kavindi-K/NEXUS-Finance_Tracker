package com.nexus.financetracker.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.RecurringItemRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Date

/**
 * Background worker (WorkManager) that checks for recurring items due today or
 * tomorrow and fires a local notification for each one.
 *
 * Runs daily via [NotificationScheduler]. Injected by Hilt through [@HiltWorker].
 * Supports FR23 — recurring payment schedule confirmation before posting.
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurringItemRepository: RecurringItemRepository,
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

        // Fetch all active recurring items for the user
        val items = recurringItemRepository.getRecurringItems(userId).first()
            .filter { it.isActive }

        items.forEachIndexed { index, item ->
            val dueDate = item.nextDueDate
            val isDueToday = dueDate.isInRange(todayStart, todayEnd)
            val isDueTomorrow = dueDate.isInRange(tomorrowStart, tomorrowEnd)

            if (isDueToday || isDueTomorrow) {
                // Use a stable notification ID: hash of item ID (avoids stacking duplicates
                // on repeated runs while keeping separate IDs per item)
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
