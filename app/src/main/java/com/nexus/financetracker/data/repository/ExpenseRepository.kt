package com.nexus.financetracker.data.repository

import com.nexus.financetracker.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Repository interface for expenses (expenses/{expenseId}).
 * Coordinated with Member 2 (Expenses & Offline).
 */

interface ExpenseRepository {
    fun getExpenses(userId: String): Flow<List<Expense>>
    fun getExpensesByDateRange(userId: String, startDate: Date, endDate: Date): Flow<List<Expense>>
    suspend fun getExpenseById(expenseId: String): Expense?
    suspend fun getLatestExpense(userId: String): Expense?
    suspend fun addExpense(expense: Expense): String
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(expenseId: String)
}

