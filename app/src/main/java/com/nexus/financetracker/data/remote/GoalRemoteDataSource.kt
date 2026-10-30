package com.nexus.financetracker.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.nexus.financetracker.domain.model.Goal
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote data source — wraps all Firestore operations for goals in top-level "goals" collection.
 */
@Singleton
class GoalRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val goalsCollection = firestore.collection("goals")

    fun getGoals(userId: String): Flow<List<Goal>> = callbackFlow {
        val listener = goalsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    close(error)
                    return@addSnapshotListener
                }
                val goals = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Goal::class.java)?.copy(id = doc.id)
                }.sortedByDescending { it.createdAt }
                trySend(goals)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getGoalById(goalId: String): Goal? {
        val doc = goalsCollection.document(goalId).get().await()
        return if (doc.exists()) {
            doc.toObject(Goal::class.java)?.copy(id = doc.id)
        } else {
            null
        }
    }

    suspend fun addGoal(userId: String, goal: Goal): String {
        val withUser = if (goal.userId.isEmpty()) goal.copy(userId = userId) else goal
        val docRef = if (withUser.id.isNotBlank()) goalsCollection.document(withUser.id) else goalsCollection.document()
        val data = hashMapOf(
            "userId" to withUser.userId,
            "title" to withUser.title,
            "targetAmountLKR" to withUser.targetAmountLKR,
            "openingSavingsLKR" to withUser.openingSavingsLKR,
            "startDate" to withUser.startDate,
            "targetDate" to withUser.targetDate,
            "isActive" to withUser.isActive,
            "createdAt" to withUser.createdAt,
            "priority" to withUser.priority
        )
        try {
            docRef.set(data).await()
        } catch (_: Exception) {}
        return docRef.id
    }

    suspend fun updateGoal(userId: String, goal: Goal) {
        val withUser = if (goal.userId.isEmpty()) goal.copy(userId = userId) else goal
        val updates = hashMapOf<String, Any>(
            "userId" to withUser.userId,
            "title" to withUser.title,
            "targetAmountLKR" to withUser.targetAmountLKR,
            "openingSavingsLKR" to withUser.openingSavingsLKR,
            "startDate" to withUser.startDate,
            "targetDate" to withUser.targetDate,
            "isActive" to withUser.isActive,
            "priority" to withUser.priority
        )
        try {
            goalsCollection.document(goal.id).set(updates).await()
        } catch (_: Exception) {}
    }

    suspend fun deleteGoal(userId: String, goalId: String) {
        try {
            goalsCollection.document(goalId).delete().await()
        } catch (_: Exception) {}
    }
}
