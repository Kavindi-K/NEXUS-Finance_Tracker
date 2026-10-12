package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for transactions.
 * The ViewModel depends on this interface — never on the implementation directly (Hilt injects it).
 */
interface TransactionRepository {
    fun getTransactions(userId: String): Flow<List<Transaction>>
    suspend fun addTransaction(userId: String, transaction: Transaction): String
    suspend fun updateTransaction(userId: String, transaction: Transaction)
    suspend fun deleteTransaction(userId: String, transactionId: String)
}
