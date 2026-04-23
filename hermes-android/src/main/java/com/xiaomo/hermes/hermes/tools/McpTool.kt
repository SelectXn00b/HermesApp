/**
 * MCP (Model Context Protocol) Client Support
 *
 * 1:1 对齐 — connects to external MCP servers via stdio or
 * HTTP/StreamableHTTP transport, discovers their tools, and registers
 * them into the hermes-agent tool registry so the agent can call them
 * like any built-in tool.
 *
 * Ported from tools/mcp_tool.py (Python 原始) — Python relies on the
 * optional `mcp` SDK and a dedicated asyncio background loop. Android
 * has neither, so the port is a structural skeleton: all top-level
 * names present, bodies deferred with TODO comments.
 */
package com.xiaomo.hermes.hermes.tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val _TAG = "mcp_tool"

// ---------------------------------------------------------------------------
// Graceful import — MCP SDK is an optional dependency
// ---------------------------------------------------------------------------

private const val _MCP_AVAILABLE = false
private const val _MCP_HTTP_AVAILABLE = false
private const val _MCP_SAMPLING_TYPES = false
private const val _MCP_NOTIFICATION_TYPES = false
private const val _MCP_MESSAGE_HANDLER_SUPPORTED = false

// ---------------------------------------------------------------------------
// Module-level helpers
// ---------------------------------------------------------------------------

fun _checkMessageHandlerSupport(): Boolean = false

@Suppress("UNUSED_PARAMETER")
fun _buildSafeEnv(userEnv: Map<String, String>?): Map<String, String> = emptyMap<String, String>()

@Suppress("UNUSED_PARAMETER")
fun _sanitizeError(text: String): String {
    // TODO: port credential-stripping regex passes.
    return text
}

@Suppress("UNUSED_PARAMETER")
fun _scanMcpDescription(serverName: String, toolName: String, description: String): List<String> = emptyList<String>()

@Suppress("UNUSED_PARAMETER")
fun _prependPath(env: MutableMap<String, String>, directory: String): MutableMap<String, String> {
    // TODO: port PATH prepend.
    return env
}

@Suppress("UNUSED_PARAMETER")
fun _resolveStdioCommand(command: String, env: MutableMap<String, String>): Pair<String, MutableMap<String, String>> {
    // TODO: port shutil.which + npx/uvx resolver.
    return command to env
}

@Suppress("UNUSED_PARAMETER")
fun _formatConnectError(exc: Throwable): String {
    // TODO: port exception-chain walker + cause detection.
    return exc.message ?: exc.toString()
}

@Suppress("UNUSED_PARAMETER")
fun _safeNumeric(value: Any?, default: Number, coerce: (Any?) -> Number = { (it as? Number) ?: default }, minimum: Int = 1): Number {
    // TODO: port numeric coercion with bounds check.
    return default
}

// ---------------------------------------------------------------------------
// SamplingHandler — server-initiated LLM requests
// ---------------------------------------------------------------------------

class SamplingHandler(val serverName: String, val config: Map<String, Any?>) {

    @Suppress("UNUSED_PARAMETER")
    fun _checkRateLimit(): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun _resolveModel(preferences: Any?): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun _extractToolResultText(block: Any?): String = ""

    @Suppress("UNUSED_PARAMETER")
    fun _convertMessages(params: Any?): List<Map<String, Any?>> = emptyList<Map<String, Any?>>()

    fun _error(message: String, code: Int = -1): Map<String, Any?> {
        // TODO: port ErrorData envelope.
        return mapOf("error" to mapOf("message" to message, "code" to code))
    }

    @Suppress("UNUSED_PARAMETER")
    fun _buildToolUseResult(choice: Any?, response: Any?): Map<String, Any?> = emptyMap<String, Any?>()

    @Suppress("UNUSED_PARAMETER")
    fun _buildTextResult(choice: Any?, response: Any?): Map<String, Any?> = emptyMap<String, Any?>()

