package com.nexus.financetracker.ui.chatbot

import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.RecurringFrequency
import com.nexus.financetracker.ui.navigation.Screen
import java.util.Calendar
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTopic {
    OFFLINE_SYNC,
    BUDGET_50_30_20,
    EMERGENCY_RUNWAY,
    BIOMETRIC_SECURITY,
    GOALS_PACE,
    MULTI_CURRENCY,
    RECURRING_BILLS,
    CATEGORY_BUDGETS,
    ABOUT_NEXUS
}

sealed class ParsedIntent {
    data class AddExpense(val payload: ActionPayload) : ParsedIntent()
    data class AddIncome(val payload: ActionPayload) : ParsedIntent()
    data class AddGoal(val payload: ActionPayload) : ParsedIntent()
    data class AddCategory(val payload: ActionPayload) : ParsedIntent()
    data class AddRecurringBill(val payload: ActionPayload) : ParsedIntent()
    object ClearAlerts : ParsedIntent()
    data class ToggleSetting(val settingKey: String, val enable: Boolean, val label: String) : ParsedIntent()
    data class Navigate(val destinationRoute: String, val screenName: String) : ParsedIntent()

    // Smart data-driven inquiries
    data class CategorySpendingQuery(val categoryKeyword: String) : ParsedIntent()
    object RecentTransactionsQuery : ParsedIntent()
    object UpcomingBillsQuery : ParsedIntent()
    object IncomeBreakdownQuery : ParsedIntent()
    data class GoalSpecificQuery(val goalKeyword: String) : ParsedIntent()

    // Advisory & analytics
    data class AffordabilityQuery(val amount: Double) : ParsedIntent()
    object FinancialHealthQuery : ParsedIntent()
    object ExpenseAnalysisQuery : ParsedIntent()
    object EmergencyRunwayQuery : ParsedIntent()
    object GeneralFinancialAdvice : ParsedIntent()
    object PredictionAndAdvice : ParsedIntent()
    object FinancialSummary : ParsedIntent()
    object GoalPaceReport : ParsedIntent()

    // Explanations & help
    data class AppExplanationQuery(val topic: AppTopic) : ParsedIntent()
    object GreetingOrHelp : ParsedIntent()

    // N8N workflow-style intelligent consultative fallback
    data class GeneralConsultation(val query: String) : ParsedIntent()
    data class Unknown(val originalText: String) : ParsedIntent()
}

@Singleton
class ChatBotNlpParser @Inject constructor() {

