package com.nexus.financetracker.domain.model

import java.util.Date

/**
 * Domain model representing an income entry (incomes/{incomeId}).
 * Conforms to FR3-FR8 and Technical Document Section 5.2.
 */
data class Income(
    val id: String = "",
    val userId: String = "",
    val source: String = IncomeSource.SALARY,
    val status: String = IncomeStatus.CONFIRMED,
    val amountOriginal: Double = 0.0,
    val currency: String = "LKR",
    val exchangeRate: Double = 1.0,
    val amountLKR: Double = 0.0,
    val dateReceived: Date? = null,
    val expectedDate: Date? = null,
    val note: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date? = null
)

object IncomeSource {
    const val SALARY = "salary"
    const val FREELANCE = "freelance"
    const val ADSENSE = "adsense"
    const val CRYPTO = "crypto"
}

object IncomeStatus {
    const val CONFIRMED = "confirmed"
    const val PENDING = "pending"
}
