package com.xiaomo.androidforclaw.hermes.plugins.memory.honcho

import com.xiaomo.androidforclaw.hermes.getLogger
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
    private val _writeFrequency: String = config?.writeFrequency ?: "async"
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
    fun getPeerCard(sessionKey: String): List<String> {
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
    fun searchContext(sessionKey: String, query: String, maxTokens: Int = 800): String {
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
    fun createConclusion(sessionKey: String, content: String): Boolean {
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
}
