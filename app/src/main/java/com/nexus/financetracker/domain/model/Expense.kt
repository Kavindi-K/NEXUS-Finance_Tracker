package com.nexus.financetracker.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date

/**
 * Domain model representing an expense entry (expenses/{expenseId}).
 * Conforms to FR9-FR14, Technical Document Section 5.2, and firestore.rules validExpense schema.
 */
@IgnoreExtraProperties
data class Expense(
    @get:Exclude
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
) {
    /**
     * Converts the expense object to a Map strictly conforming to Firestore security rules
     * (validExpense key whitelist: userId, amountLKR, categoryId, account, classification, note, date, createdAt, recurringItemId).
     */
    @Exclude
    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "amountLKR" to amountLKR,
            "categoryId" to categoryId,
            "account" to account,
            "classification" to classification,
            "date" to date,
            "createdAt" to createdAt
        )
        note?.takeIf { it.isNotBlank() }?.let { map["note"] = it }
        recurringItemId?.takeIf { it.isNotBlank() }?.let { map["recurringItemId"] = it }
        return map
    }

    /**
     * Validates that the expense conforms to business constraints and security rules.
     * Returns null if valid, or an error message if invalid.
     */
    @Exclude
    fun validate(): String? {
        if (userId.isBlank()) return "User ID is required"
        if (amountLKR <= 0.0) return "Expense amount must be greater than zero"
        if (categoryId.isBlank()) return "Please select a category"
        if (account !in ExpenseAccount.ALL) return "Invalid account: $account"
        if (classification !in ExpenseClassification.ALL) return "Invalid classification: $classification"
        return null
    }
}

object ExpenseAccount {
    const val CARD = "card"
    const val CASH = "cash"
    const val SECONDARY = "secondary"

    val ALL = listOf(CARD, CASH, SECONDARY)

    fun getDisplayName(account: String): String = when (account.lowercase()) {
        CARD -> "Card"
        CASH -> "Cash"
        SECONDARY -> "Secondary Account"
        else -> account.replaceFirstChar { it.uppercase() }
    }
}

object ExpenseClassification {
    const val COMMITTED = "committed"
    const val DISCRETIONARY = "discretionary"

    val ALL = listOf(COMMITTED, DISCRETIONARY)

    fun getDisplayName(classification: String): String = when (classification.lowercase()) {
        COMMITTED -> "Committed (Fixed)"
        DISCRETIONARY -> "Discretionary (Flexible)"
        else -> classification.replaceFirstChar { it.uppercase() }
    }
}

