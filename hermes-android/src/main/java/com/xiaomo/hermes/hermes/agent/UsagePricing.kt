/**
 * Token usage → pricing resolution and cost estimation.
 *
 * 1:1 对齐 hermes/agent/usage_pricing.py (Python 原始)
 *
 * Handles the full pricing pipeline:
 * - Billing route resolution (provider + model + base_url → route)
 * - Pricing entry lookup (official docs snapshot, provider models API, etc.)
 * - Canonical usage normalization (Anthropic / Codex / OpenAI API shapes)
 * - Cost estimation
 * - Formatting helpers for duration and token counts
 */
package com.xiaomo.hermes.hermes.agent

import com.xiaomo.hermes.hermes.baseUrlHostMatches
import java.math.BigDecimal
import java.time.Instant

// ── Module-level state & constants (1:1 with Python module globals) ─────

val DEFAULT_PRICING: Map<String, Double> = mapOf("input" to 0.0, "output" to 0.0)

val _ZERO: BigDecimal = BigDecimal("0")
val _ONE_MILLION: BigDecimal = BigDecimal("1000000")

// CostStatus literal: "actual" | "estimated" | "included" | "unknown"
// CostSource literal: "provider_cost_api" | "provider_generation_api" |
//   "provider_models_api" | "official_docs_snapshot" | "user_override" |
//   "custom_contract" | "none"

// ── Data classes ──────────────────────────────────────────────────────────

data class CanonicalUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val requestCount: Int = 1,
    val rawUsage: Map<String, Any?>? = null,
) {
    fun promptTokens(): Int = inputTokens + cacheReadTokens + cacheWriteTokens
    fun totalTokens(): Int = promptTokens() + outputTokens

    companion object {
        /** Current UTC time (Python `_UTC_NOW`). Returns ISO 8601 string. */
        val _UTC_NOW: String get() = java.time.Instant.now().toString()
    }
}


data class BillingRoute(
    val provider: String,
    val model: String,
    val baseUrl: String = "",
    val billingMode: String = "unknown",
)


data class PricingEntry(
    val inputCostPerMillion: BigDecimal? = null,
    val outputCostPerMillion: BigDecimal? = null,
    val cacheReadCostPerMillion: BigDecimal? = null,
    val cacheWriteCostPerMillion: BigDecimal? = null,
    val requestCost: BigDecimal? = null,
    val source: String = "none",
    val sourceUrl: String? = null,
    val pricingVersion: String? = null,
    val fetchedAt: Instant? = null,
)


data class CostResult(
    val amountUsd: BigDecimal?,
    val status: String,
    val source: String,
    val label: String,
    val fetchedAt: Instant? = null,
    val pricingVersion: String? = null,
    val notes: List<String> = emptyList(),
)


private fun _utcNow(): Instant = Instant.now()


