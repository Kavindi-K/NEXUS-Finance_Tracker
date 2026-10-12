package com.nexus.financetracker.data.repository

import com.nexus.financetracker.data.local.dao.TransactionDao
import com.nexus.financetracker.data.local.entity.TransactionEntity
import com.nexus.financetracker.data.remote.TransactionRemoteDataSource
import com.nexus.financetracker.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [TransactionRepository].
 *
 * Strategy:
 * - Read: Expose local Room cache as the single source of truth.
 *   Firestore real-time listener writes to Room; UI observes Room.
 * - Write: Write to Firestore first, then cache locally.
 *
 * TODO: Wire Firestore → Room sync in a background coroutine scope.
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val remoteDataSource: TransactionRemoteDataSource
) : TransactionRepository {

    override fun getTransactions(userId: String): Flow<List<Transaction>> =
        transactionDao.getTransactionsByUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addTransaction(userId: String, transaction: Transaction): String {
        val id = remoteDataSource.addTransaction(userId, transaction)
        val withId = transaction.copy(id = id)
        transactionDao.insertTransaction(TransactionEntity.fromDomain(withId))
        return id
    }

    override suspend fun updateTransaction(userId: String, transaction: Transaction) {
        remoteDataSource.updateTransaction(userId, transaction)
        transactionDao.updateTransaction(TransactionEntity.fromDomain(transaction))
    }

    override suspend fun deleteTransaction(userId: String, transactionId: String) {
        remoteDataSource.deleteTransaction(userId, transactionId)
        transactionDao.getTransactionById(transactionId)?.let {
            transactionDao.deleteTransaction(it)
        }
    }
}
