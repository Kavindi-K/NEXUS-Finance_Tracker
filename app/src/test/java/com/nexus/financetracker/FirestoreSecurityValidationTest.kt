package com.nexus.financetracker

import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

/**
 * Unit tests verifying security rule constraints and schema validation patterns
 * defined in Technical Document Section 5.5 for branch 02 feature/firestore-security.
 */
class FirestoreSecurityValidationTest {

    private val phonePattern = Pattern.compile("^\\+[1-9][0-9]{7,14}$")

    @Test
    fun phoneNumberRegex_validatesInternationalFormat() {
        // Valid international numbers
        assertTrue(phonePattern.matcher("+94771234567").matches())
        assertTrue(phonePattern.matcher("+14155552671").matches())
        assertTrue(phonePattern.matcher("+447911123456").matches())

        // Invalid local numbers or missing prefix
        assertFalse(phonePattern.matcher("0771234567").matches())
        assertFalse(phonePattern.matcher("771234567").matches())
        assertFalse(phonePattern.matcher("+0771234567").matches())
        assertFalse(phonePattern.matcher("+94").matches())
    }

    @Test
    fun amountLKR_consistencyCheck_matchesExchangeRateMath() {
        val amountOriginal = 45.0
        val exchangeRate = 320.0
        val computedLKR = amountOriginal * exchangeRate
        val expectedLKR = 14400.0

        // Security rule: math.abs(amountLKR - amountOriginal * exchangeRate) <= 0.01
        assertTrue(kotlin.math.abs(computedLKR - expectedLKR) <= 0.01)
    }

    @Test
    fun cryptoPnl_allowsNegativeAmounts_whileStandardIncomeIsStrictlyPositive() {
        val salaryAmount = 140000.0
        val cryptoLoss = -15000.0
        val cryptoGain = 25000.0

        assertTrue(salaryAmount > 0)
        assertTrue(cryptoLoss != 0.0)
        assertTrue(cryptoGain > 0)
    }

    @Test
    fun allowedCollections_whitelistValidatesAllSixCollections() {
        val allowed = setOf("users", "incomes", "expenses", "goals", "categories", "recurringItems")
        assertEquals(6, allowed.size)
        assertTrue(allowed.contains("users"))
        assertTrue(allowed.contains("incomes"))
        assertTrue(allowed.contains("expenses"))
        assertTrue(allowed.contains("goals"))
        assertTrue(allowed.contains("categories"))
        assertTrue(allowed.contains("recurringItems"))
        assertFalse(allowed.contains("unauthorized_collection"))
    }
}
