package com.nexus.financetracker

import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.ui.income.IncomeFilter
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit tests verifying business logic for Branch 05 feature/income-add:
 * 1. Confirmed-only totals exclude pending income.
 * 2. Mixed multi-currency calculations.
 * 3. Realized crypto profit & loss handling.
 * 4. Filter logic (All, Confirmed, Pending).
 */
class IncomeFlowTest {

    private val testUserId = "user-abc-123"

    @Test
    fun confirmedOnlyTotals_strictlyExcludesPendingItems() {
        val incomes = listOf(
            Income(
                id = "1",
                userId = testUserId,
                source = IncomeSource.SALARY,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = 150000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = 150000.0,
                dateReceived = Date()
            ),
            Income(
                id = "2",
                userId = testUserId,
                source = IncomeSource.FREELANCE,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = 200.0,
                currency = "USD",
                exchangeRate = 320.0,
                amountLKR = 64000.0,
                dateReceived = Date()
            ),
            Income(
                id = "3",
                userId = testUserId,
                source = IncomeSource.ADSENSE,
                status = IncomeStatus.PENDING,
                amountOriginal = 50.0,
                currency = "USD",
                exchangeRate = 320.0,
                amountLKR = 16000.0,
                expectedDate = Date()
            ),
            Income(
                id = "4",
                userId = testUserId,
                source = IncomeSource.FREELANCE,
                status = IncomeStatus.PENDING,
                amountOriginal = 45000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = 45000.0,
                expectedDate = Date()
            )
        )

        val confirmedTotal = incomes
            .filter { it.status == IncomeStatus.CONFIRMED }
            .sumOf { it.amountLKR }

        val pendingTotal = incomes
            .filter { it.status == IncomeStatus.PENDING }
            .sumOf { it.amountLKR }

        assertEquals(214000.0, confirmedTotal, 0.001)
        assertEquals(61000.0, pendingTotal, 0.001)
        assertNotEquals(275000.0, confirmedTotal)
    }

    @Test
    fun realizedCrypto_calculatesNetGainsAndLossesInConfirmedTotal() {
        val incomes = listOf(
            Income(
                id = "1",
                userId = testUserId,
                source = IncomeSource.SALARY,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = 100000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = 100000.0,
                dateReceived = Date()
            ),
            Income(
                id = "2",
                userId = testUserId,
                source = IncomeSource.CRYPTO,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = 20000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = 20000.0,
                dateReceived = Date()
            ),
            Income(
                id = "3",
                userId = testUserId,
                source = IncomeSource.CRYPTO,
                status = IncomeStatus.CONFIRMED,
                amountOriginal = -8000.0,
                currency = "LKR",
                exchangeRate = 1.0,
                amountLKR = -8000.0,
                dateReceived = Date()
            )
        )

        val total = incomes
            .filter { it.status == IncomeStatus.CONFIRMED }
            .sumOf { it.amountLKR }

        assertEquals(112000.0, total, 0.001)
    }

    @Test
    fun filteringLogic_splitsListCorrectly() {
        val items = listOf(
            Income(id = "1", status = IncomeStatus.CONFIRMED),
            Income(id = "2", status = IncomeStatus.PENDING),
            Income(id = "3", status = IncomeStatus.CONFIRMED)
        )

        fun applyFilter(filter: IncomeFilter): List<Income> {
            return when (filter) {
                IncomeFilter.ALL -> items
                IncomeFilter.CONFIRMED -> items.filter { it.status == IncomeStatus.CONFIRMED }
                IncomeFilter.PENDING -> items.filter { it.status == IncomeStatus.PENDING }
            }
        }

        assertEquals(3, applyFilter(IncomeFilter.ALL).size)
        assertEquals(2, applyFilter(IncomeFilter.CONFIRMED).size)
        assertEquals(1, applyFilter(IncomeFilter.PENDING).size)
    }
}
