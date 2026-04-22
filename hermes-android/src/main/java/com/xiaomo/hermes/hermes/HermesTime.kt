package com.xiaomo.hermes.hermes

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Timestamp utilities for Hermes session logging.
 * 1:1 对齐 hermes-agent/hermes_time.py
 *
 * 每个消息在 JSONL session 中都有 created_at 时间戳。
 * 这些工具函数用于生成、格式化和解析时间戳。
 */

// ── Python zoneinfo.ZoneInfo("Asia/Shanghai") 的 Kotlin 等价 ──────────────
private val DEFAULT_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

/**
 * 获取当前 UTC ISO 时间字符串
 * Python: _ts_iso(z=None) -> str
 */
fun tsIso(zone: TimeZone? = null): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date())
}

/**
 * 获取当前时间的字符串表示（用于 session UI 展示）
 * Python: ts_now() -> str
 */
fun tsNow(zone: TimeZone? = null): String {
    val tz = zone ?: DEFAULT_ZONE
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    fmt.timeZone = tz
    return fmt.format(Date())
}

/**
 * 获取当前时间 Date 对象（使用 Hermes 配置的时区）
 * Python: from hermes_time import now as _hermes_now
 *
 * 对齐 Python hermes_time.now()，返回 timezone-aware datetime.
 * Kotlin Date 本身不带时区（内部存储 UTC millis），但此函数返回
 * 当前时刻的 Date，由调用方按需选择时区格式化。
 */
fun hermesNow(): Date = Date()

/**
 * 获取当前 UTC UNIX 时间戳（秒）
 * Python: ts_utc() -> int
 */
fun tsUtc(): Long {
    return System.currentTimeMillis() / 1000
}

/**
 * 从 ISO 格式解析为 UNIX 时间戳
 * Python: iso_to_unix(iso_str: str) -> int
 */
fun isoToUnix(isoStr: String): Long {
    return try {
        // 尝试带毫秒的格式
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(isoStr)?.time?.div(1000) ?: 0L
    } catch (e: Exception) {
        try {
            // 尝试不带毫秒的格式
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(isoStr)?.time?.div(1000) ?: 0L
        } catch (e2: Exception) {
            0L
        }
    }
}

/**
 * 从 UNIX 时间戳解析为 ISO 格式
 * Python: unix_to_iso(ts: int | float) -> str
 */
fun unixToIso(ts: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(ts * 1000))
}

/**
 * 获取 session 文件名（基于时间戳）
 * Python: session_filename(ts: int | None = None) -> str
 */
fun sessionFilename(ts: Long? = null): String {
    val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date((ts ?: tsUtc()) * 1000))
}

/**
 * 解析 session 文件名为 UNIX 时间戳
 * Python: parse_session_filename(name: str) -> int
 */
fun parseSessionFilename(name: String): Long {
    return try {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(name)?.time?.div(1000) ?: 0L
    } catch (e: Exception) {
        0L
    }
}

/**
 * 获取 session 显示时间（给用户看的时间）
 * Python: session_display_time(ts: int | None = None) -> str
 */
fun sessionDisplayTime(ts: Long? = null): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    fmt.timeZone = DEFAULT_ZONE
    return fmt.format(Date((ts ?: tsUtc()) * 1000))
}

/**
 * 获取当前时间戳
 * Python: get_timestamp() -> str
 */
fun getTimestamp(): String {
    return tsUtc().toString()
}

/**
 * 获取友好格式的文件大小
 */
fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * 获取友好格式的时间差
 * Python: format_time_delta(seconds: int) -> str
 */
fun formatTimeDelta(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        seconds < 86400 -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            "${h}h ${m}m"
        }
        else -> {
            val d = seconds / 86400
            val h = (seconds % 86400) / 3600
            "${d}d ${h}h"
        }
    }
}

/**
 * 获取 session 路径
 * Python: session_path(name: str | None = None) -> Path
 */
fun sessionPath(name: String? = null): java.io.File {
    val sessionsDir = getSessionsDir()
    return if (name != null) {
        java.io.File(sessionsDir, name)
    } else {
        val ts = tsUtc()
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        java.io.File(sessionsDir, fmt.format(Date(ts * 1000)))
    }
}

/**
 * 创建新 session 目录
 * Python: create_session_dir(name: str | None = None) -> Path
 */
fun createSessionDir(name: String? = null): java.io.File {
    val dir = sessionPath(name)
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

/**
 * 获取最新的 session 目录
 */
fun getLatestSessionDir(): java.io.File? {
    val sessionsDir = getSessionsDir()
    if (!sessionsDir.exists()) return null
    return sessionsDir.listFiles()
        ?.filter { it.isDirectory }
        ?.maxByOrNull { it.lastModified() }
}

/**
 * 列出所有 session 目录（按时间倒序）
 */
fun listSessionDirs(limit: Int = 50): List<java.io.File> {
    val sessionsDir = getSessionsDir()
    if (!sessionsDir.exists()) return emptyList()
    return sessionsDir.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedByDescending { it.lastModified() }
        ?.take(limit)
        ?: emptyList()
}

/**
 * 获取 session 统计信息
 */
data class SessionStats(
    val name: String,
    val messageCount: Int,
    val sizeBytes: Long,
    val createdAt: String,
    val updatedAt: String)

fun getSessionStats(sessionDir: java.io.File): SessionStats? {
    if (!sessionDir.exists()) return null
    val jsonlFile = java.io.File(sessionDir, "messages.jsonl")
    if (!jsonlFile.exists()) return null

    val messageCount = jsonlFile.readLines().size
    val sizeBytes = jsonlFile.length()
    val createdAt = sessionDisplayTime(parseSessionFilename(sessionDir.name))
    val updatedAt = sessionDisplayTime(jsonlFile.lastModified() / 1000)

    return SessionStats(
        name = sessionDir.name,
        messageCount = messageCount,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
        updatedAt = updatedAt)


}
