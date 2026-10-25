package com.nexus.financetracker.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker running daily at 8:30 PM (FR9).
 * Fires a gentle reminder to prompt users to log forgotten cash and daily expenses.
 */
@HiltWorker
class EveningQuickLogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        NotificationHelper.createNotificationChannel(context)
        NotificationHelper.postDailyQuickLogReminder(context)
        return Result.success()
    }
}
