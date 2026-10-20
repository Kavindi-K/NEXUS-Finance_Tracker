package com.nexus.financetracker.ui.income

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Income Screen — Conforms to FR3-FR8 and Branch 08 feature/income-freelance.
 * Features:
 * - Confirmed-only total banner (Pending excluded from earned totals)
 * - Separation of pending freelance invoices from confirmed income
 * - One-tap confirmation to mark pending invoices as received, setting dateReceived,
 *   removing expectedDate, keeping the same income ID, and adding it exactly once to totals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(
    viewModel: IncomeViewModel,
    onAddIncome: () -> Unit,
    onEditIncome: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var incomeToConfirm by remember { mutableStateOf<Income?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Income", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddIncome,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Income")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Confirmed-Only Total Income Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Confirmed Income",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        AssistChip(
                            onClick = {},
                            label = { Text("Confirmed Only", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "LKR %,.2f".format(uiState.totalConfirmedLKR),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (uiState.totalPendingLKR > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Pending (excluded): LKR %,.2f".format(uiState.totalPendingLKR),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 2. Filter Chips (All, Confirmed, Pending)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IncomeFilter.values().forEach { filter ->
                    val isSelected = uiState.selectedFilter == filter
                    val label = when (filter) {
                        IncomeFilter.ALL -> "All (${uiState.incomes.size})"
                        IncomeFilter.CONFIRMED -> "Confirmed (${uiState.incomes.count { it.status == IncomeStatus.CONFIRMED }})"
                        IncomeFilter.PENDING -> "Pending (${uiState.incomes.count { it.status == IncomeStatus.PENDING }})"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = { Text(label) }
                    )
                }
            }

            // 3. Incomes List or Empty State
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredIncomes.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No income records found",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap the + button to record confirmed or pending income",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val pendingList = uiState.incomes.filter { it.status == IncomeStatus.PENDING }
                val confirmedList = uiState.incomes.filter { it.status == IncomeStatus.CONFIRMED }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.selectedFilter == IncomeFilter.ALL && pendingList.isNotEmpty()) {
                        // Section 1: Pending Invoices / Payments (Shown separately and excluded from totals)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pending Invoices & Milestones (${pendingList.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                                Text(
                                    text = "Excluded from totals",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(pendingList, key = { it.id }) { income ->
                            IncomeCard(
                                income = income,
                                dateFormatter = dateFormatter,
                                onEdit = { onEditIncome(income.id) },
                                onDelete = { viewModel.deleteIncome(income.id) },
                                onConfirmPayment = { incomeToConfirm = income }
                            )
                        }

                        // Section 2: Confirmed Received Income
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Confirmed Income (${confirmedList.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        items(confirmedList, key = { it.id }) { income ->
                            IncomeCard(
                                income = income,
                                dateFormatter = dateFormatter,
                                onEdit = { onEditIncome(income.id) },
                                onDelete = { viewModel.deleteIncome(income.id) },
                                onConfirmPayment = null
                            )
                        }
                    } else {
                        // Single filtered view (Pending or Confirmed or All without pending)
                        items(uiState.filteredIncomes, key = { it.id }) { income ->
                            IncomeCard(
                                income = income,
                                dateFormatter = dateFormatter,
                                onEdit = { onEditIncome(income.id) },
                                onDelete = { viewModel.deleteIncome(income.id) },
                                onConfirmPayment = if (income.status == IncomeStatus.PENDING) {
                                    { incomeToConfirm = income }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Pending Invoices
    incomeToConfirm?.let { income ->
        ConfirmPaymentDialog(
            income = income,
            dateFormatter = dateFormatter,
            onDismiss = { incomeToConfirm = null },
            onConfirm = { dateReceived ->
                viewModel.confirmIncome(income, dateReceived)
                incomeToConfirm = null
            }
        )
    }
}

@Composable
private fun IncomeCard(
    income: Income,
    dateFormatter: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfirmPayment: (() -> Unit)? = null
) {
    val isConfirmed = income.status == IncomeStatus.CONFIRMED
    val isCryptoLoss = income.source == IncomeSource.CRYPTO && income.amountLKR < 0

    val icon: ImageVector = when (income.source) {
        IncomeSource.SALARY -> Icons.Default.Work
        IncomeSource.FREELANCE -> Icons.Default.Laptop
        IncomeSource.ADSENSE -> Icons.Default.AdsClick
        IncomeSource.CRYPTO -> Icons.Default.CurrencyBitcoin
        else -> Icons.Default.AttachMoney
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = if (!isConfirmed) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConfirmed) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isConfirmed) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = IncomeSource.getDisplayName(income.source),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    Spacer(Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isConfirmed) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ) {
                            Text(
                                text = if (isConfirmed) "Confirmed" else "Pending",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConfirmed) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val date = if (isConfirmed) income.dateReceived else income.expectedDate
                        val datePrefix = if (isConfirmed) "Received: " else "Expected: "
                        Text(
                            text = date?.let { "$datePrefix${dateFormatter.format(it)}" } ?: "No date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!income.note.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = income.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val amountColor = if (isCryptoLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Text(
                        text = "LKR %,.2f".format(income.amountLKR),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )

                    if (income.currency != "LKR") {
                        Text(
                            text = "$%,.2f %s @ %,.2f".format(income.amountOriginal, income.currency, income.exchangeRate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Quick Confirmation Action on Pending Invoices
            if (!isConfirmed && onConfirmPayment != null) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onConfirmPayment,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFE8F5E9),
                        contentColor = Color(0xFF2E7D32)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Mark Payment Received",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Dialog allowing the user to confirm receipt of a pending freelance invoice or payment,
 * selecting the exact Date Received (defaults to today) before transitioning it to Confirmed.
 */
@Composable
private fun ConfirmPaymentDialog(
    income: Income,
    dateFormatter: SimpleDateFormat,
    onDismiss: () -> Unit,
    onConfirm: (Date) -> Unit
) {
    var selectedDate by remember { mutableStateOf(Date()) }
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }

    fun showDatePicker() {
        calendar.time = selectedDate
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance()
                chosen.set(year, month, dayOfMonth, 12, 0, 0)
                selectedDate = chosen.time
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32)
                )
                Text("Confirm Payment Received")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Confirm receipt of LKR %,.2f for %s?".format(
                        income.amountLKR,
                        IncomeSource.getDisplayName(income.source)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!income.note.isNullOrBlank()) {
                    Text(
                        text = "Reference: ${income.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "This will move the payment to confirmed earned totals and remove the expected date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text(
                    text = "Date Received",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = dateFormatter.format(selectedDate),
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker() },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDate) }
            ) {
                Text("Confirm & Add to Totals")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
