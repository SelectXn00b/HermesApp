package com.xiaomo.androidforclaw.core

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/agent-command.ts, ../openclaw/src/gateway/boot.ts
 */


import android.app.Application
import java.util.concurrent.ConcurrentHashMap
import android.os.Build
import android.text.TextUtils
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.routing.buildAgentMainSessionKey
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.workspace.StoragePaths
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.loop.ProgressUpdate
import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import kotlinx.coroutines.flow.asSharedFlow
import com.xiaomo.androidforclaw.ext.mmkv
import com.xiaomo.androidforclaw.ext.simpleSafeLaunch
import com.xiaomo.androidforclaw.providers.llm.toNewMessage
import com.xiaomo.androidforclaw.providers.llm.toLegacyMessage
import com.xiaomo.androidforclaw.accessibility.service.AccessibilityBinderService
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger
import com.xiaomo.androidforclaw.util.MMKVKeys
import com.xiaomo.androidforclaw.util.WakeLockManager
import com.xiaomo.androidforclaw.util.ReasoningTagFilter
import com.xiaomo.androidforclaw.ui.float.SessionFloatWindow
import com.xiaomo.androidforclaw.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import android.os.Environment
import java.io.File
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * New MainEntry - Refactored version based on Nanobot architecture
 *
 * Core changes:
 * 1. Use AgentLoop instead of fixed process
 * 2. Use LLM Provider (Claude Opus 4.6 + Reasoning)
 * 3. Toolize all operations
 * 4. Dynamic decision-making instead of hardcoded flow
 */
object MainEntryNew {
    private const val TAG = "MainEntryNew"
    const val ACTION_AGENT_PROGRESS = "com.xiaomo.androidforclaw.ACTION_AGENT_PROGRESS"
    const val EXTRA_PROGRESS_TYPE = "type"
    const val EXTRA_PROGRESS_TITLE = "title"
    const val EXTRA_PROGRESS_CONTENT = "content"

    // ================ Core Components ================
    private lateinit var application: Application
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var androidToolRegistry: AndroidToolRegistry
    private lateinit var agentLoop: AgentLoop
    private lateinit var contextBuilder: ContextBuilder
    private lateinit var sessionManager: SessionManager
    private lateinit var configLoader: ConfigLoader
    private var subagentSpawner: com.xiaomo.androidforclaw.agent.subagent.SubagentSpawner? = null

    // ================ State Management ================
    var user: String = ""
    private var currentTaskId: String? = null
    private var currentDocId: String? = null
    private val sessionJobs = ConcurrentHashMap<String, Job>()
    @Volatile
    private var activeSessionId: String? = null  // For block reply broadcasting
    @Volatile
    private var lastBlockReplyText: String? = null  // Track last sent block reply to avoid duplicate final
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val taskDataManager: TaskDataManager = TaskDataManager.getInstance()

    // Document sync completion state
    private val _docSyncFinished = MutableStateFlow(false)
    val docSyncFinished = _docSyncFinished.asStateFlow()

    // Test summary completion state
    private val _summaryFinished = MutableStateFlow(false)
    val summaryFinished = _summaryFinished.asStateFlow()

    data class UiProgressEvent(
        val type: String,
        val title: String,
        val content: String
    )

    private val _uiProgressFlow = MutableSharedFlow<UiProgressEvent>(extraBufferCapacity = 64)
    val uiProgressFlow: SharedFlow<UiProgressEvent> = _uiProgressFlow

    /**
     * Get SessionManager (for Gateway use)
     */
    fun getSessionManager(): SessionManager? {
        return if (::sessionManager.isInitialized) sessionManager else null
    }

    /**
     * Get ToolRegistry (for registering extension tools like feishu)
     */
    fun getToolRegistry(): ToolRegistry? {
        return if (::toolRegistry.isInitialized) toolRegistry else null
    }

