package com.xiaomo.hermes.hermes.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * UsagePricing — model → pricing lookup + cost estimation.
 *
 * Requirement: R-AGENT-008 (usage pricing table powers cost rotation)
 * Test cases:
 *   TC-AGENT-224-a — price lookup: resolveBillingRoute + getPricingEntry
 *                    recognize known providers and return the official-docs
 *                    snapshot pricing.
 */
class UsagePricingTest {

    /** TC-AGENT-224-a — anthropic Opus 4 model looks up the official docs snapshot. */
    @Test
    fun `getPricingEntry resolves anthropic claude-opus-4`() {
        val entry = getPricingEntry("claude-opus-4-20250514", provider = "anthropic")
        assertNotNull(entry)
        assertEquals("official_docs_snapshot", entry!!.source)
        assertEquals(BigDecimal("15.00"), entry.inputCostPerMillion)
        assertEquals(BigDecimal("75.00"), entry.outputCostPerMillion)
    }

    /** TC-AGENT-224-a continued — resolveBillingRoute extracts the model from
     *  "anthropic/model" style names when no explicit provider is passed. */
    @Test
    fun `resolveBillingRoute splits provider prefix from model`() {
        val route = resolveBillingRoute("anthropic/claude-sonnet-4-20250514")
        assertEquals("anthropic", route.provider)
        assertEquals("claude-sonnet-4-20250514", route.model)
        assertEquals("official_docs_snapshot", route.billingMode)
    }

    /** Codex always reports "subscription_included" — cost tracked elsewhere. */
    @Test
    fun `resolveBillingRoute marks codex as subscription_included`() {
        val route = resolveBillingRoute("gpt-5-codex", provider = "openai-codex")
        assertEquals("openai-codex", route.provider)
        assertEquals("subscription_included", route.billingMode)
    }

    /** OpenRouter uses the models API, not the official docs snapshot. */
    @Test
    fun `resolveBillingRoute marks openrouter as models_api`() {
        val route = resolveBillingRoute("anthropic/claude-opus-4", provider = "openrouter")
        assertEquals("openrouter", route.provider)
        assertEquals("official_models_api", route.billingMode)
    }

    /** baseUrl hitting openrouter.ai infers the provider even without explicit flag. */
    @Test
    fun `resolveBillingRoute infers openrouter from baseUrl host`() {
        val route = resolveBillingRoute("some-model", baseUrl = "https://openrouter.ai/api/v1")
        assertEquals("openrouter", route.provider)
        assertEquals("official_models_api", route.billingMode)
    }

    /** Custom / local providers route to billingMode=unknown. */
    @Test
    fun `resolveBillingRoute marks custom provider as unknown`() {
        val route = resolveBillingRoute("my-model", provider = "custom")
        assertEquals("custom", route.provider)
        assertEquals("unknown", route.billingMode)
    }

    /** Localhost baseUrl → billingMode unknown regardless of provider. */
    @Test
    fun `resolveBillingRoute localhost baseUrl becomes unknown`() {
        val route = resolveBillingRoute("m", provider = "", baseUrl = "http://localhost:8080/v1")
        assertEquals("unknown", route.billingMode)
    }

    /** getPricingEntry with subscription_included returns zero-priced entry. */
    @Test
    fun `getPricingEntry codex returns zero-priced included entry`() {
        val entry = getPricingEntry("gpt-5-codex", provider = "openai-codex")
        assertNotNull(entry)
        assertEquals(BigDecimal.ZERO.toPlainString(), entry!!.inputCostPerMillion!!.stripTrailingZeros().toPlainString())
        assertEquals("included-route", entry.pricingVersion)
    }

    /** Unknown model → getPricingEntry returns null. */
    @Test
    fun `getPricingEntry unknown model returns null`() {
        assertNull(getPricingEntry("bogus-model-name-xyz", provider = "anthropic"))
    }

    /** hasKnownPricing reflects the getPricingEntry outcome. */
    @Test
    fun `hasKnownPricing returns true for known anthropic model`() {
        assertTrue(hasKnownPricing("claude-opus-4-20250514", provider = "anthropic"))
    }

    @Test
    fun `hasKnownPricing returns false for unknown model`() {
        assertFalse(hasKnownPricing("no-such-model", provider = "anthropic"))
    }

    /** Subscription models are always "known" via the route itself. */
    @Test
    fun `hasKnownPricing returns true for codex subscription`() {
        assertTrue(hasKnownPricing("any-codex-model", provider = "openai-codex"))
    }

    // -----------------------------------------------------------------------
    // normalizeUsage — converts Anthropic / Codex / OpenAI shapes into canonical
    // -----------------------------------------------------------------------

    /** Anthropic shape: inputTokens + cache buckets break out distinctly. */
    @Test
    fun `normalizeUsage parses anthropic shape`() {
        val raw = mapOf(
            "input_tokens" to 100,
            "output_tokens" to 50,
            "cache_read_input_tokens" to 20,
            "cache_creation_input_tokens" to 10,
        )
        val usage = normalizeUsage(raw, provider = "anthropic", apiMode = "anthropic_messages")
        assertEquals(100, usage.inputTokens)
        assertEquals(50, usage.outputTokens)
        assertEquals(20, usage.cacheReadTokens)
        assertEquals(10, usage.cacheWriteTokens)
    }

