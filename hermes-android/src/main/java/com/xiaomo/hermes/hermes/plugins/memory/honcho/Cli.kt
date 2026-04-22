package com.xiaomo.hermes.hermes.plugins.memory.honcho

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xiaomo.hermes.hermes.getLogger
import com.xiaomo.hermes.hermes.getHermesHome
import com.xiaomo.hermes.hermes.prettyGson
import java.io.File

/**
 * Honcho CLI 命令处理（Android 简化版）
 * 1:1 对齐 hermes-agent/plugins/memory/honcho/cli.py
 *
 * Python 版本的 TUI 交互（input、print 等）替换为 Android UI 回调。
 * 核心逻辑保持 1:1 对齐。
 */

private val logger = getLogger("honcho.cli")

/** Host constant is `HOST` (Python top-level), defined in Client.kt */

// ── 配置读写 ──────────────────────────────────────────────────────────────

/**
 * 读取配置
 * Python: _read_config() -> dict
 */
fun readHonchoConfig(): Map<String, Any> {
    val path = resolveConfigPath()
    if (!path.exists()) return emptyMap()

    return try {
        val content = path.readText(Charsets.UTF_8)
        val type = com.google.gson.reflect.TypeToken.getParameterized(
            Map::class.java, String::class.java, Any::class.java
        ).type
        Gson().fromJson(content, type) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * 写入配置
 * Python: _write_config(cfg, path=None)
 */
fun writeHonchoConfig(cfg: Map<String, Any>, path: File? = null) {
    val writePath = path ?: localConfigPath()
    writePath.parentFile.mkdirs()
    writePath.writeText(
        prettyGson.toJson(cfg) + "\n",
        Charsets.UTF_8)
}

/**
 * 本地配置路径
 * Python: _local_config_path() -> Path
 */
fun localConfigPath(): File {
    return File(getHermesHome(), "honcho.json")
}

/**
 * 解析 API Key
 * Python: _resolve_api_key(cfg) -> str
 */
fun resolveApiKey(cfg: Map<String, Any>): String {
    val hostKey = ((cfg["hosts"] as? Map<*, *>)?.get(hostKey()) as? Map<*, *>)?.get("apiKey") as? String
    return hostKey ?: (cfg["apiKey"] as? String) ?: System.getenv("HONCHO_API_KEY") ?: ""
}

/**
 * 获取 host key
 * Python: _host_key() -> str
 */
fun hostKey(): String {
    return HOST
}

// ── 命令实现 ──────────────────────────────────────────────────────────────

/**
 * 启用 Honcho
 * Python: cmd_enable(args)
 */
fun cmdEnable() {
    val cfg = readHonchoConfig().toMutableMap()
    val host = hostKey()
    val hosts = (cfg.getOrPut("hosts") { mutableMapOf<String, Any>() } as MutableMap<String, Any>)
    val block = (hosts.getOrPut(host) { mutableMapOf<String, Any>() } as MutableMap<String, Any>)

    if (block["enabled"] == true) {
        logger.info("Honcho is already enabled")
        return
    }

    block["enabled"] = true

    // 克隆默认配置
    if (block["aiPeer"] == null) {
        val defaultBlock = (hosts[HOST] as? Map<String, Any>) ?: emptyMap()
        for (key in listOf(
            "recallMode", "writeFrequency", "sessionStrategy",
            "contextTokens", "dialecticReasoningLevel", "dialecticDynamic",
            "dialecticMaxChars", "messageMaxChars", "dialecticMaxInputChars",
            "saveMessages", "observation"
        )) {
            val value = defaultBlock[key]
            if (value != null && key !in block) {
                block[key] = value
            }
        }

        val peerName = defaultBlock["peerName"] ?: cfg["peerName"]
        if (peerName != null && "peerName" !in block) {
            block["peerName"] = peerName
        }

        val aiPeer = if ("." in host) host.split(".", limit = 2)[1] else host
        block.putIfAbsent("aiPeer", aiPeer)
        block.putIfAbsent("workspace", defaultBlock["workspace"] ?: cfg["workspace"] ?: HOST)
    }

    writeHonchoConfig(cfg)
    logger.info("Honcho enabled")

    // 创建 peer
    ensurePeerExists(host)
}

/**
 * 禁用 Honcho
 * Python: cmd_disable(args)
 */
fun cmdDisable() {
    val cfg = readHonchoConfig().toMutableMap()
    val host = hostKey()
    val hosts = cfg["hosts"] as? Map<String, Any> ?: return
    val block = hosts[host] as? MutableMap<String, Any> ?: return

    if (block["enabled"] == false) {
        logger.info("Honcho is already disabled")
        return
    }

    block["enabled"] = false
    writeHonchoConfig(cfg)
    logger.info("Honcho disabled")
}

/**
 * 同步配置到所有 profile
 * Python: cmd_sync(args)
 */
fun cmdSync(): Int {
    val cfg = readHonchoConfig()
    val hosts = cfg["hosts"] as? Map<String, Any> ?: return 0
    val defaultBlock = hosts[HOST] as? Map<String, Any> ?: return 0
    val hasKey = (cfg["apiKey"] as? String)?.isNotEmpty() == true ||
                 System.getenv("HONCHO_API_KEY")?.isNotEmpty() == true

    if (defaultBlock.isEmpty() && !hasKey) return 0

    // 简化版：只处理当前配置
    return 0
}

/**
 * 确保 peer 存在
 * Python: _ensure_peer_exists(host_key=None)
 */
fun ensurePeerExists(hostKey: String? = null): Boolean {
    return try {
        val hcfg = HonchoClientConfig.fromGlobalConfig(host = hostKey)
        if (!hcfg.enabled || (hcfg.apiKey.isNullOrEmpty() && hcfg.baseUrl.isNullOrEmpty())) {
            return false
        }
        val client = getHonchoClient(hcfg)
        client.peer(hcfg.aiPeer)
        if (!hcfg.peerName.isNullOrEmpty()) {
            client.peer(hcfg.peerName)
        }
        true
    } catch (e: Exception) {
        false
    }
}

// ── 状态查询 ──────────────────────────────────────────────────────────────

/**
 * 获取 Honcho 状态
 * Python: cmd_status(args)
 */
data class HonchoStatus(
    val enabled: Boolean,
    val apiKeyMasked: String,
    val workspace: String,
    val aiPeer: String,
    val userPeer: String,
    val sessionKey: String,
    val recallMode: String,
    val writeFrequency: String,
    val observationMode: String,
    val connected: Boolean,
    val userCard: List<String> = emptyList(),
    val aiRepresentation: String = "")

fun getHonchoStatus(): HonchoStatus {
    val cfg = readHonchoConfig()
    val hcfg = HonchoClientConfig.fromGlobalConfig(host = hostKey())

    val apiKey = hcfg.apiKey.orEmpty()
    val masked = if (apiKey.length > 8) {
        "...${apiKey.takeLast(8)}"
    } else if (apiKey.isNotEmpty()) "set" else "not set"

    var connected = false
    var userCard = emptyList<String>()
    var aiRepresentation = ""

    if (hcfg.enabled && (!hcfg.apiKey.isNullOrEmpty() || !hcfg.baseUrl.isNullOrEmpty())) {
        try {
            val client = getHonchoClient(hcfg)
            connected = client.testConnection()

            if (connected) {
                val mgr = HonchoSessionManager(honcho = client, config = hcfg)
                val sessionKey = hcfg.resolveSessionName() ?: ""
                mgr.getOrCreate(sessionKey)

                userCard = mgr.getPeerCard(sessionKey)
                val aiRep = mgr.getAiRepresentation(sessionKey)
                aiRepresentation = aiRep["representation"] ?: ""
            }
        } catch (e: Exception) {
            logger.warning("Connection test failed: ${e.message}")
        }
    }

    return HonchoStatus(
        enabled = hcfg.enabled,
        apiKeyMasked = masked,
        workspace = hcfg.workspaceId,
        aiPeer = hcfg.aiPeer,
        userPeer = hcfg.peerName.orEmpty(),
        sessionKey = hcfg.resolveSessionName().orEmpty(),
        recallMode = hcfg.recallMode,
        writeFrequency = hcfg.writeFrequency.toString(),
        observationMode = hcfg.observationMode,
        connected = connected,
        userCard = userCard,
        aiRepresentation = aiRepresentation)
}

// ── Peer 管理 ──────────────────────────────────────────────────────────────

/**
 * 更新 peer 名称
 * Python: cmd_peer(args) --user / --ai
 */
fun updatePeerNames(userName: String? = null, aiName: String? = null) {
    val cfg = readHonchoConfig().toMutableMap()
    val host = hostKey()
    val hosts = (cfg.getOrPut("hosts") { mutableMapOf<String, Any>() } as MutableMap<String, Any>)
    val block = (hosts.getOrPut(host) { mutableMapOf<String, Any>() } as MutableMap<String, Any>)

    if (userName != null) {
        block["peerName"] = userName.trim()
        logger.info("User peer -> ${userName.trim()}")
    }

    if (aiName != null) {
        block["aiPeer"] = aiName.trim()
        logger.info("AI peer -> ${aiName.trim()}")
    }

    writeHonchoConfig(cfg)
}

// ── 模式管理 ──────────────────────────────────────────────────────────────

/**
 * 设置 recall 模式
 * Python: cmd_mode(args)
 */
fun setRecallMode(mode: String) {
    if (mode !in listOf("hybrid", "context", "tools")) {
        logger.warning("Invalid mode '$mode'. Options: hybrid, context, tools")
        return
    }

    val cfg = readHonchoConfig().toMutableMap()
    val host = hostKey()
    val hosts = (cfg.getOrPut("hosts") { mutableMapOf<String, Any>() } as MutableMap<String, Any>)
    val block = (hosts.getOrPut(host) { mutableMapOf<String, Any>() } as MutableMap<String, Any>)

    block["recallMode"] = mode
    writeHonchoConfig(cfg)
    logger.info("Recall mode -> $mode")
}

/**
 * 获取当前 recall 模式
 */
fun getRecallMode(): String {
    val cfg = readHonchoConfig()
    val hosts = cfg["hosts"] as? Map<String, Any> ?: return "hybrid"
    val block = hosts[hostKey()] as? Map<String, Any> ?: return "hybrid"
    return block["recallMode"] as? String ?: cfg["recallMode"] as? String ?: "hybrid"
}

// ── Token 配置 ──────────────────────────────────────────────────────────────

/**
 * 设置 token 预算
 * Python: cmd_tokens(args) --context / --dialectic
 */
fun setTokenBudget(contextTokens: Int? = null, dialecticMaxChars: Int? = null) {
    val cfg = readHonchoConfig().toMutableMap()
    val host = hostKey()
    val hosts = (cfg.getOrPut("hosts") { mutableMapOf<String, Any>() } as MutableMap<String, Any>)
    val block = (hosts.getOrPut(host) { mutableMapOf<String, Any>() } as MutableMap<String, Any>)

    if (contextTokens != null) {
        block["contextTokens"] = contextTokens
        logger.info("context tokens -> $contextTokens")
    }

    if (dialecticMaxChars != null) {
        block["dialecticMaxChars"] = dialecticMaxChars
        logger.info("dialectic cap -> $dialecticMaxChars chars")
    }

    writeHonchoConfig(cfg)
}

// ── Session 映射 ──────────────────────────────────────────────────────────────

/**
 * 列出 session 映射
 * Python: cmd_sessions(args)
 */
fun listSessionMappings(): Map<String, String> {
    val cfg = readHonchoConfig()
    val sessions = cfg["sessions"] as? Map<*, *> ?: return emptyMap()
    return sessions.entries.associate { (k, v) -> k.toString() to v.toString() }
}

/**
 * 映射目录到 session 名称
 * Python: cmd_map(args)
 */
fun mapSession(sessionName: String) {
    val sanitized = sessionName.replace(Regex("[^a-zA-Z0-9_-]"), "-").trim('-')
    if (sanitized != sessionName) {
        logger.info("Session name sanitized to: $sanitized")
    }

    val cfg = readHonchoConfig().toMutableMap()
    val sessions = (cfg.getOrPut("sessions") { mutableMapOf<String, Any>() } as MutableMap<String, Any>)
    sessions[System.getProperty("user.dir") ?: "default"] = sanitized
    writeHonchoConfig(cfg)
    logger.info("Mapped directory -> $sanitized")
}

// ── 身份管理 ──────────────────────────────────────────────────────────────

/**
 * 播种 AI 身份
 * Python: cmd_identity(args)
 */
fun seedIdentity(content: String, source: String = "manual"): Boolean {
    val cfg = readHonchoConfig()
    if (resolveApiKey(cfg).isEmpty()) {
        logger.warning("No API key configured")
        return false
    }

    return try {
        val hcfg = HonchoClientConfig.fromGlobalConfig(host = hostKey())
        val client = getHonchoClient(hcfg)
        val mgr = HonchoSessionManager(honcho = client, config = hcfg)
        val sessionKey = hcfg.resolveSessionName() ?: ""
        mgr.getOrCreate(sessionKey)
        mgr.seedAiIdentity(sessionKey, content, source = source)
    } catch (e: Exception) {
        logger.error("Failed to seed identity: ${e.message}")
        false
    }
}

/**
 * 获取 AI 表示
 */
fun getAiIdentity(): Map<String, String> {
    return try {
        val hcfg = HonchoClientConfig.fromGlobalConfig(host = hostKey())
        val client = getHonchoClient(hcfg)
        val mgr = HonchoSessionManager(honcho = client, config = hcfg)
        val sessionKey = hcfg.resolveSessionName() ?: ""
        mgr.getOrCreate(sessionKey)
        mgr.getAiRepresentation(sessionKey)
    } catch (e: Exception) {
        logger.error("Failed to get AI identity: ${e.message}")
        mapOf("representation" to "", "card" to "")
    }
}

/**
 * 获取用户 peer 卡片
 */
fun getUserIdentity(): List<String> {
    return try {
        val hcfg = HonchoClientConfig.fromGlobalConfig(host = hostKey())
        val client = getHonchoClient(hcfg)
        val mgr = HonchoSessionManager(honcho = client, config = hcfg)
        val sessionKey = hcfg.resolveSessionName() ?: ""
        mgr.getOrCreate(sessionKey)
        mgr.getPeerCard(sessionKey)
    } catch (e: Exception) {
        logger.error("Failed to get user identity: ${e.message}")
        emptyList()
    }
}
