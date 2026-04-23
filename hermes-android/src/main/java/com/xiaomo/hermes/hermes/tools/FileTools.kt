package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson

/**
 * File Tools — read, write, and patch files.
 * High-level tool interface wrapping FileOperations.
 * Ported from tools/file_tools.py (top-level functions).
 */

private val _fileToolsGson = Gson()

/**
 * Read a file with line numbers.
 * Ported from file_tools.py :: read_file_tool
 */
fun readFileTool(
    path: String,
    offset: Int = 1,
    limit: Int = 500,
    ops: FileOperations? = null): String {
    val fileOps = ops ?: ShellFileOperations()
    val result = fileOps.readFile(path, offset, limit)
    return if (result.error != null) {
        val resp = mutableMapOf<String, Any>("error" to result.error)
        if (result.similarFiles.isNotEmpty()) resp["similar_files"] = result.similarFiles
        _fileToolsGson.toJson(resp)
    } else {
        val resp = mutableMapOf<String, Any>(
            "content" to result.content,
            "total_lines" to result.totalLines,
            "file_size" to result.fileSize)
        if (result.truncated) resp["truncated"] = true
        result.hint?.let { resp["hint"] = it }
        _fileToolsGson.toJson(resp)
    }
}

/**
 * Write content to a file.
 * Ported from file_tools.py :: write_file_tool
 */
fun writeFileTool(
    path: String,
    content: String,
    ops: FileOperations? = null): String {
    val fileOps = ops ?: ShellFileOperations()
    val result = fileOps.writeFile(path, content)
    return if (result.error != null) {
        _fileToolsGson.toJson(mapOf("error" to result.error))
    } else {
        val resp = mutableMapOf<String, Any>("bytes_written" to result.bytesWritten)
        if (result.dirsCreated) resp["dirs_created"] = true
        _fileToolsGson.toJson(resp)
    }
}

/**
 * Patch a file using fuzzy find-and-replace.
 * Ported from file_tools.py :: patch_tool (replace mode)
 */
@Suppress("UNUSED_PARAMETER")
fun patchTool(
    mode: String = "replace",
    path: String? = null,
    oldString: String? = null,
    newString: String? = null,
    replaceAll: Boolean = false,
    patch: String? = null,
    taskId: String = "default",
): String {
    val fileOps = _getFileOps(taskId)
    val result = fileOps.patchReplace(path ?: "", oldString ?: "", newString ?: "", replaceAll)
    return if (result.success) {
        val resp = mutableMapOf<String, Any>("success" to true)
        if (result.diff.isNotEmpty()) resp["diff"] = result.diff
        if (result.filesModified.isNotEmpty()) resp["files_modified"] = result.filesModified
        _fileToolsGson.toJson(resp)
    } else {
        _fileToolsGson.toJson(mapOf("error" to (result.error ?: "Unknown error")))
    }
}

/**
 * Search for content in files.
 * Ported from file_tools.py :: search_tool
 */
@Suppress("UNUSED_PARAMETER")
fun searchTool(
    pattern: String,
    target: String = "content",
    path: String = ".",
    fileGlob: String? = null,
    limit: Int = 50,
    offset: Int = 0,
    outputMode: String = "content",
    context: Int = 0,
    taskId: String = "default",
): String {
    val fileOps = _getFileOps(taskId)
    val result = fileOps.search(pattern, path, fileGlob = fileGlob, limit = limit)
    return _fileToolsGson.toJson(result.toDict())
}


// ── Module-level constants (1:1 with tools/file_tools.py) ────────────────

val _EXPECTED_WRITE_ERRNOS: Set<Int> = setOf(13, 1, 30)  // EACCES, EPERM, EROFS
const val _DEFAULT_MAX_READ_CHARS: Int = 100_000
const val _LARGE_FILE_HINT_BYTES: Int = 512_000

val _BLOCKED_DEVICE_PATHS: Set<String> = setOf(
    "/dev/zero", "/dev/random", "/dev/urandom", "/dev/full",
    "/dev/tty", "/dev/stdin", "/dev/stdout", "/dev/stderr",
)

val _SENSITIVE_PATH_PREFIXES: List<String> = listOf(
    "/etc/", "/boot/", "/usr/lib/systemd/",
    "/private/etc/", "/private/var/",
)

