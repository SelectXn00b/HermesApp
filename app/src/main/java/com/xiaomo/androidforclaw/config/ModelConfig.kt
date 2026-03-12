package com.xiaomo.androidforclaw.config

import com.google.gson.annotations.SerializedName

/**
 * Model Configuration Data Classes - Aligned with OpenClaw config format
 *
 * Config file location: /sdcard/.androidforclaw/config/models.json
 *
 * Reference: OpenClaw src/config/types.models.ts
 */

/**
 * Top-level model configuration
 */
data class ModelsConfig(
    @SerializedName("mode")
    val mode: String = "merge",  // "merge" | "replace"

    @SerializedName("providers")
    val providers: Map<String, ProviderConfig> = emptyMap()
)

/**
 * Provider configuration
 */
data class ProviderConfig(
    @SerializedName("baseUrl")
    val baseUrl: String,  // API endpoint base URL (required)

    @SerializedName("apiKey")
    val apiKey: String? = null,  // API key (optional, supports ${ENV_VAR} format)

    @SerializedName("api")
    val api: String = "openai-completions",  // API type

    @SerializedName("auth")
    val auth: String? = null,  // Authentication mode: "api-key" | "oauth" | "token"

    @SerializedName("authHeader")
    val authHeader: Boolean = true,  // Whether to send API key in Authorization header

    @SerializedName("headers")
    val headers: Map<String, String>? = null,  // Custom HTTP headers

    @SerializedName("injectNumCtxForOpenAICompat")
    val injectNumCtxForOpenAICompat: Boolean? = null,  // OpenAI compatibility flag

    @SerializedName("models")
    val models: List<ModelDefinition> = emptyList()  // Model definition array
)

/**
 * Model definition
 */
data class ModelDefinition(
    @SerializedName("id")
    val id: String,  // Model ID (e.g., "claude-opus-4-6")

    @SerializedName("name")
    val name: String,  // Model display name

    @SerializedName("api")
    val api: String? = null,  // Model-level API type override (optional)

    @SerializedName("reasoning")
    val reasoning: Boolean = false,  // Whether supports reasoning/thinking (Extended Thinking)

    @SerializedName("input")
    val input: List<Any> = listOf("text"),  // Supported input types: ["text", "image"] or [{"type":"text"}]

    @SerializedName("cost")
    val cost: CostConfig = CostConfig(),  // Cost configuration

    @SerializedName("contextWindow")
    val contextWindow: Int = 128000,  // Context window size (tokens)

    @SerializedName("maxTokens")
    val maxTokens: Int = 8192,  // Maximum completion tokens

    @SerializedName("headers")
    val headers: Map<String, String>? = null,  // Model-level custom headers (optional)

    @SerializedName("compat")
    val compat: ModelCompatConfig? = null  // Compatibility configuration (optional)
)

/**
 * Model compatibility configuration
 * Used to handle differences in different model APIs
 */
data class ModelCompatConfig(
    @SerializedName("supportsStore")
    val supportsStore: Boolean? = null,  // Whether supports session storage

    @SerializedName("supportsDeveloperRole")
    val supportsDeveloperRole: Boolean? = null,  // Whether supports developer role

    @SerializedName("supportsReasoningEffort")
    val supportsReasoningEffort: Boolean? = null,  // Whether supports reasoning effort control

    @SerializedName("supportsUsageInStreaming")
    val supportsUsageInStreaming: Boolean? = null,  // Whether includes usage in streaming output

    @SerializedName("supportsStrictMode")
    val supportsStrictMode: Boolean? = null,  // Whether supports strict mode

    @SerializedName("maxTokensField")
    val maxTokensField: String? = null,  // maxTokens field name: "max_completion_tokens" | "max_tokens"

    @SerializedName("thinkingFormat")
    val thinkingFormat: String? = null,  // Thinking format: "openai" | "zai" | "qwen"

    @SerializedName("requiresToolResultName")
    val requiresToolResultName: Boolean? = null,  // Whether requires name field in tool_result

    @SerializedName("requiresAssistantAfterToolResult")
    val requiresAssistantAfterToolResult: Boolean? = null,  // Whether requires assistant message after tool_result

    @SerializedName("requiresThinkingAsText")
    val requiresThinkingAsText: Boolean? = null,  // Whether requires thinking content as plain text

    @SerializedName("requiresMistralToolIds")
    val requiresMistralToolIds: Boolean? = null  // Whether requires Mistral-style tool ID
)

/**
 * Cost configuration (unit: USD per 1M tokens)
 */
data class CostConfig(
    @SerializedName("input")
    val input: Double = 0.0,  // Input cost

    @SerializedName("output")
    val output: Double = 0.0,  // Output cost

    @SerializedName("cacheRead")
    val cacheRead: Double = 0.0,  // Cache read cost

    @SerializedName("cacheWrite")
    val cacheWrite: Double = 0.0  // Cache write cost
)

/**
 * API type constants
 * Aligned with MODEL_APIS in OpenClaw src/config/types.models.ts
 */
object ModelApi {
    const val OPENAI_COMPLETIONS = "openai-completions"  // OpenAI Chat Completions API
    const val OPENAI_RESPONSES = "openai-responses"  // OpenAI Responses API (streaming)
    const val OPENAI_CODEX_RESPONSES = "openai-codex-responses"  // OpenAI Codex API
    const val ANTHROPIC_MESSAGES = "anthropic-messages"  // Anthropic Messages API
    const val GOOGLE_GENERATIVE_AI = "google-generative-ai"  // Google Gemini API
    const val GITHUB_COPILOT = "github-copilot"  // GitHub Copilot API
    const val BEDROCK_CONVERSE_STREAM = "bedrock-converse-stream"  // AWS Bedrock API
    const val OLLAMA = "ollama"  // Ollama local API

    // All supported API types
    val ALL_APIS = listOf(
        OPENAI_COMPLETIONS,
        OPENAI_RESPONSES,
        OPENAI_CODEX_RESPONSES,
        ANTHROPIC_MESSAGES,
        GOOGLE_GENERATIVE_AI,
        GITHUB_COPILOT,
        BEDROCK_CONVERSE_STREAM,
        OLLAMA
    )

    /**
     * Check if API type is valid
     */
    fun isValidApi(api: String): Boolean {
        return api in ALL_APIS
    }

    /**
     * Check if it's an OpenAI compatible API
     */
    fun isOpenAICompat(api: String): Boolean {
        return api in listOf(
            OPENAI_COMPLETIONS,
            OPENAI_RESPONSES,
            OPENAI_CODEX_RESPONSES,
            OLLAMA,  // Ollama provides OpenAI compatible endpoint
            GITHUB_COPILOT  // GitHub Copilot uses OpenAI format
        )
    }
}

/**
 * Authentication mode constants
 */
object AuthMode {
    const val API_KEY = "api-key"
    const val OAUTH = "oauth"
    const val TOKEN = "token"
    const val AWS_SDK = "aws-sdk"
}
