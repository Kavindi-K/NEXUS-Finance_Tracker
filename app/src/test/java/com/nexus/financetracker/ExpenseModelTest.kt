package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit tests verifying Expense domain model validation and Firestore schema serialization
 * as specified in FR9-FR14 and firestore.rules validExpense constraints.
 */
class ExpenseModelTest {

    private val testUserId = "test-user-member2"

    @Test
    fun validExpense_passesValidation() {
        val expense = Expense(
            userId = testUserId,
            amountLKR = 2500.0,
            categoryId = "cat_groceries",
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.DISCRETIONARY,
            note = "Keells supermarket shopping",
            date = Date()
        )
        assertNull(expense.validate())
    }

    @Test
    fun missingUserId_failsValidation() {
        val expense = Expense(
            userId = "",
            amountLKR = 1000.0,
            categoryId = "cat_food",
            account = ExpenseAccount.CASH,
            classification = ExpenseClassification.DISCRETIONARY
        )
        assertEquals("User ID is required", expense.validate())
    }

    @Test
    fun zeroOrNegativeAmount_failsValidation() {
        val zeroExpense = Expense(
            userId = testUserId,
            amountLKR = 0.0,
            categoryId = "cat_food",
            account = ExpenseAccount.CASH,
            classification = ExpenseClassification.DISCRETIONARY
        )
        assertEquals("Expense amount must be greater than zero", zeroExpense.validate())

        val negativeExpense = zeroExpense.copy(amountLKR = -500.0)
        assertEquals("Expense amount must be greater than zero", negativeExpense.validate())
    }

    @Test
    fun blankCategory_failsValidation() {
        val expense = Expense(
            userId = testUserId,
            amountLKR = 1500.0,
            categoryId = "",
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.COMMITTED
        )
        assertEquals("Please select a category", expense.validate())
    }

    @Test
    fun invalidAccount_failsValidation() {
        val expense = Expense(
            userId = testUserId,
            amountLKR = 1500.0,
            categoryId = "cat_food",
            account = "bitcoin_wallet",
            classification = ExpenseClassification.DISCRETIONARY
        )
        assertEquals("Invalid account: bitcoin_wallet", expense.validate())
    }

    @Test
    fun invalidClassification_failsValidation() {
        val expense = Expense(
            userId = testUserId,
            amountLKR = 1500.0,
            categoryId = "cat_food",
            account = ExpenseAccount.CASH,
            classification = "luxury"
        )
        assertEquals("Invalid classification: luxury", expense.validate())
    }

    @Test
    fun toFirestoreMap_strictlyConformsToSecurityRulesSchema() {
        val now = Date()
        val expense = Expense(
            id = "test-doc-id-123",
            userId = testUserId,
            amountLKR = 4500.0,
            categoryId = "cat_utilities",
            account = ExpenseAccount.SECONDARY,
            classification = ExpenseClassification.COMMITTED,
            note = "Electricity bill",
            date = now,
            recurringItemId = "recurring_elec_01",
            createdAt = now
        )

        val map = expense.toFirestoreMap()

        // 1. Must never include 'id' in Firestore document data (causes onlyKeys rule failure)
        assertFalse("Map must not contain document id", map.containsKey("id"))

        // 2. Must only contain valid whitelist keys
        val allowedKeys = setOf(
            "userId", "amountLKR", "categoryId", "account",
            "classification", "note", "date", "createdAt", "recurringItemId"
        )
        assertTrue("Map keys must be a subset of allowed keys", allowedKeys.containsAll(map.keys))

        // 3. Values must match
        assertEquals(testUserId, map["userId"])
        assertEquals(4500.0, map["amountLKR"])
        assertEquals("cat_utilities", map["categoryId"])
        assertEquals("secondary", map["account"])
        assertEquals("committed", map["classification"])
        assertEquals("Electricity bill", map["note"])
        assertEquals(now, map["date"])
        assertEquals(now, map["createdAt"])
        assertEquals("recurring_elec_01", map["recurringItemId"])
    }

