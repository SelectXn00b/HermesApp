package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.core.tools.AgentStatusResultData
import com.ai.assistance.operit.core.tools.ChatCreationResultData
import com.ai.assistance.operit.core.tools.ChatFindResultData
import com.ai.assistance.operit.core.tools.ChatListResultData
import com.ai.assistance.operit.core.tools.ChatMessagesResultData
import com.ai.assistance.operit.core.tools.CharacterCardListResultData
import com.ai.assistance.operit.core.tools.ChatServiceStartResultData
import com.ai.assistance.operit.core.tools.ChatSwitchResultData
import com.ai.assistance.operit.core.tools.ChatTitleUpdateResultData
import com.ai.assistance.operit.core.tools.ChatDeleteResultData
import com.ai.assistance.operit.core.tools.MessageSendResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.services.ChatServiceCore
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingMode
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

data class MessageSendStreamSession(
    val chatId: String,
    val message: String,
    val responseStream: SharedStream<String>,
    private val currentStateProvider: () -> InputProcessingState,
    private val cancelAction: () -> Unit
) {
    fun currentState(): InputProcessingState = currentStateProvider()

    fun cancel() {
        cancelAction()
    }
}

sealed class MessageSendStreamStartResult {
    data class Started(val session: MessageSendStreamSession) : MessageSendStreamStartResult()

    data class Failed(val result: ToolResult) : MessageSendStreamStartResult()
}

/**
 * 对话管理工具
 * 通过绑定 FloatingChatService 来管理对话，实现创建、切换、列出对话和发送消息等功能
 */
class StandardChatManagerTool(private val context: Context) {

    companion object {
        private const val TAG = "StandardChatManagerTool"
        private const val SERVICE_CONNECTION_TIMEOUT = 15000L // 15秒超时
        private const val RESPONSE_STREAM_ACQUIRE_TIMEOUT = 15000L
        private const val AI_RESPONSE_TIMEOUT = 300000L
    }

    private fun simplifyXmlBlocksForHistory(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
            .trim()
    }

    private fun isMatchTitle(title: String, query: String, matchMode: String): Boolean {
        if (query.isBlank()) return true
        return when (matchMode) {
            "exact" -> title == query
            "regex" -> {
                try {
                    Regex(query).containsMatchIn(title)
                } catch (_: Exception) {
                    false
                }
            }
            else -> title.contains(query)
        }
    }

    private fun toSortableNumber(value: String): Long {
        return runCatching { value.toLong() }
            .getOrElse { 0L }
    }

    private fun toEpochMillis(dateTime: java.time.LocalDateTime): Long {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun buildChatInfo(
        chat: ChatHistory,
        messageCounts: Map<String, Int>,
        currentChatId: String?
    ): ChatListResultData.ChatInfo {
        return ChatListResultData.ChatInfo(
            id = chat.id,
            title = chat.title,
            messageCount = messageCounts[chat.id] ?: 0,
            createdAt = chat.createdAt.toString(),
            updatedAt = chat.updatedAt.toString(),
            isCurrent = currentChatId != null && chat.id == currentChatId,
            inputTokens = chat.inputTokens,
            outputTokens = chat.outputTokens,
            characterCardName = chat.characterCardName
        )
    }

    suspend fun getChatMessages(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val rawOrder = tool.parameters.find { it.name == "order" }?.value?.trim()
            val order = rawOrder?.lowercase()?.takeIf { it == "asc" || it == "desc" }
            if (rawOrder != null && rawOrder.isNotBlank() && order == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: order must be asc/desc"
                )
            }

            val rawLimit = tool.parameters.find { it.name == "limit" }?.value?.trim()
            val parsedLimit = rawLimit?.takeIf { it.isNotBlank() }?.toIntOrNull()
            if (rawLimit != null && rawLimit.isNotBlank() && parsedLimit == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid parameter: limit must be an integer"
                )
            }

