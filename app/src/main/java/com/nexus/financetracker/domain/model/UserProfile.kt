package com.nexus.financetracker.domain.model

import java.util.Date

/**
 * Domain model representing a user profile (users/{uid}).
 * Conforms to FR1 and Technical Document Section 5.2.
 */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val createdAt: Date = Date()
)
