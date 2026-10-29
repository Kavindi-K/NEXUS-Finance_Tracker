package com.nexus.financetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.financetracker.data.local.AppSettingsManager
import com.nexus.financetracker.data.local.ThemeMode
import com.nexus.financetracker.data.local.dao.ExpenseDao
import com.nexus.financetracker.data.repository.AuthRepository
import com.nexus.financetracker.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val baseCurrency: String = "LKR",
    val defaultExpenseAccount: String = "Card",
    val userEmail: String? = null,
    val displayName: String? = null,
    val cachedExpensesCount: Int = 0,
    val isClearingCache: Boolean = false,
    val message: String? = null
)

/**
 * ViewModel for Settings and App Preferences (Member 2 Order 9: feature/app-settings).
 * Coordinates logout with Member 1, theme with Member 3, and preferences persistence.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsManager: AppSettingsManager,
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository,
    private val expenseDao: ExpenseDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val currentUserId: String?
        get() = authRepository.currentUserId

    init {
        observeSettings()
        loadUserProfile()
        loadCacheMetrics()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            appSettingsManager.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    themeMode = settings.themeMode,
                    notificationsEnabled = settings.notificationsEnabled,
                    biometricLockEnabled = settings.biometricLockEnabled,
                    baseCurrency = settings.baseCurrency,
                    defaultExpenseAccount = settings.defaultExpenseAccount
                )
            }
        }
    }

    private fun loadUserProfile() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            authRepository.getUserProfile(uid).collect { profile ->
                if (profile != null) {
                    _uiState.value = _uiState.value.copy(
                        userEmail = profile.email,
                        displayName = profile.capitalizedDisplayName
                    )
                }
            }
        }
    }

    fun loadCacheMetrics() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            try {
                val count = expenseDao.getExpenseCountByUser(uid)
                _uiState.value = _uiState.value.copy(cachedExpensesCount = count)
            } catch (_: Exception) {}
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        appSettingsManager.setThemeMode(mode)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        appSettingsManager.setNotificationsEnabled(enabled)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        appSettingsManager.setBiometricLockEnabled(enabled)
    }

    fun setBaseCurrency(currency: String) {
        appSettingsManager.setBaseCurrency(currency)
    }

    fun setDefaultExpenseAccount(account: String) {
        appSettingsManager.setDefaultExpenseAccount(account)
    }

    fun clearLocalCache(onComplete: (Int) -> Unit) {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearingCache = true)
            val currentCount = _uiState.value.cachedExpensesCount
            expenseRepository.clearLocalCache(uid)
            val newCount = expenseDao.getExpenseCountByUser(uid)
            _uiState.value = _uiState.value.copy(
                isClearingCache = false,
                cachedExpensesCount = newCount,
                message = "Cleared $currentCount cached local records"
            )
            onComplete(currentCount)
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        val uid = currentUserId
        viewModelScope.launch {
            if (uid != null) {
                expenseRepository.clearLocalCache(uid)
            }
            authRepository.signOut()
            onSuccess()
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

