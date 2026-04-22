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
suspend fun camofoxSoftCleanup(): String =
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

suspend fun camofoxSnapshot(sessionId: String? = null): String =
    toolError("Camofox is not available on Android")

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
    amount: Int = 1,
    sessionId: String? = null
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
    sessionId: String? = null,
    limit: Int = 10
): String = toolError("Camofox is not available on Android")

suspend fun camofoxVision(
    prompt: String,
    sessionId: String? = null
): String = toolError("Camofox is not available on Android")

suspend fun camofoxConsole(sessionId: String? = null): String =
    toolError("Camofox is not available on Android")
