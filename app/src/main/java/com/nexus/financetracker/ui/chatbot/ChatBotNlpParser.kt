package com.nexus.financetracker.ui.chatbot

import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.ui.navigation.Screen
import java.util.Calendar
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

sealed class ParsedIntent {
    data class AddExpense(val payload: ActionPayload) : ParsedIntent()
    data class AddIncome(val payload: ActionPayload) : ParsedIntent()
    data class AddGoal(val payload: ActionPayload) : ParsedIntent()
    data class Navigate(val destinationRoute: String, val screenName: String) : ParsedIntent()
    data class AffordabilityQuery(val amount: Double) : ParsedIntent()
    object FinancialHealthQuery : ParsedIntent()
    object ExpenseAnalysisQuery : ParsedIntent()
    object EmergencyRunwayQuery : ParsedIntent()
    object GeneralFinancialAdvice : ParsedIntent()
    object PredictionAndAdvice : ParsedIntent()
    object FinancialSummary : ParsedIntent()
    object GoalPaceReport : ParsedIntent()
    object GreetingOrHelp : ParsedIntent()
    data class Unknown(val originalText: String) : ParsedIntent()
}

@Singleton
class ChatBotNlpParser @Inject constructor() {

    fun parse(text: String, categories: List<Category>): ParsedIntent {
        val clean = text.trim()
        val lower = clean.lowercase()

        // 1. Navigation Commands
        val navMatch = matchNavigation(lower)
        if (navMatch != null) return navMatch

        // 2. Greetings or General Help
        if (isGreetingOrHelp(lower)) {
            return ParsedIntent.GreetingOrHelp
        }

        // 3. Purchase Affordability Query (e.g. "Can I afford 50000?", "Can I buy a phone for 85k?")
        val affordAmount = checkAffordabilityQuery(lower)
        if (affordAmount != null) {
            return ParsedIntent.AffordabilityQuery(affordAmount)
        }

        // 4. Financial Health & Score Query
        if (isFinancialHealthQuery(lower)) {
            return ParsedIntent.FinancialHealthQuery
        }

        // 5. Emergency Runway Query
        if (isRunwayQuery(lower)) {
            return ParsedIntent.EmergencyRunwayQuery
        }

        // 6. Expense Breakdown & Leak Analysis
        if (isExpenseAnalysisQuery(lower)) {
            return ParsedIntent.ExpenseAnalysisQuery
        }

        // 7. General Financial Advice / Budgeting rules (e.g. 50/30/20 rule)
        if (isGeneralAdviceQuery(lower)) {
            return ParsedIntent.GeneralFinancialAdvice
        }

        // 8. Predictions and Pace Forecast
        if (isPredictionQuery(lower)) {
            return ParsedIntent.PredictionAndAdvice
        }

        // 9. Goal Progress and Pace Report
        if (isGoalPaceQuery(lower)) {
            return ParsedIntent.GoalPaceReport
        }

        // 10. Financial Summary / Overview
        if (isSummaryQuery(lower)) {
            return ParsedIntent.FinancialSummary
        }

        // 11. Add Goal Intent
        val goalMatch = tryParseGoal(lower, clean)
        if (goalMatch != null) return goalMatch

        // 12. Add Income Intent
        val incomeMatch = tryParseIncome(lower, clean)
        if (incomeMatch != null) return incomeMatch

        // 13. Add Expense Intent
        val expenseMatch = tryParseExpense(lower, clean, categories)
        if (expenseMatch != null) return expenseMatch

        return ParsedIntent.Unknown(clean)
    }