    fun parse(text: String, categories: List<Category>): ParsedIntent {
        val clean = text.trim()
        if (clean.isBlank()) return ParsedIntent.GreetingOrHelp
        val lower = clean.lowercase()

        // 1. Navigation Commands
        val navMatch = matchNavigation(lower)
        if (navMatch != null) return navMatch

        // 2. Clear Alerts / Notifications
        if (isClearAlertsCommand(lower)) {
            return ParsedIntent.ClearAlerts
        }

        // 3. App Settings Commands (Theme, Notifications, Biometrics)
        val settingMatch = matchSettingsAction(lower)
        if (settingMatch != null) return settingMatch

        // 4. Greetings or General Help
        if (isGreetingOrHelp(lower)) {
            return ParsedIntent.GreetingOrHelp
        }

        // 5. Add Recurring Bill Intent (e.g. "Add recurring bill Netflix 2200", "Monthly bill Wifi 3000")
        val recurringMatch = tryParseRecurringBill(lower, clean, categories)
        if (recurringMatch != null) return recurringMatch

        // 6. Add Category Intent (e.g. "Add category Gym 5000", "New category Tuition")
        val categoryMatch = tryParseAddCategory(lower, clean)
        if (categoryMatch != null) return categoryMatch

        // 7. App Knowledge & Feature Inquiries
        val topicMatch = matchAppExplanation(lower)
        if (topicMatch != null) {
            return ParsedIntent.AppExplanationQuery(topicMatch)
        }

        // 8. Upcoming Bills & Recurring Inquiries
        if (isUpcomingBillsQuery(lower)) {
            return ParsedIntent.UpcomingBillsQuery
        }

        // 9. Recent Transactions Inquiry
        if (isRecentTransactionsQuery(lower)) {
            return ParsedIntent.RecentTransactionsQuery
        }

        // 10. Income Stream Breakdown Inquiry
        if (isIncomeBreakdownQuery(lower)) {
            return ParsedIntent.IncomeBreakdownQuery
        }

        // 11. Specific Category Spending Inquiry (e.g. "How much did I spend on food?", "Show fuel expenses")
        val catSpendingMatch = matchCategorySpendingQuery(lower, categories)
        if (catSpendingMatch != null) return catSpendingMatch

        // 12. Specific Goal Inquiry (e.g. "Progress on laptop goal", "My car goal")
        val goalQueryMatch = matchGoalQuery(lower)
        if (goalQueryMatch != null) return goalQueryMatch

        // 13. Purchase Affordability Query (e.g. "Can I afford 50000?", "Can I buy a phone for 85k?")
        val affordAmount = checkAffordabilityQuery(lower)
        if (affordAmount != null) {
            return ParsedIntent.AffordabilityQuery(affordAmount)
        }

        // 14. Financial Health & Score Query
        if (isFinancialHealthQuery(lower)) {
            return ParsedIntent.FinancialHealthQuery
        }

        // 15. Emergency Runway Query
        if (isRunwayQuery(lower)) {
            return ParsedIntent.EmergencyRunwayQuery
        }

        // 16. Expense Breakdown & Leak Analysis
        if (isExpenseAnalysisQuery(lower)) {
            return ParsedIntent.ExpenseAnalysisQuery
        }

        // 17. General Financial Advice / Budgeting rules (e.g. 50/30/20 rule)
        if (isGeneralAdviceQuery(lower)) {
            return ParsedIntent.GeneralFinancialAdvice
        }

        // 18. Predictions and Pace Forecast
        if (isPredictionQuery(lower)) {
            return ParsedIntent.PredictionAndAdvice
        }

        // 19. Goal Progress and Pace Report
        if (isGoalPaceQuery(lower)) {
            return ParsedIntent.GoalPaceReport
        }

        // 20. Financial Summary / Overview
        if (isSummaryQuery(lower)) {
            return ParsedIntent.FinancialSummary
        }

        // 21. Add Goal Intent
        val goalMatch = tryParseGoal(lower, clean)
        if (goalMatch != null) return goalMatch

        // 22. Add Income Intent
        val incomeMatch = tryParseIncome(lower, clean)
        if (incomeMatch != null) return incomeMatch

        // 23. Add Expense Intent
        val expenseMatch = tryParseExpense(lower, clean, categories)
        if (expenseMatch != null) return expenseMatch

        // 24. Intelligent N8N workflow-style consultative fallback (never rigid failure!)
        return ParsedIntent.GeneralConsultation(clean)
    }

    private fun matchNavigation(lower: String): ParsedIntent? {
        val isNav = lower.contains("go to") || lower.contains("open") || lower.contains("show me") ||
            lower.contains("navigate") || lower.contains("යන්න") || lower.contains("පෙන්නන්න") ||
            lower.startsWith("view ") || lower.contains("take me to")

        if (!isNav) return null

        return when {
            lower.contains("dashboard") || lower.contains("home") || lower.contains("මුල් පිටුව") ->
                ParsedIntent.Navigate(Screen.Dashboard.route, "Dashboard")
            lower.contains("expense") || lower.contains("වියදම්") ->
                ParsedIntent.Navigate(Screen.Expenses.route, "Expenses")
            lower.contains("income") || lower.contains("ආදායම්") ->
                ParsedIntent.Navigate(Screen.Income.route, "Income")
            lower.contains("goal") || lower.contains("ඉලක්ක") ->
                ParsedIntent.Navigate(Screen.Goals.route, "Goals")
            lower.contains("report") || lower.contains("chart") || lower.contains("analytics") || lower.contains("වාර්තා") ->
                ParsedIntent.Navigate(Screen.Reports.route, "Reports")
            lower.contains("alert") || lower.contains("notification") || lower.contains("දැනුම්දීම්") ->
                ParsedIntent.Navigate(Screen.Alerts.route, "Alerts")
            lower.contains("setting") || lower.contains("සැකසුම්") ->
                ParsedIntent.Navigate(Screen.Settings.route, "Settings")
            lower.contains("profile") || lower.contains("පැතිකඩ") ->
                ParsedIntent.Navigate(Screen.Profile.route, "Profile")
            lower.contains("categor") || lower.contains("කාණ්ඩ") ->
                ParsedIntent.Navigate(Screen.CategoryDashboard.route, "Categories")
            else -> null
        }
        return null
    }

    private fun isClearAlertsCommand(lower: String): Boolean {
        return ((lower.contains("clear") || lower.contains("dismiss") || lower.contains("delete")) &&
            (lower.contains("alert") || lower.contains("notification"))) ||
            lower.contains("dismiss all") || lower.contains("mark all read") ||
            lower.contains("mark notifications read") || lower.contains("දැනුම්දීම් අයින්") ||
            lower.contains("alerts clear")
    }

