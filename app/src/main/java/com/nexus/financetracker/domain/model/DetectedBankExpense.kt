package com.nexus.financetracker.domain.model

import java.util.Date
import java.util.UUID

/**
 * Represents an auto-detected incoming bank debit/expense staged for user review on the Expense screen.
 * Requires explicit user confirmation (Accept or Reject) before affecting confirmed expense records.
 */
data class DetectedBankExpense(
    val id: String = UUID.randomUUID().toString(),
    val bankName: String,
    val accountNumber: String,
    val amountLKR: Double,
    val date: Date = Date(),
    val merchant: String? = null,
    val reference: String? = null,
    val rawText: String? = null,
    val accountType: String = ExpenseAccount.CARD,
    val classification: String = ExpenseClassification.DISCRETIONARY
)

