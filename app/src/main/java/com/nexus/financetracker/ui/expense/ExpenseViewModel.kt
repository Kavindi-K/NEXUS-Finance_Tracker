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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

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

    private val _formState = MutableStateFlow(ExpenseFormState())
    val formState: StateFlow<ExpenseFormState> = _formState.asStateFlow()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    init {
        loadCategoriesAndDefaults()
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
                    val resolvedCategories = if (customCats.isNotEmpty()) {
                        customCats
                    } else {
                        DEFAULT_EXPENSE_CATEGORIES
                    }

                    // 2. Fetch latest expense to pre-populate last-used category & account (FR9/FR10)
                    val latestExpense = try {
                        expenseRepository.getLatestExpense(userId)
                    } catch (e: Exception) {
                        null
                    }

                    val defaultAccount = latestExpense?.account ?: ExpenseAccount.CARD
                    val defaultCatId = latestExpense?.categoryId ?: resolvedCategories.firstOrNull()?.id ?: "cat_food"
                    val defaultCatName = resolvedCategories.find { it.id == defaultCatId }?.name
                        ?: resolvedCategories.firstOrNull()?.name ?: "Food & Dining"

                    _formState.value = _formState.value.copy(
                        availableCategories = resolvedCategories,
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
        val fallbackCat = currentCats.firstOrNull()
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
