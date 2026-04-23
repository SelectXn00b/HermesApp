/**
 * Browser Tool — agent-browser CLI wrapper.
 *
 * 1:1 对齐 — provides browser automation backed by agent-browser /
 * Browserbase / Browser Use / Firecrawl / Camofox. Uses ariaSnapshot
 * for text-based page representation, making it ideal for LLM agents
 * without vision.
 *
 * Ported from tools/browser_tool.py (Python 原始, 2505 lines).
 * Android has no subprocess/CDP browser — the port is a structural
 * skeleton: all top-level names present, bodies deferred with TODO
 * comments. The schema list is preserved verbatim so tool-discovery
 * still works.
 */
package com.xiaomo.hermes.hermes.tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val _TAG = "browser_tool"

// ---------------------------------------------------------------------------
// PATH handling — mirrors _SANE_PATH_DIRS / homebrew discovery
// ---------------------------------------------------------------------------

val _SANE_PATH_DIRS: List<String> = listOf(
    "/data/data/com.termux/files/usr/bin",
    "/data/data/com.termux/files/usr/local/bin",
    "/usr/local/bin",
    "/usr/bin",
    "/bin",
    "/opt/homebrew/bin",
    "/opt/homebrew/sbin")

val _SANE_PATH: String = _SANE_PATH_DIRS.joinToString(":")

fun _discoverHomebrewNodeDirs(): List<String> = emptyList<String>()

fun _browserCandidatePathDirs(): List<String> = _SANE_PATH_DIRS + _discoverHomebrewNodeDirs()

@Suppress("UNUSED_PARAMETER")
fun _mergeBrowserPath(existingPath: String = ""): String {
    // TODO: port PATH merge preserving existing entries.
    val extras = _browserCandidatePathDirs()
    return if (existingPath.isEmpty()) extras.joinToString(":")
    else existingPath + ":" + extras.joinToString(":")
}

// ---------------------------------------------------------------------------
// Configuration constants
// ---------------------------------------------------------------------------

const val DEFAULT_COMMAND_TIMEOUT: Int = 30
const val SNAPSHOT_SUMMARIZE_THRESHOLD: Int = 8000

fun _getCommandTimeout(): Int {
    // TODO: port env override BROWSER_COMMAND_TIMEOUT.
    return DEFAULT_COMMAND_TIMEOUT
}

fun _getVisionModel(): String? = null

fun _getExtractionModel(): String? = null

@Suppress("UNUSED_PARAMETER")
fun _resolveCdpOverride(cdpUrl: String): String {
    // TODO: port host alias rewriting (localhost→host.docker.internal, etc.).
    return cdpUrl
}

fun _getCdpOverride(): String = ""

fun _getCloudProvider(): Any? = null

fun _browserInstallHint(): String {
    // TODO: port install-instruction string.
    return "Install agent-browser via: agent-browser install"
}

@Suppress("UNUSED_PARAMETER")
fun _requiresRealTermuxBrowserInstall(browserCmd: String): Boolean = false

fun _termuxBrowserInstallError(): String = ""

fun _isLocalMode(): Boolean {
    // TODO: port local-mode detection (no cloud provider available).
    return true
}

fun _isLocalBackend(): Boolean {
    // TODO: port local-backend detection.
    return true
}

fun _allowPrivateUrls(): Boolean = false

fun _socketSafeTmpdir(): String {
    // TODO: port socket-path length-safe tempdir selection.
    return "/tmp"
}

// ---------------------------------------------------------------------------
// Session tracking
// ---------------------------------------------------------------------------

val BROWSER_SESSION_INACTIVITY_TIMEOUT: Int =
    (System.getenv("BROWSER_INACTIVITY_TIMEOUT") ?: "300").toIntOrNull() ?: 300

fun _emergencyCleanupAllSessions(): Unit = Unit

fun _cleanupInactiveBrowserSessions(): Unit = Unit

@Suppress("UNUSED_PARAMETER")
fun _writeOwnerPid(socketDir: String, sessionName: String): Unit = Unit

