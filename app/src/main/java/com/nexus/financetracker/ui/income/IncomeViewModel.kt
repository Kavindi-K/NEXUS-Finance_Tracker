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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToLong

enum class IncomeSortOrder {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC
}

enum class IncomeFilter {
    ALL,
    CONFIRMED,
    PENDING
}

enum class EstimateComparisonStatus {
    BELOW_ESTIMATE,
    WITHIN_ESTIMATE,
    ABOVE_ESTIMATE
}

data class IncomeSourceTotal(
    val source: String,
    val displayName: String,
    val amountLKR: Double,
    val percentage: Int
)

data class MonthlyReconciliation(
    val monthLabel: String,
    val reconciledActualLKR: Double,
    val estimateMinLKR: Double = 160000.0,
    val estimateMaxLKR: Double = 210000.0,
    val comparisonStatus: EstimateComparisonStatus,
    val differenceLKR: Double,
    val salaryLKR: Double = 0.0,
    val freelanceLKR: Double = 0.0,
    val adsenseLKR: Double = 0.0,
    val cryptoNetLKR: Double = 0.0,
    val otherLKR: Double = 0.0,
    val sourceBreakdown: List<IncomeSourceTotal> = emptyList()
)

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
    val isCryptoLoss: Boolean = false,
    val dateReceived: Date = Date(),
    val expectedDate: Date = Date(),
    val customSourceTitle: String = "",
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
    val streamConfirmedLKR: Double = 0.0,
    val streamPendingLKR: Double = 0.0,
    val lastSalaryAmount: Double? = null,
    val selectedSortOrder: IncomeSortOrder = IncomeSortOrder.DATE_DESC,
    val selectedFilter: IncomeFilter = IncomeFilter.ALL,
    val selectedSource: String? = null,
    val selectedMonthLabel: String = "",
    val availableMonths: List<String> = emptyList(),
    val monthlyIncomeTotals: Map<String, Double> = emptyMap(),
    val isPeriodAllTime: Boolean = false,
    val reconciliation: MonthlyReconciliation? = null,
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

    private var selectedCalendar: Calendar = Calendar.getInstance()
    private var rawIncomes: List<Income> = emptyList()

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
                    rawIncomes = allIncomes
                    recomputeState()
                }
        }
    }

    fun selectMonth(monthLabel: String) {
        if (monthLabel.equals("All Time", ignoreCase = true)) {
            onToggleAllTimePeriod(true)
            return
        }
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        try {
            val parsed = monthFormat.parse(monthLabel)
            if (parsed != null) {
                selectedCalendar.time = parsed
            }
        } catch (_: Exception) {}
        _uiState.value = _uiState.value.copy(
            selectedMonthLabel = monthLabel,
            isPeriodAllTime = false
        )
        recomputeState()
    }

    fun selectPreviousMonth() {
        val months = _uiState.value.availableMonths
        if (_uiState.value.isPeriodAllTime) {
            if (months.isNotEmpty()) {
                selectMonth(months.first())
            }
            return
        }
        val currentIndex = months.indexOf(_uiState.value.selectedMonthLabel)
        if (currentIndex in 0 until months.lastIndex) {
            selectMonth(months[currentIndex + 1])
        } else {
            selectedCalendar.add(Calendar.MONTH, -1)
            recomputeState()
        }
    }

    fun selectNextMonth() {
        val months = _uiState.value.availableMonths
        if (_uiState.value.isPeriodAllTime) {
            if (months.isNotEmpty()) {
                selectMonth(months.first())
            }
            return
        }
        val currentIndex = months.indexOf(_uiState.value.selectedMonthLabel)
        if (currentIndex > 0) {
            selectMonth(months[currentIndex - 1])
        } else {
            selectedCalendar.add(Calendar.MONTH, 1)
            recomputeState()
        }
    }

    fun onPreviousMonthClicked() {
        selectPreviousMonth()
    }

    fun onNextMonthClicked() {
        selectNextMonth()
    }

    fun onCurrentMonthClicked() {
        selectedCalendar = Calendar.getInstance()
        _uiState.value = _uiState.value.copy(isPeriodAllTime = false)
        recomputeState()
    }

    fun onToggleAllTimePeriod(allTime: Boolean) {
        _uiState.value = _uiState.value.copy(isPeriodAllTime = allTime)
        recomputeState()
    }

    private fun recomputeState() {
        val allIncomes = rawIncomes
        val isAllTime = _uiState.value.isPeriodAllTime
        val selectedSource = _uiState.value.selectedSource
        val monthLabelFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        val monthLabel = monthLabelFormat.format(selectedCalendar.time)

        val cal = Calendar.getInstance().apply { time = selectedCalendar.time }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.time

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfMonth = cal.time

        // Overall confirmed & pending across all entries
        val overallConfirmedTotal = allIncomes.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        val overallPendingTotal = allIncomes.filter { it.status == IncomeStatus.PENDING }.sumOf { it.amountLKR }

        // Selected period incomes (taking expectedDate for pending into account)
        val periodIncomes = if (isAllTime) {
            allIncomes
        } else {
            allIncomes.filter { income ->
                val date = income.dateReceived ?: income.expectedDate ?: income.createdAt
                !date.before(startOfMonth) && !date.after(endOfMonth)
            }
        }

        val periodConfirmed = periodIncomes.filter { it.status == IncomeStatus.CONFIRMED }
        val reconciledActual = periodConfirmed.sumOf { it.amountLKR }

        val salaryTotal = periodConfirmed.filter { it.source == IncomeSource.SALARY }.sumOf { it.amountLKR }
        val freelanceTotal = periodConfirmed.filter { it.source == IncomeSource.FREELANCE }.sumOf { it.amountLKR }
        val cryptoNetTotal = periodConfirmed.filter { it.source == IncomeSource.CRYPTO }.sumOf { it.amountLKR }
        val adsenseTotal = periodConfirmed.filter { it.source == IncomeSource.ADSENSE }.sumOf { it.amountLKR }
        val otherTotal = periodConfirmed.filter { it.source == IncomeSource.OTHER }.sumOf { it.amountLKR }

        val positiveDenominator = maxOf(1.0, salaryTotal + freelanceTotal + adsenseTotal + otherTotal + maxOf(0.0, cryptoNetTotal))
        val sourceBreakdown = listOf(
            IncomeSourceTotal(
                source = IncomeSource.SALARY,
                displayName = "Base Salary",
                amountLKR = salaryTotal,
                percentage = if (salaryTotal > 0) ((salaryTotal / positiveDenominator) * 100).toInt() else 0
            ),
            IncomeSourceTotal(
                source = IncomeSource.FREELANCE,
                displayName = "Freelance",
                amountLKR = freelanceTotal,
                percentage = if (freelanceTotal > 0) ((freelanceTotal / positiveDenominator) * 100).toInt() else 0
            ),
            IncomeSourceTotal(
                source = IncomeSource.CRYPTO,
                displayName = "Crypto (Net P&L)",
                amountLKR = cryptoNetTotal,
                percentage = if (cryptoNetTotal > 0) ((cryptoNetTotal / positiveDenominator) * 100).toInt() else 0
            ),
            IncomeSourceTotal(
                source = IncomeSource.ADSENSE,
                displayName = "AdSense (USD)",
                amountLKR = adsenseTotal,
                percentage = if (adsenseTotal > 0) ((adsenseTotal / positiveDenominator) * 100).toInt() else 0
            ),
            IncomeSourceTotal(
                source = IncomeSource.OTHER,
                displayName = "Other Income",
                amountLKR = otherTotal,
                percentage = if (otherTotal > 0) ((otherTotal / positiveDenominator) * 100).toInt() else 0
            )
        )

        // Compare against Kavindu's estimate range: LKR 160,000 - 210,000
        val estimateMin = 160000.0
        val estimateMax = 210000.0
        val (comparisonStatus, diff) = when {
            reconciledActual < estimateMin -> Pair(EstimateComparisonStatus.BELOW_ESTIMATE, estimateMin - reconciledActual)
            reconciledActual > estimateMax -> Pair(EstimateComparisonStatus.ABOVE_ESTIMATE, reconciledActual - estimateMax)
            else -> Pair(EstimateComparisonStatus.WITHIN_ESTIMATE, 0.0)
        }

        val reconciliation = MonthlyReconciliation(
            monthLabel = if (isAllTime) "All-Time" else monthLabel,
            reconciledActualLKR = reconciledActual,
            estimateMinLKR = estimateMin,
            estimateMaxLKR = estimateMax,
            comparisonStatus = comparisonStatus,
            differenceLKR = diff,
            salaryLKR = salaryTotal,
            freelanceLKR = freelanceTotal,
            adsenseLKR = adsenseTotal,
            cryptoNetLKR = cryptoNetTotal,
            otherLKR = otherTotal,
            sourceBreakdown = sourceBreakdown
        )

        // Generate rolling 24 months (current month + past 23 months)
        val rollingMonths = (0 downTo -23).map { offset ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
            monthLabelFormat.format(c.time)
        }

        // Include any historical months present in income records
        val incomeMonths = allIncomes.map {
            monthLabelFormat.format(it.dateReceived ?: it.expectedDate ?: it.createdAt)
        }

        // Merge and sort chronologically descending (newest first)
        val allMonths = (rollingMonths + incomeMonths).distinct().sortedByDescending { label ->
            try {
                monthLabelFormat.parse(label)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        // Calculate confirmed income totals for each month
        val monthlyTotals = mutableMapOf<String, Double>()
        allMonths.forEach { label ->
            val (sMonth, eMonth) = getMonthDateRange(label, monthLabelFormat)
            val monthConfirmedSum = allIncomes.filter { income ->
                income.status == IncomeStatus.CONFIRMED &&
                    (income.dateReceived ?: income.expectedDate ?: income.createdAt).let { !it.before(sMonth) && !it.after(eMonth) }
            }.sumOf { it.amountLKR }
            monthlyTotals[label] = monthConfirmedSum
        }

        val lastSalary = allIncomes
            .filter { it.source == IncomeSource.SALARY }
            .maxByOrNull { it.dateReceived ?: it.createdAt }
            ?.amountOriginal

        // Incomes filtered by stream (if selectedSource != null)
        val streamIncomes = if (selectedSource != null) {
            periodIncomes.filter { it.source == selectedSource }
        } else {
            periodIncomes
        }

        val streamConfirmedLKR = streamIncomes.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        val streamPendingLKR = streamIncomes.filter { it.status == IncomeStatus.PENDING }.sumOf { it.amountLKR }

        val sortedStreamIncomes = sortIncomes(streamIncomes, _uiState.value.selectedSortOrder)
        val filtered = applyFilter(sortedStreamIncomes, _uiState.value.selectedFilter)

        _uiState.value = _uiState.value.copy(
            incomes = sortedStreamIncomes,
            filteredIncomes = filtered,
            totalConfirmedLKR = overallConfirmedTotal,
            totalPendingLKR = overallPendingTotal,
            streamConfirmedLKR = streamConfirmedLKR,
            streamPendingLKR = streamPendingLKR,
            lastSalaryAmount = lastSalary,
            selectedMonthLabel = monthLabel,
            availableMonths = allMonths,
            monthlyIncomeTotals = monthlyTotals,
            reconciliation = reconciliation,
            isLoading = false,
            errorMessage = null
        )

        // Prefill salary if user currently has salary selected and amount is default
        if (!_formState.value.isEditMode && _formState.value.source == IncomeSource.SALARY && lastSalary != null) {
            val formatted = String.format(Locale.US, "%.2f", lastSalary)
            if (_formState.value.amountOriginal.isBlank() || _formState.value.amountOriginal == "130000.00" || _formState.value.amountOriginal == "130000") {
                _formState.value = _formState.value.copy(amountOriginal = formatted)
            }
        }
    }

    private fun getMonthDateRange(monthLabel: String, monthFormat: SimpleDateFormat): Pair<Date, Date> {
        val cal = Calendar.getInstance()
        try {
            val parsed = monthFormat.parse(monthLabel)
            if (parsed != null) {
                cal.time = parsed
            }
        } catch (_: Exception) {}

        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.time

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfMonth = cal.time

        return Pair(startOfMonth, endOfMonth)
    }

    fun onSortOrderSelected(order: IncomeSortOrder) {
        _uiState.value = _uiState.value.copy(selectedSortOrder = order)
        recomputeState()
    }

    private fun sortIncomes(list: List<Income>, order: IncomeSortOrder): List<Income> {
        return when (order) {
            IncomeSortOrder.DATE_DESC -> list.sortedWith(
                compareByDescending<Income> { it.dateReceived ?: it.expectedDate ?: it.createdAt }
                    .thenByDescending { it.createdAt }
            )
            IncomeSortOrder.DATE_ASC -> list.sortedWith(
                compareBy<Income> { it.dateReceived ?: it.expectedDate ?: it.createdAt }
                    .thenBy { it.createdAt }
            )
            IncomeSortOrder.AMOUNT_DESC -> list.sortedWith(
                compareByDescending<Income> { it.amountLKR }
                    .thenByDescending { it.dateReceived ?: it.expectedDate ?: it.createdAt }
            )
            IncomeSortOrder.AMOUNT_ASC -> list.sortedWith(
                compareBy<Income> { it.amountLKR }
                    .thenByDescending { it.dateReceived ?: it.expectedDate ?: it.createdAt }
            )
        }
    }

    fun onSourceFilterSelected(source: String?) {
        _uiState.value = _uiState.value.copy(
            selectedSource = source,
            selectedFilter = IncomeFilter.ALL // When selecting a stream, default to ALL so they see both confirmed and pending
        )
        recomputeState()
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
                    val isCrypto = existing.source == IncomeSource.CRYPTO
                    val isLoss = isCrypto && existing.amountOriginal < 0
                    val (sourceTitle, noteBody) = if (existing.source == IncomeSource.OTHER && existing.note != null) {
                        if (existing.note.contains(" • ")) {
                            existing.note.substringBefore(" • ") to existing.note.substringAfter(" • ")
                        } else {
                            existing.note to ""
                        }
                    } else {
                        "" to (existing.note ?: "")
                    }
                    _formState.value = IncomeFormState(
                        editingIncomeId = existing.id,
                        originalCreatedAt = existing.createdAt,
                        source = existing.source,
                        status = existing.status,
                        amountOriginal = String.format(java.util.Locale.US, "%.2f", existing.amountOriginal),
                        currency = existing.currency,
                        exchangeRate = existing.exchangeRate.toString(),
                        isCryptoLoss = isLoss,
                        dateReceived = existing.dateReceived ?: existing.createdAt,
                        expectedDate = existing.expectedDate ?: Date(),
                        customSourceTitle = sourceTitle,
                        note = noteBody,
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
            IncomeSource.OTHER -> {
                // Other / Custom Income: Simple LKR form
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
            isCryptoLoss = false,
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
        val trimmed = amount.trim()
        val isCrypto = _formState.value.source == IncomeSource.CRYPTO
        val isLoss = if (isCrypto) {
            if (trimmed.startsWith("-")) true
            else _formState.value.isCryptoLoss
        } else false

        val normalizedAmount = if (isCrypto && isLoss && trimmed.isNotBlank() && !trimmed.startsWith("-")) {
            "-$trimmed"
        } else {
            amount
        }

        _formState.value = _formState.value.copy(
            amountOriginal = normalizedAmount,
            isCryptoLoss = isLoss,
            error = null
        )
    }

    fun onCryptoLossToggled(isLoss: Boolean) {
        val current = _formState.value.amountOriginal.trim()
        val updated = if (isLoss) {
            if (current.isNotBlank() && !current.startsWith("-")) "-$current" else current
        } else {
            if (current.startsWith("-")) current.removePrefix("-") else current
        }
        _formState.value = _formState.value.copy(
            isCryptoLoss = isLoss,
            amountOriginal = updated,
            error = null
        )
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

    fun onCustomSourceTitleChanged(title: String) {
        _formState.value = _formState.value.copy(customSourceTitle = title, error = null)
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
            exchangeRate = "1.0",
            isCryptoLoss = false,
            customSourceTitle = ""
        )
    }

    fun submitIncome(onSuccess: () -> Unit) {
        val userId = currentUserId
        if (userId == null) {
            _formState.value = _formState.value.copy(error = "User session expired. Please sign in again.")
            return
        }

        val state = _formState.value
        val rawAmount = state.amountOriginal.toDoubleOrNull()
        if (rawAmount == null) {
            _formState.value = _formState.value.copy(error = "Please enter a valid amount")
            return
        }

        val amount = if (state.source == IncomeSource.CRYPTO) {
            if (state.isCryptoLoss && rawAmount > 0) -rawAmount
            else if (!state.isCryptoLoss && rawAmount < 0) kotlin.math.abs(rawAmount)
            else rawAmount
        } else {
            rawAmount
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

        val finalNote = if (state.source == IncomeSource.OTHER) {
            when {
                state.customSourceTitle.isNotBlank() && state.note.isNotBlank() ->
                    "${state.customSourceTitle.trim()} • ${state.note.trim()}"
                state.customSourceTitle.isNotBlank() ->
                    state.customSourceTitle.trim()
                else ->
                    state.note.trim().takeIf { it.isNotBlank() }
            }
        } else {
            state.note.trim().takeIf { it.isNotBlank() }
        }

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
            note = finalNote,
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

    fun confirmIncome(income: Income, dateReceived: Date = Date(), onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val updated = income.copy(
                status = IncomeStatus.CONFIRMED,
                dateReceived = dateReceived,
                expectedDate = null,
                updatedAt = Date()
            )
            val error = updated.validate()
            if (error != null) {
                _uiState.value = _uiState.value.copy(errorMessage = error)
                return@launch
            }
            try {
                incomeRepository.updateIncome(updated)
                onComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.localizedMessage ?: "Failed to confirm income"
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
