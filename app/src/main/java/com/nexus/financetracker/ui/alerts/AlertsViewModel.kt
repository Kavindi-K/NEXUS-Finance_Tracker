package com.nexus.financetracker.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.data.repository.RecurringItemRepository
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.RecurringItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class BillAlert(
    val item: RecurringItem,
    val isDueToday: Boolean
)

data class IncomeAlert(
    val income: Income,
    val isExpectedToday: Boolean
)

data class AlertsUiState(
    val isLoading: Boolean = true,
    val billAlerts: List<BillAlert> = emptyList(),
    val incomeAlerts: List<IncomeAlert> = emptyList(),
    val isProcessing: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val recurringItemRepository: RecurringItemRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        loadAlerts()
    }

    fun loadAlerts() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Please sign in to view alerts"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            combine(
                recurringItemRepository.getRecurringItems(userId),
                incomeRepository.getPendingIncomes(userId)
            ) { allRecurring, allPendingIncomes ->
                val activeItems = allRecurring.filter { it.isActive }
                val now = Calendar.getInstance()

                val todayStart = now.cloneCalendar().apply { setStartOfDay() }.time
                val todayEnd = now.cloneCalendar().apply { setEndOfDay() }.time

                val tomorrowCal = now.cloneCalendar().apply { add(Calendar.DAY_OF_YEAR, 1) }
                val tomorrowStart = tomorrowCal.cloneCalendar().apply { setStartOfDay() }.time
                val tomorrowEnd = tomorrowCal.cloneCalendar().apply { setEndOfDay() }.time

                // 1. Bill Reminders (FR23)
                val billList = mutableListOf<BillAlert>()
                for (item in activeItems) {
                    val due = item.nextDueDate
                    when {
                        due in todayStart..todayEnd -> billList.add(BillAlert(item, isDueToday = true))
                        due in tomorrowStart..tomorrowEnd -> billList.add(BillAlert(item, isDueToday = false))
                    }
                }

                // 2. Pending Income Arrival Alerts (FR8)
                val incomeList = mutableListOf<IncomeAlert>()
                for (income in allPendingIncomes) {
                    val expected = income.expectedDate ?: continue
                    when {
                        expected in todayStart..todayEnd -> incomeList.add(IncomeAlert(income, isExpectedToday = true))
                        expected in tomorrowStart..tomorrowEnd -> incomeList.add(IncomeAlert(income, isExpectedToday = false))
                    }
                }

                Pair(
                    billList.sortedBy { it.item.nextDueDate },
                    incomeList.sortedBy { it.income.expectedDate }
                )
            }
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Failed to load alerts"
                    )
                }
                .collect { (bills, incomes) ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        billAlerts = bills,
                        incomeAlerts = incomes,
                        errorMessage = null
                    )
                }
        }
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun Calendar.cloneCalendar(): Calendar = clone() as Calendar

    private fun Calendar.setStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.setEndOfDay() {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
}
