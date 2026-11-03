package com.nexus.financetracker.ui.chatbot

import android.util.Log
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.domain.model.RecurringItem
import com.nexus.financetracker.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiAiResult(
    val replyText: String,
    val actionCard: ChatActionCard? = null
)

@Singleton
class GeminiAiService @Inject constructor() {

    companion object {
        private const val TAG = "GeminiAiService"
        private const val DEFAULT_API_KEY = "AIzaSyAnLrjOzxTjBuYyDpDCxm1Y8QCkZwLmmqg"
        private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun queryGemini(
        userMessage: String,
        userFirstName: String,
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>,
        recurringItems: List<RecurringItem>,
        configuredApiKey: String?,
        isSinhala: Boolean
    ): GeminiAiResult? = withContext(Dispatchers.IO) {
        val apiKey = configuredApiKey?.takeIf { it.isNotBlank() } ?: DEFAULT_API_KEY
        if (apiKey.isBlank()) return@withContext null

        try {
            val systemContext = buildFinancialContext(
                userFirstName = userFirstName,
                incomes = incomes,
                expenses = expenses,
                goals = goals,
                categories = categories,
                recurringItems = recurringItems,
                isSinhala = isSinhala
            )

            val fullPrompt = "$systemContext\n\nUser Message: \"$userMessage\""

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val partObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", fullPrompt) })
                        }
                        put("parts", parts)
                    }
                    put(partObj)
                }
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.7)
                    put("topK", 40)
                    put("topP", 0.95)
                    put("maxOutputTokens", 1024)
                }
                put("generationConfig", generationConfig)
            }

            val request = Request.Builder()
                .url("$GEMINI_ENDPOINT?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini call unsuccessful: ${response.code} ${response.message}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates") ?: return@withContext null
            if (candidates.length() == 0) return@withContext null

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return@withContext null
            val parts = content.optJSONArray("parts") ?: return@withContext null
            if (parts.length() == 0) return@withContext null

            val rawReply = parts.getJSONObject(0).optString("text", "").trim()
            if (rawReply.isBlank()) return@withContext null

            parseGeminiOutput(rawReply, isSinhala, categories)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API execution failed, falling back to local engine", e)
            null
        }
    }

    private fun buildFinancialContext(
        userFirstName: String,
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>,
        recurringItems: List<RecurringItem>,
        isSinhala: Boolean
    ): String {
        val totalIncome = incomes.filter { it.status == IncomeStatus.CONFIRMED }.sumOf { it.amountLKR }
        val totalExpenses = expenses.sumOf { it.amountLKR }
        val netBalance = totalIncome - totalExpenses
        val savingsRate = if (totalIncome > 0) ((netBalance / totalIncome) * 100.0).coerceAtLeast(0.0) else 0.0
        val runwayMonths = if (totalExpenses > 0) (netBalance / totalExpenses).coerceAtLeast(0.0) else 0.0

        val goalsSummary = goals.filter { it.isActive }.take(3).joinToString("; ") {
            "${it.title} (Target: LKR ${it.targetAmountLKR.toInt()})"
        }.ifBlank { "None" }

        val categoriesSummary = categories.take(6).joinToString(", ") { it.name }
        val billsSummary = recurringItems.filter { it.isActive }.take(3).joinToString("; ") {
            "${it.title} (LKR ${it.amountLKR.toInt()})"
        }.ifBlank { "None" }

        return """
You are NEXUS AI, an elite, empathetic, and exceptionally intelligent personal financial copilot integrated inside the NEXUS Finance Tracker app.
You are having a conversation with $userFirstName.

Live Financial Ledger Context for $userFirstName:
- Total Confirmed Income: LKR %,.2f
- Total Recorded Expenses: LKR %,.2f
- Net Available Balance: LKR %,.2f
- Active Savings Retention Rate: %.1f%%
- Emergency Living Runway: %.1f months
- Active Savings Goals: $goalsSummary
- Expense Categories: $categoriesSummary
- Scheduled Recurring Bills: $billsSummary

Rules:
1. Act like a true, top-tier personal AI financial consultant. Do NOT use canned or repetitive templates. Analyze whatever the user specifically asked with depth, clarity, and personalized calculations.
2. Language: If the user communicates in English, reply in natural, engaging, professional English. If the user writes in Sinhala or Singlish, reply naturally and fluently in Sinhala!
3. Action Recognition: If the user asks or intends to perform an app task, you MUST append an action block at the very end of your response formatted exactly as:
```nexus_action
{"action":"ADD_EXPENSE","amount":2500,"category":"Groceries","account":"cash","note":"Dinner with friends"}
```
Supported actions:
- ADD_EXPENSE: "amount" (number), "category" (string), "account" ("cash"|"card"|"secondary"), "note" (string)
- ADD_INCOME: "amount" (number), "source" ("salary"|"freelance"|"crypto"|"adsense"|"other"), "note" (string)
- ADD_GOAL: "amount" (number), "title" (string)
- ADD_CATEGORY: "title" (string), "budget" (number optional)
- ADD_RECURRING_BILL: "title" (string), "amount" (number), "frequency" ("monthly"|"weekly"|"yearly")
- CLEAR_ALERTS: {}
- NAVIGATE: "route" ("dashboard"|"expenses"|"income"|"goals"|"reports"|"alerts"|"settings"|"profile"|"categories")

If the user is just chatting, asking advice, checking health, asking about runway, or asking any general question, do NOT append a nexus_action block.
""".trimIndent().format(totalIncome, totalExpenses, netBalance, savingsRate, runwayMonths)
    }

    private fun parseGeminiOutput(
        rawReply: String,
        isSinhala: Boolean,
        categories: List<Category>
    ): GeminiAiResult {
        val actionRegex = "```nexus_action\\s*([\\s\\S]*?)\\s*```".toRegex()
        val match = actionRegex.find(rawReply)

        if (match == null) {
            return GeminiAiResult(replyText = rawReply.trim(), actionCard = null)
        }

        val actionJsonStr = match.groupValues[1].trim()
        val cleanReply = rawReply.replace(actionRegex, "").trim()

        var card: ChatActionCard? = null
        try {
            val json = JSONObject(actionJsonStr)
            val action = json.optString("action", "")

            when (action) {
                "ADD_EXPENSE" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val catName = json.optString("category", "Other")
                    val account = json.optString("account", "cash").lowercase()
                    val note = json.optString("note", "Logged via NEXUS AI")

                    val matchedCat = categories.find { it.name.equals(catName, ignoreCase = true) }
                    val catId = matchedCat?.id ?: "cat_other"
                    val classification = matchedCat?.classification ?: ExpenseClassification.DISCRETIONARY

                    val payload = ActionPayload(
                        amount = amount,
                        categoryId = catId,
                        categoryName = catName,
                        account = if (account in listOf("cash", "card", "secondary")) account else "cash",
                        classification = classification,
                        note = note
                    )
                    card = ChatActionCard(
                        type = ActionType.ADD_EXPENSE,
                        title = if (isSinhala) "වියදම තහවුරු කරන්න" else "Confirm Expense Entry",
                        summary = "Amount: LKR %,.2f · %s (%s)".format(amount, catName, account.replaceFirstChar { it.uppercase() }),
                        payload = payload,
                        isSinhala = isSinhala
                    )
                }

                "ADD_INCOME" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val source = json.optString("source", "other").lowercase()
                    val note = json.optString("note", "Logged via NEXUS AI")
                    val title = IncomeSource.getDisplayName(source)

                    val payload = ActionPayload(
                        amount = amount,
                        source = source,
                        title = title,
                        note = note,
                        account = ExpenseAccount.CARD
                    )
                    card = ChatActionCard(
                        type = ActionType.ADD_INCOME,
                        title = if (isSinhala) "ආදායම තහවුරු කරන්න" else "Confirm Income Entry",
                        summary = "Amount: +LKR %,.2f · Source: %s".format(amount, title),
                        payload = payload,
                        isSinhala = isSinhala
                    )
                }

                "ADD_GOAL" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val title = json.optString("title", "Savings Goal")
                    val targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, 6) }

                    val payload = ActionPayload(
                        amount = amount,
                        title = title,
                        targetDate = targetCal.time
                    )
                    card = ChatActionCard(
                        type = ActionType.ADD_GOAL,
                        title = if (isSinhala) "ඉලක්කය සාදන්න" else "Create Savings Goal",
                        summary = "Target: LKR %,.0f · Title: %s".format(amount, title),
                        payload = payload,
                        isSinhala = isSinhala
                    )
                }

                "ADD_CATEGORY" -> {
                    val title = json.optString("title", "New Category")
                    val budget = if (json.has("budget")) json.optDouble("budget", 0.0) else null

                    val payload = ActionPayload(
                        title = title,
                        classification = ExpenseClassification.DISCRETIONARY,
                        estimatedAmount = budget
                    )
                    val budgetText = if (budget != null && budget > 0) " · Budget: LKR %,.0f".format(budget) else ""
                    card = ChatActionCard(
                        type = ActionType.ADD_CATEGORY,
                        title = if (isSinhala) "නව කාණ්ඩය තහවුරු කරන්න" else "Confirm New Category",
                        summary = "Name: %s%s".format(title, budgetText),
                        payload = payload,
                        isSinhala = isSinhala
                    )
                }

                "ADD_RECURRING_BILL" -> {
                    val title = json.optString("title", "Recurring Bill")
                    val amount = json.optDouble("amount", 0.0)
                    val freq = json.optString("frequency", "monthly")
                    val targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }

                    val payload = ActionPayload(
                        amount = amount,
                        title = title,
                        classification = ExpenseClassification.COMMITTED,
                        frequency = freq,
                        targetDate = targetCal.time
                    )
                    card = ChatActionCard(
                        type = ActionType.ADD_RECURRING_BILL,
                        title = if (isSinhala) "පුනරාවර්තී බිල්පත තහවුරු කරන්න" else "Confirm Recurring Bill",
                        summary = "%s: LKR %,.2f · Frequency: %s".format(title, amount, freq.replaceFirstChar { it.uppercase() }),
                        payload = payload,
                        isSinhala = isSinhala
                    )
                }

                "CLEAR_ALERTS" -> {
                    card = ChatActionCard(
                        type = ActionType.CLEAR_ALERTS,
                        title = if (isSinhala) "දැනුම්දීම් ඉවත් කිරීම" else "Clear All Active Alerts",
                        summary = if (isSinhala) "සියලුම Alert දැනුම්දීම් කියවා අවසන් ලෙස සලකුණු කෙරේ." else "All notifications will be marked as read.",
                        payload = ActionPayload(),
                        isSinhala = isSinhala
                    )
                }

                "NAVIGATE" -> {
                    val routeKey = json.optString("route", "dashboard").lowercase()
                    val (route, name) = when {
                        routeKey.contains("expense") -> Pair(Screen.Expenses.route, "Expenses")
                        routeKey.contains("income") -> Pair(Screen.Income.route, "Income")
                        routeKey.contains("goal") -> Pair(Screen.Goals.route, "Goals")
                        routeKey.contains("report") -> Pair(Screen.Reports.route, "Reports")
                        routeKey.contains("alert") -> Pair(Screen.Alerts.route, "Alerts")
                        routeKey.contains("setting") -> Pair(Screen.Settings.route, "Settings")
                        routeKey.contains("profile") -> Pair(Screen.Profile.route, "Profile")
                        routeKey.contains("categor") -> Pair(Screen.CategoryDashboard.route, "Categories")
                        else -> Pair(Screen.Dashboard.route, "Dashboard")
                    }
                    val payload = ActionPayload(destinationRoute = route)
                    card = ChatActionCard(
                        type = ActionType.NAVIGATE,
                        title = "Go to $name",
                        summary = "Tap below to immediately open the $name screen.",
                        payload = payload,
                        isSinhala = isSinhala
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing nexus_action JSON from Gemini", e)
        }

        return GeminiAiResult(
            replyText = cleanReply.ifBlank { rawReply },
            actionCard = card
        )
    }
}
