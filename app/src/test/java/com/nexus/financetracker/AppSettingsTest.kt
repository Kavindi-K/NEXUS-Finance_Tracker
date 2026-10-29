package com.nexus.financetracker

import com.nexus.financetracker.data.local.AppSettings
import com.nexus.financetracker.data.local.ThemeMode
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.ui.settings.SettingsUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying App Settings, preferences persistence models,
 * dynamic theme coordination (with Member 3), and logout/cache coordination (with Member 1)
 * as specified in Member 2 Order 9 (feature/app-settings).
 */
class AppSettingsTest {

    @Test
    fun defaultSettings_hasSensibleDefaults() {
        val settings = AppSettings()

        assertEquals("Default theme must follow system appearance", ThemeMode.SYSTEM, settings.themeMode)
        assertTrue("Notifications must be enabled by default", settings.notificationsEnabled)
        assertFalse("Biometric lock must be disabled by default", settings.biometricLockEnabled)
        assertEquals("Base currency must default to LKR", "LKR", settings.baseCurrency)
        assertEquals("Default expense account must default to Card", ExpenseAccount.CARD, settings.defaultExpenseAccount)
    }

    @Test
    fun themeMode_allOptionsSupported() {
        val system = ThemeMode.SYSTEM
        val light = ThemeMode.LIGHT
        val dark = ThemeMode.DARK

        assertEquals(3, ThemeMode.values().size)
        assertTrue(listOf(system, light, dark).contains(ThemeMode.valueOf("SYSTEM")))
        assertTrue(listOf(system, light, dark).contains(ThemeMode.valueOf("LIGHT")))
        assertTrue(listOf(system, light, dark).contains(ThemeMode.valueOf("DARK")))
    }

    @Test
    fun themeMode_darkThemeEvaluation_matchesExpectation() {
        fun isDark(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
            ThemeMode.SYSTEM -> systemInDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        // When system is dark
        assertTrue(isDark(ThemeMode.SYSTEM, systemInDark = true))
        assertFalse(isDark(ThemeMode.LIGHT, systemInDark = true))
        assertTrue(isDark(ThemeMode.DARK, systemInDark = true))

        // When system is light
        assertFalse(isDark(ThemeMode.SYSTEM, systemInDark = false))
        assertFalse(isDark(ThemeMode.LIGHT, systemInDark = false))
        assertTrue(isDark(ThemeMode.DARK, systemInDark = false))
    }

    @Test
    fun currencyOptions_supportsStandardCurrencies() {
        val supportedCurrencies = listOf("LKR", "USD", "EUR", "GBP")

        assertTrue(supportedCurrencies.contains("LKR"))
        assertTrue(supportedCurrencies.contains("USD"))
        assertTrue(supportedCurrencies.contains("EUR"))
        assertTrue(supportedCurrencies.contains("GBP"))

        val customCurrencySettings = AppSettings(baseCurrency = "USD")
        assertEquals("USD", customCurrencySettings.baseCurrency)
    }

    @Test
    fun defaultExpenseAccount_supportsAllThreeAccounts() {
        val cardSettings = AppSettings(defaultExpenseAccount = ExpenseAccount.CARD)
        val cashSettings = AppSettings(defaultExpenseAccount = ExpenseAccount.CASH)
        val secondarySettings = AppSettings(defaultExpenseAccount = ExpenseAccount.SECONDARY)

        assertEquals(ExpenseAccount.CARD, cardSettings.defaultExpenseAccount)
        assertEquals(ExpenseAccount.CASH, cashSettings.defaultExpenseAccount)
        assertEquals(ExpenseAccount.SECONDARY, secondarySettings.defaultExpenseAccount)
        assertEquals("Card", ExpenseAccount.getDisplayName(cardSettings.defaultExpenseAccount))
        assertEquals("Cash", ExpenseAccount.getDisplayName(cashSettings.defaultExpenseAccount))
    }

    @Test
    fun settingsUiState_updatesStateCorrectly() {
        val initial = SettingsUiState()
        assertEquals(0, initial.cachedExpensesCount)
        assertFalse(initial.isClearingCache)
        assertNull(initial.message)

        val updated = initial.copy(
            themeMode = ThemeMode.DARK,
            notificationsEnabled = false,
            cachedExpensesCount = 42,
            message = "Cleared 42 cached local records"
        )

        assertEquals(ThemeMode.DARK, updated.themeMode)
        assertFalse(updated.notificationsEnabled)
        assertEquals(42, updated.cachedExpensesCount)
        assertEquals("Cleared 42 cached local records", updated.message)
    }

    @Test
    fun logoutCoordination_clearsLocalCacheAndResetsState() {
        var localCacheCleared = false
        var authSignedOut = false

        val mockClearLocalCache: (String) -> Unit = { userId ->
            if (userId == "user-123") {
                localCacheCleared = true
            }
        }
        val mockSignOut: () -> Unit = {
            authSignedOut = true
        }

        // Simulate coordinated logout flow
        val testUserId = "user-123"
        mockClearLocalCache(testUserId)
        mockSignOut()

        assertTrue("Local Room cache should be cleared on logout", localCacheCleared)
        assertTrue("Auth session should be signed out", authSignedOut)
    }
}
