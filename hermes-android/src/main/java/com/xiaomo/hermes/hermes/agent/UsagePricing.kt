package com.xiaomo.hermes.hermes.agent

/**
 * Usage Pricing - 价格/费用计算
 * 1:1 对齐 hermes/agent/usage_pricing.py
 *
 * 管理各 provider/model 的 token 价格，计算 API 调用费用。
 */

data class Pricing(
    val inputPricePerMillion: Double = 0.0,   // 每百万输入 token 价格（美元）
    val outputPricePerMillion: Double = 0.0,  // 每百万输出 token 价格（美元）
    val cacheReadPricePerMillion: Double? = null,   // 缓存读取价格
    val cacheWritePricePerMillion: Double? = null,  // 缓存写入价格
)

data class UsageResult(
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val inputCost: Double,
    val outputCost: Double,
    val cacheReadCost: Double = 0.0,
    val cacheWriteCost: Double = 0.0,
    val totalCost: Double,
    val model: String,
    val provider: String
)

class UsagePricing {

    companion object {
        // 价格数据来源：models.dev + provider 官网
        // 格式：provider -> model_pattern -> Pricing
        private val PRICING_TABLE: Map<String, Map<String, Pricing>> = mapOf(
            "anthropic" to mapOf(
                "claude-opus-4-6" to Pricing(15.0, 75.0, 1.5, 18.75),
                "claude-sonnet-4-6" to Pricing(3.0, 15.0, 0.3, 3.75),
                "claude-sonnet-4" to Pricing(3.0, 15.0, 0.3, 3.75),
                "claude-haiku-3.5" to Pricing(0.8, 4.0, 0.08, 1.0),
                "claude-haiku-3" to Pricing(0.25, 1.25, 0.03, 0.3),
                "claude" to Pricing(3.0, 15.0, 0.3, 3.75),  // fallback
            ),
            "openai" to mapOf(
                "gpt-5.1-codex-max" to Pricing(2.5, 10.0),
                "gpt-5" to Pricing(2.5, 10.0),
                "gpt-4.1" to Pricing(2.0, 8.0, 0.5, 2.0),
                "gpt-4o" to Pricing(2.5, 10.0, 1.25, 2.5),
                "gpt-4o-mini" to Pricing(0.15, 0.6, 0.075, 0.15),
                "o1" to Pricing(15.0, 60.0),
                "o1-mini" to Pricing(3.0, 12.0),
                "o1-pro" to Pricing(150.0, 600.0),
                "o3-mini" to Pricing(1.1, 4.4),
                "o3" to Pricing(2.0, 8.0),
                "o4-mini" to Pricing(1.1, 4.4),
                "gpt-4" to Pricing(30.0, 60.0)),
            "openrouter" to mapOf(
                // OpenRouter 加价 5-10%，这里用上游价格近似
                "anthropic/claude-opus-4-6" to Pricing(15.0, 75.0),
                "anthropic/claude-sonnet-4-6" to Pricing(3.0, 15.0),
                "anthropic/claude-sonnet-4" to Pricing(3.0, 15.0),
                "anthropic/claude-haiku-3.5" to Pricing(0.8, 4.0),
                "openai/gpt-4o" to Pricing(2.5, 10.0),
                "openai/gpt-4o-mini" to Pricing(0.15, 0.6),
                "google/gemini-2.5-pro" to Pricing(1.25, 10.0),
                "google/gemini-2.5-flash" to Pricing(0.15, 0.6),
                "deepseek/deepseek-chat" to Pricing(0.14, 0.28),
                "deepseek/deepseek-reasoner" to Pricing(0.55, 2.19),
                // fallback
                "" to Pricing(0.0, 0.0)),
            "google" to mapOf(
                "gemini-2.5-pro" to Pricing(1.25, 10.0),
                "gemini-2.5-flash" to Pricing(0.15, 0.6),
                "gemini-2.0-flash" to Pricing(0.1, 0.4),
                "gemini" to Pricing(1.25, 10.0),  // fallback
            ),
            "deepseek" to mapOf(
                "deepseek-chat" to Pricing(0.14, 0.28),
                "deepseek-reasoner" to Pricing(0.55, 2.19),
                "deepseek" to Pricing(0.14, 0.28),  // fallback
            ),
            "xai" to mapOf(
                "grok-4" to Pricing(3.0, 15.0),
                "grok-3" to Pricing(3.0, 15.0),
                "grok-3-mini" to Pricing(0.3, 0.5),
                "grok" to Pricing(3.0, 15.0)),
            "minimax" to mapOf(
                "minimax" to Pricing(0.0, 0.0),  // 免费额度
            ))

        // OpenRouter 模型到上游的映射
        private val OPENROUTER_UPSTREAM: Map<String, Pair<String, String>> = mapOf(
            "anthropic/claude-opus-4-6" to ("anthropic" to "claude-opus-4-6"),
            "anthropic/claude-sonnet-4-6" to ("anthropic" to "claude-sonnet-4-6"),
            "anthropic/claude-sonnet-4" to ("anthropic" to "claude-sonnet-4"),
            "openai/gpt-4o" to ("openai" to "gpt-4o"),
            "openai/gpt-4o-mini" to ("openai" to "gpt-4o-mini"),
            "google/gemini-2.5-pro" to ("google" to "gemini-2.5-pro"))
    }

