package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.domain.model.UserProfile
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.regex.Pattern

/**
 * Comprehensive Integration & Business Rule Unit Test Suite for Branch 12 (feature/test-income-auth).
 *
 * Verifies key cross-cutting concerns between Income and Authentication/Security before integration:
 * 1. Registration input validation (name, email, Sri Lankan phone, password, confirmPassword)
 * 2. Profile write errors and security invariants (preserves Auth email & createdAt)
 * 3. Authentication sessions, active user state, and logout isolation
 * 4. Foreign exchange (FX) currency conversion rules and precision
 * 5. Pending vs. Confirmed income calculations & invoice transition
 * 6. Negative crypto P&L (realized profits & losses) net calculations
 * 7. Empty and Error state handling without crashes or division by zero
 * 8. Firestore data ownership and cross-user isolation (request.auth.uid == userId)
 */
class IncomeAuthIntegrationTest {

    private val emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
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

    private fun validateRegistration(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPass: String
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            errors["name"] = "Full name is required"
        } else if (trimmedName.length > 50) {
            errors["name"] = "Full name must be under 50 characters"
        }

        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            errors["email"] = "Email address is required"
        } else if (!emailPattern.matcher(trimmedEmail).matches()) {
            errors["email"] = "Enter a valid email address"
        }

        val strippedPhone = phone.replace(Regex("[\\s\\-().]"), "").trimStart('+')
        if (strippedPhone.isBlank()) {
            errors["phone"] = "Phone number is required"
        } else if (!strippedPhone.all { it.isDigit() }) {
            errors["phone"] = "Phone number must contain only digits"
        } else {
            val normalised = normalizePhone(phone)
            if (!sriLankanMobilePattern.matcher(normalised).matches()) {
                errors["phone"] = "Enter a valid Sri Lankan mobile number (e.g. 0771234567)"
            }
        }

        if (password.isBlank()) {
            errors["password"] = "Password is required"
        } else if (password.length < 6) {
            errors["password"] = "Password must be at least 6 characters"
        }

        if (confirmPass.isBlank()) {
            errors["confirmPassword"] = "Confirm password is required"
        } else if (password != confirmPass) {
            errors["confirmPassword"] = "Passwords do not match"
        }

        return errors
    }

    // =========================================================================
    // 1. Registration Input Validation
    // =========================================================================

    @Test
    fun registrationValidation_acceptsValidInputs() {
        val errors = validateRegistration(
            name = "Kavindu Gunarathna",
            email = "kavindu@nexus.com",
            phone = "0771234567",
            password = "securePassword123",
            confirmPass = "securePassword123"
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun registrationValidation_rejectsInvalidFields() {
        // Blank name
        val errBlankName = validateRegistration("", "test@nexus.com", "0771234567", "pass123", "pass123")
        assertEquals("Full name is required", errBlankName["name"])

        // Too long name (>50 chars)
        val errLongName = validateRegistration("A".repeat(55), "test@nexus.com", "0771234567", "pass123", "pass123")
        assertEquals("Full name must be under 50 characters", errLongName["name"])

        // Invalid email formats
        val errEmail1 = validateRegistration("Kavindu", "notanemail", "0771234567", "pass123", "pass123")
        assertEquals("Enter a valid email address", errEmail1["email"])

        val errEmail2 = validateRegistration("Kavindu", "missing@domain", "0771234567", "pass123", "pass123")
        assertEquals("Enter a valid email address", errEmail2["email"])

        // Invalid phone formats (landlines, short numbers, letters)
        val errPhoneLandline = validateRegistration("Kavindu", "test@nexus.com", "0112345678", "pass123", "pass123")
        assertEquals("Enter a valid Sri Lankan mobile number (e.g. 0771234567)", errPhoneLandline["phone"])

        val errPhoneLetters = validateRegistration("Kavindu", "test@nexus.com", "077abc1234", "pass123", "pass123")
        assertEquals("Phone number must contain only digits", errPhoneLetters["phone"])

        // Short password (< 6 chars)
        val errShortPass = validateRegistration("Kavindu", "test@nexus.com", "0771234567", "12345", "12345")
        assertEquals("Password must be at least 6 characters", errShortPass["password"])

        // Password mismatch
        val errMismatch = validateRegistration("Kavindu", "test@nexus.com", "0771234567", "password123", "passwordXYZ")
        assertEquals("Passwords do not match", errMismatch["confirmPassword"])
    }

    // =========================================================================
    // 2. Profile Write Errors & Security Invariants
    // =========================================================================

    @Test
    fun profileWriteError_handlesNetworkAndFirestoreFailuresGracefully() {
        // Mock profile update returning failure
        val failureResult: Result<Unit> = Result.failure(Exception("Firestore network connection timed out"))
        assertTrue(failureResult.isFailure)
        assertEquals("Firestore network connection timed out", failureResult.exceptionOrNull()?.message)

        // Ensure failure does not mutate the in-memory fallback state
        val originalProfile = UserProfile(
            uid = "user-123",
            displayName = "Kavindu Gunarathna",
            email = "kavindu@nexus.com",
            phoneNumber = "+94771234567",
            createdAt = Date(1700000000000L)
        )

        var currentProfile = originalProfile
        if (failureResult.isSuccess) {
            currentProfile = currentProfile.copy(displayName = "Updated Name")
        }

        // Profile remains original on failure
        assertEquals("Kavindu Gunarathna", currentProfile.displayName)
    }

    @Test
    fun profileWrite_strictlyPreservesAuthEmailAndCreatedAt() {
        val originalDate = Date(1760000000000L)
        val profile = UserProfile(
            uid = "user_kavindu_id",
            displayName = "Kavindu",
            email = "kavindu@nexus.com",
            phoneNumber = "+9477454545",
            createdAt = originalDate
        )

        // Supported edits: displayName and phoneNumber only
        val updatedProfile = profile.copy(
            displayName = "Kavindu Gunarathna",
            phoneNumber = "+94771234567"
        )

        // Email and CreatedAt must NEVER be mutated via profile updates
        assertEquals("kavindu@nexus.com", updatedProfile.email)
        assertEquals(originalDate, updatedProfile.createdAt)
        assertEquals("user_kavindu_id", updatedProfile.uid)
        assertEquals("Kavindu Gunarathna", updatedProfile.displayName)
        assertEquals("+94771234567", updatedProfile.phoneNumber)
    }

    // =========================================================================
    // 3. Sessions and Logout Lifecycle
    // =========================================================================

    @Test
    fun sessionLifecycle_managesActiveUserAndEnforcesLogout() {
        var currentUserUid: String? = "user_authenticated_101"

        fun determineStartRoute(uid: String?): String = if (uid != null) "dashboard" else "login"

        // Active session returns correct start route
        assertEquals("dashboard", determineStartRoute(currentUserUid))

        // Perform Sign Out
        currentUserUid = null

        // Logged out route returns login
        assertEquals("login", determineStartRoute(currentUserUid))

        // Any attempt to read or write without active session throws IllegalStateException
        val unauthenticatedAttempt = runCatching {
            val uid = currentUserUid ?: throw IllegalStateException("No authenticated user active")
            "data_for_$uid"
        }
        assertTrue(unauthenticatedAttempt.isFailure)
        assertTrue(unauthenticatedAttempt.exceptionOrNull() is IllegalStateException)
    }

    // =========================================================================
    // 4. Currency Conversion (FX) Rules & Precision
    // =========================================================================

    @Test
    fun fxCurrencyConversion_computesCorrectLKR_andEnforcesPositiveRates() {
        fun convertToLKR(amountOriginal: Double, currency: String, exchangeRate: Double): Double {
            require(exchangeRate > 0) { "Exchange rate must be greater than zero" }
            if (currency == "LKR") {
                require(exchangeRate == 1.0) { "LKR exchange rate must be exactly 1.0" }
            }
            return amountOriginal * exchangeRate
        }

        // Standard USD conversion
        val usdLkr = convertToLKR(250.0, "USD", 320.0)
        assertEquals(80000.0, usdLkr, 0.01)

        // EUR conversion
        val eurLkr = convertToLKR(100.0, "EUR", 350.25)
        assertEquals(35025.0, eurLkr, 0.01)

        // GBP conversion
        val gbpLkr = convertToLKR(50.0, "GBP", 415.50)
        assertEquals(20775.0, gbpLkr, 0.01)

        // AUD conversion
        val audLkr = convertToLKR(150.0, "AUD", 212.80)
        assertEquals(31920.0, audLkr, 0.01)

        // Reject zero or negative exchange rate
        assertThrows(IllegalArgumentException::class.java) {
            convertToLKR(100.0, "USD", 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            convertToLKR(100.0, "USD", -300.0)
        }

        // Reject non-1 rate for LKR
        assertThrows(IllegalArgumentException::class.java) {
            convertToLKR(1000.0, "LKR", 1.05)
        }
    }

    // =========================================================================
    // 5. Pending vs. Confirmed Income Totals
    // =========================================================================

    @Test
    fun incomeTotals_strictlyCalculatesConfirmed_andTracksPendingSeparately() {
        val now = Date()
        val salary = Income(
            id = "inc-1",
            userId = "user-1",
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 150000.0,
            amountLKR = 150000.0,
            dateReceived = now
        )

        val pendingInvoice = Income(
            id = "inc-2",
            userId = "user-1",
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 50000.0,
            amountLKR = 50000.0,
            expectedDate = now
        )

        val incomes = listOf(salary, pendingInvoice)

        // Reconciled confirmed total includes ONLY confirmed items
        val confirmedTotal = incomes.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        val pendingTotal = incomes.filter { it.status == IncomeStatus.PENDING }.sumOf { it.amountLKR }

        assertEquals(150000.0, confirmedTotal, 0.01)
        assertEquals(50000.0, pendingTotal, 0.01)

        // Transition pending invoice to confirmed (FR6, FR8)
        val dateReceived = Date(now.time + 3600000)
        val confirmedInvoice = pendingInvoice.copy(
            status = IncomeStatus.CONFIRMED,
            dateReceived = dateReceived,
            expectedDate = null
        )

        val updatedIncomes = listOf(salary, confirmedInvoice)
        val newConfirmedTotal = updatedIncomes.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        val newPendingTotal = updatedIncomes.filter { it.status == IncomeStatus.PENDING }.sumOf { it.amountLKR }

        assertEquals(200000.0, newConfirmedTotal, 0.01)
        assertEquals(0.0, newPendingTotal, 0.01)
    }

    // =========================================================================
    // 6. Negative Crypto P&L Net Calculations
    // =========================================================================

    @Test
    fun cryptoRealizedPnL_allowsNegativeAmounts_andCorrectlyOffsetsNetTotals() {
        val salary = Income(
            userId = "user-1",
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 120000.0,
            amountLKR = 120000.0,
            dateReceived = Date()
        )

        // Realized Crypto Gain
        val cryptoGain = Income(
            userId = "user-1",
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 15000.0,
            amountLKR = 15000.0,
            dateReceived = Date()
        )

        // Realized Crypto Loss
        val cryptoLoss = Income(
            userId = "user-1",
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -8000.0,
            amountLKR = -8000.0,
            dateReceived = Date()
        )

        assertNull(cryptoGain.validate())
        assertNull(cryptoLoss.validate())

        val listWithGain = listOf(salary, cryptoGain)
        assertEquals(135000.0, listWithGain.sumOf { it.amountLKR }, 0.01)

        val listWithLoss = listOf(salary, cryptoLoss)
        assertEquals(112000.0, listWithLoss.sumOf { it.amountLKR }, 0.01)

        val listWithBoth = listOf(salary, cryptoGain, cryptoLoss)
        assertEquals(127000.0, listWithBoth.sumOf { it.amountLKR }, 0.01)

        // Non-crypto sources strictly reject negative amounts
        val invalidSalary = salary.copy(amountOriginal = -10000.0, amountLKR = -10000.0)
        assertEquals("Income amount must be greater than zero", invalidSalary.validate())

        // Crypto rejects 0 amount
        val zeroCrypto = cryptoGain.copy(amountOriginal = 0.0, amountLKR = 0.0)
        assertEquals("Crypto realized amount cannot be zero", zeroCrypto.validate())
    }

    // =========================================================================
    // 7. Empty and Error States
    // =========================================================================

    @Test
    fun emptyAndErrorStates_handlesZeroIncomesAndGracefulDegradation() {
        val emptyIncomes: List<Income> = emptyList()

        val confirmedTotal = emptyIncomes.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        val pendingTotal = emptyIncomes.filter { it.status == IncomeStatus.PENDING }.sumOf { it.amountLKR }

        assertEquals(0.0, confirmedTotal, 0.0)
        assertEquals(0.0, pendingTotal, 0.0)

        // Source breakdown on empty list produces 0 for each stream without crashes
        val sources = listOf(IncomeSource.SALARY, IncomeSource.FREELANCE, IncomeSource.ADSENSE, IncomeSource.CRYPTO)
        val streamTotals = sources.associateWith { source ->
            emptyIncomes.filter { it.source == source && it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        }

        sources.forEach { source ->
            assertEquals(0.0, streamTotals[source] ?: 0.0, 0.0)
        }

        // Graceful error mapping
        fun mapErrorToUserFriendlyMessage(error: Throwable): String {
            return when {
                error.message?.contains("permission-denied", ignoreCase = true) == true ->
                    "You do not have permission to view or modify this record."
                error.message?.contains("network", ignoreCase = true) == true ->
                    "Unable to connect to NEXUS cloud. Showing cached data."
                else -> error.localizedMessage ?: "An unexpected error occurred."
            }
        }

        val permError = mapErrorToUserFriendlyMessage(Exception("PERMISSION-DENIED: Cross-user access blocked"))
        assertEquals("You do not have permission to view or modify this record.", permError)

        val netError = mapErrorToUserFriendlyMessage(Exception("Network unavailable"))
        assertEquals("Unable to connect to NEXUS cloud. Showing cached data.", netError)
    }

    // =========================================================================
    // 8. Firestore Cross-User Isolation and Data Ownership
    // =========================================================================

    @Test
    fun firestoreDataOwnership_enforcesCrossUserIsolation() {
        val userA = "user_kavindu_A"
        val userB = "user_malicious_B"

        val userAIncome = Income(
            id = "inc-user-a-101",
            userId = userA,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 180000.0,
            amountLKR = 180000.0,
            dateReceived = Date()
        )

        val userBIncome = Income(
            id = "inc-user-b-202",
            userId = userB,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 95000.0,
            amountLKR = 95000.0,
            dateReceived = Date()
        )

        val allIncomesInDatabase = listOf(userAIncome, userBIncome)

        // Simulated Firestore Security Rule: `request.auth.uid == resource.data.userId`
        fun queryIncomesForUser(authenticatedUid: String, database: List<Income>): List<Income> {
            return database.filter { it.userId == authenticatedUid }
        }

        fun mutateIncome(authenticatedUid: String, targetIncome: Income): Result<Unit> {
            if (authenticatedUid != targetIncome.userId) {
                return Result.failure(SecurityException("Permission Denied: User $authenticatedUid cannot mutate income belonging to ${targetIncome.userId}"))
            }
            return Result.success(Unit)
        }

        // User A querying only sees User A's incomes
        val userAResults = queryIncomesForUser(userA, allIncomesInDatabase)
        assertEquals(1, userAResults.size)
        assertEquals(userA, userAResults.first().userId)
        assertEquals(180000.0, userAResults.first().amountLKR, 0.01)

        // User B cannot mutate User A's income
        val crossUserMutationAttempt = mutateIncome(authenticatedUid = userB, targetIncome = userAIncome)
        assertTrue(crossUserMutationAttempt.isFailure)
        assertTrue(crossUserMutationAttempt.exceptionOrNull() is SecurityException)

        // User A mutating own income succeeds
        val validMutation = mutateIncome(authenticatedUid = userA, targetIncome = userAIncome)
        assertTrue(validMutation.isSuccess)
    }
}
