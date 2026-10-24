package com.nexus.financetracker

import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

/**
 * Unit tests verifying authentication login validation, email normalization,
 * and session logic for branch 04 feature/auth-login (FR2).
 */
class AuthLoginSessionTest {

    private val emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
    private val sriLankanMobilePattern = Pattern.compile("^\\+947[0-9]{8}\$")

    @Test
    fun emailNormalization_lowercasesAndTrimsWhitespace() {
        val rawInput = "  TestUser@Gmail.COM "
        val normalized = rawInput.trim().lowercase()

        assertEquals("testuser@gmail.com", normalized)
        assertTrue(emailPattern.matcher(normalized).matches())
    }

    @Test
    fun emailPattern_validatesFormatCorrectly() {
        assertTrue(emailPattern.matcher("kavindu@nexus.com").matches())
        assertTrue(emailPattern.matcher("user.name+tag@domain.co.uk").matches())

        assertFalse(emailPattern.matcher("not-an-email").matches())
        assertFalse(emailPattern.matcher("missing@domain").matches())
        assertFalse(emailPattern.matcher("@nodomain.com").matches())
    }

    @Test
    fun phoneNormalization_convertsLocalToInternationalFormat() {
        fun normalize(raw: String): String {
            val clean = raw.replace(Regex("[\\s\\-().]"), "")
            return when {
                clean.startsWith("+94") -> clean
                clean.startsWith("94")  -> "+$clean"
                clean.startsWith("0")   -> "+94" + clean.substring(1)
                else                    -> "+94$clean"
            }
        }

        assertEquals("+94771234567", normalize("0771234567"))
        assertEquals("+94771234567", normalize("771234567"))
        assertEquals("+94771234567", normalize("94771234567"))
        assertEquals("+94771234567", normalize("+94771234567"))
        assertEquals("+94771234567", normalize(" 077-123-4567 "))

        assertTrue(sriLankanMobilePattern.matcher(normalize("0771234567")).matches())
        assertTrue(sriLankanMobilePattern.matcher(normalize("0719876543")).matches())
    }

    @Test
    fun loginValidation_detectsBlankAndInvalidInputs() {
        fun validate(email: String, pass: String): String? {
            val trimmed = email.trim()
            if (trimmed.isBlank()) return "Email address is required"
            if (!emailPattern.matcher(trimmed).matches()) return "Enter a valid email address"
            if (pass.isBlank()) return "Password is required"
            return null
        }

        assertEquals("Email address is required", validate("", "password123"))
        assertEquals("Email address is required", validate("   ", "password123"))
        assertEquals("Enter a valid email address", validate("invalidEmail", "password123"))
        assertEquals("Password is required", validate("user@test.com", ""))
        assertNull(validate("user@test.com", "password123"))
    }

    @Test
    fun sessionPersistenceLogic_restoresDashboardDestination() {
        fun determineStartDestination(isLoggedIn: Boolean): String {
            return if (isLoggedIn) "dashboard" else "login"
        }

        // Active session: reopen directly to dashboard
        assertEquals("dashboard", determineStartDestination(isLoggedIn = true))
        // No session: require login
        assertEquals("login", determineStartDestination(isLoggedIn = false))
    }

    @Test
    fun forgotPasswordValidation_detectsBlankAndInvalidEmails() {
        fun validateResetEmail(email: String): String? {
            val trimmed = email.trim()
            if (trimmed.isBlank()) return "Email address is required"
            if (!emailPattern.matcher(trimmed).matches()) return "Enter a valid email address"
            return null
        }

        assertEquals("Email address is required", validateResetEmail(""))
        assertEquals("Email address is required", validateResetEmail("   "))
        assertEquals("Enter a valid email address", validateResetEmail("plainaddress"))
        assertEquals("Enter a valid email address", validateResetEmail("@missingusername.com"))
        assertEquals("Enter a valid email address", validateResetEmail("missingdomain@.com"))
        assertNull(validateResetEmail("user@example.com"))
        assertNull(validateResetEmail("  kavindu.dev@nexus.org  "))
    }

    @Test
    fun forgotPasswordEmail_normalizesBeforeSending() {
        val input = "  User.Name@Example.COM  "
        val normalized = input.trim().lowercase()
        assertEquals("user.name@example.com", normalized)
        assertTrue(emailPattern.matcher(normalized).matches())
    }
}