    fun sessionKwargs(): Map<String, Any?> = emptyMap<String, Any?>()

    @Suppress("UNUSED_PARAMETER")
    suspend operator fun invoke(context: Any?, params: Any?): Map<String, Any?> = emptyMap<String, Any?>()
}

// ---------------------------------------------------------------------------
// MCPServerTask — long-lived asyncio Task wrapping one server
// ---------------------------------------------------------------------------

class MCPServerTask(val name: String) {
    var session: Any? = null
    var connected: Boolean = false
    var lastError: String? = null
    var config: Map<String, Any?> = emptyMap()
    var tools: List<Map<String, Any?>> = emptyList()

    fun _isHttp(): Boolean = false

    fun _makeMessageHandler(): Any? = null

    // TODO: port the full ~500 lines of connection/reconnect loop,
    // list_tools refresh, call_tool wrapper, resource/prompt proxying,
    // and anyio cleanup protocol.
}

// ---------------------------------------------------------------------------
// Server-error tracking + auth retry
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
fun _bumpServerError(serverName: String): Unit = Unit

@Suppress("UNUSED_PARAMETER")
fun _resetServerError(serverName: String): Unit = Unit

fun _getAuthErrorTypes(): List<Class<*>> = emptyList<Class<*>>()

@Suppress("UNUSED_PARAMETER")
fun _isAuthError(exc: Throwable): Boolean = false

@Suppress("UNUSED_PARAMETER")
suspend fun _handleAuthErrorAndRetry(
    serverName: String,
    config: Map<String, Any?>,
    exc: Throwable,
    originalCall: suspend () -> Any?): Any? {
    // TODO: port OAuth refresh + single retry.
    return null
}

// ---------------------------------------------------------------------------
// Child-PID tracking for orphan cleanup
// ---------------------------------------------------------------------------

fun _snapshotChildPids(): Set<Int> = emptySet<Int>()

@Suppress("UNUSED_PARAMETER")
fun _mcpLoopExceptionHandler(loop: Any?, context: Map<String, Any?>): Unit = Unit

fun _ensureMcpLoop(): Unit = Unit

@Suppress("UNUSED_PARAMETER")
fun <T> _runOnMcpLoop(coro: suspend () -> T, timeout: Double = 30.0): T? {
    // TODO: port run_coroutine_threadsafe bridge.
    return null
}

