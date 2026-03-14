/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.session

import com.xiaomo.androidforclaw.ui.compose.ChatMessage
import com.xiaomo.androidforclaw.util.ReasoningTagFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Session Manager - Multi-session management
 *
 * Features:
 * - Create/delete sessions
 * - Switch sessions
 * - Independent message history for each session
 * - Session metadata (title, creation time, etc.)
 */
class SessionManager {

    data class Session(
        val id: String = UUID.randomUUID().toString(),
        val title: String = "新对话",
        val createdAt: Long = System.currentTimeMillis(),
        val messages: List<ChatMessage> = emptyList(),
        val isActive: Boolean = false
    ) {
        /**
         * Generate title based on first user message
         */
        fun generateTitle(): String {
            val firstUserMessage = messages.firstOrNull { it.isUser }
            return if (firstUserMessage != null) {
                val content = firstUserMessage.content
                if (content.length > 20) {
                    content.take(20) + "..."
                } else {
                    content
                }
            } else {
                "新对话 ${createdAt}"
            }
        }
    }

    companion object {
        private const val PREF_LAST_SESSION_ID = "last_session_id"
    }

    // MMKV for persistent storage
    private val mmkv by lazy {
        com.tencent.mmkv.MMKV.defaultMMKV()
    }

    private val _sessions = MutableStateFlow<List<Session>>(listOf(createDefaultSession()))
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<Session>(_sessions.value.first())
    val currentSession: StateFlow<Session> = _currentSession.asStateFlow()

    /**
     * Create default session
     */
    private fun createDefaultSession(): Session {
        // Check if it's first run - OpenClaw style
        val welcomeMessage = getWelcomeMessage()

        return Session(
            title = "新对话",
            messages = listOf(
                ChatMessage(
                    content = welcomeMessage,
                    isUser = false
                )
            ),
            isActive = true
        )
    }

    /**
     * Whether a session is just the startup placeholder with no real conversation yet.
     */
    private fun isEphemeralDefaultSession(session: Session): Boolean {
        return session.title == "新对话" &&
            session.messages.size == 1 &&
            session.messages.firstOrNull()?.isUser == false
    }

    /**
     * Get welcome message - OpenClaw style
     *
     * If workspace's IDENTITY.md is empty or doesn't exist, it's first run
     */
    private fun getWelcomeMessage(): String {
        // Check if /sdcard/.androidforclaw/workspace/ exists
        val workspaceDir = java.io.File("/sdcard/.androidforclaw/workspace")
        val identityFile = java.io.File(workspaceDir, "IDENTITY.md")

        // Determine if it's first run (file doesn't exist or is empty or contains template text)
        val isFirstRun = !identityFile.exists() ||
                         identityFile.readText().trim().isEmpty() ||
                         identityFile.readText().contains("Fill this in during your first conversation")

        return if (isFirstRun) {
            // First run - OpenClaw style guidance
            """
你好！👋

我是 AndroidForClaw，一个 AI 助手，运行在你的 Android 设备上。

在我们开始之前，我想更好地了解你，也让你了解我。

我注意到这是你第一次使用 AndroidForClaw。我们需要一起完成一些初始设置。

## 📝 需要配置的文件

你的 workspace 位于：`/sdcard/.androidforclaw/workspace/`

请使用文件管理器创建和编辑以下文件：

### 1. **IDENTITY.md** - 我是谁？
定义我的身份、个性和风格。

示例内容：
```markdown
# IDENTITY.md - Who Am I?

- **Name:** AndroidClaw
- **Creature:** AI Assistant
- **Vibe:** Helpful, precise, efficient
- **Emoji:** 🤖
```

### 2. **USER.md** - 关于你
告诉我关于你的信息，这样我可以更好地帮助你。

示例内容：
```markdown
# USER.md - About You

- **Name:** (你的名字)
- **Timezone:** Asia/Shanghai
- **Preferences:**
  - 语言: 中文
  - 风格: 简洁高效
```

### 3. **SOUL.md** - 我的性格（可选）
定义我应该如何行动和沟通。

---

完成这些配置后，我们就可以开始工作了！

你也可以直接告诉我"跳过配置"，我会使用默认设置。

需要帮助吗？😊
            """.trimIndent()
        } else {
            // Already configured - Read IDENTITY info
            try {
                val identityContent = identityFile.readText()

                // Try to parse Name and Emoji
                val nameMatch = Regex("""[*-]\s*\*?Name\*?[：:]\s*(.+)""").find(identityContent)
                val emojiMatch = Regex("""[*-]\s*\*?Emoji\*?[：:]\s*(.+)""").find(identityContent)

                val name = nameMatch?.groupValues?.get(1)?.trim() ?: "AndroidForClaw"
                val emoji = emojiMatch?.groupValues?.get(1)?.trim() ?: "🤖"

                // Regular welcome message
                """
你好！$emoji 我是 $name

我可以帮你：
- 📱 控制和测试 Android 应用
- 🔍 UI 自动化和功能验证
- 🌐 浏览网页和信息搜索
- ⚙️ 设备操作和文件管理

需要什么帮助？
                """.trimIndent()
            } catch (e: Exception) {
                // Read failed, use default message
                "你好！🤖 我是 AndroidForClaw\n\n我可以帮你控制和测试 Android 应用。需要什么帮助？"
            }
        }
    }

