package com.nexus.financetracker.data.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexus.financetracker.data.local.dao.IncomeDao
import com.nexus.financetracker.data.local.entity.IncomeEntity
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IncomeRepository connecting to Firestore "incomes" collection
 * with persistent offline write queueing and Room local read cache for full offline support.
 */
@Singleton
class IncomeRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val incomeDao: IncomeDao
) : IncomeRepository {

    private val incomesCollection = firestore.collection("incomes")
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override fun getIncomes(userId: String): Flow<List<Income>> = callbackFlow {
        // 1. Room acts as primary reactive read cache
        val roomJob = repositoryScope.launch {
            incomeDao.getIncomesByUser(userId).collect { entities ->
                trySend(entities.map { it.toDomain() })
            }
        }

        // 2. Real-time Firestore snapshot listener syncs remote changes into Room
        val listener = incomesCollection
            .whereEqualTo("userId", userId)
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
                                    val inc = change.document.toObject(Income::class.java)
                                        ?.copy(id = change.document.id)
                                    if (inc != null) {
                                        try {
                                            incomeDao.insertIncome(IncomeEntity.fromDomain(inc))
                                        } catch (_: Exception) {}
                                    }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    try {
                                        incomeDao.deleteIncomeById(change.document.id)
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

    override fun getConfirmedIncomes(userId: String): Flow<List<Income>> =
        incomeDao.getConfirmedIncomesByUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getTotalConfirmedIncomeLKR(userId: String): Flow<Double> =
        getConfirmedIncomes(userId).map { list ->
            list.sumOf { it.amountLKR }
        }

    override fun getIncomesByDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<Income>> = callbackFlow {
        val roomJob = repositoryScope.launch {
            incomeDao.getIncomesByDateRange(userId, startDate.time, endDate.time).collect { entities ->
                trySend(entities.map { it.toDomain() })
            }
        }

        val listener = incomesCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("dateReceived", startDate)
            .whereLessThanOrEqualTo("dateReceived", endDate)
            .orderBy("dateReceived", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val remote = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Income::class.java)?.copy(id = doc.id)
                    }
                    repositoryScope.launch {
                        try {
                            incomeDao.insertAll(remote.map { IncomeEntity.fromDomain(it) })
                        } catch (_: Exception) {}
                    }
                }
            }

        awaitClose {
            roomJob.cancel()
            listener.remove()
        }
    }

    override fun getPendingIncomes(userId: String): Flow<List<Income>> =
        incomeDao.getPendingIncomesByUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getIncomeById(incomeId: String): Income? {
        return try {
            val local = incomeDao.getIncomeById(incomeId)?.toDomain()
            if (local != null) return local

            val snapshot = incomesCollection.document(incomeId).get().await()
            val remote = snapshot.toObject(Income::class.java)?.copy(id = snapshot.id)
            if (remote != null) {
                try {
                    incomeDao.insertIncome(IncomeEntity.fromDomain(remote))
                } catch (_: Exception) {}
            }
            remote
        } catch (_: Exception) {
            incomeDao.getIncomeById(incomeId)?.toDomain()
        }
    }

    override suspend fun addIncome(income: Income): String {
        // Client-side ID generation avoids blocking on network availability
        val docRef = if (income.id.isNotBlank()) incomesCollection.document(income.id) else incomesCollection.document()
        val withId = income.copy(id = docRef.id)

        // 1. Write to Room local cache first for instantaneous UI update and resilience
        try {
            incomeDao.insertIncome(IncomeEntity.fromDomain(withId))
        } catch (_: Exception) {}

        // 2. Queue write in Firestore persistent cache
        try {
            docRef.set(withId.toFirestoreMap()).await()
        } catch (_: Exception) {
            // Write remains queued in Firestore offline cache if offline
        }

        return docRef.id
    }

    override suspend fun updateIncome(income: Income) {
        // 1. Update Room local cache first
        try {
            incomeDao.updateIncome(IncomeEntity.fromDomain(income))
        } catch (_: Exception) {}

        // 2. Queue update in Firestore persistent cache
        try {
            incomesCollection.document(income.id).set(income.toFirestoreMap()).await()
        } catch (_: Exception) {}
    }

    override suspend fun deleteIncome(incomeId: String) {
        // 1. Delete from Room cache first
        try {
            incomeDao.deleteIncomeById(incomeId)
        } catch (_: Exception) {}

        // 2. Queue deletion in Firestore persistent cache
        try {
            incomesCollection.document(incomeId).delete().await()
        } catch (_: Exception) {}
    }

    override suspend fun clearLocalCache(userId: String) {
        try {
            incomeDao.deleteAllForUser(userId)
        } catch (_: Exception) {}
    }
}
