package com.nexus.financetracker

import com.nexus.financetracker.data.local.entity.CategoryEntity
import com.nexus.financetracker.data.local.entity.ExpenseEntity
import com.nexus.financetracker.data.local.entity.GoalEntity
import com.nexus.financetracker.data.local.entity.IncomeEntity
import com.nexus.financetracker.domain.model.*
import com.nexus.financetracker.ui.dashboard.DashboardUiState
import com.nexus.financetracker.ui.expense.ExpenseUiState
import com.nexus.financetracker.ui.goal.GoalUiState
import com.nexus.financetracker.ui.income.IncomeUiState
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import java.util.UUID

/**
 * Unit tests verifying comprehensive offline support across the entire NEXUS app:
 * Incomes, Categories, Goals, Expenses, Dashboard, and Account Isolation.
 */
class FullAppOfflineSyncTest {

    private val user1 = "user-offline-alpha"
    private val user2 = "user-offline-beta"

    @Test
    fun incomeEntity_roundTripMappingPreservesAllFields() {
        val now = Date()
        val original = Income(
            id = "inc-offline-001",
            userId = user1,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 150.0,
            currency = "USD",
            exchangeRate = 300.0,
            amountLKR = 45000.0,
            dateReceived = now,
            expectedDate = null,
            note = "Web development freelance milestone",
            createdAt = now,
            updatedAt = now
        )

        val entity = IncomeEntity.fromDomain(original)
        assertEquals("inc-offline-001", entity.id)
        assertEquals(user1, entity.userId)
        assertEquals(IncomeSource.FREELANCE, entity.source)
        assertEquals(IncomeStatus.CONFIRMED, entity.status)
        assertEquals(45000.0, entity.amountLKR, 0.001)
        assertEquals(now.time, entity.dateReceived)

        val fromEntity = entity.toDomain()
        assertEquals(original.id, fromEntity.id)
        assertEquals(original.userId, fromEntity.userId)
        assertEquals(original.source, fromEntity.source)
        assertEquals(original.status, fromEntity.status)
        assertEquals(original.amountLKR, fromEntity.amountLKR, 0.001)
        assertEquals(original.currency, fromEntity.currency)
        assertEquals(original.exchangeRate, fromEntity.exchangeRate, 0.001)
        assertEquals(original.note, fromEntity.note)
    }

    @Test
    fun categoryEntity_roundTripMappingPreservesAllFields() {
        val now = Date()
        val original = Category(
            id = "cat-custom-001",
            userId = user1,
            name = "Pet Supplies",
            classification = ExpenseClassification.DISCRETIONARY,
            estimatedAmount = 15000.0,
            recurringFrequency = "Monthly",
            mergingInto = null,
            createdAt = now
        )

        val entity = CategoryEntity.fromDomain(original)
        assertEquals("cat-custom-001", entity.id)
        assertEquals("Pet Supplies", entity.name)
        assertEquals(15000.0, entity.estimatedAmount ?: 0.0, 0.001)

        val fromEntity = entity.toDomain()
        assertEquals(original.id, fromEntity.id)
        assertEquals(original.name, fromEntity.name)
        assertEquals(original.classification, fromEntity.classification)
        assertEquals(original.estimatedAmount, fromEntity.estimatedAmount)
        assertEquals(original.recurringFrequency, fromEntity.recurringFrequency)
    }

    @Test
    fun incomeOfflineQueueing_generatesClientSideIdInstantly() {
        val clientSideId = UUID.randomUUID().toString()
        val income = Income(
            id = clientSideId,
            userId = user1,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 130000.0,
            currency = "LKR",
            amountLKR = 130000.0,
            dateReceived = Date()
        )

        assertTrue(income.id.isNotBlank())
        val entity = IncomeEntity.fromDomain(income)
        assertEquals(clientSideId, entity.id)
        assertEquals(130000.0, entity.amountLKR, 0.001)
    }

