package com.nexus.financetracker.domain.model

import com.google.firebase.firestore.Exclude
import java.util.Date
import java.util.Locale

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
) {
    @get:Exclude
    val capitalizedDisplayName: String
        get() = formatCapitalizedName(displayName)

    companion object {
        fun formatCapitalizedName(name: String): String {
            return name.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
        }
    }
}
