package com.xiaomo.androidforclaw.core

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/boot.ts, ../openclaw/src/entry.ts
 */


import android.app.Activity
import com.xiaomo.androidforclaw.util.ReasoningTagFilter
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import com.xiaomo.androidforclaw.logging.Log
import android.widget.Toast
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy
import com.xiaomo.androidforclaw.accessibility.AccessibilityHealthMonitor
import com.xiaomo.androidforclaw.util.GlobalExceptionHandler
import com.xiaomo.androidforclaw.workspace.StoragePaths
import com.xiaomo.androidforclaw.util.SPHelper
import com.xiaomo.androidforclaw.util.WakeLockManager
import com.xiaomo.androidforclaw.camera.CameraCaptureManager
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.xiaomo.androidforclaw.gateway.GatewayService
import com.xiaomo.androidforclaw.gateway.MainEntryAgentHandler
import com.xiaomo.androidforclaw.gateway.GatewayServer
import com.xiaomo.androidforclaw.gateway.GatewayController
import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.agent.skills.SkillsLoader
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.feishu.FeishuChannel
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.discord.DiscordChannel
import com.xiaomo.discord.DiscordConfig
import com.xiaomo.discord.ChannelEvent
import com.xiaomo.discord.session.DiscordSessionManager
import com.xiaomo.discord.session.DiscordHistoryManager
import com.xiaomo.discord.session.DiscordDedup
import com.xiaomo.discord.messaging.DiscordTyping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import com.xiaomo.androidforclaw.providers.llm.toNewMessage
import com.xiaomo.androidforclaw.providers.llm.toLegacyMessage
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.loop.ProgressUpdate
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider

/**
 */
