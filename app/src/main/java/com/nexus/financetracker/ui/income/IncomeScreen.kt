package com.nexus.financetracker.ui.income

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
 * Uses verticalScroll(rememberScrollState()) for flawless, universal scrolling with both
 * mouse wheel, click-and-drag, and touch on emulators and physical devices.
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
    var showBreakdownSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

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
        val pendingList = uiState.incomes.filter { it.status == IncomeStatus.PENDING }
        val confirmedList = uiState.incomes.filter { it.status == IncomeStatus.CONFIRMED }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. Date Period Selector (Month Navigation / All-Time Toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.onPreviousMonthClicked() },
                        enabled = !uiState.isPeriodAllTime,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                    }

                    Text(
                        text = if (uiState.isPeriodAllTime) "All Time History" else uiState.selectedMonthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { viewModel.onNextMonthClicked() },
                        enabled = !uiState.isPeriodAllTime,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !uiState.isPeriodAllTime,
                        onClick = { viewModel.onToggleAllTimePeriod(false) },
                        label = { Text("Month", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = uiState.isPeriodAllTime,
                        onClick = { viewModel.onToggleAllTimePeriod(true) },
                        label = { Text("All Time", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // 1. Confirmed-Only Total Income Banner (Pinned at the top - tap anywhere for breakdown)
            Card(
                onClick = { showBreakdownSheet = true },
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
                                text = if (uiState.isPeriodAllTime) "Total Income Received (All-Time)" else "Total Income Received",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AssistChip(
                                onClick = { showBreakdownSheet = true },
                                label = { Text("Confirmed", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Breakdown",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    val displayTotal = if (uiState.isPeriodAllTime) uiState.totalConfirmedLKR
                    else (uiState.reconciliation?.reconciledActualLKR ?: uiState.totalConfirmedLKR)

                    Text(
                        text = "LKR %,.2f".format(displayTotal),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (uiState.totalPendingLKR > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "+ LKR %,.2f pending payment".format(uiState.totalPendingLKR),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // 2. Filter Chips (Pinned at the top)
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

            // 3. Scrollable List of Income Items (Occupies remaining screen space)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        if (uiState.filteredIncomes.isEmpty()) {
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
                                            text = "No income records found for this period",
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
                        if (uiState.selectedFilter == IncomeFilter.ALL && pendingList.isNotEmpty()) {
                            // Section 1: Pending Invoices & Milestones (Excluded from totals)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
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

                            pendingList.forEach { income ->
                                IncomeCard(
                                    income = income,
                                    dateFormatter = dateFormatter,
                                    onEdit = { onEditIncome(income.id) },
                                    onDelete = { viewModel.deleteIncome(income.id) },
                                    onConfirmPayment = { incomeToConfirm = income }
                                )
                            }

                            // Section 2: Confirmed Received Income
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Confirmed Income (${confirmedList.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            confirmedList.forEach { income ->
                                IncomeCard(
                                    income = income,
                                    dateFormatter = dateFormatter,
                                    onEdit = { onEditIncome(income.id) },
                                    onDelete = { viewModel.deleteIncome(income.id) },
                                    onConfirmPayment = null
                                )
                            }
                        } else {
                            // Filtered view (Pending or Confirmed)
                            uiState.filteredIncomes.forEach { income ->
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

    // Modal Bottom Sheet for 4-Source Breakdown & FR20 Estimate Comparison Details
    if (showBreakdownSheet) {
        uiState.reconciliation?.let { reconciliation ->
            IncomeBreakdownBottomSheet(
                reconciliation = reconciliation,
                isAllTime = uiState.isPeriodAllTime,
                onDismiss = { showBreakdownSheet = false }
            )
        }
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
    val isCrypto = income.source == IncomeSource.CRYPTO
    val isCryptoLoss = isCrypto && income.amountLKR < 0

    val icon: ImageVector = when (income.source) {
        IncomeSource.SALARY -> Icons.Default.Work
        IncomeSource.FREELANCE -> Icons.Default.Laptop
        IncomeSource.ADSENSE -> Icons.Default.AdsClick
        IncomeSource.CRYPTO -> Icons.Default.CurrencyBitcoin
        else -> Icons.Default.AttachMoney
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Row 1: Icon, Source & Note, and Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCryptoLoss) MaterialTheme.colorScheme.errorContainer
                            else if (isConfirmed) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isCryptoLoss) MaterialTheme.colorScheme.onErrorContainer
                        else if (isConfirmed) MaterialTheme.colorScheme.onPrimaryContainer
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

                    if (!income.note.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
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
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    val amountColor = if (isCryptoLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val amountText = when {
                        isCryptoLoss -> "- LKR %,.2f".format(kotlin.math.abs(income.amountLKR))
                        isCrypto -> "+ LKR %,.2f".format(income.amountLKR)
                        else -> "LKR %,.2f".format(income.amountLKR)
                    }
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )

                    if (isCrypto) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isCryptoLoss) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = if (isCryptoLoss) "Realized Loss" else "Realized Profit",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCryptoLoss) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (income.currency != "LKR") {
                        Text(
                            text = "$%,.2f %s @ %,.2f".format(income.amountOriginal, income.currency, income.exchangeRate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            // Row 2: Status Chip & Date (Left) + Edit/Delete Actions (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Text(
                        text = date?.let { dateFormatter.format(it) } ?: "No date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
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
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick Confirmation Action on Pending Invoices
            if (!isConfirmed && onConfirmPayment != null) {
                Spacer(Modifier.height(8.dp))
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

/**
 * Modern, clean Modal Bottom Sheet for 4-Source Breakdown & FR20 Estimate Details.
 * Keeps the main screen breathing and uncluttered while ensuring full compliance with FR3, FR5, FR20.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomeBreakdownBottomSheet(
    reconciliation: MonthlyReconciliation,
    isAllTime: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isAllTime) "Income Breakdown" else "${reconciliation.monthLabel} Breakdown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Total Received: LKR %,.2f".format(reconciliation.reconciledActualLKR),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Text(
                text = "Income by Stream",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reconciliation.sourceBreakdown.forEach { sourceItem ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val (icon, iconBg, iconTint) = when (sourceItem.source) {
                                    IncomeSource.SALARY -> Triple(Icons.Default.Work, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                    IncomeSource.FREELANCE -> Triple(Icons.Default.Laptop, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    IncomeSource.ADSENSE -> Triple(Icons.Default.AdsClick, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                    IncomeSource.CRYPTO -> Triple(Icons.Default.CurrencyBitcoin, Color(0xFFFFF3E0), Color(0xFFE65100))
                                    else -> Triple(Icons.Default.AttachMoney, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(iconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Text(
                                    text = sourceItem.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val isNegative = sourceItem.amountLKR < 0
                            Text(
                                text = (if (isNegative) "-LKR %,.2f" else "LKR %,.2f").format(kotlin.math.abs(sourceItem.amountLKR)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}



