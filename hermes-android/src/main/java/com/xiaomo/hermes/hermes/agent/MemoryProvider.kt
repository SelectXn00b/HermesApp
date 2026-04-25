package com.xiaomo.hermes.hermes.agent

import android.util.Log

/**
 * Memory Provider - 记忆接口抽象类
 * 1:1 对齐 hermes/agent/memory_provider.py
 *
 * 定义记忆存储的抽象接口，支持多种后端实现。
 */

data class MemoryEntry(
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any>? = null
)

/**
 * 记忆提供者抽象接口
 */
interface MemoryProvider {

    /**
     * 存储一条记忆
     */
    suspend fun store(entry: MemoryEntry)

    /**
     * 根据 key 获取记忆
     */
    suspend fun recall(key: String): MemoryEntry?

    /**
     * 搜索记忆
     *
     * @param query 搜索关键词
     * @param category 可选的分类过滤
     * @param limit 最大返回数量
     * @return 匹配的记忆列表
     */
    suspend fun search(query: String, category: String? = null, limit: Int = 10): List<MemoryEntry>

    /**
     * 列出所有记忆
     *
     * @param category 可选的分类过滤
     * @param limit 最大返回数量
     * @return 记忆列表
     */
    suspend fun list(category: String? = null, limit: Int = 100): List<MemoryEntry>

    /**
     * 删除一条记忆
     */
    suspend fun delete(key: String): Boolean

    /**
     * 清空所有记忆
     */
    suspend fun clear()

    /**
     * 获取记忆总数
     */
    suspend fun count(): Int

    // ── Lifecycle / orchestration methods ──────────────────────────────

    /** Initialize for a session. */
    fun initialize(sessionId: String, kwargs: Any)

    /** Return text to include in the system prompt. */
    fun systemPromptBlock(): String

    /** Recall relevant context for the upcoming turn. */
    fun prefetch(query: String): String

    /** Queue a background recall for the NEXT turn. */
    fun queuePrefetch(query: String)

    /** Persist a completed turn to the backend. */
    fun syncTurn(userContent: String, assistantContent: String)

    /** Return tool schemas this provider exposes. */
    fun getToolSchemas(): List<Map<String, Any>>

    /** Handle a tool call for one of this provider's tools. */
    fun handleToolCall(toolName: String, args: Map<String, Any>, kwargs: Any): String

    /** Clean shutdown — flush queues, close connections. */
    fun shutdown()

    /** Called at the start of each turn with the user message. */
    fun onTurnStart(turnNumber: Int, message: String, kwargs: Any)

    /** Called when a session ends (explicit exit or timeout). */
    fun onSessionEnd(messages: List<Map<String, Any>>)

    /** Called before context compression discards old messages. */
    fun onPreCompress(messages: List<Map<String, Any>>): String

    /** Called on the PARENT agent when a subagent completes. */
    fun onDelegation(task: String, result: String, kwargs: Any)

    /** Called when the built-in memory tool writes an entry. */
    fun onMemoryWrite(action: String, target: String, content: String)
}

/**
 * 内存中的记忆提供者实现（用于测试和 Android 本地存储）
 *
 * 对齐 Python BuiltinMemoryProvider 的默认行为：
 * - name = "builtin"
 * - isAvailable = true（始终可用）
 * - 所有 lifecycle 方法提供合理的默认实现
 */
class InMemoryMemoryProvider : MemoryProvider {
    private val store = mutableMapOf<String, MemoryEntry>()
    private var initialized = false
    private var sessionId: String = ""

    companion object {
        private const val _TAG = "InMemoryMemoryProvider"
    }

    // ── MemoryEntry CRUD (suspend) ────────────────────────────────────

    override suspend fun store(entry: MemoryEntry) {
        store[entry.key] = entry
        Log.d(_TAG, "Stored entry key=${entry.key} category=${entry.category}")
    }

    override suspend fun recall(key: String): MemoryEntry? = store[key]

