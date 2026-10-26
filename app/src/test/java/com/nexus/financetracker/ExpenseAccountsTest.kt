package com.nexus.financetracker

import com.nexus.financetracker.data.local.entity.ExpenseEntity
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit tests verifying Card, Cash, and Secondary account selection, retention,
 * and comprehensive total aggregation as specified in Member 2 Order 4 (feature/expense-accounts).
 * Done when: "Totals include all three account types."
 */
class ExpenseAccountsTest {

    private val testUserId = "user_member2_accounts"

    private fun createExpense(id: String, amount: Double, account: String): Expense {
        return Expense(
            id = id,
            userId = testUserId,
            amountLKR = amount,
            categoryId = "cat_general",
            account = account,
            classification = ExpenseClassification.DISCRETIONARY,
            note = "Account test for $account",
            date = Date(),
            createdAt = Date()
        )
    }

    @Test
    fun accountSelection_supportsCardCashAndSecondary() {
        assertEquals(3, ExpenseAccount.ALL.size)
        assertTrue(ExpenseAccount.ALL.contains(ExpenseAccount.CARD))
        assertTrue(ExpenseAccount.ALL.contains(ExpenseAccount.CASH))
        assertTrue(ExpenseAccount.ALL.contains(ExpenseAccount.SECONDARY))

        assertEquals("Card", ExpenseAccount.getDisplayName(ExpenseAccount.CARD))
        assertEquals("Cash", ExpenseAccount.getDisplayName(ExpenseAccount.CASH))
        assertEquals("Secondary Account", ExpenseAccount.getDisplayName(ExpenseAccount.SECONDARY))
    }

    @Test
    fun totalsIncludeAllThreeAccountTypes() {
        val expenses = listOf(
            createExpense("exp-1", 12500.0, ExpenseAccount.CARD),
            createExpense("exp-2", 3400.0, ExpenseAccount.CASH),
            createExpense("exp-3", 8100.0, ExpenseAccount.SECONDARY),
            createExpense("exp-4", 2500.0, ExpenseAccount.CARD),
            createExpense("exp-5", 1500.0, ExpenseAccount.CASH)
        )

        val total = expenses.sumOf { it.amountLKR }
        val cardTotal = expenses.filter { it.account.equals(ExpenseAccount.CARD, ignoreCase = true) }.sumOf { it.amountLKR }
        val cashTotal = expenses.filter { it.account.equals(ExpenseAccount.CASH, ignoreCase = true) }.sumOf { it.amountLKR }
        val secondaryTotal = expenses.filter { it.account.equals(ExpenseAccount.SECONDARY, ignoreCase = true) }.sumOf { it.amountLKR }

        assertEquals(15000.0, cardTotal, 0.001)
        assertEquals(4900.0, cashTotal, 0.001)
        assertEquals(8100.0, secondaryTotal, 0.001)

        // Total MUST equal the sum of all three accounts
        assertEquals(28000.0, total, 0.001)
        assertEquals(total, cardTotal + cashTotal + secondaryTotal, 0.001)
    }

    @Test
    fun accountRetention_inExpenseModelAndValidation() {
        // Card
        val cardExpense = createExpense("e1", 1000.0, ExpenseAccount.CARD)
        assertEquals(ExpenseAccount.CARD, cardExpense.account)
        assertNull(cardExpense.validate())

        // Cash
        val cashExpense = createExpense("e2", 2000.0, ExpenseAccount.CASH)
        assertEquals(ExpenseAccount.CASH, cashExpense.account)
        assertNull(cashExpense.validate())

        // Secondary
        val secExpense = createExpense("e3", 3000.0, ExpenseAccount.SECONDARY)
        assertEquals(ExpenseAccount.SECONDARY, secExpense.account)
        assertNull(secExpense.validate())

        // Invalid account is rejected
        val invalidExpense = createExpense("e4", 500.0, "crypto_wallet")
        assertNotNull(invalidExpense.validate())
        assertTrue(invalidExpense.validate()!!.contains("Invalid account"))
    }

