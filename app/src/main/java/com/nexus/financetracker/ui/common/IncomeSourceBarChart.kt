package com.nexus.financetracker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
private val LabelHeadroom = 20.dp
private val PlotAreaHeight = 135.dp
private val ChartAreaHeight = LabelHeadroom + PlotAreaHeight // 155.dp

/**
 * Formats monetary amounts compactly for chart axis and bar values in K (e.g., 32K, 0.6K, 1.2M).
 */
fun formatCompactLkr(amount: Double): String {
    if (amount <= 0.0) return "0K"
    return when {
        amount >= 1_000_000 -> {
            val inM = amount / 1_000_000.0
            if (inM % 1.0 == 0.0) {
                String.format(Locale.US, "%.0fM", inM)
            } else {
                String.format(Locale.US, "%.1fM", inM)
            }
        }
        else -> {
            val inK = amount / 1_000.0
            if (inK % 1.0 == 0.0) {
                String.format(Locale.US, "%.0fK", inK)
            } else {
                val formatted = String.format(Locale.US, "%.1fK", inK)
                if (formatted == "0.0K" && amount > 0.0) {
                    String.format(Locale.US, "%.2fK", inK)
                } else {
                    formatted
                }
            }
        }
    }
}

/**
 * Vertical Bar Chart for Income by Source:
 * - X-Axis: Income Source name
 * - Y-Axis: Income Value (LKR)
 *
 * Every bar height is precisely mapped to its exact monetary amount on the Y-axis:
 * When an income source is at the maximum (e.g. 32K), the top of the bar meets the 32K grid line.
 */
@Composable
fun IncomeSourceBarChart(
    items: List<IncomeSourceSpending>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val sortedItems = remember(items) {
        items.sortedWith(
            compareBy<IncomeSourceSpending> { IncomeSource.getSourceOrder(it.source) }
                .thenByDescending { it.amountLKR }
        )
    }

    val maxAmount = sortedItems.maxOfOrNull { it.amountLKR }?.coerceAtLeast(1.0) ?: 1.0
    val yTop = maxAmount
    val yMid = maxAmount / 2.0

    val scrollStateBars = rememberScrollState()
    val scrollStateLabels = rememberScrollState()

    LaunchedEffect(scrollStateBars.value) {
        if (scrollStateLabels.value != scrollStateBars.value) {
            scrollStateLabels.scrollTo(scrollStateBars.value)
        }
    }
    LaunchedEffect(scrollStateLabels.value) {
        if (scrollStateBars.value != scrollStateLabels.value) {
            scrollStateBars.scrollTo(scrollStateLabels.value)
        }
    }

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
                .height(ChartAreaHeight)
        ) {
            // Y-Axis tick labels on the left aligned with the grid lines
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = formatCompactLkr(yTop),
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(y = LabelHeadroom - 7.dp)
                )
                Text(
                    text = formatCompactLkr(yMid),
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(y = LabelHeadroom + (PlotAreaHeight / 2) - 7.dp)
                )
                Text(
                    text = "0K",
                    color = ChartMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(y = ChartAreaHeight - 14.dp)
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
                    val topY = LabelHeadroom.toPx()
                    val bottomY = size.height - 1.dp.toPx()
                    val midY = topY + (bottomY - topY) / 2f
                    val lineColor = ChartGrid.copy(alpha = 0.45f)
                    val strokeW = 1.dp.toPx()

                    // Top line (yTop)
                    drawLine(lineColor, Offset(0f, topY), Offset(size.width, topY), strokeWidth = strokeW)
                    // Mid line (yMid)
                    drawLine(lineColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = strokeW)
                    // Baseline (0)
                    drawLine(ChartNavy, Offset(0f, bottomY), Offset(size.width, bottomY), strokeWidth = strokeW * 2f)
                }

                // Vertical bars horizontally scrollable
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollStateBars)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    sortedItems.forEach { item ->
                        val amount = item.amountLKR.coerceAtLeast(0.0)
                        val fraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f)
                        val barHeight = if (amount > 0) maxOf(4.dp, PlotAreaHeight * fraction) else 0.dp

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

                            // Vertical bar reaching exact value proportional to the Y-axis
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
                .padding(start = 36.dp) // Offset matching Y-axis labels width
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollStateLabels)
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
