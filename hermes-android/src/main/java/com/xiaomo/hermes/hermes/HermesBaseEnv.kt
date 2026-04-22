package com.xiaomo.hermes.hermes

import android.util.Log

/**
 * HermesBaseEnv - Ported from ../hermes-agent/environments/hermes_base_env.py
 *
 * Abstract Base Environment for Hermes-Agent + Atropos.
 * Provides the Atropos integration plumbing that all hermes-agent environments share:
 * - Two-mode operation (OpenAI server for Phase 1, VLLM ManagedServer for Phase 2)
 * - Per-group toolset/distribution resolution
 * - Agent loop orchestration via HermesAgentLoop
 * - ToolContext creation for reward functions
 * - ScoredDataGroup construction from ManagedServer state
 */

// Type aliases for clarity (maps to Python's type annotations)
typealias Item = Map<String, Any?>
typealias ScoredDataGroup = Map<String, Any?>
typealias ScoredDataItem = Map<String, Any?>
// AgentResult defined in AgentLoop.kt as data class
// ToolContext defined in ToolContext.kt as class

/**
 * Configuration for hermes-agent Atropos environments.
 *
 * Extends BaseEnvConfig with agent-specific settings for toolsets,
 * terminal backend, dataset loading, and tool call parsing.
 */
data class HermesAgentEnvConfig(
    // --- Toolset configuration ---
    val enabledToolsets: List<String>? = null,
    val disabledToolsets: List<String>? = null,
    val distribution: String? = null,

    // --- Agent loop configuration ---
    val maxAgentTurns: Int = 30,
    val systemPrompt: String? = null,
    val agentTemperature: Double = 1.0,

    // --- Terminal backend ---
    val terminalBackend: String = "local",
    val terminalTimeout: Int = 120,
    val terminalLifetime: Int = 3600,

    // --- Dataset ---
    val datasetName: String? = null,
    val datasetSplit: String = "train",
    val promptField: String = "prompt",

    // --- Thread pool ---
    val toolPoolSize: Int = 128,

    // --- Phase 2: Tool call parsing ---
    val toolCallParser: String = "hermes",

    // --- Tool result budget ---
    val defaultResultSizeChars: Int = 20000,
    val turnBudgetChars: Int = 80000,
    val previewSizeChars: Int = 2000,
    val toolResultOverrides: Map<String, Int>? = null,

    // --- Provider-specific parameters ---
    val extraBody: Map<String, Any?>? = null,

    // --- Base env config fields ---
    val groupSize: Int = 4,
    val numRolloutsPerGroupForLogging: Int = -1,
    val maxTokenLength: Int = 4096,
    val thinkingMode: Boolean = false,
) {
    /**
     * Build a BudgetConfig from env config fields.
     */
    fun buildBudgetConfig(): Map<String, Any?> {
        return mapOf(
            "default_result_size" to defaultResultSizeChars,
            "turn_budget" to turnBudgetChars,
            "preview_size" to previewSizeChars,
            "tool_overrides" to (toolResultOverrides ?: emptyMap<String, Int>())
        )
    }
}

/**
 * Abstract base environment for hermes-agent Atropos integration.
 *
 * Handles two modes of operation:
 * - Phase 1 (OpenAI server type): Uses server.chat_completion() directly.
 * - Phase 2 (VLLM server type): Uses ManagedServer for exact token IDs + logprobs.
 *
 * Subclasses must implement:
 *   setup()           -- Load dataset, initialize state
 *   getNextItem()     -- Return the next item to roll out
 *   formatPrompt()    -- Convert a dataset item into the user message string
 *   computeReward()   -- Score the rollout using ToolContext
 *   evaluate()        -- Periodic evaluation
 */