fun _reapOrphanedBrowserSessions(): Unit = Unit

fun _browserCleanupThreadWorker(): Unit = Unit

fun _startBrowserCleanupThread(): Unit = Unit

fun _stopBrowserCleanupThread(): Unit = Unit

@Suppress("UNUSED_PARAMETER")
fun _updateSessionActivity(taskId: String): Unit = Unit

// ---------------------------------------------------------------------------
// Tool schemas (preserved verbatim from Python source)
// ---------------------------------------------------------------------------

val BROWSER_TOOL_SCHEMAS: List<Map<String, Any?>> = listOf(
    mapOf(
        "name" to "browser_navigate",
        "description" to (
            "Navigate to a URL in the browser. Initializes the session and loads the page. " +
            "Must be called before other browser tools. For simple information retrieval, prefer " +
            "web_search or web_extract (faster, cheaper). Use browser tools when you need to interact " +
            "with a page (click, fill forms, dynamic content). Returns a compact page snapshot with " +
            "interactive elements and ref IDs — no need to call browser_snapshot separately after navigating."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "url" to mapOf(
                    "type" to "string",
                    "description" to "The URL to navigate to (e.g., 'https://example.com')")),
            "required" to listOf("url"))),
    mapOf(
        "name" to "browser_snapshot",
        "description" to (
            "Get a text-based snapshot of the current page's accessibility tree. Returns interactive " +
            "elements with ref IDs (like @e1, @e2) for browser_click and browser_type. full=false " +
            "(default): compact view with interactive elements. full=true: complete page content. " +
            "Snapshots over 8000 chars are truncated or LLM-summarized. Requires browser_navigate first. " +
            "Note: browser_navigate already returns a compact snapshot — use this to refresh after " +
            "interactions that change the page, or with full=true for complete content."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "full" to mapOf(
                    "type" to "boolean",
                    "description" to "If true, returns complete page content. If false (default), returns compact view with interactive elements only.",
                    "default" to false)),
            "required" to emptyList<String>())),
    mapOf(
        "name" to "browser_click",
        "description" to (
            "Click on an element identified by its ref ID from the snapshot (e.g., '@e5'). The ref IDs " +
            "are shown in square brackets in the snapshot output. Requires browser_navigate and " +
            "browser_snapshot to be called first."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "ref" to mapOf(
                    "type" to "string",
                    "description" to "The element reference from the snapshot (e.g., '@e5', '@e12')")),
            "required" to listOf("ref"))),
    mapOf(
        "name" to "browser_type",
        "description" to (
            "Type text into an input field identified by its ref ID. Clears the field first, then types " +
            "the new text. Requires browser_navigate and browser_snapshot to be called first."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "ref" to mapOf(
                    "type" to "string",
                    "description" to "The element reference from the snapshot (e.g., '@e3')"),
                "text" to mapOf(
                    "type" to "string",
                    "description" to "The text to type into the field")),
            "required" to listOf("ref", "text"))),
    mapOf(
        "name" to "browser_scroll",
        "description" to (
            "Scroll the page in a direction. Use this to reveal more content that may be below or above " +
            "the current viewport. Requires browser_navigate to be called first."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "direction" to mapOf(
                    "type" to "string",
                    "enum" to listOf("up", "down"),
                    "description" to "Direction to scroll")),
            "required" to listOf("direction"))),
    mapOf(
        "name" to "browser_back",
        "description" to "Navigate back to the previous page in browser history. Requires browser_navigate to be called first.",
        "parameters" to mapOf(
            "type" to "object",
            "properties" to emptyMap<String, Any?>(),
            "required" to emptyList<String>())),
    mapOf(
        "name" to "browser_press",
        "description" to (
            "Press a keyboard key. Useful for submitting forms (Enter), navigating (Tab), or keyboard " +
            "shortcuts. Requires browser_navigate to be called first."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "key" to mapOf(
                    "type" to "string",
                    "description" to "Key to press (e.g., 'Enter', 'Tab', 'Escape', 'ArrowDown')")),
            "required" to listOf("key"))),
    mapOf(
        "name" to "browser_get_images",
        "description" to (
            "Get a list of all images on the current page with their URLs and alt text. Useful for finding " +
            "images to analyze with the vision tool. Requires browser_navigate to be called first."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to emptyMap<String, Any?>(),
            "required" to emptyList<String>())),
    mapOf(
        "name" to "browser_vision",
        "description" to (
            "Take a screenshot of the current page and analyze it with vision AI. Use this when you need " +
            "to visually understand what's on the page - especially useful for CAPTCHAs, visual verification " +
            "challenges, complex layouts, or when the text snapshot doesn't capture important visual " +
            "information. Returns both the AI analysis and a screenshot_path that you can share with the " +
            "user by including MEDIA:<screenshot_path> in your response. Requires browser_navigate to be " +
            "called first."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "question" to mapOf(
                    "type" to "string",
                    "description" to "What you want to know about the page visually. Be specific about what you're looking for."),
                "annotate" to mapOf(
                    "type" to "boolean",
                    "default" to false,
                    "description" to "If true, overlay numbered [N] labels on interactive elements. Each [N] maps to ref @eN for subsequent browser commands. Useful for QA and spatial reasoning about page layout.")),
            "required" to listOf("question"))),
    mapOf(
        "name" to "browser_console",
        "description" to (
            "Get browser console output and JavaScript errors from the current page. Returns " +
            "console.log/warn/error/info messages and uncaught JS exceptions. Use this to detect silent " +
            "JavaScript errors, failed API calls, and application warnings. Requires browser_navigate to be " +
            "called first. When 'expression' is provided, evaluates JavaScript in the page context and " +
            "returns the result — use this for DOM inspection, reading page state, or extracting data " +
            "programmatically."),
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "clear" to mapOf(
                    "type" to "boolean",
                    "default" to false,
                    "description" to "If true, clear the message buffers after reading"),
                "expression" to mapOf(
                    "type" to "string",
                    "description" to "JavaScript expression to evaluate in the page context. Runs in the browser like DevTools console — full access to DOM, window, document. Return values are serialized to JSON. Example: 'document.title' or 'document.querySelectorAll(\"a\").length'")),
            "required" to emptyList<String>())))

