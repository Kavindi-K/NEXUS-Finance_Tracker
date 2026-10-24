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
        val PHONE_REGEX = Regex("^(\\+94[0-9]{9}|0[0-9]{9}|[1-9][0-9]{8})$")

        fun cleanPhoneNumber(phone: String): String {
            return phone.replace(Regex("[\\s\\-().]"), "")
        }

        fun isValidPhoneNumber(phone: String): Boolean {
            val clean = cleanPhoneNumber(phone)
            return clean.matches(PHONE_REGEX)
        }

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
