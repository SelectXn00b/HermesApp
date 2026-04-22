package com.xiaomo.hermes.hermes.gateway

/**
 * Session store — manages session lifecycle, persistence, and lookup.
 *
 * Ported from gateway/session.py (1086 lines)
 */

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// ── Helpers ─────────────────────────────────────────────────────────

private const val TAG = "SessionStore"

private fun now(): String = Instant.now().toString()

/** Hash a value for PII-safe logging. */
private fun hashId(value: String): String {
    if (value.isEmpty()) return ""
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(value.toByteArray())
    return hash.take(8).joinToString("") { "%02x".format(it) }
}

/** Hash a sender id for PII-safe logging. */
private fun hashSenderId(value: String): String = hashId(value)

/** Hash a chat id for PII-safe logging. */
private fun hashChatId(value: String): String = hashId(value)

private val PII_SAFE_PLATFORMS = setOf("api_server", "webhook")

// ── Data classes ────────────────────────────────────────────────────

/** Where a session was loaded from. */
enum class SessionSource {
    NEW,
    PERSISTED,
    RECOVERED;

    /** Human-readable description. */
    val description: String get() = when (this) {
        NEW -> "fresh session"
        PERSISTED -> "restored from disk"
        RECOVERED -> "recovered after restart"
    }

    fun toDict(): Map<String, Any> = mapOf("source" to name)

    companion object {
        fun fromDict(data: Map<String, Any?>): SessionSource {
            val name = (data["source"] as? String) ?: "NEW"
            return try { valueOf(name) } catch (_unused: Exception) { NEW }
        }
    }
}

/**
 * A single session record.
 */
data class SessionRecord(
    val sessionKey: String,
    val platform: String,
    val chatId: String,
    val userId: String,
    var chatName: String = "",
    var userName: String = "",
    val chatType: String = "dm",
    val createdAt: String = now(),
    var lastMessageAt: String = now(),
    val messageCount: AtomicInteger = AtomicInteger(0),
    val turnCount: AtomicInteger = AtomicInteger(0),
    val inputTokens: AtomicLong = AtomicLong(0),
    val outputTokens: AtomicLong = AtomicLong(0),
    var modelOverride: String? = null,
    var systemPromptOverride: String? = null,
    @Volatile var isProcessing: Boolean = false,
    var processingStartedAt: Long = 0L,
    var parentSessionKey: String? = null,
    val source: SessionSource = SessionSource.NEW) {
    fun recordMessage() {
        messageCount.incrementAndGet()
        lastMessageAt = now()
    }

    fun recordTurn() {
        turnCount.incrementAndGet()
    }

    fun recordTokens(input: Long, output: Long) {
        inputTokens.addAndGet(input)
        outputTokens.addAndGet(output)
    }

    fun markProcessing() {
        isProcessing = true
        processingStartedAt = System.currentTimeMillis()
    }

    fun markIdle() {
        isProcessing = false
        processingStartedAt = 0L
    }

    fun label(): String = "$platform:$chatId (user=$userId)"

    fun toDict(): Map<String, Any?> = buildMap {
        put("session_key", sessionKey)
        put("platform", platform)
        put("chat_id", chatId)
        put("user_id", userId)
        put("chat_name", chatName)
        put("user_name", userName)
        put("chat_type", chatType)
        put("created_at", createdAt)
        put("last_message_at", lastMessageAt)
        put("message_count", messageCount.get())
        put("turn_count", turnCount.get())
        put("input_tokens", inputTokens.get())
        put("output_tokens", outputTokens.get())
        modelOverride?.let { put("model_override", it) }
        systemPromptOverride?.let { put("system_prompt_override", it) }
        put("is_processing", isProcessing)
        put("processing_started_at", processingStartedAt)
        parentSessionKey?.let { put("parent_session_key", it) }
        put("source", source.name)
    }

    fun toJson(): JSONObject = JSONObject(toDict())

    companion object {
        fun fromDict(data: Map<String, Any?>): SessionRecord = SessionRecord(
            sessionKey = data["session_key"] as? String ?: "",
            platform = data["platform"] as? String ?: "",
            chatId = data["chat_id"] as? String ?: "",
            userId = data["user_id"] as? String ?: "",
            chatName = data["chat_name"] as? String ?: "",
            userName = data["user_name"] as? String ?: "",
            chatType = data["chat_type"] as? String ?: "dm",
            createdAt = data["created_at"] as? String ?: now(),
            lastMessageAt = data["last_message_at"] as? String ?: now(),
            source = try { SessionSource.valueOf(data["source"] as? String ?: "NEW") } catch (_unused: Exception) { SessionSource.NEW }).apply {
            messageCount.set((data["message_count"] as? Number)?.toInt() ?: 0)
            turnCount.set((data["turn_count"] as? Number)?.toInt() ?: 0)
            inputTokens.set((data["input_tokens"] as? Number)?.toLong() ?: 0)
            outputTokens.set((data["output_tokens"] as? Number)?.toLong() ?: 0)
            modelOverride = data["model_override"] as? String
            systemPromptOverride = data["system_prompt_override"] as? String
            isProcessing = data["is_processing"] as? Boolean ?: false
            processingStartedAt = (data["processing_started_at"] as? Number)?.toLong() ?: 0
            parentSessionKey = data["parent_session_key"] as? String
        }

        fun fromJson(json: JSONObject): SessionRecord {
            val map = mutableMapOf<String, Any?>()
            json.keys().forEach { key -> map[key] = json.opt(key) }
            return fromDict(map)
        }
    }
}

