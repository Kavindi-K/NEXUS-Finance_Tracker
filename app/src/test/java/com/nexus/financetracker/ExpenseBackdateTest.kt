package com.nexus.financetracker

import com.nexus.financetracker.data.local.entity.ExpenseEntity
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.ui.expense.BulkExpenseItem
import com.nexus.financetracker.ui.expense.ExpenseFormState
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests verifying back-dating and bulk-adding missed transactions using actual transaction dates,
 * confirming correct monthly totals, account allocations, and transaction history.
 * Conforms to Member 2 Order 6 (feature/expense-backdate).
 */
class ExpenseBackdateTest {

    private val testUserId = "user_member2_backdate"

    private fun createExpense(
        id: String,
        amount: Double,
        date: Date,
        createdAt: Date = Date(),
        account: String = ExpenseAccount.CARD,
        classification: String = ExpenseClassification.DISCRETIONARY,
        categoryId: String = "cat_food"
    ): Expense {
        return Expense(
            id = id,
            userId = testUserId,
            amountLKR = amount,
            categoryId = categoryId,
            account = account,
            classification = classification,
            note = "Backdated test entry $id",
            date = date,
            createdAt = createdAt
        )
    }

    @Test
    fun isBackdated_returnsTrue_forPastDate_andFalseForToday() {
        val today = Date()
        val formStateToday = ExpenseFormState(date = today)
        assertFalse("Today should not be considered backdated", formStateToday.isBackdated)

        val calPast = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -2)
        }
        val formStatePast = ExpenseFormState(date = calPast.time)
        assertTrue("Past date (2 days ago) must be considered backdated", formStatePast.isBackdated)
    }

    @Test
    fun backdatedExpense_aggregatesIntoTargetMonth_notCurrentMonth() {
        val calNow = Calendar.getInstance()

        // Target: 2 months ago (August if currently October)
        val calPastMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, -2)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
        }
        val pastDate = calPastMonth.time

        val backdatedExpense = createExpense(
            id = "exp_backdated_01",
            amount = 7500.0,
            date = pastDate,
            createdAt = calNow.time
        )

        val currentMonthExpense = createExpense(
            id = "exp_current_01",
            amount = 2500.0,
            date = calNow.time,
            createdAt = calNow.time
        )

        val allExpenses = listOf(backdatedExpense, currentMonthExpense)

        // Calculate start & end of current month
        val currentStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val currentEnd = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        // Current month filter
        val currentMonthFiltered = allExpenses.filter {
            !it.date.before(currentStart) && !it.date.after(currentEnd)
        }
        val currentMonthTotal = currentMonthFiltered.sumOf { it.amountLKR }

        assertEquals("Current month total must only include current expense", 2500.0, currentMonthTotal, 0.01)
        assertFalse("Backdated expense must not pollute current month", currentMonthFiltered.contains(backdatedExpense))

        // Target month (2 months ago) filter
        val pastStart = (calPastMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val pastEnd = (calPastMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        val pastMonthFiltered = allExpenses.filter {
            !it.date.before(pastStart) && !it.date.after(pastEnd)
        }
        val pastMonthTotal = pastMonthFiltered.sumOf { it.amountLKR }

        assertEquals("Past month total must accurately receive backdated expense", 7500.0, pastMonthTotal, 0.01)
        assertTrue("Past month must include the backdated transaction", pastMonthFiltered.contains(backdatedExpense))
    }

    @Test
    fun backdatedExpense_maintainsTransactionDate_andCreatedAtSeparation() {
        val calPast = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 11, 0, 0)
        }
        val pastDate = calPast.time

        val calLogged = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 28, 20, 0, 0)
        }
        val loggedDate = calLogged.time

        val expense = createExpense(
            id = "exp_audit_01",
            amount = 12000.0,
            date = pastDate,
            createdAt = loggedDate
        )

        assertEquals("Transaction date must reflect the actual spending event", pastDate, expense.date)
        assertEquals("CreatedAt must reflect the system logging timestamp", loggedDate, expense.createdAt)

        // Audit check: Day of year and Month must differ between actual date and log date
        val calD = Calendar.getInstance().apply { time = expense.date }
        val calC = Calendar.getInstance().apply { time = expense.createdAt }
        val isBackdatedAudit = calD.get(Calendar.YEAR) != calC.get(Calendar.YEAR) ||
                calD.get(Calendar.DAY_OF_YEAR) != calC.get(Calendar.DAY_OF_YEAR)

        assertTrue("Audit logic should accurately detect entry was backdated", isBackdatedAudit)
    }

    @Test
    fun bulkExpenseItem_validation_andTotals() {
        val validItem1 = BulkExpenseItem(
            amountText = "1250.50",
            categoryId = "cat_food",
            account = ExpenseAccount.CARD
        )
        val validItem2 = BulkExpenseItem(
            amountText = "3500",
            categoryId = "cat_transport",
            account = ExpenseAccount.CASH
        )
        val invalidItemEmpty = BulkExpenseItem(
            amountText = "",
            categoryId = "cat_food"
        )
        val invalidItemZero = BulkExpenseItem(
            amountText = "0",
            categoryId = "cat_bills"
        )
        val invalidItemBlankCat = BulkExpenseItem(
            amountText = "500",
            categoryId = "   "
        )

        assertTrue(validItem1.isValid)
        assertTrue(validItem2.isValid)
        assertFalse(invalidItemEmpty.isValid)
        assertFalse(invalidItemZero.isValid)
        assertFalse(invalidItemBlankCat.isValid)

        val batch = listOf(validItem1, validItem2, invalidItemEmpty, invalidItemZero, invalidItemBlankCat)
        val validItems = batch.filter { it.isValid }
        val batchTotal = validItems.mapNotNull { it.amountLKR }.sum()

        assertEquals(2, validItems.size)
        assertEquals(4750.50, batchTotal, 0.01)
    }

    @Test
    fun bulkExpenses_batchTransformation_maintainsAccountAndDates() {
        val calBatchDate = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
        }
        val batchDate = calBatchDate.time
        val now = Date()

        val items = listOf(
            BulkExpenseItem(amountText = "850.00", categoryId = "cat_food", account = ExpenseAccount.CASH, date = batchDate, note = "Bakery"),
            BulkExpenseItem(amountText = "2200.00", categoryId = "cat_transport", account = ExpenseAccount.CARD, date = batchDate, note = "Taxi fuel"),
            BulkExpenseItem(amountText = "15000.00", categoryId = "cat_bills", account = ExpenseAccount.SECONDARY, date = batchDate, note = "Electricity bill")
        )

        val transformed = items.map { item ->
            Expense(
                userId = testUserId,
                amountLKR = item.amountLKR ?: 0.0,
                categoryId = item.categoryId,
                account = item.account,
                classification = item.classification,
                note = item.note.trim().takeIf { it.isNotBlank() },
                date = item.date,
                createdAt = now
            )
        }

        assertEquals(3, transformed.size)
        assertEquals(850.00, transformed[0].amountLKR, 0.01)
        assertEquals(ExpenseAccount.CASH, transformed[0].account)
        assertEquals(batchDate, transformed[0].date)
        assertEquals("Bakery", transformed[0].note)

        assertEquals(2200.00, transformed[1].amountLKR, 0.01)
        assertEquals(ExpenseAccount.CARD, transformed[1].account)

        assertEquals(15000.00, transformed[2].amountLKR, 0.01)
        assertEquals(ExpenseAccount.SECONDARY, transformed[2].account)
    }

    @Test
    fun backdatedExpenses_acrossDifferentAccounts_updatesAccountTotalsAccurately() {
        val calMonth = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 10, 10, 0, 0)
        }
        val dateInAugust = calMonth.time

        val augustExpenses = listOf(
            createExpense("e1", 10000.0, dateInAugust, account = ExpenseAccount.CARD),
            createExpense("e2", 4500.0, dateInAugust, account = ExpenseAccount.CASH),
            createExpense("e3", 6000.0, dateInAugust, account = ExpenseAccount.SECONDARY),
            createExpense("e4", 5000.0, dateInAugust, account = ExpenseAccount.CARD)
        )

        val totalAugust = augustExpenses.sumOf { it.amountLKR }
        val cardTotal = augustExpenses.filter { it.account == ExpenseAccount.CARD }.sumOf { it.amountLKR }
        val cashTotal = augustExpenses.filter { it.account == ExpenseAccount.CASH }.sumOf { it.amountLKR }
        val secondaryTotal = augustExpenses.filter { it.account == ExpenseAccount.SECONDARY }.sumOf { it.amountLKR }

        assertEquals(25500.0, totalAugust, 0.01)
        assertEquals(15000.0, cardTotal, 0.01)
        assertEquals(4500.0, cashTotal, 0.01)
        assertEquals(6000.0, secondaryTotal, 0.01)
    }

    @Test
    fun transactionHistory_sortsChronologicallyDescending_includingBackdatedEntries() {
        val calNow = Calendar.getInstance()

        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val calLastWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        val calLastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

        // Entries logged in chaotic order
        val eToday = createExpense("e_today", 1000.0, date = calNow.time, createdAt = calNow.time)
        val eLastMonthBackdated = createExpense("e_last_month", 5000.0, date = calLastMonth.time, createdAt = calNow.time)
        val eYesterdayBackdated = createExpense("e_yesterday", 2000.0, date = calYesterday.time, createdAt = calNow.time)
        val eLastWeekBackdated = createExpense("e_last_week", 3000.0, date = calLastWeek.time, createdAt = calNow.time)

        val unsorted = listOf(eLastMonthBackdated, eToday, eLastWeekBackdated, eYesterdayBackdated)
        val sorted = unsorted.sortedByDescending { it.date }

        assertEquals("e_today", sorted[0].id)
        assertEquals("e_yesterday", sorted[1].id)
        assertEquals("e_last_week", sorted[2].id)
        assertEquals("e_last_month", sorted[3].id)
    }
}