    /**
     * Load sessions from backend SessionManager
     * (sessions created by Feishu, Discord, WebSocket)
     */
    fun loadSessionsFromBackend() {
        try {
            val backendSessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
            if (backendSessionManager == null) {
                android.util.Log.w("SessionManager", "Backend SessionManager not initialized")
                return
            }

            val backendSessionKeys = backendSessionManager.getAllKeys()
            if (backendSessionKeys.isEmpty()) {
                android.util.Log.d("SessionManager", "No backend sessions found")
                return
            }

            // Convert backend sessions to UI sessions
            val backendSessions = backendSessionKeys.mapNotNull { key ->
                val backendSession = backendSessionManager.get(key)
                if (backendSession != null) {
                    val type = when {
                        key.startsWith("discord_") -> "Discord"
                        key.contains("_p2p") || key.contains("_group") -> "飞书"
                        key.startsWith("session_") -> "WebSocket"
                        else -> "其他"
                    }

                    // Generate title
                    val title = if (backendSession.messages.isNotEmpty()) {
                        val firstUserMsg = backendSession.messages.firstOrNull {
                            it.role == "user"
                        }
                        if (firstUserMsg != null && firstUserMsg.content != null) {
                            val content = when (val c = firstUserMsg.content) {
                                is String -> c
                                else -> c.toString()
                            }
                            if (content.length > 15) {
                                "[$type] ${content.take(15)}..."
                            } else {
                                "[$type] $content"
                            }
                        } else {
                            "[$type] ${key.take(10)}..."
                        }
                    } else {
                        "[$type] ${key.take(10)}..."
                    }

                    // Convert message format, filter reasoning tags
                    val uiMessages = backendSession.messages.mapNotNull { msg ->
                        if (msg.role == "user" || msg.role == "assistant") {
                            val contentStr = when (val c = msg.content) {
                                is String -> c
                                null -> ""
                                else -> c.toString()
                            }

                            // Filter reasoning tags for assistant messages
                            val cleanContent = if (msg.role == "assistant") {
                                ReasoningTagFilter.stripReasoningTags(contentStr)
                            } else {
                                contentStr
                            }

                            if (cleanContent.isNotEmpty()) {
                                ChatMessage(
                                    content = cleanContent,
                                    isUser = msg.role == "user"
                                )
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    Session(
                        id = key,
                        title = title,
                        createdAt = try {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                                .parse(backendSession.createdAt)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        },
                        messages = uiMessages,
                        isActive = false
                    )
                } else {
                    null
                }
            }

            if (backendSessions.isNotEmpty()) {
                // Keep real UI-only sessions, but drop the startup placeholder session
                val uiOnlySessions = _sessions.value.filter { session ->
                    !backendSessionKeys.contains(session.id) && !isEphemeralDefaultSession(session)
                }

                // Merge: backend sessions + real UI sessions
                val allSessions = (backendSessions + uiOnlySessions).sortedByDescending { it.createdAt }

                val lastSessionId = mmkv.decodeString(PREF_LAST_SESSION_ID)
                val restoredSession = lastSessionId?.let { id -> allSessions.find { it.id == id } }
                val targetSession = restoredSession ?: allSessions.firstOrNull()

                if (targetSession != null) {
                    _sessions.value = allSessions.map {
                        it.copy(isActive = it.id == targetSession.id)
                    }
                    _currentSession.value = targetSession.copy(isActive = true)
                    mmkv.encode(PREF_LAST_SESSION_ID, targetSession.id)
                    android.util.Log.d("SessionManager", "✅ Restored session: ${targetSession.id}")
                } else {
                    val defaultSession = createDefaultSession()
                    _sessions.value = listOf(defaultSession)
                    _currentSession.value = defaultSession
                    mmkv.encode(PREF_LAST_SESSION_ID, defaultSession.id)
                }

                android.util.Log.d("SessionManager", "✅ Loaded ${backendSessions.size} sessions from backend")
            }

        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "Failed to load backend sessions", e)
        }
    }

    /**
     * Create new session
     */
    fun createSession(): Session {
        val newSession = createDefaultSession()

        // Set all current sessions to inactive
        val updatedSessions = _sessions.value.map { it.copy(isActive = false) }

        // Add new session
        _sessions.value = updatedSessions + newSession.copy(isActive = true)
        _currentSession.value = newSession

        // 💾 Save current session ID to MMKV
        mmkv.encode(PREF_LAST_SESSION_ID, newSession.id)
        android.util.Log.d("SessionManager", "💾 Saved new session ID: ${newSession.id}")

        return newSession
    }

    /**
     * Switch to specified session
     */
    fun switchSession(sessionId: String) {
        val session = _sessions.value.find { it.id == sessionId }
        if (session != null) {
            // Update active status
            _sessions.value = _sessions.value.map {
                it.copy(isActive = it.id == sessionId)
            }
            _currentSession.value = session.copy(isActive = true)

            // 💾 Save current session ID to MMKV
            mmkv.encode(PREF_LAST_SESSION_ID, sessionId)
            android.util.Log.d("SessionManager", "💾 Saved session ID: $sessionId")
        }
    }

    /**
     * Delete session
     */
    fun deleteSession(sessionId: String) {
        val currentSessions = _sessions.value
        if (currentSessions.size <= 1) {
            // Keep at least one session
            return
        }

        val remainingSessions = currentSessions.filter { it.id != sessionId }
        _sessions.value = remainingSessions

        // If deleting current session, switch to latest session
        if (_currentSession.value.id == sessionId) {
            val newCurrent = remainingSessions.first()
            _currentSession.value = newCurrent.copy(isActive = true)
        }
    }

    /**
     * Add message to current session
     */
    fun addMessageToCurrentSession(message: ChatMessage) {
        val current = _currentSession.value
        val updatedMessages = current.messages + message
        val updatedSession = current.copy(messages = updatedMessages)

        // Update session list
        _sessions.value = _sessions.value.map {
            if (it.id == current.id) updatedSession else it
        }
        _currentSession.value = updatedSession
    }

    /**
     * Replace all messages in current session (for syncing from backend)
     */
    fun replaceCurrentSessionMessages(messages: List<ChatMessage>) {
        val current = _currentSession.value

        // Merge with current local messages and dedupe by semantic identity
        val merged = (current.messages + messages)
            .distinctBy { Triple(it.isUser, it.status, it.content.trim()) }

        val updatedSession = current.copy(messages = merged)

        // Update session list
        _sessions.value = _sessions.value.map {
            if (it.id == current.id) updatedSession else it
        }
        _currentSession.value = updatedSession
    }

    /**
     * Remove message from current session
     */
    fun removeMessageFromCurrentSession(messageId: String) {
        val current = _currentSession.value
        val updatedMessages = current.messages.filter { it.id != messageId }
        val updatedSession = current.copy(messages = updatedMessages)

        // Update session list
        _sessions.value = _sessions.value.map {
            if (it.id == current.id) updatedSession else it
        }
        _currentSession.value = updatedSession
    }

    /**
     * Update current session title
     */
    fun updateCurrentSessionTitle(title: String) {
        val current = _currentSession.value
        val updatedSession = current.copy(title = title)

        _sessions.value = _sessions.value.map {
            if (it.id == current.id) updatedSession else it
        }
        _currentSession.value = updatedSession
    }

    /**
     * Auto-generate current session title (based on first user message)
     */
    fun autoGenerateCurrentSessionTitle() {
        val current = _currentSession.value
        val generatedTitle = current.generateTitle()
        if (generatedTitle != current.title) {
            updateCurrentSessionTitle(generatedTitle)
        }
    }

    /**
     * Clear current session messages
     */
    fun clearCurrentSession() {
        val current = _currentSession.value
        val updatedSession = current.copy(
            messages = listOf(
                ChatMessage(
                    content = "聊天记录已清空。有什么可以帮到你的吗？",
                    isUser = false
                )
            )
        )

        _sessions.value = _sessions.value.map {
            if (it.id == current.id) updatedSession else it
        }
        _currentSession.value = updatedSession
    }

    /**
     * Get all sessions
     */
    fun getAllSessions(): List<Session> = _sessions.value

    /**
     * Get session count
     */
    fun getSessionCount(): Int = _sessions.value.size
}
