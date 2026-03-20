package com.xiaomo.androidforclaw.config

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/(all)
 * - ../openclaw/docs/gateway/configuration-reference.md
 *
 * AndroidForClaw adaptation: load/save/observe openclaw.json on Android storage.
 */


import android.content.Context
import android.os.FileObserver
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 配置加载器 - 对齐 OpenClaw 的配置加载逻辑
 *
 * 使用 org.json.JSONObject 解析，缺失字段自动用 data class 默认值。
 * 用户 config 只需写想覆盖的字段，其他全用默认值。
 */
class ConfigLoader(private val context: Context) {

    companion object {
        private const val TAG = "ConfigLoader"
        private const val OPENCLAW_CONFIG_FILE = "openclaw.json"
    }

    private val configDir = File("/sdcard/.androidforclaw")
    private val openclawConfigFile = File(configDir, OPENCLAW_CONFIG_FILE)

    // Config cache
    private var cachedOpenClawConfig: OpenClawConfig? = null
    private var openclawConfigCacheValid = false

    // Hot reload support
    private var fileObserver: FileObserver? = null
    private var hotReloadEnabled = false
    private var reloadCallback: ((OpenClawConfig) -> Unit)? = null

    init {
        Log.d(TAG, "配置目录: ${configDir.absolutePath}")
    }

    /**
     * 加载 OpenClaw 主配置（带自动备份和恢复）
     */
    fun loadOpenClawConfig(): OpenClawConfig {
        if (openclawConfigCacheValid && cachedOpenClawConfig != null) {
            return cachedOpenClawConfig!!
        }

        val backupManager = ConfigBackupManager(context)
        val config = backupManager.loadConfigSafely {
            loadOpenClawConfigInternal()
        }

        if (config != null) {
            cachedOpenClawConfig = config
            openclawConfigCacheValid = true
            return config
        } else {
            Log.w(TAG, "使用默认配置")
            val defaultConfig = OpenClawConfig()
            cachedOpenClawConfig = defaultConfig
            openclawConfigCacheValid = true
            return defaultConfig
        }
    }

    private fun loadOpenClawConfigInternal(): OpenClawConfig {
        ensureConfigDir()

        if (!openclawConfigFile.exists()) {
            Log.i(TAG, "配置文件不存在，创建默认配置: ${openclawConfigFile.absolutePath}")
            createDefaultConfig()
        }

        val configJson = openclawConfigFile.readText()
        val processedJson = replaceEnvVars(configJson)
        val config = parseConfig(processedJson)
        val migrated = migrateProviderIds(config)
        validateConfig(migrated)

        Log.i(TAG, "✅ 配置加载成功")
        return migrated
    }

