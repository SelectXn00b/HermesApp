package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: legacy model/provider compatibility bridge.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.config.ProviderConfig
import com.xiaomo.androidforclaw.util.AppConstants

/**
 * Legacy Repository
 * Provides higher-level API wrapper
 * Automatically selects OpenAI or Anthropic format based on config
 *
 * **配置来源**: 从 /sdcard/.androidforclaw/openclaw.json 和 models.json 读取配置
 */
class LegacyRepository(
    context: Context,
    apiKey: String? = null,  // Optional, defaults to reading from config
    apiBase: String? = null,  // Optional, defaults to reading from config
    private val apiType: String? = null  // Optional, defaults to reading from config
) {
    companion object {
        private const val TAG = "LegacyRepository"
    }

    // Config loader
    private val configLoader = ConfigLoader(context)

    // Load OpenClaw config
    private val openClawConfig: OpenClawConfig by lazy {
        configLoader.loadOpenClawConfig()
    }

    // Find corresponding provider by defaultModel
    private fun getProviderForDefaultModel(): ProviderConfig? {
        val defaultModel = openClawConfig.agent.defaultModel
        val providerName = configLoader.findProviderByModelId(defaultModel)
        Log.d(TAG, "Default model: $defaultModel, Provider: $providerName")
        return providerName?.let { configLoader.getProviderConfig(it) }
    }

    // Read API config from config (prioritize constructor parameters, otherwise read from config file)
    private val actualApiKey: String by lazy {
        apiKey ?: run {
            // Read apiKey from provider corresponding to defaultModel
            val provider = getProviderForDefaultModel() ?: configLoader.getProviderConfig("openrouter")
            provider?.apiKey ?: AppConstants.OPENROUTER_API_KEY
        }
    }

    private val actualApiBase: String by lazy {
        apiBase ?: run {
            // Read baseUrl from provider corresponding to defaultModel
            val provider = getProviderForDefaultModel() ?: configLoader.getProviderConfig("openrouter")
            provider?.baseUrl ?: "https://openrouter.ai/api/v1"
        }
    }

    private val actualApiType: String by lazy {
        apiType ?: run {
            // Read api type from provider corresponding to defaultModel
            val provider = getProviderForDefaultModel() ?: configLoader.getProviderConfig("openrouter")
            provider?.api ?: "openai-completions"
        }
    }

    // Select Provider based on API type
    private val openAIProvider by lazy {
        val provider = getProviderForDefaultModel() ?: configLoader.getProviderConfig("anthropic")
        Log.d(TAG, "Creating OpenAI Provider:")
        Log.d(TAG, "  Provider name: ${provider?.let { configLoader.findProviderByModelId(openClawConfig.agent.defaultModel) }}")
        Log.d(TAG, "  authHeader from config: ${provider?.authHeader}")
        Log.d(TAG, "  Final authHeader value: ${provider?.authHeader ?: true}")
        LegacyProviderOpenAI(
            apiKey = actualApiKey,
            apiBase = actualApiBase,
            providerId = "legacy",
            authHeader = provider?.authHeader ?: true,
            customHeaders = provider?.headers
        )
    }

    private val anthropicProvider by lazy {
        LegacyProviderAnthropic(
            apiKey = actualApiKey,
            apiBase = actualApiBase
        )
    }

    /**
     * 带工具调用的聊天
     *
     * @param messages Message list
     * @param tools Tool definition list
     * @param model Model ID (optional, defaults to agent.defaultModel from openclaw.json)
     * @param reasoningEnabled Whether Extended Thinking is enabled (optional, defaults to thinking.enabled from openclaw.json)
     */
    suspend fun chatWithTools(
        messages: List<LegacyMessage>,
        tools: List<ToolDefinition>,
        model: String? = null,
        reasoningEnabled: Boolean? = null
    ): LegacyResponse {
        // Read default values from config
        val actualModel = model ?: openClawConfig.agent.defaultModel
        val actualReasoningEnabled = reasoningEnabled ?: openClawConfig.thinking.enabled

        Log.d(TAG, "chatWithTools: ${messages.size} messages, ${tools.size} tools")
        Log.d(TAG, "Model: $actualModel, API Type: $actualApiType")
        Log.d(TAG, "Reasoning enabled: $actualReasoningEnabled, Budget: ${openClawConfig.thinking.budgetTokens}")

        return when (actualApiType) {
            "anthropic-messages" -> {
                anthropicProvider.chat(
                    messages = messages,
                    tools = tools,
                    model = actualModel,
                    thinkingEnabled = actualReasoningEnabled,
                    thinkingBudget = openClawConfig.thinking.budgetTokens
                )
            }
            "openai-completions" -> {
                openAIProvider.chat(
                    messages = messages,
                    tools = tools,
                    model = actualModel
                )
            }
            else -> {
                Log.w(TAG, "Unknown API type: $actualApiType, falling back to Anthropic")
                anthropicProvider.chat(
                    messages = messages,
                    tools = tools,
                    model = actualModel,
                    thinkingEnabled = actualReasoningEnabled,
                    thinkingBudget = openClawConfig.thinking.budgetTokens
                )
            }
        }
    }

    /**
     * 简单聊天（无工具）
     *
     * @param userMessage User message
     * @param systemPrompt System prompt (optional)
     * @param reasoningEnabled Extended Thinking 是否启用（可选，默认从 openclaw.json 读取）
     */
    suspend fun simpleChat(
        userMessage: String,
        systemPrompt: String? = null,
        reasoningEnabled: Boolean? = null
    ): String {
        val actualReasoningEnabled = reasoningEnabled ?: openClawConfig.thinking.enabled

        Log.d(TAG, "simpleChat: $userMessage")
        Log.d(TAG, "Reasoning enabled: $actualReasoningEnabled")

        return when (actualApiType) {
            "anthropic-messages" -> {
                anthropicProvider.simpleChat(
                    userMessage = userMessage,
                    systemPrompt = systemPrompt
                )
            }
            "openai-completions" -> {
                openAIProvider.simpleChat(
                    userMessage = userMessage,
                    systemPrompt = systemPrompt
                )
            }
            else -> {
                anthropicProvider.simpleChat(
                    userMessage = userMessage,
                    systemPrompt = systemPrompt
                )
            }
        }
    }

    /**
     * 继续对话
     *
     * @param messages Existing message list
     * @param newUserMessage New user message
     * @param tools Tool definition list（可选）
     */
    suspend fun continueChat(
        messages: List<LegacyMessage>,
        newUserMessage: String,
        tools: List<ToolDefinition>? = null
    ): LegacyResponse {
        val updatedMessages = messages.toMutableList()
        updatedMessages.add(LegacyMessage("user", newUserMessage))

        return when (actualApiType) {
            "anthropic-messages" -> {
                anthropicProvider.chat(
                    messages = updatedMessages,
                    tools = tools
                )
            }
            "openai-completions" -> {
                openAIProvider.chat(
                    messages = updatedMessages,
                    tools = tools
                )
            }
            else -> {
                anthropicProvider.chat(
                    messages = updatedMessages,
                    tools = tools
                )
            }
        }
    }

    /**
     * 获取当前配置信息（用于调试）
     */
    fun getConfigInfo(): String {
        return """
            |Configuration:
            |  API Key: ${actualApiKey.take(10)}***
            |  API Base: $actualApiBase
            |  API Type: $actualApiType
            |  Default Model: ${openClawConfig.agent.defaultModel}
            |  Max Iterations: ${openClawConfig.agent.maxIterations}
            |  Thinking Enabled: ${openClawConfig.thinking.enabled}
            |  Thinking Budget: ${openClawConfig.thinking.budgetTokens}
        """.trimMargin()
    }
}
