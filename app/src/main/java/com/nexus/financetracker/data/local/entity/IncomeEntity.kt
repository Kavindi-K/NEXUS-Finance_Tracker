package com.nexus.financetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexus.financetracker.domain.model.Income
import java.util.Date

/**
 * Room entity for caching Income records locally for seamless offline read/write resilience.
 */
@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val source: String,
    val status: String,
    val amountOriginal: Double,
    val currency: String,
    val exchangeRate: Double,
    val amountLKR: Double,
    val dateReceived: Long?,
    val expectedDate: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long?
) {
    fun toDomain(): Income = Income(
        id = id,
        userId = userId,
        source = source,
        status = status,
        amountOriginal = amountOriginal,
        currency = currency,
        exchangeRate = exchangeRate,
        amountLKR = amountLKR,
        dateReceived = dateReceived?.let { Date(it) },
        expectedDate = expectedDate?.let { Date(it) },
        note = note,
        createdAt = Date(createdAt),
        updatedAt = updatedAt?.let { Date(it) }
    )

    companion object {
        fun fromDomain(income: Income): IncomeEntity = IncomeEntity(
            id = income.id,
            userId = income.userId,
            source = income.source,
            status = income.status,
            amountOriginal = income.amountOriginal,
            currency = income.currency,
            exchangeRate = income.exchangeRate,
            amountLKR = income.amountLKR,
            dateReceived = income.dateReceived?.time,
            expectedDate = income.expectedDate?.time,
            note = income.note,
            createdAt = income.createdAt.time,
            updatedAt = income.updatedAt?.time
        )
    }
}
