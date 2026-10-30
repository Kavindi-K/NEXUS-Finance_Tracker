package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.DocumentChange
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ExpenseRepository connecting to Firestore "expenses" collection
 * with persistent offline write queueing and Room as the reactive local read cache (FR9-FR14, Member 2 Order 7).
 *
 * Conforms to Member 2 Order 7:
 * - Uses Firestore Android offline persistence and queued writes for offline entry.
 * - Room remains the primary read cache for instantaneous, zero-latency UI updates.
 * - Client-side ID generation avoids blocking on network availability.
 * - Reconnect sync handles two-way updates without needing complex custom engines.
 * - Room cache isolation per user via clearLocalCache(userId).
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val expenseDao: ExpenseDao
) : ExpenseRepository {

    private val expensesCollection = firestore.collection("expenses")
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override fun getExpenses(userId: String): Flow<List<Expense>> = callbackFlow {
        // 1. Room acts as the primary reactive read cache
        val roomJob = repositoryScope.launch {
            expenseDao.getExpensesByUser(userId).collect { entities ->
                trySend(entities.map { it.toDomain() })
            }
        }

        // 2. Real-time Firestore snapshot listener synchronizes remote changes into Room
        val listener = expensesCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Offline or disconnected: Room continues to serve cached data seamlessly
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    repositoryScope.launch {
                        for (change in snapshot.documentChanges) {
                            when (change.type) {
                                DocumentChange.Type.ADDED,
                                DocumentChange.Type.MODIFIED -> {
                                    val exp = change.document.toObject(Expense::class.java)
                                        ?.copy(id = change.document.id)
                                    if (exp != null) {
                                        try {
                                            expenseDao.insertExpense(ExpenseEntity.fromDomain(exp))
                                        } catch (_: Exception) {}
                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    try {
                                        expenseDao.deleteExpenseById(change.document.id)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
            }

        awaitClose {
            roomJob.cancel()
            listener.remove()
        }
    }

    override fun getExpensesByDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<Expense>> = callbackFlow {
        // 1. Room read cache for date range queries
        val roomJob = repositoryScope.launch {
            expenseDao.getExpensesByDateRange(userId, startDate.time, endDate.time).collect { entities ->
                trySend(entities.map { it.toDomain() })
            }
        }

        // 2. Sync from Firestore into Room
        val listener = expensesCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .orderBy("date", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    repositoryScope.launch {
                        for (change in snapshot.documentChanges) {
                            when (change.type) {
                                DocumentChange.Type.ADDED,
                                DocumentChange.Type.MODIFIED -> {
                                    val exp = change.document.toObject(Expense::class.java)
                                        ?.copy(id = change.document.id)
                                    if (exp != null) {
                                        try {
                                            expenseDao.insertExpense(ExpenseEntity.fromDomain(exp))
                                        } catch (_: Exception) {}
                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    try {
                                        expenseDao.deleteExpenseById(change.document.id)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
            }

        awaitClose {
            roomJob.cancel()
            listener.remove()
        }
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
        // Client-side ID generation ensures instant local persistence without blocking on network availability
        val docRef = if (expense.id.isNotBlank()) expensesCollection.document(expense.id) else expensesCollection.document()
        val withId = expense.copy(id = docRef.id)

        // 1. Write to Room local cache first for instantaneous UI update and resilience
        try {
            expenseDao.insertExpense(ExpenseEntity.fromDomain(withId))
        } catch (_: Exception) {}

        // 2. Queue write in Firestore (persisted to SQLite disk cache and synced once reconnected)
        try {
            docRef.set(withId.toFirestoreMap()).await()
        } catch (_: Exception) {
            // Write remains queued in Firestore offline cache if offline
        }

        return docRef.id
    }

    override suspend fun addExpenses(expenses: List<Expense>): List<String> {
        if (expenses.isEmpty()) return emptyList()
        val createdIds = mutableListOf<String>()
        val entitiesToCache = mutableListOf<ExpenseEntity>()
        val preparedExpenses = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Expense>>()

        expenses.forEach { exp ->
            val docRef = if (exp.id.isNotBlank()) expensesCollection.document(exp.id) else expensesCollection.document()
            val withId = exp.copy(id = docRef.id)
            createdIds.add(docRef.id)
            entitiesToCache.add(ExpenseEntity.fromDomain(withId))
            preparedExpenses.add(docRef to withId)
        }

        // 1. Write to Room first so all items immediately reflect in local read cache
        try {
            expenseDao.insertAll(entitiesToCache)
        } catch (_: Exception) {}

        // 2. Queue Firestore batch writes (chunked into 400 operations)
        preparedExpenses.chunked(400).forEach { chunk ->
            try {
                val batch = firestore.batch()
                chunk.forEach { (ref, exp) ->
                    batch.set(ref, exp.toFirestoreMap())
                }
                batch.commit().await()
            } catch (_: Exception) {
                // Batch is queued locally by Firestore offline persistence
            }
        }

        return createdIds
    }

    override suspend fun updateExpense(expense: Expense) {
        // 1. Update Room local cache first
        try {
            expenseDao.updateExpense(ExpenseEntity.fromDomain(expense))
        } catch (_: Exception) {}

        // 2. Queue update in Firestore persistent cache
        try {
            expensesCollection.document(expense.id).set(expense.toFirestoreMap()).await()
        } catch (_: Exception) {}
    }

    override suspend fun deleteExpense(expenseId: String) {
        // 1. Delete from Room cache first
        try {
            expenseDao.deleteExpenseById(expenseId)
        } catch (_: Exception) {}

        // 2. Queue deletion in Firestore persistent cache
        try {
            expensesCollection.document(expenseId).delete().await()
        } catch (_: Exception) {}
    }

    override suspend fun clearLocalCache(userId: String) {
        try {
            expenseDao.deleteAllForUser(userId)
        } catch (_: Exception) {}
    }
}
