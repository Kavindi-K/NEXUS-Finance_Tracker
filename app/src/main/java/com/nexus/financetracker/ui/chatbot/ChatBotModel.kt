package com.nexus.financetracker.ui.chatbot

import java.util.Date
import java.util.UUID

enum class MessageSender {
    USER,
    BOT
}

enum class ActionType {
    ADD_EXPENSE,
    ADD_INCOME,
    ADD_GOAL,
    ADD_CATEGORY,
    ADD_RECURRING_BILL,
    CLEAR_ALERTS,
    UPDATE_SETTINGS,
    NAVIGATE
}

enum class ActionStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}

data class ActionPayload(
    val amount: Double = 0.0,
    val categoryId: String = "",
    val categoryName: String = "",
    val account: String = "cash",
    val classification: String = "discretionary",
    val source: String = "other",
    val note: String = "",
    val title: String = "",
    val targetDate: Date = Date(),
    val destinationRoute: String = "",
    val settingKey: String = "",
    val settingValue: Boolean = false,
    val themeMode: String = "",
    val frequency: String = "monthly",
    val estimatedAmount: Double? = null
)

data class ChatActionCard(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val title: String,
    val summary: String,
    val payload: ActionPayload,
    val status: ActionStatus = ActionStatus.PENDING,
    val isSinhala: Boolean = false,
    val timestamp: Date = Date()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val actionCard: ChatActionCard? = null,
    val timestamp: Date = Date(),
    val isThinking: Boolean = false
)

data class ChatBotUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val quickPrompts: List<String> = listOf(
        "📅 ඊයේ මම කොච්චර වියදම් කළාද?",
        "💰 මට කොච්චර income එකක් ඇවිත් තියෙනවද?",
        "📊 Recent expenses breakdown",
        "🛡️ Emergency runway check",
        "💸 Can I afford 50,000?",
        "🏆 Financial health check"
    ),
    val userFirstName: String = "User",
    val isOnline: Boolean = true,
    val hasGeminiApiKey: Boolean = false,
    val activeGeminiModel: String = "auto",
    val errorMessage: String? = null
)