    private fun matchSettingsAction(lower: String): ParsedIntent? {
        return when {
            lower.contains("dark mode") || lower.contains("dark theme") || lower.contains("night mode") || lower.contains("කළු තේමාව") ->
                ParsedIntent.ToggleSetting("theme_dark", true, "Dark Theme")
            lower.contains("light mode") || lower.contains("light theme") || lower.contains("එළිය තේමාව") ->
                ParsedIntent.ToggleSetting("theme_light", false, "Light Theme")
            (lower.contains("turn on") || lower.contains("enable")) && (lower.contains("notification") || lower.contains("දැනුම්දීම්")) ->
                ParsedIntent.ToggleSetting("notifications", true, "Notifications")
            (lower.contains("turn off") || lower.contains("disable")) && (lower.contains("notification") || lower.contains("දැනුම්දීම්")) ->
                ParsedIntent.ToggleSetting("notifications", false, "Notifications")
            (lower.contains("turn on") || lower.contains("enable")) && (lower.contains("biometric") || lower.contains("fingerprint") || lower.contains("face id")) ->
                ParsedIntent.ToggleSetting("biometrics", true, "Biometric Lock")
            (lower.contains("turn off") || lower.contains("disable")) && (lower.contains("biometric") || lower.contains("fingerprint")) ->
                ParsedIntent.ToggleSetting("biometrics", false, "Biometric Lock")
            else -> null
        }
        return null
    }

    private fun matchAppExplanation(lower: String): AppTopic? {
        val isAdd = lower.contains("add") || lower.contains("create") || lower.contains("new") || lower.contains("හදන්න")
        if (isAdd) return null

        return when {
            lower.contains("offline") || lower.contains("sync") || lower.contains("room db") || lower.contains("without internet") || lower.contains("නෙට් නැතිව") ->
                AppTopic.OFFLINE_SYNC
            lower.contains("what is 50") || lower.contains("explain 50/30/20") || lower.contains("50 30 20 rule") || lower.contains("50 30 20 නීතිය") ->
                AppTopic.BUDGET_50_30_20
            lower.contains("what is runway") || lower.contains("what is emergency") || lower.contains("explain runway") || lower.contains("හදිසි අරමුදල යනු") ->
                AppTopic.EMERGENCY_RUNWAY
            lower.contains("biometric") || lower.contains("fingerprint") || lower.contains("face unlock") || lower.contains("app lock") || lower.contains("ආරක්ෂාව") ->
                AppTopic.BIOMETRIC_SECURITY
            lower.contains("how goals work") || lower.contains("pace status") || lower.contains("milestone") || lower.contains("ඉලක්ක ගණනය") ->
                AppTopic.GOALS_PACE
            lower.contains("currency") || lower.contains("foreign") || lower.contains("usd") || lower.contains("eur") || lower.contains("විනිමය") ->
                AppTopic.MULTI_CURRENCY
            lower.contains("recurring") || lower.contains("bill reminder") || lower.contains("පුනරාවර්තී") ->
                AppTopic.RECURRING_BILLS
            lower.contains("budget limit") || lower.contains("estimation") || lower.contains("surplus") || lower.contains("ඇස්තමේන්තුව") ->
                AppTopic.CATEGORY_BUDGETS
            lower.contains("what is nexus") || lower.contains("about nexus") || lower.contains("who made you") || lower.contains("ඇප් එක ගැන") ->
                AppTopic.ABOUT_NEXUS
            else -> null
        }
        return null
    }

