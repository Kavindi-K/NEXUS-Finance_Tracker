package com.nexus.financetracker.data.repository

import com.nexus.financetracker.data.local.dao.GoalDao
import com.nexus.financetracker.data.local.entity.GoalEntity
import com.nexus.financetracker.data.remote.GoalRemoteDataSource
import com.nexus.financetracker.domain.model.Goal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [GoalRepository].
 */
@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao,
    private val remoteDataSource: GoalRemoteDataSource
) : GoalRepository {

    override fun getGoals(userId: String): Flow<List<Goal>> =
        goalDao.getGoalsByUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addGoal(userId: String, goal: Goal): String {
        val id = remoteDataSource.addGoal(userId, goal)
        val withId = goal.copy(id = id)
        goalDao.insertGoal(GoalEntity.fromDomain(withId))
        return id
    }

    override suspend fun updateGoal(userId: String, goal: Goal) {
        remoteDataSource.updateGoal(userId, goal)
        goalDao.updateGoal(GoalEntity.fromDomain(goal))
    }

    override suspend fun deleteGoal(userId: String, goalId: String) {
        remoteDataSource.deleteGoal(userId, goalId)
        goalDao.getGoalById(goalId)?.let {
            goalDao.deleteGoal(it)
        }
    }
}
