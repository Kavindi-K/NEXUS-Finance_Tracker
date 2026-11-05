package com.nexus.financetracker.data.local

import com.nexus.financetracker.domain.model.DetectedBankExpense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory manager for staging auto-detected bank debit alerts on the Expense screen.
 * Strictly focused on outbound Expense review (Accept or Reject).
 */
@Singleton
class BankExpenseManager @Inject constructor() {

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
        // Starts clean — populated from live bank SMS alerts
    }

    /**
     * Dismisses all pending detected debit alerts.
     */
    fun dismissAllExpenses() {
        _pendingExpenses.value = emptyList()
    }

    /**
     * Dismisses/Rejects a detected debit alert so it does not affect expense records.
     */
    fun dismissExpense(expenseId: String) {
        _pendingExpenses.value = _pendingExpenses.value.filter { it.id != expenseId }
    }

    /**
     * Stages an incoming detected debit for review.
     */
    fun stageExpense(expense: DetectedBankExpense) {
        _pendingExpenses.value = listOf(expense) + _pendingExpenses.value.filter { it.id != expense.id }
    }

    /**
     * Simulates a Commercial Bank POS supermarket card payment alert.
     */
    fun simulateCommercialBankCardPayment(amount: Double = 4250.0) {
        val sample = DetectedBankExpense(
            bankName = "Commercial Bank",
            accountNumber = "*1234",
            amountLKR = amount,
            date = Date(),
            merchant = "CARGILLS FOOD CITY",
            reference = "POS-7821",
            rawText = "Your Card *1234 was debited with LKR ${String.format(java.util.Locale.US, "%,.2f", amount)} at CARGILLS FOOD CITY on 04-NOV-2026. Ref: POS-7821.",
            accountType = ExpenseAccount.CARD,
            classification = ExpenseClassification.DISCRETIONARY
        )
        stageExpense(sample)
    }

    /**
     * Simulates a Sampath Bank fuel station alert.
     */
    fun simulateSampathFuelPayment(amount: Double = 2500.0) {
        val sample = DetectedBankExpense(
            bankName = "Sampath Bank",
            accountNumber = "*5678",
            amountLKR = amount,
            date = Date(),
            merchant = "CEYPETCO FUEL",
            reference = "FUEL-4311",
            rawText = "Card *5678 used for LKR ${String.format(java.util.Locale.US, "%,.2f", amount)} at CEYPETCO FUEL on 04-NOV-2026.",
            accountType = ExpenseAccount.CARD,
            classification = ExpenseClassification.DISCRETIONARY
        )
        stageExpense(sample)
    }
}

