package com.nexus.financetracker.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullMessageBody = messages.joinToString(separator = "") {
            it.displayMessageBody ?: it.messageBody ?: ""
        }
        val sender = messages.firstOrNull()?.displayOriginatingAddress
            ?: messages.firstOrNull()?.originatingAddress
            ?: ""

        Log.d("BankSmsReceiver", "Received SMS from: $sender")

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

