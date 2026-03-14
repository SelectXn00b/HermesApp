/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: session persistence.
 */
package com.xiaomo.androidforclaw.session

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * JSONL Session 存储
 * 对齐 OpenClaw 的 agents/main/sessions/ 架构
 *
 * JSONL 格式:
 * - 每条消息一行 JSON
 * - 增量追加，不重写整个文件
 * - 易于解析和流式读取
 */
class JsonlSessionStorage(private val context: Context) {

    companion object {
        private const val TAG = "JsonlSessionStorage"

        // Align with OpenClaw: agents/main/sessions/
        private const val SESSIONS_DIR = "/sdcard/.androidforclaw/agents/main/sessions"
        private const val SESSIONS_INDEX_FILE = "$SESSIONS_DIR/sessions.json"
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    init {
        ensureDirectoryExists()
    }

    /**
     * 创建新会话
     */
    fun createSession(title: String = "New Session"): String {
        val sessionId = UUID.randomUUID().toString()
        val now = Instant.now().toString()

        // Create session file
        val sessionFile = File(SESSIONS_DIR, "$sessionId.jsonl")
        sessionFile.createNewFile()

        // Update index
        val index = loadSessionsIndex().toMutableMap()
        index[sessionId] = SessionMetadata(
            title = title,
            createdAt = now,
            lastMessageAt = now,
            messageCount = 0
        )
        saveSessionsIndex(index)

        Log.i(TAG, "创建新会话: $sessionId")
        return sessionId
    }

    /**
     * 追加消息到会话 (JSONL 格式)
     */
    fun appendMessage(sessionId: String, message: SessionMessage) {
        val sessionFile = File(SESSIONS_DIR, "$sessionId.jsonl")
        if (!sessionFile.exists()) {
            Log.e(TAG, "会话文件不存在: $sessionId")
            return
        }

        // Append one line of JSON
        val jsonLine = gson.toJson(message)
        sessionFile.appendText("$jsonLine\n")

        // Update index
        updateSessionMetadata(sessionId) { metadata ->
            metadata.copy(
                lastMessageAt = Instant.now().toString(),
                messageCount = metadata.messageCount + 1
            )
        }

        Log.d(TAG, "追加消息到会话 $sessionId: ${message.role}")
    }

    /**
     * 读取会话所有消息
     */
    fun loadSession(sessionId: String): List<SessionMessage> {
        val sessionFile = File(SESSIONS_DIR, "$sessionId.jsonl")
        if (!sessionFile.exists()) {
            Log.w(TAG, "会话文件不存在: $sessionId")
            return emptyList()
        }

        return try {
            sessionFile.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        gson.fromJson(line, SessionMessage::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "解析消息失败: $line", e)
                        null
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "加载会话失败: $sessionId", e)
            emptyList()
        }
    }

    /**
     * 获取所有会话列表
     */
    fun listSessions(): Map<String, SessionMetadata> {
        return loadSessionsIndex()
    }

    /**
     * 获取会话元数据
     */
    fun getSessionMetadata(sessionId: String): SessionMetadata? {
        return loadSessionsIndex()[sessionId]
    }

    /**
     * 更新会话标题
     */
    fun updateSessionTitle(sessionId: String, newTitle: String) {
        updateSessionMetadata(sessionId) { metadata ->
            metadata.copy(title = newTitle)
        }
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String): Boolean {
        val sessionFile = File(SESSIONS_DIR, "$sessionId.jsonl")
        val deleted = sessionFile.delete()

        if (deleted) {
            // Remove from index
            val index = loadSessionsIndex().toMutableMap()
            index.remove(sessionId)
            saveSessionsIndex(index)
            Log.i(TAG, "删除会话: $sessionId")
        }

        return deleted
    }

    /**
     * 清空会话（保留文件但清空内容）
     */
    fun clearSession(sessionId: String) {
        val sessionFile = File(SESSIONS_DIR, "$sessionId.jsonl")
        if (sessionFile.exists()) {
            sessionFile.writeText("")
            updateSessionMetadata(sessionId) { metadata ->
                metadata.copy(
                    messageCount = 0,
                    lastMessageAt = Instant.now().toString()
                )
            }
            Log.i(TAG, "清空会话: $sessionId")
        }
    }

    /**
     * 导出会话为 JSONL 文件
     */
    fun exportSession(sessionId: String, outputPath: String): Boolean {
        val sessionFile = File(SESSIONS_DIR, "$sessionId.jsonl")
        val outputFile = File(outputPath)

        return try {
            sessionFile.copyTo(outputFile, overwrite = true)
            Log.i(TAG, "导出会话 $sessionId 到 $outputPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导出会话失败", e)
            false
        }
    }

