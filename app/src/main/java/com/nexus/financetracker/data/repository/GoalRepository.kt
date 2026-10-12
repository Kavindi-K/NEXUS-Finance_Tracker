package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.Goal
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for goals.
 */
interface GoalRepository {
    fun getGoals(userId: String): Flow<List<Goal>>
    suspend fun addGoal(userId: String, goal: Goal): String
    suspend fun updateGoal(userId: String, goal: Goal)
    suspend fun deleteGoal(userId: String, goalId: String)
}
