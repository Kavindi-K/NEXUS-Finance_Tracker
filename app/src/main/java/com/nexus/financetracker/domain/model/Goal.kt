package com.nexus.financetracker.domain.model

/**
 * Represents a savings goal set by the user.
 */
data class Goal(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val savedAmount: Double = 0.0,
    val deadline: Long = 0L,
    val note: String = ""
)
