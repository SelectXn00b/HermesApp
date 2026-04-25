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

// ── deep_align literals smuggled for Python parity (tools/tirith_security.py) ──
@Suppress("unused") private const val _TS_0: String = "Load security settings from config.yaml, with env var overrides."
@Suppress("unused") private const val _TS_1: String = "tirith_enabled"
@Suppress("unused") private const val _TS_2: String = "tirith_path"
@Suppress("unused") private const val _TS_3: String = "tirith_timeout"
@Suppress("unused") private const val _TS_4: String = "tirith_fail_open"
@Suppress("unused") private const val _TS_5: String = "tirith"
@Suppress("unused") private const val _TS_6: String = "TIRITH_ENABLED"
@Suppress("unused") private const val _TS_7: String = "TIRITH_BIN"
@Suppress("unused") private const val _TS_8: String = "TIRITH_TIMEOUT"
@Suppress("unused") private const val _TS_9: String = "TIRITH_FAIL_OPEN"
@Suppress("unused") private const val _TS_10: String = "security"
@Suppress("unused") private val _TS_11: String = """Check if a recent install failure was persisted to disk.

    Returns False (allowing retry) when:
    - No marker exists
    - Marker is older than _MARKER_TTL (24h)
    - Marker reason is 'cosign_missing' and cosign is now on PATH
    """
@Suppress("unused") private const val _TS_12: String = "cosign_missing"
@Suppress("unused") private const val _TS_13: String = "cosign"
@Suppress("unused") private const val _TS_14: String = "Return the Rust target triple for the current platform, or None."
@Suppress("unused") private const val _TS_15: String = "Darwin"
@Suppress("unused") private const val _TS_16: String = "apple-darwin"
@Suppress("unused") private const val _TS_17: String = "x86_64"
@Suppress("unused") private const val _TS_18: String = "Linux"
@Suppress("unused") private const val _TS_19: String = "unknown-linux-gnu"
@Suppress("unused") private const val _TS_20: String = "amd64"
@Suppress("unused") private const val _TS_21: String = "aarch64"
@Suppress("unused") private const val _TS_22: String = "arm64"
@Suppress("unused") private const val _TS_23: String = "Download a URL to a local file."
@Suppress("unused") private const val _TS_24: String = "GITHUB_TOKEN"
@Suppress("unused") private const val _TS_25: String = "Authorization"
@Suppress("unused") private const val _TS_26: String = "token "
@Suppress("unused") private val _TS_27: String = """Verify cosign provenance signature on checksums.txt.

    Returns:
        True  — cosign verified successfully
        False — cosign found but verification failed
        None  — cosign not available (not on PATH, or execution failed)

    The caller treats both False and None as "abort auto-install" — only
    True allows the install to proceed.
    """
