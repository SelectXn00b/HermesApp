package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * MCP (Model Context Protocol) Client Support.
 * Connects to external MCP servers via HTTP, discovers tools, and calls them.
 * Ported from mcp_tool.py
 */
object McpTool {

    private const val TAG = "Mcptool"
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    data class McpServerConfig(
        val command: String? = null,
        val args: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
        val url: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val timeout: Int = 120,
        val connectTimeout: Int = 60)

    data class McpToolDef(
        val name: String,
        val description: String = "",
        val inputSchema: Map<String, Any> = emptyMap())

    data class McpCallResult(
        val content: String = "",
        val isError: Boolean = false,
        val error: String? = null)

    private val _servers = ConcurrentHashMap<String, McpServerConfig>()
    private val _tools = ConcurrentHashMap<String, Pair<String, McpToolDef>>() // toolName -> (serverName, def)
    private val _httpClients = ConcurrentHashMap<String, OkHttpClient>()

    /**
     * Register an MCP server configuration.
     */
    fun registerServer(name: String, config: McpServerConfig) {
        _servers[name] = config
        if (!config.url.isNullOrEmpty()) {
            _httpClients[name] = OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
                .readTimeout(config.timeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(config.timeout.toLong(), TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Unregister an MCP server.
     */
    fun unregisterServer(name: String) {
        _servers.remove(name)
        _httpClients.remove(name)
        _tools.entries.removeIf { it.value.first == name }
    }

    /**
     * Discover tools from an HTTP MCP server.
     */
    fun discoverTools(serverName: String): List<McpToolDef> {
        val config = _servers[serverName] ?: return emptyList()
        val url = config.url ?: return emptyList()

        return try {
            val client = _httpClients[serverName] ?: OkHttpClient()
            val payload = gson.toJson(mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "tools/list"))

            val requestBuilder = Request.Builder()
                .url("$url/mcp")
                .post(payload.toRequestBody(JSON))
                .header("Content-Type", "application/json")

            for ((k, v) in config.headers) {
                requestBuilder.header(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to list tools from $serverName: ${response.code}")
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                val json = gson.fromJson(body, Map::class.java) as Map<String, Any>
                val result = json["result"] as? Map<String, Any> ?: return emptyList()
                val toolsList = result["tools"] as? List<Map<String, Any>> ?: return emptyList()

                toolsList.map { tool ->
                    val name = tool["name"] as? String ?: "unknown"
                    val desc = tool["description"] as? String ?: ""
                    val schema = (tool["inputSchema"] as? Map<String, Any>) ?: emptyMap()
                    val def = McpToolDef(name, desc, schema)
                    _tools[name] = serverName to def
                    def
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover tools from $serverName: ${e.message}")
            emptyList()
        }
    }

    /**
     * Call an MCP tool.
     */
    fun callTool(toolName: String, arguments: Map<String, Any> = emptyMap()): McpCallResult {
        val (serverName, _) = _tools[toolName]
            ?: return McpCallResult(isError = true, error = "Tool '$toolName' not found")

        val config = _servers[serverName]
            ?: return McpCallResult(isError = true, error = "Server '$serverName' not configured")

        val url = config.url
            ?: return McpCallResult(isError = true, error = "Server '$serverName' has no HTTP endpoint")

        return try {
            val client = _httpClients[serverName] ?: OkHttpClient()
            val payload = gson.toJson(mapOf(
                "jsonrpc" to "2.0",
                "id" to System.currentTimeMillis(),
                "method" to "tools/call",
                "params" to mapOf("name" to toolName, "arguments" to arguments)))

            val requestBuilder = Request.Builder()
                .url("$url/mcp")
                .post(payload.toRequestBody(JSON))
                .header("Content-Type", "application/json")

            for ((k, v) in config.headers) {
                requestBuilder.header(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return McpCallResult(isError = true, error = "HTTP ${response.code}: ${body.take(500)}")
                }

                val json = gson.fromJson(body, Map::class.java) as Map<String, Any>
                val error = json["error"] as? Map<String, Any>
                if (error != null) {
                    return McpCallResult(isError = true, error = stripCredentials(error["message"] as? String ?: "Unknown error"))
                }

                val result = json["result"] as? Map<String, Any> ?: return McpCallResult(isError = true, error = "No result")
                val contentList = result["content"] as? List<Map<String, Any>> ?: emptyList()

                val textContent = contentList
                    .filter { it["type"] == "text" }
                    .joinToString("\n") { it["text"] as? String ?: "" }

                McpCallResult(content = textContent, isError = false)
            }
        } catch (e: Exception) {
            McpCallResult(isError = true, error = "Call failed: ${stripCredentials(e.message)}")
        }
    }

    /**
     * Get all discovered tools.
     */
    fun getDiscoveredTools(): Map<String, McpToolDef> = _tools.mapValues { it.value.second }

    /**
     * Get all registered servers.
     */
    fun getServers(): Map<String, McpServerConfig> = _servers.toMap()

    /**
     * Strip credentials from error messages.
     */
    private fun stripCredentials(message: String?): String {
        if (message.isNullOrEmpty()) return ""
        return message
            .replace(Regex("ghp_[A-Za-z0-9_]{1,255}"), "[CREDENTIAL_REDACTED]")
            .replace(Regex("sk-[A-Za-z0-9_]{1,255}"), "[CREDENTIAL_REDACTED]")
            .replace(Regex("Bearer\\s+\\S+"), "Bearer [CREDENTIAL_REDACTED]")
            .replace(Regex("(token|key|password|secret)=[^\\s&,;\"']{1,255}", RegexOption.IGNORE_CASE), "$1=[CREDENTIAL_REDACTED]")
    }


    // === Constants (ported from mcp_tool.py) ===
    val _MCP_AVAILABLE = false          // MCP SDK not available on Android
    val _MCP_HTTP_AVAILABLE = false     // HTTP transport not available
    val _MCP_SAMPLING_TYPES = setOf("text", "tool_use")
    val _MCP_NOTIFICATION_TYPES = setOf("tools_list_changed", "prompts_list_changed", "resources_list_changed")
    val _MCP_MESSAGE_HANDLER_SUPPORTED = "false"
    val _DEFAULT_TOOL_TIMEOUT = 120          // seconds for tool calls
    val _DEFAULT_CONNECT_TIMEOUT = 60        // seconds for initial connection
    val _MAX_RECONNECT_RETRIES = 5
    val _MAX_INITIAL_CONNECT_RETRIES = 3
    val _MAX_BACKOFF_SECONDS = 60
    val _SAFE_ENV_KEYS = setOf("PATH", "HOME", "USER", "LANG", "LC_ALL", "TERM", "SHELL", "TMPDIR")
    val _CREDENTIAL_PATTERN = Regex(
        """(?:ghp_[A-Za-z0-9_]{1,255}|sk-[A-Za-z0-9_]{1,255}|Bearer\s+\S+|token=[^\s&,;"']{1,255}|key=[^\s&,;"']{1,255}|password=[^\s&,;"']{1,255}|secret=[^\s&,;"']{1,255})""",
        RegexOption.IGNORE_CASE
    )
    val _UTILITY_CAPABILITY_METHODS = setOf("sampling/createMessage", "roots/list")

    // === Rate limiter state ===
    private val _rateLimitTimestamps = mutableListOf<Long>()
    private val _rateLimitMaxRequests = 10
    private val _rateLimitWindowMs = 60_000L // 1 minute sliding window

    /**
     * Check if the message_handler kwarg is supported by ClientSession.
     * On Android, MCP SDK is not available, so always returns false.
     */
    fun checkMessageHandlerSupport(): Boolean {
        return false
    }

    /** Sliding-window rate limiter. Returns true if request is allowed. */
    @Synchronized
    fun _checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        _rateLimitTimestamps.removeAll { now - it > _rateLimitWindowMs }
        if (_rateLimitTimestamps.size >= _rateLimitMaxRequests) {
            return false
        }
        _rateLimitTimestamps.add(now)
        return true
    }

    /**
     * Config override > server hint > null (use default).
     * On Android, always returns null (no sampling support).
     */
    fun _resolveModel(preferences: Any?): String? {
        if (preferences is String && preferences.isNotEmpty()) return preferences
        if (preferences is Map<*, *>) {
            val model = preferences["model"] as? String
            if (!model.isNullOrEmpty()) return model
        }
        return null
    }

    /**
     * Extract text from a ToolResultContent block.
     * Supports maps with "type" and "text" fields (MCP content format).
     */
    fun _extractToolResultText(block: Any?): String {
        if (block is String) return block
        if (block is Map<*, *>) {
            val type = block["type"]
            if (type == "text") return (block["text"] as? String) ?: ""
            if (type == "error") return "Error: ${(block["text"] as? String) ?: "unknown"}"
        }
        if (block is List<*>) {
            return block.joinToString("\n") { _extractToolResultText(it) }
        }
        return block?.toString() ?: ""
    }

    /**
     * Convert MCP SamplingMessages to OpenAI format.
     * Each message has "role" and "content" fields.
     */
    fun _convertMessages(params: Any?): List<Map<String, Any>> {
        if (params !is Map<*, *>) return emptyList()
        val messages = params["messages"] as? List<*> ?: return emptyList()
        return messages.mapNotNull { msg ->
            if (msg !is Map<*, *>) return@mapNotNull null
            val role = msg["role"] as? String ?: "user"
            val content = msg["content"]
            val text = when (content) {
                is String -> content
                is Map<*, *> -> _extractToolResultText(content)
                is List<*> -> content.joinToString("\n") { _extractToolResultText(it) }
                else -> content?.toString() ?: ""
            }
            mapOf("role" to role, "content" to text)
        }
    }

    /**
     * Return error data as a map (MCP spec ErrorData format).
     */
    fun _error(message: String, code: Int = -1): Map<String, Any> {
        return mapOf(
            "code" to code,
            "message" to stripCredentials(message))
    }

    /**
     * Build a result map from an LLM tool_calls response.
     * Returns a map representing CreateMessageResultWithTools.
     */
    fun _buildToolUseResult(choice: Any?, response: Any?): Map<String, Any>? {
        if (choice !is Map<*, *>) return null
        val message = choice["message"] as? Map<*, *> ?: return null
        val toolCalls = message["tool_calls"] as? List<*> ?: return null
        return mapOf(
            "role" to "assistant",
            "content" to emptyList<Map<String, Any>>(),
            "tool_calls" to toolCalls)
    }

    /**
     * Build a result map from a normal text LLM response.
     * Returns a map representing CreateMessageResult.
     */
    fun _buildTextResult(choice: Any?, response: Any?): Map<String, Any>? {
        if (choice !is Map<*, *>) return null
        val message = choice["message"] as? Map<*, *> ?: return null
        val text = message["content"] as? String ?: ""
        return mapOf(
            "role" to "assistant",
            "content" to listOf(mapOf("type" to "text", "text" to text)))
    }

    /**
     * Return kwargs for ClientSession with sampling support.
     * On Android, returns null (no MCP SDK).
     */
    fun sessionKwargs(): Map<String, Any>? {
        return null
    }

    /**
     * Check if a server config uses HTTP transport.
     */
    fun _isHttp(): Boolean {
        // Check if any registered server uses HTTP
        return _servers.values.any { !it.url.isNullOrEmpty() }
    }

    /**
     * Build a message_handler callback map for ClientSession.
     * On Android, returns null (no MCP SDK).
     */
    fun _makeMessageHandler(): Map<String, Any>? {
        return null
    }

    /**
     * Re-fetch tools from the server and update the registry.
     * On Android, delegates to discoverTools for each server.
     */
    suspend fun _refreshTools(): List<McpToolDef> {
        val allTools = mutableListOf<McpToolDef>()
        for (serverName in _servers.keys) {
            allTools.addAll(discoverTools(serverName))
        }
        return allTools
    }

    /**
     * Run the server using stdio transport.
     * Not supported on Android (no subprocess management).
     */
    suspend fun _runStdio(config: Any?): Any? {
        Log.w(TAG, "_runStdio: stdio transport not supported on Android")
        return null
    }

    /**
     * Run the server using HTTP/StreamableHTTP transport.
     * On Android, discovers tools via HTTP but does not maintain a long-lived connection.
     */
    suspend fun _runHttp(config: Any?): Any? {
        if (config is Map<*, *>) {
            val serverName = config["server_name"] as? String
            if (serverName != null) {
                discoverTools(serverName)
            }
        }
        return null
    }

    /**
     * Discover tools from all connected sessions/servers.
     */
    suspend fun _discoverTools(): Map<String, McpToolDef> {
        for (serverName in _servers.keys) {
            discoverTools(serverName)
        }
        return getDiscoveredTools()
    }

    /**
     * Long-lived coroutine: connect, discover tools, wait, disconnect.
     * On Android, just runs discover and returns discovered tools.
     */
    suspend fun run(config: Any?): Map<String, McpToolDef> {
        if (config is Map<*, *>) {
            val serverName = config["server_name"] as? String
            if (serverName != null) {
                val serverConfig = config["config"] as? McpServerConfig
                if (serverConfig != null) {
                    registerServer(serverName, serverConfig)
                }
                discoverTools(serverName)
            }
        }
        return getDiscoveredTools()
    }

    /**
     * Create the background Task and wait until ready (or failed).
     * On Android, just returns null (no background event loop).
     */
    suspend fun start(config: Any?): Map<String, McpToolDef>? {
        return run(config) as? Map<String, McpToolDef>
    }

    /**
     * Signal the Task to exit and wait for clean resource teardown.
     * On Android, unregisters all servers.
     */
    suspend fun shutdown(): Boolean {
        val serverNames = _servers.keys.toList()
        for (name in serverNames) {
            unregisterServer(name)
        }
        Log.i(TAG, "Shutdown complete. Unregistered ${serverNames.size} servers.")
        return true
    }

}

/**
 * Handles sampling/createMessage requests for a single MCP server.
 * Ported from SamplingHandler in mcp_tool.py.
 *
 * On Android, MCP sampling is not supported, so this is a structural
 * placeholder preserving the interface for alignment.
 */
class SamplingHandler(
    val serverName: String,
    config: Map<String, Any> = emptyMap()
) {
    val maxRpm: Int = (config["max_rpm"] as? Number)?.toInt() ?: 10
    val timeout: Float = (config["timeout"] as? Number)?.toFloat() ?: 30f
    val maxTokensCap: Int = (config["max_tokens_cap"] as? Number)?.toInt() ?: 4096
    val maxToolRounds: Int = (config["max_tool_rounds"] as? Number)?.toInt() ?: 5
    val modelOverride: String? = config["model"] as? String
    val allowedModels: List<String> = (config["allowed_models"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    companion object {
        val STOP_REASON_MAP = mapOf("stop" to "endTurn", "length" to "maxTokens", "tool_calls" to "toolUse")
    }
}

/**
 * Manages a single MCP server connection in a dedicated asyncio Task.
 * Ported from MCPServerTask in mcp_tool.py.
 *
 * On Android, this is a structural placeholder. The actual MCP connection
 * lifecycle is managed differently without asyncio Tasks.
 */
class MCPServerTask(val name: String) {
    var session: Any? = null
    var toolTimeout: Float = 120f
    private val _tools: MutableList<Any> = mutableListOf()
    private var _error: String? = null

    /**
     * Block until either shutdown or reconnect event fires.
     * On Android, returns "shutdown" immediately (no event loop).
     */
    suspend fun _waitForLifecycleEvent(): String {
        // On Android, there is no long-lived event loop.
        // Returns "shutdown" to indicate the task should exit.
        return "shutdown"
    }
}
