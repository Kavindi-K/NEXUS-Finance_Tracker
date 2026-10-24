package com.nexus.financetracker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.financetracker.ui.dashboard.IncomeSourceSpending
import java.util.Locale

private val ChartNavy = Color(0xFF072044)
private val ChartMuted = Color(0xFF64748B)
private val ChartGrid = Color(0xFFBAC7D5)

/**
 * Formats monetary amounts compactly for chart axis and bar values (e.g., 250K, 1.2M, 500).
 */
fun formatCompactLkr(amount: Double): String {
    return when {
        amount >= 1_000_000 -> String.format(Locale.US, "%.1fM", amount / 1_000_000.0)
        amount >= 1_000 -> {
            if (amount % 1_000 == 0.0) {
                String.format(Locale.US, "%.0fK", amount / 1_000.0)
            } else {
                String.format(Locale.US, "%.1fK", amount / 1_000.0)
            }
        }
        else -> String.format(Locale.US, "%.0f", amount)
    }
}

/**
 * Vertical Bar Chart for Income by Source:
 * - X-Axis: Income Source name
 * - Y-Axis: Income Value (LKR)
 */
@Composable
fun IncomeSourceBarChart(
    items: List<IncomeSourceSpending>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val maxAmount = items.maxOfOrNull { it.amountLKR }?.coerceAtLeast(1.0) ?: 1.0
    val yTop = maxAmount
    val yMid = maxAmount / 2.0
    val chartHeight = 150.dp

    Column(modifier = modifier) {
        // Y-Axis title
        Text(
            text = "Income (LKR)",
            color = ChartMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            // Y-Axis tick labels on the left
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatCompactLkr(yTop),
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatCompactLkr(yMid),
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "0",
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Chart plotting area with background grid lines and vertical bars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Background horizontal grid lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineColor = ChartGrid.copy(alpha = 0.45f)
                    val strokeW = 1.dp.toPx()
                    // Top line
                    drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = strokeW)
                    // Mid line
                    drawLine(lineColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = strokeW)
                    // Baseline (X-axis)
                    drawLine(ChartNavy, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), strokeWidth = strokeW * 2f)
                }

                // Vertical bars horizontally scrollable
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    items.forEach { item ->
                        val barFraction = (item.amountLKR / maxAmount).toFloat().coerceIn(0.04f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            // Amount label above bar
                            Text(
                                text = formatCompactLkr(item.amountLKR),
                                color = ChartNavy,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Vertical bar
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .fillMaxHeight(fraction = barFraction * 0.78f)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(ChartNavy)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // X-Axis source labels below the baseline
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 38.dp) // Offset for Y-axis labels width
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                items.forEach { item ->
                    Box(
                        modifier = Modifier.width(36.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = item.source,
                            color = ChartNavy,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // X-Axis Title
        Text(
            text = "Income Source →",
            color = ChartMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.End)
        )
    }
}
