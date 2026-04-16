package com.xiaomo.androidforclaw.hermes.plugins.memory.honcho

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.xiaomo.androidforclaw.hermes.getLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Honcho API 客户端
 * 1:1 对齐 hermes-agent/plugins/memory/honcho/client.py
 *
 * 使用 OkHttp 替代 Python requests。
 * 支持云端和本地 Honcho 服务器。
 */

private val logger = getLogger("honcho.client")

// ── 常量 ──────────────────────────────────────────────────────────────────
const val HOST = "hermes"
private const val DEFAULT_BASE_URL = "https://api.honcho.dev"
private const val API_VERSION = "v1"

// ── 配置类 ────────────────────────────────────────────────────────────────

/**
 * Honcho 客户端配置
 * Python: class HonchoClientConfig
 */
data class HonchoClientConfig(
    val host: String = HOST,
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val workspaceId: String = "",
    val peerName: String = "",
    val aiPeer: String = "hermes-assistant",
    val enabled: Boolean = true,
    val recallMode: String = "hybrid",
    val writeFrequency: String = "async",
    val sessionStrategy: String = "per-directory",
    val contextTokens: Int? = null,
    val dialecticReasoningLevel: String = "low",
    val dialecticDynamic: Boolean = true,
    val dialecticMaxChars: Int = 600,
    val messageMaxChars: Int = 25000,
    val dialecticMaxInputChars: Int = 10000,
    val saveMessages: Boolean = true,
    val observationMode: String = "directional",
    val userObserveMe: Boolean = true,
    val userObserveOthers: Boolean = true,
    val aiObserveMe: Boolean = true,
    val aiObserveOthers: Boolean = true) {
    companion object {
        /**
         * 从全局配置创建
         * Python: HonchoClientConfig.from_global_config(host=None)
         */
        fun fromGlobalConfig(host: String? = null): HonchoClientConfig {
            // 简化版：从 Android SharedPreferences 或配置文件读取
            return HonchoClientConfig(
                host = host ?: HOST,
                apiKey = System.getenv("HONCHO_API_KEY") ?: "",
                baseUrl = DEFAULT_BASE_URL)
        }
    }

    /**
     * 解析 session 名称
     * Python: resolve_session_name()
     */
    fun resolveSessionName(): String {
        return when (sessionStrategy) {
            "per-directory" -> {
                val cwd = System.getProperty("user.dir") ?: "default"
                val sanitized = cwd.replace(Regex("[^a-zA-Z0-9_-]"), "-")
                "$HOST:$sanitized"
            }
            "per-session" -> "$HOST:${System.currentTimeMillis()}"
            "global" -> HOST
            else -> HOST
        }
    }

    /**
     * 检查是否为本地服务器
     */
    fun isLocal(): Boolean {
        return baseUrl.contains("localhost") ||
               baseUrl.contains("127.0.0.1") ||
               baseUrl.contains("::1")
    }
}

// ── Peer 和 Session 类型 ──────────────────────────────────────────────────

/**
 * Honcho Peer
 * Python: client.peer(peer_id)
 */
data class HonchoPeer(
    val id: String,
    val name: String? = null,
    val metadata: Map<String, Any> = emptyMap())

/**
 * Honcho Session
 * Python: client.session(session_id)
 */
data class HonchoSessionData(
    val id: String,
    val peers: List<HonchoPeer> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis())

/**
 * Honcho Message
 * Python: peer.message(content)
 */
data class HonchoMessage(
    val id: String = "",
    val peerId: String = "",
    val content: String = "",
    val role: String = "user",
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis())

/**
 * Honcho Context（session.context() 的返回值）
 */
data class HonchoContext(
    val messages: List<HonchoMessage> = emptyList(),
    val representation: String = "",
    val peerRepresentation: String = "",
    val peerCard: List<String> = emptyList(),
    val summary: String = "")

// ── Honcho 客户端 ─────────────────────────────────────────────────────────

/**
 * Honcho HTTP 客户端
 * Python: class Honcho (from honcho package)
 */
