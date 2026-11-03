package com.nexus.financetracker.ui.chatbot

import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalCalculation
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.domain.model.PaceStatus
import com.nexus.financetracker.domain.model.RecurringItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-performance, intelligent financial reasoning engine.
 * Computes deep financial analytics, health scores, affordability checks,
 * emergency runway calculations, and personalized recommendations
 * entirely on-device from live verified user data without third-party exposure.
 */
@Singleton
class FinancialAdvisorEngine @Inject constructor() {

    fun analyzeFinancialHealth(
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        userName: String,
        isSinhala: Boolean = false
    ): String {
        val (monthIncomes, monthExpenses) = filterCurrentMonth(incomes, expenses)
        val totalIncome = monthIncomes.sumOf { it.amountLKR }
        val totalSpent = monthExpenses.sumOf { it.amountLKR }
        val netSavings = totalIncome - totalSpent

        val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100.0).coerceAtLeast(0.0) else 0.0
        val runwayMonths = if (totalSpent > 0) netSavings / totalSpent else if (netSavings > 0) 12.0 else 0.0

        // Financial Health Score calculation (0 to 100)
        var score = 0
        score += when {
            savingsRate >= 50.0 -> 40
            savingsRate >= 30.0 -> 35
            savingsRate >= 20.0 -> 30
            savingsRate >= 10.0 -> 20
            else -> 10
        }
        score += when {
            runwayMonths >= 6.0 -> 30
            runwayMonths >= 3.0 -> 25
            runwayMonths >= 1.0 -> 15
            else -> 5
        }
        val activeGoals = goals.filter { it.isActive }
        val hasOverdue = activeGoals.any {
            val calc = GoalCalculation.calculate(it, incomes, expenses, Date())
            calc.paceStatus == PaceStatus.OVERDUE
        }
        score += if (!hasOverdue) 30 else 15
        score = min(100, score)

        val tierName = when {
            score >= 90 -> if (isSinhala) "විශිෂ්ටයි (Elite Wealth Builder)" else "Elite Wealth Builder (Grade A+)"
            score >= 75 -> if (isSinhala) "ඉතා යහපත් (Strong Saver)" else "Strong Saver (Grade A)"
            score >= 50 -> if (isSinhala) "මධ්‍යස්ථයි (Moderate)" else "Moderate Discipline (Grade B)"
            else -> if (isSinhala) "අවධානය අවශ්‍යයි" else "Needs Improvement (Grade C)"
        }

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("🏆 **මූල්‍ය සෞඛ්‍ය තත්වය (Financial Health Score):**\n\n")
            sb.append("• **ලකුණු:** **$score / 100** ($tierName)\n")
            sb.append("• **පරිශීලක:** $userName\n")
            sb.append("• **වත්මන් ආදායම:** LKR %,.2f\n".format(totalIncome))
            sb.append("• **වත්මන් වියදම:** LKR %,.2f\n".format(totalSpent))
            sb.append("• **ශුද්ධ ඉතිරිය:** LKR %,.2f\n".format(netSavings))
            sb.append("• **ඉතිරිකිරීමේ ප්‍රතිශතය:** %.1f%%\n".format(savingsRate))
            sb.append("• **හදිසි අරමුදල් ආවරණය (Runway):** මාස %.1f ක කාලයක්\n\n".format(runwayMonths))

