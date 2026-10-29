package com.nexus.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import java.util.Date

/**
 * Room entity for caching expenses locally.
 * Conforms to FR9-FR14 and Member 2 Order 2 (feature/offline-cache).
 * Allows offline read access to recorded expenses.
 */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val amountLKR: Double,
    val categoryId: String,
    val account: String = ExpenseAccount.CARD,
    val classification: String = ExpenseClassification.DISCRETIONARY,
    val note: String? = null,
    val date: Long,
    val recurringItemId: String? = null,
    val createdAt: Long
) {
    fun toDomain(): Expense = Expense(
        id = id,
        userId = userId,
        amountLKR = amountLKR,
        categoryId = categoryId,
        account = account,
        classification = classification,
        note = note,
        date = Date(date),
        recurringItemId = recurringItemId,
        createdAt = Date(createdAt)
    )

    companion object {
        fun fromDomain(e: Expense): ExpenseEntity = ExpenseEntity(
            id = e.id,
            userId = e.userId,
            amountLKR = e.amountLKR,
            categoryId = e.categoryId,
            account = e.account,
            classification = e.classification,
            note = e.note,
            date = e.date.time,
            recurringItemId = e.recurringItemId,
            createdAt = e.createdAt.time
        )
    }
}
