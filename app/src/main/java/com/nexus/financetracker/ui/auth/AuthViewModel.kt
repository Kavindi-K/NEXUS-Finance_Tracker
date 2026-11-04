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
import com.nexus.financetracker.data.local.database.AppDatabase
import com.nexus.financetracker.data.repository.AuthRepository
import com.nexus.financetracker.domain.model.UserProfile
import com.nexus.financetracker.ui.chatbot.ChatHistoryRepository
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
    private val authRepository: AuthRepository,
    private val appDatabase: AppDatabase,
    private val chatHistoryRepository: ChatHistoryRepository
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
     * Authenticates user using Google ID token and creates profile in Firestore if first login.
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signInWithGoogle(idToken)
            if (result.isSuccess) {
                uiState = uiState.copy(isLoading = false, isSuccess = true)
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage
                    ?: "Google sign in failed. Please try again."
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
        val cleanPhone = UserProfile.cleanPhoneNumber(phoneNumber)

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val profile = UserProfile(
                displayName = UserProfile.formatCapitalizedName(fullName),
                email = normalizedEmail,
                phoneNumber = cleanPhone
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
        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    appDatabase.expenseDao().deleteAllForUser(uid)
                    appDatabase.incomeDao().deleteAllForUser(uid)
                    appDatabase.goalDao().deleteAllForUser(uid)
                    appDatabase.categoryDao().deleteAllForUser(uid)
                    appDatabase.transactionDao().deleteAllForUser(uid)
                    chatHistoryRepository.clearHistory(uid)
                } catch (_: Exception) {}
            }
        }
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

        val clean = UserProfile.cleanPhoneNumber(phone)
        if (clean.isBlank()) return "Phone number is required"
        if (!UserProfile.isValidPhoneNumber(phone))
            return "Enter a valid phone number (e.g. +94778654657, 0116754378, or 748796654)"

        if (password.length < 6) return "Password must be at least 6 characters"
        if (password != confirmPwd) return "Passwords do not match"

        return null
    }

    /**
     * Validates email for password reset.
     */
    fun validateResetEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return "Email address is required"
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) return "Enter a valid email address"
        return null
    }

    /**
     * Sends password reset email via Firebase Auth.
     */
    fun sendPasswordReset(email: String) {
        val normalizedEmail = email.trim().lowercase()
        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                errorMessage = null,
                resetEmailSent = false,
                resetSuccessMessage = null
            )
            val result = authRepository.sendPasswordResetEmail(normalizedEmail)
            if (result.isSuccess) {
                uiState = uiState.copy(
                    isLoading = false,
                    resetEmailSent = true,
                    resetSuccessMessage = "A password reset link has been sent to $normalizedEmail. Please check your inbox."
                )
            } else {
                val errorMsg = when (val ex = result.exceptionOrNull()) {
                    is FirebaseAuthInvalidUserException ->
                        "No account found with this email address. Please check and try again."
                    else ->
                        ex?.localizedMessage ?: "Failed to send reset link. Please check your network and try again."
                }
                uiState = uiState.copy(isLoading = false, errorMessage = errorMsg)
            }
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val resetEmailSent: Boolean = false,
    val resetSuccessMessage: String? = null
)
