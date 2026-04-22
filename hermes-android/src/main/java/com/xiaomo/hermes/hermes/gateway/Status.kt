package com.xiaomo.hermes.hermes.gateway

/**
 * Gateway status reporting.
 *
 * Provides a snapshot of the running gateway: connected platforms, active
 * sessions, uptime, and per-platform send counters.
 *
 * Ported from gateway/status.py
 */

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Return the path to the gateway PID file. */
fun pidPath(hermesHome: File? = null): File {
    val home = hermesHome ?: File(System.getProperty("user.home"), ".hermes")
    home.mkdirs()
    return File(home, "gateway.pid")
}

/** Read the PID from the PID file. */
fun readPid(hermesHome: File? = null): Long? {
    val file = pidPath(hermesHome)
    if (!file.exists()) return null
    return try { file.readText().trim().toLong() } catch (_unused: Exception) { null }
}

/** Write a PID to the PID file. */
fun writePid(pid: Long, hermesHome: File? = null) {
    val file = pidPath(hermesHome)
    file.parentFile?.mkdirs()
    file.writeText(pid.toString())
}

/**
 * Thread-safe counters for a single platform.
 */
class PlatformCounters {
    val messagesReceived: AtomicLong = AtomicLong(0)
    val messagesSent: AtomicLong = AtomicLong(0)
    val sendErrors: AtomicLong = AtomicLong(0)
    val reconnections: AtomicLong = AtomicLong(0)
    val bytesReceived: AtomicLong = AtomicLong(0)
    val bytesSent: AtomicLong = AtomicLong(0)

    /** Increment the received counter. */
    fun recordReceived(bytes: Long = 0) {
        messagesReceived.incrementAndGet()
        if (bytes > 0) bytesReceived.addAndGet(bytes)
    }

    /** Increment the sent counter. */
    fun recordSent(bytes: Long = 0) {
        messagesSent.incrementAndGet()
        if (bytes > 0) bytesSent.addAndGet(bytes)
    }

    /** Increment the error counter. */
    fun recordError() {
        sendErrors.incrementAndGet()
    }

    /** Increment the reconnection counter. */
    fun recordReconnection() {
        reconnections.incrementAndGet()
    }

    /** Convert to JSON. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("messages_received", messagesReceived.get())
        put("messages_sent", messagesSent.get())
        put("send_errors", sendErrors.get())
        put("reconnections", reconnections.get())
        put("bytes_received", bytesReceived.get())
        put("bytes_sent", bytesSent.get())
    }
}

/**
 * Gateway-wide status snapshot.
 *
 * Updated by platform adapters and the delivery router.  Read by the
 * ``/status`` command and the ``GET /health`` endpoint.
 */
class GatewayStatus {
    /** When the gateway process started. */
    val startedAt: Instant = Instant.now()

    /** Per-platform counters. */
    val platformCounters: ConcurrentHashMap<String, PlatformCounters> = ConcurrentHashMap()

    /** Names of currently connected platforms. */
    val connectedPlatforms: ConcurrentHashMap.KeySetView<String, Boolean> =
        ConcurrentHashMap.newKeySet()

    /** Number of active sessions (set externally by SessionStore). */
    @Volatile var activeSessions: Int = 0

    /** Number of sessions currently being processed. */
    @Volatile var processingSessions: Int = 0

    /** Uptime in seconds. */
    val uptimeSeconds: Long
        get() = java.time.Duration.between(startedAt, Instant.now()).seconds

    /** Get or create counters for a platform. */
    fun countersFor(platform: String): PlatformCounters =
        platformCounters.getOrPut(platform) { PlatformCounters() }

    /** Mark a platform as connected. */
    fun markConnected(platform: String) {
        connectedPlatforms.add(platform)
    }

    /** Mark a platform as disconnected. */
    fun markDisconnected(platform: String) {
        connectedPlatforms.remove(platform)
    }

    /** Build a human-readable status string. */
    fun formatHuman(): String = buildString {
        appendLine("Gateway Status")
        appendLine("  Uptime: ${formatDuration(uptimeSeconds)}")
        appendLine("  Connected platforms: ${connectedPlatforms.joinToString(", ").ifEmpty { "none" }}")
        appendLine("  Active sessions: $activeSessions")
        appendLine("  Processing sessions: $processingSessions")
        if (platformCounters.isNotEmpty()) {
            appendLine("  Platform counters:")
            platformCounters.forEach { (name, c) ->
                appendLine("    $name: recv=${c.messagesReceived.get()} sent=${c.messagesSent.get()} errors=${c.sendErrors.get()}")
            }
        }
    }

