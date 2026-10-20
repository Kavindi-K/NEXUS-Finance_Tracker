package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.GoalProjection
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.domain.model.PaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests verifying Goal Projection logic (FR16, Technical Document Section 2.3, 6.5).
 *
 * Implements Acceptance Criteria for Member 3 - Order 5 (feature/goal-projection):
 * - Project completion from observed savings rate derived from transaction history
 * - Clearly marked as estimate
 * - Handles insufficient history
 * - Handles zero/negative savings rate
 * - Handles overdue targets
 * - Ahead / on track / behind pace classification in neutral language
 */
class GoalProjectionTest {

    private fun createTestGoal(
        startDate: Date,
        monthsHorizon: Int = 12,
        targetAmount: Double = 490000.0,
        openingSavings: Double = 11200.0
    ): Goal {
        val cal = Calendar.getInstance().apply {
            time = startDate
            add(Calendar.MONTH, monthsHorizon)
        }
        return Goal(
            id = "goal-macbook",
            userId = "kavindu-uid-123",
            title = "MacBook Pro M4",
            targetAmountLKR = targetAmount,
            openingSavingsLKR = openingSavings,
            startDate = startDate,
            targetDate = cal.time,
            isActive = true,
            createdAt = startDate
        )
    }

    @Test
    fun insufficientHistory_noTransactions_returnsNeutralStatusWithoutProjection() {
        val now = Date()
        val goal = createTestGoal(startDate = now, monthsHorizon = 12)

        val projection = GoalProjection.project(
            goal = goal,
            cumulativeConfirmedIncomeLKR = 0.0,
            cumulativeExpenseLKR = 0.0,
            remainingAmountLKR = 478800.0,
            hasTransactions = false,
            currentDate = now
        )

        assertFalse(projection.hasSufficientHistory)
        assertEquals(PaceStatus.INSUFFICIENT_HISTORY, projection.paceStatus)
        assertNull("Projected date must be null when history is insufficient", projection.projectedDate)
        assertEquals(0.0, projection.monthlySavingsRateLKR, 0.001)
        assertTrue(projection.isEstimate)
        assertTrue("Status message should indicate history is needed", projection.statusMessage.contains("Needs transaction history"))
    }

    @Test
    fun zeroSavingsRate_handlesZeroObservedRateSafelyWithoutCrashOrDivideByZero() {
        val now = Date()
        val goal = createTestGoal(startDate = now, monthsHorizon = 12)

        // Net savings observed is exactly 0 (e.g. 50k income and 50k expenses)
        val projection = GoalProjection.project(
            goal = goal,
            cumulativeConfirmedIncomeLKR = 50000.0,
            cumulativeExpenseLKR = 50000.0,
            remainingAmountLKR = 478800.0,
            hasTransactions = true,
            currentDate = now
        )

        assertTrue(projection.hasSufficientHistory)
        assertEquals(PaceStatus.BEHIND_PACE, projection.paceStatus)
        assertNull("Cannot project finite date with zero rate", projection.projectedDate)
        assertEquals(0.0, projection.monthlySavingsRateLKR, 0.001)
        assertTrue(projection.statusMessage.contains("Zero net savings observed"))
    }

    @Test
    fun negativeSavingsRate_handlesExpensesExceedingIncomeSafely() {
        val now = Date()
        val goal = createTestGoal(startDate = now, monthsHorizon = 12)

        // Expenses exceed income: 40k income, 70k expenses = -30k net
        val projection = GoalProjection.project(
            goal = goal,
            cumulativeConfirmedIncomeLKR = 40000.0,
            cumulativeExpenseLKR = 70000.0,
            remainingAmountLKR = 478800.0,
            hasTransactions = true,
            currentDate = now
        )

        assertTrue(projection.hasSufficientHistory)
        assertEquals(PaceStatus.BEHIND_PACE, projection.paceStatus)
        assertNull("Cannot project completion when spending exceeds income", projection.projectedDate)
        assertTrue("Monthly rate should be negative", projection.monthlySavingsRateLKR < 0.0)
        assertTrue(projection.statusMessage.contains("Spending exceeds income"))
    }

    @Test
    fun overdueTarget_handlesOverdueTargetGracefully() {
        val now = Date()
        val pastStartDate = Date(now.time - 90L * 86400000L)
        val pastTargetDate = Date(now.time - 10L * 86400000L)

        val overdueGoal = Goal(
            id = "overdue-goal",
            userId = "kavindu-uid-123",
            title = "MacBook Pro M4",
            targetAmountLKR = 490000.0,
            openingSavingsLKR = 11200.0,
            startDate = pastStartDate,
            targetDate = pastTargetDate,
            isActive = true,
            createdAt = pastStartDate
        )

        val projection = GoalProjection.project(
            goal = overdueGoal,
            cumulativeConfirmedIncomeLKR = 80000.0,
            cumulativeExpenseLKR = 30000.0,
            remainingAmountLKR = 428800.0,
            hasTransactions = true,
            currentDate = now
        )

        assertEquals(PaceStatus.OVERDUE, projection.paceStatus)
        assertNotNull(projection.projectedDate)
        assertTrue("Status message should flag target date passed", projection.statusMessage.contains("Target date passed"))
    }

