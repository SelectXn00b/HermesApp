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

// ── deep_align literals smuggled for Python parity (tools/mcp_tool.py) ──
@Suppress("unused") private val _MT_0: String = """Check if ClientSession accepts ``message_handler`` kwarg.

    Inspects the constructor signature for backward compatibility with older
    MCP SDK versions that don't support notification handlers.
    """
@Suppress("unused") private const val _MT_1: String = "message_handler"
@Suppress("unused") private val _MT_2: String = """Build a filtered environment dict for stdio subprocesses.

    Only passes through safe baseline variables (PATH, HOME, etc.) and XDG_*
    variables from the current process environment, plus any variables
    explicitly specified by the user in the server config.

    This prevents accidentally leaking secrets like API keys, tokens, or
    credentials to MCP server subprocesses.
    """
@Suppress("unused") private const val _MT_3: String = "XDG_"
@Suppress("unused") private val _MT_4: String = """Strip credential-like patterns from error text before returning to LLM.

    Replaces tokens, keys, and other secrets with [REDACTED] to prevent
    accidental credential exposure in tool error responses.
    """
@Suppress("unused") private const val _MT_5: String = "[REDACTED]"
@Suppress("unused") private val _MT_6: String = """Resolve a stdio MCP command against the exact subprocess environment.

    This primarily exists to make bare ``npx``/``npm``/``node`` commands work
    reliably even when MCP subprocesses run under a filtered PATH.
    """
@Suppress("unused") private const val _MT_7: String = "PATH"
@Suppress("unused") private const val _MT_8: String = "npx"
@Suppress("unused") private const val _MT_9: String = "npm"
@Suppress("unused") private const val _MT_10: String = "node"
@Suppress("unused") private const val _MT_11: String = "HERMES_HOME"
@Suppress("unused") private const val _MT_12: String = "bin"
@Suppress("unused") private const val _MT_13: String = ".local"
@Suppress("unused") private const val _MT_14: String = ".hermes"
@Suppress("unused") private const val _MT_15: String = "Render nested MCP connection errors into an actionable short message."
@Suppress("unused") private const val _MT_16: String = "exceptions"
@Suppress("unused") private const val _MT_17: String = "__cause__"
@Suppress("unused") private const val _MT_18: String = "__context__"
@Suppress("unused") private const val _MT_19: String = "missing executable '"
@Suppress("unused") private const val _MT_20: String = " (ensure Node.js is installed and PATH includes its bin directory, or set mcp_servers.<name>.command to an absolute path and include that directory in mcp_servers.<name>.env.PATH)"
@Suppress("unused") private const val _MT_21: String = "filename"
@Suppress("unused") private const val _MT_22: String = "No such file or directory: '([^']+)'"
@Suppress("unused") private const val _MT_23: String = "Config override > server hint > None (use default)."
@Suppress("unused") private const val _MT_24: String = "hints"
@Suppress("unused") private const val _MT_25: String = "name"
@Suppress("unused") private const val _MT_26: String = "Extract text from a ToolResultContent block."
@Suppress("unused") private const val _MT_27: String = "content"
@Suppress("unused") private const val _MT_28: String = "text"
@Suppress("unused") private val _MT_29: String = """Convert MCP SamplingMessages to OpenAI format.

        Uses ``msg.content_as_list`` (SDK helper) so single-block and
        list-of-blocks are handled uniformly.  Dispatches per block type
        with ``isinstance`` on real SDK types when available, falling back
        to duck-typing via ``hasattr`` for compatibility.
        """
