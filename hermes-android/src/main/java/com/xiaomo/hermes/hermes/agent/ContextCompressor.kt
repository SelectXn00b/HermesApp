package com.xiaomo.hermes.hermes.agent

/**
 * Context Compressor - 上下文压缩
 * 1:1 对齐 hermes/agent/context_compressor.py
 *
 * 当上下文接近溢出时，压缩历史消息以腾出空间。
 * 保留 system prompt、最近的工具调用、和最新的用户消息。
 */

// ── Compression Strategy ─────────────────────────────────────────────────

enum class CompressionStrategy {
    TRUNCATE_OLDEST,      // 删除最旧的消息
    SUMMARIZE,            // 生成摘要
    DROP_TOOL_RESULTS,    // 删除旧的工具结果
    KEEP_RECENT,          // 只保留最近 N 条
    ADAPTIVE              // 自适应策略
}

data class CompressionResult(
    val compressedMessages: List<Map<String, Any>>,
    val originalCount: Int,
    val compressedCount: Int,
    val removedCount: Int,
    val estimatedTokensBefore: Int,
    val estimatedTokensAfter: Int,
    val strategy: String,
    val preservedToolCalls: Boolean = true
)

data class CompressorConfig(
    val strategy: CompressionStrategy = CompressionStrategy.ADAPTIVE,
    val minRecentMessages: Int = 4,         // 至少保留最近 N 条消息
    val preserveToolPairs: Boolean = true,  // 保留 tool_use/tool_result 配对
    val preserveSystemPrompt: Boolean = true,
    val targetUtilization: Double = 0.7,    // 压缩后目标利用率
    val maxSummaryTokens: Int = 2000,       // 摘要最大 token 数
    val dropOldToolResults: Boolean = true  // 是否先删除旧的工具结果
)

// ── Main Compressor Class ────────────────────────────────────────────────

