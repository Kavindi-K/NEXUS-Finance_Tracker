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

private val NavyPrimary = Color(0xFF072044)
private val CardBlue = Color(0xFF2563EB)
private val LightCardBg = Color(0xFFF1F5F9)
private val TextSubtle = Color(0xFF64748B)
private val CardBorderColor = Color(0xFFCBD5E1)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseAddScreen(
    viewModel: ExpenseViewModel,
    expenseId: String? = null,
    categoryId: String? = null,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(expenseId, categoryId) {
        if (!expenseId.isNullOrBlank() && expenseId != "{expenseId}") {
            viewModel.loadExpenseForEdit(expenseId)
        } else {
            val cleanCatId = if (!categoryId.isNullOrBlank() && categoryId != "{categoryId}") categoryId else null
            viewModel.resetForm(preferredCategoryId = cleanCatId)
        }
    }

    LaunchedEffect(categoryId, uiState.allCategories) {
        val cleanCatId = if (!categoryId.isNullOrBlank() && categoryId != "{categoryId}") categoryId else null
        if (cleanCatId != null && (expenseId.isNullOrBlank() || expenseId == "{expenseId}")) {
            val found = uiState.allCategories.find { it.id == cleanCatId }
                ?: viewModel.formState.value.availableCategories.find { it.id == cleanCatId }
                ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == cleanCatId }
            if (found != null) {
                viewModel.onCategorySelected(found)
                viewModel.onClassificationSelected(found.classification)
            }
        }
    }

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

                // Pre-selected Category & Classification Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LightCardBg),
                    border = BorderStroke(1.dp, CardBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Category",
                                fontSize = 12.sp,
                                color = TextSubtle,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formState.categoryName.ifBlank { "Expense Category" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (formState.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)) {
                                NavyPrimary.copy(alpha = 0.12f)
                            } else {
                                CardBlue.copy(alpha = 0.12f)
                            }
                        ) {
                            Text(
                                text = if (formState.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)) "Committed" else "Discretionary",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (formState.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)) NavyPrimary else CardBlue
                            )
                        }
                    }
                }

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

                // Payment Account Selection (Card, Cash, Secondary Account)
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

                // Date Selection with Back-dating Presets (Member 2 Order 6)
                Text(
                    text = "Transaction Date",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cal = Calendar.getInstance().apply { time = formState.date }
                    val todayCal = Calendar.getInstance()
                    val isToday = cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                    todayCal.add(Calendar.DAY_OF_YEAR, -1)
                    val isYesterday = cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                    FilterChip(
                        selected = isToday,
                        onClick = { viewModel.onQuickDateSelected(0) },
                        label = { Text("Today", fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = isYesterday,
                        onClick = { viewModel.onQuickDateSelected(1) },
                        label = { Text("Yesterday", fontWeight = if (isYesterday) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = !isToday && !isYesterday,
                        onClick = {
                            showDatePicker(formState.date) { selected ->
                                viewModel.onDateSelected(selected)
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                if (!isToday && !isYesterday) dateFormatter.format(formState.date) else "Pick Date",
                                fontWeight = if (!isToday && !isYesterday) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1.3f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }

                // Informative Back-dated Banner
                if (formState.isBackdated) {
                    val targetMonthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF92400E),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Backdated Entry: This expense will be recorded for ${dateFormatter.format(formState.date)} and counted in ${targetMonthFormat.format(formState.date)} totals.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

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
