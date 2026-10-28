package com.nexus.financetracker.ui.expense

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// App Theme Colors (Matching Deep Sapphire Navy Design System)
private val NavyPrimary = Color(0xFF072044)
private val LightCardBg = Color(0xFFF1F5F9)
private val TextSubtle = Color(0xFF64748B)
private val CardBorderColor = Color(0xFFCBD5E1)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseAddScreen(
    viewModel: ExpenseViewModel,
    expenseId: String? = null,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(expenseId) {
        if (!expenseId.isNullOrBlank() && expenseId != "{expenseId}") {
            viewModel.loadExpenseForEdit(expenseId)
        } else {
            viewModel.resetForm()
        }
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var categoryDialogError by remember { mutableStateOf<String?>(null) }

    val calendar = remember { Calendar.getInstance() }

    fun showDatePicker(initialDate: Date, onDateSelected: (Date) -> Unit) {
        calendar.time = initialDate
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(year, month, dayOfMonth, 12, 0, 0)
                onDateSelected(selectedCal.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val screenTitle = if (formState.isEditMode) "Edit Expense" else "Record Expense"
    val submitButtonText = if (formState.isEditMode) "Update Expense" else "Save Expense"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screenTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = {
                            viewModel.submitExpense(onSuccess = onNavigateBack)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !formState.isSubmitting && !formState.isLoadingDefaults,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (formState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = submitButtonText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        if (formState.isLoadingDefaults) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NavyPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                // Amount Input Field
                OutlinedTextField(
                    value = formState.amountText,
                    onValueChange = viewModel::onAmountChanged,
                    label = { Text("Amount (LKR)") },
                    prefix = { Text("LKR ", fontWeight = FontWeight.Bold, color = NavyPrimary) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyPrimary,
                        focusedLabelColor = NavyPrimary,
                        cursorColor = NavyPrimary
                    ),
                    supportingText = {
                        formState.computedAmountLKR?.let { lkr ->
                            Text("Formatted: LKR %,.2f".format(lkr), color = NavyPrimary, fontWeight = FontWeight.Medium)
                        } ?: Text("Enter expense amount in LKR", color = TextSubtle)
                    }
                )

                // Quick Increment Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(500.0, 1000.0, 2500.0, 5000.0).forEach { increment ->
                        SuggestionChip(
                            onClick = { viewModel.onQuickAmountAdd(increment) },
                            label = { Text("+%,d".format(increment.toInt()), fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorderColor),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = LightCardBg,
                                labelColor = NavyPrimary
                            )
                        )
                    }
                }

                // Category Selection
                Text(
                    text = "Category",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    formState.availableCategories.forEach { category ->
                        val isSelected = formState.categoryId == category.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onCategorySelected(category) },
                            label = {
                                Text(
                                    category.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = LightCardBg,
                                labelColor = NavyPrimary,
                                iconColor = NavyPrimary
                            ),
                            border = if (isSelected) null else BorderStroke(1.dp, CardBorderColor)
                        )
                    }

                    // + Custom Category Chip
                    FilterChip(
                        selected = false,
                        onClick = {
                            newCategoryInput = ""
                            categoryDialogError = null
                            showAddCategoryDialog = true
                        },
                        label = { Text("+ Custom", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = "Add custom category", modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White,
                            labelColor = NavyPrimary,
                            iconColor = NavyPrimary
                        ),
                        border = BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.5f))
                    )
                }

                if (showAddCategoryDialog) {
                    AlertDialog(
                        onDismissRequest = { showAddCategoryDialog = false },
                        title = { Text("New Expense Category", fontWeight = FontWeight.Bold, color = NavyPrimary) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Create a new custom category. Stable ID ensures past expenses remain categorized even if renamed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSubtle
                                )
                                OutlinedTextField(
                                    value = newCategoryInput,
                                    onValueChange = {
                                        newCategoryInput = it
                                        categoryDialogError = null
                                    },
                                    label = { Text("Category Name") },
                                    placeholder = { Text("e.g. Gaming, Pet Care, Gym") },
                                    singleLine = true,
                                    isError = categoryDialogError != null,
                                    supportingText = categoryDialogError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.createCategory(
                                        name = newCategoryInput,
                                        onSuccess = { showAddCategoryDialog = false },
                                        onError = { categoryDialogError = it }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                            ) {
                                Text("Add Category")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddCategoryDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Account Selection (Card, Cash, Secondary Account)
                Text(
                    text = "Payment Account",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseAccount.ALL.forEach { account ->
                        val isSelected = formState.account == account
                        val accountIcon = when (account) {
                            ExpenseAccount.CARD -> Icons.Default.CreditCard
                            ExpenseAccount.CASH -> Icons.Default.Payments
                            else -> Icons.Default.AccountBalanceWallet
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onAccountSelected(account) },
                            leadingIcon = {
                                Icon(
                                    imageVector = accountIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    ExpenseAccount.getDisplayName(account),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = LightCardBg,
                                labelColor = NavyPrimary,
                                iconColor = NavyPrimary
                            ),
                            border = if (isSelected) null else BorderStroke(1.dp, CardBorderColor)
                        )
                    }
                }

                // Classification Selection (Committed vs Discretionary)
                Text(
                    text = "Spending Classification",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCommitted = formState.classification == ExpenseClassification.COMMITTED
                    FilterChip(
                        selected = isCommitted,
                        onClick = { viewModel.onClassificationSelected(ExpenseClassification.COMMITTED) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                "Committed (Fixed)",
                                fontWeight = if (isCommitted) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = LightCardBg,
                            labelColor = NavyPrimary,
                            iconColor = NavyPrimary
                        ),
                        border = if (isCommitted) null else BorderStroke(1.dp, CardBorderColor)
                    )

                    val isDiscretionary = formState.classification == ExpenseClassification.DISCRETIONARY
                    FilterChip(
                        selected = isDiscretionary,
                        onClick = { viewModel.onClassificationSelected(ExpenseClassification.DISCRETIONARY) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                "Discretionary",
                                fontWeight = if (isDiscretionary) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = LightCardBg,
                            labelColor = NavyPrimary,
                            iconColor = NavyPrimary
                        ),
                        border = if (isDiscretionary) null else BorderStroke(1.dp, CardBorderColor)
                    )
                }

                // Date Selection (defaults to Today)
                OutlinedTextField(
                    value = dateFormatter.format(formState.date),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Expense Date") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = NavyPrimary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDatePicker(formState.date) { selected ->
                                viewModel.onDateSelected(selected)
                            }
                        },
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = NavyPrimary,
                        disabledLabelColor = TextSubtle,
                        disabledLeadingIconColor = NavyPrimary,
                        disabledBorderColor = CardBorderColor
                    )
                )

                // Optional Note
                OutlinedTextField(
                    value = formState.note,
                    onValueChange = viewModel::onNoteChanged,
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("e.g. Lunch with team, monthly fuel refill") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NavyPrimary,
                        focusedLabelColor = NavyPrimary,
                        cursorColor = NavyPrimary
                    ),
                    maxLines = 3
                )

                // Validation Error Banner
                formState.errorMessage?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
