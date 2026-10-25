package com.nexus.financetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexus.financetracker.MainActivity
import com.nexus.financetracker.R

/**
 * Utility object for creating, managing, and posting local notifications.
 * Handles notification channel setup (required for Android 8+) and
 * constructs bill reminder notifications for the RecurringItem schedule.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "nexus_bill_reminders"
    private const val CHANNEL_NAME = "Bill & Subscription Reminders"
    private const val CHANNEL_DESCRIPTION =
        "Reminds you when a recurring payment is coming up so you can confirm and log it."

    /**
     * Creates the notification channel. Safe to call multiple times — Android
     * ignores duplicate channel registrations. Must be called before posting
     * any notification (called from [BillReminderWorker]).
     */
    fun createNotificationChannel(context: Context) {
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = CHANNEL_DESCRIPTION
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Posts a bill reminder notification for a single recurring item.
     *
     * @param context       Application context.
     * @param notificationId Unique ID per recurring item (prevents collapse of multiple alerts).
     * @param title         Payment title (e.g. "Gym Membership").
     * @param amountLKR     Payment amount in LKR.
     * @param isDueToday    True = "due today", false = "due tomorrow".
     */
    fun postBillReminder(
        context: Context,
        notificationId: Int,
        title: String,
        amountLKR: Double,
        isDueToday: Boolean
    ) {
        // Deep-link tap opens MainActivity (which restores the navigation stack)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dueText = if (isDueToday) "due today" else "due tomorrow"
        val amountFormatted = "LKR %.0f".format(amountLKR)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$title — $amountFormatted")
            .setContentText("Payment is $dueText. Tap to confirm and log expense.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$title ($amountFormatted) is $dueText. Tap to open NEXUS and confirm the payment.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission was not granted — silently skip
        }
    }
}