    private fun matchNavigation(lower: String): ParsedIntent? {
        val isNav = lower.contains("go to") || lower.contains("open") || lower.contains("show me") ||
            lower.contains("navigate") || lower.contains("යන්න") || lower.contains("පෙන්නන්න") ||
            lower.startsWith("view ")

        if (!isNav) return null

        return when {
            lower.contains("expense") || lower.contains("වියදම්") ->
                ParsedIntent.Navigate(Screen.Expenses.route, "Expenses")
            lower.contains("income") || lower.contains("ආදායම්") ->
                ParsedIntent.Navigate(Screen.Income.route, "Income")
            lower.contains("goal") || lower.contains("ඉලක්ක") ->
                ParsedIntent.Navigate(Screen.Goals.route, "Goals")
            lower.contains("report") || lower.contains("chart") || lower.contains("වාර්තා") ->
                ParsedIntent.Navigate(Screen.Reports.route, "Reports")
            lower.contains("alert") || lower.contains("notification") || lower.contains("දැනුම්දීම්") ->
                ParsedIntent.Navigate(Screen.Alerts.route, "Alerts")
            lower.contains("setting") || lower.contains("සැකසුම්") ->
                ParsedIntent.Navigate(Screen.Settings.route, "Settings")
            lower.contains("profile") || lower.contains("පැතිකඩ") ->
                ParsedIntent.Navigate(Screen.Profile.route, "Profile")
            else -> null
        }
    }

    private fun isGreetingOrHelp(lower: String): Boolean {
        val greetings = listOf("hi", "hello", "hey", "ayubowan", "හායි", "හෙලෝ", "ආයුබෝවන්", "help", "who are you", "what can you do", "මොනවද කරන්න පුළුවන්")
        return greetings.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
    }

    private fun checkAffordabilityQuery(lower: String): Double? {
        val isAfford = lower.contains("afford") || lower.contains("can i buy") ||
            lower.contains("should i buy") || lower.contains("ගන්න පුළුවන්ද") ||
            lower.contains("වියදම් කරන්න පුළුවන්ද") || lower.contains("මිලදී ගන්න පුළුවන්ද")

        if (!isAfford) return null
        return extractAmount(lower)
    }

