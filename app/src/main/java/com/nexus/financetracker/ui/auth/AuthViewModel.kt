package com.nexus.financetracker.ui.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.nexus.financetracker.data.repository.AuthRepository
import com.nexus.financetracker.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Authentication (Login and Register) and Session Management.
 * Conforms to FR1, FR2 and Technical Document Section 4.2.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val authRepository: AuthRepository
) : ViewModel() {

    var uiState by mutableStateOf(AuthUiState())
        private set

    /** Check if a valid session exists for persistent login across app restarts. */
    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    val currentUser get() = auth.currentUser

    /**
     * Authenticates user with trimmed/lowercased email and maps Firebase Auth exceptions
     * to clean, human-readable user messages.
     */
    fun login(email: String, password: String) {
        val normalizedEmail = email.trim().lowercase()
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val result = authRepository.login(normalizedEmail, password)
            if (result.isSuccess) {
                uiState = uiState.copy(isLoading = false, isSuccess = true)
            } else {
                val errorMsg = when (val ex = result.exceptionOrNull()) {
                    is FirebaseAuthInvalidUserException ->
                        "No account found with this email. Please create an account."
                    is FirebaseAuthInvalidCredentialsException ->
                        "Invalid email or password. Please try again."
                    else ->
                        ex?.localizedMessage ?: "Sign in failed. Please check your network and credentials."
                }
                uiState = uiState.copy(isLoading = false, errorMessage = errorMsg)
            }
        }
    }

    /**
     * Validates login inputs before network authentication (kept in ViewModel per architecture rules).
     */
    fun validateLogin(email: String, password: String): String? {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) return "Email address is required"
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) return "Enter a valid email address"
        if (password.isBlank()) return "Password is required"
        return null
    }

    fun register(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String
    ) {
        val normalizedEmail = email.trim().lowercase()
        val normalizedPhone = normalizePhone(phoneNumber)

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val profile = UserProfile(
                displayName = UserProfile.formatCapitalizedName(fullName),
                email = normalizedEmail,
                phoneNumber = normalizedPhone
            )
            val result = authRepository.register(normalizedEmail, password, profile)
            if (result.isSuccess) {
                uiState = uiState.copy(isLoading = false, isSuccess = true)
            } else {
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
     * Client-side registration validation — lives in the ViewModel, not the Composable.
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
