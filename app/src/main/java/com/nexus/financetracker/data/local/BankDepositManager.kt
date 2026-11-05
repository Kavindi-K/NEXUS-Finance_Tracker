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
        context.getSharedPreferences("nexus_bank_deposits_prefs", Context.MODE_PRIVATE)
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
        val updated = _pendingDeposits.value.filter { it.id != depositId }
        _pendingDeposits.value = updated
        saveDepositsToStorage(updated)
    }

    /**
     * Stages an incoming detected deposit for review.
     * Persists immediately to disk so it survives app kills and phone lock.
     */
    fun stageDeposit(deposit: DetectedBankDeposit) {
        val updated = listOf(deposit) + _pendingDeposits.value.filter { it.id != deposit.id }
        _pendingDeposits.value = updated
        saveDepositsToStorage(updated)
    }

    private fun saveDepositsToStorage(deposits: List<DetectedBankDeposit>) {
        try {
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
            // Use commit() to guarantee immediate synchronous disk write even in background BroadcastReceiver
            prefs.edit().putString("pending_deposits_json", jsonArray.toString()).commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDepositsFromStorage(): List<DetectedBankDeposit> {
        val jsonStr = prefs.getString("pending_deposits_json", null) ?: return emptyList()
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
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
