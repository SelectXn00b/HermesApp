package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.WorkspaceRenameResult
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.ChatMessageTimestampAllocator
import kotlinx.coroutines.withTimeoutOrNull

/** 委托类，负责管理聊天历史相关功能 */
class ChatHistoryDelegate(
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val selectionMode: ChatSelectionMode = ChatSelectionMode.FOLLOW_GLOBAL,
        private val onTokenStatisticsLoaded: (chatId: String, inputTokens: Int, outputTokens: Int, windowSize: Int) -> Unit,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val ensureAiServiceAvailable: () -> Unit = {}, // 确保AI服务可用的回调
        private val getChatStatistics: () -> Triple<Int, Int, Int> = { Triple(0, 0, 0) }, // 获取（输入token, 输出token, 窗口大小）
        private val onScrollToBottom: () -> Unit = {} // 滚动到底部事件回调
) {
    companion object {
        private const val TAG = "ChatHistoryDelegate"
        // This constant is now in AIMessageManager
        // private const val SUMMARY_CHUNK_SIZE = 8
    }

    private val chatHistoryManager = ChatHistoryManager.getInstance(context)
    private val characterCardManager = CharacterCardManager.getInstance(context) // 新增
    private val activePromptManager = ActivePromptManager.getInstance(context)
    private val isInitialized = AtomicBoolean(false)
    private val historyUpdateMutex = Mutex()
    private val allowAddMessage = AtomicBoolean(true) // 控制是否允许添加消息，切换对话时设为false
    private var beforeDestructiveHistoryMutation: (suspend (String) -> Unit)? = null
    private var afterDestructiveHistoryMutation: (suspend (String) -> Unit)? = null

    private var pendingPersistChatOrderJob: Job? = null

    // This is no longer needed here as summary logic is moved.
    // private val apiPreferences = ApiPreferences(context)

    // State flows
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    fun setBeforeDestructiveHistoryMutation(handler: suspend (String) -> Unit) {
        beforeDestructiveHistoryMutation = handler
    }

    fun setAfterDestructiveHistoryMutation(handler: suspend (String) -> Unit) {
        afterDestructiveHistoryMutation = handler
    }

    private suspend fun prepareChatForDestructiveMutation(chatId: String) {
        beforeDestructiveHistoryMutation?.invoke(chatId)
    }

    private suspend fun finishDestructiveHistoryMutation(chatId: String) {
        afterDestructiveHistoryMutation?.invoke(chatId)
    }

    private suspend fun runDestructiveHistoryMutation(
        chatId: String,
        mutation: suspend () -> Boolean
    ) {
        prepareChatForDestructiveMutation(chatId)
        val didMutate = historyUpdateMutex.withLock { mutation() }
        if (didMutate) {
            finishDestructiveHistoryMutation(chatId)
        }
    }

    private suspend fun runCurrentChatDestructiveHistoryMutation(
        mismatchMessage: String,
        mutation: suspend (String) -> Boolean
    ) {
        val chatIdSnapshot = _currentChatId.value ?: return
        prepareChatForDestructiveMutation(chatIdSnapshot)
        val didMutate =
            historyUpdateMutex.withLock {
                val currentChatId = _currentChatId.value
                if (currentChatId != chatIdSnapshot) {
                    AppLogger.w(
                        TAG,
                        "$mismatchMessage: expected=$chatIdSnapshot, actual=$currentChatId"
                    )
                    return@withLock false
                }
                mutation(chatIdSnapshot)
            }
        if (didMutate) {
            finishDestructiveHistoryMutation(chatIdSnapshot)
        }
    }

    suspend fun getChatHistory(chatId: String): List<ChatMessage> {
        return if (chatId == _currentChatId.value) {
            _chatHistory.value
        } else {
            chatHistoryManager.loadChatMessages(chatId)
        }
    }

    private val _showChatHistorySelector = MutableStateFlow(false)
    val showChatHistorySelector: StateFlow<Boolean> = _showChatHistorySelector.asStateFlow()

    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    // This is no longer the responsibility of this delegate
    // private var summarizationPerformed = false

    init {
        initialize()
    }

    private fun initialize() {
        if (!isInitialized.compareAndSet(false, true)) {
            return
        }

        coroutineScope.launch {
            chatHistoryManager.chatHistoriesFlow.collect { histories ->
                _chatHistories.value = histories

                val currentId = _currentChatId.value
                if (currentId != null && histories.none { it.id == currentId }) {
                    val exists = chatHistoryManager.chatExists(currentId)
                    if (!exists) {
                        AppLogger.w(TAG, "当前聊天已不存在，清除currentChatId: $currentId")
                        if (selectionMode == ChatSelectionMode.FOLLOW_GLOBAL) {
                            chatHistoryManager.clearCurrentChatId()
                        }
                        _currentChatId.value = null
                        _chatHistory.value = emptyList()
                    }
                }
            }
        }

        when (selectionMode) {
            ChatSelectionMode.FOLLOW_GLOBAL -> {
                coroutineScope.launch {
                    chatHistoryManager.currentChatIdFlow.collect { chatId ->
                        if (chatId != null && chatId != _currentChatId.value) {
                            if (!chatHistoryManager.chatExists(chatId)) {
                                AppLogger.w(TAG, "currentChatId不存在于数据库，已清除: $chatId")
                                chatHistoryManager.clearCurrentChatId()
                                _currentChatId.value = null
                                _chatHistory.value = emptyList()
                                return@collect
                            }
                            AppLogger.d(TAG, "检测到聊天ID变化: ${_currentChatId.value} -> $chatId")
                            _currentChatId.value = chatId
                            loadChatMessages(chatId)
                        } else if (chatId == null && _currentChatId.value == null) {
                            AppLogger.d(TAG, "首次初始化，没有当前聊天")
                        }
                    }
                }
            }
            ChatSelectionMode.LOCAL_ONLY -> {
                coroutineScope.launch {
                    val initialChatId =
                        withTimeoutOrNull(300) {
                            chatHistoryManager.currentChatIdFlow.first { it != null }
                        } ?: chatHistoryManager.currentChatIdFlow.value

                    if (initialChatId == null) {
                        AppLogger.d(TAG, "本地会话初始化时没有 currentChatId")
                        return@launch
                    }

                    if (!chatHistoryManager.chatExists(initialChatId)) {
                        AppLogger.w(TAG, "初始 currentChatId 不存在，跳过本地会话初始化: $initialChatId")
                        return@launch
                    }

                    AppLogger.d(TAG, "本地会话初始化 currentChatId: $initialChatId")
                    _currentChatId.value = initialChatId
                    loadChatMessages(initialChatId)
                }
            }
        }

        // 监听活跃目标变更：仅当当前为角色卡时才同步开场白
        coroutineScope.launch {
            activePromptManager.activePromptFlow.collect { activePrompt ->
                if (activePrompt is ActivePrompt.CharacterCard) {
                    val chatId = _currentChatId.value ?: return@collect
                    syncOpeningStatementIfNoUserMessage(chatId)
                }
            }
        }
    }

    private suspend fun loadChatMessages(chatId: String) {
        try {
            // 直接从数据库加载消息
            val messages = chatHistoryManager.loadChatMessages(chatId)
            AppLogger.d(TAG, "加载聊天 $chatId 的消息：${messages.size} 条")

            // 无论消息是否为空，都更新聊天历史
            _chatHistory.value = messages

            // 查找聊天元数据，更新token统计
            val selectedChat = _chatHistories.value.find { it.id == chatId }
            if (selectedChat != null) {
                onTokenStatisticsLoaded(chatId, selectedChat.inputTokens, selectedChat.outputTokens, selectedChat.currentWindowSize)


            }

            // 打开历史对话时也执行开场白同步：仅当当前会话还没有用户消息时
            syncOpeningStatementIfNoUserMessage(chatId)
            
            // 加载完成后，允许添加消息
            allowAddMessage.set(true)
            AppLogger.d(TAG, "聊天 $chatId 加载完成，已允许添加消息")
        } catch (e: Exception) {
            AppLogger.e(TAG, "加载聊天消息失败", e)
        }
    }

    /**
     * 智能重新加载聊天消息，通过 timestamp 匹配已存在的消息，保持原实例不变
     * 这样可以防止UI重组，提高性能
     * 
     * @param chatId 聊天ID
     */
    suspend fun reloadChatMessagesSmart(chatId: String) {
        historyUpdateMutex.withLock {
            try {
                // 从数据库加载最新消息
                val newMessages = chatHistoryManager.loadChatMessages(chatId)
                val currentMessages = _chatHistory.value
                
                AppLogger.d(TAG, "智能重新加载聊天 $chatId: 当前 ${currentMessages.size} 条，数据库 ${newMessages.size} 条")
                
                // 创建 timestamp 到消息的映射，用于快速查找
                val currentMessageMap = currentMessages.associateBy { it.timestamp }
                
                // 智能合并：保持已存在消息的实例，只更新内容（如果变化）
                val mergedMessages = newMessages.map { newMsg ->
                    val existingMsg = currentMessageMap[newMsg.timestamp]
                    if (existingMsg != null) {
                        // 消息已存在，保持原实例，但更新内容（如果内容有变化）
                        if (existingMsg.content != newMsg.content || existingMsg.roleName != newMsg.roleName) {
                            existingMsg.copy(content = newMsg.content, roleName = newMsg.roleName)
                        } else {
                            existingMsg
                        }
                    } else {
                        // 新消息，直接添加
                        newMsg
                    }
                }
                
                // 更新聊天历史
                _chatHistory.value = mergedMessages
                
                // 重新加载完成后，允许添加消息
                allowAddMessage.set(true)
                AppLogger.d(TAG, "智能合并完成: ${mergedMessages.size} 条消息，已允许添加消息")
            } catch (e: Exception) {
                AppLogger.e(TAG, "智能重新加载聊天消息失败", e)
            }
        }
    }

    private suspend fun syncOpeningStatementIfNoUserMessage(chatId: String) {
        AppLogger.d(TAG, "开始同步开场白，聊天ID: $chatId")
        
        historyUpdateMutex.withLock {
            val chatMeta = _chatHistories.value.firstOrNull { it.id == chatId }
            if (!chatMeta?.characterGroupId.isNullOrBlank()) {
                AppLogger.d(TAG, "聊天 $chatId 绑定群组角色卡，跳过开场白同步")
                return@withLock
            }

            // 在互斥锁内，先从数据库加载最新消息，确保数据一致性
            // 这样可以避免竞态条件：如果内存中的_chatHistory还未加载，直接从数据库检查
            val dbMessages = chatHistoryManager.loadChatMessages(chatId)
            val hasUserMessage = dbMessages.any { it.sender == "user" }
            
            AppLogger.d(TAG, "从数据库检查消息 - 数据库消息数: ${dbMessages.size}, 内存消息数: ${_chatHistory.value.size}, 是否有用户消息: $hasUserMessage")
            
            if (hasUserMessage) {
                AppLogger.d(TAG, "聊天 $chatId 已存在用户消息，跳过开场白同步")
                // 如果数据库有消息但内存中没有，同步一下内存状态
                if (_chatHistory.value.size != dbMessages.size) {
                    AppLogger.d(TAG, "同步内存消息列表，从 ${_chatHistory.value.size} 条更新为 ${dbMessages.size} 条")
                    _chatHistory.value = dbMessages
                }
                return@withLock
            }

            val boundCardName = chatMeta?.characterCardName
            val boundCard = boundCardName?.let { characterCardManager.findCharacterCardByName(it) }
            val activePrompt = activePromptManager.getActivePrompt()
            val activeCard = when (activePrompt) {
                is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCard(activePrompt.id)
                is ActivePrompt.CharacterGroup -> null
            }
            val effectiveCard = boundCard ?: activeCard

            // 如果没有有效的角色卡，使用默认角色卡
            if (effectiveCard == null) {
                AppLogger.d(TAG, "没有有效的角色卡，跳过开场白处理")
                _chatHistory.value = dbMessages
                return@withLock
            }

            val opening = effectiveCard.openingStatement
            val roleName = effectiveCard.name
            if (boundCard == null && boundCardName != null) {
                AppLogger.w(TAG, "绑定角色卡未找到，回退使用当前活跃角色卡: $boundCardName")
            }
            AppLogger.d(TAG, "获取角色卡信息 - 名称: $roleName, 开场白长度: ${opening.length}, 是否为空: ${opening.isBlank()}, 绑定角色卡: $boundCardName")

            // 使用数据库中的消息作为基准，但优先使用内存中的消息（如果已加载）
            val currentMessages = if (_chatHistory.value.isNotEmpty() && _chatHistory.value.size >= dbMessages.size) {
                _chatHistory.value.toMutableList()
            } else {
                dbMessages.toMutableList()
            }
            val existingIndex = currentMessages.indexOfFirst { it.sender == "ai" }
            AppLogger.d(TAG, "当前消息数量: ${currentMessages.size}, 现有AI消息索引: $existingIndex")

            if (existingIndex >= 0) {
                val existing = currentMessages[existingIndex]
                val isOpeningMessage = existing.provider.isBlank() && existing.modelName.isBlank()
                if (opening.isNotBlank()) {
                    if (isOpeningMessage) {
                        if (existing.content != opening || existing.roleName != roleName) {
                            AppLogger.d(TAG, "更新现有开场白消息 - 原内容长度: ${existing.content.length}, 新内容长度: ${opening.length}, 原角色名: ${existing.roleName}, 新角色名: $roleName")
                            val updated = existing.copy(content = opening, roleName = roleName)
                            currentMessages[existingIndex] = updated
                            _chatHistory.value = currentMessages
                            chatHistoryManager.updateMessage(chatId, updated)
                            AppLogger.d(TAG, "开场白消息更新完成")
                        } else {
                            AppLogger.d(TAG, "开场白内容未变化，无需更新")
                        }
                    } else {
                        AppLogger.d(TAG, "已有AI消息非开场白，跳过同步")
                    }
                } else {
                    if (isOpeningMessage) {
                        AppLogger.d(TAG, "开场白为空，删除现有AI开场白消息，时间戳: ${existing.timestamp}")
                        currentMessages.removeAt(existingIndex)
                        _chatHistory.value = currentMessages
                        chatHistoryManager.deleteMessage(chatId, existing.timestamp)
                        AppLogger.d(TAG, "AI消息删除完成")
                    } else {
                        AppLogger.d(TAG, "开场白为空但现有AI消息非开场白，跳过删除")
                    }
                }
            } else if (opening.isNotBlank()) {
                val openingMessage = ChatMessage(
                    sender = "ai",
                    content = opening,
                    timestamp = ChatMessageTimestampAllocator.next(),
                    roleName = roleName,
                    provider = "", // 开场白不是AI生成，使用空值
                    modelName = "" // 开场白不是AI生成，使用空值
                )
                AppLogger.d(TAG, "添加新开场白消息 - 时间戳: ${openingMessage.timestamp}, 角色名: $roleName, 内容长度: ${opening.length}")
                currentMessages.add(openingMessage)
                _chatHistory.value = currentMessages
                chatHistoryManager.addMessage(chatId, openingMessage)
                AppLogger.d(TAG, "开场白消息添加完成，当前消息总数: ${currentMessages.size}")
            } else {
                AppLogger.d(TAG, "无现有AI消息且开场白为空，无需操作")
            }
        }
        
        AppLogger.d(TAG, "开场白同步完成，聊天ID: $chatId")
    }

    /** 检查是否应该创建新聊天，确保同步 */
    fun checkIfShouldCreateNewChat(): Boolean {
        // 只有当历史记录和当前对话ID都已加载，且未创建过初始对话时才检查
        if (!isInitialized.get() || _currentChatId.value == null) {
            return false
        }
        return true
    }

    /** 创建新的聊天 */
    fun createNewChat(
        characterCardName: String? = null,
        characterGroupId: String? = null,
        group: String? = null,
        inheritGroupFromCurrent: Boolean = true,
        setAsCurrentChat: Boolean = true,
        characterCardId: String? = null
    ) {
        coroutineScope.launch {
            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(inputTokens, outputTokens, windowSize) // 使用获取到的完整统计数据

            // 获取当前对话ID，以便继承分组
            val currentChatId = _currentChatId.value
            val inheritGroupFromChatId = if (inheritGroupFromCurrent) currentChatId else null
            
            // 获取当前活跃的角色卡
            val activePrompt = activePromptManager.getActivePrompt()
            val activeCard = when (activePrompt) {
                is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCard(activePrompt.id)
                is ActivePrompt.CharacterGroup -> null
            }
            val resolvedCard =
                if (characterGroupId.isNullOrBlank()) {
                    characterCardId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { characterCardManager.getCharacterCard(it) }
                        ?: activeCard
                } else {
                    null  // 群组模式下不使用角色卡
                }

            // 确定角色卡名称：如果参数指定了则使用参数，否则使用目标角色卡
            val effectiveCharacterCardName =
                if (characterGroupId.isNullOrBlank()) {
                    characterCardName ?: resolvedCard?.name
                } else {
                    null  // 群组模式下不使用角色卡名称
                }

            val shouldSyncCurrentChatToGlobal =
                selectionMode == ChatSelectionMode.FOLLOW_GLOBAL && setAsCurrentChat

            // 创建新对话，如果有当前对话则继承其分组，并绑定角色卡
            val newChat = chatHistoryManager.createNewChat(
                group = group,
                inheritGroupFromChatId = inheritGroupFromChatId,
                characterCardName = effectiveCharacterCardName,
                characterGroupId = characterGroupId,
                setAsCurrentChat = shouldSyncCurrentChatToGlobal
            )

            // --- 新增：检查并添加开场白（群组模式跳过） ---
            if (characterGroupId.isNullOrBlank() && characterCardName == null && resolvedCard != null && resolvedCard.openingStatement.isNotBlank()) {
                val openingMessage = ChatMessage(
                    sender = "ai",
                    content = resolvedCard.openingStatement,
                    timestamp = ChatMessageTimestampAllocator.next(),
                    roleName = resolvedCard.name, // 使用角色卡的名称
                    provider = "", // 开场白不是AI生成，使用空值
                    modelName = "" // 开场白不是AI生成，使用空值
                )
                // 保存带开场白的消息到数据库
                chatHistoryManager.addMessage(newChat.id, openingMessage)
            }
            // --- 结束 ---
            
            // 等待数据库Flow更新，确保新对话在列表中（最多等待500ms）
            withTimeoutOrNull(500) {
                _chatHistories.first { histories ->
                    histories.any { it.id == newChat.id }
                }
            }
            
            if (setAsCurrentChat) {
                if (selectionMode == ChatSelectionMode.FOLLOW_GLOBAL) {
                    // FOLLOW_GLOBAL 由 currentChatId 的 collector 负责驱动切换与加载。
                    chatHistoryManager.setCurrentChatId(newChat.id)
                } else {
                    // LOCAL_ONLY 不写回全局 currentChatId，只切换悬浮窗自己的本地会话。
                    _currentChatId.value = newChat.id
                    loadChatMessages(newChat.id)
                }
                onTokenStatisticsLoaded(newChat.id, 0, 0, 0)
            }
        }
    }

    /** 切换聊天 */
    fun switchChat(chatId: String, syncToGlobal: Boolean = true) {
        coroutineScope.launch {
            // 切换对话时，禁止添加消息
            allowAddMessage.set(false)
            AppLogger.d(TAG, "切换对话到 $chatId (syncToGlobal=$syncToGlobal)，已禁止添加消息")

            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(inputTokens, outputTokens, windowSize) // 切换前使用正确的窗口大小保存

            if (syncToGlobal) {
                chatHistoryManager.setCurrentChatId(chatId)
                // _currentChatId.value will be updated by the collector, no need to set it here.
                // loadChatMessages(chatId) is also called by the collector.

                // 等待切换完成后再滚动到底部
                withTimeoutOrNull(500) {
                    _currentChatId.first { it == chatId }
                }
            } else {
                // 本地切换：只更新内存态（供悬浮窗使用），不写回 DataStore。
                _currentChatId.value = chatId
                loadChatMessages(chatId)
            }

            onScrollToBottom()
        }
    }

    /** 创建对话分支 */
    fun createBranch(upToMessageTimestamp: Long? = null) {
        coroutineScope.launch {
            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(inputTokens, outputTokens, windowSize) // 保存当前聊天

            val currentChatId = _currentChatId.value
            if (currentChatId != null) {
                // 创建分支
                val branchChat = chatHistoryManager.createBranch(currentChatId, upToMessageTimestamp)
                _currentChatId.value = branchChat.id
                
                // 加载分支的消息
                _chatHistory.value = branchChat.messages
                
                // 加载分支的 token 统计（继承自父对话）
                onTokenStatisticsLoaded(
                    branchChat.id,
                    branchChat.inputTokens,
                    branchChat.outputTokens,
                    branchChat.currentWindowSize
                )
                
                delay(200)
                onScrollToBottom()
            }
        }
    }

    /** 删除聊天历史 */
    fun deleteChatHistory(chatId: String, onResult: (Boolean) -> Unit = {}) {
        coroutineScope.launch {
            if (!chatHistoryManager.canDeleteChatHistory(chatId)) {
                onResult(false)
                return@launch
            }
            prepareChatForDestructiveMutation(chatId)
            val deleted =
                if (chatId == _currentChatId.value) {
                    val ok = chatHistoryManager.deleteChatHistory(chatId)
                    if (ok) {
                        createNewChat()
                    }
                    ok
                } else {
                    chatHistoryManager.deleteChatHistory(chatId)
                }
            onResult(deleted)
        }
    }

    /** 删除单条消息 */
    fun deleteMessage(index: Int) {
        coroutineScope.launch {
            runCurrentChatDestructiveHistoryMutation("删除消息时当前会话已变化，放弃操作") { chatId ->
                val currentMessages = _chatHistory.value.toMutableList()
                if (index < 0 || index >= currentMessages.size) {
                    return@runCurrentChatDestructiveHistoryMutation false
                }

                val messageToDelete = currentMessages[index]
                chatHistoryManager.deleteMessage(chatId, messageToDelete.timestamp)
                currentMessages.removeAt(index)
                _chatHistory.value = currentMessages
                true
            }
        }
    }

    fun deleteMessageByTimestamp(chatId: String, timestamp: Long) {
        coroutineScope.launch {
            runDestructiveHistoryMutation(chatId) {
                chatHistoryManager.deleteMessage(chatId, timestamp)

                if (_currentChatId.value == chatId) {
                    val currentMessages = _chatHistory.value
                    val newMessages = currentMessages.filterNot { it.timestamp == timestamp }
                    if (newMessages.size != currentMessages.size) {
                        _chatHistory.value = newMessages
                    }
                }
                true
            }
        }
    }

    /** 从指定索引删除后续所有消息 */
    suspend fun deleteMessagesFrom(index: Int) {
        runCurrentChatDestructiveHistoryMutation("批量删除后续消息时当前会话已变化，放弃操作") { chatId ->
                val currentMessages = _chatHistory.value
                if (index < 0 || index >= currentMessages.size) {
                    return@runCurrentChatDestructiveHistoryMutation false
                }

                val messageToStartDeletingFrom = currentMessages[index]
                val newHistory = currentMessages.subList(0, index)
                chatHistoryManager.deleteMessagesFrom(chatId, messageToStartDeletingFrom.timestamp)
                _chatHistory.value = newHistory
                true
            }
    }

    suspend fun selectMessageVariant(timestamp: Long, selectedVariantIndex: Int) {
        val chatId = _currentChatId.value ?: throw IllegalStateException("No active chat")
        val shouldReloadCurrentChat = historyUpdateMutex.withLock {
            chatHistoryManager.selectMessageVariant(chatId, timestamp, selectedVariantIndex)
            chatId == _currentChatId.value
        }
        if (shouldReloadCurrentChat && chatId == _currentChatId.value) {
            loadChatMessages(chatId)
        }
    }

    suspend fun addMessageVariant(
        timestamp: Long,
        message: ChatMessage,
        chatIdOverride: String? = null,
    ): Int {
        val chatId = chatIdOverride ?: _currentChatId.value ?: throw IllegalStateException("No active chat")
        val isCurrentChat = chatId == _currentChatId.value
        val selectedVariantIndex = historyUpdateMutex.withLock {
            val selectedVariantIndex =
                chatHistoryManager.addMessageVariant(chatId, timestamp, message)
            selectedVariantIndex
        }
        if (isCurrentChat && chatId == _currentChatId.value) {
            loadChatMessages(chatId)
        }
        return selectedVariantIndex
    }

    /** 清空当前聊天 */
    fun clearCurrentChat(onResult: (Boolean) -> Unit = {}) {
        coroutineScope.launch {
            val chatId = _currentChatId.value
            if (chatId == null) {
                createNewChat()
                onResult(false)
                return@launch
            }

            if (!chatHistoryManager.canDeleteChatHistory(chatId)) {
                onResult(false)
                return@launch
            }
            prepareChatForDestructiveMutation(chatId)
            val deleted = chatHistoryManager.deleteChatHistory(chatId)
            if (deleted) {
                createNewChat()
            }
            onResult(deleted)
        }
    }

    /** 保存当前聊天到持久存储 */
    fun saveCurrentChat(
        inputTokens: Int = 0,
        outputTokens: Int = 0,
        actualContextWindowSize: Int = 0,
        chatIdOverride: String? = null
    ) {
        coroutineScope.launch {
            val chatId = chatIdOverride ?: _currentChatId.value
            chatId?.let {
                if (
                    _chatHistory.value.isNotEmpty() ||
                        inputTokens != 0 ||
                        outputTokens != 0 ||
                        actualContextWindowSize != 0
                ) {
                    chatHistoryManager.updateChatTokenCounts(
                        it,
                        inputTokens,
                        outputTokens,
                        actualContextWindowSize
                    )
                }
            }
        }
    }

    /** 绑定聊天到工作区 */
    fun bindChatToWorkspace(chatId: String, workspace: String, workspaceEnv: String?) {
        coroutineScope.launch {
            // 1. Update the database
            chatHistoryManager.updateChatWorkspace(chatId, workspace, workspaceEnv)

            // 2. Manually update the UI state to reflect the change immediately
            val updatedHistories = _chatHistories.value.map {
                if (it.id == chatId) {
                    it.copy(workspace = workspace, workspaceEnv = workspaceEnv, updatedAt = LocalDateTime.now())
                } else {
                    it
                }
            }
            _chatHistories.value = updatedHistories
        }
    }

    /** 更新聊天绑定的角色卡 */
    fun updateChatCharacterCard(chatId: String, characterCardName: String?) {
        updateChatCharacterBinding(chatId, characterCardName, null)
    }

    /** 更新聊天绑定的群组角色卡 */
    fun updateChatCharacterGroup(chatId: String, characterGroupId: String?) {
        updateChatCharacterBinding(chatId, null, characterGroupId)
    }

    /** 同时更新聊天绑定的角色卡与群组 */
    fun updateChatCharacterBinding(
        chatId: String,
        characterCardName: String?,
        characterGroupId: String?
    ) {
        coroutineScope.launch {
            chatHistoryManager.updateChatCharacterBinding(chatId, characterCardName, characterGroupId)

            val updatedHistories = _chatHistories.value.map {
                if (it.id == chatId) {
                    it.copy(
                        characterCardName = characterCardName,
                        characterGroupId = characterGroupId,
                        updatedAt = LocalDateTime.now()
                    )
                } else {
                    it
                }
            }
            _chatHistories.value = updatedHistories
        }
    }

    /** 解绑聊天的工作区 */
    fun unbindChatFromWorkspace(chatId: String) {
        coroutineScope.launch {
            // 1. Update the database (set workspace to null)
            chatHistoryManager.updateChatWorkspace(chatId, null, null)

            // 2. Manually update the UI state to reflect the change immediately
            val updatedHistories = _chatHistories.value.map {
                if (it.id == chatId) {
                    it.copy(workspace = null, workspaceEnv = null, updatedAt = LocalDateTime.now())
                } else {
                    it
                }
            }
            _chatHistories.value = updatedHistories
        }
    }

    /** 更新聊天标题 */
    fun updateChatTitle(chatId: String, title: String) {
        coroutineScope.launch {
            // 更新数据库
            chatHistoryManager.updateChatTitle(chatId, title)

            // 更新UI状态
            val updatedHistories =
                    _chatHistories.value.map {
                        if (it.id == chatId) {
                            it.copy(title = title, updatedAt = LocalDateTime.now())
                        } else {
                            it
                        }
                    }
            _chatHistories.value = updatedHistories
        }
    }

    suspend fun renameWorkspaceAndChat(
        chatId: String,
        newWorkspaceName: String
    ): WorkspaceRenameResult {
        val result = chatHistoryManager.renameManagedWorkspace(chatId, newWorkspaceName)
        _chatHistories.value =
            _chatHistories.value.map {
                if (it.id == chatId) {
                    it.copy(
                        title = result.workspaceName,
                        workspace = result.workspacePath,
                        workspaceEnv = result.workspaceEnv,
                        updatedAt = LocalDateTime.now()
                    )
                } else {
                    it
                }
            }
        return result
    }

    /** 根据第一条用户消息生成聊天标题 */
    private fun generateChatTitle(): String {
        val firstUserMessage = _chatHistory.value.firstOrNull { it.sender == "user" }?.content
        return if (firstUserMessage != null) {
            // 截取前20个字符作为标题，并添加省略号
            if (firstUserMessage.length > 20) {
                "${firstUserMessage.take(20)}..."
            } else {
                firstUserMessage
            }
        } else {
            context.getString(R.string.new_conversation)
        }
    }
    
    /**
     * 向聊天历史添加或更新消息。
     *
     * @param message 待添加或更新的消息
     * @param chatIdOverride 可选：指定聊天会话ID（不使用`currentChatId`）
     *
     * 行为逻辑：
     *   - 已存在同时间戳消息：更新内存与数据库（保持UI与持久层一致）。
     *   - 不存在：追加到内存，并持久化。
     */
    private fun replaceOrAppendCurrentChatMessageInMemory(message: ChatMessage) {
        val currentMessages = _chatHistory.value
        val existingIndex = currentMessages.indexOfFirst { it.timestamp == message.timestamp }

        if (existingIndex >= 0) {
            if (message.contentStream == null || currentMessages[existingIndex].contentStream == null) {
                AppLogger.d(TAG, "更新当前会话内存消息, ts: ${message.timestamp}")
                _chatHistory.value =
                    currentMessages.mapIndexed { index, existingMessage ->
                        if (index == existingIndex) {
                            message
                        } else {
                            existingMessage
                        }
                    }
            }
        } else {
            AppLogger.d(TAG, "向当前会话内存追加消息, ts: ${message.timestamp}")
            _chatHistory.value = currentMessages + message
        }
    }

    suspend fun addMessageToChat(message: ChatMessage, chatIdOverride: String? = null) {
        historyUpdateMutex.withLock {
            val targetChatId = chatIdOverride ?: _currentChatId.value ?: return@withLock

            val isCurrentChat = (targetChatId == _currentChatId.value)

            if (message.isVariantPreview) {
                if (isCurrentChat) {
                    replaceOrAppendCurrentChatMessageInMemory(message)
                }
                return@withLock
            }

            // 仅在切换当前会话时阻止写入，后台会话仍允许写入
            if (isCurrentChat && !allowAddMessage.get()) {
                AppLogger.d(
                    TAG,
                    "当前会话正在切换，跳过内存刷新但继续持久化消息: timestamp=${message.timestamp}"
                )
                chatHistoryManager.updateMessage(targetChatId, message)
                return@withLock
            }

            if (!isCurrentChat) {
                    // 非当前会话：使用“更新或插入”语义，避免每个chunk都插入新消息
                chatHistoryManager.updateMessage(targetChatId, message)
                return@withLock
            }

            // 当前会话：尝试在内存中定位并更新
            val currentMessages = _chatHistory.value
            val existingIndex = currentMessages.indexOfFirst { it.timestamp == message.timestamp }

            if (existingIndex >= 0) {
                // 如果新消息结束了流，或者现有消息丢失了流（例如页面切换后重新加载），则允许替换以恢复流或更新最终内容
                if(message.contentStream == null || currentMessages[existingIndex].contentStream == null) {
                    AppLogger.d(TAG, "更新消息到聊天 $targetChatId, condition met, ts: ${message.timestamp}")
                    val updatedMessages = currentMessages.mapIndexed { index, existingMessage ->
                        if (index == existingIndex) {
                            message // 替换为新消息对象
                        } else {
                            existingMessage // 保持原对象不变
                        }
                    }
                    _chatHistory.value = updatedMessages
                }

                chatHistoryManager.updateMessage(targetChatId, message)
            } else {
                AppLogger.d(
                    TAG,
                    "添加新消息到聊天 $targetChatId, isCurrent=$isCurrentChat, stream is null: ${message.contentStream == null}, ts: ${message.timestamp}"
                )
                val updated = currentMessages + message
                _chatHistory.value = updated
                chatHistoryManager.addMessage(targetChatId, message)
            }
        }
    }

    /**
     * 异步向聊天历史添加或更新消息（供不需要等待完成的场景使用）
     */
    fun addMessageToChatAsync(message: ChatMessage, chatIdOverride: String? = null) {
        coroutineScope.launch {
            addMessageToChat(message, chatIdOverride)
        }
    }

    /**
     * 截断聊天记录，会同步删除数据库中指定时间戳之后的消息，并更新内存中的消息列表。
     *
     * @param newHistory 截断后保留的消息列表。
     * @param timestampOfFirstDeletedMessage 用于删除数据库记录的起始时间戳。如果为null，则清空所有消息。
     */
    suspend fun truncateChatHistory(newHistory: List<ChatMessage>, timestampOfFirstDeletedMessage: Long?) {
        runCurrentChatDestructiveHistoryMutation("截断聊天历史时当前会话已变化，放弃操作") { chatIdSnapshot ->
            if (timestampOfFirstDeletedMessage != null) {
                // 从数据库中删除指定时间戳之后的消息
                chatHistoryManager.deleteMessagesFrom(
                        chatIdSnapshot,
                        timestampOfFirstDeletedMessage
                )
            } else {
                // 如果时间戳为空，则清除该聊天的所有消息
                chatHistoryManager.clearChatMessages(chatIdSnapshot)
            }

            // 更新内存中的聊天记录
            _chatHistory.value = newHistory
            true
        }
    }

    /** 更新整个聊天历史 用于编辑或回档等操作 */
    fun updateChatHistory(newHistory: List<ChatMessage>) {
        _chatHistory.value = newHistory.toList()
    }

    /**
     * 更新聊天记录的顺序和分组
     * @param reorderedHistories 重新排序后的完整聊天历史列表
     * @param movedItem 移动的聊天项
     * @param targetGroup 目标分组的名称，如果拖拽到分组上
     */
    fun updateChatOrderAndGroup(
        reorderedHistories: List<ChatHistory>,
        movedItem: ChatHistory,
        targetGroup: String?
    ) {
        coroutineScope.launch {
            try {
                // The list is already reordered. We just need to update displayOrder and group.
                val updatedList = reorderedHistories.mapIndexed { index, history ->
                    var newGroup = history.group
                    if (history.id == movedItem.id && targetGroup != null) {
                        newGroup = targetGroup
                    }
                    history.copy(displayOrder = index.toLong(), group = newGroup)
                }

                // Update UI immediately
                _chatHistories.value = updatedList

                // Persist changes (debounced) to avoid emitting intermediate ordering states.
                // Drag-and-drop reordering can trigger many moves; persisting each move causes
                // Room Flow to emit multiple intermediate lists, leading to visible jumping.
                pendingPersistChatOrderJob?.cancel()
                pendingPersistChatOrderJob = coroutineScope.launch {
                    delay(350)
                    chatHistoryManager.updateChatOrderAndGroup(updatedList)
                }

            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat order and group", e)
                // Optionally revert UI changes or show an error
            }
        }
    }

    /** 重命名分组 */
    fun updateGroupName(oldName: String, newName: String, characterCardName: String?) {
        coroutineScope.launch {
            chatHistoryManager.updateGroupName(oldName, newName, characterCardName)
        }
    }

    /** 删除分组 */
    fun deleteGroup(groupName: String, deleteChats: Boolean, characterCardName: String?) {
        coroutineScope.launch {
            chatHistoryManager.deleteGroup(groupName, deleteChats, characterCardName)
        }
    }

    /** 创建新分组（通过创建新聊天实现） */
    fun createGroup(groupName: String, characterCardName: String?, characterGroupId: String? = null) {
        coroutineScope.launch {
            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(inputTokens, outputTokens, windowSize)

            val newChat = chatHistoryManager.createNewChat(
                group = groupName,
                characterCardName = characterCardName,
                characterGroupId = characterGroupId
            )
            _currentChatId.value = newChat.id
            _chatHistory.value = newChat.messages

            onTokenStatisticsLoaded(newChat.id, 0, 0, 0)
        }
    }

    /**
     * 添加一条总结消息到预先计算好的位置。
     *
     * @param summaryMessage 要添加的总结消息。
     * @param insertPosition 预先计算好的插入索引。
     */
    suspend fun addSummaryMessage(
        summaryMessage: ChatMessage,
        insertPosition: Int,
        chatIdOverride: String? = null
    ) {
        historyUpdateMutex.withLock {
            val chatId = chatIdOverride ?: _currentChatId.value ?: return@withLock
            val isCurrentChat = chatId == _currentChatId.value
            val currentMessages =
                if (isCurrentChat) {
                    _chatHistory.value.toMutableList()
                } else {
                    chatHistoryManager.loadChatMessages(chatId).toMutableList()
                }

            // 检查插入位置是否越界
            if (insertPosition < 0 || insertPosition > currentMessages.size) {
                AppLogger.e(TAG, "总结插入位置越界: insertPosition=$insertPosition, size=${currentMessages.size}，取消插入")
                return@withLock
            }

            // 检查上个消息是否为总结消息
            if (insertPosition > 0 && currentMessages[insertPosition - 1].sender == "summary") {
                AppLogger.e(TAG, "上个消息已是总结消息，取消插入以避免重复")
                return@withLock
            }

            // 检查下个消息是否为总结消息
            if (insertPosition < currentMessages.size && currentMessages[insertPosition].sender == "summary") {
                AppLogger.e(TAG, "下个消息已是总结消息，取消插入以避免重复")
                return@withLock
            }

            val persistedSummaryMessage =
                chatHistoryManager.addMessage(chatId, summaryMessage, insertPosition)

            // 这里必须使用最终持久化后的消息。
            // 位置插入会按实际顺序重排时间戳，如果内存继续保留旧时间戳，
            // 后续编辑保存会因按时间戳找不到原记录而退化成插入新消息。
            currentMessages.add(insertPosition, persistedSummaryMessage)
            AppLogger.d(TAG, "在预计算索引 $insertPosition 处添加总结消息，更新后总消息数量: ${currentMessages.size}")

            // 更新消息列表
            if (isCurrentChat) {
                _chatHistory.value = currentMessages
            }
        }
    }

    // This function is moved to AIMessageManager
    /*
    fun shouldGenerateSummary(
        messages: List<ChatMessage>,
        currentTokens: Int,
        maxTokens: Int
    ): Boolean { ... }
    */

    // This function is moved to AIMessageManager
    /*
    suspend fun summarizeMemory(messages: List<ChatMessage>) { ... }
    */
    
    /**
     * 找到合适的总结插入位置。
     * 新的逻辑是，总结应该插入在上一个已完成对话轮次的末尾，
     * 即最后一条AI消息之后。
     */
    fun findProperSummaryPosition(messages: List<ChatMessage>): Int {
        // 从后往前找，找到最近的一条AI消息的索引。
        val lastAiMessageIndex = messages.indexOfLast { it.sender == "ai" }

        // 摘要应该被放置在最后一条AI消息之后，这标志着一个完整对话轮次的结束。
        // 如果没有找到AI消息（例如，在聊天的开始），lastAiMessageIndex将是-1，
        // 我们将在索引0处插入，这是正确的行为。
        return lastAiMessageIndex + 1
    }

    /** 切换是否显示聊天历史选择器 */
    fun toggleChatHistorySelector() {
        _showChatHistorySelector.value = !_showChatHistorySelector.value
    }

    /** 显示或隐藏聊天历史选择器 */
    fun showChatHistorySelector(show: Boolean) {
        _showChatHistorySelector.value = show
    }

    // This function is moved to AIMessageManager and renamed to getMemoryFromMessages
    /*
    fun getMemory(includePlanInfo: Boolean = true): List<Pair<String, String>> { ... }
    */

    /** 获取EnhancedAIService实例 */
    private fun getEnhancedAiService(): EnhancedAIService? {
        // 使用构造函数中传入的callback获取EnhancedAIService实例
        return getEnhancedAiService.invoke()
    }

    /** 通过回调获取当前token统计数据 */
    private fun getCurrentTokenCounts(): Pair<Int, Int> {
        // 使用构造函数中传入的回调获取当前token统计数据
        val stats = getChatStatistics()
        return Pair(stats.first, stats.second)
    }
}
