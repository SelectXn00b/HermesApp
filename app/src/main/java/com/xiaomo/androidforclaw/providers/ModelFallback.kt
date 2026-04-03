package com.xiaomo.androidforclaw.providers

import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.logging.Log

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-fallback.ts
 *
 * Model fallback chain — iterates through candidate models (primary → configured
 * fallbacks → default) when requests fail with retryable errors. Context overflow
 * errors are rethrown immediately (not retried on a different model).
 */
object ModelFallback {

    private const val TAG = "ModelFallback"

    /** Per-provider cooldown throttle: 30 seconds between probe attempts */
    private const val MIN_PROBE_INTERVAL_MS = 30_000L

    /** Track last probe attempt per provider for cooldown throttling */
    private val lastProbeAttempt = mutableMapOf<String, Long>()

    /**
     * A model candidate in the fallback chain.
     */
    data class ModelCandidate(
        val provider: String,
        val model: String
    )

    /**
     * Record of an attempted candidate.
     */
    data class FallbackAttempt(
        val provider: String,
        val model: String,
        val error: String?,
        val reason: String? = null
    )

    /**
     * Result of a successful fallback run.
     */
    data class ModelFallbackResult<T>(
        val result: T,
        val provider: String,
        val model: String,
        val attempts: List<FallbackAttempt>
    )

    /**
     * Run a function with model fallback.
     *
     * Builds an ordered candidate list: primary → configured fallbacks → config default.
     * Iterates candidates, calling [run] on each. If a candidate fails with a retryable
     * error, moves to the next. Context overflow errors are rethrown immediately.
     *
     * @param config OpenClaw configuration (for fallback list)
     * @param configLoader Config loader (for model resolution)
     * @param provider Primary provider name
     * @param model Primary model ID
     * @param fallbacksOverride Optional explicit fallbacks; replaces config fallbacks when non-null
     * @param run The actual request function (provider, model) -> result
     * @param onError Optional callback on each failed attempt
     */
    suspend fun <T> runWithModelFallback(
        config: OpenClawConfig?,
        configLoader: ConfigLoader,
        provider: String,
        model: String,
        fallbacksOverride: List<String>? = null,
        run: suspend (provider: String, model: String) -> T,
        onError: (suspend (provider: String, model: String, error: Exception, attempt: Int, total: Int) -> Unit)? = null
    ): ModelFallbackResult<T> {
        val candidates = resolveFallbackCandidates(config, configLoader, provider, model, fallbacksOverride)
        val attempts = mutableListOf<FallbackAttempt>()
        var lastError: Exception? = null
        val hasFallbacks = candidates.size > 1

        for ((i, candidate) in candidates.withIndex()) {
            val isPrimary = i == 0

            // Per-provider cooldown throttle
            if (!isPrimary && hasFallbacks) {
                val now = System.currentTimeMillis()
                val lastProbe = lastProbeAttempt[candidate.provider] ?: 0L
                if (now - lastProbe < MIN_PROBE_INTERVAL_MS) {
                    attempts.add(FallbackAttempt(
                        candidate.provider, candidate.model,
                        "Provider ${candidate.provider} is in cooldown (throttled)",
                        reason = "cooldown"
                    ))
                    continue
                }
            }

            try {
                val result = run(candidate.provider, candidate.model)
                if (i > 0) {
                    Log.i(TAG, "Fallback succeeded: ${candidate.provider}/${candidate.model} " +
                        "(after ${attempts.size} failed attempts)")
                }
                return ModelFallbackResult(result, candidate.provider, candidate.model, attempts)
            } catch (e: Exception) {
                lastError = e

                // Context overflow: rethrow immediately, don't try another model
                if (isLikelyContextOverflowError(e)) {
                    Log.w(TAG, "Context overflow on ${candidate.provider}/${candidate.model}, rethrowing")
                    throw e
                }

                // Record the failure
                val reason = classifyErrorReason(e)
                attempts.add(FallbackAttempt(
                    candidate.provider, candidate.model,
                    e.message, reason = reason
                ))

                // Update cooldown state for this provider
                if (reason == "rate_limit" || reason == "auth" || reason == "overloaded") {
                    lastProbeAttempt[candidate.provider] = System.currentTimeMillis()
                }

                // If not retryable and this is the last candidate, rethrow
                if (!isRetryableForFallback(e) && i == candidates.size - 1) {
                    throw e
                }

                Log.w(TAG, "Candidate ${candidate.provider}/${candidate.model} failed " +
                    "(attempt ${i + 1}/${candidates.size}): ${e.message}")
                onError?.invoke(candidate.provider, candidate.model, e, i + 1, candidates.size)
            }
        }

        // All candidates failed
        if (attempts.size <= 1 && lastError != null) {
            throw lastError
        }
        val summary = attempts.joinToString(" | ") { "${it.provider}/${it.model}: ${it.error}" }
        throw LLMException("All models failed (${attempts.size}): $summary", lastError)
    }

