package com.nexus.financetracker.data.local

import android.content.Context
import com.nexus.financetracker.domain.model.DetectedBankDeposit
import com.nexus.financetracker.domain.model.IncomeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent manager for staging auto-detected bank deposits on the Income screen.
 * Saves unreviewed alerts to SharedPreferences so they survive app kills,
 * device restarts, and background SMS arrival when phone is locked.
 */
@Singleton
class BankDepositManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _pendingDeposits = MutableStateFlow<List<DetectedBankDeposit>>(emptyList())
    val pendingDeposits: StateFlow<List<DetectedBankDeposit>> = _pendingDeposits.asStateFlow()

    private val _stagedDepositForOther = MutableStateFlow<DetectedBankDeposit?>(null)
    val stagedDepositForOther: StateFlow<DetectedBankDeposit?> = _stagedDepositForOther.asStateFlow()

    fun stageForOtherRecord(deposit: DetectedBankDeposit) {
        _stagedDepositForOther.value = deposit
    }

    fun consumeStagedDepositForOther(): DetectedBankDeposit? {
        val deposit = _stagedDepositForOther.value
        _stagedDepositForOther.value = null
        return deposit
    }

    init {
        activeInstance = this
        _pendingDeposits.value = loadDepositsFromStorage()
    }

    /**
     * Refreshes the in-memory list from persistent storage.
     */
    fun refreshFromStorage() {
        _pendingDeposits.value = loadDepositsFromStorage()
    }

    /**
     * Dismisses all pending detected deposits.
     */
    fun dismissAllDeposits() {
        _pendingDeposits.value = emptyList()
        saveDepositsToStorage(emptyList())
    }

    /**
     * Dismisses/Rejects a detected deposit so it does not affect income balance.
     */
    fun dismissDeposit(depositId: String) {
        val current = loadDepositsFromStorage()
        val updated = current.filter { it.id != depositId }
        _pendingDeposits.value = updated
        saveDepositsToStorage(updated)
    }

    /**
     * Stages an incoming detected deposit for review.
     * Persists immediately to disk so it survives app kills and phone lock.
     */
    fun stageDeposit(deposit: DetectedBankDeposit) {
        val current = loadDepositsFromStorage()
        val isDuplicate = current.any { existing ->
            existing.id == deposit.id || (
                existing.bankName == deposit.bankName &&
                existing.amountLKR == deposit.amountLKR &&
                existing.accountNumber == deposit.accountNumber &&
                (existing.rawText == deposit.rawText || existing.reference == deposit.reference)
            )
        }
        if (isDuplicate) return

        val updated = listOf(deposit) + current.filter { it.id != deposit.id }
        _pendingDeposits.value = updated
        saveDepositsToStorage(updated)
    }

    private fun saveDepositsToStorage(deposits: List<DetectedBankDeposit>) {
        saveDeposits(context, deposits)
    }

    private fun loadDepositsFromStorage(): List<DetectedBankDeposit> {
        return loadDeposits(context)
    }

    companion object {
        private const val PREFS_NAME = "nexus_bank_deposits_prefs"
        private const val KEY_PENDING_JSON = "pending_deposits_json"
        private var activeInstance: BankDepositManager? = null

        /**
         * Standalone stage function that does not depend on Hilt injection.
         * Guarantees synchronous disk write when BroadcastReceiver catches an SMS while the app is killed.
         */
        fun stageDirectly(context: Context, deposit: DetectedBankDeposit) {
            val current = loadDeposits(context)
            val isDuplicate = current.any { existing ->
                existing.id == deposit.id || (
                    existing.bankName == deposit.bankName &&
                    existing.amountLKR == deposit.amountLKR &&
                    existing.accountNumber == deposit.accountNumber &&
                    (existing.rawText == deposit.rawText || existing.reference == deposit.reference)
                )
            }
            if (isDuplicate) return

            val updated = listOf(deposit) + current.filter { it.id != deposit.id }
            saveDeposits(context, updated)
            activeInstance?._pendingDeposits?.value = updated
        }

        fun loadDeposits(context: Context): List<DetectedBankDeposit> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PENDING_JSON, null) ?: return emptyList()
            return try {
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<DetectedBankDeposit>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        DetectedBankDeposit(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            bankName = obj.optString("bankName", "Bank Deposit"),
                            accountNumber = obj.optString("accountNumber", "*1234"),
                            amountLKR = obj.optDouble("amountLKR", 0.0),
                            date = Date(obj.optLong("date", System.currentTimeMillis())),
                            reference = obj.optString("reference").takeIf { it.isNotBlank() },
                            rawText = obj.optString("rawText").takeIf { it.isNotBlank() },
                            suggestedSource = obj.optString("suggestedSource", IncomeSource.SALARY)
                        )
                    )
                }
                list.distinctBy { "${it.bankName}_${it.amountLKR}_${it.reference}_${it.rawText?.trim()}" }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun saveDeposits(context: Context, deposits: List<DetectedBankDeposit>) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val jsonArray = JSONArray()
                for (d in deposits) {
                    val obj = JSONObject().apply {
                        put("id", d.id)
                        put("bankName", d.bankName)
                        put("accountNumber", d.accountNumber)
                        put("amountLKR", d.amountLKR)
                        put("date", d.date.time)
                        put("reference", d.reference ?: "")
                        put("rawText", d.rawText ?: "")
                        put("suggestedSource", d.suggestedSource)
                    }
                    jsonArray.put(obj)
                }
                prefs.edit().putString(KEY_PENDING_JSON, jsonArray.toString()).commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