    /**
     * 迁移旧的 provider ID 到新 ID（对齐 OpenClaw 官方命名）
     * "mimo" → "xiaomi"
     */
    private fun migrateProviderIds(config: OpenClawConfig): OpenClawConfig {
        val providers = config.models?.providers ?: return config
        if (!providers.containsKey("mimo")) return config

        Log.i(TAG, "🔄 迁移 provider ID: mimo → xiaomi")
        val migrated = providers.toMutableMap()
        val mimoProvider = migrated.remove("mimo")!!
        migrated["xiaomi"] = mimoProvider

        // Also update default model ref if it points to mimo/
        val agents = config.agents
        val updatedAgents = if (agents?.defaults?.model?.primary?.startsWith("mimo/") == true) {
            val newPrimary = agents.defaults.model.primary.replace("mimo/", "xiaomi/")
            agents.copy(
                defaults = agents.defaults.copy(
                    model = ModelSelectionConfig(primary = newPrimary)
                )
            )
        } else agents

        val migratedConfig = config.copy(
            models = config.models?.copy(providers = migrated),
            agents = updatedAgents
        )

        // Persist the migration
        try {
            val root = JSONObject(openclawConfigFile.readText())
            val modelsJson = root.optJSONObject("models")
            val providersJson = modelsJson?.optJSONObject("providers")
            if (providersJson?.has("mimo") == true) {
                val mimoJson = providersJson.getJSONObject("mimo")
                providersJson.put("xiaomi", mimoJson)
                providersJson.remove("mimo")

                // Update default model ref
                val agentsJson = root.optJSONObject("agents")
                val defaultsJson = agentsJson?.optJSONObject("defaults")
                val modelJson = defaultsJson?.optJSONObject("model")
                val primary = modelJson?.optString("primary")
                if (primary?.startsWith("mimo/") == true) {
                    modelJson.put("primary", primary.replace("mimo/", "xiaomi/"))
                }

                openclawConfigFile.writeText(root.toString(4))
                Log.i(TAG, "✅ provider ID 迁移已持久化")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ provider ID 迁移持久化失败: ${e.message}")
        }

        return migratedConfig
    }

    /**
     * 从 JSON 字符串解析完整配置
     * 所有缺失字段都用 data class 的默认值
     */
    private fun parseConfig(json: String): OpenClawConfig {
        val root = JSONObject(json)

        // Models
        val modelsJson = root.optJSONObject("models")
        val models = modelsJson?.let { parseModelsConfig(it) }

        // Agents
        val agentsJson = root.optJSONObject("agents")
        val agents = agentsJson?.let { parseAgentsConfig(it) }

        // Agent (Android extension, legacy)
        val agentJson = root.optJSONObject("agent")
        val agent = agentJson?.let { parseAgentConfig(it) } ?: AgentConfig()

        // Channels（对齐 OpenClaw: channels.feishu / channels.discord）
        val channelsJson = root.optJSONObject("channels")
        // 兼容旧格式：如果 channels 没有 feishu，fallback 到 gateway.feishu
        val gatewayJson = root.optJSONObject("gateway")
        val channels = parseChannelsConfig(channelsJson, gatewayJson)

        // Gateway（对齐 OpenClaw: 只有 port/mode/bind/auth）
        val gateway = gatewayJson?.let { parseGatewayConfig(it) } ?: GatewayConfig()

        // Skills
        val skillsJson = root.optJSONObject("skills")
        val skills = skillsJson?.let { parseSkillsConfig(it) } ?: SkillsConfig()

        // Plugins
        val pluginsJson = root.optJSONObject("plugins")
        val plugins = pluginsJson?.let { parsePluginsConfig(it) } ?: PluginsConfig()

        // Tools
        val toolsJson = root.optJSONObject("tools")
        val tools = toolsJson?.let { parseToolsConfig(it) } ?: ToolsConfig()

        // UI
        val uiJson = root.optJSONObject("ui")
        val ui = uiJson?.let { parseUIConfig(it) } ?: UIConfig()

        // Logging
        val loggingJson = root.optJSONObject("logging")
        val logging = loggingJson?.let { parseLoggingConfig(it) } ?: LoggingConfig()

        // Memory
        val memoryJson = root.optJSONObject("memory")
        val memory = memoryJson?.let { parseMemoryConfig(it) } ?: MemoryConfig()

        // Messages
        val messagesJson = root.optJSONObject("messages")
        val messages = messagesJson?.let {
            MessagesConfig(ackReactionScope = it.optString("ackReactionScope", "own"))
        } ?: MessagesConfig()

        // Session
        val sessionJson = root.optJSONObject("session")
        val session = sessionJson?.let { parseSessionConfig(it) } ?: SessionConfig()

        // Legacy providers (top-level)
        val legacyProviders = root.optJSONObject("providers")?.let { parseProvidersMap(it) } ?: emptyMap()

        return OpenClawConfig(
            models = models,
            agents = agents,
            channels = channels,
            gateway = gateway,
            skills = skills,
            plugins = plugins,
            tools = tools,
            memory = memory,
            messages = messages,
            session = session,
            logging = logging,
            ui = ui,
            agent = agent,
            providers = legacyProviders
        )
    }

    // ============ Section Parsers ============

    private fun parseModelsConfig(json: JSONObject): ModelsConfig {
        val providersJson = json.optJSONObject("providers")
        val providers = providersJson?.let { parseProvidersMap(it) } ?: emptyMap()
        return ModelsConfig(
            mode = json.optString("mode", "merge"),
            providers = providers
        )
    }

    private fun parseProvidersMap(json: JSONObject): Map<String, ProviderConfig> {
        val map = mutableMapOf<String, ProviderConfig>()
        json.keys().forEach { key ->
            json.optJSONObject(key)?.let { map[key] = parseProviderConfig(it) }
        }
        return map
    }

    private fun parseProviderConfig(json: JSONObject): ProviderConfig {
        val modelsArray = json.optJSONArray("models") ?: JSONArray()
        val defaultApi = json.optString("api", "openai-completions")
        val models = (0 until modelsArray.length()).mapNotNull { i ->
            modelsArray.optJSONObject(i)?.let { parseModelDefinition(it, defaultApi) }
        }

        val headers = json.optJSONObject("headers")?.let { h ->
            h.keys().asSequence().associateWith { h.optString(it, "") }
        }

        return ProviderConfig(
            baseUrl = json.optString("baseUrl", ""),
            apiKey = json.optString("apiKey", null),
            api = defaultApi,
            auth = json.optString("auth", null),
            authHeader = json.optBoolean("authHeader", true),
            headers = headers,
            injectNumCtxForOpenAICompat = if (json.has("injectNumCtxForOpenAICompat")) json.optBoolean("injectNumCtxForOpenAICompat") else null,
            models = models
        )
    }

    private fun parseModelDefinition(json: JSONObject, defaultApi: String): ModelDefinition {
        val input: List<Any> = if (json.has("input")) {
            val arr = json.optJSONArray("input") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val item = arr.get(i)
                if (item is JSONObject) item.toString() else item.toString()
            }
        } else listOf("text")

        val headers = json.optJSONObject("headers")?.let { h ->
            h.keys().asSequence().associateWith { h.optString(it, "") }
        }

        val cost = json.optJSONObject("cost")?.let { c ->
            CostConfig(
                input = c.optDouble("input", 0.0),
                output = c.optDouble("output", 0.0),
                cacheRead = c.optDouble("cacheRead", 0.0),
                cacheWrite = c.optDouble("cacheWrite", 0.0)
            )
        }

        val compat = json.optJSONObject("compat")?.let { c ->
            ModelCompatConfig(
                supportsStore = if (c.has("supportsStore")) c.optBoolean("supportsStore") else null,
                supportsReasoningEffort = if (c.has("supportsReasoningEffort")) c.optBoolean("supportsReasoningEffort") else null,
                maxTokensField = if (c.has("maxTokensField")) c.optString("maxTokensField") else null,
                thinkingFormat = if (c.has("thinkingFormat")) c.optString("thinkingFormat") else null,
                requiresToolResultName = if (c.has("requiresToolResultName")) c.optBoolean("requiresToolResultName") else null,
                requiresAssistantAfterToolResult = if (c.has("requiresAssistantAfterToolResult")) c.optBoolean("requiresAssistantAfterToolResult") else null
            )
        }

        val modelId = json.optString("id", "")
        val modelIdLower = modelId.lowercase()
        val compatWithDefaults = if (compat?.maxTokensField == null) {
            val defaultMaxTokensField = when {
                modelIdLower.startsWith("gpt-5") -> "max_completion_tokens"
                modelIdLower.startsWith("o1") -> "max_completion_tokens"
                modelIdLower.startsWith("o3") -> "max_completion_tokens"
                modelIdLower.startsWith("gpt-4.1") -> "max_completion_tokens"
                else -> null
            }
            if (defaultMaxTokensField != null) {
                (compat ?: ModelCompatConfig()).copy(maxTokensField = defaultMaxTokensField)
            } else {
                compat
            }
        } else {
            compat
        }

        return ModelDefinition(
            id = json.optString("id", ""),
            name = json.optString("name", ""),
            api = if (json.has("api")) json.optString("api") else null,
            reasoning = json.optBoolean("reasoning", false),
            input = input,
            cost = cost,
            contextWindow = json.optInt("contextWindow", 128000),
            maxTokens = json.optInt("maxTokens", 8192),
            headers = headers,
            compat = compatWithDefaults
        )
    }

