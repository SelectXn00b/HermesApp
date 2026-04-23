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

private const val _TAG = "SessionStore"

private fun now(): String = Instant.now().toString()

/** ISO-8601 UTC timestamp used by external call sites. */
internal val _sessionNowIso: () -> String = { Instant.now().toString() }

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

// ── Data classes ────────────────────────────────────────────────────

/**
 * Source of a message (platform, chat, user metadata).
 *
 * Ported from gateway/session.py:SessionSource.
 */
data class SessionSource(
    /** Platform name. */
    val platform: String,
    /** Chat/channel id. */
    val chatId: String,
    /** Chat name (best-effort). */
    val chatName: String = "",
    /** Chat type: "dm", "group", "channel". */
    val chatType: String = "dm",
    /** User id. */
    val userId: String = "",
    /** User name. */
    val userName: String = "",
    /** Thread/topic id (if applicable). */
    val threadId: String? = null,
    /** Whether the user is an admin in the chat. */
    val isAdmin: Boolean = false,
    /** Arbitrary metadata. */
    val metadata: JSONObject = JSONObject()) {
    /** Build a session key from this source. */
    val sessionKey: String get() = buildSessionKey(platform, chatId, userId)
}

/**
 * A single session record.
 */
data class SessionEntry(
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
    var parentSessionKey: String? = null) {
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
    }

    companion object {
        fun fromDict(data: Map<String, Any?>): SessionEntry = SessionEntry(
            sessionKey = data["session_key"] as? String ?: "",
            platform = data["platform"] as? String ?: "",
            chatId = data["chat_id"] as? String ?: "",
            userId = data["user_id"] as? String ?: "",
            chatName = data["chat_name"] as? String ?: "",
            userName = data["user_name"] as? String ?: "",
            chatType = data["chat_type"] as? String ?: "dm",
            createdAt = data["created_at"] as? String ?: now(),
            lastMessageAt = data["last_message_at"] as? String ?: now()).apply {
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
    }
}

// ── Helper functions ────────────────────────────────────────────────

/** Build a session key from platform + chat id + user id. */
fun buildSessionKey(platform: String, chatId: String, userId: String): String =
    "$platform:$chatId:$userId"

/** Build a system-prompt fragment for a session. */
// ── SessionStore ────────────────────────────────────────────────────

/**
 * Session store — manages session lifecycle and persistence.
 * Thread-safe. Sessions are persisted to disk periodically.
 */
