package com.nexus.financetracker.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow



import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.financetracker.R
import com.nexus.financetracker.ui.common.CategoryExpenseBarChart
import com.nexus.financetracker.ui.common.IncomeSourceBarChart
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

// Design System Colors matching exact Figma Wireframe
private val NavyHeaderBg = Color(0xFF072044)
private val NavyCardBg = Color(0xFF132D52)
private val NavyCardBorder = Color(0x33FFFFFF)
private val LightCardBg = Color(0xFFE2E8F0)
private val TextPrimaryNavy = Color(0xFF072044)
private val TextSubtleNavy = Color(0xFF64748B)
private val TextHeaderSubtle = Color(0xFFBAC7D5)
private val TextCategoryName = Color(0xFF334155)

@Composable
fun DashboardScreen(
    onNavigateToGoals: () -> Unit,
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},

    onNavigateToChatBot: () -> Unit = {},



    onLogExpense: () -> Unit = onAddExpense,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val currencyFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    }

    val balanceCurrencyFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val context = LocalContext.current
    var showSavingsReviewDialog by remember { mutableStateOf(false) }

    val displayBalance = if (uiState.totalBalanceLKR >= 0) {
        "LKR ${balanceCurrencyFormat.format(uiState.totalBalanceLKR)}"
    } else {
        "-LKR ${balanceCurrencyFormat.format(kotlin.math.abs(uiState.totalBalanceLKR))}"
    }

    val displayIncome = balanceCurrencyFormat.format(uiState.totalConfirmedIncomeLKR)
    val displaySpent = balanceCurrencyFormat.format(uiState.totalExpensesLKR)
    val displayFirstName = uiState.userName
        .trim()
        .split("\\s+".toRegex())
        .firstOrNull { it.isNotBlank() }
        ?: "User"















    // Root Box enables the scrollable content and the floating AI Copilot button to overlay seamlessly
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyHeaderBg)
                .verticalScroll(scrollState)
        ) {



        // --- 1. Top Navy Section (Greeting + Nexus Logo + Total Amount Card) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Row (Avatar + Good Morning on left, Nexus Logo on right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E3A8A))
                            .border(1.5.dp, Color(0xFF60A5FA), CircleShape)
                            .clickable(onClick = onNavigateToProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!uiState.userPhotoUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = uiState.userPhotoUrl,
                                contentDescription = "Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = displayFirstName.firstOrNull()?.uppercase() ?: "U",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column {
                        Text(
                            text = uiState.greeting.ifBlank { "Good Morning," },
                            color = TextHeaderSubtle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = displayFirstName,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                NexusBrandLogo(modifier = Modifier.height(50.dp))
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Total Amount Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCardBg),
                border = BorderStroke(1.dp, NavyCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "Total Amount",
                        color = Color(0xFFA0B2C6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = displayBalance,
                        color = Color.White,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "▼ Income LKR $displayIncome",
                            color = TextHeaderSubtle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "▲ Spent LKR $displaySpent",
                            color = TextHeaderSubtle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (uiState.rolledOverSavingsLKR > 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(15.dp)
                                )

                                Text(
                                    text = "Rolled Over from Last Month: LKR ${balanceCurrencyFormat.format(uiState.rolledOverSavingsLKR)}",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // End-of-Month Savings Allocation Action Banner
            // Compact, elegant banner appearing at the end of the month or early next month.
            // When allocation is done, the banner disappears completely.
            if (uiState.showSavingsAllocationBanner && uiState.allocationSavingsLKR > 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSavingsReviewDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0D2847),
                    border = BorderStroke(1.dp, Color(0xFF1E4E7E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF059669)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val monthText = if (uiState.allocationMonthLabel.isNotBlank()) " (${uiState.allocationMonthLabel})" else ""
                                Text(
                                    text = "End-of-Month Savings$monthText",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF059669)
                                ) {
                                    Text(
                                        text = "REVIEW",
                                        color = Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "LKR %,.0f".format(uiState.allocationSavingsLKR),
                                    color = Color(0xFF34D399),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "•",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "Tap to allocate % to Goals & roll over",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFF93C5FD),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Offline Mode Indicator Banner
            if (uiState.isOffline) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
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
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFDE68A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline Mode",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(16.dp)
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
                                text = "Showing local cached balances. All new logs will sync automatically when reconnected.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. Main Themed Content Sheet ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section Title: Menu
                Text(
                    text = "Menu",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                // 4 Action Buttons in a Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Income
                    WireframeMenuButton(
                        label = "Income",
                        iconContent = {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Income",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = onAddIncome
                    )

                    // Expenses
                    WireframeMenuButton(
                        label = "Expenses",
                        iconContent = {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Expenses",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = onAddExpense
                    )

                    // Goal (Concentric Bullseye target icon)
                    WireframeMenuButton(
                        label = "Goal",
                        iconContent = { BullseyeIcon() },
                        onClick = onNavigateToGoals
                    )

                    // Reports (Three vertical bars icon)
                    WireframeMenuButton(
                        label = "Reports",
                        iconContent = { BarChartIcon() },
                        onClick = onNavigateToReports
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Card 1: Income vs Expenses — Circular Progress Bar (FR18)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Income vs Expenses",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Circular Income vs Expenses Graph
                        CircularSpendingProgressBar(
                            totalIncome = uiState.totalConfirmedIncomeLKR,
                            totalExpenses = uiState.totalExpensesLKR
                        )
                    }
                }

                // Card 2: Financial Goal (Priority Goals only — displayed only if ongoing priority goals exist)
                val priorityGoals = uiState.priorityGoals.filter { item ->
                    val isAchieved = item.calculation.isCompleted ||
                        item.calculation.remainingAmountLKR <= 0.0 ||
                        item.goal.openingSavingsLKR >= item.goal.targetAmountLKR
                    val isOverdue = item.calculation.isOverdue && item.calculation.daysRemaining < 0L

                    !isAchieved && !isOverdue
                }

                if (priorityGoals.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToGoals() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (priorityGoals.size == 1) "Priority Goal" else "Priority Goals",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = "HIGH PRIORITY",
                                        color = Color(0xFFDC2626),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            priorityGoals.forEachIndexed { index, item ->

                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.8.dp)

                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                val goal = item.goal
                                val calc = item.calculation
                                val progressFraction = calc.progressFraction.coerceIn(0f, 1f)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = goal.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Text(
                                        text = "Target: LKR ${currencyFormat.format(goal.targetAmountLKR)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    if (progressFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = progressFraction)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val progressPercentStr = if (calc.progressPercentage >= 100.0 || calc.isCompleted) "100%" else "%.0f%%".format(calc.progressPercentage.coerceIn(0.0, 100.0))
                                val needAmountStr = "Need LKR ${currencyFormat.format(calc.remainingAmountLKR)}"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$progressPercentStr complete",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = needAmountStr,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Deadline & Days Remaining Row
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
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )

                                        Text(
                                            text = "Deadline: ${dateFormat.format(goal.targetDate)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (calc.daysRemaining < 0) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = calc.daysRemainingText,
                                            color = if (calc.daysRemaining < 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onPrimary,
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

                // Card 3: Expenses by Category Visualization (FR19 / Order 8)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Expenses by Category",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = uiState.selectedMonthLabel.ifBlank { "Current Month" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (uiState.categorySpendings.isNotEmpty()) {
                                Text(
                                    text = "${uiState.categorySpendings.size} categories",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Explicit Empty State UI when user has 0 expenses this month
                        if (uiState.categorySpendings.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No expenses recorded this month",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "+ Tap here to log an expense",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onLogExpense() }
                                )
                            }
                        } else {
                            CategoryExpenseBarChart(
                                items = uiState.categorySpendings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Card 4: Income Sources Breakdown Chart (FR3, FR20)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Income by Source",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = uiState.selectedMonthLabel.ifBlank { "Current Month" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (uiState.incomeSources.isNotEmpty()) {
                                Text(
                                    text = "${uiState.incomeSources.size} streams",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (uiState.incomeSources.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No confirmed income recorded this month",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "+ Tap here to log income",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onAddIncome() }
                                )
                            }
                        } else {
                            IncomeSourceBarChart(
                                items = uiState.incomeSources,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Generous bottom clearance ensures all scrolled cards are completely visible above bottom navigation bar
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }



    // Modern Circular Floating AI Copilot Action Icon featuring NEXUS brand mark
    ModernAiCopilotFab(
        onClick = onNavigateToChatBot,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 24.dp)
    )
}







    if (showSavingsReviewDialog && uiState.showSavingsAllocationBanner && uiState.allocationSavingsLKR > 0.0) {
        SavingsAllocationDialog(
            totalSavings = uiState.allocationSavingsLKR,
            confirmedIncome = uiState.allocationIncomeLKR,
            totalExpenses = uiState.allocationExpensesLKR,
            monthLabel = uiState.allocationMonthLabel,
            nextMonthLabel = uiState.allocationTargetMonthLabel,
            onDismiss = { showSavingsReviewDialog = false },
            onConfirm = { percent ->
                viewModel.allocateMonthlySavings(percent) { contrib, roll ->
                    val target = uiState.allocationTargetMonthLabel.ifBlank { "next month" }
                    Toast.makeText(
                        context,
                        "LKR %,.0f sent to Goals, LKR %,.0f rolled over to $target".format(contrib, roll),
                        Toast.LENGTH_LONG
                    ).show()
                }
                showSavingsReviewDialog = false
            }
        )
    }
}

/**
 * Modern Circular Floating Action Button featuring the NEXUS icon mark
 * for opening the NEXUS AI Copilot from the Dashboard.
 */
@Composable
private fun ModernAiCopilotFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = Color(0xFF0F172A),
                spotColor = Color(0xFF38BDF8)
            )
            .clip(CircleShape),
        color = Color(0xFF072044),
        border = BorderStroke(2.dp, Color(0xFF38BDF8))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.nexus_icon_mark),
                contentDescription = "NEXUS AI Copilot",
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**

 * Circular progress bar component visually comparing Total Income vs Total Expenses.
 */

@Composable
private fun CircularSpendingProgressBar(
    totalIncome: Double,
    totalExpenses: Double,
    modifier: Modifier = Modifier
) {
    val total = totalIncome + totalExpenses
    val incomePercent = if (total > 0) ((totalIncome / total) * 100).roundToInt() else 0
    val expensePercent = if (total > 0) (100 - incomePercent) else 0
    val hasData = total > 0
    val trackColor = MaterialTheme.colorScheme.surface
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

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

                // 1. Full track
                drawCircle(
                    color = trackColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                // Only draw progress arcs if there is transaction data
                if (hasData) {
                    val expenseSweep = (expensePercent / 100f) * 360f
                    val incomeSweep = (incomePercent / 100f) * 360f

                    // 2. Expense arc
                    if (expenseSweep > 0f) {
                        drawArc(
                            color = expenseColor,
                            startAngle = -90f,
                            sweepAngle = expenseSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // 3. Income arc
                    if (incomeSweep > 0f) {
                        drawArc(
                            color = incomeColor,
                            startAngle = -90f + expenseSweep,
                            sweepAngle = incomeSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Center Content inside the circular progress bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (hasData) "$incomePercent%" else "0%",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Income",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Ratio Subtitle
        Text(
            text = if (hasData) {
                "$incomePercent% Income | $expensePercent% Expenses"
            } else {
                "No income or expenses recorded this month"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**

 * Menu Action Button matching wireframe: Dark navy squircle with centered icon.
 */

@Composable
private fun WireframeMenuButton(
    label: String,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**

 * Concentric target/bullseye icon matching the wireframe Goal button.
 */

@Composable
private fun BullseyeIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)

        // Outer ring
        drawCircle(
            color = color,
            radius = 8.5.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Inner dot
        drawCircle(
            color = color,
            radius = 3.5.dp.toPx(),
            center = center
        )
    }
}

/**

 * 3 Vertical bars icon matching the wireframe Reports button.
 */

@Composable
private fun BarChartIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.6.dp.toPx()
        val bottomY = size.height - 4.dp.toPx()
        val x1 = size.width * 0.28f
        val x2 = size.width * 0.50f
        val x3 = size.width * 0.72f

        // Bar 1 (left)
        drawLine(color, Offset(x1, bottomY - 8.dp.toPx()), Offset(x1, bottomY), stroke, StrokeCap.Round)

        // Bar 2 (center, tallest)
        drawLine(color, Offset(x2, bottomY - 14.dp.toPx()), Offset(x2, bottomY), stroke, StrokeCap.Round)

        // Bar 3 (right, medium)
        drawLine(color, Offset(x3, bottomY - 10.dp.toPx()), Offset(x3, bottomY), stroke, StrokeCap.Round)
    }
}

/**

 * App Logo loaded from drawables (R.drawable.app_logo) displayed on the Dashboard header.
 */

@Composable
private fun NexusBrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.app_logo),
        contentDescription = "Nexus App Logo",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun SavingsAllocationDialog(
    totalSavings: Double,
    confirmedIncome: Double,
    totalExpenses: Double,
    monthLabel: String = "",
    nextMonthLabel: String = "",
    onDismiss: () -> Unit,
    onConfirm: (percent: Double) -> Unit
) {
    var percentInput by remember { mutableStateOf("20") }

    val typedPercent = percentInput.toDoubleOrNull() ?: 0.0
    val validPercent = typedPercent.coerceIn(0.0, 100.0)
    val isValidInput = percentInput.isNotBlank() && (percentInput.toDoubleOrNull() != null) && typedPercent in 0.0..100.0
    val contributionAmount = totalSavings * (validPercent / 100.0)
    val rolloverAmount = totalSavings - contributionAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = null,
                    tint = Color(0xFF059669)
                )

                Text(
                    text = if (monthLabel.isNotBlank()) "End-of-Month Savings ($monthLabel)" else "End-of-Month Savings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Formula breakdown card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Monthly Savings = Income - Expenses",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "LKR %,.2f".format(totalSavings),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )

                        Text(
                            text = "Income LKR %,.2f  −  Spent LKR %,.2f".format(
                                confirmedIncome,
                                totalExpenses
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // User Types Percentage
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Enter percentage to allocate to Goal Contributions:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = percentInput,
                        onValueChange = { input ->

                            val filtered = input.filter { it.isDigit() || it == '.' }

                            val num = filtered.toDoubleOrNull()

                            if (filtered.isEmpty() || (num != null && num in 0.0..100.0) || filtered == ".") {
                                percentInput = filtered
                            }
                        },
                        placeholder = { Text("Enter % (e.g. 25)", fontSize = 14.sp) },
                        suffix = {
                            Text(
                                text = "%",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669),
                                fontSize = 16.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        isError = percentInput.isNotBlank() && (percentInput.toDoubleOrNull() == null || percentInput.toDouble() !in 0.0..100.0),
                        supportingText = {
                            Text(
                                text = "Type any percentage between 0% and 100%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                // Breakdown 1: Contribution to Goals
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFD1FAE5),
                    border = BorderStroke(1.dp, Color(0xFF34D399)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "To Goal Contribution (${"%.1f".format(validPercent).trimEnd('0').trimEnd('.')}%):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF065F46),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Added to Goal Contribution list",
                                fontSize = 10.sp,
                                color = Color(0xFF047857),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "LKR %,.2f".format(contributionAmount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }

                // Breakdown 2: Rollover to Next Month
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFDBEAFE),
                    border = BorderStroke(1.dp, Color(0xFF60A5FA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val rolloverPct = (100.0 - validPercent).coerceIn(0.0, 100.0)
                        val rolloverMonth = if (nextMonthLabel.isNotBlank()) nextMonthLabel else "Next Month"
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "$rolloverMonth Rollover (${"%.1f".format(rolloverPct).trimEnd('0').trimEnd('.')}%):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (nextMonthLabel.isNotBlank()) "Rolled over to $nextMonthLabel" else "Rolled over to next month",
                                fontSize = 10.sp,
                                color = Color(0xFF1D4ED8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "LKR %,.2f".format(rolloverAmount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(validPercent) },
                enabled = isValidInput,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("Confirm & Allocate", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
