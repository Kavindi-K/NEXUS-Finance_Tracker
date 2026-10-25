package com.nexus.financetracker.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the [BillReminderWorker] to run once per day.
 *
 * The worker fires at approximately 8:00 AM each morning. WorkManager calculates
 * the initial delay from now → 8:00 AM, then repeats every 24 hours.
 *
 * Using [ExistingPeriodicWorkPolicy.KEEP] ensures re-scheduling on app start does
 * not reset or duplicate the existing periodic task.
 */
object NotificationScheduler {

    private const val WORK_TAG = "nexus_bill_reminder_daily"
    private const val REMINDER_HOUR = 8   // 8:00 AM
    private const val REMINDER_MINUTE = 0

    /**
     * Schedules (or keeps existing) the daily bill reminder worker.
     * Safe to call on every app start — duplicate requests are ignored.
     *
     * @param context Application context.
     */
    fun scheduleDailyBillReminder(context: Context) {
        val initialDelay = calculateInitialDelay()

        val workRequest = PeriodicWorkRequestBuilder<BillReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP, // Do not reset if already scheduled
            workRequest
        )
    }

    /**
     * Calculates milliseconds from now until the next 8:00 AM.
     * If 8:00 AM has already passed today, schedules for tomorrow's 8:00 AM.
     */
    private fun calculateInitialDelay(): Long {
        val now = Calendar.getInstance()

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, REMINDER_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If 8 AM today has already passed, target tomorrow's 8 AM
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return target.timeInMillis - now.timeInMillis
    }
}
