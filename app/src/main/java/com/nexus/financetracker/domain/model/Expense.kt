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

    @get:Exclude
    val isRecurring: Boolean
        get() = recurringItemId?.startsWith("rec:") == true

    @get:Exclude
    val recurringFrequency: String?
        get() = recurringItemId?.takeIf { it.startsWith("rec:") }?.split(":")?.getOrNull(1)

    @get:Exclude
    val recurringDuration: Int?
        get() = recurringItemId?.takeIf { it.startsWith("rec:") }?.split(":")?.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }

    @get:Exclude
    val recurringDurationUnit: String?
        get() = recurringItemId?.takeIf { it.startsWith("rec:") }?.split(":")?.getOrNull(3)

    @get:Exclude
    val recurrenceIndex: Int?
        get() = recurringItemId?.takeIf { it.startsWith("rec:") }?.split(":")?.getOrNull(4)?.toIntOrNull()

    @get:Exclude
    val recurringSeriesId: String?
        get() = recurringItemId?.takeIf { it.startsWith("rec:") }?.split(":")?.getOrNull(5)

    @Exclude
    fun getRecurringProgressText(): String? {
        if (!isRecurring) return null
        val freq = recurringFrequency?.replaceFirstChar { it.uppercase() } ?: "Monthly"
        val dur = recurringDuration
        val idx = recurrenceIndex ?: 1
        return if (dur != null && dur > 0) {
            val left = (dur - idx).coerceAtLeast(0)
            if (left > 0) {
                "Recurring ($idx of $dur · $left left)"
            } else {
                "Recurring (Final · $idx of $dur)"
            }
        } else {
            "Recurring ($freq · Ongoing)"
        }
    }

    companion object {
        fun buildRecurringItemId(
            frequency: String,
            duration: Int?,
            unit: String?,
            index: Int,
            seriesId: String
        ): String {
            val freqClean = frequency.lowercase()
            val durPart = duration?.takeIf { it > 0 }?.toString() ?: "0"
            val unitClean = (unit ?: "months").lowercase()
            return "rec:$freqClean:$durPart:$unitClean:$index:$seriesId"
        }
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

