package com.ai.assistance.operit.hermes.gateway

import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes gateway-specific diagnostic logs to an external-storage file
 * at `Downloads/Hermes/gateway_logs/gateway.log`.
 *
 * The file is human-readable and accessible via any file manager on the
 * device, so the user can inspect gateway behavior without adb/logcat.
 *
 * Log entries are timestamped and append-only.  A new session header is
 * written each time the gateway starts.  The file is capped at ~2 MB;
 * when exceeded the oldest half is truncated.
 */
object GatewayFileLogger {

    private const val DIR_NAME = "gateway_logs"
    private const val FILE_NAME = "gateway.log"
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024L // 2 MB

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    private fun resolveLogFile(): File? {
        val existing = logFile
        if (existing != null) return existing
        return try {
            val dir = File(OperitPaths.operitRootDir(), DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            File(dir, FILE_NAME).also { logFile = it }
        } catch (_: Throwable) {
            null
        }
    }

    /** Write a single log line. */
    fun log(level: String, tag: String, msg: String) {
        val file = resolveLogFile() ?: return
        val time = dateFormat.format(Date())
        val line = "$time $level/$tag: $msg\n"
        try {
            trimIfNeeded(file)
            FileWriter(file, true).use { it.write(line) }
        } catch (_: Throwable) {
            // swallow — never crash the gateway for logging
        }
    }

    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String) = log("E", tag, msg)
    fun d(tag: String, msg: String) = log("D", tag, msg)

    /** Write a prominent session-start banner. */
    fun logSessionStart() {
        val file = resolveLogFile() ?: return
        val time = dateFormat.format(Date())
        val banner = "\n${"=".repeat(60)}\n" +
            "  Gateway Session Started — $time\n" +
            "${"=".repeat(60)}\n\n"
        try {
            trimIfNeeded(file)
            FileWriter(file, true).use { it.write(banner) }
        } catch (_: Throwable) {}
    }

    /** Returns the absolute path for display to the user. */
    fun getLogFilePath(): String {
        return resolveLogFile()?.absolutePath ?: "(unavailable)"
    }

    private fun trimIfNeeded(file: File) {
        try {
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                val content = file.readText()
                // Keep the latter half
                val keepFrom = content.length / 2
                val newStart = content.indexOf('\n', keepFrom)
                if (newStart > 0) {
                    file.writeText("[...truncated...]\n" + content.substring(newStart + 1))
                }
            }
        } catch (_: Throwable) {}
    }
}
