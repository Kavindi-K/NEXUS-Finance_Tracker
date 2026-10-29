package com.nexus.financetracker.ui.income

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.ui.unit.sp
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Design System Colors matching Dashboard Wireframe (#132D52 and #E2E8F0)
private val NavyPrimary = Color(0xFF072044)
private val NavyCardBg = Color(0xFF132D52)
private val NavyCardBorder = Color(0x33FFFFFF)
private val TextHeaderSubtle = Color(0xFFBAC7D5)
private val TextSubtle = Color(0xFF64748B)
private val LightCardBg = Color(0xFFF1F5F9)

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
    onEditIncome: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var incomeToConfirm by remember { mutableStateOf<Income?>(null) }
    var incomeToDelete by remember { mutableStateOf<Income?>(null) }
    var showBreakdownSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val isStreamDetail = uiState.selectedSource != null

    // Intercept hardware/gesture back press to return from Stream Detail to Main Income Overview
    BackHandler(enabled = isStreamDetail) {
        viewModel.onSourceFilterSelected(null)
    }

    var showMonthDialog by remember { mutableStateOf(false) }
    val currencyFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    if (showMonthDialog) {
        AlertDialog(
            onDismissRequest = { showMonthDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Select Month",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Option 1: "All Time"
                    val isAllTimeSelected = uiState.isPeriodAllTime
                    Surface(
                        onClick = {
                            viewModel.onToggleAllTimePeriod(true)
                            showMonthDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAllTimeSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "All Time",
                                    fontSize = 15.sp,
                                    fontWeight = if (isAllTimeSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllTimeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Income: LKR ${currencyFormat.format(uiState.totalConfirmedLKR)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            if (isAllTimeSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Option 2: Available Months
                    uiState.availableMonths.forEach { month ->
                        val isSelected = !uiState.isPeriodAllTime && month == uiState.selectedMonthLabel
                        val monthIncome = uiState.monthlyIncomeTotals[month] ?: 0.0

                        Surface(
                            onClick = {
                                viewModel.selectMonth(month)
                                showMonthDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = month,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (monthIncome > 0) {
                                        Text(
                                            text = "Income: LKR ${currencyFormat.format(monthIncome)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isStreamDetail) {
                        Text(
                            text = IncomeSource.getDisplayName(uiState.selectedSource!!),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text("Income", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isStreamDetail) {
                                viewModel.onSourceFilterSelected(null)
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isStreamDetail) "Back to Income Overview" else "Back to Dashboard"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isStreamDetail) {
                        viewModel.onSourceChanged(uiState.selectedSource!!)
                    }
                    onAddIncome()
                },
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
            // Offline Indicator Banner
            if (uiState.isOffline) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFDE68A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline Mode",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Mode Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Changes saved to local Room cache and will sync automatically once reconnected.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }
            }

            // 0. Month Selector Filter Card
            Card(
                onClick = { showMonthDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select Month",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Select Month",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (uiState.isPeriodAllTime) "All Time" else uiState.selectedMonthLabel.ifBlank { "Select Month" },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Choose",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            val salaryItem = if (uiState.selectedSource == IncomeSource.SALARY) {
                uiState.incomes.firstOrNull { it.source == IncomeSource.SALARY }
            } else null
            val isSalaryMonthView = uiState.selectedSource == IncomeSource.SALARY && !uiState.isPeriodAllTime

            // 1. Hero Banner: StreamHeroCard (on Stream Detail) OR Navy Banner (on Main Overview)
            if (isStreamDetail) {
                StreamHeroCard(
                    source = uiState.selectedSource!!,
                    confirmedLKR = uiState.streamConfirmedLKR,
                    pendingLKR = uiState.streamPendingLKR,
                    totalCount = uiState.incomes.size,
                    salaryItem = salaryItem,
                    dateFormatter = dateFormatter,
                    onEditSalary = onEditIncome,
                    onDeleteSalary = { incomeToDelete = it }
                )
            } else {
                Card(
                    onClick = { showBreakdownSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NavyCardBg
                    ),
                    border = BorderStroke(1.dp, NavyCardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = TextHeaderSubtle
                                )
                                Text(
                                    text = "Total Income Received",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextHeaderSubtle
                                )
                            }

                            // Confirmed Status Indicator (pure text & dot, no container or button look)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF34D399), CircleShape)
                                )
                                Text(
                                    text = "Confirmed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        val displayTotal = if (uiState.isPeriodAllTime) uiState.totalConfirmedLKR
                        else (uiState.reconciliation?.reconciledActualLKR ?: uiState.totalConfirmedLKR)

                        val totalText = when {
                            displayTotal < 0 -> "- LKR %,.2f".format(kotlin.math.abs(displayTotal))
                            else -> "LKR %,.2f".format(displayTotal)
                        }

                        Text(
                            text = totalText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        if (uiState.totalPendingLKR > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TextHeaderSubtle,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "+ LKR %,.2f pending payment".format(uiState.totalPendingLKR),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = TextHeaderSubtle
                                )
                            }
                        }
                    }
                }
            }

            // 2. Filter Chips (Pinned at the top) - Hidden in Base Salary Month view
            if (!isSalaryMonthView) {
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

                // Modern Sleek Sort Control Bar (Matching Expenses Dashboard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = TextSubtle,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Sort by",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSubtle
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isDateActive = uiState.selectedSortOrder == IncomeSortOrder.DATE_DESC || uiState.selectedSortOrder == IncomeSortOrder.DATE_ASC
                        val isDateDesc = uiState.selectedSortOrder == IncomeSortOrder.DATE_DESC

                        Surface(
                            onClick = {
                                if (isDateActive) {
                                    viewModel.onSortOrderSelected(if (isDateDesc) IncomeSortOrder.DATE_ASC else IncomeSortOrder.DATE_DESC)
                                } else {
                                    viewModel.onSortOrderSelected(IncomeSortOrder.DATE_DESC)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isDateActive) NavyPrimary else Color.White,
                            border = BorderStroke(1.dp, if (isDateActive) NavyPrimary else Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = if (isDateActive) Color.White else NavyPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isDateActive) {
                                        if (isDateDesc) "Date (Newest)" else "Date (Oldest)"
                                    } else "Date",
                                    fontSize = 12.sp,
                                    fontWeight = if (isDateActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isDateActive) Color.White else NavyPrimary
                                )
                                if (isDateActive) {
                                    Icon(
                                        imageVector = if (isDateDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        val isAmountActive = uiState.selectedSortOrder == IncomeSortOrder.AMOUNT_DESC || uiState.selectedSortOrder == IncomeSortOrder.AMOUNT_ASC
                        val isAmountDesc = uiState.selectedSortOrder == IncomeSortOrder.AMOUNT_DESC

                        Surface(
                            onClick = {
                                if (isAmountActive) {
                                    viewModel.onSortOrderSelected(if (isAmountDesc) IncomeSortOrder.AMOUNT_ASC else IncomeSortOrder.AMOUNT_DESC)
                                } else {
                                    viewModel.onSortOrderSelected(IncomeSortOrder.AMOUNT_DESC)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isAmountActive) NavyPrimary else Color.White,
                            border = BorderStroke(1.dp, if (isAmountActive) NavyPrimary else Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isAmountActive) {
                                        if (isAmountDesc) "Amount (High)" else "Amount (Low)"
                                    } else "Amount",
                                    fontSize = 12.sp,
                                    fontWeight = if (isAmountActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAmountActive) Color.White else NavyPrimary
                                )
                                if (isAmountActive) {
                                    Icon(
                                        imageVector = if (isAmountDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Scrollable List of Income Items (Hidden in Base Salary Month view because salary has 1 entry represented above)
            if (!isSalaryMonthView) {
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                                if (uiState.selectedFilter == IncomeFilter.ALL) {
                                    if (pendingList.isNotEmpty()) {
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
                                                onDelete = { incomeToDelete = income },
                                                onConfirmPayment = { incomeToConfirm = income },
                                                isStreamView = isStreamDetail
                                            )
                                        }
                                    }

                                    if (confirmedList.isNotEmpty()) {
                                        // Section 2: Confirmed Received Income
                                        if (pendingList.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                        }
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
                                                onDelete = { incomeToDelete = income },
                                                onConfirmPayment = null,
                                                isStreamView = isStreamDetail
                                            )
                                        }
                                    }
                                } else {
                                    // Filtered view (Pending or Confirmed)
                                    uiState.filteredIncomes.forEach { income ->
                                        IncomeCard(
                                            income = income,
                                            dateFormatter = dateFormatter,
                                            onEdit = { onEditIncome(income.id) },
                                            onDelete = { incomeToDelete = income },
                                            onConfirmPayment = if (income.status == IncomeStatus.PENDING) {
                                                { incomeToConfirm = income }
                                            } else null,
                                            isStreamView = isStreamDetail
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (salaryItem == null && !uiState.isLoading) {
                // If in Base Salary month view but no salary has been recorded yet:
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                                imageVector = Icons.Default.Work,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No Base Salary recorded for this month",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap the + button to record your salary",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Confirmation Dialog for Deleting Income
    incomeToDelete?.let { income ->
        DeleteIncomeConfirmationDialog(
            income = income,
            onDismiss = { incomeToDelete = null },
            onConfirm = {
                viewModel.deleteIncome(income.id)
                incomeToDelete = null
            }
        )
    }

    // Modal Bottom Sheet for 4-Source Breakdown & FR20 Estimate Comparison Details
    if (showBreakdownSheet) {
        uiState.reconciliation?.let { reconciliation ->
            IncomeBreakdownBottomSheet(
                reconciliation = reconciliation,
                selectedSource = uiState.selectedSource,
                isAllTime = uiState.isPeriodAllTime,
                onSelectSource = { source -> viewModel.onSourceFilterSelected(source) },
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
    onConfirmPayment: (() -> Unit)? = null,
    isStreamView: Boolean = false
) {
    val isConfirmed = income.status == IncomeStatus.CONFIRMED
    val isCrypto = income.source == IncomeSource.CRYPTO
    val isCryptoLoss = isCrypto && income.amountLKR < 0

    val icon: ImageVector = when (income.source) {
        IncomeSource.SALARY -> Icons.Default.Work
        IncomeSource.FREELANCE -> Icons.Default.Laptop
        IncomeSource.CRYPTO -> Icons.Default.CurrencyBitcoin
        IncomeSource.ADSENSE -> Icons.Default.AdsClick
        IncomeSource.OTHER -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.AttachMoney
    }

    // In a dedicated stream view, avoid repeating the stream name (e.g. "AdSense").
    // Prioritize the user's note/description, or a specific payout title.
    val (displayTitle, subtitleNote) = if (income.source == IncomeSource.OTHER) {
        val noteContent = income.note
        if (noteContent != null && noteContent.contains(" • ")) {
            noteContent.substringBefore(" • ") to noteContent.substringAfter(" • ")
        } else if (!noteContent.isNullOrBlank()) {
            noteContent to null
        } else {
            "Other Income" to null
        }
    } else if (isStreamView) {
        val title = if (!income.note.isNullOrBlank()) {
            income.note
        } else {
            when (income.source) {
                IncomeSource.SALARY -> "Base Salary"
                IncomeSource.FREELANCE -> if (isConfirmed) "Milestone Payment" else "Pending Milestone"
                IncomeSource.ADSENSE -> "AdSense Payout"
                IncomeSource.CRYPTO -> if (isCryptoLoss) "Realized Loss" else "Crypto Realized Gain"
                else -> "Income Payout"
            }
        }
        title to null
    } else {
        IncomeSource.getDisplayName(income.source) to income.note
    }

    val showNoteAsSubtitle = !isStreamView && !subtitleNote.isNullOrBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Row 1: Icon, Source & Note, and Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )

                    if (showNoteAsSubtitle) {
                        Spacer(Modifier.height(1.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = subtitleNote!!,
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

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(5.dp))

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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Confirmation Action on Pending Invoices
            if (!isConfirmed && onConfirmPayment != null) {
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = onConfirmPayment,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Mark Payment Received",
                        style = MaterialTheme.typography.labelMedium,
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
 * Confirmation dialog shown before permanently deleting an income record.
 */
@Composable
private fun DeleteIncomeConfirmationDialog(
    income: Income,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Delete Income Record?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val formattedAmount = when {
                    income.source == IncomeSource.CRYPTO && income.amountLKR < 0 ->
                        "- LKR %,.2f".format(kotlin.math.abs(income.amountLKR))
                    else -> "LKR %,.2f".format(income.amountLKR)
                }
                Text(
                    text = "Are you sure you want to delete this $formattedAmount (${IncomeSource.getDisplayName(income.source)}) entry?",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!income.note.isNullOrBlank()) {
                    Text(
                        text = "Note: ${income.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "This action will permanently remove this transaction from your financial history and update your totals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
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
    selectedSource: String?,
    isAllTime: Boolean,
    onSelectSource: (String?) -> Unit,
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

            // Stream section header with reset button if filtered
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Income by Stream",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap a stream to filter this month's transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedSource != null) {
                    TextButton(
                        onClick = {
                            onSelectSource(null)
                            onDismiss()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Show All", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reconciliation.sourceBreakdown.forEach { sourceItem ->
                    val isSelected = selectedSource == sourceItem.source
                    Surface(
                        onClick = {
                            onSelectSource(if (isSelected) null else sourceItem.source)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else null,
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
                                    IncomeSource.CRYPTO -> Triple(Icons.Default.CurrencyBitcoin, Color(0xFFFFF3E0), Color(0xFFE65100))
                                    IncomeSource.ADSENSE -> Triple(Icons.Default.AdsClick, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                    IncomeSource.OTHER -> Triple(Icons.Default.AccountBalanceWallet, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
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

                                Column {
                                    Text(
                                        text = sourceItem.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "Active filter",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isNegative = sourceItem.amountLKR < 0
                                Text(
                                    text = (if (isNegative) "-LKR %,.2f" else "LKR %,.2f").format(kotlin.math.abs(sourceItem.amountLKR)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated category summary card for a selected income stream.
 * Styled with LightCardBg (#E2E8F0) and distinct side-by-side Received vs Pending metrics
 * to clearly distinguish it from the dark Navy main total card.
 */
@Composable
private fun StreamHeroCard(
    source: String,
    confirmedLKR: Double,
    pendingLKR: Double,
    totalCount: Int,
    salaryItem: Income? = null,
    dateFormatter: SimpleDateFormat? = null,
    onEditSalary: ((String) -> Unit)? = null,
    onDeleteSalary: ((Income) -> Unit)? = null
) {
    val isSalary = source == IncomeSource.SALARY
    val icon = when (source) {
        IncomeSource.SALARY -> Icons.Default.Work
        IncomeSource.FREELANCE -> Icons.Default.Laptop
        IncomeSource.CRYPTO -> Icons.Default.CurrencyBitcoin
        IncomeSource.ADSENSE -> Icons.Default.AdsClick
        IncomeSource.OTHER -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.AttachMoney
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Category Icon Avatar + Name + Non-button entry count / Date + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isSalary) "Base Salary" else "${IncomeSource.getDisplayName(source)} Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isSalary && salaryItem != null && dateFormatter != null && salaryItem.dateReceived != null) {
                            Text(
                                text = "Received on ${dateFormatter.format(salaryItem.dateReceived)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val entryLabel = if (totalCount == 1) "1 entry recorded" else "$totalCount entries recorded"
                            Text(
                                text = entryLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // If Base Salary and salaryItem exists, render Edit & Delete action buttons in the header
                if (isSalary && salaryItem != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onEditSalary?.invoke(salaryItem.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Base Salary",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteSalary?.invoke(salaryItem) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Base Salary",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (isSalary) {
                // Base Salary has 1 single salary per month: show confirmed amount and confirmed badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Received",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "LKR %,.2f".format(confirmedLKR),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (salaryItem != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            contentColor = Color(0xFF2E7D32)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF2E7D32), CircleShape)
                                )
                                Text(
                                    text = "Confirmed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                // Metrics: Side-by-side Received (Confirmed) and Pending Payment across other stream categories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Received (Confirmed)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        val amountText = when {
                            confirmedLKR < 0 -> "- LKR %,.2f".format(kotlin.math.abs(confirmedLKR))
                            else -> "LKR %,.2f".format(confirmedLKR)
                        }
                        Text(
                            text = amountText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (confirmedLKR < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Pending Payment",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pendingLKR > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "LKR %,.2f".format(pendingLKR),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingLKR > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}