    private fun parseAgentsConfig(json: JSONObject): AgentsConfig {
        val defaultsJson = json.optJSONObject("defaults")
        val defaults = if (defaultsJson != null) {
            val modelJson = defaultsJson.optJSONObject("model")
            val model = modelJson?.let {
                ModelSelectionConfig(
                    primary = it.optString("primary", null),
                    fallbacks = it.optJSONArray("fallbacks")?.let { arr ->
                        (0 until arr.length()).map { i -> arr.getString(i) }
                    }
                )
            }
            AgentDefaultsConfig(
                model = model,
                bootstrapMaxChars = defaultsJson.optInt("bootstrapMaxChars", 20_000),
                bootstrapTotalMaxChars = defaultsJson.optInt("bootstrapTotalMaxChars", 150_000)
            )
        } else AgentDefaultsConfig()
        return AgentsConfig(defaults = defaults)
    }

    private fun parseAgentConfig(json: JSONObject): AgentConfig {
        return AgentConfig(
            maxIterations = json.optInt("maxIterations", 40),
            defaultModel = json.optString("defaultModel", "anthropic/claude-opus-4.6"),
            timeout = json.optLong("timeout", 300000),
            retryOnError = json.optBoolean("retryOnError", true),
            maxRetries = json.optInt("maxRetries", 3),
            mode = json.optString("mode", "exploration")
        )
    }