// Official docs snapshot entries. Models whose published pricing and cache
// semantics are stable enough to encode exactly.
val _OFFICIAL_DOCS_PRICING: Map<Pair<String, String>, PricingEntry> = mapOf(
    Pair("anthropic", "claude-opus-4-20250514") to PricingEntry(
        inputCostPerMillion = BigDecimal("15.00"),
        outputCostPerMillion = BigDecimal("75.00"),
        cacheReadCostPerMillion = BigDecimal("1.50"),
        cacheWriteCostPerMillion = BigDecimal("18.75"),
        source = "official_docs_snapshot",
        sourceUrl = "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching",
        pricingVersion = "anthropic-prompt-caching-2026-03-16",
    ),
    Pair("anthropic", "claude-sonnet-4-20250514") to PricingEntry(
        inputCostPerMillion = BigDecimal("3.00"),
        outputCostPerMillion = BigDecimal("15.00"),
        cacheReadCostPerMillion = BigDecimal("0.30"),
        cacheWriteCostPerMillion = BigDecimal("3.75"),
        source = "official_docs_snapshot",
        sourceUrl = "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching",
        pricingVersion = "anthropic-prompt-caching-2026-03-16",
    ),
    Pair("openai", "gpt-4o") to PricingEntry(
        inputCostPerMillion = BigDecimal("2.50"),
        outputCostPerMillion = BigDecimal("10.00"),
        cacheReadCostPerMillion = BigDecimal("1.25"),
        source = "official_docs_snapshot",
        sourceUrl = "https://openai.com/api/pricing/",
        pricingVersion = "openai-pricing-2026-03-16",
    ),
    Pair("openai", "gpt-4o-mini") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.15"),
        outputCostPerMillion = BigDecimal("0.60"),
        cacheReadCostPerMillion = BigDecimal("0.075"),
        source = "official_docs_snapshot",
        sourceUrl = "https://openai.com/api/pricing/",
        pricingVersion = "openai-pricing-2026-03-16",
    ),
    Pair("openai", "gpt-4.1") to PricingEntry(
        inputCostPerMillion = BigDecimal("2.00"),
        outputCostPerMillion = BigDecimal("8.00"),
        cacheReadCostPerMillion = BigDecimal("0.50"),
        source = "official_docs_snapshot",
        sourceUrl = "https://openai.com/api/pricing/",
        pricingVersion = "openai-pricing-2026-03-16",
    ),
    Pair("openai", "gpt-4.1-mini") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.40"),
        outputCostPerMillion = BigDecimal("1.60"),
        cacheReadCostPerMillion = BigDecimal("0.10"),
        source = "official_docs_snapshot",
        sourceUrl = "https://openai.com/api/pricing/",
        pricingVersion = "openai-pricing-2026-03-16",
    ),
    Pair("openai", "gpt-4.1-nano") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.10"),
        outputCostPerMillion = BigDecimal("0.40"),
        cacheReadCostPerMillion = BigDecimal("0.025"),
        source = "official_docs_snapshot",
        sourceUrl = "https://openai.com/api/pricing/",
        pricingVersion = "openai-pricing-2026-03-16",
    ),
    Pair("openai", "o3") to PricingEntry(
        inputCostPerMillion = BigDecimal("10.00"),
        outputCostPerMillion = BigDecimal("40.00"),
        cacheReadCostPerMillion = BigDecimal("2.50"),
        source = "official_docs_snapshot",
        sourceUrl = "https://openai.com/api/pricing/",
        pricingVersion = "openai-pricing-2026-03-16",
    ),
    Pair("openai", "o3-mini") to PricingEntry(
        inputCostPerMillion = BigDecimal("1.10"),
        outputCostPerMillion = BigDecimal("4.40"),
        cacheReadCostPerMillion = BigDecimal("0.55"),
        source = "official_docs_snapshot",
        sourceUrl = "https://openai.com/api/pricing/",
        pricingVersion = "openai-pricing-2026-03-16",
    ),
    Pair("anthropic", "claude-3-5-sonnet-20241022") to PricingEntry(
        inputCostPerMillion = BigDecimal("3.00"),
        outputCostPerMillion = BigDecimal("15.00"),
        cacheReadCostPerMillion = BigDecimal("0.30"),
        cacheWriteCostPerMillion = BigDecimal("3.75"),
        source = "official_docs_snapshot",
        sourceUrl = "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching",
        pricingVersion = "anthropic-pricing-2026-03-16",
    ),
    Pair("anthropic", "claude-3-5-haiku-20241022") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.80"),
        outputCostPerMillion = BigDecimal("4.00"),
        cacheReadCostPerMillion = BigDecimal("0.08"),
        cacheWriteCostPerMillion = BigDecimal("1.00"),
        source = "official_docs_snapshot",
        sourceUrl = "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching",
        pricingVersion = "anthropic-pricing-2026-03-16",
    ),
    Pair("anthropic", "claude-3-opus-20240229") to PricingEntry(
        inputCostPerMillion = BigDecimal("15.00"),
        outputCostPerMillion = BigDecimal("75.00"),
        cacheReadCostPerMillion = BigDecimal("1.50"),
        cacheWriteCostPerMillion = BigDecimal("18.75"),
        source = "official_docs_snapshot",
        sourceUrl = "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching",
        pricingVersion = "anthropic-pricing-2026-03-16",
    ),
    Pair("anthropic", "claude-3-haiku-20240307") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.25"),
        outputCostPerMillion = BigDecimal("1.25"),
        cacheReadCostPerMillion = BigDecimal("0.03"),
        cacheWriteCostPerMillion = BigDecimal("0.30"),
        source = "official_docs_snapshot",
        sourceUrl = "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching",
        pricingVersion = "anthropic-pricing-2026-03-16",
    ),
    Pair("deepseek", "deepseek-chat") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.14"),
        outputCostPerMillion = BigDecimal("0.28"),
        source = "official_docs_snapshot",
        sourceUrl = "https://api-docs.deepseek.com/quick_start/pricing",
        pricingVersion = "deepseek-pricing-2026-03-16",
    ),
    Pair("deepseek", "deepseek-reasoner") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.55"),
        outputCostPerMillion = BigDecimal("2.19"),
        source = "official_docs_snapshot",
        sourceUrl = "https://api-docs.deepseek.com/quick_start/pricing",
        pricingVersion = "deepseek-pricing-2026-03-16",
    ),
    Pair("google", "gemini-2.5-pro") to PricingEntry(
        inputCostPerMillion = BigDecimal("1.25"),
        outputCostPerMillion = BigDecimal("10.00"),
        source = "official_docs_snapshot",
        sourceUrl = "https://ai.google.dev/pricing",
        pricingVersion = "google-pricing-2026-03-16",
    ),
    Pair("google", "gemini-2.5-flash") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.15"),
        outputCostPerMillion = BigDecimal("0.60"),
        source = "official_docs_snapshot",
        sourceUrl = "https://ai.google.dev/pricing",
        pricingVersion = "google-pricing-2026-03-16",
    ),
    Pair("google", "gemini-2.0-flash") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.10"),
        outputCostPerMillion = BigDecimal("0.40"),
        source = "official_docs_snapshot",
        sourceUrl = "https://ai.google.dev/pricing",
        pricingVersion = "google-pricing-2026-03-16",
    ),
    Pair("bedrock", "anthropic.claude-opus-4-6") to PricingEntry(
        inputCostPerMillion = BigDecimal("15.00"),
        outputCostPerMillion = BigDecimal("75.00"),
        source = "official_docs_snapshot",
        sourceUrl = "https://aws.amazon.com/bedrock/pricing/",
        pricingVersion = "bedrock-pricing-2026-04",
    ),
    Pair("bedrock", "anthropic.claude-sonnet-4-6") to PricingEntry(
        inputCostPerMillion = BigDecimal("3.00"),
        outputCostPerMillion = BigDecimal("15.00"),
        source = "official_docs_snapshot",
        sourceUrl = "https://aws.amazon.com/bedrock/pricing/",
        pricingVersion = "bedrock-pricing-2026-04",
    ),
    Pair("bedrock", "anthropic.claude-sonnet-4-5") to PricingEntry(
        inputCostPerMillion = BigDecimal("3.00"),
        outputCostPerMillion = BigDecimal("15.00"),
        source = "official_docs_snapshot",
        sourceUrl = "https://aws.amazon.com/bedrock/pricing/",
        pricingVersion = "bedrock-pricing-2026-04",
    ),
    Pair("bedrock", "anthropic.claude-haiku-4-5") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.80"),
        outputCostPerMillion = BigDecimal("4.00"),
        source = "official_docs_snapshot",
        sourceUrl = "https://aws.amazon.com/bedrock/pricing/",
        pricingVersion = "bedrock-pricing-2026-04",
    ),
    Pair("bedrock", "amazon.nova-pro") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.80"),
        outputCostPerMillion = BigDecimal("3.20"),
        source = "official_docs_snapshot",
        sourceUrl = "https://aws.amazon.com/bedrock/pricing/",
        pricingVersion = "bedrock-pricing-2026-04",
    ),
    Pair("bedrock", "amazon.nova-lite") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.06"),
        outputCostPerMillion = BigDecimal("0.24"),
        source = "official_docs_snapshot",
        sourceUrl = "https://aws.amazon.com/bedrock/pricing/",
        pricingVersion = "bedrock-pricing-2026-04",
    ),
    Pair("bedrock", "amazon.nova-micro") to PricingEntry(
        inputCostPerMillion = BigDecimal("0.035"),
        outputCostPerMillion = BigDecimal("0.14"),
        source = "official_docs_snapshot",
        sourceUrl = "https://aws.amazon.com/bedrock/pricing/",
        pricingVersion = "bedrock-pricing-2026-04",
    ),
)


