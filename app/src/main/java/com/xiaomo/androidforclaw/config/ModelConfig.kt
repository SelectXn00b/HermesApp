package com.xiaomo.androidforclaw.config

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all), ../openclaw/types/(all).d.ts
 *
 * AndroidForClaw adaptation: provider/model config structures.
 */


/**
 * Model Configuration Data Classes
 * 解析由 ConfigLoader 的 JSONObject 处理，不依赖 Gson 注解。
 */

data class ModelsConfig(
    val mode: String = "merge",
    val providers: Map<String, ProviderConfig> = emptyMap()
)

data class ProviderConfig(
    val baseUrl: String,
    val apiKey: String? = null,
    val api: String = "openai-completions",
    val auth: String? = null,
    val authHeader: Boolean = true,
    val headers: Map<String, String>? = null,
    val injectNumCtxForOpenAICompat: Boolean? = null,
    val models: List<ModelDefinition> = emptyList()
)

data class ModelDefinition(
    val id: String,
    val name: String,
    val api: String? = null,
    val reasoning: Boolean = false,
    val input: List<Any> = listOf("text"),
    val cost: CostConfig? = null,
    val contextWindow: Int = 128000,
    val maxTokens: Int = 8192,
    val headers: Map<String, String>? = null,
    val compat: ModelCompatConfig? = null
)

data class ModelCompatConfig(
    val supportsStore: Boolean? = null,
    val supportsReasoningEffort: Boolean? = null,
    val maxTokensField: String? = null,
    val thinkingFormat: String? = null,
    val requiresToolResultName: Boolean? = null,
    val requiresAssistantAfterToolResult: Boolean? = null
)

data class CostConfig(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheRead: Double = 0.0,
    val cacheWrite: Double = 0.0
)

/**
 * API type constants
 */
object ModelApi {
    const val OPENAI_COMPLETIONS = "openai-completions"
    const val OPENAI_RESPONSES = "openai-responses"
    const val OPENAI_CODEX_RESPONSES = "openai-codex-responses"
    const val ANTHROPIC_MESSAGES = "anthropic-messages"
    const val GOOGLE_GENERATIVE_AI = "google-generative-ai"
    const val GITHUB_COPILOT = "github-copilot"
    const val BEDROCK_CONVERSE_STREAM = "bedrock-converse-stream"
    const val OLLAMA = "ollama"

    val ALL_APIS = listOf(
        OPENAI_COMPLETIONS, OPENAI_RESPONSES, OPENAI_CODEX_RESPONSES,
        ANTHROPIC_MESSAGES, GOOGLE_GENERATIVE_AI,
        GITHUB_COPILOT, BEDROCK_CONVERSE_STREAM, OLLAMA
    )

    fun isValidApi(api: String): Boolean = api in ALL_APIS

    fun isOpenAICompat(api: String): Boolean = api in listOf(
        OPENAI_COMPLETIONS, OPENAI_RESPONSES, OPENAI_CODEX_RESPONSES,
        OLLAMA, GITHUB_COPILOT
    )
}
