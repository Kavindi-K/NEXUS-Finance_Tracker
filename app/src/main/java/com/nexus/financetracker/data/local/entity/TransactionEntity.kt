package com.nexus.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexus.financetracker.domain.model.Transaction
import com.nexus.financetracker.domain.model.TransactionType

/**
 * Room entity for caching transactions locally.
 * Maps 1:1 to the Transaction domain model.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val amount: Double,
    val category: String,
    val type: String,           // stored as String name of TransactionType enum
    val date: Long,
    val note: String
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        userId = userId,
        title = title,
        amount = amount,
        category = category,
        type = TransactionType.valueOf(type),
        date = date,
        note = note
    )

    companion object {
        fun fromDomain(t: Transaction): TransactionEntity = TransactionEntity(
            id = t.id,
            userId = t.userId,
            title = t.title,
            amount = t.amount,
            category = t.category,
            type = t.type.name,
            date = t.date,
            note = t.note
        )
    }
}
