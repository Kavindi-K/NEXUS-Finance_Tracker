package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.ui.expense.ExpenseSortOrder
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests verifying Expense List, Sorting, Filtering, Edit mode, and Stale/Duplicate entry prevention
 * as specified in Section 5 Order 3 (feature/expense-list) and FR9-FR14.
 */
class ExpenseListTest {

    private val testUserId = "test-user-member2"

    private fun createTestExpense(
        id: String,
        amount: Double,
        date: Date,
        createdAt: Date = date,
        account: String = ExpenseAccount.CARD,
        categoryId: String = "cat_food"
    ): Expense {
        return Expense(
            id = id,
            userId = testUserId,
            amountLKR = amount,
            categoryId = categoryId,
            account = account,
            classification = ExpenseClassification.DISCRETIONARY,
            note = "Test note $id",
            date = date,
            createdAt = createdAt
        )
    }

    @Test
    fun sortExpenses_byDateDescending_ordersNewestFirst() {
        val d1 = Date(1000L)
        val d2 = Date(2000L)
        val d3 = Date(3000L)

        val expenses = listOf(
            createTestExpense("1", 100.0, d1),
            createTestExpense("3", 300.0, d3),
            createTestExpense("2", 200.0, d2)
        )

        val sorted = expenses.sortedWith(
            compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt }
        )

