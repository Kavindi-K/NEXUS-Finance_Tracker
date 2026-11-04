package com.nexus.financetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.financetracker.data.local.AppSettingsManager
import com.nexus.financetracker.data.local.SecurityManager
import com.nexus.financetracker.data.local.ThemeMode
import com.nexus.financetracker.data.local.dao.CategoryDao
import com.nexus.financetracker.data.local.dao.ExpenseDao
import com.nexus.financetracker.data.local.dao.GoalDao
import com.nexus.financetracker.data.local.dao.IncomeDao
import com.nexus.financetracker.data.repository.AuthRepository
import com.nexus.financetracker.data.repository.CategoryRepository
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.ui.chatbot.ChatHistoryRepository
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
    val isPinSet: Boolean = false,
    val baseCurrency: String = "LKR",
    val defaultExpenseAccount: String = "Card",
    val geminiApiKey: String = "",
    val userEmail: String? = null,
    val displayName: String? = null,
    val cachedExpensesCount: Int = 0,
    val isClearingCache: Boolean = false,
    val message: String? = null
)

/**
 * ViewModel for Settings and App Preferences.
 * Coordinates logout with Member 1, theme with Member 3, and preferences persistence across all modules.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsManager: AppSettingsManager,
    private val securityManager: SecurityManager,
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
    private val expenseDao: ExpenseDao,
    private val incomeDao: IncomeDao,
    private val goalDao: GoalDao,
    private val categoryDao: CategoryDao,
    private val chatHistoryRepository: ChatHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(isPinSet = securityManager.isPinSet()))
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
                    defaultExpenseAccount = settings.defaultExpenseAccount,
                    geminiApiKey = settings.geminiApiKey
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
                val expCount = expenseDao.getExpenseCountByUser(uid)
                val incCount = incomeDao.getIncomeCountByUser(uid)
                val goalCount = goalDao.getGoalCountByUser(uid)
                val catCount = categoryDao.getCategoryCountByUser(uid)
                _uiState.value = _uiState.value.copy(
                    cachedExpensesCount = expCount + incCount + goalCount + catCount
                )
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
            incomeRepository.clearLocalCache(uid)
            goalRepository.clearLocalCache(uid)
            categoryRepository.clearLocalCache(uid)

            loadCacheMetrics()
            _uiState.value = _uiState.value.copy(
                isClearingCache = false,
                message = "Cleared $currentCount cached local records across all modules"
            )
            onComplete(currentCount)
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        val uid = currentUserId
        viewModelScope.launch {
            if (uid != null) {
                expenseRepository.clearLocalCache(uid)
                incomeRepository.clearLocalCache(uid)
                goalRepository.clearLocalCache(uid)
                categoryRepository.clearLocalCache(uid)
                chatHistoryRepository.clearHistory(uid)
            }
            authRepository.signOut()
            onSuccess()
        }
    }

    fun setPin(pin: String): Boolean {
        val success = securityManager.setPin(pin)
        if (success) {
            _uiState.value = _uiState.value.copy(isPinSet = true, message = "4-digit PIN saved successfully")
        }
        return success
    }

    fun removePin() {
        securityManager.removePin()
        _uiState.value = _uiState.value.copy(isPinSet = false, message = "4-digit PIN removed")
    }

    fun setGeminiApiKey(key: String) {
        appSettingsManager.setGeminiApiKey(key)
        _uiState.value = _uiState.value.copy(message = "Gemini AI API Key saved successfully")
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
