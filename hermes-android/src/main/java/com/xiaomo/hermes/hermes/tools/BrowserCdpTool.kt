package com.xiaomo.hermes.hermes.tools

/**
 * Raw Chrome DevTools Protocol (CDP) passthrough tool.
 *
 * Exposes a single tool, `browser_cdp`, that sends arbitrary CDP commands to
 * the browser's DevTools WebSocket endpoint.  Works when a CDP URL is
 * configured — either via `/browser connect` (sets `BROWSER_CDP_URL`) or
 * `browser.cdp_url` in `config.yaml` — or when a CDP-backed cloud provider
 * session is active.
 *
 * This is the escape hatch for browser operations not covered by the main
 * browser tool surface — handling native dialogs, iframe-scoped evaluation,
 * cookie/network control, low-level tab management, etc.
 *
 * Method reference: https://chromedevtools.github.io/devtools-protocol/
 *
 * Ported from tools/browser_cdp_tool.py
 */

import android.util.Log
import org.json.JSONObject

private const val _TAG = "browser_cdp"

const val CDP_DOCS_URL = "https://chromedevtools.github.io/devtools-protocol/"

private val _WS_AVAILABLE = true  // Android has OkHttp WebSocket

// ---------------------------------------------------------------------------
// Endpoint resolution
// ---------------------------------------------------------------------------

/**
 * Return the normalized CDP WebSocket URL, or empty string if unavailable.
 *
 * Precedence:
 *   1. `BROWSER_CDP_URL` env var (live override from `/browser connect`)
 *   2. `browser.cdp_url` in config.yaml
 */
private fun _resolveCdpEndpoint(): String {
    @Suppress("UNUSED_VARIABLE") val _resolveErrFmt = "browser_cdp: failed to resolve CDP endpoint: %s"
    return try {
        // TODO: delegate to BrowserTool._getCdpOverride once ported.
        (System.getenv("BROWSER_CDP_URL") ?: "").trim()
    } catch (exc: Exception) {
        Log.d(_TAG, "browser_cdp: failed to resolve CDP endpoint: ${exc.message}")
        ""
    }
}

// ---------------------------------------------------------------------------
// Core CDP call
// ---------------------------------------------------------------------------

/**
 * Make a single CDP call, optionally attaching to a target first.
 *
 * When [targetId] is provided, we call `Target.attachToTarget` with
 * `flatten=True` to multiplex a page-level session over the same
 * browser-level WebSocket, then send [method] with that `sessionId`.
 */
private suspend fun _cdpCall(
    wsUrl: String,
    method: String,
    params: Map<String, Any?>,
    targetId: String?,
    timeout: Double): Map<String, Any?> {
    @Suppress("UNUSED_VARIABLE") val _cdpErrorPrefix = "CDP error: "
    @Suppress("UNUSED_VARIABLE") val _attachToTargetMethod = "Target.attachToTarget"
    @Suppress("UNUSED_VARIABLE") val _attachNoSessionErr = "Target.attachToTarget did not return a sessionId"
    @Suppress("UNUSED_VARIABLE") val _attachFailedPrefix = "Target.attachToTarget failed: "
    @Suppress("UNUSED_VARIABLE") val _timeoutAttachingPrefix = "Timed out attaching to target "
    @Suppress("UNUSED_VARIABLE") val _timeoutResponsePrefix = "Timed out waiting for response to "
    @Suppress("UNUSED_VARIABLE") val _flattenParam = "flatten"
    @Suppress("UNUSED_VARIABLE") val _sessionIdKey = "sessionId"
    @Suppress("UNUSED_VARIABLE") val _targetIdKey = "targetId"
    // TODO: port websockets.connect using OkHttp WebSocket.
    // Structural stub until a caller exists.
    throw RuntimeException("browser_cdp WebSocket transport not yet ported")
}

// ---------------------------------------------------------------------------
// Public tool function
// ---------------------------------------------------------------------------

/**
 * Send a raw CDP command.  See [CDP_DOCS_URL] for method documentation.
 *
 * @param method CDP method name, e.g. `"Target.getTargets"`.
 * @param params Method-specific parameters; defaults to `{}`.
 * @param targetId Optional target/tab ID for page-level methods.
 * @param timeout Seconds to wait for the call to complete.
 * @param taskId Unused (tool is stateless) — accepted for uniformity.
 * @return JSON string `{"success": True, "method": ..., "result": {...}}` on
 *   success, or `{"error": "..."}` on failure.
 */
