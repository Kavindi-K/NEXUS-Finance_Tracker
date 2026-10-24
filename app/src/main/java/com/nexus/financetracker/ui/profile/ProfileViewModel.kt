package com.nexus.financetracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.financetracker.data.repository.AuthRepository
import com.nexus.financetracker.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import com.nexus.financetracker.data.remote.CloudinaryImageUploader
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val isEditing: Boolean = false,
    val nameInput: String = "",
    val phoneInput: String = "",
    val newPasswordInput: String = "",
    val confirmPasswordInput: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val nameError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null,
    val formattedCreatedAt: String = ""
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudinaryUploader: CloudinaryImageUploader
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val currentUserId: String?
        get() = authRepository.currentUserId

    init {
        loadProfile()
    }

    fun loadProfile() {
        val uid = currentUserId
        if (uid.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "No active user session found"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.getUserProfile(uid)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load profile"
                    )
                }
                .collect { profile ->
                    if (profile != null) {
                        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                        val formattedDate = try {
                            dateFormat.format(profile.createdAt)
                        } catch (e: Exception) {
                            ""
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            profile = profile,
                            nameInput = if (!_uiState.value.isEditing) profile.capitalizedDisplayName.ifBlank { profile.displayName } else _uiState.value.nameInput,
                            phoneInput = if (!_uiState.value.isEditing) profile.phoneNumber else _uiState.value.phoneInput,
                            formattedCreatedAt = formattedDate,
                            errorMessage = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Profile not found"
                        )
                    }
                }
        }
    }

    fun startEditing() {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            nameInput = currentProfile?.capitalizedDisplayName?.ifBlank { currentProfile.displayName } ?: "",
            phoneInput = currentProfile?.phoneNumber ?: "",
            newPasswordInput = "",
            confirmPasswordInput = "",
            isPasswordVisible = false,
            isConfirmPasswordVisible = false,
            nameError = null,
            phoneError = null,
            passwordError = null,
            confirmPasswordError = null,
            userMessage = null,
            errorMessage = null
        )
    }

    fun cancelEditing() {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            isEditing = false,
            nameInput = currentProfile?.displayName ?: "",
            phoneInput = currentProfile?.phoneNumber ?: "",
            newPasswordInput = "",
            confirmPasswordInput = "",
            isPasswordVisible = false,
            isConfirmPasswordVisible = false,
            nameError = null,
            phoneError = null,
            passwordError = null,
            confirmPasswordError = null,
            userMessage = null,
            errorMessage = null
        )
    }

    fun onNameChanged(newName: String) {
        _uiState.value = _uiState.value.copy(
            nameInput = newName,
            nameError = if (newName.isNotBlank()) null else _uiState.value.nameError
        )
    }

    fun onPhoneChanged(newPhone: String) {
        _uiState.value = _uiState.value.copy(
            phoneInput = newPhone,
            phoneError = null
        )
    }

    fun onNewPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            newPasswordInput = password,
            passwordError = null
        )
    }

    fun onConfirmPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            confirmPasswordInput = password,
            confirmPasswordError = null
        )
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            isPasswordVisible = !_uiState.value.isPasswordVisible
        )
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            isConfirmPasswordVisible = !_uiState.value.isConfirmPasswordVisible
        )
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null, errorMessage = null)
    }

    fun normalizePhone(raw: String): String {
        val clean = raw.replace(Regex("[\\s\\-().]"), "")
        return when {
            clean.startsWith("+94") -> clean
            clean.startsWith("94")  -> "+$clean"
            clean.startsWith("0")   -> "+94" + clean.substring(1)
            else                    -> "+94$clean"
        }
    }

    fun validateInputs(): Boolean {
        var hasError = false
        val trimmedName = _uiState.value.nameInput.trim()
        val nameErr = if (trimmedName.isBlank()) {
            hasError = true
            "Full name is required"
        } else if (trimmedName.length > 50) {
            hasError = true
            "Full name must be under 50 characters"
        } else null

        val rawPhone = _uiState.value.phoneInput
        val clean = UserProfile.cleanPhoneNumber(rawPhone)
        val phoneErr = if (clean.isBlank()) {
            hasError = true
            "Phone number is required"
        } else if (!UserProfile.isValidPhoneNumber(rawPhone)) {
            hasError = true
            "Enter a valid phone number (e.g. +94778654657, 0116754378, or 748796654)"
        } else null

        val pwd = _uiState.value.newPasswordInput
        val confirmPwd = _uiState.value.confirmPasswordInput
        var pwdErr: String? = null
        var confirmPwdErr: String? = null

        if (pwd.isNotBlank()) {
            if (pwd.length < 6) {
                hasError = true
                pwdErr = "Password must be at least 6 characters"
            }
            if (pwd != confirmPwd) {
                hasError = true
                confirmPwdErr = "Passwords do not match"
            }
        } else if (confirmPwd.isNotBlank()) {
            hasError = true
            confirmPwdErr = "Please enter and confirm your new password"
        }

        _uiState.value = _uiState.value.copy(
            nameError = nameErr,
            phoneError = phoneErr,
            passwordError = pwdErr,
            confirmPasswordError = confirmPwdErr
        )
        return !hasError
    }

    fun saveProfile() {
        if (!validateInputs()) return

        val existingProfile = _uiState.value.profile ?: return
        val cleanPhone = UserProfile.cleanPhoneNumber(_uiState.value.phoneInput)
        val formattedName = UserProfile.formatCapitalizedName(_uiState.value.nameInput)
        val pwdToUpdate = _uiState.value.newPasswordInput

        // Notice: email and createdAt are strictly preserved.
        // As required: do not change Auth email by updating Firestore alone.
        val updatedProfile = existingProfile.copy(
            displayName = formattedName,
            phoneNumber = cleanPhone
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, userMessage = null)

            // If a new password was provided, update it via Firebase Auth
            if (pwdToUpdate.isNotBlank()) {
                val pwdResult = authRepository.updatePassword(pwdToUpdate)
                if (pwdResult.isFailure) {
                    val err = pwdResult.exceptionOrNull()?.localizedMessage ?: "Failed to update password"
                    _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = err)
                    return@launch
                }
            }

            val result = authRepository.saveUserProfile(updatedProfile)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isEditing = false,
                    profile = updatedProfile,
                    nameInput = updatedProfile.displayName,
                    phoneInput = updatedProfile.phoneNumber,
                    newPasswordInput = "",
                    confirmPasswordInput = "",
                    userMessage = if (pwdToUpdate.isNotBlank()) "Profile and password updated successfully" else "Profile updated successfully"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to save profile"
                )
            }
        }
    }

    fun uploadProfilePhoto(uri: android.net.Uri) {
        val uid = currentUserId ?: run {
            android.util.Log.e("ProfilePhoto", "currentUserId is null in uploadProfilePhoto")
            return
        }
        android.util.Log.d("ProfilePhoto", "uploadProfilePhoto called for $uid with uri: $uri")
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null, userMessage = null) }
            val uploadResult = cloudinaryUploader.uploadProfileImage(uri, uid)
            uploadResult.onSuccess { secureUrl ->
                android.util.Log.d("ProfilePhoto", "Cloudinary upload success: $secureUrl")
                val currentProfile = _uiState.value.profile
                if (currentProfile != null) {
                    val updatedProfile = currentProfile.copy(photoUrl = secureUrl)
                    val saveResult = authRepository.saveUserProfile(updatedProfile)
                    if (saveResult.isSuccess) {
                        android.util.Log.d("ProfilePhoto", "Profile saved with photoUrl in Firestore")
                        _uiState.update {
                            it.copy(
                                isUploadingPhoto = false,
                                profile = updatedProfile,
                                userMessage = "Profile photo updated successfully!"
                            )
                        }
                    } else {
                        android.util.Log.e("ProfilePhoto", "Failed to save profile: ${saveResult.exceptionOrNull()?.localizedMessage}")
                        _uiState.update {
                            it.copy(
                                isUploadingPhoto = false,
                                errorMessage = "Photo uploaded, but could not update profile: ${saveResult.exceptionOrNull()?.localizedMessage}"
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(isUploadingPhoto = false) }
                }
            }.onFailure { error ->
                android.util.Log.e("ProfilePhoto", "Cloudinary upload failed: ${error.localizedMessage}", error)
                _uiState.update {
                    it.copy(
                        isUploadingPhoto = false,
                        errorMessage = "Failed to upload photo: ${error.localizedMessage ?: "Network error"}"
                    )
                }
            }
        }
    }

    fun removeProfilePhoto() {
        val currentProfile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null, userMessage = null) }
            val updatedProfile = currentProfile.copy(photoUrl = null)
            val saveResult = authRepository.saveUserProfile(updatedProfile)
            if (saveResult.isSuccess) {
                _uiState.update {
                    it.copy(
                        isUploadingPhoto = false,
                        profile = updatedProfile,
                        userMessage = "Profile photo removed"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isUploadingPhoto = false,
                        errorMessage = saveResult.exceptionOrNull()?.localizedMessage ?: "Failed to remove photo"
                    )
                }
            }
        }
    }
}
