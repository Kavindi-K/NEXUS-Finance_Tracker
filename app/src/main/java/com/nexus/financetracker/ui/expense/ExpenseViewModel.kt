package com.nexus.financetracker.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.CategoryRepository
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
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

enum class ExpenseSortOrder {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC
}

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val filteredExpenses: List<Expense> = emptyList(),
    val selectedSortOrder: ExpenseSortOrder = ExpenseSortOrder.DATE_DESC,
    val selectedAccountFilter: String? = null,
    val selectedCategoryFilter: String? = null,
    val selectedMonthLabel: String = "",
    val availableMonths: List<String> = emptyList(),
    val monthlyExpenseTotals: Map<String, Double> = emptyMap(),
    val isPeriodAllTime: Boolean = false,
    val totalExpenseLKR: Double = 0.0,
    val overallExpenseLKR: Double = 0.0,
    val cardExpenseLKR: Double = 0.0,
    val cashExpenseLKR: Double = 0.0,
    val secondaryExpenseLKR: Double = 0.0,
    val cardExpensePercentage: Double = 0.0,
    val cashExpensePercentage: Double = 0.0,
    val secondaryExpensePercentage: Double = 0.0,
    val committedExpenseLKR: Double = 0.0,
    val discretionaryExpenseLKR: Double = 0.0,
    val categoriesMap: Map<String, String> = emptyMap(),
    val customCategories: List<Category> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class ExpenseFormState(
    val editingExpenseId: String? = null,
    val originalCreatedAt: Date? = null,
    val amountText: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val account: String = ExpenseAccount.CARD,
    val classification: String = ExpenseClassification.DISCRETIONARY,
    val date: Date = Date(),
    val note: String = "",
    val isSubmitting: Boolean = false,
    val isLoadingDefaults: Boolean = false,
    val errorMessage: String? = null,
    val availableCategories: List<Category> = emptyList()
) {
    val isEditMode: Boolean
        get() = !editingExpenseId.isNullOrBlank()

    val computedAmountLKR: Double?
        get() = amountText.toDoubleOrNull()

    val isBackdated: Boolean
        get() {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return date.before(cal.time)
        }
}

/**
 * Representation of an individual item in a bulk expense entry batch.
 */
data class BulkExpenseItem(
    val tempId: String = java.util.UUID.randomUUID().toString(),
    val amountText: String = "",
    val categoryId: String = "cat_food",
    val account: String = ExpenseAccount.CARD,
    val classification: String = ExpenseClassification.DISCRETIONARY,
    val date: Date = Date(),
    val note: String = ""
) {
    val amountLKR: Double? get() = amountText.toDoubleOrNull()
    val isValid: Boolean get() = (amountLKR ?: 0.0) > 0.0 && categoryId.isNotBlank()
}

/**
 * Standard default categories provided out-of-the-box for low-friction expense recording (FR9/FR10).
 */
