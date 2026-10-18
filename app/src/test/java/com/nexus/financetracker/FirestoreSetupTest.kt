package com.nexus.financetracker

import com.nexus.financetracker.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit test verifying that the 6 core domain models agree with the Technical Document schema
 * and user profile serialization contract for branch 01 feature/firestore-setup.
 */
class FirestoreSetupTest {

    @Test
    fun userProfile_hasExpectedSchemaFields() {
        val now = Date()
        val user = UserProfile(
            uid = "u123",
            displayName = "Kavindu Silva",
            email = "kavindu@example.com",
            phoneNumber = "+94771234567",
            createdAt = now
        )
        assertEquals("u123", user.uid)
        assertEquals("Kavindu Silva", user.displayName)
        assertEquals("+94771234567", user.phoneNumber)
        assertEquals("kavindu@example.com", user.email)
        assertNotNull(user.createdAt)
    }

    @Test
    fun userProfile_contractForFirestoreReadWrite() {
        val now = Date()
        val user = UserProfile(
            uid = "user_kavindu_1",
            displayName = "Kavindu Silva",
            email = "kavindu.dev@example.com",
            phoneNumber = "+94771234567",
            createdAt = now
        )
        // Verify document field map adheres to Firestore schema in Technical Doc 5.2
        val map = mapOf(
            "uid" to user.uid,
            "displayName" to user.displayName,
            "email" to user.email,
            "phoneNumber" to user.phoneNumber,
            "createdAt" to user.createdAt
        )
        assertEquals("user_kavindu_1", map["uid"])
        assertEquals("Kavindu Silva", map["displayName"])
        assertEquals("+94771234567", map["phoneNumber"])
        assertEquals("kavindu.dev@example.com", map["email"])
    }

    @Test
    fun income_supportsMultiCurrencyAndPnl() {
        val income = Income(
            id = "inc1",
            userId = "u123",
            source = IncomeSource.ADSENSE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 45.0,
            currency = "USD",
            exchangeRate = 320.0,
            amountLKR = 14400.0
        )
        assertEquals(14400.0, income.amountLKR, 0.001)
        assertEquals("USD", income.currency)
        assertEquals(IncomeSource.ADSENSE, income.source)
    }

    @Test
    fun expense_supportsAccountsAndClassification() {
        val expense = Expense(
            id = "exp1",
            userId = "u123",
            amountLKR = 24000.0,
            categoryId = "cat_food",
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.DISCRETIONARY
        )
        assertEquals(24000.0, expense.amountLKR, 0.001)
        assertEquals(ExpenseClassification.DISCRETIONARY, expense.classification)
        assertEquals(ExpenseAccount.CARD, expense.account)
    }

    @Test
    fun goal_hasTargetAndOpeningSavings() {
        val goal = Goal(
            id = "goal1",
            userId = "u123",
            title = "MacBook Pro M4",
            targetAmountLKR = 490000.0,
            openingSavingsLKR = 11200.0,
            isActive = true
        )
        assertEquals(490000.0, goal.targetAmountLKR, 0.001)
        assertEquals(11200.0, goal.openingSavingsLKR, 0.001)
        assertTrue(goal.isActive)
    }

    @Test
    fun category_supportsMergingMarker() {
        val cat = Category(
            id = "cat_old",
            userId = "u123",
            name = "Coffee",
            mergingInto = "cat_food"
        )
        assertEquals("cat_food", cat.mergingInto)
    }

    @Test
    fun recurringItem_supportsSchedules() {
        val item = RecurringItem(
            id = "rec1",
            userId = "u123",
            title = "Rent",
            amountLKR = 34000.0,
            categoryId = "cat_rent",
            frequency = RecurringFrequency.MONTHLY,
            isActive = true
        )
        assertEquals(34000.0, item.amountLKR, 0.001)
        assertEquals(RecurringFrequency.MONTHLY, item.frequency)
    }
}
