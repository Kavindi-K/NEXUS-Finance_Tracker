package com.nexus.financetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.TransactionRepository
import com.nexus.financetracker.domain.model.Transaction
import com.nexus.financetracker.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Dashboard screen.
 * Exposes only immutable StateFlow to the UI — no mutable state leaks out.
 *
 * TODO: Add budget summary and goal progress to DashboardUiState.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadTransactions()
    }

    private fun loadTransactions() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            transactionRepository.getTransactions(userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
                }
                .collect { transactions ->
                    _uiState.value = _uiState.value.copy(
                        transactions = transactions,
                        totalIncome = transactions
                            .filter { it.type == TransactionType.INCOME }
                            .sumOf { it.amount },
                        totalExpense = transactions
                            .filter { it.type == TransactionType.EXPENSE }
                            .sumOf { it.amount },
                        isLoading = false
                    )
                }
        }
    }
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val errorMessage: String? = null
) {
    val balance: Double get() = totalIncome - totalExpense
}