val DEFAULT_EXPENSE_CATEGORIES = listOf(
    Category(
        id = "cat_food",
        name = "Food & Dining",
        classification = ExpenseClassification.DISCRETIONARY,
        estimatedAmount = 25000.0
    ),
    Category(
        id = "cat_transport",
        name = "Transport",
        classification = ExpenseClassification.DISCRETIONARY,
        estimatedAmount = 15000.0
    ),
    Category(
        id = "cat_entertainment",
        name = "Subscriptions",
        classification = ExpenseClassification.COMMITTED,
        recurringFrequency = "Monthly"
    ),
    Category(
        id = "cat_rent",
        name = "Rent",
        classification = ExpenseClassification.COMMITTED,
        recurringFrequency = "Monthly"
    ),
    Category(
        id = "cat_health",
        name = "Health",
        classification = ExpenseClassification.COMMITTED,
        recurringFrequency = "Monthly"
    ),
    Category(
        id = "cat_shopping",
        name = "Shopping",
        classification = ExpenseClassification.DISCRETIONARY,
        estimatedAmount = 10000.0
    ),
    Category(
        id = "cat_groceries",
        name = "Groceries",
        classification = ExpenseClassification.DISCRETIONARY,
        estimatedAmount = 20000.0
    ),
    Category(
        id = "cat_utilities",
        name = "Bills & Utilities",
        classification = ExpenseClassification.COMMITTED,
        recurringFrequency = "Monthly"
    ),
    Category(
        id = "cat_other",
        name = "Other",
        classification = ExpenseClassification.DISCRETIONARY
    )
)

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(ExpenseFormState())
    val formState: StateFlow<ExpenseFormState> = _formState.asStateFlow()

    private var rawExpenses: List<Expense> = emptyList()
    private var selectedCalendar: Calendar = Calendar.getInstance()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    init {
        loadExpenses()
        loadCategoriesAndDefaults()
    }

    /**
     * Observes real-time expenses from Firestore/Room cache and updates uiState.
     */
    fun loadExpenses() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            expenseRepository.getExpenses(userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load expenses"
                    )
                }
                .collect { list ->
                    rawExpenses = list
                    recomputeUiState()
                }
        }
    }

    // --- Period Selection & History Navigation ---

    fun onPreviousMonthClicked() {
        selectedCalendar.add(Calendar.MONTH, -1)
        if (_uiState.value.isPeriodAllTime) {
            _uiState.value = _uiState.value.copy(isPeriodAllTime = false)
        }
        recomputeUiState()
    }

    fun onNextMonthClicked() {
        selectedCalendar.add(Calendar.MONTH, 1)
        if (_uiState.value.isPeriodAllTime) {
            _uiState.value = _uiState.value.copy(isPeriodAllTime = false)
        }
        recomputeUiState()
    }

    fun onCurrentMonthClicked() {
        selectedCalendar = Calendar.getInstance()
        _uiState.value = _uiState.value.copy(isPeriodAllTime = false)
        recomputeUiState()
    }

    fun onToggleAllTimePeriod(allTime: Boolean) {
        _uiState.value = _uiState.value.copy(isPeriodAllTime = allTime)
        recomputeUiState()
    }

    fun selectMonth(monthLabel: String) {
        if (monthLabel.equals("All Time", ignoreCase = true) || monthLabel.equals("All Time History", ignoreCase = true)) {
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
        recomputeUiState()
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

    // --- Sorting and Filtering ---

    fun onSortOrderSelected(order: ExpenseSortOrder) {
        _uiState.value = _uiState.value.copy(selectedSortOrder = order)
        recomputeUiState()
    }

    fun onAccountFilterSelected(account: String?) {
        _uiState.value = _uiState.value.copy(selectedAccountFilter = account)
        recomputeUiState()
    }

    fun onCategoryFilterSelected(categoryId: String?) {
        _uiState.value = _uiState.value.copy(selectedCategoryFilter = categoryId)
        recomputeUiState()
    }

    private fun recomputeUiState() {
        val allExpenses = rawExpenses
        val isAllTime = _uiState.value.isPeriodAllTime

        val monthLabelFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        val monthLabel = monthLabelFormat.format(selectedCalendar.time)

        // Month boundary calculation
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

        // Period filter (Month vs All-time)
        val periodExpenses = if (isAllTime) {
            allExpenses
        } else {
            allExpenses.filter { expense ->
                !expense.date.before(startOfMonth) && !expense.date.after(endOfMonth)
            }
        }

        // Account filter
        val selectedAccount = _uiState.value.selectedAccountFilter
        val accountFiltered = if (selectedAccount.isNullOrBlank()) {
            periodExpenses
        } else {
            periodExpenses.filter { it.account.equals(selectedAccount, ignoreCase = true) }
        }

        // Category filter
        val selectedCategory = _uiState.value.selectedCategoryFilter
        val categoryFiltered = if (selectedCategory.isNullOrBlank()) {
            accountFiltered
        } else {
            accountFiltered.filter { it.categoryId == selectedCategory }
        }

        // Apply sorting
        val sorted = when (_uiState.value.selectedSortOrder) {
            ExpenseSortOrder.DATE_DESC -> categoryFiltered.sortedWith(
                compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt }
            )
            ExpenseSortOrder.DATE_ASC -> categoryFiltered.sortedWith(
                compareBy<Expense> { it.date }.thenBy { it.createdAt }
            )
            ExpenseSortOrder.AMOUNT_DESC -> categoryFiltered.sortedWith(
                compareByDescending<Expense> { it.amountLKR }.thenByDescending { it.date }
            )
            ExpenseSortOrder.AMOUNT_ASC -> categoryFiltered.sortedWith(
                compareBy<Expense> { it.amountLKR }.thenByDescending { it.date }
            )
        }

        val total = periodExpenses.sumOf { it.amountLKR }
        val overallTotal = allExpenses.sumOf { it.amountLKR }
        val card = periodExpenses.filter { it.account.equals(ExpenseAccount.CARD, ignoreCase = true) }.sumOf { it.amountLKR }
        val cash = periodExpenses.filter { it.account.equals(ExpenseAccount.CASH, ignoreCase = true) }.sumOf { it.amountLKR }
        val secondary = periodExpenses.filter { it.account.equals(ExpenseAccount.SECONDARY, ignoreCase = true) }.sumOf { it.amountLKR }
        val committed = periodExpenses.filter { it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }.sumOf { it.amountLKR }
        val discretionary = periodExpenses.filter { it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true) }.sumOf { it.amountLKR }

        val cardPct = if (total > 0.0) (card / total) * 100.0 else 0.0
        val cashPct = if (total > 0.0) (cash / total) * 100.0 else 0.0
        val secondaryPct = if (total > 0.0) (secondary / total) * 100.0 else 0.0

        // Generate rolling 24 months (current month + past 23 months)
        val rollingMonths = (0 downTo -23).map { offset ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
            monthLabelFormat.format(c.time)
        }

        // Include any historical months present in expense records
        val expenseMonths = allExpenses.map {
            monthLabelFormat.format(it.date)
        }

        // Merge and sort chronologically descending (newest first)
        val allMonths = (rollingMonths + expenseMonths).distinct().sortedByDescending { label ->
            try {
                monthLabelFormat.parse(label)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        // Calculate total expenses for each month
        val monthlyTotals = mutableMapOf<String, Double>()
        allMonths.forEach { label ->
            val (sMonth, eMonth) = getMonthDateRange(label, monthLabelFormat)
            val monthSum = allExpenses.filter { expense ->
                !expense.date.before(sMonth) && !expense.date.after(eMonth)
            }.sumOf { it.amountLKR }
            monthlyTotals[label] = monthSum
        }

        _uiState.value = _uiState.value.copy(
            expenses = periodExpenses,
            filteredExpenses = sorted,
            selectedMonthLabel = if (isAllTime) "All Time History" else monthLabel,
            availableMonths = allMonths,
            monthlyExpenseTotals = monthlyTotals,
            totalExpenseLKR = total,
            overallExpenseLKR = overallTotal,
            cardExpenseLKR = card,
            cashExpenseLKR = cash,
            secondaryExpenseLKR = secondary,
            cardExpensePercentage = cardPct,
            cashExpensePercentage = cashPct,
            secondaryExpensePercentage = secondaryPct,
            committedExpenseLKR = committed,
            discretionaryExpenseLKR = discretionary,
            isLoading = false,
            errorMessage = null
        )
    }

    /**
     * Loads categories and fast entry defaults (FR9/FR10).
     */
    fun loadCategoriesAndDefaults() {
        val userId = currentUserId ?: return

        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoadingDefaults = true)

            // 1. Observe / fetch categories
            categoryRepository.getCategories(userId)
                .catch { emit(emptyList()) }
                .collect { customCats ->
                    val combined = (DEFAULT_EXPENSE_CATEGORIES + customCats).distinctBy { it.id }
                    val catMap = combined.associate { it.id to it.name }

                    _uiState.value = _uiState.value.copy(
                        categoriesMap = catMap,
                        customCategories = customCats,
                        allCategories = combined
                    )

                    if (!_formState.value.isEditMode) {
                        // 2. Fetch latest expense to pre-populate last-used category & account (FR9/FR10)
                        val latestExpense = try {
                            expenseRepository.getLatestExpense(userId)
                        } catch (e: Exception) {
                            null
                        }

                        val defaultAccount = latestExpense?.account ?: ExpenseAccount.CARD
                        val currentSelectedId = _formState.value.categoryId
                        val hasUserSelection = currentSelectedId.isNotBlank() && currentSelectedId != "cat_food"

                        val defaultCatId = if (hasUserSelection) {
                            currentSelectedId
                        } else {
                            latestExpense?.categoryId ?: combined.firstOrNull()?.id ?: "cat_food"
                        }
                        val defaultCatName = catMap[defaultCatId]
                            ?: combined.firstOrNull()?.name ?: "Food & Dining"
                        val matchingCat = combined.find { it.id == defaultCatId }
                        val defaultClassification = matchingCat?.classification
                            ?: latestExpense?.classification
                            ?: ExpenseClassification.DISCRETIONARY

                        _formState.value = _formState.value.copy(
                            availableCategories = combined,
                            account = if (_formState.value.amountText.isNotBlank()) _formState.value.account else defaultAccount,
                            categoryId = defaultCatId,
                            categoryName = defaultCatName,
                            date = if (_formState.value.amountText.isNotBlank()) _formState.value.date else Date(),
                            classification = defaultClassification,
                            isLoadingDefaults = false
                        )
                    } else {
                        _formState.value = _formState.value.copy(
                            availableCategories = combined,
                            isLoadingDefaults = false
                        )
                    }
                }
        }
    }

    /**
     * Loads an existing expense into the form state for editing.
     */
    fun loadExpenseForEdit(expenseId: String) {
        if (expenseId.isBlank() || expenseId == "{expenseId}") return
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoadingDefaults = true, errorMessage = null)
            val existing = rawExpenses.find { it.id == expenseId }
                ?: try { expenseRepository.getExpenseById(expenseId) } catch (e: Exception) { null }

            if (existing != null) {
                val catMap = _uiState.value.categoriesMap
                val catName = catMap[existing.categoryId]
                    ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == existing.categoryId }?.name
                    ?: existing.categoryId

                _formState.value = _formState.value.copy(
                    editingExpenseId = existing.id,
                    originalCreatedAt = existing.createdAt,
                    amountText = if (existing.amountLKR % 1.0 == 0.0) {
                        existing.amountLKR.toLong().toString()
                    } else {
                        String.format(Locale.US, "%.2f", existing.amountLKR)
                    },
                    categoryId = existing.categoryId,
                    categoryName = catName,
                    account = existing.account,
                    classification = existing.classification,
                    date = existing.date,
                    note = existing.note ?: "",
                    isLoadingDefaults = false,
                    isSubmitting = false,
                    errorMessage = null
                )
            } else {
                _formState.value = _formState.value.copy(
                    isLoadingDefaults = false,
                    errorMessage = "Expense record not found"
                )
            }
        }
    }

    fun deleteExpense(expenseId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(expenseId)
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.localizedMessage ?: "Failed to delete expense"
                )
            }
        }
    }

    fun onAmountChanged(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        _formState.value = _formState.value.copy(amountText = filtered, errorMessage = null)
    }

    fun onQuickAmountAdd(increment: Double) {
        val current = _formState.value.amountText.toDoubleOrNull() ?: 0.0
        val next = current + increment
        _formState.value = _formState.value.copy(
            amountText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(Locale.US, "%.2f", next),
            errorMessage = null
        )
    }

    fun onCategorySelected(category: Category) {
        _formState.value = _formState.value.copy(
            categoryId = category.id,
            categoryName = category.name,
            errorMessage = null
        )
    }

    // --- Category Management (Create, Rename, Merge, Delete) ---

    fun createCategory(
        name: String,
        onSuccess: (Category) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val userId = currentUserId ?: run {
            onError("User not signed in")
            return
        }
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onError("Category name cannot be empty")
            return
        }
        if (trimmed.length > 40) {
            onError("Category name cannot exceed 40 characters")
            return
        }
        val newCategory = Category(
            userId = userId,
            name = trimmed,
            createdAt = Date()
        )
        viewModelScope.launch {
            try {
                val newId = categoryRepository.addCategory(newCategory)
                val created = newCategory.copy(id = newId)
                val updatedCustom = (_uiState.value.customCategories + created).distinctBy { it.id }
                val updatedAll = (DEFAULT_EXPENSE_CATEGORIES + updatedCustom).distinctBy { it.id }
                val updatedMap = updatedAll.associate { it.id to it.name }
                _uiState.value = _uiState.value.copy(
                    customCategories = updatedCustom,
                    allCategories = updatedAll,
                    categoriesMap = updatedMap
                )
                _formState.value = _formState.value.copy(
                    availableCategories = updatedAll
                )
                onCategorySelected(created)
                onSuccess(created)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to create category")
            }
        }
    }

    fun createCategoryWithDetails(
        name: String,
        classification: String = ExpenseClassification.DISCRETIONARY,
        estimatedAmount: Double? = null,
        recurringFrequency: String? = null,
        onSuccess: (Category) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val userId = currentUserId ?: run {
            onError("User not signed in")
            return
        }
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onError("Category name cannot be empty")
            return
        }
        if (trimmed.length > 40) {
            onError("Category name cannot exceed 40 characters")
            return
        }
        val newCategory = Category(
            userId = userId,
            name = trimmed,
            classification = classification,
            estimatedAmount = if (classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true)) estimatedAmount else null,
            recurringFrequency = if (classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)) recurringFrequency else null,
            createdAt = Date()
        )
        viewModelScope.launch {
            try {
                val newId = categoryRepository.addCategory(newCategory)
                val created = newCategory.copy(id = newId)
                val updatedCustom = (_uiState.value.customCategories + created).distinctBy { it.id }
                val updatedAll = (DEFAULT_EXPENSE_CATEGORIES + updatedCustom).distinctBy { it.id }
                val updatedMap = updatedAll.associate { it.id to it.name }
                _uiState.value = _uiState.value.copy(
                    customCategories = updatedCustom,
                    allCategories = updatedAll,
                    categoriesMap = updatedMap
                )
                _formState.value = _formState.value.copy(
                    availableCategories = updatedAll
                )
                onCategorySelected(created)
                onClassificationSelected(created.classification)
                onSuccess(created)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to create category")
            }
        }
    }

    fun renameCategory(
        categoryId: String,
        newName: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            onError("Category name cannot be empty")
            return
        }
        if (trimmed.length > 40) {
            onError("Category name cannot exceed 40 characters")
            return
        }
        viewModelScope.launch {
            try {
                categoryRepository.renameCategory(categoryId, trimmed)
                if (_formState.value.categoryId == categoryId) {
                    _formState.value = _formState.value.copy(categoryName = trimmed)
                }
                recomputeUiState()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to rename category")
            }
        }
    }

    fun mergeCategories(
        sourceCategoryId: String,
        targetCategoryId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (sourceCategoryId == targetCategoryId) {
            onError("Cannot merge a category into itself")
            return
        }
        viewModelScope.launch {
            try {
                categoryRepository.mergeCategories(sourceCategoryId, targetCategoryId)
                if (_formState.value.categoryId == sourceCategoryId) {
                    val targetName = _uiState.value.categoriesMap[targetCategoryId] ?: targetCategoryId
                    _formState.value = _formState.value.copy(
                        categoryId = targetCategoryId,
                        categoryName = targetName
                    )
                }
                if (_uiState.value.selectedCategoryFilter == sourceCategoryId) {
                    _uiState.value = _uiState.value.copy(selectedCategoryFilter = targetCategoryId)
                }
                loadExpenses()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to merge categories")
            }
        }
    }

    fun deleteCategory(
        categoryId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                categoryRepository.deleteCategory(categoryId)
                if (_uiState.value.selectedCategoryFilter == categoryId) {
                    _uiState.value = _uiState.value.copy(selectedCategoryFilter = null)
                }
                loadExpenses()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to delete category")
            }
        }
    }

    fun onAccountSelected(account: String) {
        if (account in ExpenseAccount.ALL) {
            _formState.value = _formState.value.copy(account = account, errorMessage = null)
        }
    }

    fun onClassificationSelected(classification: String) {
        if (classification in ExpenseClassification.ALL) {
            _formState.value = _formState.value.copy(classification = classification, errorMessage = null)
        }
    }

    fun onDateSelected(date: Date) {
        _formState.value = _formState.value.copy(date = date, errorMessage = null)
    }

    /**
     * Quick preset for back-dating (e.g. 0 = Today, 1 = Yesterday, 2 = 2 days ago).
     */
    fun onQuickDateSelected(daysAgo: Int) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        onDateSelected(cal.time)
    }

    /**
     * Jump the month navigation in the history screen to match the backdated expense's month.
     */
    fun navigateToExpenseMonth(date: Date) {
        selectedCalendar.time = date
        _uiState.value = _uiState.value.copy(isPeriodAllTime = false)
        recomputeUiState()
    }

    /**
     * Submits a list of missed expenses in bulk using atomic Firestore batch write and Room insertAll.
     * Conforms to Member 2 Order 6 (feature/expense-backdate).
     */
    fun submitBulkExpenses(
        items: List<BulkExpenseItem>,
        onSuccess: (count: Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            onError("User session expired. Please sign in again.")
            return
        }

        val validItems = items.filter { it.isValid }
        if (validItems.isEmpty()) {
            onError("Please enter at least one valid expense item with an amount greater than 0")
            return
        }

        val now = Date()
        val expenses = validItems.map { item ->
            Expense(
                userId = userId,
                amountLKR = item.amountLKR ?: 0.0,
                categoryId = item.categoryId,
                account = item.account,
                classification = item.classification,
                note = item.note.trim().takeIf { it.isNotBlank() },
                date = item.date, // Actual transaction date (back-dated)
                createdAt = now   // Current system timestamp
            )
        }

        viewModelScope.launch {
            try {
                expenseRepository.addExpenses(expenses)
                loadExpenses()
                onSuccess(expenses.size)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to record bulk expenses. Please try again.")
            }
        }
    }

    fun onNoteChanged(note: String) {
        _formState.value = _formState.value.copy(note = note)
    }

    fun resetForm(preferredCategoryId: String? = null) {
        val currentCats = if (_uiState.value.allCategories.isNotEmpty()) {
            _uiState.value.allCategories
        } else if (_formState.value.availableCategories.isNotEmpty()) {
            _formState.value.availableCategories
        } else {
            DEFAULT_EXPENSE_CATEGORIES
        }
        val targetCat = if (!preferredCategoryId.isNullOrBlank() && preferredCategoryId != "{categoryId}") {
            currentCats.find { it.id == preferredCategoryId }
                ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == preferredCategoryId }
                ?: currentCats.first()
        } else {
            currentCats.first()
        }
        _formState.value = ExpenseFormState(
            editingExpenseId = null,
            originalCreatedAt = null,
            amountText = "",
            categoryId = targetCat.id,
            categoryName = targetCat.name,
            account = ExpenseAccount.CARD,
            classification = targetCat.classification,
            date = Date(),
            note = "",
            isSubmitting = false,
            errorMessage = null,
            availableCategories = currentCats
        )
    }

    fun submitExpense(onSuccess: () -> Unit) {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            _formState.value = _formState.value.copy(errorMessage = "User session expired. Please sign in again.")
            return
        }

        val state = _formState.value
        val amount = state.amountText.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _formState.value = _formState.value.copy(errorMessage = "Please enter a valid expense amount greater than 0")
            return
        }

        if (state.categoryId.isBlank()) {
            _formState.value = _formState.value.copy(errorMessage = "Please select an expense category")
            return
        }

        val isEdit = state.isEditMode
        val expense = Expense(
            id = state.editingExpenseId ?: "",
            userId = userId,
            amountLKR = amount,
            categoryId = state.categoryId,
            account = state.account,
            classification = state.classification,
            note = state.note.trim().takeIf { it.isNotBlank() },
            date = state.date,
            createdAt = state.originalCreatedAt ?: Date()
        )

        val validationError = expense.validate()
        if (validationError != null) {
            _formState.value = _formState.value.copy(errorMessage = validationError)
            return
        }

        viewModelScope.launch {
            _formState.value = _formState.value.copy(isSubmitting = true, errorMessage = null)
            try {
                if (isEdit) {
                    // Update existing record — produces no duplicates!
                    expenseRepository.updateExpense(expense)
                } else {
                    // Insert new record
                    expenseRepository.addExpense(expense)
                }
                _formState.value = _formState.value.copy(isSubmitting = false)
                resetForm()
                onSuccess()
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isSubmitting = false,
                    errorMessage = e.localizedMessage ?: "Failed to save expense. Please try again."
                )
            }
        }
    }
}
