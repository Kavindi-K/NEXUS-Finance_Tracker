package com.nexus.financetracker.data.local.dao

import androidx.room.*
import com.nexus.financetracker.data.local.entity.IncomeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for Income offline cache and reactive local read/write queries.
 */
@Dao
interface IncomeDao {

    @Query("SELECT * FROM incomes WHERE userId = :userId ORDER BY COALESCE(dateReceived, createdAt) DESC, createdAt DESC")
    fun getIncomesByUser(userId: String): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE userId = :userId AND status = 'confirmed' ORDER BY COALESCE(dateReceived, createdAt) DESC")
    fun getConfirmedIncomesByUser(userId: String): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE userId = :userId AND status = 'pending' ORDER BY createdAt DESC")
    fun getPendingIncomesByUser(userId: String): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE userId = :userId AND COALESCE(dateReceived, createdAt) >= :startDate AND COALESCE(dateReceived, createdAt) <= :endDate ORDER BY COALESCE(dateReceived, createdAt) ASC")
    fun getIncomesByDateRange(userId: String, startDate: Long, endDate: Long): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE id = :id LIMIT 1")
    suspend fun getIncomeById(id: String): IncomeEntity?

    @Query("SELECT COUNT(*) FROM incomes WHERE userId = :userId")
    suspend fun getIncomeCountByUser(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncome(income: IncomeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incomes: List<IncomeEntity>)

    @Update
    suspend fun updateIncome(income: IncomeEntity)

    @Delete
    suspend fun deleteIncome(income: IncomeEntity)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteIncomeById(id: String)

    @Query("DELETE FROM incomes WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
