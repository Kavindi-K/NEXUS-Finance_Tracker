package com.nexus.financetracker.domain.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date

/**
 * Priority level constants for Savings Goals.
 */
object GoalPriority {
    const val HIGH = "high"
    const val MEDIUM = "medium"
    const val LOW = "low"

    val ALL = listOf(HIGH, MEDIUM, LOW)

    fun getDisplayName(priority: String): String = when (priority.lowercase()) {
        HIGH -> "High"
        LOW -> "Low"
        else -> "Medium"
    }
}

/**
 * Domain model representing a savings goal (goals/{goalId}).
 */
@IgnoreExtraProperties
data class Goal(
    @get:Exclude
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetAmountLKR: Double = 0.0,
    val openingSavingsLKR: Double = 0.0,
    val startDate: Date = Date(),
    val targetDate: Date = Date(),
    val isActive: Boolean = true,
    val createdAt: Date = Date(),
    val priority: String = GoalPriority.MEDIUM
) {
    @get:Exclude
    val daysRemaining: Long
        get() = GoalCalculation.calculateDaysRemaining(Date(), targetDate)

    @get:Exclude
    val daysRemainingText: String
        get() = when {
            daysRemaining > 1L -> "$daysRemaining days left"
            daysRemaining == 1L -> "1 day left"
            daysRemaining == 0L -> "Due today"
            else -> "${-daysRemaining} days overdue"
        }
}
