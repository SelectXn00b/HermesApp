package com.ai.assistance.operit.api.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.api.chat.enhance.ConversationMarkupManager
import com.ai.assistance.operit.api.chat.enhance.ConversationRoundManager
import com.ai.assistance.operit.api.chat.enhance.ConversationService
import com.ai.assistance.operit.api.chat.enhance.FileBindingService
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.appendUserTurnIfMissing
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.core.chat.hooks.toRoleContentPairs
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.stream.withEventChannel
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterCardToolAccessResolver
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.hermes.OperitChatCompletionServer
import com.ai.assistance.operit.hermes.OperitToolDispatcher
import com.ai.assistance.operit.hermes.extractToolNames
import com.ai.assistance.operit.hermes.toolPromptsToOpenAiSchemas
import com.xiaomo.hermes.hermes.AgentEvent
import com.xiaomo.hermes.hermes.AgentEventSink
import com.xiaomo.hermes.hermes.HermesAgentLoop
import org.json.JSONObject

/**
 * Enhanced AI service that provides advanced conversational capabilities by integrating various
 * components like tool execution, conversation management, user preferences, and problem library.
 */
class EnhancedAIService private constructor(private val context: Context) {
    companion object {
        private const val TAG = "EnhancedAIService"

        @Volatile private var INSTANCE: EnhancedAIService? = null

        private val CHAT_INSTANCES = ConcurrentHashMap<String, EnhancedAIService>()

        private val FOREGROUND_REF_COUNT = AtomicInteger(0)

        /**
         * 获取EnhancedAIService实例
         * @param context 应用上下文
         * @return EnhancedAIService实.
         */
        fun getInstance(context: Context): EnhancedAIService {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: EnhancedAIService(context.applicationContext).also {
                                    INSTANCE = it
                                }
                    }
        }

        fun getChatInstance(context: Context, chatId: String): EnhancedAIService {
            val appContext = context.applicationContext
            return CHAT_INSTANCES[chatId]
                ?: synchronized(CHAT_INSTANCES) {
                    CHAT_INSTANCES[chatId]
                        ?: EnhancedAIService(appContext).also { CHAT_INSTANCES[chatId] = it }
                }
        }

        fun releaseChatInstance(chatId: String) {
            val instance = CHAT_INSTANCES.remove(chatId) ?: return
            runCatching {
                instance.cancelConversation()
            }.onFailure { e ->
                AppLogger.e(TAG, "释放chat实例资源失败: chatId=$chatId", e)
            }
        }

        /**
         * 获取指定功能类型的 AIService 实例（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return AIService 实例
         */
        suspend fun getAIServiceForFunction(
                context: Context,
                functionType: FunctionType
        ): AIService {
            return getInstance(context).multiServiceManager.getServiceForFunction(functionType)
        }

        suspend fun getModelConfigForFunction(
            context: Context,
            functionType: FunctionType
        ): ModelConfigData {
            return getInstance(context).multiServiceManager.getModelConfigForFunction(functionType)
        }

        /**
         * 刷新指定功能类型的 AIService 实例（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         */
        suspend fun refreshServiceForFunction(context: Context, functionType: FunctionType) {
            val allInstances = buildList {
                add(getInstance(context))
                addAll(CHAT_INSTANCES.values)
            }.distinct()
            allInstances.forEach { it.multiServiceManager.refreshServiceForFunction(functionType) }
        }

        /**
         * 刷新所有 AIService 实例（非实例化方式）
         * @param context 应用上下文
         */
        suspend fun refreshAllServices(context: Context) {
            val allInstances = buildList {
                add(getInstance(context))
                addAll(CHAT_INSTANCES.values)
            }.distinct()
            allInstances.forEach { it.multiServiceManager.refreshAllServices() }
        }

        /**
         * 获取指定功能类型的当前输入token计数（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return 输入token计数
         */
        suspend fun getCurrentInputTokenCountForFunction(
                context: Context,
                functionType: FunctionType
        ): Int {
            return getInstance(context)
                    .multiServiceManager
                    .getServiceForFunction(functionType)
                    .inputTokenCount
        }

        /**
         * 获取指定功能类型的当前输出token计数（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return 输出token计数
         */
        suspend fun getCurrentOutputTokenCountForFunction(
                context: Context,
                functionType: FunctionType
        ): Int {
            return getInstance(context)
                    .multiServiceManager
                    .getServiceForFunction(functionType)
                    .outputTokenCount
        }

        /**
         * 重置指定功能类型或所有功能类型的token计数器（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型，如果为null则重置所有功能类型
         */
        suspend fun resetTokenCountersForFunction(
                context: Context,
                functionType: FunctionType? = null
        ) {
            val allInstances = buildList {
                add(getInstance(context))
                addAll(CHAT_INSTANCES.values)
            }.distinct()
            allInstances.forEach {
                if (functionType == null) {
                    it.multiServiceManager.resetAllTokenCounters()
                } else {
                    it.multiServiceManager.resetTokenCountersForFunction(functionType)
                }
            }
        }

        fun resetTokenCounters(context: Context) {
            val appContext = context.applicationContext
            val allInstances = buildList {
                add(getInstance(appContext))
                addAll(CHAT_INSTANCES.values)
            }.distinct()

            allInstances.forEach { instance ->
                instance.initScope.launch {
                    runCatching {
                        instance.multiServiceManager.resetAllTokenCounters()
                    }.onFailure { e ->
                        AppLogger.e(TAG, "重置token计数器失败", e)
                    }
                }
            }
        }

        /**
         * 处理文件绑定操作（非实例化方式）
         * @param context 应用上下文
         * @param originalContent 原始文件内容
         * @param aiGeneratedCode AI生成的代码（包含"//existing code"标记）
         * @return 混合后的文件内容
         */
        suspend fun applyFileBinding(
                context: Context,
                originalContent: String,
                aiGeneratedCode: String,
                onProgress: ((Float, String) -> Unit)? = null
        ): Pair<String, String> {
            // 获取EnhancedAIService实例
            val instance = getInstance(context)

            // 委托给FileBindingService处理
            return instance.fileBindingService.processFileBinding(
                    originalContent,
                    aiGeneratedCode,
                    onProgress
            )
        }

        suspend fun applyFileBindingOperations(
            context: Context,
            originalContent: String,
            operations: List<FileBindingService.StructuredEditOperation>,
            onProgress: ((Float, String) -> Unit)? = null
        ): Pair<String, String> {
            val instance = getInstance(context)
            return instance.fileBindingService.processFileBindingOperations(
                originalContent = originalContent,
                operations = operations,
                onProgress = onProgress
            )
        }

