package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.nexus.financetracker.domain.model.RecurringItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of RecurringItemRepository connecting to Firestore "recurringItems" collection.
 */
@Singleton
class RecurringItemRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : RecurringItemRepository {

    private val recurringCollection = firestore.collection("recurringItems")

    override fun getRecurringItems(userId: String): Flow<List<RecurringItem>> = callbackFlow {
        val listener = recurringCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(RecurringItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun getDueRecurringItems(userId: String): Flow<List<RecurringItem>> = callbackFlow {
        val listener = recurringCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("isActive", true)
            .orderBy("nextDueDate", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(RecurringItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addRecurringItem(item: RecurringItem): String {
        val docRef = if (item.id.isNotBlank()) recurringCollection.document(item.id) else recurringCollection.document()
        val withId = item.copy(id = docRef.id)
        try {
            docRef.set(withId).await()
        } catch (_: Exception) {}
        return docRef.id
    }

    override suspend fun updateRecurringItem(item: RecurringItem) {
        try {
            recurringCollection.document(item.id).set(item).await()
        } catch (_: Exception) {}
    }

    override suspend fun confirmPayment(itemId: String, expenseDate: Date) {
        try {
            recurringCollection.document(itemId).update("isActive", true).await()
        } catch (_: Exception) {}
    }
}