    private fun isFinancialHealthQuery(lower: String): Boolean {
        val keywords = listOf(
            "financial health", "how am i doing", "rate my", "am i saving enough",
            "how are my finances", "health score", "wealth status",
            "මගේ තත්වය", "තත්වය හොඳද", "මම හොඳට ඉතුරු කරනවද", "මූල්‍ය තත්වය"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isRunwayQuery(lower: String): Boolean {
        val keywords = listOf(
            "runway", "survive", "emergency fund", "how long can i live",
            "හදිසි අරමුදල", "කොච්චර කල් ඉන්න පුළුවන්ද", "ආරක්ෂාව"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isExpenseAnalysisQuery(lower: String): Boolean {
        val keywords = listOf(
            "where is my money", "what did i spend the most", "highest expense",
            "analyze my expense", "analyze expense", "spending breakdown",
            "වියදම් වැඩි මොනවාටද", "සල්ලි යන්නේ කොහෙටද", "වියදම් විස්තර"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isGeneralAdviceQuery(lower: String): Boolean {
        val keywords = listOf(
            "50/30/20", "50 30 20", "how to save", "savings tips", "budget rule",
            "මූල්‍ය උපදෙස්", "ඉතිරි කරන්නේ කොහොමද", "අයවැය රීතිය"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isPredictionQuery(lower: String): Boolean {
        val keywords = listOf(
            "predict", "prediction", "forecast", "pace", "how to save", "advice", "advise",
            "අනාවැකි", "ඉස්සරහට", "කොහොම වෙයිද", "උපදෙස්", "වියදම් වැඩි"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isSummaryQuery(lower: String): Boolean {
        val keywords = listOf(
            "summary", "balance", "total spent", "total income", "status", "overview",
            "සාරාංශය", "කීයද", "ඉතුරු කීයද", "වියදම කීයද", "ආදායම කීයද"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isGoalPaceQuery(lower: String): Boolean {
        val keywords = listOf(
            "my goals", "goal status", "goal pace", "goals pace", "goals report",
            "ඉලක්ක කොහොමද", "ඉලක්ක ප්‍රගතිය", "goal progress"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun tryParseGoal(lower: String, original: String): ParsedIntent? {
        val isGoal = (lower.contains("save") && (lower.contains("for") || lower.contains("goal") || lower.contains("target"))) ||
            lower.contains("goal") || lower.contains("target") ||
            lower.contains("ඉලක්කය") || lower.contains("ඉතිරි කරන්න") || lower.contains("ඉතිරි")

        if (!isGoal) return null

        val amount = extractAmount(lower) ?: return null

        // Extract clean goal title
        var title = original
            .replace("(?i)\\b(?:add|create|goal|save|for|target|lkr|rs|රු|රුපියල්|ඉතිරි|කරන්න|ඉලක්කය)\\b".toRegex(), "")
            .replace("[0-9,.]".toRegex(), "")
            .trim()

        if (title.isBlank()) title = "Savings Target"

        val targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, 6) }

        val payload = ActionPayload(
            amount = amount,
            title = title.replaceFirstChar { it.uppercase() },
            targetDate = targetCal.time
        )
        return ParsedIntent.AddGoal(payload)
    }

    private fun tryParseIncome(lower: String, original: String): ParsedIntent? {
        val incomeKeywords = listOf(
            "income", "salary", "earned", "received", "got", "freelance", "crypto", "profit",
            "ආදායම", "පඩිය", "හම්බුනා", "ලැබුණා", "ආවා", "ඉන්කම්"
        )

        val hasIncomeKeyword = incomeKeywords.any { lower.contains(it) }
        if (!hasIncomeKeyword) return null

        val amount = extractAmount(lower) ?: return null

        val source = when {
            lower.contains("salary") || lower.contains("පඩිය") -> IncomeSource.SALARY
            lower.contains("freelance") || lower.contains("fiverr") || lower.contains("upwork") || lower.contains("ෆ්‍රීලාන්ස්") -> IncomeSource.FREELANCE
            lower.contains("crypto") || lower.contains("binance") || lower.contains("ක්‍රිප්ටෝ") -> IncomeSource.CRYPTO
            lower.contains("adsense") || lower.contains("youtube") -> IncomeSource.ADSENSE
            else -> IncomeSource.OTHER
        }

        val note = original.take(100)

        val payload = ActionPayload(
            amount = amount,
            source = source,
            title = IncomeSource.getDisplayName(source),
            note = note,
            account = ExpenseAccount.CARD
        )
        return ParsedIntent.AddIncome(payload)
    }

    private fun tryParseExpense(lower: String, original: String, categories: List<Category>): ParsedIntent? {
        val amount = extractAmount(lower) ?: return null

        // Identify account
        val account = when {
            lower.contains("cash") || lower.contains("අතින්") || lower.contains("සල්ලි") -> ExpenseAccount.CASH
            lower.contains("card") || lower.contains("කාඩ්") || lower.contains("visa") || lower.contains("master") -> ExpenseAccount.CARD
            lower.contains("secondary") || lower.contains("bank") || lower.contains("transfer") || lower.contains("බැංකු") -> ExpenseAccount.SECONDARY
            else -> ExpenseAccount.CASH
        }

        // Match Category
        val (catId, catName, classification) = matchCategory(lower, categories)

        val payload = ActionPayload(
            amount = amount,
            categoryId = catId,
            categoryName = catName,
            account = account,
            classification = classification,
            note = original.take(100)
        )
        return ParsedIntent.AddExpense(payload)
    }

    private fun matchCategory(lower: String, categories: List<Category>): Triple<String, String, String> {
        // First check custom user categories by name
        for (cat in categories) {
            val cName = cat.name.lowercase()
            if (cName.isNotBlank() && lower.contains(cName)) {
                return Triple(cat.id, cat.name, cat.classification)
            }
        }

        // Default semantic matcher
        return when {
            // Food & Dining
            lower.contains("food") || lower.contains("dining") || lower.contains("lunch") ||
                lower.contains("dinner") || lower.contains("breakfast") || lower.contains("tea") ||
                lower.contains("coffee") || lower.contains("restaurant") || lower.contains("burger") ||
                lower.contains("pizza") || lower.contains("rice") || lower.contains("කෑම") ||
                lower.contains("බත්") || lower.contains("තේ") || lower.contains("රෙස්ටුරන්ට්") ->
                Triple("cat_food", "Food & Dining", ExpenseClassification.DISCRETIONARY)

            // Transport & Fuel
            lower.contains("petrol") || lower.contains("fuel") || lower.contains("diesel") ||
                lower.contains("uber") || lower.contains("pickme") || lower.contains("taxi") ||
                lower.contains("bus") || lower.contains("train") || lower.contains("transport") ||
                lower.contains("පෙට්‍රල්") || lower.contains("ඩීසල්") || lower.contains("බස්") ||
                lower.contains("ටැක්සි") || lower.contains("ට්‍රැන්ස්පෝට්") ->
                Triple("cat_transport", "Transport", ExpenseClassification.DISCRETIONARY)

            // Groceries & Market
            lower.contains("groceries") || lower.contains("grocery") || lower.contains("supermarket") ||
                lower.contains("keells") || lower.contains("cargills") || lower.contains("market") ||
                lower.contains("vegetable") || lower.contains("fruit") || lower.contains("කඩෙන්") ||
                lower.contains("බඩු") || lower.contains("එළවළු") ->
                Triple("cat_groceries", "Groceries", ExpenseClassification.DISCRETIONARY)

            // Bills & Utilities
            lower.contains("electricity") || lower.contains("current") || lower.contains("water") ||
                lower.contains("wifi") || lower.contains("internet") || lower.contains("dialog") ||
                lower.contains("mobitel") || lower.contains("slt") || lower.contains("utility") ||
                lower.contains("bill") || lower.contains("විදුලි") || lower.contains("වතුර") ||
                lower.contains("බිල") ->
                Triple("cat_utilities", "Bills & Utilities", ExpenseClassification.COMMITTED)

            // Rent & Accommodation
            lower.contains("rent") || lower.contains("boarding") || lower.contains("room") ||
                lower.contains("house") || lower.contains("කුලිය") || lower.contains("බෝඩිම") ->
                Triple("cat_rent", "Rent", ExpenseClassification.COMMITTED)

            // Health & Pharmacy
            lower.contains("health") || lower.contains("medicine") || lower.contains("pharmacy") ||
                lower.contains("doctor") || lower.contains("hospital") || lower.contains("clinic") ||
                lower.contains("බෙහෙත්") || lower.contains("ෆාමසි") || lower.contains("දොස්තර") ->
                Triple("cat_health", "Health", ExpenseClassification.COMMITTED)

            // Shopping & Clothing
            lower.contains("shopping") || lower.contains("cloth") || lower.contains("clothes") ||
                lower.contains("shoes") || lower.contains("shirt") || lower.contains("dress") ||
                lower.contains("ඇඳුම්") || lower.contains("ෂොපින්") ->
                Triple("cat_shopping", "Shopping", ExpenseClassification.DISCRETIONARY)

            // Subscriptions & Entertainment
            lower.contains("netflix") || lower.contains("spotify") || lower.contains("youtube") ||
                lower.contains("movie") || lower.contains("cinema") || lower.contains("game") ||
                lower.contains("subscription") || lower.contains("ෆිල්ම්") ->
                Triple("cat_entertainment", "Subscriptions", ExpenseClassification.COMMITTED)

            else ->
                Triple("cat_other", "Other", ExpenseClassification.DISCRETIONARY)
        }
    }

    private fun extractAmount(text: String): Double? {
        val regexK = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*[kK]\\b")
        val matcherK = regexK.matcher(text)
        if (matcherK.find()) {
            val num = matcherK.group(1)?.toDoubleOrNull()
            if (num != null) return num * 1000.0
        }

        val regexNumber = Pattern.compile("(?:lkr|rs|රු|රුපියල්)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE)
        val matcher = regexNumber.matcher(text)

        var foundAmount: Double? = null
        while (matcher.find()) {
            val raw = matcher.group(1)?.replace(",", "")
            val parsed = raw?.toDoubleOrNull()
            if (parsed != null && parsed > 0) {
                if (parsed in 2020.0..2030.0 && (text.contains("year") || text.contains("2026"))) {
                    foundAmount = parsed
                } else {
                    return parsed
                }
            }
        }

        return foundAmount
    }
}
