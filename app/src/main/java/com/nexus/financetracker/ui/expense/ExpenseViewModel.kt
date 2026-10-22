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
import java.util.Date
import javax.inject.Inject

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val totalExpenseLKR: Double = 0.0,
    val cardExpenseLKR: Double = 0.0,
    val cashExpenseLKR: Double = 0.0,
    val secondaryExpenseLKR: Double = 0.0,
    val committedExpenseLKR: Double = 0.0,
    val discretionaryExpenseLKR: Double = 0.0,
    val categoriesMap: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class ExpenseFormState(
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
    val computedAmountLKR: Double?
        get() = amountText.toDoubleOrNull()
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

    val currentUserId: String?
        get() = auth.currentUser?.uid

    init {
        loadExpenses()
        loadCategoriesAndDefaults()
    }

    /**
     * Observes real-time expenses from Firestore and updates uiState.
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

    private fun recomputeUiState() {
        val expenses = rawExpenses
        val total = expenses.sumOf { it.amountLKR }
        val card = expenses.filter { it.account.equals(ExpenseAccount.CARD, ignoreCase = true) }.sumOf { it.amountLKR }
        val cash = expenses.filter { it.account.equals(ExpenseAccount.CASH, ignoreCase = true) }.sumOf { it.amountLKR }
        val secondary = expenses.filter { it.account.equals(ExpenseAccount.SECONDARY, ignoreCase = true) }.sumOf { it.amountLKR }
        val committed = expenses.filter { it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }.sumOf { it.amountLKR }
        val discretionary = expenses.filter { it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true) }.sumOf { it.amountLKR }

        _uiState.value = _uiState.value.copy(
            expenses = expenses,
            totalExpenseLKR = total,
            cardExpenseLKR = card,
            cashExpenseLKR = cash,
            secondaryExpenseLKR = secondary,
            committedExpenseLKR = committed,
            discretionaryExpenseLKR = discretionary,
            isLoading = false,
            errorMessage = null
        )
    }

    /**
     * Initializes the form with fast entry defaults:
     * - Today's date (FR9/FR10)
     * - Last-used category and account from the user's latest expense entry (FR9/FR10)
     * - Available user or default categories
     */
    fun loadCategoriesAndDefaults() {
        val userId = currentUserId ?: return

        viewModelScope.launch {
            _formState.value = _formState.value.copy(isLoadingDefaults = true)

            // 1. Observe / fetch categories
            categoryRepository.getCategories(userId)
                .catch { emit(emptyList()) }
                .collect { customCats ->
                    val combined = if (customCats.isNotEmpty()) {
                        customCats
                    } else {
                        DEFAULT_EXPENSE_CATEGORIES
                    }

                    val catMap = combined.associate { it.id to it.name } +
                            DEFAULT_EXPENSE_CATEGORIES.associate { it.id to it.name }

                    _uiState.value = _uiState.value.copy(categoriesMap = catMap)

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
                        date = Date(), // Today's date default
                        classification = latestExpense?.classification ?: ExpenseClassification.DISCRETIONARY,
                        isLoadingDefaults = false
                    )
                }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(expenseId)
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
            amountText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(java.util.Locale.US, "%.2f", next),
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

    fun onNoteChanged(note: String) {
        _formState.value = _formState.value.copy(note = note)
    }

    fun resetForm() {
        val currentCats = _formState.value.availableCategories
        _formState.value = _formState.value.copy(
            amountText = "",
            date = Date(),
            note = "",
            isSubmitting = false,
            errorMessage = null
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

        val expense = Expense(
            userId = userId,
            amountLKR = amount,
            categoryId = state.categoryId,
            account = state.account,
            classification = state.classification,
            note = state.note.trim().takeIf { it.isNotBlank() },
            date = state.date,
            createdAt = Date()
        )

        val validationError = expense.validate()
        if (validationError != null) {
            _formState.value = _formState.value.copy(errorMessage = validationError)
            return
        }

        viewModelScope.launch {
            _formState.value = _formState.value.copy(isSubmitting = true, errorMessage = null)
            try {
                expenseRepository.addExpense(expense)
                _formState.value = _formState.value.copy(isSubmitting = false)
                resetForm()
                onSuccess()
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isSubmitting = false,
                    errorMessage = e.localizedMessage ?: "Failed to record expense. Please try again."
                )
            }
        }
    }
}
