package com.nexus.financetracker.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexus.financetracker.data.local.dao.GoalDao
import com.nexus.financetracker.data.local.dao.TransactionDao
import com.nexus.financetracker.data.local.entity.GoalEntity
import com.nexus.financetracker.data.local.entity.TransactionEntity

/**
 * Room database — single source of truth for local offline cache.
 * Increment [version] whenever schema changes. Provide a migration or use
 * fallbackToDestructiveMigration() during development only.
 */
@Database(
    entities = [
        TransactionEntity::class,
        GoalEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao
}
