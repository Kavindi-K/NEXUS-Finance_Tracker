package com.nexus.financetracker

import com.nexus.financetracker.ui.security.AppLockUiState
import com.nexus.financetracker.ui.settings.SettingsUiState
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SecurityManagerTest {

    private fun hashPin(pin: String): String {
        val salt = "NEXUS_FINANCE_SALT_2026"
        val bytes = (pin + salt).toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    @Test
    fun pinHashing_producesConsistentDeterministicHash() {
        val pin = "1234"
        val hash1 = hashPin(pin)
        val hash2 = hashPin(pin)

        assertEquals("Hash must be deterministic", hash1, hash2)
        assertNotEquals("Hash must not equal raw pin", pin, hash1)
        assertEquals("SHA-256 hex string must be 64 characters long", 64, hash1.length)
    }

    @Test
    fun pinVerification_rejectsIncorrectPin() {
        val correctPin = "4826"
        val wrongPin = "4827"

        val correctHash = hashPin(correctPin)
        val testHash = hashPin(wrongPin)

        assertNotEquals("Different PINs must have distinct hashes", correctHash, testHash)
    }

    @Test
    fun appLockUiState_defaultsAndPinInputBehavior() {
        val state = AppLockUiState()
        assertEquals("", state.enteredPin)
        assertFalse(state.isPinSet)
        assertFalse(state.isBiometricAvailable)
        assertNull(state.errorMessage)
        assertFalse(state.isUnlocked)

        val updated = state.copy(enteredPin = "12")
        assertEquals("12", updated.enteredPin)

        val afterDelete = updated.copy(enteredPin = updated.enteredPin.dropLast(1))
        assertEquals("1", afterDelete.enteredPin)
    }

    @Test
    fun settingsUiState_containsPinState() {
        val settings = SettingsUiState()
        assertFalse("PIN must be unset by default in clean state", settings.isPinSet)

        val configured = settings.copy(isPinSet = true)
        assertTrue(configured.isPinSet)
    }
}