    /**
     * 解析 channels 配置（对齐 OpenClaw: channels.feishu）
     * 兼容旧格式：如果 channels.feishu 不存在，fallback 到 gateway.feishu
     */
    private fun parseChannelsConfig(channelsJson: JSONObject?, gatewayJson: JSONObject?): ChannelsConfig {
        val feishuJson = channelsJson?.optJSONObject("feishu")
            ?: gatewayJson?.optJSONObject("feishu")  // legacy fallback
        val feishu = feishuJson?.let { parseFeishuConfig(it) } ?: FeishuChannelConfig()

        val discordJson = channelsJson?.optJSONObject("discord")
            ?: gatewayJson?.optJSONObject("discord")  // legacy fallback
        val discord = discordJson?.let { parseDiscordConfig(it) }

        return ChannelsConfig(feishu = feishu, discord = discord)
    }

    /**
     * 解析 gateway（对齐 OpenClaw: 只有 port/mode/bind/auth）
     */
    private fun parseGatewayConfig(json: JSONObject): GatewayConfig {
        val authJson = json.optJSONObject("auth")
        val auth = authJson?.let {
            GatewayAuthConfig(
                mode = it.optString("mode", "token"),
                token = if (it.has("token")) it.optString("token") else null
            )
        }

        return GatewayConfig(
            port = json.optInt("port", 18789),
            mode = json.optString("mode", "local"),
            bind = json.optString("bind", "loopback"),
            auth = auth
        )
    }

