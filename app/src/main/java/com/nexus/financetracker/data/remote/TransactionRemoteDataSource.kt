package com.nexus.financetracker.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.nexus.financetracker.domain.model.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote data source — wraps all Firestore operations for transactions.
 * Uses callbackFlow to expose real-time Firestore listeners as Kotlin Flow.
 */
@Singleton
class TransactionRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun userCollection(userId: String) =
        firestore.collection("users").document(userId).collection("transactions")

    /**
     * Real-time stream of all transactions for the given user.
     * Emits a new list on every Firestore change.
     */
    fun getTransactions(userId: String): Flow<List<Transaction>> = callbackFlow {
        val listener = userCollection(userId)
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    close(error)
                    return@addSnapshotListener
                }
                val transactions = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                }
                trySend(transactions)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addTransaction(userId: String, transaction: Transaction): String {
        val ref = userCollection(userId).add(transaction).await()
        return ref.id
    }

    suspend fun updateTransaction(userId: String, transaction: Transaction) {
        userCollection(userId).document(transaction.id).set(transaction).await()
    }

    suspend fun deleteTransaction(userId: String, transactionId: String) {
        userCollection(userId).document(transactionId).delete().await()
    }
}
