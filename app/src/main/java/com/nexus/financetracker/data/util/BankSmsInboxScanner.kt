package com.nexus.financetracker.data.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexus.financetracker.data.local.BankDepositManager
import com.nexus.financetracker.data.local.BankExpenseManager
import java.util.Date

/**
 * Scans the device's native SMS inbox for recent bank alerts.
 * Acts as a failsafe catch-up mechanism for physical devices where aggressive OEM battery killers
 * (Samsung, Xiaomi/MIUI, Oppo, Vivo) suspend [BankSmsReceiver] broadcasts while the app is killed or phone is locked.
 */
object BankSmsInboxScanner {

    private const val TAG = "BankSmsInboxScanner"
    private const val PREFS_NAME = "nexus_sms_scanner_prefs"
    private const val KEY_PROCESSED_SMS_IDS = "processed_sms_ids"

    /**
     * Scans the SMS inbox for incoming bank alerts from the past 3 days that have not yet been processed.
     * Safely stages them into [BankDepositManager] and [BankExpenseManager].
     */
    fun scanRecentBankSms(
        context: Context,
        onAlertsFound: () -> Unit = {}
    ) {
        // Check user setting toggle: if disabled, do not scan inbox
        if (!com.nexus.financetracker.data.local.AppSettingsManager.isBankSmsDetectionEnabled(context)) {
            Log.d(TAG, "Bank SMS detection disabled in App Settings; skipping inbox scan.")
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.d(TAG, "READ_SMS permission not granted; skipping inbox scan.")
            return
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val processedIds = prefs.getStringSet(KEY_PROCESSED_SMS_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()

            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            // Look back up to 3 days, but NEVER earlier than the timestamp when SMS detection was enabled by the user
            val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
            val enabledAt = com.nexus.financetracker.data.local.AppSettingsManager.getBankSmsEnabledAt(context)
            val minTimestamp = if (enabledAt > 0L) maxOf(threeDaysAgo, enabledAt) else threeDaysAgo
            val selection = "date >= ?"
            val selectionArgs = arrayOf(minTimestamp.toString())
            val sortOrder = "date DESC LIMIT 50"

            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder) ?: return
            var foundAny = false

            cursor.use { c ->
                val idCol = c.getColumnIndex("_id")
                val addressCol = c.getColumnIndex("address")
                val bodyCol = c.getColumnIndex("body")
                val dateCol = c.getColumnIndex("date")

                while (c.moveToNext()) {
                    val id = if (idCol != -1) c.getString(idCol) else continue
                    if (processedIds.contains(id)) continue

                    val timestamp = if (dateCol != -1) c.getLong(dateCol) else System.currentTimeMillis()
                    // Discard any SMS that was received while SMS detection was turned off
                    if (timestamp < minTimestamp) {
                        processedIds.add(id)
                        continue
                    }

                    val sender = if (addressCol != -1) c.getString(addressCol) ?: "" else ""
                    val body = if (bodyCol != -1) c.getString(bodyCol) ?: "" else ""

                    // 1. Try parsing as an inbound bank deposit (Income)
                    val deposit = BankDepositParser.parse(sender, body)
                    if (deposit != null) {
                        Log.i(TAG, "Catch-up scan found income deposit: ${deposit.bankName} - LKR ${deposit.amountLKR}")
                        val syncedDeposit = deposit.copy(date = Date(timestamp))
                        BankDepositManager.stageDirectly(context, syncedDeposit)
                        processedIds.add(id)
                        foundAny = true
                        continue
                    }

                    // 2. Try parsing as an outbound bank debit / card spend (Expense)
                    val debit = BankDebitParser.parse(sender, body)
                    if (debit != null) {
                        Log.i(TAG, "Catch-up scan found expense debit: ${debit.bankName} - LKR ${debit.amountLKR} at ${debit.merchant}")
                        val syncedDebit = debit.copy(date = Date(timestamp))
                        BankExpenseManager.stageDirectly(context, syncedDebit)
                        processedIds.add(id)
                        foundAny = true
                        continue
                    }
                }
            }

            prefs.edit().putStringSet(KEY_PROCESSED_SMS_IDS, processedIds).apply()

            if (foundAny) {
                onAlertsFound()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scanning SMS inbox", e)
        }
    }
}

