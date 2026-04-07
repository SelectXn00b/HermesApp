package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/session-dirs.ts, command/session-store.ts
 *
 * AndroidForClaw adaptation: persist and restore agent sessions on Android.
 */


import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.infra.JsonFile
import com.xiaomo.androidforclaw.sessions.SessionLifecycleEvent
import com.xiaomo.androidforclaw.sessions.emitSessionLifecycleEvent
import com.xiaomo.androidforclaw.agent.memory.ContextCompressor
import com.xiaomo.androidforclaw.agent.memory.TokenEstimator
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.providers.LegacyToolCall
import com.xiaomo.androidforclaw.providers.LegacyFunction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Session Manager
 * Aligned with OpenClaw session management
 *
 * Storage format (OpenClaw Protocol):
 * - sessions.json: Metadata index {"agent:main:main": {"sessionId":"uuid", "updatedAt":1234567890, "sessionFile":"/path/to/uuid.jsonl", ...}}
 * - {sessionId}.jsonl: Message history (JSONL, one event per line)
 *
 * Responsibilities:
 * 1. Manage conversation history
 * 2. Persist session data (JSONL format)
 * 3. Auto context compression
 * 4. Token budget management
 * 5. Provide session create, get, save, clear functions
 */
class SessionManager(
    private val workspace: File,
    private val contextCompressor: ContextCompressor? = null
) {
    companion object {
        private const val TAG = "SessionManager"
        private const val SESSIONS_DIR = "sessions"
        private const val SESSIONS_INDEX = "sessions.json"
        private const val AUTO_PRUNE_DAYS = 30        // Auto clean sessions older than 30 days

        @JvmStatic
        internal fun ensureSessionFileParentExists(sessionFile: File) {
            sessionFile.parentFile?.mkdirs()
        }
    }

    private val gson: Gson = GsonBuilder().create()  // No pretty printing for JSONL
    private val gsonPretty: Gson = GsonBuilder().setPrettyPrinting().create()  // For sessions.json index

    private val sessionsDir: File = File(workspace, SESSIONS_DIR).apply {
        if (!exists()) {
            mkdirs()
            Log.d(TAG, "Created sessions directory: $absolutePath")
        }
    }

    private val indexFile: File = File(sessionsDir, SESSIONS_INDEX)

    // In-memory cache
    private val sessions = mutableMapOf<String, Session>()
    private val sessionIndex = mutableMapOf<String, SessionMetadata>()

    // Session write lock — prevents concurrent writes corrupting JSONL files
    // Aligned with OpenClaw's acquireSessionWriteLock
    private val sessionWriteLock = ReentrantReadWriteLock()

    init {
        loadIndex()
    }

    /**
     * Get or create session
     */
    fun getOrCreate(sessionKey: String): Session {
        return sessions.getOrPut(sessionKey) {
            Log.d(TAG, "Creating new session: $sessionKey")
            loadSession(sessionKey) ?: createNewSession(sessionKey)
        }
    }

    /**
     * Get session (return null if doesn't exist)
     */
    fun get(sessionKey: String): Session? {
        return sessions[sessionKey] ?: loadSession(sessionKey)
    }

    /**
     * Save session (with write lock — aligned with OpenClaw acquireSessionWriteLock)
     */
    fun save(session: Session) {
        sessionWriteLock.write {
            val nowMs = System.currentTimeMillis()
            session.updatedAt = currentTimestamp()
            sessions[session.key] = session

            // Persist to JSONL file
            try {
                saveSessionMessages(session)

                // Update index
                val metadata = sessionIndex.getOrPut(session.key) {
                    SessionMetadata(
                        sessionId = session.sessionId,
                        updatedAt = nowMs,
                        sessionFile = getSessionJSONLFile(session.sessionId).absolutePath,
                        compactionCount = session.compactionCount
                    )
                }
                metadata.updatedAt = nowMs
                metadata.compactionCount = session.compactionCount
                saveIndex()

                Log.d(TAG, "Session saved: ${session.key}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save session: ${session.key}", e)
            }
        }
    }

    /**
     * Clear session
     */
    fun clear(sessionKey: String) {
        sessionWriteLock.write {
            // Try to delete JSONL file from in-memory cache first
            val session = sessions.remove(sessionKey)
            if (session != null) {
                getSessionJSONLFile(session.sessionId).delete()
            }

            // Also check index (session may not be in memory cache after restart)
            val metadata = sessionIndex.remove(sessionKey)
            if (metadata != null) {
                if (session == null) {
                    // Session wasn't in memory, delete JSONL via index metadata
                    getSessionJSONLFile(metadata.sessionId).delete()
                }
                saveIndex()
            }

            Log.d(TAG, "Session cleared: $sessionKey")
        }

        // Notify session lifecycle listeners (sessions module)
        emitSessionLifecycleEvent(SessionLifecycleEvent(
            sessionKey = sessionKey,
            reason = "destroyed"
        ))

        // Opportunistic cleanup: remove orphan JSONL files not referenced by any index entry
        cleanOrphanJsonlFiles()

        // Clean up agentloop session log files
        cleanSessionLogs()
    }

    /**
     * Clean up agentloop session log files from workspace/logs/
     */
    fun cleanSessionLogs() {
        try {
            val logDir = File(workspace, "logs")
            if (!logDir.exists()) return
            val logFiles = logDir.listFiles { file -> file.extension == "log" } ?: return
            var cleaned = 0
            for (file in logFiles) {
                if (file.delete()) {
                    cleaned++
                }
            }
            if (cleaned > 0) {
                Log.i(TAG, "🧹 Cleaned $cleaned session log file(s)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean session logs: ${e.message}")
        }
    }

    /**
     * Clean orphan JSONL files — files in sessions/ that have no corresponding entry in sessions.json.
     * Called opportunistically when a session is deleted.
     */
    fun cleanOrphanJsonlFiles() {
        try {
            val indexedSessionIds = sessionIndex.values.map { it.sessionId }.toSet()
            val jsonlFiles = sessionsDir.listFiles { file -> file.extension == "jsonl" } ?: return

            var cleaned = 0
            for (file in jsonlFiles) {
                val fileSessionId = file.nameWithoutExtension
                if (fileSessionId !in indexedSessionIds) {
                    file.delete()
                    cleaned++
                    Log.d(TAG, "🧹 Cleaned orphan JSONL: ${file.name}")
                }
            }

            if (cleaned > 0) {
                Log.i(TAG, "🧹 Cleaned $cleaned orphan JSONL file(s)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean orphan JSONL files: ${e.message}")
        }
    }

    /**
     * Clear all sessions
     */
    fun clearAll() {
        sessions.clear()
        sessionIndex.clear()
        sessionsDir.listFiles()?.forEach {
            if (it.extension == "jsonl") {
                it.delete()
            }
        }
        indexFile.delete()
        Log.d(TAG, "All sessions cleared")

        // Clean up agentloop session log files
        cleanSessionLogs()
    }

    /**
     * Get all session keys (only return new format sessions)
     */
    fun getAllKeys(): List<String> {
        loadIndex()
        // Only return sessions from index (new format), ignore old .json files
        return sessionIndex.keys.toList()
    }

    /**
     * Check and auto compress session
     *
     * @param session Session
     * @return Whether compression was performed
     */
    suspend fun compressIfNeeded(session: Session): Boolean = withContext(Dispatchers.IO) {
        if (contextCompressor == null) {
            return@withContext false
        }

        try {
            // Check if compaction is needed
            if (!contextCompressor.needsCompaction(session.messages)) {
                return@withContext false
            }

            Log.d(TAG, "Auto-compressing session: ${session.key} (${session.messages.size} messages, ${session.getTokenCount()} tokens)")

            // Perform compression
            val compressedMessages = contextCompressor.compress(session.messages)

            // Update session
            session.messages.clear()
            session.messages.addAll(compressedMessages)
            session.markCompacted()

            // Save session
            save(session)

            Log.d(TAG, "Session compressed: ${session.key} → ${session.messages.size} messages, ${session.getTokenCount()} tokens (compaction #${session.compactionCount})")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress session: ${session.key}", e)
            false
        }
    }

    /**
     * Auto clean old sessions
     *
     * @param days Clean sessions older than this many days
     */
    suspend fun pruneOldSessions(days: Int = AUTO_PRUNE_DAYS): Unit = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

            var prunedCount = 0

            getAllKeys().forEach { key ->
                val session = loadSession(key)
                if (session != null) {
                    try {
                        val updatedDate = dateFormat.parse(session.updatedAt)
                        if (updatedDate != null && updatedDate.time < cutoffTime) {
                            clear(key)
                            prunedCount++
                            Log.d(TAG, "Pruned old session: $key (last updated: ${session.updatedAt})")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse date for session: $key", e)
                    }
                }
            }

            if (prunedCount > 0) {
                Log.d(TAG, "Pruned $prunedCount old sessions (older than $days days)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune old sessions", e)
        }
    }

    /**
     * Run full maintenance cycle (OpenClaw store-maintenance.ts + disk-budget.ts).
     * Prune stale entries, cap count, rotate index file, enforce disk budget.
     */
    suspend fun runMaintenance(
        activeSessionKey: String? = null,
        maxAgeDays: Int = AUTO_PRUNE_DAYS,
        maxEntries: Int = 500,
        maxDiskBytes: Long = 100_000_000L,
        highWaterRatio: Float = 0.8f
    ) = withContext(Dispatchers.IO) {
        try {
            val maxAgeMs = maxAgeDays.toLong() * 24 * 60 * 60 * 1000

            // 1. Prune stale entries
            SessionStoreMaintenance.pruneStaleEntries(sessionIndex, sessionsDir, maxAgeMs, activeSessionKey)

            // 2. Cap entry count
            SessionStoreMaintenance.capEntryCount(sessionIndex, sessionsDir, maxEntries, activeSessionKey)

            // 3. Rotate index file if too large
            SessionStoreMaintenance.rotateSessionFile(indexFile)

            // 4. Enforce disk budget
            val highWaterBytes = (maxDiskBytes * highWaterRatio).toLong()
            SessionDiskBudget.enforceSessionDiskBudget(
                sessionsDir, sessionIndex, activeSessionKey, maxDiskBytes, highWaterBytes
            )

            // 5. Persist updated index
            saveIndex()
        } catch (e: Exception) {
            Log.e(TAG, "Session maintenance failed", e)
        }
    }

    // ================ Private Helpers ================

    /**
     * Create new session
     */
    private fun createNewSession(sessionKey: String): Session {
        val sessionId = UUID.randomUUID().toString()
        val nowMs = System.currentTimeMillis()
        val timestamp = currentTimestamp()

        val session = Session(
            key = sessionKey,
            sessionId = sessionId,
            messages = mutableListOf(),
            createdAt = timestamp,
            updatedAt = timestamp
        )

        // 写入 JSONL header
        val jsonlFile = getSessionJSONLFile(sessionId)
        ensureSessionFileParentExists(jsonlFile)
        FileOutputStream(jsonlFile, false).use { out ->
            val header = mapOf(
                "type" to "session",
                "version" to 3,
                "id" to sessionId,
                "timestamp" to timestamp,
                "cwd" to workspace.absolutePath
            )
            out.write((gson.toJson(header) + "\n").toByteArray())
        }

        // Update index
        sessionIndex[sessionKey] = SessionMetadata(
            sessionId = sessionId,
            updatedAt = nowMs,
            sessionFile = jsonlFile.absolutePath,
            compactionCount = 0
        )
        saveIndex()

        // Notify session lifecycle listeners (sessions module)
        emitSessionLifecycleEvent(SessionLifecycleEvent(
            sessionKey = sessionKey,
            reason = "created"
        ))

        return session
    }

    /**
     * Load index file
     */
    private fun loadIndex() {
        try {
            val json = JsonFile.loadJsonFile(indexFile) ?: return
            val jsonObject = JsonParser.parseString(json).asJsonObject

            sessionIndex.clear()
            for ((key, value) in jsonObject.entrySet()) {
                val obj = value.asJsonObject
                sessionIndex[key] = SessionMetadata(
                    sessionId = obj.get("sessionId").asString,
                    updatedAt = obj.get("updatedAt").asLong,
                    sessionFile = obj.get("sessionFile").asString,
                    compactionCount = obj.get("compactionCount")?.asInt ?: 0
                )
            }

            Log.d(TAG, "Index loaded: ${sessionIndex.size} sessions")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load index", e)
        }
    }

    /**
     * Save index file
     */
    private fun saveIndex() {
        try {
            val jsonObject = JsonObject()
            for ((key, metadata) in sessionIndex) {
                val obj = JsonObject()
                obj.addProperty("sessionId", metadata.sessionId)
                obj.addProperty("updatedAt", metadata.updatedAt)
                obj.addProperty("sessionFile", metadata.sessionFile)
                obj.addProperty("compactionCount", metadata.compactionCount)
                jsonObject.add(key, obj)
            }

            Log.d(TAG, "💾 Saving index to: ${indexFile.absolutePath}")
            JsonFile.saveJsonFile(indexFile, gsonPretty.toJson(jsonObject))
            Log.d(TAG, "✅ Index saved: ${sessionIndex.size} sessions")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save index: ${e.message}", e)
        }
    }

    /**
     * Load session (with JSONL repair — aligned with OpenClaw repairSessionFileIfNeeded)
     */
    private fun loadSession(sessionKey: String): Session? {
        val metadata = sessionIndex[sessionKey] ?: return null
        val jsonlFile = getSessionJSONLFile(metadata.sessionId)

        if (!jsonlFile.exists()) {
            Log.w(TAG, "Session JSONL file not found: ${metadata.sessionId}")
            return null
        }

        return try {
            val messages = mutableListOf<LegacyMessage>()
            var createdAt = currentTimestamp()
            var updatedAt = currentTimestamp()
            var droppedLines = 0

            jsonlFile.forEachLine { line ->
                if (line.isBlank()) return@forEachLine

                val event = try {
                    JsonParser.parseString(line).asJsonObject
                } catch (e: Exception) {
                    droppedLines++
                    Log.w(TAG, "Dropped malformed JSONL line: ${line.take(80)}")
                    return@forEachLine
                }
                val type = event.get("type")?.asString ?: return@forEachLine

                when (type) {
                    "session" -> {
                        createdAt = event.get("timestamp")?.asString ?: createdAt
                    }
                    "message" -> {
                        val role = event.get("role")?.asString ?: return@forEachLine
                        val content = event.get("content")?.asString ?: ""
                        val name = event.get("name")?.asString
                        val toolCallId = event.get("tool_call_id")?.asString

                        // Parse tool_calls array (for assistant messages)
                        val toolCalls = if (event.has("tool_calls") && !event.get("tool_calls").isJsonNull) {
                            try {
                                val arr = event.getAsJsonArray("tool_calls")
                                arr.map { tc ->
                                    val obj = tc.asJsonObject
                                    val fnObj = obj.getAsJsonObject("function")
                                    LegacyToolCall(
                                        id = obj.get("id")?.asString ?: "",
                                        type = obj.get("type")?.asString ?: "function",
                                        function = LegacyFunction(
                                            name = fnObj.get("name")?.asString ?: "",
                                            arguments = fnObj.get("arguments")?.asString ?: "{}"
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse tool_calls: ${e.message}")
                                null
                            }
                        } else null

                        messages.add(LegacyMessage(
                            role = role,
                            content = content,
                            name = name,
                            toolCallId = toolCallId,
                            toolCalls = toolCalls
                        ))
                    }
                }
            }

            // Repair report (aligned with OpenClaw session-file-repair.ts)
            if (droppedLines > 0) {
                Log.w(TAG, "⚠️ Session file repaired: dropped $droppedLines malformed lines (${jsonlFile.name})")
            }

            val session = Session(
                key = sessionKey,
                sessionId = metadata.sessionId,
                messages = messages,
                createdAt = createdAt,
                updatedAt = updatedAt,
                compactionCount = metadata.compactionCount
            )

            Log.d(TAG, "Session loaded: $sessionKey (${messages.size} messages)")
            session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session: $sessionKey", e)
            null
        }
    }

    /**
     * Save session messages to JSONL — records full tool blocks
     * Aligned with OpenClaw's session JSONL format:
     * - assistant messages include tool_calls array
     * - tool messages include tool_call_id and name
     * - thinking content preserved as metadata
     */
    private fun saveSessionMessages(session: Session) {
        val jsonlFile = getSessionJSONLFile(session.sessionId)
        ensureSessionFileParentExists(jsonlFile)
        val tmpFile = File(jsonlFile.parentFile, "${jsonlFile.name}.tmp-${System.currentTimeMillis()}")

        try {
            Log.d(TAG, "💾 Saving session messages to: ${jsonlFile.absolutePath}")

            // Write to temp file first, then atomic rename (prevents corruption)
            FileOutputStream(tmpFile, false).use { out ->
                // 1. Session header
                val header = mapOf(
                    "type" to "session",
                    "version" to 3,
                    "id" to session.sessionId,
                    "timestamp" to session.createdAt,
                    "cwd" to workspace.absolutePath
                )
                out.write((gson.toJson(header) + "\n").toByteArray())

                // 2. Messages — full tool block recording
                for (msg in session.messages) {
                    val event = JsonObject()
                    event.addProperty("type", "message")
                    event.addProperty("id", UUID.randomUUID().toString())
                    event.addProperty("role", msg.role)

                    // Content (can be string or complex)
                    when (val content = msg.content) {
                        is String -> event.addProperty("content", content)
                        else -> event.addProperty("content", content?.toString() ?: "")
                    }

                    // Tool call ID (for tool role messages)
                    msg.toolCallId?.let { event.addProperty("tool_call_id", it) }

                    // Tool name (for tool role messages)
                    msg.name?.let { event.addProperty("name", it) }

                    // Tool calls array (for assistant messages with tool invocations)
                    msg.toolCalls?.let { toolCalls ->
                        val tcArray = JsonArray()
                        for (tc in toolCalls) {
                            val tcObj = JsonObject()
                            tcObj.addProperty("id", tc.id)
                            tcObj.addProperty("type", tc.type)
                            val fnObj = JsonObject()
                            fnObj.addProperty("name", tc.function.name)
                            fnObj.addProperty("arguments", tc.function.arguments)
                            tcObj.add("function", fnObj)
                            tcArray.add(tcObj)
                        }
                        event.add("tool_calls", tcArray)
                    }

                    event.addProperty("timestamp", currentTimestamp())
                    out.write((gson.toJson(event) + "\n").toByteArray())
                }
            }

            // Atomic rename
            if (jsonlFile.exists()) {
                jsonlFile.delete()
            }
            tmpFile.renameTo(jsonlFile)

            Log.d(TAG, "✅ Session messages saved: ${session.messages.size} messages to ${jsonlFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save session messages: ${e.message}", e)
            // Clean up temp file on failure
            tmpFile.delete()
        }
    }

    private fun getSessionJSONLFile(sessionId: String): File {
        return File(sessionsDir, "$sessionId.jsonl")
    }

    private fun currentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .format(Date())
    }
}

/**
 * Session - Session data
 */
data class Session(
    val key: String,
    val sessionId: String,                     // UUID (aligned with OpenClaw)
    var messages: MutableList<LegacyMessage>,
    var createdAt: String,
    var updatedAt: String,
    var metadata: MutableMap<String, Any?> = mutableMapOf(),
    var compactionCount: Int = 0,              // Compaction count
    var totalTokens: Int = 0,                  // Total token count
    var totalTokensFresh: Boolean = false      // Whether token data is fresh
) {
    /** Per-message creation timestamps (epoch ms), parallel to messages list */
    val messageTimestamps: MutableList<Long> = mutableListOf()

    /**
     * Add message
     */
    fun addMessage(message: LegacyMessage) {
        messages.add(message)
        messageTimestamps.add(System.currentTimeMillis())
        totalTokensFresh = false  // Mark token count as stale
    }

    /**
     * Get recent N messages
     */
    fun getRecentMessages(count: Int): List<LegacyMessage> {
        return if (messages.size <= count) {
            messages.toList()
        } else {
            messages.takeLast(count)
        }
    }

    /**
     * Clear messages
     */
    fun clearMessages() {
        messages.clear()
        messageTimestamps.clear()
        totalTokens = 0
        totalTokensFresh = true
    }

    /**
     * Get message count
     */
    fun messageCount(): Int {
        return messages.size
    }

    /**
     * Update token count
     */
    fun updateTokenCount() {
        totalTokens = TokenEstimator.estimateMessagesTokens(messages)
        totalTokensFresh = true
    }

    /**
     * Get token count (recalculate if not fresh)
     */
    fun getTokenCount(): Int {
        if (!totalTokensFresh) {
            updateTokenCount()
        }
        return totalTokens
    }

    /**
     * Mark as compacted
     */
    fun markCompacted() {
        compactionCount++
        totalTokensFresh = false
    }
}

/**
 * SessionMetadata - Session metadata (aligned with OpenClaw sessions.json)
 */
data class SessionMetadata(
    val sessionId: String,
    var updatedAt: Long,
    val sessionFile: String,
    var compactionCount: Int = 0
)
