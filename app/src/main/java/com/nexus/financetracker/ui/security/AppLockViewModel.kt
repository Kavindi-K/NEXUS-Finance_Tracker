package com.nexus.financetracker.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.financetracker.data.local.SecurityManager
import com.nexus.financetracker.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val enteredPin: String = "",
    val isPinSet: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val errorMessage: String? = null,
    val shakeErrorKey: Int = 0,
    val isUnlocked: Boolean = false
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppLockUiState(
            isPinSet = securityManager.isPinSet(),
            isBiometricAvailable = securityManager.isBiometricEnabled() && securityManager.canAuthenticateBiometrics(),
            isUnlocked = securityManager.isSessionUnlocked()
        )
    )
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    fun onDigit(digit: String) {
        if (_uiState.value.enteredPin.length >= 4) return
        val newPin = _uiState.value.enteredPin + digit
        _uiState.update { it.copy(enteredPin = newPin, errorMessage = null) }

        if (newPin.length == 4) {
            verifyPin(newPin)
        }
    }

    fun onDelete() {
        if (_uiState.value.enteredPin.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    enteredPin = it.enteredPin.dropLast(1),
                    errorMessage = null
                )
            }
        }
    }

    private fun verifyPin(pin: String) {
        if (securityManager.verifyPin(pin)) {
            securityManager.unlockSession()
            _uiState.update { it.copy(isUnlocked = true, errorMessage = null) }
        } else {
            _uiState.update {
                it.copy(
                    enteredPin = "",
                    errorMessage = "Incorrect PIN. Try again.",
                    shakeErrorKey = it.shakeErrorKey + 1
                )
            }
        }
    }

    fun onBiometricSuccess() {
        securityManager.unlockSession()
        _uiState.update { it.copy(isUnlocked = true, errorMessage = null) }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            securityManager.lockSession()
            onComplete()
        }
    }
}
