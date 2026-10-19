package com.nexus.financetracker.ui.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.nexus.financetracker.data.repository.AuthRepository
import com.nexus.financetracker.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Login and Register screens.
 *
 * Registration flow:
 *  1. Normalize email (trim + lowercase) so it matches the Firebase Auth token email.
 *  2. Normalize phone to E.164 (+94XXXXXXXXX).
 *  3. Delegate to AuthRepository.register() which handles Auth + Firestore write with rollback.
 *  4. Map FirebaseAuthUserCollisionException to a human-readable message.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val authRepository: AuthRepository
) : ViewModel() {

    var uiState by mutableStateOf(AuthUiState())
        private set

    val currentUser get() = auth.currentUser

    fun login(email: String, password: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                uiState = uiState.copy(isLoading = false, isSuccess = true)
            } else {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun register(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String
    ) {
        // Normalize email: trim whitespace and lowercase so it matches the Auth token email
        // (Firestore rule checks request.auth.token.email == profile.email)
        val normalizedEmail = email.trim().lowercase()
        val normalizedPhone = normalizePhone(phoneNumber)

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val profile = UserProfile(
                displayName = fullName.trim(),
                email = normalizedEmail,
                phoneNumber = normalizedPhone
            )
            val result = authRepository.register(normalizedEmail, password, profile)
            if (result.isSuccess) {
                uiState = uiState.copy(isLoading = false, isSuccess = true)
            } else {
                // Map duplicate-email error to a clear message
                val errorMsg = when (result.exceptionOrNull()) {
                    is FirebaseAuthUserCollisionException ->
                        "This email is already registered. Please sign in instead."
                    else ->
                        result.exceptionOrNull()?.localizedMessage
                            ?: "Registration failed. Please try again."
                }
                uiState = uiState.copy(isLoading = false, errorMessage = errorMsg)
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        uiState = AuthUiState()
    }

    fun resetState() {
        uiState = AuthUiState()
    }

    /**
     * Normalises a Sri Lankan phone number to E.164 format (+94XXXXXXXXX).
     *
     * Accepted inputs (spaces and dashes are stripped first):
     *   0771234567   → +94771234567
     *   771234567    → +94771234567
     *   94771234567  → +94771234567
     *   +94771234567 → +94771234567  (already valid, returned as-is)
     */
    internal fun normalizePhone(raw: String): String {
        val clean = raw.replace(Regex("[\\s\\-().]"), "")
        return when {
            clean.startsWith("+94") -> clean
            clean.startsWith("94")  -> "+$clean"
            clean.startsWith("0")   -> "+94" + clean.substring(1)
            else                    -> "+94$clean"
        }
    }

    /**
     * Client-side field validation — lives in the ViewModel, not the Composable.
     * Returns a human-readable error string, or null if every field is valid.
     */
    fun validateRegistration(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        confirmPwd: String
    ): String? {
        if (fullName.isBlank()) return "Full name is required"

        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) return "Email is required"
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches())
            return "Enter a valid email address"

        val stripped = phone.replace(Regex("[\\s\\-().]"), "").trimStart('+')
        if (stripped.isBlank()) return "Phone number is required"
        if (!stripped.all { it.isDigit() })
            return "Phone number must contain only digits (spaces and dashes are allowed)"
        val normalised = normalizePhone(phone)
        if (!normalised.matches(Regex("^\\+947[0-9]{8}$")))
            return "Enter a valid Sri Lankan mobile number (e.g. 0771234567)"

        if (password.length < 6) return "Password must be at least 6 characters"
        if (password != confirmPwd) return "Passwords do not match"

        return null
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)
