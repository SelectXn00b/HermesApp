/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: file logging.
 */
package com.xiaomo.androidforclaw.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * File logging system
 * Align with OpenClaw's app.log and gateway.log
 *
 * Features:
 * - Structured logging (timestamp, level, tag, message)
 * - Log rotation (size limit)
 * - Categorized storage (app.log, gateway.log)
 * - 异步写入：所有 I/O 在后台单线程执行，不阻塞调用方
 */
class FileLogger(private val context: Context) {

    companion object {
        private const val TAG = "FileLogger"

        private const val LOGS_DIR = "/sdcard/.androidforclaw/logs"
        private const val APP_LOG_FILE = "$LOGS_DIR/app.log"
        private const val GATEWAY_LOG_FILE = "$LOGS_DIR/gateway.log"

        private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
        private const val MAX_ARCHIVED_LOGS = 5
    }

    private var loggingEnabled = true

    /** 异步写入队列 */
    private data class LogEntry(val filePath: String, val content: String)
    private val writeQueue = LinkedBlockingQueue<LogEntry>()
    private val running = AtomicBoolean(true)

    /** 后台写入线程 */
    private val writerThread = Thread({
        while (running.get() || writeQueue.isNotEmpty()) {
            try {
                val entry = writeQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    ?: continue
                doAppendToFile(entry.filePath, entry.content)
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "写入日志文件失败", e)
            }
        }
    }, "FileLogger-Writer").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
    }

    init {
        ensureDirectoryExists()
        writerThread.start()
    }

    /**
     * Log app logs（不再调用 outputToLogcat，由 Log.kt 包装器负责 logcat 输出）
     */
    fun logApp(level: LogLevel, tag: String, message: String, error: Throwable? = null) {
        if (!loggingEnabled) return
        val logLine = formatLogLine(level, tag, message, error)
        writeQueue.offer(LogEntry(APP_LOG_FILE, logLine))
    }

    /**
     * Log Gateway logs
     */
    fun logGateway(level: LogLevel, message: String, error: Throwable? = null) {
        if (!loggingEnabled) return
        val logLine = formatLogLine(level, "Gateway", message, error)
        writeQueue.offer(LogEntry(GATEWAY_LOG_FILE, logLine))
    }

    /**
     * Enable/disable file logging
     */
    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
        Log.i(TAG, "File logging ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Clear log files
     */
    fun clearLogs(logType: LogType = LogType.ALL) {
        writeQueue.offer(LogEntry(
            when (logType) {
                LogType.APP -> APP_LOG_FILE
                LogType.GATEWAY -> GATEWAY_LOG_FILE
                LogType.ALL -> APP_LOG_FILE // clear both
            },
            "" // empty → special marker handled below
        ))
        if (logType == LogType.ALL) {
            // Queue both clears
            try { File(APP_LOG_FILE).writeText("") } catch (_: Exception) {}
            try { File(GATEWAY_LOG_FILE).writeText("") } catch (_: Exception) {}
        } else {
            try { File(when (logType) {
                LogType.APP -> APP_LOG_FILE
                LogType.GATEWAY -> GATEWAY_LOG_FILE
                else -> APP_LOG_FILE
            }).writeText("") } catch (_: Exception) {}
        }
        Log.i(TAG, "Clear logs: $logType")
    }

    /**
     * Get log file size
     */
    fun getLogSize(logType: LogType): Long {
        return when (logType) {
            LogType.APP -> File(APP_LOG_FILE).length()
            LogType.GATEWAY -> File(GATEWAY_LOG_FILE).length()
            LogType.ALL -> File(APP_LOG_FILE).length() + File(GATEWAY_LOG_FILE).length()
        }
    }

    /**
     * 获取日志统计信息
     */
    fun getLogStats(): LogStats {
        val appLogFile = File(APP_LOG_FILE)
        val gatewayLogFile = File(GATEWAY_LOG_FILE)

        val appLines = if (appLogFile.exists()) {
            appLogFile.readLines().size
        } else 0

        val gatewayLines = if (gatewayLogFile.exists()) {
            gatewayLogFile.readLines().size
        } else 0

        return LogStats(
            appLogSize = appLogFile.length(),
            gatewayLogSize = gatewayLogFile.length(),
            appLogLines = appLines,
            gatewayLogLines = gatewayLines,
            totalSize = appLogFile.length() + gatewayLogFile.length()
        )
    }

    /**
     * 导出日志
     */
    fun exportLogs(logType: LogType, outputPath: String): Boolean {
        return try {
            val outputFile = File(outputPath)
            when (logType) {
                LogType.APP -> File(APP_LOG_FILE).copyTo(outputFile, overwrite = true)
                LogType.GATEWAY -> File(GATEWAY_LOG_FILE).copyTo(outputFile, overwrite = true)
                LogType.ALL -> {
                    val combined = File(APP_LOG_FILE).readText() +
                            "\n\n=== GATEWAY LOG ===\n\n" +
                            File(GATEWAY_LOG_FILE).readText()
                    outputFile.writeText(combined)
                }
            }
            Log.i(TAG, "日志已导出到: $outputPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导出日志失败", e)
            false
        }
    }

    /**
     * 读取最近的日志行
     */
    fun readRecentLogs(logType: LogType, lineCount: Int = 100): List<String> {
        val file = when (logType) {
            LogType.APP -> File(APP_LOG_FILE)
            LogType.GATEWAY -> File(GATEWAY_LOG_FILE)
            LogType.ALL -> return emptyList()
        }

        if (!file.exists()) return emptyList()

        return try {
            file.readLines().takeLast(lineCount)
        } catch (e: Exception) {
            Log.e(TAG, "读取日志失败", e)
            emptyList()
        }
    }

    /** 停止后台写入线程 */
    fun shutdown() {
        running.set(false)
        writerThread.interrupt()
    }

    // ==================== 私有方法 ====================

    private fun ensureDirectoryExists() {
        val dir = File(LOGS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
            Log.d(TAG, "创建 logs 目录: $LOGS_DIR")
        }
    }

    /**
     * 实际写入文件（在后台线程执行）
     */
    private fun doAppendToFile(filePath: String, content: String) {
        try {
            val file = File(filePath)

            // 检查文件大小，超过限制则轮转
            if (file.exists() && file.length() > MAX_FILE_SIZE) {
                rotateLog(file)
            }

            file.appendText(content)
        } catch (e: Exception) {
            Log.e(TAG, "写入日志文件失败: $filePath", e)
        }
    }

    /**
     * 日志轮转
     */
    private fun rotateLog(file: File) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val archiveName = "${file.nameWithoutExtension}-$timestamp.log"
            val archiveFile = File(file.parent, archiveName)

            file.renameTo(archiveFile)

            Log.i(TAG, "日志已轮转: $archiveName")

            cleanOldArchives(file.parentFile)
        } catch (e: Exception) {
            Log.e(TAG, "日志轮转失败", e)
        }
    }

    /**
     * 清理旧的归档日志
     */
    private fun cleanOldArchives(logsDir: File?) {
        if (logsDir == null || !logsDir.exists()) return

        try {
            val archives = logsDir.listFiles()
                ?.filter { it.name.contains("-20") && it.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?: return

            if (archives.size > MAX_ARCHIVED_LOGS) {
                archives.drop(MAX_ARCHIVED_LOGS).forEach { file ->
                    file.delete()
                    Log.d(TAG, "删除旧日志: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧归档失败", e)
        }
    }

    /**
     * Format log line
     */
    private fun formatLogLine(
        level: LogLevel,
        tag: String,
        message: String,
        error: Throwable?
    ): String {
        val timestamp = Instant.now().toString()
        val levelStr = level.name.padEnd(5)

        val errorInfo = if (error != null) {
            "\n${Log.getStackTraceString(error)}"
        } else {
            ""
        }

        return "[$timestamp] $levelStr $tag: $message$errorInfo\n"
    }
}

/**
 * Log level
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Log type
 */
enum class LogType {
    APP,
    GATEWAY,
    ALL
}

/**
 * 日志统计
 */
data class LogStats(
    val appLogSize: Long,
    val gatewayLogSize: Long,
    val appLogLines: Int,
    val gatewayLogLines: Int,
    val totalSize: Long
) {
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    override fun toString(): String {
        return """
            App Log: ${formatSize(appLogSize)} ($appLogLines lines)
            Gateway Log: ${formatSize(gatewayLogSize)} ($gatewayLogLines lines)
            Total: ${formatSize(totalSize)}
        """.trimIndent()
    }
}

/**
 * 全局日志实例（便捷使用）
 *
 * 注意：AppLog 方法只写入文件，不再调用 logcat。
 * logcat 输出由 Log.kt 包装器在调用 AppLog 之外单独完成，
 * 避免 Log.kt → AppLog → FileLogger → outputToLogcat → Log.kt 的无限递归。
 */
object AppLog {
    private lateinit var fileLogger: FileLogger

    fun init(context: Context) {
        fileLogger = FileLogger(context)
    }

    fun v(tag: String, message: String) {
        if (::fileLogger.isInitialized) {
            fileLogger.logApp(LogLevel.VERBOSE, tag, message)
        }
    }

    fun d(tag: String, message: String) {
        if (::fileLogger.isInitialized) {
            fileLogger.logApp(LogLevel.DEBUG, tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (::fileLogger.isInitialized) {
            fileLogger.logApp(LogLevel.INFO, tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (::fileLogger.isInitialized) {
            fileLogger.logApp(LogLevel.WARN, tag, message)
        }
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (::fileLogger.isInitialized) {
            fileLogger.logApp(LogLevel.ERROR, tag, message, error)
        }
    }

    fun gateway(level: LogLevel, message: String) {
        if (::fileLogger.isInitialized) {
            fileLogger.logGateway(level, message)
        }
    }
}