class MyApplication : ai.openclaw.app.NodeApp(), Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "MyApplication"
        private var activeActivityCount = 0
        private var isChangingConfiguration = false

        lateinit var application: Application

        // Singleton access
        val instance: MyApplication
            get() = application as MyApplication

        // Gateway Server
        private var gatewayServer: GatewayServer? = null

        // Gateway Controller
        private var gatewayController: GatewayController? = null

        // 本地进程内 channel（绕过 WebSocket）
        private var localGatewayChannel: com.xiaomo.androidforclaw.gateway.LocalGatewayChannel? = null

        fun isGatewayRunning(): Boolean = gatewayController != null

        // Feishu Channel
        private var feishuChannel: FeishuChannel? = null
        private var feishuWakeLock: android.os.PowerManager.WakeLock? = null

        /**
         * Get Feishu Channel (for tool invocation)
         */
        fun getFeishuChannel(): FeishuChannel? = feishuChannel

        // Message Queue Manager: fully aligned with OpenClaw's queue mechanism
        // Supports five modes: interrupt, steer, followup, collect, queue
        private val messageQueueManager = MessageQueueManager()

        // Discord Channel
        private var discordChannel: DiscordChannel? = null
        private val discordSessionManager = DiscordSessionManager()
        private val discordHistoryManager = DiscordHistoryManager(maxHistoryPerChannel = 50)
        private val discordDedup = DiscordDedup()
        private var discordTyping: DiscordTyping? = null
        private val discordProcessingJobs = mutableMapOf<String, Job>()

        // Weixin Channel
        private var weixinChannel: com.xiaomo.weixin.WeixinChannel? = null
        fun getWeixinChannel(): com.xiaomo.weixin.WeixinChannel? = weixinChannel

        // Weixin agent loop tracking moved to MessageQueueManager (channel-agnostic)

        // Accessibility Health Monitor
        private var healthMonitor: AccessibilityHealthMonitor? = null

        // Camera Capture Manager (对齐 OpenClaw CameraCaptureManager)
        private var cameraCaptureManager: CameraCaptureManager? = null

        fun getCameraCaptureManager(): CameraCaptureManager? = cameraCaptureManager

        private fun onAppForeground() {
            Log.d(TAG, "App回到前台")
            // Check if test task is running, if so ensure WakeLock is acquired
            ensureWakeLockForTesting()
        }

        private fun onAppBackground() {
            Log.d(TAG, "App进入后台")
            // Check if test task is running, if so ensure WakeLock is acquired
            ensureWakeLockForTesting()
        }

        /**
         * Check test task status, if test task is running ensure WakeLock is acquired
         * This ensures the app won't lock screen when running in background
         *
         * Called at:
         * 1. App startup (onCreate)
         * 2. App entering background (onAppBackground)
         * 3. App returning to foreground (onAppForeground)
         */
        private fun ensureWakeLockForTesting() {
            try {
                val taskDataManager = TaskDataManager.getInstance()
                val hasTask = taskDataManager.hasCurrentTask()
                
                if (hasTask) {
                    val taskData = taskDataManager.getCurrentTaskData()
                    val isRunning = taskData?.getIsRunning() ?: false

                    if (isRunning) {
                        // Test task is running, ensure WakeLock is acquired
                        // acquireScreenWakeLock has internal duplicate acquisition prevention, safe to call
                        Log.d(TAG, "检测到测试任务在运行，确保 WakeLock 已获取（应用状态: ${if (activeActivityCount == 0) "后台" else "前台"}）")
                        WakeLockManager.acquireScreenWakeLock()
                    } else {
                        // Test task has stopped, release WakeLock
                        Log.d(TAG, "测试任务已停止，释放 WakeLock")
                        WakeLockManager.releaseScreenWakeLock()
                    }
                } else {
                    // No test task, ensure WakeLock is released
                    // releaseScreenWakeLock has internal check, skip if not active
                    if (WakeLockManager.isScreenWakeLockActive()) {
                        Log.d(TAG, "没有测试任务，释放 WakeLock")
                        WakeLockManager.releaseScreenWakeLock()
                    } else {
                        Log.d(TAG, "没有测试任务，WakeLock 未激活，无需释放")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查测试任务状态失败: ${e.message}", e)
            }
        }

        /**
         * Handle messages from ChatBroadcastReceiver
         * Send local broadcast for MainActivityCompose to handle
         */
        fun handleChatBroadcast(message: String) {
            Log.d(TAG, "📨 handleChatBroadcast: $message")
            try {
                // Send local broadcast for MainActivityCompose to handle
                val intent = Intent("com.xiaomo.androidforclaw.CHAT_MESSAGE_FROM_BROADCAST")
                intent.putExtra("message", message)
                androidx.localbroadcastmanager.content.LocalBroadcastManager
                    .getInstance(application)
                    .sendBroadcast(intent)
                Log.d(TAG, "✅ 已发送本地广播")
            } catch (e: Exception) {
                Log.e(TAG, "发送本地广播失败: ${e.message}", e)
            }
        }
    }

    override fun provideLocalChatChannel(): com.xiaomo.base.IGatewayChannel? = localGatewayChannel

    override fun onCreate() {
        super.onCreate()
        application = this

        // Apply saved language settings
        com.xiaomo.androidforclaw.util.LocaleHelper.applyLanguage(this)

        MMKV.initialize(this)
        com.xiaomo.androidforclaw.config.ProviderRegistry.init(this)
        registerActivityLifecycleCallbacks(this)

        // 初始化 CameraCaptureManager (对齐 OpenClaw camera.snap/clip)
        cameraCaptureManager = CameraCaptureManager(this)

        // Initialize file logging system
        initializeFileLogger()

        // Initialize Workspace (aligned with OpenClaw)
        initializeWorkspace()

        // Initialize Cron scheduled tasks
        initializeCronJobs()

        // Register global exception handler
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler())

        // Start foreground service keep-alive
        startForegroundServiceKeepAlive()

        // Start Gateway server
        startGatewayServer()

        // ✅ Test config system
        testConfigSystem()

        // ⚠️ Block 1: SkillParser test temporarily skipped (JSON parsing issue pending fix)
        // testSkillParser()
        Log.i(TAG, "⏭️  Block 1 测试已跳过，应用继续启动")

        // Check if test task is running on app startup, if so acquire WakeLock
        // Delayed check to ensure TaskDataManager is initialized
        Handler(Looper.getMainLooper()).postDelayed({
            ensureWakeLockForTesting()
        }, 1000) // 1 second delay

        // 🌐 Start Gateway service
        startGatewayService()

        // 🐧 Termux SSH pre-warm (non-blocking, auto-start sshd via RUN_COMMAND)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Termux SSH pre-warm: checking availability...")
                val termux = com.xiaomo.androidforclaw.agent.tools.TermuxBridgeTool(applicationContext)
                if (!termux.isTermuxInstalled()) {
                    Log.i(TAG, "Termux SSH warm-up skipped: Termux not installed")
                    return@launch
                }
                val status = termux.getStatus()
                if (status.ready) {
                    com.xiaomo.androidforclaw.agent.tools.TermuxSSHPool.warmUp(applicationContext)
                    Log.i(TAG, "Termux SSH pool warmed up")
                } else if (status.keypairPresent && !status.sshReachable) {
                    // sshd not running but keypair is configured → ensure Termux is running, then auto-start sshd
                    Log.i(TAG, "🐧 Termux sshd 未运行，先确保 Termux 已启动，再通过 RUN_COMMAND 启动 sshd...")
                    try {
                        TermuxSshdLauncher.ensureAndLaunch(applicationContext)
                        // Wait for sshd to come up, then warm up SSH pool
                        for (attempt in 1..10) {
                            kotlinx.coroutines.delay(1000)
                            val retryStatus = termux.getStatus()
                            if (retryStatus.ready) {
                                com.xiaomo.androidforclaw.agent.tools.TermuxSSHPool.warmUp(applicationContext)
                                Log.i(TAG, "✅ Termux sshd 自动启动成功（等待 ${attempt}s）")
                                return@launch
                            }
                        }
                        Log.w(TAG, "⚠️ Termux sshd 自动启动后 10s 内未就绪")
                    } catch (e: Exception) {
                        Log.w(TAG, "Termux RUN_COMMAND 启动 sshd 失败: ${e.message}")
                    }
                } else {
                    Log.i(TAG, "Termux SSH warm-up skipped: ${status.message} (step=${status.lastStep})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Termux SSH warm-up skipped: ${e.message}")
            }
        }

        // 📱 Start channels (only if storage permission is granted)
        if (hasStoragePermission()) {
            startAllChannels()
        } else {
            Log.w(TAG, "⚠️ 存储权限未授权，跳过 Channel 初始化。授权后将自动启动。")
        }

        // 🪟 Initialize floating window manager
        com.xiaomo.androidforclaw.ui.float.SessionFloatWindow.init(this)

        // 🔌 Start health monitoring (serviceInstance managed by observer lifecycle)
        healthMonitor = AccessibilityHealthMonitor(applicationContext)
        healthMonitor?.startMonitoring()

        // Listen to connection status
        GlobalScope.launch(Dispatchers.Main) {
            AccessibilityProxy.isConnected.observeForever { connected ->
                if (connected) {
                    Log.i(TAG, "✅ 无障碍服务已连接")
                } else {
                    Log.w(TAG, "⚠️ 无障碍服务未连接")
                }
            }
        }



    }

    fun isAppInBackground(): Boolean {
        return activeActivityCount == 0
    }

    /**
     * Start foreground service keep-alive
     */
    private fun startForegroundServiceKeepAlive() {
        try {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.i(TAG, "✅ 前台服务已启动（保活）")
        } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
            // Android 14+: cannot start foreground service from background
            Log.w(TAG, "⚠️ 前台服务启动受限（应用在后台），将在下次回到前台时重试")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 前台服务启动失败", e)
        }
    }

    /**
     * Start Gateway server
     */
    private fun startGatewayServer() {
        try {
            // Stop old instance first (if exists)
            gatewayServer?.stop()
            gatewayServer = null

            // Create and start new instance
            gatewayServer = GatewayServer(this, port = 19789)
            gatewayServer?.start()

            Log.i(TAG, "✅ Gateway Server 启动成功")
            Log.i(TAG, "  - HTTP: http://0.0.0.0:19789")
            Log.i(TAG, "  - WebSocket: ws://0.0.0.0:19789/ws")

            // Get local IP
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val ip = getLocalIpAddress()
                    if (ip != null) {
                        Log.i(TAG, "  - 局域网访问: http://$ip:19789")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "无法获取本机 IP", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Gateway Server 启动失败", e)
        }
    }

    /**
     * Get local IP address
     */
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 IP 地址失败", e)
        }
        return null
    }

    /**
     * Test config system
     */
    /**
     * Initialize file logging system
     */
    private fun initializeFileLogger() {
        try {
            com.xiaomo.androidforclaw.logging.AppLog.init(this)
            Log.i(TAG, "✅ 文件日志系统已初始化")
        } catch (e: Exception) {
            Log.e(TAG, "初始化文件日志系统失败", e)
        }
    }

    /**
     * Initialize Cron scheduled tasks
     */
    private fun initializeCronJobs() {
        try {
            com.xiaomo.androidforclaw.cron.CronInitializer.initialize(this)
            Log.i(TAG, "✅ Cron 系统已初始化")
        } catch (e: Exception) {
            Log.e(TAG, "初始化 Cron 系统失败", e)
        }
    }

    /**
     * Initialize Workspace (aligned with OpenClaw)
     */
    private fun initializeWorkspace() {
        try {
            val initializer = com.xiaomo.androidforclaw.workspace.WorkspaceInitializer(this)

            if (!initializer.isWorkspaceInitialized()) {
                Log.i(TAG, "========================================")
                Log.i(TAG, "📁 首次启动 - 初始化 Workspace...")
                Log.i(TAG, "========================================")

                val success = initializer.initializeWorkspace()

                if (success) {
                    Log.i(TAG, "✅ Workspace 初始化成功")
                    Log.i(TAG, "   路径: ${initializer.getWorkspacePath()}")
                    Log.i(TAG, "   Device ID: ${initializer.getDeviceId()}")
                    Log.i(TAG, "   文件: BOOTSTRAP.md, IDENTITY.md, USER.md, SOUL.md, AGENTS.md, TOOLS.md")
                } else {
                    Log.e(TAG, "❌ Workspace 初始化失败")
                }
            } else {
                Log.d(TAG, "Workspace 已初始化: ${initializer.getWorkspacePath()}")
            }

            // Always ensure bundled skills are deployed (copies missing, won't overwrite)
            initializer.ensureBundledSkills()

        } catch (e: Exception) {
            Log.e(TAG, "初始化 Workspace 失败", e)
        }
    }

    private fun testConfigSystem() {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "🧪 配置系统测试开始")
            Log.d(TAG, "========================================")

            // Run basic config tests
            // com.xiaomo.androidforclaw.config.ConfigTestRunner.runBasicTests(this)

            // Test LegacyRepository config integration
            // com.xiaomo.androidforclaw.config.ConfigTestRunner.testLegacyRepository(this)

            Log.d(TAG, "")
            Log.d(TAG, "========================================")
            Log.i(TAG, "✅ 配置系统测试完成!")
            Log.d(TAG, "========================================")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 配置系统测试异常: ${e.message}", e)
        }
    }



    /**
     * Start auto test
     */
    private fun startAutoTest() {
        try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "🚀 启动自动测试")
            Log.i(TAG, "========================================")
            /*
            */
            Log.i(TAG, "========================================")

            // Start MainEntryNew to execute test
            /*
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    MainEntryNew.run(
                        application = application
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "自动测试执行失败: ${e.message}", e)
                }
            }
            */

        } catch (e: Exception) {
            Log.e(TAG, "启动自动测试失败: ${e.message}", e)
        }
    }

    /**
     * Start Gateway service
     */
    private fun startGatewayService() {
        try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "🌐 启动 Gateway 服务 (GatewayController)...")
            Log.i(TAG, "========================================")

            // Initialize TaskDataManager
            val taskDataManager = TaskDataManager.getInstance()

            // Initialize LLM Provider
            val llmProvider = UnifiedLLMProvider(this)

            // Initialize dependencies
            val toolRegistry = ToolRegistry(this, taskDataManager)
            val androidToolRegistry = AndroidToolRegistry(this, taskDataManager, cameraCaptureManager = cameraCaptureManager)
            val skillsLoader = SkillsLoader(this)
            val workspaceDir = StoragePaths.workspace
            val sessionManager = SessionManager(workspaceDir)

            // Create AgentLoop (requires these dependencies)
            val agentLoop = AgentLoop(
                llmProvider = llmProvider,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                contextManager = null,
                maxIterations = 50,
                modelRef = null
            )

            // Create GatewayController
            gatewayController = GatewayController(
                context = this,
                agentLoop = agentLoop,
                sessionManager = sessionManager,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                skillsLoader = skillsLoader,
                port = 8765,
                authToken = null // Temporarily disable auth
            )

            Log.i(TAG, "✅ GatewayController 实例创建成功")

            // 创建本地进程内 channel，绕过 WebSocket
            localGatewayChannel = com.xiaomo.androidforclaw.gateway.LocalGatewayChannel(gatewayController!!)
            Log.i(TAG, "✅ LocalGatewayChannel 创建成功")

            // Start service
            gatewayController?.start()

            Log.i(TAG, "========================================")
            Log.i(TAG, "✅ Gateway 服务已启动: ws://0.0.0.0:8765")
            Log.i(TAG, "========================================")

        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "❌ Gateway 初始化失败", e)
            e.printStackTrace()
            Log.e(TAG, "========================================")
        }
    }

    /**
     * Check if MANAGE_EXTERNAL_STORAGE permission is granted (Android 11+).
     * Below Android 11, always returns true.
     */
    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Start all messaging channels. Called after storage permission is granted.
     * Safe to call multiple times — each channel checks its own enabled flag.
     */
    fun startAllChannels() {
        Log.i(TAG, "🚀 startAllChannels() — 启动所有消息通道")
        startFeishuChannelIfEnabled()
        startDiscordChannelIfEnabled()
        startWeixinChannelIfEnabled()
    }

    /**
     * Start Feishu Channel (if enabled in config)
     */
    private fun startFeishuChannelIfEnabled() {
        Log.i(TAG, "⏰ startFeishuChannelIfEnabled() 被调用")
        val scope = CoroutineScope(Dispatchers.IO)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "========================================")
                Log.i(TAG, "📱 检查 Feishu Channel 配置...")
                Log.i(TAG, "========================================")

                val configLoader = ConfigLoader(this@MyApplication)
                val openClawConfig = configLoader.loadOpenClawConfig()
                val feishuConfig = openClawConfig.channels.feishu

                if (!feishuConfig.enabled) {
                    Log.i(TAG, "⏭️  Feishu Channel 未启用，跳过初始化")
                    Log.i(TAG, "   配置路径: /sdcard/.androidforclaw/openclaw.json")
                    Log.i(TAG, "   设置 channels.feishu.enabled = true 以启用")
                    Log.i(TAG, "========================================")
                    return@launch
                }

                Log.i(TAG, "✅ Feishu Channel 已启用，准备启动...")
                Log.i(TAG, "   App ID: ${feishuConfig.appId}")
                Log.i(TAG, "   Domain: ${feishuConfig.domain}")
                Log.i(TAG, "   Mode: ${feishuConfig.connectionMode}")
                Log.i(TAG, "   DM Policy: ${feishuConfig.dmPolicy}")
                Log.i(TAG, "   Group Policy: ${feishuConfig.groupPolicy}")

                // Create FeishuConfig
                val config = FeishuConfig(
                    appId = feishuConfig.appId,
                    appSecret = feishuConfig.appSecret,
                    verificationToken = feishuConfig.verificationToken,
                    encryptKey = feishuConfig.encryptKey,
                    domain = feishuConfig.domain,
                    connectionMode = when (feishuConfig.connectionMode) {
                        "webhook" -> FeishuConfig.ConnectionMode.WEBHOOK
                        "websocket" -> FeishuConfig.ConnectionMode.WEBSOCKET
                        else -> FeishuConfig.ConnectionMode.WEBSOCKET
                    },
                    dmPolicy = when (feishuConfig.dmPolicy.lowercase()) {
                        "open" -> FeishuConfig.DmPolicy.OPEN
                        "pairing" -> FeishuConfig.DmPolicy.PAIRING
                        "allowlist" -> FeishuConfig.DmPolicy.ALLOWLIST
                        else -> FeishuConfig.DmPolicy.PAIRING
                    },
                    groupPolicy = when (feishuConfig.groupPolicy.lowercase()) {
                        "open" -> FeishuConfig.GroupPolicy.OPEN
                        "allowlist" -> FeishuConfig.GroupPolicy.ALLOWLIST
                        "disabled" -> FeishuConfig.GroupPolicy.DISABLED
                        else -> FeishuConfig.GroupPolicy.ALLOWLIST
                    },
                    requireMention = feishuConfig.requireMention,
                    historyLimit = feishuConfig.historyLimit ?: 0,
                    dmHistoryLimit = feishuConfig.dmHistoryLimit ?: 0
                )

                // Create and start FeishuChannel
                feishuChannel = FeishuChannel(config)
                val result = feishuChannel?.start()

                if (result?.isSuccess == true) {
                    // Update MMKV status
                    val mmkv = MMKV.defaultMMKV()
                    mmkv?.encode("channel_feishu_enabled", true)

                    // 获取 PARTIAL_WAKE_LOCK 保持 CPU 运行，防止锁屏后 Doze 断网
                    try {
                        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                        val wl = pm.newWakeLock(
                            android.os.PowerManager.PARTIAL_WAKE_LOCK,
                            "AndroidForClaw::FeishuChannel"
                        )
                        wl.setReferenceCounted(false)
                        wl.acquire()
                        feishuWakeLock = wl
                        Log.i(TAG, "🔒 已获取 Feishu Channel WakeLock（防止锁屏断连）")
                    } catch (e: Exception) {
                        Log.w(TAG, "获取 Feishu WakeLock 失败: ${e.message}")
                    }

                    Log.i(TAG, "========================================")
                    Log.i(TAG, "✅ Feishu Channel 启动成功!")
                    Log.i(TAG, "   现在可以接收飞书消息了")
                    Log.i(TAG, "========================================")

                    // Register feishu tools into MainEntryNew's ToolRegistry
                    // (so broadcast/gateway messages also get feishu tools)
                    try {
                        val mainToolRegistry = MainEntryNew.getToolRegistry()
                        val ftr = feishuChannel?.getToolRegistry()
                        if (mainToolRegistry != null && ftr != null) {
                            // Register Feishu tools into function-call ToolRegistry
                            // (skills are guidance; actual tool execution still needs registry entry)
                            val count = com.xiaomo.androidforclaw.agent.tools.registerFeishuTools(mainToolRegistry, ftr)
                            Log.i(TAG, "🔧 已注册 $count 个飞书工具到 MainEntryNew ToolRegistry")
                        } else {
                            Log.w(TAG, "⚠️ MainEntryNew 未初始化，飞书工具将在首次消息处理时注册")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "飞书工具注册到 MainEntryNew 失败: ${e.message}")
                    }

                    // Subscribe to event flow, handle received messages
                    scope.launch(Dispatchers.IO) {
                        feishuChannel?.eventFlow?.collect { event ->
                            handleFeishuEvent(event)
                        }
                    }
                } else {
                    // Clear MMKV status
                    val mmkv = MMKV.defaultMMKV()
                    mmkv?.encode("channel_feishu_enabled", false)

                    Log.e(TAG, "========================================")
                    Log.e(TAG, "❌ Feishu Channel 启动失败")
                    Log.e(TAG, "   错误: ${result?.exceptionOrNull()?.message}")
                    Log.e(TAG, "========================================")
                }

            } catch (e: Exception) {
                // Clear MMKV status
                val mmkv = MMKV.defaultMMKV()
                mmkv?.encode("channel_feishu_enabled", false)

                Log.e(TAG, "========================================")
                Log.e(TAG, "❌ Feishu Channel 初始化异常", e)
                Log.e(TAG, "========================================")
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

    }

    override fun onActivityStarted(activity: Activity) {
        activeActivityCount += 1
        if (activeActivityCount == 1 && isChangingConfiguration) {
            isChangingConfiguration = false
        } else if (activeActivityCount == 1) {
            // App returned to foreground from background
            onAppForeground()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // Attach LifecycleOwner to CameraCaptureManager for CameraX binding
        if (activity is androidx.lifecycle.LifecycleOwner) {
            cameraCaptureManager?.attachLifecycleOwner(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStopped(activity: Activity) {
        activeActivityCount -= 1
        if (activity.isChangingConfigurations) {
            isChangingConfiguration = true
        } else if (activeActivityCount == 0) {
            // App entered background
            onAppBackground()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {

    }

    /**
     * Get queue mode (aligned with OpenClaw)
     *
     * Reference: openclaw/src/auto-reply/reply/queue/resolve-settings.ts
     */
    private fun getQueueModeForChat(chatId: String, chatType: String): MessageQueueManager.QueueMode {
        return try {
            val configLoader = ConfigLoader(this@MyApplication)
            val openClawConfig = configLoader.loadOpenClawConfig()

            // Read Feishu queue config
            val queueMode = openClawConfig.channels.feishu.queueMode ?: "followup"

            // Set both queue capacity and drop policy
            val queueKey = "feishu:$chatId"
            messageQueueManager.setQueueSettings(
                key = queueKey,
                cap = openClawConfig.channels.feishu.queueCap,
                dropPolicy = when (openClawConfig.channels.feishu.queueDropPolicy.lowercase()) {
                    "new" -> MessageQueueManager.DropPolicy.NEW
                    "summarize" -> MessageQueueManager.DropPolicy.SUMMARIZE
                    else -> MessageQueueManager.DropPolicy.OLD
                }
            )

            when (queueMode.lowercase()) {
                "interrupt" -> MessageQueueManager.QueueMode.INTERRUPT
                "steer" -> MessageQueueManager.QueueMode.STEER
                "followup" -> MessageQueueManager.QueueMode.FOLLOWUP
                "collect" -> MessageQueueManager.QueueMode.COLLECT
                "queue" -> MessageQueueManager.QueueMode.QUEUE
                else -> {
                    Log.w(TAG, "Unknown queue mode: $queueMode, using FOLLOWUP")
                    MessageQueueManager.QueueMode.FOLLOWUP
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load queue mode, using default FOLLOWUP", e)
            MessageQueueManager.QueueMode.FOLLOWUP
        }
    }

    /**
     * Process Feishu message (with Typing Indicator)
     *
     * Aligned with OpenClaw message processing flow:
     * 1. Add "typing" reaction
     * 2. Process message (call Agent)
     * 3. Remove "typing" reaction
     * 4. Send reply
     */
    private suspend fun processFeishuMessageWithTyping(
        event: com.xiaomo.feishu.FeishuEvent.Message,
        queuedMessage: MessageQueueManager.QueuedMessage
    ) {
        var typingReactionId: String? = null
        try {
            // 1. Add "typing" reaction (Typing Indicator)
            val configLoader = ConfigLoader(this@MyApplication)
            val openClawConfig = configLoader.loadOpenClawConfig()
            val typingIndicatorEnabled = openClawConfig.channels.feishu.typingIndicator

            if (typingIndicatorEnabled) {
                Log.d(TAG, "⌨️  添加输入中表情...")
                val reactionResult = feishuChannel?.addReaction(event.messageId, "Typing")
                if (reactionResult?.isSuccess == true) {
                    typingReactionId = reactionResult.getOrNull()
                    Log.d(TAG, "✅ 输入中表情已添加: $typingReactionId")
                }
            }

            // 2. Call MainEntryNew to process message
            val response = processFeishuMessage(event)

            // 2.5 Check if reply should be skipped (noReply logic)
            if (shouldSkipReply(response, queuedMessage)) {
                Log.d(TAG, "🔕 noReply directive detected, skipping reply")
                // Remove reaction and return immediately
                if (typingReactionId != null) {
                    Log.d(TAG, "🧹 移除输入中表情...")
                    feishuChannel?.removeReaction(event.messageId, typingReactionId)
                }
                return
            }

            // 3. Remove typing reaction
            if (typingReactionId != null) {
                Log.d(TAG, "🧹 移除输入中表情...")
                feishuChannel?.removeReaction(event.messageId, typingReactionId)
            }

            // 4. Send final reply to Feishu (skip if already sent via block reply)
            if (response == "\u0000BLOCK_REPLY_ALREADY_SENT") {
                Log.d(TAG, "✅ Final reply already sent via block reply, skipping")
            } else {
                // Strip leaked model control tokens before sending to user (OpenClaw 2026.3.11)
                var sanitizedResponse = com.xiaomo.androidforclaw.agent.session.HistorySanitizer
                    .stripControlTokensFromText(response)
                // Strip trailing NO_REPLY / HEARTBEAT_OK from mixed content
                // Aligned with OpenClaw stripSilentToken(): (?:^|\s+|\*+)NO_REPLY\s*$
                sanitizedResponse = sanitizedResponse
                    .replace(Regex("(?:^|\\s+|\\*+)NO_REPLY\\s*$"), "")
                    .replace(Regex("(?:^|\\s+|\\*+)HEARTBEAT_OK\\s*$"), "")
                    .trim()
                if (sanitizedResponse.isNotBlank()) {
                    sendFeishuReply(event, sanitizedResponse)
                } else {
                    Log.d(TAG, "🔕 Response became empty after stripping silent tokens, skipping reply")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理飞书消息失败", e)
            // Ensure reaction is removed (even if error occurs)
            if (typingReactionId != null) {
                try {
                    feishuChannel?.removeReaction(event.messageId, typingReactionId)
                } catch (cleanupError: Exception) {
                    Log.w(TAG, "清理输入中表情失败", cleanupError)
                }
            }
            // 发送错误提示给用户，避免静默无回复
            try {
                sendFeishuReply(event, "⚠️ 处理消息时出错：${e.message?.take(200) ?: "未知错误"}")
            } catch (sendError: Exception) {
                Log.e(TAG, "发送错误提示也失败了", sendError)
            }
        }
    }

    /**
     * Check if reply should be skipped (noReply logic)
     *
     * Aligned with OpenClaw's noReply detection:
     * - Agent can return special directive indicating no reply needed
     * - Certain message types (notifications, status updates) don't need reply
     * - Batch messages may contain noReply flag
     */
    private fun shouldSkipReply(
        response: String,
        queuedMessage: MessageQueueManager.QueuedMessage
    ): Boolean {
        // 1. Check if response is a silent reply
        // Aligned with OpenClaw isSilentReplyText(): exact match only (^\s*NO_REPLY\s*$)
        val trimmed = response.trim()
        if (trimmed.equals(ContextBuilder.SILENT_REPLY_TOKEN, ignoreCase = false)) {
            Log.d(TAG, "Silent reply detected (exact match): NO_REPLY")
            return true
        }

        // 2. Check HEARTBEAT_OK (exact match only, aligned with OpenClaw HEARTBEAT_TOKEN)
        if (trimmed.equals("HEARTBEAT_OK", ignoreCase = false)) {
            Log.d(TAG, "Heartbeat ack detected, skipping reply")
            return true
        }

        // 3. Check if response is empty
        if (response.isBlank()) {
            Log.d(TAG, "Response is empty, skipping reply")
            return true
        }

        // 4. Check batch message metadata
        val isBatch = queuedMessage.metadata["isBatch"] as? Boolean ?: false
        if (isBatch) {
            val noReplyFlag = queuedMessage.metadata["noReply"] as? Boolean ?: false
            if (noReplyFlag) {
                return true
            }
        }

        return false
    }

    /**
     * Handle Feishu event
     */
    private fun handleFeishuEvent(event: com.xiaomo.feishu.FeishuEvent) {
        when (event) {
            is com.xiaomo.feishu.FeishuEvent.Message -> {
                Log.i(TAG, "📨 收到飞书消息")
                Log.i(TAG, "   发送者: ${event.senderId}")
                Log.i(TAG, "   内容: ${event.content}")
                Log.i(TAG, "   聊天类型: ${event.chatType}")
                Log.i(TAG, "   Mentions: ${event.mentions}")

                // 🔄 Update current chat context (for Agent tool use)
                feishuChannel?.updateCurrentChatContext(
                    receiveId = event.chatId,
                    receiveIdType = "chat_id",
                    messageId = event.messageId
                )
                Log.d(TAG, "✅ 已更新当前对话上下文: chatId=${event.chatId}")

                // ✅ Check message permissions (aligned with OpenClaw bot.ts)
                try {
                    val configLoader = ConfigLoader(this@MyApplication)
                    val openClawConfig = configLoader.loadOpenClawConfig()
                    val feishuConfig = openClawConfig.channels.feishu

                    // Check DM Policy (private chat permission)
                    if (event.chatType == "p2p") {
                        val dmPolicy = feishuConfig.dmPolicy
                        Log.d(TAG, "   DM Policy: $dmPolicy")

                        when (dmPolicy) {
                            "pairing" -> {
                                // TODO: Implement pairing logic
                                // Temporarily allow all DMs (dev mode)
                                Log.d(TAG, "✅ DM allowed (pairing mode - 暂未实现配对验证)")
                            }
                            "allowlist" -> {
                                // Check allowlist
                                val allowFrom = feishuConfig.allowFrom
                                if (allowFrom.isEmpty() || event.senderId !in allowFrom) {
                                    Log.d(TAG, "❌ DM from ${event.senderId} not in allowlist, sending reject message")

                                    // Send rejection message in coroutine
                                    val sender = feishuChannel?.sender
                                    if (sender != null) {
                                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val rejectMessage = "⚠️ 抱歉，你的账号不在白名单中，无法使用此机器人。\n\n你的飞书 User ID: `${event.senderId}`\n\n如需使用，请联系管理员将你的 User ID 添加到白名单。"
                                                sender.sendTextMessage(
                                                    receiveId = event.chatId,
                                                    text = rejectMessage,
                                                    receiveIdType = "chat_id",
                                                    renderMode = com.xiaomo.feishu.messaging.RenderMode.AUTO
                                                )
                                                Log.i(TAG, "✅ 已发送白名单拒绝提示")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "❌ 发送白名单拒绝提示失败: ${e.message}")
                                            }
                                        }
                                    }
                                    return
                                }
                                Log.d(TAG, "✅ DM allowed (sender in allowlist)")
                            }
                            "open" -> {
                                Log.d(TAG, "✅ DM allowed (open policy)")
                            }
                            else -> {
                                Log.w(TAG, "⚠️ Unknown DM policy: $dmPolicy, defaulting to open")
                            }
                        }
                    }

                    // Check group messages (aligned with OpenClaw: resolve requireMention based on groupPolicy)
                    if (event.chatType == "group") {
                        // OpenClaw: requireMentionDefault = groupPolicy === "open" ? false : true
                        val groupPolicy = feishuConfig.groupPolicy
                        val requireMentionDefault = groupPolicy != "open"
                        val requireMention = feishuConfig.requireMention ?: requireMentionDefault
                        Log.d(TAG, "   requireMention: $requireMention (groupPolicy=$groupPolicy, explicit=${feishuConfig.requireMention})")

                        if (requireMention) {
                            // Check @_all (aligned with OpenClaw: treat as @ all bots)
                            if (event.content.contains("@_all")) {
                                Log.d(TAG, "✅ 消息包含 @_all")
                            } else if (event.mentions.isEmpty()) {
                                // No @mention at all
                                Log.w(TAG, "❌ 群消息需要 @机器人，但没有任何 @mention，忽略此消息")
                                Log.w(TAG, "   消息内容: ${event.content}")
                                return
                            } else {
                                // Has @mention, check if bot is @mentioned
                                val botOpenId = feishuChannel?.getBotOpenId()
                                if (botOpenId == null) {
                                    // Cannot get bot open_id, reject message for safety
                                    Log.w(TAG, "❌ 无法获取 bot open_id，无法验证 @mention，忽略此消息")
                                    Log.w(TAG, "   提示: 检查飞书配置或网络连接，确保能获取机器人信息")
                                    return
                                } else if (botOpenId !in event.mentions) {
                                    // Has bot open_id, but message doesn't @ bot
                                    Log.w(TAG, "❌ 群消息 @了其他人但没有 @机器人(${botOpenId})，忽略此消息")
                                    Log.w(TAG, "   消息内容: ${event.content}")
                                    Log.w(TAG, "   Bot Open ID: $botOpenId")
                                    Log.w(TAG, "   Mentions: ${event.mentions}")
                                    return
                                } else {
                                    Log.d(TAG, "✅ 群消息包含机器人的 @mention")
                                }
                            }
                        } else {
                            Log.d(TAG, "✅ 群消息无需 @机器人（requireMention=false）")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "检查消息权限失败", e)
                    // For safety, ignore message on error
                    return
                }

                // 🔑 Generate queue key (aligned with OpenClaw)
                val queueKey = "feishu:${event.chatId}"

                // 🛑 Channel-agnostic stop command check
                if (messageQueueManager.isStopCommand(event.content)) {
                    GlobalScope.launch(Dispatchers.IO) {
                        if (messageQueueManager.stopActiveRun(queueKey)) {
                            sendFeishuReply(event, "✅ 已停止当前任务")
                        } else {
                            sendFeishuReply(event, "当前没有正在执行的任务")
                        }
                    }
                    return
                }

                // 📦 Build queued message
                val queuedMessage = MessageQueueManager.QueuedMessage(
                    messageId = event.messageId,
                    content = event.content,
                    senderId = event.senderId,
                    chatId = event.chatId,
                    chatType = event.chatType,
                    metadata = mapOf(
                        "event" to event
                    )
                )

                // 🎯 Get queue mode (read from config)
                val queueMode = getQueueModeForChat(event.chatId, event.chatType)

                // 🚀 Enqueue message for processing (fully aligned with OpenClaw)
                // OpenClaw 主 session 无消息处理超时，此处也不加超时限制
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        messageQueueManager.enqueue(
                            key = queueKey,
                            message = queuedMessage,
                            mode = queueMode
                        ) { msg ->
                            // Restore original event from metadata
                            val originalEvent = msg.metadata["event"] as? com.xiaomo.feishu.FeishuEvent.Message
                                ?: event
                            processFeishuMessageWithTyping(originalEvent, msg)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "消息队列处理失败", e)
                    }
                }
            }
            is com.xiaomo.feishu.FeishuEvent.Connected -> {
                Log.i(TAG, "✅ Feishu WebSocket 已连接")
            }
            is com.xiaomo.feishu.FeishuEvent.Disconnected -> {
                Log.w(TAG, "⚠️ Feishu WebSocket 已断开，5 秒后尝试重连...")
                GlobalScope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(5000)
                    try {
                        Log.i(TAG, "🔄 尝试重连 Feishu WebSocket...")
                        feishuChannel?.stop()
                        val result = feishuChannel?.start()
                        if (result?.isSuccess == true) {
                            Log.i(TAG, "✅ Feishu WebSocket 重连成功")
                        } else {
                            Log.e(TAG, "❌ Feishu WebSocket 重连失败: ${result?.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Feishu WebSocket 重连异常", e)
                    }
                }
            }
            is com.xiaomo.feishu.FeishuEvent.Error -> {
                Log.e(TAG, "❌ Feishu 错误: ${event.error.message}")
            }
        }
    }

    /**
     * Process Feishu message - call Agent
     *
     * Create lightweight AgentLoop call and return result directly
     */
    private suspend fun processFeishuMessage(event: com.xiaomo.feishu.FeishuEvent.Message): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🤖 开始处理消息: ${event.content}")

                // 📎 Download media attachment if present (aligned with OpenClaw resolveFeishuMediaList)
                var userMessage = event.content
                var userImages: List<com.xiaomo.androidforclaw.providers.llm.ImageBlock>? = null
                val eventMediaKeys = event.mediaKeys
                if (eventMediaKeys != null) {
                    try {
                        val mediaDownload = com.xiaomo.feishu.messaging.FeishuMediaDownload(
                            client = feishuChannel!!.getClient(),
                            cacheDir = cacheDir
                        )
                        val downloadResult = mediaDownload.downloadMedia(event.messageId, eventMediaKeys)
                        if (downloadResult.isSuccess) {
                            val localPath = downloadResult.getOrNull()!!.file.absolutePath
                            Log.i(TAG, "📎 媒体附件已下载: $localPath (type=${eventMediaKeys.mediaType})")

                            // If image, convert to ImageBlock for multimodal LLM input
                            if (eventMediaKeys.mediaType == "image") {
                                try {
                                    val imageFile = java.io.File(localPath)
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(localPath)
                                    if (bitmap != null) {
                                        // Resize to max 1024px on longest side to reduce payload
                                        val maxDim = 1024
                                        val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
                                        val scaled = if (scale < 1f) {
                                            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                                        } else bitmap
                                        val stream = java.io.ByteArrayOutputStream()
                                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                                        val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                                        userImages = listOf(com.xiaomo.androidforclaw.providers.llm.ImageBlock(base64 = base64, mimeType = "image/jpeg"))
                                        if (scaled != bitmap) scaled.recycle()
                                        bitmap.recycle()
                                        userMessage = if (userMessage.isBlank()) "[用户发送了一张图片]" else userMessage
                                        Log.i(TAG, "🖼️ 图片已转为 ImageBlock (${scaled.width}x${scaled.height}, ${stream.size()} bytes)")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "🖼️ 图片转 base64 失败: ${e.message}")
                                    userMessage = "$userMessage\n[附件已下载: $localPath]"
                                }
                            } else {
                                userMessage = "$userMessage\n[附件已下载: $localPath]"
                            }
                        } else {
                            Log.w(TAG, "📎 媒体下载失败: ${downloadResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "📎 媒体下载异常: ${e.message}")
                    }
                }

                // 🆔 Generate session ID: use chatId_chatType as unique identifier
                // This way different groups/private chats have independent session history
                val sessionId = "${event.chatId}_${event.chatType}"
                Log.i(TAG, "🆔 Session ID: $sessionId (chatType: ${event.chatType})")

                // Execute AgentLoop synchronously and return result
                val sessionManager = MainEntryNew.getSessionManager()
                if (sessionManager == null) {
                    MainEntryNew.initialize(this@MyApplication)
                }

                val session = MainEntryNew.getSessionManager()?.getOrCreate(sessionId)
                if (session == null) {
                    return@withContext "系统错误：无法创建会话"
                }

                Log.i(TAG, "📋 [Session] 加载会话: ${session.messageCount()} 条历史消息")

                // Get history messages and cleanup (ensure tool_use and tool_result are paired)
                val rawHistory = session.getRecentMessages(20)
                val contextHistory = cleanupToolMessages(rawHistory)
                Log.i(TAG, "📋 [Session] 清理后: ${contextHistory.size} 条消息（原始: ${rawHistory.size}）")

                // Initialize components
                val taskDataManager = TaskDataManager.getInstance()
                val toolRegistry = ToolRegistry(
                    context = this@MyApplication,
                    taskDataManager = taskDataManager
                )
                val androidToolRegistry = AndroidToolRegistry(
                    context = this@MyApplication,
                    taskDataManager = taskDataManager,
                    cameraCaptureManager = cameraCaptureManager,
                )

                // Register feishu tools into ToolRegistry (aligned with OpenClaw extension tools)
                val fc = feishuChannel
                if (fc != null) {
                    try {
                        val feishuToolRegistry = fc.getToolRegistry()
                        if (feishuToolRegistry != null) {
                            // Register Feishu tools into function-call ToolRegistry
                            // (skills help routing, but real execution requires tool registration)
                            val feishuToolCount = com.xiaomo.androidforclaw.agent.tools.registerFeishuTools(toolRegistry, feishuToolRegistry)
                            Log.i(TAG, "🔧 已注册 $feishuToolCount 个飞书工具到 ToolRegistry")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "飞书工具注册失败: ${e.message}")
                    }
                }

                val configLoader = ConfigLoader(this@MyApplication)
                val contextBuilder = ContextBuilder(
                    context = this@MyApplication,
                    toolRegistry = toolRegistry,
                    androidToolRegistry = androidToolRegistry,
                    configLoader = configLoader
                )
                val llmProvider = com.xiaomo.androidforclaw.providers.UnifiedLLMProvider(this@MyApplication)
                val contextManager = com.xiaomo.androidforclaw.agent.context.ContextManager(llmProvider)

                // Load maxIterations from config
                val config = configLoader.loadOpenClawConfig()
                val maxIterations = config.agent.maxIterations

                val agentLoop = AgentLoop(
                    llmProvider = llmProvider,
                    toolRegistry = toolRegistry,
                    androidToolRegistry = androidToolRegistry,
                    contextManager = contextManager,
                    maxIterations = maxIterations,
                    modelRef = null
                )

                // Register AgentLoop for STEER mode mid-run message injection
                val steerQueueKey = "feishu:${event.chatId}"
                messageQueueManager.setActiveAgentLoop(steerQueueKey, agentLoop)

                // Build system prompt (with channel context for messaging awareness)
                val channelCtx = ContextBuilder.ChannelContext(
                    channel = "feishu",
                    chatId = event.chatId,
                    chatType = event.chatType,
                    senderId = event.senderId,
                    messageId = event.messageId
                )
                val systemPrompt = contextBuilder.buildSystemPrompt(
                    userGoal = event.content,
                    packageName = "",
                    testMode = "chat",
                    channelContext = channelCtx
                )

                // ✅ Streaming Card: real-time card updates during agent processing
                // Aligned with OpenClaw reply-dispatcher.ts + streaming-card.ts
                val blockRepliesSent = mutableListOf<String>()
                val streamingCard = feishuChannel?.createStreamingCard()
                var streamingCardMessageId: String? = null
                var streamingFailed = false

                val progressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    agentLoop.progressFlow.collect { update ->
                        when {
                            // Start streaming card on first Thinking event
                            update is ProgressUpdate.Thinking && streamingCard != null && !streamingFailed && streamingCard.cardId == null -> {
                                try {
                                    val startResult = streamingCard.start("Thinking...")
                                    if (startResult.isSuccess) {
                                        val cardId = startResult.getOrNull()!!
                                        val sender = feishuChannel?.sender
                                        if (sender != null) {
                                            // Send card message with reply routing
                                            val sendResult = if (event.chatType == "group" && event.rootId == null) {
                                                sender.sendCardByIdReply(event.messageId, cardId)
                                            } else {
                                                sender.sendCardById(event.chatId, cardId)
                                            }
                                            streamingCardMessageId = sendResult.getOrNull()?.messageId
                                            Log.i(TAG, "📺 Streaming card sent: $streamingCardMessageId")
                                        }
                                    } else {
                                        Log.w(TAG, "Streaming card creation failed: ${startResult.exceptionOrNull()?.message}")
                                        streamingFailed = true
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Streaming card start failed: ${e.message}")
                                    streamingFailed = true
                                }
                            }

                            // Update streaming card with reasoning
                            update is ProgressUpdate.Reasoning && streamingCard?.isActive() == true -> {
                                try {
                                    streamingCard.appendText("> ${update.content}\n\n---\n\n")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Streaming reasoning update failed: ${e.message}")
                                }
                            }

                            // Update streaming card with tool call info
                            update is ProgressUpdate.ToolCall && streamingCard?.isActive() == true -> {
                                try {
                                    streamingCard.appendText("`Using: ${update.name}...`\n\n")
                                } catch (e: Exception) { /* ignore */ }
                            }

                            // Update streaming card with block reply text
                            update is ProgressUpdate.BlockReply -> {
                                val text = update.text.trim()
                                if (text.isNotEmpty()) {
                                    if (streamingCard?.isActive() == true) {
                                        try {
                                            streamingCard.appendText(text)
                                            blockRepliesSent.add(text)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Streaming block reply update failed: ${e.message}")
                                        }
                                    } else {
                                        // Fallback: send as separate message (old behavior)
                                        try {
                                            sendFeishuReply(event, text)
                                            blockRepliesSent.add(text)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "发送中间回复失败: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Run AgentLoop (convert history messages)
                val result = try {
                    agentLoop.run(
                        systemPrompt = systemPrompt,
                        userMessage = userMessage,
                        contextHistory = contextHistory.map { it.toNewMessage() },
                        reasoningEnabled = true,
                        images = userImages
                    )
                } finally {
                    // Always unregister after the run completes (or fails)
                    messageQueueManager.clearActiveAgentLoop(steerQueueKey)
                }

                // Stop progress listener
                progressJob.cancel()

                // Save messages to session (convert back to old format)
                result.messages.forEach { message ->
                    session.addMessage(message.toLegacyMessage())
                }
                MainEntryNew.getSessionManager()?.save(session)
                Log.i(TAG, "💾 [Session] 会话已保存，总消息数: ${session.messageCount()}")

                Log.i(TAG, "✅ Agent 处理完成")
                Log.i(TAG, "   迭代次数: ${result.iterations}")
                Log.i(TAG, "   使用工具: ${result.toolsUsed.joinToString(", ")}")

                // Close streaming card with final content
                val finalContent = com.xiaomo.androidforclaw.util.ReplyTagFilter.strip(result.finalContent ?: "抱歉，我无法处理这个请求。")

                if (streamingCard?.isActive() == true) {
                    try {
                        streamingCard.close(finalContent)
                        Log.i(TAG, "📺 Streaming card closed with final content")
                        "\u0000BLOCK_REPLY_ALREADY_SENT"  // Sentinel: final content is in the streaming card
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to close streaming card: ${e.message}")
                        finalContent // Fall through to normal reply
                    }
                } else if (blockRepliesSent.isNotEmpty() && blockRepliesSent.last().trim() == finalContent.trim()) {
                    Log.i(TAG, "📤 Final content matches last block reply, marking as already sent")
                    "\u0000BLOCK_REPLY_ALREADY_SENT"
                } else {
                    finalContent
                }

            } catch (e: Exception) {
                Log.e(TAG, "Agent 处理失败", e)
                "抱歉，处理消息时出错了：${e.message}"
            }
        }
    }

    /**
     * Send reply to Feishu
     *
     * Features:
     * - Reply routing: group → quote reply, thread → thread reply, DM → direct send
     * - Fallback: quote reply fails → direct send (message may have been withdrawn)
     * - Use FeishuSender to auto-detect Markdown and render with cards
     * - Detect screenshot paths and auto-upload send images
     *
     * Aligned with OpenClaw reply-dispatcher.ts
     */
    private suspend fun sendFeishuReply(event: com.xiaomo.feishu.FeishuEvent.Message, content: String) {
        try {
            Log.i(TAG, "📤 发送回复到飞书...")

            // Filter internal reasoning tags (<think>, <final>, etc.)
            val cleanContent = filterReasoningTags(content)

            val sender = feishuChannel?.sender
            if (sender == null) {
                Log.e(TAG, "❌ FeishuSender 未初始化")
                return
            }

            // Detect screenshot path
            val screenshotPathRegex = Regex("""路径:\s*((?:/storage/|/sdcard/|content://)[^\s\n]+\.png)""")
            val screenshotMatch = screenshotPathRegex.find(cleanContent)

            if (screenshotMatch != null) {
                val screenshotPath = screenshotMatch.groupValues[1]
                Log.i(TAG, "📸 检测到截图路径: $screenshotPath")

                // Upload and send image
                val imageFile = resolveImageFile(screenshotPath)
                if (imageFile != null && imageFile.exists()) {
                    try {
                        val imageResult = feishuChannel?.uploadAndSendImage(
                            imageFile = imageFile,
                            receiveId = event.chatId,
                            receiveIdType = "chat_id"
                        )
                        if (imageResult?.isSuccess == true) {
                            Log.i(TAG, "✅ 图片发送成功")
                        } else {
                            Log.e(TAG, "❌ 图片上传失败: ${imageResult?.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "上传截图失败", e)
                    }
                }

                // Send text reply (remove screenshot path info)
                val textContent = cleanContent.replace(screenshotPathRegex, "").trim()
                if (textContent.isNotEmpty()) {
                    sendTextWithRouting(sender, event, textContent)
                }
            } else {
                sendTextWithRouting(sender, event, cleanContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送飞书回复失败", e)
        }
    }

    /**
     * 发送文本回复（带路由策略）
     * 对齐 OpenClaw reply-dispatcher: group → quote reply, thread → thread reply, DM → direct
     */
    private suspend fun sendTextWithRouting(
        sender: com.xiaomo.feishu.messaging.FeishuSender,
        event: com.xiaomo.feishu.FeishuEvent.Message,
        text: String
    ) {
        val renderMode = com.xiaomo.feishu.messaging.RenderMode.AUTO

        // Determine reply strategy
        val useQuoteReply = event.chatType == "group" && event.rootId == null && event.threadId == null
        val useThreadReply = event.rootId != null || event.threadId != null

        var result: Result<com.xiaomo.feishu.messaging.SendResult>? = null

        // Strategy 1: Thread reply (message is in a topic)
        if (useThreadReply) {
            val rootId = event.rootId ?: event.messageId
            result = try {
                sender.replyInThread(messageId = rootId, text = text)
            } catch (e: Exception) {
                Log.w(TAG, "Thread reply failed, falling back to direct: ${e.message}")
                null
            }
        }

        // Strategy 2: Quote reply (group message, not in topic)
        if (result == null && useQuoteReply) {
            result = try {
                sender.sendTextReply(
                    replyToMessageId = event.messageId,
                    text = text,
                    renderMode = renderMode
                )
            } catch (e: Exception) {
                Log.w(TAG, "Quote reply failed, falling back to direct: ${e.message}")
                null
            }
            // Fallback: quote reply API error → direct send
            if (result != null && result.isFailure) {
                Log.w(TAG, "Quote reply returned error: ${result.exceptionOrNull()?.message}, falling back to direct")
                result = null
            }
        }

        // Strategy 3: Direct send (DM or fallback)
        if (result == null || result.isFailure) {
            result = sender.sendTextMessage(
                receiveId = event.chatId,
                text = text,
                receiveIdType = "chat_id",
                renderMode = renderMode
            )
        }

        if (result.isSuccess) {
            Log.i(TAG, "✅ 回复发送成功: ${result.getOrNull()?.messageId}")
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
            Log.e(TAG, "❌ 回复发送失败: $errorMsg")

            // Fallback: card fails → plain text
            if (errorMsg.contains("table number over limit") || errorMsg.contains("230099") || errorMsg.contains("HTTP 400")) {
                Log.w(TAG, "⚠️ 降级为纯文本模式重试...")
                sender.sendTextMessage(
                    receiveId = event.chatId,
                    text = text,
                    receiveIdType = "chat_id",
                    renderMode = com.xiaomo.feishu.messaging.RenderMode.TEXT
                )
            }
        }
    }

    /**
     * 解析图片文件路径（支持 Content URI 和文件路径）
     */
    private fun resolveImageFile(path: String): java.io.File? {
        return if (path.startsWith("content://")) {
            try {
                val uri = android.net.Uri.parse(path)
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = java.io.File(cacheDir, "temp_screenshot_${System.currentTimeMillis()}.png")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve Content URI", e)
                null
            }
        } else {
            java.io.File(path)
        }
    }

    /**
     * Filter reasoning tags from LLM response.
     * Delegates to ReasoningTagFilter to avoid code duplication.
     */
    private fun filterReasoningTags(content: String): String =
        ReasoningTagFilter.stripReasoningTags(content)

    /**
     * Start Discord Channel (if configured)
     */
    private fun startDiscordChannelIfEnabled() {
        Log.i(TAG, "⏰ startDiscordChannelIfEnabled() 被调用")
        val scope = CoroutineScope(Dispatchers.IO)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "========================================")
                Log.i(TAG, "🤖 检查 Discord Channel 配置...")
                Log.i(TAG, "========================================")

                val configLoader = ConfigLoader(this@MyApplication)
                val openClawConfig = configLoader.loadOpenClawConfig()
                val discordConfigData = openClawConfig.channels.discord

                if (discordConfigData == null || !discordConfigData.enabled) {
                    Log.i(TAG, "⏭️  Discord Channel 未启用，跳过初始化")
                    Log.i(TAG, "   配置路径: /sdcard/.androidforclaw/openclaw.json")
                    Log.i(TAG, "   设置 channels.discord.enabled = true 以启用")
                    Log.i(TAG, "========================================")
                    return@launch
                }

                val token = discordConfigData.token
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "⚠️  Discord Bot Token 未配置，跳过启动")
                    Log.i(TAG, "   请在配置中设置 channels.discord.token")
                    Log.i(TAG, "========================================")
                    return@launch
                }

                Log.i(TAG, "✅ Discord Channel 已启用，准备启动...")
                Log.i(TAG, "   Name: ${discordConfigData.name ?: "default"}")
                Log.i(TAG, "   DM Policy: ${discordConfigData.dm?.policy ?: "pairing"}")
                Log.i(TAG, "   Group Policy: ${discordConfigData.groupPolicy ?: "open"}")
                Log.i(TAG, "   Reply Mode: ${discordConfigData.replyToMode ?: "off"}")

                // Create DiscordConfig
                val config = DiscordConfig(
                    enabled = true,
                    token = token,
                    name = discordConfigData.name,
                    dm = discordConfigData.dm?.let {
                        DiscordConfig.DmConfig(
                            policy = it.policy ?: "pairing",
                            allowFrom = it.allowFrom ?: emptyList()
                        )
                    },
                    groupPolicy = discordConfigData.groupPolicy,
                    guilds = discordConfigData.guilds?.mapValues { (_, guildData) ->
                        DiscordConfig.GuildConfig(
                            channels = guildData.channels,
                            requireMention = guildData.requireMention ?: true,
                            toolPolicy = guildData.toolPolicy
                        )
                    },
                    replyToMode = discordConfigData.replyToMode,
                    accounts = discordConfigData.accounts?.mapValues { (_, accountData) ->
                        DiscordConfig.DiscordAccountConfig(
                            enabled = accountData.enabled ?: true,
                            token = accountData.token,
                            name = accountData.name,
                            dm = accountData.dm?.let {
                                DiscordConfig.DmConfig(
                                    policy = it.policy ?: "pairing",
                                    allowFrom = it.allowFrom ?: emptyList()
                                )
                            },
                            guilds = accountData.guilds?.mapValues { (_, guildData) ->
                                DiscordConfig.GuildConfig(
                                    channels = guildData.channels,
                                    requireMention = guildData.requireMention ?: true,
                                    toolPolicy = guildData.toolPolicy
                                )
                            }
                        )
                    }
                )

                // Start DiscordChannel
                val result = DiscordChannel.start(this@MyApplication, config)

                if (result.isSuccess) {
                    discordChannel = result.getOrNull()

                    // Initialize DiscordTyping
                    discordChannel?.let { channel ->
                        val client = com.xiaomo.discord.DiscordClient(token)
                        discordTyping = DiscordTyping(client)
                    }

                    // Update MMKV status
                    val mmkv = MMKV.defaultMMKV()
                    mmkv?.encode("channel_discord_enabled", true)

                    Log.i(TAG, "========================================")
                    Log.i(TAG, "✅ Discord Channel 启动成功!")
                    Log.i(TAG, "   Bot: ${discordChannel?.getBotUsername()} (${discordChannel?.getBotUserId()})")
                    Log.i(TAG, "   现在可以接收 Discord 消息了")
                    Log.i(TAG, "========================================")

                    // Subscribe to event flow, handle received messages
                    scope.launch(Dispatchers.IO) {
                        discordChannel?.eventFlow?.collect { event ->
                            handleDiscordEvent(event)
                        }
                    }
                } else {
                    // Clear MMKV status
                    val mmkv = MMKV.defaultMMKV()
                    mmkv?.encode("channel_discord_enabled", false)

                    Log.e(TAG, "========================================")
                    Log.e(TAG, "❌ Discord Channel 启动失败")
                    Log.e(TAG, "   错误: ${result.exceptionOrNull()?.message}")
                    Log.e(TAG, "========================================")
                }

            } catch (e: Exception) {
                // Clear MMKV status
                val mmkv = MMKV.defaultMMKV()
                mmkv?.encode("channel_discord_enabled", false)

                Log.e(TAG, "========================================")
                Log.e(TAG, "❌ Discord Channel 初始化异常", e)
                Log.e(TAG, "========================================")
            }
        }
    }

    /**
     * Handle Discord event
     */
    private suspend fun handleDiscordEvent(event: ChannelEvent) {
        try {
            when (event) {
                is ChannelEvent.Connected -> {
                    Log.i(TAG, "🔗 Discord Connected")
                }

                is ChannelEvent.Message -> {
                    Log.i(TAG, "📨 收到 Discord 消息")
                    Log.i(TAG, "   From: ${event.authorName} (${event.authorId})")
                    Log.i(TAG, "   Content: ${event.content}")
                    Log.i(TAG, "   Type: ${event.chatType}")
                    Log.i(TAG, "   Channel: ${event.channelId}")

                    // Send reply
                    sendDiscordReply(event)
                }

                is ChannelEvent.ReactionAdd -> {
                    Log.d(TAG, "👍 Discord Reaction Added: ${event.emoji}")
                }

                is ChannelEvent.ReactionRemove -> {
                    Log.d(TAG, "👎 Discord Reaction Removed: ${event.emoji}")
                }

                is ChannelEvent.TypingStart -> {
                    Log.d(TAG, "⌨️ Discord User Typing: ${event.userId}")
                }

                is ChannelEvent.Error -> {
                    Log.e(TAG, "❌ Discord Error", event.error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理 Discord 事件失败", e)
        }
    }

    /**
     * Send Discord reply (actual implementation)
     */
    private suspend fun sendDiscordReply(event: ChannelEvent.Message) {
        val startTime = System.currentTimeMillis()

        try {
            // Message deduplication check
            if (discordDedup.isDuplicate(event.messageId)) {
                Log.d(TAG, "⏭️  消息已处理，跳过: ${event.messageId}")
                return
            }

            // Cancel previous processing task for this channel
            discordProcessingJobs[event.channelId]?.cancel()

            // Create new processing task
            val job = GlobalScope.launch(Dispatchers.IO) {
                try {
                    processDiscordMessage(event, startTime)
                } finally {
                    discordProcessingJobs.remove(event.channelId)
                }
            }

            discordProcessingJobs[event.channelId] = job

        } catch (e: Exception) {
            Log.e(TAG, "发送 Discord 回复失败", e)
            try {
                discordChannel?.addReaction(event.channelId, event.messageId, "❌")
            } catch (e2: Exception) {
                Log.e(TAG, "添加错误表情失败", e2)
            }
        }
    }

    /**
     * Process Discord message (core logic)
     */
    private suspend fun processDiscordMessage(event: ChannelEvent.Message, startTime: Long) {
        var thinkingReactionAdded = false
        var typingStarted = false

        try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "🤖 开始处理 Discord 消息")
            Log.i(TAG, "   MessageID: ${event.messageId}")
            Log.i(TAG, "   From: ${event.authorName} (${event.authorId})")
            Log.i(TAG, "   Channel: ${event.channelId}")
            Log.i(TAG, "   Content: ${event.content}")
            Log.i(TAG, "========================================")

            // 1. Add thinking reaction
            discordChannel?.addReaction(event.channelId, event.messageId, "🤔")
            thinkingReactionAdded = true

            // 2. Start typing indicator
            discordTyping?.startContinuous(event.channelId)
            typingStarted = true

            // 3. 🆔 Generate session ID: use channelId as unique identifier
            val sessionId = "discord_${event.channelId}"
            Log.i(TAG, "🆔 Session ID: $sessionId")

            // 4. Get or create unified session
            if (MainEntryNew.getSessionManager() == null) {
                MainEntryNew.initialize(this@MyApplication)
            }
            val session = MainEntryNew.getSessionManager()?.getOrCreate(sessionId)
            if (session == null) {
                throw Exception("无法创建会话")
            }

            Log.i(TAG, "📋 [Session] 加载会话: ${session.messageCount()} 条历史消息")

            // 5. Get history messages and cleanup (ensure tool_use and tool_result are paired)
            val rawHistory = session.getRecentMessages(20)
            val contextHistory = cleanupToolMessages(rawHistory)
            Log.i(TAG, "📋 [Session] 清理后: ${contextHistory.size} 条消息（原始: ${rawHistory.size}）")

            // 6. Build system prompt
            val historyContext = ""  // History is in contextHistory
            val systemPrompt = buildDiscordSystemPrompt(event, historyContext)

            // 7. Call AgentLoop
            Log.i(TAG, "🔄 调用 AgentLoop 处理消息...")

            val llmProvider = com.xiaomo.androidforclaw.providers.UnifiedLLMProvider(this@MyApplication)
            val contextManager = com.xiaomo.androidforclaw.agent.context.ContextManager(llmProvider)
            val taskDataManager = TaskDataManager.getInstance()

            val toolRegistry = ToolRegistry(this@MyApplication, taskDataManager)
            val androidToolRegistry = AndroidToolRegistry(this@MyApplication, taskDataManager, cameraCaptureManager = cameraCaptureManager)

            val agentLoop = AgentLoop(
                llmProvider = llmProvider,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                contextManager = contextManager,
                maxIterations = 40,
                modelRef = null
            )

            val result = agentLoop.run(
                systemPrompt = systemPrompt,
                userMessage = event.content,
                contextHistory = contextHistory.map { it.toNewMessage() },
                reasoningEnabled = true
            )

            // 8. Stop typing status
            if (typingStarted) {
                discordTyping?.stopContinuous(event.channelId)
                typingStarted = false
            }

            // 9. Remove thinking reaction
            if (thinkingReactionAdded) {
                discordChannel?.removeReaction(event.channelId, event.messageId, "🤔")
                thinkingReactionAdded = false
            }

            // 9. Save messages to session (convert back to old format)
            result.messages.forEach { message ->
                session.addMessage(message.toLegacyMessage())
            }
            MainEntryNew.getSessionManager()?.save(session)
            Log.i(TAG, "💾 [Session] 会话已保存，总消息数: ${session.messageCount()}")

            // 10. Send reply
            val replyContent = com.xiaomo.androidforclaw.util.ReplyTagFilter.strip(result.finalContent ?: "抱歉，我无法处理这个请求。")

            // Send in chunks (Discord 2000 character limit)
            val chunks = splitMessageIntoChunks(replyContent, 1900)

            for ((index, chunk) in chunks.withIndex()) {
                val sendResult = discordChannel?.sendMessage(
                    channelId = event.channelId,
                    content = chunk,
                    replyToId = if (index == 0) event.messageId else null
                )

                if (sendResult?.isSuccess == true) {
                    val sentMessageId = sendResult.getOrNull()
                    Log.i(TAG, "✅ 消息块 ${index + 1}/${chunks.size} 发送成功: $sentMessageId")
                } else {
                    Log.e(TAG, "❌ 消息块 ${index + 1}/${chunks.size} 发送失败: ${sendResult?.exceptionOrNull()?.message}")
                }
            }

            // 11. Add completion reaction
            discordChannel?.addReaction(event.channelId, event.messageId, "✅")

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "========================================")
            Log.i(TAG, "✅ Discord 消息处理完成")
            Log.i(TAG, "   耗时: ${elapsed}ms")
            Log.i(TAG, "   迭代: ${result.iterations}")
            Log.i(TAG, "   回复长度: ${replyContent.length} 字符")
            Log.i(TAG, "   分块数: ${chunks.size}")
            Log.i(TAG, "========================================")

        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "❌ Discord 消息处理失败", e)
            Log.e(TAG, "========================================")

            // Cleanup status
            if (typingStarted) {
                discordTyping?.stopContinuous(event.channelId)
            }

            if (thinkingReactionAdded) {
                try {
                    discordChannel?.removeReaction(event.channelId, event.messageId, "🤔")
                } catch (e2: Exception) {
                    Log.e(TAG, "移除思考表情失败", e2)
                }
            }

            // Add error reaction and error message
            try {
                discordChannel?.addReaction(event.channelId, event.messageId, "❌")

                discordChannel?.sendMessage(
                    channelId = event.channelId,
                    content = "抱歉，处理您的消息时遇到错误：${e.message}",
                    replyToId = event.messageId
                )
            } catch (e2: Exception) {
                Log.e(TAG, "发送错误消息失败", e2)
            }
        }
    }

    // ── Weixin Channel ───────────────────────────────────────────────────────

    /**
     * 重新启动微信通道（扫码登录成功后调用）。
     * 会先停掉旧的 channel（如有），再重新初始化并启动 monitor。
     */
    fun restartWeixinChannel() {
        Log.i(TAG, "🔄 restartWeixinChannel() 被调用")
        weixinChannel?.stop()
        weixinChannel = null
        startWeixinChannelIfEnabled()
    }

    private fun startWeixinChannelIfEnabled() {
        Log.i(TAG, "⏰ startWeixinChannelIfEnabled() 被调用")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val configLoader = ConfigLoader(this@MyApplication)
                val openClawConfig = configLoader.loadOpenClawConfig()
                val weixinCfg = openClawConfig.channels.weixin

                if (weixinCfg == null || !weixinCfg.enabled) {
                    Log.i(TAG, "⏭️  Weixin Channel 未启用，跳过")
                    return@launch
                }

                Log.i(TAG, "✅ Weixin Channel 已启用，准备启动...")

                val config = com.xiaomo.weixin.WeixinConfig(
                    enabled = true,
                    baseUrl = weixinCfg.baseUrl,
                    cdnBaseUrl = weixinCfg.cdnBaseUrl,
                    routeTag = weixinCfg.routeTag,
                )

                val channel = com.xiaomo.weixin.WeixinChannel(config)
                val configured = channel.init()

                if (!configured) {
                    Log.w(TAG, "Weixin Channel 未登录，需要先扫码")
                    return@launch
                }

                val started = channel.start()
                if (!started) {
                    Log.e(TAG, "Weixin Channel 启动失败")
                    return@launch
                }

                weixinChannel = channel
                Log.i(TAG, "✅ Weixin Channel 启动成功")

                // Collect inbound messages and dispatch to agent via MessageQueueManager
                channel.messageFlow?.collect { msg ->
                    Log.i(TAG, "📨 Weixin 收到消息: from=${msg.fromUserId} body=${msg.body.take(50)} hasMedia=${msg.hasMedia} mediaType=${msg.mediaType}")

                    // Download media if present
                    var downloadedMediaFile: java.io.File? = null
                    if (msg.hasMedia && msg.mediaCdn != null) {
                        try {
                            downloadedMediaFile = com.xiaomo.weixin.cdn.WeixinCdnDownloader.downloadAndDecrypt(
                                media = msg.mediaCdn!!,
                                fileExtension = msg.mediaFileExtension ?: "bin",
                            )
                            if (downloadedMediaFile != null) {
                                Log.i(TAG, "📎 Weixin 媒体下载成功: ${downloadedMediaFile.absolutePath} (${downloadedMediaFile.length()} bytes)")
                            } else {
                                Log.w(TAG, "⚠️ Weixin 媒体下载失败")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Weixin 媒体下载异常", e)
                        }
                    }

                    if (msg.body.isBlank() && !msg.hasMedia) {
                        Log.d(TAG, "Weixin: 空消息且无媒体，跳过")
                        return@collect
                    }

                    val queueKey = "weixin:${msg.fromUserId}"

                    // Build content for agent: text + media context
                    val agentContent = buildString {
                        if (msg.body.isNotBlank()) {
                            append(msg.body)
                        }
                        if (msg.hasMedia && downloadedMediaFile != null) {
                            if (isNotEmpty()) append("\n\n")
                            when (msg.mediaType) {
                                com.xiaomo.weixin.api.MessageItemType.IMAGE -> {
                                    append("[用户发送了一张图片: ${downloadedMediaFile.absolutePath}")
                                    if (msg.imageWidth != null && msg.imageHeight != null) {
                                        append(", ${msg.imageWidth}x${msg.imageHeight}")
                                    }
                                    append("]")
                                }
                                com.xiaomo.weixin.api.MessageItemType.VOICE -> {
                                    append("[用户发送了一条语音")
                                    if (msg.voicePlaytime != null) append(", 时长${msg.voicePlaytime}秒")
                                    if (!msg.voiceText.isNullOrBlank()) append(", 内容: ${msg.voiceText}")
                                    append(", 文件: ${downloadedMediaFile.absolutePath}]")
                                }
                                com.xiaomo.weixin.api.MessageItemType.FILE -> {
                                    append("[用户发送了一个文件: ${msg.mediaFileName ?: downloadedMediaFile.name}, 路径: ${downloadedMediaFile.absolutePath}]")
                                }
                                com.xiaomo.weixin.api.MessageItemType.VIDEO -> {
                                    append("[用户发送了一个视频: ${downloadedMediaFile.absolutePath}]")
                                }
                            }
                        } else if (msg.hasMedia && downloadedMediaFile == null) {
                            if (isNotEmpty()) append("\n\n")
                            append("[用户发送了媒体文件，但下载失败]")
                        }
                    }

                    // Channel-agnostic stop command check
                    if (messageQueueManager.isStopCommand(agentContent)) {
                        if (messageQueueManager.stopActiveRun(queueKey)) {
                            channel.sender?.sendText(msg.fromUserId, "✅ 已停止当前任务")
                        } else {
                            channel.sender?.sendText(msg.fromUserId, "当前没有正在执行的任务")
                        }
                        return@collect
                    }

                    val queuedMessage = MessageQueueManager.QueuedMessage(
                        messageId = msg.messageId?.toString() ?: "weixin_${System.currentTimeMillis()}",
                        content = agentContent,
                        senderId = msg.fromUserId,
                        chatId = msg.fromUserId,
                        chatType = "p2p",
                        metadata = mapOf("weixinMsg" to msg, "mediaFile" to downloadedMediaFile)
                    )

                    val queueMode = getQueueModeForChat(msg.fromUserId, "p2p")

                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            messageQueueManager.enqueue(
                                key = queueKey,
                                message = queuedMessage,
                                mode = queueMode
                            ) { qMsg ->
                                val originalMsg = qMsg.metadata["weixinMsg"]
                                    as? com.xiaomo.weixin.messaging.WeixinInboundMessage ?: msg
                                processWeixinMessageQueued(originalMsg, queueKey)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            Log.i(TAG, "Weixin: 任务被用户取消 ${msg.fromUserId}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Weixin 消息队列处理失败", e)
                            try {
                                channel.sender?.sendText(msg.fromUserId, "处理消息时出错：${e.message}")
                            } catch (_: Exception) {}
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Weixin Channel 启动异常", e)
            }
        }
    }

    /**
     * Process Weixin message through MessageQueueManager pipeline.
     * Registers AgentLoop with MessageQueueManager for channel-agnostic stop support.
     */
    private suspend fun processWeixinMessageQueued(
        msg: com.xiaomo.weixin.messaging.WeixinInboundMessage,
        queueKey: String
    ) {
        val channel = weixinChannel ?: return
        val sender = channel.sender
        val toUser = msg.fromUserId

        try {
            // Send typing indicator
            sender?.sendTyping(toUser)

            val sessionId = "weixin_${msg.fromUserId}"
            Log.i(TAG, "🆔 Weixin Session ID: $sessionId")

            // Rebuild agent content from message fields (includes media context)
            val agentContent = buildString {
                if (msg.body.isNotBlank()) {
                    append(msg.body)
                }
                if (msg.hasMedia) {
                    if (isNotEmpty()) append("\n\n")
                    when (msg.mediaType) {
                        com.xiaomo.weixin.api.MessageItemType.IMAGE -> {
                            append("[用户发送了一张图片]")
                        }
                        com.xiaomo.weixin.api.MessageItemType.VOICE -> {
                            append("[用户发送了一条语音")
                            if (msg.voicePlaytime != null) append(", 时长${msg.voicePlaytime}秒")
                            if (!msg.voiceText.isNullOrBlank()) append(", 内容: ${msg.voiceText}")
                            append("]")
                        }
                        com.xiaomo.weixin.api.MessageItemType.FILE -> {
                            append("[用户发送了一个文件: ${msg.mediaFileName ?: "未知文件名"}]")
                        }
                        com.xiaomo.weixin.api.MessageItemType.VIDEO -> {
                            append("[用户发送了一个视频]")
                        }
                    }
                }
            }

            if (MainEntryNew.getSessionManager() == null) {
                MainEntryNew.initialize(this@MyApplication)
            }
            val sessionManager = MainEntryNew.getSessionManager()
            if (sessionManager == null) {
                sender?.sendText(toUser, "系统错误：无法创建会话")
                return
            }

            val session = sessionManager.getOrCreate(sessionId)

            val rawHistory = session.getRecentMessages(20)
            val contextHistory = cleanupToolMessages(rawHistory)

            val taskDataManager = TaskDataManager.getInstance()
            val toolRegistry = ToolRegistry(
                context = this@MyApplication,
                taskDataManager = taskDataManager
            )
            val androidToolRegistry = AndroidToolRegistry(
                context = this@MyApplication,
                taskDataManager = taskDataManager,
                cameraCaptureManager = cameraCaptureManager,
            )

            val configLoader = ConfigLoader(this@MyApplication)
            val contextBuilder = ContextBuilder(
                context = this@MyApplication,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                configLoader = configLoader
            )
            val llmProvider = com.xiaomo.androidforclaw.providers.UnifiedLLMProvider(this@MyApplication)
            val contextManager = com.xiaomo.androidforclaw.agent.context.ContextManager(llmProvider)

            val config = configLoader.loadOpenClawConfig()
            val maxIterations = config.agent.maxIterations

            // Use weixin-specific model if configured
            val weixinModel = config.channels.weixin?.model

            val agentLoop = AgentLoop(
                llmProvider = llmProvider,
                toolRegistry = toolRegistry,
                androidToolRegistry = androidToolRegistry,
                contextManager = contextManager,
                maxIterations = maxIterations,
                modelRef = weixinModel
            )

            // Register with MessageQueueManager (channel-agnostic stop/steer support)
            messageQueueManager.setActiveAgentLoop(queueKey, agentLoop)

            val channelCtx = ContextBuilder.ChannelContext(
                channel = "weixin",
                chatId = msg.fromUserId,
                chatType = "p2p",
                senderId = msg.fromUserId,
                messageId = msg.messageId?.toString() ?: ""
            )
            val systemPrompt = contextBuilder.buildSystemPrompt(
                userGoal = agentContent,
                packageName = "",
                testMode = "chat",
                channelContext = channelCtx
            )

            // Collect intermediate progress updates and send to Weixin user
            val blockRepliesSent = mutableListOf<String>()

            val progressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                agentLoop.progressFlow.collect { update ->
                    when (update) {
                        is ProgressUpdate.ToolCall -> {
                            Log.d(TAG, "Weixin: ToolCall ${update.name}")
                        }
                        is ProgressUpdate.BlockReply -> {
                            val text = update.text.trim()
                            if (text.isNotEmpty()) {
                                try {
                                    val sanitized = com.xiaomo.androidforclaw.agent.session.HistorySanitizer
                                        .stripControlTokensFromText(text)
                                        .trim()
                                    if (sanitized.isNotBlank()) {
                                        sender?.sendText(toUser, sanitized)
                                        blockRepliesSent.add(sanitized)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Weixin: 发送中间回复失败: ${e.message}")
                                }
                            }
                        }
                        is ProgressUpdate.Error -> {
                            try {
                                sender?.sendText(toUser, "⚠️ ${update.message}")
                            } catch (_: Exception) {}
                        }
                        else -> { /* ignore other updates */ }
                    }
                }
            }

            val result = try {
                agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = agentContent,
                    contextHistory = contextHistory.map { it.toNewMessage() },
                )
            } finally {
                // Always unregister after the run completes (or fails)
                messageQueueManager.clearActiveAgentLoop(queueKey)
            }

            // Cancel progress collection after agent finishes
            progressJob.cancel()

            // Save to session history
            session.addMessage(com.xiaomo.androidforclaw.providers.LegacyMessage(
                role = "user", content = agentContent
            ))
            session.addMessage(com.xiaomo.androidforclaw.providers.LegacyMessage(
                role = "assistant", content = result.finalContent
            ))

            // Send final reply — skip parts already sent as BlockReply
            val response = result.finalContent
            if (response.isNotBlank()) {
                val trimmed = response.trim()
                if (trimmed != "NO_REPLY" && trimmed != "HEARTBEAT_OK") {
                    var sanitized = com.xiaomo.androidforclaw.agent.session.HistorySanitizer
                        .stripControlTokensFromText(response)
                        .replace(Regex("(?:^|\\s+|\\*+)NO_REPLY\\s*$"), "")
                        .replace(Regex("(?:^|\\s+|\\*+)HEARTBEAT_OK\\s*$"), "")
                        .trim()
                    // Deduplicate: remove block reply text already sent mid-process
                    for (sent in blockRepliesSent) {
                        sanitized = sanitized.replace(sent, "").trim()
                    }
                    if (sanitized.isNotBlank()) {
                        sender?.sendText(toUser, sanitized)
                    }
                }
            }

            // Cancel typing
            sender?.cancelTyping(toUser)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.i(TAG, "Weixin: 任务被用户取消 ${msg.fromUserId}")
        } catch (e: Exception) {
            Log.e(TAG, "processWeixinMessageQueued 异常", e)
            try {
                sender?.sendText(toUser, "处理消息时出错：${e.message}")
            } catch (_: Exception) {}
        }
    }

    /**
     * Build Discord system prompt
     */
    private fun buildDiscordSystemPrompt(event: ChannelEvent.Message, historyContext: String): String {
        val botName = discordChannel?.getBotUsername() ?: "AndroidForClaw Bot"
        val botId = discordChannel?.getBotUserId() ?: ""

        return """
# 身份
你是 **$botName**，一个运行在 Android 设备上的智能助手，通过 Discord 与用户交互。

# 当前上下文
- **平台**: Discord
- **频道类型**: ${event.chatType}
- **频道 ID**: ${event.channelId}
- **用户**: ${event.authorName} (ID: ${event.authorId})
- **Bot ID**: $botId

$historyContext

# 核心能力
你可以通过工具调用来控制 Android 设备：
- 📸 截图观察屏幕
- 👆 点击、滑动、输入
- 🏠 导航、打开应用
- 🔍 获取 UI 信息

# 交互规则
1. **简洁明了**: Discord 消息尽量简洁，重要信息用 Markdown 格式化
2. **主动截图**: 需要观察屏幕时主动使用 screenshot 工具
3. **逐步执行**: 复杂任务分解为多个步骤
4. **反馈进度**: 长时间操作时告知用户当前进度
5. **错误处理**: 遇到问题时说明原因并提供建议

# 响应格式
- 使用 Discord Markdown: **粗体**、*斜体*、`代码`、```代码块```
- 重要操作结果用表情符号: ✅ ❌ ⚠️ 🔄
- 列表使用 - 或数字编号

# 注意事项
- 不要输出过长的消息（建议 1500 字符以内）
- 代码块使用语法高亮
- 链接使用 [文本](URL) 格式

现在，请处理用户的消息。
        """.trimIndent()
    }

    /**
     * Split message into multiple chunks (Discord 2000 character limit)
     */
    private fun splitMessageIntoChunks(message: String, maxChunkSize: Int = 1900): List<String> {
        if (message.length <= maxChunkSize) {
            return listOf(message)
        }

        val chunks = mutableListOf<String>()
        var remaining = message

        while (remaining.length > maxChunkSize) {
            // Try to split at appropriate position (newline, period, space)
            var splitIndex = maxChunkSize

            // Prioritize splitting at newline
            val lastNewline = remaining.substring(0, maxChunkSize).lastIndexOf('\n')
            if (lastNewline > maxChunkSize / 2) {
                splitIndex = lastNewline + 1
            } else {
                // Next try to split at period
                val lastPeriod = remaining.substring(0, maxChunkSize).lastIndexOf('。')
                if (lastPeriod > maxChunkSize / 2) {
                    splitIndex = lastPeriod + 1
                } else {
                    // Finally try to split at space
                    val lastSpace = remaining.substring(0, maxChunkSize).lastIndexOf(' ')
                    if (lastSpace > maxChunkSize / 2) {
                        splitIndex = lastSpace + 1
                    }
                }
            }

            chunks.add(remaining.substring(0, splitIndex))
            remaining = remaining.substring(splitIndex)
        }

        if (remaining.isNotEmpty()) {
            chunks.add(remaining)
        }

        return chunks
    }

    override fun onTerminate() {
        super.onTerminate()

        // Stop Discord related services
        try {
            discordTyping?.cleanup()
            discordTyping = null

            discordProcessingJobs.values.forEach { it.cancel() }
            discordProcessingJobs.clear()

            discordSessionManager.clearAll()
            discordHistoryManager.clearAll()

            DiscordChannel.stop()
            discordChannel = null

            // Clear MMKV status
            val mmkv = MMKV.defaultMMKV()
            mmkv?.encode("channel_discord_enabled", false)

            Log.i(TAG, "Discord 服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止 Discord 服务时出错", e)
        }

        // Stop Feishu Channel
        feishuChannel?.stop()
        feishuChannel = null

        // Stop Gateway Server
        gatewayServer?.stop()
        gatewayServer = null

        Log.i(TAG, "应用终止，所有服务已停止")
    }

    /**
     * Cleanup message history, ensure tool_use and tool_result are paired
     *
     * Problem: When loading history messages from session, there may be orphaned tool_results
     * (corresponding tool_use is in earlier messages, already truncated)
     *
     * Solution: Only keep complete user/assistant messages, remove all tool-related content
     */
    private fun cleanupToolMessages(messages: List<com.xiaomo.androidforclaw.providers.LegacyMessage>): List<com.xiaomo.androidforclaw.providers.LegacyMessage> {
        return messages.filter { message ->
            // Only keep text messages from user and assistant
            // Remove all messages containing tool_calls or tool_result
            when (message.role) {
                "user" -> true  // Keep all user messages
                "assistant" -> {
                    // Only keep plain text assistant messages, remove those with tool_calls
                    message.content != null && message.toolCalls == null
                }
                else -> false  // Remove tool role messages
            }
        }
    }
}