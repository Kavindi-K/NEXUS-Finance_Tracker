package com.nexus.financetracker.domain.model

/**
 * Represents a financial transaction (income or expense).
 * Used as the core domain model across UI, ViewModel, and Repository layers.
 */
data class Transaction(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val date: Long = System.currentTimeMillis(),
    val note: String = ""
)

enum class TransactionType {
    INCOME, EXPENSE
}
