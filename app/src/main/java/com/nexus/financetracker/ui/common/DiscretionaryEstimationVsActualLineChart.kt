package com.nexus.financetracker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.financetracker.ui.reports.DiscretionaryCategoryComparison

private val NavyEstimationColor = Color(0xFF072044) // Navy Blue for estimation value line
private val RedActualColor = Color(0xFFDC2626) // Red for actual expense line
private val ChartMuted = Color(0xFF64748B)
private val ChartGrid = Color(0xFFCBD5E1)
private val LabelHeadroom = 24.dp
private val PlotAreaHeight = 150.dp
private val ChartAreaHeight = LabelHeadroom + PlotAreaHeight // 174.dp

/**
 * Line Chart comparing Estimation Value (Navy Blue) vs Actual Expense (Red)
 * for Discretionary Categories.
 *
 * - Displayed exclusively in Reports (not in Dashboard).
 * - X-Axis: Discretionary Category name
 * - Y-Axis: Expense Value (LKR)
 * - Estimation Line: Navy Blue (#072044)
 * - Actual Expense Line: Red (#DC2626)
 */
@Composable
fun DiscretionaryEstimationVsActualLineChart(
    items: List<DiscretionaryCategoryComparison>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val maxAmount = remember(items) {
        val maxVal = items.maxOfOrNull { maxOf(it.estimatedAmountLKR, it.actualExpenseLKR) } ?: 0.0
        if (maxVal > 0) maxVal * 1.15 else 10000.0
    }

    val yTop = maxAmount
    val yMid = maxAmount / 2.0

    val itemSlotWidth = 84.dp
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        // Legend at top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navy Blue Line: Estimation Value
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 3.dp)
                        .background(NavyEstimationColor, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(NavyEstimationColor, CircleShape)
                )
                Text(
                    text = "Estimated Value",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NavyEstimationColor
                )
            }

            // Red Line: Actual Expense
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 3.dp)
                        .background(RedActualColor, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(RedActualColor, CircleShape)
                )
                Text(
                    text = "Actual Expense",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RedActualColor
                )
            }
        }

        // Y-Axis title
        Text(
            text = "Expense Value (LKR)",
            color = ChartMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Main Chart Row (Y-Axis ticks on left + Line Plot Area with horizontal scroll on right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartAreaHeight)
        ) {
            // Y-Axis tick labels on the left aligned with the grid lines
            Box(
                modifier = Modifier
                    .width(42.dp)
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

            // Scrollable Line Chart canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Background grid lines spanning full width
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val topY = LabelHeadroom.toPx()
                    val bottomY = size.height - 1.dp.toPx()
                    val midY = topY + (bottomY - topY) / 2f
                    val lineColor = ChartGrid.copy(alpha = 0.45f)
                    val strokeW = 1.dp.toPx()

                    drawLine(lineColor, Offset(0f, topY), Offset(size.width, topY), strokeWidth = strokeW)
                    drawLine(lineColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = strokeW)
                    drawLine(NavyEstimationColor, Offset(0f, bottomY), Offset(size.width, bottomY), strokeWidth = strokeW * 2f)
                }

                val totalCanvasWidth = maxOf(itemSlotWidth * items.size, 260.dp)

                // Scrollable container for the line graph
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .width(totalCanvasWidth)
                            .fillMaxHeight()
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val topY = LabelHeadroom.toPx()
                            val bottomY = size.height - 1.dp.toPx()
                            val plotHeightPx = bottomY - topY
                            val slotWidthPx = size.width / items.size.coerceAtLeast(1)

                            val estPoints = mutableListOf<Offset>()
                            val actPoints = mutableListOf<Offset>()

                            items.forEachIndexed { index, item ->
                                val x = (index * slotWidthPx) + (slotWidthPx / 2f)

                                val estFraction = (item.estimatedAmountLKR / maxAmount).toFloat().coerceIn(0f, 1f)
                                val estY = bottomY - (plotHeightPx * estFraction)
                                estPoints.add(Offset(x, estY))

                                val actFraction = (item.actualExpenseLKR / maxAmount).toFloat().coerceIn(0f, 1f)
                                val actY = bottomY - (plotHeightPx * actFraction)
                                actPoints.add(Offset(x, actY))
                            }

                            // 1. Draw Navy Blue Line for Estimation Value
                            if (estPoints.size > 1) {
                                val estPath = Path().apply {
                                    moveTo(estPoints[0].x, estPoints[0].y)
                                    for (i in 1 until estPoints.size) {
                                        lineTo(estPoints[i].x, estPoints[i].y)
                                    }
                                }
                                drawPath(
                                    path = estPath,
                                    color = NavyEstimationColor,
                                    style = Stroke(
                                        width = 3.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // 2. Draw Red Line for Actual Expenses
                            if (actPoints.size > 1) {
                                val actPath = Path().apply {
                                    moveTo(actPoints[0].x, actPoints[0].y)
                                    for (i in 1 until actPoints.size) {
                                        lineTo(actPoints[i].x, actPoints[i].y)
                                    }
                                }
                                drawPath(
                                    path = actPath,
                                    color = RedActualColor,
                                    style = Stroke(
                                        width = 3.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // 3. Draw Data Point Dots (Navy for estimation, Red for actual)
                            estPoints.forEach { point ->
                                drawCircle(
                                    color = NavyEstimationColor,
                                    radius = 5.dp.toPx(),
                                    center = point
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = point
                                )
                            }

                            actPoints.forEach { point ->
                                drawCircle(
                                    color = RedActualColor,
                                    radius = 5.dp.toPx(),
                                    center = point
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = point
                                )
                            }
                        }
                    }
                }
            }
        }

        // X-Axis Category Names aligned with points
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(42.dp)) // Offset matching Y-axis ticks

            val totalCanvasWidth = maxOf(itemSlotWidth * items.size, 260.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.width(totalCanvasWidth),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    items.forEach { item ->
                        Box(
                            modifier = Modifier
                                .width(itemSlotWidth)
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = item.categoryName,
                                color = NavyEstimationColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // X-Axis Title
        Text(
            text = "Discretionary Category",
            color = ChartMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