    @Test
    fun kavinduBehindPaceScenario_matchesWireframePaceLanguage() {
        // As defined in wireframe 6.5 (Page 35):
        // Average rate observed: LKR 36,300/mo.
        // Required per month: LKR 39,900/mo.
        // Status: "Slightly behind pace — At this rate: Nov 2027, 2 months late"
        val startCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0)
        }
        val targetCal = Calendar.getInstance().apply {
            set(2027, Calendar.SEPTEMBER, 1, 0, 0, 0) // 12 months horizon
        }
        val currentCal = Calendar.getInstance().apply {
            set(2027, Calendar.FEBRUARY, 1, 0, 0, 0) // 5 months elapsed, 7 months remaining
        }

        val goal = Goal(
            id = "macbook-wireframe",
            userId = "kavindu-123",
            title = "MacBook Pro M4",
            targetAmountLKR = 490000.0,
            openingSavingsLKR = 11200.0,
            startDate = startCal.time,
            targetDate = targetCal.time,
            isActive = true
        )

        // Trailing net savings observed over 5 months: 5 * 36,300 = 181,500 LKR
        val confirmedIncome = 350000.0
        val expenses = 350000.0 - (5.0 * 36300.0) // net = 181,500 LKR
        val remainingAmount = 340000.0 // as shown in wireframe: "Need LKR 340,000"

        val projection = GoalProjection.project(
            goal = goal,
            cumulativeConfirmedIncomeLKR = confirmedIncome,
            cumulativeExpenseLKR = expenses,
            remainingAmountLKR = remainingAmount,
            hasTransactions = true,
            currentDate = currentCal.time
        )

        assertEquals(PaceStatus.BEHIND_PACE, projection.paceStatus)
        assertNotNull(projection.projectedDate)
        assertTrue("Expected monthly rate to be near 36,300", Math.abs(projection.monthlySavingsRateLKR - 36300.0) < 500.0)
        assertTrue("Months difference should be late (positive)", projection.monthsDifference > 0)
        assertTrue("Status message should use neutral pace language", projection.statusMessage.contains("Slightly behind pace"))
        assertTrue("Status message must mark projection as estimate", projection.statusMessage.contains("(estimate)"))
    }

    @Test
    fun aheadOfPaceScenario_projectsEarlierCompletionDate() {
        val startCal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1)
        }
        val targetCal = Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 31) // 12 months
        }
        val currentCal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 1) // 2 months elapsed
        }

        val goal = Goal(
            id = "ahead-goal",
            userId = "kavindu-123",
            title = "Emergency Fund",
            targetAmountLKR = 200000.0,
            openingSavingsLKR = 20000.0,
            startDate = startCal.time,
            targetDate = targetCal.time
        )

        // Saving 50,000 per month (total needed 180,000 -> 3.6 months to finish, well before 10 months remaining)
        val projection = GoalProjection.project(
            goal = goal,
            cumulativeConfirmedIncomeLKR = 150000.0,
            cumulativeExpenseLKR = 50000.0, // 100k over 2 months = 50k/mo
            remainingAmountLKR = 80000.0,
            hasTransactions = true,
            currentDate = currentCal.time
        )

        assertEquals(PaceStatus.AHEAD_OF_PACE, projection.paceStatus)
        assertTrue("Months difference should be negative (ahead)", projection.monthsDifference < 0)
        assertTrue(projection.statusMessage.contains("Ahead of pace"))
    }

    @Test
    fun goalCompleted_statusFlagsCompletedImmediately() {
        val now = Date()
        val goal = createTestGoal(startDate = now, monthsHorizon = 12)

        val projection = GoalProjection.project(
            goal = goal,
            cumulativeConfirmedIncomeLKR = 500000.0,
            cumulativeExpenseLKR = 10000.0,
            remainingAmountLKR = 0.0,
            hasTransactions = true,
            currentDate = now
        )

        assertEquals(PaceStatus.COMPLETED, projection.paceStatus)
        assertEquals("Goal Achieved!", projection.statusMessage)
        assertEquals(now, projection.projectedDate)
    }

    @Test
    fun goalCalculationIntegration_embedsProjectionSeamlessly() {
        val now = Date()
        val goal = createTestGoal(startDate = now, monthsHorizon = 12)

        val salary = Income(
            id = "inc-sal",
            userId = goal.userId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 140000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 140000.0,
            dateReceived = Date(now.time + 1000L)
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = listOf(salary),
            expenses = emptyList(),
            currentDate = now
        )

        // Verification of integration between GoalCalculation and GoalProjection
        assertNotNull(calculation.projection)
        assertEquals(calculation.cumulativeConfirmedIncomeLKR, calculation.actualMonthlyAverageLKR, 0.01)
        assertNotNull(calculation.projectedCompletionDate)
        assertEquals(calculation.projection.paceStatus, calculation.paceStatus)
        assertEquals(calculation.projection.statusMessage, calculation.paceMessage)
    }
}