    private fun parseFeishuConfig(json: JSONObject): FeishuChannelConfig {
        // tools 子配置（对齐 OpenClaw FeishuToolsConfigSchema）
        val toolsJson = json.optJSONObject("tools")
        val tools = toolsJson?.let {
            FeishuToolsConfig(
                doc = it.optBoolean("doc", true),
                chat = it.optBoolean("chat", true),
                wiki = it.optBoolean("wiki", true),
                drive = it.optBoolean("drive", true),
                perm = it.optBoolean("perm", false),
                scopes = it.optBoolean("scopes", true),
                bitable = it.optBoolean("bitable", true),
                task = it.optBoolean("task", true),
                urgent = it.optBoolean("urgent", true)
            )
        } ?: FeishuToolsConfig()

        // 多账号
        val accountsJson = json.optJSONObject("accounts")
        val accounts = accountsJson?.let { a ->
            val map = mutableMapOf<String, FeishuAccountConfig>()
            a.keys().forEach { key ->
                a.optJSONObject(key)?.let { aj ->
                    map[key] = FeishuAccountConfig(
                        enabled = aj.optBoolean("enabled", true),
                        name = if (aj.has("name")) aj.optString("name") else null,
                        appId = if (aj.has("appId")) aj.optString("appId") else null,
                        appSecret = if (aj.has("appSecret")) aj.optString("appSecret") else null,
                        domain = if (aj.has("domain")) aj.optString("domain") else null,
                        connectionMode = if (aj.has("connectionMode")) aj.optString("connectionMode") else null,
                        webhookPath = if (aj.has("webhookPath")) aj.optString("webhookPath") else null
                    )
                }
            }
            map
        }

        return FeishuChannelConfig(
            enabled = json.optBoolean("enabled", false),
            appId = json.optString("appId", ""),
            appSecret = json.optString("appSecret", ""),
            encryptKey = if (json.has("encryptKey")) json.optString("encryptKey") else null,
            verificationToken = if (json.has("verificationToken")) json.optString("verificationToken") else null,
            domain = json.optString("domain", "feishu"),
            connectionMode = json.optString("connectionMode", "websocket"),
            webhookPath = json.optString("webhookPath", "/feishu/events"),
            webhookHost = if (json.has("webhookHost")) json.optString("webhookHost") else null,
            webhookPort = if (json.has("webhookPort")) json.optInt("webhookPort") else null,
            dmPolicy = json.optString("dmPolicy", "pairing"),
            allowFrom = json.optJSONArray("allowFrom")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            groupPolicy = json.optString("groupPolicy", "allowlist"),
            groupAllowFrom = json.optJSONArray("groupAllowFrom")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            requireMention = json.optBoolean("requireMention", true),
            groupCommandMentionBypass = json.optString("groupCommandMentionBypass", "never"),
            allowMentionlessInMultiBotGroup = json.optBoolean("allowMentionlessInMultiBotGroup", false),
            groupSessionScope = if (json.has("groupSessionScope")) json.optString("groupSessionScope") else null,
            topicSessionMode = json.optString("topicSessionMode", "disabled"),
            replyInThread = json.optString("replyInThread", "disabled"),
            historyLimit = json.optInt("historyLimit", 20),
            dmHistoryLimit = json.optInt("dmHistoryLimit", 100),
            textChunkLimit = json.optInt("textChunkLimit", 4000),
            chunkMode = json.optString("chunkMode", "length"),
            renderMode = json.optString("renderMode", "auto"),
            streaming = if (json.has("streaming")) json.optBoolean("streaming") else null,
            mediaMaxMb = json.optDouble("mediaMaxMb", 20.0),
            tools = tools,
            queueMode = if (json.has("queueMode")) json.optString("queueMode") else "followup",
            queueCap = json.optInt("queueCap", 10),
            queueDropPolicy = json.optString("queueDropPolicy", "old"),
            queueDebounceMs = json.optInt("queueDebounceMs", 100),
            typingIndicator = json.optBoolean("typingIndicator", true),
            resolveSenderNames = json.optBoolean("resolveSenderNames", true),
            reactionNotifications = json.optString("reactionNotifications", "own"),
            reactionDedup = json.optBoolean("reactionDedup", true),
            debugMode = json.optBoolean("debugMode", false),
            accounts = accounts,
            defaultAccount = if (json.has("defaultAccount")) json.optString("defaultAccount") else null
        )
    }

    private fun parseDiscordConfig(json: JSONObject): DiscordChannelConfig {
        val dm = json.optJSONObject("dm")?.let { d ->
            DmPolicyConfig(
                policy = d.optString("policy", "pairing"),
                allowFrom = d.optJSONArray("allowFrom")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            )
        }

        val guilds = json.optJSONObject("guilds")?.let { g ->
            val map = mutableMapOf<String, GuildPolicyConfig>()
            g.keys().forEach { key ->
                g.optJSONObject(key)?.let { gj ->
                    map[key] = GuildPolicyConfig(
                        channels = gj.optJSONArray("channels")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        },
                        requireMention = if (gj.has("requireMention")) gj.optBoolean("requireMention") else true,
                        toolPolicy = if (gj.has("toolPolicy")) gj.optString("toolPolicy") else null
                    )
                }
            }
            map
        }

        val accounts = json.optJSONObject("accounts")?.let { a ->
            val map = mutableMapOf<String, DiscordAccountPolicyConfig>()
            a.keys().forEach { key ->
                a.optJSONObject(key)?.let { aj ->
                    map[key] = DiscordAccountPolicyConfig(
                        enabled = aj.optBoolean("enabled", true),
                        token = if (aj.has("token")) aj.optString("token") else null,
                        name = if (aj.has("name")) aj.optString("name") else null,
                        dm = aj.optJSONObject("dm")?.let { d ->
                            DmPolicyConfig(
                                policy = d.optString("policy", "pairing"),
                                allowFrom = d.optJSONArray("allowFrom")?.let { arr ->
                                    (0 until arr.length()).map { arr.getString(it) }
                                }
                            )
                        },
                        guilds = aj.optJSONObject("guilds")?.let { g ->
                            val gmap = mutableMapOf<String, GuildPolicyConfig>()
                            g.keys().forEach { gk ->
                                g.optJSONObject(gk)?.let { gj ->
                                    gmap[gk] = GuildPolicyConfig(
                                        channels = gj.optJSONArray("channels")?.let { arr ->
                                            (0 until arr.length()).map { arr.getString(it) }
                                        }
                                    )
                                }
                            }
                            gmap
                        }
                    )
                }
            }
            map
        }

