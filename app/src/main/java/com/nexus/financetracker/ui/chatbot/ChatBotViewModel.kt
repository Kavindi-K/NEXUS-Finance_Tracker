package com.nexus.financetracker.ui.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.CategoryRepository
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.Income
import com.nexus.financetracker.domain.model.IncomeStatus
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
    private val advisorEngine: FinancialAdvisorEngine,
    private val nlpParser: ChatBotNlpParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatBotUiState())
    val uiState: StateFlow<ChatBotUiState> = _uiState.asStateFlow()

    private var currentIncomes: List<Income> = emptyList()
    private var currentExpenses: List<Expense> = emptyList()
    private var currentGoals: List<Goal> = emptyList()
    private var currentCategories: List<Category> = emptyList()

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

        // Initial welcoming message in English by default
        val welcomeMsg = ChatMessage(
            sender = MessageSender.BOT,
            text = "Hello $firstName! I am your personal NEXUS AI Financial Copilot.\n\n" +
                "You can chat with me in English or සිංහල to manage your finances:\n" +
                "• 💸 **Log Expenses:** *'Petrol 2500 cash'*, *'Spent 1500 for lunch card'*\n" +
                "• 💰 **Add Income:** *'Salary 150000'*, *'Freelance 35000'*\n" +
                "• 🔮 **Predictions & Advice:** *'How is my spending pace?'*, *'How to save more?'*\n" +
                "• 📊 **Summaries:** *'Monthly summary'*, *'Goals pace'*\n\n" +
                "I respond in English by default, but if you message me in සිංහල or Singlish, I will gladly reply in සිංහල!"
        )
        _uiState.value = _uiState.value.copy(messages = listOf(welcomeMsg))

        // Observe reactive user data strictly isolated by authenticated userId
        combine(
            incomeRepository.getIncomes(user.uid),
            expenseRepository.getExpenses(user.uid),
            goalRepository.getGoals(user.uid),
            categoryRepository.getCategories(user.uid)
        ) { incomes, expenses, goals, categories ->
            currentIncomes = incomes
            currentExpenses = expenses
            currentGoals = goals
            currentCategories = categories
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

        viewModelScope.launch {
            delay(350)

            val isSinhala = isSinhalaLanguage(text)
            val intent = nlpParser.parse(text, currentCategories)
            val botResponse = processIntent(intent, isSinhala)

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + botResponse,
                isTyping = false
            )
        }
    }

    private fun isSinhalaLanguage(text: String): Boolean {
        // 1. Any Sinhala Unicode characters: \u0D80 to \u0DFF
        if (text.any { it in '\u0D80'..'\u0DFF' }) return true

        // 2. Common Singlish markers
        val singlishMarkers = listOf(
            "kiyada", "wiyadam", "aawa", "hambuna", "gahauwa", "gaththa", "gewwa",
            "karanna", "sinhalen", "kohomada", "ithuru", "padiya", "salli",
            "wiyadama", "aadaayama", "ilakkaya", "mata", "ona", "mage", "kaewa"
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
                        "ඔබට අවශ්‍ය ${intent.screenName} පිටුවට යාමට පහත බටන් එක ක්ලික් කරන්න:"
                    } else {
                        "Tap the button below to immediately open the ${intent.screenName} screen:"
                    },
                    actionCard = card
                )
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
                        "හායි! මම NEXUS AI Financial Copilot.\n\n" +
                            "මම ඔබේ මූල්‍ය කළමනාකරු ලෙස ක්‍රියා කරනවා. ඔබට පුළුවන්:\n" +
                            "1. **වියදම් හෝ ආදායම් එකතු කරන්න:** උදා: *'Fuel 2000 cash'*, *'Salary 120000'*\n" +
                            "2. **අනාවැකි හා මූල්‍ය උපදෙස් ලබාගන්න:** උදා: *'මේ මාසෙ වියදම් කොහොම වෙයිද?'*, *'How to save more?'*\n" +
                            "3. **මූල්‍ය සාරාංශය බලන්න:** උදා: *'Monthly summary'*, *'Goals pace'*\n" +
                            "4. **පිටු අතර මාරු වෙන්න:** උදා: *'Go to reports'*, *'Open expenses'*\n\n" +
                            "දැන්ම විධානයක් ලබා දෙන්න!"
                    } else {
                        "Hello! I am your personal NEXUS AI Financial Copilot.\n\n" +
                            "Here is what I can assist you with:\n" +
                            "1. **Log Expenses or Income:** e.g. *'Fuel 2000 cash'*, *'Salary 150000'*\n" +
                            "2. **Predictions & Financial Advice:** e.g. *'How is my spending pace?'*, *'How to save more?'*\n" +
                            "3. **Financial Summaries:** e.g. *'Monthly summary'*, *'Goals pace'*\n" +
                            "4. **Navigate the App:** e.g. *'Go to reports'*, *'Open expenses'*\n\n" +
                            "Feel free to type any command or tap a suggestion below!"
                    }
                )
            }

            is ParsedIntent.Unknown -> {
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = if (isSinhala) {
                        "මට මෙය හරියටම තේරුම් ගැනීමට අපහසු විය. කරුණාකර පහත උදාහරණයක් මෙන් පැහැදිලි විධානයක් ලබා දෙන්න:\n\n" +
                            "• *'Spent 2500 for groceries cash'*\n" +
                            "• *'Salary 150000'*\n" +
                            "• *'Monthly summary'*\n" +
                            "• *'Spending prediction'*"
                    } else {
                        "I didn't quite catch that. Here are some examples of what you can ask me:\n\n" +
                            "• *'Spent 2500 for groceries cash'*\n" +
                            "• *'Salary 150000'*\n" +
                            "• *'Monthly summary'*\n" +
                            "• *'Spending prediction'*"
                    }
                )
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
                        startDate = Date(),
                        targetDate = p.targetDate,
                        isActive = true,
                        createdAt = Date()
                    )
                    goalRepository.addGoal(userId, newGoal)
                    val text = if (isSinhala) {
                        "✅ **නව Savings Goal එක සාදන ලදී!**\n" +
                            "• ඉලක්කය: **%s**\n• මුදල: **LKR %,.0f**\n"
                                .format(p.title, p.amount) +
                            "Goals පිටුවෙන් ඔබට මෙහි ප්‍රගතිය නිරීක්ෂණය කළ හැක."
                    } else {
                        "✅ **New Savings Goal created!**\n" +
                            "• Goal: **%s**\n• Target: **LKR %,.0f**\n"
                                .format(p.title, p.amount) +
                            "You can track your real-time savings pace from the Goals screen."
                    }
                    updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))
                }

                ActionType.NAVIGATE -> {}
            }

            _uiState.value = _uiState.value.copy(messages = updatedMessages)
        }
    }

    fun cancelActionCard(cardId: String) {
        val currentMessages = _uiState.value.messages
        val messageIndex = currentMessages.indexOfFirst { it.actionCard?.id == cardId }
        if (messageIndex == -1) return

        val message = currentMessages[messageIndex]
        val card = message.actionCard ?: return

        if (card.status != ActionStatus.PENDING) return

        val updatedCard = card.copy(status = ActionStatus.CANCELLED)
        val updatedMessages = currentMessages.toMutableList()
        updatedMessages[messageIndex] = message.copy(actionCard = updatedCard)

        val text = if (card.isSinhala) {
            "❌ **ක්‍රියාව අවලංගු කරන ලදී.** කිසිදු දත්තයක් සටහන් නොවීය."
        } else {
            "❌ **Action cancelled.** No changes were made to your records."
        }
        updatedMessages.add(ChatMessage(sender = MessageSender.BOT, text = text))

        _uiState.value = _uiState.value.copy(messages = updatedMessages)
    }

    fun clearChat() {
        initializeSession()
    }
}
