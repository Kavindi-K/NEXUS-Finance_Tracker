package com.nexus.financetracker.ui.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.local.AppSettingsManager
import com.nexus.financetracker.data.local.ThemeMode
import com.nexus.financetracker.data.repository.CategoryRepository
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.data.repository.RecurringItemRepository
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalPriority
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeStatus
import com.nexus.financetracker.domain.model.RecurringItem
import com.nexus.financetracker.notification.AlertsReadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ChatBotViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringItemRepository: RecurringItemRepository,
    private val alertsReadManager: AlertsReadManager,
    private val appSettingsManager: AppSettingsManager,
    private val advisorEngine: FinancialAdvisorEngine,
    private val nlpParser: ChatBotNlpParser,
    private val geminiAiService: GeminiAiService,
    private val chatHistoryRepository: ChatHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatBotUiState())
    val uiState: StateFlow<ChatBotUiState> = _uiState.asStateFlow()

    private var currentIncomes: List<Income> = emptyList()
    private var currentExpenses: List<Expense> = emptyList()
    private var currentGoals: List<Goal> = emptyList()
    private var currentCategories: List<Category> = emptyList()
    private var currentRecurringItems: List<RecurringItem> = emptyList()

    init {
        initializeSession()
    }

    private fun initializeSession() {
        val user = auth.currentUser
        if (user == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please sign in to access your personal AI Financial Copilot."
            )
            return
        }

        val firstName = user.displayName?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Friend"
        _uiState.value = _uiState.value.copy(userFirstName = firstName)

        // Load persisted conversation history so returning to chat preserves all messages
        viewModelScope.launch {
            val saved = chatHistoryRepository.getMessages(user.uid)
            val initialMessages = if (saved.isNotEmpty()) {
                saved
            } else {
                val welcomeMsg = ChatMessage(
                    sender = MessageSender.BOT,
                    text = "Hello $firstName! I am your personal NEXUS AI Financial Copilot.\n\n" +
                        "I am directly connected to your live financial records and ready to assist you:\n" +
                        "• 💸 **Log Expenses & Incomes:** *'Petrol 2500 cash'*, *'Salary 150000'*\n" +
                        "• 🏷️ **Manage Categories & Bills:** *'Add category Gym 5000'*, *'Add recurring bill Netflix 2200'*\n" +
                        "• 🔍 **Inquire about Spending:** *'How much did I spend on food?'*, *'Recent expenses'*\n" +
                        "• 🛡️ **Runway & Affordability:** *'Emergency runway'*, *'Can I afford 50000?'*\n" +
                        "• ⚙️ **App Actions & Workflows:** *'Enable dark mode'*, *'Clear alerts'*, *'Go to reports'*\n\n" +
                        "I respond in English by default, but if you message me in සිංහල or Singlish, I will gladly answer in සිංහල!"
                )
                chatHistoryRepository.saveMessages(user.uid, listOf(welcomeMsg))
                listOf(welcomeMsg)
            }

            _uiState.value = _uiState.value.copy(
                messages = initialMessages,
                hasGeminiApiKey = appSettingsManager.settings.value.geminiApiKey.isNotBlank(),
                activeGeminiModel = appSettingsManager.settings.value.geminiModel
            )
        }

        // Observe settings for live Gemini API Key changes
        viewModelScope.launch {
            appSettingsManager.settings.collect { settings ->
                val safeModel = if (settings.geminiModel.contains("gemma", ignoreCase = true) || settings.geminiModel.isBlank()) {
                    "gemini-2.0-flash"
                } else {
                    settings.geminiModel
                }
                _uiState.value = _uiState.value.copy(
                    hasGeminiApiKey = settings.geminiApiKey.isNotBlank(),
                    activeGeminiModel = safeModel
                )
            }
        }

        // Observe reactive user data strictly isolated by authenticated userId
        combine(
            incomeRepository.getIncomes(user.uid),
            expenseRepository.getExpenses(user.uid),
            goalRepository.getGoals(user.uid),
            categoryRepository.getCategories(user.uid),
            recurringItemRepository.getRecurringItems(user.uid)
        ) { incomes, expenses, goals, categories, recurring ->
            currentIncomes = incomes
            currentExpenses = expenses
            currentGoals = goals
            currentCategories = categories
            currentRecurringItems = recurring
        }
            .catch {
                // Keep local state resilient even if remote sync encounters transient issues
            }
            .launchIn(viewModelScope)
    }

    fun sendMessage(userText: String) {
        val text = userText.trim()
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = text
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isTyping = true
        )

        val currentUserId = auth.currentUser?.uid.orEmpty()
        if (currentUserId.isNotBlank()) {
            viewModelScope.launch {
                chatHistoryRepository.appendMessage(currentUserId, userMessage)
            }
        }

        viewModelScope.launch {
            val isSinhala = isSinhalaLanguage(text)

            // Direct in-chat Gemini API Key detection and activation
            val apiKeyPattern = "(?:AIza[0-9A-Za-z_-]{35})".toRegex()
            val keyMatch = apiKeyPattern.find(text)
            if (keyMatch != null && (text.length <= 60 || text.contains("key", ignoreCase = true) || text.contains("api", ignoreCase = true) || text.contains("gemini", ignoreCase = true))) {
                val extractedKey = keyMatch.value
                val testResult = geminiAiService.testApiKey(extractedKey)
                if (testResult.isSuccess) {
                    val rawModel = testResult.getOrNull() ?: "gemini-2.0-flash"
                    val modelUsed = if (rawModel.contains("gemma", ignoreCase = true)) "gemini-2.0-flash" else rawModel
                    appSettingsManager.setGeminiApiKey(extractedKey)
                    appSettingsManager.setGeminiModel(modelUsed)
                    val successReply = if (isSinhala) {
                        "✨ **Google Gemini AI සාර්ථකව සම්බන්ධ විය! (Connected)**\n\n" +
                            "• **Active Model:** $modelUsed\n" +
                            "• **API Status:** Live & Verified ⚡\n\n" +
                            "ඔබගේ Gemini API Key එක සාර්ථකව තහවුරු කර තැන්පත් කරන ලදී. දැන් ඔබ අසන ඕනෑම ප්‍රශ්නයක් Google Gemini AI ($modelUsed) මඟින් ඔබගේ සැබෑ ආදායම්, වියදම් සහ ගිණුම් වාර්තා ගැඹුරින් විශ්ලේෂණය කර dynamic ලෙස පිළිතුරු සපයනු ඇත. ඕනෑම දෙයක් අසන්න!"
                    } else {
                        "✨ **Google Gemini AI Connected Successfully!**\n\n" +
                            "• **Active Model:** $modelUsed\n" +
                            "• **API Status:** Live & Verified ⚡\n\n" +
                            "Your Gemini API Key has been verified. All inquiries will now be dynamically powered by $modelUsed. Ask me anything!"
                    }
                    val replyMessage = ChatMessage(sender = MessageSender.BOT, text = successReply)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + replyMessage,
                        isTyping = false,
                        hasGeminiApiKey = true,
                        activeGeminiModel = modelUsed
                    )
                    if (currentUserId.isNotBlank()) {
                        chatHistoryRepository.appendMessage(currentUserId, replyMessage)
                    }
                    return@launch
                } else {
                    val errorMsg = testResult.exceptionOrNull()?.message ?: "Verification failed"
                    val failReply = if (isSinhala) {
                        "⚠️ **Gemini API Key දෝෂයක් පවතී:**\n$errorMsg\n\nකරුණාකර https://aistudio.google.com/app/apikey වෙතින් නොමිලේ ලබාගත් නිවැරදි Gemini API Key එකක් ඇතුළත් කරන්න."
                    } else {
                        "⚠️ **Gemini API Key Verification Error:**\n$errorMsg\n\nPlease ensure the key was generated at https://aistudio.google.com/app/apikey and retry."
                    }
                    val replyMessage = ChatMessage(sender = MessageSender.BOT, text = failReply)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + replyMessage,
                        isTyping = false
                    )
                    if (currentUserId.isNotBlank()) {
                        chatHistoryRepository.appendMessage(currentUserId, replyMessage)
                    }
                    return@launch
                }
            }

            // 1. Query Real Cloud AI (Gemini) with full financial context
            val currentApiKey = appSettingsManager.settings.value.geminiApiKey
            val currentModel = appSettingsManager.settings.value.geminiModel
            val outcome = geminiAiService.queryGemini(
                userMessage = text,
                userFirstName = _uiState.value.userFirstName,
                incomes = currentIncomes,
                expenses = currentExpenses,
                goals = currentGoals,
                categories = currentCategories,
                recurringItems = currentRecurringItems,
                configuredApiKey = currentApiKey,
                configuredModel = currentModel,
                isSinhala = isSinhala
            )

            val botResponse = when (outcome) {
                is GeminiAiOutcome.Success -> {
                    val actualModel = outcome.result.modelUsed.ifBlank { "gemini-2.0-flash" }
                    if (_uiState.value.activeGeminiModel != actualModel) {
                        _uiState.value = _uiState.value.copy(activeGeminiModel = actualModel)
                        appSettingsManager.setGeminiModel(actualModel)
                    }
                    ChatMessage(
                        sender = MessageSender.BOT,
                        text = outcome.result.replyText,
                        actionCard = outcome.result.actionCard
                    )
                }
                is GeminiAiOutcome.ApiKeyInvalid -> {
                    // Fallback to local on-device advisor and gently explain key issue
                    val intent = nlpParser.parse(text, currentCategories)
                    val localMsg = processIntent(intent, isSinhala)
                    val note = if (isSinhala) {
                        "\n\n---\n⚠️ *Google Gemini Key දෝෂයක් පවතී (${outcome.code}): කරුණාකර Settings වෙතින් නිවැරදි Gemini API Key එකක් ඇතුළත් කරන්න.*"
                    } else {
                        "\n\n---\n⚠️ *Google Gemini API Key Issue (${outcome.code}): Please update your key via Settings.*"
                    }
                    localMsg.copy(text = localMsg.text + note)
                }
                is GeminiAiOutcome.NetworkError -> {
                    val intent = nlpParser.parse(text, currentCategories)
                    val localMsg = processIntent(intent, isSinhala)
                    if (currentApiKey.isNotBlank()) {
                        val friendlyError = if (outcome.error.contains("timeout", ignoreCase = true)) {
                            if (isSinhala) "සම්බන්ධතාවය ප්‍රමාද විය (Network Timeout)" else "Connection timed out"
                        } else {
                            outcome.error
                        }
                        val note = if (isSinhala) {
                            "\n\n---\n⚠️ *Gemini Cloud AI: $friendlyError (On-device advisor මඟින් පිළිතුරු සපයන ලදී)*"
                        } else {
                            "\n\n---\n⚠️ *Gemini Cloud AI: $friendlyError (Answered via on-device advisor)*"
                        }
                        localMsg.copy(text = localMsg.text + note)
                    } else {
                        localMsg
                    }
                }
                is GeminiAiOutcome.ApiKeyMissing -> {
                    // Hybrid Fallback: Use on-device dynamic engine
                    val intent = nlpParser.parse(text, currentCategories)
                    val localMsg = processIntent(intent, isSinhala)
                    if (currentApiKey.isBlank() && (text.contains("gemini", ignoreCase = true) || text.contains("ai", ignoreCase = true) || text.contains("hardcode", ignoreCase = true) || text.contains("rich", ignoreCase = true))) {
                        val note = if (isSinhala) {
                            "\n\n---\n💡 *සැබෑ Google Gemini Cloud AI සක්‍රීය කිරීමට, ඔබගේ නොමිලේ ලබාගත හැකි Gemini API Key එක මෙහි paste කරන්න (හෝ ඉහළ 🔑 icon එක click කරන්න).*"
                        } else {
                            "\n\n---\n💡 *To power responses directly with Google Gemini Cloud LLM, paste your free Gemini API key here or tap the 🔑 icon at the top.*"
                        }
                        localMsg.copy(text = localMsg.text + note)
                    } else {
                        localMsg
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + botResponse,
                isTyping = false
            )
            if (currentUserId.isNotBlank()) {
                chatHistoryRepository.appendMessage(currentUserId, botResponse)
            }
        }
    }

    fun saveGeminiApiKey(key: String, preferredModel: String = "auto", onComplete: (Boolean, String?, String?) -> Unit) {
        viewModelScope.launch {
            val trimmed = key.trim()
            val testResult = geminiAiService.testApiKey(trimmed, preferredModel)
            if (testResult.isSuccess) {
                val workingModel = testResult.getOrNull() ?: "auto"
                appSettingsManager.setGeminiApiKey(trimmed)
                appSettingsManager.setGeminiModel(workingModel)
                _uiState.value = _uiState.value.copy(
                    hasGeminiApiKey = true,
                    activeGeminiModel = workingModel
                )
                onComplete(true, null, workingModel)
            } else {
                onComplete(false, testResult.exceptionOrNull()?.message ?: "API Key verification failed", null)
            }
        }
    }

    suspend fun discoverAvailableModels(key: String): List<String> {
        return geminiAiService.listSupportedModels(key)
    }

    fun removeGeminiApiKey() {
        appSettingsManager.setGeminiApiKey("")
        appSettingsManager.setGeminiModel("auto")
        _uiState.value = _uiState.value.copy(
            hasGeminiApiKey = false,
            activeGeminiModel = "auto"
        )
    }

    private fun isSinhalaLanguage(text: String): Boolean {
        if (text.any { it in '\u0D80'..'\u0DFF' }) return true

        val singlishMarkers = listOf(
            "kiyada", "wiyadam", "aawa", "hambuna", "gahauwa", "gaththa", "gewwa",
            "karanna", "sinhalen", "kohomada", "ithuru", "padiya", "salli",
            "wiyadama", "aadaayama", "ilakkaya", "mata", "ona", "mage", "kaewa",
            "balanna", "puluwanda", "hadanna", "mokakda"
        )
        val lower = text.lowercase()
        return singlishMarkers.any { lower.contains(it) }
    }

    private fun processIntent(intent: ParsedIntent, isSinhala: Boolean): ChatMessage {
        return when (intent) {
            is ParsedIntent.AddExpense -> {
                val p = intent.payload
                val card = ChatActionCard(
                    type = ActionType.ADD_EXPENSE,
                    title = if (isSinhala) "වියදම තහවුරු කරන්න" else "Confirm Expense Entry",
                    summary = "Amount: LKR %,.2f · %s (%s)".format(p.amount, p.categoryName, p.account.replaceFirstChar { it.uppercase() }),
                    payload = p,
                    isSinhala = isSinhala
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "මම ඔබේ වියදම පහත පරිදි සටහන් කිරීමට සූදානම් කළා. එය තහවුරු කිරීමට **'Confirm & Save'** ඔබන්න:"
                    } else {
                        "I have prepared your expense entry below. Please tap **'Confirm & Save'** to record it:"
                    },
                    actionCard = card
                )
            }

            is ParsedIntent.AddIncome -> {
                val p = intent.payload
                val card = ChatActionCard(
                    type = ActionType.ADD_INCOME,
                    title = if (isSinhala) "ආදායම තහවුරු කරන්න" else "Confirm Income Entry",
                    summary = "Amount: +LKR %,.2f · Source: %s".format(p.amount, p.title),
                    payload = p,
                    isSinhala = isSinhala
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "මම මෙම ආදායම පහත පරිදි සටහන් කිරීමට සූදානම් කළා. එය තහවුරු කිරීමට **'Confirm & Save'** ඔබන්න:"
                    } else {
                        "I have prepared your income entry below. Please tap **'Confirm & Save'** to record it:"
                    },
                    actionCard = card
                )
            }

            is ParsedIntent.AddGoal -> {
                val p = intent.payload
                val card = ChatActionCard(
                    type = ActionType.ADD_GOAL,
                    title = if (isSinhala) "ඉලක්කය සාදන්න" else "Create Savings Goal",
                    summary = "Target: LKR %,.0f · Title: %s".format(p.amount, p.title),
                    payload = p,
                    isSinhala = isSinhala
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "ඔබේ නව Savings Goal එක සාදා ගැනීමට පහත තොරතුරු සූදානම් කර ඇත:"
                    } else {
                        "I have prepared your new Savings Goal below. Tap **'Confirm & Save'** to create it:"
                    },
                    actionCard = card
                )
            }

            is ParsedIntent.AddCategory -> {
                val p = intent.payload
                val budgetText = if (p.estimatedAmount != null && p.estimatedAmount > 0) " · Budget: LKR %,.0f".format(p.estimatedAmount) else ""
                val card = ChatActionCard(
                    type = ActionType.ADD_CATEGORY,
                    title = if (isSinhala) "නව කාණ්ඩය තහවුරු කරන්න" else "Confirm New Category",
                    summary = "Name: %s%s".format(p.title, budgetText),
                    payload = p,
                    isSinhala = isSinhala
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "ඔබට නව කාණ්ඩයක් (Category) එක් කිරීමට පහත විස්තර සකස් කර ඇත. තහවුරු කිරීමට **'Confirm & Save'** ඔබන්න:"
                    } else {
                        "I have prepared your new expense category below. Tap **'Confirm & Save'** to create it:"
                    },
                    actionCard = card
                )
            }

            is ParsedIntent.AddRecurringBill -> {
                val p = intent.payload
                val card = ChatActionCard(
                    type = ActionType.ADD_RECURRING_BILL,
                    title = if (isSinhala) "පුනරාවර්තී බිල්පත තහවුරු කරන්න" else "Confirm Recurring Bill",
                    summary = "%s: LKR %,.2f · Frequency: Monthly".format(p.title, p.amount),
                    payload = p,
                    isSinhala = isSinhala
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "ඔබගේ පුනරාවර්තී බිල්පත (Recurring Bill) පහත පරිදි කාලසටහන් කිරීමට සූදානම්. තහවුරු කිරීමට **'Confirm & Save'** ඔබන්න:"
                    } else {
                        "I have scheduled your recurring bill reminder below. Tap **'Confirm & Save'** to activate it:"
                    },
                    actionCard = card
                )
            }

            is ParsedIntent.ClearAlerts -> {
                val card = ChatActionCard(
                    type = ActionType.CLEAR_ALERTS,
                    title = if (isSinhala) "දැනුම්දීම් ඉවත් කිරීම" else "Clear All Active Alerts",
                    summary = if (isSinhala) "සියලුම Alert දැනුම්දීම් කියවා අවසන් ලෙස සලකුණු කෙරේ." else "All notifications will be marked as read.",
                    payload = ActionPayload(),
                    isSinhala = isSinhala
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "ඔබේ දැනුම්දීම් ලැයිස්තුව පිරිසිදු කිරීමට අවශ්‍යද? තහවුරු කිරීමට පහත බොත්තම ඔබන්න:"
                    } else {
                        "Would you like to mark all pending notifications and alerts as read? Tap below to confirm:"
                    },
                    actionCard = card
                )
            }

            is ParsedIntent.ToggleSetting -> {
                val p = ActionPayload(settingKey = intent.settingKey, settingValue = intent.enable)
                val card = ChatActionCard(
                    type = ActionType.UPDATE_SETTINGS,
                    title = "Update Setting: ${intent.label}",
                    summary = "Set ${intent.label} to ${if (intent.enable) "Enabled" else "Disabled"}.",
                    payload = p,
                    isSinhala = isSinhala
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "ඔබට ${intent.label} සැකසුම වෙනස් කිරීමට අවශ්‍ය නම් **'Confirm & Save'** ඔබන්න:"
                    } else {
                        "Tap **'Confirm & Save'** to apply this system preference change:"
                    },
                    actionCard = card
                )
            }

            is ParsedIntent.Navigate -> {
                val p = ActionPayload(destinationRoute = intent.destinationRoute)
                val card = ChatActionCard(
                    type = ActionType.NAVIGATE,
                    title = "Go to ${intent.screenName}",
                    summary = "Tap below to immediately open the ${intent.screenName} screen.",
                    payload = p,
                    isSinhala = isSinhala
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "ඔබට අවශ්‍ය ${intent.screenName} පිටුවට යාමට පහත බොත්තම ඔබන්න:"
                    } else {
                        "Tap the button below to immediately open the ${intent.screenName} screen:"
                    },
                    actionCard = card
                )
            }

            is ParsedIntent.CategorySpendingQuery -> {
                val report = advisorEngine.queryCategorySpending(
                    intent.categoryKeyword,
                    currentExpenses,
                    currentCategories,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.RecentTransactionsQuery -> {
                val report = advisorEngine.queryRecentTransactions(
                    currentExpenses,
                    currentCategories,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.UpcomingBillsQuery -> {
                val report = advisorEngine.queryUpcomingBills(
                    currentRecurringItems,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.IncomeBreakdownQuery -> {
                val report = advisorEngine.queryIncomeBreakdown(
                    currentIncomes,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.GoalSpecificQuery -> {
                val report = advisorEngine.queryGoalSpecific(
                    intent.goalKeyword,
                    currentGoals,
                    currentIncomes,
                    currentExpenses,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.AppExplanationQuery -> {
                val report = advisorEngine.explainAppFeature(intent.topic, isSinhala = isSinhala)
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.AffordabilityQuery -> {
                val report = advisorEngine.checkAffordability(
                    intent.amount,
                    currentIncomes,
                    currentExpenses,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.FinancialHealthQuery -> {
                val report = advisorEngine.analyzeFinancialHealth(
                    currentIncomes,
                    currentExpenses,
                    currentGoals,
                    _uiState.value.userFirstName,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.ExpenseAnalysisQuery -> {
                val report = advisorEngine.analyzeExpenseBreakdown(
                    currentExpenses,
                    currentCategories,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.EmergencyRunwayQuery -> {
                val report = advisorEngine.analyzeEmergencyRunway(
                    currentIncomes,
                    currentExpenses,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.GeneralFinancialAdvice -> {
                val report = advisorEngine.provide50_30_20Budget(
                    currentIncomes,
                    currentExpenses,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.PredictionAndAdvice -> {
                val report = advisorEngine.generatePredictionAndAdvice(
                    currentIncomes,
                    currentExpenses,
                    currentGoals,
                    currentCategories,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.FinancialSummary -> {
                val summary = advisorEngine.generateSummary(
                    currentIncomes,
                    currentExpenses,
                    currentGoals,
                    currentCategories,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = summary)
            }

            is ParsedIntent.GoalPaceReport -> {
                val report = advisorEngine.generateGoalPaceReport(
                    currentGoals,
                    currentIncomes,
                    currentExpenses,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.GreetingOrHelp -> {
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "හායි! මම ඔබගේ පුද්ගලික NEXUS AI Financial Copilot.\n\n" +
                            "මම ඔබේ මූල්‍ය දත්ත සමඟ සජීවීව සම්බන්ධ වී සිටින අතර පහත ඕනෑම දෙයක් කිරීමට මට හැක:\n" +
                            "• 💸 **වියදම් හෝ ආදායම් සටහන් කිරීම:** උදා: *'Fuel 2500 cash'*, *'Salary 150000'*\n" +
                            "• 🏷️ **අලුත් Category හෝ Recurring Bill එකතු කිරීම:** උදා: *'Add category Gym 5000'*, *'Add recurring bill Wifi 3000'*\n" +
                            "• 🔍 **වියදම් විමසීම:** උදා: *'How much did I spend on food?'*, *'Recent expenses'*\n" +
                            "• 🛡️ **මූල්‍ය සෞඛ්‍යය සහ Runway:** උදා: *'Can I afford 50000?'*, *'Emergency runway'*\n" +
                            "• ⚙️ **Settings සහ Navigation:** උදා: *'Enable dark mode'*, *'Clear alerts'*, *'Go to reports'*\n\n" +
                            "ඔබට අවශ්‍ය ඕනෑම දෙයක් මගෙන් අසන්න!"
                    } else {
                        "Hello! I am your personal NEXUS AI Financial Copilot.\n\n" +
                            "I am directly connected to your live financial records and ready to assist you:\n" +
                            "• 💸 **Log Expenses & Incomes:** e.g. *'Petrol 2500 cash'*, *'Salary 150000'*\n" +
                            "• 🏷️ **Add Categories & Recurring Bills:** e.g. *'Add category Gym 5000'*, *'Add recurring bill Netflix 2200'*\n" +
                            "• 🔍 **Inquire about Spending:** e.g. *'How much did I spend on food?'*, *'Recent expenses'*\n" +
                            "• 🛡️ **Runway & Affordability:** e.g. *'Can I afford 50000?'*, *'Emergency runway'*\n" +
                            "• ⚙️ **Settings & Navigation:** e.g. *'Enable dark mode'*, *'Clear alerts'*, *'Go to reports'*\n\n" +
                            "Feel free to type any command or tap a suggestion below!"
                    }
                )
            }

            is ParsedIntent.GeneralConsultation -> {
                val response = advisorEngine.provideGeneralConsultation(
                    intent.query,
                    currentIncomes,
                    currentExpenses,
                    currentGoals,
                    currentCategories,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = response)
            }

            is ParsedIntent.Unknown -> {
                val response = advisorEngine.provideGeneralConsultation(
                    intent.originalText,
                    currentIncomes,
                    currentExpenses,
                    currentGoals,
                    currentCategories,
                    isSinhala = isSinhala
                )
                ChatMessage(sender = MessageSender.BOT, text = response)
            }
        }
    }

    fun confirmActionCard(cardId: String) {
        val userId = auth.currentUser?.uid ?: return

        val currentMessages = _uiState.value.messages
        val messageIndex = currentMessages.indexOfFirst { it.actionCard?.id == cardId }
        if (messageIndex == -1) return

        val message = currentMessages[messageIndex]
        val card = message.actionCard ?: return

        if (card.status != ActionStatus.PENDING) return

        val updatedCard = card.copy(status = ActionStatus.CONFIRMED)
        val updatedMessages = currentMessages.toMutableList()
        updatedMessages[messageIndex] = message.copy(actionCard = updatedCard)

        val isSinhala = card.isSinhala

        viewModelScope.launch {
            when (card.type) {
                ActionType.ADD_EXPENSE -> {
                    val p = card.payload
                    val newExpense = Expense(
                        userId = userId,
                        amountLKR = p.amount,
                        categoryId = p.categoryId,
                        account = p.account,
                        classification = p.classification,
                        note = p.note.ifBlank { "Logged via NEXUS AI Copilot" },
                        date = Date(),
                        createdAt = Date()
                    )
                    expenseRepository.addExpense(newExpense)
                    val text = if (isSinhala) {
                        "✅ **වියදම සාර්ථකව සටහන් කරන ලදී!**\n" +
                            "• මුදල: **LKR %,.2f**\n• Category: **%s**\n• Account: **%s**\n"
                                .format(p.amount, p.categoryName, p.account.replaceFirstChar { it.uppercase() }) +
                            "දත්ත ආරක්ෂිතව ගබඩා වූ අතර මූල්‍ය වාර්තා යාවත්කාලීන විය."
                    } else {
                        "✅ **Expense recorded successfully!**\n" +
                            "• Amount: **LKR %,.2f**\n• Category: **%s**\n• Account: **%s**\n"
                                .format(p.amount, p.categoryName, p.account.replaceFirstChar { it.uppercase() }) +
                            "Your financial records and balances have been updated securely."
                    }
                    updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))
                }

                ActionType.ADD_INCOME -> {
                    val p = card.payload
                    val newIncome = Income(
                        userId = userId,
                        amountLKR = p.amount,
                        amountOriginal = p.amount,
                        currency = "LKR",
                        exchangeRate = 1.0,
                        source = p.source,
                        status = IncomeStatus.CONFIRMED,
                        dateReceived = Date(),
                        note = p.note.ifBlank { "Logged via NEXUS AI Copilot" },
                        createdAt = Date()
                    )
                    incomeRepository.addIncome(newIncome)
                    val text = if (isSinhala) {
                        "✅ **ආදායම සාර්ථකව සටහන් කරන ලදී!**\n" +
                            "• මුදල: **+LKR %,.2f**\n• මාර්ගය: **%s**\n"
                                .format(p.amount, p.title) +
                            "දත්ත ආරක්ෂිතව ගබඩා වූ අතර ඔබේ ශේෂය (Balance) ඉහළ ගියේය."
                    } else {
                        "✅ **Income recorded successfully!**\n" +
                            "• Amount: **+LKR %,.2f**\n• Source: **%s**\n"
                                .format(p.amount, p.title) +
                            "Your financial records and net balance have been updated securely."
                    }
                    updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))
                }

                ActionType.ADD_GOAL -> {
                    val p = card.payload
                    val newGoal = Goal(
                        userId = userId,
                        title = p.title,
                        targetAmountLKR = p.amount,
                        openingSavingsLKR = 0.0,
                        targetDate = p.targetDate,
                        priority = GoalPriority.HIGH,
                        isActive = true,
                        createdAt = Date()
                    )
                    goalRepository.addGoal(userId, newGoal)
                    val text = if (isSinhala) {
                        "🎯 **Savings Goal එක සාර්ථකව නිර්මාණය විය!**\n" +
                            "• ඉලක්කය: **%s**\n• මුදල: **LKR %,.0f**\n"
                                .format(p.title, p.amount) +
                            "ඔබේ ප්‍රගතිය සහ අවශ්‍ය මාසික ඉතිරිය මම ස්වයංක්‍රීයව අධීක්ෂණය කරන්නෙමි."
                    } else {
                        "🎯 **Savings Goal created successfully!**\n" +
                            "• Goal: **%s**\n• Target: **LKR %,.0f**\n"
                                .format(p.title, p.amount) +
                            "I will proactively track your progress and required monthly pace."
                    }
                    updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))
                }

                ActionType.ADD_CATEGORY -> {
                    val p = card.payload
                    val newCategory = Category(
                        userId = userId,
                        name = p.title,
                        classification = p.classification,
                        estimatedAmount = p.estimatedAmount,
                        createdAt = Date()
                    )
                    categoryRepository.addCategory(newCategory)
                    val budgetInfo = if (p.estimatedAmount != null && p.estimatedAmount > 0) " (Budget: LKR %,.0f)".format(p.estimatedAmount) else ""
                    val text = if (isSinhala) {
                        "🏷️ **නව කාණ්ඩය සාර්ථකව එකතු කරන ලදී!**\n" +
                            "• නම: **%s**%s\n".format(p.title, budgetInfo) +
                            "දැන් මෙම කාණ්ඩය යටතේ වියදම් සටහන් කළ හැක."
                    } else {
                        "🏷️ **Category added successfully!**\n" +
                            "• Name: **%s**%s\n".format(p.title, budgetInfo) +
                            "You can now assign expenses to this category immediately."
                    }
                    updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))
                }

                ActionType.ADD_RECURRING_BILL -> {
                    val p = card.payload
                    val newItem = RecurringItem(
                        userId = userId,
                        title = p.title,
                        amountLKR = p.amount,
                        categoryId = p.categoryId,
                        classification = p.classification,
                        frequency = p.frequency,
                        nextDueDate = p.targetDate,
                        isActive = true,
                        createdAt = Date()
                    )
                    recurringItemRepository.addRecurringItem(newItem)
                    val text = if (isSinhala) {
                        "⏰ **පුනරාවර්තී බිල්පත සාර්ථකව කාලසටහන් කරන ලදී!**\n" +
                            "• බිල්පත: **%s**\n• මුදල: **LKR %,.2f**\n".format(p.title, p.amount) +
                            "නියමිත දිනයට පෙර මම ඔබට සිහිපත් කරන්නෙමි."
                    } else {
                        "⏰ **Recurring bill reminder scheduled successfully!**\n" +
                            "• Bill: **%s**\n• Amount: **LKR %,.2f**\n".format(p.title, p.amount) +
                            "Automated notifications will alert you prior to the due date."
                    }
                    updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))
                }

                ActionType.CLEAR_ALERTS -> {
                    val allIds = currentRecurringItems.map { "bill_${it.id}" } +
                        currentIncomes.map { "income_${it.id}" } +
                        currentGoals.map { "goal_${it.id}" }
                    alertsReadManager.markAllAsRead(allIds)
                    alertsReadManager.markAllAsSeen(allIds)
                    val text = if (isSinhala) {
                        "🔔 **සියලුම දැනුම්දීම් කියවා අවසන් ලෙස සලකුණු කරන ලදී!**"
                    } else {
                        "🔔 **All active notifications have been marked as read!**"
                    }
                    updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))
                }

                ActionType.UPDATE_SETTINGS -> {
                    val p = card.payload
                    when (p.settingKey) {
                        "theme_dark" -> appSettingsManager.setThemeMode(ThemeMode.DARK)
                        "theme_light" -> appSettingsManager.setThemeMode(ThemeMode.LIGHT)
                        "notifications" -> appSettingsManager.setNotificationsEnabled(p.settingValue)
                        "biometrics" -> appSettingsManager.setBiometricLockEnabled(p.settingValue)
                    }
                    val text = if (isSinhala) {
                        "⚙️ **සැකසුම සාර්ථකව යාවත්කාලීන කරන ලදී!**"
                    } else {
                        "⚙️ **System preference updated successfully!**"
                    }
                    updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))
                }

                ActionType.NAVIGATE -> {
                    // Navigation actions are executed directly via onNavigate callback in UI
                }
            }

            _uiState.value = _uiState.value.copy(messages = updatedMessages)
            if (userId.isNotBlank()) {
                chatHistoryRepository.saveMessages(userId, updatedMessages)
            }
        }
    }

    fun cancelActionCard(cardId: String) {
        val currentMessages = _uiState.value.messages
        val messageIndex = currentMessages.indexOfFirst { it.actionCard?.id == cardId }
        if (messageIndex == -1) return

        val message = currentMessages[messageIndex]
        val card = message.actionCard ?: return

        val updatedCard = card.copy(status = ActionStatus.CANCELLED)
        val updatedMessages = currentMessages.toMutableList()
        updatedMessages[messageIndex] = message.copy(actionCard = updatedCard)

        val isSinhala = card.isSinhala
        val text = if (isSinhala) {
            "❌ මෙම ක්‍රියාව අවලංගු කරන ලදී. කිසිදු දත්තයක් වෙනස් නොවිණි."
        } else {
            "❌ Action cancelled. No records were modified."
        }
        updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))

        _uiState.value = _uiState.value.copy(messages = updatedMessages)
        val userId = auth.currentUser?.uid.orEmpty()
        if (userId.isNotBlank()) {
            viewModelScope.launch {
                chatHistoryRepository.saveMessages(userId, updatedMessages)
            }
        }
    }

    fun clearChat() {
        val user = auth.currentUser
        val firstName = _uiState.value.userFirstName
        val welcomeMsg = ChatMessage(
            sender = MessageSender.BOT,
            text = "Hello $firstName! How else can I assist you with your finances today?"
        )
        _uiState.value = _uiState.value.copy(messages = listOf(welcomeMsg))
        if (user != null) {
            viewModelScope.launch {
                chatHistoryRepository.saveMessages(user.uid, listOf(welcomeMsg))
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
