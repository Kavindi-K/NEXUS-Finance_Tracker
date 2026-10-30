package com.nexus.financetracker.ui.chatbot

import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.domain.model.PaceStatus
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * High-performance, lightweight financial reasoning engine.
 * Computes predictive trajectory, burn-rates, and personalized recommendations
 * entirely on-device from verified user data without third-party exposure.
 */
@Singleton
class FinancialAdvisorEngine @Inject constructor() {

    fun generatePredictionAndAdvice(
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>
    ): String {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        val maxDays = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemainingInMonth = maxDays - currentDay

        // Filter this month's confirmed transactions
        val startOfMonth = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val monthExpenses = expenses.filter { !it.date.before(startOfMonth) }
        val monthIncomes = incomes.filter {
            val d = it.dateReceived ?: it.createdAt
            !d.before(startOfMonth) && it.status == IncomeStatus.CONFIRMED
        }

        val totalSpentThisMonth = monthExpenses.sumOf { it.amountLKR }
        val totalIncomeThisMonth = monthIncomes.sumOf { it.amountLKR }

        val dailyBurnRate = if (currentDay > 0) totalSpentThisMonth / currentDay else 0.0
        val projectedMonthEndExpense = totalSpentThisMonth + (dailyBurnRate * daysRemainingInMonth)
        val projectedNetSavings = totalIncomeThisMonth - projectedMonthEndExpense

        // Discretionary vs Committed breakdown
        val discretionarySum = monthExpenses
            .filter { it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true) }
            .sumOf { it.amountLKR }
        val discretionaryPercent = if (totalSpentThisMonth > 0) (discretionarySum / totalSpentThisMonth) * 100.0 else 0.0

        // Find active goals
        val activeGoals = goals.filter { it.isActive }
        val overdueOrBehindGoals = mutableListOf<Pair<Goal, GoalCalculation>>()
        for (g in activeGoals) {
            val calc = GoalCalculation.calculate(g, incomes, expenses, now.time)
            if (calc.paceStatus == PaceStatus.BEHIND_PACE || calc.paceStatus == PaceStatus.OVERDUE) {
                overdueOrBehindGoals.add(g to calc)
            }
        }

        val sb = StringBuilder()
        sb.append("🔮 **මාසික මූල්‍ය අනාවැකිය (Monthly Prediction):**\n\n")

        sb.append("📅 **දින සාරාංශය:** අද මාසයේ %d වන දින. තව දින %dක් ඉතිරිව ඇත.\n".format(currentDay, daysRemainingInMonth))
        sb.append("💸 **වත්මන් වියදම:** LKR %,.0f\n".format(totalSpentThisMonth))
        sb.append("📊 **දෛනික සාමාන්‍ය වියදම (Daily Pace):** LKR %,.0f / දිනකට\n".format(dailyBurnRate))
        sb.append("🎯 **මාසය අවසානයේ අපේක්ෂිත වියදම:** LKR %,.0f\n".format(projectedMonthEndExpense))

        if (totalIncomeThisMonth > 0) {
            sb.append("💵 **අපේක්ෂිත ශුද්ධ ඉතිරිය (Projected Savings):** LKR %,.0f\n".format(projectedNetSavings))
            if (projectedNetSavings < 0) {
                sb.append("⚠️ *අවධානය:* වත්මන් වේගයෙන් ගියහොත් ඔබේ වියදම ආදායමට වඩා වැඩි වේ!\n")
            } else {
                sb.append("✅ *සුබ ලකුණක්:* ඔබේ ආදායමෙන් %d%% ක් පමණ ඉතිරි කරගත හැකි වනු ඇත.\n".format(((projectedNetSavings / totalIncomeThisMonth) * 100).roundToInt()))
            }
        }

        sb.append("\n💡 **මූල්‍ය උපදෙස් සහ නිර්දේශ (Smart Advice):**\n")

        if (discretionaryPercent > 40.0) {
            sb.append("• ඔබේ වියදම්වලින් %.0f%% ක්ම අනවශ්‍ය/විකල්ප (Discretionary) වියදම් වේ. Dining සහ Shopping වියදම් 15-20%% කින් අඩු කළහොත් මාසය අවසානයේ LKR %,.0f ක අතිරේක මුදලක් ඉතිරි කරගත හැක.\n"
                .format(discretionaryPercent, (discretionarySum * 0.20)))
        } else {
            sb.append("• ඔබේ වියදම් පාලනය ඉතා යහපත් මට්ටමක පවතී. අත්‍යවශ්‍ය නොවන වියදම් අවම කර ඇත.\n")
        }

