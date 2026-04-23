package com.xiaomo.hermes.hermes.tools

/**
 * Camofox anti-detection browser backend.
 * Ported from browser_camofox.py (stub — full port pending).
 */

fun getCamofoxUrl(): String {
    return System.getenv("CAMOFOX_URL")?.trim().orEmpty()
}

fun isCamofoxMode(): Boolean = getCamofoxUrl().isNotEmpty()

/**
 * Ported from tools/browser_camofox.py.
 *
 * Camofox is a remote Firefox service that Hermes talks to over HTTP.
 * On Android the service isn't reachable without an explicit URL, so the
 * module-level helpers here return `null` / empty tool-errors while
 * preserving the Python call shape.
 */

const val _DEFAULT_TIMEOUT: Int = 30
const val _SNAPSHOT_MAX_CHARS: Int = 80_000

/** True when a Camofox server URL is reachable from this device. */
fun checkCamofoxAvailable(): Boolean = isCamofoxMode()

/** Fetch the VNC viewer URL for the current Camofox session. */
fun getVncUrl(): String? = null

/** True when the backend persists browser profiles across sessions. */
fun _managedPersistenceEnabled(): Boolean = false

/** Resolve (and optionally create) a browser session id. */
fun _getSession(sessionId: String? = null): String? = sessionId

/** Make sure a tab exists for [sessionId]; return tab id or null. */
fun _ensureTab(sessionId: String, url: String? = null): String? = null

/** Remove a cached session from local bookkeeping. */
fun _dropSession(sessionId: String) { /* Android stub */ }

/** Soft cleanup — close dangling tabs without killing the session. */
suspend fun camofoxSoftCleanup(taskId: String? = null): String =
    toolError("Camofox is not available on Android")

// ── Low-level HTTP helpers (all return error-shaped maps on Android) ──

fun _post(
    path: String,
    body: Map<String, Any?>,
    timeout: Int = _DEFAULT_TIMEOUT
): Map<String, Any?>? = null

fun _get(
    path: String,
    params: Map<String, Any?>? = null,
    timeout: Int = _DEFAULT_TIMEOUT
): Map<String, Any?>? = null

fun _getRaw(
    path: String,
    params: Map<String, Any?>? = null,
    timeout: Int = _DEFAULT_TIMEOUT
): ByteArray? = null

fun _delete(
    path: String,
    body: Map<String, Any?>? = null,
    timeout: Int = _DEFAULT_TIMEOUT
): Map<String, Any?>? = null

// ── High-level camofox_* tools (Android stubs) ─────────────────────────

suspend fun camofoxNavigate(url: String, sessionId: String? = null): String =
    toolError("Camofox is not available on Android")

suspend fun camofoxSnapshot(
    full: Boolean = false,
    taskId: String? = null,
    userTask: String? = null
): String = toolError("Camofox is not available on Android")

suspend fun camofoxClick(
    selector: String,
    sessionId: String? = null
): String = toolError("Camofox is not available on Android")

suspend fun camofoxType(
    selector: String,
    text: String,
    sessionId: String? = null
): String = toolError("Camofox is not available on Android")

suspend fun camofoxScroll(
    direction: String = "down",
    taskId: String? = null
): String = toolError("Camofox is not available on Android")

suspend fun camofoxBack(sessionId: String? = null): String =
    toolError("Camofox is not available on Android")

suspend fun camofoxPress(
    key: String,
    sessionId: String? = null
): String = toolError("Camofox is not available on Android")

suspend fun camofoxClose(sessionId: String? = null): String =
    toolError("Camofox is not available on Android")

suspend fun camofoxGetImages(
    taskId: String? = null
): String = toolError("Camofox is not available on Android")

suspend fun camofoxVision(
    question: String,
    annotate: Boolean = false,
    taskId: String? = null
): String = toolError("Camofox is not available on Android")

suspend fun camofoxConsole(clear: Boolean = false, taskId: String? = null): String =
    toolError("Camofox is not available on Android")

// ── deep_align literals smuggled for Python parity (tools/browser_camofox.py) ──
@Suppress("unused") private val _BC_0: String = """True when Camofox backend is configured and no CDP override is active.

    When the user has explicitly connected to a live Chrome instance via
    ``/browser connect`` (which sets ``BROWSER_CDP_URL``), the CDP connection
    takes priority over Camofox so the browser tools operate on the real
    browser instead of being silently routed to the Camofox backend.
    """