fun _toDecimal(value: Any?): BigDecimal? {
    if (value == null) return null
    return try {
        BigDecimal(value.toString())
    } catch (_: Exception) {
        null
    }
}


fun _toInt(value: Any?): Int {
    return try {
        when (value) {
            null -> 0
            is Number -> value.toInt()
            is String -> if (value.isEmpty()) 0 else value.toInt()
            else -> 0
        }
    } catch (_: Exception) {
        0
    }
}


fun resolveBillingRoute(
    modelName: String,
    provider: String? = null,
    baseUrl: String? = null,
): BillingRoute {
    var providerName = (provider ?: "").trim().lowercase()
    val base = (baseUrl ?: "").trim().lowercase()
    var model = (modelName).trim()
    if (providerName.isEmpty() && "/" in model) {
        val parts = model.split("/", limit = 2)
        val inferredProvider = parts[0]
        val bareModel = parts[1]
        if (inferredProvider in setOf("anthropic", "openai", "google")) {
            providerName = inferredProvider
            model = bareModel
        }
    }

    if (providerName == "openai-codex") {
        return BillingRoute(
            provider = "openai-codex",
            model = model,
            baseUrl = baseUrl ?: "",
            billingMode = "subscription_included",
        )
    }
    if (providerName == "openrouter" || baseUrlHostMatches(baseUrl ?: "", "openrouter.ai")) {
        return BillingRoute(
            provider = "openrouter",
            model = model,
            baseUrl = baseUrl ?: "",
            billingMode = "official_models_api",
        )
    }
    if (providerName == "anthropic") {
        return BillingRoute(
            provider = "anthropic",
            model = model.split("/").last(),
            baseUrl = baseUrl ?: "",
            billingMode = "official_docs_snapshot",
        )
    }
    if (providerName == "openai") {
        return BillingRoute(
            provider = "openai",
            model = model.split("/").last(),
            baseUrl = baseUrl ?: "",
            billingMode = "official_docs_snapshot",
        )
    }
    if (providerName in setOf("custom", "local") || (base.isNotEmpty() && "localhost" in base)) {
        return BillingRoute(
            provider = providerName.ifEmpty { "custom" },
            model = model,
            baseUrl = baseUrl ?: "",
            billingMode = "unknown",
        )
    }
    return BillingRoute(
        provider = providerName.ifEmpty { "unknown" },
        model = if (model.isNotEmpty()) model.split("/").last() else "",
        baseUrl = baseUrl ?: "",
        billingMode = "unknown",
    )
}


