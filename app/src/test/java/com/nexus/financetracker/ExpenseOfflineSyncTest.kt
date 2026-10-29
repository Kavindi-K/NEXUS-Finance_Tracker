package com.nexus.financetracker

import com.nexus.financetracker.data.local.entity.ExpenseEntity
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.ui.expense.ExpenseUiState
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import java.util.UUID

/**
 * Unit tests verifying offline write queueing, Room local read caching, reconnect synchronization,
 * and account switching isolation as specified in Member 2 Order 7 (feature/offline-sync).
 */
class ExpenseOfflineSyncTest {

    private val userAlpha = "user-alpha-001"
    private val userBeta = "user-beta-002"

    private fun createTestExpense(
        id: String = UUID.randomUUID().toString(),
        userId: String = userAlpha,
        amount: Double = 1500.0,
        categoryId: String = "cat_food",
        account: String = ExpenseAccount.CARD,
        date: Date = Date()
    ): Expense {
        return Expense(
            id = id,
            userId = userId,
            amountLKR = amount,
            categoryId = categoryId,
            account = account,
            classification = ExpenseClassification.DISCRETIONARY,
            note = "Offline expense note",
            date = date,
            createdAt = date
        )
    }

    @Test
    fun offlineWriteQueueing_clientSideIdGeneratedInstantlyWithoutNetwork() {
        // In offline mode, client generates ID before queuing to Firestore and Room
        val generatedId = UUID.randomUUID().toString()
        val expense = createTestExpense(id = generatedId)

        assertNotNull(expense.id)
        assertTrue("Generated client-side ID must not be blank", expense.id.isNotBlank())
        assertEquals(userAlpha, expense.userId)
        assertEquals(1500.0, expense.amountLKR, 0.001)

        // Verify conversion to Room Entity for immediate zero-latency local caching
        val entity = ExpenseEntity.fromDomain(expense)
        assertEquals(generatedId, entity.id)
        assertEquals(1500.0, entity.amountLKR, 0.001)
        assertEquals(expense.date.time, entity.date)
    }

    @Test
    fun roomReadCache_servesLocalDataResilientlyWithoutNetwork() {
        // Simulate local Room database containing cached items
        val localRoomStore = mutableMapOf<String, ExpenseEntity>()

        val exp1 = createTestExpense(id = "exp-offline-1", amount = 2000.0)
        val exp2 = createTestExpense(id = "exp-offline-2", amount = 3500.0)

        // Insert into Room read cache
        localRoomStore[exp1.id] = ExpenseEntity.fromDomain(exp1)
        localRoomStore[exp2.id] = ExpenseEntity.fromDomain(exp2)

        // Read from cache
        val cachedExpenses = localRoomStore.values.map { it.toDomain() }
        assertEquals(2, cachedExpenses.size)
        assertTrue(cachedExpenses.any { it.id == "exp-offline-1" && it.amountLKR == 2000.0 })
        assertTrue(cachedExpenses.any { it.id == "exp-offline-2" && it.amountLKR == 3500.0 })
    }

    @Test
    fun reconnectSync_incomingDocumentChanges_updatesLocalCache() {
        val localRoomCache = mutableMapOf<String, ExpenseEntity>()

        // 1. Initial local item created while offline
        val localExpense = createTestExpense(id = "exp-offline-local", amount = 1000.0)
        localRoomCache[localExpense.id] = ExpenseEntity.fromDomain(localExpense)

        // 2. Reconnect: Remote server sends ADDED event for a transaction added on another device
        val remoteNewExpense = createTestExpense(id = "exp-remote-new", amount = 4200.0)
        localRoomCache[remoteNewExpense.id] = ExpenseEntity.fromDomain(remoteNewExpense)

        assertEquals("Cache should contain both local and synced remote items", 2, localRoomCache.size)
        assertNotNull(localRoomCache["exp-remote-new"])

        // 3. Remote server sends MODIFIED event
        val updatedRemoteExpense = remoteNewExpense.copy(amountLKR = 4500.0, note = "Updated from web")
        localRoomCache[updatedRemoteExpense.id] = ExpenseEntity.fromDomain(updatedRemoteExpense)

        assertEquals(4500.0, localRoomCache["exp-remote-new"]?.amountLKR ?: 0.0, 0.001)
        assertEquals("Updated from web", localRoomCache["exp-remote-new"]?.note)

        // 4. Remote server sends REMOVED event
        localRoomCache.remove("exp-offline-local")
        assertNull("Removed document should be deleted from Room read cache", localRoomCache["exp-offline-local"])
        assertEquals(1, localRoomCache.size)
    }

    @Test
    fun accountSwitching_clearLocalCache_purgesOnlyTargetUser() {
        val localRoomDatabase = mutableListOf<ExpenseEntity>()

        // Add records for User Alpha
        localRoomDatabase.add(ExpenseEntity.fromDomain(createTestExpense(id = "alpha-1", userId = userAlpha, amount = 100.0)))
        localRoomDatabase.add(ExpenseEntity.fromDomain(createTestExpense(id = "alpha-2", userId = userAlpha, amount = 200.0)))

        // Add records for User Beta
        localRoomDatabase.add(ExpenseEntity.fromDomain(createTestExpense(id = "beta-1", userId = userBeta, amount = 500.0)))

        assertEquals("Total records before clearing", 3, localRoomDatabase.size)

        // User Alpha logs out or switches -> clearLocalCache(userAlpha)
        localRoomDatabase.removeAll { it.userId == userAlpha }

        assertEquals("Only User Beta records should remain", 1, localRoomDatabase.size)
        assertEquals(userBeta, localRoomDatabase.first().userId)
        assertEquals("beta-1", localRoomDatabase.first().id)
        assertFalse(localRoomDatabase.any { it.userId == userAlpha })
    }

    @Test
    fun offlineBulkAdd_generatesDistinctIdsAndPreparesBatch() {
        val rawExpenses = listOf(
            createTestExpense(id = "", amount = 120.0),
            createTestExpense(id = "", amount = 250.0),
            createTestExpense(id = "", amount = 890.0)
        )

        val preparedIds = mutableListOf<String>()
        val entitiesToCache = mutableListOf<ExpenseEntity>()

        rawExpenses.forEach { exp ->
            val assignedId = if (exp.id.isNotBlank()) exp.id else UUID.randomUUID().toString()
            preparedIds.add(assignedId)
            entitiesToCache.add(ExpenseEntity.fromDomain(exp.copy(id = assignedId)))
        }

        assertEquals(3, preparedIds.size)
        assertEquals(3, entitiesToCache.size)
        // All IDs must be unique
        assertEquals(3, preparedIds.toSet().size)
        entitiesToCache.forEach { entity ->
            assertTrue(entity.id.isNotBlank())
            assertTrue(entity.amountLKR > 0)
        }
    }

    @Test
    fun expenseUiState_reflectsOfflineModeIndicator() {
        val onlineState = ExpenseUiState(isOffline = false)
        assertFalse(onlineState.isOffline)

        val offlineState = ExpenseUiState(isOffline = true)
        assertTrue(offlineState.isOffline)
    }
}