            val effectiveOrder = order ?: "desc"
            val effectiveLimit = (parsedLimit ?: 20).coerceIn(1, 200)

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val title = chatHistoryManager.getChatTitle(chatId)
            if (title == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Chat does not exist: $chatId"
                )
            }

            val messages = chatHistoryManager.loadChatMessages(
                chatId = chatId,
                order = effectiveOrder,
                limit = effectiveLimit
            )

            val filteredMessages = messages.filterNot { msg -> msg.sender == "summary" }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatMessagesResultData(
                    chatId = chatId,
                    order = effectiveOrder,
                    limit = effectiveLimit,
                    messages = filteredMessages.map { msg ->
                        ChatMessagesResultData.ChatMessageInfo(
                            sender = msg.sender,
                            content = simplifyXmlBlocksForHistory(msg.content),
                            timestamp = msg.timestamp,
                            roleName = msg.roleName,
                            provider = msg.provider,
                            modelName = msg.modelName
                        )
                    }
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get chat messages", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting chat messages: ${e.message}"
            )
        }
    }

    /**
     * 查询对话输入状态
     */
    suspend fun agentStatus(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = AgentStatusResultData(chatId = "", state = "unknown"),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val title = chatHistoryManager.getChatTitle(chatId)
            if (title == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = AgentStatusResultData(chatId = chatId, state = "unknown"),
                    error = "Chat does not exist: $chatId"
                )
            }

            val connected = ensureServiceConnected()
            val chatService = chatCore
            if (!connected || chatService == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = AgentStatusResultData(chatId = chatId, state = "unknown"),
                    error = "Chat service not connected"
                )
            }

            val state = chatService.inputProcessingStateByChatId.value[chatId] ?: InputProcessingState.Idle
            var stateKey = "idle"
            var message: String? = null
            var isIdle = false
            var isProcessing = false
            when (state) {
                is InputProcessingState.Idle -> {
                    stateKey = "idle"
                    isIdle = true
                }
                is InputProcessingState.Completed -> {
                    stateKey = "completed"
                    isIdle = true
                }
                is InputProcessingState.Processing -> {
                    stateKey = "processing"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.Connecting -> {
                    stateKey = "connecting"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.Receiving -> {
                    stateKey = "receiving"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.ExecutingTool -> {
                    stateKey = "executing_tool"
                    message = state.toolName
                    isProcessing = true
                }
                is InputProcessingState.ToolProgress -> {
                    stateKey = "tool_progress"
                    message = if (state.message.isNotBlank()) {
                        "${state.toolName}: ${state.message}"
                    } else {
                        "${state.toolName}: ${(state.progress * 100).toInt()}%"
                    }
                    isProcessing = true
                }
                is InputProcessingState.ProcessingToolResult -> {
                    stateKey = "processing_tool_result"
                    message = state.toolName
                    isProcessing = true
                }
                is InputProcessingState.Summarizing -> {
                    stateKey = "summarizing"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.ExecutingPlan -> {
                    stateKey = "executing_plan"
                    message = state.message
                    isProcessing = true
                }
                is InputProcessingState.Error -> {
                    stateKey = "error"
                    message = state.message
                }
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = AgentStatusResultData(
                    chatId = chatId,
                    state = stateKey,
                    message = message,
                    isIdle = isIdle,
                    isProcessing = isProcessing
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get agent status", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = AgentStatusResultData(chatId = "", state = "unknown"),
                error = "Error getting agent status: ${e.message}"
            )
        }
    }

    /**
     * 查找对话
     */
    suspend fun findChat(tool: AITool): ToolResult {
        return try {
            val query = tool.parameters.find { it.name == "query" }?.value?.trim().orEmpty()
            if (query.isBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatFindResultData(matchedCount = 0, chat = null),
                    error = "Invalid parameter: missing query"
                )
            }

            val matchRaw = tool.parameters.find { it.name == "match" }?.value?.trim()?.lowercase()
            val matchMode = when (matchRaw) {
                null, "", "contains" -> "contains"
                "exact", "regex" -> matchRaw
                else ->
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatFindResultData(matchedCount = 0, chat = null),
                        error = "Invalid parameter: match must be contains/exact/regex"
                    )
            }

            val rawIndex = tool.parameters.find { it.name == "index" }?.value?.trim()
            val index = rawIndex?.takeIf { it.isNotBlank() }?.toIntOrNull()
            if (rawIndex != null && rawIndex.isNotBlank() && index == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatFindResultData(matchedCount = 0, chat = null),
                    error = "Invalid parameter: index must be an integer"
                )
            }

            val targetIndex = index ?: 0
            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val chatHistories = chatHistoryManager.chatHistoriesFlow.first()
            val currentChatId = chatHistoryManager.currentChatIdFlow.first()
            val messageCounts = chatHistoryManager.getMessageCountsByChatId()

            val idMatches = chatHistories.filter { chat -> chat.id == query }
            val matched = if (idMatches.isNotEmpty()) {
                idMatches
            } else {
                chatHistories.filter { chat -> isMatchTitle(chat.title, query, matchMode) }
            }
            if (matched.isEmpty()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatFindResultData(matchedCount = 0, chat = null),
                    error = "Chat not found by query: $query"
                )
            }

            if (targetIndex !in matched.indices) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatFindResultData(matchedCount = matched.size, chat = null),
                    error = "Chat index out of range: index=$targetIndex, matched=${matched.size}"
                )
            }

            val chatInfo = buildChatInfo(matched[targetIndex], messageCounts, currentChatId)
            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatFindResultData(matchedCount = matched.size, chat = chatInfo)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to find chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatFindResultData(matchedCount = 0, chat = null),
                error = "Error finding chat: ${e.message}"
            )
        }
    }

    /**
     * 更新对话标题
     */
    suspend fun updateChatTitle(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatTitleUpdateResultData(chatId = "", title = ""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val titleRaw = tool.parameters.find { it.name == "title" }?.value
            val title = titleRaw?.trim().orEmpty()
            if (title.isBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatTitleUpdateResultData(chatId = chatId, title = ""),
                    error = "Invalid parameter: missing title"
                )
            }

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val existingTitle = chatHistoryManager.getChatTitle(chatId)
            if (existingTitle == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatTitleUpdateResultData(chatId = chatId, title = title),
                    error = "Chat does not exist: $chatId"
                )
            }

            chatHistoryManager.updateChatTitle(chatId, title)
            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatTitleUpdateResultData(chatId = chatId, title = title)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update chat title", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatTitleUpdateResultData(chatId = "", title = ""),
                error = "Error updating chat title: ${e.message}"
            )
        }
    }

    /**
     * 删除对话
     */
    suspend fun deleteChat(tool: AITool): ToolResult {
        return try {
            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatDeleteResultData(chatId = ""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val chat = chatHistoryManager.chatHistoriesFlow.first().find { it.id == chatId }
            if (chat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatDeleteResultData(chatId = chatId),
                    error = "Chat does not exist: $chatId"
                )
            }

            if (chat.locked) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatDeleteResultData(chatId = chatId),
                    error = "Chat is locked and cannot be deleted: $chatId"
                )
            }

            val deleted = chatHistoryManager.deleteChatHistory(chatId)
            if (!deleted) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatDeleteResultData(chatId = chatId),
                    error = "Failed to delete chat: $chatId"
                )
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatDeleteResultData(chatId = chatId)
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatDeleteResultData(chatId = ""),
                error = "Error deleting chat: ${e.message}"
            )
        }
    }

    private val appContext = context.applicationContext

    // Service 连接状态
    private var chatCore: ChatServiceCore? = null
    private var floatingService: FloatingChatService? = null
    private var isBound = false
    private var connectionDeferred = CompletableDeferred<Boolean>().apply { complete(false) }

    // Service 连接回调
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppLogger.d(TAG, "Service connected")
            val binder = service as? FloatingChatService.LocalBinder
            if (binder != null) {
                floatingService = binder.getService()
                chatCore = binder.getChatCore()
                isBound = true
                binder.setCloseCallback {
                    AppLogger.d(TAG, "Received close callback from FloatingChatService")
                    unbindService()
                }
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(true)
                }
                AppLogger.d(TAG, "ChatServiceCore obtained successfully")
            } else {
                AppLogger.e(TAG, "Failed to cast binder")
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(false)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLogger.d(TAG, "Service disconnected")
            chatCore = null
            floatingService = null
            isBound = false
            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(false)
            }
        }
    }

    /**
     * 确保服务已连接
     * @return 是否成功连接
     */
    private suspend fun ensureServiceConnected(startIntent: Intent? = null): Boolean {
        // 如果已经连接，直接返回
        if (isBound && chatCore != null) {
            if (startIntent != null) {
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(startIntent)
                    } else {
                        appContext.startService(startIntent)
                    }
                }
            }
            return true
        }

        if (startIntent == null && FloatingChatService.getInstance() == null) {
            AppLogger.w(TAG, "FloatingChatService not running; skip auto-start in ensureServiceConnected")
            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(false)
            }
            return false
        }

        val prefs = appContext.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service_disabled_due_to_crashes", false)) {
            AppLogger.w(TAG, "FloatingChatService is disabled due to frequent crashes")
            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(false)
            }
            return false
        }

        // 如果正在连接中，等待连接完成
        if (!connectionDeferred.isCompleted) {
            return try {
                withTimeout(SERVICE_CONNECTION_TIMEOUT) {
                    connectionDeferred.await()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Service connection timeout", e)
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(false)
                }
                false
            }
        }

        // 重新启动和绑定服务
        return try {
            // 重置 deferred
            connectionDeferred = CompletableDeferred()
            
            val intent = startIntent ?: Intent(appContext, FloatingChatService::class.java)

            val bound =
                withContext(Dispatchers.Main) {
                    if (startIntent != null) {
                        // 启动服务
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            appContext.startForegroundService(intent)
                        } else {
                            appContext.startService(intent)
                        }
                    }

                    // 绑定服务
                    appContext.bindService(
                        intent,
                        serviceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }
            
            if (!bound) {
                AppLogger.e(TAG, "Failed to bind service")
                connectionDeferred.complete(false)
                return false
            }

            // 等待连接完成
            withTimeout(SERVICE_CONNECTION_TIMEOUT) {
                connectionDeferred.await()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to ensure service connected", e)
            connectionDeferred.completeExceptionally(e)
            false
        }
    }

    /**
     * 解绑服务
     */
    fun unbindService() {
        if (isBound) {
            try {
                appContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error unbinding service", e)
            }
        }

        isBound = false
        chatCore = null
        floatingService = null
        connectionDeferred = CompletableDeferred<Boolean>().apply { complete(false) }
        AppLogger.d(TAG, "Service unbound")
    }

    /**
     * 启动对话服务
     */
    suspend fun startChatService(tool: AITool): ToolResult {
        return try {
            val initialModeParam = tool.parameters.find { it.name == "initial_mode" }?.value?.trim()
            val autoEnterVoiceChatParam =
                tool.parameters.find { it.name == "auto_enter_voice_chat" }?.value?.trim()
            val wakeLaunchedParam = tool.parameters.find { it.name == "wake_launched" }?.value?.trim()
            val timeoutMsParam = tool.parameters.find { it.name == "timeout_ms" }?.value?.trim()
            val keepIfExistsParam = tool.parameters.find { it.name == "keep_if_exists" }?.value?.trim()

            val initialMode =
                initialModeParam
                    ?.takeIf { it.isNotBlank() }
                    ?.let { raw ->
                        runCatching { FloatingMode.valueOf(raw.uppercase()) }.getOrNull()
                    }
            if (initialModeParam != null && initialModeParam.isNotBlank() && initialMode == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: initial_mode is invalid: $initialModeParam"
                )
            }

            fun parseBooleanOrNull(value: String?): Boolean? {
                return when (value?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }

            val autoEnterVoiceChat = parseBooleanOrNull(autoEnterVoiceChatParam)
            if (autoEnterVoiceChatParam != null && autoEnterVoiceChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: auto_enter_voice_chat must be true/false"
                )
            }

            val wakeLaunched = parseBooleanOrNull(wakeLaunchedParam)
            if (wakeLaunchedParam != null && wakeLaunched == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: wake_launched must be true/false"
                )
            }

            val timeoutMs = timeoutMsParam?.takeIf { it.isNotBlank() }?.toLongOrNull()
            if (timeoutMsParam != null && timeoutMsParam.isNotBlank() && timeoutMs == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: timeout_ms must be an integer (milliseconds)"
                )
            }

            val keepIfExists = parseBooleanOrNull(keepIfExistsParam)
            if (keepIfExistsParam != null && keepIfExists == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Invalid parameter: keep_if_exists must be true/false"
                )
            }

            val intent = Intent(appContext, FloatingChatService::class.java)
            if (initialMode != null) {
                intent.putExtra("INITIAL_MODE", initialMode.name)
            }
            if (autoEnterVoiceChat == true) {
                intent.putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
            }
            if (wakeLaunched != null) {
                intent.putExtra(FloatingChatService.EXTRA_WAKE_LAUNCHED, wakeLaunched)
            }
            if (timeoutMs != null) {
                intent.putExtra(FloatingChatService.EXTRA_AUTO_EXIT_AFTER_MS, timeoutMs)
            }
            if (keepIfExists == true) {
                intent.putExtra(FloatingChatService.EXTRA_KEEP_IF_EXISTS, true)
            }

            val connected = ensureServiceConnected(intent)
            
            if (connected) {
                try {
                    floatingService?.setFloatingWindowVisible(true)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to set floating window visible", e)
                }
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatServiceStartResultData(isConnected = true)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "Chat service failed to start or connection timed out"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start chat service", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatServiceStartResultData(isConnected = false),
                error = "Error starting chat service: ${e.message}"
            )
        }
    }

    suspend fun stopChatService(tool: AITool): ToolResult {
        return try {
            try {
                floatingService?.setFloatingWindowVisible(false)
            } catch (_: Exception) {
            }

            unbindService()

            val intent = Intent(appContext, FloatingChatService::class.java)
            val stopped = runCatching { appContext.stopService(intent) }.getOrDefault(false)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(if (stopped) "Chat service stopped" else "Requested to stop chat service")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop chat service", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error stopping chat service: ${e.message}"
            )
        }
    }

    /**
     * 创建新的对话
     */
    suspend fun createNewChat(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "Service not connected"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatCreationResultData(chatId = ""),
                error = "ChatServiceCore not initialized"
            )

            // 获取创建前的 chat list
            val previousChatIds = core.chatHistories.value.map { it.id }.toSet()

            val group = tool.parameters.find { it.name == "group" }?.value?.trim()
            val effectiveGroup = group?.takeIf { it.isNotBlank() }

            val rawSetAsCurrent = tool.parameters.find { it.name == "set_as_current_chat" }?.value?.trim()
            val setAsCurrentChat =
                when (rawSetAsCurrent?.lowercase()) {
                    null, "" -> true
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            if (setAsCurrentChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "Invalid parameter: set_as_current_chat must be true/false"
                )
            }

            val characterCardId =
                tool.parameters.find { it.name == "character_card_id" }?.value?.trim()
            if (!characterCardId.isNullOrBlank()) {
                val roleCardManager = CharacterCardManager.getInstance(appContext)
                val targetCard = roleCardManager.getCharacterCard(characterCardId)
                if (targetCard == null) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatCreationResultData(chatId = ""),
                        error = "Invalid parameter: character_card_id not found"
                    )
                }
            }
            
            // 创建新对话（不切换当前对话）
            core.createNewChat(
                group = effectiveGroup,
                setAsCurrentChat = setAsCurrentChat,
                characterCardId = characterCardId
            )

            val newChatId = try {
                withTimeout(5000L) {
                    var newId: String? = null
                    while (newId == null) {
                        val newChat = core.chatHistories.value.firstOrNull { it.id !in previousChatIds }
                        newId = newChat?.id
                        if (newId == null) {
                            delay(50)
                        }
                    }
                    newId
                }
            } catch (_: TimeoutCancellationException) {
                null
            }

            if (newChatId != null) {
                if (setAsCurrentChat) {
                    val switched = try {
                        withTimeout(5000L) {
                            while (core.currentChatId.value != newChatId) {
                                delay(50)
                            }
                            true
                        }
                    } catch (_: TimeoutCancellationException) {
                        false
                    }
                    if (!switched) {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = ChatCreationResultData(chatId = newChatId),
                            error = "Chat created but current chat switch did not complete in time"
                        )
                    }
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatCreationResultData(chatId = newChatId)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "Failed to create chat, unable to get new chat ID"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create new chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatCreationResultData(chatId = ""),
                error = "Error creating chat: ${e.message}"
            )
        }
    }

    /**
     * 列出所有对话
     */
    suspend fun listChats(tool: AITool): ToolResult {
        return try {
            val chatHistoryManager = ChatHistoryManager.getInstance(appContext)
            val chatHistories = chatHistoryManager.chatHistoriesFlow.first()
            val currentChatId = chatHistoryManager.currentChatIdFlow.first()
            val messageCounts = chatHistoryManager.getMessageCountsByChatId()

            val query = tool.parameters.find { it.name == "query" }?.value?.trim().orEmpty()
            val matchRaw = tool.parameters.find { it.name == "match" }?.value?.trim()?.lowercase()
            val matchMode = when (matchRaw) {
                null, "", "contains" -> "contains"
                "exact", "regex" -> matchRaw
                else ->
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatListResultData(totalCount = 0, currentChatId = currentChatId, chats = emptyList()),
                        error = "Invalid parameter: match must be contains/exact/regex"
                    )
            }

            val rawLimit = tool.parameters.find { it.name == "limit" }?.value?.trim()
            val parsedLimit = rawLimit?.takeIf { it.isNotBlank() }?.toIntOrNull()
            if (rawLimit != null && rawLimit.isNotBlank() && parsedLimit == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatListResultData(totalCount = 0, currentChatId = currentChatId, chats = emptyList()),
                    error = "Invalid parameter: limit must be an integer"
                )
            }
            val limit = (parsedLimit ?: 50).coerceIn(1, 200)

            val sortByRaw = tool.parameters.find { it.name == "sort_by" }?.value?.trim()
            val sortBy = when (sortByRaw) {
                null, "", "updatedAt" -> "updatedAt"
                "createdAt", "messageCount" -> sortByRaw
                else ->
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatListResultData(totalCount = 0, currentChatId = currentChatId, chats = emptyList()),
                        error = "Invalid parameter: sort_by must be updatedAt/createdAt/messageCount"
                    )
            }

            val sortOrderRaw = tool.parameters.find { it.name == "sort_order" }?.value?.trim()?.lowercase()
            val sortOrder = when (sortOrderRaw) {
                null, "", "desc" -> "desc"
                "asc" -> "asc"
                else ->
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = ChatListResultData(totalCount = 0, currentChatId = currentChatId, chats = emptyList()),
                        error = "Invalid parameter: sort_order must be asc/desc"
                    )
            }

            val matched = chatHistories
                .filter { chat -> isMatchTitle(chat.title, query, matchMode) }
                .sortedWith { a, b ->
                    val av = when (sortBy) {
                        "messageCount" -> toSortableNumber((messageCounts[a.id] ?: 0).toString())
                        "createdAt" -> toEpochMillis(a.createdAt)
                        else -> toEpochMillis(a.updatedAt)
                    }
                    val bv = when (sortBy) {
                        "messageCount" -> toSortableNumber((messageCounts[b.id] ?: 0).toString())
                        "createdAt" -> toEpochMillis(b.createdAt)
                        else -> toEpochMillis(b.updatedAt)
                    }
                    if (sortOrder == "asc") av.compareTo(bv) else bv.compareTo(av)
                }

            val chatInfoList = matched.take(limit).map { chat ->
                buildChatInfo(chat, messageCounts, currentChatId)
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatListResultData(
                    totalCount = matched.size,
                    currentChatId = currentChatId,
                    chats = chatInfoList
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list chats", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatListResultData(
                    totalCount = 0,
                    currentChatId = null,
                    chats = emptyList()
                ),
                error = "Error listing chats: ${e.message}"
            )
        }
    }

    /**
     * 切换对话
     */
    suspend fun switchChat(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                    error = "Service not connected"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                error = "ChatServiceCore not initialized"
            )

            val chatId = tool.parameters.find { it.name == "chat_id" }?.value
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                    error = "Invalid parameter: missing chat_id"
                )
            }

            // 检查对话是否存在并获取标题
            val targetChat = core.chatHistories.value.find { it.id == chatId }
            if (targetChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = chatId, chatTitle = ""),
                    error = "Chat does not exist: $chatId"
                )
            }

            // 切换对话
            core.switchChatLocal(chatId)
            
            // 等待切换完成（最多等待1秒）
            var attempts = 0
            while (attempts < 10 && core.currentChatId.value != chatId) {
                delay(100)
                attempts++
            }
            
            if (core.currentChatId.value == chatId) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatSwitchResultData(
                        chatId = chatId,
                        chatTitle = targetChat.title
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = chatId, chatTitle = targetChat.title),
                    error = "Failed to switch chat, current chat ID not updated"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to switch chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                error = "Error switching chat: ${e.message}"
            )
        }
    }

    /**
     * 向AI发送消息
     */
    suspend fun startMessageToAIStream(tool: AITool): MessageSendStreamStartResult {
        return try {
            if (!ensureServiceConnected()) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = ""),
                        error = "Service not connected"
                    )
                )
            }

            val core =
                chatCore
                    ?: return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = "", message = ""),
                            error = "ChatServiceCore not initialized"
                        )
                    )

            val message = tool.parameters.find { it.name == "message" }?.value
            if (message.isNullOrBlank()) {
                return MessageSendStreamStartResult.Failed(
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = "", message = ""),
                        error = "Invalid parameter: missing message"
                    )
                )
            }

            val roleCardManager = CharacterCardManager.getInstance(appContext)
            val roleCardId = tool.parameters.find { it.name == "role_card_id" }?.value?.trim()
            if (!roleCardId.isNullOrBlank()) {
                val cardExists = runCatching { roleCardManager.getCharacterCard(roleCardId) }.isSuccess
                if (!cardExists) {
                    return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = "", message = message),
                            error = "Invalid parameter: role_card_id not found"
                        )
                    )
                }
            }

            val senderNameParam = tool.parameters.find { it.name == "sender_name" }?.value?.trim()
            val proxySenderName = senderNameParam?.takeIf { it.isNotBlank() }

            try {
                // 可选的 chat_id 参数
                val targetChatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
                val hasTargetChat = !targetChatId.isNullOrBlank()

                if (hasTargetChat) {
                    val chatExists = core.chatHistories.value.any { it.id == targetChatId }
                    if (!chatExists) {
                        return MessageSendStreamStartResult.Failed(
                            ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = MessageSendResultData(chatId = targetChatId!!, message = message),
                                error = "Specified chat does not exist: $targetChatId"
                            )
                        )
                    }
                }

                val preflightChatId = targetChatId ?: core.currentChatId.value

                try {
                    preflightChatId?.let { chatId ->
                        withTimeout(300000L) {
                            core.activeStreamingChatIds.first { activeChatIds ->
                                !activeChatIds.contains(chatId)
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = preflightChatId ?: "", message = message),
                            error = "Previous message is still being processed"
                        )
                    )
                }

                if (hasTargetChat) {
                    // 后台发送到指定对话，不切换 UI
                    core.sendUserMessage(
                        promptFunctionType = PromptFunctionType.CHAT,
                        roleCardIdOverride = roleCardId,
                        chatIdOverride = preflightChatId,
                        messageTextOverride = message,
                        proxySenderNameOverride = proxySenderName
                    )
                } else {
                    // 发送消息（包含总结逻辑），由 Coordination 处理 chatId 默认
                    core.sendUserMessage(
                        promptFunctionType = PromptFunctionType.CHAT,
                        roleCardIdOverride = roleCardId,
                        messageTextOverride = message,
                        proxySenderNameOverride = proxySenderName
                    )
                }

                val resolvedChatId = if (hasTargetChat) {
                    preflightChatId
                } else {
                    withTimeout(RESPONSE_STREAM_ACQUIRE_TIMEOUT) {
                        var id = core.currentChatId.value
                        while (id == null) {
                            delay(50)
                            id = core.currentChatId.value
                        }
                        id
                    }
                }

                if (resolvedChatId == null) {
                    return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = preflightChatId ?: "", message = message),
                            error = "Unable to get current chat ID"
                        )
                    )
                }

                val responseStream: SharedStream<String> = try {
                    var stream: SharedStream<String>? = core.getResponseStream(resolvedChatId)
                    withTimeout(RESPONSE_STREAM_ACQUIRE_TIMEOUT) {
                        while (stream == null) {
                            val state = core.inputProcessingStateByChatId.value[resolvedChatId]
                                ?: InputProcessingState.Idle
                            if (state is InputProcessingState.Error) {
                                throw IllegalStateException(state.message)
                            }
                            delay(50)
                            stream = core.getResponseStream(resolvedChatId)
                        }
                    }
                    requireNotNull(stream)
                } catch (e: TimeoutCancellationException) {
                    runCatching { core.cancelMessage(resolvedChatId) }
                    return MessageSendStreamStartResult.Failed(
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = resolvedChatId, message = message),
                            error = "Timeout waiting for AI response"
                        )
                    )
                }

                MessageSendStreamStartResult.Started(
                    MessageSendStreamSession(
                        chatId = resolvedChatId,
                        message = message,
                        responseStream = responseStream,
                        currentStateProvider = {
                            core.inputProcessingStateByChatId.value[resolvedChatId]
                                ?: InputProcessingState.Idle
                        },
                        cancelAction = {
                            runCatching { core.cancelMessage(resolvedChatId) }
                        }
                    )
                )
            } finally {}
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send message", e)
            MessageSendStreamStartResult.Failed(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = ""),
                    error = "Error sending message: ${e.message}"
                )
            )
        }
    }

    suspend fun sendMessageToAI(tool: AITool): ToolResult {
        return try {
            when (val startResult = startMessageToAIStream(tool)) {
                is MessageSendStreamStartResult.Failed -> startResult.result
                is MessageSendStreamStartResult.Started -> {
                    val session = startResult.session
                    val aiResponse =
                        try {
                            withTimeout(AI_RESPONSE_TIMEOUT) {
                                val sb = StringBuilder()
                                session.responseStream.collect { chunk: String ->
                                    sb.append(chunk)
                                }
                                sb.toString()
                            }
                        } catch (e: TimeoutCancellationException) {
                            runCatching { session.cancel() }
                            return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = MessageSendResultData(chatId = session.chatId, message = session.message),
                                error = "Timeout waiting for AI reply"
                            )
                        }

                    val finalState = session.currentState()
                    if (finalState is InputProcessingState.Error) {
                        ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(
                                chatId = session.chatId,
                                message = session.message,
                                aiResponse = aiResponse,
                                receivedAt = System.currentTimeMillis()
                            ),
                            error = finalState.message
                        )
                    } else {
                        ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = MessageSendResultData(
                                chatId = session.chatId,
                                message = session.message,
                                aiResponse = aiResponse,
                                receivedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send message", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = MessageSendResultData(chatId = "", message = ""),
                error = "Error sending message: ${e.message}"
            )
        }
    }

    /**
     * 向AI发送消息（高级参数）
     */
    suspend fun sendMessageToAIAdvanced(tool: AITool): ToolResult {
        return try {
            val message = tool.parameters.find { it.name == "message" }?.value?.trim()
            if (message.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = ""),
                    error = "Invalid parameter: missing message"
                )
            }

            val maxTokensParam = tool.parameters.find { it.name == "max_tokens" }?.value?.trim()
            val maxTokens = maxTokensParam?.toIntOrNull()
            if (maxTokens == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = message),
                    error = "Invalid parameter: max_tokens must be an integer"
                )
            }

            val tokenUsageThresholdParam =
                tool.parameters.find { it.name == "token_usage_threshold" }?.value?.trim()
            val tokenUsageThreshold = tokenUsageThresholdParam?.toDoubleOrNull()
            if (tokenUsageThreshold == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = message),
                    error = "Invalid parameter: token_usage_threshold must be a number"
                )
            }

            fun parseBooleanOrNull(value: String?): Boolean? {
                return when (value?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }

            val chatId = tool.parameters.find { it.name == "chat_id" }?.value?.trim()
                ?.takeIf { it.isNotBlank() }
            val chatHistoryParam = tool.parameters.find { it.name == "chat_history" }?.value?.trim()
            val workspacePath =
                tool.parameters.find { it.name == "workspace_path" }?.value?.trim()?.takeIf { it.isNotBlank() }
            val functionTypeParam = tool.parameters.find { it.name == "function_type" }?.value?.trim()
            val promptFunctionTypeParam =
                tool.parameters.find { it.name == "prompt_function_type" }?.value?.trim()
            val enableThinkingParam = tool.parameters.find { it.name == "enable_thinking" }?.value?.trim()
            val thinkingGuidanceParam = tool.parameters.find { it.name == "thinking_guidance" }?.value?.trim()
            val enableMemoryQueryParam = tool.parameters.find { it.name == "enable_memory_query" }?.value?.trim()
            val customSystemPromptTemplate =
                tool.parameters.find { it.name == "custom_system_prompt_template" }?.value?.trim()
                    ?.takeIf { it.isNotBlank() }
            val isSubTaskParam = tool.parameters.find { it.name == "is_sub_task" }?.value?.trim()
            val streamParam = tool.parameters.find { it.name == "stream" }?.value?.trim()

            val functionType =
                if (functionTypeParam.isNullOrBlank()) {
                    FunctionType.CHAT
                } else {
                    runCatching { FunctionType.valueOf(functionTypeParam.uppercase()) }.getOrNull()
                        ?: return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = chatId ?: "", message = message),
                            error = "Invalid parameter: function_type is invalid: $functionTypeParam"
                        )
                }

            val promptFunctionType =
                if (promptFunctionTypeParam.isNullOrBlank()) {
                    PromptFunctionType.CHAT
                } else {
                    runCatching { PromptFunctionType.valueOf(promptFunctionTypeParam.uppercase()) }.getOrNull()
                        ?: return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = chatId ?: "", message = message),
                            error = "Invalid parameter: prompt_function_type is invalid: $promptFunctionTypeParam"
                        )
                }

            val enableThinking = parseBooleanOrNull(enableThinkingParam)
            if (enableThinkingParam != null && enableThinking == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = chatId ?: "", message = message),
                    error = "Invalid parameter: enable_thinking must be true/false"
                )
            }

            val thinkingGuidance = parseBooleanOrNull(thinkingGuidanceParam)
            if (thinkingGuidanceParam != null && thinkingGuidance == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = chatId ?: "", message = message),
                    error = "Invalid parameter: thinking_guidance must be true/false"
                )
            }

            val enableMemoryQuery = parseBooleanOrNull(enableMemoryQueryParam)
            if (enableMemoryQueryParam != null && enableMemoryQuery == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = chatId ?: "", message = message),
                    error = "Invalid parameter: enable_memory_query must be true/false"
                )
            }

            val isSubTask = parseBooleanOrNull(isSubTaskParam)
            if (isSubTaskParam != null && isSubTask == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = chatId ?: "", message = message),
                    error = "Invalid parameter: is_sub_task must be true/false"
                )
            }

            val stream = parseBooleanOrNull(streamParam)
            if (streamParam != null && stream == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = chatId ?: "", message = message),
                    error = "Invalid parameter: stream must be true/false"
                )
            }

            val chatHistory =
                if (chatHistoryParam.isNullOrBlank()) {
                    emptyList()
                } else {
                    val parsed = runCatching {
                        val arr = JSONArray(chatHistoryParam)
                        val result = ArrayList<Pair<String, String>>(arr.length())
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONArray(i)
                            if (item.length() < 2) {
                                return@runCatching null
                            }
                            result.add(Pair(item.getString(0), item.getString(1)))
                        }
                        result
                    }.getOrNull()
                    if (parsed == null) {
                        return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = MessageSendResultData(chatId = chatId ?: "", message = message),
                            error = "Invalid parameter: chat_history must be a JSON array of [role, content]"
                        )
                    }
                    parsed
                }

            val enhancedService =
                if (chatId != null) {
                    EnhancedAIService.getChatInstance(appContext, chatId)
                } else {
                    EnhancedAIService.getInstance(appContext)
                }

            val responseBuilder = StringBuilder()
            val responseStream =
                enhancedService.sendMessage(
                    message = message,
                    chatId = chatId,
                    chatHistory = chatHistory.toPromptTurns(),
                    workspacePath = workspacePath,
                    functionType = functionType,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking ?: false,
                    thinkingGuidance = thinkingGuidance ?: false,
                    enableMemoryQuery = enableMemoryQuery ?: true,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    customSystemPromptTemplate = customSystemPromptTemplate,
                    isSubTask = isSubTask ?: false,
                    stream = stream ?: true
                )

            responseStream.collect { chunk ->
                responseBuilder.append(chunk)
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = MessageSendResultData(
                    chatId = chatId ?: "",
                    message = message,
                    aiResponse = responseBuilder.toString(),
                    receivedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send message (advanced)", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = MessageSendResultData(chatId = "", message = ""),
                error = "Error sending message: ${e.message}"
            )
        }
    }

    /**
     * 列出所有角色卡
     */
    suspend fun listCharacterCards(tool: AITool): ToolResult {
        return try {
            val characterCardManager = CharacterCardManager.getInstance(appContext)
            val cards = characterCardManager.getAllCharacterCards()
            val result = CharacterCardListResultData(
                totalCount = cards.size,
                cards = cards.map { card ->
                    CharacterCardListResultData.CharacterCardInfo(
                        id = card.id,
                        name = card.name,
                        description = card.description,
                        isDefault = card.isDefault,
                        createdAt = card.createdAt,
                        updatedAt = card.updatedAt
                    )
                }
            )
            ToolResult(
                toolName = tool.name,
                success = true,
                result = result
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list character cards", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = CharacterCardListResultData(totalCount = 0, cards = emptyList()),
                error = "Error listing character cards: ${e.message}"
            )
        }
    }
}