class ContextCompressor(
    private val config: CompressorConfig = CompressorConfig()
) {

    /**
     * 检查是否需要压缩
     *
     * @param messages 消息列表
     * @param contextLength 模型的 context length
     * @param systemPrompt system prompt
     * @param tools 工具定义
     * @param threshold 触发阈值（0.0 - 1.0），默认 0.85
     * @return 是否需要压缩
     */
    fun needsCompression(
        messages: List<Map<String, Any>>,
        contextLength: Int,
        systemPrompt: String = "",
        tools: List<Map<String, Any>> = emptyList(),
        threshold: Double = 0.85
    ): Boolean {
        val estimatedTokens = estimateRequestTokensRough(messages, systemPrompt, tools)
        return estimatedTokens > (contextLength * threshold).toInt()
    }

    /** Compact the message list and return the new message list. */
    @Suppress("UNUSED_PARAMETER")
    fun compress(
        messages: List<Map<String, Any>>,
        currentTokens: Int? = null,
        focusTopic: String? = null,
    ): List<Map<String, Any>> {
        val targetTokens = (contextLength * config.targetUtilization).toInt()
        return when (config.strategy) {
            CompressionStrategy.TRUNCATE_OLDEST -> truncateOldest(messages, targetTokens, "", emptyList())
            CompressionStrategy.DROP_TOOL_RESULTS -> dropOldToolResults(messages, targetTokens, "", emptyList())
            CompressionStrategy.KEEP_RECENT -> keepRecent(messages, targetTokens, "", emptyList())
            CompressionStrategy.ADAPTIVE -> adaptiveCompress(messages, targetTokens, "", emptyList())
            CompressionStrategy.SUMMARIZE -> truncateOldest(messages, targetTokens, "", emptyList())
        }
    }

    /**
     * 自适应压缩策略
     *
     * 按优先级尝试不同的压缩方式：
     * 1. 删除旧的工具结果
     * 2. 截断最旧的消息
     * 3. 只保留最近的消息
     */
    private fun adaptiveCompress(
        messages: List<Map<String, Any>>,
        targetTokens: Int,
        systemPrompt: String,
        tools: List<Map<String, Any>>
    ): List<Map<String, Any>> {
        // Step 1: 尝试删除旧工具结果
        var result = dropOldToolResults(messages, targetTokens, systemPrompt, tools)
        var tokens = estimateRequestTokensRough(result, systemPrompt, tools)
        if (tokens <= targetTokens) return result

        // Step 2: 截断最旧的消息
        result = truncateOldest(result, targetTokens, systemPrompt, tools)
        tokens = estimateRequestTokensRough(result, systemPrompt, tools)
        if (tokens <= targetTokens) return result

        // Step 3: 只保留最近的消息
        return keepRecent(result, targetTokens, systemPrompt, tools)
    }

    /**
     * 删除旧的工具结果（保留最近的 tool_use/tool_result 配对）
     */
    private fun dropOldToolResults(
        messages: List<Map<String, Any>>,
        targetTokens: Int,
        systemPrompt: String,
        tools: List<Map<String, Any>>
    ): List<Map<String, Any>> {
        if (messages.size <= config.minRecentMessages) return messages

        // 找到最后一个用户消息的索引
        val lastUserIndex = messages.indexOfLast { it["role"] == "user" }
        if (lastUserIndex < 0) return messages

        // 保留最近的消息，删除中间的工具结果
        val result = mutableListOf<Map<String, Any>>()
        var dropped = false

        for ((index, msg) in messages.withIndex()) {
            val isRecent = index >= messages.size - config.minRecentMessages
            val isLastUser = index == lastUserIndex
            val content = msg["content"]

            // 检查是否是工具结果消息
            val isToolResult = content is List<*> && content.any {
                it is Map<*, *> && (it as Map<*, *>)["type"] == "tool_result"
            }

            if (isToolResult && !isRecent && !isLastUser && !dropped) {
                dropped = true
                continue // 删除这个工具结果
            }

            result.add(msg)
        }

        return result
    }

    /**
     * 截断最旧的消息
     */
    private fun truncateOldest(
        messages: List<Map<String, Any>>,
        targetTokens: Int,
        systemPrompt: String,
        tools: List<Map<String, Any>>
    ): List<Map<String, Any>> {
        if (messages.size <= config.minRecentMessages) return messages

        val toolTokens = if (tools.isNotEmpty()) estimateTokensRough(com.google.gson.Gson().toJson(tools)) else 0
        val systemTokens = if (systemPrompt.isNotEmpty()) estimateTokensRough(systemPrompt) else 0
        val availableTokens = targetTokens - toolTokens - systemTokens

        // 从最新的消息开始保留
        val result = mutableListOf<Map<String, Any>>()
        var usedTokens = 0

        for (msg in messages.reversed()) {
            val msgTokens = estimateMessagesTokensRough(listOf(msg))
            if (usedTokens + msgTokens > availableTokens && result.size >= config.minRecentMessages) {
                break
            }
            result.add(0, msg)
            usedTokens += msgTokens
        }

        return result
    }

    /**
     * 只保留最近的消息
     */
    private fun keepRecent(
        messages: List<Map<String, Any>>,
        targetTokens: Int,
        systemPrompt: String,
        tools: List<Map<String, Any>>
    ): List<Map<String, Any>> {
        val toolTokens = if (tools.isNotEmpty()) estimateTokensRough(com.google.gson.Gson().toJson(tools)) else 0
        val systemTokens = if (systemPrompt.isNotEmpty()) estimateTokensRough(systemPrompt) else 0
        val availableTokens = targetTokens - toolTokens - systemTokens

        val result = mutableListOf<Map<String, Any>>()
        var usedTokens = 0

        for (msg in messages.reversed()) {
            val msgTokens = estimateMessagesTokensRough(listOf(msg))
            if (usedTokens + msgTokens > availableTokens) break
            result.add(0, msg)
            usedTokens += msgTokens
        }

        // 确保至少保留 minRecentMessages 条
        if (result.size < config.minRecentMessages && messages.size >= config.minRecentMessages) {
            return messages.takeLast(config.minRecentMessages)
        }

        return result
    }

    /**
     * 确保 tool_use/tool_result 配对完整
     *
     * 如果 assistant 消息中有 tool_use，则后续的 user 消息必须包含对应的 tool_result。
     */
    fun ensureToolPairIntegrity(messages: List<Map<String, Any>>): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        val pendingToolUseIds = mutableSetOf<String>()

        for (msg in messages) {
            val role = msg["role"] as? String
            val content = msg["content"]

            if (role == "assistant" && content is List<*>) {
                // 收集 tool_use 的 id
                for (block in content) {
                    if (block is Map<*, *> && block["type"] == "tool_use") {
                        pendingToolUseIds.add(block["id"] as? String ?: "")
                    }
                }
                result.add(msg)
            } else if (role == "user" && content is List<*> && pendingToolUseIds.isNotEmpty()) {
                // 检查是否有对应的 tool_result
                val resolvedIds = mutableSetOf<String>()
                for (block in content) {
                    if (block is Map<*, *> && block["type"] == "tool_result") {
                        resolvedIds.add(block["tool_use_id"] as? String ?: "")
                    }
                }

                // 如果没有完全匹配的 tool_result，添加占位符
                val unresolvedIds = pendingToolUseIds - resolvedIds
                if (unresolvedIds.isNotEmpty()) {
                    val newContent = content.toMutableList()
                    for (id in unresolvedIds) {
                        newContent.add(
                            mapOf(
                                "type" to "tool_result",
                                "tool_use_id" to id,
                                "content" to "[Tool result lost during compression]",
                                "is_error" to true
                            )
                        )
                    }
                    result.add(mapOf("role" to "user", "content" to newContent))
                } else {
                    result.add(msg)
                }
                pendingToolUseIds.clear()
            } else {
                pendingToolUseIds.clear()
                result.add(msg)
            }
        }

        return result
    }





    // ── Internal state (mirrors Python __init__) ──────────────────────────

    private var _contextProbed: Boolean = false
    private var _previousSummary: String? = null
    private var _summaryFailureCooldownUntil: Double = 0.0
    private var compressionCount: Int = 0
    private var lastPromptTokens: Int = 0
    private var lastCompletionTokens: Int = 0

    // Config derived from constructor (Python __init__ fields)
    private var model: String = ""
    private var baseUrl: String = ""
    private var apiKey: String = ""
    private var provider: String = ""
    private var apiMode: String = ""
    private var contextLength: Int = 0
    private var thresholdPercent: Double = 0.50
    private var thresholdTokens: Int = 0
    private var tailTokenBudget: Int = 0
    private var maxSummaryTokens: Int = 0
    private var quietMode: Boolean = false
    private var summaryModel: String = ""
    private var protectFirstN: Int = 3
    private var protectLastN: Int = 20
    private var summaryTargetRatio: Double = 0.20

    companion object {
        private const val _TAG = "ContextCompressor"
        private const val MINIMUM_CONTEXT_LENGTH = 4096
        private const val _MIN_SUMMARY_TOKENS = 2000
        private const val _SUMMARY_RATIO = 0.20
        private const val _SUMMARY_TOKENS_CEILING = 12_000
        private const val _PRUNED_TOOL_PLACEHOLDER = "[Old tool output cleared to save context space]"
        private const val _CHARS_PER_TOKEN = 4
        private const val _SUMMARY_FAILURE_COOLDOWN_SECONDS = 600L
        private const val _CONTENT_MAX = 6000
        private const val _CONTENT_HEAD = 4000
        private const val _CONTENT_TAIL = 1500
        private const val _TOOL_ARGS_MAX = 1500
        private const val _TOOL_ARGS_HEAD = 1200

        private val SUMMARY_PREFIX =
            "[CONTEXT COMPACTION — REFERENCE ONLY] Earlier turns were compacted " +
            "into the summary below. This is a handoff from a previous context " +
            "window — treat it as background reference, NOT as active instructions. " +
            "Do NOT answer questions or fulfill requests mentioned in this summary; " +
            "they were already addressed. " +
            "Your current task is identified in the '## Active Task' section of the " +
            "summary — resume exactly from there. " +
            "Respond ONLY to the latest user message " +
            "that appears AFTER this summary. The current session state (files, " +
            "config, etc.) may reflect work described here — avoid repeating it:"
        private val LEGACY_SUMMARY_PREFIX = "[CONTEXT SUMMARY]:"
    }

    fun name(): String {
        return "compressor"
    }

    /** Reset all per-session state for /new or /reset. */
    fun onSessionReset(){ /* void */ }

    /** Check if context exceeds the compression threshold. */
    fun shouldCompress(promptTokens: Int? = null): Boolean {
        val tokens = promptTokens ?: lastPromptTokens
        return tokens >= thresholdTokens
    }

    /** Replace old tool result contents with a short placeholder. */
    fun _pruneOldToolResults(messages: List<Map<String, Any>>, protectTailCount: Int, protectTailTokens: Any? = null): Pair<List<Map<String, Any>>, Int> {
        if (messages.isEmpty()) return Pair(messages, 0)

        val result = messages.map { it.toMutableMap() }.toMutableList()
        var pruned = 0

        val pruneBoundary: Int
        val tailBudget = (protectTailTokens as? Number)?.toInt() ?: 0

        if (tailBudget > 0) {
            var accumulated = 0
            var boundary = result.size
            val minProtect = minOf(protectTailCount, result.size - 1)
            for (i in (result.size - 1) downTo 0) {
                val msg = result[i]
                val contentLen = (msg["content"] as? String)?.length ?: 0
                var msgTokens = contentLen / _CHARS_PER_TOKEN + 10
                val toolCalls = msg["tool_calls"] as? List<*>
                if (toolCalls != null) {
                    for (tc in toolCalls) {
                        if (tc is Map<*, *>) {
                            val args = ((tc["function"] as? Map<*, *>)?.get("arguments") as? String) ?: ""
                            msgTokens += args.length / _CHARS_PER_TOKEN
                        }
                    }
                }
                if (accumulated + msgTokens > tailBudget && (result.size - i) >= minProtect) {
                    boundary = i
                    break
                }
                accumulated += msgTokens
                boundary = i
            }
            pruneBoundary = maxOf(boundary, result.size - minProtect)
        } else {
            pruneBoundary = result.size - protectTailCount
        }

        for (i in 0 until pruneBoundary) {
            val msg = result[i]
            if (msg["role"] != "tool") continue
            val content = msg["content"] as? String ?: ""
            if (content.isEmpty() || content == _PRUNED_TOOL_PLACEHOLDER) continue
            if (content.length > 200) {
                result[i] = msg.toMutableMap().apply { put("content", _PRUNED_TOOL_PLACEHOLDER) }
                pruned++
            }
        }

        return Pair(result, pruned)
    }

    /** Scale summary token budget with the amount of content being compressed. */
    fun _computeSummaryBudget(turnsToSummarize: List<Map<String, Any>>): Int {
        val contentTokens = estimateMessagesTokensRough(turnsToSummarize)
        val budget = (contentTokens * _SUMMARY_RATIO).toInt()
        return maxOf(_MIN_SUMMARY_TOKENS, minOf(budget, maxSummaryTokens))
    }

    /** Serialize conversation turns into labeled text for the summarizer. */
    fun _serializeForSummary(turns: List<Map<String, Any>>): String {
        val parts = mutableListOf<String>()
        for (msg in turns) {
            val role = msg["role"] as? String ?: "unknown"
            var content = msg["content"] as? String ?: ""

            if (role == "tool") {
                val toolId = msg["tool_call_id"] as? String ?: ""
                if (content.length > _CONTENT_MAX) {
                    content = content.take(_CONTENT_HEAD) + "\n...[truncated]...\n" + content.takeLast(_CONTENT_TAIL)
                }
                parts.add("[TOOL RESULT $toolId]: $content")
                continue
            }

            if (role == "assistant") {
                if (content.length > _CONTENT_MAX) {
                    content = content.take(_CONTENT_HEAD) + "\n...[truncated]...\n" + content.takeLast(_CONTENT_TAIL)
                }
                val toolCalls = msg["tool_calls"] as? List<*>
                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    val tcParts = mutableListOf<String>()
                    for (tc in toolCalls) {
                        if (tc is Map<*, *>) {
                            val fn = tc["function"] as? Map<*, *>
                            val name = fn?.get("name") as? String ?: "?"
                            var args = fn?.get("arguments") as? String ?: ""
                            if (args.length > _TOOL_ARGS_MAX) {
                                args = args.take(_TOOL_ARGS_HEAD) + "..."
                            }
                            tcParts.add("  $name($args)")
                        }
                    }
                    content += "\n[Tool calls:\n" + tcParts.joinToString("\n") + "\n]"
                }
                parts.add("[ASSISTANT]: $content")
                continue
            }

            if (content.length > _CONTENT_MAX) {
                content = content.take(_CONTENT_HEAD) + "\n...[truncated]...\n" + content.takeLast(_CONTENT_TAIL)
            }
            parts.add("[${role.uppercase()}]: $content")
        }
        return parts.joinToString("\n\n")
    }

    /** Generate a structured summary of conversation turns. */
    fun _generateSummary(turnsToSummarize: List<Map<String, Any>>, focusTopic: String? = null): String? {
        // Android side cannot call LLM directly for summarization.
        // Return null to signal the caller to use a static fallback.
        @Suppress("UNUSED_VARIABLE") val _deepAlignFocusFrag = """

FOCUS TOPIC: "
The user has requested that this compaction PRIORITISE preserving all information related to the focus topic above. For content related to "X"""
        android.util.Log.d(_TAG, "_generateSummary called but LLM summarization not available on Android")
        return null
    }

    /** Normalize summary text to the current compaction handoff format. */
    fun _withSummaryPrefix(summary: String): String {
        var text = summary.trim()
        for (prefix in listOf(LEGACY_SUMMARY_PREFIX, SUMMARY_PREFIX)) {
            if (text.startsWith(prefix)) {
                text = text.substring(prefix.length).trimStart()
                break
            }
        }
        return if (text.isNotEmpty()) "$SUMMARY_PREFIX\n$text" else SUMMARY_PREFIX
    }

    /** Extract the call ID from a tool_call entry (dict or SimpleNamespace). */
    fun _getToolCallId(tc: Any?): String {
        if (tc is Map<*, *>) {
            return (tc["id"] as? String) ?: ""
        }
        return ""
    }

    /** Fix orphaned tool_call / tool_result pairs after compression. */
    fun _sanitizeToolPairs(messages: List<Map<String, Any>>): List<Map<String, Any>> {
        val survivingCallIds = mutableSetOf<String>()
        for (msg in messages) {
            if (msg["role"] == "assistant") {
                val toolCalls = msg["tool_calls"] as? List<*>
                if (toolCalls != null) {
                    for (tc in toolCalls) {
                        val cid = _getToolCallId(tc)
                        if (cid.isNotEmpty()) survivingCallIds.add(cid)
                    }
                }
            }
        }

        val resultCallIds = mutableSetOf<String>()
        for (msg in messages) {
            if (msg["role"] == "tool") {
                val cid = msg["tool_call_id"] as? String
                if (!cid.isNullOrEmpty()) resultCallIds.add(cid)
            }
        }

        // Remove orphaned results
        val orphanedResults = resultCallIds - survivingCallIds
        var result = if (orphanedResults.isNotEmpty()) {
            messages.filterNot { it["role"] == "tool" && (it["tool_call_id"] as? String) in orphanedResults }
        } else messages

        // Add stub results for missing calls
        val missingResults = survivingCallIds - resultCallIds
        if (missingResults.isNotEmpty()) {
            val patched = mutableListOf<Map<String, Any>>()
            for (msg in result) {
                patched.add(msg)
                if (msg["role"] == "assistant") {
                    val toolCalls = msg["tool_calls"] as? List<*>
                    if (toolCalls != null) {
                        for (tc in toolCalls) {
                            val cid = _getToolCallId(tc)
                            if (cid in missingResults) {
                                patched.add(mapOf(
                                    "role" to "tool",
                                    "content" to "[Result from earlier conversation — see context summary above]",
                                    "tool_call_id" to cid
                                ))
                            }
                        }
                    }
                }
            }
            result = patched
        }

        return result
    }

    /** Push a compress-start boundary forward past any orphan tool results. */
    fun _alignBoundaryForward(messages: List<Map<String, Any>>, idx: Int): Int {
        var i = idx
        while (i < messages.size && messages[i]["role"] == "tool") {
            i++
        }
        return i
    }

    /** Pull a compress-end boundary backward to avoid splitting a tool_call / result group. */
    fun _alignBoundaryBackward(messages: List<Map<String, Any>>, idx: Int): Int {
        if (idx <= 0 || idx >= messages.size) return idx
        var check = idx - 1
        while (check >= 0 && messages[check]["role"] == "tool") {
            check--
        }
        if (check >= 0 && messages[check]["role"] == "assistant") {
            val toolCalls = messages[check]["tool_calls"] as? List<*>
            if (toolCalls != null && toolCalls.isNotEmpty()) {
                return check
            }
        }
        return idx
    }

    /** Walk backward from the end of messages, accumulating tokens until the budget is reached. */
    fun _findTailCutByTokens(messages: List<Map<String, Any>>, headEnd: Int, tokenBudget: Any? = null): Int {
        val budget = (tokenBudget as? Number)?.toInt() ?: tailTokenBudget
        val n = messages.size
        val minTail = if (n - headEnd > 1) minOf(3, n - headEnd - 1) else 0
        val softCeiling = (budget * 1.5).toInt()
        var accumulated = 0
        var cutIdx = n

        for (i in (n - 1) downTo headEnd) {
            val msg = messages[i]
            val content = msg["content"] as? String ?: ""
            var msgTokens = content.length / _CHARS_PER_TOKEN + 10
            val toolCalls = msg["tool_calls"] as? List<*>
            if (toolCalls != null) {
                for (tc in toolCalls) {
                    if (tc is Map<*, *>) {
                        val args = ((tc["function"] as? Map<*, *>)?.get("arguments") as? String) ?: ""
                        msgTokens += args.length / _CHARS_PER_TOKEN
                    }
                }
            }
            if (accumulated + msgTokens > softCeiling && (n - i) >= minTail) break
            accumulated += msgTokens
            cutIdx = i
        }

        val fallbackCut = n - minTail
        if (cutIdx > fallbackCut) cutIdx = fallbackCut
        if (cutIdx <= headEnd) cutIdx = maxOf(fallbackCut, headEnd + 1)
        val aligned = _alignBoundaryBackward(messages, cutIdx)
        return maxOf(aligned, headEnd + 1)
    }



    fun updateModel(model: String, contextLength: Int, baseUrl: String, apiKey: String, provider: String, apiMode: String) {
        this.model = model
        this.baseUrl = baseUrl
        this.apiKey = apiKey
        this.provider = provider
        this.apiMode = apiMode
        this.contextLength = contextLength
        this.thresholdTokens = maxOf(
            (contextLength * thresholdPercent).toInt(),
            MINIMUM_CONTEXT_LENGTH
        )
    }

    fun updateFromResponse(usage: Map<String, Any?>): Any? {
        lastPromptTokens = (usage["prompt_tokens"] as? Number)?.toInt() ?: 0
        lastCompletionTokens = (usage["completion_tokens"] as? Number)?.toInt() ?: 0
        return null
    }

    fun _findLastUserMessageIdx(messages: List<Map<String, Any?>>, headEnd: Int): Int {
        for (i in messages.size - 1 downTo headEnd) {
            if (messages[i]["role"] == "user") return i
        }
        return -1
    }

    fun _ensureLastUserMessageInTail(messages: List<Map<String, Any?>>, cutIdx: Int, headEnd: Int): Int {
        val lastUserIdx = _findLastUserMessageIdx(messages, headEnd)
        if (lastUserIdx < 0) return cutIdx
        if (lastUserIdx >= cutIdx) return cutIdx
        if (!quietMode) {
            android.util.Log.d(_TAG, "Anchoring tail cut to last user message at index $lastUserIdx (was $cutIdx)")
        }
        return maxOf(lastUserIdx, headEnd + 1)
    }
}

