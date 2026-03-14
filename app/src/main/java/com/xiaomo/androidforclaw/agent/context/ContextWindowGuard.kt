package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: context budget guard.
 */


import android.util.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.ModelDefinition

/**
 * Context Window Guard — Gap 2 alignment with OpenClaw context-window-guard.ts
 *
 * Resolves the effective context window from config → model metadata → default,
 * with hard min / warn thresholds.
 */
object ContextWindowGuard {
    private const val TAG = "ContextWindowGuard"

    const val CONTEXT_WINDOW_HARD_MIN_TOKENS = 16_000
    const val CONTEXT_WINDOW_WARN_BELOW_TOKENS = 32_000
    const val DEFAULT_CONTEXT_WINDOW_TOKENS = 128_000

    enum class ContextWindowSource { MODEL, MODELS_CONFIG, AGENT_CONTEXT_TOKENS, DEFAULT }

    data class ContextWindowInfo(
        val tokens: Int,
        val source: ContextWindowSource
    )

    data class ContextWindowGuardResult(
        val tokens: Int,
        val source: ContextWindowSource,
        val shouldWarn: Boolean,
        val shouldBlock: Boolean
    )

    /**
     * Resolve the effective context window info.
     *
     * Priority:
     * 1. modelsConfig: model definition contextWindow from openclaw.json
     * 2. modelMetadata: provider-reported context window
     * 3. default: DEFAULT_CONTEXT_WINDOW_TOKENS
     *
     * Then capped by agents.defaults.contextTokens if set.
     */
    fun resolveContextWindowInfo(
        configLoader: ConfigLoader?,
        providerName: String?,
        modelId: String?,
        modelContextWindow: Int? = null,
        defaultTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS
    ): ContextWindowInfo {
        // 1. Try from models config
        val fromModelsConfig = if (configLoader != null && providerName != null && modelId != null) {
            val modelDef = configLoader.getModelDefinition(providerName, modelId)
            if (modelDef != null && modelDef.contextWindow > 0) modelDef.contextWindow else null
        } else null

        // 2. Try from model metadata
        val fromModel = if (modelContextWindow != null && modelContextWindow > 0) modelContextWindow else null

        val baseInfo = when {
            fromModelsConfig != null -> ContextWindowInfo(fromModelsConfig, ContextWindowSource.MODELS_CONFIG)
            fromModel != null -> ContextWindowInfo(fromModel, ContextWindowSource.MODEL)
            else -> ContextWindowInfo(defaultTokens, ContextWindowSource.DEFAULT)
        }

        // 3. Cap by agents.defaults.contextTokens if set
        // Note: OpenClaw has this config field; AndroidForClaw doesn't yet — placeholder for future
        // configLoader?.loadOpenClawConfig()?.agents?.defaults?.contextTokens

        Log.d(TAG, "Resolved context window: ${baseInfo.tokens} tokens (source: ${baseInfo.source})")
        return baseInfo
    }

    /**
     * Evaluate whether the context window triggers warnings or blocks.
     */
    fun evaluateContextWindowGuard(
        info: ContextWindowInfo,
        warnBelowTokens: Int = CONTEXT_WINDOW_WARN_BELOW_TOKENS,
        hardMinTokens: Int = CONTEXT_WINDOW_HARD_MIN_TOKENS
    ): ContextWindowGuardResult {
        val tokens = maxOf(0, info.tokens)
        val shouldWarn = tokens in 1 until warnBelowTokens
        val shouldBlock = tokens in 1 until hardMinTokens

        if (shouldBlock) {
            Log.e(TAG, "Context window too small: $tokens tokens (hard min: $hardMinTokens)")
        } else if (shouldWarn) {
            Log.w(TAG, "Context window below recommended: $tokens tokens (recommend: $warnBelowTokens+)")
        }

        return ContextWindowGuardResult(
            tokens = tokens,
            source = info.source,
            shouldWarn = shouldWarn,
            shouldBlock = shouldBlock
        )
    }

    /**
     * Convenience: resolve + evaluate in one call.
     */
    fun resolveAndEvaluate(
        configLoader: ConfigLoader?,
        providerName: String?,
        modelId: String?
    ): ContextWindowGuardResult {
        val info = resolveContextWindowInfo(configLoader, providerName, modelId)
        return evaluateContextWindowGuard(info)
    }
}
