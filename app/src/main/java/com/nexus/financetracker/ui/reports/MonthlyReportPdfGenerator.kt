package com.nexus.financetracker.ui.reports

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nexus.financetracker.ui.dashboard.CategorySpending
import com.nexus.financetracker.ui.dashboard.IncomeSourceSpending
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native, offline PDF report generator for NEXUS Finance Tracker.
 * Produces an executive 3-page comprehensive financial intelligence report:
 * - Page 1: Executive Financial Summary, Cashflow Donut (with Graph Explanation), MoM Momentum Dual Bars (with Graph Explanation), Monthly Handling Assessment.
 * - Page 2: Month-to-Date (MTD) Cumulative Income Line Graph (with Graph Explanation), Cumulative Expenses Line Graph (with Graph Explanation), Next-Month Recommendations, Savings Goals.
 * - Page 3: Screen Charts: Expenses by Category Vertical Bar Chart (with Graph Explanation), Income by Source Vertical Bar Chart (with Graph Explanation), Discretionary Estimation vs Actual Dual Line Chart (with Graph Explanation).
 */
object MonthlyReportPdfGenerator {

    private const val PAGE_WIDTH = 595 // Standard A4 width (points)
    private const val PAGE_HEIGHT = 842 // Standard A4 height (points)
    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 559f
    private const val CONTENT_WIDTH = MARGIN_RIGHT - MARGIN_LEFT // 523f

    // Theme Colors
    private val COLOR_NAVY = Color.rgb(7, 32, 68)           // #072044 Primary Brand
    private val COLOR_DARK_TEXT = Color.rgb(15, 23, 42)      // #0F172A Body Dark
    private val COLOR_MUTED_TEXT = Color.rgb(100, 116, 139)  // #64748B Secondary Text
    private val COLOR_BORDER = Color.rgb(226, 232, 240)      // #E2E8F0 Card borders
    private val COLOR_CARD_BG = Color.rgb(248, 250, 252)     // #F8FAFC
    private val COLOR_TRACK = Color.rgb(241, 245, 249)       // #F1F5F9 Bar track
    private val COLOR_GREEN = Color.rgb(22, 163, 74)         // #16A34A Positive
    private val COLOR_GREEN_BG = Color.rgb(220, 252, 231)    // #DCFCE7
    private val COLOR_RED = Color.rgb(220, 38, 38)           // #DC2626 Negative
    private val COLOR_RED_BG = Color.rgb(254, 226, 226)      // #FEE2E2
    private val COLOR_AMBER = Color.rgb(217, 119, 6)         // #D97706 Warning
    private val COLOR_AMBER_BG = Color.rgb(254, 243, 199)    // #FEF3C7
    private val COLOR_BLUE = Color.rgb(37, 99, 235)          // #2563EB Primary Action
    private val COLOR_BLUE_BG = Color.rgb(219, 234, 254)     // #DBEAFE
    private val COLOR_SLATE = Color.rgb(148, 163, 184)       // #94A3B8 Previous month / comparative

    fun generateAndOpen(context: Context, report: MonthlyIncomeReport) {
        try {
            val pdfDocument = PdfDocument()
            val currencyFormat = NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            val dateStr = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.US).format(Date())

            // ---------------- PAGE 1: Executive Macro Health, Donut Cashflow & MoM Performance ----------------
            val pageInfo1 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page1 = pdfDocument.startPage(pageInfo1)
            drawPageOne(page1.canvas, report, currencyFormat, dateStr)
            pdfDocument.finishPage(page1)

            // ---------------- PAGE 2: Month-to-Date Trajectory & Actionable Plan ----------------
            val pageInfo2 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
            val page2 = pdfDocument.startPage(pageInfo2)
            drawPageTwo(page2.canvas, report, currencyFormat)
            pdfDocument.finishPage(page2)

            // ---------------- PAGE 3: Screen Charts: Expenses, Income & Estimation vs Actual ----------------
            val pageInfo3 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 3).create()
            val page3 = pdfDocument.startPage(pageInfo3)
            drawPageThree(page3.canvas, report, currencyFormat)
            pdfDocument.finishPage(page3)