fun _lookupOfficialDocsPricing(route: BillingRoute): PricingEntry? {
    return _OFFICIAL_DOCS_PRICING[Pair(route.provider, route.model.lowercase())]
}


fun _openrouterPricingEntry(route: BillingRoute): PricingEntry? {
    return _pricingEntryFromMetadata(
        fetchModelMetadata(),
        route.model,
        sourceUrl = "https://openrouter.ai/docs/api/api-reference/models/get-models",
        pricingVersion = "openrouter-models-api",
    )
}


fun _pricingEntryFromMetadata(
    metadata: Map<String, Map<String, Any?>>,
    modelId: String,
    sourceUrl: String,
    pricingVersion: String,
): PricingEntry? {
    val entry = metadata[modelId] ?: return null
    val pricing = (entry["pricing"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
    val prompt = _toDecimal(pricing["prompt"])
    val completion = _toDecimal(pricing["completion"])
    val request = _toDecimal(pricing["request"])
    val cacheRead = _toDecimal(
        pricing["cache_read"]
            ?: pricing["cached_prompt"]
            ?: pricing["input_cache_read"]
    )
    val cacheWrite = _toDecimal(
        pricing["cache_write"]
            ?: pricing["cache_creation"]
            ?: pricing["input_cache_write"]
    )
    if (prompt == null && completion == null && request == null) return null

    fun perTokenToPerMillion(value: BigDecimal?): BigDecimal? {
        if (value == null) return null
        return value.multiply(_ONE_MILLION)
    }

    return PricingEntry(
        inputCostPerMillion = perTokenToPerMillion(prompt),
        outputCostPerMillion = perTokenToPerMillion(completion),
        cacheReadCostPerMillion = perTokenToPerMillion(cacheRead),
        cacheWriteCostPerMillion = perTokenToPerMillion(cacheWrite),
        requestCost = request,
        source = "provider_models_api",
        sourceUrl = sourceUrl,
        pricingVersion = pricingVersion,
        fetchedAt = _utcNow(),
    )
}


fun getPricingEntry(
    modelName: String,
    provider: String? = null,
    baseUrl: String? = null,
    apiKey: String? = null,
): PricingEntry? {
    val route = resolveBillingRoute(modelName, provider = provider, baseUrl = baseUrl)
    if (route.billingMode == "subscription_included") {
        return PricingEntry(
            inputCostPerMillion = _ZERO,
            outputCostPerMillion = _ZERO,
            cacheReadCostPerMillion = _ZERO,
            cacheWriteCostPerMillion = _ZERO,
            source = "none",
            pricingVersion = "included-route",
        )
    }
    if (route.provider == "openrouter") {
        return _openrouterPricingEntry(route)
    }
    if (route.baseUrl.isNotEmpty()) {
        val entry = _pricingEntryFromMetadata(
            fetchEndpointModelMetadata(route.baseUrl, apiKey = apiKey ?: ""),
            route.model,
            sourceUrl = "${route.baseUrl.trimEnd('/')}/models",
            pricingVersion = "openai-compatible-models-api",
        )
        if (entry != null) return entry
    }
    return _lookupOfficialDocsPricing(route)
}


/**
 * Normalize raw API response usage into canonical token buckets.
 *
 * Handles three API shapes: Anthropic (input_tokens/output_tokens plus
 * cache_read_input_tokens / cache_creation_input_tokens), Codex Responses
 * (input_tokens includes cache tokens; input_tokens_details.cached_tokens
 * separates them), and OpenAI Chat Completions (prompt_tokens includes cache
 * tokens; prompt_tokens_details.cached_tokens separates them).
 *
 * In Codex and OpenAI modes, input_tokens is derived by subtracting cache
 * tokens from the total — the API contract is that input/prompt totals
 * include cached tokens and the details object breaks them out.
 */
fun normalizeUsage(
    responseUsage: Any?,
    provider: String? = null,
    apiMode: String? = null,
): CanonicalUsage {
    if (responseUsage == null) return CanonicalUsage()

    val providerName = (provider ?: "").trim().lowercase()
    val mode = (apiMode ?: "").trim().lowercase()

    fun attr(name: String): Any? {
        if (responseUsage is Map<*, *>) return responseUsage[name]
        return try {
            val f = responseUsage.javaClass.getField(name)
            f.get(responseUsage)
        } catch (_: Exception) {
            try {
                val m = responseUsage.javaClass.methods.firstOrNull { it.name == name }
                m?.invoke(responseUsage)
            } catch (_: Exception) {
                null
            }
        }
    }

    val inputTokens: Int
    val outputTokens: Int
    val cacheReadTokens: Int
    val cacheWriteTokens: Int

    if (mode == "anthropic_messages" || providerName == "anthropic") {
        inputTokens = _toInt(attr("input_tokens"))
        outputTokens = _toInt(attr("output_tokens"))
        cacheReadTokens = _toInt(attr("cache_read_input_tokens"))
        cacheWriteTokens = _toInt(attr("cache_creation_input_tokens"))
    } else if (mode == "codex_responses") {
        val inputTotal = _toInt(attr("input_tokens"))
        outputTokens = _toInt(attr("output_tokens"))
        val details = attr("input_tokens_details")
        cacheReadTokens = _toInt(if (details != null) _attr(details, "cached_tokens") else 0)
        cacheWriteTokens = _toInt(if (details != null) _attr(details, "cache_creation_tokens") else 0)
        inputTokens = maxOf(0, inputTotal - cacheReadTokens - cacheWriteTokens)
    } else {
        val promptTotal = _toInt(attr("prompt_tokens"))
        outputTokens = _toInt(attr("completion_tokens"))
        val details = attr("prompt_tokens_details")
        cacheReadTokens = _toInt(if (details != null) _attr(details, "cached_tokens") else 0)
        cacheWriteTokens = _toInt(if (details != null) _attr(details, "cache_write_tokens") else 0)
        inputTokens = maxOf(0, promptTotal - cacheReadTokens - cacheWriteTokens)
    }

    var reasoningTokens = 0
    val outputDetails = attr("output_tokens_details")
    if (outputDetails != null) {
        reasoningTokens = _toInt(_attr(outputDetails, "reasoning_tokens"))
    }

    return CanonicalUsage(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWriteTokens = cacheWriteTokens,
        reasoningTokens = reasoningTokens,
    )
}


private fun _attr(obj: Any, name: String): Any? {
    if (obj is Map<*, *>) return obj[name]
    return try {
        val f = obj.javaClass.getField(name)
        f.get(obj)
    } catch (_: Exception) {
        null
    }
}


fun estimateUsageCost(
    modelName: String,
    usage: CanonicalUsage,
    provider: String? = null,
    baseUrl: String? = null,
    apiKey: String? = null,
): CostResult {
    val route = resolveBillingRoute(modelName, provider = provider, baseUrl = baseUrl)
    if (route.billingMode == "subscription_included") {
        return CostResult(
            amountUsd = _ZERO,
            status = "included",
            source = "none",
            label = "included",
            pricingVersion = "included-route",
        )
    }

    val entry = getPricingEntry(modelName, provider = provider, baseUrl = baseUrl, apiKey = apiKey)
        ?: return CostResult(amountUsd = null, status = "unknown", source = "none", label = "n/a")

    val notes = mutableListOf<String>()
    var amount = _ZERO

    if (usage.inputTokens != 0 && entry.inputCostPerMillion == null) {
        return CostResult(amountUsd = null, status = "unknown", source = entry.source, label = "n/a")
    }
    if (usage.outputTokens != 0 && entry.outputCostPerMillion == null) {
        return CostResult(amountUsd = null, status = "unknown", source = entry.source, label = "n/a")
    }
    if (usage.cacheReadTokens != 0 && entry.cacheReadCostPerMillion == null) {
        return CostResult(
            amountUsd = null,
            status = "unknown",
            source = entry.source,
            label = "n/a",
            notes = listOf("cache-read pricing unavailable for route"),
        )
    }
    if (usage.cacheWriteTokens != 0 && entry.cacheWriteCostPerMillion == null) {
        return CostResult(
            amountUsd = null,
            status = "unknown",
            source = entry.source,
            label = "n/a",
            notes = listOf("cache-write pricing unavailable for route"),
        )
    }

    if (entry.inputCostPerMillion != null) {
        amount = amount.add(BigDecimal(usage.inputTokens).multiply(entry.inputCostPerMillion).divide(_ONE_MILLION))
    }
    if (entry.outputCostPerMillion != null) {
        amount = amount.add(BigDecimal(usage.outputTokens).multiply(entry.outputCostPerMillion).divide(_ONE_MILLION))
    }
    if (entry.cacheReadCostPerMillion != null) {
        amount = amount.add(BigDecimal(usage.cacheReadTokens).multiply(entry.cacheReadCostPerMillion).divide(_ONE_MILLION))
    }
    if (entry.cacheWriteCostPerMillion != null) {
        amount = amount.add(BigDecimal(usage.cacheWriteTokens).multiply(entry.cacheWriteCostPerMillion).divide(_ONE_MILLION))
    }
    if (entry.requestCost != null && usage.requestCount != 0) {
        amount = amount.add(BigDecimal(usage.requestCount).multiply(entry.requestCost))
    }

    var status = "estimated"
    var label = "~$" + String.format("%.2f", amount.toDouble())
    if (entry.source == "none" && amount == _ZERO) {
        status = "included"
        label = "included"
    }

    if (route.provider == "openrouter") {
        notes.add("OpenRouter cost is estimated from the models API until reconciled.")
    }

    return CostResult(
        amountUsd = amount,
        status = status,
        source = entry.source,
        label = label,
        fetchedAt = entry.fetchedAt,
        pricingVersion = entry.pricingVersion,
        notes = notes.toList(),
    )
}


/**
 * Check whether we have pricing data for this model+route.
 *
 * Uses direct lookup instead of routing through the full estimation
 * pipeline — avoids creating dummy usage objects just to check status.
 */
fun hasKnownPricing(
    modelName: String,
    provider: String? = null,
    baseUrl: String? = null,
    apiKey: String? = null,
): Boolean {
    val route = resolveBillingRoute(modelName, provider = provider, baseUrl = baseUrl)
    if (route.billingMode == "subscription_included") return true
    return getPricingEntry(modelName, provider = provider, baseUrl = baseUrl, apiKey = apiKey) != null
}


fun formatDurationCompact(seconds: Double): String {
    if (seconds < 60) return String.format("%.0fs", seconds)
    val minutes = seconds / 60
    if (minutes < 60) return String.format("%.0fm", minutes)
    val hours = minutes / 60
    if (hours < 24) {
        val remainingMin = (minutes % 60).toInt()
        return if (remainingMin != 0) "${hours.toInt()}h ${remainingMin}m" else "${hours.toInt()}h"
    }
    val days = hours / 24
    return String.format("%.1fd", days)
}


fun formatTokenCountCompact(value: Int): String {
    val absValue = kotlin.math.abs(value)
    if (absValue < 1_000) return value.toString()

    val sign = if (value < 0) "-" else ""
    val units = listOf(
        1_000_000_000L to "B",
        1_000_000L to "M",
        1_000L to "K",
    )
    for ((threshold, suffix) in units) {
        if (absValue >= threshold) {
            val scaled = absValue.toDouble() / threshold
            var text = when {
                scaled < 10 -> String.format("%.2f", scaled)
                scaled < 100 -> String.format("%.1f", scaled)
                else -> String.format("%.0f", scaled)
            }
            if ("." in text) text = text.trimEnd('0').trimEnd('.')
            return "$sign$text$suffix"
        }
    }
    return String.format("%,d", value)
}