class HonchoClient(
    private val config: HonchoClientConfig) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val peers = mutableMapOf<String, HonchoPeer>()
    private val sessions = mutableMapOf<String, HonchoSessionData>()

    // ── Peer 操作 ──────────────────────────────────────────────────────────

    /**
     * 获取或创建 peer
     * Python: client.peer(peer_id)
     */
    fun peer(peerId: String): HonchoPeer {
        return peers.getOrPut(peerId) {
            HonchoPeer(id = peerId)
        }
    }

    /**
     * 获取或创建 session
     * Python: client.session(session_id)
     */
    fun session(sessionId: String): HonchoSessionData {
        return sessions.getOrPut(sessionId) {
            HonchoSessionData(id = sessionId)
        }
    }

    // ── HTTP 请求 ──────────────────────────────────────────────────────────

    /**
     * 发送 GET 请求
     */
    private fun get(path: String): String? {
        val url = "${config.baseUrl}/$API_VERSION/$path"
        val request = buildRequest(url, "GET")
        return executeRequest(request)
    }

    /**
     * 发送 POST 请求
     */
    private fun post(path: String, body: Any): String? {
        val url = "${config.baseUrl}/$API_VERSION/$path"
        val jsonBody = gson.toJson(body)
        val request = buildRequest(url, "POST", jsonBody)
        return executeRequest(request)
    }

    /**
     * 构建请求
     */
    private fun buildRequest(url: String, method: String, body: String? = null): Request {
        val builder = Request.Builder().url(url)

        // 认证头
        if (config.apiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }
        builder.addHeader("Content-Type", "application/json")
        builder.addHeader("User-Agent", "Hermes-Android/${com.xiaomo.androidforclaw.hermes.HERMES_VERSION}")

        when (method) {
            "GET" -> builder.get()
            "POST" -> {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                builder.post(body?.toRequestBody(mediaType) ?: "".toRequestBody(mediaType))
            }
            "PUT" -> {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                builder.put(body?.toRequestBody(mediaType) ?: "".toRequestBody(mediaType))
            }
            "DELETE" -> builder.delete()
        }

        return builder.build()
    }

    /**
     * 执行请求
     */
    private fun executeRequest(request: Request): String? {
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    logger.warning("Honcho API error: ${response.code} ${response.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Honcho request failed: ${e.message}")
            null
        }
    }

    // ── 高级 API ──────────────────────────────────────────────────────────

    /**
     * 获取 peer 上下文
     */
    fun getPeerContext(peerId: String, searchQuery: String? = null): HonchoContext {
        val path = buildString {
            append("peers/$peerId/context")
            if (searchQuery != null) {
                append("?search_query=$searchQuery")
            }
        }

        val response = get(path) ?: return HonchoContext()
        return try {
            gson.fromJson(response, HonchoContext::class.java)
        } catch (e: Exception) {
            HonchoContext()
        }
    }

    /**
     * 获取 peer 表示
     */
    fun getPeerRepresentation(peerId: String): String {
        val ctx = getPeerContext(peerId)
        return ctx.representation.ifEmpty { ctx.peerRepresentation }
    }

    /**
     * 获取 peer 卡片
     */
    fun getPeerCard(peerId: String): List<String> {
        val ctx = getPeerContext(peerId)
        return ctx.peerCard
    }

    /**
     * 添加消息到 session
     */
    fun addMessages(sessionId: String, messages: List<HonchoMessage>): Boolean {
        val body = mapOf("messages" to messages)
        val response = post("sessions/$sessionId/messages", body)
        return response != null
    }

    /**
     * 对话式查询
     */
    fun chat(
        query: String,
        target: String? = null,
        reasoningLevel: String = "low"): String? {
        val body = mutableMapOf<String, Any>(
            "query" to query,
            "reasoning_level" to reasoningLevel)
        if (target != null) {
            body["target"] = target
        }
        val response = post("chat", body)
        return response?.let {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val result: Map<String, String> = gson.fromJson(it, type)
                result["response"]
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 创建结论
     */
    fun createConclusion(
        peerId: String,
        targetPeerId: String,
        content: String,
        sessionId: String): Boolean {
        val body = mapOf(
            "content" to content,
            "session_id" to sessionId)
        val response = post("peers/$peerId/conclusions/$targetPeerId", body)
        return response != null
    }

    /**
     * 上传文件
     */
    fun uploadFile(
        sessionId: String,
        peerId: String,
        fileName: String,
        content: ByteArray,
        mimeType: String = "text/plain",
        metadata: Map<String, Any> = emptyMap()): Boolean {
        val body = mapOf(
            "file_name" to fileName,
            "content" to String(content, Charsets.UTF_8),
            "mime_type" to mimeType,
            "metadata" to metadata)
        val response = post("sessions/$sessionId/files", body)
        return response != null
    }

    /**
     * 测试连接
     */
    fun testConnection(): Boolean {
        return try {
            val response = get("health")
            response != null
        } catch (e: Exception) {
            false
        }
    }
}

// ── 全局客户端 ──────────────────────────────────────────────────────────────

private var _globalClient: HonchoClient? = null

/**
 * 获取全局 Honcho 客户端
 * Python: get_honcho_client(config=None)
 */
fun getHonchoClient(config: HonchoClientConfig? = null): HonchoClient {
    if (_globalClient == null || config != null) {
        _globalClient = HonchoClient(config ?: HonchoClientConfig.fromGlobalConfig())
    }
    return _globalClient!!
}

/**
 * 重置全局客户端
 * Python: reset_honcho_client()
 */
fun resetHonchoClient() {
    _globalClient = null
}

/**
 * 解析活跃 host
 * Python: resolve_active_host()
 */
fun resolveActiveHost(): String {
    return HOST
}

/**
 * 解析配置路径
 * Python: resolve_config_path()
 */
fun resolveConfigPath(): File {
    return File(com.xiaomo.androidforclaw.hermes.getHermesHome(), "honcho.json")
}