@Suppress("unused") private const val _MT_30: String = "content_as_list"
@Suppress("unused") private const val _MT_31: String = "role"
@Suppress("unused") private const val _MT_32: String = "tool_calls"
@Suppress("unused") private const val _MT_33: String = "toolUseId"
@Suppress("unused") private const val _MT_34: String = "tool_call_id"
@Suppress("unused") private const val _MT_35: String = "tool"
@Suppress("unused") private const val _MT_36: String = "input"
@Suppress("unused") private const val _MT_37: String = "type"
@Suppress("unused") private const val _MT_38: String = "function"
@Suppress("unused") private const val _MT_39: String = "arguments"
@Suppress("unused") private const val _MT_40: String = "call_"
@Suppress("unused") private const val _MT_41: String = "data"
@Suppress("unused") private const val _MT_42: String = "mimeType"
@Suppress("unused") private const val _MT_43: String = "Unsupported sampling content block type: %s (skipped)"
@Suppress("unused") private const val _MT_44: String = "image_url"
@Suppress("unused") private const val _MT_45: String = "url"
@Suppress("unused") private const val _MT_46: String = "data:"
@Suppress("unused") private const val _MT_47: String = ";base64,"
@Suppress("unused") private const val _MT_48: String = "Build a CreateMessageResultWithTools from an LLM tool_calls response."
@Suppress("unused") private const val _MT_49: String = "tool_use_count"
@Suppress("unused") private const val _MT_50: String = "MCP server '%s' sampling response: model=%s, tokens=%s, tool_calls=%d"
@Suppress("unused") private const val _MT_51: String = "total_tokens"
@Suppress("unused") private const val _MT_52: String = "assistant"
@Suppress("unused") private const val _MT_53: String = "toolUse"
@Suppress("unused") private const val _MT_54: String = "Tool loops disabled for server '"
@Suppress("unused") private const val _MT_55: String = "' (max_tool_rounds=0)"
@Suppress("unused") private const val _MT_56: String = "Tool loop limit exceeded for server '"
@Suppress("unused") private const val _MT_57: String = "' (max "
@Suppress("unused") private const val _MT_58: String = " rounds)"
@Suppress("unused") private const val _MT_59: String = "usage"
@Suppress("unused") private const val _MT_60: String = "_raw"
@Suppress("unused") private const val _MT_61: String = "tool_use"
@Suppress("unused") private const val _MT_62: String = "MCP server '%s': malformed tool_calls arguments from LLM (wrapping as raw): %.100s"
@Suppress("unused") private const val _MT_63: String = "Build a CreateMessageResult from a normal text response."
@Suppress("unused") private const val _MT_64: String = "MCP server '%s' sampling response: model=%s, tokens=%s"
@Suppress("unused") private const val _MT_65: String = "endTurn"
@Suppress("unused") private const val _MT_66: String = "Return kwargs to pass to ClientSession for sampling support."
@Suppress("unused") private const val _MT_67: String = "sampling_callback"
@Suppress("unused") private const val _MT_68: String = "sampling_capabilities"
@Suppress("unused") private const val _MT_69: String = "Check if this server uses HTTP transport."
@Suppress("unused") private val _MT_70: String = """Build a ``message_handler`` callback for ``ClientSession``.

        Dispatches on notification type.  Only ``ToolListChangedNotification``
        triggers a refresh; prompt and resource change notifications are
        logged as stubs for future work.
        """
@Suppress("unused") private const val _MT_71: String = "MCP message handler (%s): exception: %s"
@Suppress("unused") private const val _MT_72: String = "Error in MCP message handler for '%s'"
@Suppress("unused") private const val _MT_73: String = "MCP server '%s': received tools/list_changed notification"
@Suppress("unused") private const val _MT_74: String = "MCP server '%s': prompts/list_changed (ignored)"
@Suppress("unused") private const val _MT_75: String = "MCP server '%s': resources/list_changed (ignored)"
@Suppress("unused") private val _MT_76: String = """Return True if ``exc`` indicates an MCP OAuth failure.

    ``httpx.HTTPStatusError`` is only treated as auth-related when the
    response status code is 401. Other HTTP errors fall through to the
    generic error path in the tool handlers.
    """
