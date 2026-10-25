package com.nexus.financetracker.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date
import kotlin.math.abs

/**
 * Domain model representing an income entry (incomes/{incomeId}).
 * Conforms to FR3-FR8, Technical Document Section 5.2, and firestore.rules validIncome schema.
 */
@IgnoreExtraProperties
data class Income(
    @get:Exclude
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
) {
    /**
     * Converts the income object to a Map strictly conforming to Firestore security rules
     * (validIncome key whitelist and conditional date constraints).
     */
    @Exclude
    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "source" to source,
            "status" to status,
            "amountOriginal" to amountOriginal,
            "currency" to currency.uppercase(),
            "exchangeRate" to exchangeRate,
            "amountLKR" to amountLKR,
            "createdAt" to createdAt
        )

        // Security rule: confirmed requires dateReceived; pending must NOT have dateReceived
        if (status == IncomeStatus.CONFIRMED) {
            map["dateReceived"] = dateReceived ?: createdAt
        } else if (status == IncomeStatus.PENDING && expectedDate != null) {
            map["expectedDate"] = expectedDate
        }

        note?.takeIf { it.isNotBlank() }?.let { map["note"] = it }
        updatedAt?.let { map["updatedAt"] = it }

        return map
    }

    /**
     * Validates that the income entry conforms to all business and Firestore security constraints.
     * Returns null if valid, or an error description string if invalid.
     */
    @Exclude
    fun validate(): String? {
        if (userId.isBlank()) return "User ID is required"
        if (source !in IncomeSource.ALL) return "Invalid income source: $source"
        if (status !in IncomeStatus.ALL) return "Invalid income status: $status"
        if (currency.length < 3) return "Currency code must be at least 3 characters"
        if (exchangeRate <= 0.0) return "Exchange rate must be positive"
        if (currency.equals("LKR", ignoreCase = true) && exchangeRate != 1.0) {
            return "Exchange rate for LKR must be 1.0"
        }
        if (source == IncomeSource.CRYPTO) {
            if (amountOriginal == 0.0) return "Crypto realized amount cannot be zero"
        } else {
            if (amountOriginal <= 0.0) return "Income amount must be greater than zero"
        }
        if (abs(amountLKR - (amountOriginal * exchangeRate)) > 0.01) {
            return "amountLKR does not match amountOriginal * exchangeRate"
        }
        if (status == IncomeStatus.CONFIRMED && dateReceived == null) {
            return "Confirmed income must have a date received"
        }
        if (status == IncomeStatus.PENDING && dateReceived != null) {
            return "Pending income cannot have a date received"
        }
        return null
    }
}

object IncomeSource {
    const val SALARY = "salary"
    const val FREELANCE = "freelance"
    const val CRYPTO = "crypto"
    const val ADSENSE = "adsense"
    const val OTHER = "other"

    val ALL = listOf(SALARY, FREELANCE, CRYPTO, ADSENSE, OTHER)

    fun getDisplayName(source: String): String = when (source) {
        SALARY -> "Base Salary"
        FREELANCE -> "Freelance"
        CRYPTO -> "Realized Crypto"
        ADSENSE -> "AdSense"
        OTHER -> "Other"
        else -> source.replaceFirstChar { it.uppercase() }
    }

    /**
     * Determines the display order of income streams matching the standard sequence:
     * 1. Base Salary
     * 2. Freelance
     * 3. Crypto (Net P&L)
     * 4. AdSense (USD)
     * 5. Other Income
     */
    fun getSourceOrder(sourceKeyOrName: String): Int {
        val lower = sourceKeyOrName.lowercase().trim()
        return when {
            lower == SALARY || lower.contains("salary") -> 1
            lower == FREELANCE || lower.contains("freelance") -> 2
            lower == CRYPTO || lower.contains("crypto") -> 3
            lower == ADSENSE || lower.contains("adsense") -> 4
            lower.contains("other") -> 5
            else -> 6
        }
    }
}

object IncomeStatus {
    const val CONFIRMED = "confirmed"
    const val PENDING = "pending"

    val ALL = listOf(CONFIRMED, PENDING)

    fun getDisplayName(status: String): String = when (status) {
        CONFIRMED -> "Confirmed"
        PENDING -> "Pending"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}

