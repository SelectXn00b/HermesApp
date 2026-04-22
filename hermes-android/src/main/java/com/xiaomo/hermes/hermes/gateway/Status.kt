package com.xiaomo.hermes.hermes.gateway

/**
 * Gateway runtime status helpers.
 *
 * Provides PID-file based detection of whether the gateway daemon is
 * running, used by send_message's check_fn to gate availability in the
 * CLI.
 *
 * The PID file lives at `{HERMES_HOME}/gateway.pid`. HERMES_HOME defaults
 * to `~/.hermes` but can be overridden via the environment variable.
 *
 * Ported from gateway/status.py
 */

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val _TAG = "gateway.status"

const val _GATEWAY_KIND = "hermes-gateway"
const val _RUNTIME_STATUS_FILE = "gateway_state.json"
const val _LOCKS_DIRNAME = "gateway-locks"
val _IS_WINDOWS: Boolean = (System.getProperty("os.name") ?: "").lowercase().startsWith("windows")
private val _UNSET: Any = Any()

private fun _hermesHome(): File {
    val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
    return if (envVal.isNotEmpty()) File(envVal).canonicalFile
    else File(System.getProperty("user.home") ?: "/", ".hermes").canonicalFile
}

fun _getPidPath(): File = File(_hermesHome(), "gateway.pid")

fun _getRuntimeStatusPath(): File = File(_getPidPath().parentFile, _RUNTIME_STATUS_FILE)

fun _getLockDir(): File {
    val override = System.getenv("HERMES_GATEWAY_LOCK_DIR")
    if (!override.isNullOrEmpty()) return File(override)
    val stateHome = System.getenv("XDG_STATE_HOME") ?: (System.getProperty("user.home") ?: "/") + "/.local/state"
    return File(File(stateHome, "hermes"), _LOCKS_DIRNAME)
}

fun _utcNowIso(): String = OffsetDateTime.now(ZoneOffset.UTC).toString()

/**
 * Terminate a PID with platform-appropriate force semantics.
 *
 * Android has no direct `os.kill` equivalent exposed to apps — this
 * remains a TODO stub.
 */
@Suppress("UNUSED_PARAMETER")
fun terminatePid(pid: Int, force: Boolean = false) {
    // TODO: port os.kill / taskkill — Android apps cannot kill other processes.
    throw UnsupportedOperationException("terminatePid is not supported on Android")
}

