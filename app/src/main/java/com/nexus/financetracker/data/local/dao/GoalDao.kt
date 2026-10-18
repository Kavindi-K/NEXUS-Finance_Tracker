package com.nexus.financetracker.data.local.dao

import androidx.room.*
import com.nexus.financetracker.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for goal CRUD operations.
 */
@Dao
interface GoalDao {

    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY targetDate ASC")
    fun getGoalsByUser(userId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalById(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Delete
    suspend fun deleteGoal(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
