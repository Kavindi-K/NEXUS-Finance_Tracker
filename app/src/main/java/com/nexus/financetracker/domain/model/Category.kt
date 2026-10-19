package com.nexus.financetracker.domain.model

import java.util.Date

/**
 * Domain model representing an expense category (categories/{categoryId}).
 * Conforms to FR12 and Technical Document Section 5.2.
 */
data class Category(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val mergingInto: String? = null,
    val createdAt: Date = Date()
)
