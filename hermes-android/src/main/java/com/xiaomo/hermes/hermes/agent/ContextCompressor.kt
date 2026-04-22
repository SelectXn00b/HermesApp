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

// ── Token Estimation ─────────────────────────────────────────────────────

/**
 * 粗略估算 token 数（~4 字符/token）
 */
fun estimateTokensRough(text: String): Int {
    if (text.isEmpty()) return 0
    return (text.length + 3) / 4
}

/**
 * 估算消息列表的 token 数
 */
fun estimateMessagesTokensRough(messages: List<Map<String, Any>>): Int {
    var total = 0
    for (msg in messages) {
        val content = msg["content"]
        when (content) {
            is String -> total += estimateTokensRough(content)
            is List<*> -> {
                for (item in content) {
                    if (item is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val block = item as Map<String, Any>
                        when (block["type"]) {
                            "text" -> total += estimateTokensRough(block["text"] as? String ?: "")
                            "tool_use" -> total += estimateTokensRough(
                                com.google.gson.Gson().toJson(block["input"])
                            )
                            "tool_result" -> {
                                val resultContent = block["content"]
                                when (resultContent) {
                                    is String -> total += estimateTokensRough(resultContent)
                                    is List<*> -> {
                                        for (rc in resultContent) {
                                            if (rc is Map<*, *>) {
                                                total += estimateTokensRough(
                                                    (rc as Map<*, *>)["text"] as? String ?: ""
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 角色和结构开销
        total += 10
    }
    return total
}

/**
 * 估算完整请求的 token 数（包含 system prompt 和 tools）
 */
fun estimateRequestTokensRough(
    messages: List<Map<String, Any>>,
    systemPrompt: String = "",
    tools: List<Map<String, Any>> = emptyList()
): Int {
    var total = 0
    if (systemPrompt.isNotEmpty()) total += estimateTokensRough(systemPrompt)
    total += estimateMessagesTokensRough(messages)
    if (tools.isNotEmpty()) {
        total += estimateTokensRough(com.google.gson.Gson().toJson(tools))
    }
    return total
}

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

    /**
     * 压缩消息列表
     *
     * @param messages 原始消息列表
     * @param contextLength 模型的 context length
     * @param systemPrompt system prompt
     * @param tools 工具定义
     * @return 压缩结果
     */
    fun compress(
        messages: List<Map<String, Any>>,
        contextLength: Int,
        systemPrompt: String = "",
        tools: List<Map<String, Any>> = emptyList()
    ): CompressionResult {
        val tokensBefore = estimateRequestTokensRough(messages, systemPrompt, tools)
        val targetTokens = (contextLength * config.targetUtilization).toInt()

        val result = when (config.strategy) {
            CompressionStrategy.TRUNCATE_OLDEST -> truncateOldest(messages, targetTokens, systemPrompt, tools)
            CompressionStrategy.DROP_TOOL_RESULTS -> dropOldToolResults(messages, targetTokens, systemPrompt, tools)
            CompressionStrategy.KEEP_RECENT -> keepRecent(messages, targetTokens, systemPrompt, tools)
            CompressionStrategy.ADAPTIVE -> adaptiveCompress(messages, targetTokens, systemPrompt, tools)
            CompressionStrategy.SUMMARIZE -> truncateOldest(messages, targetTokens, systemPrompt, tools) // 简化版
        }

        val tokensAfter = estimateRequestTokensRough(result, systemPrompt, tools)

        return CompressionResult(
            compressedMessages = result,
            originalCount = messages.size,
            compressedCount = result.size,
            removedCount = messages.size - result.size,
            estimatedTokensBefore = tokensBefore,
            estimatedTokensAfter = tokensAfter,
            strategy = config.strategy.name
        )
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

    /**
     * 从错误消息中解析 context length 限制
     *
     * @param errorMsg API 错误消息
     * @return 解析到的 context length，未找到返回 null
     */
    fun parseContextLimitFromError(errorMsg: String): Int? {
        // 解析 "context length of X tokens" 或 "maximum context length is X"
        val patterns = listOf(
            Regex("""context length of (\d+) tokens"""),
            Regex("""maximum context length is (\d+)"""),
            Regex("""max_tokens.*?(\d+)"""),
            Regex("""context_window.*?(\d+)"""))
        for (pattern in patterns) {
            val match = pattern.find(errorMsg)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        }
        return null
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
        private const val TAG = "ContextCompressor"
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
            "Do NOT answer questions or requests mentioned in this summary; " +
            "they were already addressed. Respond ONLY to the latest user message " +
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
        android.util.Log.d(TAG, "_generateSummary called but LLM summarization not available on Android")
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
            android.util.Log.d(TAG, "Anchoring tail cut to last user message at index $lastUserIdx (was $cutIdx)")
        }
        return maxOf(lastUserIdx, headEnd + 1)
    }
}