    /**
     * 导入 JSONL 会话文件
     */
    fun importSession(inputPath: String, title: String? = null): String? {
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            Log.e(TAG, "导入文件不存在: $inputPath")
            return null
        }

        val sessionId = UUID.randomUUID().toString()
        val sessionFile = File(SESSIONS_DIR, "$sessionId.jsonl")

        return try {
            inputFile.copyTo(sessionFile)

            // Count messages
            val messageCount = sessionFile.readLines().count { it.isNotBlank() }

            // Create index
            val index = loadSessionsIndex().toMutableMap()
            index[sessionId] = SessionMetadata(
                title = title ?: "Imported Session",
                createdAt = Instant.now().toString(),
                lastMessageAt = Instant.now().toString(),
                messageCount = messageCount
            )
            saveSessionsIndex(index)

            Log.i(TAG, "导入会话成功: $sessionId")
            sessionId
        } catch (e: Exception) {
            Log.e(TAG, "导入会话失败", e)
            null
        }
    }

    /**
     * 获取会话统计信息
     */
    fun getSessionStats(sessionId: String): SessionStats? {
        val messages = loadSession(sessionId)
        if (messages.isEmpty()) return null

        val userMessages = messages.count { it.role == "user" }
        val assistantMessages = messages.count { it.role == "assistant" }
        val systemMessages = messages.count { it.role == "system" }

        val firstMessage = messages.firstOrNull()
        val lastMessage = messages.lastOrNull()

        return SessionStats(
            sessionId = sessionId,
            totalMessages = messages.size,
            userMessages = userMessages,
            assistantMessages = assistantMessages,
            systemMessages = systemMessages,
            firstMessageTime = firstMessage?.timestamp,
            lastMessageTime = lastMessage?.timestamp
        )
    }

    // ==================== Private methods ====================

    private fun ensureDirectoryExists() {
        val dir = File(SESSIONS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
            Log.d(TAG, "创建 sessions 目录: $SESSIONS_DIR")
        }
    }

    /**
     * 加载 sessions.json 索引
     */
    private fun loadSessionsIndex(): Map<String, SessionMetadata> {
        val indexFile = File(SESSIONS_INDEX_FILE)
        if (!indexFile.exists()) {
            return emptyMap()
        }

        return try {
            val json = indexFile.readText()
            val wrapper = gson.fromJson(json, SessionsIndexWrapper::class.java)
            wrapper.sessions
        } catch (e: Exception) {
            Log.e(TAG, "加载 sessions.json 失败", e)
            emptyMap()
        }
    }

    /**
     * 保存 sessions.json 索引
     */
    private fun saveSessionsIndex(sessions: Map<String, SessionMetadata>) {
        val indexFile = File(SESSIONS_INDEX_FILE)

        try {
            val wrapper = SessionsIndexWrapper(sessions)
            val json = gson.toJson(wrapper)
            indexFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "保存 sessions.json 失败", e)
        }
    }

    /**
     * 更新会话元数据
     */
    private fun updateSessionMetadata(
        sessionId: String,
        update: (SessionMetadata) -> SessionMetadata
    ) {
        val index = loadSessionsIndex().toMutableMap()
        val metadata = index[sessionId] ?: return

        index[sessionId] = update(metadata)
        saveSessionsIndex(index)
    }
}

/**
 * Session 消息 (JSONL 每行一条)
 */
data class SessionMessage(
    val role: String,              // "user" | "assistant" | "system" | "tool"
    val content: String,           // Message content
    val timestamp: String,         // ISO 8601 timestamp
    val name: String? = null,      // Optional: tool name or username
    val toolCallId: String? = null, // Optional: tool call ID
    val metadata: Map<String, Any?>? = null  // Optional: extra metadata
)

/**
 * Session 元数据 (sessions.json 中存储)
 */
data class SessionMetadata(
    val title: String,
    val createdAt: String,
    val lastMessageAt: String,
    val messageCount: Int
)

/**
 * sessions.json 包装器
 */
private data class SessionsIndexWrapper(
    val sessions: Map<String, SessionMetadata>
)

/**
 * Session 统计信息
 */
data class SessionStats(
    val sessionId: String,
    val totalMessages: Int,
    val userMessages: Int,
    val assistantMessages: Int,
    val systemMessages: Int,
    val firstMessageTime: String?,
    val lastMessageTime: String?
)