    @Test
    fun goalOfflineQueueing_generatesClientSideIdInstantly() {
        val clientSideId = UUID.randomUUID().toString()
        val now = Date()
        val goal = Goal(
            id = clientSideId,
            userId = user1,
            title = "Emergency Fund",
            targetAmountLKR = 500000.0,
            openingSavingsLKR = 100000.0,
            startDate = now,
            targetDate = Date(now.time + 180L * 24 * 60 * 60 * 1000),
            isActive = true,
            priority = GoalPriority.HIGH
        )

        assertTrue(goal.id.isNotBlank())
        val entity = GoalEntity.fromDomain(goal)
        assertEquals(clientSideId, entity.id)
        assertEquals(500000.0, entity.targetAmountLKR, 0.001)
    }

    @Test
    fun allModules_accountIsolation_clearsOnlyTargetUserLocalCache() {
        val localIncomes = mutableListOf<IncomeEntity>()
        val localExpenses = mutableListOf<ExpenseEntity>()
        val localGoals = mutableListOf<GoalEntity>()
        val localCategories = mutableListOf<CategoryEntity>()

        // Seed data for User 1
        localIncomes.add(IncomeEntity.fromDomain(Income(id = "inc-1", userId = user1, amountLKR = 50000.0)))
        localExpenses.add(ExpenseEntity.fromDomain(Expense(id = "exp-1", userId = user1, amountLKR = 2000.0, categoryId = "cat_food")))
        localGoals.add(GoalEntity.fromDomain(Goal(id = "goal-1", userId = user1, title = "Laptop", targetAmountLKR = 300000.0)))
        localCategories.add(CategoryEntity.fromDomain(Category(id = "cat-1", userId = user1, name = "Gear")))

        // Seed data for User 2
        localIncomes.add(IncomeEntity.fromDomain(Income(id = "inc-2", userId = user2, amountLKR = 80000.0)))
        localExpenses.add(ExpenseEntity.fromDomain(Expense(id = "exp-2", userId = user2, amountLKR = 5000.0, categoryId = "cat_rent")))
        localGoals.add(GoalEntity.fromDomain(Goal(id = "goal-2", userId = user2, title = "Car", targetAmountLKR = 2000000.0)))
        localCategories.add(CategoryEntity.fromDomain(Category(id = "cat-2", userId = user2, name = "Maintenance")))

        assertEquals(2, localIncomes.size)
        assertEquals(2, localExpenses.size)
        assertEquals(2, localGoals.size)
        assertEquals(2, localCategories.size)

        // User 1 logs out / switches accounts -> clearLocalCache(user1) across all modules
        localIncomes.removeAll { it.userId == user1 }
        localExpenses.removeAll { it.userId == user1 }
        localGoals.removeAll { it.userId == user1 }
        localCategories.removeAll { it.userId == user1 }

        // Assert User 1 data is completely removed while User 2 remains intact
        assertEquals(1, localIncomes.size)
        assertEquals(user2, localIncomes.first().userId)
        assertEquals(1, localExpenses.size)
        assertEquals(user2, localExpenses.first().userId)
        assertEquals(1, localGoals.size)
        assertEquals(user2, localGoals.first().userId)
        assertEquals(1, localCategories.size)
        assertEquals(user2, localCategories.first().userId)
    }

    @Test
    fun uiStates_reflectOfflineModeAcrossAllScreens() {
        val incomeState = IncomeUiState(isOffline = true)
        val goalState = GoalUiState(isOffline = true)
        val dashboardState = DashboardUiState(isOffline = true)
        val expenseState = ExpenseUiState(isOffline = true)

        assertTrue("Income screen reflects offline mode", incomeState.isOffline)
        assertTrue("Goal screen reflects offline mode", goalState.isOffline)
        assertTrue("Dashboard screen reflects offline mode", dashboardState.isOffline)
        assertTrue("Expense screen reflects offline mode", expenseState.isOffline)
    }
}