abstract class HermesAgentBaseEnv(
    val config: HermesAgentEnvConfig
) {
    companion object {
        private const val _TAG = "HermesAgentBaseEnv"
    }

    // Current group's resolved tools (set in collectTrajectories)
    private var currentGroupTools: Pair<List<Map<String, Any?>>, Set<String>>? = null

    // Tool error tracking for wandb logging
    private val toolErrorBuffer: MutableList<Map<String, Any?>> = mutableListOf()

    // =========================================================================
    // Toolset resolution (per-group)
    // =========================================================================

    /**
     * Resolve toolsets for a group. Called once in collectTrajectories(),
     * then shared by all collectTrajectory() calls in the group.
     *
     * If distribution is set, samples probabilistically.
     * If enabledToolsets is set, uses that explicit list.
     * disabledToolsets is applied as a filter on top.
     *
     * Returns (tool_schemas, valid_tool_names) pair.
     */
    private fun _resolveToolsForGroup(): Pair<List<Map<String, Any?>>, Set<String>> {
        val groupToolsets: List<String>? = if (config.distribution != null) {
            // Would sample from distribution - stub for Android
            Log.i(_TAG, "Distribution sampling not available on Android, using all tools")
            null
        } else {
            config.enabledToolsets
        }

        // On Android, tool resolution is simplified - return empty defaults
        // Real tool definitions come from the agent loop server-side
        val tools = emptyList<Map<String, Any?>>()
        val validNames = emptySet<String>()
        Log.i(_TAG, "Resolved ${validNames.size} tools for group")
        return Pair(tools, validNames)
    }

    // =========================================================================
    // Server mode detection
    // =========================================================================

    /**
     * Determine if we should use ManagedServer (Phase 2) or direct server (Phase 1).
     *
     * Phase 2 (ManagedServer) is used when the server type is 'vllm' or 'sglang'.
     * Phase 1 (direct server) is used for 'openai' server type.
     *
     * On Android, always returns false (Phase 1 mode - direct API calls).
     */
    private fun _useManagedServer(): Boolean {
        // Android client always uses Phase 1 (OpenAI-compatible API)
        return false
    }

    // =========================================================================
    // Core Atropos integration
    // =========================================================================

    /**
     * Override collectTrajectories to resolve toolsets once per group,
     * then delegate to the standard group-level collection.
     */
    suspend fun collectTrajectories(item: Item): Pair<List<ScoredDataGroup?>, List<Item>> {
        // Resolve toolsets for this group (shared by all rollouts in the group)
        currentGroupTools = _resolveToolsForGroup()

        // Delegate to collect individual trajectories for each group member
        val results = mutableListOf<ScoredDataGroup?>()
        val remainingItems = mutableListOf<Item>()

        for (i in 0 until config.groupSize) {
            val (scoredItem, newItems) = collectTrajectory(item)
            results.add(scoredItem)
            remainingItems.addAll(newItems)
        }

        return Pair(results, remainingItems)
    }

    // =========================================================================
    // Wandb rollout display -- format trajectories nicely
    // =========================================================================

    /**
     * Format a conversation's messages into a readable trajectory string
     * for wandb rollout tables.
     */
    private fun _formatTrajectoryForDisplay(messages: List<Map<String, Any?>>): String {
        val parts = mutableListOf<String>()
        for (msg in messages) {
            val role = msg["role"] as? String ?: "unknown"
            val content = msg["content"] as? String ?: ""

            when (role) {
                "system" -> parts.add("[SYSTEM]\n$content")
                "user" -> parts.add("[USER]\n$content")
                "assistant" -> {
                    // Show reasoning if present
                    val reasoning = msg["reasoning_content"] as? String ?: ""
                    if (reasoning.isNotEmpty()) {
                        val truncated = if (reasoning.length > 300) reasoning.substring(0, 300) + "..." else reasoning
                        parts.add("[ASSISTANT thinking]\n$truncated")
                    }
                    // Show content
                    if (content.isNotEmpty()) {
                        parts.add("[ASSISTANT]\n$content")
                    }
                    // Show tool calls
                    @Suppress("UNCHECKED_CAST")
                    val toolCalls = msg["tool_calls"] as? List<Map<String, Any?>> ?: emptyList()
                    for (tc in toolCalls) {
                        @Suppress("UNCHECKED_CAST")
                        val func = tc["function"] as? Map<String, Any?> ?: emptyMap()
                        val name = func["name"] as? String ?: "?"
                        var args = func["arguments"] as? String ?: "{}"
                        if (args.length > 200) args = args.substring(0, 200) + "..."
                        parts.add("[TOOL CALL] $name($args)")
                    }
                }
                "tool" -> {
                    var result = content
                    if (result.length > 500) result = result.substring(0, 500) + "..."
                    parts.add("[TOOL RESULT] $result")
                }
            }
        }
        return parts.joinToString("\n\n")
    }

    /**
     * Override to show formatted trajectories with tool calls visible,
     * instead of raw token decoding which loses all structure.
     */
    suspend fun addRolloutsForWandb(scoredData: Map<String, Any?>?, item: Item? = null) {
        // On Android, wandb logging is a no-op (server-side concern)
        Log.d(_TAG, "addRolloutsForWandb called (no-op on Android)")
    }

    /**
     * Log base metrics including tool errors to wandb.
     */
    suspend fun wandbLog(wandbMetrics: Map<String, Any?>? = null) {
        val metrics = wandbMetrics?.toMutableMap() ?: mutableMapOf()

        // Log tool error stats
        if (toolErrorBuffer.isNotEmpty()) {
            metrics["train/tool_errors_count"] = toolErrorBuffer.size
            val errorSummaries = toolErrorBuffer.map { err ->
                val turn = err["turn"] ?: ""
                val tool = err["tool"] ?: ""
                val args = (err["args"] as? String ?: "").take(80)
                val error = (err["error"] as? String ?: "").take(150)
                "[turn $turn] $tool($args) -> $error"
            }
            metrics["train/tool_error_details"] = errorSummaries.joinToString("\n")
            toolErrorBuffer.clear()
        } else {
            metrics["train/tool_errors_count"] = 0
        }

        // On Android, wandb logging is handled server-side
        Log.d(_TAG, "wandbLog: ${metrics.size} metrics")
    }

    /**
     * Run a single rollout: agent loop + reward computation.
     *
     * Called groupSize times in parallel by collectTrajectories().
     * Each call gets its own taskId for terminal/browser session isolation.
     */
    suspend fun collectTrajectory(item: Item): Pair<ScoredDataItem?, List<Item>> {
        val taskId = java.util.UUID.randomUUID().toString()

        // Get group-level tools (resolved once in collectTrajectories)
        val (tools, validNames) = currentGroupTools ?: _resolveToolsForGroup()

        // Build initial messages
        val messages = mutableListOf<Map<String, Any?>>()
        if (config.systemPrompt != null) {
            messages.add(mapOf("role" to "system", "content" to config.systemPrompt))
        }
        messages.add(mapOf("role" to "user", "content" to formatPrompt(item)))

        // On Android, agent loop execution is delegated to server
        // This is a structural stub - real execution happens via API
        val reward = 0.0

        val scoredItem = mapOf<String, Any?>(
            "tokens" to emptyList<Int>(),
            "masks" to emptyList<Int>(),
            "scores" to reward,
            "messages" to messages
        )

        return Pair(scoredItem, emptyList())
    }

    // =========================================================================
    // Abstract methods -- subclasses must implement
    // =========================================================================

    /**
     * Load dataset, initialize state.
     * Called once when the environment starts.
     */
    abstract suspend fun setup()

    /**
     * Return the next item from the dataset for rollout.
     * Called by the base env's main loop to get items for workers.
     */
    abstract suspend fun getNextItem(): Item

    /**
     * Convert a dataset item into the user message for the agent.
     */
    abstract fun formatPrompt(item: Item): String

    /**
     * Score the rollout. Has full access to item, result, and ToolContext.
     *
     * Returns reward float (typically 0.0 to 1.0, but any float is valid).
     */
    abstract suspend fun computeReward(item: Item, result: AgentResult, ctx: ToolContext): Double

    /**
     * Periodic evaluation. Called every steps_per_eval steps.
     */
    abstract suspend fun evaluate()
}
