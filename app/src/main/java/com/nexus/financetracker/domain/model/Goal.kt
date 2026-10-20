package com.nexus.financetracker.domain.model

import com.google.firebase.firestore.Exclude
import java.util.Date

/**
 * Domain model representing a savings goal (goals/{goalId}).
 * Conforms strictly to Technical Document Section 5.2.
 */
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
    val createdAt: Date = Date()
)