            sb.append("💡 **විශ්ලේෂණය සහ නිර්දේශ:**\n")
            if (savingsRate >= 50.0) {
                sb.append("• ඔබේ ඉතිරිකිරීමේ අනුපාතය ඉතා විශිෂ්ටයි! ඔබ උපයන මුදලින් බහුතරයක් ඉතිරි කරගැනීමට සමත් වී ඇත.\n")
                sb.append("• මෙම අතිරික්ත ඉතිරිය ඔබේ සක්‍රීය Goals වලට හෝ ආයෝජන (Fixed Deposits / Treasury Bills) සඳහා යෙදවීමට සලකා බලන්න.\n")
            } else if (savingsRate >= 20.0) {
                sb.append("• ඔබ සාර්ථකව 20% ක අවම ඉතිරිකිරීමේ ඉලක්කය පවත්වාගෙන යයි. අත්‍යවශ්‍ය නොවන වියදම් පාලනය කිරීමෙන් මෙය තවත් ඉහළ නැංවිය හැක.\n")
            } else {
                sb.append("• ඔබේ වියදම් ආදායමට වඩා සමීපව පවතී. Dining සහ Shopping වියදම් 15% කින් සීමා කර ආරක්ෂිත ඉතිරියක් ගොඩනගා ගන්න.\n")
            }
        } else {
            sb.append("🏆 **Financial Health & Wealth Assessment:**\n\n")
            sb.append("• **Overall Score:** **$score / 100** · $tierName\n")
            sb.append("• **User:** $userName\n")
            sb.append("• **Total Income:** LKR %,.2f\n".format(totalIncome))
            sb.append("• **Total Expenses:** LKR %,.2f\n".format(totalSpent))
            sb.append("• **Net Balance:** LKR %,.2f\n".format(netSavings))
            sb.append("• **Savings Rate:** %.1f%%\n".format(savingsRate))
            sb.append("• **Emergency Runway:** %.1f months of living expenses covered\n\n".format(runwayMonths))

            sb.append("💡 **Key Insights & Guidance:**\n")
            if (savingsRate >= 50.0) {
                sb.append("• Outstanding financial discipline! Retaining %.1f%% of your income puts you in the top tier of personal wealth accumulators.\n".format(savingsRate))
                sb.append("• Consider deploying surplus funds towards your active Savings Goals or high-yield savings to beat inflation.\n")
            } else if (savingsRate >= 20.0) {
                sb.append("• Solid performance! You are meeting the benchmark 20% savings rule. Trimming small discretionary leaks can accelerate your goals.\n")
            } else {
                sb.append("• Your expenses are absorbing most of your incoming cash flow. Implementing the 50/30/20 budget framework will help create a financial cushion.\n")
            }
        }

        return sb.toString().trimEnd()
    }

    fun checkAffordability(
        amount: Double,
        incomes: List<Income>,
        expenses: List<Expense>,
        isSinhala: Boolean = false
    ): String {
        val (monthIncomes, monthExpenses) = filterCurrentMonth(incomes, expenses)
        val totalIncome = monthIncomes.sumOf { it.amountLKR }
        val totalSpent = monthExpenses.sumOf { it.amountLKR }
        val currentNet = max(0.0, totalIncome - totalSpent)

        val percentOfNet = if (currentNet > 0) (amount / currentNet) * 100.0 else 100.0
        val remainingAfterPurchase = currentNet - amount

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("💳 **මිලදී ගැනීමේ හැකියාව විශ්ලේෂණය (Affordability Check):**\n\n")
            sb.append("• **අපේක්ෂිත මිලදී ගැනීම:** LKR %,.2f\n".format(amount))
            sb.append("• **වත්මන් ශුද්ධ ඉතිරිය:** LKR %,.2f\n".format(currentNet))

            if (amount <= currentNet * 0.30) {
                sb.append("• **තීරණය:** ✅ **පහසුවෙන්ම මිලදී ගත හැක (Safely Affordable)**\n\n")
                sb.append("මෙම මුදල ඔබ සතු වත්මන් ශුද්ධ ඉතිරියෙන් %.1f%% ක් පමණි. මිලදී ගැනීමෙන් පසුවද LKR %,.2f ක ශේෂයක් ඉතිරි වන බැවින් ඔබේ මූල්‍ය ස්ථාවරත්වයට හෝ Goals වලට කිසිදු තර්ජනයක් නැත."
                    .format(percentOfNet, remainingAfterPurchase))
            } else if (amount <= currentNet) {
                sb.append("• **තීරණය:** ⚠️ **මිලදී ගත හැක, නමුත් අවධානයෙන් (Manageable with Caution)**\n\n")
                sb.append("මෙම වියදම ඔබේ මාසික ඉතිරියෙන් %.1f%% ක්ම වැය කරයි. මිලදී ගැනීමෙන් පසු ඉතිරි වන්නේ LKR %,.2f ක් පමණි. අත්‍යවශ්‍යම භාණ්ඩයක් නම් පමණක් මිලදී ගැනීම සුදුසුය."
                    .format(percentOfNet, remainingAfterPurchase))
            } else {
                sb.append("• **තීරණය:** ❌ **දැනට මිලදී ගැනීම නිර්දේශ නොකරයි (Not Recommended)**\n\n")
                sb.append("මෙම මුදල ඔබේ දැනට පවතින ශුද්ධ ඉතිරිය ඉක්මවා යයි. මෙය සිදුකළහොත් ඔබේ මාසික අයවැය හිඟයකට ලක්වනු ඇත. Goal එකක් ලෙස සකසා මුදල් ඉතිරි කර ගැනීම වඩාත් සුදුසුය.")
            }
        } else {
            sb.append("💳 **Purchase Affordability Assessment:**\n\n")
            sb.append("• **Target Purchase Amount:** LKR %,.2f\n".format(amount))
            sb.append("• **Current Available Net Savings:** LKR %,.2f\n".format(currentNet))

            if (amount <= currentNet * 0.30) {
                sb.append("• **Verdict:** ✅ **Safely Affordable**\n\n")
                sb.append("This purchase accounts for only %.1f%% of your current net balance. After this expense, you will still retain LKR %,.2f in safe reserves. It will not jeopardize your monthly financial stability or active goals."
                    .format(percentOfNet, remainingAfterPurchase))
            } else if (amount <= currentNet) {
                sb.append("• **Verdict:** ⚠️ **Affordable with Caution**\n\n")
                sb.append("This purchase will consume %.1f%% of your available net savings, leaving you with LKR %,.2f. While you have the funds, ensure no major unexpected bills are due before proceeding."
                    .format(percentOfNet, remainingAfterPurchase))
            } else {
                sb.append("• **Verdict:** ❌ **Not Recommended at Present**\n\n")
                sb.append("This purchase exceeds your current available net savings by LKR %,.2f. Proceeding would create a budget deficit. We suggest setting up a dedicated Savings Goal to fund this purchase over time."
                    .format(amount - currentNet))
            }
        }

        return sb.toString()
    }

    fun analyzeExpenseBreakdown(
        expenses: List<Expense>,
        categories: List<Category>,
        isSinhala: Boolean = false
    ): String {
        val (_, monthExpenses) = filterCurrentMonth(emptyList(), expenses)
        val totalSpent = monthExpenses.sumOf { it.amountLKR }

        if (totalSpent <= 0) {
            return if (isSinhala) "📊 මේ මාසයේ කිසිදු වියදමක් සටහන් කර නොමැත." else "📊 No expenses have been recorded for the current month."
        }

        val catMap = categories.associate { it.id to it.name }
        val categoryTotals = monthExpenses
            .groupBy { it.categoryId }
            .map { (catId, list) ->
                val name = catMap[catId] ?: catId.replace("cat_", "").replaceFirstChar { it.uppercase() }
                val sum = list.sumOf { it.amountLKR }
                val classification = list.firstOrNull()?.classification ?: ExpenseClassification.DISCRETIONARY
                Triple(name, sum, classification)
            }
            .sortedByDescending { it.second }

        val committedSum = categoryTotals.filter { it.third.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }.sumOf { it.second }
        val discretionarySum = categoryTotals.filter { it.third.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true) }.sumOf { it.second }

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("🔍 **වියදම් පිළිබඳ සවිස්තරාත්මක විශ්ලේෂණය (Spending Breakdown):**\n\n")
            sb.append("• **මුළු වියදම:** LKR %,.2f\n".format(totalSpent))
            sb.append("• **අත්‍යවශ්‍ය (Committed/Bills):** LKR %,.0f (%.1f%%)\n".format(committedSum, (committedSum / totalSpent) * 100))
            sb.append("• **විකල්ප (Discretionary):** LKR %,.0f (%.1f%%)\n\n".format(discretionarySum, (discretionarySum / totalSpent) * 100))

            sb.append("🏷️ **අංශ අනුව වර්ගීකරණය:**\n")
            categoryTotals.forEachIndexed { i, (name, sum, _) ->
                val pct = (sum / totalSpent) * 100.0
                val formattedSum = "LKR %,.0f (%.1f%%)".format(sum, pct)
                sb.append("${i + 1}. **$name:** $formattedSum\n")
            }

            val highest = categoryTotals.firstOrNull()
            if (highest != null) {
                sb.append("\n📌 ඔබේ වැඩිම වියදම සිදුවී ඇත්තේ **%s** සඳහායි (%.1f%%).".format(highest.first, (highest.second / totalSpent) * 100))
            }
        } else {
            sb.append("🔍 **Detailed Spending Analytics & Breakdown:**\n\n")
            sb.append("• **Total Spending:** LKR %,.2f\n".format(totalSpent))
            sb.append("• **Fixed & Committed Needs:** LKR %,.0f (%.1f%%)\n".format(committedSum, (committedSum / totalSpent) * 100))
            sb.append("• **Discretionary Wants:** LKR %,.0f (%.1f%%)\n\n".format(discretionarySum, (discretionarySum / totalSpent) * 100))

            sb.append("🏷️ **Top Categories Breakdown:**\n")
            categoryTotals.forEachIndexed { i, (name, sum, _) ->
                val pct = (sum / totalSpent) * 100.0
                val formattedSum = "LKR %,.0f (%.1f%%)".format(sum, pct)
                sb.append("${i + 1}. **$name:** $formattedSum\n")
            }

            val highest = categoryTotals.firstOrNull()
            if (highest != null) {
                sb.append("\n📌 Primary spending driver: **%s** absorbing %.1f%% of your total outflow.".format(highest.first, (highest.second / totalSpent) * 100))
            }
        }

        return sb.toString().trimEnd()
    }

    fun analyzeEmergencyRunway(
        incomes: List<Income>,
        expenses: List<Expense>,
        isSinhala: Boolean = false
    ): String {
        val (monthIncomes, monthExpenses) = filterCurrentMonth(incomes, expenses)
        val totalIncome = monthIncomes.sumOf { it.amountLKR }
        val totalSpent = monthExpenses.sumOf { it.amountLKR }
        val currentNet = max(0.0, totalIncome - totalSpent)

        val runwayMonths = if (totalSpent > 0) currentNet / totalSpent else 24.0

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("🛡️ **හදිසි අරමුදල සහ පැවැත්මේ කාලය (Emergency Runway):**\n\n")
            sb.append("• **වත්මන් ශුද්ධ ඉතිරිය:** LKR %,.2f\n".format(currentNet))
            sb.append("• **මාසික සාමාන්‍ය වියදම:** LKR %,.2f\n".format(totalSpent))
            sb.append("• **පැවැත්මේ කාලය (Runway):** **මාස %.1f ක් (දින %dක්)**\n\n".format(runwayMonths, (runwayMonths * 30).roundToInt()))

            if (runwayMonths >= 6.0) {
                sb.append("✅ **සුපිරි ආරක්ෂාවක්:** කිසිදු ආදායමක් නොලැබුණද ඔබට මාස 6කට වඩා වැඩි කාලයක් සාමාන්‍ය පරිදි ජීවත් වීමට ප්‍රමාණවත් මුදලක් ඇත.")
            } else if (runwayMonths >= 3.0) {
                sb.append("✅ **යහපත් ආරක්ෂාවක්:** ඔබට මාස 3ක මූල්‍ය ආරක්ෂක ආවරණයක් පවතී. මෙය මාස 6 දක්වා වර්ධනය කර ගැනීමට උත්සාහ කරන්න.")
            } else {
                sb.append("⚠️ **අවදානම් කලාපය:** ඔබේ හදිසි අරමුදල මාස 3කට වඩා අඩුය. හදිසි අවස්ථාවකදී ආරක්ෂා වීම සඳහා මාස 3ක වියදම් ඉතිරි කරගැනීම ප්‍රමුඛතාවය කරගන්න.")
            }
        } else {
            sb.append("🛡️ **Emergency Fund & Survival Runway Analysis:**\n\n")
            sb.append("• **Liquid Net Reserves:** LKR %,.2f\n".format(currentNet))
            sb.append("• **Monthly Outflow:** LKR %,.2f\n".format(totalSpent))
            sb.append("• **Emergency Runway:** **%.1f Months (~%d Days)**\n\n".format(runwayMonths, (runwayMonths * 30).roundToInt()))

            if (runwayMonths >= 6.0) {
                sb.append("✅ **Elite Financial Fortification:** Your savings can sustain your current lifestyle for more than 6 months even with zero income. You have achieved golden benchmark financial resilience.")
            } else if (runwayMonths >= 3.0) {
                sb.append("✅ **Healthy Safety Cushion:** You have over 3 months of emergency buffer. Strive to build this up towards a full 6-month buffer.")
            } else {
                sb.append("⚠️ **Building Required:** Your emergency runway is under 3 months. Financial planners advise prioritizing a liquid 3-6 month safety fund before making discretionary commitments.")
            }
        }

        return sb.toString()
    }

    fun provide50_30_20Budget(
        incomes: List<Income>,
        expenses: List<Expense>,
        isSinhala: Boolean = false
    ): String {
        val (monthIncomes, monthExpenses) = filterCurrentMonth(incomes, expenses)
        val totalIncome = monthIncomes.sumOf { it.amountLKR }
        val totalSpent = monthExpenses.sumOf { it.amountLKR }

        val targetNeeds = totalIncome * 0.50
        val targetWants = totalIncome * 0.30
        val targetSavings = totalIncome * 0.20

        val actualCommitted = monthExpenses
            .filter { it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }
            .sumOf { it.amountLKR }
        val actualDiscretionary = monthExpenses
            .filter { it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true) }
            .sumOf { it.amountLKR }
        val actualSavings = max(0.0, totalIncome - totalSpent)

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("📐 **50/30/20 මූල්‍ය අයවැය රීතිය (50/30/20 Rule Analysis):**\n\n")
            sb.append("ඔබේ මාසික ආදායම (LKR %,.0f) අනුව නිවැරදි වෙන්කිරීම මෙසේ විය යුතුය:\n\n".format(totalIncome))
            sb.append("1. **50% අත්‍යවශ්‍ය (Needs):**\n")
            sb.append("   - ඉලක්කය: LKR %,.0f | සත්‍ය වැය: LKR %,.0f (%.1f%%)\n".format(targetNeeds, actualCommitted, if (totalIncome > 0) (actualCommitted / totalIncome) * 100 else 0.0))
            sb.append("2. **30% විකල්ප/කැමැත්ත (Wants):**\n")
            sb.append("   - ඉලක්කය: LKR %,.0f | සත්‍ය වැය: LKR %,.0f (%.1f%%)\n".format(targetWants, actualDiscretionary, if (totalIncome > 0) (actualDiscretionary / totalIncome) * 100 else 0.0))
            sb.append("3. **20% ඉතිරිකිරීම් (Savings):**\n")
            sb.append("   - ඉලක්කය: LKR %,.0f | සත්‍ය ඉතිරිය: LKR %,.0f (%.1f%%)\n\n".format(targetSavings, actualSavings, if (totalIncome > 0) (actualSavings / totalIncome) * 100 else 0.0))

            if (actualSavings >= targetSavings) {
                sb.append("🎯 **තීන්දුව:** විශිෂ්ටයි! ඔබ නියමිත 20% ඉලක්කයටත් වඩා ඉතිරි කරමින් 50/30/20 රීතිය ඉතා සාර්ථකව ජයගෙන ඇත.")
            } else {
                sb.append("🎯 **තීන්දුව:** ඔබේ Wants (කෑම/Shopping) වියදම් අඩු කර ඉතිරිකිරීම 20% සීමාව කරා ගෙන යාමට උත්සාහ කරන්න.")
            }
        } else {
            sb.append("📐 **The 50/30/20 Financial Budget Blueprint:**\n\n")
            sb.append("Based on your confirmed monthly income of LKR %,.0f, here is how your capital should be distributed:\n\n".format(totalIncome))
            sb.append("1. **50% Needs (Essential Bills & Housing):**\n")
            sb.append("   - Target: LKR %,.0f · Actual: LKR %,.0f (%.1f%% of income)\n".format(targetNeeds, actualCommitted, if (totalIncome > 0) (actualCommitted / totalIncome) * 100 else 0.0))
            sb.append("2. **30% Wants (Dining & Lifestyle):**\n")
            sb.append("   - Target: LKR %,.0f · Actual: LKR %,.0f (%.1f%% of income)\n".format(targetWants, actualDiscretionary, if (totalIncome > 0) (actualDiscretionary / totalIncome) * 100 else 0.0))
            sb.append("3. **20% Wealth & Savings (Goals & Investments):**\n")
            sb.append("   - Target: LKR %,.0f · Actual: LKR %,.0f (%.1f%% of income)\n\n".format(targetSavings, actualSavings, if (totalIncome > 0) (actualSavings / totalIncome) * 100 else 0.0))

            if (actualSavings >= targetSavings) {
                sb.append("🎯 **Evaluation:** Exemplary! You are exceeding the recommended 20% threshold, building true long-term wealth.")
            } else {
                sb.append("🎯 **Evaluation:** Your lifestyle wants are slightly constraining your savings. Shift 5-10% from wants into savings to hit the golden 20% target.")
            }
        }

        return sb.toString()
    }

    fun generatePredictionAndAdvice(
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>,
        isSinhala: Boolean = false
    ): String {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        val maxDays = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemainingInMonth = maxDays - currentDay

        val (monthIncomes, monthExpenses) = filterCurrentMonth(incomes, expenses)
        val totalSpentThisMonth = monthExpenses.sumOf { it.amountLKR }
        val totalIncomeThisMonth = monthIncomes.sumOf { it.amountLKR }

        val dailyBurnRate = if (currentDay > 0) totalSpentThisMonth / currentDay else 0.0
        val projectedMonthEndExpense = totalSpentThisMonth + (dailyBurnRate * daysRemainingInMonth)
        val projectedNetSavings = totalIncomeThisMonth - projectedMonthEndExpense

        val discretionarySum = monthExpenses
            .filter { it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true) }
            .sumOf { it.amountLKR }
        val discretionaryPercent = if (totalSpentThisMonth > 0) (discretionarySum / totalSpentThisMonth) * 100.0 else 0.0

        val activeGoals = goals.filter { it.isActive }
        val overdueOrBehindGoals = mutableListOf<Pair<Goal, GoalCalculation>>()
        for (g in activeGoals) {
            val calc = GoalCalculation.calculate(g, incomes, expenses, now.time)
            if (calc.paceStatus == PaceStatus.BEHIND_PACE || calc.paceStatus == PaceStatus.OVERDUE) {
                overdueOrBehindGoals.add(g to calc)
            }
        }

        val sb = StringBuilder()
        if (isSinhala) {
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
        } else {
            sb.append("🔮 **Monthly Financial Forecast & Insights:**\n\n")
            sb.append("📅 **Timeline:** Day %d of the month · %d days remaining\n".format(currentDay, daysRemainingInMonth))
            sb.append("💸 **Current Month-to-Date Spend:** LKR %,.0f\n".format(totalSpentThisMonth))
            sb.append("📊 **Daily Burn Rate:** LKR %,.0f / day\n".format(dailyBurnRate))
            sb.append("🎯 **Projected Total Month-End Spending:** LKR %,.0f\n".format(projectedMonthEndExpense))

            if (totalIncomeThisMonth > 0) {
                sb.append("💵 **Projected Net Savings:** LKR %,.0f\n".format(projectedNetSavings))
                if (projectedNetSavings < 0) {
                    sb.append("⚠️ *Warning:* At this rate, your spending will exceed your confirmed income!\n")
                } else {
                    val savingsRate = ((projectedNetSavings / totalIncomeThisMonth) * 100).roundToInt()
                    sb.append("✅ *Healthy Trajectory:* You are on track to save approximately %d%% of your monthly income.\n".format(savingsRate))
                }
            }

            sb.append("\n💡 **Actionable Recommendations:**\n")
            if (discretionaryPercent > 40.0) {
                sb.append("• Discretionary spending accounts for %.0f%% of your total expenses. Trimming dining out and shopping by 15-20%% can save an extra LKR %,.0f this month.\n"
                    .format(discretionaryPercent, (discretionarySum * 0.20)))
            } else {
                sb.append("• Great financial discipline! Your discretionary expenses are well-balanced and within healthy limits.\n")
            }

            if (overdueOrBehindGoals.isNotEmpty()) {
                val (goal, calc) = overdueOrBehindGoals.first()
                sb.append("• **Goal Alert:** You need to save LKR %,.0f this month to stay on pace for '%s'.\n"
                    .format(max(0.0, calc.requiredMonthlySavingsLKR), goal.title))
            }
            sb.append("\n*Tip: Logging expenses daily keeps your real-time burn rate accurate.*")
        }

        return sb.toString()
    }

    fun generateSummary(
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>,
        isSinhala: Boolean = false
    ): String {
        val (monthIncomes, monthExpenses) = filterCurrentMonth(incomes, expenses)
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
        if (isSinhala) {
            sb.append("📊 **මේ මාසයේ මූල්‍ය සාරාංශය (Monthly Summary):**\n\n")
            sb.append("💰 **ලැබුණු ආදායම:** LKR %,.0f\n".format(totalIncome))
            sb.append("💸 **කරන ලද වියදම:** LKR %,.0f\n".format(totalSpent))
            sb.append("⚖️ **ඉතිරි ශේෂය (Net):** %sLKR %,.0f\n".format(if (netBalance >= 0) "+" else "", netBalance))

            if (topCategories.isNotEmpty()) {
                sb.append("\n🏷️ **වැඩිපුරම වියදම් වූ අංශ (Top Categories):**\n")
                topCategories.forEachIndexed { i, (name, amount) ->
                    val percent = if (totalSpent > 0) (amount / totalSpent) * 100.0 else 0.0
                    val formattedAmount = "LKR %,.0f (%.0f%%)".format(amount, percent)
                    sb.append("${i + 1}. **$name**: $formattedAmount\n")
                }
            }

            if (activeGoals.isNotEmpty()) {
                sb.append("\n🎯 **ක්‍රියාකාරී ඉලක්ක (Active Goals):** ${activeGoals.size} ක් ක්‍රියාත්මකයි.")
            }
        } else {
            sb.append("📊 **Monthly Financial Summary:**\n\n")
            sb.append("💰 **Confirmed Income:** LKR %,.0f\n".format(totalIncome))
            sb.append("💸 **Total Expenses:** LKR %,.0f\n".format(totalSpent))
            sb.append("⚖️ **Net Savings:** %sLKR %,.0f\n".format(if (netBalance >= 0) "+" else "", netBalance))

            if (topCategories.isNotEmpty()) {
                sb.append("\n🏷️ **Top Spending Categories:**\n")
                topCategories.forEachIndexed { i, (name, amount) ->
                    val percent = if (totalSpent > 0) (amount / totalSpent) * 100.0 else 0.0
                    val formattedAmount = "LKR %,.0f (%.0f%%)".format(amount, percent)
                    sb.append("${i + 1}. **$name**: $formattedAmount\n")
                }
            }

            if (activeGoals.isNotEmpty()) {
                sb.append("\n🎯 **Active Savings Goals:** ${activeGoals.size} in progress.")
            }
        }

        return sb.toString()
    }

    fun generateGoalPaceReport(
        goals: List<Goal>,
        incomes: List<Income>,
        expenses: List<Expense>,
        isSinhala: Boolean = false
    ): String {
        val activeGoals = goals.filter { it.isActive }
        if (activeGoals.isEmpty()) {
            return if (isSinhala) {
                "🎯 ඔබට දැනට ක්‍රියාකාරී Savings Goals කිසිවක් නොමැත. අලුත් Goal එකක් එකතු කිරීමට *'Save 50000 for Laptop'* වැනි විධානයක් ලබා දෙන්න."
            } else {
                "🎯 You have no active Savings Goals currently. You can create one anytime by saying *'Save 100000 for Emergency Fund'*."
            }
        }

        val sb = StringBuilder()
        if (isSinhala) {
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
        } else {
            sb.append("🎯 **Savings Goals Progress & Pace Report:**\n\n")
            for (goal in activeGoals) {
                val calc = GoalCalculation.calculate(goal, incomes, expenses, Date())
                val statusText = when (calc.paceStatus) {
                    PaceStatus.AHEAD_OF_PACE -> "🚀 Ahead of Pace"
                    PaceStatus.ON_TRACK -> "✅ On Track"
                    PaceStatus.BEHIND_PACE -> "⚠️ Behind Pace"
                    PaceStatus.OVERDUE -> "❌ Overdue"
                    PaceStatus.COMPLETED -> "🎉 Completed"
                    PaceStatus.INSUFFICIENT_HISTORY -> "ℹ️ In Progress"
                }

                sb.append("• **${goal.title}** ($statusText)\n")
                sb.append("   - Target: LKR %,.0f | Remaining: LKR %,.0f (%.1f%% complete)\n"
                    .format(goal.targetAmountLKR, calc.remainingAmountLKR, calc.progressPercentage))
                if (calc.requiredMonthlySavingsLKR > 0) {
                    sb.append("   - Required Monthly Savings: LKR %,.0f\n".format(calc.requiredMonthlySavingsLKR))
                }
                sb.append("   - Time Remaining: %s\n\n".format(calc.daysRemainingText))
            }
        }

        return sb.toString().trimEnd()
    }

    private fun filterCurrentMonth(
        incomes: List<Income>,
        expenses: List<Expense>
    ): Pair<List<Income>, List<Expense>> {
        val now = Calendar.getInstance()
        val startOfMonth = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val mExpenses = expenses.filter { !it.date.before(startOfMonth) }
        val mIncomes = incomes.filter {
            val d = it.dateReceived ?: it.createdAt
            !d.before(startOfMonth) && it.status == IncomeStatus.CONFIRMED
        }
        return Pair(mIncomes, mExpenses)
    }

    fun explainAppFeature(topic: AppTopic, isSinhala: Boolean = false): String {
        return if (isSinhala) {
            when (topic) {
                AppTopic.OFFLINE_SYNC ->
                    "📱 **NEXUS Offline First & Dual-Sync Architecture:**\n\n" +
                    "NEXUS නිර්මාණය කර ඇත්තේ පූර්ණ Offline-First තාක්ෂණයෙනි.\n" +
                    "• **Room SQLite Local Database:** අන්තර්ජාලය නොමැතිව වුවද ඔබට වියදම්, ආදායම්, goals හා categories කිසිදු බාධාවකින් තොරව එක් කළ හැක.\n" +
                    "• **Firestore Cloud Sync:** අන්තර්ජාල සම්බන්ධතාවය ලැබුණු වහාම පසුබිමින් දත්ත ක්ලවුඩ් එක සමඟ ස්වයංක්‍රීයව සමමුහුර්ත (sync) වේ.\n" +
                    "• ඔබගේ දත්ත කිසිදා නැති නොවන අතර උපාංගය තුළ මෙන්ම Cloud එකේද සුරක්ෂිතව පවතී."
                AppTopic.BUDGET_50_30_20 ->
                    "📐 **50/30/20 රන් අයවැය රීතිය (Golden Budget Rule):**\n\n" +
                    "ජාත්‍යන්තර මූල්‍ය විශේෂඥයින් අනුමත කරන ප්‍රශස්ත ක්‍රමය:\n" +
                    "• **50% Needs (අත්‍යවශ්‍ය වියදම්):** නිවාස කුලිය, විදුලි/ජල බිල්පත්, ආහාර, වෛද්‍ය සහ ප්‍රවාහන.\n" +
                    "• **30% Wants (විකල්ප/ජීවන රටා වියදම්):** පිටතින් කෑම ගැනීම, විනෝදාස්වාදය, ෂොපින්.\n" +
                    "• **20% Wealth & Savings (ධනය හා ඉතිරිකිරීම්):** Savings Goals, ණය පියවීම් සහ ආයෝජන.\n" +
                    "NEXUS තුළ ඔබේ සියලු වියදම් Committed සහ Discretionary ලෙස වර්ගීකරණය වන්නේ මෙම රීතියට අනුකූලවයි."
                AppTopic.EMERGENCY_RUNWAY ->
                    "🛡️ **හදිසි අරමුදල සහ Emergency Runway:**\n\n" +
                    "හදිසි අරමුදලක් යනු කිසියම් අනපේක්ෂිත සිදුවීමකදී (රැකියාව අහිමිවීම, හදිසි රෝගාබාධ) ඔබේ ජීවිතය ගැටගසා ගැනීමට ඇති ආරක්ෂිත දැලයි.\n" +
                    "• **සම්මතය:** අවම වශයෙන් මාස 3 සිට 6 දක්වා ජීවන වියදම් ආවරණය විය යුතුය.\n" +
                    "• **NEXUS Runway:** ඔබගේ සාමාන්‍ය මාසික වියදමෙන් ඔබ සතු ශුද්ධ ඉතිරිය බෙදීමෙන්, ආදායමක් නොමැතිව ඔබට ජීවත් විය හැකි නිශ්චිත මාස සහ දින ගණන ගණනය කෙරේ."
                AppTopic.BIOMETRIC_SECURITY ->
                    "🔐 **ජෛවමිතික ආරක්ෂාව (Biometric Security):**\n\n" +
                    "• NEXUS ඔබගේ මූල්‍ය රහස්‍යභාවය සුරැකීමට Fingerprint සහ Face Unlock තාක්ෂණය භාවිතා කරයි.\n" +
                    "• Settings වෙත ගොස් **'Biometric Lock'** සක්‍රීය කළ හැක. ඔබ ඇප් එකෙන් ඉවත් වූ විට එය ස්වයංක්‍රීයව අගුළු වැටේ.\n" +
                    "• ඔබගේ දත්ත ඔබගේ උපාංගය සහ ඔබගේ පුද්ගලික ගිණුමට පමණක් සීමා වේ."
                AppTopic.GOALS_PACE ->
                    "🎯 **Savings Goals & Smart Pace Tracking:**\n\n" +
                    "NEXUS තුළ ඉලක්කයක් සැකසූ විට:\n" +
                    "• **Dynamic Pace Projection:** ඉලක්කගත දිනයට ළඟාවීමට මේ මාසයේ ඉතිරි කළ යුතු මුදල ස්වයංක්‍රීයව ගණනය වේ.\n" +
                    "• **Pace Statuses:** Ahead of Pace (ඉදිරියෙන්), On Track (නියමිත වේගයෙන්), Behind Pace (පසුපසින්) හෝ Overdue (කල් ඉකුත් වූ).\n" +
                    "• **Milestones:** 25%, 50%, 75% සහ 100% සම්පූර්ණ වන විට විශේෂ ඇගයීම් සහ දැනුම්දීම් ලැබේ."
                AppTopic.MULTI_CURRENCY ->
                    "💱 **බහු මුදල් ඒකක ආදායම් කළමනාකරණය (Multi-Currency):**\n\n" +
                    "• ඔබට Freelance, Upwork, Fiverr හෝ AdSense මඟින් USD, EUR, GBP ආදී විදේශ මුදලින් ලැබෙන ආදායම් සටහන් කළ හැක.\n" +
                    "• අදාළ විනිමය අනුපාතිකය (Exchange Rate) ලබා දුන් විට එය ශ්‍රී ලංකා රුපියල් (LKR) බවට පරිවර්තනය වී ප්‍රධාන ශේෂයට එකතු වේ."
                AppTopic.RECURRING_BILLS ->
                    "⏰ **පුනරාවර්තී බිල්පත් සහ දැනුම්දීම් (Recurring Items & Reminders):**\n\n" +
                    "• මාසික විදුලි බිල්පත්, වතුර, Wifi, Netflix හෝ Insurance වැනි නිශ්චිත දිනවල ගෙවිය යුතු බිල්පත් සටහන් කර තැබිය හැක.\n" +
                    "• ගෙවීමට දින 1කට පෙර සහ එදින උදෑසන 9:00ට ඔබට Android Notification සහ Alerts ලැබේ."
                AppTopic.CATEGORY_BUDGETS ->
                    "📊 **කාණ්ඩ අයවැය සහ ඉතිරිවීම් (Category Budgets & Surplus):**\n\n" +
                    "• එක් එක් වියදම් අංශය (උදා: Food, Transport) සඳහා මාසික ඇස්තමේන්තුවක් (Estimation) ලබා දිය හැක.\n" +
                    "• මාසය අවසානයේ ඔබ ඇස්තමේන්තුවට වඩා අඩුවෙන් වියදම් කළහොත් ඉතිරි මුදල (Surplus) Goals සඳහා හෝ සාමාන්‍ය ඉතිරියට යොමු කළ හැක."
                AppTopic.ABOUT_NEXUS ->
                    "🌟 **NEXUS Personal Finance Tracker:**\n\n" +
                    "NEXUS යනු ශ්‍රී ලාංකික පරිශීලකයින් සඳහා විශේෂයෙන් සකස් කළ, ඔබේ ආදායම්, වියදම් සහ ඉතිරිකිරීම් ප්‍රශස්ත කරන මූල්‍ය සහායක පද්ධතියකි.\n" +
                    "• සරලව මූල්‍ය වාර්තා තබා ගැනීම (Track)\n" +
                    "• දත්ත ගැඹුරින් විශ්ලේෂණය කිරීම (Analyze)\n" +
                    "• ධනය වර්ධනය සඳහා ප්‍රශස්ත කිරීම (Optimize)"
            }
        } else {
            when (topic) {
                AppTopic.OFFLINE_SYNC ->
                    "📱 **NEXUS Offline-First & Dual-Sync Architecture:**\n\n" +
                    "NEXUS is engineered for 100% offline resilience:\n" +
                    "• **Room SQLite Local Cache:** You can log expenses, incomes, goals, and categories without any internet connection.\n" +
                    "• **Firestore Cloud Sync:** As soon as network connectivity is restored, all offline mutations sync in the background with zero conflicts.\n" +
                    "• Your financial data is private, secure, and always accessible."
                AppTopic.BUDGET_50_30_20 ->
                    "📐 **The 50/30/20 Golden Budget Rule:**\n\n" +
                    "A proven financial blueprint recommended by wealth advisors:\n" +
                    "• **50% Needs (Essential Committed Expenses):** Rent, utilities, basic groceries, transport, healthcare.\n" +
                    "• **30% Wants (Discretionary Lifestyle):** Dining out, entertainment, shopping, hobbies.\n" +
                    "• **20% Wealth & Savings:** Savings goals, debt payoff, investments.\n" +
                    "NEXUS automatically classifies your spending into Committed vs Discretionary to benchmark your performance against this rule."
                AppTopic.EMERGENCY_RUNWAY ->
                    "🛡️ **Emergency Fund & Runway Explained:**\n\n" +
                    "An emergency runway measures how many months you can sustain your baseline living costs with zero incoming revenue.\n" +
                    "• **Safety Standard:** 3 to 6 months of typical monthly expenditures.\n" +
                    "• **Calculation:** Available liquid savings divided by your historical monthly burn rate.\n" +
                    "Ask me *'What is my emergency runway?'* anytime to check your current buffer."
                AppTopic.BIOMETRIC_SECURITY ->
                    "🔐 **Biometric Lock & Encryption:**\n\n" +
                    "• Secure your financial records with Fingerprint, Face ID, or Device PIN.\n" +
                    "• Enable it from **Settings > Security & Privacy > Biometric Lock**.\n" +
                    "• Whenever the app is minimized or backgrounded, biometric verification protects your sensitive information."
                AppTopic.GOALS_PACE ->
                    "🎯 **Savings Goals & Smart Pace Tracking:**\n\n" +
                    "NEXUS doesn't just store goal targets — it projects feasibility:\n" +
                    "• **Required Monthly Savings:** Recalculates dynamically based on your target date and cumulative contributions.\n" +
                    "• **Pace Status:** Ahead of Pace, On Track, Behind Pace, or Overdue.\n" +
                    "• **Milestones:** Celebratory alerts trigger at 25%, 50%, 75%, and 100% completion."
                AppTopic.MULTI_CURRENCY ->
                    "💱 **Multi-Currency Global Income Support:**\n\n" +
                    "• Track earnings in foreign currencies (USD, EUR, GBP, AUD, CAD, SGD, etc.) for freelance, remote contracts, or crypto.\n" +
                    "• Live or custom exchange rates convert all currencies transparently to your base LKR ledger."
                AppTopic.RECURRING_BILLS ->
                    "⏰ **Automated Bill Reminders (Recurring Payments):**\n\n" +
                    "• Schedule repeating expenses (electricity, rent, internet, gym, subscriptions).\n" +
                    "• NEXUS alerts you 1 day before and at 9:00 AM on the due date so you never miss a payment."
                AppTopic.CATEGORY_BUDGETS ->
                    "📊 **Category Estimations & Surplus Recycling:**\n\n" +
                    "• Set target spending caps on individual categories (e.g. Dining, Groceries).\n" +
                    "• If you spend less than your budget, the surplus can be recycled directly into your high-priority Savings Goals."
                AppTopic.ABOUT_NEXUS ->
                    "🌟 **About NEXUS Finance Tracker:**\n\n" +
                    "NEXUS is an intelligent, privacy-first personal wealth optimization platform.\n" +
                    "• **Track:** Seamless cash flow and multi-account logging.\n" +
                    "• **Analyze:** Real-time 50/30/20 breakdown, burn rates, and financial health scores.\n" +
                    "• **Optimize:** Proactive advice, emergency runway modeling, and goal pace projections."
            }
        }
    }

    fun queryCategorySpending(
        keyword: String,
        expenses: List<Expense>,
        categories: List<Category>,
        isSinhala: Boolean = false
    ): String {
        val lowerKey = keyword.lowercase().trim()
        val catMap = categories.associate { it.id to it.name }
        val matchingExpenses = expenses.filter { exp ->
            val name = catMap[exp.categoryId]?.lowercase() ?: exp.categoryId.lowercase()
            name.contains(lowerKey) || (exp.note?.lowercase()?.contains(lowerKey) == true)
        }

        if (matchingExpenses.isEmpty()) {
            return if (isSinhala) {
                "🔍 **'${keyword}' සඳහා වියදම් සටහන්ව නොමැත.**\n\nඔබට නව වියදමක් එක් කිරීමට අවශ්‍ය නම් *'Spent 1500 for ${keyword}'* ලෙස පවසන්න."
            } else {
                "🔍 **No recorded expenses found for '${keyword}'.**\n\nIf you recently made a purchase, you can tell me *'Spent 1500 for ${keyword}'* to record it right away."
            }
        }

        val totalAmount = matchingExpenses.sumOf { it.amountLKR }
        val count = matchingExpenses.size
        val avg = totalAmount / count
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("🏷️ **'${keyword}' වියදම් විශ්ලේෂණය:**\n\n")
            sb.append("• **මුළු වියදම:** LKR %,.2f\n".format(totalAmount))
            sb.append("• **ගනුදෙනු ගණන:** %d\n".format(count))
            sb.append("• **සාමාන්‍ය වියදම:** LKR %,.2f\n\n".format(avg))
            sb.append("📌 **මෑතකදී කළ වියදම්:**\n")
            matchingExpenses.sortedByDescending { it.date }.take(4).forEach { exp ->
                val cName = catMap[exp.categoryId] ?: "Expense"
                sb.append("• %s: LKR %,.0f (%s)\n".format(dateFormat.format(exp.date), exp.amountLKR, cName))
            }
        } else {
            sb.append("🏷️ **Spending Breakdown for '${keyword}':**\n\n")
            sb.append("• **Total Spent:** LKR %,.2f\n".format(totalAmount))
            sb.append("• **Transactions:** %d entries\n".format(count))
            sb.append("• **Average per entry:** LKR %,.2f\n\n".format(avg))
            sb.append("📌 **Recent Transactions:**\n")
            matchingExpenses.sortedByDescending { it.date }.take(4).forEach { exp ->
                val cName = catMap[exp.categoryId] ?: "Expense"
                sb.append("• %s: LKR %,.0f (%s)\n".format(dateFormat.format(exp.date), exp.amountLKR, cName))
            }
        }
        return sb.toString().trimEnd()
    }

    fun queryRecentTransactions(
        expenses: List<Expense>,
        categories: List<Category>,
        isSinhala: Boolean = false
    ): String {
        if (expenses.isEmpty()) {
            return if (isSinhala) {
                "📝 ඔබ තවමත් කිසිදු වියදමක් සටහන් කර නොමැත. වියදමක් එක් කිරීමට උදා: *'Spent 1500 for lunch cash'* ලෙස සඳහන් කරන්න."
            } else {
                "📝 You haven't recorded any expenses yet. You can log one anytime, e.g. *'Spent 1500 for lunch cash'*."
            }
        }

        val catMap = categories.associate { it.id to it.name }
        val recent = expenses.sortedByDescending { it.date }.take(5)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("🕒 **මෑතකදී කළ වියදම් (Recent Expenses):**\n\n")
            recent.forEachIndexed { i, exp ->
                val catName = catMap[exp.categoryId] ?: "Other"
                val noteStr = if (!exp.note.isNullOrBlank() && exp.note != "Logged via NEXUS AI Copilot") " · \"${exp.note}\"" else ""
                sb.append("${i + 1}. **%s:** LKR %,.2f (%s%s) - %s\n".format(
                    catName, exp.amountLKR, exp.account.uppercase(), noteStr, dateFormat.format(exp.date)
                ))
            }
        } else {
            sb.append("🕒 **Recent Recorded Expenses:**\n\n")
            recent.forEachIndexed { i, exp ->
                val catName = catMap[exp.categoryId] ?: "Other"
                val noteStr = if (!exp.note.isNullOrBlank() && exp.note != "Logged via NEXUS AI Copilot") " · \"${exp.note}\"" else ""
                sb.append("${i + 1}. **%s:** LKR %,.2f (%s%s) - %s\n".format(
                    catName, exp.amountLKR, exp.account.uppercase(), noteStr, dateFormat.format(exp.date)
                ))
            }
        }
        return sb.toString().trimEnd()
    }

    fun queryUpcomingBills(
        recurringItems: List<RecurringItem>,
        isSinhala: Boolean = false
    ): String {
        val active = recurringItems.filter { it.isActive }
        if (active.isEmpty()) {
            return if (isSinhala) {
                "📅 **ඉදිරි බිල්පත් හෝ Recurring Payments සටහන්ව නැත.**\n\nනව බිල්පතක් එක් කිරීමට *'Add recurring bill Netflix 2200'* ලෙස පවසන්න."
            } else {
                "📅 **No active recurring bills found.**\n\nTo schedule a recurring bill reminder, tell me *'Add recurring bill Netflix 2200'*."
            }
        }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val now = Date()

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("⏰ **ඉදිරි පුනරාවර්තී බිල්පත් (Upcoming Recurring Bills):**\n\n")
            active.sortedBy { it.nextDueDate }.forEach { item ->
                val daysLeft = ((item.nextDueDate.time - now.time) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                val statusTag = if (daysLeft <= 3) "🚨 Due in $daysLeft days" else "📅 Due in $daysLeft days"
                sb.append("• **${item.title}:** LKR %,.2f (%s)\n   └ %s (%s)\n\n".format(
                    item.amountLKR, item.frequency.replaceFirstChar { it.uppercase() },
                    statusTag, dateFormat.format(item.nextDueDate)
                ))
            }
        } else {
            sb.append("⏰ **Scheduled Recurring Bills & Subscriptions:**\n\n")
            active.sortedBy { it.nextDueDate }.forEach { item ->
                val daysLeft = ((item.nextDueDate.time - now.time) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                val statusTag = if (daysLeft <= 3) "🚨 Due in $daysLeft days" else "📅 Due in $daysLeft days"
                sb.append("• **${item.title}:** LKR %,.2f (%s)\n   └ %s (%s)\n\n".format(
                    item.amountLKR, item.frequency.replaceFirstChar { it.uppercase() },
                    statusTag, dateFormat.format(item.nextDueDate)
                ))
            }
        }
        return sb.toString().trimEnd()
    }

    fun queryIncomeBreakdown(
        incomes: List<Income>,
        isSinhala: Boolean = false
    ): String {
        if (incomes.isEmpty()) {
            return if (isSinhala) {
                "💰 ඔබ තවමත් කිසිදු ආදායමක් සටහන් කර නොමැත. ආදායමක් එක් කිරීමට *'Salary 150000'* ලෙස පවසන්න."
            } else {
                "💰 No income recorded yet. You can log income anytime, e.g. *'Salary 150000'* or *'Freelance 45000'*."
            }
        }

        val confirmed = incomes.filter { it.status == IncomeStatus.CONFIRMED }
        val pending = incomes.filter { it.status == IncomeStatus.PENDING }
        val totalConfirmed = confirmed.sumOf { it.amountLKR }
        val totalPending = pending.sumOf { it.amountLKR }

        val bySource = confirmed.groupBy { it.source }

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("💰 **සවිස්තරාත්මක ආදායම් විශ්ලේෂණය:**\n\n")
            sb.append("• **තහවුරු වූ මුළු ආදායම:** LKR %,.2f\n".format(totalConfirmed))
            if (totalPending > 0) {
                sb.append("• **ලැබීමට ඇති (Pending):** LKR %,.2f (%d payments)\n".format(totalPending, pending.size))
            }
            sb.append("\n🏷️ **ආදායම් මාර්ග අනුව වර්ගීකරණය:**\n")
            bySource.forEach { (src, list) ->
                val sum = list.sumOf { it.amountLKR }
                val pct = if (totalConfirmed > 0) (sum / totalConfirmed) * 100.0 else 0.0
                sb.append("• **%s:** LKR %,.0f (%.1f%%)\n".format(src.replaceFirstChar { it.uppercase() }, sum, pct))
            }
        } else {
            sb.append("💰 **Income Stream Breakdown:**\n\n")
            sb.append("• **Total Confirmed Income:** LKR %,.2f\n".format(totalConfirmed))
            if (totalPending > 0) {
                sb.append("• **Pending Income:** LKR %,.2f (%d items expected)\n".format(totalPending, pending.size))
            }
            sb.append("\n🏷️ **Earnings by Channel:**\n")
            bySource.forEach { (src, list) ->
                val sum = list.sumOf { it.amountLKR }
                val pct = if (totalConfirmed > 0) (sum / totalConfirmed) * 100.0 else 0.0
                sb.append("• **%s:** LKR %,.0f (%.1f%%)\n".format(src.replaceFirstChar { it.uppercase() }, sum, pct))
            }
        }
        return sb.toString().trimEnd()
    }

    fun queryGoalSpecific(
        goalKeyword: String,
        goals: List<Goal>,
        incomes: List<Income>,
        expenses: List<Expense>,
        isSinhala: Boolean = false
    ): String {
        val active = goals.filter { it.isActive }
        if (active.isEmpty()) {
            return if (isSinhala) {
                "🎯 ඔබ සතුව ක්‍රියාකාරී Savings Goals නොමැත. නව ඉලක්කයක් සෑදීමට *'Save 250000 for laptop'* ලෙස පවසන්න."
            } else {
                "🎯 You have no active Savings Goals. To create one, tell me *'Save 250000 for laptop'*."
            }
        }

        val targetGoal = active.find { it.title.lowercase().contains(goalKeyword.lowercase()) }
            ?: active.first()

        val calc = GoalCalculation.calculate(targetGoal, incomes, expenses, Date())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        val sb = StringBuilder()
        if (isSinhala) {
            sb.append("🎯 **ඉලක්ක ප්‍රගති වාර්තාව: ${targetGoal.title}**\n\n")
            sb.append("• **ඉලක්කගත මුදල:** LKR %,.0f\n".format(targetGoal.targetAmountLKR))
            sb.append("• **වත්මන් ඉතිරිය:** LKR %,.0f (%.1f%% සම්පූර්ණයි)\n".format(calc.estimatedCurrentSavingsLKR, calc.progressPercentage))
            sb.append("• **තව අවශ්‍ය මුදල:** LKR %,.0f\n".format(calc.remainingAmountLKR))
            sb.append("• **ඉලක්කගත දිනය:** %s (%s)\n".format(dateFormat.format(targetGoal.targetDate), calc.daysRemainingText))
            if (calc.requiredMonthlySavingsLKR > 0) {
                sb.append("• **මාසිකව ඉතිරි කළ යුතු මුදල:** LKR %,.0f\n".format(calc.requiredMonthlySavingsLKR))
            }
        } else {
            sb.append("🎯 **Goal Progress Report: ${targetGoal.title}**\n\n")
            sb.append("• **Target Amount:** LKR %,.0f\n".format(targetGoal.targetAmountLKR))
            sb.append("• **Current Savings:** LKR %,.0f (%.1f%% complete)\n".format(calc.estimatedCurrentSavingsLKR, calc.progressPercentage))
            sb.append("• **Remaining Gap:** LKR %,.0f\n".format(calc.remainingAmountLKR))
            sb.append("• **Target Deadline:** %s (%s)\n".format(dateFormat.format(targetGoal.targetDate), calc.daysRemainingText))
            if (calc.requiredMonthlySavingsLKR > 0) {
                sb.append("• **Required Monthly Savings:** LKR %,.0f\n".format(calc.requiredMonthlySavingsLKR))
            }
        }
        return sb.toString().trimEnd()
    }

    fun provideGeneralConsultation(
        query: String,
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>,
        isSinhala: Boolean = false
    ): String {
        val totalIncome = incomes.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        val totalSpent = expenses.sumOf { it.amountLKR }
        val netSavings = totalIncome - totalSpent
        val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100.0).coerceAtLeast(0.0) else 0.0

        val lower = query.lowercase()

        val sb = StringBuilder()
        if (isSinhala) {
            when {
                lower.contains("save") || lower.contains("ඉතිරි") || lower.contains("අඩු කරන්න") -> {
                    sb.append("💡 **ඔබගේ ඉතිරිකිරීම් ඉහළ නැංවීමට මඟපෙන්වීම:**\n\n")
                    sb.append("ඔබේ වත්මන් ඉතිරිකිරීමේ අනුපාතය **%.1f%%** කි.\n\n".format(savingsRate))
                    sb.append("1. **50/30/20 රීතිය අනුගමනය කරන්න:** ඔබේ අත්‍යවශ්‍ය නොවන (Discretionary) වියදම් සීමා කරන්න.\n")
                    sb.append("2. **Micro-Leaks සොයා බලන්න:** සුළු සුළු Dining හෝ Shopping වියදම් සතිපතා පාලනය කිරීමෙන් විශාල මුදලක් ඉතිරි වේ.\n")
                    sb.append("3. **Savings Goals පිහිටුවන්න:** ඉලක්කයක් රහිතව මුදල් ඉතිරි කිරීමට වඩා පැහැදිලි ඉලක්කයක් ඇතිව ඉතිරි කිරීම වේගවත්ය.")
                }
                lower.contains("invest") || lower.contains("ආයෝජන") || lower.contains("crypto") || lower.contains("ක්‍රිප්ටෝ") -> {
                    sb.append("📈 **ආයෝජන හා ධන වර්ධන උපදෙස්:**\n\n")
                    sb.append("1. **පළමුව හදිසි අරමුදල සකසා ගන්න:** ආයෝජනය කිරීමට පෙර අවම වශයෙන් මාස 3-6 ක ජීවන වියදම් සුරක්ෂිතව තබා ගන්න.\n")
                    sb.append("2. **විවිධාංගීකරණය (Diversification):** ඔබේ මුළු ඉතිරිය එකම තැනක (උදා: Crypto පමණක්) නොයොදවා ස්ථාවර තැන්පතු සහ ආරක්ෂිත සුරැකුම්පත්වලද ආයෝජනය කරන්න.\n")
                    sb.append("3. **අතිරික්ත මුදල් පමණක් අවදානම් සහිත ආයෝජනවලට යොදවන්න.**")
                }
                lower.contains("debt") || lower.contains("ණය") || lower.contains("loan") || lower.contains("card") -> {
                    sb.append("💳 **ණය හා ක්‍රෙඩිට් කාඩ් කළමනාකරණය:**\n\n")
                    sb.append("1. **Avalanche ක්‍රමය:** වැඩිම පොලියක් අයවන ණය (උදා: Credit Cards) පළමුව පියවන්න.\n")
                    sb.append("2. **Committed වියදම් සීමා කරන්න:** මාසික ණය වාරික ඔබේ මුළු ආදායමෙන් 30% ඉක්මවා නොයන පරිදි කළමනාකරණය කරගන්න.")
                }
                else -> {
                    sb.append("🤖 **NEXUS AI Financial Intelligence Co-pilot:**\n\n")
                    sb.append("මම ඔබගේ දත්ත විශ්ලේෂණය කරමින් ඔබට පහත ඕනෑම කාර්යයක් සඳහා සහාය වීමට සූදානම්:\n\n")
                    sb.append("• 💸 **වියදම් හෝ ආදායම් එක් කරන්න:** උදා: *'Spent 2500 for groceries cash'*, *'Salary 150000'*\n")
                    sb.append("• 🏷️ **අලුත් Category හෝ Recurring Bill එකක් දාන්න:** උදා: *'Add category Gym 5000'*, *'Add recurring bill Wifi 3000'*\n")
                    sb.append("• 🔍 **විශේෂිත වියදම් විමසන්න:** උදා: *'How much did I spend on food?'*, *'Recent expenses'*\n")
                    sb.append("• 🛡️ **මූල්‍ය සෞඛ්‍යය සහ Runway පරීක්ෂාව:** උදා: *'Emergency runway'*, *'Can I afford 50000?'*\n")
                    sb.append("• ⚙️ **Settings සහ App Navigation:** උදා: *'Enable dark mode'*, *'Go to reports'*\n\n")
                    sb.append("ඔබට අවශ්‍ය ඕනෑම ප්‍රශ්නයක් හෝ විධානයක් මෙහි සටහන් කරන්න!")
                }
            }
        } else {
            when {
                lower.contains("save") || lower.contains("saving") || lower.contains("reduce") || lower.contains("cut") -> {
                    sb.append("💡 **Strategic Wealth Optimization Blueprint:**\n\n")
                    sb.append("Your current active savings retention rate is **%.1f%%** of confirmed revenue.\n\n".format(savingsRate))
                    sb.append("1. **Calibrate 50/30/20 Discipline:** Cap lifestyle discretionary wants to strictly under 30% of income.\n")
                    sb.append("2. **Eliminate Discretionary Leaks:** Review Dining and Shopping categories to capture an additional 10-15% surplus.\n")
                    sb.append("3. **Automate Goal Funding:** Set automated monthly allocations for your key Savings Goals.")
                }
                lower.contains("invest") || lower.contains("crypto") || lower.contains("stock") || lower.contains("wealth") -> {
                    sb.append("📈 **Investment & Capital Growth Guidelines:**\n\n")
                    sb.append("1. **Preserve Liquid Runway First:** Ensure your 3-6 month emergency fund is untouched before committing capital to volatile assets.\n")
                    sb.append("2. **Asset Allocation:** Balance high-growth instruments (crypto, equities) with safe fixed-income yields.\n")
                    sb.append("3. **Dollar Cost Averaging (DCA):** Invest surplus capital systematically each month rather than attempting to time the market.")
                }
                lower.contains("debt") || lower.contains("loan") || lower.contains("card") || lower.contains("interest") -> {
                    sb.append("💳 **Debt Minimization Strategy:**\n\n")
                    sb.append("1. **Debt Avalanche Method:** Direct surplus savings toward the highest interest debt (e.g. revolving credit card balances) first.\n")
                    sb.append("2. **Cap Fixed Commitments:** Keep all fixed debt payments under 30% of net monthly income.")
                }
                else -> {
                    sb.append("🤖 **NEXUS AI Copilot Intelligence:**\n\n")
                    sb.append("I am fully connected to your NEXUS ledger and ready to assist you with any task:\n\n")
                    sb.append("• 💸 **Log Expenses & Incomes:** e.g. *'Fuel 2500 cash'*, *'Salary 150000'*\n")
                    sb.append("• 🏷️ **Add Categories & Recurring Bills:** e.g. *'Add category Gym 5000'*, *'Add recurring bill Netflix 2200'*\n")
                    sb.append("• 🔍 **Inquire about Expenses & Data:** e.g. *'How much did I spend on food?'*, *'Show recent expenses'*\n")
                    sb.append("• 🛡️ **Affordability & Runway Checks:** e.g. *'Can I afford 45000?'*, *'Emergency runway'*\n")
                    sb.append("• ⚙️ **App Settings & Navigation:** e.g. *'Enable dark mode'*, *'Clear alerts'*, *'Open reports'*\n\n")
                    sb.append("Feel free to ask me anything or tell me what to do!")
                }
            }
        }
        return sb.toString().trimEnd()
    }
}
