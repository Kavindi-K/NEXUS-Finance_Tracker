package com.nexus.financetracker.di

import android.content.Context
import androidx.room.Room
import com.nexus.financetracker.data.local.dao.CategoryDao
import com.nexus.financetracker.data.local.dao.ExpenseDao
import com.nexus.financetracker.data.local.dao.GoalDao
import com.nexus.financetracker.data.local.dao.IncomeDao
import com.nexus.financetracker.data.local.dao.TransactionDao
import com.nexus.financetracker.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides Room database and DAO instances across all modules for full offline support.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "nexus_finance_db"
        )
        // Destructive migration for schema upgrades during development
        .fallbackToDestructiveMigration(true)
        .allowMainThreadQueries()
        .build()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideIncomeDao(db: AppDatabase): IncomeDao = db.incomeDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
}
