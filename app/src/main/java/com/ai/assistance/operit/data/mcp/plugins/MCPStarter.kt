package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.core.tools.system.Terminal
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

/**
 * MCP Plugin Starter
 *
 * Handles starting deployed MCP plugins via the bridge
 */
class MCPStarter(private val context: Context) {
    companion object {
        private const val TAG = "MCPStarter"
        private var bridgeInitialized = false
    }

    // Coroutine scope for async operations
    private val starterDispatcher = Dispatchers.IO.limitedParallelism(6)
    private val starterScope = CoroutineScope(starterDispatcher + SupervisorJob())
    private val terminal = Terminal.getInstance(context)
    private var pnpmInstalled: Boolean? = null

    /** Plugin initialization status enum */
    enum class PluginInitStatus {
        SUCCESS,
        TERMINAL_SERVICE_UNAVAILABLE,
        NODEJS_MISSING,
        BRIDGE_FAILED,
        OTHER_ERROR
    }

    /** Plugin start progress listener interface */
    interface PluginStartProgressListener {
        fun onPluginStarting(pluginId: String, index: Int, total: Int) {}
        fun onPluginRegistered(pluginId: String, serviceName: String, success: Boolean) {}
        fun onPluginStarted(pluginId: String, success: Boolean, index: Int, total: Int) {}
        fun onPluginLog(pluginId: String, message: String) {}
        fun onAllPluginsStarted(
            successCount: Int,
            totalCount: Int,
            status: PluginInitStatus = PluginInitStatus.SUCCESS
        ) {
        }

        fun onAllPluginsVerified(verificationResults: List<VerificationResult>) {}
    }

    /** Get or create shared session */
    private suspend fun getOrCreateSharedSession(): String? {
        return MCPSharedSession.getOrCreateSharedSession(context)
    }

    /** Check if pnpm is installed in terminal */
    private suspend fun isPnpmInstalled(): Boolean {
        if (pnpmInstalled != null) return pnpmInstalled == true

        val sessionId = getOrCreateSharedSession()
        if (sessionId == null) {
            pnpmInstalled = false
            return false
        }

        try {
            val result = terminal.executeCommand(sessionId, "command -v pnpm")
            val installed = result != null && result.contains("pnpm")
            pnpmInstalled = installed
            return installed
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking pnpm installation: ${e.message}")
            pnpmInstalled = false
            return false
        }
    }

    /** Check if terminal service is connected and initialized */
    private suspend fun isTerminalServiceConnected(): Boolean {
        if (terminal.isConnected()) return true
        return terminal.initialize()
    }

    /** Initialize and start the bridge */
    private suspend fun initBridge(): Boolean {
        // 检查 bridge 是否已经在运行
        val bridge = MCPBridge.getInstance(context)
        val listResult = bridge.listMcpServices()
        if (listResult != null && listResult.optBoolean("success", false)) {
            AppLogger.d(TAG, "Bridge is already running.")
            bridgeInitialized = true
            return true
        }

        // Bridge 未运行，需要启动
        AppLogger.d(TAG, "Bridge is not running, starting fresh...")

        // Check if terminal service is available
        if (!isTerminalServiceConnected()) {
            AppLogger.e(TAG, "Terminal service is not connected. Please start it first.")
            return false
        }

        // Check if pnpm is installed
        if (!isPnpmInstalled()) {
            AppLogger.e(TAG, "pnpm is not installed in terminal. Please install pnpm first.")
            return false
        }

        // Get shared session for deployment and starting
        val sessionId = getOrCreateSharedSession()
        if (sessionId == null) {
            AppLogger.e(TAG, "Failed to get shared session for bridge initialization")
            return false
        }

        // Deploy bridge to terminal
        if (!MCPBridge.deployBridge(context, sessionId)) {
            AppLogger.e(TAG, "Failed to deploy bridge")
            return false
        }

        // Start bridge
        if (!MCPBridge.startBridge(
                context = context,
                sessionId = null // Use a dedicated session for the bridge server
            )
        ) {
            AppLogger.e(TAG, "Failed to start bridge")
            return false
        }

        bridgeInitialized = true
        return true
    }

    /** Start a plugin without initializing bridge (for batch operations) */
    private suspend fun startPluginWithoutBridgeInit(
        pluginId: String,
        statusCallback: (StartStatus) -> Unit
    ): Boolean {
        return startPluginInternal(pluginId, statusCallback, initBridgeFirst = false)
    }

    /** Start a plugin using the bridge */
    suspend fun startPlugin(pluginId: String, statusCallback: (StartStatus) -> Unit): Boolean {
        return startPluginInternal(pluginId, statusCallback, initBridgeFirst = true)
    }

