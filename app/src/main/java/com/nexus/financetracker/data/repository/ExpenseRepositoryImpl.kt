package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexus.financetracker.domain.model.Expense
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ExpenseRepository connecting to Firestore "expenses" collection.
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ExpenseRepository {

    private val expensesCollection = firestore.collection("expenses")

    override fun getExpenses(userId: String): Flow<List<Expense>> = callbackFlow {
        val listener = expensesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Expense::class.java)?.copy(id = doc.id)
                }?.sortedWith(
                    compareByDescending<Expense> { it.date }
                        .thenByDescending { it.createdAt }
                ) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override fun getExpensesByDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<Expense>> = callbackFlow {
        val listener = expensesCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .orderBy("date", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Expense::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getExpenseById(expenseId: String): Expense? {
        val snapshot = expensesCollection.document(expenseId).get().await()
        return snapshot.toObject(Expense::class.java)?.copy(id = snapshot.id)
    }

    override suspend fun getLatestExpense(userId: String): Expense? {
        val snapshot = expensesCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Expense::class.java)?.copy(id = doc.id)
        }.maxByOrNull { it.createdAt }
    }

    override suspend fun addExpense(expense: Expense): String {
        val docRef = expensesCollection.add(expense.toFirestoreMap()).await()
        return docRef.id
    }

    override suspend fun updateExpense(expense: Expense) {
        expensesCollection.document(expense.id).set(expense.toFirestoreMap()).await()
    }

    override suspend fun deleteExpense(expenseId: String) {
        expensesCollection.document(expenseId).delete().await()
    }
}

