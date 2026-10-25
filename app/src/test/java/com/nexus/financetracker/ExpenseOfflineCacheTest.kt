package com.nexus.financetracker

import com.nexus.financetracker.data.local.entity.ExpenseEntity
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit tests verifying Room ExpenseEntity, DAO mapping, and offline read caching
 * as specified in FR9-FR14 and Member 2 Order 2 (feature/offline-cache).
 */
class ExpenseOfflineCacheTest {

    private val testUserId = "test-user-member2"

    @Test
    fun fromDomain_toDomain_roundTripPreservesAllFields() {
        val now = Date()
        val originalExpense = Expense(
            id = "exp-local-101",
            userId = testUserId,
            amountLKR = 4500.0,
            categoryId = "cat_groceries",
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.COMMITTED,
            note = "Supermarket weekly essentials",
            date = now,
            recurringItemId = "recurring-item-001",
            createdAt = now
        )

        // Map to Room Entity
        val entity = ExpenseEntity.fromDomain(originalExpense)

        assertEquals("exp-local-101", entity.id)
        assertEquals(testUserId, entity.userId)
        assertEquals(4500.0, entity.amountLKR, 0.001)
        assertEquals("cat_groceries", entity.categoryId)
        assertEquals(ExpenseAccount.CARD, entity.account)
        assertEquals(ExpenseClassification.COMMITTED, entity.classification)
        assertEquals("Supermarket weekly essentials", entity.note)
        assertEquals(now.time, entity.date)
        assertEquals("recurring-item-001", entity.recurringItemId)
        assertEquals(now.time, entity.createdAt)

        // Map back to Domain Model
        val domainFromEntity = entity.toDomain()

        assertEquals(originalExpense.id, domainFromEntity.id)
        assertEquals(originalExpense.userId, domainFromEntity.userId)
        assertEquals(originalExpense.amountLKR, domainFromEntity.amountLKR, 0.001)
        assertEquals(originalExpense.categoryId, domainFromEntity.categoryId)
        assertEquals(originalExpense.account, domainFromEntity.account)
        assertEquals(originalExpense.classification, domainFromEntity.classification)
        assertEquals(originalExpense.note, domainFromEntity.note)
        assertEquals(originalExpense.date.time, domainFromEntity.date.time)
        assertEquals(originalExpense.recurringItemId, domainFromEntity.recurringItemId)
        assertEquals(originalExpense.createdAt.time, domainFromEntity.createdAt.time)
    }

    @Test
    fun nullableFields_handledSafelyInEntity() {
        val date = Date(1700000000000L)
        val entityWithoutOptionals = ExpenseEntity(
            id = "exp-no-optional",
            userId = testUserId,
            amountLKR = 1200.0,
            categoryId = "cat_transport",
            account = ExpenseAccount.CASH,
            classification = ExpenseClassification.DISCRETIONARY,
            note = null,
            date = date.time,
            recurringItemId = null,
            createdAt = date.time
        )

        val domain = entityWithoutOptionals.toDomain()
        assertNull(domain.note)
        assertNull(domain.recurringItemId)
        assertEquals(1200.0, domain.amountLKR, 0.001)
        assertEquals(ExpenseAccount.CASH, domain.account)
    }

    @Test
    fun offlineEntitiesList_mapsCorrectlyToDomainSortedByDate() {
        val t1 = 1700000000000L
        val t2 = 1700005000000L
        val t3 = 1700010000000L

        val cachedEntities = listOf(
            ExpenseEntity("1", testUserId, 500.0, "cat_food", date = t1, createdAt = t1),
            ExpenseEntity("3", testUserId, 1500.0, "cat_rent", date = t3, createdAt = t3),
            ExpenseEntity("2", testUserId, 750.0, "cat_other", date = t2, createdAt = t2)
        )

        val domainList = cachedEntities
            .map { it.toDomain() }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })

        assertEquals("3", domainList[0].id)
        assertEquals("2", domainList[1].id)
        assertEquals("1", domainList[2].id)
        assertEquals(1500.0, domainList[0].amountLKR, 0.001)
    }

    @Test
    fun dateRangeFiltering_correctlyFiltersCachedEntities() {
        val start = 1000L
        val mid = 2000L
        val end = 3000L
        val outOfRange = 5000L

        val entities = listOf(
            ExpenseEntity("in-range-1", testUserId, 200.0, "cat_food", date = start, createdAt = start),
            ExpenseEntity("in-range-2", testUserId, 400.0, "cat_groceries", date = mid, createdAt = mid),
            ExpenseEntity("in-range-3", testUserId, 600.0, "cat_bills", date = end, createdAt = end),
            ExpenseEntity("out-range", testUserId, 900.0, "cat_shopping", date = outOfRange, createdAt = outOfRange)
        )

        val filtered = entities
            .filter { it.date in start..end }
            .map { it.toDomain() }

        assertEquals(3, filtered.size)
        assertTrue(filtered.none { it.id == "out-range" })
        assertEquals(1200.0, filtered.sumOf { it.amountLKR }, 0.001)
    }
}
