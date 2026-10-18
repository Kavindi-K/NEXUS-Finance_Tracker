package com.nexus.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexus.financetracker.domain.model.Goal
import java.util.Date

/**
 * Room entity for caching goals locally.
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val targetAmountLKR: Double,
    val openingSavingsLKR: Double,
    val startDate: Long,
    val targetDate: Long,
    val isActive: Boolean,
    val createdAt: Long
) {
    fun toDomain(): Goal = Goal(
        id = id,
        userId = userId,
        title = title,
        targetAmountLKR = targetAmountLKR,
        openingSavingsLKR = openingSavingsLKR,
        startDate = Date(startDate),
        targetDate = Date(targetDate),
        isActive = isActive,
        createdAt = Date(createdAt)
    )

    companion object {
        fun fromDomain(g: Goal): GoalEntity = GoalEntity(
            id = g.id,
            userId = g.userId,
            title = g.title,
            targetAmountLKR = g.targetAmountLKR,
            openingSavingsLKR = g.openingSavingsLKR,
            startDate = g.startDate.time,
            targetDate = g.targetDate.time,
            isActive = g.isActive,
            createdAt = g.createdAt.time
        )
    }
}
