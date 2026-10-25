package com.nexus.financetracker.ui.expense

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AccountBalanceWallet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseAddScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Record Expense",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
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
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        if (formState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Save Expense",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (formState.isLoadingDefaults) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                // Header Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = "Quick Expense Entry",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Defaults to today's date & your last used account and category.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Amount Input Field
                OutlinedTextField(
                    value = formState.amountText,
                    onValueChange = viewModel::onAmountChanged,
                    label = { Text("Amount (LKR)") },
                    prefix = { Text("LKR ", fontWeight = FontWeight.Bold) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        formState.computedAmountLKR?.let { lkr ->
                            Text("Formatted: LKR %,.2f".format(lkr))
                        } ?: Text("Enter expense amount in LKR")
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
                            label = { Text("+%,d".format(increment.toInt())) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Category Selection
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
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
                            label = { Text(category.name) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                // Account Selection (Card, Cash, Secondary Account)
                Text(
                    text = "Payment Account",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
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
                            label = { Text(ExpenseAccount.getDisplayName(account)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Classification Selection (Committed vs Discretionary)
                Text(
                    text = "Spending Classification",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = formState.classification == ExpenseClassification.COMMITTED,
                        onClick = { viewModel.onClassificationSelected(ExpenseClassification.COMMITTED) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Committed (Fixed)") },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = formState.classification == ExpenseClassification.DISCRETIONARY,
                        onClick = { viewModel.onClassificationSelected(ExpenseClassification.DISCRETIONARY) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Discretionary") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Date Selection (defaults to Today)
                OutlinedTextField(
                    value = dateFormatter.format(formState.date),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Expense Date") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDatePicker(formState.date) { selected ->
                                viewModel.onDateSelected(selected)
                            }
                        },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.error,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Optional Note
                OutlinedTextField(
                    value = formState.note,
                    onValueChange = viewModel::onNoteChanged,
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("e.g. Lunch with team, monthly fuel refill") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Validation Error Banner
                formState.errorMessage?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
