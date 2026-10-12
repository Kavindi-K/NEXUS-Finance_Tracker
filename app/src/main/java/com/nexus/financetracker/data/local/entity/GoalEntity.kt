package com.nexus.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexus.financetracker.domain.model.Goal

/**
 * Room entity for caching goals locally.
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val targetAmount: Double,
    val savedAmount: Double,
    val deadline: Long,
    val note: String
) {
    fun toDomain(): Goal = Goal(
        id = id,
        userId = userId,
        title = title,
        targetAmount = targetAmount,
        savedAmount = savedAmount,
        deadline = deadline,
        note = note
    )

    companion object {
        fun fromDomain(g: Goal): GoalEntity = GoalEntity(
            id = g.id,
            userId = g.userId,
            title = g.title,
            targetAmount = g.targetAmount,
            savedAmount = g.savedAmount,
            deadline = g.deadline,
            note = g.note
        )
    }
}