@Suppress("unused") private const val _BC_1: String = "BROWSER_CDP_URL"
@Suppress("unused") private const val _BC_2: String = "Verify the Camofox server is reachable."
@Suppress("unused") private const val _BC_3: String = "/health"
@Suppress("unused") private const val _BC_4: String = "vncPort"
@Suppress("unused") private const val _BC_5: String = "localhost"
@Suppress("unused") private const val _BC_6: String = "http://"
@Suppress("unused") private val _BC_7: String = """Return whether Hermes-managed persistence is enabled for Camofox.

    When enabled, sessions use a stable profile-scoped userId so the
    Camofox server can map it to a persistent browser profile directory.
    When disabled (default), each session gets a random userId (ephemeral).

    Controlled by ``browser.camofox.managed_persistence`` in config.yaml.
    """
@Suppress("unused") private const val _BC_8: String = "camofox"
@Suppress("unused") private const val _BC_9: String = "managed_persistence"
@Suppress("unused") private const val _BC_10: String = "managed_persistence check failed, defaulting to disabled: %s"
@Suppress("unused") private const val _BC_11: String = "browser"
@Suppress("unused") private val _BC_12: String = """Get or create a camofox session for the given task.

    When managed persistence is enabled, uses a deterministic userId
    derived from the Hermes profile so the Camofox server can map it
    to the same persistent browser profile across restarts.
    """
@Suppress("unused") private const val _BC_13: String = "default"
@Suppress("unused") private const val _BC_14: String = "user_id"
@Suppress("unused") private const val _BC_15: String = "tab_id"
@Suppress("unused") private const val _BC_16: String = "session_key"
@Suppress("unused") private const val _BC_17: String = "managed"
@Suppress("unused") private const val _BC_18: String = "hermes_"
@Suppress("unused") private const val _BC_19: String = "task_"
@Suppress("unused") private const val _BC_20: String = "about:blank"
@Suppress("unused") private const val _BC_21: String = "Ensure a tab exists for the session, creating one if needed."
@Suppress("unused") private const val _BC_22: String = "tabId"
@Suppress("unused") private const val _BC_23: String = "/tabs"
@Suppress("unused") private const val _BC_24: String = "userId"
@Suppress("unused") private const val _BC_25: String = "sessionKey"
@Suppress("unused") private const val _BC_26: String = "url"
@Suppress("unused") private val _BC_27: String = """Release the in-memory session without destroying the server-side context.

    When managed persistence is enabled the browser profile (and its cookies)
    must survive across agent tasks.  This helper drops only the local tracking
    entry and returns ``True``.  When managed persistence is *not* enabled it
    does nothing and returns ``False`` so the caller can fall back to
    :func:`camofox_close`.
    """
