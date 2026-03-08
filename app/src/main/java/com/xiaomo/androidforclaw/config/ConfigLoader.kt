package com.xiaomo.androidforclaw.config

import android.content.Context
import android.os.FileObserver
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * 配置加载器 - 对齐 OpenClaw 的配置加载逻辑
 *
 * 功能：
 * 1. 从 JSON 文件加载配置
 * 2. 支持环境变量替换 (${VAR_NAME})
 * 3. 支持默认配置
 * 4. 配置验证
 *
 * 参考：OpenClaw src/agents/models-config.ts
 */
class ConfigLoader(private val context: Context) {

    companion object {
        private const val TAG = "ConfigLoader"

        // 配置文件路径 (对齐 OpenClaw: ~/.openclaw/)
        private const val CONFIG_DIR = "/sdcard/.androidforclaw/config"
        private const val OPENCLAW_CONFIG_FILE = "openclaw.json"


        // 默认 OpenClaw 配置
        private val DEFAULT_OPENCLAW_CONFIG = """
        {
          "version": "1.0.0",
          "thinking": {
            "enabled": true,
            "budgetTokens": 10000,
            "showInUI": true,
            "logToFile": false
          },
          "agent": {
            "name": "androidforclaw",
            "maxIterations": 20,
            "defaultModel": "ppio/pa/claude-opus-4-6",
            "timeout": 300000,
            "retryOnError": true,
            "maxRetries": 3,
            "mode": "exploration"
          },
          "models": {
            "mode": "merge",
            "providers": {
              "anthropic": {
                "baseUrl": "https://openrouter.ai/api/v1",
                "apiKey": "${'$'}{ANTHROPIC_API_KEY}",
                "api": "anthropic-messages",
                "authHeader": true,
                "models": [
                  {
                    "id": "ppio/pa/claude-opus-4-6",
                    "name": "Claude Opus 4.6",
                    "reasoning": true,
                    "input": ["text", "image"],
                    "cost": {
                      "input": 15.0,
                      "output": 75.0,
                      "cacheRead": 1.5,
                      "cacheWrite": 18.75
                    },
                    "contextWindow": 200000,
                    "maxTokens": 16384
                  },
                  {
                    "id": "ppio/pa/claude-sonnet-4-6",
                    "name": "Claude Sonnet 4.6",
                    "reasoning": true,
                    "input": ["text", "image"],
                    "cost": {
                      "input": 3.0,
                      "output": 15.0,
                      "cacheRead": 0.3,
                      "cacheWrite": 3.75
                    },
                    "contextWindow": 200000,
                    "maxTokens": 16384
                  },
                  {
                    "id": "ppio/pa/claude-sonnet-4-5-20250929",
                    "name": "Claude Sonnet 4.5",
                    "reasoning": true,
                    "input": ["text", "image"],
                    "cost": {
                      "input": 3.0,
                      "output": 15.0,
                      "cacheRead": 0.3,
                      "cacheWrite": 3.75
                    },
                    "contextWindow": 200000,
                    "maxTokens": 8192
                  }
                ]
              }
            }
          },
          "skills": {
            "bundledPath": "assets/skills",
            "workspacePath": "/sdcard/.androidforclaw/workspace/skills",
            "managedPath": "/sdcard/.androidforclaw/skills",
            "autoLoad": ["mobile-operations"],
            "disabled": [],
            "onDemand": true,
            "cacheEnabled": true
          },
          "tools": {
            "screenshot": {
              "enabled": true,
              "quality": 85,
              "maxWidth": 1080,
              "format": "jpeg",
              "hideFloatingWindow": true
            },
            "accessibility": {
              "enabled": true,
              "gestureDuration": 100,
              "enableUITree": true,
              "maxUITreeDepth": 20
            },
            "exec": {
              "enabled": true,
              "allowRoot": false,
              "timeout": 30000,
              "blocklist": ["rm -rf /", "dd if=", "format"]
            },
            "browser": {
              "enabled": true,
              "userAgent": null,
              "timeout": 30000
            }
          },
          "gateway": {
            "enabled": true,
            "port": 8080,
            "host": "0.0.0.0",
            "security": {
              "enabled": false,
              "pairingRequired": false,
              "allowlist": [],
              "rateLimit": {
                "enabled": false,
                "maxRequests": 100,
                "windowMs": 60000
              }
            },
            "channels": ["app", "webui", "adb"],
            "feishu": {
              "enabled": false,
              "appId": "",
              "appSecret": "",
              "encryptKey": null,
              "verificationToken": null,
              "domain": "feishu",
              "connectionMode": "websocket",
              "webhookPath": "/feishu/webhook",
              "webhookPort": 8765,
              "dmPolicy": "pairing",
              "allowFrom": [],
              "groupPolicy": "allowlist",
              "groupAllowFrom": [],
              "requireMention": true,
              "groupCommandMentionBypass": "never",
              "allowMentionlessInMultiBotGroup": false,
              "topicSessionMode": "disabled",
              "historyLimit": 20,
              "dmHistoryLimit": 100,
              "textChunkLimit": 4000,
              "chunkMode": "length",
              "mediaMaxMb": 20.0,
              "audioMaxDurationSec": 300,
              "enableDocTools": true,
              "enableWikiTools": true,
              "enableDriveTools": true,
              "enableBitableTools": true,
              "enableTaskTools": true,
              "enableChatTools": true,
              "enablePermTools": true,
              "enableUrgentTools": true,
              "typingIndicator": true,
              "reactionDedup": true,
              "debugMode": false
            }
          },
          "ui": {
            "floatingWindow": {
              "enabled": true,
              "showProgress": true,
              "showReasoningContent": true,
              "autoHide": false,
              "opacity": 0.9,
              "position": "top-right"
            },
            "theme": "auto",
            "language": "zh"
          },
          "logging": {
            "level": "INFO",
            "logToFile": true,
            "logPath": "/sdcard/.androidforclaw/logs",
            "maxFileSize": 10485760,
            "maxFiles": 5,
            "includeTimestamp": true,
            "logLLMCalls": true,
            "logToolCalls": true
          },
          "memory": {
            "enabled": true,
            "path": "/sdcard/.androidforclaw/workspace/memory",
            "autoSave": true,
            "maxEntries": 1000
          },
          "session": {
            "defaultKey": "default",
            "storagePath": "/data/data/com.xiaomo.androidforclaw/files/sessions",
            "autoSave": true,
            "maxMessages": 100,
            "compression": false
          }
        }
        """.trimIndent()
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val configDir = File(CONFIG_DIR)
    private val openclawConfigFile = File(configDir, OPENCLAW_CONFIG_FILE)

    // 配置缓存
    private var cachedOpenClawConfig: OpenClawConfig? = null
    private var openclawConfigCacheValid = false

    // 热重载支持
    private var fileObserver: FileObserver? = null
    private var hotReloadEnabled = false
    private var reloadCallback: ((OpenClawConfig) -> Unit)? = null

    /**
     * 加载 OpenClaw 主配置（带自动备份和恢复）
     */
    fun loadOpenClawConfig(): OpenClawConfig {
        // 如果缓存有效，直接返回
        if (openclawConfigCacheValid && cachedOpenClawConfig != null) {
            Log.d(TAG, "返回缓存的 OpenClaw 配置")
            return cachedOpenClawConfig!!
        }

        // 使用 ConfigBackupManager 安全加载
        val backupManager = ConfigBackupManager(context)
        val config = backupManager.loadConfigSafely {
            loadOpenClawConfigInternal()
        }

        if (config != null) {
            cachedOpenClawConfig = config
            openclawConfigCacheValid = true
            return config
        } else {
            // 如果所有恢复都失败，返回默认配置
            Log.w(TAG, "使用默认配置")
            val defaultConfig = createDefaultOpenClawConfigObject()
            cachedOpenClawConfig = defaultConfig
            openclawConfigCacheValid = true
            return defaultConfig
        }
    }

    /**
     * 内部加载方法（不带容错）
     */
    private fun loadOpenClawConfigInternal(): OpenClawConfig {
        try {
            // 确保配置目录存在
            ensureConfigDir()

            // 如果配置文件不存在，创建默认配置
            if (!openclawConfigFile.exists()) {
                Log.i(TAG, "OpenClaw 配置文件不存在，创建默认配置: ${openclawConfigFile.absolutePath}")
                createDefaultOpenClawConfig()
            }

            // 读取配置文件
            val configJson = openclawConfigFile.readText()
            Log.d(TAG, "读取 OpenClaw 配置文件: ${openclawConfigFile.absolutePath}")

            // 替换环境变量
            val processedJson = replaceEnvVars(configJson)

            // 解析 JSON
            val config = gson.fromJson(processedJson, OpenClawConfig::class.java)

            // 验证配置
            validateOpenClawConfig(config)

            Log.i(TAG, "✅ OpenClaw 配置加载成功")
            return config

        } catch (e: Exception) {
            Log.e(TAG, "❌ OpenClaw 配置加载失败: ${e.message}", e)
            throw e // 抛出异常，由 ConfigBackupManager 处理
        }
    }

    /**
     * 创建默认配置对象
     */
    private fun createDefaultOpenClawConfigObject(): OpenClawConfig {
        return gson.fromJson(DEFAULT_OPENCLAW_CONFIG, OpenClawConfig::class.java)
    }


    /**
     * 获取指定 provider 的配置
     */
    fun getProviderConfig(providerName: String): ProviderConfig? {
        val openClawConfig = loadOpenClawConfig()
        return openClawConfig.providers[providerName]
    }

    /**
     * 获取指定模型的定义
     */
    fun getModelDefinition(providerName: String, modelId: String): ModelDefinition? {
        val provider = getProviderConfig(providerName) ?: return null
        return provider.models.find { it.id == modelId }
    }

    /**
     * 列出所有可用的模型
     */
    fun listAllModels(): List<Pair<String, ModelDefinition>> {
        val config = loadOpenClawConfig()
        val models = mutableListOf<Pair<String, ModelDefinition>>()

        config.providers.forEach { (providerName, provider) ->
            provider.models.forEach { model ->
                models.add(providerName to model)
            }
        }

        return models
    }

    /**
     * 根据模型 ID 查找对应的 provider 名称
     */
    fun findProviderByModelId(modelId: String): String? {
        val openClawConfig = loadOpenClawConfig()
        openClawConfig.providers.forEach { (providerName, provider) ->
            if (provider.models.any { it.id == modelId }) {
                return providerName
            }
        }
        return null
    }


    /**
     * 保存 OpenClaw 配置
     */
    fun saveOpenClawConfig(config: OpenClawConfig): Boolean {
        return try {
            ensureConfigDir()
            val json = gson.toJson(config)
            openclawConfigFile.writeText(json)
            Log.i(TAG, "✅ OpenClaw 配置保存成功: ${openclawConfigFile.absolutePath}")
            // 清除缓存，下次加载时会重新读取
            openclawConfigCacheValid = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ OpenClaw 配置保存失败: ${e.message}", e)
            false
        }
    }


    /**
     * 重新加载 OpenClaw 配置（用于热重载）
     */
    fun reloadOpenClawConfig(): OpenClawConfig {
        Log.i(TAG, "重新加载 OpenClaw 配置...")
        openclawConfigCacheValid = false
        return loadOpenClawConfig()
    }

    /**
     * 启用配置热重载
     * 监控配置文件，变化时自动重新加载
     *
     * @param callback 配置重新加载后的回调函数
     */
    fun enableHotReload(callback: ((OpenClawConfig) -> Unit)? = null) {
        if (hotReloadEnabled) {
            Log.d(TAG, "配置热重载已启用")
            return
        }

        this.reloadCallback = callback

        try {
            // 确保配置目录存在
            ensureConfigDir()

            // 监控配置目录
            fileObserver = object : FileObserver(configDir, MODIFY or CREATE or DELETE) {
                override fun onEvent(event: Int, path: String?) {
                    when (path) {
                        OPENCLAW_CONFIG_FILE -> {
                            Log.i(TAG, "检测到 OpenClaw 配置文件变化: $path")
                            Log.i(TAG, "自动重新加载 OpenClaw 配置...")
                            val newConfig = reloadOpenClawConfig()
                            reloadCallback?.invoke(newConfig)
                        }
                    }
                }
            }

            fileObserver?.startWatching()
            hotReloadEnabled = true
            Log.i(TAG, "✅ 配置热重载已启用 - 监控: $CONFIG_DIR")

        } catch (e: Exception) {
            Log.e(TAG, "启用配置热重载失败", e)
        }
    }

    /**
     * 禁用配置热重载
     */
    fun disableHotReload() {
        fileObserver?.stopWatching()
        fileObserver = null
        reloadCallback = null
        hotReloadEnabled = false
        Log.i(TAG, "配置热重载已禁用")
    }

    /**
     * 是否启用了热重载
     */
    fun isHotReloadEnabled(): Boolean = hotReloadEnabled

    /**
     * 获取 Feishu Channel 配置（转换为 FeishuConfig）
     */
    fun getFeishuConfig(): com.xiaomo.feishu.FeishuConfig {
        val openClawConfig = loadOpenClawConfig()
        return FeishuConfigAdapter.toFeishuConfig(openClawConfig.gateway.feishu)
    }

    // ============ 私有方法 ============

    /**
     * 确保配置目录存在
     */
    private fun ensureConfigDir() {
        if (!configDir.exists()) {
            configDir.mkdirs()
            Log.i(TAG, "创建配置目录: ${configDir.absolutePath}")
        }
    }


    /**
     * 创建默认 OpenClaw 配置文件
     */
    private fun createDefaultOpenClawConfig() {
        openclawConfigFile.writeText(DEFAULT_OPENCLAW_CONFIG)
        Log.i(TAG, "✅ 创建默认 OpenClaw 配置文件")
    }

    /**
     * 替换环境变量 (${VAR_NAME})
     *
     * 支持的环境变量来源：
     * 1. 系统环境变量
     * 2. AppConstants 中的常量
     * 3. MMKV 存储的配置
     */
    private fun replaceEnvVars(json: String): String {
        var result = json
        val envVarPattern = Regex("""\$\{([A-Z_]+)\}""")

        envVarPattern.findAll(json).forEach { match ->
            val varName = match.groupValues[1]
            val value = getEnvVar(varName)

            if (value != null) {
                result = result.replace("\${$varName}", value)
                Log.d(TAG, "替换环境变量: \${$varName} -> ***")
            } else {
                Log.w(TAG, "⚠️ 环境变量未找到: \${$varName}")
            }
        }

        return result
    }

    /**
     * 获取环境变量值
     *
     * 查找优先级:
     * 1. 系统环境变量
     * 2. AppConstants 常量
     * 3. MMKV 配置
     */
    private fun getEnvVar(name: String): String? {
        // 1. 尝试从系统环境变量获取
        val systemEnv = System.getenv(name)
        if (systemEnv != null) {
            Log.d(TAG, "从系统环境变量获取: $name")
            return systemEnv
        }

        // 2. 尝试从 AppConstants 获取
        try {
            val constantsClass = Class.forName("com.xiaomo.androidforclaw.util.AppConstants")
            val field = constantsClass.getDeclaredField(name)
            field.isAccessible = true
            val value = field.get(null) as? String
            if (value != null) {
                Log.d(TAG, "从 AppConstants 获取: $name")
                return value
            }
        } catch (e: Exception) {
            // 继续尝试下一个来源
        }

        // 3. 尝试从 MMKV 获取
        try {
            val mmkv = com.tencent.mmkv.MMKV.defaultMMKV()
            val value = mmkv?.decodeString(name)
            if (value != null) {
                Log.d(TAG, "从 MMKV 获取: $name")
                return value
            }
        } catch (e: Exception) {
            Log.w(TAG, "从 MMKV 读取失败: $name", e)
        }

        return null
    }


    /**
     * 获取默认 OpenClaw 配置
     */
    private fun getDefaultOpenClawConfig(): OpenClawConfig {
        return gson.fromJson(DEFAULT_OPENCLAW_CONFIG, OpenClawConfig::class.java)
    }

    /**
     * 验证 OpenClaw 配置
     */
    private fun validateOpenClawConfig(config: OpenClawConfig) {
        // Agent 配置验证
        require(config.agent.maxIterations in ConfigDefaults.MIN_MAX_ITERATIONS..ConfigDefaults.MAX_MAX_ITERATIONS) {
            "Agent maxIterations 必须在 ${ConfigDefaults.MIN_MAX_ITERATIONS} 到 ${ConfigDefaults.MAX_MAX_ITERATIONS} 之间"
        }

        require(config.agent.timeout in ConfigDefaults.MIN_TIMEOUT_MS..ConfigDefaults.MAX_TIMEOUT_MS) {
            "Agent timeout 必须在 ${ConfigDefaults.MIN_TIMEOUT_MS} 到 ${ConfigDefaults.MAX_TIMEOUT_MS} 之间"
        }

        require(config.agent.mode in listOf("exploration", "planning")) {
            "Agent mode 必须是 'exploration' 或 'planning'"
        }

        // Thinking 配置验证
        require(config.thinking.budgetTokens in ConfigDefaults.MIN_THINKING_BUDGET..ConfigDefaults.MAX_THINKING_BUDGET) {
            "Thinking budgetTokens 必须在 ${ConfigDefaults.MIN_THINKING_BUDGET} 到 ${ConfigDefaults.MAX_THINKING_BUDGET} 之间"
        }

        // Screenshot 配置验证
        require(config.tools.screenshot.quality in ConfigDefaults.MIN_SCREENSHOT_QUALITY..ConfigDefaults.MAX_SCREENSHOT_QUALITY) {
            "Screenshot quality 必须在 ${ConfigDefaults.MIN_SCREENSHOT_QUALITY} 到 ${ConfigDefaults.MAX_SCREENSHOT_QUALITY} 之间"
        }

        require(config.tools.screenshot.format in listOf("jpeg", "png", "webp")) {
            "Screenshot format 必须是 'jpeg', 'png' 或 'webp'"
        }

        // Gateway 配置验证
        require(config.gateway.port in ConfigDefaults.MIN_GATEWAY_PORT..ConfigDefaults.MAX_GATEWAY_PORT) {
            "Gateway port 必须在 ${ConfigDefaults.MIN_GATEWAY_PORT} 到 ${ConfigDefaults.MAX_GATEWAY_PORT} 之间"
        }

        // UI 配置验证
        require(config.ui.theme in listOf("light", "dark", "auto")) {
            "UI theme 必须是 'light', 'dark' 或 'auto'"
        }

        require(config.ui.language in listOf("zh", "en")) {
            "UI language 必须是 'zh' 或 'en'"
        }

        require(config.ui.floatingWindow.position in listOf("top-left", "top-right", "bottom-left", "bottom-right")) {
            "FloatingWindow position 必须是 'top-left', 'top-right', 'bottom-left' 或 'bottom-right'"
        }

        require(config.ui.floatingWindow.opacity in 0.0f..1.0f) {
            "FloatingWindow opacity 必须在 0.0 到 1.0 之间"
        }

        // Logging 配置验证
        require(config.logging.level in listOf("DEBUG", "INFO", "WARN", "ERROR")) {
            "Logging level 必须是 'DEBUG', 'INFO', 'WARN' 或 'ERROR'"
        }

        // Providers 配置验证 (从 config.providers 读取)
        if (config.providers.isNotEmpty()) {
            config.providers.forEach { (providerName, provider) ->
                require(provider.baseUrl.isNotBlank()) {
                    "Provider '$providerName' 的 baseUrl 不能为空"
                }

                require(provider.models.isNotEmpty()) {
                    "Provider '$providerName' 必须至少包含一个模型"
                }

                provider.models.forEach { model ->
                    require(model.id.isNotBlank()) {
                        "Provider '$providerName' 中的模型 id 不能为空"
                    }
                    require(model.name.isNotBlank()) {
                        "Provider '$providerName' 中的模型 '${model.id}' 的 name 不能为空"
                    }
                    require(model.contextWindow > 0) {
                        "模型 '${model.id}' 的 contextWindow 必须大于 0"
                    }
                    require(model.maxTokens > 0) {
                        "模型 '${model.id}' 的 maxTokens 必须大于 0"
                    }
                }
            }
        }

        // Feishu Channel 配置验证
        val feishu = config.gateway.feishu
        if (feishu.enabled) {
            require(feishu.appId.isNotBlank()) {
                "Feishu appId 不能为空（enabled=true 时）"
            }
            require(feishu.appSecret.isNotBlank()) {
                "Feishu appSecret 不能为空（enabled=true 时）"
            }
        }

        require(feishu.connectionMode in listOf("websocket", "webhook")) {
            "Feishu connectionMode 必须是 'websocket' 或 'webhook'"
        }

        require(feishu.dmPolicy in listOf("open", "pairing", "allowlist")) {
            "Feishu dmPolicy 必须是 'open', 'pairing' 或 'allowlist'"
        }

        require(feishu.groupPolicy in listOf("open", "allowlist", "disabled")) {
            "Feishu groupPolicy 必须是 'open', 'allowlist' 或 'disabled'"
        }

        require(feishu.groupCommandMentionBypass in listOf("never", "single_bot", "always")) {
            "Feishu groupCommandMentionBypass 必须是 'never', 'single_bot' 或 'always'"
        }

        require(feishu.topicSessionMode in listOf("disabled", "enabled")) {
            "Feishu topicSessionMode 必须是 'disabled' 或 'enabled'"
        }

        require(feishu.chunkMode in listOf("length", "newline")) {
            "Feishu chunkMode 必须是 'length' 或 'newline'"
        }

        Log.i(TAG, "✅ OpenClaw 配置验证通过")
    }
}