    /** Convert to JSON for the ``/health`` endpoint. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("started_at", startedAt.toString())
        put("uptime_seconds", uptimeSeconds)
        put("connected_platforms", JSONArray(connectedPlatforms.toList()))
        put("active_sessions", activeSessions)
        put("processing_sessions", processingSessions)
        val platformsJson = JSONObject()
        platformCounters.forEach { (name, c) ->
            platformsJson.put(name, c.toJson())
        }
        put("platforms", platformsJson)
    }

    companion object {
        /** Format seconds as "Xd Yh Zm". */
        fun formatDuration(seconds: Long): String {
            val days = seconds / 86400
            val hours = (seconds % 86400) / 3600
            val minutes = (seconds % 3600) / 60
            return buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0) append("${hours}h ")
                append("${minutes}m")
            }.trim()
        }
    }


    /** Load runtime status from persisted file. */
    fun loadRuntimeStatus(): Map<String, Any?> {
        val path = runtimeStatusPath()
        if (!path.exists()) return emptyMap()
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson(path.readText(), type)
        } catch (_unused: Exception) { emptyMap() }
    }

    /** Save runtime status to persisted file. */
    fun saveRuntimeStatus(status: Map<String, Any?>) {
        val path = runtimeStatusPath()
        try {
            path.parentFile?.mkdirs()
            path.writeText(com.google.gson.Gson().toJson(status))
        } catch (_unused: Exception) {}
    }

    /** Get the runtime status file path. */
    fun runtimeStatusPath(): java.io.File {
        return java.io.File(pidPath().parent, "gateway_state.json")
    }

    /** Check if the gateway process is alive by checking PID. */
    fun isGatewayProcessAlive(): Boolean {
        val pid = readPid() ?: return false
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("kill", "-0", pid.toString()))
            proc.waitFor() == 0
        } catch (_unused: Exception) { false }
    }

    /** Terminate the gateway process. */
    fun terminateGateway(force: Boolean = false): Boolean {
        val pid = readPid() ?: return false
        return try {
            val sig = if (force) "-9" else "-15"
            val proc = Runtime.getRuntime().exec(arrayOf("kill", sig, pid.toString()))
            proc.waitFor()
            true
        } catch (_unused: Exception) { false }
    }

    /** Get gateway uptime in seconds (reads from runtime status). */
    fun computeUptime(): Long {
        val status = loadRuntimeStatus()
        val startedAt = status["started_at"] as? String ?: return 0
        return try {
            val start = java.time.Instant.parse(startedAt)
            java.time.Duration.between(start, java.time.Instant.now()).seconds
        } catch (_unused: Exception) { 0 }
    }

    /** Get active session count from runtime status. */
    fun getActiveSessionCount(): Int {
        return (loadRuntimeStatus()["active_sessions"] as? Number)?.toInt() ?: 0
    }

    /** Get the gateway lock directory. */
    fun getLockDir(): java.io.File {
        val dir = java.io.File(pidPath().parent, "gateway-locks")
        dir.mkdirs()
        return dir
    }

    /** Create a gateway lock file. */
    fun createLock(scope: String, identity: String): java.io.File? {
        val lockFile = java.io.File(getLockDir(), "$scope-$identity.lock")
        return try {
            lockFile.writeText(java.time.Instant.now().toString())
            lockFile
        } catch (_unused: Exception) { null }
    }

    /** Release a gateway lock file. */
    fun releaseLock(scope: String, identity: String): Boolean {
        val lockFile = java.io.File(getLockDir(), "$scope-$identity.lock")
        return lockFile.delete()
    }

    /** Get all active locks. */
    fun getActiveLocks(): List<String> {
        val dir = getLockDir()
        return dir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
    }

    /** Format uptime as human-readable string. */
    fun formatUptime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            append("${secs}s")
        }.trim()
    }

    /** Compute SHA-256 hash of a file. */
    fun hashFile(file: java.io.File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(8192)
            var n = fis.read(buf)
            while (n != -1) {
                digest.update(buf, 0, n)
                n = fis.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

}