    override suspend fun search(query: String, category: String?, limit: Int): List<MemoryEntry> {
        return store.values
            .filter { entry ->
                (category == null || entry.category == category) &&
                (entry.key.contains(query, ignoreCase = true) || entry.value.contains(query, ignoreCase = true))
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun list(category: String?, limit: Int): List<MemoryEntry> {
        return store.values
            .filter { category == null || it.category == category }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun delete(key: String): Boolean = store.remove(key) != null

    override suspend fun clear() = store.clear()

    override suspend fun count(): Int = store.size

    // ── Lifecycle / orchestration ──────────────────────────────────────

    /** Short identifier for this provider (e.g. 'builtin', 'honcho', 'hindsight'). */
    fun name(): String = "builtin"

    /** Return True if this provider is configured, has credentials, and is ready.
     *  InMemory provider is always available — no external deps needed. */
    fun isAvailable(): Boolean = true

    /** Initialize for a session. */
    override fun initialize(sessionId: String, kwargs: Any) {
        this.sessionId = sessionId
        this.initialized = true
        Log.i(_TAG, "Initialized for session=$sessionId")
    }

    /** Return text to include in the system prompt.
     *  InMemory provider has no static system prompt contribution. */
    override fun systemPromptBlock(): String = ""

    /** Recall relevant context for the upcoming turn.
     *  Searches local store for entries matching the query. */
    override fun prefetch(query: String): String {
        if (query.isBlank()) return ""
        val results = store.values
            .filter { entry ->
                entry.key.contains(query, ignoreCase = true) ||
                entry.value.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.timestamp }
            .take(5)
        if (results.isEmpty()) return ""
        return buildString {
            appendLine("## Relevant memories")
            for (entry in results) {
                appendLine("- **${entry.key}**: ${entry.value}")
            }
        }
    }

    /** Queue a background recall for the NEXT turn.
     *  InMemory is synchronous — no background queue needed. */
    override fun queuePrefetch(query: String) {
        // No-op: InMemory recall is instant, no background queue needed
    }

    /** Persist a completed turn to the backend.
     *  InMemory doesn't persist turns — no-op. */
    override fun syncTurn(userContent: String, assistantContent: String) {
        // No-op: InMemory provider does not persist conversation turns
    }

    /** Return tool schemas this provider exposes.
     *  InMemory has no dedicated tools (CRUD is via the interface). */
    override fun getToolSchemas(): List<Map<String, Any>> = emptyList()

    /** Handle a tool call for one of this provider's tools.
     *  InMemory exposes no tools, so this always throws. */
    override fun handleToolCall(toolName: String, args: Map<String, Any>, kwargs: Any): String =
        throw NotImplementedError("Provider builtin does not handle tool $toolName")

    /** Clean shutdown — flush queues, close connections. */
    override fun shutdown() {
        Log.i(_TAG, "Shutting down, clearing ${store.size} entries")
        store.clear()
        initialized = false
    }

    /** Called at the start of each turn with the user message. */
    override fun onTurnStart(turnNumber: Int, message: String, kwargs: Any) {
        // No-op: InMemory provider doesn't need per-turn processing
    }

    /** Called when a session ends (explicit exit or timeout). */
    override fun onSessionEnd(messages: List<Map<String, Any>>) {
        Log.i(_TAG, "Session ended: $sessionId, ${store.size} entries in store")
    }

    /** Called before context compression discards old messages.
     *  Returns empty string — InMemory has no pre-compression extraction. */
    override fun onPreCompress(messages: List<Map<String, Any>>): String = ""

    /** Called on the PARENT agent when a subagent completes. */
    override fun onDelegation(task: String, result: String, kwargs: Any) {
        // No-op: InMemory provider doesn't process delegation events
    }

    /** Return config fields this provider needs for setup.
     *  InMemory is local-only, no config needed. */
    fun getConfigSchema(): List<Map<String, Any>> = emptyList()

    /** Write non-secret config to the provider's native location.
     *  InMemory has no config file — no-op. */
    fun saveConfig(values: Map<String, Any>, hermesHome: String) {
        // No-op: InMemory provider uses no config files
    }

    /** Called when the built-in memory tool writes an entry.
     *  Mirrors the write to the in-memory store. */
    override fun onMemoryWrite(action: String, target: String, content: String) {
        val key = "${target}_${System.currentTimeMillis()}"
        when (action) {
            "add" -> {
                store[key] = MemoryEntry(key = key, value = content, category = target)
                Log.d(_TAG, "onMemoryWrite add: key=$key target=$target")
            }
            "replace" -> {
                // Find most recent entry for this target and replace
                val existing = store.values
                    .filter { it.category == target }
                    .maxByOrNull { it.timestamp }
                if (existing != null) {
                    store[existing.key] = existing.copy(value = content, timestamp = System.currentTimeMillis())
                    Log.d(_TAG, "onMemoryWrite replace: key=${existing.key} target=$target")
                } else {
                    store[key] = MemoryEntry(key = key, value = content, category = target)
                    Log.d(_TAG, "onMemoryWrite replace (no existing): key=$key target=$target")
                }
            }
            "remove" -> {
                val removed = store.values
                    .filter { it.category == target && it.value == content }
                    .forEach { store.remove(it.key) }
                Log.d(_TAG, "onMemoryWrite remove: target=$target")
            }
        }
    }
}