    /**
     * Initialize - Must be called before use
     */
    fun initialize(app: Application) {
        // Use sessionManager (the last critical component) as the initialization gate,
        // not application. Prevents partial-init → early-return → permanent null state.
        if (::sessionManager.isInitialized) {
            Log.w(TAG, "Already initialized")
            return
        }

        Log.d(TAG, "Initializing MainEntryNew...")

        try {
            application = app
            // 0. Initialize ConfigLoader
            configLoader = ConfigLoader(application)
            Log.d(TAG, "✓ ConfigLoader initialized")

            // 1. Initialize LLM Provider (unified Provider - supports all OpenClaw-compatible APIs)
            val llmProvider = com.xiaomo.androidforclaw.providers.UnifiedLLMProvider(application)
            Log.d(TAG, "✓ UnifiedLLMProvider initialized (supports multi-model APIs)")

            // 2. Initialize ToolRegistry (universal tools - from Pi Coding Agent)
            toolRegistry = ToolRegistry(
                context = application,
                taskDataManager = taskDataManager
            )
            Log.d(TAG, "✓ ToolRegistry initialized (${toolRegistry.getToolCount()} universal tools)")

            // 3. Initialize MemoryManager (memory management + hybrid search index)
            val workspacePath = StoragePaths.workspace.absolutePath
            val openClawCfg = configLoader.loadOpenClawConfig()
            val embeddingProviders = openClawCfg.resolveProviders()
            // Try to find an OpenAI-compatible provider for embeddings
            val embeddingBaseUrl = embeddingProviders.values.firstOrNull()?.baseUrl ?: ""
            val embeddingApiKey = embeddingProviders.values.firstOrNull()?.apiKey ?: ""
            val memoryManager = com.xiaomo.androidforclaw.agent.memory.MemoryManager(
                workspacePath = workspacePath,
                context = application,
                embeddingBaseUrl = embeddingBaseUrl,
                embeddingApiKey = embeddingApiKey
            )

            // 4. Initialize AndroidToolRegistry (Android platform tools)
            androidToolRegistry = AndroidToolRegistry(
                context = application,
                taskDataManager = taskDataManager,
                memoryManager = memoryManager,
                workspacePath = workspacePath,
                cameraCaptureManager = MyApplication.getCameraCaptureManager(),
            )
            Log.d(TAG, "✓ AndroidToolRegistry initialized (${androidToolRegistry.getToolCount()} Android tools)")

            // 5. Initialize context builder (OpenClaw style)
            contextBuilder = ContextBuilder(
                context = application,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                configLoader = configLoader
            )
            Log.d(TAG, "✓ ContextBuilder initialized")

            // 5. Initialize session manager (use workspace directory, aligned with OpenClaw)
            val workspaceDir = com.xiaomo.androidforclaw.workspace.StoragePaths.workspace.also {
                it.mkdirs()
                Log.d(TAG, "Workspace: ${it.absolutePath}")
            }
            sessionManager = SessionManager(
                workspace = workspaceDir
            )
            Log.d(TAG, "✓ SessionManager initialized (workspace: ${workspaceDir.absolutePath})")

            // 6. Initialize context manager (OpenClaw-aligned context overflow handling)
            val contextManager = com.xiaomo.androidforclaw.agent.context.ContextManager(llmProvider)
            Log.d(TAG, "✓ ContextManager initialized")

            // Load maxIterations from config
            val config = configLoader.loadOpenClawConfig()
            val maxIterations = config.agent.maxIterations

            // 7. Initialize AgentLoop
            agentLoop = AgentLoop(
                llmProvider = llmProvider,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                contextManager = contextManager,
                maxIterations = maxIterations,
                modelRef = null,  // Use default model
                configLoader = configLoader  // Gap 2: context window resolution
            )
            Log.d(TAG, "✓ AgentLoop initialized (maxIterations: $maxIterations)")

            // 8. Initialize Subagent system (aligned with OpenClaw subagent-spawn.ts)
            val subagentsConfig = config.agents?.defaults?.subagents
                ?: com.xiaomo.androidforclaw.config.SubagentsConfig()
            if (subagentsConfig.enabled) {
                val registryStore = com.xiaomo.androidforclaw.agent.subagent.SubagentRegistryStore()
                val registry = com.xiaomo.androidforclaw.agent.subagent.SubagentRegistry(registryStore)
                registry.restoreFromDisk()
                val spawner = com.xiaomo.androidforclaw.agent.subagent.SubagentSpawner(
                    registry = registry,
                    configLoader = configLoader,
                    llmProvider = llmProvider,
                    toolRegistry = toolRegistry,
                    androidToolRegistry = androidToolRegistry,
                )
                subagentSpawner = spawner

                // Wire subagent tools into main AgentLoop (depth=0)
                val subagentTools = com.xiaomo.androidforclaw.agent.subagent.SubagentSpawner.buildSubagentTools(
                    spawner = spawner,
                    parentSessionKey = buildAgentMainSessionKey("main"),
                    parentAgentLoop = agentLoop,
                    parentDepth = 0,
                    configLoader = configLoader,
                )
                agentLoop.extraTools = subagentTools
                Log.d(TAG, "✓ Subagent system initialized (${subagentTools.size} tools)")
            } else {
                Log.d(TAG, "⏭ Subagent system disabled in config")
            }

            Log.d(TAG, "========== Initialization Complete ==========")

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            throw RuntimeException("Failed to initialize MainEntryNew", e)
        }
    }

