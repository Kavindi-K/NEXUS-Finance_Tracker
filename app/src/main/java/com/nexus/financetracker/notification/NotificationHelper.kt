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

    /**
     * Posts a pending income arrival reminder notification (FR8).
     *
     * @param context        Application context.
     * @param notificationId Unique ID per income entry.
     * @param sourceName     Display name of income source (e.g. "Freelance client").
     * @param amountLKR      Income amount in LKR.
     */
    fun postPendingIncomeReminder(
        context: Context,
        notificationId: Int,
        sourceName: String,
        amountLKR: Double
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountFormatted = "LKR %,.0f".format(amountLKR)
        val titleText = "$sourceName payment expected today ($amountFormatted)"
        val contentText = "Tap to mark as confirmed."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$sourceName payment expected today ($amountFormatted). Tap to open NEXUS and mark as confirmed.")
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

    /**
     * Posts a savings goal notification (milestone reached, 1-week deadline, pace warning, or achieved) (FR15–FR17).
     *
     * @param context        Application context.
     * @param notificationId Unique ID per goal notification.
     * @param title          Notification title (e.g. "Goal Achieved: MacBook Pro M4").
     * @param message        Notification body text.
     */
    fun postGoalReminder(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText("$message Tap to open NEXUS and view your goal.")
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

    /**
     * Posts a daily quick-log evening reminder at 8:30 PM (FR9).
     * Reminds users to log unrecorded cash or secondary account expenses.
     */
    fun postDailyQuickLogReminder(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Did you spend any cash today?")
            .setContentText("3 taps is all it takes to keep your tracker accurate.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Did you spend any cash today? 3 taps is all it takes to keep your tracker accurate. Tap to log an expense now.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9001, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission was not granted — silently skip
        }
    }

    /**
     * Posts a daily quick-log income reminder.
     * Reminds users to log unrecorded freelance, cash, or secondary earnings.
     */
    fun postDailyIncomeQuickLogReminder(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Did you receive any income today?")
            .setContentText("Don't forget to log freelance, side gigs, or cash earnings.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Did you receive any income today? Don't forget to log freelance, side gigs, or cash earnings to keep your cash flow accurate.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9002, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission was not granted — silently skip
        }
    }

    /**
     * Posts a discretionary spending leak alert (FR18–FR19).
     *
     * @param context Application context.
     * @param notificationId Unique notification ID per category leak.
     * @param categoryName Name of category (e.g. "Dining & Coffee").
     * @param amountLKR Total spent in this category this week.
     */
    fun postDiscretionaryLeakReminder(
        context: Context,
        notificationId: Int,
        categoryName: String,
        amountLKR: Double
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountFormatted = "LKR %,.0f".format(amountLKR)
        val text = "Notice: You've spent $amountFormatted on $categoryName this week."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Spending Leak Alert — $categoryName")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$text Tap to review your spending and make adjustments.")
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

    private const val BANK_ALERT_CHANNEL_ID = "nexus_bank_sms_alerts"
    private const val BANK_ALERT_CHANNEL_NAME = "Bank Transaction Alerts"

    /**
     * Posts a high-priority heads-up notification when a bank SMS alert is captured in the background.
     * Wakes up device and alerts the user even when the phone is locked.
     */
    fun postBankAlertNotification(
        context: Context,
        isCredit: Boolean,
        bankName: String,
        amountLKR: Double,
        detail: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure high-importance notification channel exists for bank transaction alerts
        val channel = NotificationChannel(
            BANK_ALERT_CHANNEL_ID,
            BANK_ALERT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority notifications for detected bank SMS deposits and debit transactions."
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeTag = if (isCredit) "Bank Deposit Alert" else "Bank Debit Alert"
        val sign = if (isCredit) "+" else "-"
        val formattedAmount = "$sign LKR %,.2f".format(amountLKR)
        val title = "$bankName: $formattedAmount"
        val bodyText = "$detail • Tap to review and confirm."

        val notification = NotificationCompat.Builder(context, BANK_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$typeTag from $bankName: $formattedAmount\n$detail\nTap to open NEXUS and review in ${if (isCredit) "Income" else "Expenses"}.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }
}
