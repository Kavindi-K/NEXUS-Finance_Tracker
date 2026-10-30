package com.nexus.financetracker.data.repository

import com.nexus.financetracker.data.local.dao.GoalDao
import com.nexus.financetracker.data.local.entity.GoalEntity
import com.nexus.financetracker.data.remote.GoalRemoteDataSource
import com.nexus.financetracker.domain.model.Goal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [GoalRepository] with Room local read cache and queued offline writes.
 */
@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao,
    private val remoteDataSource: GoalRemoteDataSource
) : GoalRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override fun getGoals(userId: String): Flow<List<Goal>> = callbackFlow {
        // 1. Room acts as primary reactive read cache
        val roomJob = repositoryScope.launch {
            goalDao.getGoalsByUser(userId).collect { entities ->
                trySend(entities.map { it.toDomain() })
            }
        }

        // 2. Sync from remote Firestore data source into Room
        val remoteJob = repositoryScope.launch {
            try {
                remoteDataSource.getGoals(userId).collect { remoteList ->
                    try {
                        goalDao.insertAll(remoteList.map { GoalEntity.fromDomain(it) })
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        awaitClose {
            roomJob.cancel()
            remoteJob.cancel()
        }
    }

    override suspend fun getGoalById(userId: String, goalId: String): Goal? {
        return try {
            goalDao.getGoalById(goalId)?.toDomain() ?: remoteDataSource.getGoalById(goalId)
        } catch (_: Exception) {
            remoteDataSource.getGoalById(goalId)
        }
    }

    override suspend fun addGoal(userId: String, goal: Goal): String {
        val assignedId = if (goal.id.isNotBlank()) goal.id else UUID.randomUUID().toString()
        val withId = goal.copy(id = assignedId, userId = userId)

        // 1. Write to Room local cache first
        try {
            goalDao.insertGoal(GoalEntity.fromDomain(withId))
        } catch (_: Exception) {}

        // 2. Queue write to Firestore
        try {
            remoteDataSource.addGoal(userId, withId)
        } catch (_: Exception) {}

        return assignedId
    }

    override suspend fun updateGoal(userId: String, goal: Goal) {
        // 1. Update Room local cache first
        try {
            goalDao.insertGoal(GoalEntity.fromDomain(goal))
        } catch (_: Exception) {}

        // 2. Queue in Firestore
        try {
            remoteDataSource.updateGoal(userId, goal)
        } catch (_: Exception) {}
    }

    override suspend fun deleteGoal(userId: String, goalId: String) {
        // 1. Delete from Room first
        try {
            goalDao.deleteGoalById(goalId)
        } catch (_: Exception) {}

        // 2. Queue in Firestore
        try {
            remoteDataSource.deleteGoal(userId, goalId)
        } catch (_: Exception) {}
    }

    override suspend fun clearLocalCache(userId: String) {
        try {
            goalDao.deleteAllForUser(userId)
        } catch (_: Exception) {}
    }
}
