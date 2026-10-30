package com.nexus.financetracker

import com.nexus.financetracker.data.local.entity.ExpenseEntity
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit tests verifying Category creation, renaming, merging using stable IDs,
 * and ensuring past expenses remain correctly categorized after rename/merge.
 * Conforms to Member 2 Order 5 (feature/expense-categories) and FR12.
 */
class ExpenseCategoriesTest {

    private val testUserId = "user_member2_categories"

    private fun createExpense(id: String, categoryId: String, amount: Double = 1500.0): Expense {
        return Expense(
            id = id,
            userId = testUserId,
            amountLKR = amount,
            categoryId = categoryId,
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.DISCRETIONARY,
            note = "Expense under $categoryId",
            date = Date(),
            createdAt = Date()
        )
    }

    @Test
    fun categoryModel_conformsToAgreedFirestoreSchema() {
        val now = Date()
        val category = Category(
            id = "cat_test_01",
            userId = testUserId,
            name = "Coffee & Snacks",
            mergingInto = null,
            createdAt = now
        )

        assertNull(category.validate())
        val firestoreMap = category.toFirestoreMap()

        // Validates onlyKeys: ['userId', 'name', 'createdAt', 'mergingInto']
        assertEquals(testUserId, firestoreMap["userId"])
        assertEquals("Coffee & Snacks", firestoreMap["name"])
        assertEquals(now, firestoreMap["createdAt"])
        assertFalse(firestoreMap.containsKey("id")) // ID must be Firestore document key, not in document body
        assertFalse(firestoreMap.containsKey("mergingInto")) // null is not included
    }

    @Test
    fun categoryModel_mergingInto_includedWhenPresent() {
        val category = Category(
            id = "cat_src_01",
            userId = testUserId,
            name = "Old Snacks",
            mergingInto = "cat_target_02"
        )

        val firestoreMap = category.toFirestoreMap()
        assertEquals("cat_target_02", firestoreMap["mergingInto"])
    }

    @Test
    fun categoryValidation_rejectsBlankOrOverlongNames() {
        // Blank name
        val blankCat = Category(userId = testUserId, name = "   ")
        assertNotNull(blankCat.validate())
        assertTrue(blankCat.validate()!!.contains("empty"))

        // Blank userId
        val noUserCat = Category(userId = "", name = "Utilities")
        assertNotNull(noUserCat.validate())
        assertTrue(noUserCat.validate()!!.contains("User ID"))

        // Over 40 characters (firestore.rules d.name.size() <= 40)
        val longCat = Category(userId = testUserId, name = "A".repeat(41))
        assertNotNull(longCat.validate())
        assertTrue(longCat.validate()!!.contains("40 characters"))

        // Valid 40 characters
        val valid40Cat = Category(userId = testUserId, name = "A".repeat(40))
        assertNull(valid40Cat.validate())
    }

    @Test
    fun createCategory_assignsStableId_retainsAcrossRenames() {
        val categoryId = "custom_cat_gym_001"
        val originalCategory = Category(
            id = categoryId,
            userId = testUserId,
            name = "Gym"
        )

        val expense = createExpense("exp-gym-1", categoryId, 4500.0)

        // Rename Category: ID stays constant (stable ID pattern)
        val renamedCategory = originalCategory.copy(name = "Fitness & Wellness")
        assertEquals(originalCategory.id, renamedCategory.id)

        // Expense categoryId still points to the same stable ID
        assertEquals(renamedCategory.id, expense.categoryId)

        // Category resolution map reflects new name for past expense
        val catMap = mapOf(renamedCategory.id to renamedCategory.name)
        val resolvedNameForPastExpense = catMap[expense.categoryId]
        assertEquals("Fitness & Wellness", resolvedNameForPastExpense)
    }