        if (overdueOrBehindGoals.isNotEmpty()) {
            val (goal, calc) = overdueOrBehindGoals.first()
            sb.append("• **Goal Alert:** '%s' ඉලක්කය සඳහා නියමිත වේලාවට ළඟා වීමට මේ මාසයේදී LKR %,.0f ක් ඉතිරි කිරීම අවශ්‍ය වේ.\n"
                .format(goal.title, max(0.0, calc.requiredMonthlySavingsLKR)))
        }

        sb.append("\n*දෛනිකව වියදම් සටහන් කිරීම මඟින් ඔබේ මූල්‍ය පාලනය 100% ක් නිවැරදිව තබාගන්න.*")

        return sb.toString()
    }

    fun generateSummary(
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>
    ): String {
        val now = Calendar.getInstance()
        val startOfMonth = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val monthExpenses = expenses.filter { !it.date.before(startOfMonth) }
        val monthIncomes = incomes.filter {
            val d = it.dateReceived ?: it.createdAt
            !d.before(startOfMonth) && it.status == IncomeStatus.CONFIRMED
        }

        val totalIncome = monthIncomes.sumOf { it.amountLKR }
        val totalSpent = monthExpenses.sumOf { it.amountLKR }
        val netBalance = totalIncome - totalSpent

        val catMap = categories.associate { it.id to it.name }
        val topCategories = monthExpenses
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                val name = catMap[catId] ?: catId.replace("cat_", "").replaceFirstChar { it.uppercase() }
                name to list.sumOf { it.amountLKR }
            }
            .sortedByDescending { it.second }
            .take(3)

        val activeGoals = goals.filter { it.isActive }

        val sb = StringBuilder()
        sb.append("📊 **මේ මාසයේ මූල්‍ය සාරාංශය (Monthly Summary):**\n\n")
        sb.append("💰 **ලැබුණු ආදායම:** LKR %,.0f\n".format(totalIncome))
        sb.append("💸 **කරන ලද වියදම:** LKR %,.0f\n".format(totalSpent))
        sb.append("⚖️ **ඉතිරි ශේෂය (Net):** %sLKR %,.0f\n".format(if (netBalance >= 0) "+" else "", netBalance))

        if (topCategories.isNotEmpty()) {
            sb.append("\n🏷️ **වැඩිපුරම වියදම් වූ අංශ (Top Categories):**\n")
            topCategories.forEachIndexed { i, (name, amount) ->
                val percent = if (totalSpent > 0) (amount / totalSpent) * 100.0 else 0.0
                sb.append("${i + 1}. **$name**: LKR %,.0f (%.0f%%)\n".format(amount, percent))
            }
        }

        if (activeGoals.isNotEmpty()) {
            sb.append("\n🎯 **ක්‍රියාකාරී ඉලක්ක (Active Goals):** ${activeGoals.size} ක් ක්‍රියාත්මකයි.")
        }

        return sb.toString()
    }

    fun generateGoalPaceReport(
        goals: List<Goal>,
        incomes: List<Income>,
        expenses: List<Expense>
    ): String {
        val activeGoals = goals.filter { it.isActive }
        if (activeGoals.isEmpty()) {
            return "🎯 ඔබට දැනට ක්‍රියාකාරී Savings Goals කිසිවක් නොමැත. අලුත් Goal එකක් එකතු කිරීමට *'Save 50000 for Laptop'* වැනි විධානයක් ලබා දෙන්න."
        }

        val sb = StringBuilder()
        sb.append("🎯 **ඔබේ Savings Goals වල ප්‍රගතිය (Goal Pace Report):**\n\n")

        for (goal in activeGoals) {
            val calc = GoalCalculation.calculate(goal, incomes, expenses, Date())
            val statusEmoji = when (calc.paceStatus) {
                PaceStatus.AHEAD_OF_PACE -> "🚀 Ahead"
                PaceStatus.ON_TRACK -> "✅ On Track"
                PaceStatus.BEHIND_PACE -> "⚠️ Behind Pace"
                PaceStatus.OVERDUE -> "❌ Overdue"
                PaceStatus.COMPLETED -> "🎉 Completed"
                PaceStatus.INSUFFICIENT_HISTORY -> "ℹ️ In Progress"
            }

            sb.append("• **${goal.title}** ($statusEmoji)\n")
            sb.append("   - ඉලක්කය: LKR %,.0f | ඉතිරිව ඇත්තේ: LKR %,.0f (%.1f%%)\n"
                .format(goal.targetAmountLKR, calc.remainingAmountLKR, calc.progressPercentage))
            if (calc.requiredMonthlySavingsLKR > 0) {
                sb.append("   - අවශ්‍ය මාසික ඉතිරිය: LKR %,.0f\n".format(calc.requiredMonthlySavingsLKR))
            }
            sb.append("   - ඉතිරි කාලය: %s\n\n".format(calc.daysRemainingText))
        }

        return sb.toString().trimEnd()
    }
}
