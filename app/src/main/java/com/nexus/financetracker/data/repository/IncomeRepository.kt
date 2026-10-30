package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.Income
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Repository interface for managing Incomes (incomes/{incomeId}).
 * Conforms to Member 1 specification and Technical Document Section 4.2.3.
 */
interface IncomeRepository {
    fun getIncomes(userId: String): Flow<List<Income>>
    fun getConfirmedIncomes(userId: String): Flow<List<Income>>
    fun getTotalConfirmedIncomeLKR(userId: String): Flow<Double>
    fun getIncomesByDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<Income>>
    fun getPendingIncomes(userId: String): Flow<List<Income>>
    suspend fun getIncomeById(incomeId: String): Income?
    suspend fun addIncome(income: Income): String
    suspend fun updateIncome(income: Income)
    suspend fun deleteIncome(incomeId: String)
    suspend fun clearLocalCache(userId: String)
}
