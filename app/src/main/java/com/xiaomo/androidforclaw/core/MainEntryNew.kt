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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 新的 MainEntry - 基于 Nanobot 架构的重构版本
 *
 * 核心改变：
 * 1. 使用 AgentLoop 替代固定流程
 * 2. 使用 LLM Provider (Claude Opus 4.6 + Reasoning)
 * 3. 工具化所有操作
 * 4. 动态决策替代硬编码流程
 */
object MainEntryNew {
    private const val TAG = "MainEntryNew"

    // ================ 核心组件 ================
    private lateinit var application: Application
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var androidToolRegistry: AndroidToolRegistry
    private lateinit var agentLoop: AgentLoop
    private lateinit var contextBuilder: ContextBuilder
    private lateinit var sessionManager: SessionManager

    // ================ 状态管理 ================
    var user: String = ""
    private var currentTaskId: String? = null
    private var currentDocId: String? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val taskDataManager: TaskDataManager = TaskDataManager.getInstance()

    // 文档同步完成状态
    private val _docSyncFinished = MutableStateFlow(false)
    val docSyncFinished = _docSyncFinished.asStateFlow()

    // 测试总结完成状态
    private val _summaryFinished = MutableStateFlow(false)
    val summaryFinished = _summaryFinished.asStateFlow()

    /**
     * 获取 SessionManager (供 Gateway 使用)
     */
    fun getSessionManager(): SessionManager? {
        return if (::sessionManager.isInitialized) sessionManager else null
    }

    /**
     * 初始化 - 必须在使用前调用
     */
    fun initialize(app: Application) {
        if (::application.isInitialized) {
            Log.w(TAG, "Already initialized")
            return
        }

        application = app
        Log.d(TAG, "Initializing MainEntryNew...")

        try {
            // 1. 初始化 LLM Provider (统一 Provider - 支持所有 OpenClaw 兼容 API)
            val llmProvider = com.xiaomo.androidforclaw.providers.UnifiedLLMProvider(application)
            Log.d(TAG, "✓ UnifiedLLMProvider initialized (支持多模型 API)")

            // 2. 初始化 ToolRegistry (通用工具 - 来自 Pi Coding Agent)
            toolRegistry = ToolRegistry(
                context = application,
                taskDataManager = taskDataManager
            )
            Log.d(TAG, "✓ ToolRegistry initialized (${toolRegistry.getToolCount()} universal tools)")

            // 3. 初始化 MemoryManager (记忆管理)
            val workspacePath = "/sdcard/.androidforclaw/workspace"
            val memoryManager = com.xiaomo.androidforclaw.agent.memory.MemoryManager(workspacePath)

            // 4. 初始化 AndroidToolRegistry (Android 平台工具)
            androidToolRegistry = AndroidToolRegistry(
                context = application,
                taskDataManager = taskDataManager,
                memoryManager = memoryManager,
                workspacePath = workspacePath
            )
            Log.d(TAG, "✓ AndroidToolRegistry initialized (${androidToolRegistry.getToolCount()} Android tools)")

            // 5. 初始化上下文构建器 (OpenClaw 风格)
            contextBuilder = ContextBuilder(
                context = application,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry
            )
            Log.d(TAG, "✓ ContextBuilder initialized")

            // 5. 初始化会话管理器
            sessionManager = SessionManager(
                workspace = application.filesDir
            )
            Log.d(TAG, "✓ SessionManager initialized")

            // 6. 初始化上下文管理器 (OpenClaw 对齐的上下文超限处理)
            val contextManager = com.xiaomo.androidforclaw.agent.context.ContextManager(llmProvider)
            Log.d(TAG, "✓ ContextManager initialized")

            // 7. 初始化 AgentLoop
            agentLoop = AgentLoop(
                llmProvider = llmProvider,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                contextManager = contextManager,
                maxIterations = 40,
                modelRef = null  // 使用默认模型
            )
            Log.d(TAG, "✓ AgentLoop initialized")

            Log.d(TAG, "========== Initialization Complete ==========")

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            throw RuntimeException("Failed to initialize MainEntryNew", e)
        }
    }

