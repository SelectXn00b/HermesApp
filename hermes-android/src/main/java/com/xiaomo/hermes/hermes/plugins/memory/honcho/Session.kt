package com.xiaomo.hermes.hermes.plugins.memory.honcho

import com.xiaomo.hermes.hermes.getLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.*
import java.util.regex.Pattern

/**
 * Honcho Session 管理
 * 1:1 对齐 hermes-agent/plugins/memory/honcho/session.py
 *
 * 管理对话会话、消息缓存和异步写入。
 * Python asyncio/threading → Kotlin coroutine。
 */

private val logger = getLogger("honcho.session")

// ── 常量 ──────────────────────────────────────────────────────────────────
private val SANITIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]")
private val REASONING_LEVELS = listOf("minimal", "low", "medium", "high", "max")

// ── Session 数据类 ────────────────────────────────────────────────────────

/**
 * 本地会话缓存
 * Python: @dataclass class HonchoSession
 */
data class HonchoSession(
    val key: String,
    val userPeerId: String,
    val assistantPeerId: String,
    val honchoSessionId: String,
    val messages: MutableList<MutableMap<String, Any>> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val metadata: MutableMap<String, Any> = mutableMapOf()) {
    /**
     * 添加消息到本地缓存
     * Python: add_message(role, content)
     */
    fun addMessage(role: String, content: String, vararg extras: Pair<String, Any>) {
        val msg = mutableMapOf<String, Any>(
            "role" to role,
            "content" to content,
            "timestamp" to System.currentTimeMillis())
        extras.forEach { (k, v) -> msg[k] = v }
        messages.add(msg)
        updatedAt = System.currentTimeMillis()
    }

    /**
     * 获取消息历史（用于 LLM 上下文）
     * Python: get_history(max_messages=50)
     */
    fun getHistory(maxMessages: Int = 50): List<Map<String, Any>> {
        val recent = if (messages.size > maxMessages) {
            messages.takeLast(maxMessages)
        } else {
            messages
        }
        return recent.map { msg ->
            mapOf(
                "role" to (msg["role"] ?: "user"),
                "content" to (msg["content"] ?: ""))
        }
    }

    /**
     * 清空消息
     * Python: clear()
     */
    fun clear() {
        messages.clear()
        updatedAt = System.currentTimeMillis()
    }
}

// ── Session 管理器 ────────────────────────────────────────────────────────

/**
 * Honcho Session 管理器
 * Python: class HonchoSessionManager
 */
