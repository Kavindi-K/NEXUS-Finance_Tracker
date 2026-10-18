package com.nexus.financetracker.domain.model

import java.util.Date

/**
 * Domain model representing a scheduled recurring payment (recurringItems/{itemId}).
 * Conforms to FR23 and Technical Document Section 5.2.
 */
data class RecurringItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val amountLKR: Double = 0.0,
    val categoryId: String = "",
    val account: String = ExpenseAccount.CARD,
    val classification: String = ExpenseClassification.COMMITTED,
    val frequency: String = RecurringFrequency.MONTHLY,
    val nextDueDate: Date = Date(),
    val isActive: Boolean = true,
    val createdAt: Date = Date()
)

object RecurringFrequency {
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
}