fun _scopeHash(identity: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(identity.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
}

fun _getScopeLockPath(scope: String, identity: String): File =
    File(_getLockDir(), "$scope-${_scopeHash(identity)}.lock")

/** Return the kernel start time for a process when available. */
fun _getProcessStartTime(pid: Int): Long? {
    val statPath = File("/proc/$pid/stat")
    return try {
        val parts = statPath.readText().split(" ")
        parts[21].toLong()
    } catch (_: Exception) {
        null
    }
}

fun _readProcessCmdline(pid: Int): String? {
    val cmdlinePath = File("/proc/$pid/cmdline")
    return try {
        val raw = cmdlinePath.readBytes()
        if (raw.isEmpty()) null
        else raw.toString(Charsets.UTF_8).replace('\u0000', ' ').trim()
    } catch (_: Exception) {
        null
    }
}

private val _GATEWAY_PATTERNS = listOf(
    "hermes_cli.main gateway",
    "hermes_cli/main.py gateway",
    "hermes gateway",
    "gateway/run.py")

fun _looksLikeGatewayProcess(pid: Int): Boolean {
    val cmdline = _readProcessCmdline(pid) ?: return false
    return _GATEWAY_PATTERNS.any { it in cmdline }
}

@Suppress("UNCHECKED_CAST")
fun _recordLooksLikeGateway(record: Map<String, Any?>): Boolean {
    if (record["kind"] != _GATEWAY_KIND) return false
    val argv = record["argv"] as? List<Any?> ?: return false
    if (argv.isEmpty()) return false
    val cmdline = argv.joinToString(" ") { it.toString() }
    return _GATEWAY_PATTERNS.any { it in cmdline }
}

fun _buildPidRecord(): MutableMap<String, Any?> {
    val pid = android.os.Process.myPid()
    return mutableMapOf(
        "pid" to pid,
        "kind" to _GATEWAY_KIND,
        "argv" to emptyList<String>(),  // TODO: port sys.argv
        "start_time" to _getProcessStartTime(pid))
}

fun _buildRuntimeStatusRecord(): MutableMap<String, Any?> {
    val payload = _buildPidRecord()
    payload["gateway_state"] = "starting"
    payload["exit_reason"] = null
    payload["restart_requested"] = false
    payload["active_agents"] = 0
    payload["platforms"] = mutableMapOf<String, Any?>()
    payload["updated_at"] = _utcNowIso()
    return payload
}

fun _readJsonFile(path: File): MutableMap<String, Any?>? {
    if (!path.exists()) return null
    return try {
        val raw = path.readText().trim()
        if (raw.isEmpty()) null
        else _jsonObjectToMap(JSONObject(raw))
    } catch (_: Exception) {
        null
    }
}

fun _writeJsonFile(path: File, payload: Map<String, Any?>) {
    path.parentFile?.mkdirs()
    path.writeText(JSONObject(payload).toString())
}

fun _readPidRecord(pidPath: File? = null): MutableMap<String, Any?>? {
    val resolved = pidPath ?: _getPidPath()
    if (!resolved.exists()) return null
    val raw = resolved.readText().trim()
    if (raw.isEmpty()) return null

    return try {
        val obj = JSONObject(raw)
        _jsonObjectToMap(obj)
    } catch (_: Exception) {
        try {
            mutableMapOf("pid" to raw.toInt())
        } catch (_: NumberFormatException) {
            null
        }
    }
}

fun _cleanupInvalidPidPath(pidPath: File, cleanupStale: Boolean) {
    if (!cleanupStale) return
    try {
        if (pidPath == _getPidPath()) {
            removePidFile()
        } else {
            pidPath.delete()
        }
    } catch (_: Exception) {
    }
}

/**
 * Write the current process PID and metadata to the gateway PID file.
 *
 * Uses atomic `createNewFile()` (Java equivalent of O_CREAT | O_EXCL) so
 * that concurrent --replace invocations race: exactly one process wins
 * and the rest see `createNewFile() == false`.
 */
fun writePidFile() {
    val path = _getPidPath()
    path.parentFile?.mkdirs()
    val record = JSONObject(_buildPidRecord()).toString()
    if (!path.createNewFile()) throw RuntimeException("PID file already exists: ${path.absolutePath}")
    try {
        path.writeText(record)
    } catch (exc: Exception) {
        try { path.delete() } catch (_: Exception) {}
        throw exc
    }
}

/** Persist gateway runtime health information for diagnostics/status. */
@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
fun writeRuntimeStatus(
    gatewayState: Any? = _UNSET,
    exitReason: Any? = _UNSET,
    restartRequested: Any? = _UNSET,
    activeAgents: Any? = _UNSET,
    platform: Any? = _UNSET,
    platformState: Any? = _UNSET,
    errorCode: Any? = _UNSET,
    errorMessage: Any? = _UNSET) {
    val path = _getRuntimeStatusPath()
    val payload = _readJsonFile(path) ?: _buildRuntimeStatusRecord()
    if ("platforms" !in payload) payload["platforms"] = mutableMapOf<String, Any?>()
    if ("kind" !in payload) payload["kind"] = _GATEWAY_KIND
    val pid = android.os.Process.myPid()
    payload["pid"] = pid
    payload["start_time"] = _getProcessStartTime(pid)
    payload["updated_at"] = _utcNowIso()

    if (gatewayState !== _UNSET) payload["gateway_state"] = gatewayState
    if (exitReason !== _UNSET) payload["exit_reason"] = exitReason
    if (restartRequested !== _UNSET) payload["restart_requested"] = restartRequested == true
    if (activeAgents !== _UNSET) {
        payload["active_agents"] = maxOf(0, (activeAgents as? Number)?.toInt() ?: 0)
    }

    if (platform !== _UNSET) {
        val platforms = payload["platforms"] as? MutableMap<String, Any?> ?: mutableMapOf()
        val platformKey = platform.toString()
        val platformPayload = (platforms[platformKey] as? MutableMap<String, Any?>) ?: mutableMapOf()
        if (platformState !== _UNSET) platformPayload["state"] = platformState
        if (errorCode !== _UNSET) platformPayload["error_code"] = errorCode
        if (errorMessage !== _UNSET) platformPayload["error_message"] = errorMessage
        platformPayload["updated_at"] = _utcNowIso()
        platforms[platformKey] = platformPayload
        payload["platforms"] = platforms
    }

    _writeJsonFile(path, payload)
}

fun readRuntimeStatus(): Map<String, Any?>? = _readJsonFile(_getRuntimeStatusPath())

/**
 * Remove the gateway PID file, but only if it belongs to this process.
 */
fun removePidFile() {
    try {
        val path = _getPidPath()
        val record = _readJsonFile(path)
        if (record != null) {
            val filePid = try {
                (record["pid"] as? Number)?.toInt() ?: (record["pid"] as? String)?.toInt()
            } catch (_: Exception) { null }
            if (filePid != null && filePid != android.os.Process.myPid()) {
                return
            }
        }
        path.delete()
    } catch (_: Exception) {
    }
}

/**
 * Acquire a machine-local lock keyed by scope + identity.
 */
@Suppress("UNCHECKED_CAST")
fun acquireScopedLock(
    scope: String,
    identity: String,
    metadata: Map<String, Any?>? = null): Pair<Boolean, Map<String, Any?>?> {
    val lockPath = _getScopeLockPath(scope, identity)
    lockPath.parentFile?.mkdirs()
    val record = _buildPidRecord().toMutableMap()
    record["scope"] = scope
    record["identity_hash"] = _scopeHash(identity)
    record["metadata"] = metadata ?: emptyMap<String, Any?>()
    record["updated_at"] = _utcNowIso()

    var existing = _readJsonFile(lockPath)
    if (existing == null && lockPath.exists()) {
        try { lockPath.delete() } catch (_: Exception) {}
    }
    if (existing != null) {
        val existingPid = try {
            (existing["pid"] as? Number)?.toInt() ?: (existing["pid"] as? String)?.toInt()
        } catch (_: Exception) { null }

        if (existingPid == android.os.Process.myPid()
            && existing["start_time"] == record["start_time"]) {
            _writeJsonFile(lockPath, record)
            return true to existing
        }

        // TODO: port os.kill(existing_pid, 0) + /proc/<pid>/status 'T' state check.
        val stale = existingPid == null
        if (stale) {
            try { lockPath.delete() } catch (_: Exception) {}
        } else {
            return false to existing
        }
    }

    if (!lockPath.createNewFile()) {
        return false to _readJsonFile(lockPath)
    }
    try {
        lockPath.writeText(JSONObject(record).toString())
    } catch (exc: Exception) {
        try { lockPath.delete() } catch (_: Exception) {}
        throw exc
    }
    return true to null
}

/** Release a previously-acquired scope lock when owned by this process. */
fun releaseScopedLock(scope: String, identity: String) {
    val lockPath = _getScopeLockPath(scope, identity)
    val existing = _readJsonFile(lockPath) ?: return
    val existingPid = (existing["pid"] as? Number)?.toInt()
    val pid = android.os.Process.myPid()
    if (existingPid != pid) return
    if (existing["start_time"] != _getProcessStartTime(pid)) return
    try { lockPath.delete() } catch (_: Exception) {}
}

/** Remove all scoped lock files in the lock directory. */
fun releaseAllScopedLocks(): Int {
    val lockDir = _getLockDir()
    var removed = 0
    if (lockDir.exists()) {
        lockDir.listFiles { _, name -> name.endsWith(".lock") }?.forEach {
            try {
                if (it.delete()) removed++
            } catch (_: Exception) {}
        }
    }
    return removed
}

// ── --replace takeover marker ─────────────────────────────────────────

const val _TAKEOVER_MARKER_FILENAME = ".gateway-takeover.json"
const val _TAKEOVER_MARKER_TTL_S = 60

fun _getTakeoverMarkerPath(): File = File(_hermesHome(), _TAKEOVER_MARKER_FILENAME)

/**
 * Record that [targetPid] is being replaced by the current process.
 *
 * Returns true on successful write, false on any failure.
 */
fun writeTakeoverMarker(targetPid: Int): Boolean {
    return try {
        val targetStartTime = _getProcessStartTime(targetPid)
        val record = mapOf(
            "target_pid" to targetPid,
            "target_start_time" to targetStartTime,
            "replacer_pid" to android.os.Process.myPid(),
            "written_at" to _utcNowIso())
        _writeJsonFile(_getTakeoverMarkerPath(), record)
        true
    } catch (_: Exception) {
        false
    }
}

/** Check & unlink the takeover marker if it names the current process. */
fun consumeTakeoverMarkerForSelf(): Boolean {
    val path = _getTakeoverMarkerPath()
    val record = _readJsonFile(path) ?: return false

    val targetPid: Int
    val targetStartTime: Long?
    val writtenAt: String
    try {
        targetPid = (record["target_pid"] as? Number)?.toInt()
            ?: (record["target_pid"] as? String)?.toInt() ?: throw NumberFormatException()
        targetStartTime = (record["target_start_time"] as? Number)?.toLong()
        writtenAt = (record["written_at"] as? String) ?: ""
    } catch (_: Exception) {
        try { path.delete() } catch (_: Exception) {}
        return false
    }

    val stale = try {
        val writtenDt = OffsetDateTime.parse(writtenAt)
        val ageSec = (OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() - writtenDt.toEpochSecond())
        ageSec > _TAKEOVER_MARKER_TTL_S
    } catch (_: Exception) {
        true
    }

    if (stale) {
        try { path.delete() } catch (_: Exception) {}
        return false
    }

    val ourPid = android.os.Process.myPid()
    val ourStartTime = _getProcessStartTime(ourPid)
    val matches = (
        targetPid == ourPid
        && targetStartTime != null
        && ourStartTime != null
        && targetStartTime == ourStartTime)

    try { path.delete() } catch (_: Exception) {}
    return matches
}

fun clearTakeoverMarker() {
    try {
        _getTakeoverMarkerPath().delete()
    } catch (_: Exception) {
    }
}

/**
 * Return the PID of a running gateway instance, or null.
 */
@Suppress("UNCHECKED_CAST")
fun getRunningPid(pidPath: File? = null, cleanupStale: Boolean = true): Int? {
    val resolvedPidPath = pidPath ?: _getPidPath()
    val record = _readPidRecord(resolvedPidPath)
    if (record == null) {
        _cleanupInvalidPidPath(resolvedPidPath, cleanupStale = cleanupStale)
        return null
    }

    val pid = try {
        (record["pid"] as? Number)?.toInt() ?: (record["pid"] as? String)?.toInt()
    } catch (_: Exception) { null }
    if (pid == null) {
        _cleanupInvalidPidPath(resolvedPidPath, cleanupStale = cleanupStale)
        return null
    }

    // TODO: port os.kill(pid, 0) existence check. On Android apps cannot
    // probe arbitrary PIDs, so we fall back to /proc/<pid> presence.
    if (!File("/proc/$pid").exists()) {
        _cleanupInvalidPidPath(resolvedPidPath, cleanupStale = cleanupStale)
        return null
    }

    val recordedStart = (record["start_time"] as? Number)?.toLong()
    val currentStart = _getProcessStartTime(pid)
    if (recordedStart != null && currentStart != null && currentStart != recordedStart) {
        _cleanupInvalidPidPath(resolvedPidPath, cleanupStale = cleanupStale)
        return null
    }

    if (!_looksLikeGatewayProcess(pid)) {
        if (!_recordLooksLikeGateway(record)) {
            _cleanupInvalidPidPath(resolvedPidPath, cleanupStale = cleanupStale)
            return null
        }
    }
    return pid
}

/** Check if the gateway daemon is currently running. */
fun isGatewayRunning(pidPath: File? = null, cleanupStale: Boolean = true): Boolean =
    getRunningPid(pidPath, cleanupStale = cleanupStale) != null

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

private fun _jsonObjectToMap(obj: JSONObject): MutableMap<String, Any?> {
    val m = mutableMapOf<String, Any?>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        m[k] = _unwrapJson(obj.opt(k))
    }
    return m
}

private fun _unwrapJson(v: Any?): Any? = when (v) {
    null, JSONObject.NULL -> null
    is JSONObject -> _jsonObjectToMap(v)
    is JSONArray -> {
        val list = mutableListOf<Any?>()
        for (i in 0 until v.length()) list.add(_unwrapJson(v.opt(i)))
        list
    }
    else -> v
}
