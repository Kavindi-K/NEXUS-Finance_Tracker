package com.nexus.financetracker.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.AuthRepository
import com.nexus.financetracker.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Login and Register screens.
 * Writes UserProfile to Firestore users/{uid} upon registration.
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
                uiState = uiState.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val profile = UserProfile(
                displayName = email.substringBefore("@"),
                email = email,
                phoneNumber = "+94771234567"
            )
            val result = authRepository.register(email, password, profile)
            if (result.isSuccess) {
                uiState = uiState.copy(isLoading = false, isSuccess = true)
            } else {
                uiState = uiState.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.localizedMessage)
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
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)
