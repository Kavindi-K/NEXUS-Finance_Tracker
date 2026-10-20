package com.nexus.financetracker.data.repository

import com.nexus.financetracker.data.local.dao.GoalDao
import com.nexus.financetracker.data.local.entity.GoalEntity
import com.nexus.financetracker.data.remote.GoalRemoteDataSource
import com.nexus.financetracker.domain.model.Goal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [GoalRepository].
 * Real-time streams from Firestore with Room local cache sync for offline resilience.
 */
@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao,
    private val remoteDataSource: GoalRemoteDataSource
) : GoalRepository {

    override fun getGoals(userId: String): Flow<List<Goal>> =
        remoteDataSource.getGoals(userId)
            .onEach { remoteList ->
                try {
                    goalDao.insertAll(remoteList.map { GoalEntity.fromDomain(it) })
                } catch (_: Exception) {
                    // Cache write failure must not break repository emission
                }
            }
            .catch {
                // Fallback to local Room cache when remote query fails
                emitAll(goalDao.getGoalsByUser(userId).map { entities ->
                    entities.map { it.toDomain() }
                })
            }

    override suspend fun getGoalById(userId: String, goalId: String): Goal? {
        return try {
            goalDao.getGoalById(goalId)?.toDomain() ?: remoteDataSource.getGoalById(goalId)
        } catch (_: Exception) {
            remoteDataSource.getGoalById(goalId)
        }
    }

    override suspend fun addGoal(userId: String, goal: Goal): String {
        val id = remoteDataSource.addGoal(userId, goal)
        val withId = goal.copy(id = id, userId = userId)
        try {
            goalDao.insertGoal(GoalEntity.fromDomain(withId))
        } catch (_: Exception) {}
        return id
    }

    override suspend fun updateGoal(userId: String, goal: Goal) {
        remoteDataSource.updateGoal(userId, goal)
        try {
            goalDao.updateGoal(GoalEntity.fromDomain(goal))
        } catch (_: Exception) {}
    }

    override suspend fun deleteGoal(userId: String, goalId: String) {
        remoteDataSource.deleteGoal(userId, goalId)
        try {
            goalDao.getGoalById(goalId)?.let {
                goalDao.deleteGoal(it)
            }
        } catch (_: Exception) {}
    }
}
