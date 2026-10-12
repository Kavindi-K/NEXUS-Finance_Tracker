package com.nexus.financetracker.domain.model

/**
 * Represents a monthly budget for a category.
 */
data class Budget(
    val id: String = "",
    val userId: String = "",
    val category: String = "",
    val limitAmount: Double = 0.0,
    val spentAmount: Double = 0.0,
    val month: Int = 0,   // 1–12
    val year: Int = 0
)