    @Test
    fun toFirestoreMap_withoutOptionalFields_omitsNoteAndRecurring() {
        val expense = Expense(
            id = "doc-id-456",
            userId = testUserId,
            amountLKR = 750.0,
            categoryId = "cat_food",
            account = ExpenseAccount.CASH,
            classification = ExpenseClassification.DISCRETIONARY,
            note = null,
            recurringItemId = null
        )

        val map = expense.toFirestoreMap()
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("note"))
        assertFalse(map.containsKey("recurringItemId"))
        assertEquals(750.0, map["amountLKR"])
    }

    @Test
    fun accountsAndClassifications_helpersProduceCorrectDisplayNames() {
        assertEquals("Card", ExpenseAccount.getDisplayName(ExpenseAccount.CARD))
        assertEquals("Cash", ExpenseAccount.getDisplayName(ExpenseAccount.CASH))
        assertEquals("Secondary Account", ExpenseAccount.getDisplayName(ExpenseAccount.SECONDARY))

        assertEquals("Committed (Fixed)", ExpenseClassification.getDisplayName(ExpenseClassification.COMMITTED))
        assertEquals("Discretionary (Flexible)", ExpenseClassification.DISCRETIONARY.let { ExpenseClassification.getDisplayName(it) })
    }

    @Test
    fun category_durationHelper_formatsAndParsesCorrectly() {
        val durString = com.nexus.financetracker.domain.model.Category.buildDurationString(6, "months")
        assertEquals("dur:6:months", durString)

        val cat = com.nexus.financetracker.domain.model.Category(
            id = "cat_netflix",
            userId = testUserId,
            name = "Netflix",
            classification = ExpenseClassification.COMMITTED,
            recurringFrequency = "monthly",
            mergingInto = durString
        )
        assertEquals(6, cat.recurringDuration)
        assertEquals("months", cat.recurringDurationUnit)
        assertEquals("6 Months", cat.recurringDurationText)

        val map = cat.toFirestoreMap()
        assertEquals("dur:6:months", map["mergingInto"])
        assertEquals("monthly", map["recurringFrequency"])
    }

    @Test
    fun expense_recurringMetadata_formatsAndParsesProgressCorrectly() {
        val recurringId = Expense.buildRecurringItemId(
            frequency = "monthly",
            duration = 6,
            unit = "months",
            index = 2,
            seriesId = "ser_abc123"
        )
        assertEquals("rec:monthly:6:months:2:ser_abc123", recurringId)

        val expense = Expense(
            id = "exp_002",
            userId = testUserId,
            amountLKR = 1500.0,
            categoryId = "cat_netflix",
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.COMMITTED,
            date = Date(),
            recurringItemId = recurringId
        )

        assertTrue(expense.isRecurring)
        assertEquals("monthly", expense.recurringFrequency)
        assertEquals(6, expense.recurringDuration)
        assertEquals("months", expense.recurringDurationUnit)
        assertEquals(2, expense.recurrenceIndex)
        assertEquals("ser_abc123", expense.recurringSeriesId)
        assertEquals("Recurring (2 of 6 · 4 left)", expense.getRecurringProgressText())

        // Verify Firestore map conforms to validExpense whitelist
        val map = expense.toFirestoreMap()
        assertEquals(recurringId, map["recurringItemId"])
        val allowedKeys = setOf(
            "userId", "amountLKR", "categoryId", "account",
            "classification", "note", "date", "createdAt", "recurringItemId"
        )
        assertTrue(allowedKeys.containsAll(map.keys))
    }

    @Test
    fun expense_ongoingRecurring_formatsProgressCorrectly() {
        val recurringId = Expense.buildRecurringItemId(
            frequency = "monthly",
            duration = null,
            unit = "months",
            index = 1,
            seriesId = "ser_ongoing_1"
        )
        val expense = Expense(
            id = "exp_ongoing",
            userId = testUserId,
            amountLKR = 5000.0,
            categoryId = "cat_rent",
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.COMMITTED,
            date = Date(),
            recurringItemId = recurringId
        )

        assertTrue(expense.isRecurring)
        assertNull(expense.recurringDuration)
        assertEquals("Recurring (Monthly · Ongoing)", expense.getRecurringProgressText())
    }
}