        /**
         * 自动生成工具包描述（非实例化方式）
         * @param context 应用上下文
         * @param pluginName 工具包名称
         * @param toolDescriptions 工具描述列表
         * @return 生成的工具包描述
         */
        suspend fun generatePackageDescription(
            context: Context,
            pluginName: String,
            toolDescriptions: List<String>
        ): String {
            return getInstance(context).generatePackageDescription(pluginName, toolDescriptions)
        }
    }

    // MultiServiceManager 管理不同功能的 AIService 实例
    private val multiServiceManager = MultiServiceManager(context)

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initMutex = Mutex()
    @Volatile private var isServiceManagerInitialized = false

    // 添加ConversationService实例
    private val conversationService = ConversationService(context, CustomEmojiRepository.getInstance(context))

    // 添加FileBindingService实例
    private val fileBindingService = FileBindingService(context)

    // Tool handler for executing tools
    private val toolHandler = AIToolHandler.getInstance(context)

    private suspend fun ensureInitialized() {
        if (isServiceManagerInitialized) return
        initMutex.withLock {
            if (isServiceManagerInitialized) return
            withContext(Dispatchers.IO) {
                multiServiceManager.initialize()
            }
            isServiceManagerInitialized = true
        }
    }

    // State flows for UI updates
    private val _inputProcessingState =
            MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()

    /**
     * 设置当前的输入处理状态
     * @param newState 新的状态
     */
    fun setInputProcessingState(newState: com.ai.assistance.operit.data.model.InputProcessingState) {
        _inputProcessingState.value = newState
    }

    // Per-request token counts
    private val _perRequestTokenCounts = MutableStateFlow<Pair<Int, Int>?>(null)
    val perRequestTokenCounts: StateFlow<Pair<Int, Int>?> = _perRequestTokenCounts.asStateFlow()

    // Stable request window estimate for the next model hop.
    private val _requestWindowEstimate = MutableStateFlow<Int?>(null)
    val requestWindowEstimateFlow: StateFlow<Int?> = _requestWindowEstimate.asStateFlow()

    // Conversation management
    // private val streamBuffer = StringBuilder() // Moved to MessageExecutionContext
    // private val roundManager = ConversationRoundManager() // Moved to MessageExecutionContext
    // private val isConversationActive = AtomicBoolean(false) // Moved to MessageExecutionContext

    // Api Preferences for settings
    private val apiPreferences = ApiPreferences.getInstance(context)
    private val characterCardToolAccessResolver = CharacterCardToolAccessResolver.getInstance(context)

    // Execution context for a single sendMessage call to achieve concurrency
    private data class MessageExecutionContext(
        val executionId: Int,
        val streamBuffer: StringBuilder = StringBuilder(),
        val roundManager: ConversationRoundManager = ConversationRoundManager(),
        val isConversationActive: AtomicBoolean = AtomicBoolean(true),
        val conversationHistory: MutableList<PromptTurn>,
        val eventChannel: MutableSharedStream<TextStreamEvent>,
    )

    private val activeExecutionContexts = ConcurrentHashMap<Int, MessageExecutionContext>()
    private val nextExecutionContextId = AtomicInteger(0)

    private fun registerExecutionContext(context: MessageExecutionContext) {
        activeExecutionContexts[context.executionId] = context
    }

    private fun unregisterExecutionContext(context: MessageExecutionContext) {
        activeExecutionContexts.remove(context.executionId, context)
    }

    private fun invalidateExecutionContext(context: MessageExecutionContext, reason: String) {
        if (context.isConversationActive.compareAndSet(true, false)) {
            AppLogger.d(TAG, "执行上下文已失效: id=${context.executionId}, reason=$reason")
        }
    }

    private fun invalidateAllExecutionContexts(reason: String) {
        activeExecutionContexts.values.forEach { context ->
            invalidateExecutionContext(context, reason)
        }
    }

    private fun isExecutionContextActive(context: MessageExecutionContext): Boolean {
        return context.isConversationActive.get() &&
            activeExecutionContexts[context.executionId] === context
    }

    private suspend fun startAssistantResponseRound(context: MessageExecutionContext) {
        context.roundManager.startNewRound()
        context.streamBuffer.clear()
    }

    // Coroutine management
    private val toolProcessingScope = CoroutineScope(Dispatchers.IO)
    private val toolExecutionJobs = ConcurrentHashMap<String, Job>()
    // private val conversationHistory = mutableListOf<Pair<String, String>>() // Moved to MessageExecutionContext
    // private val conversationMutex = Mutex() // Moved to MessageExecutionContext

    private var accumulatedInputTokenCount = 0
    private var accumulatedOutputTokenCount = 0
    private var accumulatedCachedInputTokenCount = 0

    // Callbacks
    private var currentResponseCallback: ((content: String, thinking: String?) -> Unit)? = null
    private var currentCompleteCallback: (() -> Unit)? = null

    // Package manager for handling tool packages
    private val packageManager = PackageManager.getInstance(context, toolHandler)

    // 存储最后的回复内容，用于通知
    private var lastReplyContent: String? = null

    init {
        com.ai.assistance.operit.api.chat.library.MemoryLibrary.initialize(context)
        initScope.launch {
            runCatching {
                ensureInitialized()
            }.onFailure { e ->
                AppLogger.e(TAG, "MultiServiceManager初始化失败", e)
            }
        }
        initScope.launch {
            runCatching {
                toolHandler.registerDefaultTools()
            }.onFailure { e ->
                AppLogger.e(TAG, "注册默认工具失败", e)
            }
        }
    }

    /**
     * 获取指定功能类型的 AIService 实例
     * @param functionType 功能类型
     * @return AIService 实例
     */
    suspend fun getAIServiceForFunction(functionType: FunctionType): AIService {
        ensureInitialized()
        return getAIServiceForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = null,
            chatModelIndexOverride = null
        )
    }

    suspend fun getAIServiceForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?
    ): AIService {
        ensureInitialized()
        val overrideConfigId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
        return if (functionType == FunctionType.CHAT && overrideConfigId != null) {
            multiServiceManager.getServiceForConfig(
                configId = overrideConfigId,
                modelIndex = (chatModelIndexOverride ?: 0).coerceAtLeast(0)
            )
        } else {
            multiServiceManager.getServiceForFunction(functionType)
        }
    }

    /**
     * 获取指定功能类型的provider和model信息
     * @param functionType 功能类型
     * @return Pair<provider, modelName>，例如 Pair("DEEPSEEK", "deepseek-chat")
     */
    suspend fun getProviderAndModelForFunction(functionType: FunctionType): Pair<String, String> {
        return getProviderAndModelForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = null,
            chatModelIndexOverride = null
        )
    }

    suspend fun getProviderAndModelForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?
    ): Pair<String, String> {
        val service = getAIServiceForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = chatModelConfigIdOverride,
            chatModelIndexOverride = chatModelIndexOverride
        )
        val providerModel = service.providerModel
        // providerModel格式为"PROVIDER:modelName"，使用第一个冒号分割
        val colonIndex = providerModel.indexOf(":")
        return if (colonIndex > 0) {
            val provider = providerModel.substring(0, colonIndex)
            val modelName = providerModel.substring(colonIndex + 1)
            Pair(provider, modelName)
        } else {
            // 如果没有冒号，整个字符串作为provider，modelName为空
            Pair(providerModel, "")
        }
    }

    suspend fun getModelConfigForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null
    ): ModelConfigData {
        ensureInitialized()
        val overrideConfigId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
        return if (functionType == FunctionType.CHAT && overrideConfigId != null) {
            multiServiceManager.getModelConfigForConfig(overrideConfigId)
        } else {
            multiServiceManager.getModelConfigForFunction(functionType)
        }
    }

    /**
     * 刷新指定功能类型的 AIService 实例 当配置发生更改时调用
     * @param functionType 功能类型
     */
    suspend fun refreshServiceForFunction(functionType: FunctionType) {
        ensureInitialized()
        multiServiceManager.refreshServiceForFunction(functionType)
    }

    /** 刷新所有 AIService 实例 当全局配置发生更改时调用 */
    suspend fun refreshAllServices() {
        ensureInitialized()
        multiServiceManager.refreshAllServices()
    }

    private suspend fun getModelParametersForFunction(
        functionType: FunctionType,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null
    ): List<com.ai.assistance.operit.data.model.ModelParameter<*>> {
        ensureInitialized()
        val overrideConfigId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
        return if (functionType == FunctionType.CHAT && overrideConfigId != null) {
            multiServiceManager.getModelParametersForConfig(overrideConfigId)
        } else {
            multiServiceManager.getModelParametersForFunction(functionType)
        }
    }

    private fun publishRequestWindowEstimate(windowSize: Int) {
        _requestWindowEstimate.value = windowSize
    }

    private suspend fun estimatePreparedRequestWindow(
        serviceForFunction: AIService,
        preparedHistory: List<PromptTurn>,
        availableTools: List<ToolPrompt>?,
        publishEstimate: Boolean
    ): Int {
        val windowSize =
            serviceForFunction.calculateInputTokens(
                chatHistory = preparedHistory,
                availableTools = availableTools
            )
        if (publishEstimate) {
            publishRequestWindowEstimate(windowSize)
        }
        return windowSize
    }

    private fun applyPromptFinalizeHooks(
        initialContext: PromptHookContext,
        dispatchHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchPromptFinalizeHooks
    ): PromptHookContext {
        return dispatchHooks(initialContext)
    }

    private fun bypassPromptHooks(context: PromptHookContext): PromptHookContext = context

    private fun applyFinalizedCurrentUserTurn(
        preparedHistory: List<PromptTurn>,
        originalCurrentMessage: String,
        finalizedCurrentMessage: String
    ): List<PromptTurn> {
        if (finalizedCurrentMessage.isBlank()) {
            return preparedHistory
        }

        val lastTurn = preparedHistory.lastOrNull()
        return when {
            lastTurn?.kind == PromptTurnKind.USER &&
                lastTurn.content == finalizedCurrentMessage -> {
                preparedHistory
            }
            lastTurn?.kind == PromptTurnKind.USER &&
                lastTurn.content == originalCurrentMessage -> {
                preparedHistory.dropLast(1) + lastTurn.copy(content = finalizedCurrentMessage)
            }
            else -> {
                preparedHistory.appendUserTurnIfMissing(finalizedCurrentMessage)
            }
        }
    }

    suspend fun estimateRequestWindowFromMemory(
        message: String,
        chatHistory: List<PromptTurn>,
        chatId: String? = null,
        workspacePath: String? = null,
        workspaceEnv: String? = null,
        functionType: FunctionType = FunctionType.CHAT,
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        enableThinking: Boolean = false,
        thinkingGuidance: Boolean = false,
        enableMemoryQuery: Boolean = true,
        customSystemPromptTemplate: String? = null,
        roleCardId: String? = null,
        enableGroupOrchestrationHint: Boolean = false,
        groupParticipantNamesText: String? = null,
        proxySenderName: String? = null,
        isSubTask: Boolean = false,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        stream: Boolean = true,
        publishEstimate: Boolean = true
    ): Int {
        val preparedHistory =
            prepareConversationHistory(
                chatHistory = chatHistory,
                processedInput = message,
                chatId = chatId,
                workspacePath = workspacePath,
                workspaceEnv = workspaceEnv,
                promptFunctionType = promptFunctionType,
                thinkingGuidance = thinkingGuidance,
                customSystemPromptTemplate = customSystemPromptTemplate,
                enableMemoryQuery = enableMemoryQuery,
                roleCardId = roleCardId,
                enableGroupOrchestrationHint = enableGroupOrchestrationHint,
                groupParticipantNamesText = groupParticipantNamesText,
                proxySenderName = proxySenderName,
                isSubTask = isSubTask,
                functionType = functionType,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride,
                dispatchHistoryHooks = PromptHookRegistry::dispatchPromptEstimateHistoryHooks,
                dispatchSystemPromptComposeHooks = ::bypassPromptHooks,
                dispatchToolPromptComposeHooks = ::bypassPromptHooks
            )

        val modelParameters =
            getModelParametersForFunction(
                functionType = functionType,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride
            )
        val serviceForFunction =
            getAIServiceForFunction(
                functionType = functionType,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride
            )
        val availableTools =
            getAvailableToolsForFunction(
                functionType = functionType,
                roleCardId = roleCardId,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride
            )

        var finalProcessedInput = message
        var finalPreparedHistory = preparedHistory
        val beforeFinalizeContext =
            applyPromptFinalizeHooks(
                PromptHookContext(
                    stage = "before_finalize_prompt",
                    chatId = chatId,
                    functionType = functionType.name,
                    promptFunctionType = promptFunctionType.name,
                    rawInput = message,
                    processedInput = finalProcessedInput,
                    preparedHistory = finalPreparedHistory,
                    modelParameters = serializePromptHookModelParameters(modelParameters),
                    availableTools = serializePromptHookToolPrompts(availableTools),
                    metadata =
                        mapOf(
                            "workspacePath" to workspacePath,
                            "workspaceEnv" to workspaceEnv,
                            "enableThinking" to enableThinking,
                            "stream" to stream,
                            "isSubTask" to isSubTask
                        )
                ),
                dispatchHooks = PromptHookRegistry::dispatchPromptEstimateFinalizeHooks
            )
        finalProcessedInput = beforeFinalizeContext.processedInput ?: finalProcessedInput
        finalPreparedHistory = beforeFinalizeContext.preparedHistory
        val beforeSendContext =
            applyPromptFinalizeHooks(
                beforeFinalizeContext.copy(
                    stage = "before_send_to_model",
                    processedInput = finalProcessedInput,
                    preparedHistory = finalPreparedHistory
                ),
                dispatchHooks = PromptHookRegistry::dispatchPromptEstimateFinalizeHooks
            )
        finalProcessedInput = beforeSendContext.processedInput ?: finalProcessedInput
        finalPreparedHistory = beforeSendContext.preparedHistory
        if (!ChatUtils.isGeminiProviderModel(serviceForFunction.providerModel)) {
            finalProcessedInput = ChatUtils.stripGeminiThoughtSignatureMeta(finalProcessedInput)
            finalPreparedHistory = ChatUtils.stripGeminiThoughtSignatureMetaTurns(finalPreparedHistory)
        }

        val requestHistory =
            applyFinalizedCurrentUserTurn(
                preparedHistory = finalPreparedHistory,
                originalCurrentMessage = message,
                finalizedCurrentMessage = finalProcessedInput
            )

        return estimatePreparedRequestWindow(
            serviceForFunction = serviceForFunction,
            preparedHistory = requestHistory,
            availableTools = availableTools,
            publishEstimate = publishEstimate
        )
    }

    /** Send a message to the AI service */
    suspend fun sendMessage(
        message: String,
        chatId: String? = null,
        chatHistory: List<PromptTurn> = emptyList(),
        workspacePath: String? = null,
        workspaceEnv: String? = null,
        functionType: FunctionType = FunctionType.CHAT,
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        enableThinking: Boolean = false,
        thinkingGuidance: Boolean = false,
        enableMemoryQuery: Boolean = true,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit = {},
        onTokenLimitExceeded: (suspend () -> Unit)? = null,
        customSystemPromptTemplate: String? = null,
        isSubTask: Boolean = false,
        characterName: String? = null,
        avatarUri: String? = null,
        roleCardId: String? = null,
        enableGroupOrchestrationHint: Boolean = false,
        groupParticipantNamesText: String? = null,
        proxySenderName: String? = null,
        onToolInvocation: (suspend (String) -> Unit)? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        stream: Boolean = true
    ): Stream<String> {
        AppLogger.d(
                TAG,
                "sendMessage调用开始: 功能类型=$functionType, 提示词类型=$promptFunctionType, 思考引导=$thinkingGuidance"
        )
        accumulatedInputTokenCount = 0
        accumulatedOutputTokenCount = 0
        accumulatedCachedInputTokenCount = 0

        val eventChannel = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
        val wrappedStream = stream {
            val execContext =
                MessageExecutionContext(
                    executionId = nextExecutionContextId.incrementAndGet(),
                    conversationHistory = chatHistory.toMutableList(),
                    eventChannel = eventChannel
                )
            registerExecutionContext(execContext)
            var hadFatalError = false
            try {
                // 确保所有操作都在IO线程上执行
                withContext(Dispatchers.IO) {
                    // 仅当会话首次启动时开启服务，并更新前台通知为“运行中”
                    if (!isSubTask) {
                        startAiService(characterName, avatarUri)
                    }

                    // Update state to show we're processing
                    if (!isSubTask) {
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value = InputProcessingState.Processing(context.getString(R.string.enhanced_processing_message))
                        }
                    }

                    val startTime = messageTimingNow()

                    // Prepare conversation history with system prompt
                    val preparedHistory =
                            prepareConversationHistory(
                                    execContext.conversationHistory, // 始终使用内部历史记录
                                    message,
                                    chatId,
                                    workspacePath,
                                    workspaceEnv,
                                    promptFunctionType,
                                    thinkingGuidance,
                                    customSystemPromptTemplate,
                                    enableMemoryQuery,
                                    roleCardId,
                                    enableGroupOrchestrationHint,
                                    groupParticipantNamesText,
                                    proxySenderName,
                                    isSubTask,
                                    functionType,
                                    chatModelConfigIdOverride,
                                    chatModelIndexOverride
                            )
                    val tAfterPrepareHistory = messageTimingNow()
                    AppLogger.d(TAG, "sendMessage本地耗时: prepareConversationHistory=${tAfterPrepareHistory - startTime}ms")
                    
                    // 关键修复：用准备好的历史记录（包含了系统提示）去同步更新内部的 conversationHistory 状态
                    execContext.conversationHistory.clear()
                    execContext.conversationHistory.addAll(preparedHistory)

                    // Update UI state to connecting
                    if (!isSubTask) {
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value = InputProcessingState.Connecting(context.getString(R.string.enhanced_connecting_service))
                        }
                    }

                    // Get all model parameters from preferences (with enabled state)
                    val modelParameters = getModelParametersForFunction(
                        functionType = functionType,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride
                    )
                    val tAfterModelParams = messageTimingNow()
                    AppLogger.d(TAG, "sendMessage本地耗时: getModelParametersForFunction=${tAfterModelParams - tAfterPrepareHistory}ms")

                    // 获取对应功能类型的AIService实例
                    val serviceForFunction = getAIServiceForFunction(
                        functionType = functionType,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride
                    )
                    val tAfterGetService = messageTimingNow()
                    AppLogger.d(TAG, "sendMessage本地耗时: getAIServiceForFunction=${tAfterGetService - tAfterModelParams}ms")

                    // 清空之前的单次请求token计数
                    _perRequestTokenCounts.value = null

                    // 获取工具列表（如果启用Tool Call）
                    val availableTools = getAvailableToolsForFunction(
                        functionType = functionType,
                        roleCardId = roleCardId,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride
                    )
                    val tAfterGetTools = messageTimingNow()
                    AppLogger.d(TAG, "sendMessage本地耗时: getAvailableToolsForFunction=${tAfterGetTools - tAfterGetService}ms")

                    var finalProcessedInput = message
                    var finalPreparedHistory = preparedHistory
                    val beforeFinalizeContext =
                        applyPromptFinalizeHooks(
                            PromptHookContext(
                                stage = "before_finalize_prompt",
                                chatId = chatId,
                                functionType = functionType.name,
                                promptFunctionType = promptFunctionType.name,
                                rawInput = message,
                                processedInput = finalProcessedInput,
                                preparedHistory = finalPreparedHistory,
                                modelParameters = serializePromptHookModelParameters(modelParameters),
                                availableTools = serializePromptHookToolPrompts(availableTools),
                                metadata =
                                    mapOf(
                                        "workspacePath" to workspacePath,
                                        "workspaceEnv" to workspaceEnv,
                                        "enableThinking" to enableThinking,
                                        "stream" to stream,
                                        "isSubTask" to isSubTask
                                    )
                            )
                        )
                    finalProcessedInput = beforeFinalizeContext.processedInput ?: finalProcessedInput
                    finalPreparedHistory = beforeFinalizeContext.preparedHistory
                    val beforeSendContext =
                        applyPromptFinalizeHooks(
                            beforeFinalizeContext.copy(
                                stage = "before_send_to_model",
                                processedInput = finalProcessedInput,
                                preparedHistory = finalPreparedHistory
                            )
                        )
                    finalProcessedInput = beforeSendContext.processedInput ?: finalProcessedInput
                    finalPreparedHistory = beforeSendContext.preparedHistory
                    if (!ChatUtils.isGeminiProviderModel(serviceForFunction.providerModel)) {
                        finalProcessedInput = ChatUtils.stripGeminiThoughtSignatureMeta(finalProcessedInput)
                        finalPreparedHistory = ChatUtils.stripGeminiThoughtSignatureMetaTurns(finalPreparedHistory)
                    }
                    val requestHistory =
                        applyFinalizedCurrentUserTurn(
                            preparedHistory = finalPreparedHistory,
                            originalCurrentMessage = message,
                            finalizedCurrentMessage = finalProcessedInput
                        )
                    execContext.conversationHistory.clear()
                    execContext.conversationHistory.addAll(requestHistory)
                    estimatePreparedRequestWindow(
                        serviceForFunction = serviceForFunction,
                        preparedHistory = requestHistory,
                        availableTools = availableTools,
                        publishEstimate = true
                    )

                    AppLogger.d(TAG, "sendMessage请求前准备耗时: ${tAfterGetTools - startTime}ms, 流式输出: $stream")
                    val requestStartTime = messageTimingNow()
                    val collector = this@stream
                    runAgentLoopViaHermes(
                        collector = collector,
                        execContext = execContext,
                        serviceForFunction = serviceForFunction,
                        requestHistory = requestHistory,
                        availableTools = availableTools,
                        modelParameters = modelParameters,
                        functionType = functionType,
                        enableThinking = enableThinking,
                        enableMemoryQuery = enableMemoryQuery,
                        streamFromProvider = stream,
                        onNonFatalError = onNonFatalError,
                        onTokenLimitExceeded = onTokenLimitExceeded,
                        maxTokens = maxTokens,
                        tokenUsageThreshold = tokenUsageThreshold,
                        isSubTask = isSubTask,
                        characterName = characterName,
                        avatarUri = avatarUri,
                        roleCardId = roleCardId,
                        chatId = chatId,
                        onToolInvocation = onToolInvocation,
                        requestStartTime = requestStartTime
                    )
                }
            } catch (e: CancellationException) {
                invalidateExecutionContext(execContext, "sendMessage.collect.cancelled")
                AppLogger.d(TAG, "sendMessage流被取消")
                throw e
            } catch (e: Exception) {
                // 用户取消导致的 Socket closed 是预期行为，不应作为错误处理
                if (e.message?.contains("Socket closed", ignoreCase = true) == true) {
                    if (isExecutionContextActive(execContext)) {
                        AppLogger.d(TAG, "Stream was cancelled by the user (Socket closed).")
                    } else {
                        AppLogger.d(TAG, "Stream closed after execution context was invalidated.")
                    }
                } else {
                    hadFatalError = true
                    // Handle any exceptions
                    AppLogger.e(TAG, "发送消息时发生错误: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value =
                                InputProcessingState.Error(message = context.getString(R.string.enhanced_error_with_message, e.message ?: ""))
                    }
                }

                // 发生无法处理的错误时，也应停止服务，但用户取消除外
                if (e.message?.contains("Socket closed", ignoreCase = true) != true) {
                    if (!isSubTask) stopAiService()
                }
            } finally {
                unregisterExecutionContext(execContext)
            }
        }
        return wrappedStream.withEventChannel(eventChannel)
    }

    /**
     * 核心 agent loop — 把一次 sendMessage 请求委派给 HermesAgentLoop。
     * 保留 Operit 外壳语义：token 累积、onToolInvocation、onNonFatalError、
     * onTokenLimitExceeded、handleTaskCompletion / handleWaitForUserNeed 终态分发。
     */
    private suspend fun runAgentLoopViaHermes(
        collector: StreamCollector<String>,
        execContext: MessageExecutionContext,
        serviceForFunction: AIService,
        requestHistory: List<PromptTurn>,
        availableTools: List<ToolPrompt>?,
        modelParameters: List<ModelParameter<*>>,
        functionType: FunctionType,
        enableThinking: Boolean,
        enableMemoryQuery: Boolean,
        streamFromProvider: Boolean,
        onNonFatalError: suspend (error: String) -> Unit,
        onTokenLimitExceeded: (suspend () -> Unit)?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        isSubTask: Boolean,
        characterName: String?,
        avatarUri: String?,
        roleCardId: String?,
        chatId: String?,
        onToolInvocation: (suspend (String) -> Unit)?,
        requestStartTime: Long
    ) {
        startAssistantResponseRound(execContext)
        var loggedFirstChunk = false

        val server = OperitChatCompletionServer(
            context = this@EnhancedAIService.context,
            service = serviceForFunction,
            modelParameters = modelParameters,
            enableThinking = enableThinking,
            availableTools = availableTools,
            streamFromProvider = streamFromProvider,
            onTokensUpdated = { input, _, output ->
                _perRequestTokenCounts.value = Pair(input, output)
            },
            onTurnComplete = { input, cachedInput, output ->
                accumulatedInputTokenCount += input
                accumulatedOutputTokenCount += output
                accumulatedCachedInputTokenCount += cachedInput
                apiPreferences.updateTokensForProviderModel(
                    serviceForFunction.providerModel, input, output, cachedInput
                )
                apiPreferences.incrementRequestCountForProviderModel(
                    serviceForFunction.providerModel
                )
                AppLogger.d(
                    TAG,
                    "Token updated for $functionType. Input=$input, Output=$output, CachedInput=$cachedInput. Accumulated=$accumulatedInputTokenCount,$accumulatedOutputTokenCount,$accumulatedCachedInputTokenCount"
                )
            },
            onNonFatalError = onNonFatalError
        )
        val dispatcher = OperitToolDispatcher(this@EnhancedAIService.context)

        val openAiMessages = requestHistory.toOpenAiMessages().toMutableList()

        suspend fun emitChunk(piece: String) {
            if (piece.isEmpty()) return
            if (!loggedFirstChunk) {
                loggedFirstChunk = true
                if (!isSubTask) {
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value = InputProcessingState.Receiving(
                            this@EnhancedAIService.context.getString(
                                R.string.enhanced_receiving_response
                            )
                        )
                    }
                }
                logMessageTiming(
                    stage = "enhanced.sendMessage.firstResponseChunk",
                    startTimeMs = requestStartTime,
                    details = "functionType=$functionType, stream=$streamFromProvider"
                )
            }
            execContext.streamBuffer.append(piece)
            execContext.roundManager.updateContent(execContext.streamBuffer.toString())
            collector.emit(piece)
        }

        val sink: AgentEventSink = { event ->
            when (event) {
                is AgentEvent.Thinking -> {
                    emitChunk("<think>${escapeHermesXml(event.text)}</think>")
                }
                is AgentEvent.AssistantDelta -> {
                    emitChunk(event.text)
                }
                is AgentEvent.ToolCallStart -> {
                    onToolInvocation?.invoke(event.name)
                    if (!isSubTask) {
                        withContext(Dispatchers.Main) {
                            _inputProcessingState.value =
                                InputProcessingState.ExecutingTool(event.name)
                        }
                    }
                    emitChunk(renderHermesToolCallXml(event.name, event.argsJson))
                }
                is AgentEvent.ToolCallEnd -> {
                    val synthetic = ToolResult(
                        toolName = event.name,
                        success = event.error == null,
                        result = StringResultData(event.resultJson),
                        error = event.error
                    )
                    emitChunk(ConversationMarkupManager.formatToolResultForMessage(synthetic))
                }
                is AgentEvent.Error -> {
                    onNonFatalError(event.message)
                }
                is AgentEvent.Final -> {
                    // Sync final history into execContext.conversationHistory
                    execContext.conversationHistory.clear()
                    execContext.conversationHistory.addAll(
                        openAiMessages.toPromptTurnsForHistory()
                    )
                    val aggregatedContent = execContext.roundManager.getCurrentRoundContent()
                    if (ConversationMarkupManager.containsTaskCompletion(aggregatedContent)) {
                        handleTaskCompletion(
                            context = execContext,
                            content = aggregatedContent,
                            enableMemoryQuery = enableMemoryQuery,
                            onNonFatalError = onNonFatalError,
                            isSubTask = isSubTask,
                            chatId = chatId,
                            characterName = characterName,
                            avatarUri = avatarUri
                        )
                    } else {
                        val waitContent = ConversationMarkupManager.createWaitForUserNeedContent(
                            execContext.roundManager.getDisplayContent()
                        )
                        handleWaitForUserNeed(
                            context = execContext,
                            content = waitContent,
                            isSubTask = isSubTask,
                            chatId = chatId,
                            characterName = characterName,
                            avatarUri = avatarUri
                        )
                    }
                }
            }
        }

        val openAiToolSchemas = availableTools?.let(::toolPromptsToOpenAiSchemas) ?: emptyList()
        val configuredMaxTurns = HermesGatewayPreferences.getInstance(context.applicationContext)
            .agentMaxTurnsFlow.first()
        val loop = HermesAgentLoop(
            server = server,
            toolSchemas = openAiToolSchemas,
            validToolNames = extractToolNames(openAiToolSchemas),
            toolDispatcher = dispatcher,
            maxTurns = configuredMaxTurns,
            taskId = chatId ?: "chat_${execContext.executionId}",
            eventSink = sink,
            beforeNextTurn = beforeNextTurnLambda@{ turn, _ ->
                if (!isExecutionContextActive(execContext)) return@beforeNextTurnLambda false
                if (turn > 0 && maxTokens > 0) {
                    _perRequestTokenCounts.value = null
                    execContext.conversationHistory.clear()
                    execContext.conversationHistory.addAll(
                        openAiMessages.toPromptTurnsForHistory()
                    )
                    val currentTokens = estimatePreparedRequestWindow(
                        serviceForFunction = serviceForFunction,
                        preparedHistory = execContext.conversationHistory,
                        availableTools = availableTools,
                        publishEstimate = true
                    )
                    val usageRatio = currentTokens.toDouble() / maxTokens.toDouble()
                    if (usageRatio >= tokenUsageThreshold) {
                        AppLogger.w(
                            TAG,
                            "Token usage ($usageRatio) exceeds threshold ($tokenUsageThreshold) at turn $turn. Aborting loop."
                        )
                        onTokenLimitExceeded?.invoke()
                        execContext.isConversationActive.set(false)
                        if (!isSubTask) stopAiService(characterName, avatarUri)
                        return@beforeNextTurnLambda false
                    }
                }
                true
            }
        )

        loop.run(openAiMessages)

        logMessageTiming(
            stage = "enhanced.sendMessage.streamComplete",
            startTimeMs = requestStartTime,
            details = "functionType=$functionType, stream=$streamFromProvider"
        )
    }

    /** Convert Operit PromptTurn history into OpenAI chat-completion messages. */
    private fun List<PromptTurn>.toOpenAiMessages(): List<Map<String, Any?>> =
        map { turn ->
            val role = when (turn.kind) {
                PromptTurnKind.SYSTEM -> "system"
                PromptTurnKind.USER -> "user"
                PromptTurnKind.ASSISTANT -> "assistant"
                PromptTurnKind.TOOL_CALL -> "assistant"
                PromptTurnKind.TOOL_RESULT -> "tool"
                PromptTurnKind.SUMMARY -> "system"
            }
            val base = mutableMapOf<String, Any?>(
                "role" to role,
                "content" to turn.content
            )
            if (role == "tool" && turn.toolName != null) {
                // No original id available; synthesize a stable placeholder so
                // downstream code doesn't crash on missing tool_call_id.
                base["tool_call_id"] = "history_${turn.toolName}"
                base["name"] = turn.toolName
            }
            base.toMap()
        }

    /** Convert OpenAI-format message list back to PromptTurn list for history sync. */
    private fun List<Map<String, Any?>>.toPromptTurnsForHistory(): List<PromptTurn> =
        map { msg ->
            val role = (msg["role"] as? String) ?: "user"
            val rawContent = when (val c = msg["content"]) {
                is String -> c
                null -> ""
                else -> c.toString()
            }
            when (role) {
                "system" -> PromptTurn(kind = PromptTurnKind.SYSTEM, content = rawContent)
                "user" -> PromptTurn(kind = PromptTurnKind.USER, content = rawContent)
                "tool" -> PromptTurn(
                    kind = PromptTurnKind.TOOL_RESULT,
                    content = rawContent,
                    toolName = msg["name"] as? String
                )
                "assistant" -> {
                    val hasToolCalls = (msg["tool_calls"] as? List<*>)?.isNotEmpty() == true
                    PromptTurn(
                        kind = if (hasToolCalls) PromptTurnKind.TOOL_CALL else PromptTurnKind.ASSISTANT,
                        content = rawContent
                    )
                }
                else -> PromptTurn(kind = PromptTurnKind.USER, content = rawContent)
            }
        }

    private fun renderHermesToolCallXml(toolName: String, argsJson: String): String {
        val params = try {
            val json = JSONObject(argsJson)
            buildString {
                json.keys().forEach { key ->
                    val value = json.opt(key)?.toString().orEmpty()
                    append("<param name=\"").append(escapeHermesXml(key)).append("\">")
                    append(escapeHermesXml(value))
                    append("</param>")
                }
            }
        } catch (_: Exception) {
            "<param name=\"raw\">${escapeHermesXml(argsJson)}</param>"
        }
        return "<tool name=\"${escapeHermesXml(toolName)}\">$params</tool>"
    }

    private fun escapeHermesXml(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }


    /** Handle task completion logic - simplified version without callbacks */
    private suspend fun handleTaskCompletion(
        context: MessageExecutionContext,
        content: String,
        enableMemoryQuery: Boolean,
        onNonFatalError: suspend (error: String) -> Unit,
        isSubTask: Boolean,
        chatId: String? = null,
        characterName: String? = null,
        avatarUri: String? = null
    ) {
        // Mark conversation as complete
        context.isConversationActive.set(false)

        // 清除内容池
        // roundManager.clearContent()
        
        // 保存最后的回复内容用于通知
        lastReplyContent = context.roundManager.getDisplayContent()

        // Ensure input processing state is updated to completed
        if (!isSubTask) {
        withContext(Dispatchers.Main) {
            _inputProcessingState.value = InputProcessingState.Completed
            }
        }

        if (enableMemoryQuery) {
            // 保存会话记忆到记忆库
            com.ai.assistance.operit.api.chat.library.MemoryLibrary.saveMemoryAsync(
                this@EnhancedAIService.context,
                toolHandler,
                context.conversationHistory.toRoleContentPairs(),
                content,
                multiServiceManager.getServiceForFunction(FunctionType.MEMORY),
                onError = { e ->
                    AppLogger.e(TAG, "自动保存会话记忆失败", e)
                    onNonFatalError(
                        this@EnhancedAIService.context.getString(
                            R.string.chat_auto_update_memory_failed,
                            e.message ?: ""
                        )
                    )
                }
            )
        }

        if (!isSubTask) {
        notifyReplyCompleted(chatId, characterName, avatarUri)
        stopAiService(characterName, avatarUri)
        }
    }

    /** Handle wait for user need logic - simplified version without callbacks */
    private suspend fun handleWaitForUserNeed(
        context: MessageExecutionContext,
        content: String,
        isSubTask: Boolean,
        chatId: String? = null,
        characterName: String? = null,
        avatarUri: String? = null
    ) {
        // Mark conversation as complete
        context.isConversationActive.set(false)

        // 清除内容池
        // roundManager.clearContent()
        
        // 保存最后的回复内容用于通知
        lastReplyContent = context.roundManager.getDisplayContent()

        // Ensure input processing state is updated to completed
        if (!isSubTask) {
        withContext(Dispatchers.Main) {
            _inputProcessingState.value = InputProcessingState.Completed
            }
        }

        AppLogger.d(TAG, "Wait for user need - skipping problem library analysis")
        if (!isSubTask) {
        notifyReplyCompleted(chatId, characterName, avatarUri)
        stopAiService(characterName, avatarUri)
        }
    }


    /**
     * Get the current input token count from the last API call
     * @return The number of input tokens used in the most recent request
     */
    fun getCurrentInputTokenCount(): Int {
        return accumulatedInputTokenCount
    }

    /**
     * Get the current output token count from the last API call
     * @return The number of output tokens generated in the most recent response
     */
    fun getCurrentOutputTokenCount(): Int {
        return accumulatedOutputTokenCount
    }

    /**
     * Get the current cached input token count accumulated across the current turn
     * @return The number of cached input tokens used in the current turn
     */
    fun getCurrentCachedInputTokenCount(): Int {
        return accumulatedCachedInputTokenCount
    }

    /** Reset token counters to zero Use this when starting a new conversation */
    fun resetTokenCounters() {
        Companion.resetTokenCounters(context)
    }

    /**
     * 重置指定功能类型或所有功能类型的token计数器
     * @param functionType 功能类型，如果为null则重置所有功能类型
     */
    suspend fun resetTokenCountersForFunction(functionType: FunctionType? = null) {
        Companion.resetTokenCountersForFunction(context, functionType)
    }

    /**
     * 生成对话总结
     * @param messages 要总结的消息列表
     * @return 生成的总结文本
     */
    suspend fun generateSummary(messages: List<Pair<String, String>>): String {
        return generateSummary(messages, null)
    }

    /**
     * 生成对话总结，并且包含上一次的总结内容
     * @param messages 要总结的消息列表
     * @param previousSummary 上一次的总结内容，可以为null
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            previousSummary: String?
    ): String {
        return generateSummaryFromPromptTurns(messages.toPromptTurns(), previousSummary)
    }

    suspend fun generateSummaryFromPromptTurns(
            messages: List<PromptTurn>,
            previousSummary: String?
    ): String {
        // 调用ConversationService中的方法
        return conversationService.generateSummaryFromPromptTurns(messages, previousSummary, multiServiceManager)
    }

    /**
     * 获取指定功能类型的当前输入token计数
     * @param functionType 功能类型
     * @return 输入token计数
     */
    suspend fun getCurrentInputTokenCountForFunction(functionType: FunctionType): Int {
        return Companion.getCurrentInputTokenCountForFunction(context, functionType)
    }

    /**
     * 获取指定功能类型的当前输出token计数
     * @param functionType 功能类型
     * @return 输出token计数
     */
    suspend fun getCurrentOutputTokenCountForFunction(functionType: FunctionType): Int {
        return Companion.getCurrentOutputTokenCountForFunction(context, functionType)
    }

    /** Prepare the conversation history with system prompt */
    private suspend fun prepareConversationHistory(
            chatHistory: List<PromptTurn>,
            processedInput: String,
            chatId: String?,
            workspacePath: String?,
            workspaceEnv: String?,
            promptFunctionType: PromptFunctionType,
            thinkingGuidance: Boolean,
            customSystemPromptTemplate: String? = null,
            enableMemoryQuery: Boolean,
            roleCardId: String?,
            enableGroupOrchestrationHint: Boolean,
            groupParticipantNamesText: String? = null,
            proxySenderName: String? = null,
            isSubTask: Boolean = false,
            functionType: FunctionType = FunctionType.CHAT,
            chatModelConfigIdOverride: String? = null,
            chatModelIndexOverride: Int? = null,
            dispatchHistoryHooks: (PromptHookContext) -> PromptHookContext =
                PromptHookRegistry::dispatchPromptHistoryHooks,
            dispatchSystemPromptComposeHooks: (PromptHookContext) -> PromptHookContext =
                PromptHookRegistry::dispatchSystemPromptComposeHooks,
            dispatchToolPromptComposeHooks: (PromptHookContext) -> PromptHookContext =
                PromptHookRegistry::dispatchToolPromptComposeHooks
    ): List<PromptTurn> {
        // Check if backend image recognition service is configured (for intent-based vision)
        // For subtasks, always disable backend image recognition (only support OCR)
        val hasImageRecognition = if (isSubTask) false else multiServiceManager.hasImageRecognitionConfigured()
        val hasAudioRecognition = if (isSubTask) false else multiServiceManager.hasAudioRecognitionConfigured()
        val hasVideoRecognition = if (isSubTask) false else multiServiceManager.hasVideoRecognitionConfigured()

        // 获取当前功能类型（通常是聊天模型）的模型配置，用于判断聊天模型是否自带识图能力
        val config = getModelConfigForFunction(
            functionType = functionType,
            chatModelConfigIdOverride = chatModelConfigIdOverride,
            chatModelIndexOverride = chatModelIndexOverride
        )
        val useToolCallApi = config.enableToolCall
        val chatModelHasDirectImage = config.enableDirectImageProcessing
        val chatModelHasDirectAudio = config.enableDirectAudioProcessing
        val chatModelHasDirectVideo = config.enableDirectVideoProcessing

        return conversationService.prepareConversationHistory(
                chatHistory,
                processedInput,
                chatId,
                workspacePath,
                workspaceEnv,
                packageManager,
                promptFunctionType,
                thinkingGuidance,
                customSystemPromptTemplate,
                enableMemoryQuery,
                roleCardId,
                enableGroupOrchestrationHint,
                groupParticipantNamesText,
                proxySenderName,
                hasImageRecognition,
                hasAudioRecognition,
                hasVideoRecognition,
                chatModelHasDirectAudio,
                chatModelHasDirectVideo,
                useToolCallApi,
                chatModelHasDirectImage,
                dispatchHistoryHooks,
                dispatchSystemPromptComposeHooks,
                dispatchToolPromptComposeHooks
        )
    }

    private fun serializePromptHookModelParameters(
        modelParameters: List<com.ai.assistance.operit.data.model.ModelParameter<*>>
    ): List<Map<String, Any?>> {
        return modelParameters.map { parameter ->
            mapOf(
                "id" to parameter.id,
                "name" to parameter.name,
                "apiName" to parameter.apiName,
                "description" to parameter.description,
                "defaultValue" to parameter.defaultValue,
                "currentValue" to parameter.currentValue,
                "isEnabled" to parameter.isEnabled,
                "valueType" to parameter.valueType.name,
                "minValue" to parameter.minValue,
                "maxValue" to parameter.maxValue,
                "category" to parameter.category.name,
                "isCustom" to parameter.isCustom
            )
        }
    }

    private fun serializePromptHookToolPrompts(
        toolPrompts: List<ToolPrompt>?
    ): List<Map<String, Any?>> {
        return toolPrompts.orEmpty().map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parameters,
                "details" to tool.details,
                "notes" to tool.notes,
                "parametersStructured" to
                    tool.parametersStructured.orEmpty().map { parameter ->
                        mapOf(
                            "name" to parameter.name,
                            "type" to parameter.type,
                            "description" to parameter.description,
                            "required" to parameter.required,
                            "default" to parameter.default
                        )
                    }
            )
        }
    }

    /** Cancel the current conversation */
    fun cancelConversation() {
        invalidateAllExecutionContexts("cancelConversation")

        // Set conversation inactive
        // isConversationActive.set(false) // This is now per-context, can't set a global one

        // Cancel all underlying AIService streaming instances
        initScope.launch {
            runCatching {
                multiServiceManager.cancelAllStreaming()
            }.onFailure { e ->
                AppLogger.e(TAG, "取消AIService流式输出失败", e)
            }
        }

        // Cancel all tool executions
        cancelAllToolExecutions()

        // Clean up current conversation content
        // roundManager.clearContent() // This is now per-context, can't clear a global one
        AppLogger.d(TAG, "Conversation canceled")

        // Reset input processing state
        _inputProcessingState.value = InputProcessingState.Idle

        // Reset per-request token counts
        _perRequestTokenCounts.value = null
        accumulatedInputTokenCount = 0
        accumulatedOutputTokenCount = 0
        accumulatedCachedInputTokenCount = 0

        // Clear callback references
        currentResponseCallback = null
        currentCompleteCallback = null

        // 停止AI服务并关闭屏幕常亮
        stopAiService()

        AppLogger.d(TAG, "Conversation cancellation complete")
    }

    /** Cancel all tool executions */
    private fun cancelAllToolExecutions() {
        toolProcessingScope.coroutineContext.cancelChildren()
    }

    /**
     * 获取可用工具列表（用于Tool Call API）
     * 如果模型配置启用了Tool Call，返回工具列表；否则返回null
     */
    private suspend fun getAvailableToolsForFunction(
        functionType: FunctionType,
        roleCardId: String? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null
    ): List<ToolPrompt>? {
        return try {
            // 先读取全局工具和记忆开关
            val enableTools = apiPreferences.enableToolsFlow.first()
            val enableMemoryQuery = apiPreferences.enableMemoryQueryFlow.first()
            val toolPromptVisibility = runCatching {
                apiPreferences.toolPromptVisibilityFlow.first()
            }.getOrElse { emptyMap() }
            val roleCardToolAccess = characterCardToolAccessResolver.resolve(
                roleCardId = roleCardId,
                packageManager = packageManager,
                globalToolVisibility = toolPromptVisibility
            )

            // 如果同时关闭了普通工具和记忆相关工具，则完全不提供Tool Call工具
            if (!enableTools && !enableMemoryQuery) {
                AppLogger.d(TAG, "全局设置已禁用工具和记忆，本次调用不提供任何Tool Call工具")
                return null
            }

            // 获取对应功能类型的模型配置
            val config = getModelConfigForFunction(
                functionType = functionType,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride
            )
            
            // 检查是否启用Tool Call
            if (!config.enableToolCall) {
                return null
            }
            
            // 获取所有工具分类
            val isEnglish = LocaleUtils.getCurrentLanguage(context) == "en"

            // 后端识图服务是否可用（IMAGE_RECOGNITION 功能），用于 intent-based 视觉模型
            val hasBackendImageRecognition = multiServiceManager.hasImageRecognitionConfigured()

            val hasBackendAudioRecognition = multiServiceManager.hasAudioRecognitionConfigured()
            val hasBackendVideoRecognition = multiServiceManager.hasVideoRecognitionConfigured()

            val safBookmarkNames = runCatching {
                apiPreferences.safBookmarksFlow.first().map { it.name }
            }.getOrElse { emptyList() }

            // 当前功能模型（通常是聊天模型）是否支持直接看图
            val chatModelHasDirectImage = config.enableDirectImageProcessing

            val chatModelHasDirectAudio = config.enableDirectAudioProcessing
            val chatModelHasDirectVideo = config.enableDirectVideoProcessing

            val categories = if (isEnglish) {
                SystemToolPrompts.getAIAllCategoriesEn(
                    hasBackendImageRecognition = hasBackendImageRecognition,
                    chatModelHasDirectImage = chatModelHasDirectImage,
                    hasBackendAudioRecognition = hasBackendAudioRecognition,
                    hasBackendVideoRecognition = hasBackendVideoRecognition,
                    chatModelHasDirectAudio = chatModelHasDirectAudio,
                    chatModelHasDirectVideo = chatModelHasDirectVideo,
                    safBookmarkNames = safBookmarkNames
                )
            } else {
                SystemToolPrompts.getAIAllCategoriesCn(
                    hasBackendImageRecognition = hasBackendImageRecognition,
                    chatModelHasDirectImage = chatModelHasDirectImage,
                    hasBackendAudioRecognition = hasBackendAudioRecognition,
                    hasBackendVideoRecognition = hasBackendVideoRecognition,
                    chatModelHasDirectAudio = chatModelHasDirectAudio,
                    chatModelHasDirectVideo = chatModelHasDirectVideo,
                    safBookmarkNames = safBookmarkNames
                )
            }

            // 按类别拆分记忆工具和非记忆工具，以与 SystemPromptConfig 中的语义保持一致
            val memoryCategoryName = context.getString(R.string.enhanced_memory_tools_category)

            val memoryTools = categories
                .firstOrNull { it.categoryName == memoryCategoryName }
                ?.tools
                ?: emptyList()

            val nonMemoryTools = categories
                .filter { it.categoryName != memoryCategoryName }
                .flatMap { it.tools }

            // 根据开关组合最终可用工具：
            // - enableTools && enableMemoryQuery      -> 所有工具
            // - enableTools && !enableMemoryQuery     -> 仅非记忆工具
            // - !enableTools && enableMemoryQuery     -> 仅记忆工具
            val selectedTools = mutableListOf<ToolPrompt>()
            if (enableTools) {
                selectedTools.addAll(nonMemoryTools)
            }
            if (enableMemoryQuery) {
                selectedTools.addAll(memoryTools)
            }

            selectedTools.retainAll { tool ->
                roleCardToolAccess.isBuiltinToolAllowed(tool.name)
            }

            if (config.enableToolCall) {
                selectedTools.add(
                    ToolPrompt(
                        name = "package_proxy",
                        description = "Proxy tool for package tools activated by use_package.",
                        parametersStructured = listOf(
                            ToolParameterSchema(
                                name = "tool_name",
                                type = "string",
                                description = "Target tool name from an activated package (for example: packageName:toolName)",
                                required = true
                            ),
                            ToolParameterSchema(
                                name = "params",
                                type = "object",
                                description = "JSON object of parameters to forward to the target tool",
                                required = true
                            )
                        )
                    )
                )
            }

            if (selectedTools.isEmpty()) {
                AppLogger.d(TAG, "根据当前工具/记忆开关，未选择任何Tool Call工具")
                return null
            }

            AppLogger.d(
                TAG,
                "Tool Call已启用，提供 ${selectedTools.size} 个工具 (enableTools=$enableTools, enableMemoryQuery=$enableMemoryQuery, visibleToolOverrides=${toolPromptVisibility.size}, roleCardCustomTools=${roleCardToolAccess.customEnabled})"
            )
            selectedTools
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取工具列表失败", e)
            null
        }
    }

    // --- Service Lifecycle Management ---

    /** 启动或更新前台服务为“AI 正在运行”状态，以保持应用活跃 */
    private fun startAiService(characterName: String? = null, avatarUri: String? = null) {
        val refCount = FOREGROUND_REF_COUNT.incrementAndGet()
        val appInForeground = ActivityLifecycleManager.getCurrentActivity() != null
        val alwaysListeningEnabled = runCatching {
            runBlocking { WakeWordPreferences(context).alwaysListeningEnabledFlow.first() }
        }.getOrDefault(false)
        val externalHttpEnabled = runCatching {
            runBlocking { ExternalHttpApiPreferences.getInstance(context).enabledFlow.first() }
        }.getOrDefault(false)
        if (!appInForeground &&
            !AIForegroundService.isRunning.get() &&
            !alwaysListeningEnabled &&
            !externalHttpEnabled
        ) {
            AppLogger.d(TAG, "应用不在前台，跳过启动 AIForegroundService")
            return
        }
        try {
            val updateIntent = Intent(context, AIForegroundService::class.java).apply {
                putExtra(AIForegroundService.EXTRA_STATE, AIForegroundService.STATE_RUNNING)
                if (characterName != null) {
                    putExtra(AIForegroundService.EXTRA_CHARACTER_NAME, characterName)
                }
                if (avatarUri != null) {
                    putExtra(AIForegroundService.EXTRA_AVATAR_URI, avatarUri)
                }
            }
            context.startService(updateIntent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "更新AI前台服务为运行中状态失败: ${e.message}", e)
        }

        if (refCount == 1) {
            ActivityLifecycleManager.checkAndApplyKeepScreenOn(true)
        }
    }

    private fun notifyReplyCompleted(
        chatId: String?,
        characterName: String? = null,
        avatarUri: String? = null
    ) {
        AIForegroundService.notifyReplyCompleted(
            context = context,
            chatId = chatId,
            characterName = characterName,
            rawReplyContent = lastReplyContent,
            avatarUri = avatarUri
        )
    }

    /** 将前台服务更新为“空闲/已完成”状态，但不真正停止服务 */
    private fun stopAiService(characterName: String? = null, avatarUri: String? = null) {
        val remaining = run {
            var remainingValue = -1
            while (true) {
                val current = FOREGROUND_REF_COUNT.get()
                if (current <= 0) {
                    remainingValue = -1
                    break
                }
                val next = current - 1
                if (FOREGROUND_REF_COUNT.compareAndSet(current, next)) {
                    remainingValue = next
                    break
                }
            }
            remainingValue
        }
        if (remaining < 0) return
        if (remaining > 0) return
         if (AIForegroundService.isRunning.get()) {
             AppLogger.d(TAG, "更新AI前台服务为闲置状态...")

            try {
                val stopIntent = Intent(context, AIForegroundService::class.java).apply {
                    putExtra(AIForegroundService.EXTRA_CHARACTER_NAME, characterName)
                    putExtra(AIForegroundService.EXTRA_AVATAR_URI, avatarUri)
                    putExtra(AIForegroundService.EXTRA_STATE, AIForegroundService.STATE_IDLE)
                }

                AppLogger.d(TAG, "传递闲置状态 - 角色: $characterName, 头像: $avatarUri")

                // 仅发送更新，不再真正停止前台服务
                context.startService(stopIntent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新AI前台服务为闲置状态失败: ${e.message}", e)
            }
        } else {
            AppLogger.d(TAG, "AI前台服务未在运行，无需更新闲置状态。")
        }

        // 使用管理器来恢复屏幕常亮设置
        ActivityLifecycleManager.checkAndApplyKeepScreenOn(false)
    }

    /**
     * 处理文件绑定操作（实例方法）
     * @param originalContent 原始文件内容
     * @param aiGeneratedCode AI生成的代码（包含"//existing code"标记）
     * @return 混合后的文件内容
     */
    suspend fun applyFileBinding(
            originalContent: String,
            aiGeneratedCode: String
    ): Pair<String, String> {
        return fileBindingService.processFileBinding(
                originalContent,
                aiGeneratedCode
        )
    }

    /**
     * 翻译文本功能
     * @param text 要翻译的文本
     * @return 翻译后的文本
     */
    suspend fun translateText(text: String): String {
        return conversationService.translateText(text, multiServiceManager)
    }

    /**
     * 自动生成工具包描述
     * @param pluginName 工具包名称
     * @param toolDescriptions 工具描述列表
     * @return 生成的工具包描述
     */
    suspend fun generatePackageDescription(
        pluginName: String,
        toolDescriptions: List<String>
    ): String {
        return conversationService.generatePackageDescription(pluginName, toolDescriptions, multiServiceManager)
    }


    /**
     * Manually saves the current conversation to the problem library.
     * @param conversationHistory The history of the conversation to save.
     * @param lastContent The content of the last message in the conversation.
     */
    fun saveConversationToMemoryAsync(
        conversationHistory: List<Pair<String, String>>,
        lastContent: String,
        onSuccess: (suspend () -> Unit)? = null,
        onError: (suspend (Exception) -> Unit)? = null
    ) {
        AppLogger.d(TAG, "手动触发记忆更新...")
        toolProcessingScope.launch {
            try {
                val memoryService = multiServiceManager.getServiceForFunction(FunctionType.MEMORY)
                com.ai.assistance.operit.api.chat.library.MemoryLibrary.saveMemoryAsync(
                    context = context,
                    toolHandler = toolHandler,
                    conversationHistory = conversationHistory,
                    content = lastContent,
                    aiService = memoryService,
                    onSuccess = {
                        AppLogger.d(TAG, "手动记忆更新成功")
                        onSuccess?.invoke()
                    },
                    onError = { e ->
                        AppLogger.e(TAG, "手动记忆更新失败", e)
                        onError?.invoke(e)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "手动记忆更新初始化失败", e)
                onError?.invoke(e)
            }
        }
    }

    /**
     * 使用识图模型分析图片
     * @param imagePath 图片路径
     * @param userIntent 用户意图，例如"这个图片里面有什么"、"图片的题目公式是什么"等
     * @return AI分析结果
     */
    suspend fun analyzeImageWithIntent(imagePath: String, userIntent: String?): String {
        return conversationService.analyzeImageWithIntent(imagePath, userIntent, multiServiceManager)
    }

    suspend fun analyzeAudioWithIntent(audioPath: String, userIntent: String?): String {
        return conversationService.analyzeAudioWithIntent(audioPath, userIntent, multiServiceManager)
    }

    suspend fun analyzeVideoWithIntent(videoPath: String, userIntent: String?): String {
        return conversationService.analyzeVideoWithIntent(videoPath, userIntent, multiServiceManager)
    }
}
