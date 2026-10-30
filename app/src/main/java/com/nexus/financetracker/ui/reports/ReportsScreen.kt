package com.nexus.financetracker.ui.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.financetracker.ui.common.CategoryExpenseBarChart
import com.nexus.financetracker.domain.model.GoalPriority
import com.nexus.financetracker.ui.common.IncomeSourceBarChart
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private val NavyPrimary = Color(0xFF072044)
private val LightCardBg = Color(0xFFF1F5F9)
private val TextSubtle = Color(0xFF64748B)

/**
 * Screen displaying month-wise financial reports and analytics.
 * All graphs (Income vs Expenses, Goal Assignment, Income by Source)
 * dynamically reflect the user's selected month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit = {},
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showMonthDialog by remember { mutableStateOf(false) }

    val currencyFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

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
                        tint = NavyPrimary
                    )
                    Text(
                        text = "Select Month",
                        color = NavyPrimary,
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
                    // Option 1: All Time
                    val isAllTimeSelected = uiState.selectedMonthLabel == "All Time"
                    val allTimeReport = uiState.monthlyReports["All Time"]
                    Surface(
                        onClick = {
                            viewModel.selectMonth("All Time")
                            showMonthDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAllTimeSelected) NavyPrimary.copy(alpha = 0.12f) else Color.Transparent,
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
                                    color = if (isAllTimeSelected) NavyPrimary else Color(0xFF1E293B)
                                )
                                if (allTimeReport != null && (allTimeReport.totalIncomeLKR > 0 || allTimeReport.totalExpensesLKR > 0)) {
                                    Text(
                                        text = "Income: LKR ${currencyFormat.format(allTimeReport.totalIncomeLKR)}",
                                        fontSize = 12.sp,
                                        color = TextSubtle,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                            if (isAllTimeSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Option 2: Available Months
                    uiState.availableMonths.forEach { month ->
                        val isSelected = month == uiState.selectedMonthLabel
                        val reportForMonth = uiState.monthlyReports[month]
                        val hasData = reportForMonth != null && (reportForMonth.totalIncomeLKR > 0 || reportForMonth.totalExpensesLKR > 0)

                        Surface(
                            onClick = {
                                viewModel.selectMonth(month)
                                showMonthDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) NavyPrimary.copy(alpha = 0.12f) else Color.Transparent,
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
                                        color = if (isSelected) NavyPrimary else Color(0xFF1E293B)
                                    )
                                    if (hasData) {
                                        Text(
                                            text = "Income: LKR ${currencyFormat.format(reportForMonth!!.totalIncomeLKR)}",
                                            fontSize = 12.sp,
                                            color = TextSubtle,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = NavyPrimary,
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
                    Text("Close", color = NavyPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Reports", fontWeight = FontWeight.Bold, color = NavyPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                            tint = NavyPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 1: Month Selection Box (opens dialog on tap)
            Card(
                onClick = { showMonthDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = LightCardBg),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                                .background(NavyPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select Month",
                                tint = NavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Select Month",
                                color = TextSubtle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = uiState.selectedMonthLabel.ifBlank { "Select Month" },
                                color = NavyPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = NavyPrimary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Choose",
                                color = NavyPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            val currentReport = uiState.currentReport
            val selectedLabel = uiState.selectedMonthLabel.ifBlank { "Selected Month" }

            // Graph 1: Income vs Expenses Comparison Card (bound to selected month)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Income vs Expenses",
                                color = NavyPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedLabel,
                                color = TextSubtle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (currentReport != null) {
                            val net = currentReport.netResultLKR
                            val netColor = if (net >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                            val prefix = if (net >= 0) "+ " else ""
                            Text(
                                text = "Net: $prefix${currencyFormat.format(net)}",
                                color = netColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val incomeAmount = currentReport?.totalIncomeLKR ?: 0.0
                    val expenseAmount = currentReport?.totalExpensesLKR ?: 0.0
                    val hasTransactions = incomeAmount > 0.0 || expenseAmount > 0.0

                    if (!hasTransactions) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = TextSubtle.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No income or expense transactions for $selectedLabel",
                                color = TextSubtle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Circular comparison chart
                        ReportsCircularSpendingProgressBar(
                            totalIncome = incomeAmount,
                            totalExpenses = expenseAmount
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Breakdown Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(NavyPrimary, RoundedCornerShape(2.dp))
                                    )
                                    Text("Total Income", fontSize = 12.sp, color = TextSubtle)
                                }
                                Text(
                                    text = "LKR ${currencyFormat.format(incomeAmount)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(Color(0xFFBAC7D5), RoundedCornerShape(2.dp))
                                    )
                                    Text("Total Expenses", fontSize = 12.sp, color = TextSubtle)
                                }
                                Text(
                                    text = "LKR ${currencyFormat.format(expenseAmount)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Graph 2: Goal Assigned Graph Card (bound to selected month)
            val goalReports = currentReport?.goalReports.orEmpty()
            var expandedGoalId by remember(selectedLabel, goalReports) {
                mutableStateOf(goalReports.firstOrNull()?.goalId)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Goal Assignment",
                                color = NavyPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (goalReports.isNotEmpty()) {
                                    "${goalReports.size} ${if (goalReports.size == 1) "Goal" else "Goals"} · $selectedLabel"
                                } else {
                                    selectedLabel
                                },
                                color = TextSubtle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (goalReports.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = TextSubtle.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No savings goals set",
                                color = TextSubtle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            goalReports.forEach { report ->
                                val isExpanded = expandedGoalId == report.goalId
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (isExpanded) 180f else 0f,
                                    label = "goalArrowRotation_${report.goalId}"
                                )

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            expandedGoalId = if (isExpanded) null else report.goalId
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(
                                        width = if (isExpanded) 1.5.dp else 1.dp,
                                        color = if (isExpanded) NavyPrimary.copy(alpha = 0.35f) else Color(0xFFE2E8F0)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        // Header Row: Goal Title, Priority Badge, Target, Progress % & Toggle Icon
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = report.goalTitle,
                                                        color = NavyPrimary,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )

                                                    val priorityColor = when (report.priority.lowercase()) {
                                                        GoalPriority.HIGH -> Color(0xFFDC2626)
                                                        GoalPriority.LOW -> Color(0xFF64748B)
                                                        else -> Color(0xFFD97706)
                                                    }
                                                    val priorityBg = when (report.priority.lowercase()) {
                                                        GoalPriority.HIGH -> Color(0xFFFEE2E2)
                                                        GoalPriority.LOW -> Color(0xFFF1F5F9)
                                                        else -> Color(0xFFFEF3C7)
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = priorityBg
                                                    ) {
                                                        Text(
                                                            text = GoalPriority.getDisplayName(report.priority),
                                                            color = priorityColor,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    if (!report.isActive) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFFF1F5F9)
                                                        ) {
                                                            Text(
                                                                text = "Inactive",
                                                                color = TextSubtle,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = "Target: LKR ${currencyFormat.format(report.targetAmountLKR)}",
                                                    color = TextSubtle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = NavyPrimary.copy(alpha = 0.08f)
                                                ) {
                                                    Text(
                                                        text = "${minOf(100, report.overallProgressPercentage.coerceIn(0.0, 100.0).roundToInt())}%",
                                                        color = NavyPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }

                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                                                    tint = NavyPrimary,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .rotate(arrowRotation)
                                                )
                                            }
                                        }

                                        // Detailed Content when expanded
                                        AnimatedVisibility(visible = isExpanded) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 14.dp)
                                            ) {
                                                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)
                                                Spacer(modifier = Modifier.height(14.dp))

                                                // Visual Comparison Graph: How Much Assigned this month vs Monthly Target
                                                GoalAssignedComparisonBar(
                                                    assignedLKR = report.assignedThisMonthLKR,
                                                    targetLKR = report.targetMonthlyLKR,
                                                    currencyFormat = currencyFormat
                                                )

                                                Spacer(modifier = Modifier.height(12.dp))

                                                // Target achievement status badge
                                                val achieved = report.assignedThisMonthLKR >= report.targetMonthlyLKR && report.targetMonthlyLKR > 0
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (achieved) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = if (achieved) {
                                                            "✓ Monthly savings target achieved in $selectedLabel!"
                                                        } else if (report.targetMonthlyLKR > 0) {
                                                            "${(report.assignedFractionOfMonthlyTarget * 100).toInt()}% of monthly target assigned in $selectedLabel"
                                                        } else {
                                                            "No monthly target required"
                                                        },
                                                        color = if (achieved) Color(0xFF166534) else NavyPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(14.dp))
                                                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)
                                                Spacer(modifier = Modifier.height(10.dp))

                                                // Overall Goal Progress Bar
                                                Text(
                                                    text = "Overall Goal Progress",
                                                    color = NavyPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFFE2E8F0))
                                                ) {
                                                    if (report.overallProgressFraction > 0f) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(fraction = report.overallProgressFraction)
                                                                .fillMaxHeight()
                                                                .background(NavyPrimary)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val progressDisplayStr = if (report.overallProgressPercentage >= 100.0 || report.isCompleted) "100%" else "${"%.0f".format(report.overallProgressPercentage.coerceIn(0.0, 100.0))}%"
                                                    Text(
                                                        text = "$progressDisplayStr complete · Need LKR ${currencyFormat.format(report.remainingAmountLKR)}",
                                                        color = TextSubtle,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                if (report.targetDate != null) {
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.CalendarMonth,
                                                                contentDescription = null,
                                                                tint = TextSubtle,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Text(
                                                                text = "Deadline: ${dateFormat.format(report.targetDate)}",
                                                                fontSize = 12.sp,
                                                                color = TextSubtle,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }

                                                        if (report.daysRemainingText.isNotBlank()) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = if (report.daysRemaining < 0) Color(0xFFFEE2E2) else NavyPrimary
                                                            ) {
                                                                Text(
                                                                    text = report.daysRemainingText,
                                                                    color = if (report.daysRemaining < 0) Color(0xFFDC2626) else Color.White,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Graph 3: Selected Month's Income by Source Bar Graph Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Income by Source",
                                color = NavyPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedLabel,
                                color = TextSubtle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (currentReport != null && currentReport.totalIncomeLKR > 0) {
                            Text(
                                text = "LKR ${currencyFormat.format(currentReport.totalIncomeLKR)}",
                                color = NavyPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val sources = currentReport?.incomeSources.orEmpty()
                    if (sources.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = TextSubtle.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No confirmed income recorded for $selectedLabel",
                                color = TextSubtle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Render Bar Chart with X-axis (sources) and Y-axis (income values)
                        IncomeSourceBarChart(
                            items = sources,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Source details table
                        Text(
                            text = "Breakdown Details",
                            color = NavyPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        sources.forEach { sourceItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sourceItem.source,
                                    color = Color(0xFF334155),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "LKR ${currencyFormat.format(sourceItem.amountLKR)}",
                                    color = NavyPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Graph 4: Selected Month's Expenses by Category Bar Graph Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Expenses by Category",
                                color = NavyPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedLabel,
                                color = TextSubtle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (currentReport != null && currentReport.totalExpensesLKR > 0) {
                            Text(
                                text = "LKR ${currencyFormat.format(currentReport.totalExpensesLKR)}",
                                color = NavyPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val categories = currentReport?.categorySpendings.orEmpty()
                    if (categories.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = TextSubtle.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No expenses recorded for $selectedLabel",
                                color = TextSubtle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Render Bar Chart with X-axis (categories) and Y-axis (expenses)
                        CategoryExpenseBarChart(
                            items = categories,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Category details table
                        Text(
                            text = "Breakdown Details",
                            color = NavyPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        categories.forEach { catItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = catItem.name,
                                    color = Color(0xFF334155),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "LKR ${currencyFormat.format(catItem.amountLKR)}",
                                    color = NavyPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * Circular progress bar component comparing Income vs Expenses for the selected month.
 */
