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
import kotlinx.coroutines.flow.onEach
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

        // Initial welcoming message
        val welcomeMsg = ChatMessage(
            sender = MessageSender.BOT,
            text = "ආයුබෝවන් $firstName! මම NEXUS AI Financial Copilot. ඔබට අවශ්‍ය ඕනෑම දෙයක් මෙතැනින් කළ හැක:\n\n" +
                "• 💸 **වියදම් සටහන් කරන්න:** *'Petrol 2500 cash'*, *'Spent 1500 for lunch card'*\n" +
                "• 💰 **ආදායම් එක් කරන්න:** *'Salary 150000'*, *'Freelance 35000'*\n" +
                "• 🔮 **අනාවැකි හා උපදෙස්:** *'මගේ මේ මාසෙ වියදම් කොහොම වෙයිද?'*, *'How to save more?'*\n" +
                "• 📊 **සාරාංශය බලන්න:** *'Monthly summary'*, *'Goals pace'*\n\n" +
                "ඔබට අවශ්‍ය දේ කෙලින්ම Type කරන්න හෝ පහත Quick Suggestions තෝරන්න."
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
            // Natural thinking cadence
            delay(350)

            val intent = nlpParser.parse(text, currentCategories)
            val botResponse = processIntent(intent)

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + botResponse,
                isTyping = false
            )
        }
    }

    private fun processIntent(intent: ParsedIntent): ChatMessage {
        return when (intent) {
            is ParsedIntent.AddExpense -> {
                val p = intent.payload
                val card = ChatActionCard(
                    type = ActionType.ADD_EXPENSE,
                    title = "Confirm Expense Entry",
                    summary = "Amount: LKR %,.2f · %s (%s)".format(p.amount, p.categoryName, p.account.replaceFirstChar { it.uppercase() }),
                    payload = p
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = "මම ඔබේ වියදම පහත පරිදි සටහන් කිරීමට සූදානම් කළා. එය තහවුරු කිරීමට **'Confirm & Save'** ඔබන්න:",
                    actionCard = card
                )
            }

            is ParsedIntent.AddIncome -> {
                val p = intent.payload
                val card = ChatActionCard(
                    type = ActionType.ADD_INCOME,
                    title = "Confirm Income Entry",
                    summary = "Amount: +LKR %,.2f · Source: %s".format(p.amount, p.title),
                    payload = p
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = "මම මෙම ආදායම පහත පරිදි සටහන් කිරීමට සූදානම් කළා. එය තහවුරු කිරීමට **'Confirm & Save'** ඔබන්න:",
                    actionCard = card
                )
            }

            is ParsedIntent.AddGoal -> {
                val p = intent.payload
                val card = ChatActionCard(
                    type = ActionType.ADD_GOAL,
                    title = "Create Savings Goal",
                    summary = "Target: LKR %,.0f · Title: %s".format(p.amount, p.title),
                    payload = p
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = "ඔබේ නව Savings Goal එක සාදා ගැනීමට පහත තොරතුරු සූදානම් කර ඇත:",
                    actionCard = card
                )
            }

            is ParsedIntent.Navigate -> {
                val p = ActionPayload(destinationRoute = intent.destinationRoute)
                val card = ChatActionCard(
                    type = ActionType.NAVIGATE,
                    title = "Go to ${intent.screenName}",
                    summary = "Tap below to immediately open the ${intent.screenName} screen.",
                    payload = p
                )
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = "ඔබට අවශ්‍ය ${intent.screenName} පිටුවට යාමට පහත බටන් එක ක්ලික් කරන්න:",
                    actionCard = card
                )
            }

            is ParsedIntent.PredictionAndAdvice -> {
                val report = advisorEngine.generatePredictionAndAdvice(
                    currentIncomes,
                    currentExpenses,
                    currentGoals,
                    currentCategories
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.FinancialSummary -> {
                val summary = advisorEngine.generateSummary(
                    currentIncomes,
                    currentExpenses,
                    currentGoals,
                    currentCategories
                )
                ChatMessage(sender = MessageSender.BOT, text = summary)
            }

            is ParsedIntent.GoalPaceReport -> {
                val report = advisorEngine.generateGoalPaceReport(
                    currentGoals,
                    currentIncomes,
                    currentExpenses
                )
                ChatMessage(sender = MessageSender.BOT, text = report)
            }

            is ParsedIntent.GreetingOrHelp -> {
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = "හායි! මම NEXUS AI Financial Copilot.\n\n" +
                        "මම ඔබේ මූල්‍ය කළමනාකරු ලෙස ක්‍රියා කරනවා. ඔබට පුළුවන්:\n" +
                        "1. **වියදම් හෝ ආදායම් එකතු කරන්න:** උදා: *'Fuel 2000 cash'*, *'Salary 120000'*\n" +
                        "2. **අනාවැකි හා මූල්‍ය උපදෙස් ලබාගන්න:** උදා: *'මේ මාසෙ වියදම් කොහොම වෙයිද?'*, *'How to save more?'*\n" +
                        "3. **මූල්‍ය සාරාංශය බලන්න:** උදා: *'Monthly summary'*, *'Goals status'*\n" +
                        "4. **පිටු අතර මාරු වෙන්න:** උදා: *'Go to reports'*, *'Open expenses'*\n\n" +
                        "දැන්ම විධානයක් ලබා දෙන්න!"
                )
            }

            is ParsedIntent.Unknown -> {
                ChatMessage(
                    sender = MessageSender.BOT,
                    text = "මට මෙය හරියටම තේරුම් ගැනීමට අපහසු විය. කරුණාකර පහත උදාහරණයක් මෙන් පැහැදිලි විධානයක් ලබා දෙන්න:\n\n" +
                        "• *'Spent 2500 for groceries cash'*\n" +
                        "• *'Salary 150000'*\n" +
                        "• *'Monthly summary'*\n" +
                        "• *'Spending prediction'*"
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

        // Prevent duplicate execution
        if (card.status != ActionStatus.PENDING) return

        val updatedCard = card.copy(status = ActionStatus.CONFIRMED)
        val updatedMessages = currentMessages.toMutableList()
        updatedMessages[messageIndex] = message.copy(actionCard = updatedCard)

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
                    updatedMessages.add(
                        ChatMessage(
                            sender = MessageSender.BOT,
                            text = "✅ **වියදම සාර්ථකව සටහන් කරන ලදී!**\n" +
                                "• මුදල: **LKR %,.2f**\n• Category: **%s**\n• Account: **%s**\n"
                                    .format(p.amount, p.categoryName, p.account.replaceFirstChar { it.uppercase() }) +
                                "දත්ත ආරක්ෂිතව ගබඩා වූ අතර මූල්‍ය වාර්තා යාවත්කාලීන විය."
                        )
                    )
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
                    updatedMessages.add(
                        ChatMessage(
                            sender = MessageSender.BOT,
                            text = "✅ **ආදායම සාර්ථකව සටහන් කරන ලදී!**\n" +
                                "• මුදල: **+LKR %,.2f**\n• මාර්ගය: **%s**\n"
                                    .format(p.amount, p.title) +
                                "දත්ත ආරක්ෂිතව ගබඩා වූ අතර ඔබේ ශේෂය (Balance) ඉහළ ගියේය."
                        )
                    )
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
                    updatedMessages.add(
                        ChatMessage(
                            sender = MessageSender.BOT,
                            text = "✅ **නව Savings Goal එක සාදන ලදී!**\n" +
                                "• ඉලක්කය: **%s**\n• මුදල: **LKR %,.0f**\n"
                                    .format(p.title, p.amount) +
                                "Goals පිටුවෙන් ඔබට මෙහි ප්‍රගතිය නිරීක්ෂණය කළ හැක."
                        )
                    )
                }

                ActionType.NAVIGATE -> {
                    // Navigation handled in UI via callback
                }
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

        updatedMessages.add(
            ChatMessage(
                sender = MessageSender.BOT,
                text = "❌ **ක්‍රියාව අවලංගු කරන ලදී.** කිසිදු දත්තයක් සටහන් නොවීය."
            )
        )

        _uiState.value = _uiState.value.copy(messages = updatedMessages)
    }

    fun clearChat() {
        initializeSession()
    }
}
