package com.xiaomo.androidforclaw.core

import android.app.Application
import android.os.Build
import android.text.TextUtils
import android.util.Log
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
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
import com.xiaomo.androidforclaw.service.PhoneAccessibilityService
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger
import com.xiaomo.androidforclaw.util.MMKVKeys
import com.xiaomo.androidforclaw.util.WakeLockManager
import com.xiaomo.androidforclaw.ui.float.SessionFloatWindow
import com.draco.ladb.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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

    // ================ Core Components ================
    private lateinit var application: Application
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var androidToolRegistry: AndroidToolRegistry
    private lateinit var agentLoop: AgentLoop
    private lateinit var contextBuilder: ContextBuilder
    private lateinit var sessionManager: SessionManager
    private lateinit var configLoader: ConfigLoader

    // ================ State Management ================
    var user: String = ""
    private var currentTaskId: String? = null
    private var currentDocId: String? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val taskDataManager: TaskDataManager = TaskDataManager.getInstance()

    // Document sync completion state
    private val _docSyncFinished = MutableStateFlow(false)
    val docSyncFinished = _docSyncFinished.asStateFlow()

    // Test summary completion state
    private val _summaryFinished = MutableStateFlow(false)
    val summaryFinished = _summaryFinished.asStateFlow()

    /**
     * Get SessionManager (for Gateway use)
     */
    fun getSessionManager(): SessionManager? {
        return if (::sessionManager.isInitialized) sessionManager else null
    }

    /**
     * Initialize - Must be called before use
     */
    fun initialize(app: Application) {
        if (::application.isInitialized) {
            Log.w(TAG, "Already initialized")
            return
        }

        application = app
        Log.d(TAG, "Initializing MainEntryNew...")

        try {
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

            // 3. Initialize MemoryManager (memory management)
            val workspacePath = "/sdcard/.androidforclaw/workspace"
            val memoryManager = com.xiaomo.androidforclaw.agent.memory.MemoryManager(workspacePath)

            // 4. Initialize AndroidToolRegistry (Android platform tools)
            androidToolRegistry = AndroidToolRegistry(
                context = application,
                taskDataManager = taskDataManager,
                memoryManager = memoryManager,
                workspacePath = workspacePath
            )
            Log.d(TAG, "✓ AndroidToolRegistry initialized (${androidToolRegistry.getToolCount()} Android tools)")

            // 5. Initialize context builder (OpenClaw style)
            contextBuilder = ContextBuilder(
                context = application,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry
            )
            Log.d(TAG, "✓ ContextBuilder initialized")

            // 5. Initialize session manager (use workspace directory, aligned with OpenClaw)
            val workspaceDir = File(Environment.getExternalStorageDirectory(), ".androidforclaw/workspace")
            if (!workspaceDir.exists()) {
                workspaceDir.mkdirs()
                Log.d(TAG, "Created workspace directory: ${workspaceDir.absolutePath}")
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
                modelRef = null  // Use default model
            )
            Log.d(TAG, "✓ AgentLoop initialized (maxIterations: $maxIterations)")

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

        val effectiveSessionId = sessionId ?: "default"
        Log.d(TAG, "🆔 [Session] Session ID: $effectiveSessionId")

        // Get or create session
        val session = sessionManager.getOrCreate(effectiveSessionId)
        Log.d(TAG, "📋 [Session] History message count: ${session.messageCount()}")

        // Get history messages (recent 20) and convert to new format
        val contextHistory = session.getRecentMessages(20).map { it.toNewMessage() }
        Log.d(TAG, "📥 [Session] Loaded context: ${contextHistory.size} messages")

        if (TextUtils.isEmpty(user)) {
            user = Build.MODEL
        }

        // Start coroutine execution (without showing floating window)
        job = scope.simpleSafeLaunch(
            {
                Log.d(TAG, "========== Agent Session Execution Start ==========")
                Log.d(TAG, "🆔 Session ID: $effectiveSessionId")
                Log.d(TAG, "💬 User input: $userInput")
                Log.d(TAG, "📋 Context messages: ${contextHistory.size}")

                // 1. Build system prompt
                Log.d(TAG, "💬 Building system prompt...")
                val systemPrompt = contextBuilder.buildSystemPrompt(
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

                Log.d(TAG, "========== AgentLoop Complete ==========")
                Log.d(TAG, "Iterations: ${result.iterations}")
                Log.d(TAG, "Final result: ${result.finalContent}")

                // 5. Broadcast AI response
                if (result.finalContent.isNotEmpty()) {
                    Log.d(TAG, "📤 [Broadcast] Broadcasting AI response...")
                    com.xiaomo.androidforclaw.gateway.GatewayServer.broadcastChatMessage(
                        effectiveSessionId, "assistant", result.finalContent
                    )
                }

                // 6. Save messages to session (convert back to legacy format)
                Log.d(TAG, "💾 [Session] Saving messages to session...")
                result.messages.forEach { message ->
                    session.addMessage(message.toLegacyMessage())
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

        // Cancel old task
        cancelCurrentJobWithoutClearingTaskData()

        // Set new task as running
        val newTaskData = taskDataManager.getCurrentTaskData()
        newTaskData?.setIsRunning(true)

        // Acquire screen wake lock
        WakeLockManager.acquireScreenWakeLock()
        Log.d(TAG, "已获取屏幕唤醒锁")

        // Start coroutine to execute test
        Log.d(TAG, "🚀 About to start coroutine for test task...")
        job = scope.simpleSafeLaunch(
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
            }

            is ProgressUpdate.ToolResult -> {
                Log.d(TAG, "✅ Result: ${update.result.take(100)}, ${update.execDuration}ms")
                SessionFloatWindow.updateSessionInfo(
                    title = "执行完成",
                    content = update.result.take(100) + if (update.result.length > 100) "..." else ""
                )
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
        job?.cancel()

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
        job?.cancel()

        val currentTaskData = taskDataManager.getCurrentTaskData()
        currentTaskData?.setIsRunning(false)
    }

    // ================ Helper Methods ================

    private fun generateTaskId(): String {
        return "task_${System.currentTimeMillis()}"
    }

    private fun safePressHome() {
        try {
            PhoneAccessibilityService.Accessibility?.pressHomeButton()
        } catch (e: Exception) {
            LayoutExceptionLogger.log("MainEntryNew#safePressHome", e)
        }
    }
}
