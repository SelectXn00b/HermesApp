package com.xiaomo.androidforclaw.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomo.androidforclaw.channel.ChannelManager
import com.xiaomo.androidforclaw.ui.compose.ChatMessage
import com.xiaomo.androidforclaw.ui.compose.MessageStatus
import com.xiaomo.androidforclaw.ui.session.SessionManager
import com.xiaomo.androidforclaw.util.ReasoningTagFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Chat interface ViewModel - Single Source of Truth architecture
 *
 * Architecture principles:
 * 1. SessionManager is the single source of truth for messages
 * 2. Periodically sync messages from backend, only update UI when there are new messages
 * 3. Avoid duplicate messages and complex merge logic
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val SYNC_INTERVAL = 3000L // 3 seconds
    }

    // Single data source: SessionManager
    private val uiSessionManager = SessionManager()
    private val channelManager = ChannelManager(application)

    // Expose session-related flows
    val sessions: StateFlow<List<SessionManager.Session>> = uiSessionManager.sessions
    val currentSession: StateFlow<SessionManager.Session> = uiSessionManager.currentSession

    // Message list - obtained directly from currentSession
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Track sync state for each session
    private val sessionSyncState = mutableMapOf<String, Int>() // sessionId -> lastMessageCount

    init {
        // Listen to currentSession changes, update message list directly
        viewModelScope.launch {
            currentSession.collect { session ->
                _messages.value = session.messages
            }
        }

        // Initialize on startup
        viewModelScope.launch {
            initialize()
        }

        // Periodically sync messages from backend (smart sync, only update when new messages arrive)
        viewModelScope.launch {
            while (true) {
                delay(SYNC_INTERVAL)
                syncFromBackend()
            }
        }
    }

    /**
     * Initialize - Load history on startup
     */
    private suspend fun initialize() {
        try {
            Log.d(TAG, "🚀 [Initialize] Starting...")

            // Ensure MainEntryNew is initialized
            com.xiaomo.androidforclaw.core.MainEntryNew.initialize(getApplication())

            // Load all backend sessions (Feishu/Discord/WebSocket)
            uiSessionManager.loadSessionsFromBackend()

            // Load current session messages
            syncFromBackend()

            Log.d(TAG, "✅ [Initialize] Completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Initialize] Failed", e)
        }
    }

    /**
     * Sync messages from backend - Smart sync, only update when new messages arrive
     */
    private suspend fun syncFromBackend() {
        try {
            val sessionId = currentSession.value.id
            Log.d(TAG, "🔍 [Sync Check] Session: $sessionId")

            // If it's a backend session, messages are already synced in loadSessionsFromBackend
            if (isBackendSession(sessionId)) {
                Log.d(TAG, "⏭️ [Sync Skip] Backend session")
                return
            }

            // UI local session - use current session ID as backend session key
            val agentSessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
            if (agentSessionManager == null) {
                Log.w(TAG, "⚠️ [Sync] SessionManager not initialized")
                return
            }

            // Get corresponding agent session using current session ID
            val agentSession = agentSessionManager.get(sessionId)
            if (agentSession == null) {
                Log.d(TAG, "ℹ️ [Sync] Session $sessionId has no backend data")
                return
            }

            val newMessageCount = agentSession.messageCount()
            val lastSyncedCount = sessionSyncState[sessionId] ?: 0
            Log.d(TAG, "📊 [Sync] last=$lastSyncedCount, new=$newMessageCount")

            // Check if there are new messages
            if (newMessageCount <= lastSyncedCount) {
                Log.d(TAG, "⏭️ [Sync Skip] No new messages")
                return
            }

            Log.d(TAG, "🔄 [Sync] New messages: $lastSyncedCount -> $newMessageCount")

            // Convert all messages
            val chatMessages = convertMessages(agentSession.messages)

            // Update SessionManager (single source of truth)
            uiSessionManager.replaceCurrentSessionMessages(chatMessages)

            sessionSyncState[sessionId] = newMessageCount
            Log.d(TAG, "✅ [Sync] Completed: ${chatMessages.size} messages")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Sync] Failed", e)
        }
    }

    /**
     * Convert backend messages to UI messages
     */
    private fun convertMessages(messages: List<com.xiaomo.androidforclaw.providers.LegacyMessage>): List<ChatMessage> {
        return messages.mapNotNull { msg ->
            val contentStr: String? = when (val content = msg.content) {
                is String -> content
                null -> null
                else -> content.toString()
            }

            if (contentStr.isNullOrEmpty()) {
                return@mapNotNull null
            }

            when (msg.role) {
                "user" -> ChatMessage(
                    content = contentStr,
                    isUser = true,
                    status = MessageStatus.SENT
                )
                "assistant" -> {
                    val cleanContent = ReasoningTagFilter.stripReasoningTags(contentStr)
                    if (cleanContent.isNotEmpty()) {
                        ChatMessage(
                            content = cleanContent,
                            isUser = false,
                            status = MessageStatus.SENT
                        )
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }

    /**
     * Check if it's a backend session
     */
    private fun isBackendSession(sessionId: String): Boolean {
        return sessionId.startsWith("discord_") ||
               sessionId.contains("_p2p") ||
               sessionId.contains("_group") ||
               sessionId.startsWith("session_")
    }

    /**
     * Send user message
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        Log.d(TAG, "💬 [Send] $content")

        // Record inbound
        channelManager.recordInbound()

        // Add user message to UI
        val userMessage = ChatMessage(
            content = content,
            isUser = true,
            status = MessageStatus.SENT
        )
        uiSessionManager.addMessageToCurrentSession(userMessage)

        // Add thinking indicator
        val thinkingMessage = ChatMessage(
            content = "正在思考...",
            isUser = false,
            status = MessageStatus.SENDING
        )
        uiSessionManager.addMessageToCurrentSession(thinkingMessage)

        // Call MainEntryNew to execute
        viewModelScope.launch {
            val sessionId = currentSession.value.id
            Log.d(TAG, "🚀 [MainEntryNew] Execute (session: $sessionId)...")

            com.xiaomo.androidforclaw.core.MainEntryNew.runWithSession(
                userInput = content,
                sessionId = sessionId,  // 直接使用当前 session ID，不转换为 "default"
                application = getApplication()
            )

            // Wait a moment before removing thinking message
            delay(500)
            uiSessionManager.removeMessageFromCurrentSession(thinkingMessage.id)
        }

        // Auto-generate session title
        if (currentSession.value.title == "新对话") {
            uiSessionManager.autoGenerateCurrentSessionTitle()
        }
    }

    // === Session Management ===

    fun createNewSession() {
        uiSessionManager.createSession()
        // New session automatically initializes sync state to 0
    }

    fun switchSession(sessionId: String) {
        Log.d(TAG, "🔀 [Switch Session] $sessionId")
        uiSessionManager.switchSession(sessionId)
        // Immediately sync when switching sessions
        viewModelScope.launch {
            syncFromBackend()
        }
    }

    fun deleteSession(sessionId: String) {
        uiSessionManager.deleteSession(sessionId)
        // Clean up sync state
        sessionSyncState.remove(sessionId)

        // Clean up backend session
        val agentSessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
        agentSessionManager?.clear(sessionId)
    }

    fun clearCurrentSession() {
        val sessionId = currentSession.value.id
        Log.d(TAG, "🗑️ [Clear Session] $sessionId")

        uiSessionManager.clearCurrentSession()

        // Also clear Agent Session
        val agentSessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
        agentSessionManager?.clear(if (isBackendSession(sessionId)) sessionId else sessionId)

        // Reset sync state
        sessionSyncState[sessionId] = 0
    }
}