// ---------------------------------------------------------------------------
// Session creation
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
fun _createLocalSession(taskId: String): Map<String, Any?> {
    // TODO: port uuid-based session name generation.
    val sessionName = "h_" + java.util.UUID.randomUUID().toString().replace("-", "").take(10)
    Log.i(_TAG, "Created local browser session $sessionName for task $taskId")
    return mapOf(
        "session_name" to sessionName,
        "bb_session_id" to null,
        "cdp_url" to null,
        "features" to mapOf("local" to true))
}

@Suppress("UNUSED_PARAMETER")
fun _createCdpSession(taskId: String, cdpUrl: String): Map<String, Any?> {
    val sessionName = "cdp_" + java.util.UUID.randomUUID().toString().replace("-", "").take(10)
    Log.i(_TAG, "Created CDP browser session $sessionName → $cdpUrl for task $taskId")
    return mapOf(
        "session_name" to sessionName,
        "bb_session_id" to null,
        "cdp_url" to cdpUrl,
        "features" to mapOf("cdp_override" to true))
}

@Suppress("UNUSED_PARAMETER")
fun _getSessionInfo(taskId: String? = null): Map<String, Any?> {
    // TODO: port session lookup/creation with thread-safe store.
    val id = taskId ?: "default"
    return _createLocalSession(id)
}

fun _findAgentBrowser(): String {
    // TODO: port shutil.which("agent-browser") with PATH override.
    return "agent-browser"
}

@Suppress("UNUSED_PARAMETER")
fun _extractScreenshotPathFromText(text: String): String? {
    // TODO: port regex extraction of "Screenshot saved to: ..." path.
    val match = Regex("[Ss]creenshot(?:\\s+saved)?(?:\\s+to)?:\\s*(\\S+)").find(text)
    return match?.groupValues?.getOrNull(1)
}

