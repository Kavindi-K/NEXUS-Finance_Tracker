package com.nexus.financetracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.nexus.financetracker.data.local.BankDepositManager
import com.nexus.financetracker.data.util.BankDepositParser
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Android BroadcastReceiver that listens for incoming SMS messages on the device.
 * Filters for supported bank credit alerts (e.g. Commercial Bank, Sampath, HNB, BOC)
 * and stages incoming funds on the Income Screen for user review (Accept or Reject).
 */
@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var bankDepositManager: BankDepositManager

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BankSmsReceiverEntryPoint {
        fun bankDepositManager(): BankDepositManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION && action != "android.provider.Telephony.SMS_RECEIVED") return

        val messages: List<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }.ifEmpty {
            // Robust fallback for physical devices where getMessagesFromIntent returns empty
            val bundle = intent.extras
            val pdus = bundle?.get("pdus") as? Array<*>
            val format = bundle?.getString("format")
            pdus?.mapNotNull { pdu ->
                if (pdu is ByteArray) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            SmsMessage.createFromPdu(pdu, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pdu)
                        }
                    } catch (e: Exception) {
                        null
                    }
                } else null
            } ?: emptyList()
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

        Log.d("BankSmsReceiver", "Received SMS from: $sender | Body: $fullMessageBody")

        // Parse using our specialized Sri Lankan bank alert parser
        val deposit = BankDepositParser.parse(sender, fullMessageBody)
        if (deposit != null) {
            Log.d("BankSmsReceiver", "Detected valid bank credit alert: ${deposit.bankName} - LKR ${deposit.amountLKR}")
            val manager = try {
                if (::bankDepositManager.isInitialized) bankDepositManager
                else EntryPointAccessors.fromApplication(context.applicationContext, BankSmsReceiverEntryPoint::class.java).bankDepositManager()
            } catch (e: Exception) {
                EntryPointAccessors.fromApplication(context.applicationContext, BankSmsReceiverEntryPoint::class.java).bankDepositManager()
            }
            manager.stageDeposit(deposit)
        }
    }
}