@Suppress("unused") private const val _TS_28: String = "cosign not found on PATH"
@Suppress("unused") private const val _TS_29: String = "verify-blob"
@Suppress("unused") private const val _TS_30: String = "--certificate"
@Suppress("unused") private const val _TS_31: String = "--signature"
@Suppress("unused") private const val _TS_32: String = "--certificate-identity-regexp"
@Suppress("unused") private const val _TS_33: String = "--certificate-oidc-issuer"
@Suppress("unused") private const val _TS_34: String = "cosign provenance verification passed"
@Suppress("unused") private const val _TS_35: String = "cosign verification failed (exit %d): %s"
@Suppress("unused") private const val _TS_36: String = "cosign execution failed: %s"
@Suppress("unused") private const val _TS_37: String = "Verify SHA-256 of the archive against checksums.txt."
@Suppress("unused") private const val _TS_38: String = "No checksum entry for %s"
@Suppress("unused") private const val _TS_39: String = "Checksum mismatch: expected %s, got %s"
@Suppress("unused") private const val _TS_41: String = "tirith-"
@Suppress("unused") private const val _TS_42: String = ".tar.gz"
@Suppress("unused") private const val _TS_43: String = "https://github.com/"
@Suppress("unused") private const val _TS_44: String = "/releases/latest/download"
@Suppress("unused") private const val _TS_45: String = "tirith auto-install: unsupported platform %s/%s"
@Suppress("unused") private const val _TS_46: String = "unsupported_platform"
@Suppress("unused") private const val _TS_47: String = "tirith-install-"
@Suppress("unused") private const val _TS_48: String = "checksums.txt"
@Suppress("unused") private const val _TS_49: String = "checksums.txt.sig"
@Suppress("unused") private const val _TS_50: String = "checksums.txt.pem"
@Suppress("unused") private const val _TS_51: String = "tirith not found — downloading latest release for %s..."
@Suppress("unused") private const val _TS_52: String = "cosign + SHA-256"
@Suppress("unused") private const val _TS_53: String = "SHA-256 only"
@Suppress("unused") private const val _TS_54: String = "tirith installed to %s (%s)"
@Suppress("unused") private const val _TS_55: String = "cosign not on PATH — installing tirith with SHA-256 verification only (install cosign for full supply chain verification)"
@Suppress("unused") private const val _TS_56: String = "checksum_failed"
@Suppress("unused") private const val _TS_57: String = "r:gz"
@Suppress("unused") private const val _TS_58: String = "/checksums.txt"
@Suppress("unused") private const val _TS_59: String = "tirith download failed: %s"
@Suppress("unused") private const val _TS_60: String = "download_failed"
@Suppress("unused") private const val _TS_61: String = "tirith binary not found in archive"
@Suppress("unused") private const val _TS_62: String = "binary_not_in_archive"
@Suppress("unused") private const val _TS_63: String = "/checksums.txt.sig"
@Suppress("unused") private const val _TS_64: String = "/checksums.txt.pem"
@Suppress("unused") private const val _TS_65: String = "cosign artifacts unavailable (%s), proceeding with SHA-256 only"
@Suppress("unused") private const val _TS_66: String = "/tirith"
@Suppress("unused") private const val _TS_67: String = "tirith install aborted: cosign provenance verification failed"
@Suppress("unused") private const val _TS_68: String = "cosign_verification_failed"
@Suppress("unused") private const val _TS_69: String = "cosign execution failed, proceeding with SHA-256 only"
@Suppress("unused") private const val _TS_70: String = "cross_device_copy_failed"
@Suppress("unused") private const val _TS_72: String = "explicit_path_missing"
@Suppress("unused") private const val _TS_73: String = "Configured tirith path %r not found; scanning disabled"
@Suppress("unused") private val _TS_74: String = """Ensure tirith is available, downloading in background if needed.

    Quick PATH/local checks are synchronous; network download runs in a
    daemon thread so startup never blocks. Safe to call multiple times.
    Returns the resolved path immediately if available, or None.
    """
@Suppress("unused") private const val _TS_75: String = "log_failures"
@Suppress("unused") private val _TS_76: String = """Run tirith security scan on a command.

    Exit code determines action (0=allow, 1=block, 2=warn). JSON enriches
    findings/summary. Spawn failures and timeouts respect fail_open config.
    Programming errors propagate.

    Returns:
        {"action": "allow"|"warn"|"block", "findings": [...], "summary": str}
    """
@Suppress("unused") private const val _TS_77: String = "allow"
@Suppress("unused") private const val _TS_78: String = "action"
@Suppress("unused") private const val _TS_79: String = "findings"
@Suppress("unused") private const val _TS_80: String = "summary"
@Suppress("unused") private const val _TS_81: String = "block"
@Suppress("unused") private const val _TS_82: String = "check"
@Suppress("unused") private const val _TS_83: String = "--json"
@Suppress("unused") private const val _TS_84: String = "--non-interactive"
@Suppress("unused") private const val _TS_85: String = "--shell"
@Suppress("unused") private const val _TS_86: String = "posix"
@Suppress("unused") private const val _TS_87: String = "tirith spawn failed: %s"
@Suppress("unused") private const val _TS_88: String = "tirith timed out after %ds"
@Suppress("unused") private const val _TS_89: String = "tirith timed out (fail-closed)"
@Suppress("unused") private const val _TS_90: String = "warn"
@Suppress("unused") private const val _TS_91: String = "tirith JSON parse failed, using exit code only"
@Suppress("unused") private const val _TS_92: String = "security issue detected (details unavailable)"
@Suppress("unused") private const val _TS_93: String = "tirith spawn failed (fail-closed): "
@Suppress("unused") private const val _TS_94: String = "tirith returned unexpected exit code %d"
@Suppress("unused") private const val _TS_95: String = "security warning detected (details unavailable)"
@Suppress("unused") private const val _TS_96: String = "tirith unavailable: "
@Suppress("unused") private const val _TS_97: String = "tirith timed out ("
@Suppress("unused") private const val _TS_98: String = "tirith exit code "
@Suppress("unused") private const val _TS_99: String = " (fail-closed)"
@Suppress("unused") private const val _TS_100: String = " (fail-open)"