    /**
     * 获取模型的定价信息
     *
     * @param provider provider 名称
     * @param model 模型 ID
     * @return 定价信息，未找到返回 null
     */
    fun getPricing(provider: String, model: String): Pricing? {
        val providerKey = provider.lowercase().trim()
        val modelLower = model.lowercase().trim()

        // 直接查找
        val providerPricing = PRICING_TABLE[providerKey] ?: return null

        // 精确匹配
        providerPricing[modelLower]?.let { return it }

        // 模糊匹配（最长前缀匹配）
        val matched = providerPricing.entries
            .filter { (pattern, _) -> pattern.isNotEmpty() && modelLower.contains(pattern) }
            .maxByOrNull { it.key.length }

        return matched?.value
    }

    /**
     * 计算 API 调用费用
     *
     * @param provider provider 名称
     * @param model 模型 ID
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param cacheReadTokens 缓存读取 token 数
     * @param cacheWriteTokens 缓存写入 token 数
     * @return 费用计算结果
     */
    fun calculateCost(
        provider: String,
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        cacheReadTokens: Int = 0,
        cacheWriteTokens: Int = 0
    ): UsageResult {
        val pricing = getPricing(provider, model) ?: Pricing(0.0, 0.0)

        val inputCost = inputTokens * pricing.inputPricePerMillion / 1_000_000
        val outputCost = outputTokens * pricing.outputPricePerMillion / 1_000_000
        val cacheReadCost = cacheReadTokens * (pricing.cacheReadPricePerMillion ?: pricing.inputPricePerMillion) / 1_000_000
        val cacheWriteCost = cacheWriteTokens * (pricing.cacheWritePricePerMillion ?: pricing.inputPricePerMillion) / 1_000_000

        return UsageResult(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWriteTokens = cacheWriteTokens,
            inputCost = inputCost,
            outputCost = outputCost,
            cacheReadCost = cacheReadCost,
            cacheWriteCost = cacheWriteCost,
            totalCost = inputCost + outputCost + cacheReadCost + cacheWriteCost,
            model = model,
            provider = provider
        )
    }

    /**
     * 格式化费用为人类可读字符串
     *
     * @param cost 费用（美元）
     * @return 格式化字符串
     */
    fun formatCost(cost: Double): String {
        return when {
            cost < 0.0001 -> "$0.00"
            cost < 0.01 -> String.format("$%.4f", cost)
            cost < 1.0 -> String.format("$%.3f", cost)
            else -> String.format("$%.2f", cost)
        }
    }

    /**
     * 获取所有已知 provider 列表
     */
    fun getKnownProviders(): List<String> {
        return PRICING_TABLE.keys.toList()
    }

    /**
     * 获取指定 provider 的所有已知模型
     */
    fun getKnownModels(provider: String): List<String> {
        return PRICING_TABLE[provider.lowercase()]?.keys?.filter { it.isNotEmpty() }?.toList() ?: emptyList()
    
    /** Get total token count from UsageResult. */
    fun UsageResult.totalTokens(): Int = inputTokens + outputTokens
}


    fun promptTokens(): Int {
        return 0
    }
    fun totalTokens(): Int {
        return 0
    }

}

data class CanonicalUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val requestCount: Int = 1,
    val rawUsage: Map<String, Any>? = null
) {
    val promptTokens: Int get() = inputTokens + cacheReadTokens + cacheWriteTokens
    val totalTokens: Int get() = promptTokens + outputTokens
}

data class BillingRoute(
    val provider: String,
    val model: String,
    val baseUrl: String = "",
    val billingMode: String = "unknown"
)

data class PricingEntry(
    val inputCostPerMillion: Double? = null,
    val outputCostPerMillion: Double? = null,
    val cacheReadCostPerMillion: Double? = null,
    val cacheWriteCostPerMillion: Double? = null,
    val requestCost: Double? = null,
    val source: String = "none",
    val sourceUrl: String? = null,
    val pricingVersion: String? = null,
    val fetchedAt: Long? = null
)

data class CostResult(
    val amountUsd: Double?,
    val status: String,
    val source: String,
    val label: String,
    val fetchedAt: Long? = null,
    val pricingVersion: String? = null,
    val notes: List<String> = emptyList()
)