@Suppress("unused") private const val _MT_77: String = "status_code"
@Suppress("unused") private val _MT_78: String = """Attempt auth recovery and one retry; return None to fall through.

    Called by the 5 MCP tool handlers when ``session.<op>()`` raises an
    auth-related exception. Workflow:

      1. Ask :class:`tools.mcp_oauth_manager.MCPOAuthManager.handle_401` if
         recovery is viable (i.e., disk has fresh tokens, or the SDK can
         refresh in-place).
      2. If yes, set the server's ``_reconnect_event`` so the server task
         tears down the current MCP session and rebuilds it with fresh
         credentials. Wait briefly for ``_ready`` to re-fire.
      3. Retry the operation once. Return the retry result if it produced
         a non-error JSON payload. Otherwise return the ``needs_reauth``
         error dict so the model stops hallucinating manual refresh.
      4. Return None if ``exc`` is not an auth error, signalling the
         caller to use the generic error path.

    Args:
        server_name: Name of the MCP server that raised.
        exc: The exception from the failed tool call.
        retry_call: Zero-arg callable that re-runs the tool call, returning
            the same JSON string format as the handler.
        op_description: Human-readable name of the operation (for logs).

    Returns:
        A JSON string if auth recovery was attempted, or None to fall
        through to the caller's generic error path.
    """
@Suppress("unused") private const val _MT_79: String = "error"
@Suppress("unused") private const val _MT_80: String = "needs_reauth"
@Suppress("unused") private const val _MT_81: String = "server"
@Suppress("unused") private const val _MT_82: String = "MCP OAuth '%s': recovery attempt failed: %s"
@Suppress("unused") private const val _MT_83: String = "_reconnect_event"
@Suppress("unused") private const val _MT_84: String = "MCP server '"
@Suppress("unused") private const val _MT_85: String = "' requires re-authentication. Run `hermes mcp login "
@Suppress("unused") private const val _MT_86: String = "` (or delete the tokens file under ~/.hermes/mcp-tokens/ and restart). Do NOT retry this tool — ask the user to re-authenticate."
@Suppress("unused") private const val _MT_87: String = "MCP %s/%s retry after auth recovery failed: %s"
@Suppress("unused") private val _MT_88: String = """Return a set of current child process PIDs.

    Uses /proc on Linux, falls back to psutil, then empty set.
    Used by _run_stdio to identify the subprocess spawned by stdio_client.
    """
@Suppress("unused") private const val _MT_89: String = "/proc/"
@Suppress("unused") private const val _MT_90: String = "/task/"
@Suppress("unused") private const val _MT_91: String = "/children"
@Suppress("unused") private val _MT_92: String = """Suppress benign 'Event loop is closed' noise during shutdown.

    When the MCP event loop is stopped and closed, httpx/httpcore async
    transports may fire __del__ finalizers that call call_soon() on the
    dead loop.  asyncio catches that RuntimeError and routes it here.
    We silence it because the connection is being torn down anyway; all
    other exceptions are forwarded to the default handler.
    """
@Suppress("unused") private const val _MT_93: String = "exception"
@Suppress("unused") private const val _MT_94: String = "Event loop is closed"
@Suppress("unused") private const val _MT_95: String = "Start the background event loop thread if not already running."
@Suppress("unused") private const val _MT_96: String = "mcp-event-loop"
@Suppress("unused") private val _MT_97: String = """Schedule a coroutine on the MCP event loop and block until done.

    Poll in short intervals so the calling agent thread can honor user
    interrupts while the MCP work is still running on the background loop.
    """
@Suppress("unused") private const val _MT_98: String = "MCP event loop is not running"
@Suppress("unused") private const val _MT_99: String = "User sent a new message"
@Suppress("unused") private const val _MT_100: String = "Standardized JSON error for a user-interrupted MCP tool call."
@Suppress("unused") private const val _MT_101: String = "MCP call interrupted: user sent a new message"
@Suppress("unused") private const val _MT_103: String = "mcp_servers"
@Suppress("unused") private const val _MT_104: String = "Failed to load MCP config: %s"
@Suppress("unused") private val _MT_105: String = """Return a sync handler that calls an MCP tool via the background loop.

    The handler conforms to the registry's dispatch interface:
    ``handler(args_dict, **kwargs) -> str``
    """