fun _interruptedCallResult(): String {
    // TODO: port cancellation sentinel.
    return JSONObject(mapOf("error" to "MCP call interrupted")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _interpolateEnvVars(value: Any?): Any? {
    // TODO: port ${ENV_VAR} expansion inside config values.
    return value
}

fun _loadMcpConfig(): Map<String, Map<String, Any?>> = emptyMap<String, Map<String, Any?>>()

// ---------------------------------------------------------------------------
// Connection + tool handler factories
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
suspend fun _connectServer(name: String, config: Map<String, Any?>): MCPServerTask {
    // TODO: port transport selection + handshake + capability negotiation.
    return MCPServerTask(name)
}

@Suppress("UNUSED_PARAMETER")
fun _makeToolHandler(
    serverName: String,
    toolName: String,
    toolTimeout: Double): (Map<String, Any?>) -> String {
    return { _ ->
        // TODO: port tool call proxy through _run_on_mcp_loop.
        JSONObject(mapOf("error" to "mcp tool not ported")).toString()
    }
}

@Suppress("UNUSED_PARAMETER")
fun _makeListResourcesHandler(serverName: String, toolTimeout: Double): (Map<String, Any?>) -> String {
    return { _ ->
        JSONObject(mapOf("error" to "mcp list_resources not ported")).toString()
    }
}

@Suppress("UNUSED_PARAMETER")
fun _makeReadResourceHandler(serverName: String, toolTimeout: Double): (Map<String, Any?>) -> String {
    return { _ ->
        JSONObject(mapOf("error" to "mcp read_resource not ported")).toString()
    }
}

@Suppress("UNUSED_PARAMETER")
fun _makeListPromptsHandler(serverName: String, toolTimeout: Double): (Map<String, Any?>) -> String {
    return { _ ->
        JSONObject(mapOf("error" to "mcp list_prompts not ported")).toString()
    }
}

@Suppress("UNUSED_PARAMETER")
fun _makeGetPromptHandler(serverName: String, toolTimeout: Double): (Map<String, Any?>) -> String {
    return { _ ->
        JSONObject(mapOf("error" to "mcp get_prompt not ported")).toString()
    }
}

@Suppress("UNUSED_PARAMETER")
fun _makeCheckFn(serverName: String): () -> Boolean = { false }

// ---------------------------------------------------------------------------
// Schema normalization
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
fun _normalizeMcpInputSchema(schema: Map<String, Any?>?): Map<String, Any?> {
    // TODO: port type/required normalization.
    return schema ?: emptyMap()
}

/** Sanitize a component of an MCP-generated tool name. */
fun sanitizeMcpNameComponent(value: String): String {
    // TODO: port regex replace for invalid chars.
    return Regex("[^A-Za-z0-9_\\-]").replace(value, "_")
}

@Suppress("UNUSED_PARAMETER")
fun _convertMcpSchema(serverName: String, mcpTool: Any?): Map<String, Any?> = emptyMap<String, Any?>()

@Suppress("UNUSED_PARAMETER")
fun _buildUtilitySchemas(serverName: String): List<Map<String, Any?>> = emptyList<Map<String, Any?>>()

@Suppress("UNUSED_PARAMETER")
fun _normalizeNameFilter(value: Any?, label: String): Set<String> = emptySet<String>()

@Suppress("UNUSED_PARAMETER")
fun _parseBoolish(value: Any?, default: Boolean = true): Boolean {
    return when (value) {
        is Boolean -> value
        is String -> value.trim().lowercase() in setOf("true", "1", "yes", "on")
        is Number -> value.toInt() != 0
        null -> default
        else -> default
    }
}

@Suppress("UNUSED_PARAMETER")
fun _selectUtilitySchemas(
    serverName: String,
    server: MCPServerTask,
    config: Map<String, Any?>): List<Map<String, Any?>> = emptyList<Map<String, Any?>>()

fun _existingToolNames(): List<String> = emptyList<String>()

@Suppress("UNUSED_PARAMETER")
fun _registerServerTools(
    name: String,
    server: MCPServerTask,
    config: Map<String, Any?>): List<String> = emptyList<String>()

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
suspend fun _discoverAndRegisterServer(name: String, config: Map<String, Any?>): List<String> = emptyList<String>()

/** Register a dict of MCP servers from ACP-provided config. */
@Suppress("UNUSED_PARAMETER")
fun registerMcpServers(servers: Map<String, Map<String, Any?>>): List<String> {
    // TODO: port thread-safe registration across the background loop.
    if (!_MCP_AVAILABLE) {
        Log.d(_TAG, "MCP package not available — skipping server registration")
        return emptyList()
    }
    return emptyList()
}

/** Discover and register MCP tools from the user's config. */
fun discoverMcpTools(): List<String> = emptyList<String>()

/** Return runtime status for each registered MCP server. */
fun getMcpStatus(): List<Map<String, Any?>> = emptyList<Map<String, Any?>>()

/** Probe each server for its tool list (diagnostic). */
fun probeMcpServerTools(): Map<String, List<Pair<String, String>>> = emptyMap<String, List<Pair<String, String>>>()

/** Graceful shutdown hook — stop all server tasks and the background loop. */
fun shutdownMcpServers(): Unit = Unit

fun _killOrphanedMcpChildren(): Unit = Unit

fun _stopMcpLoop(): Unit = Unit
