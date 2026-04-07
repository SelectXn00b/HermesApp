package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/shared.ts
 *
 * Shared utility functions for secrets module.
 */

import com.xiaomo.androidforclaw.logging.Log
import org.json.JSONObject
import java.io.File

/** Check if a value is a non-null Map (equivalent to TS isRecord). */
fun isRecord(value: Any?): Boolean =
    value is Map<*, *>

/** Check if a string is non-empty after trimming. */
fun isNonEmptyString(value: Any?): Boolean =
    value is String && value.trim().isNotEmpty()

/**
 * Parse an env-style value: strips surrounding quotes if present.
 * Aligned with TS parseEnvValue.
 */
fun parseEnvValue(raw: String): String {
    val trimmed = raw.trim()
    if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
        (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
        return trimmed.substring(1, trimmed.length - 1)
    }
    return trimmed
}

/**
 * Normalize a positive integer value with a fallback.
 * Aligned with TS normalizePositiveInt.
 */
fun normalizePositiveInt(value: Any?, fallback: Int): Int {
    if (value is Number) {
        val num = value.toDouble()
        if (num.isFinite()) {
            return maxOf(1, num.toInt())
        }
    }
    return maxOf(1, fallback)
}

/**
 * Parse a dot-separated path into segments.
 * Aligned with TS parseDotPath.
 */
fun parseDotPath(pathname: String): List<String> =
    pathname.split(".")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * Join path segments back to a dot-separated path.
 * Aligned with TS toDotPath.
 */
fun toDotPath(segments: List<String>): String =
    segments.joinToString(".")

/**
 * Ensure the parent directory for a file exists.
 * Aligned with TS ensureDirForFile.
 */
fun ensureDirForFile(filePath: String) {
    val parent = File(filePath).parentFile
    if (parent != null && !parent.exists()) {
        parent.mkdirs()
    }
}

/**
 * Write a JSON object to a file securely.
 * Aligned with TS writeJsonFileSecure.
 */
fun writeJsonFileSecure(pathname: String, value: JSONObject) {
    ensureDirForFile(pathname)
    File(pathname).writeText(value.toString(2) + "\n")
}

/**
 * Read a text file if it exists, returning null otherwise.
 * Aligned with TS readTextFileIfExists.
 */
fun readTextFileIfExists(pathname: String): String? {
    val file = File(pathname)
    if (!file.exists()) return null
    return file.readText()
}

/**
 * Write a text file atomically using a temp file + rename.
 * Aligned with TS writeTextFileAtomic.
 */
fun writeTextFileAtomic(pathname: String, value: String) {
    ensureDirForFile(pathname)
    val tempPath = "$pathname.tmp-${android.os.Process.myPid()}-${System.currentTimeMillis()}"
    val tempFile = File(tempPath)
    tempFile.writeText(value)
    tempFile.renameTo(File(pathname))
}

/**
 * Describe an unknown error in a human-readable way.
 * Aligned with TS describeUnknownError.
 */
fun describeUnknownError(err: Any?): String {
    if (err is Throwable && err.message?.trim()?.isNotEmpty() == true) {
        return err.message!!
    }
    if (err is String && err.trim().isNotEmpty()) {
        return err
    }
    if (err is Number) {
        return err.toString()
    }
    if (err is Boolean) {
        return if (err) "true" else "false"
    }
    return try {
        err?.toString() ?: "unknown error"
    } catch (_: Exception) {
        "unknown error"
    }
}