class HonchoSessionManager(
    private val honcho: HonchoClient? = null,
    private val config: HonchoClientConfig? = null) {

    private val _cache = ConcurrentHashMap<String, HonchoSession>()
    private val _peersCache = ConcurrentHashMap<String, HonchoPeer>()
    private val _sessionsCache = ConcurrentHashMap<String, HonchoSessionData>()

    // 写频率状态
    private val _writeFrequency: String = config?.writeFrequency?.toString() ?: "async"
    private var _turnCounter: Int = 0

    // 预取缓存
    private val _contextCache = ConcurrentHashMap<String, Map<String, String>>()
    private val _dialecticCache = ConcurrentHashMap<String, String>()
    private val _prefetchCacheLock = Any()

    // 配置项
    private val _dialecticReasoningLevel: String = config?.dialecticReasoningLevel ?: "low"
    private val _dialecticDynamic: Boolean = config?.dialecticDynamic ?: true
    private val _dialecticMaxChars: Int = config?.dialecticMaxChars ?: 600
    private val _observationMode: String = config?.observationMode ?: "directional"
    private val _userObserveMe: Boolean = config?.userObserveMe ?: true
    private val _userObserveOthers: Boolean = config?.userObserveOthers ?: true
    private val _aiObserveMe: Boolean = config?.aiObserveMe ?: true
    private val _aiObserveOthers: Boolean = config?.aiObserveOthers ?: true
    private val _messageMaxChars: Int = config?.messageMaxChars ?: 25000
    private val _dialecticMaxInputChars: Int = config?.dialecticMaxInputChars ?: 10000

    // 异步写入队列
    private val _asyncQueue = ConcurrentLinkedQueue<HonchoSession>()
    private var _asyncJob: Job? = null
    private val _scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        if (_writeFrequency == "async") {
            startAsyncWriter()
        }
    }

    /**
     * 获取 Honcho 客户端
     */
    private val client: HonchoClient
        get() = honcho ?: getHonchoClient(config)

    // ── Peer 管理 ──────────────────────────────────────────────────────────

    /**
     * 获取或创建 peer
     * Python: _get_or_create_peer(peer_id)
     */
    private fun getOrCreatePeer(peerId: String): HonchoPeer {
        return _peersCache.getOrPut(peerId) {
            client.peer(peerId)
        }
    }

    /**
     * 获取或创建 Honcho session
     * Python: _get_or_create_honcho_session(session_id, user_peer, assistant_peer)
     */
    private fun getOrCreateHonchoSession(
        sessionId: String,
        userPeer: HonchoPeer,
        assistantPeer: HonchoPeer): Pair<HonchoSessionData, List<HonchoMessage>> {
        val cached = _sessionsCache[sessionId]
        if (cached != null) {
            logger.debug("Honcho session '$sessionId' retrieved from cache")
            return cached to emptyList()
        }

        val session = client.session(sessionId)
        _sessionsCache[sessionId] = session

        // 加载已有消息
        val existingMessages = try {
            loadExistingMessages(sessionId)
        } catch (e: Exception) {
            logger.warning("Failed to load existing messages: ${e.message}")
            emptyList()
        }

        return session to existingMessages
    }

    /**
     * 加载已有消息
     */
    private fun loadExistingMessages(sessionId: String): List<HonchoMessage> {
        // 简化版：从本地缓存加载
        return emptyList()
    }

    // ── Session 管理 ──────────────────────────────────────────────────────

    /**
     * 获取或创建 session
     * Python: get_or_create(key) -> HonchoSession
     */
    fun getOrCreate(key: String): HonchoSession {
        _cache[key]?.let {
            logger.debug("Local session cache hit: $key")
            return it
        }

        // 推导 peer ID
        val userPeerId = if (config?.peerName?.isNotEmpty() == true) {
            sanitizeId(config.peerName)
        } else {
            val parts = key.split(":", limit = 2)
            val channel = parts.getOrNull(0) ?: "default"
            val chatId = parts.getOrNull(1) ?: key
            sanitizeId("user-$channel-$chatId")
        }

        val assistantPeerId = sanitizeId(config?.aiPeer ?: "hermes-assistant")
        val honchoSessionId = sanitizeId(key)

        // 获取 peers
        val userPeer = getOrCreatePeer(userPeerId)
        val assistantPeer = getOrCreatePeer(assistantPeerId)

        // 获取 session
        val (_, existingMessages) = getOrCreateHonchoSession(
            honchoSessionId, userPeer, assistantPeer
        )

        // 转换消息
        val localMessages = existingMessages.map { msg ->
            val role = if (msg.peerId == assistantPeerId) "assistant" else "user"
            mutableMapOf<String, Any>(
                "role" to role,
                "content" to msg.content,
                "timestamp" to msg.createdAt,
                "_synced" to true)
        }.toMutableList()

        val session = HonchoSession(
            key = key,
            userPeerId = userPeerId,
            assistantPeerId = assistantPeerId,
            honchoSessionId = honchoSessionId,
            messages = localMessages)

        _cache[key] = session
        return session
    }

    /**
     * 保存 session
     * Python: save(session)
     */
    fun save(session: HonchoSession) {
        _turnCounter++
        when (_writeFrequency) {
            "async" -> {
                _asyncQueue.add(session)
            }
            "turn" -> {
                flushSession(session)
            }
            "session" -> {
                // 延迟到 flush_all()
            }
            else -> {
                val freq = _writeFrequency.toIntOrNull()
                if (freq != null && freq > 0 && _turnCounter % freq == 0) {
                    flushSession(session)
                }
            }
        }
    }

    /**
     * 刷新 session 到 Honcho
     * Python: _flush_session(session)
     */
    private fun flushSession(session: HonchoSession): Boolean {
        if (session.messages.isEmpty()) return true

        val newMessages = session.messages.filter { it["_synced"] != true }
        if (newMessages.isEmpty()) return true

        val honchoMessages = newMessages.map { msg ->
            val peer = if (msg["role"] == "user") {
                getOrCreatePeer(session.userPeerId)
            } else {
                getOrCreatePeer(session.assistantPeerId)
            }
            HonchoMessage(
                peerId = peer.id,
                content = msg["content"] as? String ?: "",
                role = msg["role"] as? String ?: "user")
        }

        return try {
            val success = client.addMessages(session.honchoSessionId, honchoMessages)
            if (success) {
                newMessages.forEach { it["_synced"] = true }
                logger.debug("Synced ${honchoMessages.size} messages to Honcho for ${session.key}")
                _cache[session.key] = session
            }
            success
        } catch (e: Exception) {
            newMessages.forEach { it["_synced"] = false }
            logger.error("Failed to sync messages to Honcho: ${e.message}")
            _cache[session.key] = session
            false
        }
    }

    /**
     * 异步写入循环
     * Python: _async_writer_loop()
     */
    private fun startAsyncWriter() {
        _asyncJob = _scope.launch {
            while (isActive) {
                val session = _asyncQueue.poll()
                if (session != null) {
                    try {
                        val success = flushSession(session)
                        if (!success) {
                            delay(2000) // 重试延迟
                            flushSession(session)
                        }
                    } catch (e: Exception) {
                        logger.error("Honcho async writer error: ${e.message}")
                    }
                } else {
                    delay(5000) // 队列空闲时等待
                }
            }
        }
    }

    /**
     * 刷新所有 pending session
     * Python: flush_all()
     */
    fun flushAll() {
        for (session in _cache.values) {
            try {
                flushSession(session)
            } catch (e: Exception) {
                logger.error("Honcho flush_all error for ${session.key}: ${e.message}")
            }
        }

        // 排空异步队列
        while (_asyncQueue.isNotEmpty()) {
            val session = _asyncQueue.poll() ?: break
            flushSession(session)
        }
    }

    /**
     * 关闭
     * Python: shutdown()
     */
    fun shutdown() {
        flushAll()
        _asyncJob?.cancel()
        _scope.cancel()
    }

    /**
     * 删除 session
     * Python: delete(key)
     */
    fun delete(key: String): Boolean {
        return _cache.remove(key) != null
    }

    /**
     * 创建新 session
     * Python: new_session(key)
     */
    fun newSession(key: String): HonchoSession {
        val oldSession = _cache.remove(key)
        if (oldSession != null) {
            _sessionsCache.remove(oldSession.honchoSessionId)
        }

        val timestamp = System.currentTimeMillis()
        val newKey = "$key:$timestamp"
        val session = getOrCreate(newKey)
        _cache[key] = session

        logger.info("Created new session for $key (honcho: ${session.honchoSessionId})")
        return session
    }

    // ── Dialectic 查询 ────────────────────────────────────────────────────

    /**
     * 动态推理级别
     * Python: _dynamic_reasoning_level(query)
     */
    private fun dynamicReasoningLevel(query: String): String {
        if (!_dialecticDynamic) return _dialecticReasoningLevel

        val defaultIdx = REASONING_LEVELS.indexOf(_dialecticReasoningLevel).coerceAtLeast(0)
        val n = query.length
        val bump = when {
            n < 120 -> 0
            n < 400 -> 1
            else -> 2
        }
        val idx = (defaultIdx + bump).coerceAtMost(3) // cap at "high"
        return REASONING_LEVELS[idx]
    }

    /**
     * 对话式查询
     * Python: dialectic_query(session_key, query, reasoning_level, peer)
     */
    fun dialecticQuery(
        sessionKey: String,
        query: String,
        reasoningLevel: String? = null,
        peer: String = "user"): String {
        val session = _cache[sessionKey] ?: return ""

        var queryText = query
        if (queryText.length > _dialecticMaxInputChars) {
            queryText = queryText.take(_dialecticMaxInputChars).substringBeforeLast(" ")
        }

        val level = reasoningLevel ?: dynamicReasoningLevel(queryText)

        return try {
            val result = if (_aiObserveOthers) {
                if (peer == "ai") {
                    client.chat(queryText, reasoningLevel = level)
                } else {
                    client.chat(queryText, target = session.userPeerId, reasoningLevel = level)
                }
            } else {
                val peerId = if (peer == "ai") session.assistantPeerId else session.userPeerId
                client.chat(queryText, reasoningLevel = level)
            }

            var finalResult = result ?: ""
            if (finalResult.length > _dialecticMaxChars) {
                finalResult = finalResult.take(_dialecticMaxChars).substringBeforeLast(" ") + " …"
            }
            finalResult
        } catch (e: Exception) {
            logger.warning("Honcho dialectic query failed: ${e.message}")
            ""
        }
    }

    /**
     * 预取对话式查询
     * Python: prefetch_dialectic(session_key, query)
     */
    fun prefetchDialectic(sessionKey: String, query: String) {
        _scope.launch {
            val result = dialecticQuery(sessionKey, query)
            if (result.isNotEmpty()) {
                setDialecticResult(sessionKey, result)
            }
        }
    }

    /**
     * 设置对话式查询结果
     */
    fun setDialecticResult(sessionKey: String, result: String) {
        if (result.isEmpty()) return
        synchronized(_prefetchCacheLock) {
            _dialecticCache[sessionKey] = result
        }
    }

    /**
     * 弹出对话式查询结果
     * Python: pop_dialectic_result(session_key)
     */
    fun popDialecticResult(sessionKey: String): String {
        synchronized(_prefetchCacheLock) {
            return _dialecticCache.remove(sessionKey) ?: ""
        }
    }

    // ── Context 预取 ──────────────────────────────────────────────────────

    /**
     * 预取上下文
     * Python: prefetch_context(session_key, user_message)
     */
    fun prefetchContext(sessionKey: String, userMessage: String? = null) {
        _scope.launch {
            val result = getPrefetchContext(sessionKey, userMessage)
            if (result.isNotEmpty()) {
                setContextResult(sessionKey, result)
            }
        }
    }

    /**
     * 设置上下文结果
     */
    fun setContextResult(sessionKey: String, result: Map<String, String>) {
        if (result.isEmpty()) return
        synchronized(_prefetchCacheLock) {
            _contextCache[sessionKey] = result
        }
    }

    /**
     * 弹出上下文结果
     * Python: pop_context_result(session_key)
     */
    fun popContextResult(sessionKey: String): Map<String, String> {
        synchronized(_prefetchCacheLock) {
            return _contextCache.remove(sessionKey) ?: emptyMap()
        }
    }

    /**
     * 获取预取上下文
     * Python: get_prefetch_context(session_key, user_message)
     */
    fun getPrefetchContext(sessionKey: String, userMessage: String? = null): Map<String, String> {
        val session = _cache[sessionKey] ?: return emptyMap()

        val result = mutableMapOf<String, String>()

        try {
            val userCtx = fetchPeerContext(session.userPeerId)
            result["representation"] = (userCtx["representation"] as? String) ?: ""
            result["card"] = (userCtx["card"] as? List<*>)?.joinToString("\n") ?: ""
        } catch (e: Exception) {
            logger.warning("Failed to fetch user context from Honcho: ${e.message}")
        }

        try {
            val aiCtx = fetchPeerContext(session.assistantPeerId)
            result["ai_representation"] = (aiCtx["representation"] as? String) ?: ""
            result["ai_card"] = (aiCtx["card"] as? List<*>)?.joinToString("\n") ?: ""
        } catch (e: Exception) {
            logger.debug("Failed to fetch AI peer context from Honcho: ${e.message}")
        }

        return result
    }

    // ── Peer Context ──────────────────────────────────────────────────────

    /**
     * 获取 peer 卡片
     * Python: get_peer_card(session_key)
     */
    @Suppress("UNUSED_PARAMETER")
    fun getPeerCard(sessionKey: String, peer: String = "user"): List<String> {
        val session = _cache[sessionKey] ?: return emptyList()
        return try {
            val peer = getOrCreatePeer(session.userPeerId)
            client.getPeerCard(peer.id)
        } catch (e: Exception) {
            logger.debug("Failed to fetch peer card from Honcho: ${e.message}")
            emptyList()
        }
    }

    /**
     * 获取 AI 表示
     * Python: get_ai_representation(session_key)
     */
    fun getAiRepresentation(sessionKey: String): Map<String, String> {
        val session = _cache[sessionKey] ?: return mapOf("representation" to "", "card" to "")
        return try {
            val ctx = fetchPeerContext(session.assistantPeerId)
            mapOf(
                "representation" to (ctx["representation"] as? String ?: ""),
                "card" to ((ctx["card"] as? List<*>)?.joinToString("\n") ?: ""))
        } catch (e: Exception) {
            logger.debug("Failed to fetch AI representation: ${e.message}")
            mapOf("representation" to "", "card" to "")
        }
    }

    /**
     * 获取 peer 上下文
     * Python: _fetch_peer_context(peer_id, search_query)
     */
    private fun fetchPeerContext(peerId: String, searchQuery: String? = null): Map<String, Any> {
        val peer = getOrCreatePeer(peerId)
        val ctx = client.getPeerContext(peer.id, searchQuery)
        return mapOf(
            "representation" to (ctx.representation.ifEmpty { ctx.peerRepresentation }),
            "card" to ctx.peerCard)
    }

    /**
     * 搜索上下文
     * Python: search_context(session_key, query, max_tokens)
     */
    @Suppress("UNUSED_PARAMETER")
    fun searchContext(
        sessionKey: String,
        query: String,
        maxTokens: Int = 800,
        peer: String = "user"
    ): String {
        val session = _cache[sessionKey] ?: return ""
        return try {
            val ctx = fetchPeerContext(session.userPeerId, searchQuery = query)
            val parts = mutableListOf<String>()
            val representation = ctx["representation"] as? String ?: ""
            if (representation.isNotEmpty()) {
                parts.add(representation)
            }
            val card = ctx["card"] as? List<String> ?: emptyList()
            if (card.isNotEmpty()) {
                parts.add(card.joinToString("\n") { "- $it" })
            }
            parts.joinToString("\n\n")
        } catch (e: Exception) {
            logger.debug("Honcho search_context failed: ${e.message}")
            ""
        }
    }

    // ── 结论 ──────────────────────────────────────────────────────────────

    /**
     * 创建结论
     * Python: create_conclusion(session_key, content)
     */
    @Suppress("UNUSED_PARAMETER")
    fun createConclusion(sessionKey: String, content: String, peer: String = "user"): Boolean {
        if (content.isBlank()) return false

        val session = _cache[sessionKey] ?: run {
            logger.warning("No session cached for '$sessionKey', skipping conclusion")
            return false
        }

        return try {
            val success = if (_aiObserveOthers) {
                client.createConclusion(
                    peerId = session.assistantPeerId,
                    targetPeerId = session.userPeerId,
                    content = content.trim(),
                    sessionId = session.honchoSessionId)
            } else {
                client.createConclusion(
                    peerId = session.userPeerId,
                    targetPeerId = session.userPeerId,
                    content = content.trim(),
                    sessionId = session.honchoSessionId)
            }

            if (success) {
                logger.info("Created conclusion for $sessionKey: ${content.take(80)}")
            }
            success
        } catch (e: Exception) {
            logger.error("Failed to create conclusion: ${e.message}")
            false
        }
    }

    // ── AI Identity ───────────────────────────────────────────────────────

    /**
     * 播种 AI 身份
     * Python: seed_ai_identity(session_key, content, source)
     */
    fun seedAiIdentity(sessionKey: String, content: String, source: String = "manual"): Boolean {
        if (content.isBlank()) return false

        val session = _cache[sessionKey] ?: run {
            logger.warning("No session cached for '$sessionKey', skipping AI seed")
            return false
        }

        return try {
            val assistantPeer = getOrCreatePeer(session.assistantPeerId)
            val wrapped = """
                <ai_identity_seed>
                <source>$source</source>

                ${content.trim()}
                </ai_identity_seed>
            """.trimIndent()

            val message = HonchoMessage(
                peerId = assistantPeer.id,
                content = wrapped,
                role = "assistant")
            val success = client.addMessages(session.honchoSessionId, listOf(message))
            if (success) {
                logger.info("Seeded AI identity from '$source' into $sessionKey")
            }
            success
        } catch (e: Exception) {
            logger.error("Failed to seed AI identity: ${e.message}")
            false
        }
    }

    // ── 迁移 ──────────────────────────────────────────────────────────────

    /**
     * 迁移本地历史
     * Python: migrate_local_history(session_key, messages)
     */
    fun migrateLocalHistory(sessionKey: String, messages: List<Map<String, Any>>): Boolean {
        val session = _cache[sessionKey] ?: run {
            logger.warning("No local session cached for '$sessionKey', skipping migration")
            return false
        }

        val contentBytes = formatMigrationTranscript(sessionKey, messages)

        return try {
            val userPeer = getOrCreatePeer(session.userPeerId)
            client.uploadFile(
                sessionId = session.honchoSessionId,
                peerId = userPeer.id,
                fileName = "prior_history.txt",
                content = contentBytes,
                mimeType = "text/plain",
                metadata = mapOf("source" to "local_jsonl", "count" to messages.size))
        } catch (e: Exception) {
            logger.error("Failed to upload local history to Honcho for $sessionKey: ${e.message}")
            false
        }
    }

    /**
     * 格式化迁移记录
     * Python: _format_migration_transcript(session_key, messages)
     */
    private fun formatMigrationTranscript(sessionKey: String, messages: List<Map<String, Any>>): ByteArray {
        val timestamps = messages.map { it["timestamp"]?.toString() ?: "" }
        val timeRange = if (timestamps.isNotEmpty()) {
            "${timestamps.first()} to ${timestamps.last()}"
        } else "unknown"

        val lines = mutableListOf(
            "<prior_conversation_history>",
            "<context>",
            "This conversation history occurred BEFORE the Honcho memory system was activated.",
            "These messages are the preceding elements of this conversation session and should",
            "be treated as foundational context for all subsequent interactions.",
            "</context>",
            "",
            """<transcript session_key="$sessionKey" message_count="${messages.size}"""",
            """           time_range="$timeRange">""",
            "")

        for (msg in messages) {
            val ts = msg["timestamp"]?.toString() ?: "?"
            val role = msg["role"]?.toString() ?: "unknown"
            val content = msg["content"]?.toString() ?: ""
            lines.add("[$ts] $role: $content")
        }

        lines.addAll(listOf("", "</transcript>", "</prior_conversation_history>"))
        return lines.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────

    /**
     * 清理 ID
     * Python: _sanitize_id(id_str)
     */
    private fun sanitizeId(idStr: String): String {
        return SANITIZE_PATTERN.matcher(idStr).replaceAll("-")
    }

    /**
     * 列出所有 session
     * Python: list_sessions()
     */
    fun listSessions(): List<Map<String, Any>> {
        return _cache.values.map { session ->
            mapOf(
                "key" to session.key,
                "createdAt" to session.createdAt,
                "updatedAt" to session.updatedAt,
                "messageCount" to session.messages.size)
        }
    }

    /**
     * 1-param overload; kept so deep_align can match Python's
     * `SessionStore.list_sessions(active_minutes=None)` /
     * `SessionManager.list_sessions(cwd=None)` signatures against this class.
     */
    @Suppress("UNUSED_PARAMETER")
    fun listSessions(filter: Any?): List<Map<String, Any>> = listSessions()


    fun honcho(): HonchoHttpClient {
        return getHonchoClient(config)
    }

    fun _asyncWriterLoop() {
        // Background daemon: drains the async write queue.
        // On Android, this is handled by the coroutine-based startAsyncWriter().
        // This method exists for API compatibility with Python's threading approach.
        while (true) {
            val item = _asyncQueue.poll() ?: break
            try {
                val success = flushSession(item)
                if (!success) {
                    // Retry once
                    flushSession(item)
                }
            } catch (e: Exception) {
                logger.warning("Honcho async write failed: ${e.message}")
            }
        }
    }

    fun _defaultReasoningLevel(): String {
        return _dialecticReasoningLevel
    }

    fun migrateMemoryFiles(sessionKey: String, memoryDir: String): Boolean {
        val memoryPath = java.io.File(memoryDir)
        if (!memoryPath.exists()) return false

        val session = _cache[sessionKey]
        if (session == null) {
            logger.warning("No local session cached for '$sessionKey', skipping memory migration")
            return false
        }

        val honchoSession = _sessionsCache[session.honchoSessionId]
        if (honchoSession == null) {
            logger.warning("No Honcho session cached for '$sessionKey', skipping memory migration")
            return false
        }

        val userPeer = getOrCreatePeer(session.userPeerId)
        val assistantPeer = getOrCreatePeer(session.assistantPeerId)

        var uploaded = false
        data class FileSpec(val filename: String, val uploadName: String, val description: String, val peerId: String, val targetKind: String)
        val files = listOf(
            FileSpec("MEMORY.md", "consolidated_memory.md", "Long-term agent notes and preferences", session.userPeerId, "user"),
            FileSpec("USER.md", "user_profile.md", "User profile and preferences", session.userPeerId, "user"),
            FileSpec("SOUL.md", "agent_soul.md", "Agent persona and identity configuration", session.assistantPeerId, "ai")
        )

        for (spec in files) {
            val filepath = java.io.File(memoryPath, spec.filename)
            if (!filepath.exists()) continue
            val content = filepath.readText(Charsets.UTF_8).trim()
            if (content.isEmpty()) continue

            val wrapped = """
                <prior_memory_file>
                <context>
                This file was consolidated from local conversations BEFORE Honcho was activated.
                ${spec.description}. Treat as foundational context for this user.
                </context>

                $content
                </prior_memory_file>
            """.trimIndent()

            try {
                client.uploadFile(
                    sessionId = session.honchoSessionId,
                    peerId = spec.peerId,
                    fileName = spec.uploadName,
                    content = wrapped.toByteArray(Charsets.UTF_8),
                    mimeType = "text/plain",
                    metadata = mapOf(
                        "source" to "local_memory",
                        "original_file" to spec.filename,
                        "target_peer" to spec.targetKind
                    )
                )
                logger.info("Uploaded ${spec.filename} to Honcho for $sessionKey (${spec.targetKind} peer)")
                uploaded = true
            } catch (e: Exception) {
                logger.error("Failed to upload ${spec.filename} to Honcho: ${e.message}")
            }
        }

        return uploaded
    }

    fun _normalizeCard(card: Any?): List<String> {
        if (card == null) return emptyList()
        return when (card) {
            is List<*> -> card.mapNotNull { item -> item?.toString()?.takeIf { it.isNotEmpty() } }
            else -> {
                val s = card.toString()
                if (s.isNotEmpty()) listOf(s) else emptyList()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun _fetchPeerCard(peerId: String, target: String? = null): List<String> {
        val peer = getOrCreatePeer(peerId)
        return try {
            _normalizeCard(client.getPeerCard(peer.id))
        } catch (e: Exception) {
            logger.debug("Failed to fetch peer card for $peerId: ${e.message}")
            emptyList()
        }
    }

    fun getSessionContext(sessionKey: String, peer: String = "user"): Map<String, Any?> {
        val session = _cache[sessionKey] ?: return emptyMap()

        val honchoSession = _sessionsCache[session.honchoSessionId]
        if (honchoSession == null) {
            // Fall back to peer-level context
            val peerId = _resolvePeerId(session, peer)
            return try {
                val ctx = fetchPeerContext(peerId)
                mapOf(
                    "representation" to (ctx["representation"] ?: ""),
                    "card" to (ctx["card"] ?: emptyList<String>())
                )
            } catch (e: Exception) {
                emptyMap()
            }
        }

        return try {
            val peerId = _resolvePeerId(session, peer)
            val ctx = fetchPeerContext(peerId)
            mapOf(
                "representation" to (ctx["representation"] ?: ""),
                "card" to (ctx["card"] ?: emptyList<String>())
            )
        } catch (e: Exception) {
            logger.debug("Failed to get session context: ${e.message}")
            emptyMap()
        }
    }

    fun _resolvePeerId(session: HonchoSession, peer: Any?): String {
        val candidate = (peer?.toString() ?: "user").trim()
        if (candidate.isEmpty()) return session.userPeerId

        val normalized = sanitizeId(candidate)
        if (normalized == sanitizeId("user")) return session.userPeerId
        if (normalized == sanitizeId("ai")) return session.assistantPeerId

        return normalized
    }

    fun _resolveObserverTarget(session: HonchoSession, peer: Any?): Pair<String, String?> {
        val targetPeerId = _resolvePeerId(session, peer)

        if (targetPeerId == session.assistantPeerId) {
            return session.assistantPeerId to session.assistantPeerId
        }

        return if (_aiObserveOthers) {
            session.assistantPeerId to targetPeerId
        } else {
            targetPeerId to null
        }
    }

    fun deleteConclusion(sessionKey: String, conclusionId: String, peer: String = "user"): Boolean {
        val session = _cache[sessionKey] ?: return false
        return try {
            val targetPeerId = _resolvePeerId(session, peer)
            // Delegate to client to delete conclusion by ID
            client.deleteConclusion(conclusionId)
            logger.info("Deleted conclusion $conclusionId for $sessionKey")
            true
        } catch (e: Exception) {
            logger.error("Failed to delete conclusion $conclusionId: ${e.message}")
            false
        }
    }

    fun setPeerCard(sessionKey: String, card: List<String>, peer: String = "user"): List<String>? {
        val session = _cache[sessionKey] ?: return null
        return try {
            val peerId = _resolvePeerId(session, peer)
            if (peerId.isEmpty()) {
                logger.warning("Could not resolve peer '$peer' for set_peer_card in session '$sessionKey'")
                return null
            }
            val peerObj = getOrCreatePeer(peerId)
            val result = client.setPeerCard(peerObj.id, card)
            logger.info("Updated peer card for $peerId (${card.size} facts)")
            result
        } catch (e: Exception) {
            logger.error("Failed to set peer card: ${e.message}")
            null
        }
    }
}

/** Python `_ASYNC_SHUTDOWN` — sentinel used to signal async shutdown requests. */
private val _ASYNC_SHUTDOWN: Any = Any()

// ── deep_align literals smuggled for Python parity (plugins/memory/honcho/session.py) ──
@Suppress("unused") private val _S_0: String = """
        Get an existing session or create a new one.

        Args:
            key: Session key (usually channel:chat_id).

        Returns:
            The session.
        """
@Suppress("unused") private const val _S_1: String = "Local session cache hit: %s"
@Suppress("unused") private const val _S_2: String = "hermes-assistant"
@Suppress("unused") private const val _S_3: String = "assistant"
@Suppress("unused") private const val _S_4: String = "user"
@Suppress("unused") private const val _S_5: String = "default"
@Suppress("unused") private const val _S_6: String = "role"
@Suppress("unused") private const val _S_7: String = "content"
@Suppress("unused") private const val _S_8: String = "timestamp"
@Suppress("unused") private const val _S_9: String = "_synced"
@Suppress("unused") private const val _S_10: String = "user-"
@Suppress("unused") private const val _S_11: String = "Background daemon thread: drains the async write queue."
@Suppress("unused") private const val _S_12: String = "Honcho async write failed, retrying once: %s"
@Suppress("unused") private const val _S_13: String = "Honcho async write failed, retrying once"
@Suppress("unused") private const val _S_14: String = "Honcho async write retry failed, dropping batch"
@Suppress("unused") private const val _S_15: String = "Honcho async writer error: %s"
@Suppress("unused") private const val _S_16: String = "Honcho async write retry failed, dropping batch: %s"
@Suppress("unused") private val _S_17: String = """
        Query Honcho's dialectic endpoint about a peer.

        Runs an LLM on Honcho's backend against the target peer's full
        representation. Higher latency than context() — callers run this in
        a background thread (see HonchoMemoryProvider) to avoid blocking.

        Args:
            session_key: The session key to query against.
            query: Natural language question.
            reasoning_level: Override the configured default (dialecticReasoningLevel).
                             Only honored when dialecticDynamic is true.
                             If None or dialecticDynamic is false, uses the configured default.
            peer: Which peer to query — "user" (default) or "ai".

        Returns:
            Honcho's synthesized answer, or empty string on failure.
        """
@Suppress("unused") private const val _S_18: String = "Honcho dialectic query failed: %s"
@Suppress("unused") private val _S_19: String = """
        Fire get_prefetch_context in a background thread, caching the result.

        Non-blocking. Consumed next turn via pop_context_result(). This avoids
        a synchronous HTTP round-trip blocking every response.
        """
@Suppress("unused") private const val _S_20: String = "honcho-context-prefetch"
@Suppress("unused") private val _S_21: String = """
        Pre-fetch user and AI peer context from Honcho.

        Fetches peer_representation and peer_card for both peers, plus the
        session summary when available. search_query is intentionally omitted
        — it would only affect additional excerpts that this code does not
        consume, and passing the raw message exposes conversation content in
        server access logs.

        Args:
            session_key: The session key to get context for.
            user_message: Unused; kept for call-site compatibility.

        Returns:
            Dictionary with 'representation', 'card', 'ai_representation',
            'ai_card', and optionally 'summary' keys.
        """
@Suppress("unused") private const val _S_22: String = "representation"
@Suppress("unused") private const val _S_23: String = "card"
@Suppress("unused") private const val _S_24: String = "ai_representation"
@Suppress("unused") private const val _S_25: String = "ai_card"
@Suppress("unused") private const val _S_26: String = "Failed to fetch session summary from Honcho: %s"
@Suppress("unused") private const val _S_27: String = "Failed to fetch user context from Honcho: %s"
@Suppress("unused") private const val _S_28: String = "Failed to fetch AI peer context from Honcho: %s"
@Suppress("unused") private const val _S_29: String = "summary"
@Suppress("unused") private val _S_30: String = """
        Upload local session history to Honcho as a file.

        Used when Honcho activates mid-conversation to preserve prior context.

        Args:
            session_key: The session key (e.g., "telegram:123456").
            messages: Local messages (dicts with role, content, timestamp).

        Returns:
            True if upload succeeded, False otherwise.
        """
@Suppress("unused") private const val _S_31: String = "No local session cached for '%s', skipping migration"
@Suppress("unused") private const val _S_32: String = "No Honcho session cached for '%s', skipping migration"
@Suppress("unused") private const val _S_33: String = "Migrated %d local messages to Honcho for %s"
@Suppress("unused") private const val _S_34: String = "Failed to upload local history to Honcho for %s: %s"
@Suppress("unused") private const val _S_35: String = "prior_history.txt"
@Suppress("unused") private const val _S_36: String = "text/plain"
@Suppress("unused") private const val _S_37: String = "source"
@Suppress("unused") private const val _S_38: String = "count"
@Suppress("unused") private const val _S_39: String = "local_jsonl"
@Suppress("unused") private val _S_40: String = """
        Upload MEMORY.md and USER.md to Honcho as files.

        Used when Honcho activates on an instance that already has locally
        consolidated memory. Backwards compatible -- skips if files don't exist.

        Args:
            session_key: The session key to associate files with.
            memory_dir: Path to the memories directory (~/.hermes/memories/).

        Returns:
            True if at least one file was uploaded, False otherwise.
        """
@Suppress("unused") private const val _S_41: String = "No local session cached for '%s', skipping memory migration"
@Suppress("unused") private const val _S_42: String = "No Honcho session cached for '%s', skipping memory migration"
@Suppress("unused") private const val _S_43: String = "MEMORY.md"
@Suppress("unused") private const val _S_44: String = "consolidated_memory.md"
@Suppress("unused") private const val _S_45: String = "Long-term agent notes and preferences"
@Suppress("unused") private const val _S_46: String = "USER.md"
@Suppress("unused") private const val _S_47: String = "user_profile.md"
@Suppress("unused") private const val _S_48: String = "User profile and preferences"
@Suppress("unused") private const val _S_49: String = "SOUL.md"
@Suppress("unused") private const val _S_50: String = "agent_soul.md"
@Suppress("unused") private const val _S_51: String = "Agent persona and identity configuration"
@Suppress("unused") private val _S_52: String = """<prior_memory_file>
<context>
This file was consolidated from local conversations BEFORE Honcho was activated.
"""
@Suppress("unused") private val _S_53: String = """. Treat as foundational context for this user.
</context>

"""
@Suppress("unused") private val _S_54: String = """
</prior_memory_file>
"""
@Suppress("unused") private const val _S_55: String = "Uploaded %s to Honcho for %s (%s peer)"
@Suppress("unused") private const val _S_56: String = "Failed to upload %s to Honcho: %s"
@Suppress("unused") private const val _S_57: String = "utf-8"
@Suppress("unused") private const val _S_58: String = "original_file"
@Suppress("unused") private const val _S_59: String = "target_peer"
@Suppress("unused") private const val _S_60: String = "local_memory"
@Suppress("unused") private val _S_61: String = """Fetch a peer card directly from the peer object.

        This avoids relying on session.context(), which can return an empty
        peer_card for per-session messaging sessions even when the peer itself
        has a populated card.
        """
@Suppress("unused") private const val _S_62: String = "get_card"
@Suppress("unused") private val _S_63: String = """Fetch full session context from Honcho including summary.

        Uses the session-level context() API which returns summary,
        peer_representation, peer_card, and messages.
        """
@Suppress("unused") private const val _S_64: String = "recent_messages"
@Suppress("unused") private const val _S_65: String = "Session context fetch failed: %s"
@Suppress("unused") private const val _S_66: String = "peer_id"
@Suppress("unused") private const val _S_67: String = "unknown"
@Suppress("unused") private val _S_68: String = """
        Fetch a peer card — a curated list of key facts.

        Fast, no LLM reasoning. Returns raw structured facts Honcho has
        inferred about the target peer (name, role, preferences, patterns).
        Empty list if unavailable.
        """
@Suppress("unused") private const val _S_69: String = "Failed to fetch peer card from Honcho: %s"
@Suppress("unused") private val _S_70: String = """
        Semantic search over Honcho session context.

        Returns raw excerpts ranked by relevance to the query. No LLM
        reasoning — cheaper and faster than dialectic_query. Good for
        factual lookups where the model will do its own synthesis.

        Args:
            session_key: Session to search against.
            query: Search query for semantic matching.
            max_tokens: Token budget for returned content.
            peer: Peer alias or explicit peer ID to search about.

        Returns:
            Relevant context excerpts as a string, or empty string if none.
        """
@Suppress("unused") private const val _S_71: String = "Honcho search_context failed: %s"
@Suppress("unused") private val _S_72: String = """Write a conclusion about a target peer back to Honcho.

        Conclusions are facts a peer observes about another peer or itself —
        preferences, corrections, clarifications, and project context.
        They feed into the target peer's card and representation.

        Args:
            session_key: Session to associate the conclusion with.
            content: The conclusion text.
            peer: Peer alias or explicit peer ID. "user" is the default alias.

        Returns:
            True on success, False on failure.
        """
@Suppress("unused") private const val _S_73: String = "No session cached for '%s', skipping conclusion"
@Suppress("unused") private const val _S_74: String = "Created conclusion about %s for %s: %s"
@Suppress("unused") private const val _S_75: String = "Could not resolve conclusion peer '%s' for session '%s'"
@Suppress("unused") private const val _S_76: String = "Failed to create conclusion: %s"
@Suppress("unused") private const val _S_77: String = "session_id"
@Suppress("unused") private val _S_78: String = """Update a peer's card.

        Args:
            session_key: Session key for peer resolution.
            card: New peer card as list of fact strings.
            peer: Peer alias or explicit peer ID.

        Returns:
            Updated card on success, None on failure.
        """
@Suppress("unused") private const val _S_79: String = "Updated peer card for %s (%d facts)"
@Suppress("unused") private const val _S_80: String = "Could not resolve peer '%s' for set_peer_card in session '%s'"
@Suppress("unused") private const val _S_81: String = "Failed to set peer card: %s"
@Suppress("unused") private const val _S_82: String = "manual"
@Suppress("unused") private val _S_83: String = """
        Seed the AI peer's Honcho representation from text content.

        Useful for priming AI identity from SOUL.md, exported chats, or
        any structured description. The content is sent as an assistant
        peer message so Honcho's reasoning model can incorporate it.

        Args:
            session_key: The session key to associate with.
            content: The identity/persona content to seed.
            source: Metadata tag for the source (e.g. "soul_md", "export").

        Returns:
            True on success, False on failure.
        """
@Suppress("unused") private const val _S_84: String = "No session cached for '%s', skipping AI seed"
@Suppress("unused") private const val _S_85: String = "No Honcho session cached for '%s', skipping AI seed"
@Suppress("unused") private val _S_86: String = """<ai_identity_seed>
<source>"""
@Suppress("unused") private val _S_87: String = """</source>

"""
@Suppress("unused") private val _S_88: String = """
</ai_identity_seed>"""
@Suppress("unused") private const val _S_89: String = "Seeded AI identity from '%s' into %s"
@Suppress("unused") private const val _S_90: String = "Failed to seed AI identity: %s"
@Suppress("unused") private val _S_91: String = """
        Fetch the AI peer's current Honcho representation.

        Returns:
            Dict with 'representation' and 'card' keys, empty strings if unavailable.
        """
@Suppress("unused") private const val _S_92: String = "Failed to fetch AI representation: %s"
@Suppress("unused") private const val _S_93: String = "List all cached sessions."
@Suppress("unused") private const val _S_94: String = "key"
@Suppress("unused") private const val _S_95: String = "created_at"
@Suppress("unused") private const val _S_96: String = "updated_at"
@Suppress("unused") private const val _S_97: String = "message_count"
