package com.nexus.financetracker.ui.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToLong

enum class IncomeFilter {
    ALL,
    CONFIRMED,
    PENDING
}

private data class SourceDefaults(
    val currency: String,
    val exchangeRate: String,
    val amount: String,
    val status: String
)

data class IncomeFormState(
    val editingIncomeId: String? = null,
    val originalCreatedAt: Date? = null,
    val source: String = IncomeSource.SALARY,
    val status: String = IncomeStatus.CONFIRMED,
    val amountOriginal: String = "130000.00",
    val currency: String = "LKR",
    val exchangeRate: String = "1.0",
    val dateReceived: Date = Date(),
    val expectedDate: Date = Date(),
    val note: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null
) {
    val isEditMode: Boolean
        get() = editingIncomeId != null

    val computedLkrAmount: Double?
        get() {
            val amount = amountOriginal.toDoubleOrNull() ?: return null
            val rate = exchangeRate.toDoubleOrNull() ?: return null
            return ((amount * rate) * 100.0).roundToLong() / 100.0
        }
}

data class IncomeUiState(
    val incomes: List<Income> = emptyList(),
    val filteredIncomes: List<Income> = emptyList(),
    val totalConfirmedLKR: Double = 0.0,
    val totalPendingLKR: Double = 0.0,
    val lastSalaryAmount: Double? = null,
    val selectedFilter: IncomeFilter = IncomeFilter.ALL,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class IncomeViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomeUiState())
    val uiState: StateFlow<IncomeUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(IncomeFormState())
    val formState: StateFlow<IncomeFormState> = _formState.asStateFlow()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    init {
        loadIncomes()
    }

    fun loadIncomes() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            incomeRepository.getIncomes(userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load incomes"
                    )
                }
                .collect { allIncomes ->
                    val confirmedTotal = allIncomes
                        .filter { it.status == IncomeStatus.CONFIRMED }
                        .sumOf { it.amountLKR }

                    val pendingTotal = allIncomes
                        .filter { it.status == IncomeStatus.PENDING }
                        .sumOf { it.amountLKR }

                    val lastSalary = allIncomes
                        .filter { it.source == IncomeSource.SALARY }
                        .maxByOrNull { it.dateReceived ?: it.createdAt }
                        ?.amountOriginal

                    val filtered = applyFilter(allIncomes, _uiState.value.selectedFilter)

                    _uiState.value = _uiState.value.copy(
                        incomes = allIncomes,
                        filteredIncomes = filtered,
                        totalConfirmedLKR = confirmedTotal,
                        totalPendingLKR = pendingTotal,
                        lastSalaryAmount = lastSalary,
                        isLoading = false,
                        errorMessage = null
                    )

                    // Prefill salary if user currently has salary selected and amount is default
                    if (!_formState.value.isEditMode && _formState.value.source == IncomeSource.SALARY && lastSalary != null) {
                        val formatted = String.format(java.util.Locale.US, "%.2f", lastSalary)
                        if (_formState.value.amountOriginal.isBlank() || _formState.value.amountOriginal == "130000.00" || _formState.value.amountOriginal == "130000") {
                            _formState.value = _formState.value.copy(amountOriginal = formatted)
                        }
                    }
                }
        }
    }

    fun onFilterSelected(filter: IncomeFilter) {
        _uiState.value = _uiState.value.copy(
            selectedFilter = filter,
            filteredIncomes = applyFilter(_uiState.value.incomes, filter)
        )
    }

    private fun applyFilter(incomes: List<Income>, filter: IncomeFilter): List<Income> {
        return when (filter) {
            IncomeFilter.ALL -> incomes
            IncomeFilter.CONFIRMED -> incomes.filter { it.status == IncomeStatus.CONFIRMED }
            IncomeFilter.PENDING -> incomes.filter { it.status == IncomeStatus.PENDING }
        }
    }

    fun loadIncomeForEdit(incomeId: String) {
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoading = true, error = null)
            try {
                val existing = _uiState.value.incomes.find { it.id == incomeId }
                    ?: incomeRepository.getIncomeById(incomeId)

                if (existing != null) {
                    _formState.value = IncomeFormState(
                        editingIncomeId = existing.id,
                        originalCreatedAt = existing.createdAt,
                        source = existing.source,
                        status = existing.status,
                        amountOriginal = String.format(java.util.Locale.US, "%.2f", existing.amountOriginal),
                        currency = existing.currency,
                        exchangeRate = existing.exchangeRate.toString(),
                        dateReceived = existing.dateReceived ?: existing.createdAt,
                        expectedDate = existing.expectedDate ?: Date(),
                        note = existing.note ?: "",
                        isLoading = false,
                        isSubmitting = false,
                        error = null
                    )
                } else {
                    _formState.value = _formState.value.copy(
                        isLoading = false,
                        error = "Income record not found"
                    )
                }
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to load income details"
                )
            }
        }
    }

    // --- Form Management tailored to Stream specifications ---

    fun onSourceChanged(source: String) {
        val lastSalary = _uiState.value.lastSalaryAmount
        val defaults = when (source) {
            IncomeSource.ADSENSE -> {
                // AdSense: Paid in USD and converted to LKR at transfer.
                SourceDefaults(currency = "USD", exchangeRate = "320.0", amount = "", status = IncomeStatus.CONFIRMED)
            }
            IncomeSource.SALARY -> {
                // Salary: LKR, variable (120,000 to 140,000), prefill last amount makes it a one-tap edit
                val prefill = lastSalary?.let {
                    String.format(java.util.Locale.US, "%.2f", it)
                } ?: "130000.00"
                SourceDefaults(currency = "LKR", exchangeRate = "1.0", amount = prefill, status = IncomeStatus.CONFIRMED)
            }
            IncomeSource.FREELANCE -> {
                // Freelance: Milestone-based, defaults to pending (invoiced, unpaid)
                SourceDefaults(currency = "LKR", exchangeRate = "1.0", amount = "", status = IncomeStatus.PENDING)
            }
            IncomeSource.CRYPTO -> {
                // Crypto: Realized profit and loss in LKR
                SourceDefaults(currency = "LKR", exchangeRate = "1.0", amount = "", status = IncomeStatus.CONFIRMED)
            }
            else -> SourceDefaults(currency = "LKR", exchangeRate = "1.0", amount = "", status = IncomeStatus.CONFIRMED)
        }

        _formState.value = _formState.value.copy(
            source = source,
            currency = defaults.currency,
            exchangeRate = defaults.exchangeRate,
            amountOriginal = defaults.amount,
            status = defaults.status,
            error = null
        )
    }

    fun onStatusChanged(status: String) {
        _formState.value = _formState.value.copy(status = status, error = null)
    }

    fun onCurrencyChanged(currency: String) {
        val uppercaseCurrency = currency.uppercase().trim()
        val defaultRate = when (uppercaseCurrency) {
            "LKR" -> "1.0"
            "USD" -> if (_formState.value.exchangeRate == "1.0" || _formState.value.exchangeRate.isBlank()) "320.0" else _formState.value.exchangeRate
            else -> _formState.value.exchangeRate
        }
        _formState.value = _formState.value.copy(
            currency = uppercaseCurrency,
            exchangeRate = defaultRate,
            error = null
        )
    }

    fun onExchangeRateChanged(rate: String) {
        _formState.value = _formState.value.copy(exchangeRate = rate, error = null)
    }

    fun onAmountOriginalChanged(amount: String) {
        _formState.value = _formState.value.copy(amountOriginal = amount, error = null)
    }

    fun onDateReceivedChanged(date: Date) {
        _formState.value = _formState.value.copy(dateReceived = date, error = null)
    }

    fun onExpectedDateChanged(date: Date) {
        _formState.value = _formState.value.copy(expectedDate = date, error = null)
    }

    fun onNoteChanged(note: String) {
        _formState.value = _formState.value.copy(note = note)
    }

    fun resetForm() {
        val lastSalary = _uiState.value.lastSalaryAmount
        val prefill = lastSalary?.let {
            String.format(java.util.Locale.US, "%.2f", it)
        } ?: "130000.00"

        _formState.value = IncomeFormState(
            editingIncomeId = null,
            originalCreatedAt = null,
            source = IncomeSource.SALARY,
            status = IncomeStatus.CONFIRMED,
            amountOriginal = prefill,
            currency = "LKR",
            exchangeRate = "1.0"
        )
    }

    fun submitIncome(onSuccess: () -> Unit) {
        val userId = currentUserId
        if (userId == null) {
            _formState.value = _formState.value.copy(error = "User session expired. Please sign in again.")
            return
        }

        val state = _formState.value
        val amount = state.amountOriginal.toDoubleOrNull()
        if (amount == null) {
            _formState.value = _formState.value.copy(error = "Please enter a valid amount")
            return
        }

        val rate = state.exchangeRate.toDoubleOrNull()
        if (rate == null || rate <= 0.0) {
            _formState.value = _formState.value.copy(error = "Please enter a valid positive exchange rate")
            return
        }

        if (state.currency !in listOf("LKR", "USD")) {
            _formState.value = _formState.value.copy(error = "Only LKR and USD currencies are supported")
            return
        }

        if (state.currency.equals("LKR", ignoreCase = true) && rate != 1.0) {
            _formState.value = _formState.value.copy(error = "Exchange rate for LKR must be 1.0")
            return
        }

        if (state.source == IncomeSource.CRYPTO) {
            if (amount == 0.0) {
                _formState.value = _formState.value.copy(error = "Realized crypto amount cannot be 0.0")
                return
            }
        } else {
            if (amount <= 0.0) {
                _formState.value = _formState.value.copy(error = "Amount must be greater than zero")
                return
            }
        }

        val computedLKR = ((amount * rate) * 100.0).roundToLong() / 100.0
        val isEdit = state.isEditMode
        val editingId = state.editingIncomeId
        val createdAt = state.originalCreatedAt ?: Date()

        val income = Income(
            id = editingId ?: "",
            userId = userId,
            source = state.source,
            status = state.status,
            amountOriginal = amount,
            currency = state.currency,
            exchangeRate = rate,
            amountLKR = computedLKR,
            dateReceived = if (state.status == IncomeStatus.CONFIRMED) state.dateReceived else null,
            expectedDate = if (state.status == IncomeStatus.PENDING) state.expectedDate else null,
            note = state.note.trim().takeIf { it.isNotBlank() },
            createdAt = createdAt,
            updatedAt = if (isEdit) Date() else null
        )

        val validationError = income.validate()
        if (validationError != null) {
            _formState.value = _formState.value.copy(error = validationError)
            return
        }

        viewModelScope.launch {
            _formState.value = _formState.value.copy(isSubmitting = true, error = null)
            try {
                if (isEdit) {
                    incomeRepository.updateIncome(income)
                } else {
                    incomeRepository.addIncome(income)
                }
                _formState.value = _formState.value.copy(isSubmitting = false)
                resetForm()
                onSuccess()
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isSubmitting = false,
                    error = e.localizedMessage ?: "Failed to save income record"
                )
            }
        }
    }

    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            try {
                incomeRepository.deleteIncome(incomeId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.localizedMessage ?: "Failed to delete income"
                )
            }
        }
    }
}