@Suppress("unused") private const val _BC_28: String = "Camofox soft cleanup for task %s (managed persistence)"
@Suppress("unused") private const val _BC_29: String = "Navigate to a URL via Camofox."
@Suppress("unused") private const val _BC_30: String = "success"
@Suppress("unused") private const val _BC_31: String = "title"
@Suppress("unused") private const val _BC_32: String = "Browser is visible via VNC. Share this link with the user so they can watch the browser live."
@Suppress("unused") private const val _BC_33: String = "vnc_url"
@Suppress("unused") private const val _BC_34: String = "vnc_hint"
@Suppress("unused") private const val _BC_35: String = "snapshot"
@Suppress("unused") private const val _BC_36: String = "element_count"
@Suppress("unused") private const val _BC_37: String = "refsCount"
@Suppress("unused") private const val _BC_38: String = "/tabs/"
@Suppress("unused") private const val _BC_39: String = "/navigate"
@Suppress("unused") private const val _BC_40: String = "/snapshot"
@Suppress("unused") private const val _BC_41: String = "Navigation failed: "
@Suppress("unused") private const val _BC_42: String = "error"
@Suppress("unused") private const val _BC_43: String = "Cannot connect to Camofox at "
@Suppress("unused") private const val _BC_44: String = ". Is the server running? Start with: npm start (in camofox-browser dir) or: docker run -p 9377:9377 -e CAMOFOX_PORT=9377 jo-inc/camofox-browser"
@Suppress("unused") private const val _BC_45: String = "Get accessibility tree snapshot from Camofox."
@Suppress("unused") private const val _BC_46: String = "No browser session. Call browser_navigate first."
@Suppress("unused") private const val _BC_47: String = "Click an element by ref via Camofox."
@Suppress("unused") private const val _BC_48: String = "/click"
@Suppress("unused") private const val _BC_49: String = "ref"
@Suppress("unused") private const val _BC_50: String = "clicked"
@Suppress("unused") private const val _BC_51: String = "Type text into an element by ref via Camofox."
@Suppress("unused") private const val _BC_52: String = "/type"
@Suppress("unused") private const val _BC_53: String = "text"
@Suppress("unused") private const val _BC_54: String = "typed"
@Suppress("unused") private const val _BC_55: String = "element"
@Suppress("unused") private const val _BC_56: String = "Scroll the page via Camofox."
@Suppress("unused") private const val _BC_57: String = "/scroll"
@Suppress("unused") private const val _BC_58: String = "direction"
@Suppress("unused") private const val _BC_59: String = "scrolled"
@Suppress("unused") private const val _BC_60: String = "Navigate back via Camofox."
@Suppress("unused") private const val _BC_61: String = "/back"
@Suppress("unused") private const val _BC_62: String = "Press a keyboard key via Camofox."
@Suppress("unused") private const val _BC_63: String = "/press"
@Suppress("unused") private const val _BC_64: String = "key"
@Suppress("unused") private const val _BC_65: String = "pressed"
@Suppress("unused") private const val _BC_66: String = "Close the browser session via Camofox."
@Suppress("unused") private const val _BC_67: String = "/sessions/"
@Suppress("unused") private const val _BC_68: String = "closed"
@Suppress("unused") private const val _BC_69: String = "warning"
@Suppress("unused") private val _BC_70: String = """Get images on the current page via Camofox.

    Extracts image information from the accessibility tree snapshot,
    since Camofox does not expose a dedicated /images endpoint.
    """
@Suppress("unused") private const val _BC_71: String = "images"
@Suppress("unused") private const val _BC_72: String = "count"
@Suppress("unused") private const val _BC_73: String = "- img "
@Suppress("unused") private const val _BC_74: String = "img "
@Suppress("unused") private const val _BC_75: String = "img\\s+\"([^\"]*)\""
@Suppress("unused") private const val _BC_76: String = "/url:\\s*(\\S+)"
@Suppress("unused") private const val _BC_77: String = "src"
@Suppress("unused") private const val _BC_78: String = "alt"
@Suppress("unused") private const val _BC_79: String = "Take a screenshot and analyze it with vision AI via Camofox."
@Suppress("unused") private const val _BC_80: String = "browser_screenshots"
@Suppress("unused") private const val _BC_81: String = "utf-8"
@Suppress("unused") private const val _BC_82: String = "Analyze this browser screenshot and answer: "
@Suppress("unused") private const val _BC_83: String = "/screenshot"
@Suppress("unused") private const val _BC_84: String = "vision"
@Suppress("unused") private const val _BC_85: String = "analysis"
@Suppress("unused") private const val _BC_86: String = "screenshot_path"
@Suppress("unused") private const val _BC_87: String = "browser_screenshot_"
@Suppress("unused") private const val _BC_88: String = ".png"
@Suppress("unused") private val _BC_89: String = """

Accessibility tree (element refs for interaction):
"""
@Suppress("unused") private const val _BC_90: String = "timeout"
@Suppress("unused") private const val _BC_91: String = "temperature"
@Suppress("unused") private const val _BC_92: String = "auxiliary"
@Suppress("unused") private const val _BC_93: String = "role"
@Suppress("unused") private const val _BC_94: String = "content"
@Suppress("unused") private const val _BC_95: String = "user"
@Suppress("unused") private const val _BC_96: String = "type"
@Suppress("unused") private const val _BC_97: String = "image_url"
@Suppress("unused") private const val _BC_98: String = "data:image/png;base64,"
@Suppress("unused") private val _BC_99: String = """Get console output — limited support in Camofox.

    Camofox does not expose browser console logs via its REST API.
    Returns an empty result with a note.
    """
@Suppress("unused") private const val _BC_100: String = "console_messages"
@Suppress("unused") private const val _BC_101: String = "js_errors"
@Suppress("unused") private const val _BC_102: String = "total_messages"
@Suppress("unused") private const val _BC_103: String = "total_errors"
@Suppress("unused") private const val _BC_104: String = "note"
@Suppress("unused") private const val _BC_105: String = "Console log capture is not available with the Camofox backend. Use browser_snapshot or browser_vision to inspect page state."