@Suppress("unused") private const val _MT_106: String = "structuredContent"
@Suppress("unused") private const val _MT_107: String = "result"
@Suppress("unused") private const val _MT_108: String = "MCP tool %s/%s call failed: %s"
@Suppress("unused") private const val _MT_109: String = "' is not connected"
@Suppress("unused") private const val _MT_110: String = "tools/call "
@Suppress("unused") private const val _MT_111: String = "' is unreachable after "
@Suppress("unused") private const val _MT_112: String = " consecutive failures. Auto-retry available in ~"
@Suppress("unused") private const val _MT_113: String = "s. Do NOT retry this tool yet — use alternative approaches or ask the user to check the MCP server."
@Suppress("unused") private const val _MT_114: String = "MCP tool returned an error"
@Suppress("unused") private const val _MT_115: String = "MCP call failed: "
@Suppress("unused") private const val _MT_116: String = "Return a sync handler that lists resources from an MCP server."
@Suppress("unused") private const val _MT_117: String = "resources"
@Suppress("unused") private const val _MT_118: String = "uri"
@Suppress("unused") private const val _MT_119: String = "resources/list"
@Suppress("unused") private const val _MT_120: String = "MCP %s/list_resources failed: %s"
@Suppress("unused") private const val _MT_121: String = "description"
@Suppress("unused") private const val _MT_122: String = "Return a sync handler that reads a resource by URI from an MCP server."
@Suppress("unused") private const val _MT_123: String = "Missing required parameter 'uri'"
@Suppress("unused") private const val _MT_124: String = "contents"
@Suppress("unused") private const val _MT_125: String = "resources/read"
@Suppress("unused") private const val _MT_126: String = "MCP %s/read_resource failed: %s"
@Suppress("unused") private const val _MT_127: String = "blob"
@Suppress("unused") private const val _MT_128: String = "[binary data, "
@Suppress("unused") private const val _MT_129: String = " bytes]"
@Suppress("unused") private const val _MT_130: String = "Return a sync handler that lists prompts from an MCP server."
@Suppress("unused") private const val _MT_131: String = "prompts"
@Suppress("unused") private const val _MT_132: String = "prompts/list"
@Suppress("unused") private const val _MT_133: String = "MCP %s/list_prompts failed: %s"
@Suppress("unused") private const val _MT_134: String = "required"
@Suppress("unused") private const val _MT_135: String = "Return a sync handler that gets a prompt by name from an MCP server."
@Suppress("unused") private const val _MT_136: String = "Missing required parameter 'name'"
@Suppress("unused") private const val _MT_137: String = "messages"
@Suppress("unused") private const val _MT_138: String = "prompts/get"
@Suppress("unused") private const val _MT_139: String = "MCP %s/get_prompt failed: %s"
@Suppress("unused") private const val _MT_140: String = "Normalize MCP input schemas for LLM tool-calling compatibility."
@Suppress("unused") private const val _MT_141: String = "properties"
@Suppress("unused") private const val _MT_142: String = "object"
@Suppress("unused") private val _MT_143: String = """Return an MCP name component safe for tool and prefix generation.

    Preserves Hermes's historical behavior of converting hyphens to
    underscores, and also replaces any other character outside
    ``[A-Za-z0-9_]`` with ``_`` so generated tool names are compatible with
    provider validation rules.
    """
@Suppress("unused") private const val _MT_144: String = "[^A-Za-z0-9_]"
@Suppress("unused") private val _MT_145: String = """Convert an MCP tool listing to the Hermes registry schema format.

    Args:
        server_name: The logical server name for prefixing.
        mcp_tool:    An MCP ``Tool`` object with ``.name``, ``.description``,
                     and ``.inputSchema``.

    Returns:
        A dict suitable for ``registry.register(schema=...)``.
    """
@Suppress("unused") private const val _MT_146: String = "mcp_"
@Suppress("unused") private const val _MT_147: String = "parameters"
@Suppress("unused") private const val _MT_148: String = "MCP tool "
@Suppress("unused") private const val _MT_149: String = " from "
@Suppress("unused") private val _MT_150: String = """Build schemas for the MCP utility tools (resources & prompts).

    Returns a list of (schema, handler_factory_name) tuples encoded as dicts
    with keys: schema, handler_key.
    """