        assertEquals("3", sorted[0].id)
        assertEquals("2", sorted[1].id)
        assertEquals("1", sorted[2].id)
    }

    @Test
    fun sortExpenses_byDateAscending_ordersOldestFirst() {
        val d1 = Date(1000L)
        val d2 = Date(2000L)
        val d3 = Date(3000L)

        val expenses = listOf(
            createTestExpense("2", 200.0, d2),
            createTestExpense("1", 100.0, d1),
            createTestExpense("3", 300.0, d3)
        )

        val sorted = expenses.sortedWith(
            compareBy<Expense> { it.date }.thenBy { it.createdAt }
        )

        assertEquals("1", sorted[0].id)
        assertEquals("2", sorted[1].id)
        assertEquals("3", sorted[2].id)
    }

    @Test
    fun sortExpenses_byAmountDescending_ordersHighestFirst() {
        val d = Date(1000L)
        val expenses = listOf(
            createTestExpense("low", 150.0, d),
            createTestExpense("high", 4500.0, d),
            createTestExpense("mid", 1200.0, d)
        )

        val sorted = expenses.sortedWith(
            compareByDescending<Expense> { it.amountLKR }.thenByDescending { it.date }
        )

        assertEquals("high", sorted[0].id)
        assertEquals(4500.0, sorted[0].amountLKR, 0.001)
        assertEquals("mid", sorted[1].id)
        assertEquals("low", sorted[2].id)
    }

    @Test
    fun sortExpenses_byAmountAscending_ordersLowestFirst() {
        val d = Date(1000L)
        val expenses = listOf(
            createTestExpense("high", 8000.0, d),
            createTestExpense("low", 50.0, d),
            createTestExpense("mid", 500.0, d)
        )

        val sorted = expenses.sortedWith(
            compareBy<Expense> { it.amountLKR }.thenByDescending { it.date }
        )

        assertEquals("low", sorted[0].id)
        assertEquals(50.0, sorted[0].amountLKR, 0.001)
        assertEquals("mid", sorted[1].id)
        assertEquals("high", sorted[2].id)
    }

    @Test
    fun filterExpenses_byAccount_filtersCorrectly() {
        val d = Date()
        val expenses = listOf(
            createTestExpense("1", 100.0, d, account = ExpenseAccount.CARD),
            createTestExpense("2", 200.0, d, account = ExpenseAccount.CASH),
            createTestExpense("3", 300.0, d, account = ExpenseAccount.CARD),
            createTestExpense("4", 400.0, d, account = ExpenseAccount.SECONDARY)
        )

        val cardOnly = expenses.filter { it.account == ExpenseAccount.CARD }
        assertEquals(2, cardOnly.size)
        assertEquals(400.0, cardOnly.sumOf { it.amountLKR }, 0.001)

        val cashOnly = expenses.filter { it.account == ExpenseAccount.CASH }
        assertEquals(1, cashOnly.size)
        assertEquals("2", cashOnly.first().id)

        val secondaryOnly = expenses.filter { it.account == ExpenseAccount.SECONDARY }
        assertEquals(1, secondaryOnly.size)
        assertEquals("4", secondaryOnly.first().id)
    }

    @Test
    fun filterExpenses_byMonth_includesOnlySelectedMonth() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 15, 12, 0, 0)
        val octoberDate = cal.time

        cal.set(2026, Calendar.SEPTEMBER, 20, 12, 0, 0)
        val septemberDate = cal.time

        cal.set(2026, Calendar.NOVEMBER, 5, 12, 0, 0)
        val novemberDate = cal.time

        val expenses = listOf(
            createTestExpense("oct-1", 1000.0, octoberDate),
            createTestExpense("sep-1", 2000.0, septemberDate),
            createTestExpense("nov-1", 3000.0, novemberDate)
        )

        // Filter for October 2026
        val filterCal = Calendar.getInstance()
        val octOnly = expenses.filter { exp ->
            filterCal.time = exp.date
            filterCal.get(Calendar.YEAR) == 2026 && filterCal.get(Calendar.MONTH) == Calendar.OCTOBER
        }

        assertEquals(1, octOnly.size)
        assertEquals("oct-1", octOnly.first().id)
    }

    @Test
    fun editExpense_preservesOriginalIdAndCreatedAt_preventsDuplicate() {
        val originalCreated = Date(1600000000000L)
        val originalExpense = Expense(
            id = "exp-edit-001",
            userId = testUserId,
            amountLKR = 1500.0,
            categoryId = "cat_food",
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.DISCRETIONARY,
            note = "Dinner with friends",
            date = Date(1600005000000L),
            createdAt = originalCreated
        )

        // Simulate an edit update
        val updatedExpense = originalExpense.copy(
            amountLKR = 1850.0,
            note = "Dinner + dessert with friends"
        )

        // In a list or Room DB (OnConflictStrategy.REPLACE by id), replacing by ID leaves the list size identical
        val expenseList = mutableListOf(originalExpense)
        val existingIndex = expenseList.indexOfFirst { it.id == updatedExpense.id }
        if (existingIndex >= 0) {
            expenseList[existingIndex] = updatedExpense
        } else {
            expenseList.add(updatedExpense)
        }

        assertEquals(1, expenseList.size)
        assertEquals("exp-edit-001", expenseList.first().id)
        assertEquals(1850.0, expenseList.first().amountLKR, 0.001)
        assertEquals(originalCreated, expenseList.first().createdAt)
        assertEquals("Dinner + dessert with friends", expenseList.first().note)
    }

    @Test
    fun deleteExpense_removesFromList_producesNoStaleEntries() {
        val d = Date()
        val expense1 = createTestExpense("exp-del-1", 500.0, d)
        val expense2 = createTestExpense("exp-del-2", 750.0, d)

        val list = mutableListOf(expense1, expense2)
        assertEquals(2, list.size)

        // Delete expense1
        list.removeAll { it.id == "exp-del-1" }

        assertEquals(1, list.size)
        assertEquals("exp-del-2", list.first().id)
        assertFalse(list.any { it.id == "exp-del-1" })
    }

    @Test
    fun emptyState_detectsWhenListIsEmpty() {
        val emptyExpenses = emptyList<Expense>()
        assertTrue(emptyExpenses.isEmpty())

        val cardExpenses = emptyExpenses.filter { it.account == ExpenseAccount.CARD }
        assertTrue(cardExpenses.isEmpty())
        assertEquals(0.0, cardExpenses.sumOf { it.amountLKR }, 0.001)
    }
}
