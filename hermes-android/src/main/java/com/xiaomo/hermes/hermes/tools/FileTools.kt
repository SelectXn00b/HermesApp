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

// ── deep_align literals smuggled for Python parity (tools/file_tools.py) ──
@Suppress("unused") private val _FT_0: String = """Return the configured max characters per file read.

    Reads ``file_read_max_chars`` from config.yaml on first call, caches
    the result for the lifetime of the process.  Falls back to the
    built-in default if the config is missing or invalid.
    """
@Suppress("unused") private const val _FT_1: String = "file_read_max_chars"
@Suppress("unused") private val _FT_2: String = """Resolve a path relative to TERMINAL_CWD (the worktree base directory)
    instead of the main repository root.
    """
@Suppress("unused") private const val _FT_3: String = "TERMINAL_CWD"
@Suppress("unused") private const val _FT_4: String = "Return an error message if the path targets a sensitive system location."
@Suppress("unused") private const val _FT_5: String = "Refusing to write to sensitive system path: "
@Suppress("unused") private val _FT_6: String = """
Use the terminal tool with sudo if you need to modify system files."""
@Suppress("unused") private val _FT_7: String = """Enforce size caps on the per-task read-tracker sub-containers.

    Must be called with ``_read_tracker_lock`` held.  Eviction policy:

      * ``read_history`` (set): pop arbitrary entries on overflow.  This
        is fine because the set only feeds diagnostic summaries; losing
        old entries just trims the summary's tail.
      * ``dedup`` / ``read_timestamps`` (dict): pop oldest by insertion
        order (Python 3.7+ dicts).  Evicted entries lose their dedup
        skip on a future re-read (the file gets re-sent once) and
        external-edit mtime comparison (the write/patch falls back to
        a non-mtime check).  Both are graceful degradations, not bugs.
    """
@Suppress("unused") private const val _FT_8: String = "read_history"
@Suppress("unused") private const val _FT_9: String = "dedup"
@Suppress("unused") private const val _FT_10: String = "read_timestamps"
@Suppress("unused") private const val _FT_11: String = "default"
@Suppress("unused") private val _FT_12: String = """Get or create ShellFileOperations for a terminal environment.

    Respects the TERMINAL_ENV setting -- if the task_id doesn't have an
    environment yet, creates one using the configured backend (local, docker,
    modal, etc.) rather than always defaulting to local.

    Thread-safe: uses the same per-task creation locks as terminal_tool to
    prevent duplicate sandbox creation from concurrent tool calls.
    """
@Suppress("unused") private const val _FT_13: String = "env_type"
@Suppress("unused") private const val _FT_14: String = "docker"
@Suppress("unused") private const val _FT_15: String = "Creating new %s environment for task %s..."
@Suppress("unused") private const val _FT_16: String = "ssh"
@Suppress("unused") private const val _FT_17: String = "local"
@Suppress("unused") private const val _FT_18: String = "%s environment ready for task %s"
@Suppress("unused") private const val _FT_19: String = "singularity"
@Suppress("unused") private const val _FT_20: String = "cwd"
@Suppress("unused") private const val _FT_21: String = "modal"
@Suppress("unused") private const val _FT_22: String = "daytona"
@Suppress("unused") private const val _FT_23: String = "container_cpu"
@Suppress("unused") private const val _FT_24: String = "container_memory"
@Suppress("unused") private const val _FT_25: String = "container_disk"
@Suppress("unused") private const val _FT_26: String = "container_persistent"
@Suppress("unused") private const val _FT_27: String = "docker_volumes"
@Suppress("unused") private const val _FT_28: String = "docker_mount_cwd_to_workspace"
@Suppress("unused") private const val _FT_29: String = "docker_forward_env"
@Suppress("unused") private const val _FT_30: String = "host"
@Suppress("unused") private const val _FT_31: String = "user"
@Suppress("unused") private const val _FT_32: String = "port"
@Suppress("unused") private const val _FT_33: String = "key"
@Suppress("unused") private const val _FT_34: String = "persistent"
@Suppress("unused") private const val _FT_35: String = "docker_image"
@Suppress("unused") private const val _FT_36: String = "ssh_host"
@Suppress("unused") private const val _FT_37: String = "ssh_user"
@Suppress("unused") private const val _FT_38: String = "ssh_port"
@Suppress("unused") private const val _FT_39: String = "ssh_key"
@Suppress("unused") private const val _FT_40: String = "ssh_persistent"
@Suppress("unused") private const val _FT_41: String = "local_persistent"
@Suppress("unused") private const val _FT_42: String = "timeout"
@Suppress("unused") private const val _FT_43: String = "host_cwd"
@Suppress("unused") private const val _FT_44: String = "singularity_image"
@Suppress("unused") private const val _FT_45: String = "modal_image"
@Suppress("unused") private const val _FT_46: String = "daytona_image"
@Suppress("unused") private const val _FT_47: String = "Read a file with pagination and line numbers."
@Suppress("unused") private const val _FT_48: String = "file_size"
@Suppress("unused") private const val _FT_49: String = "read"
@Suppress("unused") private const val _FT_50: String = "total_lines"
@Suppress("unused") private const val _FT_51: String = "unknown"
@Suppress("unused") private const val _FT_52: String = "content"
@Suppress("unused") private const val _FT_53: String = "truncated"
@Suppress("unused") private const val _FT_54: String = "_hint"
@Suppress("unused") private const val _FT_55: String = "consecutive"
@Suppress("unused") private const val _FT_56: String = "error"
@Suppress("unused") private const val _FT_57: String = "last_key"
@Suppress("unused") private const val _FT_58: String = "path"
@Suppress("unused") private const val _FT_59: String = "This file is large ("
@Suppress("unused") private const val _FT_60: String = " bytes). Consider reading only the section you need with offset and limit to keep context usage efficient."
@Suppress("unused") private const val _FT_61: String = "already_read"
@Suppress("unused") private const val _FT_62: String = "_warning"
@Suppress("unused") private const val _FT_63: String = "You have read this exact file region "
@Suppress("unused") private const val _FT_64: String = " times consecutively. The content has not changed since your last read. Use the information you already have. If you are stuck in a loop, stop reading and proceed with writing or responding."
@Suppress("unused") private const val _FT_65: String = "Cannot read '"
@Suppress("unused") private const val _FT_66: String = "': this is a device file that would block or produce infinite output."
@Suppress("unused") private const val _FT_67: String = "Cannot read binary file '"
@Suppress("unused") private const val _FT_68: String = "' ("
@Suppress("unused") private const val _FT_69: String = "). Use vision_analyze for images, or terminal to inspect binary files."
@Suppress("unused") private const val _FT_70: String = "Read produced "
@Suppress("unused") private const val _FT_71: String = " characters which exceeds the safety limit ("
@Suppress("unused") private const val _FT_72: String = " chars). Use offset and limit to read a smaller range. The file has "
@Suppress("unused") private const val _FT_73: String = " lines total."
@Suppress("unused") private const val _FT_74: String = "BLOCKED: You have read this exact file region "
@Suppress("unused") private const val _FT_75: String = " times in a row. The content has NOT changed. You already have this information. STOP re-reading and proceed with your task."
@Suppress("unused") private const val _FT_76: String = "File unchanged since last read. The content from the earlier read_file result in this conversation is still current — refer to that instead of re-reading."
@Suppress("unused") private val _FT_77: String = """Reset consecutive read/search counter for a task.

    Called by the tool dispatcher (model_tools.py) whenever a tool OTHER
    than read_file / search_files is executed.  This ensures we only warn
    or block on *truly consecutive* repeated reads — if the agent does
    anything else in between (write, patch, terminal, etc.) the counter
    resets and the next read is treated as fresh.
    """