// ---------------------------------------------------------------------------
// Subprocess runner + content helpers
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
fun _runBrowserCommand(
    command: List<String>,
    timeout: Int? = null,
    env: Map<String, String>? = null,
    taskId: String? = null): String {
    // TODO: port subprocess.run + stderr/stdout capture + error handling.
    return JSONObject(mapOf("error" to "browser tool not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _extractRelevantContent(
    snapshotText: String,
    userTask: String? = null): String {
    // TODO: port LLM-based task-aware extraction.
    return snapshotText.take(8000)
}

@Suppress("UNUSED_PARAMETER")
fun _truncateSnapshot(snapshotText: String, maxChars: Int = 8000): String {
    if (snapshotText.length <= maxChars) return snapshotText
    return snapshotText.take(maxChars) + "\n... [truncated]"
}

// ---------------------------------------------------------------------------
// Public tool entry points — all disabled on Android until transport lands
// ---------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
fun browserNavigate(url: String, taskId: String? = null): String {
    // TODO: port navigate via agent-browser / cloud provider.
    return JSONObject(mapOf("error" to "browser_navigate not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun browserSnapshot(full: Boolean = false, taskId: String? = null): String {
    // TODO: port ariaSnapshot fetch + summarize.
    return JSONObject(mapOf("error" to "browser_snapshot not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun browserClick(ref: String, taskId: String? = null): String {
    // TODO: port click via ref selector.
    return JSONObject(mapOf("error" to "browser_click not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun browserType(ref: String, text: String, taskId: String? = null): String {
    // TODO: port type into input field.
    return JSONObject(mapOf("error" to "browser_type not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun browserScroll(direction: String, taskId: String? = null): String {
    // TODO: port scroll up/down.
    return JSONObject(mapOf("error" to "browser_scroll not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun browserBack(taskId: String? = null): String {
    // TODO: port history back.
    return JSONObject(mapOf("error" to "browser_back not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun browserPress(key: String, taskId: String? = null): String {
    // TODO: port keypress.
    return JSONObject(mapOf("error" to "browser_press not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun browserConsole(clear: Boolean = false, expression: String? = null, taskId: String? = null): String {
    // TODO: port console.log readback + JS eval.
    return JSONObject(mapOf("error" to "browser_console not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _browserEval(expression: String, taskId: String? = null): String {
    // TODO: port JS eval without console logs.
    return JSONObject(mapOf("error" to "_browser_eval not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _camofoxEval(expression: String, taskId: String? = null): String {
    // TODO: port camofox REST eval.
    return JSONObject(mapOf("error" to "_camofox_eval not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _maybeStartRecording(taskId: String): Unit = Unit

@Suppress("UNUSED_PARAMETER")
fun _maybeStopRecording(taskId: String): Unit = Unit

@Suppress("UNUSED_PARAMETER")
fun browserGetImages(taskId: String? = null): String {
    // TODO: port image list extraction.
    return JSONObject(mapOf("error" to "browser_get_images not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun browserVision(question: String, annotate: Boolean = false, taskId: String? = null): String {
    // TODO: port screenshot + vision LLM call.
    return JSONObject(mapOf("error" to "browser_vision not available on Android")).toString()
}

@Suppress("UNUSED_PARAMETER")
fun _cleanupOldScreenshots(screenshotsDir: Any?, maxAgeHours: Int = 24): Unit = Unit

@Suppress("UNUSED_PARAMETER")
fun _cleanupOldRecordings(maxAgeHours: Int = 72): Unit = Unit

@Suppress("UNUSED_PARAMETER")
fun cleanupBrowser(taskId: String? = null): Unit = Unit

fun cleanupAllBrowsers(): Unit = Unit

fun checkBrowserRequirements(): Boolean = false

// ---------------------------------------------------------------------------
// Schema lookup
// ---------------------------------------------------------------------------

val _BROWSER_SCHEMA_MAP: Map<String, Map<String, Any?>> =
    BROWSER_TOOL_SCHEMAS.associateBy { it["name"].toString() }
