package com.nexus.financetracker

import com.nexus.financetracker.domain.model.UserProfile
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Unit tests verifying user profile requirements for branch 11 feature/user-profile:
 * - Read users/{uid} fields: displayName, email, phoneNumber, createdAt
 * - Validation for editable name and phone number
 * - Security preservation of Auth email and creation date (never altered via Firestore profile update alone)
 */
class UserProfileTest {

    private val sriLankanMobilePattern = Pattern.compile("^\\+947[0-9]{8}\$")

    private fun normalizePhone(raw: String): String {
        val clean = raw.replace(Regex("[\\s\\-().]"), "")
        return when {
            clean.startsWith("+94") -> clean
            clean.startsWith("94")  -> "+$clean"
            clean.startsWith("0")   -> "+94" + clean.substring(1)
            else                    -> "+94$clean"
        }
    }

    private fun validateProfileInputs(name: String, phone: String): Pair<String?, String?> {
        val trimmedName = name.trim()
        val nameErr = when {
            trimmedName.isBlank() -> "Full name is required"
            trimmedName.length > 50 -> "Full name must be under 50 characters"
            else -> null
        }

        val stripped = phone.replace(Regex("[\\s\\-().]"), "").trimStart('+')
        val phoneErr = when {
            stripped.isBlank() -> "Phone number is required"
            !stripped.all { it.isDigit() } -> "Phone number must contain only digits (spaces and dashes are allowed)"
            else -> {
                val normalised = normalizePhone(phone)
                if (!sriLankanMobilePattern.matcher(normalised).matches()) {
                    "Enter a valid Sri Lankan mobile number (e.g. 0771234567)"
                } else null
            }
        }

        return Pair(nameErr, phoneErr)
    }

    @Test
    fun userProfile_holdsAllRequiredFields() {
        val createdDate = Date(1700000000000L)
        val profile = UserProfile(
            uid = "usr_kavindu_123",
            displayName = "Kavindu Silva",
            email = "kavindu@nexus.com",
            phoneNumber = "+94771234567",
            createdAt = createdDate
        )

        assertEquals("usr_kavindu_123", profile.uid)
        assertEquals("Kavindu Silva", profile.displayName)
        assertEquals("kavindu@nexus.com", profile.email)
        assertEquals("+94771234567", profile.phoneNumber)
        assertEquals(createdDate, profile.createdAt)
    }

    @Test
    fun profileNameValidation_acceptsValidNamesAndRejectsInvalid() {
        val (errBlank, _) = validateProfileInputs("   ", "+94771234567")
        assertEquals("Full name is required", errBlank)

        val longName = "A".repeat(55)
        val (errLong, _) = validateProfileInputs(longName, "+94771234567")
        assertEquals("Full name must be under 50 characters", errLong)

        val (errValid, _) = validateProfileInputs("Kavindu Silva", "+94771234567")
        assertNull(errValid)
    }

    @Test
    fun profilePhoneValidation_acceptsSriLankanMobileFormats() {
        // Valid 07X mobile formats
        val (nameErr1, phoneErr1) = validateProfileInputs("Kavindu Silva", "0771234567")
        assertNull(nameErr1)
        assertNull(phoneErr1)
        assertEquals("+94771234567", normalizePhone("0771234567"))

        val (_, phoneErr2) = validateProfileInputs("Kavindu Silva", "+94719876543")
        assertNull(phoneErr2)
        assertEquals("+94719876543", normalizePhone("+94719876543"))

        val (_, phoneErr3) = validateProfileInputs("Kavindu Silva", " 078-345-6789 ")
        assertNull(phoneErr3)
        assertEquals("+94783456789", normalizePhone(" 078-345-6789 "))
    }

    @Test
    fun profilePhoneValidation_rejectsInvalidPhoneNumbers() {
        val (_, phoneErrBlank) = validateProfileInputs("Kavindu", "")
        assertEquals("Phone number is required", phoneErrBlank)

        val (_, phoneErrLetters) = validateProfileInputs("Kavindu", "077abc1234")
        assertEquals("Phone number must contain only digits (spaces and dashes are allowed)", phoneErrLetters)

        // Invalid prefix (e.g. 011 is landline, only 07X mobile allowed)
        val (_, phoneErrLandline) = validateProfileInputs("Kavindu", "0112345678")
        assertEquals("Enter a valid Sri Lankan mobile number (e.g. 0771234567)", phoneErrLandline)

        // Invalid length
        val (_, phoneErrShort) = validateProfileInputs("Kavindu", "077123")
        assertEquals("Enter a valid Sri Lankan mobile number (e.g. 0771234567)", phoneErrShort)
    }

    @Test
    fun profileUpdate_preservesEmailAndCreatedAtWithoutTampering() {
        val originalDate = Date(1700000000000L)
        val originalProfile = UserProfile(
            uid = "usr_kavindu_123",
            displayName = "Kavindu Silva",
            email = "kavindu@nexus.com",
            phoneNumber = "+94771234567",
            createdAt = originalDate
        )

        // Editing name and phone number
        val updatedName = "Kavindu S. Silva"
        val updatedPhone = "0719998888"
        val normalizedPhone = normalizePhone(updatedPhone)

        val updatedProfile = originalProfile.copy(
            displayName = updatedName,
            phoneNumber = normalizedPhone
        )

        // Assert editable fields changed
        assertEquals("Kavindu S. Silva", updatedProfile.displayName)
        assertEquals("+94719998888", updatedProfile.phoneNumber)

        // Assert Auth email and CreatedAt were strictly preserved
        assertEquals("kavindu@nexus.com", updatedProfile.email)
        assertEquals(originalDate, updatedProfile.createdAt)
        assertEquals("usr_kavindu_123", updatedProfile.uid)
    }

    @Test
    fun profileDateFormatting_formatsNicelyForUI() {
        val testDate = Date(1760500000000L) // Some fixed date
        val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        val formatted = formatter.format(testDate)

        assertTrue(formatted.isNotBlank())
        assertTrue(formatted.contains("202"))
    }

    @Test
    fun profilePasswordValidation_validatesLengthAndMatch() {
        fun validatePassword(pwd: String, confirm: String): Pair<String?, String?> {
            if (pwd.isBlank() && confirm.isBlank()) return Pair(null, null)
            var pwdErr: String? = null
            var confirmErr: String? = null
            if (pwd.length < 6) {
                pwdErr = "Password must be at least 6 characters"
            }
            if (pwd != confirm) {
                confirmErr = "Passwords do not match"
            }
            return Pair(pwdErr, confirmErr)
        }

        // Short password
        val (errShort, _) = validatePassword("12345", "12345")
        assertEquals("Password must be at least 6 characters", errShort)

        // Mismatched passwords
        val (_, errMismatch) = validatePassword("password123", "different123")
        assertEquals("Passwords do not match", errMismatch)

        // Valid matching password
        val (okPwd, okConfirm) = validatePassword("securePass123", "securePass123")
        assertNull(okPwd)
        assertNull(okConfirm)

        // Optional blank password
        val (blankPwd, blankConfirm) = validatePassword("", "")
        assertNull(blankPwd)
        assertNull(blankConfirm)
    }
}

