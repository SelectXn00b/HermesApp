/**
 * Tirith pre-exec security scanning wrapper.
 *
 * Python spawns a tirith subprocess to scan commands; Android has no
 * tirith binary and no cosign, so the top-level surface is stubbed
 * (fail-open) while keeping the Python shape. Registration stays
 * aligned with tools/tirith_security.py.
 *
 * Ported from tools/tirith_security.py
 */
package com.xiaomo.hermes.hermes.tools

private const val _REPO: String = "sheeki03/tirith"
private val _COSIGN_IDENTITY_REGEXP: String =
    "^https://github.com/$_REPO/\\.github/workflows/release\\.yml@refs/tags/v"
private const val _COSIGN_ISSUER: String = "https://token.actions.githubusercontent.com"

private const val _MARKER_TTL: Long = 86400L

private const val _MAX_FINDINGS: Int = 50
private const val _MAX_SUMMARY_LEN: Int = 500

@Volatile private var _resolvedPath: String? = null
@Volatile private var _installFailed: Boolean = false
@Volatile private var _installFailureReason: String = ""
private val _installLock = Any()
@Volatile private var _installThread: Thread? = null

private fun _envBool(key: String, default: Boolean): Boolean {
    val v = System.getenv(key) ?: return default
    return v.lowercase() in setOf("1", "true", "yes")
}

private fun _envInt(key: String, default: Int): Int {
    val v = System.getenv(key) ?: return default
    return v.toIntOrNull() ?: default
}

private fun _loadSecurityConfig(): Map<String, Any?> = mapOf(
    "tirith_enabled" to _envBool("TIRITH_ENABLED", true),
    "tirith_path" to (System.getenv("TIRITH_BIN") ?: "tirith"),
    "tirith_timeout" to _envInt("TIRITH_TIMEOUT", 5),
    "tirith_fail_open" to _envBool("TIRITH_FAIL_OPEN", true),
)

private fun _getHermesHome(): String = System.getProperty("java.io.tmpdir") ?: "/tmp"

private fun _failureMarkerPath(): String = java.io.File(_getHermesHome(), ".tirith-install-failed").absolutePath

private fun _readFailureReason(): String? = null

private fun _isInstallFailedOnDisk(): Boolean = false

private fun _markInstallFailed(reason: String = ""): Unit = Unit

private fun _clearInstallFailed(): Unit = Unit

private fun _hermesBinDir(): String = java.io.File(_getHermesHome(), "bin").absolutePath

private fun _detectTarget(): String? = null

private fun _downloadFile(url: String, dest: String, timeout: Int = 10): Unit = Unit

private fun _verifyCosign(checksumsPath: String, sigPath: String, certPath: String): Boolean? = null

private fun _verifyChecksum(archivePath: String, checksumsPath: String, archiveName: String): Boolean = false

private fun _installTirith(logFailures: Boolean = true): Pair<String?, String> =
    null to "unsupported_platform"

private fun _isExplicitPath(configuredPath: String): Boolean = configuredPath != "tirith"

private fun _resolveTirithPath(configuredPath: String): String = configuredPath

private fun _backgroundInstall(logFailures: Boolean = true): Unit = Unit

fun ensureInstalled(logFailures: Boolean = true): String? {
    val cfg = _loadSecurityConfig()
    if (cfg["tirith_enabled"] != true) return null
    return null
}

fun checkCommandSecurity(command: String): Map<String, Any?> {
    val cfg = _loadSecurityConfig()
    if (cfg["tirith_enabled"] != true) {
        return mapOf("action" to "allow", "findings" to emptyList<Any?>(), "summary" to "")
    }
    val failOpen = cfg["tirith_fail_open"] as? Boolean ?: true
    return if (failOpen) {
        mapOf("action" to "allow", "findings" to emptyList<Any?>(), "summary" to "tirith unavailable on Android")
    } else {
        mapOf("action" to "block", "findings" to emptyList<Any?>(), "summary" to "tirith spawn failed (fail-closed): unavailable on Android")
    }
}

/** Python `_INSTALL_FAILED` — in-memory flag that the installer gave up. */
private val _INSTALL_FAILED: Boolean = false