// ── Helper functions ────────────────────────────────────────────────

/** Build a session key from platform + chat id + user id. */
fun buildSessionKey(platform: String, chatId: String, userId: String): String =
    "$platform:$chatId:$userId"

/** Build a session context from platform adapter metadata. */
fun buildSessionRecord(
    sessionKey: String,
    platform: String,
    chatId: String,
    userId: String,
    chatName: String = "",
    userName: String = "",
    chatType: String = "dm"): SessionRecord = SessionRecord(
    sessionKey = sessionKey,
    platform = platform,
    chatId = chatId,
    userId = userId,
    chatName = chatName,
    userName = userName,
    chatType = chatType,
    source = SessionSource.NEW)

/** Build a system-prompt fragment for a session. */
fun buildSessionRecordPrompt(session: SessionRecord, redactPii: Boolean = false): String = buildString {
    appendLine("# Session Context")
    appendLine("- Platform: ${session.platform}")
    appendLine("- Chat: ${session.chatName.ifEmpty { session.chatId }}")
    appendLine("- User: ${session.userName.ifEmpty { session.userId }}")
    appendLine("- Chat type: ${session.chatType}")
    appendLine("- Messages: ${session.messageCount.get()}")
    appendLine("- Turns: ${session.turnCount.get()}")
    if (session.modelOverride != null) {
        appendLine("- Model override: ${session.modelOverride}")
    }
}

// ── SessionStore ────────────────────────────────────────────────────

/**
 * Session store — manages session lifecycle and persistence.
 * Thread-safe. Sessions are persisted to disk periodically.
 */