    private fun summarizeEnvKeys(env: Map<String, String>?): String {
        val keys = env?.keys
            ?.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotEmpty() } }
            ?.sorted()
            .orEmpty()
        return if (keys.isEmpty()) "(none)" else keys.joinToString(", ")
    }

    /** Internal plugin start logic */
    private suspend fun startPluginInternal(
        pluginId: String,
        statusCallback: (StartStatus) -> Unit,
        initBridgeFirst: Boolean
    ): Boolean {
        try {
            val mcpLocalServer = MCPLocalServer.getInstance(context)
            val mcpRepository = MCPRepository(context)
            AppLogger.d(TAG, "Refreshing MCP config before starting plugin: $pluginId")
            mcpRepository.refreshPluginList()

            val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)
            if (pluginInfo == null) {
                statusCallback(StartStatus.Error("Plugin info not found: $pluginId"))
                return false
            }

            val serviceType = pluginInfo.type

            // For local plugins, ensure the runtime workspace exists before spawn
            if (serviceType == "local") {
                val isRuntimeReady = mcpLocalServer.isPluginRuntimeReady(pluginId)
                if (!isRuntimeReady) {
                    // 自动准备运行目录
                    statusCallback(StartStatus.InProgress(context.getString(R.string.plugin_deploying, pluginId)))
                    AppLogger.d(TAG, "插件 $pluginId 运行目录未就绪，开始自动部署")

                    val pluginPath = mcpRepository.getInstalledPluginPath(pluginId)

                    if (pluginPath == null) {
                        statusCallback(StartStatus.Error(context.getString(R.string.plugin_cannot_get_path, pluginId)))
                        return false
                    }

                    // 使用MCPDeployer自动部署
                    val deployer = MCPDeployer(context)

                    // 对于虚拟路径（npx/uvx/uv 插件），直接使用空命令列表
                    val deployCommands = if (pluginPath.startsWith("virtual://")) {
                        emptyList()
                    } else {
                        deployer.getDeployCommands(pluginId, pluginPath)
                    }

                    // 只有非虚拟路径且命令为空时才报错
                    if (deployCommands.isEmpty() && !pluginPath.startsWith("virtual://")) {
                        statusCallback(StartStatus.Error(context.getString(R.string.plugin_cannot_determine_deploy, pluginId)))
                        return false
                    }

                    // 执行部署
                    var deploySuccess = false
                    deployer.deployPluginWithCommands(
                        pluginId = pluginId,
                        pluginPath = pluginPath,
                        customCommands = deployCommands,
                        environmentVariables = emptyMap(),
                        statusCallback = { deployStatus ->
                            when (deployStatus) {
                                is MCPDeployer.DeploymentStatus.Success -> {
                                    deploySuccess = true
                                    statusCallback(StartStatus.InProgress(context.getString(R.string.plugin_deploy_success, pluginId)))
                                }

                                is MCPDeployer.DeploymentStatus.Error -> {
                                    statusCallback(StartStatus.Error(context.getString(R.string.plugin_deploy_failed, deployStatus.message)))
                                }

                                is MCPDeployer.DeploymentStatus.InProgress -> {
                                    statusCallback(StartStatus.InProgress(deployStatus.message))
                                }

                                else -> {}
                            }
                        }
                    )

                    if (!deploySuccess) {
                        statusCallback(StartStatus.Error(context.getString(R.string.plugin_deploy_error, pluginId)))
                        return false
                    }

                    statusCallback(StartStatus.InProgress(context.getString(R.string.plugin_deploy_complete, pluginId)))
                }
            }

            // Check if plugin is enabled by the user
            val isEnabled = mcpLocalServer.isServerEnabled(pluginId) // 从配置读取
            if (!isEnabled) {
                statusCallback(StartStatus.Error("Plugin not enabled by user: $pluginId"))
                return false
            }

            statusCallback(StartStatus.InProgress("Starting plugin: $pluginId"))

            val serverName = pluginInfo.name.replace(" ", "_").lowercase()
                .ifEmpty { pluginId.split("/").last().lowercase() }

            // Handle remote services differently
            if (serviceType == "remote") {
                val endpoint = pluginInfo.endpoint
                val connectionType = pluginInfo.connectionType
                val bearerToken = pluginInfo.bearerToken
                val headers = pluginInfo.headers

                AppLogger.d(TAG, "启动远程服务 $pluginId: endpoint=$endpoint, bearerToken=${bearerToken?.take(10)}..., headers=${headers?.keys}")

                if (endpoint == null) {
                    statusCallback(StartStatus.Error("Remote service is missing endpoint: $pluginId"))
                    return false
                }

                // Initialize bridge only if requested
                if (initBridgeFirst && !initBridge()) {
                    statusCallback(StartStatus.Error("Failed to initialize bridge for remote service"))
                    return false
                }

                val bridge = MCPBridge.getInstance(context)

                // Register remote service with the bridge
                val registerResult =
                    bridge.registerMcpService(
                        name = serverName,
                        type = "remote",
                        endpoint = endpoint,
                        connectionType = connectionType,
                        description = "Remote MCP Server: $pluginId",
                        bearerToken = bearerToken,
                        headers = headers
                    )

                if (registerResult == null || !registerResult.optBoolean("success", false)) {
                    statusCallback(StartStatus.Error("Failed to register remote MCP service"))
                    return false
                }

                // "Connect" the remote service to trigger a connection and verify
                val client = MCPBridgeClient(context, serverName)
                val connectSuccess = client.connect() // connect will try to spawn if not active
                if (!connectSuccess) {
                    statusCallback(StartStatus.Error("Failed to connect to remote MCP service"))
                    return false
                }

                statusCallback(StartStatus.Success("Remote service $pluginId connected successfully"))
                return true
            }

            // --- Existing logic for local plugins ---
            val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
            val config = parseConfigJson(pluginConfig)
            val extractedServerName =
                extractServerNameFromConfig(pluginConfig) ?: serverName

            // Get server command and args
            val serverConfig = config?.mcpServers?.get(extractedServerName)
            if (serverConfig == null) {
                statusCallback(StartStatus.Error("Invalid plugin config: $pluginId"))
                return false
            }

            AppLogger.d(
                TAG,
                "Local plugin $pluginId loaded env keys: ${summarizeEnvKeys(serverConfig.env)}"
            )

            // Check if plugin service is already running
            val clientForCheck = MCPBridgeClient(context, extractedServerName)
            if (clientForCheck.isActive()) {
                statusCallback(StartStatus.Success("Plugin $pluginId is already running"))
                return true
            }

            // Initialize bridge only if requested (for single plugin start)
            if (initBridgeFirst) {
                if (!initBridge()) {
                    when {
                        !isTerminalServiceConnected() -> {
                            statusCallback(StartStatus.TerminalServiceUnavailable(context.getString(R.string.plugin_terminal_service_unavailable)))
                        }

                        !isPnpmInstalled() -> {
                            statusCallback(StartStatus.PnpmMissing(context.getString(R.string.plugin_pnpm_missing)))
                        }

                        else -> {
                            statusCallback(StartStatus.Error("Failed to initialize bridge"))
                        }
                    }
                    return false
                }
            }

            statusCallback(StartStatus.InProgress("Starting plugin via bridge..."))

            // Use MCPBridge instance
            val bridge = MCPBridge.getInstance(context)
            val termuxPluginDir = mcpLocalServer.getPluginRuntimeDirectory(pluginId)

            // Register MCP service
            val registerResult =
                bridge.registerMcpService(
                    name = extractedServerName,
                    command = serverConfig.command,
                    args = serverConfig.args ?: emptyList(),
                    description = "MCP Server: $pluginId",
                    env = serverConfig.env ?: emptyMap(),
                    cwd = termuxPluginDir
                )

            if (registerResult == null || !registerResult.optBoolean("success", false)) {
                statusCallback(StartStatus.Error("Failed to register MCP service"))
                return false
            }

            // Start and verify MCP service using the client
            val client = MCPBridgeClient(context, extractedServerName)
            val connectSuccess = client.connect()

            if (connectSuccess) {
                statusCallback(StartStatus.Success("Service $pluginId started successfully"))
                return true
            } else {
                statusCallback(StartStatus.Error("Service $pluginId started but is not active"))
                return false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error starting plugin", e)
            statusCallback(StartStatus.Error("Start error: ${e.message}"))
            return false
        }
    }

    /** Start all deployed plugins */
    fun startAllDeployedPlugins(
        progressListener: PluginStartProgressListener = object : PluginStartProgressListener {}
    ) {
        starterScope.launch {
            try {
                // Check if terminal service is available
                if (!isTerminalServiceConnected()) {
                    AppLogger.e(TAG, "Terminal service is not connected. Please start it first.")
                    progressListener.onAllPluginsStarted(
                        0,
                        0,
                        PluginInitStatus.TERMINAL_SERVICE_UNAVAILABLE
                    )
                    return@launch
                }

                // Initialize bridge ONCE, this will also reset existing services
                if (!initBridge()) {
                    val status =
                        if (pnpmInstalled == false) PluginInitStatus.NODEJS_MISSING else PluginInitStatus.BRIDGE_FAILED
                    progressListener.onAllPluginsStarted(0, 0, status)
                    return@launch
                }

                val mcpRepository = MCPRepository(context)
                val mcpLocalServer = MCPLocalServer.getInstance(context)
                val bridge = MCPBridge.getInstance(context)
                AppLogger.d(TAG, "Refreshing MCP config before batch startup")
                mcpRepository.refreshPluginList()

                // Get all installed plugins and partition into enabled and disabled
                val allInstalledPlugins = mcpRepository.installedPluginIds.first()
                val (pluginsToStart, disabledPlugins) = allInstalledPlugins.partition { pluginId ->
                    mcpLocalServer.isServerEnabled(pluginId)
                }

                // Get the list of currently registered services from the bridge
                val listResponse = bridge.listMcpServices()
                val registeredServiceNames = mutableListOf<String>()
                if (listResponse?.optBoolean("success", false) == true) {
                    val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                    if (services != null) {
                        for (i in 0 until services.length()) {
                            services.optJSONObject(i)?.optString("name")?.let { registeredServiceNames.add(it) }
                        }
                    }
                }

                // Unregister any disabled plugins that are still registered in the bridge
                if (registeredServiceNames.isNotEmpty() && disabledPlugins.isNotEmpty()) {
                    starterScope.launch { // Unregister in a separate coroutine to not block startup sequence
                        for (pluginId in disabledPlugins) {
                            try {
                                // Determine the service name for the disabled plugin
                                val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId) ?: continue
                                val baseServerName = pluginInfo.name.replace(" ", "_").lowercase()
                                    .ifEmpty { pluginId.split("/").last().lowercase() }

                                val serviceNameToUnregister = if (pluginInfo.type == "local") {
                                    val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
                                    extractServerNameFromConfig(pluginConfig) ?: baseServerName
                                } else {
                                    baseServerName
                                }

                                // If the service is in the registered list, unregister it
                                if (registeredServiceNames.contains(serviceNameToUnregister)) {
                                    AppLogger.d(TAG, "Unregistering disabled plugin '$pluginId' with service name '$serviceNameToUnregister'")
                                    bridge.unregisterMcpService(serviceNameToUnregister)
                                }
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "Failed to unregister disabled plugin '$pluginId'", e)
                            }
                        }

                        runCatching {
                            mcpRepository.unregisterToolsForPlugins(disabledPlugins)
                        }.onFailure { e ->
                            AppLogger.e(TAG, "Failed to unregister runtime MCP tools for disabled plugins", e)
                        }
                    }
                }

                if (pluginsToStart.isEmpty()) {
                    progressListener.onAllPluginsStarted(0, 0, PluginInitStatus.SUCCESS)
                    return@launch
                }

                // 将注册与处理串联到同一个插件任务中，避免“全部先转 loading，再同时完成”的体验
                val allVerificationResults = mutableListOf<VerificationResult>()
                val batchSize = 4
                val semaphore = Semaphore(batchSize)
                var pluginsProcessingStartedCount = 0
                var pluginsProcessedCount = 0
                val totalPluginsToProcess = pluginsToStart.size

                val jobs =
                    pluginsToStart.map { pluginId ->
                        async {
                            val serviceName = registerPlugin(pluginId, progressListener)
                            if (serviceName == null) {
                                progressListener.onPluginRegistered(pluginId, "", false)

                                val currentIndex = synchronized(this@MCPStarter) {
                                    pluginsProcessedCount++
                                    pluginsProcessedCount
                                }

                                progressListener.onPluginStarted(
                                    pluginId,
                                    false,
                                    currentIndex,
                                    totalPluginsToProcess
                                )
                                return@async
                            }

                            progressListener.onPluginRegistered(pluginId, serviceName, true)

                            val result = semaphore.withPermit {
                                val startIndex = synchronized(this@MCPStarter) {
                                    pluginsProcessingStartedCount++
                                    pluginsProcessingStartedCount
                                }

                                progressListener.onPluginStarting(
                                    pluginId,
                                    startIndex,
                                    totalPluginsToProcess
                                )

                                processPlugin(pluginId, serviceName, progressListener)
                            }

                            synchronized(allVerificationResults) {
                                allVerificationResults.add(result)
                            }

                            val currentIndex = synchronized(this@MCPStarter) {
                                pluginsProcessedCount++
                                pluginsProcessedCount
                            }

                            progressListener.onPluginStarted(
                                result.pluginId,
                                result.isResponding,
                                currentIndex,
                                totalPluginsToProcess
                            )
                            delay(150)
                        }
                    }

                jobs.awaitAll()

                val successfulResults = allVerificationResults.filter { it.isResponding }
                if (successfulResults.isNotEmpty()) {
                    registerToolsForVerifiedPlugins(successfulResults)
                    generateMissingDescriptions(successfulResults)
                }

                val successCount = successfulResults.size
                AppLogger.i(TAG, "All plugin batches processed. Total successful: $successCount")

                progressListener.onAllPluginsStarted(
                    successCount,
                    pluginsToStart.size,
                    PluginInitStatus.SUCCESS
                )

            } catch (e: Exception) {
                AppLogger.e(TAG, "Error starting plugins", e)
                progressListener.onAllPluginsStarted(0, 0, PluginInitStatus.OTHER_ERROR)
            }
        }
    }

    private suspend fun processPlugin(
        pluginId: String,
        serviceName: String,
        progressListener: PluginStartProgressListener
    ): VerificationResult {
        val mcpLocalServer = MCPLocalServer.getInstance(context)
        val mcpRepository = MCPRepository(context)
        val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)

        val needsSpawning =
            !mcpLocalServer.hasValidToolCache(pluginId) || pluginInfo?.description.isNullOrBlank()

        if (needsSpawning) {
            AppLogger.d(TAG, "Spawning plugin for processing: $pluginId")
            val client = MCPBridgeClient(context, serviceName)

            val startTime = System.currentTimeMillis()
            val spawnResp = client.spawnBlocking()
            val responseTime = System.currentTimeMillis() - startTime

            val success = spawnResp?.optBoolean("success", false) == true
            val ready = spawnResp?.optJSONObject("result")?.optBoolean("ready", false) == true

            val logsObj =
                if (success) {
                    spawnResp?.optJSONObject("result")
                } else {
                    spawnResp?.optJSONObject("error")?.optJSONObject("data")
                }

            val lastError = logsObj?.optString("lastError").orEmpty()
            val logs = logsObj?.optString("logs").orEmpty()

            val result: VerificationResult
            if (success && ready) {
                cacheToolsFromService(pluginId, serviceName)
                result = VerificationResult(
                    pluginId,
                    serviceName,
                    true,
                    responseTime,
                    "Service is responding"
                )
            } else {
                val errorMessage =
                    spawnResp?.optJSONObject("error")?.optString("message")
                        ?: "Service not responding"

                if (lastError.isNotBlank()) {
                    progressListener.onPluginLog(pluginId, context.getString(R.string.plugin_runtime_error, lastError))
                }
                if (logs.isNotBlank()) {
                    progressListener.onPluginLog(pluginId, context.getString(R.string.plugin_runtime_output, logs))
                }

                result = VerificationResult(
                    pluginId,
                    serviceName,
                    false,
                    0,
                    errorMessage
                )
            }

            client.unspawn()
            return result
        } else {
            AppLogger.d(TAG, "Processing cached plugin: $pluginId")
            sendCachedToolsToBridge(pluginId, serviceName)
            return VerificationResult(pluginId, serviceName, true, 0, "Using cached tools")
        }
    }

    private suspend fun registerPlugin(
        pluginId: String,
        progressListener: PluginStartProgressListener? = null
    ): String? {
        try {
            val mcpRepository = MCPRepository(context)
            val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId) ?: return null

            if (pluginInfo.type == "local") {
                val mcpLocalServer = MCPLocalServer.getInstance(context)
                if (!mcpLocalServer.isPluginRuntimeReady(pluginId)) {
                    val deployer = MCPDeployer(context)
                    val pluginPath = mcpRepository.getInstalledPluginPath(pluginId) ?: return null
                    progressListener?.onPluginLog(pluginId, context.getString(R.string.plugin_auto_deploy))
                    val deployCommands =
                        if (pluginPath.startsWith("virtual://")) emptyList() else deployer.getDeployCommands(
                            pluginId,
                            pluginPath
                        )

                    if (deployCommands.isEmpty() && !pluginPath.startsWith("virtual://")) return null

                    var deploySuccess = false
                    deployer.deployPluginWithCommands(
                        pluginId,
                        pluginPath,
                        deployCommands,
                        emptyMap()
                    ) { status ->
                        when (status) {
                            is MCPDeployer.DeploymentStatus.InProgress -> {
                                progressListener?.onPluginLog(pluginId, status.message)
                            }

                            is MCPDeployer.DeploymentStatus.Error -> {
                                progressListener?.onPluginLog(pluginId, status.message)
                            }

                            is MCPDeployer.DeploymentStatus.Success -> {
                                progressListener?.onPluginLog(pluginId, status.message)
                            }

                            else -> {}
                        }
                        if (status is MCPDeployer.DeploymentStatus.Success) deploySuccess = true
                    }
                    if (!deploySuccess) return null
                    progressListener?.onPluginLog(pluginId, context.getString(R.string.plugin_auto_deploy_complete))
                }
            }

            val bridge = MCPBridge.getInstance(context)
            val serverName =
                pluginInfo.name.replace(" ", "_").lowercase().ifEmpty {
                    pluginId.split("/").last().lowercase()
                }
            var actualServiceName = serverName

            val registerResult =
                when (pluginInfo.type) {
                    "remote" -> {
                        if (pluginInfo.endpoint == null) {
                            JSONObject().apply {
                                put("success", false)
                                put("error", "Endpoint is missing for remote plugin $pluginId")
                            }
                        } else {
                            bridge.registerMcpService(
                                name = serverName,
                                type = "remote",
                                endpoint = pluginInfo.endpoint,
                                connectionType = pluginInfo.connectionType,
                                description = "Remote MCP Server: $pluginId",
                                bearerToken = pluginInfo.bearerToken,
                                headers = pluginInfo.headers
                            )
                        }
                    }

                    "local" -> {
                        val mcpLocalServer = MCPLocalServer.getInstance(context)
                        val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
                        val config = parseConfigJson(pluginConfig)
                        val extractedServerName = extractServerNameFromConfig(pluginConfig) ?: serverName
                        val serverConfig = config?.mcpServers?.get(extractedServerName) ?: return null
                        val termuxPluginDir = mcpLocalServer.getPluginRuntimeDirectory(pluginId)
                        val envKeysSummary = summarizeEnvKeys(serverConfig.env)

                        actualServiceName = extractedServerName
                        AppLogger.d(
                            TAG,
                            "Registering local plugin $pluginId with env keys: $envKeysSummary"
                        )
                        progressListener?.onPluginLog(pluginId, "读取到配置 env 键: $envKeysSummary")

                        bridge.registerMcpService(
                            name = extractedServerName,
                            command = serverConfig.command,
                            args = serverConfig.args ?: emptyList(),
                            description = "MCP Server: $pluginId",
                            env = serverConfig.env ?: emptyMap(),
                            cwd = termuxPluginDir
                        )
                    }

                    else -> null
                }

            return if (registerResult?.optBoolean("success", false) == true) actualServiceName else null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to register plugin $pluginId", e)
            return null
        }
    }

    /** Verify plugin statuses */
    private fun verifyPlugins(progressListener: PluginStartProgressListener) {
        starterScope.launch {
            try {
                delay(5000) // Wait for services to initialize
                val results = verifyAllMcpPlugins()

                // 自动生成空描述的工具包描述
                generateMissingDescriptions(results)

                // 注册验证成功的插件的工具
                registerToolsForVerifiedPlugins(results)

                progressListener.onAllPluginsVerified(results)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error verifying plugins", e)
                progressListener.onAllPluginsVerified(emptyList())
            }
        }
    }

    /**
     * 从服务缓存工具列表
     */
    private suspend fun cacheToolsFromService(pluginId: String, serviceName: String) {
        try {
            val mcpLocalServer = MCPLocalServer.getInstance(context)
            
            // 检查是否已有有效缓存
            if (mcpLocalServer.hasValidToolCache(pluginId)) {
                AppLogger.d(TAG, "插件 $pluginId 已有工具缓存，跳过")
                return
            }

            AppLogger.d(TAG, "开始为插件 $pluginId 缓存工具列表")
            val client = MCPBridgeClient(context, serviceName)
            val tools = client.getTools()

            if (tools.isNotEmpty()) {
                val cachedTools = tools.map { toolJson ->
                    val name = toolJson.optString("name", "")
                    val description = toolJson.optString("description", "")
                    val inputSchema = toolJson.optJSONObject("inputSchema")?.toString() ?: "{}"
                    
                    MCPLocalServer.CachedToolInfo(
                        name = name,
                        description = description,
                        inputSchema = inputSchema,
                        cachedAt = System.currentTimeMillis()
                    )
                }
                
                mcpLocalServer.cacheServerTools(pluginId, cachedTools)
                AppLogger.i(TAG, "成功缓存插件 $pluginId 的 ${cachedTools.size} 个工具")
            } else {
                AppLogger.w(TAG, "插件 $pluginId 没有返回任何工具")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "缓存插件 $pluginId 的工具列表时出错", e)
        }
    }

    /**
     * 将缓存的工具发送到bridge
     * 用于已有缓存的插件，使得bridge能返回工具列表而无需spawn插件
     */
    private suspend fun sendCachedToolsToBridge(pluginId: String, serviceName: String) {
        try {
            val mcpLocalServer = MCPLocalServer.getInstance(context)
            val cachedTools = mcpLocalServer.getCachedTools(pluginId)

            if (cachedTools == null || cachedTools.isEmpty()) {
                AppLogger.w(TAG, "插件 $pluginId 没有缓存的工具，跳过发送")
                return
            }

            AppLogger.d(TAG, "将插件 $pluginId 的 ${cachedTools.size} 个缓存工具发送到bridge")

            // 将CachedToolInfo转换为JSONObject格式
            val toolJsonList = cachedTools.map { cachedTool: MCPLocalServer.CachedToolInfo ->
                JSONObject().apply {
                    put("name", cachedTool.name)
                    put("description", cachedTool.description)
                    put("inputSchema", JSONObject(cachedTool.inputSchema))
                }
            }

            // 发送到bridge
            val bridge = MCPBridge.getInstance(context)
            val result = bridge.cacheTools(serviceName, toolJsonList)

            if (result?.optBoolean("success", false) == true) {
                AppLogger.i(TAG, "成功将插件 $pluginId 的工具缓存发送到bridge")
            } else {
                val error = result?.optJSONObject("error")?.optString("message") ?: "Unknown error"
                AppLogger.w(TAG, "发送工具缓存到bridge失败: $error")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "发送插件 $pluginId 的缓存工具到bridge时出错", e)
        }
    }

    /**
     * 为验证成功的插件注册工具
     * 这确保只有真正就绪并响应的插件才会注册其工具
     */
    private suspend fun registerToolsForVerifiedPlugins(results: List<VerificationResult>) {
        try {
            val mcpRepository = MCPRepository(context)
            val successfulPluginIds = results
                .filter { it.isResponding }
                .map { it.pluginId }

            if (successfulPluginIds.isNotEmpty()) {
                AppLogger.d(
                    TAG,
                    "开始为 ${successfulPluginIds.size} 个验证成功的插件注册工具: $successfulPluginIds"
                )
                mcpRepository.registerToolsForLoadedPlugins(successfulPluginIds)
                AppLogger.d(TAG, "工具注册流程已完成")
            } else {
                AppLogger.d(TAG, "没有验证成功的插件，跳过工具注册")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "注册验证成功插件的工具时出错", e)
        }
    }

    /**
     * 为没有描述的工具包自动生成描述
     */
    private suspend fun generateMissingDescriptions(results: List<VerificationResult>) {
        try {
            val mcpRepository = MCPRepository(context)
            val mcpLocalServer = MCPLocalServer.getInstance(context)

            // 筛选出成功响应的插件
            val respondingPlugins = results.filter { it.isResponding }

            for (result in respondingPlugins) {
                try {
                    // 获取插件信息
                    val pluginInfo = mcpRepository.getInstalledPluginInfo(result.pluginId)

                    // 检查描述是否为空
                    if (pluginInfo != null && pluginInfo.description.isBlank()) {
                        AppLogger.d(TAG, "为插件 ${result.pluginId} 生成描述，当前描述为空")

                        // 获取工具描述
                        val client = MCPBridgeClient(context, result.serviceName)
                        val toolDescriptions = client.getToolDescriptions()

                        if (toolDescriptions.isNotEmpty()) {
                            // 调用EnhancedAIService生成描述
                            val generatedDescription =
                                com.ai.assistance.operit.api.chat.EnhancedAIService.generatePackageDescription(
                                    context = context,
                                    pluginName = pluginInfo.name,
                                    toolDescriptions = toolDescriptions
                                )

                            // 只有在AI成功生成描述时才保存，失败时保持原有的空描述
                            if (generatedDescription.isNotBlank()) {
                                val updatedMetadata =
                                    pluginInfo.copy(description = generatedDescription)
                                mcpLocalServer.addOrUpdatePluginMetadata(updatedMetadata)
                                AppLogger.i(
                                    TAG,
                                    "已为插件 ${result.pluginId} 生成描述: $generatedDescription"
                                )
                            } else {
                                AppLogger.w(TAG, "插件 ${result.pluginId} 的描述生成失败，保持原有空描述")
                            }
                        } else {
                            AppLogger.w(TAG, "插件 ${result.pluginId} 没有可用的工具描述")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "为插件 ${result.pluginId} 生成描述时出错: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成缺失描述时出错", e)
        }
    }

    /** Verify all MCP plugins */
    suspend fun verifyAllMcpPlugins(): List<VerificationResult> {
        val results = mutableListOf<VerificationResult>()

        try {
            val mcpRepository = MCPRepository(context)
            val mcpLocalServer = MCPLocalServer.getInstance(context)

            // Get plugins whose runtime workspaces are ready
            val pluginList = mcpRepository.installedPluginIds.first()
            val runtimeReadyPlugins = pluginList.filter { pluginId ->
                mcpLocalServer.isPluginRuntimeReady(pluginId)
            }

            // Get registered services
            val bridge = MCPBridge.getInstance(context)
            val listResponse = bridge.listMcpServices()
            val servicesList = mutableListOf<String>()

            if (listResponse?.optBoolean("success", false) == true) {
                val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                if (services != null) {
                    for (i in 0 until services.length()) {
                        val name = services.optJSONObject(i)?.optString("name", "")
                        if (!name.isNullOrEmpty()) {
                            servicesList.add(name)
                        }
                    }
                }
            }

            // Verify each plugin
            for (pluginId in runtimeReadyPlugins) {
                val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
                val serverName =
                    extractServerNameFromConfig(pluginConfig)
                        ?: pluginId.split("/").last().lowercase()

                if (!servicesList.contains(serverName)) {
                    results.add(
                        VerificationResult(
                            pluginId = pluginId,
                            serviceName = serverName,
                            isResponding = false,
                            responseTime = 0,
                            details = "Service not registered"
                        )
                    )
                    continue
                }

                // Verify service status
                val client = MCPBridgeClient(context, serverName)
                val startTime = System.currentTimeMillis()
                val pingSuccess = client.pingSync()
                val responseTime = System.currentTimeMillis() - startTime

                if (pingSuccess) {
                    results.add(
                        VerificationResult(
                            pluginId = pluginId,
                            serviceName = serverName,
                            isResponding = true,
                            responseTime = responseTime,
                            details = "Service is responding"
                        )
                    )
                } else {
                    results.add(
                        VerificationResult(
                            pluginId = pluginId,
                            serviceName = serverName,
                            isResponding = false,
                            responseTime = 0,
                            details = "Service not responding"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error verifying plugins", e)
        }

        return results
    }

    /** Extract server name from config */
    private fun extractServerNameFromConfig(configJson: String): String? {
        if (configJson.isBlank()) return null

        try {
            val jsonObject = JsonParser.parseString(configJson).asJsonObject
            val mcpServers = jsonObject.getAsJsonObject("mcpServers")
            return mcpServers?.keySet()?.firstOrNull()
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析配置JSON失败", e)
            return null
        }
    }

    /** Parse config JSON to MCPConfig */
    private fun parseConfigJson(configJson: String): MCPLocalServer.MCPConfig? {
        if (configJson.isBlank()) return null

        try {
            return Gson().fromJson(configJson, MCPLocalServer.MCPConfig::class.java)
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析配置JSON失败", e)
            return null
        }
    }

    /** Register server if needed */
    private fun registerServerIfNeeded(
        serverName: String,
        serverConfig: MCPLocalServer.MCPConfig.ServerConfig,
        pluginId: String
    ) {
        try {
            val mcpManager = MCPManager.getInstance(context)

            val extraDataMap = mutableMapOf<String, String>()
            extraDataMap["command"] = serverConfig.command
            extraDataMap["args"] = serverConfig.args?.joinToString(",") ?: ""

            serverConfig.env?.forEach { (key, value) -> extraDataMap["env_$key"] = value }

            val mcpServerConfig =
                MCPServerConfig(
                    name = serverName,
                    endpoint = "mcp://plugin/$serverName",
                    description = "MCP Server from plugin: $pluginId",
                    capabilities = listOf("tools", "resources"),
                    extraData = extraDataMap
                )

            mcpManager.registerServer(serverName, mcpServerConfig)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to register server", e)
        }
    }

    /** Start status */
    sealed class StartStatus {
        object NotStarted : StartStatus()
        data class InProgress(val message: String) : StartStatus()
        data class Success(val message: String) : StartStatus()
        data class Error(val message: String) : StartStatus()
        data class TerminalServiceUnavailable(
            val message: String = ""
        ) : StartStatus()

        data class PnpmMissing(
            val message: String = ""
        ) : StartStatus()
    }

    /** Verification result */
    data class VerificationResult(
        val pluginId: String,
        val serviceName: String,
        val isResponding: Boolean,
        val responseTime: Long,
        val details: String = ""
    )
}

 