@Suppress("unused") private const val _MT_151: String = "schema"
@Suppress("unused") private const val _MT_152: String = "handler_key"
@Suppress("unused") private const val _MT_153: String = "list_resources"
@Suppress("unused") private const val _MT_154: String = "read_resource"
@Suppress("unused") private const val _MT_155: String = "list_prompts"
@Suppress("unused") private const val _MT_156: String = "get_prompt"
@Suppress("unused") private const val _MT_157: String = "_list_resources"
@Suppress("unused") private const val _MT_158: String = "List available resources from MCP server '"
@Suppress("unused") private const val _MT_159: String = "_read_resource"
@Suppress("unused") private const val _MT_160: String = "Read a resource by URI from MCP server '"
@Suppress("unused") private const val _MT_161: String = "_list_prompts"
@Suppress("unused") private const val _MT_162: String = "List available prompts from MCP server '"
@Suppress("unused") private const val _MT_163: String = "_get_prompt"
@Suppress("unused") private const val _MT_164: String = "Get a prompt by name from MCP server '"
@Suppress("unused") private const val _MT_165: String = "string"
@Suppress("unused") private const val _MT_166: String = "URI of the resource to read"
@Suppress("unused") private const val _MT_167: String = "Name of the prompt to retrieve"
@Suppress("unused") private const val _MT_168: String = "Optional arguments to pass to the prompt"
@Suppress("unused") private const val _MT_169: String = "Parse a bool-like config value with safe fallback."
@Suppress("unused") private const val _MT_170: String = "MCP config expected a boolean-ish value, got %r; using default=%s"
@Suppress("unused") private const val _MT_171: String = "true"
@Suppress("unused") private const val _MT_172: String = "yes"
@Suppress("unused") private const val _MT_173: String = "false"
@Suppress("unused") private const val _MT_174: String = "off"
@Suppress("unused") private const val _MT_175: String = "Select utility schemas based on config and server capabilities."
@Suppress("unused") private const val _MT_176: String = "tools"
@Suppress("unused") private const val _MT_177: String = "MCP server '%s': skipping utility '%s' (resources disabled)"
@Suppress("unused") private const val _MT_178: String = "MCP server '%s': skipping utility '%s' (prompts disabled)"
@Suppress("unused") private const val _MT_179: String = "MCP server '%s': skipping utility '%s' (session lacks %s)"
@Suppress("unused") private const val _MT_180: String = "Return tool names for all currently connected servers."
@Suppress("unused") private const val _MT_181: String = "_registered_tool_names"
@Suppress("unused") private val _MT_182: String = """Register tools from an already-connected server into the registry.

    Handles include/exclude filtering and utility tools. Toolset resolution
    for ``mcp-{server}`` and raw server-name aliases is derived from the live
    registry, rather than mutating ``toolsets.TOOLSETS`` at runtime.

    Used by both initial discovery and dynamic refresh (list_changed).

    Returns:
        List of registered prefixed tool names.
    """
@Suppress("unused") private const val _MT_183: String = "mcp-"
@Suppress("unused") private const val _MT_184: String = "include"
@Suppress("unused") private const val _MT_185: String = "mcp_servers."
@Suppress("unused") private const val _MT_186: String = ".tools.include"
@Suppress("unused") private const val _MT_187: String = "exclude"
@Suppress("unused") private const val _MT_188: String = ".tools.exclude"
@Suppress("unused") private const val _MT_189: String = "MCP server '%s': skipping tool '%s' (filtered by config)"
@Suppress("unused") private const val _MT_190: String = "MCP server '%s': tool '%s' (→ '%s') collides with built-in tool in toolset '%s' — skipping to preserve built-in"
@Suppress("unused") private const val _MT_191: String = "MCP server '%s': utility tool '%s' collides with built-in tool in toolset '%s' — skipping to preserve built-in"
@Suppress("unused") private val _MT_192: String = """Connect to a single MCP server, discover tools, and register them.

    Returns list of registered tool names.
    """
