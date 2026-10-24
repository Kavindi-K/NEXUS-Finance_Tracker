package com.nexus.financetracker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nexus.financetracker.domain.model.IncomeSource
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

    val sortedItems = remember(items) {
        items.map { item ->
            val cleanName = when {
                item.source.contains("salary", ignoreCase = true) -> "Salary"
                item.source.contains("freelance", ignoreCase = true) -> "Freelance"
                item.source.contains("crypto", ignoreCase = true) -> "Crypto"
                item.source.contains("adsense", ignoreCase = true) -> "AdSense"
                else -> "Other"
            }
            Pair(cleanName, item.amountLKR)
        }.groupBy({ it.first }, { it.second })
        .map { (name, amounts) ->
            val totalSum = amounts.sum()
            IncomeSourceSpending(
                source = name,
                amountLKR = totalSum,
                fraction = 1f
            )
        }.sortedWith(
            compareBy<IncomeSourceSpending> { IncomeSource.getSourceOrder(it.source) }
                .thenByDescending { it.amountLKR }
        )
    }

    val maxAmount = sortedItems.maxOfOrNull { it.amountLKR }?.coerceAtLeast(1.0) ?: 1.0
    val yTop = maxAmount
    val yMid = maxAmount / 2.0
    val labelAreaHeight = 20.dp
    val plotHeight = 120.dp
    val totalChartHeight = labelAreaHeight + plotHeight

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
                .height(totalChartHeight)
        ) {
            // Y-Axis tick labels on the left (aligned with grid lines)
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(totalChartHeight)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = formatCompactLkr(yTop),
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .offset(y = labelAreaHeight - 7.dp)
                        .align(Alignment.TopEnd)
                )
                Text(
                    text = formatCompactLkr(yMid),
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .offset(y = labelAreaHeight + plotHeight / 2f - 7.dp)
                        .align(Alignment.TopEnd)
                )
                Text(
                    text = "0",
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .offset(y = labelAreaHeight + plotHeight - 8.dp)
                        .align(Alignment.TopEnd)
                )
            }

            // Chart plotting area with background grid lines and vertical bars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalChartHeight)
            ) {
                // Background horizontal grid lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineColor = ChartGrid.copy(alpha = 0.45f)
                    val strokeW = 1.dp.toPx()
                    val topY = labelAreaHeight.toPx()
                    val midY = (labelAreaHeight + plotHeight / 2f).toPx()
                    val baseY = (labelAreaHeight + plotHeight).toPx()

                    // Top line (yTop)
                    drawLine(lineColor, Offset(0f, topY), Offset(size.width, topY), strokeWidth = strokeW)
                    // Mid line (yMid)
                    drawLine(lineColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = strokeW)
                    // Baseline (X-axis, 0)
                    drawLine(ChartNavy, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = strokeW * 1.5f)
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
                    sortedItems.forEach { item ->
                        val barFraction = (item.amountLKR / maxAmount).toFloat().coerceIn(0f, 1f)
                        val barHeight = (plotHeight * barFraction).coerceAtLeast(if (item.amountLKR > 0) 3.dp else 0.dp)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.height(totalChartHeight)
                        ) {
                            // Amount label above bar
                            Text(
                                text = formatCompactLkr(item.amountLKR),
                                color = ChartNavy,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            // Vertical bar reaching exact value on Y-axis
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(barHeight)
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
                .padding(start = 36.dp) // Offset for Y-axis labels width
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                sortedItems.forEach { item ->
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
            text = "Income Source",
            color = ChartMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.End)
        )
    }
}
