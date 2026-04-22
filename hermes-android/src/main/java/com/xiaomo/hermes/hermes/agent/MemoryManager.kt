package com.xiaomo.hermes.hermes.agent

/**
 * Memory Manager - 记忆管理器
 * 1:1 对齐 hermes/agent/memory_manager.py
 *
 * 管理 agent 的长期记忆，支持存储、检索、总结。
 */

class MemoryManager(
    private val provider: MemoryProvider = InMemoryMemoryProvider()
) {

    companion object {
        private const val _TAG = "MemoryManager"
    }

    private val _providers: MutableList<MemoryProvider> = mutableListOf()
    private val _toolToProvider: MutableMap<String, MemoryProvider> = mutableMapOf()
    private var _hasExternal: Boolean = false

    /**
     * 存储一条记忆
     *
     * @param key 记忆键
     * @param value 记忆值
     * @param category 分类
     */
    suspend fun remember(key: String, value: String, category: String = "general") {
        provider.store(MemoryEntry(key = key, value = value, category = category))
    }

    /**
     * 回忆一条记忆
     *
     * @param key 记忆键
     * @return 记忆值，不存在返回 null
     */
    suspend fun recall(key: String): String? {
        return provider.recall(key)?.value
    }

    /**
     * 搜索记忆
     *
     * @param query 搜索关键词
     * @param category 可选分类过滤
     * @param limit 最大返回数量
     * @return 匹配的记忆列表
     */
    suspend fun search(query: String, category: String? = null, limit: Int = 10): List<MemoryEntry> {
        return provider.search(query, category, limit)
    }

    /**
     * 删除一条记忆
     *
     * @param key 记忆键
     * @return 是否成功删除
     */
    suspend fun forget(key: String): Boolean {
        return provider.delete(key)
    }

    /**
     * 列出所有记忆
     *
     * @param category 可选分类过滤
     * @param limit 最大返回数量
     * @return 记忆列表
     */
    suspend fun listMemories(category: String? = null, limit: Int = 100): List<MemoryEntry> {
        return provider.list(category, limit)
    }

    /**
     * 生成记忆摘要
     *
     * @param category 可选分类过滤
     * @return 摘要文本
     */
    suspend fun summarize(category: String? = null): String {
        val memories = provider.list(category, limit = 50)
        if (memories.isEmpty()) return "No memories stored."

        val sb = StringBuilder()
        sb.appendLine("Memories (${memories.size} entries):")
        for (memory in memories) {
            sb.appendLine("- [${memory.category}] ${memory.key}: ${memory.value.take(100)}")
        }
        return sb.toString().trim()
    }

    /**
     * 清空所有记忆
     */
    suspend fun clearAll() {
        provider.clear()
    }

    /**
     * 获取记忆总数
     */
    suspend fun count(): Int {
        return provider.count()
    }



    /** Register a memory provider. */
    fun addProvider(provider: MemoryProvider): Unit {
        val isBuiltin = (provider as? InMemoryMemoryProvider) != null || provider == this.provider

        if (!isBuiltin) {
            if (_hasExternal) {
                val existing = _providers.firstOrNull { it !is InMemoryMemoryProvider && it != this.provider }
                android.util.Log.w(_TAG,
                    "Rejected memory provider — external provider is already registered. " +
                    "Only one external memory provider is allowed at a time."
                )
                return
            }
            _hasExternal = true
        }

        _providers.add(provider)

        // Index tool names → provider for routing
        try {
            for (schema in provider.getToolSchemas()) {
                val toolName = schema["name"] as? String ?: ""
                if (toolName.isNotEmpty() && !_toolToProvider.containsKey(toolName)) {
                    _toolToProvider[toolName] = provider
                }
            }
        } catch (_: Exception) { }

        android.util.Log.d(_TAG, "Memory provider registered (${provider.getToolSchemas().size} tools)")
    }

    /** All registered providers in order. */
    fun providers(): List<MemoryProvider> {
        return _providers.toList()
    }

    /** Get a provider by name, or None if not registered. */
    fun getProvider(name: String): MemoryProvider? {
        return _providers.firstOrNull { it.toString().contains(name) }
    }

    /** Collect system prompt blocks from all providers. */
    fun buildSystemPrompt(): String {
        val blocks = mutableListOf<String>()
        for (p in _providers) {
            try {
                val block = p.systemPromptBlock()
                if (block.isNotEmpty()) blocks.add(block)
            } catch (e: Exception) {
                android.util.Log.w(_TAG, "system_prompt_block() failed: ${e.message}")
            }
        }
        return blocks.joinToString("\n\n")
    }

    /** Collect prefetch context from all providers. */
    fun prefetchAll(query: String): String {
        val parts = mutableListOf<String>()
        for (p in _providers) {
            try {
                val result = p.prefetch(query)
                if (result.isNotEmpty()) parts.add(result)
            } catch (e: Exception) {
                android.util.Log.d(_TAG, "prefetch failed (non-fatal): ${e.message}")
            }
        }
        return parts.joinToString("\n\n")
    }

    /** Queue background prefetch on all providers for the next turn. */
    fun queuePrefetchAll(query: String): Unit {
        for (p in _providers) {
            try {
                p.queuePrefetch(query)
            } catch (e: Exception) {
                android.util.Log.d(_TAG, "queue_prefetch failed (non-fatal): ${e.message}")
            }
        }
    }

    /** Sync a completed turn to all providers. */
    fun syncAll(userContent: String, assistantContent: String): Unit {
        for (p in _providers) {
            try {
                p.syncTurn(userContent, assistantContent)
            } catch (e: Exception) {
                android.util.Log.w(_TAG, "sync_turn failed: ${e.message}")
            }
        }
    }

    /** Collect tool schemas from all providers. */
    fun getAllToolSchemas(): List<Map<String, Any>> {
        val schemas = mutableListOf<Map<String, Any>>()
        val seen = mutableSetOf<String>()
        for (p in _providers) {
            try {
                for (schema in p.getToolSchemas()) {
                    val name = schema["name"] as? String ?: ""
                    if (name.isNotEmpty() && name !in seen) {
                        schemas.add(schema)
                        seen.add(name)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(_TAG, "get_tool_schemas() failed: ${e.message}")
            }
        }
        return schemas
    }

    /** Return set of all tool names across all providers. */
    fun getAllToolNames(): Any? {
        return _toolToProvider.keys.toSet()
    }

    /** Check if any provider handles this tool. */
    fun hasTool(toolName: String): Boolean {
        return _toolToProvider.containsKey(toolName)
    }

    /** Route a tool call to the correct provider. */
    fun handleToolCall(toolName: String, args: Map<String, Any>, kwargs: Any): String {
        val p = _toolToProvider[toolName]
        if (p == null) {
            return "Error: No memory provider handles tool '$toolName'"
        }
        return try {
            p.handleToolCall(toolName, args, kwargs)
        } catch (e: Exception) {
            android.util.Log.e(_TAG, "handle_tool_call($toolName) failed: ${e.message}")
            "Error: Memory tool '$toolName' failed: ${e.message}"
        }
    }

    /** Notify all providers of a new turn. */
    fun onTurnStart(turnNumber: Int, message: String, kwargs: Any): Unit {
        for (p in _providers) {
            try {
                p.onTurnStart(turnNumber, message, kwargs)
            } catch (e: Exception) {
                android.util.Log.d(_TAG, "on_turn_start failed: ${e.message}")
            }
        }
    }

    /** Notify all providers of session end. */
    fun onSessionEnd(messages: List<Map<String, Any>>): Unit {
        for (p in _providers) {
            try {
                p.onSessionEnd(messages)
            } catch (e: Exception) {
                android.util.Log.d(_TAG, "on_session_end failed: ${e.message}")
            }
        }
    }

    /** Notify all providers before context compression. */
    fun onPreCompress(messages: List<Map<String, Any>>): String {
        val parts = mutableListOf<String>()
        for (p in _providers) {
            try {
                val result = p.onPreCompress(messages)
                if (result.isNotEmpty()) parts.add(result)
            } catch (e: Exception) {
                android.util.Log.d(_TAG, "on_pre_compress failed: ${e.message}")
            }
        }
        return parts.joinToString("\n\n")
    }

    /** Notify external providers when the built-in memory tool writes. */
    fun onMemoryWrite(action: String, target: String, content: String): Unit {
        for (p in _providers) {
            if (p is InMemoryMemoryProvider) continue
            try {
                p.onMemoryWrite(action, target, content)
            } catch (e: Exception) {
                android.util.Log.d(_TAG, "on_memory_write failed: ${e.message}")
            }
        }
    }

    /** Notify all providers that a subagent completed. */
    fun onDelegation(task: String, result: String, kwargs: Any): Unit {
        for (p in _providers) {
            try {
                p.onDelegation(task, result, kwargs)
            } catch (e: Exception) {
                android.util.Log.d(_TAG, "on_delegation failed: ${e.message}")
            }
        }
    }

    /** Shut down all providers (reverse order for clean teardown). */
    fun shutdownAll(): Unit {
        for (p in _providers.reversed()) {
            try {
                p.shutdown()
            } catch (e: Exception) {
                android.util.Log.w(_TAG, "shutdown failed: ${e.message}")
            }
        }
    }

    /** Initialize all providers. */
    fun initializeAll(sessionId: String, kwargs: Any): Unit {
        for (p in _providers) {
            try {
                p.initialize(sessionId, kwargs)
            } catch (e: Exception) {
                android.util.Log.w(_TAG, "initialize failed: ${e.message}")
            }
        }
    }

}

// ── Module-level aligned with agent/memory_manager.py ────────────────────

/** Regex matching code-fence tags inside assistant content. */
val _FENCE_TAG_RE: Regex = Regex("```[a-zA-Z0-9_+-]*")

/** Regex matching internal-context wrappers (stripped before model input). */
val _INTERNAL_CONTEXT_RE: Regex = Regex("<internal_context>.*?</internal_context>", RegexOption.DOT_MATCHES_ALL)

/** Regex matching internal-note wrappers. */
val _INTERNAL_NOTE_RE: Regex = Regex("<internal_note>.*?</internal_note>", RegexOption.DOT_MATCHES_ALL)

/** Strip internal/metadata wrappers from agent context before model input. */
fun sanitizeContext(content: String): String {
    var out = _INTERNAL_CONTEXT_RE.replace(content, "")
    out = _INTERNAL_NOTE_RE.replace(out, "")
    return out
}

/** Build a per-turn memory-context block injected into the system prompt. */
@Suppress("UNUSED_PARAMETER")
fun buildMemoryContextBlock(sessionKey: String, userId: String? = null): String = ""