@Composable
private fun ReportsCircularSpendingProgressBar(
    totalIncome: Double,
    totalExpenses: Double,
    modifier: Modifier = Modifier
) {
    val total = totalIncome + totalExpenses
    val incomePercent = if (total > 0) ((totalIncome / total) * 100).roundToInt() else 0
    val expensePercent = if (total > 0) (100 - incomePercent) else 0
    val hasData = total > 0

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                val strokeWidth = 12.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Track
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                if (hasData) {
                    val expenseSweep = (expensePercent / 100f) * 360f
                    val incomeSweep = (incomePercent / 100f) * 360f

                    if (expenseSweep > 0f) {
                        drawArc(
                            color = Color(0xFFBAC7D5),
                            startAngle = -90f,
                            sweepAngle = expenseSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    if (incomeSweep > 0f) {
                        drawArc(
                            color = NavyPrimary,
                            startAngle = -90f + expenseSweep,
                            sweepAngle = incomeSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hasData) "$incomePercent%" else "0%",
                    color = NavyPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Income",
                    color = TextSubtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Visual comparative bar component comparing Amount Assigned vs Monthly Target for the selected month.
 */
@Composable
private fun GoalAssignedComparisonBar(
    assignedLKR: Double,
    targetLKR: Double,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val maxVal = maxOf(assignedLKR, targetLKR).coerceAtLeast(1.0)
    val assignedFraction = (assignedLKR / maxVal).toFloat().coerceIn(0.04f, 1f)
    val targetFraction = (targetLKR / maxVal).toFloat().coerceIn(0.04f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Assigned Bar
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Assigned this month",
                    fontSize = 12.sp,
                    color = NavyPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "LKR ${currencyFormat.format(assignedLKR)}",
                    fontSize = 12.sp,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = assignedFraction)
                        .fillMaxHeight()
                        .background(NavyPrimary)
                )
            }
        }

        // Target Bar
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Monthly Target",
                    fontSize = 12.sp,
                    color = TextSubtle,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "LKR ${currencyFormat.format(targetLKR)}",
                    fontSize = 12.sp,
                    color = TextSubtle,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = targetFraction)
                        .fillMaxHeight()
                        .background(Color(0xFFBAC7D5))
                )
            }
        }
    }
}