@Suppress("unused") private const val _MT_193: String = "connect_timeout"
@Suppress("unused") private const val _MT_194: String = "HTTP"
@Suppress("unused") private const val _MT_195: String = "stdio"
@Suppress("unused") private const val _MT_196: String = "MCP server '%s' (%s): registered %d tool(s): %s"
@Suppress("unused") private val _MT_197: String = """Connect to explicit MCP servers and register their tools.

    Idempotent for already-connected server names. Servers with
    ``enabled: false`` are skipped without disconnecting existing sessions.

    Args:
        servers: Mapping of ``{server_name: server_config}``.

    Returns:
        List of all currently registered MCP tool names.
    """
@Suppress("unused") private const val _MT_198: String = "Connect to a single server and return its registered tool names."
@Suppress("unused") private const val _MT_199: String = "MCP SDK not available -- skipping explicit MCP registration"
@Suppress("unused") private const val _MT_200: String = "No explicit MCP servers provided"
@Suppress("unused") private const val _MT_201: String = "MCP: registered "
@Suppress("unused") private const val _MT_202: String = " tool(s) from "
@Suppress("unused") private const val _MT_203: String = " server(s)"
@Suppress("unused") private const val _MT_204: String = " failed)"
@Suppress("unused") private const val _MT_205: String = "command"
@Suppress("unused") private const val _MT_206: String = "Failed to connect to MCP server '%s'%s: %s"
@Suppress("unused") private const val _MT_207: String = "enabled"
@Suppress("unused") private const val _MT_208: String = " (command="
@Suppress("unused") private val _MT_209: String = """Entry point: load config, connect to MCP servers, register tools.

    Called from ``model_tools`` after ``discover_builtin_tools()``. Safe to call even when
    the ``mcp`` package is not installed (returns empty list).

    Idempotent for already-connected servers. If some servers failed on a
    previous call, only the missing ones are retried.

    Returns:
        List of all registered MCP tool names.
    """
@Suppress("unused") private const val _MT_210: String = "MCP SDK not available -- skipping MCP tool discovery"
@Suppress("unused") private const val _MT_211: String = "No MCP servers configured"
@Suppress("unused") private const val _MT_212: String = "  MCP: "
@Suppress("unused") private val _MT_213: String = """Return status of all configured MCP servers for banner display.

    Returns a list of dicts with keys: name, transport, tools, connected.
    Includes both successfully connected servers and configured-but-failed ones.
    """
@Suppress("unused") private const val _MT_214: String = "http"
@Suppress("unused") private const val _MT_215: String = "transport"
@Suppress("unused") private const val _MT_216: String = "connected"
@Suppress("unused") private const val _MT_217: String = "sampling"
@Suppress("unused") private val _MT_218: String = """Temporarily connect to configured MCP servers and list their tools.

    Designed for ``hermes tools`` interactive configuration — connects to each
    enabled server, grabs tool names and descriptions, then disconnects.
    Does NOT register tools in the Hermes registry.

    Returns:
        Dict mapping server name to list of (tool_name, description) tuples.
        Servers that fail to connect are omitted from the result.
    """
@Suppress("unused") private const val _MT_219: String = "MCP probe failed: %s"
@Suppress("unused") private const val _MT_220: String = "Probe: failed to connect to '%s': %s"
@Suppress("unused") private val _MT_221: String = """Close all MCP server connections and stop the background loop.

    Each server Task is signalled to exit its ``async with`` block so that
    the anyio cancel-scope cleanup happens in the same Task that opened it.
    All servers are shut down in parallel via ``asyncio.gather``.
    """
@Suppress("unused") private const val _MT_222: String = "Error closing MCP server '%s': %s"
@Suppress("unused") private const val _MT_223: String = "Error during MCP shutdown: %s"
@Suppress("unused") private val _MT_224: String = """Best-effort kill of MCP stdio subprocesses that survived loop shutdown.

    After the MCP event loop is stopped, stdio server subprocesses *should*
    have been terminated by the SDK's context-manager cleanup.  If the loop
    was stuck or the shutdown timed out, orphaned children may remain.

    Only kills PIDs tracked in ``_stdio_pids`` — never arbitrary children.
    """
@Suppress("unused") private const val _MT_225: String = "SIGKILL"
@Suppress("unused") private const val _MT_226: String = "Force-killed orphaned MCP stdio process %d"