    /** Codex shape: input_tokens is the total; cache buckets are subtracted. */
    @Test
    fun `normalizeUsage codex subtracts cache from input`() {
        val raw = mapOf(
            "input_tokens" to 100,
            "output_tokens" to 50,
            "input_tokens_details" to mapOf(
                "cached_tokens" to 30,
                "cache_creation_tokens" to 10,
            ),
        )
        val usage = normalizeUsage(raw, provider = "openai-codex", apiMode = "codex_responses")
        assertEquals(60, usage.inputTokens) // 100 - 30 - 10
        assertEquals(50, usage.outputTokens)
        assertEquals(30, usage.cacheReadTokens)
        assertEquals(10, usage.cacheWriteTokens)
    }

    /** OpenAI ChatCompletions: prompt_tokens is the total, cached_tokens carved out. */
    @Test
    fun `normalizeUsage openai subtracts cache from prompt`() {
        val raw = mapOf(
            "prompt_tokens" to 200,
            "completion_tokens" to 80,
            "prompt_tokens_details" to mapOf("cached_tokens" to 50),
        )
        val usage = normalizeUsage(raw, provider = "openai")
        assertEquals(150, usage.inputTokens)
        assertEquals(80, usage.outputTokens)
        assertEquals(50, usage.cacheReadTokens)
    }

    /** null usage returns zeroed canonical. */
    @Test
    fun `normalizeUsage null returns zero`() {
        val usage = normalizeUsage(null)
        assertEquals(0, usage.inputTokens)
        assertEquals(0, usage.outputTokens)
    }

    /** reasoning_tokens is extracted from output_tokens_details. */
    @Test
    fun `normalizeUsage picks reasoning_tokens from output details`() {
        val raw = mapOf(
            "input_tokens" to 10,
            "output_tokens" to 20,
            "output_tokens_details" to mapOf("reasoning_tokens" to 7),
        )
        val usage = normalizeUsage(raw, provider = "anthropic", apiMode = "anthropic_messages")
        assertEquals(7, usage.reasoningTokens)
    }

    /** CanonicalUsage.promptTokens aggregates input + cache buckets. */
    @Test
    fun `CanonicalUsage prompt and total token aggregations`() {
        val u = CanonicalUsage(
            inputTokens = 100,
            outputTokens = 50,
            cacheReadTokens = 20,
            cacheWriteTokens = 10,
        )
        assertEquals(130, u.promptTokens())
        assertEquals(180, u.totalTokens())
    }

    // -----------------------------------------------------------------------
    // estimateUsageCost — cost math
    // -----------------------------------------------------------------------

    /** Codex returns "included" cost status. */
    @Test
    fun `estimateUsageCost codex is included`() {
        val usage = CanonicalUsage(inputTokens = 1000, outputTokens = 500)
        val cost = estimateUsageCost("gpt-5-codex", usage, provider = "openai-codex")
        assertEquals("included", cost.status)
        assertEquals(BigDecimal.ZERO.toPlainString(), cost.amountUsd!!.stripTrailingZeros().toPlainString())
    }

    /** anthropic Opus with a real usage vector gives non-zero estimated cost. */
    @Test
    fun `estimateUsageCost anthropic opus produces estimated`() {
        val usage = CanonicalUsage(inputTokens = 1_000_000, outputTokens = 0)
        val cost = estimateUsageCost("claude-opus-4-20250514", usage, provider = "anthropic")
        assertEquals("estimated", cost.status)
        // $15 / 1M input tokens × 1M = $15
        assertEquals(BigDecimal("15.00"), cost.amountUsd!!.stripTrailingZeros().setScale(2))
    }

    /** Unknown model: cost is "unknown" with n/a label. */
    @Test
    fun `estimateUsageCost unknown model returns unknown status`() {
        val usage = CanonicalUsage(inputTokens = 10, outputTokens = 10)
        val cost = estimateUsageCost("nope-model", usage, provider = "anthropic")
        assertEquals("unknown", cost.status)
        assertEquals("n/a", cost.label)
        assertNull(cost.amountUsd)
    }

    // -----------------------------------------------------------------------
    // format helpers
    // -----------------------------------------------------------------------

    @Test
    fun `formatDurationCompact picks seconds for short durations`() {
        assertEquals("45s", formatDurationCompact(45.0))
        assertEquals("1s", formatDurationCompact(1.0))
    }

    @Test
    fun `formatDurationCompact picks minutes for mid durations`() {
        assertEquals("5m", formatDurationCompact(300.0))
        assertEquals("59m", formatDurationCompact(59 * 60.0))
    }

    @Test
    fun `formatDurationCompact picks hours for long durations`() {
        assertEquals("1h", formatDurationCompact(3600.0))
        assertTrue(formatDurationCompact(3600.0 + 30 * 60).startsWith("1h"))
    }

    @Test
    fun `formatTokenCountCompact uses raw for small values`() {
        assertEquals("500", formatTokenCountCompact(500))
        assertEquals("0", formatTokenCountCompact(0))
    }

    @Test
    fun `formatTokenCountCompact uses K for thousands`() {
        assertTrue(formatTokenCountCompact(1_000).endsWith("K"))
        assertTrue(formatTokenCountCompact(5_500).endsWith("K"))
    }

    @Test
    fun `formatTokenCountCompact uses M for millions`() {
        assertTrue(formatTokenCountCompact(1_500_000).endsWith("M"))
    }
}
