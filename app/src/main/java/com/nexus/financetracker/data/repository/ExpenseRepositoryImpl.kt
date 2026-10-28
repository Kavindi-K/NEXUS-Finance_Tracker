package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexus.financetracker.data.local.dao.ExpenseDao
import com.nexus.financetracker.data.local.entity.ExpenseEntity
import com.nexus.financetracker.domain.model.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ExpenseRepository connecting to Firestore "expenses" collection
 * with Room local caching for offline read resilience (FR9-FR14, Member 2 Order 2).
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val expenseDao: ExpenseDao
) : ExpenseRepository {

    private val expensesCollection = firestore.collection("expenses")
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

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

                // Sync latest remote list to local Room cache
                repositoryScope.launch {
                    try {
                        expenseDao.insertAll(list.map { ExpenseEntity.fromDomain(it) })
                    } catch (_: Exception) {}
                }

                trySend(list)
            }
        awaitClose { listener.remove() }
    }.catch {
        // Fallback to local Room cache when remote query fails (e.g. offline / disconnected)
        emitAll(expenseDao.getExpensesByUser(userId).map { entities ->
            entities.map { it.toDomain() }
        })
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

                repositoryScope.launch {
                    try {
                        expenseDao.insertAll(list.map { ExpenseEntity.fromDomain(it) })
                    } catch (_: Exception) {}
                }

                trySend(list)
            }
        awaitClose { listener.remove() }
    }.catch {
        emitAll(expenseDao.getExpensesByDateRange(userId, startDate.time, endDate.time).map { entities ->
            entities.map { it.toDomain() }
        })
    }

    override suspend fun getExpenseById(expenseId: String): Expense? {
        return try {
            val local = expenseDao.getExpenseById(expenseId)?.toDomain()
            if (local != null) return local

            val snapshot = expensesCollection.document(expenseId).get().await()
            val remote = snapshot.toObject(Expense::class.java)?.copy(id = snapshot.id)
            if (remote != null) {
                try {
                    expenseDao.insertExpense(ExpenseEntity.fromDomain(remote))
                } catch (_: Exception) {}
            }
            remote
        } catch (_: Exception) {
            try {
                expenseDao.getExpenseById(expenseId)?.toDomain()
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun getLatestExpense(userId: String): Expense? {
        return try {
            val local = expenseDao.getLatestExpense(userId)?.toDomain()
            if (local != null) return local

            val snapshot = expensesCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val latest = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Expense::class.java)?.copy(id = doc.id)
            }.maxByOrNull { it.createdAt }
            latest?.let {
                try {
                    expenseDao.insertExpense(ExpenseEntity.fromDomain(it))
                } catch (_: Exception) {}
            }
            latest
        } catch (_: Exception) {
            try {
                expenseDao.getLatestExpense(userId)?.toDomain()
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun addExpense(expense: Expense): String {
        val docRef = expensesCollection.add(expense.toFirestoreMap()).await()
        val withId = expense.copy(id = docRef.id)
        try {
            expenseDao.insertExpense(ExpenseEntity.fromDomain(withId))
        } catch (_: Exception) {}
        return docRef.id
    }

    override suspend fun addExpenses(expenses: List<Expense>): List<String> {
        if (expenses.isEmpty()) return emptyList()
        val createdIds = mutableListOf<String>()
        val entitiesToCache = mutableListOf<ExpenseEntity>()

        // Use Firestore batch writes (up to 400 operations per batch chunk)
        expenses.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { exp ->
                val newDocRef = expensesCollection.document()
                batch.set(newDocRef, exp.toFirestoreMap())
                createdIds.add(newDocRef.id)
                entitiesToCache.add(ExpenseEntity.fromDomain(exp.copy(id = newDocRef.id)))
            }
            batch.commit().await()
        }

        // Cache in Room offline database via insertAll
        try {
            expenseDao.insertAll(entitiesToCache)
        } catch (_: Exception) {}

        return createdIds
    }

    override suspend fun updateExpense(expense: Expense) {
        expensesCollection.document(expense.id).set(expense.toFirestoreMap()).await()
        try {
            expenseDao.updateExpense(ExpenseEntity.fromDomain(expense))
        } catch (_: Exception) {}
    }

    override suspend fun deleteExpense(expenseId: String) {
        expensesCollection.document(expenseId).delete().await()
        try {
            expenseDao.deleteExpenseById(expenseId)
        } catch (_: Exception) {}
    }
}


