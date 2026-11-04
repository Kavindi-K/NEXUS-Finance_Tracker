package com.nexus.financetracker.data.local

import android.content.Context
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.notification.NotificationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val baseCurrency: String = "LKR",
    val defaultExpenseAccount: String = ExpenseAccount.CARD,
    val geminiApiKey: String = "",
    val geminiModel: String = "auto"
)

/**
 * Manages user application preferences with persistent SharedPreferences storage
 * and reactive StateFlow streams for system-wide reactivity (Member 2 Order 9: feature/app-settings).
 */
@Singleton
class AppSettingsManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("nexus_app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BASE_CURRENCY = "base_currency"
        private const val KEY_DEFAULT_ACCOUNT = "default_expense_account"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeString = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeString)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }

        return AppSettings(
            themeMode = themeMode,
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            biometricLockEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false),
            baseCurrency = prefs.getString(KEY_BASE_CURRENCY, "LKR") ?: "LKR",
            defaultExpenseAccount = prefs.getString(KEY_DEFAULT_ACCOUNT, ExpenseAccount.CARD) ?: ExpenseAccount.CARD,
            geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: "",
            geminiModel = prefs.getString(KEY_GEMINI_MODEL, "auto") ?: "auto"
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(notificationsEnabled = enabled)
        if (enabled) {
            NotificationScheduler.scheduleAllReminders(context)
        } else {
            NotificationScheduler.cancelAllReminders(context)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(biometricLockEnabled = enabled)
    }

    fun setBaseCurrency(currency: String) {
        prefs.edit().putString(KEY_BASE_CURRENCY, currency).apply()
        _settings.value = _settings.value.copy(baseCurrency = currency)
    }

    fun setDefaultExpenseAccount(account: String) {
        prefs.edit().putString(KEY_DEFAULT_ACCOUNT, account).apply()
        _settings.value = _settings.value.copy(defaultExpenseAccount = account)
    }

    fun setGeminiApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
        _settings.value = _settings.value.copy(geminiApiKey = apiKey.trim())
    }

    fun setGeminiModel(model: String) {
        prefs.edit().putString(KEY_GEMINI_MODEL, model.trim()).apply()
        _settings.value = _settings.value.copy(geminiModel = model.trim())
    }
}

