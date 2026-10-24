package com.nexus.financetracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.nexus.financetracker.domain.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AuthRepository connecting to Firebase Auth and Firestore "users/{uid}".
 * Conforms to FR1, FR2 and Technical Document Section 5.2.
 *
 * Registration is intentionally split into two steps so that a Firestore failure
 * does not leave a dangling Auth account:
 *   Step 1 – Create the Firebase Auth account.
 *   Step 2 – Write the Firestore profile document.
 *             On failure the Auth account is deleted (rollback) so the user can retry.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override fun getUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val profile = snapshot?.toObject(UserProfile::class.java)
                trySend(profile)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun saveUserProfile(profile: UserProfile): Result<Unit> = runCatching {
        firestore.collection("users").document(profile.uid).set(profile).await()
        runCatching {
            val reqBuilder = UserProfileChangeRequest.Builder()
                .setDisplayName(profile.displayName)
            if (!profile.photoUrl.isNullOrBlank()) {
                reqBuilder.setPhotoUri(android.net.Uri.parse(profile.photoUrl))
            }
            auth.currentUser?.updateProfile(reqBuilder.build())?.await()
        }
    }

    /**
     * Two-step registration with rollback:
     * 1. Create Auth account → get uid.
     * 2. Write Firestore profile.
     *    • If the Firestore write fails → delete the Auth account so the user can retry.
     */
    override suspend fun register(
        email: String,
        password: String,
        profile: UserProfile
    ): Result<Unit> {
        // Step 1 — Create Firebase Auth account
        val authResult = runCatching {
            auth.createUserWithEmailAndPassword(email, password).await()
        }
        if (authResult.isFailure) return Result.failure(authResult.exceptionOrNull()!!)

        val uid = authResult.getOrNull()?.user?.uid
            ?: return Result.failure(IllegalStateException("Auth UID is null after account creation"))

        // Step 2 — Write Firestore profile
        val updatedProfile = profile.copy(uid = uid, email = email)
        val profileResult = runCatching {
            firestore.collection("users").document(uid).set(updatedProfile).await()
            runCatching {
                auth.currentUser?.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(updatedProfile.displayName)
                        .build()
                )?.await()
            }
        }

        if (profileResult.isFailure) {
            // Attempt rollback: delete the Auth account so the email can be retried.
            // The rollback may itself fail (e.g. no network), so we wrap it separately.
            val rollbackResult = runCatching { auth.currentUser?.delete()?.await() }

            return if (rollbackResult.isSuccess) {
                // Clean rollback — user can retry registration freely
                Result.failure(
                    Exception("Profile could not be saved. Please check your connection and try again.")
                )
            } else {
                // Rollback failed — Auth account exists but has no Firestore profile.
                // The user must sign in and their profile will be missing until recreated.
                Result.failure(
                    Exception(
                        "Profile save failed and the account could not be fully cleaned up. " +
                        "Please try signing in — if your profile is missing, contact support."
                    )
                )
            }
        }

        return Result.success(Unit)
    }

    override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("No authenticated user")
        user.updatePassword(newPassword).await()
    }

    override fun signOut() {
        auth.signOut()
    }
}