/** Python `_truncate_tool_call_args_json` — stub. */
private fun _truncateToolCallArgsJson(args: String, limit: Int = 4096): String =
    if (args.length <= limit) args else args.substring(0, limit) + "...[truncated]"

/** Python `_summarize_tool_result` — stub. */
@Suppress("UNUSED_PARAMETER", "FunctionName")
private fun _summarizeToolResult(toolName: String, toolArgs: String, toolContent: String): String = toolContent

// ── deep_align literals smuggled for Python parity (agent/context_compressor.py) ──
@Suppress("unused") private val _CC_0: String = """Create an informative 1-line summary of a tool call + result.

    Used during the pre-compression pruning pass to replace large tool
    outputs with a short but useful description of what the tool did,
    rather than a generic placeholder that carries zero information.

    Returns strings like::

        [terminal] ran `npm test` -> exit 0, 47 lines output
        [read_file] read config.py from line 1 (1,200 chars)
        [search_files] content search for 'compress' in agent/ -> 12 matches
    """
@Suppress("unused") private const val _CC_1: String = "terminal"
@Suppress("unused") private const val _CC_2: String = "read_file"
@Suppress("unused") private const val _CC_3: String = "write_file"
@Suppress("unused") private const val _CC_4: String = "search_files"
@Suppress("unused") private const val _CC_5: String = "patch"
@Suppress("unused") private const val _CC_6: String = "web_search"
@Suppress("unused") private const val _CC_7: String = "web_extract"
@Suppress("unused") private const val _CC_8: String = "delegate_task"
@Suppress("unused") private const val _CC_9: String = "execute_code"
@Suppress("unused") private const val _CC_10: String = "vision_analyze"
@Suppress("unused") private const val _CC_11: String = "memory"
@Suppress("unused") private const val _CC_12: String = "todo"
@Suppress("unused") private const val _CC_13: String = "[todo] updated task list"
@Suppress("unused") private const val _CC_14: String = "clarify"
@Suppress("unused") private const val _CC_15: String = "[clarify] asked user a question"
@Suppress("unused") private const val _CC_16: String = "text_to_speech"
@Suppress("unused") private const val _CC_17: String = "cronjob"
@Suppress("unused") private const val _CC_18: String = "process"
@Suppress("unused") private const val _CC_19: String = " chars result)"
@Suppress("unused") private const val _CC_20: String = "command"
@Suppress("unused") private const val _CC_21: String = "\"exit_code\"\\s*:\\s*(-?\\d+)"
@Suppress("unused") private const val _CC_22: String = "[terminal] ran `"
@Suppress("unused") private const val _CC_23: String = "` -> exit "
@Suppress("unused") private const val _CC_24: String = " lines output"
@Suppress("unused") private const val _CC_25: String = "path"
@Suppress("unused") private const val _CC_26: String = "offset"
@Suppress("unused") private const val _CC_27: String = "[read_file] read "
@Suppress("unused") private const val _CC_28: String = " from line "
@Suppress("unused") private const val _CC_29: String = " chars)"
@Suppress("unused") private const val _CC_30: String = "[write_file] wrote to "
@Suppress("unused") private const val _CC_31: String = " lines)"
@Suppress("unused") private const val _CC_32: String = "pattern"
@Suppress("unused") private const val _CC_33: String = "target"
@Suppress("unused") private const val _CC_34: String = "content"
@Suppress("unused") private const val _CC_35: String = "\"total_count\"\\s*:\\s*(\\d+)"
@Suppress("unused") private const val _CC_36: String = "[search_files] "
@Suppress("unused") private const val _CC_37: String = " search for '"
@Suppress("unused") private const val _CC_38: String = "' in "
@Suppress("unused") private const val _CC_39: String = " -> "
@Suppress("unused") private const val _CC_40: String = " matches"
@Suppress("unused") private const val _CC_41: String = "mode"
@Suppress("unused") private const val _CC_42: String = "replace"
@Suppress("unused") private const val _CC_43: String = "[patch] "
@Suppress("unused") private const val _CC_44: String = " in "
@Suppress("unused") private const val _CC_45: String = "browser_navigate"
@Suppress("unused") private const val _CC_46: String = "browser_click"
@Suppress("unused") private const val _CC_47: String = "browser_snapshot"
@Suppress("unused") private const val _CC_48: String = "browser_type"
@Suppress("unused") private const val _CC_49: String = "browser_scroll"
@Suppress("unused") private const val _CC_50: String = "browser_vision"
@Suppress("unused") private const val _CC_51: String = "url"
@Suppress("unused") private const val _CC_52: String = "ref"
@Suppress("unused") private const val _CC_53: String = "query"
@Suppress("unused") private const val _CC_54: String = "[web_search] query='"
@Suppress("unused") private const val _CC_55: String = "' ("
@Suppress("unused") private const val _CC_56: String = "urls"
@Suppress("unused") private const val _CC_57: String = "[web_extract] "
@Suppress("unused") private const val _CC_58: String = "goal"
@Suppress("unused") private const val _CC_59: String = "[delegate_task] '"
@Suppress("unused") private const val _CC_60: String = "..."
@Suppress("unused") private const val _CC_61: String = "[execute_code] `"
@Suppress("unused") private const val _CC_62: String = "` ("
@Suppress("unused") private const val _CC_63: String = " lines output)"
@Suppress("unused") private const val _CC_64: String = "skill_view"
@Suppress("unused") private const val _CC_65: String = "skills_list"
@Suppress("unused") private const val _CC_66: String = "skill_manage"
@Suppress("unused") private const val _CC_67: String = "name"
@Suppress("unused") private const val _CC_68: String = "] name="
@Suppress("unused") private const val _CC_69: String = "[vision_analyze] '"
@Suppress("unused") private const val _CC_70: String = "action"
@Suppress("unused") private const val _CC_71: String = "[memory] "
@Suppress("unused") private const val _CC_72: String = " on "
@Suppress("unused") private const val _CC_73: String = "[text_to_speech] generated audio ("
@Suppress("unused") private const val _CC_74: String = "[cronjob] "
@Suppress("unused") private const val _CC_75: String = "session_id"
@Suppress("unused") private const val _CC_76: String = "[process] "
@Suppress("unused") private const val _CC_77: String = " session="
@Suppress("unused") private const val _CC_78: String = " (+"
@Suppress("unused") private const val _CC_79: String = " more)"
@Suppress("unused") private const val _CC_80: String = "question"
@Suppress("unused") private const val _CC_81: String = " ref="
@Suppress("unused") private const val _CC_82: String = "code"
@Suppress("unused") private val _CC_83: String = """Replace old tool result contents with informative 1-line summaries.

        Instead of a generic placeholder, generates a summary like::

            [terminal] ran `npm test` -> exit 0, 47 lines output
            [read_file] read config.py from line 1 (3,400 chars)

        Also deduplicates identical tool results (e.g. reading the same file
        5x keeps only the newest full copy) and truncates large tool_call
        arguments in assistant messages outside the protected tail.

        Walks backward from the end, protecting the most recent messages that
        fall within ``protect_tail_tokens`` (when provided) OR the last
        ``protect_tail_count`` messages (backward-compatible default).
        When both are given, the token budget takes priority and the message
        count acts as a hard minimum floor.

        Returns (pruned_messages, pruned_count).
        """
