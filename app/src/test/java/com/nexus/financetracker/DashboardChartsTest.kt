package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.ui.dashboard.DashboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests verifying Dashboard Chart visualizations (FR18, FR19, FR22).
 *
 * Implements Acceptance Criteria for Member 3 - Order 8 (feature/dashboard-charts):
 * - Category spending breakdown resolves category IDs to human-readable names (FR19)
 * - Ranks categories in descending order to expose financial leaks (e.g. coffee/dining shock)
 * - Computes proportional fractions (top category = 1.0f) and total percentages
 * - Computes Committed vs Discretionary spending ratios (FR18)
 * - Strictly excludes transactions outside the selected calendar month
 * - Safely handles empty datasets without divide-by-zero or crashes (FR22)
 */
class DashboardChartsTest {

    @Test
    fun categorySpending_resolvesIdsAndRanksDescending() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 15) }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)
        val testDate = Date(startOfMonth.time + 3L * 86400000L)

        val categories = listOf(
            Category(id = "cat-rent", userId = "user-1", name = "Rent"),
            Category(id = "cat-food", userId = "user-1", name = "Food and dining"),
            Category(id = "cat-transport", userId = "user-1", name = "Transport")
        )

        val expenses = listOf(
            // Food: 24,000 LKR
            Expense(id = "e1", userId = "user-1", amountLKR = 24000.0, categoryId = "cat-food", date = testDate),
            // Rent: 36,500 LKR (Biggest leak/commitment)
            Expense(id = "e2", userId = "user-1", amountLKR = 36500.0, categoryId = "cat-rent", date = testDate),
            // Transport: 11,800 LKR
            Expense(id = "e3", userId = "user-1", amountLKR = 11800.0, categoryId = "cat-transport", date = testDate)
        )

        val chartItems = DashboardViewModel.calculateCategorySpending(
            expenses = expenses,
            categories = categories,
            startOfMonth = startOfMonth,
            endOfMonth = endOfMonth
        )

        assertEquals(3, chartItems.size)

        // Rank 1: Rent (36,500)
        assertEquals("Rent", chartItems[0].name)
        assertEquals(36500.0, chartItems[0].amountLKR, 0.01)
        assertEquals(1.0f, chartItems[0].fraction, 0.01f) // Max item has fraction 1.0f

        // Rank 2: Food and dining (24,000)
        assertEquals("Food and dining", chartItems[1].name)
        assertEquals(24000.0, chartItems[1].amountLKR, 0.01)
        assertTrue(chartItems[1].fraction < 1.0f)
        assertEquals((24000.0 / 36500.0).toFloat(), chartItems[1].fraction, 0.02f)

        // Rank 3: Transport (11,800)
        assertEquals("Transport", chartItems[2].name)
        assertEquals(11800.0, chartItems[2].amountLKR, 0.01)
        assertTrue(chartItems[2].fraction < chartItems[1].fraction)
    }

    @Test
    fun categorySpending_filtersOutTransactionsOutsideCurrentMonth() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 15) }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)

        val insideDate = Date(startOfMonth.time + 4L * 86400000L)
        val outsideDate = Date(startOfMonth.time - 5L * 86400000L)

        val categories = listOf(Category(id = "cat-food", name = "Food"))

        val expenses = listOf(
            Expense(id = "e-in", amountLKR = 5000.0, categoryId = "cat-food", date = insideDate),
            Expense(id = "e-out", amountLKR = 15000.0, categoryId = "cat-food", date = outsideDate)
        )

        val result = DashboardViewModel.calculateCategorySpending(
            expenses = expenses,
            categories = categories,
            startOfMonth = startOfMonth,
            endOfMonth = endOfMonth
        )

        assertEquals(1, result.size)
        assertEquals(5000.0, result[0].amountLKR, 0.01)
    }

    @Test
    fun categorySpending_handlesEmptyExpensesWithoutCrashing() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1) }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)

        val result = DashboardViewModel.calculateCategorySpending(
            expenses = emptyList(),
            categories = emptyList(),
            startOfMonth = startOfMonth,
            endOfMonth = endOfMonth
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun categorySpending_fallsBackWhenCategoryIdUnmapped() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1) }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)

        val expenses = listOf(
            Expense(id = "e1", amountLKR = 4500.0, categoryId = "groceries", date = startOfMonth)
        )

        val result = DashboardViewModel.calculateCategorySpending(
            expenses = expenses,
            categories = emptyList(), // No mapped categories
            startOfMonth = startOfMonth,
            endOfMonth = endOfMonth
        )

        assertEquals(1, result.size)
        assertEquals("Groceries", result[0].name)
        assertEquals(4500.0, result[0].amountLKR, 0.01)
    }

    @Test
    fun committedDiscretionarySplit_calculatesRatiosAccurately() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 10) }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)
        val date = Date(startOfMonth.time + 86400000L)

        // Total: 100,000 LKR
        // Committed (Rent): 37,000 LKR (37%)
        // Discretionary (Dining, Shopping): 63,000 LKR (63%)
        val expenses = listOf(
            Expense(amountLKR = 37000.0, classification = ExpenseClassification.COMMITTED, date = date),
            Expense(amountLKR = 40000.0, classification = ExpenseClassification.DISCRETIONARY, date = date),
            Expense(amountLKR = 23000.0, classification = ExpenseClassification.DISCRETIONARY, date = date)
        )

        val (committedPct, discretionaryPct) = DashboardViewModel.calculateCommittedDiscretionarySplit(
            expenses = expenses,
            startOfMonth = startOfMonth,
            endOfMonth = endOfMonth
        )

        assertEquals(37, committedPct)
        assertEquals(63, discretionaryPct)
    }

    @Test
    fun committedDiscretionarySplit_handlesZeroExpensesSafely() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1) }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)

        val (committedPct, discretionaryPct) = DashboardViewModel.calculateCommittedDiscretionarySplit(
            expenses = emptyList(),
            startOfMonth = startOfMonth,
            endOfMonth = endOfMonth
        )

        assertEquals(0, committedPct)
        assertEquals(0, discretionaryPct)
    }

    @Test
    fun categorySpending_retainsAllCategoriesForScrollDown() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 5) }
        val (startOfMonth, endOfMonth) = DashboardViewModel.getMonthRange(cal)
        val date = Date(startOfMonth.time + 86400000L)

        val categories = listOf(
            Category(id = "c1", name = "Rent"),
            Category(id = "c2", name = "Food and dining"),
            Category(id = "c3", name = "Transport"),
            Category(id = "c4", name = "Subscriptions"),
            Category(id = "c5", name = "Utilities"),
            Category(id = "c6", name = "Shopping"),
            Category(id = "c7", name = "Healthcare"),
            Category(id = "c8", name = "Entertainment")
        )

        val expenses = categories.mapIndexed { index, cat ->
            Expense(
                id = "exp-$index",
                amountLKR = (10 - index) * 1000.0,
                categoryId = cat.id,
                date = date
            )
        }

        val result = DashboardViewModel.calculateCategorySpending(
            expenses = expenses,
            categories = categories,
            startOfMonth = startOfMonth,
            endOfMonth = endOfMonth
        )

        // All 8 categories should be preserved so user sees them on scroll down
        assertEquals(8, result.size)
        assertEquals("Rent", result[0].name)
        assertEquals("Entertainment", result[7].name)
    }

    @Test
    fun incomeSources_groupsAllFourStreamsAndRanksDescending() {
        val date = Date()
        val incomes = listOf(
            com.nexus.financetracker.domain.model.Income(
                id = "inc-1",
                source = com.nexus.financetracker.domain.model.IncomeSource.SALARY,
                amountLKR = 140000.0,
                status = com.nexus.financetracker.domain.model.IncomeStatus.CONFIRMED,
                dateReceived = date
            ),
            com.nexus.financetracker.domain.model.Income(
                id = "inc-2",
                source = com.nexus.financetracker.domain.model.IncomeSource.FREELANCE,
                amountLKR = 35000.0,
                status = com.nexus.financetracker.domain.model.IncomeStatus.CONFIRMED,
                dateReceived = date
            ),
            com.nexus.financetracker.domain.model.Income(
                id = "inc-3",
                source = com.nexus.financetracker.domain.model.IncomeSource.ADSENSE,
                amountLKR = 14400.0,
                status = com.nexus.financetracker.domain.model.IncomeStatus.CONFIRMED,
                dateReceived = date
            )
        )

        val sources = DashboardViewModel.calculateIncomeSources(incomes)

        assertEquals(3, sources.size)
        assertEquals("Salary", sources[0].source)
        assertEquals(140000.0, sources[0].amountLKR, 0.01)
        assertEquals(1.0f, sources[0].fraction, 0.01f)

        assertEquals("Freelance", sources[1].source)
        assertEquals(35000.0, sources[1].amountLKR, 0.01)

        assertEquals("AdSense", sources[2].source)
        assertEquals(14400.0, sources[2].amountLKR, 0.01)
    }

    @Test
    fun incomeSources_handlesEmptyIncomesSafely() {
        val result = DashboardViewModel.calculateIncomeSources(emptyList())
        assertTrue(result.isEmpty())
    }
}