val _SENSITIVE_EXACT_PATHS: Set<String> = setOf(
    "/var/run/docker.sock", "/run/docker.sock",
)

const val _READ_HISTORY_CAP: Int = 500
const val _DEDUP_CAP: Int = 1000
const val _READ_TIMESTAMPS_CAP: Int = 1000

val READ_FILE_SCHEMA: Map<String, Any?> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "path" to mapOf("type" to "string"),
        "offset" to mapOf("type" to "integer", "minimum" to 1),
        "limit" to mapOf("type" to "integer", "minimum" to 1),
    ),
    "required" to listOf("path"),
)

val WRITE_FILE_SCHEMA: Map<String, Any?> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "path" to mapOf("type" to "string"),
        "content" to mapOf("type" to "string"),
    ),
    "required" to listOf("path", "content"),
)

val PATCH_SCHEMA: Map<String, Any?> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "path" to mapOf("type" to "string"),
        "old_string" to mapOf("type" to "string"),
        "new_string" to mapOf("type" to "string"),
        "replace_all" to mapOf("type" to "boolean"),
    ),
    "required" to listOf("path", "old_string", "new_string"),
)

val SEARCH_FILES_SCHEMA: Map<String, Any?> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "pattern" to mapOf("type" to "string"),
        "path" to mapOf("type" to "string"),
        "file_glob" to mapOf("type" to "string"),
        "limit" to mapOf("type" to "integer"),
    ),
    "required" to listOf("pattern"),
)


// ── Module-level helpers (Android fallbacks / shims) ─────────────────────

private var _cachedMaxReadChars: Int? = null

fun _getMaxReadChars(): Int {
    _cachedMaxReadChars?.let { return it }
    val v = _DEFAULT_MAX_READ_CHARS
    _cachedMaxReadChars = v
    return v
}

fun _resolvePath(filepath: String): java.io.File {
    val expanded = if (filepath.startsWith("~/")) {
        (System.getProperty("user.home") ?: "") + filepath.substring(1)
    } else filepath
    return java.io.File(expanded)
}

fun _isBlockedDevice(filepath: String): Boolean {
    val expanded = if (filepath.startsWith("~/")) {
        (System.getProperty("user.home") ?: "") + filepath.substring(1)
    } else filepath
    if (expanded in _BLOCKED_DEVICE_PATHS) return true
    if (expanded.startsWith("/proc/") && (expanded.endsWith("/fd/0") ||
            expanded.endsWith("/fd/1") || expanded.endsWith("/fd/2"))) {
        return true
    }
    return false
}

fun _checkSensitivePath(filepath: String): String? {
    val expanded = if (filepath.startsWith("~/")) {
        (System.getProperty("user.home") ?: "") + filepath.substring(1)
    } else filepath
    for (prefix in _SENSITIVE_PATH_PREFIXES) {
        if (expanded.startsWith(prefix)) return "Writing to $prefix is restricted"
    }
    if (expanded in _SENSITIVE_EXACT_PATHS) return "Writing to $expanded is restricted"
    return null
}

fun _isExpectedWriteException(exc: Throwable): Boolean {
    if (exc is java.nio.file.AccessDeniedException) return true
    if (exc is java.io.FileNotFoundException) return false
    if (exc is java.nio.file.ReadOnlyFileSystemException) return true
    return false
}

private val _readTrackerLock = Any()
private val _readTimestamps = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Long>>()
private val _readHistory = java.util.concurrent.ConcurrentHashMap<String, java.util.LinkedHashSet<String>>()
private val _readDedup = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Int>>()

