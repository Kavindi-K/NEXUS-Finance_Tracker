package com.nexus.financetracker.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.RecurringItemRepository
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.RecurringFrequency
import com.nexus.financetracker.domain.model.RecurringItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class BillAlert(
    val item: RecurringItem,
    val isDueToday: Boolean
)

data class AlertsUiState(
    val isLoading: Boolean = true,
    val alerts: List<BillAlert> = emptyList(),
    val isProcessing: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val recurringItemRepository: RecurringItemRepository,
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
            recurringItemRepository.getRecurringItems(userId)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Failed to load alerts"
                    )
                }
                .collect { allItems ->
                    val activeItems = allItems.filter { it.isActive }
                    val now = Calendar.getInstance()

                    val todayStart = now.cloneCalendar().apply { setStartOfDay() }.time
                    val todayEnd = now.cloneCalendar().apply { setEndOfDay() }.time

                    val tomorrowCal = now.cloneCalendar().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val tomorrowStart = tomorrowCal.cloneCalendar().apply { setStartOfDay() }.time
                    val tomorrowEnd = tomorrowCal.cloneCalendar().apply { setEndOfDay() }.time

                    val alertList = mutableListOf<BillAlert>()

                    for (item in activeItems) {
                        val due = item.nextDueDate
                        when {
                            due in todayStart..todayEnd -> alertList.add(BillAlert(item, isDueToday = true))
                            due in tomorrowStart..tomorrowEnd -> alertList.add(BillAlert(item, isDueToday = false))
                        }
                    }

                    // Sorted with today's alerts first, then tomorrow
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        alerts = alertList.sortedBy { it.item.nextDueDate },
                        errorMessage = null
                    )
                }
        }
    }

    /**
     * Confirms the payment for a recurring item (FR23):
     * 1. Logs an Expense entry in the expenses collection with recurringItemId set.
     * 2. Advances the nextDueDate of the recurringItem to the next cycle.
     * 3. Calls onSuccess to navigate to Expenses UI.
     */
    fun confirmPayment(item: RecurringItem, onSuccess: () -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                // 1. Post expense
                val expense = Expense(
                    userId = userId,
                    amountLKR = item.amountLKR,
                    categoryId = item.categoryId,
                    account = item.account,
                    classification = item.classification,
                    note = "${item.title} (Recurring bill confirmed)",
                    date = Date(),
                    recurringItemId = item.id,
                    createdAt = Date()
                )
                expenseRepository.addExpense(expense)

                // 2. Advance nextDueDate
                val nextDate = calculateNextDueDate(item.nextDueDate, item.frequency)
                recurringItemRepository.updateRecurringItem(item.copy(nextDueDate = nextDate))

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    userMessage = "Payment confirmed and logged as expense for ${item.title}"
                )
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Failed to confirm payment: ${e.localizedMessage}"
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

    private fun calculateNextDueDate(currentDue: Date, frequency: String): Date {
        val cal = Calendar.getInstance().apply { time = currentDue }
        when (frequency.lowercase()) {
            RecurringFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurringFrequency.YEARLY -> cal.add(Calendar.YEAR, 1)
            else -> cal.add(Calendar.MONTH, 1)
        }
        return cal.time
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
