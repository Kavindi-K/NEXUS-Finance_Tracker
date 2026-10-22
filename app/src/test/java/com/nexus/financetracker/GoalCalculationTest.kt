package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests verifying Goal Calculation logic (FR15, Section 5.2).
 *
 * Implements Acceptance Criteria for Member 3 - Order 4 (feature/goal-calculation):
 * - Formula: current savings estimate = opening savings + cumulative confirmed net income - cumulative expenses
 * - required/month = max(0, target - current savings) / months remaining
 * - Progress changes with transactions
 * - Pending income strictly excluded
 * - No double counting (pre-goal transactions excluded)
 */
class GoalCalculationTest {

    private fun createBaseGoal(
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
    fun kavinduMacBookBaseline_initialCalculationMatchesRequirement() {
        // As defined in Technical Document Section 5.2:
        // "For the MacBook goal created with LKR 11,200 already saved, it starts at
        // (490,000 - 11,200) / 12, about LKR 39,900 a month."
        val now = Date()
        val goal = createBaseGoal(startDate = now, monthsHorizon = 12)

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = emptyList(),
            expenses = emptyList(),
            currentDate = now
        )

        assertEquals("Estimated current savings should equal opening savings with 0 transactions", 11200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
        assertEquals("Remaining needed", 478800.0, calculation.remainingAmountLKR, 0.01)
        assertEquals("Months remaining", 12, calculation.monthsRemaining)
        assertEquals("Required monthly savings should be 39,900.0", 39900.0, calculation.requiredMonthlySavingsLKR, 0.01)
        assertEquals(2.2857, calculation.progressPercentage, 0.01)
        assertFalse(calculation.isCompleted)
        assertFalse(calculation.isOverdue)
    }

    @Test
    fun confirmedIncomeOnOrAfterStartDate_increasesSavingsAndLowersRequiredRate() {
        val startDate = Date()
        val goal = createBaseGoal(startDate = startDate, monthsHorizon = 12)

        // Confirmed salary deposit of LKR 130,000 received 5 days after goal start
        val confirmedSalary = Income(
            id = "inc-salary-1",
            userId = goal.userId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 130000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 130000.0,
            dateReceived = Date(startDate.time + 5L * 86400000L),
            createdAt = Date(startDate.time + 5L * 86400000L)
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = listOf(confirmedSalary),
            expenses = emptyList(),
            currentDate = startDate
        )

        // Estimated savings = 11,200 + 130,000 = 141,200 LKR
        assertEquals(141200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
        assertEquals(348800.0, calculation.remainingAmountLKR, 0.01)
        // Required per month = 348,800 / 12 = 29,066.67 LKR
        assertEquals(348800.0 / 12.0, calculation.requiredMonthlySavingsLKR, 0.01)
        assertEquals((141200.0 / 490000.0) * 100.0, calculation.progressPercentage, 0.01)
    }

    @Test
    fun pendingIncome_isStrictlyExcludedFromGoalSavings() {
        val startDate = Date()
        val goal = createBaseGoal(startDate = startDate, monthsHorizon = 12)

        // Pending freelance invoice for LKR 50,000 (status = pending, no dateReceived)
        val pendingInvoice = Income(
            id = "inc-freelance-pending",
            userId = goal.userId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.PENDING,
            amountOriginal = 50000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 50000.0,
            dateReceived = null,
            expectedDate = Date(startDate.time + 10L * 86400000L),
            createdAt = startDate
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = listOf(pendingInvoice),
            expenses = emptyList(),
            currentDate = startDate
        )

        // Savings must still be 11,200 LKR because pending income is excluded
        assertEquals(11200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
        assertEquals(0.0, calculation.cumulativeConfirmedIncomeLKR, 0.01)
        assertEquals(39900.0, calculation.requiredMonthlySavingsLKR, 0.01)
    }

    @Test
    fun incomeDatedBeforeGoalStartDate_isExcluded_preventingDoubleCounting() {
        val startDate = Date(1771500000000L) // Fixed reference time
        val goal = createBaseGoal(startDate = startDate, monthsHorizon = 12)

        // Income received 10 days BEFORE goal start date (e.g. earned in past year)
        val preGoalIncome = Income(
            id = "inc-pre-goal",
            userId = goal.userId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 120000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 120000.0,
            dateReceived = Date(startDate.time - 10L * 86400000L),
            createdAt = Date(startDate.time - 10L * 86400000L)
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = listOf(preGoalIncome),
            expenses = emptyList(),
            currentDate = startDate
        )

        // Must not double count pre-goal transactions: opening savings already represents it
        assertEquals(11200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
        assertEquals(0.0, calculation.cumulativeConfirmedIncomeLKR, 0.01)
    }

    @Test
    fun expensesOnOrAfterStartDate_decreaseSavingsAndIncreaseRequiredRate() {
        val startDate = Date()
        val goal = createBaseGoal(startDate = startDate, monthsHorizon = 12)

        val salary = Income(
            id = "inc-1",
            userId = goal.userId,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 140000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 140000.0,
            dateReceived = Date(startDate.time + 1L * 86400000L),
            createdAt = Date(startDate.time + 1L * 86400000L)
        )

        // Rent expense of LKR 34,000 and dining expense of LKR 24,000
        val rent = Expense(
            id = "exp-rent",
            userId = goal.userId,
            amountLKR = 34000.0,
            date = Date(startDate.time + 2L * 86400000L)
        )
        val dining = Expense(
            id = "exp-dining",
            userId = goal.userId,
            amountLKR = 24000.0,
            date = Date(startDate.time + 15L * 86400000L)
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = listOf(salary),
            expenses = listOf(rent, dining),
            currentDate = startDate
        )

        // Net income = 140,000 - 58,000 = 82,000 LKR
        // Estimated savings = 11,200 + 82,000 = 93,200 LKR
        assertEquals(93200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
        assertEquals(58000.0, calculation.cumulativeExpenseLKR, 0.01)
        assertEquals(140000.0, calculation.cumulativeConfirmedIncomeLKR, 0.01)
        // Remaining = 490,000 - 93,200 = 396,800 LKR
        assertEquals(396800.0, calculation.remainingAmountLKR, 0.01)
        assertEquals(396800.0 / 12.0, calculation.requiredMonthlySavingsLKR, 0.01)
    }

    @Test
    fun expensesDatedBeforeGoalStartDate_areExcluded() {
        val startDate = Date(1771500000000L)
        val goal = createBaseGoal(startDate = startDate, monthsHorizon = 12)

        val preGoalExpense = Expense(
            id = "exp-pre-goal",
            userId = goal.userId,
            amountLKR = 60000.0,
            date = Date(startDate.time - 5L * 86400000L)
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = emptyList(),
            expenses = listOf(preGoalExpense),
            currentDate = startDate
        )

        // Pre-goal expense is not counted
        assertEquals(11200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
        assertEquals(0.0, calculation.cumulativeExpenseLKR, 0.01)
    }

    @Test
    fun cryptoProfitsAndLosses_handledCorrectlyWithoutSpecialCase() {
        val startDate = Date()
        val goal = createBaseGoal(startDate = startDate, monthsHorizon = 12)

        // Crypto trade gain: +25,000 LKR
        val cryptoGain = Income(
            id = "crypto-gain",
            userId = goal.userId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 25000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 25000.0,
            dateReceived = Date(startDate.time + 3L * 86400000L),
            createdAt = Date(startDate.time + 3L * 86400000L)
        )

        // Crypto trade loss: -15,000 LKR
        val cryptoLoss = Income(
            id = "crypto-loss",
            userId = goal.userId,
            source = IncomeSource.CRYPTO,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = -15000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = -15000.0,
            dateReceived = Date(startDate.time + 8L * 86400000L),
            createdAt = Date(startDate.time + 8L * 86400000L)
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = listOf(cryptoGain, cryptoLoss),
            expenses = emptyList(),
            currentDate = startDate
        )

        // Net crypto = +25,000 - 15,000 = +10,000 LKR
        assertEquals(10000.0, calculation.cumulativeConfirmedIncomeLKR, 0.01)
        // Estimated savings = 11,200 + 10,000 = 21,200 LKR
        assertEquals(21200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
    }

    @Test
    fun targetAmountReachedOrExceeded_requiredMonthlySavingsIsZero() {
        val startDate = Date()
        val goal = createBaseGoal(startDate = startDate, monthsHorizon = 12)

        // Large confirmed payment exceeding goal target
        val windfall = Income(
            id = "inc-windfall",
            userId = goal.userId,
            source = IncomeSource.FREELANCE,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = 500000.0,
            currency = "LKR",
            exchangeRate = 1.0,
            amountLKR = 500000.0,
            dateReceived = Date(startDate.time + 10L * 86400000L),
            createdAt = Date(startDate.time + 10L * 86400000L)
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = listOf(windfall),
            expenses = emptyList(),
            currentDate = startDate
        )

        // Estimated savings = 11,200 + 500,000 = 511,200 LKR (> 490,000)
        assertEquals(511200.0, calculation.estimatedCurrentSavingsLKR, 0.01)
        assertEquals(0.0, calculation.remainingAmountLKR, 0.01)
        // FR15: max(0, target - estimatedCurrentSavings) / months = 0
        assertEquals(0.0, calculation.requiredMonthlySavingsLKR, 0.0)
        assertTrue(calculation.isCompleted)
        assertEquals(1.0f, calculation.progressFraction)
        assertTrue(calculation.progressPercentage >= 100.0)
    }

    @Test
    fun overdueGoal_handlesDeadlineSafelyWithoutCrashOrDivideByZero() {
        val now = Date()
        // Goal whose targetDate was 10 days ago
        val pastTargetDate = Date(now.time - 10L * 86400000L)
        val pastStartDate = Date(now.time - 70L * 86400000L)

        val overdueGoal = Goal(
            id = "overdue-goal",
            userId = "kavindu-uid-123",
            title = "Past Goal",
            targetAmountLKR = 100000.0,
            openingSavingsLKR = 20000.0,
            startDate = pastStartDate,
            targetDate = pastTargetDate,
            isActive = true,
            createdAt = pastStartDate
        )

        val calculation = GoalCalculation.calculate(
            goal = overdueGoal,
            incomes = emptyList(),
            expenses = emptyList(),
            currentDate = now
        )

        assertEquals(0, calculation.monthsRemaining)
        assertTrue(calculation.isOverdue)
        assertFalse(calculation.isCompleted)
        // Safe remaining amount without divide-by-zero
        assertEquals(80000.0, calculation.remainingAmountLKR, 0.01)
        assertEquals(80000.0, calculation.requiredMonthlySavingsLKR, 0.01)
    }

    @Test
    fun multiStreamReconciliation_allFourIncomeSourcesReconcileAccurately() {
        val startDate = Date()
        val goal = createBaseGoal(startDate = startDate, monthsHorizon = 10)

        val transactions = listOf(
            // 1. Salary
            Income(
                id = "inc-salary",
                userId = goal.userId,
                source = IncomeSource.SALARY,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = 135000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = 135000.0,
                dateReceived = Date(startDate.time + 1000L)
            ),
            // 2. Confirmed freelance milestone
            Income(
                id = "inc-freelance-conf",
                userId = goal.userId,
                source = IncomeSource.FREELANCE,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = 45000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = 45000.0,
                dateReceived = Date(startDate.time + 2000L)
            ),
            // 3. Pending freelance milestone (EXCLUDED)
            Income(
                id = "inc-freelance-pend",
                userId = goal.userId,
                source = IncomeSource.FREELANCE,
                status = IncomeStatus.PENDING,
                amountOriginal = 30000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = 30000.0,
                dateReceived = null
            ),
            // 4. AdSense: USD 45 at 320.0 = LKR 14,400
            Income(
                id = "inc-adsense",
                userId = goal.userId,
                source = IncomeSource.ADSENSE,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = 45.0,
                currency = "USD",
                exchangeRate = 320.0,
                amountLKR = 14400.0,
                dateReceived = Date(startDate.time + 3000L)
            ),
            // 5. Crypto net loss: -8,000 LKR
            Income(
                id = "inc-crypto-loss",
                userId = goal.userId,
                source = IncomeSource.CRYPTO,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = -8000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = -8000.0,
                dateReceived = Date(startDate.time + 4000L)
            )
        )

        val expenses = listOf(
            Expense(
                id = "exp-rent",
                userId = goal.userId,
                amountLKR = 34000.0,
                date = Date(startDate.time + 1500L)
            ),
            Expense(
                id = "exp-subs",
                userId = goal.userId,
                amountLKR = 6500.0,
                date = Date(startDate.time + 2500L)
            )
        )

        val calculation = GoalCalculation.calculate(
            goal = goal,
            incomes = transactions,
            expenses = expenses,
            currentDate = startDate
        )

        // Confirmed Income Total = 135,000 + 45,000 + 14,400 - 8,000 = 186,400 LKR
        assertEquals(186400.0, calculation.cumulativeConfirmedIncomeLKR, 0.01)
        // Expenses Total = 34,000 + 6,500 = 40,500 LKR
        assertEquals(40500.0, calculation.cumulativeExpenseLKR, 0.01)
        // Net Period Savings = 186,400 - 40,500 = 145,900 LKR
        // Estimated Current Savings = 11,200 + 145,900 = 157,100 LKR
        assertEquals(157100.0, calculation.estimatedCurrentSavingsLKR, 0.01)

        // Remaining = 490,000 - 157,100 = 332,900 LKR
        assertEquals(332900.0, calculation.remainingAmountLKR, 0.01)
        // Required / Month for 10 months horizon = 332,900 / 10 = 33,290.0 LKR/mo
        assertEquals(33290.0, calculation.requiredMonthlySavingsLKR, 0.01)
    }
}
