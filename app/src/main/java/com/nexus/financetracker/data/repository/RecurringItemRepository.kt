package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.RecurringItem
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Repository interface for recurring items (recurringItems/{itemId}).
 */
interface RecurringItemRepository {
    fun getRecurringItems(userId: String): Flow<List<RecurringItem>>
    fun getDueRecurringItems(userId: String): Flow<List<RecurringItem>>
    suspend fun addRecurringItem(item: RecurringItem): String
    suspend fun updateRecurringItem(item: RecurringItem)
    suspend fun confirmPayment(itemId: String, expenseDate: Date)
}
