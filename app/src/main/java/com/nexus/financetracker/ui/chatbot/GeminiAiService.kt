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
    val actionCard: ChatActionCard? = null,
    val modelUsed: String = ""
)

sealed class GeminiAiOutcome {
    data class Success(val result: GeminiAiResult) : GeminiAiOutcome()
    data class ApiKeyMissing(val message: String) : GeminiAiOutcome()
    data class ApiKeyInvalid(val code: Int, val message: String) : GeminiAiOutcome()
    data class NetworkError(val error: String) : GeminiAiOutcome()
}

@Singleton
class GeminiAiService @Inject constructor() {

    companion object {
        private const val TAG = "GeminiAiService"

        // Default candidate models to test in priority order if ListModels is unavailable
        val FALLBACK_CANDIDATE_MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-2.0-flash-exp",
            "gemini-1.5-pro",
            "gemini-pro"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var cachedWorkingModel: String? = null

    @Volatile
    private var cachedApiVersion: String = "v1beta"

    suspend fun listSupportedModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) return@withContext emptyList()

        for (apiVersion in listOf("v1beta", "v1")) {
            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/$apiVersion/models?key=$trimmedKey")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val root = JSONObject(body)
                    val modelsArray = root.optJSONArray("models") ?: continue
                    val result = mutableListOf<String>()
                    for (i in 0 until modelsArray.length()) {
                        val m = modelsArray.getJSONObject(i)
                        val name = m.optString("name", "") // e.g. "models/gemini-2.0-flash"
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        val supportsGenerate = if (methods != null) {
                            (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                        } else true
                        if (supportsGenerate && name.isNotBlank()) {
                            result.add(name.removePrefix("models/"))
                        }
                    }
                    if (result.isNotEmpty()) {
                        Log.i(TAG, "Discovered ${result.size} supported models via $apiVersion: $result")
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error discovering models via $apiVersion", e)
            }
        }
        emptyList()
    }

    fun selectBestModel(availableModels: List<String>, preferredModel: String? = null): String {
        if (!preferredModel.isNullOrBlank() && preferredModel != "auto") {
            val directMatch = availableModels.find { it.equals(preferredModel, ignoreCase = true) }
            if (directMatch != null) return directMatch
            val partialMatch = availableModels.find { it.contains(preferredModel, ignoreCase = true) }
            if (partialMatch != null) return partialMatch
        }

        // Priority ranking
        val priorityList = listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp",
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash-8b",
            "gemini-2.0",
            "gemini-1.5-pro",
            "gemini-1.5-pro-latest",
            "gemini-pro"
        )

        for (target in priorityList) {
            val found = availableModels.find { it.equals(target, ignoreCase = true) }
                ?: availableModels.find { it.contains(target, ignoreCase = true) }
            if (found != null) return found
        }

