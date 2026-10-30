package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.ui.expense.DEFAULT_EXPENSE_CATEGORIES
import com.nexus.financetracker.ui.expense.ExpenseFormState
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unit tests verifying Expense quick entry state and fast defaults
 * as specified in FR9 & FR10 and Member 2 Order 1 (feature/expense-add).
 */
class ExpenseAddTest {

    @Test
    fun defaultFormState_hasTodayDateAndSensibleDefaults() {
        val state = ExpenseFormState()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val stateDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(state.date)

        // FR9/FR10: Today's date default
        assertEquals("Date must default to today", todayStr, stateDateStr)
        assertEquals("Account must default to Card", ExpenseAccount.CARD, state.account)
        assertEquals("Classification must default to Discretionary", ExpenseClassification.DISCRETIONARY, state.classification)
        assertEquals("", state.amountText)
        assertNull(state.computedAmountLKR)
        assertNull(state.errorMessage)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun defaultExpenseCategories_providesPredefinedOptions() {
        assertTrue("Default categories list must not be empty", DEFAULT_EXPENSE_CATEGORIES.isNotEmpty())
        val names = DEFAULT_EXPENSE_CATEGORIES.map { it.name }
        assertTrue("Must include Food & Dining", names.contains("Food & Dining"))
        assertTrue("Must include Groceries", names.contains("Groceries"))
        assertTrue("Must include Transport", names.contains("Transport"))
        assertTrue("Must include Rent or Rent & Housing", names.contains("Rent") || names.contains("Rent & Housing"))
        assertTrue("Must include Bills & Utilities", names.contains("Bills & Utilities"))
    }

    @Test
    fun computedAmountLKR_parsesValidDoubles() {
        val state = ExpenseFormState(amountText = "1250.50")
        assertEquals(1250.50, state.computedAmountLKR!!, 0.001)

        val invalidState = ExpenseFormState(amountText = "abc")
        assertNull(invalidState.computedAmountLKR)
    }

    @Test
    fun quickIncrement_addsToCurrentAmount() {
        val initial = ExpenseFormState(amountText = "1000")
        val currentAmount = initial.amountText.toDoubleOrNull() ?: 0.0
        val next = currentAmount + 500.0
        val updatedText = if (next % 1.0 == 0.0) next.toLong().toString() else "%.2f".format(Locale.US, next)

        val updated = initial.copy(amountText = updatedText)
        assertEquals("1500", updated.amountText)
        assertEquals(1500.0, updated.computedAmountLKR!!, 0.001)
    }

    @Test
    fun lastUsedDefaultsSimulation_retainsLastUsedCategoryAndAccount() {
        // FR9/FR10: simulate pre-populating last-used category and account
        val simulatedLatestCategory = Category(id = "cat_transport", name = "Transport")
        val simulatedLatestAccount = ExpenseAccount.CASH

        val formState = ExpenseFormState(
            categoryId = simulatedLatestCategory.id,
            categoryName = simulatedLatestCategory.name,
            account = simulatedLatestAccount,
            date = Date()
        )

        assertEquals("cat_transport", formState.categoryId)
        assertEquals("Transport", formState.categoryName)
        assertEquals(ExpenseAccount.CASH, formState.account)
    }
}