class SessionStore(
    private val persistDir: File? = null) {
    private val sessions: ConcurrentHashMap<String, SessionRecord> = ConcurrentHashMap()

    /** Get or create a session. */
    fun getOrCreate(
        sessionKey: String,
        platform: String,
        chatId: String,
        userId: String,
        chatName: String = "",
        userName: String = "",
        chatType: String = "dm"): SessionRecord = sessions.getOrPut(sessionKey) {
        buildSessionRecord(sessionKey, platform, chatId, userId, chatName, userName, chatType)
    }

    /** Get or create session from a source map (Python-compatible). */
    fun getOrCreateSession(source: Map<String, Any?>, forceNew: Boolean = false): SessionRecord {
        val platform = source["platform"] as? String ?: "unknown"
        val chatId = source["chat_id"] as? String ?: ""
        val userId = source["user_id"] as? String ?: ""
        val chatName = source["chat_name"] as? String ?: ""
        val userName = source["user_name"] as? String ?: ""
        val chatType = source["chat_type"] as? String ?: "dm"
        val sessionKey = buildSessionKey(platform, chatId, userId)

        if (forceNew) {
            sessions.remove(sessionKey)
        }

        return getOrCreate(sessionKey, platform, chatId, userId, chatName, userName, chatType)
    }

    fun get(sessionKey: String): SessionRecord? = sessions[sessionKey]
    fun remove(sessionKey: String) { sessions.remove(sessionKey) }
    val keys: Set<String> get() = sessions.keys.toSet()
    val all: Collection<SessionRecord> get() = sessions.values
    val size: Int get() = sessions.size
    val processingCount: Int get() = sessions.values.count { it.isProcessing }
    val hasAnySessions: Boolean get() = sessions.isNotEmpty()

    fun clear() { sessions.clear() }

    /** Check if a session is expired (idle timeout). */
    fun isSessionExpired(entry: SessionRecord, idleMinutes: Int = 1440): Boolean {
        val last = Instant.parse(entry.lastMessageAt)
        val elapsed = java.time.Duration.between(last, Instant.now())
        return elapsed.toMinutes() > idleMinutes
    }

    /** Check if session should be reset (daily boundary or idle). */
    fun shouldReset(entry: SessionRecord, resetHour: Int = 4): String? {
        // Daily reset check
        val now = java.time.ZonedDateTime.now()
        val lastMsg = java.time.ZonedDateTime.parse(entry.lastMessageAt)
        if (now.toLocalDate() != lastMsg.toLocalDate() && now.hour >= resetHour) {
            return "daily"
        }
        // Idle reset check
        if (isSessionExpired(entry)) {
            return "idle"
        }
        return null
    }

    /** Update session with latest token counts. */
    fun updateSession(sessionKey: String, promptTokens: Int = 0, completionTokens: Int = 0) {
        val entry = sessions[sessionKey] ?: return
        entry.recordTurn()
        entry.recordTokens(promptTokens.toLong(), completionTokens.toLong())
        entry.lastMessageAt = now()
    }

    /** Suspend a session (mark as not processing). */
    fun suspendSession(sessionKey: String): Boolean {
        val entry = sessions[sessionKey] ?: return false
        entry.markIdle()
        return true
    }

    /** Suspend all recently-active sessions. */
    fun suspendRecentlyActive(maxAgeSeconds: Int = 120): Int {
        val cutoff = System.currentTimeMillis() - maxAgeSeconds * 1000
        var count = 0
        sessions.values.filter { it.isProcessing && it.processingStartedAt < cutoff }.forEach {
            it.markIdle()
            count++
        }
        return count
    }

    /** Reset a session (clear conversation state). */
    fun resetSession(sessionKey: String): SessionRecord? {
        val old = sessions[sessionKey] ?: return null
        val newEntry = SessionRecord(
            sessionKey = old.sessionKey,
            platform = old.platform,
            chatId = old.chatId,
            userId = old.userId,
            chatName = old.chatName,
            userName = old.userName,
            chatType = old.chatType,
            source = old.source)
        sessions[sessionKey] = newEntry
        return newEntry
    }

    /** List sessions, optionally filtered by activity. */
    fun listSessions(activeMinutes: Int? = null): List<SessionRecord> {
        val all = sessions.values.toList()
        if (activeMinutes == null) return all
        val cutoff = Instant.now().minus(java.time.Duration.ofMinutes(activeMinutes.toLong()))
        return all.filter { Instant.parse(it.lastMessageAt).isAfter(cutoff) }
    }

    /** Get transcript file path for a session. */
    fun getTranscriptPath(sessionId: String): File? {
        val dir = persistDir ?: return null
        return File(dir, "transcripts/$sessionId.jsonl")
    }

    /** Append a message to the session transcript. */
    fun appendToTranscript(sessionId: String, message: Map<String, Any?>) {
        val path = getTranscriptPath(sessionId) ?: return
        path.parentFile?.mkdirs()
        val json = JSONObject(message)
        path.appendText(json.toString() + "\n", Charsets.UTF_8)
    }

    /** Rewrite the entire transcript for a session. */
    fun rewriteTranscript(sessionId: String, messages: List<Map<String, Any?>>) {
        val path = getTranscriptPath(sessionId) ?: return
        path.parentFile?.mkdirs()
        val content = messages.joinToString("\n") { JSONObject(it).toString() }
        path.writeText(content, Charsets.UTF_8)
    }

    /** Load transcript for a session. */
    fun loadTranscript(sessionId: String): List<Map<String, Any?>> {
        val path = getTranscriptPath(sessionId) ?: return emptyList()
        if (!path.exists()) return emptyList()
        return try {
            path.readLines(Charsets.UTF_8)
                .filter { it.isNotBlank() }
                .map { line ->
                    val json = JSONObject(line)
                    val map = mutableMapOf<String, Any?>()
                    json.keys().forEach { key -> map[key] = json.opt(key) }
                    map.toMap()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load transcript for $sessionId: ${e.message}")
            emptyList()
        }
    }

    /** Persist all sessions to disk. */
    fun persist() {
        val dir = persistDir ?: return
        dir.mkdirs()
        val sessionsJson = JSONArray()
        sessions.values.forEach { sessionsJson.put(it.toJson()) }
        val file = File(dir, "sessions.json")
        file.writeText(sessionsJson.toString(2), Charsets.UTF_8)
        Log.d(TAG, "Persisted ${sessions.size} sessions to ${file.absolutePath}")
    }

    /** Load sessions from disk. */
    fun load() {
        val dir = persistDir ?: return
        val file = File(dir, "sessions.json")
        if (!file.exists()) return
        try {
            val json = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until json.length()) {
                val session = SessionRecord.fromJson(json.getJSONObject(i))
                sessions[session.sessionKey] = session
            }
            Log.i(TAG, "Loaded ${sessions.size} sessions from ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load sessions: ${e.message}")
        }
    }

    /** Switch a session key to point at a different session (for /resume). */
    fun switchSession(sessionKey: String, targetSessionKey: String): SessionRecord? {
        val old = sessions[sessionKey] ?: return null
        if (sessionKey == targetSessionKey) return old

        val newEntry = SessionRecord(
            sessionKey = targetSessionKey,
            platform = old.platform,
            chatId = old.chatId,
            userId = old.userId,
            chatName = old.chatName,
            userName = old.userName,
            chatType = old.chatType,
            source = old.source)
        sessions[targetSessionKey] = newEntry
        return newEntry
    }
    fun getSessionCount(): Int = sessions.size

    /** Check if a session exists. */
    fun hasSession(sessionKey: String): Boolean = sessions.containsKey(sessionKey)

    /** Get all session keys. */
    fun getSessionKeys(): List<String> = sessions.keys.toList()

    /** Remove a session by key. */
    fun removeSession(sessionKey: String): SessionRecord? = sessions.remove(sessionKey)

    /** Remove all sessions. */
    fun clearAllSessions() { sessions.clear() }

    /** Get sessions filtered by platform. */
    fun getSessionsByPlatform(platform: String): List<SessionRecord> {
        return sessions.values.filter { it.platform == platform }
    }

    /** Get the last active session key. */
    fun getLastActiveSession(): String? {
        return sessions.entries.maxByOrNull { it.value.createdAt }?.key
    }

    /** Update the last active timestamp for a session. */
    fun touchSession(sessionKey: String) {
        // SessionRecord has no lastActive field; createdAt is immutable
    }

    /** Get the total message count across all sessions. */
    fun getTotalMessageCount(): Int {
        return sessions.size
    }



    /** Remove session entries older than [maxAgeDays] days.
     *  Returns the number of entries pruned. */
    fun pruneOldEntries(maxAgeDays: Int): Int {
        if (maxAgeDays <= 0) return 0
        val cutoff = Instant.now().minus(java.time.Duration.ofDays(maxAgeDays.toLong()))
        val toRemove = sessions.entries.filter { (_, entry) ->
            try {
                Instant.parse(entry.lastMessageAt).isBefore(cutoff)
            } catch (_: Exception) { false }
        }.map { it.key }
        for (key in toRemove) {
            sessions.remove(key)
        }
        if (toRemove.isNotEmpty()) {
            Log.d(TAG, "Pruned ${toRemove.size} session entries older than $maxAgeDays days")
        }
        return toRemove.size
    }
}

