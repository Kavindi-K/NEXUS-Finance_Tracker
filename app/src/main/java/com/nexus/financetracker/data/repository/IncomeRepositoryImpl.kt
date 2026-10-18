package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IncomeRepository connecting to Firestore "incomes" collection.
 * Conforms to FR3-FR8 and Technical Document Section 5.1/5.2.
 */
@Singleton
class IncomeRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : IncomeRepository {

    private val incomesCollection = firestore.collection("incomes")

    override fun getIncomes(userId: String): Flow<List<Income>> = callbackFlow {
        val listener = incomesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Income::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun getIncomesByDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<Income>> = callbackFlow {
        val listener = incomesCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("dateReceived", startDate)
            .whereLessThanOrEqualTo("dateReceived", endDate)
            .orderBy("dateReceived", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Income::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun getPendingIncomes(userId: String): Flow<List<Income>> = callbackFlow {
        val listener = incomesCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", IncomeStatus.PENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Income::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addIncome(income: Income): String {
        val docRef = incomesCollection.add(income).await()
        return docRef.id
    }

    override suspend fun updateIncome(income: Income) {
        incomesCollection.document(income.id).set(income).await()
    }

    override suspend fun deleteIncome(incomeId: String) {
        incomesCollection.document(incomeId).delete().await()
    }
}