    /**
     * Build the ordered list of fallback candidates.
     */
    internal fun resolveFallbackCandidates(
        config: OpenClawConfig?,
        configLoader: ConfigLoader,
        provider: String,
        model: String,
        fallbacksOverride: List<String>?
    ): List<ModelCandidate> {
        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<ModelCandidate>()

        fun addCandidate(p: String, m: String) {
            val key = "$p/$m"
            if (key in seen) return
            // Verify the provider+model actually exists in config
            val providerConfig = configLoader.getProviderConfig(p) ?: return
            if (providerConfig.models.none { it.id == m }) return
            seen.add(key)
            candidates.add(ModelCandidate(p, m))
        }

        // 1. Primary candidate
        addCandidate(provider, model)

        // 2. Configured fallbacks (or override)
        val fallbacks = fallbacksOverride
            ?: config?.agents?.defaults?.model?.fallbacks
            ?: emptyList()

        for (raw in fallbacks) {
            val parsed = parseModelRefString(raw, configLoader)
            if (parsed != null) {
                addCandidate(parsed.first, parsed.second)
            }
        }

        // 3. Config default model as last resort
        if (fallbacksOverride == null) {
            val defaultModel = config?.resolveDefaultModel()
            if (defaultModel != null) {
                val parsed = parseModelRefString(defaultModel, configLoader)
                if (parsed != null) {
                    addCandidate(parsed.first, parsed.second)
                }
            }
        }

        return candidates
    }

    /**
     * Parse a model reference string ("provider/model" or "model") into (provider, model).
     */
    internal fun parseModelRefString(ref: String, configLoader: ConfigLoader): Pair<String, String>? {
        val trimmed = ref.trim()
        if (trimmed.isEmpty()) return null

        // Try "provider/model" format
        val parts = trimmed.split("/", limit = 2)
        if (parts.size == 2) {
            val p = parts[0].trim()
            val m = parts[1].trim()
            if (p.isNotEmpty() && m.isNotEmpty()) {
                return Pair(p, m)
            }
        }

        // Try finding by model ID across all providers
        val providerName = configLoader.findProviderByModelId(trimmed)
        if (providerName != null) {
            return Pair(providerName, trimmed)
        }

        return null
    }

    /**
     * Check if an error is likely a context overflow / context length exceeded.
     */
    internal fun isLikelyContextOverflowError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("context_length_exceeded") ||
            msg.contains("context window") ||
            msg.contains("maximum context length") ||
            msg.contains("too many tokens") ||
            msg.contains("prompt is too long")
    }

    /**
     * Check if an error is retryable for fallback purposes.
     * More permissive than the per-request retry — any error that isn't
     * a clear client bug should allow trying the next model.
     */
    internal fun isRetryableForFallback(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("429") || msg.contains("rate limit") -> true
            msg.contains("503") || msg.contains("service unavailable") -> true
            msg.contains("500") || msg.contains("502") || msg.contains("504") -> true
            msg.contains("timeout") || msg.contains("timed out") -> true
            msg.contains("connection") || msg.contains("network") -> true
            msg.contains("overloaded") -> true
            msg.contains("auth") || msg.contains("unauthorized") || msg.contains("401") -> true
            msg.contains("model_not_found") || msg.contains("model not found") -> true
            else -> false
        }
    }

    /**
     * Classify an error into a reason category.
     */
    private fun classifyErrorReason(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("429") || msg.contains("rate limit") -> "rate_limit"
            msg.contains("401") || msg.contains("auth") || msg.contains("unauthorized") -> "auth"
            msg.contains("overloaded") -> "overloaded"
            msg.contains("503") || msg.contains("service unavailable") -> "service_unavailable"
            msg.contains("timeout") || msg.contains("timed out") -> "timeout"
            msg.contains("model_not_found") || msg.contains("model not found") -> "model_not_found"
            msg.contains("402") || msg.contains("billing") || msg.contains("payment") -> "billing"
            else -> "unknown"
        }
    }
}