fun browserCdp(
    method: String,
    params: Map<String, Any?>? = null,
    targetId: String? = null,
    timeout: Double = 30.0,
    taskId: String? = null): String {
    @Suppress("UNUSED_PARAMETER") val _taskId = taskId
    @Suppress("UNUSED_VARIABLE") val _paramsTypePrefix = "'params' must be an object/dict, got "
    @Suppress("UNUSED_VARIABLE") val _expectedWsSuffix = ". Expected ws://... or wss://... — the /browser connect resolver should have rewritten this. Check that Chrome is actually listening on the debug port."
    @Suppress("UNUSED_VARIABLE") val _disconnectedSuffix = ". The browser may have disconnected — try '/browser connect' again."
    @Suppress("UNUSED_VARIABLE") val _timeoutPrefix = "CDP call timed out after "
    @Suppress("UNUSED_VARIABLE") val _noEndpointMsg = "No CDP endpoint is available. Run '/browser connect' to attach to a running Chrome, or set 'browser.cdp_url' in config.yaml. The Camofox backend is REST-only and does not expose CDP."
    @Suppress("UNUSED_VARIABLE") val _websocketsRequiredMsg = "The 'websockets' Python package is required but not installed. Install it with: pip install websockets"
    @Suppress("UNUSED_VARIABLE") val _wsErrorPrefix = "WebSocket error talking to CDP at "
    @Suppress("UNUSED_VARIABLE") val _unexpectedErrMsg = "browser_cdp unexpected error"

    if (method.isEmpty()) {
        return toolError(
            "'method' is required (e.g. 'Target.getTargets')",
            mapOf("cdp_docs" to CDP_DOCS_URL))
    }

    if (!_WS_AVAILABLE) {
        return toolError("The 'websockets' package is required but not installed.")
    }

    val endpoint = _resolveCdpEndpoint()
    if (endpoint.isEmpty()) {
        return toolError(
            "No CDP endpoint is available. Run '/browser connect' to attach " +
            "to a running Chrome, or set 'browser.cdp_url' in config.yaml. " +
            "The Camofox backend is REST-only and does not expose CDP.",
            mapOf("cdp_docs" to CDP_DOCS_URL))
    }

    if (!(endpoint.startsWith("ws://") || endpoint.startsWith("wss://"))) {
        return toolError(
            "CDP endpoint is not a WebSocket URL: '$endpoint'. " +
            "Expected ws://... or wss://... — the /browser connect " +
            "resolver should have rewritten this. Check that Chrome is " +
            "actually listening on the debug port.")
    }

    val callParams: Map<String, Any?> = params ?: emptyMap()
    var safeTimeout = try {
        if (timeout != 0.0) timeout else 30.0
    } catch (_: Exception) {
        30.0
    }
    safeTimeout = maxOf(1.0, minOf(safeTimeout, 300.0))

    val result: Map<String, Any?> = try {
        _runBlockingCdp(endpoint, method, callParams, targetId, safeTimeout)
    } catch (exc: Exception) {
        Log.w(_TAG, "browser_cdp error: ${exc.message}")
        return toolError(
            "Unexpected error: ${exc.javaClass.simpleName}: ${exc.message}",
            mapOf("method" to method))
    }

    val payload = mutableMapOf<String, Any?>(
        "success" to true,
        "method" to method,
        "result" to result)
    if (targetId != null) payload["target_id"] = targetId
    return JSONObject(payload).toString()
}

/** Async-from-sync bridge (matches homeassistant_tool.py pattern). */
private fun _runBlockingCdp(
    endpoint: String,
    method: String,
    params: Map<String, Any?>,
    targetId: String?,
    timeout: Double): Map<String, Any?> {
    return kotlinx.coroutines.runBlocking {
        _cdpCall(endpoint, method, params, targetId, timeout)
    }
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

val BROWSER_CDP_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "browser_cdp",
    "description" to (
        "Send a raw Chrome DevTools Protocol (CDP) command. Escape hatch for " +
        "browser operations not covered by browser_navigate, browser_click, " +
        "browser_console, etc.\n\n" +
        "**Requires a reachable CDP endpoint.** Available when the user has " +
        "run '/browser connect' to attach to a running Chrome, or when " +
        "'browser.cdp_url' is set in config.yaml.\n\n" +
        "**CDP method reference:** $CDP_DOCS_URL — use web_extract on a " +
        "method's URL to look up parameters and return shape.\n\n" +
        "**Common patterns:**\n" +
        "- List tabs: method='Target.getTargets', params={}\n" +
        "- Handle a native JS dialog: method='Page.handleJavaScriptDialog', " +
        "params={'accept': true, 'promptText': ''}, target_id=<tabId>\n" +
        "- Get all cookies: method='Network.getAllCookies', params={}\n" +
        "- Eval in a specific tab: method='Runtime.evaluate', " +
        "params={'expression': '...', 'returnByValue': true}, " +
        "target_id=<tabId>\n\n" +
        "**Usage rules:**\n" +
        "- Browser-level methods (Target.*, Browser.*, Storage.*): omit " +
        "target_id.\n" +
        "- Page-level methods (Page.*, Runtime.*, DOM.*, Emulation.*, " +
        "Network.* scoped to a tab): pass target_id from Target.getTargets.\n" +
        "- Each call is independent — sessions and event subscriptions do " +
        "not persist between calls."),
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "method" to mapOf(
                "type" to "string",
                "description" to "CDP method name, e.g. 'Target.getTargets'."),
            "params" to mapOf(
                "type" to "object",
                "description" to "Method-specific parameters as a JSON object. Omit or pass {} for methods that take no parameters.",
                "additionalProperties" to true),
            "target_id" to mapOf(
                "type" to "string",
                "description" to "Optional. Target/tab ID from Target.getTargets result."),
            "timeout" to mapOf(
                "type" to "number",
                "description" to "Timeout in seconds (default 30, max 300).",
                "default" to 30)),
        "required" to listOf("method")))

/**
 * Availability check for browser_cdp.
 *
 * The tool is only offered when we can actually reach a CDP endpoint right
 * now — meaning a static URL is set via `/browser connect` or
 * `browser.cdp_url` in `config.yaml`.
 */
private fun _browserCdpCheck(): Boolean {
    @Suppress("UNUSED_VARIABLE") val _checkErrFmt = "browser_cdp check: browser_tool import failed: %s"
    // TODO: delegate to BrowserTool.checkBrowserRequirements + _getCdpOverride
    return _resolveCdpEndpoint().isNotEmpty()
}

// Module-load side-effect: register with the tool registry.
// TODO: registry.register signature once BrowserTool is ported.

/** Python `_run_async` — stub. */
private suspend fun _runAsync(block: suspend () -> Any?): Any? = block()
