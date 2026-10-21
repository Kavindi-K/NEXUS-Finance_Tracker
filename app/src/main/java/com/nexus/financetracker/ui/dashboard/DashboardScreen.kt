package com.nexus.financetracker.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import java.util.Locale

// Design System Colors matching exact Figma Wireframe
private val NavyHeaderBg = Color(0xFF072044)
private val NavyCardBg = Color(0xFF132D52)
private val NavyCardBorder = Color(0x33FFFFFF)
private val LightCardBg = Color(0xFFE2E8F0)
private val TextPrimaryNavy = Color(0xFF072044)
private val TextSubtleNavy = Color(0xFF64748B)
private val TextHeaderSubtle = Color(0xFFBAC7D5)
private val TextCategoryName = Color(0xFF334155)

/**
 * Dashboard screen matching the exact NEXUS Finance Tracker wireframe design.
 */
@Composable
fun DashboardScreen(
    onNavigateToGoals: () -> Unit,
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currencyFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    }

    // Dynamic vs Mockup fallback bindings
    val hasRealData = uiState.totalConfirmedIncomeLKR > 0 || uiState.totalExpensesLKR > 0

    val displayBalance = if (hasRealData) {
        "LKR ${currencyFormat.format(uiState.totalConfirmedIncomeLKR)}"
    } else {
        "LKR 184,250"
    }

    val displayIncome = if (hasRealData) {
        currencyFormat.format(uiState.totalConfirmedIncomeLKR)
    } else {
        "187,500"
    }

    val displaySpent = if (hasRealData) {
        currencyFormat.format(uiState.totalExpensesLKR)
    } else {
        "96,300"
    }

    val displayDiscretionaryPercent = if (hasRealData && uiState.totalExpensesLKR > 0) {
        uiState.discretionaryPercentage
    } else {
        63
    }

    val displayCommittedPercent = if (hasRealData && uiState.totalExpensesLKR > 0) {
        uiState.committedPercentage
    } else {
        37
    }

    val goalTitle = uiState.activeGoal?.title ?: "MacBook Pro M4"
    val goalProgressPercent = uiState.activeGoalCalculation?.let {
        "%.0f%%".format(it.progressPercentage)
    } ?: "17%"
    val goalNeedAmount = uiState.activeGoalCalculation?.let {
        "Need LKR ${currencyFormat.format(it.remainingAmountLKR)}"
    } ?: "Need LKR 407,600"
    val goalProgressFraction = uiState.activeGoalCalculation?.progressFraction ?: 0.17f

    val categoriesToShow = if (uiState.categorySpendings.isNotEmpty()) {
        uiState.categorySpendings
    } else {
        listOf(
            CategorySpending("Rent", 36500.0, 0.68f),
            CategorySpending("Food and dining", 24000.0, 0.48f),
            CategorySpending("Transport", 11800.0, 0.24f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyHeaderBg)
    ) {
        // --- 1. Top Navy Section (Greeting + Nexus Logo + Total Amount Card) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Row (Good Morning + Nexus Node Logo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good Morning,",
                        color = TextHeaderSubtle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = uiState.userName.ifBlank { "Kavindu" },
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                NexusBrandLogo(modifier = Modifier.size(34.dp))
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = displayBalance,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "▲ Income $displayIncome",
                            color = TextHeaderSubtle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "▼ Spent $displaySpent",
                            color = TextHeaderSubtle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 2. Main White Content Sheet ---
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section Title: Menu
                Text(
                    text = "Menu",
                    color = TextPrimaryNavy,
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
                                tint = Color.White,
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
                                tint = Color.White,
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
                        onClick = onNavigateToGoals
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Card 1: Income vs Expenses
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LightCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Income vs Expenses",
                            color = TextPrimaryNavy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom Progress Bar (Track: White, Fill: Deep Navy)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (displayDiscretionaryPercent / 100f).coerceIn(0.05f, 1f))
                                    .fillMaxHeight()
                                    .background(TextPrimaryNavy)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "$displayDiscretionaryPercent% Discretionary | $displayCommittedPercent% Committed",
                            color = TextSubtleNavy,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Card 2: Goal · MacBook Pro M4
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToGoals() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LightCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Goal · $goalTitle",
                            color = TextPrimaryNavy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = goalProgressFraction.coerceIn(0.05f, 1f))
                                    .fillMaxHeight()
                                    .background(TextPrimaryNavy)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "$goalProgressPercent complete | $goalNeedAmount",
                            color = TextSubtleNavy,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Card 3: Spending by Category
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LightCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Spending by Category",
                            color = TextPrimaryNavy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        categoriesToShow.forEachIndexed { index, item ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    color = TextCategoryName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Text(
                                    text = currencyFormat.format(item.amountLKR),
                                    color = TextPrimaryNavy,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                        ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = item.fraction.coerceIn(0.05f, 1f))
                                        .fillMaxHeight()
                                        .background(TextPrimaryNavy)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Menu Action Button matching wireframe: Dark navy squircle with white centered icon.
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
                .background(TextPrimaryNavy),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            color = TextPrimaryNavy,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Concentric target/bullseye icon matching the wireframe Goal button.
 */
@Composable
private fun BullseyeIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        // Outer ring
        drawCircle(
            color = Color.White,
            radius = 8.5.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        // Inner dot
        drawCircle(
            color = Color.White,
            radius = 3.5.dp.toPx(),
            center = center
        )
    }
}

/**
 * 3 Vertical bars icon matching the wireframe Reports button.
 */
@Composable
private fun BarChartIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.6.dp.toPx()
        val bottomY = size.height - 4.dp.toPx()
        val x1 = size.width * 0.28f
        val x2 = size.width * 0.50f
        val x3 = size.width * 0.72f

        // Bar 1 (left)
        drawLine(Color.White, Offset(x1, bottomY - 8.dp.toPx()), Offset(x1, bottomY), stroke, StrokeCap.Round)
        // Bar 2 (center, tallest)
        drawLine(Color.White, Offset(x2, bottomY - 14.dp.toPx()), Offset(x2, bottomY), stroke, StrokeCap.Round)
        // Bar 3 (right, medium)
        drawLine(Color.White, Offset(x3, bottomY - 10.dp.toPx()), Offset(x3, bottomY), stroke, StrokeCap.Round)
    }
}

/**
 * Nexus 4-Node brand mark matching wireframe top-right header:
 * 4 hollow ring nodes connected by thick rounded pipes.
 */
@Composable
private fun NexusBrandLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(34.dp)) {
        val pad = 6.dp.toPx()
        val left = pad
        val right = size.width - pad
        val top = pad
        val bottom = size.height - pad

        val outerRadius = 5.2.dp.toPx()
        val innerRadius = 2.4.dp.toPx()
        val stroke = 3.2.dp.toPx()

        // Connecting rounded loop / lines
        drawLine(Color.White, Offset(left, top), Offset(right, top), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(left, top), Offset(left, bottom), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(right, top), Offset(right, bottom), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(left, bottom), Offset(right, bottom), stroke, StrokeCap.Round)

        // 4 Connected Donut Nodes
        val nodes = listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(left, bottom),
            Offset(right, bottom)
        )

        nodes.forEach { node ->
            drawCircle(Color.White, outerRadius, node)
            drawCircle(NavyHeaderBg, innerRadius, node)
        }
    }
}
