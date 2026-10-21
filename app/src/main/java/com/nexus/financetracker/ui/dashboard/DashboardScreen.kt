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
 * Dashboard screen matching the NEXUS Finance Tracker wireframe design (Section 6.2).
 * Features:
 * - Unified root-level scrolling allowing seamless scroll through all cards and charts
 * - Circular Progress Bar for Income vs Expenses (FR18)
 * - Scrollable full category spending breakdown (FR19)
 * - Income by source breakdown chart (FR3, FR20)
 * - Empty state support (FR22)
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
    val scrollState = rememberScrollState()
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

    // Comprehensive category list: reveals all categories when scrolling down
    val categoriesToShow = if (uiState.categorySpendings.isNotEmpty()) {
        uiState.categorySpendings
    } else {
        listOf(
            CategorySpending("Rent", 36500.0, 1.0f, 38),
            CategorySpending("Food and dining", 24000.0, 0.66f, 25),
            CategorySpending("Transport", 11800.0, 0.32f, 12),
            CategorySpending("Subscriptions", 8500.0, 0.23f, 9),
            CategorySpending("Utilities", 7200.0, 0.20f, 7),
            CategorySpending("Shopping", 5400.0, 0.15f, 5),
            CategorySpending("Healthcare", 2900.0, 0.08f, 3),
            CategorySpending("Entertainment", 2200.0, 0.06f, 2)
        )
    }

    // Income sources list
    val incomeSourcesToShow = if (uiState.incomeSources.isNotEmpty()) {
        uiState.incomeSources
    } else {
        listOf(
            IncomeSourceSpending("Base Salary", 140000.0, 1.0f),
            IncomeSourceSpending("Freelance", 35000.0, 0.25f),
            IncomeSourceSpending("AdSense (USD)", 14400.0, 0.10f),
            IncomeSourceSpending("Crypto Net P&L", -1900.0, 0.05f)
        )
    }

    // Root-level scrollable Column ensures touch and mouse-wheel scrolling works everywhere
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
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color.White
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

                // Card 1: Income vs Expenses — Circular Progress Bar (FR18)
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Circular Progress Bar Component
                        CircularSpendingProgressBar(
                            discretionaryPercent = displayDiscretionaryPercent,
                            committedPercent = displayCommittedPercent
                        )
                    }
                }

                // Card 2: Goal · MacBook Pro M4 (FR15, FR17)
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

                // Card 3: Spending by Category Visualization (FR19 / Order 8)
                // Fully scrollable: reveals all categories when user scrolls down
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Spending by Category",
                                color = TextPrimaryNavy,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (categoriesToShow.isNotEmpty()) {
                                Text(
                                    text = "${categoriesToShow.size} categories",
                                    color = TextSubtleNavy,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Explicit Empty State UI (FR22) when user has registered data but 0 expenses this month
                        if (uiState.categorySpendings.isEmpty() && hasRealData && uiState.totalExpensesLKR == 0.0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No expenses recorded this month",
                                    color = TextSubtleNavy,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "+ Tap here to log an expense",
                                    color = TextPrimaryNavy,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onAddExpense() }
                                )
                            }
                        } else {
                            // Ranked category spending list revealing all categories on scroll down (FR19)
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
                }

                // Card 4: Income Sources Breakdown Chart (FR3, FR20)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Income by Source",
                                color = TextPrimaryNavy,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${incomeSourcesToShow.size} streams",
                                color = TextSubtleNavy,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        incomeSourcesToShow.forEachIndexed { index, sourceItem ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sourceItem.source,
                                    color = TextCategoryName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Text(
                                    text = if (sourceItem.amountLKR >= 0) {
                                        currencyFormat.format(sourceItem.amountLKR)
                                    } else {
                                        "-${currencyFormat.format(kotlin.math.abs(sourceItem.amountLKR))}"
                                    },
                                    color = if (sourceItem.amountLKR >= 0) TextPrimaryNavy else MaterialTheme.colorScheme.error,
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
                                        .fillMaxWidth(fraction = sourceItem.fraction.coerceIn(0.05f, 1f))
                                        .fillMaxHeight()
                                        .background(if (sourceItem.amountLKR >= 0) TextPrimaryNavy else MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                }

                // Generous bottom clearance ensures all scrolled cards are completely visible above bottom navigation bar
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

/**
 * Circular progress bar component visually comparing Discretionary vs Committed ratios (FR18).
 */
@Composable
private fun CircularSpendingProgressBar(
    discretionaryPercent: Int,
    committedPercent: Int,
    modifier: Modifier = Modifier
) {
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

                // 1. Full track (White circle track)
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                // 2. Committed arc (Muted blue-gray)
                val committedSweep = (committedPercent / 100f) * 360f
                drawArc(
                    color = Color(0xFFBAC7D5),
                    startAngle = -90f,
                    sweepAngle = committedSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 3. Discretionary arc (Deep navy main progress indicator)
                val discretionarySweep = (discretionaryPercent / 100f) * 360f
                drawArc(
                    color = TextPrimaryNavy,
                    startAngle = -90f + committedSweep,
                    sweepAngle = discretionarySweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Center Content inside the circular progress bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$discretionaryPercent%",
                    color = TextPrimaryNavy,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Discretionary",
                    color = TextSubtleNavy,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Ratio Subtitle
        Text(
            text = "$discretionaryPercent% Discretionary | $committedPercent% Committed",
            color = TextSubtleNavy,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
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
