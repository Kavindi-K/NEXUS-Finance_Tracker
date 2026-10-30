package com.nexus.financetracker.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date

/**
 * Domain model representing a goal contribution saved from category budget surpluses.
 */
@IgnoreExtraProperties
data class Contribution(
    @get:Exclude
    val id: String = "",
    val userId: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val amountLKR: Double = 0.0,
    val monthLabel: String = "",
    val isAllocated: Boolean = false,
    val allocatedGoalId: String? = null,
    val allocatedGoalTitle: String? = null,
    val createdAt: Date = Date()
) {
    @Exclude
    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "categoryId" to categoryId,
            "categoryName" to categoryName,
            "amountLKR" to amountLKR,
            "monthLabel" to monthLabel,
            "isAllocated" to isAllocated,
            "createdAt" to createdAt
        )
        allocatedGoalId?.let { map["allocatedGoalId"] = it }
        allocatedGoalTitle?.let { map["allocatedGoalTitle"] = it }
        return map
    }
}

