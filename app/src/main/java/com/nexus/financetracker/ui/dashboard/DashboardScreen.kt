package com.nexus.financetracker.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.financetracker.R
import java.text.NumberFormat
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

    val displayBalance = if (uiState.netResultLKR >= 0) {
        "LKR ${balanceCurrencyFormat.format(uiState.netResultLKR)}"
    } else {
        "-LKR ${balanceCurrencyFormat.format(kotlin.math.abs(uiState.netResultLKR))}"
    }

    val displayIncome = balanceCurrencyFormat.format(uiState.totalConfirmedIncomeLKR)
    val displaySpent = balanceCurrencyFormat.format(uiState.totalExpensesLKR)

    val displayFirstName = uiState.userName
        .trim()
        .split("\\s+".toRegex())
        .firstOrNull { it.isNotBlank() }
        ?: "User"



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
                        text = uiState.greeting.ifBlank { "Good Morning," },
                        color = TextHeaderSubtle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayFirstName,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                NexusBrandLogo(modifier = Modifier.height(52.dp))
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        onClick = onNavigateToReports
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

                        // Circular Income vs Expenses Graph
                        CircularSpendingProgressBar(
                            totalIncome = uiState.totalConfirmedIncomeLKR,
                            totalExpenses = uiState.totalExpensesLKR
                        )
                    }
                }

                // Card 2: Financial Goal (FR15, FR17)
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
                        val activeGoal = uiState.activeGoal
                        val calc = uiState.activeGoalCalculation

                        if (activeGoal != null) {
                            Text(
                                text = "Goal · ${activeGoal.title}",
                                color = TextPrimaryNavy,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Progress Bar
                            val progressFraction = calc?.progressFraction?.coerceIn(0f, 1f) ?: 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            ) {
                                if (progressFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = progressFraction)
                                            .fillMaxHeight()
                                            .background(TextPrimaryNavy)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val progressPercentStr = calc?.let { "%.0f%%".format(it.progressPercentage) } ?: "0%"
                            val needAmountStr = calc?.let { "Need LKR ${currencyFormat.format(it.remainingAmountLKR)}" } ?: ""
                            Text(
                                text = if (needAmountStr.isNotBlank()) "$progressPercentStr complete | $needAmountStr" else "$progressPercentStr complete",
                                color = TextSubtleNavy,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Financial Goal",
                                color = TextPrimaryNavy,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "No active goal set",
                                color = TextSubtleNavy,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "+ Tap here to set a savings goal",
                                color = TextPrimaryNavy,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                            if (uiState.categorySpendings.isNotEmpty()) {
                                Text(
                                    text = "${uiState.categorySpendings.size} categories",
                                    color = TextSubtleNavy,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

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
                            uiState.categorySpendings.forEachIndexed { index, item ->
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
                            if (uiState.incomeSources.isNotEmpty()) {
                                Text(
                                    text = "${uiState.incomeSources.size} streams",
                                    color = TextSubtleNavy,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (uiState.incomeSources.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No confirmed income recorded this month",
                                    color = TextSubtleNavy,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "+ Tap here to log income",
                                    color = TextPrimaryNavy,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onAddIncome() }
                                )
                            }
                        } else {
                            uiState.incomeSources.forEachIndexed { index, sourceItem ->
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
                }

                // Generous bottom clearance ensures all scrolled cards are completely visible above bottom navigation bar
                Spacer(modifier = Modifier.height(100.dp))
            }
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

                // Only draw progress arcs if there is transaction data
                if (hasData) {
                    val expenseSweep = (expensePercent / 100f) * 360f
                    val incomeSweep = (incomePercent / 100f) * 360f

                    // 2. Expense arc (Muted blue-gray)
                    if (expenseSweep > 0f) {
                        drawArc(
                            color = Color(0xFFBAC7D5),
                            startAngle = -90f,
                            sweepAngle = expenseSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // 3. Income arc (Deep navy main progress indicator)
                    if (incomeSweep > 0f) {
                        drawArc(
                            color = TextPrimaryNavy,
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
                    color = TextPrimaryNavy,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Income",
                    color = TextSubtleNavy,
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
