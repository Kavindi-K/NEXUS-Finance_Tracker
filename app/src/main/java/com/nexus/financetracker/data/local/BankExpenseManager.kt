package com.nexus.financetracker.data.local

import android.content.Context
import com.nexus.financetracker.domain.model.DetectedBankExpense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
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
 * Persistent manager for staging auto-detected bank debit alerts on the Expense screen.
 * Saves unreviewed alerts to SharedPreferences so they survive app kills,
 * device restarts, and background SMS arrival when phone is locked.
 */
@Singleton
class BankExpenseManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences("nexus_bank_expenses_prefs", Context.MODE_PRIVATE)
    }

    private val _pendingExpenses = MutableStateFlow<List<DetectedBankExpense>>(emptyList())
    val pendingExpenses: StateFlow<List<DetectedBankExpense>> = _pendingExpenses.asStateFlow()

    private val _stagedExpenseForOther = MutableStateFlow<DetectedBankExpense?>(null)
    val stagedExpenseForOther: StateFlow<DetectedBankExpense?> = _stagedExpenseForOther.asStateFlow()

    fun stageForOtherRecord(expense: DetectedBankExpense) {
        _stagedExpenseForOther.value = expense
    }

    fun consumeStagedExpenseForOther(): DetectedBankExpense? {
        val expense = _stagedExpenseForOther.value
        _stagedExpenseForOther.value = null
        return expense
    }

    init {
        _pendingExpenses.value = loadExpensesFromStorage()
    }

    /**
     * Dismisses all pending detected debit alerts.
     */
    fun dismissAllExpenses() {
        _pendingExpenses.value = emptyList()
        saveExpensesToStorage(emptyList())
    }

    /**
     * Dismisses/Rejects a detected debit alert so it does not affect expense records.
     */
    fun dismissExpense(expenseId: String) {
        val updated = _pendingExpenses.value.filter { it.id != expenseId }
        _pendingExpenses.value = updated
        saveExpensesToStorage(updated)
    }

    /**
     * Stages an incoming detected debit for review.
     * Persists immediately to disk so it survives app kills and phone lock.
     */
    fun stageExpense(expense: DetectedBankExpense) {
        val updated = listOf(expense) + _pendingExpenses.value.filter { it.id != expense.id }
        _pendingExpenses.value = updated
        saveExpensesToStorage(updated)
    }

    private fun saveExpensesToStorage(expenses: List<DetectedBankExpense>) {
        try {
            val jsonArray = JSONArray()
            for (e in expenses) {
                val obj = JSONObject().apply {
                    put("id", e.id)
                    put("bankName", e.bankName)
                    put("accountNumber", e.accountNumber)
                    put("amountLKR", e.amountLKR)
                    put("date", e.date.time)
                    put("merchant", e.merchant ?: "")
                    put("reference", e.reference ?: "")
                    put("rawText", e.rawText ?: "")
                    put("accountType", e.accountType)
                    put("classification", e.classification)
                }
                jsonArray.put(obj)
            }
            // Use commit() to guarantee immediate synchronous disk write even in background BroadcastReceiver
            prefs.edit().putString("pending_expenses_json", jsonArray.toString()).commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadExpensesFromStorage(): List<DetectedBankExpense> {
        val jsonStr = prefs.getString("pending_expenses_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<DetectedBankExpense>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DetectedBankExpense(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        bankName = obj.optString("bankName", "Bank Expense"),
                        accountNumber = obj.optString("accountNumber", "*1234"),
                        amountLKR = obj.optDouble("amountLKR", 0.0),
                        date = Date(obj.optLong("date", System.currentTimeMillis())),
                        merchant = obj.optString("merchant").takeIf { it.isNotBlank() },
                        reference = obj.optString("reference").takeIf { it.isNotBlank() },
                        rawText = obj.optString("rawText").takeIf { it.isNotBlank() },
                        accountType = obj.optString("accountType", ExpenseAccount.CARD),
                        classification = obj.optString("classification", ExpenseClassification.DISCRETIONARY)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