        return availableModels.firstOrNull { it.contains("gemini", ignoreCase = true) }
            ?: availableModels.firstOrNull()
            ?: "gemini-2.0-flash"
    }

    suspend fun testApiKey(apiKey: String, preferredModel: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank"))
        }

        // 1. Discover models available for this specific key
        val available = listSupportedModels(trimmed)
        val candidateModels = if (available.isNotEmpty()) {
            val best = selectBestModel(available, preferredModel)
            listOf(best) + available.filter { it != best }
        } else {
            FALLBACK_CANDIDATE_MODELS
        }

        var lastError: Exception = Exception("Unable to verify Gemini API connection")

        for (model in candidateModels.take(6)) {
            for (apiVersion in listOf("v1beta", "v1")) {
                try {
                    val cleanModel = model.removePrefix("models/").removePrefix("/")
                    val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$cleanModel:generateContent?key=$trimmed"

                    val jsonBody = JSONObject().apply {
                        val contents = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                val parts = JSONArray().apply {
                                    put(JSONObject().apply { put("text", "ping") })
                                }
                                put("parts", parts)
                            }
                            put(partObj)
                        }
                        put("contents", contents)
                        val generationConfig = JSONObject().apply {
                            put("maxOutputTokens", 5)
                        }
                        put("generationConfig", generationConfig)
                    }

                    val request = Request.Builder()
                        .url(url)
                        .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        Log.i(TAG, "Test ping successful with model $cleanModel on $apiVersion")
                        cachedWorkingModel = cleanModel
                        cachedApiVersion = apiVersion
                        return@withContext Result.success(cleanModel)
                    } else {
                        val errorMsg = try {
                            JSONObject(body).optJSONObject("error")?.optString("message") ?: response.message
                        } catch (_: Exception) {
                            response.message
                        }

                        // If key is totally invalid, stop trying other models
                        if (response.code == 400 && (errorMsg.contains("API key not valid", ignoreCase = true) || errorMsg.contains("API_KEY_INVALID", ignoreCase = true))) {
                            return@withContext Result.failure(Exception("API key not valid. Please check your Gemini API key."))
                        }

                        lastError = Exception("HTTP ${response.code}: $errorMsg")
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }

        Result.failure(lastError)
    }

    suspend fun queryGemini(
        userMessage: String,
        userFirstName: String,
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>,
        recurringItems: List<RecurringItem>,
        configuredApiKey: String?,
        configuredModel: String? = null,
        isSinhala: Boolean
    ): GeminiAiOutcome = withContext(Dispatchers.IO) {
        val apiKey = configuredApiKey?.trim().orEmpty()
        if (apiKey.isBlank()) {
            return@withContext GeminiAiOutcome.ApiKeyMissing("No Gemini API key provided")
        }

        try {
            val systemContext = buildFinancialContext(
                userFirstName = userFirstName,
                incomes = incomes,
                expenses = expenses,
                goals = goals,
                categories = categories,
                recurringItems = recurringItems
            )

            val fullPrompt = "$systemContext\n\nUser Question/Request: \"$userMessage\""

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
                    put("maxOutputTokens", 800)
                }
                put("generationConfig", generationConfig)
            }

            // Determine models to attempt
            val candidateModels = mutableListOf<String>()
            if (!configuredModel.isNullOrBlank() && configuredModel != "auto") {
                candidateModels.add(configuredModel.removePrefix("models/"))
            }
            cachedWorkingModel?.let { candidateModels.add(it) }
            candidateModels.addAll(FALLBACK_CANDIDATE_MODELS)
            val distinctModels = candidateModels.distinct()

            var lastErrorOutcome: GeminiAiOutcome? = null

            for (model in distinctModels) {
                for (apiVersion in listOf(cachedApiVersion, if (cachedApiVersion == "v1beta") "v1" else "v1beta")) {
                    try {
                        val cleanModel = model.removePrefix("models/").removePrefix("/")
                        val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$cleanModel:generateContent?key=$apiKey"

                        val request = Request.Builder()
                            .url(url)
                            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()

                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string().orEmpty()

                        if (response.isSuccessful) {
                            cachedWorkingModel = cleanModel
                            cachedApiVersion = apiVersion

                            val root = JSONObject(responseBody)
                            val candidates = root.optJSONArray("candidates")
                                ?: continue
                            if (candidates.length() == 0) continue

                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                                ?: continue
                            val parts = content.optJSONArray("parts")
                                ?: continue
                            if (parts.length() == 0) continue

                            val rawReply = parts.getJSONObject(0).optString("text", "").trim()
                            if (rawReply.isBlank()) continue

                            val parsed = parseGeminiOutput(rawReply, isSinhala, categories, cleanModel)
                            return@withContext GeminiAiOutcome.Success(parsed)
                        } else {
                            Log.w(TAG, "Gemini model $cleanModel on $apiVersion returned: ${response.code}")
                            val isKeyIssue = response.code == 400 || response.code == 403
                            val errorMsg = try {
                                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: response.message
                            } catch (_: Exception) {
                                response.message
                            }

                            // If 404 (model not found), continue trying the next model!
                            if (response.code == 404) {
                                continue
                            }

                            // If 400 with invalid key or 403 permission denied, break early
                            if (isKeyIssue && (errorMsg.contains("API key not valid", ignoreCase = true) || errorMsg.contains("SERVICE_DISABLED", ignoreCase = true))) {
                                return@withContext GeminiAiOutcome.ApiKeyInvalid(response.code, errorMsg)
                            }

                            lastErrorOutcome = GeminiAiOutcome.NetworkError("HTTP ${response.code}: $errorMsg")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Attempt with model $model on $apiVersion failed: ${e.message}")
                        val friendlyMsg = if (e.message?.contains("timeout", ignoreCase = true) == true) {
                            "Connection timed out while reaching Gemini servers"
                        } else {
                            e.message ?: "Network error"
                        }
                        lastErrorOutcome = GeminiAiOutcome.NetworkError(friendlyMsg)
                    }
                }
            }

            lastErrorOutcome ?: GeminiAiOutcome.NetworkError("Failed to reach any available Gemini model")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API execution failed", e)
            GeminiAiOutcome.NetworkError(e.message ?: "Network error connecting to Gemini")
        }
    }

    private fun buildFinancialContext(
        userFirstName: String,
        incomes: List<Income>,
        expenses: List<Expense>,
        goals: List<Goal>,
        categories: List<Category>,
        recurringItems: List<RecurringItem>
    ): String {
        val confirmedIncomes = incomes.filter { it.status == IncomeStatus.CONFIRMED }
        val totalIncome = confirmedIncomes.sumOf { it.amountLKR }
        val totalExpenses = expenses.sumOf { it.amountLKR }
        val netBalance = totalIncome - totalExpenses
        val savingsRate = if (totalIncome > 0) ((netBalance / totalIncome) * 100.0).coerceAtLeast(0.0) else 0.0
        val runwayMonths = if (totalExpenses > 0) (netBalance / totalExpenses).coerceAtLeast(0.0) else 0.0
        val runwayDays = (runwayMonths * 30).toInt()

        val goalsSummary = goals.filter { it.isActive }.take(4).joinToString("; ") {
            "${it.title} (Target: LKR ${it.targetAmountLKR.toInt()}, Days remaining: ${it.daysRemaining})"
        }.ifBlank { "None active" }

        val billsSummary = recurringItems.filter { it.isActive }.take(4).joinToString("; ") {
            "${it.title} (LKR ${it.amountLKR.toInt()} ${it.frequency})"
        }.ifBlank { "None active" }

        val categorySpending: List<Pair<String, Double>> = expenses.groupBy { exp ->
            categories.find { it.id == exp.categoryId }?.name ?: "Other"
        }.mapValues { entry -> entry.value.sumOf { it.amountLKR } }
            .toList()
            .sortedByDescending { it.second }

        val topCategorySummary = if (categorySpending.isEmpty()) "No expense records yet"
        else categorySpending.take(5).joinToString("\n") { (name, amount) ->
            val pct = if (totalExpenses > 0) (amount / totalExpenses) * 100.0 else 0.0
            "- $name: LKR %,.2f (%.1f%% of all spending)".format(amount, pct)
        }

        val recentExpensesSummary = if (expenses.isEmpty()) "No recent expenses"
        else expenses.sortedByDescending { it.date }.take(8).joinToString("\n") { exp ->
            val catName = categories.find { it.id == exp.categoryId }?.name ?: "Other"
            val noteStr = if (!exp.note.isNullOrBlank()) " (${exp.note})" else ""
            "- %s: LKR %,.2f [%s, %s]%s".format(catName, exp.amountLKR, exp.account, exp.classification, noteStr)
        }

        val incomeSourcesSummary = if (confirmedIncomes.isEmpty()) "None recorded"
        else confirmedIncomes.groupBy { it.source }.mapValues { it.value.sumOf { inc -> inc.amountLKR } }
            .toList().joinToString(", ") { (src, amt) ->
                "${IncomeSource.getDisplayName(src)}: LKR %,.0f".format(amt)
            }

        val formattedIncome = "LKR %,.2f".format(totalIncome)
        val formattedExpenses = "LKR %,.2f".format(totalExpenses)
        val formattedBalance = "LKR %,.2f".format(netBalance)
        val formattedSavingsRate = "%.1f%%".format(savingsRate)
        val formattedRunway = "%.1f".format(runwayMonths)

        return """
You are NEXUS AI, an elite, empathetic, and exceptionally intelligent personal financial copilot inside the NEXUS Finance Tracker app.
You are having a direct, engaging conversation with $userFirstName.

Live Financial Ledger for $userFirstName:
• Confirmed Total Income: $formattedIncome (Breakdown: $incomeSourcesSummary)
• Total Living Expenses: $formattedExpenses
• Current Net Capital Surplus: $formattedBalance
• Savings Retention Rate: $formattedSavingsRate
• Survival Emergency Runway: $formattedRunway months (~$runwayDays days)
• Active Savings Goals: $goalsSummary
• Scheduled Recurring Commitments: $billsSummary

Top Spending Breakdown:
$topCategorySummary

Recent Transactions:
$recentExpensesSummary

System Directives:
1. True AI Reasoning: Carefully analyze the user's specific inquiry. NEVER give canned, hardcoded, or repetitive answers. Deeply incorporate their live figures (income, expenses, surplus, top spending categories, and runway).
2. Language: If the user asks in English, answer in polished, encouraging, highly analytical English. If the user writes in Sinhala or Singlish, answer fluently, warmly, and naturally in Sinhala!
3. Mathematical Precision: Calculate real percentages and savings potentials based on their numbers above.
4. App Actions: If the user wants to take an action (record expense/income, add goal, add category, recurring bill, clear alerts, navigate), append the ```nexus_action JSON block at the very end of your message. Supported actions:
- ADD_EXPENSE: "amount" (number), "category" (string), "account" ("cash"|"card"|"secondary"), "note" (string)
- ADD_INCOME: "amount" (number), "source" ("salary"|"freelance"|"crypto"|"adsense"|"other"), "note" (string)
- ADD_GOAL: "amount" (number), "title" (string)
- ADD_CATEGORY: "title" (string), "budget" (number optional)
- ADD_RECURRING_BILL: "title" (string), "amount" (number), "frequency" ("monthly"|"weekly"|"yearly")
- CLEAR_ALERTS: {}
- NAVIGATE: "route" ("dashboard"|"expenses"|"income"|"goals"|"reports"|"alerts"|"settings"|"profile"|"categories")
5. Speed & Conciseness: Deliver immediate, high-value financial insight. Keep answers within 2-3 focused paragraphs or bullet points without unnecessarily verbose filler.

If the user is asking advice, chatting, asking about wealth, or checking health, do NOT output any action block.
""".trimIndent()
    }

    private fun parseGeminiOutput(
        rawReply: String,
        isSinhala: Boolean,
        categories: List<Category>,
        modelName: String = ""
    ): GeminiAiResult {
        val actionRegex = "```nexus_action\\s*([\\s\\S]*?)\\s*```".toRegex()
        val match = actionRegex.find(rawReply)

        if (match == null) {
            return GeminiAiResult(replyText = rawReply.trim(), actionCard = null, modelUsed = modelName)
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
            actionCard = card,
            modelUsed = modelName
        )
    }
}