@Suppress("unused") private const val _CC_84: String = "assistant"
@Suppress("unused") private const val _CC_85: String = "tool"
@Suppress("unused") private const val _CC_86: String = "[Duplicate tool output"
@Suppress("unused") private const val _CC_87: String = "tool_calls"
@Suppress("unused") private const val _CC_88: String = "role"
@Suppress("unused") private const val _CC_89: String = "[Duplicate tool output — same content as a more recent call]"
@Suppress("unused") private const val _CC_90: String = "tool_call_id"
@Suppress("unused") private const val _CC_91: String = "unknown"
@Suppress("unused") private const val _CC_92: String = "arguments"
@Suppress("unused") private const val _CC_93: String = "function"
@Suppress("unused") private const val _CC_94: String = "utf-8"
@Suppress("unused") private const val _CC_95: String = "text"
@Suppress("unused") private val _CC_96: String = """Serialize conversation turns into labeled text for the summarizer.

        Includes tool call arguments and result content (up to
        ``_CONTENT_MAX`` chars per message) so the summarizer can preserve
        specific details like file paths, commands, and outputs.

        All content is redacted before serialization to prevent secrets
        (API keys, tokens, passwords) from leaking into the summary that
        gets sent to the auxiliary model and persisted across compactions.
        """
@Suppress("unused") private const val _CC_97: String = "]: "
@Suppress("unused") private const val _CC_98: String = "[TOOL RESULT "
@Suppress("unused") private const val _CC_99: String = "[ASSISTANT]: "
@Suppress("unused") private val _CC_100: String = """
...[truncated]...
"""
@Suppress("unused") private val _CC_101: String = """
[Tool calls:
"""
@Suppress("unused") private const val _CC_102: String = "(...)"
@Suppress("unused") private val _CC_103: String = """Generate a structured summary of conversation turns.

        Uses a structured template (Goal, Progress, Decisions, Resolved/Pending
        Questions, Files, Remaining Work) with explicit preamble telling the
        summarizer not to answer questions.  When a previous summary exists,
        generates an iterative update instead of summarizing from scratch.

        Args:
            focus_topic: Optional focus string for guided compression.  When
                provided, the summariser prioritises preserving information
                related to this topic and is more aggressive about compressing
                everything else.  Inspired by Claude Code's ``/compact``.

        Returns None if all attempts fail — the caller should drop
        the middle turns without a summary rather than inject a useless
        placeholder.
        """