    // registerAllTools() 已移除
    // 工具现在分为:
    // - ToolRegistry: 通用工具 (read, write, exec, web_fetch)
    // - AndroidToolRegistry: Android 平台工具 (tap, screenshot, open_app)

    /**
     * 使用会话管理运行 Agent - 支持多轮对话
     */
    fun runWithSession(
        userInput: String,
        sessionId: String?,
        application: Application
    ) {
        // 确保已初始化
        if (!::agentLoop.isInitialized) {
            initialize(application)
        }

        val effectiveSessionId = sessionId ?: "default"
        Log.d(TAG, "🆔 [Session] Session ID: $effectiveSessionId")

        // 获取或创建会话
        val session = sessionManager.getOrCreate(effectiveSessionId)
        Log.d(TAG, "📋 [Session] 历史消息数: ${session.messageCount()}")

        // 获取历史消息（最近 20 条）并转换为新格式
        val contextHistory = session.getRecentMessages(20).map { it.toNewMessage() }
        Log.d(TAG, "📥 [Session] 加载上下文: ${contextHistory.size} 条消息")

        if (TextUtils.isEmpty(user)) {
            user = Build.MODEL
        }

        // 启动协程执行 (不显示悬浮窗)
        job = scope.simpleSafeLaunch(
            {
                Log.d(TAG, "========== Agent 会话执行开始 ==========")
                Log.d(TAG, "🆔 Session ID: $effectiveSessionId")
                Log.d(TAG, "💬 User input: $userInput")
                Log.d(TAG, "📋 Context messages: ${contextHistory.size}")

                // 1. 构建系统提示词
                Log.d(TAG, "💬 构建系统提示词...")
                val systemPrompt = contextBuilder.buildSystemPrompt(
                    userGoal = userInput,
                    packageName = "",
                    testMode = "chat"
                )
                Log.d(TAG, "✅ System prompt 构建完成 (${systemPrompt.length} chars)")

                // 2. 广播用户消息
                Log.d(TAG, "📤 [Broadcast] 广播用户消息...")
                com.xiaomo.androidforclaw.gateway.GatewayServer.broadcastChatMessage(
                    effectiveSessionId, "user", userInput
                )

                // 3. 启动进度监听
                val progressJob = launch {
                    agentLoop.progressFlow.collect { update ->
                        handleProgressUpdate(update)
                    }
                }

                // 4. 运行 AgentLoop (传入上下文历史)
                val result = agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = userInput,
                    contextHistory = contextHistory,
                    reasoningEnabled = true  // 默认启用 reasoning
                )

                Log.d(TAG, "========== AgentLoop 完成 ==========")
                Log.d(TAG, "迭代次数: ${result.iterations}")
                Log.d(TAG, "最终结果: ${result.finalContent}")

                // 5. 广播 AI 响应
                if (result.finalContent.isNotEmpty()) {
                    Log.d(TAG, "📤 [Broadcast] 广播 AI 响应...")
                    com.xiaomo.androidforclaw.gateway.GatewayServer.broadcastChatMessage(
                        effectiveSessionId, "assistant", result.finalContent
                    )
                }

                // 6. 保存消息到会话（转换回旧格式）
                Log.d(TAG, "💾 [Session] 保存消息到会话...")
                result.messages.forEach { message ->
                    session.addMessage(message.toLegacyMessage())
                }
                sessionManager.save(session)
                Log.d(TAG, "✅ [Session] 会话已保存，总消息数: ${session.messageCount()}")

                // 取消进度监听
                progressJob.cancel()

            },
            {
                Log.e(TAG, "❌ Agent 会话执行失败", it)
            }
        )
    }

    /**
     * 运行测试任务 - 新架构版本
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

        // 设置用户选择的模式
        val isExplorationMode = mmkv.decodeBool(MMKVKeys.EXPLORATION_MODE.key, false)
        val testMode = if (isExplorationMode) "exploration" else "planning"
        // TODO: updateIsExplorationMode 已删除（旧架构），新架构不需要在 TaskData 中存储此状态
        // taskDataManager.getCurrentTaskData()?.updateIsExplorationMode(isExplorationMode)
        Log.d(TAG, "测试模式: $testMode")

        // 取消旧任务
        cancelCurrentJobWithoutClearingTaskData()

        // 设置新任务为运行状态
        val newTaskData = taskDataManager.getCurrentTaskData()
        newTaskData?.setIsRunning(true)

        // 获取屏幕唤醒锁
        WakeLockManager.acquireScreenWakeLock()
        Log.d(TAG, "已获取屏幕唤醒锁")

        // 启动协程执行测试
        Log.d(TAG, "🚀 即将启动协程执行测试任务...")
        job = scope.simpleSafeLaunch(
            {
                Log.d(TAG, "✅ 协程已启动，开始执行测试任务...")

                // 1. 构建系统提示词
                Log.d(TAG, "💬 步骤1: 构建系统提示词...")
                val packageName = existingPackageName ?: ""
                val systemPrompt = contextBuilder.buildSystemPrompt(
                    userGoal = userInput,
                    packageName = packageName,
                    testMode = testMode
                )

                Log.d(TAG, "✅ System prompt 构建完成 (${systemPrompt.length} chars)")
                Log.d(TAG, "✅ 预估 Tokens: ~${systemPrompt.length / 4}")

                // 打印 Skills 统计信息
                val skillsStats = contextBuilder.getSkillsStatistics()
                if (skillsStats.isNotEmpty()) {
                    Log.d(TAG, "📊 Skills 统计:")
                    skillsStats.lines().forEach { line ->
                        Log.d(TAG, "   $line")
                    }
                }

                // 2. 监听 AgentLoop 进度（在启动前监听）
                Log.d(TAG, "👂 步骤2: 启动进度监听...")
                val progressJob = launch {
                    Log.d(TAG, "✅ 进度监听协程已启动")
                    agentLoop.progressFlow.collect { update ->
                        Log.d(TAG, "📥 收到进度更新: ${update.javaClass.simpleName}")
                        handleProgressUpdate(update)
                    }
                }
                Log.d(TAG, "✅ 进度监听已设置")

                // 3. 运行 AgentLoop
                Log.d(TAG, "========== 启动 AgentLoop ==========")
                Log.d(TAG, "System prompt length: ${systemPrompt.length}")
                Log.d(TAG, "User input: $userInput")
                Log.d(TAG, "Universal tools: ${toolRegistry.getToolCount()}")
                Log.d(TAG, "Android tools: ${androidToolRegistry.getToolCount()}")

                val result = agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = userInput,
                    reasoningEnabled = true
                )

                Log.d(TAG, "========== AgentLoop 完成 ==========")
                Log.d(TAG, "迭代次数: ${result.iterations}")
                Log.d(TAG, "使用工具: ${result.toolsUsed.joinToString(", ")}")
                Log.d(TAG, "最终结果: ${result.finalContent}")

                // 4. 释放资源
                WakeLockManager.releaseScreenWakeLock()
                _summaryFinished.value = true
                onSummaryFinished?.invoke()

                Log.d(TAG, "测试任务执行完成")

            },
            { error ->
                Log.e(TAG, "测试任务执行失败", error)
                LayoutExceptionLogger.log("MainEntryNew#run", error)

                // 释放资源
                WakeLockManager.releaseScreenWakeLock()

                _summaryFinished.value = true
            }
        )
    }

    /**
     * 处理进度更新 - 仅更新悬浮窗显示
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
     * 取消当前任务
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

        // 停止 AgentLoop
        if (::agentLoop.isInitialized) {
            agentLoop.stop()
        }
    }

    /**
     * 取消当前任务但不清理 TaskData
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
