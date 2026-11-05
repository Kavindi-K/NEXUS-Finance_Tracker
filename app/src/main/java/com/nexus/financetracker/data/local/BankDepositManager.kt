package com.nexus.financetracker.data.local

import com.nexus.financetracker.data.util.BankDepositParser
import com.nexus.financetracker.domain.model.DetectedBankDeposit
import com.nexus.financetracker.domain.model.IncomeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory manager for staging auto-detected bank deposits on the Income screen.
 * Strictly focused on inbound Income review (Accept or Reject).
 */
@Singleton
class BankDepositManager @Inject constructor() {

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
        // Pre-populate with one realistic detected deposit for demonstration
        // Starts clean: populated from live bank SMS alerts
    }

    /**
     * Dismisses all pending detected deposits.
     */
    fun dismissAllDeposits() {
        _pendingDeposits.value = emptyList()
    }

    /**
     * Dismisses/Rejects a detected deposit so it does not affect income balance.
     */
    fun dismissDeposit(depositId: String) {
        _pendingDeposits.value = _pendingDeposits.value.filter { it.id != depositId }
    }

    /**
     * Staging an incoming detected deposit for review.
     */
    fun stageDeposit(deposit: DetectedBankDeposit) {
        _pendingDeposits.value = listOf(deposit) + _pendingDeposits.value.filter { it.id != deposit.id }
    }

    /**
     * Simulates a Commercial Bank salary alert deposit.
     */
    fun simulateCommercialBankSalary(amount: Double = 135000.0) {
        val sample = DetectedBankDeposit(
            bankName = "Commercial Bank",
            accountNumber = "*1234",
            amountLKR = amount,
            date = Date(),
            reference = "SALARY-OCT",
            rawText = "Your A/C *1234 has been credited with LKR ${String.format(java.util.Locale.US, "%,.2f", amount)} on 04-NOV-2026. Ref: SALARY-OCT.",
            suggestedSource = IncomeSource.SALARY
        )
        stageDeposit(sample)
    }

    /**
     * Simulates a Sampath Bank freelance deposit.
     */
    fun simulateSampathFreelance(amount: Double = 45000.0) {
        val sample = DetectedBankDeposit(
            bankName = "Sampath Bank",
            accountNumber = "*5678",
            amountLKR = amount,
            date = Date(),
            reference = "CLIENT-UPWORK",
            rawText = "A/C *5678 has received a deposit of LKR ${String.format(java.util.Locale.US, "%,.2f", amount)} on 04-NOV-2026. CLIENT-UPWORK",
            suggestedSource = IncomeSource.FREELANCE
        )
        stageDeposit(sample)
    }
}