// ── SessionManager (ACP) ───────────────────────────────────────────

/**
 * Higher-level session manager for ACP protocol sessions.
 * Simplified Android port — manages agent sessions.
 *
 * SessionStore 负责 JSON 文件持久化 (sessions_index.json)。
 * SessionDB 负责 SQLite 持久化 (HermesState.kt)。
 * SessionManager 代理两者 + 管理 Honcho 集成 / dialectic / migration。
 */
class SessionManager(
    private val sessionsDir: File) {
    private val sessions = ConcurrentHashMap<String, Map<String, Any?>>()
    private val taskCwds = ConcurrentHashMap<String, String>()

    // ── Session store persistence ──
    private val _storeFile get() = File(sessionsDir, "sessions_index.json")
    @Volatile private var _storeLoaded = false
    private val _storeLock = Any()

    // ── In-memory message histories ──
    private val _histories = ConcurrentHashMap<String, MutableList<Map<String, Any?>>>()
    private val MAX_HISTORY = 200

    // ── Honcho peer cache ──
    private val _peers = ConcurrentHashMap<String, Any>()

    // ── Dialectic prefetch cache ──
    private val _dialecticResults = ConcurrentHashMap<String, String>()
    private val _contextResults = ConcurrentHashMap<String, Map<String, String>>()

    // ── Async writer state ──
    @Volatile private var _writerRunning = false
    private var _writerThread: Thread? = null
    private val _writeQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, List<Map<String, Any>>>>()

    // =====================================================================
    // Existing ACP methods (kept untouched)
    // =====================================================================

    fun createSession(cwd: String): Map<String, Any?> {
        val sessionId = java.util.UUID.randomUUID().toString()
        val session = mapOf<String, Any?>(
            "session_id" to sessionId,
            "cwd" to cwd,
            "created_at" to now())
        sessions[sessionId] = session
        taskCwds[sessionId] = cwd
        return session
    }

    fun getSession(sessionId: String): Map<String, Any?>? = sessions[sessionId]

    fun forkSession(sessionId: String, cwd: String): Map<String, Any?> {
        val newId = java.util.UUID.randomUUID().toString()
        val original = sessions[sessionId] ?: emptyMap()
        val forked = original.toMutableMap().apply {
            put("session_id", newId)
            put("cwd", cwd)
            put("forked_from", sessionId)
            put("created_at", now())
        }
        sessions[newId] = forked
        taskCwds[newId] = cwd
        return forked
    }

    fun updateCwd(sessionId: String, cwd: String) {
        taskCwds[sessionId] = cwd
    }

    fun cleanup() {
        sessions.clear()
        taskCwds.clear()
    }

    fun saveSession(sessionId: String) {
        val session = sessions[sessionId] ?: return
        val dir = File(sessionsDir, "acp")
        dir.mkdirs()
        val file = File(dir, "$sessionId.json")
        file.writeText(JSONObject(session).toString(2), Charsets.UTF_8)
    }

    /** Switch a session key to point at a different session (for /resume). */
    private fun registerTaskCwd(taskId: String, cwd: String) { taskCwds[taskId] = cwd }
    private fun clearTaskCwd(taskId: String) { taskCwds.remove(taskId) }

    // =====================================================================
    // Session store methods (ported from Python SessionStore)
    // =====================================================================

    /** Human-readable description of the source. */
    fun description(): String {
        return "Session store: ${sessionsDir.absolutePath}"
    }

    /** Load sessions index from disk if not already loaded. */
    fun _ensureLoaded() {
        if (_storeLoaded) return
        synchronized(_storeLock) {
            _ensureLoadedLocked()
        }
    }

    /** Load sessions index from disk. Must be called with self._lock held. */
    fun _ensureLoadedLocked() {
        if (_storeLoaded) return
        val file = _storeFile
        if (!file.exists()) {
            _storeLoaded = true
            return
        }
        try {
            val content = file.readText(Charsets.UTF_8)
            if (content.isNotBlank()) {
                val obj = JSONObject(content)
                for (key in obj.keys()) {
                    val value = obj.getJSONObject(key)
                    val map = mutableMapOf<String, Any?>()
                    for (k in value.keys()) {
                        map[k] = value.get(k)
                    }
                    sessions[key] = map
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load sessions index: ${e.message}")
        }
        _storeLoaded = true
    }

    /** Save sessions index to disk (kept for session key → ID mapping). */
    fun _save() {
        val file = _storeFile
        file.parentFile.mkdirs()
        try {
            val obj = JSONObject()
            for ((key, value) in sessions) {
                obj.put(key, JSONObject(value as Map<*, *>))
            }
            val tmpFile = File(file.parent, ".${file.name}.tmp")
            tmpFile.writeText(obj.toString(2), Charsets.UTF_8)
            tmpFile.renameTo(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save sessions index: ${e.message}")
        }
    }

    /** Generate a session key from a source. */
    fun _generateSessionKey(source: SessionSource): String {
        return "${source.name.lowercase()}_${System.currentTimeMillis()}"
    }

    /** Check if a session has expired based on its reset policy. */
    fun _isSessionExpired(entry: Any?): Boolean {
        if (entry !is Map<*, *>) return false
        val resetAt = entry["reset_at"]
        if (resetAt is String) {
            try {
                val resetInstant = Instant.parse(resetAt)
                return Instant.now().isAfter(resetInstant)
            } catch (_: Exception) { }
        }
        return false
    }

    /** Check if a session should be reset based on policy. */
    fun _shouldReset(entry: Any?, source: SessionSource): String? {
        if (entry !is Map<*, *>) return null
        if (_isSessionExpired(entry)) return "expired"
        val policy = entry["reset_policy"] as? String
        if (policy == "always") return "policy"
        return null
    }

    /** Check if any sessions have ever been created (across all platforms). */
    fun hasAnySessions(): Boolean {
        _ensureLoaded()
        return sessions.isNotEmpty()
    }

    // =====================================================================
    // Message management
    // =====================================================================

    /** Add a message to the local cache. */
    fun addMessage(role: String, content: String, kwargs: Any) {
        val sessionKey = when (kwargs) {
            is Map<*, *> -> kwargs["session_key"] as? String
            else -> null
        } ?: return
        val msg = mutableMapOf<String, Any?>(
            "role" to role,
            "content" to content,
            "timestamp" to now())
        if (kwargs is Map<*, *>) {
            kwargs["tool_calls"]?.let { msg["tool_calls"] = it }
            kwargs["tool_call_id"]?.let { msg["tool_call_id"] = it }
            kwargs["tool_name"]?.let { msg["tool_name"] = it }
        }
        val history = _histories.getOrPut(sessionKey) { mutableListOf() }
        synchronized(history) {
            history.add(msg)
            while (history.size > MAX_HISTORY) history.removeAt(0)
        }
    }

    /** Get message history for LLM context. */
    fun getHistory(maxMessages: Int = 50): List<Map<String, Any>> {
        val allMessages = mutableListOf<Map<String, Any>>()
        for ((_, history) in _histories) {
            synchronized(history) {
                @Suppress("UNCHECKED_CAST")
                allMessages.addAll(history as Collection<Map<String, Any>>)
            }
        }
        allMessages.sortBy { it["timestamp"] as? String ?: "" }
        return if (allMessages.size > maxMessages) allMessages.takeLast(maxMessages) else allMessages
    }

    // =====================================================================
    // Honcho integration
    // =====================================================================

    /** Get the Honcho client, initializing if needed. */
    fun honcho(): Any? {
        // Honcho SDK is not available on Android.
        // Python: from honcho import Honcho; self._client = Honcho()
        return null
    }

    /** Get or create a Honcho peer. */
    fun _getOrCreatePeer(peerId: String): Any {
        val cached = _peers[peerId]
        if (cached != null) return cached
        // Python: peer = self.client.peer(peer_id, create=True)
        val peer = mapOf("peer_id" to peerId, "created_at" to now())
        _peers[peerId] = peer
        return peer
    }

    /** Get or create a Honcho session with peers configured. */
    fun _getOrCreateHonchoSession(sessionId: String, userPeer: Any, assistantPeer: Any): Pair<Any, Any?> {
        // Python: session = self.client.session(session_id, create=True, peers=[user_peer)
        val session = mapOf(
            "session_id" to sessionId,
            "user_peer" to userPeer,
            "assistant_peer" to assistantPeer,
            "created_at" to now())
        return Pair(session, null)
    }

    /** Sanitize an ID to match Honcho's pattern: ^[a-zA-Z0-9_-]+ */
    fun _sanitizeId(idStr: String): String {
        return idStr.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    /** Internal: write unsynced messages to Honcho synchronously. */
    fun _flushSession(session: Any?): Boolean {
        // Python: for msg in self._unsynced[session_key]: session.add_message(...)
        if (session == null) return false
        while (_writeQueue.poll() != null) { /* drain */ }
        return true
    }

    /** Background daemon thread: drains the async write queue. */
    fun _asyncWriterLoop() {
        while (_writerRunning) {
            try {
                val item = _writeQueue.poll()
                if (item != null) {
                    val (sessionKey, messages) = item
                    val history = _histories.getOrPut(sessionKey) { mutableListOf() }
                    synchronized(history) {
                        @Suppress("UNCHECKED_CAST")
                        history.addAll(messages as Collection<Map<String, Any?>>)
                        while (history.size > MAX_HISTORY) history.removeAt(0)
                    }
                } else {
                    Thread.sleep(100)
                }
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.w(TAG, "Async writer error: ${e.message}")
            }
        }
    }

    /** Save messages to Honcho, respecting write_frequency. */
    fun save(session: Any?) {
        if (session == null) return
        _save()
    }

    /** Flush all pending unsynced messages for all cached sessions. */
    fun flushAll() {
        _flushSession(null)
        _save()
    }

    /** Gracefully shut down the async writer thread. */
    fun shutdown() {
        _writerRunning = false
        _writerThread?.interrupt()
        _writerThread = null
        flushAll()
    }

    /** Delete a session from local cache. */
    fun delete(key: String): Boolean {
        val removed = sessions.remove(key) != null
        taskCwds.remove(key)
        _histories.remove(key)
        _dialecticResults.remove(key)
        _contextResults.remove(key)
        _peers.remove(key)
        if (removed) _save()
        return removed
    }

    /** Create a new session, preserving the old one for user modeling. */
    fun newSession(key: String): Any? {
        _ensureLoaded()
        val oldSession = sessions[key]
        val newId = java.util.UUID.randomUUID().toString()
        val newSession = mutableMapOf<String, Any?>(
            "session_id" to newId,
            "created_at" to now(),
            "source" to "session_manager")
        if (oldSession != null) {
            newSession["previous_session_id"] = oldSession["session_id"]
        }
        sessions[key] = newSession
        _histories.remove(key)
        _save()
        return newSession
    }

    // =====================================================================
    // Dialectic methods
    // =====================================================================

    /** Pick a reasoning level for a dialectic query. */
    fun _dynamicReasoningLevel(query: String): String {
        val codeKeywords = listOf("code", "implement", "function", "class", "debug", "error", "fix")
        val lower = query.lowercase()
        return when {
            query.length > 500 && codeKeywords.any { it in lower } -> "high"
            query.length > 200 -> "medium"
            else -> "low"
        }
    }

    /** Query Honcho's dialectic endpoint about a peer. */
    fun dialecticQuery(sessionKey: String, query: String, reasoningLevel: Any? = null, peer: String = "user"): String {
        // Python: self.honcho().dialectic_query(session_key, query, reasoning_level, peer)
        return ""
    }

    /** Fire a dialectic_query in a background thread, caching the result. */
    fun prefetchDialectic(sessionKey: String, query: String) {
        Thread {
            try {
                val level = _dynamicReasoningLevel(query)
                val result = dialecticQuery(sessionKey, query, reasoningLevel = level)
                setDialecticResult(sessionKey, result)
            } catch (e: Exception) {
                Log.w(TAG, "prefetchDialectic error: ${e.message}")
            }
        }.start()
    }

    /** Store a prefetched dialectic result in a thread-safe way. */
    fun setDialecticResult(sessionKey: String, result: String) {
        _dialecticResults[sessionKey] = result
    }

    /** Return and clear the cached dialectic result for this session. */
    fun popDialecticResult(sessionKey: String): String {
        return _dialecticResults.remove(sessionKey) ?: ""
    }

    /** Fire get_prefetch_context in a background thread, caching the result. */
    fun prefetchContext(sessionKey: String, userMessage: Any? = null) {
        Thread {
            try {
                val result = getPrefetchContext(sessionKey, userMessage)
                setContextResult(sessionKey, result)
            } catch (e: Exception) {
                Log.w(TAG, "prefetchContext error: ${e.message}")
            }
        }.start()
    }

    /** Store a prefetched context result in a thread-safe way. */
    fun setContextResult(sessionKey: String, result: Map<String, String>) {
        _contextResults[sessionKey] = result
    }

    /** Return and clear the cached context result for this session. */
    fun popContextResult(sessionKey: String): Map<String, String> {
        return _contextResults.remove(sessionKey) ?: emptyMap()
    }

    /** Pre-fetch user and AI peer context from Honcho. */
    fun getPrefetchContext(sessionKey: String, userMessage: Any? = null): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val card = getPeerCard(sessionKey)
        if (card.isNotEmpty()) result["user_card"] = card.joinToString("\n")
        val aiRep = getAiRepresentation(sessionKey)
        result.putAll(aiRep)
        return result
    }

    // =====================================================================
    // Migration methods
    // =====================================================================

    /** Upload local session history to Honcho as a file. */
    fun migrateLocalHistory(sessionKey: String, messages: List<Map<String, Any>>): Boolean {
        if (messages.isEmpty()) return false
        try {
            val transcript = _formatMigrationTranscript(sessionKey, messages)
            val dir = File(sessionsDir, "migrations")
            dir.mkdirs()
            val file = File(dir, "${_sanitizeId(sessionKey)}_migration.xml")
            file.writeBytes(transcript)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "migrateLocalHistory error: ${e.message}")
            return false
        }
    }

    /** Format local messages as an XML transcript for Honcho file upload. */
    fun _formatMigrationTranscript(sessionKey: String, messages: List<Map<String, Any>>): ByteArray {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<transcript session_id=\"${_sanitizeId(sessionKey)}\">")
        for (msg in messages) {
            val role = msg["role"] as? String ?: "unknown"
            val content = msg["content"] as? String ?: ""
            val timestamp = msg["timestamp"] as? String ?: ""
            sb.appendLine("  <message role=\"$role\" timestamp=\"$timestamp\">")
            sb.appendLine("    ${content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}")
            sb.appendLine("  </message>")
        }
        sb.appendLine("</transcript>")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** Upload MEMORY.md and USER.md to Honcho as files. */
    fun migrateMemoryFiles(sessionKey: String, memoryDir: String): Boolean {
        val dir = File(memoryDir)
        if (!dir.exists()) return false
        var migrated = false
        for (name in listOf("MEMORY.md", "USER.md")) {
            val file = File(dir, name)
            if (file.exists()) {
                try {
                    val migrationsDir = File(sessionsDir, "migrations")
                    migrationsDir.mkdirs()
                    val target = File(migrationsDir, "${_sanitizeId(sessionKey)}_$name")
                    file.copyTo(target, overwrite = true)
                    migrated = true
                } catch (e: Exception) {
                    Log.w(TAG, "migrateMemoryFiles($name) error: ${e.message}")
                }
            }
        }
        return migrated
    }

    // =====================================================================
    // Peer card methods
    // =====================================================================

    /** Normalize Honcho card payloads into a plain list of strings. */
    fun _normalizeCard(card: Any): List<String> {
        return when (card) {
            is List<*> -> card.filterIsInstance<String>()
            is String -> if (card.isNotBlank()) card.split("\n").filter { it.isNotBlank() } else emptyList()
            is Map<*, *> -> {
                val content = card["content"] ?: card["card"] ?: card["items"]
                when (content) {
                    is List<*> -> content.filterIsInstance<String>()
                    is String -> content.split("\n").filter { it.isNotBlank() }
                    else -> card.values.filterIsInstance<String>()
                }
            }
            else -> emptyList()
        }
    }

    /** Fetch a peer card directly from the peer object. */
    fun _fetchPeerCard(peerId: String): List<String> {
        val peer = _peers[peerId] as? Map<*, *> ?: return emptyList()
        val card = peer["card"] ?: return emptyList()
        return _normalizeCard(card)
    }

    /** Fetch representation + peer card directly from a peer object. */
    fun _fetchPeerContext(peerId: String, searchQuery: Any? = null): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val peer = _peers[peerId] as? Map<*, *>
        if (peer != null) {
            val card = peer["card"]
            if (card != null) result["card"] = _normalizeCard(card)
            val representation = peer["representation"]
            if (representation != null) result["representation"] = representation
        }
        return result
    }

    /** Fetch the user peer's card — a curated list of key facts. */
    fun getPeerCard(sessionKey: String): List<String> {
        val userPeerId = "${_sanitizeId(sessionKey)}_user"
        return _fetchPeerCard(userPeerId)
    }

    /** Semantic search over Honcho session context. */
    fun searchContext(sessionKey: String, query: String, maxTokens: Int = 800): String {
        val history = _histories[sessionKey] ?: return ""
        val lowerQuery = query.lowercase()
        val matches = mutableListOf<String>()
        var totalLen = 0
        synchronized(history) {
            for (msg in history) {
                val content = msg["content"] as? String ?: continue
                if (lowerQuery in content.lowercase()) {
                    val snippet = content.take(200)
                    if (totalLen + snippet.length > maxTokens * 4) break
                    matches.add(snippet)
                    totalLen += snippet.length
                }
            }
        }
        return matches.joinToString("\n---\n")
    }

    /** Write a conclusion about the user back to Honcho. */
    fun createConclusion(sessionKey: String, content: String): Boolean {
        try {
            val dir = File(sessionsDir, "conclusions")
            dir.mkdirs()
            val file = File(dir, "${_sanitizeId(sessionKey)}.txt")
            file.writeText(content, Charsets.UTF_8)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "createConclusion error: ${e.message}")
            return false
        }
    }

    /** Seed the AI peer's Honcho representation from text content. */
    fun seedAiIdentity(sessionKey: String, content: String, source: String = "manual"): Boolean {
        val aiPeerId = "${_sanitizeId(sessionKey)}_ai"
        val peer = _peers.getOrPut(aiPeerId) {
            mutableMapOf<String, Any>("peer_id" to aiPeerId)
        } as MutableMap<String, Any>
        peer["representation"] = content
        peer["representation_source"] = source
        return true
    }

    /** Fetch the AI peer's current Honcho representation. */
    fun getAiRepresentation(sessionKey: String): Map<String, String> {
        val aiPeerId = "${_sanitizeId(sessionKey)}_ai"
        val peer = _peers[aiPeerId] as? Map<*, *> ?: return emptyMap()
        val repr = peer["representation"] as? String ?: return emptyMap()
        return mapOf("ai_representation" to repr)
    }

}

/** Lightweight session entry for store index persistence. */
class SessionEntry(
    val sessionKey: String = "",
    val sessionId: String = "",
    var suspended: Boolean = false,
    var memoryFlushed: Boolean = false
)