            // Save to application cache reports directory
            val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
            val cleanMonth = report.monthLabel.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val pdfFile = File(reportsDir, "NEXUS_Report_${cleanMonth}.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            // Open or share via FileProvider
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "Open Monthly Report")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                context.startActivity(chooser)
                Toast.makeText(context, "Monthly report downloaded: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val shareChooser = Intent.createChooser(shareIntent, "Share Monthly Report")
                shareChooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(shareChooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error generating report: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // PAGE 1: EXECUTIVE MACRO HEALTH, DONUT CASHFLOW & MOM COMPARISON
    // =========================================================================
    private fun drawPageOne(
        canvas: Canvas,
        report: MonthlyIncomeReport,
        cf: NumberFormat,
        generatedDate: String
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Header Banner
        paint.color = COLOR_NAVY
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 90f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 17f
        canvas.drawText("NEXUS FINANCE TRACKER", MARGIN_LEFT, 35f, paint)

        paint.color = Color.rgb(203, 213, 225)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        canvas.drawText("Comprehensive Monthly Financial Intelligence & Analytics Report", MARGIN_LEFT, 51f, paint)
        canvas.drawText("Generated on: $generatedDate  ·  100% Offline & Private", MARGIN_LEFT, 65f, paint)

        // Month Pill Badge
        val monthBadgeText = report.monthLabel.uppercase(Locale.US)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val badgeWidth = paint.measureText(monthBadgeText) + 24f
        val badgeRect = RectF(MARGIN_RIGHT - badgeWidth, 26f, MARGIN_RIGHT, 60f)
        paint.color = Color.argb(60, 255, 255, 255)
        canvas.drawRoundRect(badgeRect, 8f, 8f, paint)
        paint.color = Color.WHITE
        canvas.drawText(monthBadgeText, badgeRect.left + 12f, 48f, paint)

        var y = 106f

        // 2. Executive Metric Cards (4 Grid)
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("EXECUTIVE FINANCIAL SUMMARY", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Compares total confirmed earnings against total spending for the selected month to show your net cashflow.", MARGIN_LEFT, y + 12f, paint)
        y += 22f

        val net = report.netResultLKR
        val savingsRate = if (report.totalIncomeLKR > 0) (maxOf(0.0, net) / report.totalIncomeLKR) * 100 else 0.0

        val cardWidth = (CONTENT_WIDTH - 18f) / 4f
        val cardHeight = 50f

        val metrics = listOf(
            Triple("Total Income", "LKR ${cf.format(report.totalIncomeLKR)}", COLOR_BLUE),
            Triple("Total Expenses", "LKR ${cf.format(report.totalExpensesLKR)}", COLOR_RED),
            Triple("Net Savings", (if (net >= 0) "+LKR " else "-LKR ") + cf.format(Math.abs(net)), if (net >= 0) COLOR_GREEN else COLOR_RED),
            Triple("Savings Rate", String.format(Locale.US, "%.1f%%", savingsRate), if (savingsRate >= 20) COLOR_GREEN else COLOR_AMBER)
        )

        metrics.forEachIndexed { index, (label, value, accentColor) ->
            val cardLeft = MARGIN_LEFT + index * (cardWidth + 6f)
            val cardRect = RectF(cardLeft, y, cardLeft + cardWidth, y + cardHeight)
            drawCardBackground(canvas, cardRect, COLOR_CARD_BG, COLOR_BORDER)

            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8f
            canvas.drawText(label, cardRect.left + 8f, cardRect.top + 15f, paint)

            paint.color = accentColor
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 10f
            canvas.drawText(value, cardRect.left + 8f, cardRect.top + 34f, paint)
        }
        y += cardHeight + 16f

        // 3. Visual Chart: Cashflow Donut Chart (Income vs Expenses)
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("CASHFLOW ALLOCATION (INCOME VS EXPENSES)", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Visual breakdown of confirmed earnings retained as net surplus versus consumed by expenditures.", MARGIN_LEFT, y + 12f, paint)
        y += 20f

        drawCashflowDonutChart(canvas, y, report.totalIncomeLKR, report.totalExpensesLKR, cf)
        y += 114f + 6f

        // Cashflow Graph Explanation Box
        val expenseRate = if (report.totalIncomeLKR > 0) (report.totalExpensesLKR / report.totalIncomeLKR) * 100 else 0.0
        val donutExplanation = "Visualizes cashflow retention ratio. Of LKR ${cf.format(report.totalIncomeLKR)} earned, you preserved ${String.format(Locale.US, "%.1f%%", savingsRate)} (LKR ${cf.format(maxOf(0.0, net))}) as net savings, with ${String.format(Locale.US, "%.1f%%", expenseRate)} (LKR ${cf.format(report.totalExpensesLKR)}) utilized for expenditures."
        val donutExpHeight = drawGraphExplanationBox(canvas, y, "Cashflow Allocation", donutExplanation)
        y += donutExpHeight + 16f

        // 4. Visual Chart: Month-over-Month Comparative Dual Bars
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("MONTH-OVER-MONTH PERFORMANCE COMPARISON", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Analyzes your financial momentum by comparing this month's earnings, spending, and savings against the previous month.", MARGIN_LEFT, y + 12f, paint)
        y += 20f

        val momHeight = drawMomComparisonVisual(canvas, y, report.comparison, report.monthLabel, cf)
        y += momHeight + 6f

        // MoM Graph Explanation Box
        val momExplanation = if (report.comparison != null && report.comparison.hasPreviousMonth) {
            val comp = report.comparison
            val expVerb = if (comp.expenseDiffLKR <= 0) "decreased by ${String.format(Locale.US, "%.1f%%", Math.abs(comp.expenseChangePercent))} (-LKR ${cf.format(Math.abs(comp.expenseDiffLKR))})" else "increased by ${String.format(Locale.US, "%.1f%%", comp.expenseChangePercent)} (+LKR ${cf.format(comp.expenseDiffLKR)})"
            val incVerb = if (comp.incomeDiffLKR >= 0) "revenue grew by ${String.format(Locale.US, "%.1f%%", comp.incomeChangePercent)}" else "revenue was lower by ${String.format(Locale.US, "%.1f%%", Math.abs(comp.incomeChangePercent))}"
            "Compares velocity against ${comp.previousMonthLabel}. Expenditures $expVerb while $incVerb."
        } else {
            "Baseline month (${report.monthLabel}). Comparative velocity will activate once consecutive monthly data is recorded."
        }
        val momExpHeight = drawGraphExplanationBox(canvas, y, "MoM Performance", momExplanation)
        y += momExpHeight + 16f

        // 5. Monthly Financial Handling Assessment Card
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("MONTHLY FINANCIAL HANDLING ASSESSMENT", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Algorithmic assessment of this month's cashflow and spending discipline with health grading.", MARGIN_LEFT, y + 12f, paint)
        y += 20f

        drawHealthAssessmentCard(canvas, y, report.healthSummary, cf)

        // Page 1 Footer
        drawFooter(canvas, 1, 3)
    }

    // =========================================================================
    // PAGE 2: MONTH-TO-DATE TRAJECTORY & ACTIONABLE INTELLIGENCE
    // =========================================================================
    private fun drawPageTwo(
        canvas: Canvas,
        report: MonthlyIncomeReport,
        cf: NumberFormat
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header Strip
        paint.color = COLOR_NAVY
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 52f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13.5f
        canvas.drawText("NEXUS TRAJECTORY & ACTION INTELLIGENCE", MARGIN_LEFT, 26f, paint)

        paint.color = Color.rgb(203, 213, 225)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        canvas.drawText("Month-to-Date Trajectory Comparison & Actionable Next-Month Recommendations · ${report.monthLabel}", MARGIN_LEFT, 41f, paint)

        var y = 66f
        val mtd = report.monthToDateComparison
        val cutoffDay = mtd?.cutoffDay ?: 1
        val prevMonthLabel = mtd?.previousMonthLabel.orEmpty()

        // 1. MTD Trajectory Section Header
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("MONTH-TO-DATE COMPARATIVE TRAJECTORY (UNTIL REPORT DATE)", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        val mtdSubtitle = if (prevMonthLabel.isNotBlank()) {
            "Daily cumulative comparison between ${report.monthLabel} (Day 1 to $cutoffDay) and $prevMonthLabel (Day 1 to $cutoffDay)."
        } else {
            "Daily cumulative trajectory for ${report.monthLabel} from Day 1 to Day $cutoffDay."
        }
        canvas.drawText(mtdSubtitle, MARGIN_LEFT, y + 12f, paint)
        y += 20f

        // Graph A: Cumulative Income MTD Line Graph
        val incHeight = drawMtdTrajectoryLineChart(
            canvas = canvas,
            y = y,
            title = "Cumulative Income Trajectory (MTD)",
            currentMonthLabel = report.monthLabel,
            previousMonthLabel = prevMonthLabel,
            cutoffDay = cutoffDay,
            currentMtdTotal = mtd?.currentIncomeMtdLKR ?: report.totalIncomeLKR,
            previousMtdTotal = mtd?.previousIncomeMtdLKR ?: 0.0,
            points = mtd?.dailyIncomePoints.orEmpty(),
            isIncome = true,
            cf = cf
        )
        y += incHeight + 5f

        // Graph A Explanation Box
        val currIncMtd = mtd?.currentIncomeMtdLKR ?: report.totalIncomeLKR
        val prevIncMtd = mtd?.previousIncomeMtdLKR ?: 0.0
        val incDiff = currIncMtd - prevIncMtd
        val incPct = if (prevIncMtd > 0) (incDiff / prevIncMtd) * 100.0 else 0.0
        val incExplanation = if (prevMonthLabel.isNotBlank()) {
            "Tracks day-by-day revenue velocity up to Day $cutoffDay. You confirmed LKR ${cf.format(currIncMtd)} vs LKR ${cf.format(prevIncMtd)} in $prevMonthLabel (${if (incDiff >= 0) "+" else ""}${String.format(Locale.US, "%.1f%%", incPct)})."
        } else {
            "Tracks cumulative income velocity up to Day $cutoffDay, totaling LKR ${cf.format(currIncMtd)} across confirmed earnings."
        }
        val incExpHeight = drawGraphExplanationBox(canvas, y, "Income MTD Trajectory", incExplanation)
        y += incExpHeight + 12f

        // Graph B: Cumulative Expenses MTD Line Graph
        val expHeight = drawMtdTrajectoryLineChart(
            canvas = canvas,
            y = y,
            title = "Cumulative Expenses Trajectory (MTD)",
            currentMonthLabel = report.monthLabel,
            previousMonthLabel = prevMonthLabel,
            cutoffDay = cutoffDay,
            currentMtdTotal = mtd?.currentExpensesMtdLKR ?: report.totalExpensesLKR,
            previousMtdTotal = mtd?.previousExpensesMtdLKR ?: 0.0,
            points = mtd?.dailyExpensePoints.orEmpty(),
            isIncome = false,
            cf = cf
        )
        y += expHeight + 5f

        // Graph B Explanation Box
        val currExpMtd = mtd?.currentExpensesMtdLKR ?: report.totalExpensesLKR
        val prevExpMtd = mtd?.previousExpensesMtdLKR ?: 0.0
        val expDiff = currExpMtd - prevExpMtd
        val expPct = if (prevExpMtd > 0) (expDiff / prevExpMtd) * 100.0 else 0.0
        val expPaceStatus = if (expDiff <= 0) "demonstrating favorable cost discipline" else "reflecting increased spending velocity"
        val expExplanation = if (prevMonthLabel.isNotBlank()) {
            "Tracks cumulative spending burn rate up to Day $cutoffDay. Outflows total LKR ${cf.format(currExpMtd)} vs LKR ${cf.format(prevExpMtd)} in $prevMonthLabel (${if (expDiff >= 0) "+" else ""}${String.format(Locale.US, "%.1f%%", expPct)}), $expPaceStatus."
        } else {
            "Tracks cumulative spending progression up to Day $cutoffDay, totaling LKR ${cf.format(currExpMtd)}."
        }
        val expExpHeight = drawGraphExplanationBox(canvas, y, "Expenses MTD Trajectory", expExplanation)
        y += expExpHeight + 16f

        // 2. Actionable Recommendations for Next Month
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("ACTIONABLE RECOMMENDATIONS FOR NEXT MONTH", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Data-driven financial recommendations and next-step actions based on this month's performance.", MARGIN_LEFT, y + 12f, paint)
        y += 20f

        val recsHeight = drawRecommendationsSection(canvas, y, report.healthSummary?.recommendations.orEmpty())
        y += recsHeight + 14f

        // 3. Savings Goals & Surplus Allocation
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("SAVINGS GOALS & SURPLUS ALLOCATION", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Tracks monthly savings surplus allocation toward active goals relative to required monthly targets.", MARGIN_LEFT, y + 12f, paint)
        y += 20f

        drawGoalProgressVisuals(canvas, y, report.goalReports, cf)

        // Page 2 Footer
        drawFooter(canvas, 2, 3)
    }

    // =========================================================================
    // PAGE 3: EXACT REPORT SCREEN VISUAL CHARTS
    // =========================================================================
    private fun drawPageThree(
        canvas: Canvas,
        report: MonthlyIncomeReport,
        cf: NumberFormat
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header Strip
        paint.color = COLOR_NAVY
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 52f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13.5f
        canvas.drawText("NEXUS GRAPHICAL BREAKDOWN & BUDGET TRACKING", MARGIN_LEFT, 26f, paint)

        paint.color = Color.rgb(203, 213, 225)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        canvas.drawText("Expenses by Category, Income by Source, and Discretionary Estimation vs Actual · ${report.monthLabel}", MARGIN_LEFT, 41f, paint)

        var y = 66f

        // 1. Expenses by Category Vertical Bar Chart (matching CategoryExpenseBarChart)
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("EXPENSES BY CATEGORY BREAKDOWN", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Vertical bar chart showing spending distribution mapped to monetary value on Y-axis.", MARGIN_LEFT, y + 12f, paint)
        y += 18f

        val catItems = report.categorySpendings.map { it.name to it.amountLKR }
        val catHeight = drawVerticalBarChart(
            canvas = canvas,
            y = y,
            yAxisTitle = "Expenses (LKR)",
            xAxisTitle = "Expense Category",
            items = catItems,
            barColor = COLOR_BLUE,
            cf = cf
        )
        y += catHeight + 5f

        // Graph 1 Explanation Box
        val topExpense = report.categorySpendings.maxByOrNull { it.amountLKR }
        val catExplanation = if (topExpense != null) {
            val pct = if (report.totalExpensesLKR > 0) (topExpense.amountLKR / report.totalExpensesLKR) * 100 else 0.0
            "Ranks categories by expenditure. Primary cost driver is '${topExpense.name}' at LKR ${cf.format(topExpense.amountLKR)} (${String.format(Locale.US, "%.1f%%", pct)} of total spend)."
        } else {
            "Displays distribution of spending across categories mapped directly to monetary values on the Y-axis."
        }
        val catExpHeight = drawGraphExplanationBox(canvas, y, "Expenses by Category", catExplanation)
        y += catExpHeight + 14f

        // 2. Income by Source Vertical Bar Chart (matching IncomeSourceBarChart)
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("INCOME BY SOURCE BREAKDOWN", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Vertical bar chart showing confirmed earnings breakdown by source stream.", MARGIN_LEFT, y + 12f, paint)
        y += 18f

        val srcItems = report.incomeSources.map { it.source to it.amountLKR }
        val srcHeight = drawVerticalBarChart(
            canvas = canvas,
            y = y,
            yAxisTitle = "Income (LKR)",
            xAxisTitle = "Income Source",
            items = srcItems,
            barColor = COLOR_BLUE,
            cf = cf
        )
        y += srcHeight + 5f

        // Graph 2 Explanation Box
        val topSource = report.incomeSources.maxByOrNull { it.amountLKR }
        val srcExplanation = if (topSource != null) {
            val pct = if (report.totalIncomeLKR > 0) (topSource.amountLKR / report.totalIncomeLKR) * 100 else 0.0
            "Highlights revenue stream distribution. Primary liquidity stream is '${topSource.source}' at LKR ${cf.format(topSource.amountLKR)} (${String.format(Locale.US, "%.1f%%", pct)} of confirmed income)."
        } else {
            "Displays confirmed earnings broken down by revenue streams mapped directly to the Y-axis."
        }
        val srcExpHeight = drawGraphExplanationBox(canvas, y, "Income by Source", srcExplanation)
        y += srcExpHeight + 14f

        // 3. Discretionary Categories: Estimation vs Actual Dual Line Chart
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("DISCRETIONARY CATEGORIES: ESTIMATION VS ACTUAL", MARGIN_LEFT, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("Dual line comparison tracking planned budget limits vs actual expenditure across flexible categories.", MARGIN_LEFT, y + 12f, paint)
        y += 18f

        val discHeight = drawDiscretionaryEstimationVsActualLineChart(
            canvas = canvas,
            y = y,
            items = report.discretionaryComparisons,
            cf = cf
        )
        y += discHeight + 5f

        // Graph 3 Explanation Box
        val overspent = report.discretionaryComparisons.filter { !it.isWithinBudget }
        val totalSurplus = report.discretionaryComparisons.sumOf { it.differenceLKR }
        val discExplanation = if (overspent.isNotEmpty()) {
            val overNames = overspent.joinToString(", ") { "'${it.categoryName}'" }
            "Compares planned budget limits (Navy) against actual expenses (Red). Categories exceeding budget: $overNames. Audit variable spending in these areas to restore surplus."
        } else if (report.discretionaryComparisons.isNotEmpty()) {
            "Compares planned budget limits (Navy) against actual expenses (Red). All discretionary categories operated within planned caps, generating LKR ${cf.format(Math.max(0.0, totalSurplus))} in cumulative budget surplus."
        } else {
            "Tracks planned budget limits against actual spending across flexible lifestyle categories."
        }
        drawGraphExplanationBox(canvas, y, "Estimation vs Actual", discExplanation)

        // Page 3 Footer
        drawFooter(canvas, 3, 3)
    }

    // =========================================================================
    // VISUAL CHART COMPONENT DRAWING METHODS
    // =========================================================================

    private fun drawCashflowDonutChart(
        canvas: Canvas,
        y: Float,
        totalIncome: Double,
        totalExpenses: Double,
        cf: NumberFormat
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardHeight = 114f
        val cardRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + cardHeight)
        drawCardBackground(canvas, cardRect, Color.WHITE, COLOR_BORDER)

        val net = totalIncome - totalExpenses
        val savingsRate = if (totalIncome > 0) (maxOf(0.0, net) / totalIncome) * 100 else 0.0
        val expenseRate = if (totalIncome > 0) (totalExpenses / totalIncome) * 100 else 0.0

        val centerX = MARGIN_LEFT + 65f
        val centerY = y + 57f
        val radius = 36f
        val strokeWidth = 11f

        // Track circle
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = COLOR_TRACK
        canvas.drawCircle(centerX, centerY, radius, paint)

        val donutOval = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        if (totalIncome > 0) {
            val expenseSweep = (minOf(100.0, expenseRate) / 100.0 * 360.0).toFloat()
            val savingsSweep = (minOf(100.0, savingsRate) / 100.0 * 360.0).toFloat()

            if (expenseSweep > 0) {
                paint.color = COLOR_RED
                canvas.drawArc(donutOval, -90f, expenseSweep, false, paint)
            }
            if (savingsSweep > 0) {
                paint.color = COLOR_GREEN
                canvas.drawArc(donutOval, -90f + expenseSweep, savingsSweep, false, paint)
            }
        }

        // Inside text
        paint.style = Paint.Style.FILL
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        val rateText = String.format(Locale.US, "%.1f%%", savingsRate)
        val rateW = paint.measureText(rateText)
        canvas.drawText(rateText, centerX - (rateW / 2f), centerY + 1f, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        val subText = "Net Saved"
        val subW = paint.measureText(subText)
        canvas.drawText(subText, centerX - (subW / 2f), centerY + 12f, paint)

        // Legend
        val legendLeft = MARGIN_LEFT + 140f
        var legendY = y + 24f

        drawLegendRow(canvas, legendLeft, legendY, COLOR_BLUE, "Total Confirmed Earnings", "LKR ${cf.format(totalIncome)}", "(100%)", paint)
        legendY += 26f

        drawLegendRow(canvas, legendLeft, legendY, COLOR_RED, "Total Expenditures", "LKR ${cf.format(totalExpenses)}", "(${String.format(Locale.US, "%.1f%%", expenseRate)})", paint)
        legendY += 26f

        val netPrefix = if (net >= 0) "+LKR " else "-LKR "
        drawLegendRow(canvas, legendLeft, legendY, if (net >= 0) COLOR_GREEN else COLOR_RED, "Net Monthly Surplus", "$netPrefix${cf.format(Math.abs(net))}", "(${String.format(Locale.US, "%.1f%%", savingsRate)})", paint)
    }

    private fun drawLegendRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        bulletColor: Int,
        title: String,
        amount: String,
        pct: String,
        paint: Paint
    ) {
        paint.style = Paint.Style.FILL
        paint.color = bulletColor
        canvas.drawRoundRect(RectF(x, y - 8f, x + 9f, y + 1f), 2.5f, 2.5f, paint)

        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText(title, x + 15f, y, paint)

        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText(amount, x + 225f, y, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = bulletColor
        canvas.drawText(pct, x + 325f, y, paint)
    }

    private fun drawMomComparisonVisual(
        canvas: Canvas,
        y: Float,
        comp: MonthOverMonthComparison?,
        currentMonthLabel: String,
        cf: NumberFormat
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (comp == null || !comp.hasPreviousMonth) {
            val cardHeight = 44f
            val cardRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + cardHeight)
            drawCardBackground(canvas, cardRect, Color.WHITE, COLOR_BORDER)
            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9f
            canvas.drawText("Baseline financial month ($currentMonthLabel). Comparative trends will activate with consecutive monthly data.", MARGIN_LEFT + 14f, y + 26f, paint)
            return cardHeight
        }

        val cardHeight = 125f
        val cardRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + cardHeight)
        drawCardBackground(canvas, cardRect, Color.WHITE, COLOR_BORDER)

        // Legend
        paint.color = COLOR_SLATE
        canvas.drawRoundRect(RectF(MARGIN_LEFT + 14f, y + 10f, MARGIN_LEFT + 22f, y + 18f), 2f, 2f, paint)
        paint.color = COLOR_MUTED_TEXT
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Previous (${comp.previousMonthLabel})", MARGIN_LEFT + 26f, y + 17f, paint)

        paint.color = COLOR_BLUE
        canvas.drawRoundRect(RectF(MARGIN_LEFT + 190f, y + 10f, MARGIN_LEFT + 198f, y + 18f), 2f, 2f, paint)
        paint.color = COLOR_MUTED_TEXT
        canvas.drawText("This Month ($currentMonthLabel)", MARGIN_LEFT + 202f, y + 17f, paint)

        var rowY = y + 34f
        val barMaxWidth = 180f

        val rows = listOf(
            Triple("Confirmed Income", Pair(comp.previousIncomeLKR, comp.currentIncomeLKR), Pair(comp.incomeDiffLKR, comp.incomeChangePercent)) to true,
            Triple("Total Expenses", Pair(comp.previousExpensesLKR, comp.currentExpensesLKR), Pair(comp.expenseDiffLKR, comp.expenseChangePercent)) to false,
            Triple("Net Surplus / Savings", Pair(comp.previousSavingsLKR, comp.currentSavingsLKR), Pair(comp.savingsDiffLKR, comp.savingsChangePercent)) to true
        )

        rows.forEach { (data, isIncomeOrSavings) ->
            val (label, values, delta) = data
            val (prevVal, currVal) = values
            val (diff, pct) = delta

            paint.color = COLOR_DARK_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8.5f
            canvas.drawText(label, MARGIN_LEFT + 14f, rowY + 9f, paint)

            val maxPairVal = maxOf(prevVal, currVal).coerceAtLeast(1.0)
            val prevWidth = ((prevVal / maxPairVal) * barMaxWidth).toFloat().coerceIn(4f, barMaxWidth)
            val currWidth = ((currVal / maxPairVal) * barMaxWidth).toFloat().coerceIn(4f, barMaxWidth)

            val barStartX = MARGIN_LEFT + 140f

            paint.color = COLOR_SLATE
            val prevBarRect = RectF(barStartX, rowY, barStartX + prevWidth, rowY + 5.5f)
            canvas.drawRoundRect(prevBarRect, 2.5f, 2.5f, paint)

            paint.color = if (isIncomeOrSavings) COLOR_BLUE else COLOR_RED
            val currBarRect = RectF(barStartX, rowY + 8f, barStartX + currWidth, rowY + 15.5f)
            canvas.drawRoundRect(currBarRect, 2.5f, 2.5f, paint)

            paint.color = COLOR_DARK_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8f
            canvas.drawText("LKR ${cf.format(currVal)}", barStartX + barMaxWidth + 12f, rowY + 13f, paint)

            val isFavorable = if (isIncomeOrSavings) diff >= 0 else diff <= 0
            val deltaText = (if (diff >= 0) "+" else "-") + String.format(Locale.US, "%.1f%%", Math.abs(pct))
            val deltaW = paint.measureText(deltaText) + 12f
            val deltaRect = RectF(MARGIN_RIGHT - deltaW - 12f, rowY, MARGIN_RIGHT - 12f, rowY + 16f)

            paint.color = if (isFavorable) COLOR_GREEN_BG else COLOR_RED_BG
            canvas.drawRoundRect(deltaRect, 4f, 4f, paint)
            paint.color = if (isFavorable) COLOR_GREEN else COLOR_RED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8f
            canvas.drawText(deltaText, deltaRect.left + 6f, rowY + 11.5f, paint)

            rowY += 28f
        }
        return cardHeight
    }

    private fun drawHealthAssessmentCard(
        canvas: Canvas,
        y: Float,
        health: MonthlyHealthSummary?,
        cf: NumberFormat
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardHeight = 88f
        val healthBox = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + cardHeight)
        drawCardBackground(canvas, healthBox, Color.WHITE, COLOR_BORDER)

        paint.color = COLOR_NAVY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        val headline = health?.headline?.ifBlank { "Financial Health Overview" } ?: "Financial Health Overview"
        canvas.drawText("●  $headline", MARGIN_LEFT + 14f, y + 20f, paint)

        if (!health?.healthGrade.isNullOrBlank()) {
            val gradeRect = RectF(MARGIN_RIGHT - 75f, y + 8f, MARGIN_RIGHT - 14f, y + 28f)
            paint.color = COLOR_NAVY
            canvas.drawRoundRect(gradeRect, 5f, 5f, paint)
            paint.color = Color.WHITE
            paint.textSize = 8.5f
            canvas.drawText("GRADE ${health.healthGrade}", gradeRect.left + 8f, gradeRect.top + 14f, paint)
        }

        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        val overviewText = health?.overview ?: "Financial performance reflects current recorded transactions."
        drawWrappedText(canvas, overviewText, MARGIN_LEFT + 14f, y + 38f, CONTENT_WIDTH - 28f, 11f, paint)

        if (health != null && health.topExpenseCategory.isNotBlank()) {
            paint.color = COLOR_MUTED_TEXT
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val insight = "Key Insight: Highest expense category was '${health.topExpenseCategory}' (LKR ${cf.format(health.topExpenseAmountLKR)})."
            canvas.drawText(insight, MARGIN_LEFT + 14f, y + 74f, paint)
        }
    }

    // =========================================================================
    // MTD TRAJECTORY LINE CHARTS (PAGE 2)
    // =========================================================================
    private fun drawMtdTrajectoryLineChart(
        canvas: Canvas,
        y: Float,
        title: String,
        currentMonthLabel: String,
        previousMonthLabel: String,
        cutoffDay: Int,
        currentMtdTotal: Double,
        previousMtdTotal: Double,
        points: List<DailyCumulativePoint>,
        isIncome: Boolean,
        cf: NumberFormat
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardHeight = 118f
        val cardRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + cardHeight)
        drawCardBackground(canvas, cardRect, Color.WHITE, COLOR_BORDER)

        val mainColor = if (isIncome) COLOR_BLUE else COLOR_RED

        // Title
        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText(title, MARGIN_LEFT + 14f, y + 16f, paint)

        val legendY = y + 29f
        // Current Month Dot & Text
        paint.style = Paint.Style.FILL
        paint.color = mainColor
        canvas.drawCircle(MARGIN_LEFT + 18f, legendY - 2.5f, 3.5f, paint)

        paint.color = COLOR_DARK_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        val currLegendText = "$currentMonthLabel (Day 1–$cutoffDay): LKR ${cf.format(currentMtdTotal)}"
        canvas.drawText(currLegendText, MARGIN_LEFT + 25f, legendY, paint)

        // Previous Month Dot & Text
        if (previousMonthLabel.isNotBlank()) {
            val prevX = MARGIN_LEFT + 230f
            paint.color = COLOR_SLATE
            canvas.drawCircle(prevX + 4f, legendY - 2.5f, 3.5f, paint)

            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val prevLegendText = "$previousMonthLabel (Day 1–$cutoffDay): LKR ${cf.format(previousMtdTotal)}"
            canvas.drawText(prevLegendText, prevX + 11f, legendY, paint)

            // Delta Badge
            val diff = currentMtdTotal - previousMtdTotal
            val pct = if (previousMtdTotal > 0) ((diff) / previousMtdTotal) * 100.0 else 0.0
            val isFavorable = if (isIncome) diff >= 0 else diff <= 0
            val deltaText = (if (diff >= 0) "+" else "") + String.format(Locale.US, "%.1f%%", pct)
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val deltaW = paint.measureText(deltaText) + 10f
            val badgeRect = RectF(MARGIN_RIGHT - deltaW - 12f, y + 7f, MARGIN_RIGHT - 12f, y + 21f)
            paint.color = if (isFavorable) COLOR_GREEN_BG else COLOR_RED_BG
            canvas.drawRoundRect(badgeRect, 3.5f, 3.5f, paint)
            paint.color = if (isFavorable) COLOR_GREEN else COLOR_RED
            canvas.drawText(deltaText, badgeRect.left + 5f, y + 17f, paint)
        }

        // Plotting bounds
        val yAxisWidth = 36f
        val plotLeft = MARGIN_LEFT + yAxisWidth + 12f
        val plotRight = MARGIN_RIGHT - 14f
        val plotTop = y + 38f
        val plotHeight = 60f
        val plotBottom = plotTop + plotHeight

        val maxVal = maxOf(
            points.maxOfOrNull { maxOf(it.currentMonthCumulativeLKR, it.previousMonthCumulativeLKR) } ?: 0.0,
            1.0
        ) * 1.1

        // Grid lines & Y tick labels
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        paint.color = COLOR_MUTED_TEXT

        val topLabel = formatCompactLkr(maxVal)
        canvas.drawText(topLabel, plotLeft - paint.measureText(topLabel) - 4f, plotTop + 3f, paint)
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.6f
        canvas.drawLine(plotLeft, plotTop, plotRight, plotTop, paint)

        val midY = plotTop + (plotHeight / 2f)
        val midLabel = formatCompactLkr(maxVal / 2f)
        canvas.drawText(midLabel, plotLeft - paint.measureText(midLabel) - 4f, midY + 3f, paint)
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.6f
        canvas.drawLine(plotLeft, midY, plotRight, midY, paint)

        paint.color = COLOR_MUTED_TEXT
        canvas.drawText("0K", plotLeft - paint.measureText("0K") - 4f, plotBottom + 3f, paint)
        paint.color = COLOR_NAVY
        paint.strokeWidth = 1.2f
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, paint)

        if (points.isNotEmpty()) {
            val totalDays = maxOf(cutoffDay, points.size)
            fun getX(day: Int): Float {
                if (totalDays <= 1) return plotLeft + (plotRight - plotLeft) / 2f
                val fraction = (day - 1).toFloat() / (totalDays - 1).toFloat()
                return plotLeft + (plotRight - plotLeft) * fraction
            }

            // Draw Previous Month Line if present
            if (previousMonthLabel.isNotBlank()) {
                val prevPath = mutableListOf<Pair<Float, Float>>()
                points.forEach { pt ->
                    val x = getX(pt.day)
                    val frac = (pt.previousMonthCumulativeLKR / maxVal).toFloat().coerceIn(0f, 1f)
                    val yPt = plotBottom - (plotHeight * frac)
                    prevPath.add(Pair(x, yPt))
                }

                paint.color = COLOR_SLATE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f
                for (i in 0 until prevPath.size - 1) {
                    canvas.drawLine(prevPath[i].first, prevPath[i].second, prevPath[i + 1].first, prevPath[i + 1].second, paint)
                }

                paint.style = Paint.Style.FILL
                prevPath.forEach { (x, yPt) ->
                    canvas.drawCircle(x, yPt, 2.2f, paint)
                }
            }

            // Draw Current Month Line
            val currPath = mutableListOf<Pair<Float, Float>>()
            points.forEach { pt ->
                val x = getX(pt.day)
                val frac = (pt.currentMonthCumulativeLKR / maxVal).toFloat().coerceIn(0f, 1f)
                val yPt = plotBottom - (plotHeight * frac)
                currPath.add(Pair(x, yPt))
            }

            paint.color = mainColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f
            for (i in 0 until currPath.size - 1) {
                canvas.drawLine(currPath[i].first, currPath[i].second, currPath[i + 1].first, currPath[i + 1].second, paint)
            }

            paint.style = Paint.Style.FILL
            currPath.forEach { (x, yPt) ->
                paint.color = mainColor
                canvas.drawCircle(x, yPt, 3.4f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(x, yPt, 1.6f, paint)
            }

            // Endpoint Badge
            val lastPt = currPath.last()
            val endBadgeText = formatCompactLkr(currentMtdTotal)
            paint.textSize = 6.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val badgeW = paint.measureText(endBadgeText) + 8f
            val endBadgeRect = RectF(
                (lastPt.first - badgeW / 2f).coerceIn(plotLeft, plotRight - badgeW),
                lastPt.second - 15f,
                (lastPt.first + badgeW / 2f).coerceIn(plotLeft + badgeW, plotRight),
                lastPt.second - 3f
            )
            paint.color = if (isIncome) COLOR_BLUE_BG else COLOR_RED_BG
            canvas.drawRoundRect(endBadgeRect, 2.5f, 2.5f, paint)
            paint.color = mainColor
            canvas.drawText(endBadgeText, endBadgeRect.left + 4f, endBadgeRect.top + 8.5f, paint)

            // X-Axis Day Labels
            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7f

            val step = when {
                totalDays <= 7 -> 1
                totalDays <= 14 -> 2
                totalDays <= 21 -> 3
                else -> 5
            }

            for (d in 1..totalDays step step) {
                val x = getX(d)
                val label = "D$d"
                val lblW = paint.measureText(label)
                canvas.drawText(label, x - (lblW / 2f), plotBottom + 11f, paint)
            }
            if (totalDays % step != 0) {
                val x = getX(totalDays)
                val label = "D$totalDays"
                val lblW = paint.measureText(label)
                canvas.drawText(label, x - (lblW / 2f), plotBottom + 11f, paint)
            }
        }

        // X-Axis Title
        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        val xTitle = "Timeline (Day 1 to $cutoffDay)"
        val xTitleW = paint.measureText(xTitle)
        canvas.drawText(xTitle, plotRight - xTitleW, y + cardHeight - 5f, paint)

        return cardHeight
    }

    private fun drawRecommendationsSection(
        canvas: Canvas,
        y: Float,
        recs: List<FinancialRecommendation>
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (recs.isEmpty()) {
            val emptyBox = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 36f)
            drawCardBackground(canvas, emptyBox, COLOR_CARD_BG, COLOR_BORDER)
            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8.5f
            canvas.drawText("Continue recording routine expenses and maintain disciplined monthly goal allocations.", MARGIN_LEFT + 14f, y + 22f, paint)
            return 36f
        }

        var currY = y
        val cardHeight = 40f
        val gap = 5f
        val itemsToShow = recs.take(3)

        itemsToShow.forEach { rec ->
            val recCard = RectF(MARGIN_LEFT, currY, MARGIN_RIGHT, currY + cardHeight)
            drawCardBackground(canvas, recCard, COLOR_CARD_BG, COLOR_BORDER)

            val (pillBg, pillTextColor) = when (rec.type) {
                RecommendationType.WARNING -> Pair(COLOR_RED_BG, COLOR_RED)
                RecommendationType.POSITIVE -> Pair(COLOR_GREEN_BG, COLOR_GREEN)
                RecommendationType.ACTION -> Pair(COLOR_BLUE_BG, COLOR_BLUE)
                else -> Pair(COLOR_AMBER_BG, COLOR_AMBER)
            }

            val impactText = "[${rec.impactArea}]"
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 7.5f
            val pillW = paint.measureText(impactText) + 8f
            val pillRect = RectF(MARGIN_LEFT + 10f, currY + 5f, MARGIN_LEFT + 10f + pillW, currY + 18f)
            paint.color = pillBg
            canvas.drawRoundRect(pillRect, 3f, 3f, paint)
            paint.color = pillTextColor
            canvas.drawText(impactText, pillRect.left + 4f, currY + 14.5f, paint)

            paint.color = COLOR_DARK_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8.5f
            canvas.drawText(rec.title, pillRect.right + 7f, currY + 15f, paint)

            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7.5f
            drawWrappedText(canvas, rec.description, MARGIN_LEFT + 12f, currY + 29f, CONTENT_WIDTH - 24f, 10f, paint)

            currY += cardHeight + gap
        }

        return currY - y - gap
    }

    private fun drawGoalProgressVisuals(
        canvas: Canvas,
        y: Float,
        goals: List<MonthlyGoalReport>,
        cf: NumberFormat
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val itemsToShow = goals.take(2)
        if (itemsToShow.isEmpty()) {
            val emptyBox = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 36f)
            drawCardBackground(canvas, emptyBox, COLOR_CARD_BG, COLOR_BORDER)
            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8.5f
            canvas.drawText("No savings goals active for this month.", MARGIN_LEFT + 14f, y + 22f, paint)
            return 36f
        }

        val itemHeight = 38f
        val cardHeight = 14f + (itemsToShow.size * itemHeight)
        val cardRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + cardHeight)
        drawCardBackground(canvas, cardRect, Color.WHITE, COLOR_BORDER)

        var rowY = y + 14f
        itemsToShow.forEach { goal ->
            paint.color = COLOR_DARK_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8.5f
            canvas.drawText(goal.goalTitle, MARGIN_LEFT + 14f, rowY, paint)

            paint.color = if (goal.isCompleted) COLOR_GREEN else COLOR_BLUE
            val pctText = if (goal.isCompleted) "Completed (100%)" else String.format(Locale.US, "%.1f%% Saved", goal.overallProgressPercentage)
            val pctW = paint.measureText(pctText)
            canvas.drawText(pctText, MARGIN_RIGHT - 14f - pctW, rowY, paint)

            val barTop = rowY + 5f
            val barWidth = CONTENT_WIDTH - 28f
            val barHeight = 5f
            val trackRect = RectF(MARGIN_LEFT + 14f, barTop, MARGIN_LEFT + 14f + barWidth, barTop + barHeight)
            paint.color = COLOR_TRACK
            canvas.drawRoundRect(trackRect, 2.5f, 2.5f, paint)

            val fillFraction = if (goal.isCompleted) 1f else goal.overallProgressFraction.coerceIn(0f, 1f)
            val fillWidth = (barWidth * fillFraction).coerceAtLeast(4f)
            paint.color = if (goal.isCompleted) COLOR_GREEN else COLOR_BLUE
            val fillRect = RectF(MARGIN_LEFT + 14f, barTop, MARGIN_LEFT + 14f + fillWidth, barTop + barHeight)
            canvas.drawRoundRect(fillRect, 2.5f, 2.5f, paint)

            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7.5f
            val details = "Target: LKR ${cf.format(goal.targetAmountLKR)}  ·  Saved: LKR ${cf.format(goal.cumulativeSavingsLKR)}  ·  Monthly Target: LKR ${cf.format(goal.targetMonthlyLKR)}"
            canvas.drawText(details, MARGIN_LEFT + 14f, barTop + 14f, paint)

            rowY += itemHeight
        }
        return cardHeight
    }

    // =========================================================================
    // EXACT SCREEN REPRODUCTION GRAPHS (PAGE 3)
    // =========================================================================

    /**
     * Vertical Bar Chart for Expenses by Category or Income by Source:
     * - Matches CategoryExpenseBarChart and IncomeSourceBarChart exactly:
     *   - Y-Axis with grid lines and compact values (e.g., 32K, 16K, 0K).
     *   - Vertical bars mapped proportionally to max value.
     *   - Compact value labels directly above each bar.
     *   - Category/Source names along the X-axis below baseline.
     */
    private fun drawVerticalBarChart(
        canvas: Canvas,
        y: Float,
        yAxisTitle: String,
        xAxisTitle: String,
        items: List<Pair<String, Double>>,
        barColor: Int,
        cf: NumberFormat
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardHeight = 142f
        val cardRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + cardHeight)
        drawCardBackground(canvas, cardRect, Color.WHITE, COLOR_BORDER)

        if (items.isEmpty()) {
            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9f
            canvas.drawText("No recorded data for this period.", MARGIN_LEFT + 14f, y + 40f, paint)
            return cardHeight
        }

        // Y-Axis Title at top left
        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        canvas.drawText(yAxisTitle, MARGIN_LEFT + 14f, y + 14f, paint)

        val maxAmount = items.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
        val yTop = maxAmount
        val yMid = maxAmount / 2.0

        val yAxisWidth = 36f
        val plotLeft = MARGIN_LEFT + yAxisWidth + 12f
        val plotRight = MARGIN_RIGHT - 14f
        val plotTop = y + 22f
        val plotHeight = 78f
        val plotBottom = plotTop + plotHeight

        // Horizontal Grid lines & Y tick labels
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        paint.color = COLOR_MUTED_TEXT

        // Top line
        val topLabel = formatCompactLkr(yTop)
        val topLabelW = paint.measureText(topLabel)
        canvas.drawText(topLabel, plotLeft - topLabelW - 4f, plotTop + 3f, paint)

        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.6f
        canvas.drawLine(plotLeft, plotTop, plotRight, plotTop, paint)

        // Mid line
        val midY = plotTop + (plotHeight / 2f)
        val midLabel = formatCompactLkr(yMid)
        val midLabelW = paint.measureText(midLabel)
        canvas.drawText(midLabel, plotLeft - midLabelW - 4f, midY + 3f, paint)

        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.6f
        canvas.drawLine(plotLeft, midY, plotRight, midY, paint)

        // Baseline (0)
        paint.color = COLOR_MUTED_TEXT
        canvas.drawText("0K", plotLeft - paint.measureText("0K") - 4f, plotBottom + 3f, paint)

        paint.color = COLOR_NAVY
        paint.strokeWidth = 1.2f
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, paint)

        // Vertical Bars & X-axis labels
        val itemsToShow = items.take(7)
        val slotWidth = (plotRight - plotLeft) / itemsToShow.size.toFloat()
        val barWidth = minOf(30f, slotWidth * 0.55f)

        itemsToShow.forEachIndexed { index, (label, amount) ->
            val slotCenter = plotLeft + (index * slotWidth) + (slotWidth / 2f)
            val fraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f)
            val barH = (plotHeight * fraction).coerceAtLeast(if (amount > 0) 3f else 0f)
            val barTop = plotBottom - barH

            // Amount label above bar
            paint.color = COLOR_DARK_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 7f
            val amtText = formatCompactLkr(amount)
            val amtW = paint.measureText(amtText)
            canvas.drawText(amtText, slotCenter - (amtW / 2f), barTop - 3f, paint)

            // Vertical bar
            paint.color = barColor
            paint.style = Paint.Style.FILL
            val barRect = RectF(slotCenter - (barWidth / 2f), barTop, slotCenter + (barWidth / 2f), plotBottom)
            canvas.drawRoundRect(barRect, 3f, 3f, paint)

            // X-Axis Category / Source label below baseline
            paint.color = COLOR_DARK_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7f

            val words = label.split(" ")
            if (words.size > 1 && paint.measureText(label) > slotWidth - 4f) {
                val line1 = words[0]
                val line2 = words.drop(1).joinToString(" ")
                val w1 = paint.measureText(line1)
                val w2 = paint.measureText(line2)
                canvas.drawText(line1, slotCenter - (w1 / 2f), plotBottom + 10f, paint)
                canvas.drawText(line2, slotCenter - (w2 / 2f), plotBottom + 19f, paint)
            } else {
                val displayLabel = if (paint.measureText(label) > slotWidth) {
                    label.take(8) + ".."
                } else label
                val lblW = paint.measureText(displayLabel)
                canvas.drawText(displayLabel, slotCenter - (lblW / 2f), plotBottom + 11f, paint)
            }
        }

        // X-Axis Title at bottom right
        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        val xTitleW = paint.measureText(xAxisTitle)
        canvas.drawText(xAxisTitle, plotRight - xTitleW, y + cardHeight - 6f, paint)

        return cardHeight
    }

    /**
     * Line Chart comparing Estimation Value (Navy Blue) vs Actual Expense (Red)
     * Matches DiscretionaryEstimationVsActualLineChart exactly.
     */
    private fun drawDiscretionaryEstimationVsActualLineChart(
        canvas: Canvas,
        y: Float,
        items: List<DiscretionaryCategoryComparison>,
        cf: NumberFormat
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardHeight = 155f
        val cardRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + cardHeight)
        drawCardBackground(canvas, cardRect, Color.WHITE, COLOR_BORDER)

        if (items.isEmpty()) {
            paint.color = COLOR_MUTED_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9f
            canvas.drawText("No discretionary categories configured for this month.", MARGIN_LEFT + 14f, y + 40f, paint)
            return cardHeight
        }

        // 1. Legend at top
        val legendY = y + 14f
        // Estimated Value (Navy)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = COLOR_NAVY
        canvas.drawLine(MARGIN_LEFT + 14f, legendY, MARGIN_LEFT + 28f, legendY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(MARGIN_LEFT + 21f, legendY, 3.5f, paint)

        paint.color = COLOR_NAVY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        canvas.drawText("Estimated Value", MARGIN_LEFT + 34f, legendY + 3f, paint)

        // Actual Expense (Red)
        val actLegendX = MARGIN_LEFT + 140f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = COLOR_RED
        canvas.drawLine(actLegendX, legendY, actLegendX + 14f, legendY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(actLegendX + 7f, legendY, 3.5f, paint)

        paint.color = COLOR_RED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        canvas.drawText("Actual Expense", actLegendX + 20f, legendY + 3f, paint)

        // Y-Axis Title
        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        canvas.drawText("Expense Value (LKR)", MARGIN_LEFT + 14f, y + 30f, paint)

        val maxVal = items.maxOfOrNull { maxOf(it.estimatedAmountLKR, it.actualExpenseLKR) }?.coerceAtLeast(1.0) ?: 1.0
        val yTop = maxVal * 1.15
        val yMid = yTop / 2.0

        val yAxisWidth = 36f
        val plotLeft = MARGIN_LEFT + yAxisWidth + 12f
        val plotRight = MARGIN_RIGHT - 14f
        val plotTop = y + 38f
        val plotHeight = 78f
        val plotBottom = plotTop + plotHeight

        // Grid lines & Y tick labels
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        paint.color = COLOR_MUTED_TEXT

        val topLabel = formatCompactLkr(yTop)
        canvas.drawText(topLabel, plotLeft - paint.measureText(topLabel) - 4f, plotTop + 3f, paint)
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.6f
        canvas.drawLine(plotLeft, plotTop, plotRight, plotTop, paint)

        val midY = plotTop + (plotHeight / 2f)
        val midLabel = formatCompactLkr(yMid)
        canvas.drawText(midLabel, plotLeft - paint.measureText(midLabel) - 4f, midY + 3f, paint)
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.6f
        canvas.drawLine(plotLeft, midY, plotRight, midY, paint)

        paint.color = COLOR_MUTED_TEXT
        canvas.drawText("0K", plotLeft - paint.measureText("0K") - 4f, plotBottom + 3f, paint)
        paint.color = COLOR_NAVY
        paint.strokeWidth = 1.2f
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, paint)

        val itemsToShow = items.take(7)
        val slotWidth = (plotRight - plotLeft) / itemsToShow.size.toFloat()

        val estPoints = mutableListOf<Pair<Float, Float>>()
        val actPoints = mutableListOf<Pair<Float, Float>>()

        itemsToShow.forEachIndexed { index, item ->
            val slotCenter = plotLeft + (index * slotWidth) + (slotWidth / 2f)
            val estFrac = (item.estimatedAmountLKR / yTop).toFloat().coerceIn(0f, 1f)
            val estPtY = plotBottom - (plotHeight * estFrac)
            estPoints.add(Pair(slotCenter, estPtY))

            val actFrac = (item.actualExpenseLKR / yTop).toFloat().coerceIn(0f, 1f)
            val actPtY = plotBottom - (plotHeight * actFrac)
            actPoints.add(Pair(slotCenter, actPtY))
        }

        // 1. Draw Navy Line for Estimated Value
        if (estPoints.size > 1) {
            paint.color = COLOR_NAVY
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            for (i in 0 until estPoints.size - 1) {
                canvas.drawLine(estPoints[i].first, estPoints[i].second, estPoints[i + 1].first, estPoints[i + 1].second, paint)
            }
        }

        // 2. Draw Red Line for Actual Expense
        if (actPoints.size > 1) {
            paint.color = COLOR_RED
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            for (i in 0 until actPoints.size - 1) {
                canvas.drawLine(actPoints[i].first, actPoints[i].second, actPoints[i + 1].first, actPoints[i + 1].second, paint)
            }
        }

        // 3. Draw Dots & Value Labels
        estPoints.forEachIndexed { i, (xPt, yPt) ->
            paint.style = Paint.Style.FILL
            paint.color = COLOR_NAVY
            canvas.drawCircle(xPt, yPt, 3.5f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(xPt, yPt, 1.6f, paint)

            paint.color = COLOR_NAVY
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 6f
            val amtText = formatCompactLkr(itemsToShow[i].estimatedAmountLKR)
            canvas.drawText(amtText, xPt - (paint.measureText(amtText) / 2f), yPt - 4.5f, paint)
        }

        actPoints.forEachIndexed { i, (xPt, yPt) ->
            paint.style = Paint.Style.FILL
            paint.color = COLOR_RED
            canvas.drawCircle(xPt, yPt, 3.5f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(xPt, yPt, 1.6f, paint)

            paint.color = COLOR_RED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 6f
            val amtText = formatCompactLkr(itemsToShow[i].actualExpenseLKR)
            canvas.drawText(amtText, xPt - (paint.measureText(amtText) / 2f), yPt + 9f, paint)
        }

        // 4. X-Axis Labels below baseline
        itemsToShow.forEachIndexed { index, item ->
            val slotCenter = plotLeft + (index * slotWidth) + (slotWidth / 2f)
            paint.color = COLOR_NAVY
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 7f

            val words = item.categoryName.split(" ")
            if (words.size > 1 && paint.measureText(item.categoryName) > slotWidth - 4f) {
                val line1 = words[0]
                val line2 = words.drop(1).joinToString(" ")
                val w1 = paint.measureText(line1)
                val w2 = paint.measureText(line2)
                canvas.drawText(line1, slotCenter - (w1 / 2f), plotBottom + 10f, paint)
                canvas.drawText(line2, slotCenter - (w2 / 2f), plotBottom + 19f, paint)
            } else {
                val displayLabel = if (paint.measureText(item.categoryName) > slotWidth) {
                    item.categoryName.take(8) + ".."
                } else item.categoryName
                val lblW = paint.measureText(displayLabel)
                canvas.drawText(displayLabel, slotCenter - (lblW / 2f), plotBottom + 11f, paint)
            }
        }

        // X-Axis Title at bottom right
        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        val xTitle = "Discretionary Category"
        val xTitleW = paint.measureText(xTitle)
        canvas.drawText(xTitle, plotRight - xTitleW, y + cardHeight - 6f, paint)

        return cardHeight
    }

    // =========================================================================
    // GRAPH EXPLANATION BOX HELPER
    // =========================================================================

    /**
     * Draws a dedicated, styled graph explanation container directly under a chart:
     * - Displays "● Graph Explanation: [Insight/Analysis text]"
     * - Dynamically wraps multi-line explanations without colliding with following sections.
     */
    private fun drawGraphExplanationBox(
        canvas: Canvas,
        y: Float,
        chartName: String,
        explanation: String
    ): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val fullText = "●  Explanation ($chartName): $explanation"
        val words = fullText.split(" ")
        var lineCount = 1
        var currLine = StringBuilder()
        val maxWidth = CONTENT_WIDTH - 24f

        for (w in words) {
            val candidate = if (currLine.isEmpty()) w else "$currLine $w"
            if (paint.measureText(candidate) <= maxWidth) {
                currLine = StringBuilder(candidate)
            } else {
                lineCount++
                currLine = StringBuilder(w)
            }
        }

        val boxHeight = maxOf(22f, 10f + (lineCount * 10.5f))
        val boxRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + boxHeight)

        // Soft Slate/Navy light container background
        paint.color = Color.rgb(241, 245, 249)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(boxRect, 4f, 4f, paint)

        paint.color = Color.rgb(203, 213, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(boxRect, 4f, 4f, paint)

        // Draw wrapped explanation text inside the container
        paint.style = Paint.Style.FILL
        var currY = y + 13f
        currLine = StringBuilder()

        for (w in words) {
            val candidate = if (currLine.isEmpty()) w else "$currLine $w"
            if (paint.measureText(candidate) <= maxWidth) {
                currLine = StringBuilder(candidate)
            } else {
                paint.color = COLOR_DARK_TEXT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(currLine.toString(), MARGIN_LEFT + 12f, currY, paint)
                currY += 10.5f
                currLine = StringBuilder(w)
            }
        }
        if (currLine.isNotEmpty()) {
            paint.color = COLOR_DARK_TEXT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(currLine.toString(), MARGIN_LEFT + 12f, currY, paint)
        }

        return boxHeight
    }

    // =========================================================================
    // GENERAL HELPERS
    // =========================================================================

    private fun formatCompactLkr(amount: Double): String {
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

    private fun drawCardBackground(canvas: Canvas, rect: RectF, fillColor: Int, strokeColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 7f, 7f, paint)

        paint.apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(rect, 7f, 7f, paint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ): Float {
        var currY = startY
        val words = text.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = StringBuilder(candidate)
            } else {
                canvas.drawText(currentLine.toString(), x, currY, paint)
                currY += lineHeight
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine.toString(), x, currY, paint)
            currY += lineHeight
        }
        return currY
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, totalPages: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Divider Line
        paint.color = COLOR_BORDER
        paint.strokeWidth = 0.8f
        canvas.drawLine(MARGIN_LEFT, PAGE_HEIGHT - 32f, MARGIN_RIGHT, PAGE_HEIGHT - 32f, paint)

        // Footer Text
        paint.color = COLOR_MUTED_TEXT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        canvas.drawText("NEXUS Finance Tracker · Private & Confidential Financial Document", MARGIN_LEFT, PAGE_HEIGHT - 18f, paint)

        val pageLabel = "Page $pageNumber of $totalPages"
        val labelWidth = paint.measureText(pageLabel)
        canvas.drawText(pageLabel, MARGIN_RIGHT - labelWidth, PAGE_HEIGHT - 18f, paint)
    }
}
