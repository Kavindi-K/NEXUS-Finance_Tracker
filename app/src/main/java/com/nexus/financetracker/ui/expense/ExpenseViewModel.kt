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
    val selectedMonthLabel: String = "",
    val isPeriodAllTime: Boolean = false,
    val totalExpenseLKR: Double = 0.0,
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
    Category(id = "cat_food", name = "Food & Dining"),
    Category(id = "cat_groceries", name = "Groceries"),
    Category(id = "cat_transport", name = "Transport"),
    Category(id = "cat_utilities", name = "Bills & Utilities"),
    Category(id = "cat_rent", name = "Rent & Housing"),
    Category(id = "cat_shopping", name = "Shopping"),
    Category(id = "cat_entertainment", name = "Entertainment"),
    Category(id = "cat_health", name = "Health & Medical"),
    Category(id = "cat_other", name = "Other")
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

    // --- Sorting and Filtering ---

    fun onSortOrderSelected(order: ExpenseSortOrder) {
        _uiState.value = _uiState.value.copy(selectedSortOrder = order)
        recomputeUiState()
    }

    fun onAccountFilterSelected(account: String?) {
        _uiState.value = _uiState.value.copy(selectedAccountFilter = account)
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

        // Apply sorting
        val sorted = when (_uiState.value.selectedSortOrder) {
            ExpenseSortOrder.DATE_DESC -> accountFiltered.sortedWith(
                compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt }
            )
            ExpenseSortOrder.DATE_ASC -> accountFiltered.sortedWith(
                compareBy<Expense> { it.date }.thenBy { it.createdAt }
            )
            ExpenseSortOrder.AMOUNT_DESC -> accountFiltered.sortedWith(
                compareByDescending<Expense> { it.amountLKR }.thenByDescending { it.date }
            )
            ExpenseSortOrder.AMOUNT_ASC -> accountFiltered.sortedWith(
                compareBy<Expense> { it.amountLKR }.thenByDescending { it.date }
            )
        }

        val total = periodExpenses.sumOf { it.amountLKR }
        val card = periodExpenses.filter { it.account.equals(ExpenseAccount.CARD, ignoreCase = true) }.sumOf { it.amountLKR }
        val cash = periodExpenses.filter { it.account.equals(ExpenseAccount.CASH, ignoreCase = true) }.sumOf { it.amountLKR }
        val secondary = periodExpenses.filter { it.account.equals(ExpenseAccount.SECONDARY, ignoreCase = true) }.sumOf { it.amountLKR }
        val committed = periodExpenses.filter { it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }.sumOf { it.amountLKR }
        val discretionary = periodExpenses.filter { it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true) }.sumOf { it.amountLKR }

        val cardPct = if (total > 0.0) (card / total) * 100.0 else 0.0
        val cashPct = if (total > 0.0) (cash / total) * 100.0 else 0.0
        val secondaryPct = if (total > 0.0) (secondary / total) * 100.0 else 0.0

        _uiState.value = _uiState.value.copy(
            expenses = periodExpenses,
            filteredExpenses = sorted,
            selectedMonthLabel = if (isAllTime) "All Time History" else monthLabel,
            totalExpenseLKR = total,
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
                        val defaultCatId = latestExpense?.categoryId ?: combined.firstOrNull()?.id ?: "cat_food"
                        val defaultCatName = catMap[defaultCatId]
                            ?: combined.firstOrNull()?.name ?: "Food & Dining"

                        _formState.value = _formState.value.copy(
                            availableCategories = combined,
                            account = defaultAccount,
                            categoryId = defaultCatId,
                            categoryName = defaultCatName,
                            date = Date(),
                            classification = latestExpense?.classification ?: ExpenseClassification.DISCRETIONARY,
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
                onCategorySelected(created)
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

    fun resetForm() {
        val currentCats = _formState.value.availableCategories
        val defaultCat = currentCats.firstOrNull() ?: DEFAULT_EXPENSE_CATEGORIES.first()
        _formState.value = ExpenseFormState(
            editingExpenseId = null,
            originalCreatedAt = null,
            amountText = "",
            categoryId = defaultCat.id,
            categoryName = defaultCat.name,
            account = ExpenseAccount.CARD,
            classification = ExpenseClassification.DISCRETIONARY,
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
