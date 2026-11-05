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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        activeInstance = this
        _pendingExpenses.value = loadExpensesFromStorage()
    }

    /**
     * Refreshes the in-memory list from persistent storage.
     */
    fun refreshFromStorage() {
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
        val current = loadExpensesFromStorage()
        val updated = current.filter { it.id != expenseId }
        _pendingExpenses.value = updated
        saveExpensesToStorage(updated)
    }

    /**
     * Stages an incoming detected debit for review.
     * Persists immediately to disk so it survives app kills and phone lock.
     */
    fun stageExpense(expense: DetectedBankExpense) {
        val current = loadExpensesFromStorage()
        val isDuplicate = current.any { existing ->
            existing.id == expense.id || (
                existing.bankName == expense.bankName &&
                existing.amountLKR == expense.amountLKR &&
                existing.accountNumber == expense.accountNumber &&
                (existing.rawText == expense.rawText || existing.merchant == expense.merchant)
            )
        }
        if (isDuplicate) return

        val updated = listOf(expense) + current.filter { it.id != expense.id }
        _pendingExpenses.value = updated
        saveExpensesToStorage(updated)
    }

    private fun saveExpensesToStorage(expenses: List<DetectedBankExpense>) {
        saveExpenses(context, expenses)
    }

    private fun loadExpensesFromStorage(): List<DetectedBankExpense> {
        return loadExpenses(context)
    }

    companion object {
        private const val PREFS_NAME = "nexus_bank_expenses_prefs"
        private const val KEY_PENDING_JSON = "pending_expenses_json"
        private var activeInstance: BankExpenseManager? = null

        /**
         * Standalone stage function that does not depend on Hilt injection.
         * Guarantees synchronous disk write when BroadcastReceiver catches an SMS while the app is killed.
         */
        fun stageDirectly(context: Context, expense: DetectedBankExpense) {
            val current = loadExpenses(context)
            val isDuplicate = current.any { existing ->
                existing.id == expense.id || (
                    existing.bankName == expense.bankName &&
                    existing.amountLKR == expense.amountLKR &&
                    existing.accountNumber == expense.accountNumber &&
                    (existing.rawText == expense.rawText || existing.merchant == expense.merchant)
                )
            }
            if (isDuplicate) return

            val updated = listOf(expense) + current.filter { it.id != expense.id }
            saveExpenses(context, updated)
            activeInstance?._pendingExpenses?.value = updated
        }

        fun loadExpenses(context: Context): List<DetectedBankExpense> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PENDING_JSON, null) ?: return emptyList()
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
                list.distinctBy { "${it.bankName}_${it.amountLKR}_${it.merchant}_${it.rawText?.trim()}" }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun saveExpenses(context: Context, expenses: List<DetectedBankExpense>) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                prefs.edit().putString(KEY_PENDING_JSON, jsonArray.toString()).commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
