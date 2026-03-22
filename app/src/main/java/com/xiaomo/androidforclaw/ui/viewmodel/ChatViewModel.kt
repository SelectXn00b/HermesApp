/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.viewmodel

import android.app.Application
import com.xiaomo.androidforclaw.core.MainEntryNew
import com.xiaomo.androidforclaw.logging.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomo.androidforclaw.channel.ChannelManager
import com.xiaomo.androidforclaw.ui.compose.ChatMessage
import com.xiaomo.androidforclaw.ui.compose.MessageStatus
import com.xiaomo.androidforclaw.ui.session.SessionManager
import com.xiaomo.androidforclaw.util.ReasoningTagFilter
import org.json.JSONObject
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
        private const val THINKING_SYNC_POLL_INTERVAL = 500L
        private const val THINKING_MAX_WAIT_MS = 60_000L
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

        observeAgentProgress()

        // Periodically sync messages from backend (smart sync, only update when new messages arrive)
        viewModelScope.launch {
            while (true) {
                delay(SYNC_INTERVAL)
                syncFromBackend()
            }
        }
    }

    private var lastProgressContent: String? = null
    private var thinkingShownForCurrentRun: Boolean = false

    private fun observeAgentProgress() {
        viewModelScope.launch {
            MainEntryNew.uiProgressFlow.collect { event ->
                val isToolEvent = event.type == "tool_call" || event.type == "tool_result"
                val rendered = when (event.type) {
                    "iteration" -> ""
                    "thinking" -> ""  // Skip: sendMessage() already adds thinking indicator
                    "tool_call" -> "${event.title}\n${event.content}"
                    "tool_result" -> "${event.title}\n${event.content}"
                    "block_reply" -> event.content
                    "error" -> "${event.title}\n${event.content}"
                    else -> "${event.title}\n${event.content}"
                }.trim()

                if (rendered.isBlank() || rendered == lastProgressContent) return@collect
                lastProgressContent = rendered

                uiSessionManager.addMessageToCurrentSession(
                    ChatMessage(
                        content = rendered,
                        isUser = false,
                        status = MessageStatus.SENT,
                        isToolCall = isToolEvent
                    )
                )
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

            // Use current session ID as backend session key
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

            when (msg.role) {
                "user" -> {
                    if (contentStr.isNullOrEmpty()) return@mapNotNull null
                    ChatMessage(content = contentStr, isUser = true, status = MessageStatus.SENT)
                }
                "assistant" -> {
                    val toolCalls = msg.toolCalls
                    if (!toolCalls.isNullOrEmpty()) {
                        // Tool call request: show tool name + args, same format as progress events
                        val toolText = toolCalls.joinToString("\n\n") { tc ->
                            val argsText = try {
                                val json = JSONObject(tc.function.arguments)
                                if (json.length() == 0) "无参数"
                                else json.keys().asSequence().joinToString("\n") { key ->
                                    "  • $key: ${json.opt(key)}"
                                }
                            } catch (e: Exception) {
                                tc.function.arguments
                            }
                            "执行: ${tc.function.name}\n$argsText"
                        }
                        ChatMessage(
                            content = toolText,
                            isUser = false,
                            status = MessageStatus.SENT,
                            isToolCall = true
                        )
                    } else {
                        if (contentStr.isNullOrEmpty()) return@mapNotNull null
                        val cleanContent = ReasoningTagFilter.stripReasoningTags(contentStr)
                        if (cleanContent.isEmpty()) return@mapNotNull null
                        ChatMessage(content = cleanContent, isUser = false, status = MessageStatus.SENT)
                    }
                }
                "tool" -> {
                    if (contentStr.isNullOrEmpty()) return@mapNotNull null
                    ChatMessage(
                        content = "执行完成\n$contentStr",
                        isUser = false,
                        status = MessageStatus.SENT,
                        isToolCall = true
                    )
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
        thinkingShownForCurrentRun = false
        lastProgressContent = null

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
            val startSyncedCount = sessionSyncState[sessionId] ?: 0
            _isLoading.value = true
            Log.d(TAG, "🚀 [MainEntryNew] Execute (session: $sessionId)...")

            try {
                com.xiaomo.androidforclaw.core.MainEntryNew.runWithSession(
                    userInput = content,
                    sessionId = sessionId,  // 直接使用当前 session ID，不转换为 "default"
                    application = getApplication()
                )

                val startedAt = System.currentTimeMillis()
                while (System.currentTimeMillis() - startedAt < THINKING_MAX_WAIT_MS) {
                    syncFromBackend()
                    val currentSyncedCount = sessionSyncState[sessionId] ?: 0
                    if (currentSyncedCount > startSyncedCount) {
                        break
                    }
                    delay(THINKING_SYNC_POLL_INTERVAL)
                }
            } finally {
                uiSessionManager.removeMessageFromCurrentSession(thinkingMessage.id)
                _isLoading.value = false
                syncFromBackend()
            }
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
        // Note: backend session clear is already handled inside uiSessionManager.deleteSession()
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