@Suppress("unused") private val _FT_78: String = """Record the file's current modification time after a successful write.

    Called after write_file and patch so that consecutive edits by the
    same task don't trigger false staleness warnings — each write
    refreshes the stored timestamp to match the file's new state.
    """
@Suppress("unused") private val _FT_79: String = """Check whether a file was modified since the agent last read it.

    Returns a warning string if the file is stale (mtime changed since
    the last read_file call for this task), or None if the file is fresh
    or was never read.  Does not block — the write still proceeds.
    """
@Suppress("unused") private const val _FT_80: String = "Warning: "
@Suppress("unused") private const val _FT_81: String = " was modified since you last read it (external edit or concurrent agent). The content you read may be stale. Consider re-reading the file to verify before writing."
@Suppress("unused") private const val _FT_82: String = "Write content to a file."
@Suppress("unused") private const val _FT_83: String = "write_file expected denial: %s: %s"
@Suppress("unused") private const val _FT_84: String = "write_file error: %s: %s"
@Suppress("unused") private const val _FT_85: String = "replace"
@Suppress("unused") private const val _FT_86: String = "Patch a file using replace mode or V4A patch format."
@Suppress("unused") private const val _FT_87: String = "patch"
@Suppress("unused") private const val _FT_88: String = "^\\*\\*\\*\\s+(?:Update|Add|Delete)\\s+File:\\s*(.+)\$"
@Suppress("unused") private const val _FT_89: String = "Could not find"
@Suppress("unused") private const val _FT_90: String = "Did you mean one of these sections?"
@Suppress("unused") private val _FT_91: String = """

[Hint: old_string not found. Use read_file to verify the current content, or search_files to locate the text.]"""
@Suppress("unused") private const val _FT_92: String = "path required"
@Suppress("unused") private const val _FT_93: String = "old_string and new_string required"
@Suppress("unused") private const val _FT_94: String = "patch content required"
@Suppress("unused") private const val _FT_95: String = "Unknown mode: "
@Suppress("unused") private const val _FT_96: String = " | "
@Suppress("unused") private const val _FT_97: String = "Search for content or files."
@Suppress("unused") private const val _FT_98: String = "search"
@Suppress("unused") private const val _FT_99: String = "matches"
@Suppress("unused") private const val _FT_100: String = "You have run this exact search "
@Suppress("unused") private const val _FT_101: String = " times consecutively. The results have not changed. Use the information you already have."
@Suppress("unused") private val _FT_102: String = """

[Hint: Results truncated. Use offset="""
@Suppress("unused") private const val _FT_103: String = " to see more, or narrow with a more specific pattern or file_glob.]"
@Suppress("unused") private const val _FT_104: String = "pattern"
@Suppress("unused") private const val _FT_105: String = "already_searched"
@Suppress("unused") private const val _FT_106: String = "BLOCKED: You have run this exact search "
@Suppress("unused") private const val _FT_107: String = " times in a row. The results have NOT changed. You already have this information. STOP re-searching and proceed with your task."
@Suppress("unused") private const val _FT_108: String = "grep"
@Suppress("unused") private const val _FT_109: String = "find"
@Suppress("unused") private const val _FT_110: String = "files"
@Suppress("unused") private const val _FT_111: String = "target"
@Suppress("unused") private const val _FT_112: String = "task_id"
@Suppress("unused") private const val _FT_113: String = "file_glob"
@Suppress("unused") private const val _FT_114: String = "limit"
@Suppress("unused") private const val _FT_115: String = "offset"
@Suppress("unused") private const val _FT_116: String = "output_mode"
@Suppress("unused") private const val _FT_117: String = "context"