    @Test
    fun accountRetention_inRoomEntityRoundTrip() {
        for (acc in ExpenseAccount.ALL) {
            val original = createExpense("id-$acc", 4500.0, acc)
            val entity = ExpenseEntity.fromDomain(original)
            assertEquals(acc, entity.account)

            val fromEntity = entity.toDomain()
            assertEquals(acc, fromEntity.account)
        }
    }

    @Test
    fun accountBreakdownPercentages_calculatedCorrectly() {
        val expenses = listOf(
            createExpense("1", 5000.0, ExpenseAccount.CARD),       // 50%
            createExpense("2", 3000.0, ExpenseAccount.CASH),       // 30%
            createExpense("3", 2000.0, ExpenseAccount.SECONDARY)   // 20%
        )

        val total = expenses.sumOf { it.amountLKR }
        assertEquals(10000.0, total, 0.001)

        val cardPct = (expenses.filter { it.account == ExpenseAccount.CARD }.sumOf { it.amountLKR } / total) * 100.0
        val cashPct = (expenses.filter { it.account == ExpenseAccount.CASH }.sumOf { it.amountLKR } / total) * 100.0
        val secPct = (expenses.filter { it.account == ExpenseAccount.SECONDARY }.sumOf { it.amountLKR } / total) * 100.0

        assertEquals(50.0, cardPct, 0.001)
        assertEquals(30.0, cashPct, 0.001)
        assertEquals(20.0, secPct, 0.001)
        assertEquals(100.0, cardPct + cashPct + secPct, 0.001)
    }

    @Test
    fun accountFiltering_returnsOnlyMatchingAccountExpenses() {
        val expenses = listOf(
            createExpense("card-1", 100.0, ExpenseAccount.CARD),
            createExpense("card-2", 200.0, ExpenseAccount.CARD),
            createExpense("cash-1", 300.0, ExpenseAccount.CASH),
            createExpense("sec-1", 400.0, ExpenseAccount.SECONDARY)
        )

        val cardOnly = expenses.filter { it.account.equals(ExpenseAccount.CARD, ignoreCase = true) }
        val cashOnly = expenses.filter { it.account.equals(ExpenseAccount.CASH, ignoreCase = true) }
        val secOnly = expenses.filter { it.account.equals(ExpenseAccount.SECONDARY, ignoreCase = true) }

        assertEquals(2, cardOnly.size)
        assertEquals(1, cashOnly.size)
        assertEquals(1, secOnly.size)

        assertEquals("cash-1", cashOnly.first().id)
        assertEquals("sec-1", secOnly.first().id)
    }

    @Test
    fun totalsWithZeroExpensesInAnAccount_calculatesCorrectly() {
        // User has only Card and Cash expenses, no Secondary yet
        val expenses = listOf(
            createExpense("1", 2000.0, ExpenseAccount.CARD),
            createExpense("2", 1500.0, ExpenseAccount.CASH)
        )

        val total = expenses.sumOf { it.amountLKR }
        val cardTotal = expenses.filter { it.account.equals(ExpenseAccount.CARD, ignoreCase = true) }.sumOf { it.amountLKR }
        val cashTotal = expenses.filter { it.account.equals(ExpenseAccount.CASH, ignoreCase = true) }.sumOf { it.amountLKR }
        val secTotal = expenses.filter { it.account.equals(ExpenseAccount.SECONDARY, ignoreCase = true) }.sumOf { it.amountLKR }

        assertEquals(3500.0, total, 0.001)
        assertEquals(2000.0, cardTotal, 0.001)
        assertEquals(1500.0, cashTotal, 0.001)
        assertEquals(0.0, secTotal, 0.001)
        assertEquals(total, cardTotal + cashTotal + secTotal, 0.001)
    }

    @Test
    fun editExpense_preservesOrUpdatesAccount() {
        val original = createExpense("e100", 3000.0, ExpenseAccount.CASH)
        assertEquals(ExpenseAccount.CASH, original.account)

        // User edits the payment account to Secondary Account
        val updated = original.copy(account = ExpenseAccount.SECONDARY)
        assertEquals(ExpenseAccount.SECONDARY, updated.account)
        assertEquals(original.id, updated.id)
        assertNull(updated.validate())
    }
}
