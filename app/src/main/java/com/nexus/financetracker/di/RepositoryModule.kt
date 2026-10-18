package com.nexus.financetracker.di

import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.GoalRepositoryImpl
import com.nexus.financetracker.data.repository.TransactionRepository
import com.nexus.financetracker.data.repository.TransactionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds repository interfaces to their implementations.
 * Using @Binds is more efficient than @Provides for interface binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(
        impl: GoalRepositoryImpl
    ): GoalRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: com.nexus.financetracker.data.repository.AuthRepositoryImpl
    ): com.nexus.financetracker.data.repository.AuthRepository

    @Binds
    @Singleton
    abstract fun bindIncomeRepository(
        impl: com.nexus.financetracker.data.repository.IncomeRepositoryImpl
    ): com.nexus.financetracker.data.repository.IncomeRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        impl: com.nexus.financetracker.data.repository.ExpenseRepositoryImpl
    ): com.nexus.financetracker.data.repository.ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: com.nexus.financetracker.data.repository.CategoryRepositoryImpl
    ): com.nexus.financetracker.data.repository.CategoryRepository

    @Binds
    @Singleton
    abstract fun bindRecurringItemRepository(
        impl: com.nexus.financetracker.data.repository.RecurringItemRepositoryImpl
    ): com.nexus.financetracker.data.repository.RecurringItemRepository
}