    private fun isUpcomingBillsQuery(lower: String): Boolean {
        val keywords = listOf(
            "upcoming bills", "due bills", "recurring bills", "what bills are due",
            "next payments", "subscriptions", "ඉදිරි බිල්පත්", "ගෙවන්න තියෙන බිල්", "බිල්පත් මොනවද"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isRecentTransactionsQuery(lower: String): Boolean {
        val keywords = listOf(
            "recent expense", "recent spending", "recent transactions", "last expense",
            "last spending", "latest expense", "මෑත වියදම්", "අන්තිම වියදම්", "මෑතකදී කළ වියදම්",
            "ඊයේ", "yesterday", "කලින් දවසේ", "කලින් දවසෙ", "spending breakdown"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isIncomeBreakdownQuery(lower: String): Boolean {
        val keywords = listOf(
            "income breakdown", "income sources", "where did my income come from",
            "how did i earn", "salary and freelance", "ආදායම් විස්තර", "ආදායම් මාර්ග",
            "income ekak", "income එකක්", "කොච්චර income", "total income", "my income",
            "ආදායමක් ඇවිත්", "ආදායම කොච්චර"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun tryParseRecurringBill(lower: String, original: String, categories: List<Category>): ParsedIntent? {
        val isRecurring = lower.contains("recurring bill") || lower.contains("monthly bill") ||
            lower.contains("recurring payment") || lower.contains("add recurring") ||
            lower.contains("මාසික බිල") || lower.contains("පුනරාවර්තී බිල")

        if (!isRecurring) return null

        val amount = extractAmount(lower) ?: return null
        val (catId, catName, classification) = matchCategory(lower, categories)

        var title = original
            .replace("(?i)\\b(?:add|create|recurring|monthly|bill|payment|for|lkr|rs|රු|මාසික|බිලක්|දාන්න)\\b".toRegex(), "")
            .replace("[0-9,.]".toRegex(), "")
            .trim()

        if (title.isBlank()) title = "$catName Bill"

        val targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }

        val payload = ActionPayload(
            amount = amount,
            title = title.replaceFirstChar { it.uppercase() },
            categoryId = catId,
            categoryName = catName,
            classification = ExpenseClassification.COMMITTED,
            frequency = RecurringFrequency.MONTHLY,
            targetDate = targetCal.time
        )
        return ParsedIntent.AddRecurringBill(payload)
    }

    private fun tryParseAddCategory(lower: String, original: String): ParsedIntent? {
        val isCategory = lower.contains("add category") || lower.contains("create category") ||
            lower.contains("new category") || lower.contains("කැටගරි එකක් හදන්න") ||
            lower.contains("අලුත් category")

        if (!isCategory) return null

        val budget = extractAmount(lower)

        var name = original
            .replace("(?i)\\b(?:add|create|new|category|budget|limit|estimation|lkr|rs|කැටගරි|එකක්|හදන්න|අලුත්)\\b".toRegex(), "")
            .replace("[0-9,.]".toRegex(), "")
            .trim()

        if (name.isBlank()) name = "Custom Category"

        val payload = ActionPayload(
            title = name.replaceFirstChar { it.uppercase() },
            classification = ExpenseClassification.DISCRETIONARY,
            estimatedAmount = budget
        )
        return ParsedIntent.AddCategory(payload)
    }

    private fun matchCategorySpendingQuery(lower: String, categories: List<Category>): ParsedIntent? {
        val isSpendingQuery = lower.contains("how much did i spend") || lower.contains("spent on") ||
            lower.contains("spending on") || lower.contains("expense on") ||
            lower.contains("expenses for") || lower.contains("වියදම කීයද") ||
            lower.contains("කොච්චර ගියාද") || lower.contains("වියදම් වුනේ කොහොමද")

        if (!isSpendingQuery) return null

        for (cat in categories) {
            val cName = cat.name.lowercase()
            if (cName.isNotBlank() && lower.contains(cName)) {
                return ParsedIntent.CategorySpendingQuery(cat.name)
            }
        }

        val common = listOf("food", "transport", "fuel", "groceries", "bills", "rent", "shopping", "health", "dining")
        for (w in common) {
            if (lower.contains(w)) {
                return ParsedIntent.CategorySpendingQuery(w.replaceFirstChar { it.uppercase() })
            }
        }

        return null
    }

    private fun matchGoalQuery(lower: String): ParsedIntent? {
        if (!lower.contains("goal") && !lower.contains("ඉලක්ක")) return null
        if (lower.contains("add") || lower.contains("create") || lower.contains("save") || lower.contains("pace")) return null

        val clean = lower.replace("goal", "").replace("progress", "").replace("status", "").replace("on", "").replace("my", "").trim()
        return if (clean.isNotBlank()) ParsedIntent.GoalSpecificQuery(clean) else null
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
            "predict", "prediction", "forecast", "pace", "advice", "advise",
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
        val isQuestion = lower.startsWith("how ") || lower.startsWith("why ") ||
            lower.startsWith("what ") || lower.contains("invest") || lower.contains("should i")
        if (isQuestion) return null

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

        while (matcher.find()) {
            val fullMatch = matcher.group(0) ?: ""
            val raw = matcher.group(1)?.replace(",", "")
            val parsed = raw?.toDoubleOrNull()
            if (parsed != null && parsed > 0) {
                val hasCurrency = fullMatch.contains("lkr", ignoreCase = true) ||
                    fullMatch.contains("rs", ignoreCase = true) ||
                    fullMatch.contains("රු")
                if (parsed in 2020.0..2035.0 && !hasCurrency) {
                    continue
                }
                return parsed
            }
        }
        return null
    }
}
