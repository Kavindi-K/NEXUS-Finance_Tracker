package com.nexus.financetracker.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexus.financetracker.data.local.dao.CategoryDao
import com.nexus.financetracker.data.local.dao.ExpenseDao
import com.nexus.financetracker.data.local.dao.GoalDao
import com.nexus.financetracker.data.local.dao.IncomeDao
import com.nexus.financetracker.data.local.dao.TransactionDao
import com.nexus.financetracker.data.local.entity.CategoryEntity
import com.nexus.financetracker.data.local.entity.ExpenseEntity
import com.nexus.financetracker.data.local.entity.GoalEntity
import com.nexus.financetracker.data.local.entity.IncomeEntity
import com.nexus.financetracker.data.local.entity.TransactionEntity

/**
 * Room database — single source of truth for local offline cache across all app modules:
 * Expenses, Income, Goals, Categories, and Transactions.
 */
@Database(
    entities = [
        TransactionEntity::class,
        GoalEntity::class,
        ExpenseEntity::class,
        IncomeEntity::class,
        CategoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun categoryDao(): CategoryDao
}
