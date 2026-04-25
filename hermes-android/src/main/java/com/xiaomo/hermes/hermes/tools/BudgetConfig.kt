/**
 * Configurable budget constants for tool result persistence.
 *
 * Overridable at the RL environment level via HermesAgentEnvConfig fields.
 * Per-tool resolution: pinned > config overrides > registry > default.
 *
 * Ported from tools/budget_config.py
 */
package com.xiaomo.hermes.hermes.tools

// Tools whose thresholds must never be overridden.
// read_file=inf prevents infinite persist->read->persist loops.
val PINNED_THRESHOLDS: Map<String, Double> = mapOf(
    "read_file" to Double.POSITIVE_INFINITY,
)

// Defaults matching the current hardcoded values in tool_result_storage.py.
// Kept here as the single source of truth; ToolResultStorage imports these.
const val DEFAULT_RESULT_SIZE_CHARS: Int = 100_000
const val DEFAULT_TURN_BUDGET_CHARS: Int = 200_000
const val DEFAULT_PREVIEW_SIZE_CHARS: Int = 1_500

/**
 * Immutable budget constants for the 3-layer tool result persistence system.
 *
 * Layer 2 (per-result): resolveThreshold(toolName) -> threshold in chars.
 * Layer 3 (per-turn):   turnBudget -> aggregate char budget across all tool
 *                       results in a single assistant turn.
 * Preview:              previewSize -> inline snippet size after persistence.
 */
data class BudgetConfig(
    val defaultResultSize: Int = DEFAULT_RESULT_SIZE_CHARS,
    val turnBudget: Int = DEFAULT_TURN_BUDGET_CHARS,
    val previewSize: Int = DEFAULT_PREVIEW_SIZE_CHARS,
    val toolOverrides: Map<String, Int> = emptyMap(),
) {
    /**
     * Resolve the persistence threshold for a tool.
     *
     * Priority: pinned -> toolOverrides -> registry per-tool -> default.
     */
    fun resolveThreshold(toolName: String): Double {
        PINNED_THRESHOLDS[toolName]?.let { return it }
        toolOverrides[toolName]?.let { return it.toDouble() }
        return registry.getMaxResultSize(toolName, default = defaultResultSize).toDouble()
    }
}

// Default config -- matches current hardcoded behavior exactly.
val DEFAULT_BUDGET: BudgetConfig = BudgetConfig()