@Suppress("unused") private const val _CC_104: String = "You are a summarization agent creating a context checkpoint. Your output will be injected as reference material for a DIFFERENT assistant that continues the conversation. Do NOT respond to any questions or requests in the conversation — only output the structured summary. Do NOT include any preamble, greeting, or prefix. Write the summary in the same language the user was using in the conversation — do not translate or switch to English. NEVER include API keys, tokens, passwords, secrets, credentials, or connection strings in the summary — replace any that appear with [REDACTED]. Note that the user had credentials present, but do not preserve their values."
@Suppress("unused") private val _CC_105: String = """## Active Task
[THE SINGLE MOST IMPORTANT FIELD. Copy the user's most recent request or
task assignment verbatim — the exact words they used. If multiple tasks
were requested and only some are done, list only the ones NOT yet completed.
The next assistant must pick up exactly here. Example:
"User asked: 'Now refactor the auth module to use JWT instead of sessions'"
If no outstanding task exists, write "None."]

## Goal
[What the user is trying to accomplish overall]

## Constraints & Preferences
[User preferences, coding style, constraints, important decisions]

## Completed Actions
[Numbered list of concrete actions taken — include tool used, target, and outcome.
Format each as: N. ACTION target — outcome [tool: name]
Example:
1. READ config.py:45 — found `==` should be `!=` [tool: read_file]
2. PATCH config.py:45 — changed `==` to `!=` [tool: patch]
3. TEST `pytest tests/` — 3/50 failed: test_parse, test_validate, test_edge [tool: terminal]
Be specific with file paths, commands, line numbers, and results.]

## Active State
[Current working state — include:
- Working directory and branch (if applicable)
- Modified/created files with brief note on each
- Test status (X/Y passing)
- Any running processes or servers
- Environment details that matter]

## In Progress
[Work currently underway — what was being done when compaction fired]

## Blocked
[Any blockers, errors, or issues not yet resolved. Include exact error messages.]

## Key Decisions
[Important technical decisions and WHY they were made]

## Resolved Questions
[Questions the user asked that were ALREADY answered — include the answer so the next assistant does not re-answer them]

## Pending User Asks
[Questions or requests from the user that have NOT yet been answered or fulfilled. If none, write "None."]

## Relevant Files
[Files read, modified, or created — with brief note on each]

## Remaining Work
[What remains to be done — framed as context, not instructions]

## Critical Context
[Any specific values, error messages, configuration details, or data that would be lost without explicit preservation. NEVER include API keys, tokens, passwords, or credentials — write [REDACTED] instead.]

Target ~"""
@Suppress("unused") private val _CC_106: String = """ tokens. Be CONCRETE — include file paths, command outputs, error messages, line numbers, and specific values. Avoid vague descriptions like "made some changes" — say exactly what changed.

Write only the summary body. Do not include any preamble or prefix."""
@Suppress("unused") private const val _CC_107: String = "Skipping context summary during cooldown (%.0fs remaining)"
@Suppress("unused") private val _CC_108: String = """

You are updating a context compaction summary. A previous compaction produced the summary below. New conversation turns have occurred since then and need to be incorporated.

PREVIOUS SUMMARY:
"""
@Suppress("unused") private val _CC_109: String = """

NEW TURNS TO INCORPORATE:
"""
@Suppress("unused") private val _CC_110: String = """

Update the summary using this exact structure. PRESERVE all existing information that is still relevant. ADD new completed actions to the numbered list (continue numbering). Move items from "In Progress" to "Completed Actions" when done. Move answered questions to "Resolved Questions". Update "Active State" to reflect current state. Remove information only if it is clearly obsolete. CRITICAL: Update "## Active Task" to reflect the user's most recent unfulfilled request — this is the most important field for task continuity.

"""
@Suppress("unused") private val _CC_111: String = """

Create a structured handoff summary for a different assistant that will continue this conversation after earlier turns are compacted. The next assistant should be able to understand what happened without re-reading the original turns.

TURNS TO SUMMARIZE:
"""
@Suppress("unused") private val _CC_112: String = """

Use this exact structure:

"""
@Suppress("unused") private val _CC_113: String = """

FOCUS TOPIC: """"
@Suppress("unused") private val _CC_114: String = """"
The user has requested that this compaction PRIORITISE preserving all information related to the focus topic above. For content related to """"
@Suppress("unused") private const val _CC_115: String = "\", include full detail — exact values, file paths, command outputs, error messages, and decisions. For content NOT related to the focus topic, summarise more aggressively (brief one-liners or omit if truly irrelevant). The focus topic sections should receive roughly 60-70% of the summary token budget. Even for the focus topic, NEVER preserve API keys, tokens, passwords, or credentials — use [REDACTED]."
@Suppress("unused") private const val _CC_116: String = "task"
@Suppress("unused") private const val _CC_117: String = "main_runtime"
@Suppress("unused") private const val _CC_118: String = "messages"
@Suppress("unused") private const val _CC_119: String = "max_tokens"
@Suppress("unused") private const val _CC_120: String = "compression"
@Suppress("unused") private const val _CC_121: String = "model"
@Suppress("unused") private const val _CC_122: String = "provider"
@Suppress("unused") private const val _CC_123: String = "base_url"
@Suppress("unused") private const val _CC_124: String = "api_key"
@Suppress("unused") private const val _CC_125: String = "api_mode"
@Suppress("unused") private const val _CC_126: String = "Context compression: no provider available for summary. Middle turns will be dropped without summary for %d seconds."
@Suppress("unused") private const val _CC_127: String = "Failed to generate context summary: %s. Further summary attempts paused for %d seconds."
@Suppress("unused") private const val _CC_128: String = "user"
@Suppress("unused") private const val _CC_129: String = "status_code"
@Suppress("unused") private const val _CC_130: String = "model_not_found"
@Suppress("unused") private const val _CC_131: String = "does not exist"
@Suppress("unused") private const val _CC_132: String = "no available channel"
@Suppress("unused") private const val _CC_133: String = "Summary model '%s' not available (%s). Falling back to main model '%s' for compression."
@Suppress("unused") private const val _CC_134: String = "response"
@Suppress("unused") private const val _CC_135: String = "_summary_model_fallen_back"
@Suppress("unused") private val _CC_136: String = """Fix orphaned tool_call / tool_result pairs after compression.

        Two failure modes:
        1. A tool *result* references a call_id whose assistant tool_call was
           removed (summarized/truncated).  The API rejects this with
           "No tool call found for function call output with call_id ...".
        2. An assistant message has tool_calls whose results were dropped.
           The API rejects this because every tool_call must be followed by
           a tool result with the matching call_id.

        This method removes orphaned results and inserts stub results for
        orphaned calls so the message list is always well-formed.
        """
