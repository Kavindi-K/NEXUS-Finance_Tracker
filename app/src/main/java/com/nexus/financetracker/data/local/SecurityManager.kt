package com.nexus.financetracker.data.local

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages App Security: Encrypted 4-digit PIN and Biometric Authentication.
 */
@Singleton
class SecurityManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appSettingsManager: AppSettingsManager
) {
    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nexus_security_prefs_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback for older emulators or environments where Keystore AES is unavailable
        context.getSharedPreferences("nexus_security_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_PIN_HASH = "nexus_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "nexus_biometric_enabled"
        private const val SALT = "NEXUS_FINANCE_SALT_2026"
    }

    // In-memory session state (resets on app close / process kill)
    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    fun isSessionUnlocked(): Boolean = _isSessionUnlocked.value

    fun unlockSession() {
        _isSessionUnlocked.value = true
    }

    fun lockSession() {
        _isSessionUnlocked.value = false
    }

    // --- PIN Management ---

    fun isPinSet(): Boolean {
        return prefs.getString(KEY_PIN_HASH, null)?.isNotBlank() == true
    }

    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        val hashed = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hashed).apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return savedHash == hashPin(pin)
    }

    fun removePin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
    }

    private fun hashPin(pin: String): String {
        val bytes = (pin + SALT).toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    // --- Biometric Management ---

    fun isBiometricEnabled(): Boolean {
        return appSettingsManager.settings.value.biometricLockEnabled
    }

    fun setBiometricEnabled(enabled: Boolean) {
        appSettingsManager.setBiometricLockEnabled(enabled)
    }

    fun canAuthenticateBiometrics(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = if (isPinSet()) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Determines whether the app should present the lock screen upon opening.
     * True if the user is signed in AND either biometrics or PIN is active.
     */
    fun isAppLockActive(): Boolean {
        return isPinSet() || (isBiometricEnabled() && canAuthenticateBiometrics())
    }

    /**
     * Triggers the Android Native Biometric Prompt.
     */
    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "NEXUS Unlock",
        subtitle: String = if (isPinSet()) "Unlock with Biometrics or enter your 4-digit PIN" else "Confirm your identity to unlock NEXUS",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelOrUsePin: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlockSession()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        onCancelOrUsePin()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Biometric sensor prompt itself provides visual retry feedback
                }
            }
        )

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (isPinSet()) {
            // When custom PIN exists, provide negative button to switch to PIN
            promptInfoBuilder
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Use PIN")
        } else {
            // Fallback to device PIN/Pattern if device supports it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptInfoBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                promptInfoBuilder
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .setNegativeButtonText("Cancel")
            }
        }

        try {
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Biometric prompt error")
        }
    }
}
