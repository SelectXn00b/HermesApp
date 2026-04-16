package com.xiaomo.androidforclaw.hermes.agent

import android.util.Log
import org.json.JSONObject

/**
 * Context Engine - ContextEngine 抽象基类
 * 1:1 对齐 hermes/agent/context_engine.py
 *
 * 定义上下文管理的抽象接口，包括消息历史、上下文窗口管理等。
 */

data class ContextWindow(
    val messages: List<Map<String, Any>>,
    val totalTokens: Int,
    val systemPromptTokens: Int,
    val toolsTokens: Int,
    val availableTokens: Int
)

/**
 * 上下文引擎抽象基类
 *
 * 对齐 Python ContextEngine ABC：
 * - 维护 token 使用状态（last_prompt_tokens 等）
 * - 压缩参数（threshold_percent, protect_first_n, protect_last_n）
 * - 核心接口：updateFromResponse / shouldCompress / compress
 * - 可选钩子：session lifecycle, tools, status, model switch
 */
abstract class ContextEngine {

    companion object {
        private const val TAG = "ContextEngine"
    }

    // ── Token state (read by run_agent for display/logging) ───────────

    var lastPromptTokens: Int = 0
    var lastCompletionTokens: Int = 0
    var lastTotalTokens: Int = 0
    var thresholdTokens: Int = 0
    var contextLength: Int = 0
    var compressionCount: Int = 0

    // ── Compaction parameters ─────────────────────────────────────────

    var thresholdPercent: Float = 0.75f
    var protectFirstN: Int = 3
    var protectLastN: Int = 6

    // ── Identity ──────────────────────────────────────────────────────

    /** Short identifier (e.g. 'compressor', 'lcm'). */
    open fun name(): String = "compressor"

    // ── Token tracking ────────────────────────────────────────────────

    /** Update tracked token usage from an API response.
     *  Called after every LLM call with the usage dict from the response. */
    open fun updateFromResponse(usage: Map<String, Any>) {
        lastPromptTokens = (usage["prompt_tokens"] as? Number)?.toInt() ?: lastPromptTokens
        lastCompletionTokens = (usage["completion_tokens"] as? Number)?.toInt() ?: lastCompletionTokens
        lastTotalTokens = (usage["total_tokens"] as? Number)?.toInt() ?: lastTotalTokens
        Log.d(TAG, "Updated tokens: prompt=$lastPromptTokens completion=$lastCompletionTokens total=$lastTotalTokens")
    }

    /** Return True if compaction should fire this turn. */
    open fun shouldCompress(promptTokens: Int?): Boolean {
        val tokens = promptTokens ?: lastPromptTokens
        if (contextLength <= 0) return false
        return tokens >= thresholdTokens
    }

    /** Compact the message list and return the new message list.
     *  Base implementation returns messages unchanged — subclasses must override. */
    open fun compress(
        messages: List<Map<String, Any>>,
        currentTokens: Int?
    ): List<Map<String, Any>> {
        Log.w(TAG, "compress() called on base ContextEngine — returning messages unchanged")
        return messages
    }

    // ── Pre-flight check ──────────────────────────────────────────────

    /** Quick rough check before the API call (no real token count yet).
     *  Default returns False (skip pre-flight). Override if your engine
     *  can do a cheap estimate. */
    open fun shouldCompressPreflight(messages: List<Map<String, Any>>): Boolean = false

    // ── Session lifecycle ─────────────────────────────────────────────

    /** Called when a new conversation session begins.
     *  Use this to load persisted state (DAG, store) for the session. */
    open fun onSessionStart(sessionId: String, kwargs: Any) {
        Log.d(TAG, "Session started: $sessionId")
    }

    /** Called at real session boundaries (CLI exit, /reset, gateway expiry).
     *  Use this to flush state, close DB connections, etc. */
    open fun onSessionEnd(sessionId: String, messages: List<Map<String, Any>>) {
        Log.d(TAG, "Session ended: $sessionId, compressionCount=$compressionCount")
    }

    /** Called on /new or /reset. Reset per-session state. */
    open fun onSessionReset() {
        lastPromptTokens = 0
        lastCompletionTokens = 0
        lastTotalTokens = 0
        compressionCount = 0
        Log.d(TAG, "Session state reset")
    }

    // ── Tools ─────────────────────────────────────────────────────────

    /** Return tool schemas this engine provides to the agent.
     *  Default returns empty list (no tools). */
    open fun getToolSchemas(): List<Map<String, Any>> = emptyList()

    /** Handle a tool call from the agent.
     *  Only called for tool names returned by getToolSchemas().
     *  Must return a JSON string. */
    open fun handleToolCall(name: String, args: Map<String, Any>, kwargs: Any): String {
        return JSONObject().apply {
            put("error", "Unknown context engine tool: $name")
        }.toString()
    }

    // ── Status / display ──────────────────────────────────────────────

    /** Return status dict for display/logging.
     *  Default returns the standard fields run_agent expects. */
    open fun getStatus(): Map<String, Any> {
        val usagePercent = if (contextLength > 0) {
            minOf(100.0, lastPromptTokens.toDouble() / contextLength * 100)
        } else {
            0.0
        }
        return mapOf(
            "last_prompt_tokens" to lastPromptTokens,
            "threshold_tokens" to thresholdTokens,
            "context_length" to contextLength,
            "usage_percent" to usagePercent,
            "compression_count" to compressionCount
        )
    }

    // ── Model switch support ──────────────────────────────────────────

    /** Called when the user switches models or on fallback activation.
     *  Default updates context_length and recalculates threshold_tokens
     *  from threshold_percent. */
    open fun updateModel(
        model: String,
        contextLength: Int,
        baseUrl: String,
        apiKey: String,
        provider: String
    ) {
        this.contextLength = contextLength
        this.thresholdTokens = (contextLength * thresholdPercent).toInt()
        Log.d(TAG, "Model updated: model=$model contextLength=$contextLength thresholdTokens=$thresholdTokens")
    }

    // ── Abstract: concrete subclasses must implement ──────────────────

    /**
     * 获取当前上下文窗口
     */
    abstract suspend fun getContextWindow(
        model: String,
        systemPrompt: String = "",
        tools: List<Map<String, Any>> = emptyList()
    ): ContextWindow

    /**
     * 估算文本的 token 数
     */
    abstract fun estimateTokens(text: String): Int

    /**
     * 估算消息列表的 token 数
     */
    abstract fun estimateMessagesTokens(messages: List<Map<String, Any>>): Int

    /**
     * 检查上下文是否即将溢出
     */
    abstract suspend fun isNearOverflow(
        model: String,
        messages: List<Map<String, Any>>,
        systemPrompt: String = "",
        tools: List<Map<String, Any>> = emptyList(),
        threshold: Double = 0.9
    ): Boolean

    /**
     * 获取模型的 context length
     */
    abstract fun getContextLength(model: String): Int
}