        return DiscordChannelConfig(
            enabled = json.optBoolean("enabled", false),
            token = if (json.has("token")) json.optString("token") else null,
            name = if (json.has("name")) json.optString("name") else null,
            dm = dm,
            groupPolicy = if (json.has("groupPolicy")) json.optString("groupPolicy") else null,
            guilds = guilds,
            replyToMode = if (json.has("replyToMode")) json.optString("replyToMode") else null,
            accounts = accounts
        )
    }

    private fun parseSkillsConfig(json: JSONObject): SkillsConfig {
        val entries = json.optJSONObject("entries")?.let { e ->
            val map = mutableMapOf<String, SkillConfig>()
            e.keys().forEach { key ->
                e.optJSONObject(key)?.let { sc ->
                    map[key] = SkillConfig(
                        enabled = sc.optBoolean("enabled", true),
                        apiKey = if (sc.has("apiKey")) sc.get("apiKey") else null,
                        env = sc.optJSONObject("env")?.let { envObj ->
                            envObj.keys().asSequence().associateWith { envObj.optString(it, "") }
                        }
                    )
                }
            }
            map
        } ?: emptyMap()

        val extraDirs = json.optJSONArray("extraDirs")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        return SkillsConfig(
            allowBundled = json.optJSONArray("allowBundled")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            },
            extraDirs = extraDirs,
            watch = json.optBoolean("watch", true),
            watchDebounceMs = json.optLong("watchDebounceMs", 250),
            entries = entries
        )
    }

    private fun parsePluginsConfig(json: JSONObject): PluginsConfig {
        val entriesJson = json.optJSONObject("entries") ?: return PluginsConfig()
        val map = mutableMapOf<String, PluginEntry>()
        entriesJson.keys().forEach { key ->
            entriesJson.optJSONObject(key)?.let { pe ->
                val skills = pe.optJSONArray("skills")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
                map[key] = PluginEntry(
                    enabled = pe.optBoolean("enabled", false),
                    skills = skills
                )
            }
        }
        return PluginsConfig(entries = map)
    }

    private fun parseToolsConfig(json: JSONObject): ToolsConfig {
        val ssJson = json.optJSONObject("screenshot")
        val screenshot = ssJson?.let {
            ScreenshotToolConfig(
                enabled = it.optBoolean("enabled", true),
                quality = it.optInt("quality", 85),
                maxWidth = it.optInt("maxWidth", 1080),
                format = it.optString("format", "jpeg")
            )
        } ?: ScreenshotToolConfig()

        return ToolsConfig(screenshot = screenshot)
    }

    private fun parseThinkingConfig(json: JSONObject): ThinkingConfig {
        return ThinkingConfig(
            enabled = json.optBoolean("enabled", true),
            budgetTokens = json.optInt("budgetTokens", 10000)
        )
    }

    private fun parseUIConfig(json: JSONObject): UIConfig {
        return UIConfig(
            theme = json.optString("theme", "auto"),
            language = json.optString("language", "zh")
        )
    }

    private fun parseLoggingConfig(json: JSONObject): LoggingConfig {
        return LoggingConfig(
            level = json.optString("level", "INFO"),
            logToFile = json.optBoolean("logToFile", true)
        )
    }

    private fun parseMemoryConfig(json: JSONObject): MemoryConfig {
        return MemoryConfig(
            enabled = json.optBoolean("enabled", true),
            path = json.optString("path", "/sdcard/.androidforclaw/workspace/memory")
        )
    }

    private fun parseSessionConfig(json: JSONObject): SessionConfig {
        return SessionConfig(
            maxMessages = json.optInt("maxMessages", 100)
        )
    }

    // ============ Public API ============

    fun getProviderConfig(providerName: String): ProviderConfig? {
        return loadOpenClawConfig().resolveProviders()[providerName]
    }

    fun getModelDefinition(providerName: String, modelId: String): ModelDefinition? {
        return getProviderConfig(providerName)?.models?.find { it.id == modelId }
    }

    fun listAllModels(): List<Pair<String, ModelDefinition>> {
        val config = loadOpenClawConfig()
        return config.resolveProviders().flatMap { (name, provider) ->
            provider.models.map { name to it }
        }
    }

    fun findProviderByModelId(modelId: String): String? {
        return loadOpenClawConfig().resolveProviders().entries.find { (_, provider) ->
            provider.models.any { it.id == modelId }
        }?.key
    }

    /**
     * 保存配置 - 用 JSONObject 序列化（不依赖 Gson）
     */
    fun saveOpenClawConfig(config: OpenClawConfig): Boolean {
        return try {
            ensureConfigDir()
            // Read existing file to preserve unknown fields
            val existingJson = if (openclawConfigFile.exists()) {
                JSONObject(openclawConfigFile.readText())
            } else JSONObject()

            // Merge known fields into existing JSON
            mergeConfigToJson(existingJson, config)

            openclawConfigFile.writeText(existingJson.toString(4))
            Log.i(TAG, "✅ 配置保存成功")
            openclawConfigCacheValid = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 配置保存失败: ${e.message}", e)
            false
        }
    }

    /**
     * 将 config 对象的关键字段写入 JSONObject（保留文件中其他字段）
     */
    private fun mergeConfigToJson(root: JSONObject, config: OpenClawConfig) {
        // Models + providers
        config.models?.let { m ->
            val modelsObj = root.optJSONObject("models") ?: JSONObject()
            val providersObj = JSONObject()
            m.providers.forEach { (name, p) ->
                val pObj = JSONObject()
                pObj.put("baseUrl", p.baseUrl)
                if (p.apiKey != null) pObj.put("apiKey", p.apiKey)
                pObj.put("api", p.api)
                pObj.put("authHeader", p.authHeader)
                p.headers?.let { h -> pObj.put("headers", JSONObject(h)) }
                val modelsArr = JSONArray()
                p.models.forEach { md ->
                    val mObj = JSONObject()
                    mObj.put("id", md.id)
                    mObj.put("name", md.name)
                    mObj.put("reasoning", md.reasoning)
                    mObj.put("contextWindow", md.contextWindow)
                    mObj.put("maxTokens", md.maxTokens)
                    md.api?.let { mObj.put("api", it) }
                    modelsArr.put(mObj)
                }
                pObj.put("models", modelsArr)
                providersObj.put(name, pObj)
            }
            modelsObj.put("providers", providersObj)
            root.put("models", modelsObj)
        }

        // Agents
        config.agents?.let { a ->
            val agentsObj = root.optJSONObject("agents") ?: JSONObject()
            val defaultsObj = JSONObject()
            a.defaults.model?.let { model ->
                val modelObj = JSONObject()
                model.primary?.let { modelObj.put("primary", it) }
                defaultsObj.put("model", modelObj)
            }
            agentsObj.put("defaults", defaultsObj)
            root.put("agents", agentsObj)
        }

        // Agent (Android extension)
        val agentObj = root.optJSONObject("agent") ?: JSONObject()
        agentObj.put("defaultModel", config.agent.defaultModel)
        agentObj.put("maxIterations", config.agent.maxIterations)
        root.put("agent", agentObj)

        // Channels (对齐 OpenClaw: channels.feishu)
        val channelsObj = root.optJSONObject("channels") ?: JSONObject()
        val feishu = config.channels.feishu
        val feishuObj = JSONObject()
        feishuObj.put("enabled", feishu.enabled)
        feishuObj.put("appId", feishu.appId)
        feishuObj.put("appSecret", feishu.appSecret)
        feishuObj.put("domain", feishu.domain)
        feishuObj.put("connectionMode", feishu.connectionMode)
        feishuObj.put("dmPolicy", feishu.dmPolicy)
        feishuObj.put("groupPolicy", feishu.groupPolicy)
        feishuObj.put("requireMention", feishu.requireMention)
        feishuObj.put("groupCommandMentionBypass", feishu.groupCommandMentionBypass)
        feishuObj.put("allowMentionlessInMultiBotGroup", feishu.allowMentionlessInMultiBotGroup)
        channelsObj.put("feishu", feishuObj)
        root.put("channels", channelsObj)

        // Gateway (对齐 OpenClaw: 只有 port/mode/bind/auth)
        val gwObj = root.optJSONObject("gateway") ?: JSONObject()
        gwObj.put("port", config.gateway.port)
        root.put("gateway", gwObj)
    }

    fun reloadOpenClawConfig(): OpenClawConfig {
        Log.i(TAG, "重新加载配置...")
        openclawConfigCacheValid = false
        return loadOpenClawConfig()
    }

    fun enableHotReload(callback: ((OpenClawConfig) -> Unit)? = null) {
        if (hotReloadEnabled) return
        this.reloadCallback = callback
        try {
            ensureConfigDir()
            fileObserver = object : FileObserver(configDir, MODIFY or CREATE or DELETE) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == OPENCLAW_CONFIG_FILE) {
                        Log.i(TAG, "检测到配置文件变化")
                        val newConfig = reloadOpenClawConfig()
                        reloadCallback?.invoke(newConfig)
                    }
                }
            }
            fileObserver?.startWatching()
            hotReloadEnabled = true
            Log.i(TAG, "✅ 配置热重载已启用")
        } catch (e: Exception) {
            Log.e(TAG, "启用热重载失败", e)
        }
    }

    fun disableHotReload() {
        fileObserver?.stopWatching()
        fileObserver = null
        reloadCallback = null
        hotReloadEnabled = false
    }

    fun isHotReloadEnabled(): Boolean = hotReloadEnabled

    fun getFeishuConfig(): com.xiaomo.feishu.FeishuConfig {
        return FeishuConfigAdapter.toFeishuConfig(loadOpenClawConfig().channels.feishu)
    }

    // ============ Private Helpers ============

    private fun ensureConfigDir() {
        if (!configDir.exists()) configDir.mkdirs()
    }

    private fun createDefaultConfig() {
        try {
            val defaultConfig = context.assets.open("openclaw.json.default.txt")
                .bufferedReader().use { it.readText() }
            openclawConfigFile.writeText(defaultConfig)
            Log.i(TAG, "✅ 创建默认配置: ${openclawConfigFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "创建默认配置失败", e)
            throw e
        }
    }

    private fun replaceEnvVars(json: String): String {
        var result = json
        val pattern = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\}""")
        pattern.findAll(json).forEach { match ->
            val varName = match.groupValues[1]
            val value = System.getenv(varName)
            if (value != null) {
                result = result.replace("\${$varName}", value)
            } else {
                // Fallback: use built-in key for known providers on Android
                val builtInValue = when (varName) {
                    "OPENROUTER_API_KEY" -> BuiltInKeyProvider.getKey()
                    else -> null
                }
                if (builtInValue != null) {
                    result = result.replace("\${$varName}", builtInValue)
                    Log.i(TAG, "🔑 使用内置 Key 替换: \${$varName}")
                } else {
                    Log.w(TAG, "⚠️ 环境变量未找到: \${$varName}")
                }
            }
        }
        return result
    }

    /**
     * 精简验证 — 只检查关键字段
     */
    private fun validateConfig(config: OpenClawConfig) {
        // Validate feishu if enabled
        val feishu = config.channels.feishu
        if (feishu.enabled) {
            require(feishu.appId.isNotBlank() && !feishu.appId.startsWith("\${")) {
                "Feishu 已启用但 appId 未配置"
            }
            require(feishu.appSecret.isNotBlank() && !feishu.appSecret.startsWith("\${")) {
                "Feishu 已启用但 appSecret 未配置"
            }
        }

        // Validate providers have baseUrl
        config.resolveProviders().forEach { (name, provider) ->
            require(provider.baseUrl.isNotBlank()) {
                "Provider '$name' 缺少 baseUrl"
            }
        }

        Log.i(TAG, "✅ 配置验证通过")
    }
}
