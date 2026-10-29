package com.nexus.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.ExpenseClassification
import java.util.Date

/**
 * Room entity for caching user custom categories locally for offline resilience.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val classification: String = ExpenseClassification.DISCRETIONARY,
    val estimatedAmount: Double?,
    val recurringFrequency: String?,
    val mergingInto: String?,
    val createdAt: Long
) {
    fun toDomain(): Category = Category(
        id = id,
        userId = userId,
        name = name,
        classification = classification,
        estimatedAmount = estimatedAmount,
        recurringFrequency = recurringFrequency,
        mergingInto = mergingInto,
        createdAt = Date(createdAt)
    )

    companion object {
        fun fromDomain(cat: Category): CategoryEntity = CategoryEntity(
            id = cat.id,
            userId = cat.userId,
            name = cat.name,
            classification = cat.classification,
            estimatedAmount = cat.estimatedAmount,
            recurringFrequency = cat.recurringFrequency,
            mergingInto = cat.mergingInto,
            createdAt = cat.createdAt.time
        )
    }
}
