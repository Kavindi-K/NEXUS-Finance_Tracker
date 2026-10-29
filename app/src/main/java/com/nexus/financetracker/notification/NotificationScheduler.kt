package com.nexus.financetracker.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Manages periodic scheduling of background workers:
 * 1. [BillReminderWorker]: Daily at 8:00 AM (Recurring bills & savings goal alerts)
 * 2. [IncomeReminderWorker]: Daily at 6:00 PM (Daily income quick-log & pending income arrival alerts)
 * 3. [EveningQuickLogWorker]: Daily at 8:30 PM (Daily expense quick-log)
 *
 * Using [ExistingPeriodicWorkPolicy.KEEP] ensures re-scheduling on app start does
 * not reset or duplicate the existing periodic tasks.
 */
object NotificationScheduler {

    private const val BILL_WORK_TAG = "nexus_bill_reminder_daily"
    private const val BILL_REMINDER_HOUR = 8   // 8:00 AM
    private const val BILL_REMINDER_MINUTE = 0

    private const val INCOME_WORK_TAG = "nexus_income_reminder_daily"
    private const val INCOME_REMINDER_HOUR = 18 // 6:00 PM
    private const val INCOME_REMINDER_MINUTE = 0

    private const val EVENING_WORK_TAG = "nexus_evening_quick_log_daily"
    private const val EVENING_HOUR = 20  // 8:30 PM
    private const val EVENING_MINUTE = 30

    /**
     * Schedules the daily bill and goal reminder worker at 8:00 AM.
     */
    fun scheduleDailyBillReminder(context: Context) {
        val initialDelay = calculateDelay(BILL_REMINDER_HOUR, BILL_REMINDER_MINUTE)

        val workRequest = PeriodicWorkRequestBuilder<BillReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BILL_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Schedules the daily income reminder worker at 6:00 PM.
     * Fires pending income arrival alerts and the daily income quick-log prompt.
     */
    fun scheduleDailyIncomeReminder(context: Context) {
        val initialDelay = calculateDelay(INCOME_REMINDER_HOUR, INCOME_REMINDER_MINUTE)

        val workRequest = PeriodicWorkRequestBuilder<IncomeReminderWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            INCOME_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Schedules the daily 8:30 PM quick-log expense reminder (FR9).
     */
    fun scheduleDailyEveningReminder(context: Context) {
        val initialDelay = calculateDelay(EVENING_HOUR, EVENING_MINUTE)

        val workRequest = PeriodicWorkRequestBuilder<EveningQuickLogWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EVENING_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Calculates milliseconds from now until the next occurrence of [targetHour]:[targetMinute].
     * If the time has already passed today, schedules for tomorrow.
     */
    private fun calculateDelay(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }
}