    // registerAllTools() removed
    // Tools are now divided into:
    // - ToolRegistry: Universal tools (read, write, exec, web_fetch)
    // - AndroidToolRegistry: Android platform tools (tap, screenshot, open_app)

    /**
     * Run Agent with session management - Supports multi-turn conversations
     */
    fun runWithSession(
        userInput: String,
        sessionId: String?,
        application: Application
    ) {
        // Ensure initialized
        if (!::agentLoop.isInitialized) {
            initialize(application)
        }

        val effectiveSessionId = sessionId ?: buildAgentMainSessionKey("main")
        activeSessionId = effectiveSessionId  // Set for block reply broadcasting
        lastBlockReplyText = null  // Reset block reply tracking
        Log.d(TAG, "🆔 [Session] Session ID: $effectiveSessionId")

        // Get or create session
        val session = sessionManager.getOrCreate(effectiveSessionId)
        Log.d(TAG, "📋 [Session] History message count: ${session.messageCount()}")

        // Get history messages (recent 20) and convert to new format
        // Aligned with OpenClaw: limitHistoryTurns (by user turn count)
        // 1. Fetch all session messages
        // 2. Apply limitHistoryTurns with configurable dmHistoryLimit
        // 3. Context pruning in AgentLoop handles the rest
        // Aligned with OpenClaw: getHistoryLimitFromSessionKey → limitHistoryTurns
        // 1. Read dmHistoryLimit from config (per-channel, per-user)
        // 2. If not configured → no truncation (undefined → limitHistoryTurns returns all)
        // 3. AgentLoop's context pruning (soft trim / hard clear) handles oversized context
        val dmHistoryLimit: Int? = try {
            val openClawConfig = configLoader?.loadOpenClawConfig()
            // Check channels.feishu.dmHistoryLimit (or channels.android.dmHistoryLimit)
            openClawConfig?.channels?.feishu?.dmHistoryLimit
        } catch (_: Exception) { null }

        // Aligned with OpenClaw: if dmHistoryLimit not configured, send all history
        // AgentLoop's context pruning (pruneHistoryForContextShare-aligned) handles oversized context
        val allMessages = if (dmHistoryLimit != null && dmHistoryLimit > 0) {
            val raw = session.getRecentMessages(dmHistoryLimit * 4).map { it.toNewMessage() }
            com.xiaomo.androidforclaw.agent.session.HistorySanitizer
                .limitHistoryTurns(raw.toMutableList(), dmHistoryLimit)
        } else {
            session.getRecentMessages(session.messages.size).map { it.toNewMessage() }
        }
        val contextHistory = allMessages
        Log.d(TAG, "📥 [History] total=${session.messages.size} raw=${allMessages.size} → context=${contextHistory.size} (dmHistoryLimit=${dmHistoryLimit ?: "unlimited"})")
        Log.d(TAG, "📥 [Session] Loaded context: ${contextHistory.size} messages")

        if (TextUtils.isEmpty(user)) {
            user = Build.MODEL
        }

        // Cancel previous run for THE SAME session only (prevents stuck state).
        // Different sessions are independent and must not cancel each other.
        sessionJobs[effectiveSessionId]?.let { oldJob ->
            if (oldJob.isActive) {
                Log.w(TAG, "🛑 [Session] Cancelling previous run for session $effectiveSessionId")
                // Only cancel the coroutine job — do NOT call agentLoop.stop() here,
                // because agentLoop is shared and stop() would kill ALL sessions' loops.
                oldJob.cancel()
            }
        }

        // Start coroutine execution (without showing floating window)
        val job = scope.simpleSafeLaunch(
            {
                Log.d(TAG, "========== Agent Session Execution Start ==========")
                Log.d(TAG, "🆔 Session ID: $effectiveSessionId")
                Log.d(TAG, "💬 User input: $userInput")
                Log.d(TAG, "📋 Context messages: ${contextHistory.size}")

                // 1. Build system prompt
                Log.d(TAG, "💬 Building system prompt...")
                var systemPrompt = contextBuilder.buildSystemPrompt(
                    userGoal = userInput,
                    packageName = "",
                    testMode = "chat"
                    // Use default FULL mode to align with OpenClaw
                )

                Log.d(TAG, "✅ System prompt built (${systemPrompt.length} chars)")

                // 2. Broadcast user message
                Log.d(TAG, "📤 [Broadcast] Broadcasting user message...")
                com.xiaomo.androidforclaw.gateway.GatewayServer.broadcastChatMessage(
                    effectiveSessionId, "user", userInput
                )

                // 3. Start progress listening
                val progressJob = launch {
                    agentLoop.progressFlow.collect { update ->
                        handleProgressUpdate(update)
                    }
                }

                // 4. Run AgentLoop (with context history)
                val result = agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = userInput,
                    contextHistory = contextHistory,
                    reasoningEnabled = true  // Reasoning enabled by default
                )

                val cleanFinalContent = com.xiaomo.androidforclaw.util.ReplyTagFilter.strip(
                    ReasoningTagFilter.stripReasoningTags(result.finalContent)
                )
                Log.d(TAG, "========== AgentLoop Complete ==========")
                Log.d(TAG, "Iterations: ${result.iterations}")
                Log.d(TAG, "Final result: ${cleanFinalContent}")

                // 5. Broadcast AI response (skip if already sent via block reply)
                if (cleanFinalContent.isNotEmpty()) {
                    // Update floating window with latest AI response
                    com.xiaomo.androidforclaw.ui.float.SessionFloatWindow.updateLatestMessage(cleanFinalContent)

                    if (lastBlockReplyText?.trim() == cleanFinalContent.trim()) {
                        Log.d(TAG, "✅ Final content matches last block reply, skipping broadcast")
                    } else {
                        Log.d(TAG, "📤 [Broadcast] Broadcasting AI response...")
                        com.xiaomo.androidforclaw.gateway.GatewayServer.broadcastChatMessage(
                            effectiveSessionId, "assistant", cleanFinalContent
                        )
                    }
                }
                lastBlockReplyText = null  // Reset for next run

                // 6. Save messages to session (convert back to legacy format)
                Log.d(TAG, "💾 [Session] Saving messages to session...")
                result.messages.forEach { message ->
                    val sanitizedMessage = if (message.role == "assistant") {
                        message.copy(content = ReasoningTagFilter.stripReasoningTags(message.content))
                    } else message
                    session.addMessage(sanitizedMessage.toLegacyMessage())
                }
                sessionManager.save(session)
                Log.d(TAG, "✅ [Session] Session saved, total messages: ${session.messageCount()}")

                // Cancel progress listening
                progressJob.cancel()

            },
            { exception ->
                Log.e(TAG, "❌ Agent session execution failed", exception)

                // 构建友好的错误消息
                val errorMessage = buildString {
                    append("❌ 执行出错:\n\n")
                    append("**错误**: ${exception.message}\n\n")

                    // 如果是 LLM 异常，添加更详细的信息
                    if (exception is com.xiaomo.androidforclaw.providers.LLMException) {
                        append("**类型**: API 调用失败\n")
                        append("**建议**: 请检查模型配置和 API key\n\n")
                    }

                    // 添加堆栈跟踪 (前500字符)
                    append("**堆栈跟踪**:\n```\n")
                    append(exception.stackTraceToString().take(500))
                    append("\n```")
                }

                // 广播错误消息到聊天界面
                try {
                    com.xiaomo.androidforclaw.gateway.GatewayServer.broadcastChatMessage(
                        effectiveSessionId, "assistant", errorMessage
                    )
                    Log.d(TAG, "📤 [Broadcast] Error message sent to user")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to broadcast error message", e)
                }

                // 保存错误到 session
                try {
                    session.addMessage(com.xiaomo.androidforclaw.providers.LegacyMessage(
                        role = "assistant",
                        content = errorMessage
                    ))
                    sessionManager.save(session)
                    Log.d(TAG, "💾 [Session] Error saved to session")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save error to session", e)
                }
            }
        )
        sessionJobs[effectiveSessionId] = job
        job.invokeOnCompletion { sessionJobs.remove(effectiveSessionId) }
    }

    /**
     * Run test task - New architecture version
     */
    fun run(
        userInput: String,
        application: Application,
        existingRecordId: String? = null,
        existingPackageName: String? = null,
        onSummaryFinished: (() -> Job)? = null
    ) {
        // 确保已初始化
        if (!::agentLoop.isInitialized) {
            initialize(application)
        }

        // 重置状态
        _summaryFinished.value = false

        if (TextUtils.isEmpty(user)) {
            user = Build.MODEL
        }

        // 先回到桌面
        safePressHome()

        // 创建新任务
        val newTaskId = generateTaskId()
        taskDataManager.startNewTask(newTaskId, existingPackageName ?: "")
        currentTaskId = newTaskId
        Log.d(TAG, "========== 新测试任务: $newTaskId ==========")

        // Read mode from openclaw.json instead of MMKV
        val openClawConfig = configLoader.loadOpenClawConfig()
        val testMode = openClawConfig.agent.mode
        Log.d(TAG, "测试模式: $testMode (from openclaw.json)")

        // Set new task as running
        val newTaskData = taskDataManager.getCurrentTaskData()
        newTaskData?.setIsRunning(true)

        // Acquire screen wake lock
        WakeLockManager.acquireScreenWakeLock()
        Log.d(TAG, "已获取屏幕唤醒锁")

        // Cancel previous local task only
        val localSessionKey = "__local__"
        sessionJobs[localSessionKey]?.let { oldJob ->
            if (oldJob.isActive) {
                Log.w(TAG, "🛑 Cancelling previous local task")
                oldJob.cancel()
            }
        }

        // Start coroutine to execute test
        Log.d(TAG, "🚀 About to start coroutine for test task...")
        val job = scope.simpleSafeLaunch(
            {
                Log.d(TAG, "✅ Coroutine started, executing test task...")

                // 1. Build system prompt
                Log.d(TAG, "💬 Step 1: Building system prompt...")
                val packageName = existingPackageName ?: ""
                val systemPrompt = contextBuilder.buildSystemPrompt(
                    userGoal = userInput,
                    packageName = packageName,
                    testMode = testMode
                    // Use default FULL mode to align with OpenClaw
                )

                Log.d(TAG, "✅ System prompt built (${systemPrompt.length} chars)")
                Log.d(TAG, "✅ Estimated Tokens: ~${systemPrompt.length / 4}")

                // Print Skills statistics
                val skillsStats = contextBuilder.getSkillsStatistics()
                if (skillsStats.isNotEmpty()) {
                    Log.d(TAG, "📊 Skills statistics:")
                    skillsStats.lines().forEach { line ->
                        Log.d(TAG, "   $line")
                    }
                }

                // 2. Listen to AgentLoop progress (listen before start)
                Log.d(TAG, "👂 Step 2: Starting progress listening...")
                val progressJob = launch {
                    Log.d(TAG, "✅ Progress listening coroutine started")
                    agentLoop.progressFlow.collect { update ->
                        Log.d(TAG, "📥 Received progress update: ${update.javaClass.simpleName}")
                        handleProgressUpdate(update)
                    }
                }
                Log.d(TAG, "✅ Progress listening set up")

                // 3. Run AgentLoop
                Log.d(TAG, "========== Starting AgentLoop ==========")
                Log.d(TAG, "System prompt length: ${systemPrompt.length}")
                Log.d(TAG, "User input: $userInput")
                Log.d(TAG, "Universal tools: ${toolRegistry.getToolCount()}")
                Log.d(TAG, "Android tools: ${androidToolRegistry.getToolCount()}")

                val result = agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = userInput,
                    reasoningEnabled = true
                )

                Log.d(TAG, "========== AgentLoop Complete ==========")
                Log.d(TAG, "Iterations: ${result.iterations}")
                Log.d(TAG, "Tools used: ${result.toolsUsed.joinToString(", ")}")
                Log.d(TAG, "Final result: ${result.finalContent}")

                // 4. Release resources
                WakeLockManager.releaseScreenWakeLock()
                _summaryFinished.value = true
                onSummaryFinished?.invoke()

                Log.d(TAG, "测试任务执行完成")

            },
            { error ->
                Log.e(TAG, "测试任务执行失败", error)
                LayoutExceptionLogger.log("MainEntryNew#run", error)

                // Release resources
                WakeLockManager.releaseScreenWakeLock()

                _summaryFinished.value = true
            }
        )
        sessionJobs[localSessionKey] = job
        job.invokeOnCompletion { sessionJobs.remove(localSessionKey) }
    }

    private fun emitProgressToUi(type: String, title: String, content: String) {
        _uiProgressFlow.tryEmit(UiProgressEvent(type, title, content))
    }

    /**
     * Handle progress update - Only update floating window display
     */
    private suspend fun handleProgressUpdate(update: ProgressUpdate) {
        Log.d(TAG, "handleProgressUpdate called: ${update.javaClass.simpleName}")
        when (update) {
            is ProgressUpdate.Iteration -> {
                Log.d(TAG, ">>> Iteration ${update.number}")
                SessionFloatWindow.updateSessionInfo(
                    title = "迭代 ${update.number}",
                    content = "正在思考..."
                )
            }

            is ProgressUpdate.Thinking -> {
                Log.d(TAG, "💭 Thinking: 正在处理第 ${update.iteration} 步...")
                SessionFloatWindow.updateSessionInfo(
                    title = "正在思考",
                    content = "正在处理第 ${update.iteration} 步..."
                )
                emitProgressToUi("thinking", "正在思考", "正在处理第 ${update.iteration} 步...")
            }

            is ProgressUpdate.Reasoning -> {
                Log.d(TAG, "🧠 Reasoning (${update.content.length} chars, ${update.llmDuration}ms)")
                SessionFloatWindow.updateSessionInfo(
                    title = "思考完成",
                    content = update.content.take(100) + if (update.content.length > 100) "..." else ""
                )
            }

            is ProgressUpdate.ToolCall -> {
                Log.d(TAG, "🔧 Tool: ${update.name}")

                val argsText = if (update.arguments.isEmpty()) {
                    "无参数"
                } else {
                    update.arguments.entries.joinToString("\n") { (key, value) ->
                        "  • $key: $value"
                    }
                }

                SessionFloatWindow.updateSessionInfo(
                    title = "执行: ${update.name}",
                    content = argsText.take(100)
                )
                emitProgressToUi("tool_call", "执行: ${update.name}", argsText)
            }

            is ProgressUpdate.ToolResult -> {
                Log.d(TAG, "✅ Result: ${update.result.take(100)}, ${update.execDuration}ms")
                SessionFloatWindow.updateSessionInfo(
                    title = "执行完成",
                    content = update.result.take(100) + if (update.result.length > 100) "..." else ""
                )
                emitProgressToUi("tool_result", "执行完成", update.result)
            }

            is ProgressUpdate.IterationComplete -> {
                Log.d(TAG, "🏁 Iteration ${update.number} complete: total=${update.iterationDuration}ms, llm=${update.llmDuration}ms, exec=${update.execDuration}ms")
                SessionFloatWindow.updateSessionInfo(
                    title = "迭代 ${update.number} 完成",
                    content = "耗时: ${update.iterationDuration}ms"
                )
            }

            is ProgressUpdate.ContextOverflow -> {
                Log.w(TAG, "🔄 Context overflow: ${update.message}")
                SessionFloatWindow.updateSessionInfo(
                    title = "上下文超限",
                    content = update.message
                )
            }

            is ProgressUpdate.ContextRecovered -> {
                Log.d(TAG, "✅ Context recovered: ${update.strategy} (attempt ${update.attempt})")
                SessionFloatWindow.updateSessionInfo(
                    title = "上下文已恢复",
                    content = "策略: ${update.strategy}"
                )
            }

            is ProgressUpdate.LoopDetected -> {
                val logLevel = if (update.critical) "🚨" else "⚠️"
                Log.w(TAG, "$logLevel Loop detected: ${update.detector} (count: ${update.count})")
                SessionFloatWindow.updateSessionInfo(
                    title = "${if (update.critical) "严重" else "警告"}: 循环检测",
                    content = "${update.detector}: ${update.count} 次"
                )
            }

            is ProgressUpdate.Error -> {
                Log.e(TAG, "❌ Error: ${update.message}")
                SessionFloatWindow.updateSessionInfo(
                    title = "错误",
                    content = update.message.take(100)
                )
                emitProgressToUi("error", "错误", update.message)
            }

            is ProgressUpdate.BlockReply -> {
                Log.d(TAG, "📤 Block reply: ${update.text.take(200)}")
                SessionFloatWindow.updateSessionInfo(
                    title = "中间回复",
                    content = update.text.take(100) + if (update.text.length > 100) "..." else ""
                )
                emitProgressToUi("block_reply", "中间回复", update.text)
                // For Gateway WebUI sessions, broadcast intermediate text immediately
                lastBlockReplyText = update.text
                activeSessionId?.let { sessionId ->
                    com.xiaomo.androidforclaw.gateway.GatewayServer.broadcastChatMessage(
                        sessionId, "assistant", update.text
                    )
                }
            }

            is ProgressUpdate.SteerMessageInjected -> {
                Log.d(TAG, "🎯 Steer message injected: ${update.content.take(100)}")
                SessionFloatWindow.updateSessionInfo(
                    title = "消息注入",
                    content = update.content.take(100) + if (update.content.length > 100) "..." else ""
                )
            }

            is ProgressUpdate.SubagentSpawned -> {
                Log.i(TAG, "🚀 Subagent spawned: ${update.label} (${update.runId})")
                SessionFloatWindow.updateSessionInfo(
                    title = "子代理已启动",
                    content = update.label
                )
                emitProgressToUi("subagent_spawned", "子代理已启动", update.label)
            }

            is ProgressUpdate.SubagentAnnounced -> {
                Log.i(TAG, "📣 Subagent announced: ${update.label} status=${update.status} (${update.runId})")
                SessionFloatWindow.updateSessionInfo(
                    title = "子代理完成",
                    content = "${update.label}: ${update.status}"
                )
                emitProgressToUi("subagent_announced", "子代理完成", "${update.label}: ${update.status}")
            }

            is ProgressUpdate.Yielded -> {
                Log.i(TAG, "⏸️ Agent loop yielded, waiting for subagent results")
                SessionFloatWindow.updateSessionInfo(
                    title = "等待子代理",
                    content = "已暂停，等待子代理结果..."
                )
                emitProgressToUi("yielded", "等待子代理", "已暂停，等待子代理结果...")
            }

            is ProgressUpdate.ReasoningDelta -> {
                // 流式增量 reasoning — 不更新 float window（太频繁）
            }

            is ProgressUpdate.ContentDelta -> {
                // 流式增量 content — 不更新 float window（太频繁）
            }
        }
    }


    /**
     * Cancel current task
     */
    fun cancelCurrentJob(isRunning: Boolean) {
        Log.d(TAG, "cancelCurrentJob")

        WakeLockManager.releaseScreenWakeLock()

        currentTaskId = null
        taskDataManager.clearCurrentTask()
        // Cancel all session jobs
        sessionJobs.forEach { (id, j) ->
            Log.d(TAG, "Cancelling job for session: $id")
            j.cancel()
        }
        sessionJobs.clear()

        val currentTaskData = taskDataManager.getCurrentTaskData()
        currentTaskData?.setIsRunning(isRunning)

        _summaryFinished.value = true

        // Stop AgentLoop
        if (::agentLoop.isInitialized) {
            agentLoop.stop()
        }
    }

    /**
     * Cancel current task without clearing TaskData
     */
    private fun cancelCurrentJobWithoutClearingTaskData() {
        Log.d(TAG, "cancelCurrentJobWithoutClearingTaskData")

        WakeLockManager.releaseScreenWakeLock()
        // Cancel local task only
        sessionJobs["__local__"]?.cancel()
        sessionJobs.remove("__local__")

        val currentTaskData = taskDataManager.getCurrentTaskData()
        currentTaskData?.setIsRunning(false)
    }

    // ================ Helper Methods ================

    private fun generateTaskId(): String {
        return "task_${System.currentTimeMillis()}"
    }

    private fun safePressHome() {
        try {
            AccessibilityBinderService.serviceInstance?.pressHomeButton()
        } catch (e: Exception) {
            LayoutExceptionLogger.log("MainEntryNew#safePressHome", e)
        }
    }
}
