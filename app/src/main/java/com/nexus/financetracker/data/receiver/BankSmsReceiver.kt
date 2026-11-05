package com.nexus.financetracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.nexus.financetracker.data.local.BankDepositManager
import com.nexus.financetracker.data.local.BankExpenseManager
import com.nexus.financetracker.data.util.BankDebitParser
import com.nexus.financetracker.data.util.BankDepositParser
import com.nexus.financetracker.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Android BroadcastReceiver that listens for incoming SMS messages on the device.
 * Filters for supported bank credit/deposit alerts (Income) and debit/card spend alerts (Expense),
 * staging them persistently to disk so they survive app kills, restarts, and phone lock.
 */
@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var bankDepositManager: BankDepositManager

    @Inject
    lateinit var bankExpenseManager: BankExpenseManager

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BankSmsReceiverEntryPoint {
        fun bankDepositManager(): BankDepositManager
        fun bankExpenseManager(): BankExpenseManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION && action != "android.provider.Telephony.SMS_RECEIVED") {
            return
        }

        val messages: List<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }.ifEmpty {
            // Robust fallback for physical devices where getMessagesFromIntent returns empty
            val bundle = intent.extras
            val rawPdus = bundle?.get("pdus")
            val format = bundle?.getString("format")
            val pduList = when (rawPdus) {
                is Array<*> -> rawPdus.toList()
                is Collection<*> -> rawPdus.toList()
                else -> emptyList()
            }
            pduList.mapNotNull { pdu ->
                if (pdu is ByteArray) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            SmsMessage.createFromPdu(pdu, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pdu)
                        }
                    } catch (_: Exception) {
                        null
                    }
                } else null
            }
        }

        if (messages.isEmpty()) {
            Log.w("BankSmsReceiver", "No SMS messages extracted from intent")
            return
        }

        val fullMessageBody = messages.joinToString(separator = "") {
            it.displayMessageBody ?: it.messageBody ?: ""
        }
        val sender = messages.firstOrNull()?.displayOriginatingAddress
            ?: messages.firstOrNull()?.originatingAddress
            ?: ""

        Log.i("BankSmsReceiver", "Received SMS from: $sender | Body: $fullMessageBody")

        // 1. Try parsing as an inbound bank deposit (Income)
        val deposit = BankDepositParser.parse(sender, fullMessageBody)
        if (deposit != null) {
            Log.i("BankSmsReceiver", "Detected valid bank credit alert: ${deposit.bankName} - LKR ${deposit.amountLKR}")
            // Guarantee immediate synchronous disk write (works even if process is killed)
            BankDepositManager.stageDirectly(context, deposit)

            // Also update live in-memory singleton if running
            try {
                if (::bankDepositManager.isInitialized) {
                    bankDepositManager.refreshFromStorage()
                } else {
                    EntryPointAccessors.fromApplication(context.applicationContext, BankSmsReceiverEntryPoint::class.java)
                        .bankDepositManager().refreshFromStorage()
                }
            } catch (_: Exception) {}

            // Post high-priority notification to wake screen / lockscreen
            NotificationHelper.postBankAlertNotification(
                context = context,
                isCredit = true,
                bankName = deposit.bankName,
                amountLKR = deposit.amountLKR,
                detail = deposit.reference ?: "Income Deposit"
            )
            return
        }

        // 2. Try parsing as an outbound bank debit / card spend (Expense)
        val debit = BankDebitParser.parse(sender, fullMessageBody)
        if (debit != null) {
            Log.i("BankSmsReceiver", "Detected valid bank debit alert: ${debit.bankName} - LKR ${debit.amountLKR} at ${debit.merchant}")
            // Guarantee immediate synchronous disk write (works even if process is killed)
            BankExpenseManager.stageDirectly(context, debit)

            // Also update live in-memory singleton if running
            try {
                if (::bankExpenseManager.isInitialized) {
                    bankExpenseManager.refreshFromStorage()
                } else {
                    EntryPointAccessors.fromApplication(context.applicationContext, BankSmsReceiverEntryPoint::class.java)
                        .bankExpenseManager().refreshFromStorage()
                }
            } catch (_: Exception) {}

            // Post high-priority notification to wake screen / lockscreen
            NotificationHelper.postBankAlertNotification(
                context = context,
                isCredit = false,
                bankName = debit.bankName,
                amountLKR = debit.amountLKR,
                detail = debit.merchant ?: debit.reference ?: "Card Debit"
            )
        }
    }
}
