package com.nexus.financetracker.ui.chatbot

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton repository managing persistent chat history for the AI Copilot.
 * Uses Android's built-in JSON facilities with zero extra dependencies.
 * Ensures that conversation history is preserved when navigating back and forth
 * between app screens, and across app process restarts.
 */
@Singleton
class ChatHistoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ChatHistoryRepo"
        private const val MAX_PERSISTED_MESSAGES = 100
    }

    // In-memory cache per user for instant retrieval without disk reads
    private val memoryCache = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    suspend fun getMessages(userId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext emptyList()

        memoryCache[userId]?.let {
            return@withContext it.toList()
        }

        val loaded = loadFromDisk(userId)
        memoryCache[userId] = loaded.toMutableList()
        loaded
    }

    suspend fun saveMessages(userId: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val trimmed = if (messages.size > MAX_PERSISTED_MESSAGES) messages.takeLast(MAX_PERSISTED_MESSAGES) else messages
        memoryCache[userId] = trimmed.toMutableList()
        saveToDisk(userId, trimmed)
    }

    suspend fun appendMessage(userId: String, message: ChatMessage) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val list = memoryCache.getOrPut(userId) { loadFromDisk(userId).toMutableList() }
        list.add(message)
        val trimmed = if (list.size > MAX_PERSISTED_MESSAGES) list.takeLast(MAX_PERSISTED_MESSAGES) else list
        memoryCache[userId] = trimmed.toMutableList()
        saveToDisk(userId, trimmed)
    }

    suspend fun clearHistory(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        memoryCache.remove(userId)
        try {
            val file = getHistoryFile(userId)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete chat history file for user $userId", e)
        }
    }

    private fun getHistoryFile(userId: String): File {
        val sanitized = userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(context.filesDir, "nexus_chat_$sanitized.json")
    }

    private fun loadFromDisk(userId: String): List<ChatMessage> {
        return try {
            val file = getHistoryFile(userId)
            if (!file.exists()) return emptyList()
            val text = file.readText().trim()
            if (text.isBlank()) return emptyList()

            val array = JSONArray(text)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                parseMessage(obj)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chat history from disk for user $userId", e)
            emptyList()
        }
    }

    private fun saveToDisk(userId: String, messages: List<ChatMessage>) {
        try {
            val file = getHistoryFile(userId)
            val array = JSONArray()
            messages.forEach { msg ->
                array.put(serializeMessage(msg))
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat history to disk for user $userId", e)
        }
    }

    private fun serializeMessage(msg: ChatMessage): JSONObject {
        return JSONObject().apply {
            put("id", msg.id)
            put("sender", msg.sender.name)
            put("text", msg.text)
            put("timestamp", msg.timestamp.time)
            put("isThinking", msg.isThinking)
            msg.actionCard?.let { card ->
                put("actionCard", serializeCard(card))
            }
        }
    }

    private fun parseMessage(obj: JSONObject): ChatMessage? {
        return try {
            val id = obj.optString("id", "")
            val senderName = obj.optString("sender", MessageSender.BOT.name)
            val sender = try { MessageSender.valueOf(senderName) } catch (_: Exception) { MessageSender.BOT }
            val text = obj.optString("text", "")
            val timestamp = Date(obj.optLong("timestamp", System.currentTimeMillis()))
            val isThinking = obj.optBoolean("isThinking", false)
            val actionCard = obj.optJSONObject("actionCard")?.let { parseCard(it) }

            ChatMessage(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                sender = sender,
                text = text,
                actionCard = actionCard,
                timestamp = timestamp,
                isThinking = isThinking
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ChatMessage JSON", e)
            null
        }
    }

    private fun serializeCard(card: ChatActionCard): JSONObject {
        return JSONObject().apply {
            put("id", card.id)
            put("type", card.type.name)
            put("title", card.title)
            put("summary", card.summary)
            put("status", card.status.name)
            put("isSinhala", card.isSinhala)
            put("timestamp", card.timestamp.time)

            val p = card.payload
            val payloadObj = JSONObject().apply {
                put("amount", p.amount)
                put("categoryId", p.categoryId)
                put("categoryName", p.categoryName)
                put("account", p.account)
                put("classification", p.classification)
                put("note", p.note)
                put("source", p.source)
                put("title", p.title)
                p.targetDate?.let { put("targetDate", it.time) }
                put("frequency", p.frequency)
                p.estimatedAmount?.let { put("estimatedAmount", it) }
                put("destinationRoute", p.destinationRoute)
            }
            put("payload", payloadObj)
        }
    }

    private fun parseCard(obj: JSONObject): ChatActionCard? {
        return try {
            val id = obj.optString("id", java.util.UUID.randomUUID().toString())
            val typeStr = obj.optString("type", ActionType.ADD_EXPENSE.name)
            val type = try { ActionType.valueOf(typeStr) } catch (_: Exception) { ActionType.ADD_EXPENSE }
            val title = obj.optString("title", "")
            val summary = obj.optString("summary", "")
            val statusStr = obj.optString("status", ActionStatus.PENDING.name)
            val status = try { ActionStatus.valueOf(statusStr) } catch (_: Exception) { ActionStatus.PENDING }
            val isSinhala = obj.optBoolean("isSinhala", false)
            val timestamp = Date(obj.optLong("timestamp", System.currentTimeMillis()))

            val pObj = obj.optJSONObject("payload")
            val payload = if (pObj != null) {
                ActionPayload(
                    amount = pObj.optDouble("amount", 0.0),
                    categoryId = pObj.optString("categoryId", ""),
                    categoryName = pObj.optString("categoryName", ""),
                    account = pObj.optString("account", ""),
                    classification = pObj.optString("classification", ""),
                    note = pObj.optString("note", ""),
                    source = pObj.optString("source", ""),
                    title = pObj.optString("title", ""),
                    targetDate = if (pObj.has("targetDate")) Date(pObj.getLong("targetDate")) else Date(),
                    frequency = pObj.optString("frequency", "monthly"),
                    estimatedAmount = if (pObj.has("estimatedAmount")) pObj.getDouble("estimatedAmount") else null,
                    destinationRoute = pObj.optString("destinationRoute", "")
                )
            } else {
                ActionPayload()
            }

            ChatActionCard(
                id = id,
                type = type,
                title = title,
                summary = summary,
                payload = payload,
                status = status,
                isSinhala = isSinhala,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ChatActionCard JSON", e)
            null
        }
    }
}
