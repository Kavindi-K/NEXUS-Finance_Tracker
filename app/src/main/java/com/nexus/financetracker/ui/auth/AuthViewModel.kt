package com.nexus.financetracker.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ViewModel for Login and Register screens.
 * Exposes only immutable state to the UI layer (no mutableStateOf leaking out).
 *
 * TODO: Add proper error message mapping (e.g. FirebaseAuthException codes).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    var uiState by mutableStateOf(AuthUiState())
        private set

    val currentUser get() = auth.currentUser

    fun login(email: String, password: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                uiState = uiState.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, errorMessage = e.localizedMessage)
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                uiState = uiState.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, errorMessage = e.localizedMessage)
            }
        }
    }

    fun signOut() {
        auth.signOut()
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