class SessionStore(
    private val persistDir: File? = null) {
    private val sessions: ConcurrentHashMap<String, SessionEntry> = ConcurrentHashMap()
    private val resumePending: ConcurrentHashMap<String, Map<String, Any?>> = ConcurrentHashMap()

    /** Get or create a session. */
    fun getOrCreate(
        sessionKey: String,
        platform: String,
        chatId: String,
        userId: String,
        chatName: String = "",
        userName: String = "",
        chatType: String = "dm"): SessionEntry = sessions.getOrPut(sessionKey) {
        SessionEntry(
            sessionKey = sessionKey,
            platform = platform,
            chatId = chatId,
            userId = userId,
            chatName = chatName,
            userName = userName,
            chatType = chatType)
    }

    /** Get or create session from a source map (Python-compatible). */
    fun getOrCreateSession(source: Map<String, Any?>, forceNew: Boolean = false): SessionEntry {
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

    fun get(sessionKey: String): SessionEntry? = sessions[sessionKey]
    val keys: Set<String> get() = sessions.keys.toSet()
    val all: Collection<SessionEntry> get() = sessions.values
    val size: Int get() = sessions.size
    val processingCount: Int get() = sessions.values.count { it.isProcessing }
    val hasAnySessions: Boolean get() = sessions.isNotEmpty()

    fun clear() { sessions.clear() }

    /** Check if a session is expired (idle timeout). */
    fun isSessionExpired(entry: SessionEntry, idleMinutes: Int = 1440): Boolean {
        val last = Instant.parse(entry.lastMessageAt)
        val elapsed = java.time.Duration.between(last, Instant.now())
        return elapsed.toMinutes() > idleMinutes
    }

    /** Check if session should be reset (daily boundary or idle). */
    fun shouldReset(entry: SessionEntry, resetHour: Int = 4): String? {
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
        entry.turnCount.incrementAndGet()
        entry.inputTokens.addAndGet(promptTokens.toLong())
        entry.outputTokens.addAndGet(completionTokens.toLong())
        entry.lastMessageAt = now()
    }

    /** Suspend a session (mark as not processing). */
    fun suspendSession(sessionKey: String): Boolean {
        val entry = sessions[sessionKey] ?: return false
        entry.isProcessing = false
        entry.processingStartedAt = 0L
        return true
    }

    /** Suspend all recently-active sessions. */
    fun suspendRecentlyActive(maxAgeSeconds: Int = 120): Int {
        val cutoff = System.currentTimeMillis() - maxAgeSeconds * 1000
        var count = 0
        sessions.values.filter { it.isProcessing && it.processingStartedAt < cutoff }.forEach {
            it.isProcessing = false
            it.processingStartedAt = 0L
            count++
        }
        return count
    }

    /** Reset a session (clear conversation state). */
    fun resetSession(sessionKey: String): SessionEntry? {
        val old = sessions[sessionKey] ?: return null
        val newEntry = SessionEntry(
            sessionKey = old.sessionKey,
            platform = old.platform,
            chatId = old.chatId,
            userId = old.userId,
            chatName = old.chatName,
            userName = old.userName,
            chatType = old.chatType)
        sessions[sessionKey] = newEntry
        return newEntry
    }

    /** List sessions, optionally filtered by activity. */
    fun listSessions(activeMinutes: Int? = null): List<SessionEntry> {
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
            Log.w(_TAG, "Failed to load transcript for $sessionId: ${e.message}")
            emptyList()
        }
    }

    /** Persist all sessions to disk. */
    fun persist() {
        val dir = persistDir ?: return
        dir.mkdirs()
        val sessionsJson = JSONArray()
        sessions.values.forEach { sessionsJson.put(JSONObject(it.toDict())) }
        val file = File(dir, "sessions.json")
        file.writeText(sessionsJson.toString(2), Charsets.UTF_8)
        Log.d(_TAG, "Persisted ${sessions.size} sessions to ${file.absolutePath}")
    }

    /** Load sessions from disk. */
    fun load() {
        val dir = persistDir ?: return
        val file = File(dir, "sessions.json")
        if (!file.exists()) return
        try {
            val json = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                obj.keys().forEach { key -> map[key] = obj.opt(key) }
                val session = SessionEntry.fromDict(map)
                sessions[session.sessionKey] = session
            }
            Log.i(_TAG, "Loaded ${sessions.size} sessions from ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to load sessions: ${e.message}")
        }
    }

    /** Switch a session key to point at a different session (for /resume). */
    fun switchSession(sessionKey: String, targetSessionKey: String): SessionEntry? {
        val old = sessions[sessionKey] ?: return null
        if (sessionKey == targetSessionKey) return old

        val newEntry = SessionEntry(
            sessionKey = targetSessionKey,
            platform = old.platform,
            chatId = old.chatId,
            userId = old.userId,
            chatName = old.chatName,
            userName = old.userName,
            chatType = old.chatType)
        sessions[targetSessionKey] = newEntry
        return newEntry
    }
    /** Remove a session by key. */
    fun removeSession(sessionKey: String): SessionEntry? = sessions.remove(sessionKey)

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
            Log.d(_TAG, "Pruned ${toRemove.size} session entries older than $maxAgeDays days")
        }
        return toRemove.size
    }

    /**
     * Derive a stable session key from a [SessionSource] — mirrors Python's
     * `SessionStore._generate_session_key`.  Android stub composes platform
     * and chat/user ids with a colon separator.
     */
    fun _generateSessionKey(source: Any?): String {
        if (source !is Map<*, *>) return ""
        val platform = (source["platform"] as? String) ?: "unknown"
        val chatId = (source["chat_id"] as? String) ?: ""
        val userId = (source["user_id"] as? String) ?: ""
        return "$platform:$chatId:$userId"
    }

    /**
     * Mark a session as having a pending resume request (cross-restart hint).
     * Android stub persists the payload in an in-memory side-map keyed by session.
     */
    fun markResumePending(sessionKey: String, payload: Map<String, Any?>? = null): Boolean {
        if (sessions[sessionKey] == null) return false
        resumePending[sessionKey] = payload ?: emptyMap()
        return true
    }

    /**
     * Clear the resume-pending flag on a session.  Returns true if the flag
     * was set and has now been removed.
     */
    fun clearResumePending(sessionKey: String): Boolean {
        return resumePending.remove(sessionKey) != null
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
    private val _MAX_HISTORY = 200

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
            Log.w(_TAG, "Failed to load sessions index: ${e.message}")
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
            Log.e(_TAG, "Failed to save sessions index: ${e.message}")
        }
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
            while (history.size > _MAX_HISTORY) history.removeAt(0)
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
                        while (history.size > _MAX_HISTORY) history.removeAt(0)
                    }
                } else {
                    Thread.sleep(100)
                }
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.w(_TAG, "Async writer error: ${e.message}")
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

    /** Query Honcho's dialectic endpoint about a peer. */
    fun dialecticQuery(sessionKey: String, query: String, reasoningLevel: Any? = null, peer: String = "user"): String {
        // Python: self.honcho().dialectic_query(session_key, query, reasoning_level, peer)
        return ""
    }

    /** Fire get_prefetch_context in a background thread, caching the result. */
    fun prefetchContext(sessionKey: String, userMessage: Any? = null) {
        Thread {
            try {
                val result = getPrefetchContext(sessionKey, userMessage)
                setContextResult(sessionKey, result)
            } catch (e: Exception) {
                Log.w(_TAG, "prefetchContext error: ${e.message}")
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
            Log.e(_TAG, "migrateLocalHistory error: ${e.message}")
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
                    Log.w(_TAG, "migrateMemoryFiles($name) error: ${e.message}")
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
    @Suppress("UNUSED_PARAMETER")
    fun _fetchPeerCard(peerId: String, target: String? = null): List<String> {
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
    @Suppress("UNUSED_PARAMETER")
    fun getPeerCard(sessionKey: String, peer: String = "user"): List<String> {
        val userPeerId = "${_sanitizeId(sessionKey)}_user"
        return _fetchPeerCard(userPeerId)
    }

    /** Semantic search over Honcho session context. */
    @Suppress("UNUSED_PARAMETER")
    fun searchContext(
        sessionKey: String,
        query: String,
        maxTokens: Int = 800,
        peer: String = "user"
    ): String {
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
    @Suppress("UNUSED_PARAMETER")
    fun createConclusion(sessionKey: String, content: String, peer: String = "user"): Boolean {
        try {
            val dir = File(sessionsDir, "conclusions")
            dir.mkdirs()
            val file = File(dir, "${_sanitizeId(sessionKey)}.txt")
            file.writeText(content, Charsets.UTF_8)
            return true
        } catch (e: Exception) {
            Log.e(_TAG, "createConclusion error: ${e.message}")
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

// ── Module-level aligned with Python gateway/session.py ───────────────────

/**
 * Build a session-context prompt block describing the current conversation's
 * platform, chat, user, and recent turn history.  Mirrors Python
 * `build_session_context_prompt` used during system-prompt assembly.
 */
fun buildSessionContextPrompt(
    entry: SessionEntry?,
    systemInstructions: String? = null,
    @Suppress("UNUSED_PARAMETER") extra: Map<String, Any?>? = null
): String {
    if (entry == null) return systemInstructions.orEmpty()
    val lines = mutableListOf<String>()
    if (!systemInstructions.isNullOrBlank()) lines.add(systemInstructions)
    lines.add("Platform: ${entry.platform}")
    lines.add("Chat: ${entry.chatName.ifEmpty { entry.chatId }} (${entry.chatType})")
    lines.add("User: ${entry.userName.ifEmpty { entry.userId }}")
    return lines.joinToString("\n")
}

/**
 * Return true when a session represents a shared, multi-user channel — e.g. a
 * Slack public channel or a Discord guild text channel — where the bot should
 * treat follow-ups as not necessarily directed at it.  Android mirrors the
 * Python rule: chatType == "group" or platform-specific "channel" types.
 */
fun isSharedMultiUserSession(entry: SessionEntry?): Boolean {
    if (entry == null) return false
    return entry.chatType in setOf("group", "channel", "supergroup")
}

/**
 * Compose the structured session-context map passed to downstream consumers
 * (memory, platform handlers).  Mirrors Python `build_session_context` which
 * returns a dict keyed by platform/chat/user/session identifiers.
 */
fun buildSessionContext(entry: SessionEntry?): Map<String, Any?> {
    if (entry == null) return emptyMap()
    return mapOf(
        "session_key" to entry.sessionKey,
        "platform" to entry.platform,
        "chat_id" to entry.chatId,
        "chat_name" to entry.chatName,
        "chat_type" to entry.chatType,
        "user_id" to entry.userId,
        "user_name" to entry.userName,
        "created_at" to entry.createdAt,
        "last_message_at" to entry.lastMessageAt
    )
}

/** Python `_PII_SAFE_PLATFORMS` — platforms where PII redaction is already applied upstream. */
private val _PII_SAFE_PLATFORMS: Set<String> = setOf("feishu", "discord", "telegram")

/** Python `SessionContext` — full session state used for dynamic system-prompt injection. */
data class SessionContext(
    val source: SessionSource,
    val connectedPlatforms: List<String> = emptyList(),
    val homeChannels: Map<String, Any?> = emptyMap(),
    val sharedMultiUserSession: Boolean = false,
    val sessionKey: String = "",
    val sessionId: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
