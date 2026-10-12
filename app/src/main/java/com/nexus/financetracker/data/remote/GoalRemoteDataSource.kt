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
 * Remote data source — wraps all Firestore operations for goals.
 */
@Singleton
class GoalRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun userCollection(userId: String) =
        firestore.collection("users").document(userId).collection("goals")

    fun getGoals(userId: String): Flow<List<Goal>> = callbackFlow {
        val listener = userCollection(userId)
            .orderBy("deadline", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    close(error)
                    return@addSnapshotListener
                }
                val goals = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Goal::class.java)?.copy(id = doc.id)
                }
                trySend(goals)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addGoal(userId: String, goal: Goal): String {
        val ref = userCollection(userId).add(goal).await()
        return ref.id
    }

    suspend fun updateGoal(userId: String, goal: Goal) {
        userCollection(userId).document(goal.id).set(goal).await()
    }

    suspend fun deleteGoal(userId: String, goalId: String) {
        userCollection(userId).document(goalId).delete().await()
    }
}
