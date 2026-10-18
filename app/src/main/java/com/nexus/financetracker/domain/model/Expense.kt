package com.nexus.financetracker.domain.model

import java.util.Date

/**
 * Domain model representing an expense entry (expenses/{expenseId}).
 * Conforms to FR9-FR14 and Technical Document Section 5.2.
 */
data class Expense(
    val id: String = "",
    val userId: String = "",
    val amountLKR: Double = 0.0,
    val categoryId: String = "",
    val account: String = ExpenseAccount.CARD,
    val classification: String = ExpenseClassification.DISCRETIONARY,
    val note: String? = null,
    val date: Date = Date(),
    val recurringItemId: String? = null,
    val createdAt: Date = Date()
)

object ExpenseAccount {
    const val CARD = "card"
    const val CASH = "cash"
    const val SECONDARY = "secondary"
}

object ExpenseClassification {
    const val COMMITTED = "committed"
    const val DISCRETIONARY = "discretionary"
}
