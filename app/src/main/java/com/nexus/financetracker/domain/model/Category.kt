package com.nexus.financetracker.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date

/**
 * Domain model representing an expense category (categories/{categoryId}).
 * Conforms to FR12, Technical Document Section 5.2, and firestore.rules validCategory schema.
 */
@IgnoreExtraProperties
data class Category(
    @get:Exclude
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val classification: String = ExpenseClassification.DISCRETIONARY,
    val estimatedAmount: Double? = null,
    val recurringFrequency: String? = null,
    val mergingInto: String? = null,
    val createdAt: Date = Date()
) {
    @Exclude
    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "name" to name.trim(),
            "createdAt" to createdAt
        )
        mergingInto?.takeIf { it.isNotBlank() }?.let { map["mergingInto"] = it }
        classification.takeIf { it.isNotBlank() }?.let { map["classification"] = it.lowercase() }
        estimatedAmount?.let { map["estimatedAmount"] = it }
        recurringFrequency?.takeIf { it.isNotBlank() }?.let { map["recurringFrequency"] = it.lowercase() }
        return map
    }

    @Exclude
    fun toLegacyFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "name" to name.trim(),
            "createdAt" to createdAt
        )
        mergingInto?.takeIf { it.isNotBlank() }?.let { map["mergingInto"] = it }
        classification.takeIf { it.isNotBlank() }?.let { map["classification"] = it.lowercase() }
        recurringFrequency?.takeIf { it.isNotBlank() }?.let { map["recurringFrequency"] = it.lowercase() }
        return map
    }

    @Exclude
    fun validate(): String? {
        if (userId.isBlank()) return "User ID is required"
        if (name.isBlank()) return "Category name cannot be empty"
        if (name.trim().length > 40) return "Category name cannot exceed 40 characters"
        return null
    }
}
