package com.nexus.financetracker.domain.model

import java.util.Date
import java.util.UUID

/**
 * Represents an auto-detected incoming bank deposit staged for user review on the Income screen.
 * Requires explicit user confirmation (Accept or Reject) before affecting confirmed income balances.
 */
data class DetectedBankDeposit(
    val id: String = UUID.randomUUID().toString(),
    val bankName: String,
    val accountNumber: String,
    val amountLKR: Double,
    val date: Date = Date(),
    val reference: String? = null,
    val rawText: String? = null,
    val suggestedSource: String = IncomeSource.SALARY
)

