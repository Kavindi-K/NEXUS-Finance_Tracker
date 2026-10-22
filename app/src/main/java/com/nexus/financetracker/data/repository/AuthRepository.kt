package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Authentication and User Profile (users/{uid}).
 * Conforms to Member 1 specification and Technical Document Section 4.2.3.
 */
interface AuthRepository {
    val currentUserId: String?
    fun getUserProfile(userId: String): Flow<UserProfile?>
    suspend fun saveUserProfile(profile: UserProfile): Result<Unit>
    suspend fun register(email: String, password: String, profile: UserProfile): Result<Unit>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    fun signOut()
}
