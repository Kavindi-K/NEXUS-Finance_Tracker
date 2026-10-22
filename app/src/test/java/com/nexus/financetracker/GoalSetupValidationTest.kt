package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Goal
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests verifying Goal Setup business logic, input validation rules,
 * and schema alignment for branch feature/goal-setup (Member 3 - Order 3).
 */
class GoalSetupValidationTest {

    private fun validateGoalInput(
        title: String,
        targetAmountLKR: Double?,
        openingSavingsLKR: Double?,
        startDate: Date,
        targetDate: Date
    ): List<String> {
        val errors = mutableListOf<String>()

        if (title.trim().isEmpty()) {
            errors.add("TITLE_EMPTY")
        }

        if (targetAmountLKR == null || targetAmountLKR <= 0.0) {
            errors.add("TARGET_INVALID")
        }

        if (openingSavingsLKR == null || openingSavingsLKR < 0.0) {
            errors.add("SAVINGS_NEGATIVE")
        }

        if (targetAmountLKR != null && openingSavingsLKR != null && openingSavingsLKR >= targetAmountLKR) {
            errors.add("TARGET_NOT_EXCEEDING_SAVINGS")
        }

        if (targetDate.time <= startDate.time) {
            errors.add("TARGET_DATE_NOT_FUTURE")
        }

        return errors
    }

    @Test
    fun kavinduMacBookScenario_validatesSuccessfully() {
        val startDate = Date()
        val cal = Calendar.getInstance().apply {
            time = startDate
            add(Calendar.MONTH, 12)
        }
        val targetDate = cal.time

        val errors = validateGoalInput(
            title = "MacBook Pro M4",
            targetAmountLKR = 490000.0,
            openingSavingsLKR = 11200.0,
            startDate = startDate,
            targetDate = targetDate
        )

        assertTrue("Expected 0 errors for Kavindu's scenario, got: $errors", errors.isEmpty())

        val goal = Goal(
            id = "test-goal-id",
            userId = "kavindu-uid-123",
            title = "MacBook Pro M4",
            targetAmountLKR = 490000.0,
            openingSavingsLKR = 11200.0,
            startDate = startDate,
            targetDate = targetDate,
            isActive = true,
            createdAt = startDate
        )

        assertEquals("MacBook Pro M4", goal.title)
        assertEquals(490000.0, goal.targetAmountLKR, 0.001)
        assertEquals(11200.0, goal.openingSavingsLKR, 0.001)
        assertTrue(goal.isActive)
    }

    @Test
    fun emptyTitle_triggersValidationFailure() {
        val now = Date()
        val future = Date(now.time + 86400000L * 30)

        val errors = validateGoalInput(
            title = "   ",
            targetAmountLKR = 100000.0,
            openingSavingsLKR = 5000.0,
            startDate = now,
            targetDate = future
        )

        assertTrue(errors.contains("TITLE_EMPTY"))
    }

    @Test
    fun targetAmountZeroOrNegative_triggersValidationFailure() {
        val now = Date()
        val future = Date(now.time + 86400000L * 30)

        val errorsZero = validateGoalInput(
            title = "Emergency Fund",
            targetAmountLKR = 0.0,
            openingSavingsLKR = 0.0,
            startDate = now,
            targetDate = future
        )
        assertTrue(errorsZero.contains("TARGET_INVALID"))

        val errorsNegative = validateGoalInput(
            title = "Emergency Fund",
            targetAmountLKR = -500.0,
            openingSavingsLKR = 0.0,
            startDate = now,
            targetDate = future
        )
        assertTrue(errorsNegative.contains("TARGET_INVALID"))
    }

    @Test
    fun openingSavingsNegative_triggersValidationFailure() {
        val now = Date()
        val future = Date(now.time + 86400000L * 30)

        val errors = validateGoalInput(
            title = "New Phone",
            targetAmountLKR = 150000.0,
            openingSavingsLKR = -1000.0,
            startDate = now,
            targetDate = future
        )
        assertTrue(errors.contains("SAVINGS_NEGATIVE"))
    }

    @Test
    fun openingSavingsExceedingOrEqualingTarget_triggersValidationFailure() {
        val now = Date()
        val future = Date(now.time + 86400000L * 30)

        val errorsEqual = validateGoalInput(
            title = "Trip Fund",
            targetAmountLKR = 50000.0,
            openingSavingsLKR = 50000.0,
            startDate = now,
            targetDate = future
        )
        assertTrue(errorsEqual.contains("TARGET_NOT_EXCEEDING_SAVINGS"))

        val errorsGreater = validateGoalInput(
            title = "Trip Fund",
            targetAmountLKR = 50000.0,
            openingSavingsLKR = 60000.0,
            startDate = now,
            targetDate = future
        )
        assertTrue(errorsGreater.contains("TARGET_NOT_EXCEEDING_SAVINGS"))
    }

    @Test
    fun pastOrEqualTargetDate_triggersValidationFailure() {
        val now = Date()
        val past = Date(now.time - 86400000L)

        val errors = validateGoalInput(
            title = "Laptop Fund",
            targetAmountLKR = 490000.0,
            openingSavingsLKR = 11200.0,
            startDate = now,
            targetDate = past
        )
        assertTrue(errors.contains("TARGET_DATE_NOT_FUTURE"))
    }

    @Test
    fun firestoreGoalSecurityRulesSchema_keysMatchAllowedWhitelist() {
        // As defined in firestore.rules validGoal():
        // onlyKeys(d, ['userId', 'title', 'targetAmountLKR', 'openingSavingsLKR', 'startDate', 'targetDate', 'isActive', 'createdAt'])
        val firestoreAllowedKeys = setOf(
            "userId", "title", "targetAmountLKR", "openingSavingsLKR",
            "startDate", "targetDate", "isActive", "createdAt"
        )

        val goalMap = hashMapOf(
            "userId" to "test-user-id",
            "title" to "MacBook Pro M4",
            "targetAmountLKR" to 490000.0,
            "openingSavingsLKR" to 11200.0,
            "startDate" to Date(),
            "targetDate" to Date(System.currentTimeMillis() + 86400000L * 365),
            "isActive" to true,
            "createdAt" to Date()
        )

        assertEquals(8, goalMap.keys.size)
        assertTrue(firestoreAllowedKeys.containsAll(goalMap.keys))
        assertFalse("Goal map must NOT include id field to satisfy firestore security rules onlyKeys constraint", goalMap.containsKey("id"))
    }
}