fun _capReadTrackerData(taskData: MutableMap<String, Any?>) {
    synchronized(_readTrackerLock) {
        @Suppress("UNCHECKED_CAST")
        val hist = taskData["history"] as? java.util.LinkedHashSet<Any?>
        if (hist != null && hist.size > _READ_HISTORY_CAP) {
            val removeCount = hist.size - _READ_HISTORY_CAP
            val iter = hist.iterator()
            repeat(removeCount) { if (iter.hasNext()) { iter.next(); iter.remove() } }
        }
        @Suppress("UNCHECKED_CAST")
        val dedup = taskData["dedup"] as? MutableMap<Any?, Any?>
        if (dedup != null && dedup.size > _DEDUP_CAP) {
            val keys = dedup.keys.toList()
            for (k in keys.take(dedup.size - _DEDUP_CAP)) dedup.remove(k)
        }
        @Suppress("UNCHECKED_CAST")
        val ts = taskData["timestamps"] as? MutableMap<Any?, Any?>
        if (ts != null && ts.size > _READ_TIMESTAMPS_CAP) {
            val keys = ts.keys.toList()
            for (k in keys.take(ts.size - _READ_TIMESTAMPS_CAP)) ts.remove(k)
        }
    }
}

private val _fileOpsCache = java.util.concurrent.ConcurrentHashMap<String, ShellFileOperations>()

fun _getFileOps(taskId: String = "default"): ShellFileOperations {
    return _fileOpsCache.getOrPut(taskId) { ShellFileOperations() }
}

fun clearFileOpsCache(taskId: String? = null) {
    if (taskId != null) {
        _fileOpsCache.remove(taskId)
    } else {
        _fileOpsCache.clear()
    }
}

fun resetFileDedup(taskId: String = "default") {
    _readDedup.remove(taskId)
}

fun notifyOtherToolCall(taskId: String = "default") {
    // Reset the consecutive-read counter so staleness logic restarts.
    _readDedup.remove(taskId)
}

fun _updateReadTimestamp(filepath: String, taskId: String) {
    val f = java.io.File(filepath)
    if (!f.exists()) return
    val mtime = f.lastModified()
    val bucket = _readTimestamps.getOrPut(taskId) { java.util.concurrent.ConcurrentHashMap() }
    bucket[filepath] = mtime
}

fun _checkFileStaleness(filepath: String, taskId: String): String? {
    val f = java.io.File(filepath)
    if (!f.exists()) return null
    val recorded = _readTimestamps[taskId]?.get(filepath) ?: return null
    val current = f.lastModified()
    if (current > recorded) {
        return "File '$filepath' was modified externally since last read"
    }
    return null
}

fun _checkFileReqs(): Boolean {
    // Lazy wrapper in Python to avoid circular imports; Android has no
    // equivalent circular-import concern — always return available.
    return true
}

fun _handleReadFile(
    args: Map<String, Any?>,
): String {
    val tid = (args["task_id"] as? String) ?: "default"
    val path = (args["path"] as? String) ?: ""
    val offset = (args["offset"] as? Int) ?: 1
    val limit = (args["limit"] as? Int) ?: 500
    val ops = _getFileOps(tid)
    return readFileTool(path, offset, limit, ops)
}

fun _handleWriteFile(
    args: Map<String, Any?>,
): String {
    val tid = (args["task_id"] as? String) ?: "default"
    val path = (args["path"] as? String) ?: ""
    val content = (args["content"] as? String) ?: ""
    val ops = _getFileOps(tid)
    return writeFileTool(path, content, ops)
}

fun _handlePatch(
    args: Map<String, Any?>,
): String {
    val tid = (args["task_id"] as? String) ?: "default"
    val mode = (args["mode"] as? String) ?: "replace"
    val path = args["path"] as? String
    val oldString = args["old_string"] as? String
    val newString = args["new_string"] as? String
    val replaceAll = (args["replace_all"] as? Boolean) ?: false
    val patch = args["patch"] as? String
    return patchTool(mode, path, oldString, newString, replaceAll, patch, tid)
}

fun _handleSearchFiles(
    args: Map<String, Any?>,
): String {
    val tid = (args["task_id"] as? String) ?: "default"
    val pattern = (args["pattern"] as? String) ?: ""
    val target = (args["target"] as? String) ?: "content"
    val path = (args["path"] as? String) ?: "."
    val fileGlob = args["file_glob"] as? String
    val limit = (args["limit"] as? Int) ?: 50
    val offset = (args["offset"] as? Int) ?: 0
    val outputMode = (args["output_mode"] as? String) ?: "content"
    val context = (args["context"] as? Int) ?: 0
    return searchTool(pattern, target, path, fileGlob, limit, offset, outputMode, context, tid)
}
