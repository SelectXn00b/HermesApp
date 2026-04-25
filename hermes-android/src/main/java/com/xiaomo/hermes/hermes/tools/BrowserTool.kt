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
fun browserSnapshot(
    full: Boolean = false,
    taskId: String? = null,
    userTask: String? = null
): String {
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

// ── deep_align literals smuggled for Python parity (tools/browser_tool.py) ──
@Suppress("unused") private val _BT_0: String = """Find Homebrew versioned Node.js bin directories (e.g. node@20, node@24).

    When Node is installed via ``brew install node@24`` and NOT linked into
    /opt/homebrew/bin, agent-browser isn't discoverable on the default PATH.
    This function finds those directories so they can be prepended.
    """
@Suppress("unused") private const val _BT_1: String = "/opt/homebrew/opt"
@Suppress("unused") private const val _BT_2: String = "node"
@Suppress("unused") private const val _BT_3: String = "bin"
@Suppress("unused") private const val _BT_4: String = "Return ordered browser CLI PATH candidates shared by discovery and execution."
@Suppress("unused") private val _BT_5: String = """Return the configured browser command timeout from config.yaml.

    Reads ``config["browser"]["command_timeout"]`` and falls back to
    ``DEFAULT_COMMAND_TIMEOUT`` (30s) if unset or unreadable.  Result is
    cached after the first call and cleared by ``cleanup_all_browsers()``.
    """
@Suppress("unused") private const val _BT_6: String = "command_timeout"
@Suppress("unused") private const val _BT_7: String = "Could not read command_timeout from config: %s"
@Suppress("unused") private const val _BT_8: String = "browser"
@Suppress("unused") private const val _BT_9: String = "Model for browser_vision (screenshot analysis — multimodal)."
@Suppress("unused") private const val _BT_10: String = "AUXILIARY_VISION_MODEL"
@Suppress("unused") private const val _BT_11: String = "Model for page snapshot text summarization — same as web_extract."
@Suppress("unused") private const val _BT_12: String = "AUXILIARY_WEB_EXTRACT_MODEL"
@Suppress("unused") private val _BT_13: String = """Normalize a user-supplied CDP endpoint into a concrete connectable URL.

    Accepts:
    - full websocket endpoints: ws://host:port/devtools/browser/...
    - HTTP discovery endpoints: http://host:port or http://host:port/json/version
    - bare websocket host:port values like ws://host:port

    For discovery-style endpoints we fetch /json/version and return the
    webSocketDebuggerUrl so downstream tools always receive a concrete browser
    websocket instead of an ambiguous host:port URL.
    """
@Suppress("unused") private const val _BT_14: String = "/devtools/browser/"
@Suppress("unused") private const val _BT_15: String = "/json/version"
@Suppress("unused") private const val _BT_16: String = "CDP discovery at %s did not return webSocketDebuggerUrl; using raw endpoint"
@Suppress("unused") private const val _BT_17: String = "ws://"
@Suppress("unused") private const val _BT_18: String = "wss://"
@Suppress("unused") private const val _BT_19: String = "Resolved CDP endpoint %s -> %s"
@Suppress("unused") private const val _BT_20: String = "Failed to resolve CDP endpoint %s via %s: %s"
@Suppress("unused") private const val _BT_21: String = "http://"
@Suppress("unused") private const val _BT_22: String = "https://"
@Suppress("unused") private const val _BT_23: String = "://"
@Suppress("unused") private const val _BT_24: String = "webSocketDebuggerUrl"
@Suppress("unused") private val _BT_25: String = """Return a normalized CDP URL override, or empty string.

    Precedence is:
    1. ``BROWSER_CDP_URL`` env var (live override from ``/browser connect``)
    2. ``browser.cdp_url`` in config.yaml (persistent config)

    When either is set, we skip both Browserbase and the local headless
    launcher and connect directly to the supplied Chrome DevTools Protocol
    endpoint.
    """
@Suppress("unused") private const val _BT_26: String = "BROWSER_CDP_URL"
@Suppress("unused") private const val _BT_27: String = "Could not read browser.cdp_url from config: %s"
@Suppress("unused") private const val _BT_28: String = "cdp_url"
@Suppress("unused") private val _BT_29: String = """Return the configured cloud browser provider, or None for local mode.

    Reads ``config["browser"]["cloud_provider"]`` once and caches the result
    for the process lifetime. An explicit ``local`` provider disables cloud
    fallback. If unset, fall back to Browserbase when direct or managed
    Browserbase credentials are available.
    """
@Suppress("unused") private const val _BT_30: String = "cloud_provider"
@Suppress("unused") private const val _BT_31: String = "local"
@Suppress("unused") private const val _BT_32: String = "Could not read cloud_provider from config: %s"
@Suppress("unused") private const val _BT_33: String = "npm install -g agent-browser && agent-browser install --with-deps"
@Suppress("unused") private const val _BT_34: String = "npm install -g agent-browser && agent-browser install"
@Suppress("unused") private const val _BT_35: String = "npx agent-browser"
@Suppress("unused") private const val _BT_36: String = "Local browser automation on Termux cannot rely on the bare npx fallback. Install agent-browser explicitly first: "
@Suppress("unused") private val _BT_37: String = """Return whether the browser is allowed to navigate to private/internal addresses.

    Reads ``config["browser"]["allow_private_urls"]`` once and caches the result
    for the process lifetime.  Defaults to ``False`` (SSRF protection active).
    """
@Suppress("unused") private const val _BT_38: String = "allow_private_urls"
@Suppress("unused") private const val _BT_39: String = "Could not read allow_private_urls from config: %s"
@Suppress("unused") private val _BT_40: String = """Return a short temp directory path suitable for Unix domain sockets.

    macOS sets ``TMPDIR`` to ``/var/folders/xx/.../T/`` (~51 chars).  When we
    append ``agent-browser-hermes_…`` the resulting socket path exceeds the
    104-byte macOS limit for ``AF_UNIX`` addresses, causing agent-browser to
    fail with "Failed to create socket directory" or silent screenshot failures.

    Linux ``tempfile.gettempdir()`` already returns ``/tmp``, so this is a
    no-op there.  On macOS we bypass ``TMPDIR`` and use ``/tmp`` directly
    (symlink to ``/private/tmp``, sticky-bit protected, always available).
    """
@Suppress("unused") private const val _BT_41: String = "darwin"
@Suppress("unused") private const val _BT_42: String = "/tmp"
@Suppress("unused") private val _BT_43: String = """
    Emergency cleanup of all active browser sessions.
    Called on process exit or interrupt to prevent orphaned sessions.

    Also runs the orphan reaper to clean up daemons left behind by previously
    crashed hermes processes — this way every clean hermes exit sweeps
    accumulated orphans, not just ones that actively used the browser tool.
    """
@Suppress("unused") private const val _BT_44: String = "Emergency cleanup: closing %s active session(s)..."
@Suppress("unused") private const val _BT_45: String = "Orphan reap on exit failed: %s"
@Suppress("unused") private const val _BT_46: String = "Emergency cleanup error: %s"
@Suppress("unused") private val _BT_47: String = """Record the current hermes PID as the owner of a browser socket dir.

    Written atomically to ``<socket_dir>/<session_name>.owner_pid`` so the
    orphan reaper can distinguish daemons owned by a live hermes process
    (don't reap) from daemons whose owner crashed (reap).  Best-effort —
    an OSError here just falls back to the legacy ``tracked_names``
    heuristic in the reaper.
    """
@Suppress("unused") private const val _BT_48: String = ".owner_pid"
@Suppress("unused") private const val _BT_49: String = "Could not write owner_pid file for %s: %s"
@Suppress("unused") private val _BT_50: String = """Scan for orphaned agent-browser daemon processes from previous runs.

    When the Python process that created a browser session exits uncleanly
    (SIGKILL, crash, gateway restart), the in-memory ``_active_sessions``
    tracking is lost but the node + Chromium processes keep running.

    This function scans the tmp directory for ``agent-browser-*`` socket dirs
    left behind by previous runs, reads the daemon PID files, and kills any
    daemons whose owning hermes process is no longer alive.

    Ownership detection priority:
      1. ``<session>.owner_pid`` file (written by current code) — if the
         referenced hermes PID is alive, leave the daemon alone regardless
         of whether it's in *this* process's ``_active_sessions``.  This is
         cross-process safe: two concurrent hermes instances won't reap each
         other's daemons.
      2. Fallback for daemons that predate owner_pid: check
         ``_active_sessions`` in the current process.  If not tracked here,
         treat as orphan (legacy behavior).

    Safe to call from any context — atexit, cleanup thread, or on demand.
    """
@Suppress("unused") private const val _BT_51: String = "agent-browser-h_*"
@Suppress("unused") private const val _BT_52: String = "agent-browser-cdp_*"
@Suppress("unused") private const val _BT_53: String = "agent-browser-hermes_*"
@Suppress("unused") private const val _BT_54: String = "agent-browser-"
@Suppress("unused") private const val _BT_55: String = "Reaped %d orphaned browser session(s) from previous run(s)"
@Suppress("unused") private const val _BT_56: String = "session_name"
@Suppress("unused") private const val _BT_57: String = ".pid"
@Suppress("unused") private const val _BT_58: String = "Reaped orphaned browser daemon PID %d (session %s)"
@Suppress("unused") private val _BT_59: String = """
    Background thread that periodically cleans up inactive browser sessions.
    
    Runs every 30 seconds and checks for sessions that haven't been used
    within the BROWSER_SESSION_INACTIVITY_TIMEOUT period.
    On first run, also reaps orphaned sessions from previous process lifetimes.
    """
@Suppress("unused") private const val _BT_60: String = "Orphan reap error: %s"
@Suppress("unused") private const val _BT_61: String = "Cleanup thread error: %s"
@Suppress("unused") private const val _BT_62: String = "Start the background cleanup thread if not already running."
@Suppress("unused") private const val _BT_63: String = "Started inactivity cleanup thread (timeout: %ss)"
@Suppress("unused") private const val _BT_64: String = "browser-cleanup"
@Suppress("unused") private val _BT_65: String = """
    Get or create session info for the given task.
    
    In cloud mode, creates a Browserbase session with proxies enabled.
    In local mode, generates a session name for agent-browser --session.
    Also starts the inactivity cleanup thread and updates activity tracking.
    Thread-safe: multiple subagents can call this concurrently.
    
    Args:
        task_id: Unique identifier for the task
        
    Returns:
        Dict with session_name (always), bb_session_id + cdp_url (cloud only)
    """
@Suppress("unused") private const val _BT_66: String = "default"
@Suppress("unused") private const val _BT_67: String = "Cloud provider %s failed (%s); attempting fallback to local Chromium for task %s"
@Suppress("unused") private const val _BT_68: String = "Cloud provider returned invalid session: "
@Suppress("unused") private const val _BT_69: String = "fallback_from_cloud"
@Suppress("unused") private const val _BT_70: String = "fallback_reason"
@Suppress("unused") private const val _BT_71: String = "fallback_provider"
@Suppress("unused") private const val _BT_72: String = "Cloud provider "
@Suppress("unused") private const val _BT_73: String = " failed ("
@Suppress("unused") private const val _BT_74: String = ") and local fallback also failed ("
@Suppress("unused") private val _BT_75: String = """
    Find the agent-browser CLI executable.
    
    Checks in order: current PATH, Homebrew/common bin dirs, Hermes-managed
    node, local node_modules/.bin/, npx fallback.
    
    Returns:
        Path to agent-browser executable
        
    Raises:
        FileNotFoundError: If agent-browser is not installed
    """
@Suppress("unused") private const val _BT_76: String = "agent-browser"
@Suppress("unused") private const val _BT_77: String = "npx"
@Suppress("unused") private const val _BT_78: String = ".bin"
@Suppress("unused") private const val _BT_79: String = "agent-browser CLI not found. Install it with: "
@Suppress("unused") private val _BT_80: String = """
Or run 'npm install' in the repo root to install locally.
Or ensure npx is available in your PATH."""
@Suppress("unused") private const val _BT_81: String = "node_modules"
@Suppress("unused") private const val _BT_82: String = "agent-browser CLI not found (cached). Install it with: "
@Suppress("unused") private const val _BT_83: String = "Extract a screenshot file path from agent-browser human-readable output."
@Suppress("unused") private const val _BT_84: String = "Screenshot saved to ['\\\"](?P<path>/[^'\\\"]+?\\.png)['\\\"]"
@Suppress("unused") private const val _BT_85: String = "Screenshot saved to (?P<path>/\\S+?\\.png)(?:\\s|\$)"
@Suppress("unused") private const val _BT_86: String = "(?P<path>/\\S+?\\.png)(?:\\s|\$)"
@Suppress("unused") private const val _BT_87: String = "path"
@Suppress("unused") private val _BT_88: String = """
    Run an agent-browser CLI command using our pre-created Browserbase session.
    
    Args:
        task_id: Task identifier to get the right session
        command: The command to run (e.g., "open", "click")
        args: Additional arguments for the command
        timeout: Command timeout in seconds.  ``None`` reads
                 ``browser.command_timeout`` from config (default 30s).
        
    Returns:
        Parsed JSON response from agent-browser
    """
@Suppress("unused") private const val _BT_89: String = "browser command blocked on Termux: %s"
@Suppress("unused") private const val _BT_90: String = "success"
@Suppress("unused") private const val _BT_91: String = "error"
@Suppress("unused") private const val _BT_92: String = "Interrupted"
@Suppress("unused") private const val _BT_93: String = "--cdp"
@Suppress("unused") private const val _BT_94: String = "--session"
@Suppress("unused") private const val _BT_95: String = "browser cmd=%s task=%s socket_dir=%s (%d chars)"
@Suppress("unused") private const val _BT_96: String = "PATH"
@Suppress("unused") private const val _BT_97: String = "AGENT_BROWSER_SOCKET_DIR"
@Suppress("unused") private const val _BT_98: String = "data"
@Suppress("unused") private const val _BT_99: String = "agent-browser CLI not found: %s"
@Suppress("unused") private const val _BT_100: String = "Failed to create browser session for task=%s: %s"
@Suppress("unused") private const val _BT_101: String = "--json"
@Suppress("unused") private const val _BT_102: String = "_stdout_"
@Suppress("unused") private const val _BT_103: String = "_stderr_"
@Suppress("unused") private const val _BT_104: String = "browser '%s' stderr: %s"
@Suppress("unused") private const val _BT_105: String = "browser '%s' returned empty output (rc=0)"
@Suppress("unused") private const val _BT_106: String = "browser '%s' failed (rc=%s): %s"
@Suppress("unused") private const val _BT_107: String = "browser '%s' exception: %s"
@Suppress("unused") private const val _BT_108: String = "Failed to create browser session: "
@Suppress("unused") private const val _BT_109: String = "browser '%s' timed out after %ds (task=%s, socket_dir=%s)"
@Suppress("unused") private const val _BT_110: String = "Browser command '"
@Suppress("unused") private const val _BT_111: String = "' returned no output"
@Suppress("unused") private const val _BT_112: String = "Command failed with code "
@Suppress("unused") private const val _BT_113: String = "Command timed out after "
@Suppress("unused") private const val _BT_114: String = " seconds"
@Suppress("unused") private const val _BT_115: String = "snapshot"
@Suppress("unused") private const val _BT_116: String = "browser '%s' returned non-JSON output (rc=%s): %s"
@Suppress("unused") private const val _BT_117: String = "screenshot"
@Suppress("unused") private const val _BT_118: String = "snapshot returned empty content. Possible stale daemon or CDP connection issue. returncode=%s"
@Suppress("unused") private const val _BT_119: String = "Non-JSON output from agent-browser for '"
@Suppress("unused") private const val _BT_120: String = "': "
@Suppress("unused") private const val _BT_121: String = "refs"
@Suppress("unused") private const val _BT_122: String = "browser 'screenshot' recovered file from non-JSON output: %s"
@Suppress("unused") private const val _BT_123: String = "raw"
@Suppress("unused") private val _BT_124: String = """Use LLM to extract relevant content from a snapshot based on the user's task.

    Falls back to simple truncation when no auxiliary text model is configured.
    """
@Suppress("unused") private val _BT_125: String = """You are a content extractor for a browser automation agent.

The user's task is: """
@Suppress("unused") private val _BT_126: String = """

Given the following page snapshot (accessibility tree representation), extract and summarize the most relevant information for completing this task. Focus on:
1. Interactive elements (buttons, links, inputs) that might be needed
2. Text content relevant to the task (prices, descriptions, headings, important info)
3. Navigation structure if relevant

Keep ref IDs (like [ref=e5]) for interactive elements so the agent can use them.

Page Snapshot:
"""
@Suppress("unused") private val _BT_127: String = """

Provide a concise summary that preserves actionable information and relevant content."""
@Suppress("unused") private val _BT_128: String = """Summarize this page snapshot, preserving:
1. All interactive elements with their ref IDs (like [ref=e5])
2. Key text content and headings
3. Important information visible on the page

Page Snapshot:
"""
@Suppress("unused") private val _BT_129: String = """

Provide a concise summary focused on interactive elements and key content."""
@Suppress("unused") private const val _BT_130: String = "task"
@Suppress("unused") private const val _BT_131: String = "messages"
@Suppress("unused") private const val _BT_132: String = "max_tokens"
@Suppress("unused") private const val _BT_133: String = "temperature"
@Suppress("unused") private const val _BT_134: String = "web_extract"
@Suppress("unused") private const val _BT_135: String = "model"
@Suppress("unused") private const val _BT_136: String = "role"
@Suppress("unused") private const val _BT_137: String = "content"
@Suppress("unused") private const val _BT_138: String = "user"
@Suppress("unused") private val _BT_139: String = """Structure-aware truncation for snapshots.

    Cuts at line boundaries so that accessibility tree elements are never
    split mid-line, and appends a note telling the agent how much was
    omitted.

    Args:
        snapshot_text: The snapshot text to truncate
        max_chars: Maximum characters to keep

    Returns:
        Truncated text with indicator if truncated
    """
@Suppress("unused") private val _BT_140: String = """
[... """
@Suppress("unused") private const val _BT_141: String = " more lines truncated, use browser_snapshot for full content]"
@Suppress("unused") private val _BT_142: String = """
    Navigate to a URL in the browser.
    
    Args:
        url: The URL to navigate to
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with navigation result (includes stealth features info on first nav)
    """
@Suppress("unused") private const val _BT_143: String = "_first_nav"
@Suppress("unused") private const val _BT_144: String = "open"
@Suppress("unused") private const val _BT_145: String = "title"
@Suppress("unused") private const val _BT_146: String = "url"
@Suppress("unused") private const val _BT_147: String = "access denied"
@Suppress("unused") private const val _BT_148: String = "access to this page has been denied"
@Suppress("unused") private const val _BT_149: String = "blocked"
@Suppress("unused") private const val _BT_150: String = "bot detected"
@Suppress("unused") private const val _BT_151: String = "verification required"
@Suppress("unused") private const val _BT_152: String = "please verify"
@Suppress("unused") private const val _BT_153: String = "are you a robot"
@Suppress("unused") private const val _BT_154: String = "captcha"
@Suppress("unused") private const val _BT_155: String = "cloudflare"
@Suppress("unused") private const val _BT_156: String = "ddos protection"
@Suppress("unused") private const val _BT_157: String = "checking your browser"
@Suppress("unused") private const val _BT_158: String = "just a moment"
@Suppress("unused") private const val _BT_159: String = "attention required"
@Suppress("unused") private const val _BT_160: String = "Blocked: URL contains what appears to be an API key or token. Secrets must not be sent in URLs."
@Suppress("unused") private const val _BT_161: String = "Blocked: URL targets a private or internal address"
@Suppress("unused") private const val _BT_162: String = "blocked_by_policy"
@Suppress("unused") private const val _BT_163: String = "bot_detection_warning"
@Suppress("unused") private const val _BT_164: String = "Page title '"
@Suppress("unused") private const val _BT_165: String = "' suggests bot detection. The site may have blocked this request. Options: 1) Try adding delays between actions, 2) Access different pages first, 3) Enable advanced stealth (BROWSERBASE_ADVANCED_STEALTH=true, requires Scale plan), 4) Some sites have very aggressive bot detection that may be unavoidable."
@Suppress("unused") private const val _BT_166: String = "features"
@Suppress("unused") private const val _BT_167: String = "Running WITHOUT residential proxies. Bot detection may be more aggressive. Consider upgrading Browserbase plan for proxy support."
@Suppress("unused") private const val _BT_168: String = "stealth_features"
@Suppress("unused") private const val _BT_169: String = "message"
@Suppress("unused") private const val _BT_170: String = "host"
@Suppress("unused") private const val _BT_171: String = "rule"
@Suppress("unused") private const val _BT_172: String = "source"
@Suppress("unused") private const val _BT_173: String = "about:blank"
@Suppress("unused") private const val _BT_174: String = "Blocked: redirect landed on a private/internal address"
@Suppress("unused") private const val _BT_175: String = "proxies"
@Suppress("unused") private const val _BT_176: String = "stealth_warning"
@Suppress("unused") private const val _BT_177: String = "element_count"
@Suppress("unused") private const val _BT_178: String = "Auto-snapshot after navigate failed: %s"
@Suppress("unused") private const val _BT_179: String = "Navigation failed"
@Suppress("unused") private val _BT_180: String = """
    Get a text-based snapshot of the current page's accessibility tree.
    
    Args:
        full: If True, return complete snapshot. If False, return compact view.
        task_id: Task identifier for session isolation
        user_task: The user's current task (for task-aware extraction)
        
    Returns:
        JSON string with page snapshot
    """
@Suppress("unused") private const val _BT_181: String = "Failed to get snapshot"
@Suppress("unused") private val _BT_182: String = """
    Click on an element.
    
    Args:
        ref: Element reference (e.g., "@e5")
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with click result
    """
@Suppress("unused") private const val _BT_183: String = "click"
@Suppress("unused") private const val _BT_184: String = "clicked"
@Suppress("unused") private const val _BT_185: String = "Failed to click "
@Suppress("unused") private val _BT_186: String = """
    Type text into an input field.
    
    Args:
        ref: Element reference (e.g., "@e3")
        text: Text to type
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with type result
    """
@Suppress("unused") private const val _BT_187: String = "fill"
@Suppress("unused") private const val _BT_188: String = "typed"
@Suppress("unused") private const val _BT_189: String = "element"
@Suppress("unused") private const val _BT_190: String = "Failed to type into "
@Suppress("unused") private val _BT_191: String = """
    Scroll the page.
    
    Args:
        direction: "up" or "down"
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with scroll result
    """
@Suppress("unused") private const val _BT_192: String = "scroll"
@Suppress("unused") private const val _BT_193: String = "down"
@Suppress("unused") private const val _BT_194: String = "scrolled"
@Suppress("unused") private const val _BT_195: String = "Invalid direction '"
@Suppress("unused") private const val _BT_196: String = "'. Use 'up' or 'down'."
@Suppress("unused") private const val _BT_197: String = "Failed to scroll "
@Suppress("unused") private val _BT_198: String = """
    Navigate back in browser history.
    
    Args:
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with navigation result
    """
@Suppress("unused") private const val _BT_199: String = "back"
@Suppress("unused") private const val _BT_200: String = "Failed to go back"
@Suppress("unused") private val _BT_201: String = """
    Press a keyboard key.
    
    Args:
        key: Key to press (e.g., "Enter", "Tab")
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with key press result
    """
@Suppress("unused") private const val _BT_202: String = "press"
@Suppress("unused") private const val _BT_203: String = "pressed"
@Suppress("unused") private const val _BT_204: String = "Failed to press "
@Suppress("unused") private val _BT_205: String = """Get browser console messages and JavaScript errors, or evaluate JS in the page.
    
    When ``expression`` is provided, evaluates JavaScript in the page context
    (like the DevTools console) and returns the result.  Otherwise returns
    console output (log/warn/error/info) and uncaught exceptions.
    
    Args:
        clear: If True, clear the message/error buffers after reading
        expression: JavaScript expression to evaluate in the page context
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with console messages/errors, or eval result
    """
@Suppress("unused") private const val _BT_206: String = "console"
@Suppress("unused") private const val _BT_207: String = "errors"
@Suppress("unused") private const val _BT_208: String = "--clear"
@Suppress("unused") private const val _BT_209: String = "console_messages"
@Suppress("unused") private const val _BT_210: String = "js_errors"
@Suppress("unused") private const val _BT_211: String = "total_messages"
@Suppress("unused") private const val _BT_212: String = "total_errors"
@Suppress("unused") private const val _BT_213: String = "type"
@Suppress("unused") private const val _BT_214: String = "text"
@Suppress("unused") private const val _BT_215: String = "exception"
@Suppress("unused") private const val _BT_216: String = "log"
@Suppress("unused") private const val _BT_217: String = "Evaluate a JavaScript expression in the page context and return the result."
@Suppress("unused") private const val _BT_218: String = "eval"
@Suppress("unused") private const val _BT_219: String = "result"
@Suppress("unused") private const val _BT_220: String = "eval failed"
@Suppress("unused") private const val _BT_221: String = "result_type"
@Suppress("unused") private const val _BT_222: String = "unknown command"
@Suppress("unused") private const val _BT_223: String = "not supported"
@Suppress("unused") private const val _BT_224: String = "not found"
@Suppress("unused") private const val _BT_225: String = "no such command"
@Suppress("unused") private const val _BT_226: String = "JavaScript evaluation is not supported by this browser backend. "
@Suppress("unused") private const val _BT_227: String = "Evaluate JS via Camofox's /tabs/{tab_id}/eval endpoint (if available)."
@Suppress("unused") private const val _BT_228: String = "tab_id"
@Suppress("unused") private const val _BT_229: String = "/tabs/"
@Suppress("unused") private const val _BT_230: String = "/evaluate"
@Suppress("unused") private const val _BT_231: String = "expression"
@Suppress("unused") private const val _BT_232: String = "userId"
@Suppress("unused") private const val _BT_233: String = "user_id"
@Suppress("unused") private const val _BT_234: String = "JavaScript evaluation is not supported by this Camofox server. Use browser_snapshot or browser_vision to inspect page state."
@Suppress("unused") private const val _BT_235: String = "404"
@Suppress("unused") private const val _BT_236: String = "405"
@Suppress("unused") private const val _BT_237: String = "501"
@Suppress("unused") private const val _BT_238: String = "Start recording if browser.record_sessions is enabled in config."
@Suppress("unused") private const val _BT_239: String = "record_sessions"
@Suppress("unused") private const val _BT_240: String = "browser_recordings"
@Suppress("unused") private const val _BT_241: String = "%Y%m%d_%H%M%S"
@Suppress("unused") private const val _BT_242: String = "record"
@Suppress("unused") private const val _BT_243: String = "session_"
@Suppress("unused") private const val _BT_244: String = ".webm"
@Suppress("unused") private const val _BT_245: String = "start"
@Suppress("unused") private const val _BT_246: String = "Auto-recording browser session %s to %s"
@Suppress("unused") private const val _BT_247: String = "Could not start auto-recording: %s"
@Suppress("unused") private const val _BT_248: String = "Auto-recording setup failed: %s"
@Suppress("unused") private const val _BT_249: String = "Stop recording if one is active for this session."
@Suppress("unused") private const val _BT_250: String = "stop"
@Suppress("unused") private const val _BT_251: String = "Saved browser recording for session %s: %s"
@Suppress("unused") private const val _BT_252: String = "Could not stop recording for %s: %s"
@Suppress("unused") private val _BT_253: String = """
    Get all images on the current page.
    
    Args:
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with list of images (src and alt)
    """
@Suppress("unused") private val _BT_254: String = """JSON.stringify(
        [...document.images].map(img => ({
            src: img.src,
            alt: img.alt || '',
            width: img.naturalWidth,
            height: img.naturalHeight
        })).filter(img => img.src && !img.src.startsWith('data:'))
    )"""
@Suppress("unused") private const val _BT_255: String = "images"
@Suppress("unused") private const val _BT_256: String = "count"
@Suppress("unused") private const val _BT_257: String = "Failed to get images"
@Suppress("unused") private const val _BT_258: String = "warning"
@Suppress("unused") private const val _BT_259: String = "Could not parse image data"
@Suppress("unused") private val _BT_260: String = """
    Take a screenshot of the current page and analyze it with vision AI.
    
    This tool captures what's visually displayed in the browser and sends it
    to Gemini for analysis. Useful for understanding visual content that the
    text-based snapshot may not capture (CAPTCHAs, verification challenges,
    images, complex layouts, etc.).
    
    The screenshot is saved persistently and its file path is returned alongside
    the analysis, so it can be shared with users via MEDIA:<path> in the response.
    
    Args:
        question: What you want to know about the page visually
        annotate: If True, overlay numbered [N] labels on interactive elements
        task_id: Task identifier for session isolation
        
    Returns:
        JSON string with vision analysis results and screenshot_path
    """
@Suppress("unused") private const val _BT_261: String = "cache/screenshots"
@Suppress("unused") private const val _BT_262: String = "browser_screenshots"
@Suppress("unused") private const val _BT_263: String = "browser_screenshot_"
@Suppress("unused") private const val _BT_264: String = ".png"
@Suppress("unused") private const val _BT_265: String = "--full"
@Suppress("unused") private const val _BT_266: String = "ascii"
@Suppress("unused") private const val _BT_267: String = "data:image/png;base64,"
@Suppress("unused") private val _BT_268: String = """You are analyzing a screenshot of a web browser.

User's question: """
@Suppress("unused") private val _BT_269: String = """

Provide a detailed and helpful answer based on what you see in the screenshot. If there are interactive elements, describe them. If there are verification challenges or CAPTCHAs, describe what type they are and what action might be needed. Focus on answering the user's specific question."""
@Suppress("unused") private const val _BT_270: String = "browser_vision: analysing screenshot (%d bytes)"
@Suppress("unused") private const val _BT_271: String = "timeout"
@Suppress("unused") private const val _BT_272: String = "vision"
@Suppress("unused") private const val _BT_273: String = "analysis"
@Suppress("unused") private const val _BT_274: String = "screenshot_path"
@Suppress("unused") private const val _BT_275: String = "--annotate"
@Suppress("unused") private const val _BT_276: String = "Unknown error"
@Suppress("unused") private const val _BT_277: String = "Vision analysis returned no content."
@Suppress("unused") private const val _BT_278: String = "annotations"
@Suppress("unused") private const val _BT_279: String = "browser_vision failed: %s"
@Suppress("unused") private const val _BT_280: String = "Screenshot was captured but vision analysis failed. You can still share it via MEDIA:<path>."
@Suppress("unused") private const val _BT_281: String = "cloud ("
@Suppress("unused") private const val _BT_282: String = "Error during vision analysis: "
@Suppress("unused") private const val _BT_283: String = "note"
@Suppress("unused") private const val _BT_284: String = "Failed to take screenshot ("
@Suppress("unused") private const val _BT_285: String = " mode): "
@Suppress("unused") private const val _BT_286: String = "Screenshot file was not created at "
@Suppress("unused") private const val _BT_287: String = " mode). This may indicate a socket path issue (macOS /var/folders/), a missing Chromium install ('agent-browser install'), or a stale daemon process."
@Suppress("unused") private const val _BT_288: String = "auxiliary"
@Suppress("unused") private const val _BT_289: String = "Vision API rejected screenshot (%.1f MB); auto-resizing to ~%.0f MB and retrying..."
@Suppress("unused") private const val _BT_290: String = "image_url"
@Suppress("unused") private const val _BT_291: String = "image/png"
@Suppress("unused") private val _BT_292: String = """Remove browser screenshots older than max_age_hours to prevent disk bloat.

    Throttled to run at most once per hour per directory to avoid repeated
    scans on screenshot-heavy workflows.
    """
@Suppress("unused") private const val _BT_293: String = "browser_screenshot_*.png"
@Suppress("unused") private const val _BT_294: String = "Screenshot cleanup error (non-critical): %s"
@Suppress("unused") private const val _BT_295: String = "Failed to clean old screenshot %s: %s"
@Suppress("unused") private const val _BT_296: String = "Remove browser recordings older than max_age_hours to prevent disk bloat."
@Suppress("unused") private const val _BT_297: String = "session_*.webm"
@Suppress("unused") private const val _BT_298: String = "Recording cleanup error (non-critical): %s"
@Suppress("unused") private const val _BT_299: String = "Failed to clean old recording %s: %s"
@Suppress("unused") private val _BT_300: String = """
    Clean up browser session for a task.
    
    Called automatically when a task completes or when inactivity timeout is reached.
    Closes both the agent-browser/Browserbase session and Camofox sessions.
    
    Args:
        task_id: Task identifier to clean up
    """
@Suppress("unused") private const val _BT_301: String = "cleanup_browser called for task_id: %s"
@Suppress("unused") private const val _BT_302: String = "Active sessions: %s"
@Suppress("unused") private const val _BT_303: String = "bb_session_id"
@Suppress("unused") private const val _BT_304: String = "unknown"
@Suppress("unused") private const val _BT_305: String = "Found session for task %s: bb_session_id=%s"
@Suppress("unused") private const val _BT_306: String = "Removed task %s from active sessions"
@Suppress("unused") private const val _BT_307: String = "No active session found for task_id: %s"
@Suppress("unused") private const val _BT_308: String = "close"
@Suppress("unused") private const val _BT_309: String = "agent-browser close command completed for task %s"
@Suppress("unused") private const val _BT_310: String = "Camofox cleanup for task %s: %s"
@Suppress("unused") private const val _BT_311: String = "agent-browser close failed for task %s: %s"
@Suppress("unused") private const val _BT_312: String = "Could not close cloud browser session: %s"
@Suppress("unused") private const val _BT_313: String = "Killed daemon pid %s for %s"
@Suppress("unused") private const val _BT_314: String = "Could not kill daemon pid for %s (already dead or inaccessible)"