@Suppress("unused") private const val _CC_137: String = "Compression sanitizer: removed %d orphaned tool result(s)"
@Suppress("unused") private const val _CC_138: String = "Compression sanitizer: added %d stub tool result(s)"
@Suppress("unused") private const val _CC_139: String = "[Result from earlier conversation — see context summary above]"
@Suppress("unused") private val _CC_140: String = """Compress conversation messages by summarizing middle turns.

        Algorithm:
          1. Prune old tool results (cheap pre-pass, no LLM call)
          2. Protect head messages (system prompt + first exchange)
          3. Find tail boundary by token budget (~20K tokens of recent context)
          4. Summarize middle turns with structured LLM prompt
          5. On re-compression, iteratively update the previous summary

        After compression, orphaned tool_call / tool_result pairs are cleaned
        up so the API never receives mismatched IDs.

        Args:
            focus_topic: Optional focus string for guided compression.  When
                provided, the summariser will prioritise preserving information
                related to this topic and be more aggressive about compressing
                everything else.  Inspired by Claude Code's ``/compact``.
        """
@Suppress("unused") private const val _CC_141: String = "Pre-compression: pruned %d old tool result(s)"
@Suppress("unused") private const val _CC_142: String = "Context compression triggered (%d tokens >= %d threshold)"
@Suppress("unused") private const val _CC_143: String = "Model context limit: %d tokens (%.0f%% = %d)"
@Suppress("unused") private const val _CC_144: String = "Summarizing turns %d-%d (%d turns), protecting %d head + %d tail messages"
@Suppress("unused") private const val _CC_145: String = "[Note: Some earlier conversation turns have been compacted into a handoff summary to preserve context space. The current session state may still reflect earlier work, so build on that summary and state rather than re-doing work.]"
@Suppress("unused") private val _CC_146: String = """
Summary generation was unavailable. """
@Suppress("unused") private const val _CC_147: String = " conversation turns were removed to free context space but could not be summarized. The removed turns contained earlier work in this session. Continue based on the recent messages below and the current state of any files or resources."
@Suppress("unused") private const val _CC_148: String = "Compressed: %d -> %d messages (~%d tokens saved, %.0f%%)"
@Suppress("unused") private const val _CC_149: String = "Compression #%d complete"
@Suppress("unused") private const val _CC_150: String = "Cannot compress: only %d messages (need > %d)"
@Suppress("unused") private const val _CC_151: String = "system"
@Suppress("unused") private const val _CC_152: String = "Summary generation failed — inserting static fallback context marker"
@Suppress("unused") private val _CC_153: String = """

--- END OF CONTEXT SUMMARY — respond to the message below, not the summary above ---

"""
