package com.xiaomo.androidforclaw.agent.loop

/**
 * Provider System Prompt Contribution — 对齐 OpenClaw system-prompt-contribution.ts
 *
 * 允许各 provider 插件贡献额外的系统提示文本：
 * - stablePrefix: 缓存稳定的前缀（放在缓存边界之上）
 * - dynamicSuffix: 动态后缀（放在缓存边界之下，每次请求不同）
 * - sectionOverrides: 整段替换核心 prompt 区段
 *
 * OpenClaw 源: ../openclaw/src/agents/system-prompt-contribution.ts
 */

// ── Types ──

/**
 * 可被 provider 覆盖的系统 prompt 区段 ID。
 * OpenClaw: ProviderSystemPromptSectionId
 */
enum class ProviderSystemPromptSectionId {
    /** 交互风格 */
    INTERACTION_STYLE,
    /** 工具调用风格 */
    TOOL_CALL_STYLE,
    /** 执行偏好 */
    EXECUTION_BIAS
}

/**
 * Provider 的系统 prompt 贡献。
 * OpenClaw: ProviderSystemPromptContribution
 */
data class ProviderSystemPromptContribution(
    /**
     * 缓存稳定的前缀 — 放在系统 prompt 缓存边界之上。
     * 适用于静态的 provider/model 系列指令，可保持 KV 缓存复用。
     */
    val stablePrefix: String? = null,

    /**
     * 动态后缀 — 放在缓存边界之下。
     * 仅用于真正动态的文本（每次运行/会话不同）。
     */
    val dynamicSuffix: String? = null,

    /**
     * 整段替换核心 prompt 区段。
     * 值应包含完整的渲染区段，包括标题如 `## Tool Call Style`。
     */
    val sectionOverrides: Map<ProviderSystemPromptSectionId, String>? = null
)

// ── Resolver Interface ──

/**
 * Provider 贡献解析器接口。
 * 各 provider 可实现此接口来贡献系统提示。
 *
 * OpenClaw: ProviderPlugin.resolveSystemPromptContribution
 */
interface ProviderSystemPromptContributor {
    /**
     * 解析 provider 的系统 prompt 贡献。
     * @return 贡献内容，或 null 表示无贡献。
     */
    fun resolveSystemPromptContribution(
        provider: String,
        modelId: String
    ): ProviderSystemPromptContribution?
}

// ── Default Implementations ──

/**
 * Anthropic provider 贡献 — 强调工具调用格式。
 */
class AnthropicSystemPromptContributor : ProviderSystemPromptContributor {
    override fun resolveSystemPromptContribution(provider: String, modelId: String): ProviderSystemPromptContribution? {
        if (provider.lowercase() != "anthropic") return null
        return ProviderSystemPromptContribution(
            stablePrefix = "When using tools, ensure JSON arguments are valid and complete."
        )
    }
}

/**
 * OpenAI provider 贡献 — GPT-5 特定指令。
 */
class OpenAISystemPromptContributor : ProviderSystemPromptContributor {
    override fun resolveSystemPromptContribution(provider: String, modelId: String): ProviderSystemPromptContribution? {
        if (provider.lowercase() != "openai" && provider.lowercase() != "openai-codex") return null
        if (!modelId.startsWith("gpt-5", ignoreCase = true)) return null
        return ProviderSystemPromptContribution(
            stablePrefix = "Always take concrete actions rather than just describing what you will do.",
            sectionOverrides = mapOf(
                ProviderSystemPromptSectionId.EXECUTION_BIAS to
                    "## Execution Bias\nPrefer acting over planning. If you can take a tool action, do it immediately rather than describing it first."
            )
        )
    }
}

/**
 * 解析所有注册的 contributor 并合并贡献。
 * OpenClaw: resolveProviderSystemPromptContribution
 *
 * @param contributors 已注册的 contributor 列表
 * @param provider provider 名称
 * @param modelId 模型 ID
 * @return 合并后的贡献（所有非 null 的 stablePrefix 用换行连接，最后一个 dynamicSuffix 生效）
 */
fun resolveProviderSystemPromptContribution(
    contributors: List<ProviderSystemPromptContributor>,
    provider: String,
    modelId: String
): ProviderSystemPromptContribution? {
    val contributions = contributors.mapNotNull { it.resolveSystemPromptContribution(provider, modelId) }
    if (contributions.isEmpty()) return null

    val stablePrefixes = contributions.mapNotNull { it.stablePrefix }.filter { it.isNotBlank() }
    val dynamicSuffixes = contributions.mapNotNull { it.dynamicSuffix }.filter { it.isNotBlank() }
    val sectionOverrides = mutableMapOf<ProviderSystemPromptSectionId, String>()
    for (c in contributions) {
        c.sectionOverrides?.let { sectionOverrides.putAll(it) }
    }

    return ProviderSystemPromptContribution(
        stablePrefix = stablePrefixes.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        dynamicSuffix = dynamicSuffixes.lastOrNull(),
        sectionOverrides = sectionOverrides.takeIf { it.isNotEmpty() }
    )
}

/**
 * 将 provider 贡献应用到系统 prompt。
 * 返回注入贡献后的完整 prompt。
 *
 * @param basePrompt 原始系统 prompt
 * @param contribution provider 贡献
 * @return 注入后的 prompt
 */
fun applyProviderContribution(basePrompt: String, contribution: ProviderSystemPromptContribution?): String {
    if (contribution == null) return basePrompt

    val parts = mutableListOf<String>()

    // Stable prefix — goes above cache boundary (at the top)
    contribution.stablePrefix?.let { if (it.isNotBlank()) parts.add(it) }

    parts.add(basePrompt)

    // Dynamic suffix — goes below cache boundary (at the end)
    contribution.dynamicSuffix?.let { if (it.isNotBlank()) parts.add(it) }

    // Section overrides — replace sections in the base prompt
    var result = parts.joinToString("\n\n")
    for ((sectionId, sectionContent) in contribution.sectionOverrides ?: emptyMap()) {
        val sectionName = when (sectionId) {
            ProviderSystemPromptSectionId.INTERACTION_STYLE -> "Interaction Style"
            ProviderSystemPromptSectionId.TOOL_CALL_STYLE -> "Tool Call Style"
            ProviderSystemPromptSectionId.EXECUTION_BIAS -> "Execution Bias"
        }
        // Try to replace existing section, or append if not found
        val sectionPattern = Regex("""##\s*$sectionName\s*\n(.*?)(?=\n##|\Z)""", RegexOption.DOT_MATCHES_ALL)
        if (sectionPattern.containsMatchIn(result)) {
            result = result.replace(sectionPattern, sectionContent)
        } else {
            result = "$result\n\n$sectionContent"
        }
    }

    return result
}
