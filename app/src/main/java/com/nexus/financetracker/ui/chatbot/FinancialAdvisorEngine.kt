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
                sb.append("${i + 1}. **$name:** LKR %,.0f (%.1f%%)\n".format(sum, pct))
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
                sb.append("${i + 1}. **$name:** LKR %,.0f (%.1f%%)\n".format(sum, pct))
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
                    sb.append("${i + 1}. **$name**: LKR %,.0f (%.0f%%)\n".format(amount, percent))
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
                    sb.append("${i + 1}. **$name**: LKR %,.0f (%.0f%%)\n".format(amount, percent))
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
}