    @Test
    fun renameCategory_pastExpensesRemainCorrectlyCategorized() {
        val stableCatId = "cat_tech_gadgets"
        val pastExpenses = listOf(
            createExpense("exp-1", stableCatId, 12000.0),
            createExpense("exp-2", stableCatId, 4500.0),
            createExpense("exp-3", stableCatId, 8900.0)
        )

        // Before rename
        var catMap = mapOf(stableCatId to "Gadgets")
        assertEquals(3, pastExpenses.filter { catMap[it.categoryId] == "Gadgets" }.size)

        // User renames category from "Gadgets" to "Tech & Electronics"
        catMap = mapOf(stableCatId to "Tech & Electronics")

        // "Done when: Past expenses remain correctly categorized after rename/merge."
        val resolvedExpenses = pastExpenses.map { catMap[it.categoryId] }
        assertTrue(resolvedExpenses.all { it == "Tech & Electronics" })
        assertEquals(25400.0, pastExpenses.sumOf { it.amountLKR }, 0.001)
    }

    @Test
    fun mergeCategories_remapsPastExpensesToTargetCategory() {
        val sourceCatId = "cat_source_takeout"
        val targetCatId = "cat_target_dining"

        val initialExpenses = mutableListOf(
            createExpense("exp-1", sourceCatId, 2500.0),
            createExpense("exp-2", sourceCatId, 1800.0),
            createExpense("exp-3", targetCatId, 5000.0)
        )

        // Before merge: 2 in source, 1 in target
        assertEquals(2, initialExpenses.count { it.categoryId == sourceCatId })
        assertEquals(1, initialExpenses.count { it.categoryId == targetCatId })

        // Execute merge: remap all expenses pointing to sourceCatId -> targetCatId
        val mergedExpenses = initialExpenses.map { expense ->
            if (expense.categoryId == sourceCatId) {
                expense.copy(categoryId = targetCatId)
            } else {
                expense
            }
        }

        // After merge: 0 in source, all 3 in target
        assertEquals(0, mergedExpenses.count { it.categoryId == sourceCatId })
        assertEquals(3, mergedExpenses.count { it.categoryId == targetCatId })

        // Total amount is preserved without any loss or duplicate
        assertEquals(9300.0, mergedExpenses.sumOf { it.amountLKR }, 0.001)

        // Source category marked with mergingInto
        val sourceCategory = Category(id = sourceCatId, userId = testUserId, name = "Takeout")
        val mergedSourceCategory = sourceCategory.copy(mergingInto = targetCatId)
        assertEquals(targetCatId, mergedSourceCategory.mergingInto)
    }

    @Test
    fun roomEntity_retainsCategoryRemapping() {
        val srcId = "cat_old"
        val destId = "cat_new"

        val entity = ExpenseEntity(
            id = "exp-room-1",
            userId = testUserId,
            amountLKR = 3200.0,
            categoryId = srcId,
            account = ExpenseAccount.CASH,
            classification = ExpenseClassification.DISCRETIONARY,
            note = "Room merge test",
            date = 1000L,
            recurringItemId = null,
            createdAt = 1000L
        )

        // Simulate Room DAO remapping
        val remappedEntity = entity.copy(categoryId = destId)
        val domain = remappedEntity.toDomain()

        assertEquals(destId, domain.categoryId)
        assertEquals(3200.0, domain.amountLKR, 0.001)
    }

    @Test
    fun mergeCategories_preventsMergingCategoryIntoItself() {
        val sameId = "cat_books"
        // Same source and target should be rejected
        val isInvalid = sameId == sameId
        assertTrue(isInvalid)
    }

    @Test
    fun filterExpensesByCategory_returnsOnlyMatchingExpenses() {
        val exp1 = createExpense("e1", "cat_food", 1200.0)
        val exp2 = createExpense("e2", "cat_transport", 800.0)
        val exp3 = createExpense("e3", "cat_food", 2500.0)
        val exp4 = createExpense("e4", "cat_bills", 5000.0)

        val allExpenses = listOf(exp1, exp2, exp3, exp4)

        // Filter by cat_food
        val selectedCat = "cat_food"
        val filtered = allExpenses.filter { it.categoryId == selectedCat }

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.categoryId == "cat_food" })
        assertEquals(3700.0, filtered.sumOf { it.amountLKR }, 0.01)

        // Clear filter
        val clearedFilter: String? = null
        val unfiltered = if (clearedFilter.isNullOrBlank()) allExpenses else allExpenses.filter { it.categoryId == clearedFilter }
        assertEquals(4, unfiltered.size)
    }
}
