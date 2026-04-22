package com.xiaomo.hermes.hermes.tools

import java.io.File
import java.io.IOException

/**
 * File Operations Module.
 * Provides file manipulation capabilities (read, write, patch, search).
 * Ported from tools/file_operations.py
 */

// ---------------------------------------------------------------------------
// Write-path deny list — blocks writes to sensitive system/credential files
// ---------------------------------------------------------------------------

private val _HOME = System.getProperty("user.home") ?: ""

val WRITE_DENIED_PATHS: Set<String> by lazy {
    val paths = listOf(
        "$_HOME/.ssh/authorized_keys",
        "$_HOME/.ssh/id_rsa",
        "$_HOME/.ssh/id_ed25519",
        "$_HOME/.ssh/config",
        "$_HOME/.bashrc",
        "$_HOME/.zshrc",
        "$_HOME/.profile",
        "$_HOME/.bash_profile",
        "$_HOME/.zprofile",
        "$_HOME/.netrc",
        "$_HOME/.pgpass",
        "$_HOME/.npmrc",
        "$_HOME/.pypirc",
        "/etc/sudoers",
        "/etc/passwd",
        "/etc/shadow")
    paths.mapNotNull {
        try { File(it).canonicalPath } catch (_unused: Exception) { null }
    }.toSet()
}

val WRITE_DENIED_PREFIXES: List<String> by lazy {
    val prefixes = listOf(
        "$_HOME/.ssh",
        "$_HOME/.aws",
        "$_HOME/.gnupg",
        "$_HOME/.kube",
        "/etc/sudoers.d",
        "/etc/systemd",
        "$_HOME/.docker",
        "$_HOME/.azure",
        "$_HOME/.config/gh")
    prefixes.mapNotNull {
        try { File(it).canonicalPath + File.separator } catch (_unused: Exception) { null }
    }
}

/**
 * Return the resolved HERMES_WRITE_SAFE_ROOT path, or null if unset.
 * Ported from file_operations.py :: _get_safe_write_root
 */
fun _getSafeWriteRoot(): String? {
    val safeRoot = System.getenv("HERMES_WRITE_SAFE_ROOT")
    if (safeRoot.isNullOrEmpty()) return null
    return try { File(safeRoot).canonicalPath } catch (_unused: Exception) { safeRoot }
}

/**
 * Return true if path is on the write deny list.
 * Ported from file_operations.py :: _is_write_denied
 */
fun _isWriteDenied(path: String): Boolean {
    val resolved = try { File(path).canonicalPath } catch (_unused: Exception) { path }
    if (resolved in WRITE_DENIED_PATHS) return true
    for (prefix in WRITE_DENIED_PREFIXES) {
        if (resolved.startsWith(prefix)) return true
    }
    val safeRoot = _getSafeWriteRoot()
    if (safeRoot != null) {
        if (resolved != safeRoot && !resolved.startsWith(safeRoot + File.separator)) return true
    }
    return false
}

// =============================================================================
// Result Data Classes
// =============================================================================

data class ReadResult(
    val content: String = "",
    val totalLines: Int = 0,
    val fileSize: Int = 0,
    val truncated: Boolean = false,
    val hint: String? = null,
    val isBinary: Boolean = false,
    val isImage: Boolean = false,
    val base64Content: String? = null,
    val mimeType: String? = null,
    val dimensions: String? = null,
    val error: String? = null,
    val similarFiles: List<String> = emptyList()) {
    fun toDict(): Map<String, Any?> = buildMap {
        if (content.isNotEmpty()) put("content", content)
        if (totalLines > 0) put("total_lines", totalLines)
        if (fileSize > 0) put("file_size", fileSize)
        if (truncated) put("truncated", true)
        hint?.let { put("hint", it) }
        if (isBinary) put("is_binary", true)
        if (isImage) put("is_image", true)
        base64Content?.let { put("base64_content", it) }
        mimeType?.let { put("mime_type", it) }
        dimensions?.let { put("dimensions", it) }
        error?.let { put("error", it) }
        if (similarFiles.isNotEmpty()) put("similar_files", similarFiles)
    }
}

data class WriteResult(
    val bytesWritten: Int = 0,
    val dirsCreated: Boolean = false,
    val error: String? = null,
    val warning: String? = null) {
    fun toDict(): Map<String, Any?> = buildMap {
        if (bytesWritten > 0) put("bytes_written", bytesWritten)
        if (dirsCreated) put("dirs_created", true)
        error?.let { put("error", it) }
        warning?.let { put("warning", it) }
    }
}

data class PatchResult(
    val success: Boolean = false,
    val diff: String = "",
    val filesModified: List<String> = emptyList(),
    val filesCreated: List<String> = emptyList(),
    val filesDeleted: List<String> = emptyList(),
    val lint: Map<String, Any>? = null,
    val error: String? = null) {
    fun toDict(): Map<String, Any?> = buildMap {
        put("success", success)
        if (diff.isNotEmpty()) put("diff", diff)
        if (filesModified.isNotEmpty()) put("files_modified", filesModified)
        if (filesCreated.isNotEmpty()) put("files_created", filesCreated)
        if (filesDeleted.isNotEmpty()) put("files_deleted", filesDeleted)
        lint?.let { put("lint", it) }
        error?.let { put("error", it) }
    }
}

data class SearchMatch(
    val path: String,
    val lineNumber: Int,
    val content: String,
    val mtime: Double = 0.0)

data class SearchResult(
    val matches: List<SearchMatch> = emptyList(),
    val files: List<String> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val totalCount: Int = 0,
    val truncated: Boolean = false,
    val error: String? = null) {
    fun toDict(): Map<String, Any?> = buildMap {
        put("total_count", totalCount)
        if (matches.isNotEmpty()) {
            put("matches", matches.map {
                mapOf("path" to it.path, "line" to it.lineNumber, "content" to it.content)
            })
        }
        if (files.isNotEmpty()) put("files", files)
        if (counts.isNotEmpty()) put("counts", counts)
        if (truncated) put("truncated", true)
        error?.let { put("error", it) }
    }
}

data class LintResult(
    val success: Boolean = true,
    val skipped: Boolean = false,
    val output: String = "",
    val message: String = "") {
    fun toDict(): Map<String, Any> = if (skipped) {
        mapOf("status" to "skipped", "message" to message)
    } else {
        mapOf("status" to if (success) "ok" else "error", "output" to output)
    }
}

data class ExecuteResult(
    val stdout: String = "",
    val exitCode: Int = 0)

// =============================================================================
// Abstract Interface
// =============================================================================

abstract class FileOperations {
    abstract fun readFile(path: String, offset: Int = 1, limit: Int = 500): ReadResult
    abstract fun readFileRaw(path: String): ReadResult
    abstract fun writeFile(path: String, content: String): WriteResult
    abstract fun patchReplace(path: String, oldString: String, newString: String, replaceAll: Boolean = false): PatchResult
    abstract fun patchV4a(patchContent: String): PatchResult
    abstract fun deleteFile(path: String): WriteResult
    abstract fun moveFile(src: String, dst: String): WriteResult
    abstract fun search(pattern: String, path: String = ".", target: String = "content", fileGlob: String? = null, limit: Int = 50, offset: Int = 0, outputMode: String = "content", context: Int = 0): SearchResult
}

// =============================================================================
// Shell-based Implementation
// =============================================================================

// Image extensions (subset of binary that we can return as base64)
val IMAGE_EXTENSIONS = setOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico")

// Linters by file extension
val LINTERS = mapOf(
    ".py" to "python -m py_compile {file} 2>&1",
    ".js" to "node --check {file} 2>&1",
    ".ts" to "npx tsc --noEmit {file} 2>&1",
    ".go" to "go vet {file} 2>&1",
    ".rs" to "rustfmt --check {file} 2>&1")

// Max limits for read operations
private const val MAX_LINES = 2000
private const val MAX_LINE_LENGTH = 2000

/**
 * File operations implemented via shell commands / local file I/O on Android.
 * Ported from ShellFileOperations in file_operations.py.
 */
class ShellFileOperations(
    private val cwd: String = File(".").absolutePath) : FileOperations() {

    override fun readFile(path: String, offset: Int, limit: Int): ReadResult {
        val expanded = _expandPath(path)
        val file = File(expanded).let { if (it.isAbsolute) it else File(cwd, expanded) }
        if (!file.exists()) return _suggestSimilarFiles(path)
        if (!file.isFile) return ReadResult(error = "Not a file: $path")
        if (hasBinaryExtension(path)) {
            return ReadResult(isBinary = true, error = "File is binary: $path")
        }

        return try {
            val allLines = file.readLines(Charsets.UTF_8)
            val totalLines = allLines.size
            val startIdx = (offset - 1).coerceIn(0, totalLines)
            val endIdx = (startIdx + limit.coerceAtMost(MAX_LINES)).coerceAtMost(totalLines)
            val selectedLines = allLines.subList(startIdx, endIdx)
            val truncated = endIdx < totalLines

            val numbered = selectedLines.mapIndexed { idx, line ->
                val lineNum = startIdx + idx + 1
                val displayLine = if (line.length > MAX_LINE_LENGTH) line.substring(0, MAX_LINE_LENGTH) + "... [truncated]" else line
                "%6d|%s".format(lineNum, displayLine)
            }.joinToString("\n")

            val hint = if (truncated) "File has $totalLines lines. Use offset=${endIdx + 1} to read more." else null

            ReadResult(
                content = numbered,
                totalLines = totalLines,
                fileSize = file.length().toInt(),
                truncated = truncated,
                hint = hint)
        } catch (e: Exception) {
            ReadResult(error = "Failed to read file: ${e.message}")
        }
    }

    override fun readFileRaw(path: String): ReadResult {
        val expanded = _expandPath(path)
        val file = File(expanded).let { if (it.isAbsolute) it else File(cwd, expanded) }
        if (!file.exists()) return ReadResult(error = "File not found: $path")
        if (!file.isFile) return ReadResult(error = "Not a file: $path")
        if (hasBinaryExtension(path)) return ReadResult(isBinary = true, error = "File is binary: $path")

        return try {
            val content = file.readText(Charsets.UTF_8)
            ReadResult(content = content, totalLines = content.lines().size, fileSize = content.length)
        } catch (e: Exception) {
            ReadResult(error = "Failed to read file: ${e.message}")
        }
    }

    override fun writeFile(path: String, content: String): WriteResult {
        val expanded = _expandPath(path)
        val resolved = File(expanded).let { if (it.isAbsolute) it.absolutePath else File(cwd, expanded).absolutePath }
        if (_isWriteDenied(resolved)) {
            return WriteResult(error = "Write to '$path' is blocked (denied path)")
        }

        return try {
            val file = File(resolved)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            WriteResult(bytesWritten = content.toByteArray(Charsets.UTF_8).size, dirsCreated = true)
        } catch (e: IOException) {
            WriteResult(error = "Failed to write file: ${e.message}")
        }
    }

    override fun patchReplace(path: String, oldString: String, newString: String, replaceAll: Boolean): PatchResult {
        val expanded = _expandPath(path)
        val resolved = File(expanded).let { if (it.isAbsolute) it.absolutePath else File(cwd, expanded).absolutePath }
        if (_isWriteDenied(resolved)) {
            return PatchResult(error = "Write to '$path' is blocked (denied path)")
        }
        val file = File(resolved)
        if (!file.exists()) return PatchResult(error = "File not found: $path")

        return try {
            val content = file.readText(Charsets.UTF_8)
            val result = fuzzyFindAndReplace(content, oldString, newString, replaceAll)
            val err = result["error"] as String?
            if (err != null) {
                PatchResult(error = err)
            } else {
                val newContent = result["content"] as String
                file.writeText(newContent, Charsets.UTF_8)
                val diff = _unifiedDiff(content, newContent, path)
                PatchResult(success = true, diff = diff, filesModified = listOf(path))
            }
        } catch (e: Exception) {
            PatchResult(error = "Patch failed: ${e.message}")
        }
    }

    override fun patchV4a(patchContent: String): PatchResult {
        val (operations, error) = parseV4aPatch(patchContent)
        if (error != null) return PatchResult(error = error)
        val baseDir = File(cwd)
        return applyV4aOperations(operations, baseDir)
    }

    override fun deleteFile(path: String): WriteResult {
        val expanded = _expandPath(path)
        val resolved = File(expanded).let { if (it.isAbsolute) it.absolutePath else File(cwd, expanded).absolutePath }
        if (_isWriteDenied(resolved)) {
            return WriteResult(error = "Delete of '$path' is blocked (denied path)")
        }
        return try {
            val file = File(resolved)
            if (!file.exists()) return WriteResult(error = "File not found: $path")
            file.delete()
            WriteResult()
        } catch (e: Exception) {
            WriteResult(error = "Failed to delete file: ${e.message}")
        }
    }

    override fun moveFile(src: String, dst: String): WriteResult {
        val srcExpanded = _expandPath(src)
        val dstExpanded = _expandPath(dst)
        val srcResolved = File(srcExpanded).let { if (it.isAbsolute) it.absolutePath else File(cwd, srcExpanded).absolutePath }
        val dstResolved = File(dstExpanded).let { if (it.isAbsolute) it.absolutePath else File(cwd, dstExpanded).absolutePath }
        if (_isWriteDenied(dstResolved)) {
            return WriteResult(error = "Move to '$dst' is blocked (denied path)")
        }
        return try {
            val srcFile = File(srcResolved)
            val dstFile = File(dstResolved)
            if (!srcFile.exists()) return WriteResult(error = "Source file not found: $src")
            dstFile.parentFile?.mkdirs()
            if (srcFile.renameTo(dstFile)) WriteResult()
            else {
                srcFile.copyTo(dstFile, overwrite = true)
                srcFile.delete()
                WriteResult()
            }
        } catch (e: Exception) {
            WriteResult(error = "Failed to move file: ${e.message}")
        }
    }

    override fun search(
        pattern: String, path: String, target: String, fileGlob: String?,
        limit: Int, offset: Int, outputMode: String, context: Int): SearchResult {
        val expanded = _expandPath(path)
        val baseDir = File(expanded).let { if (it.isAbsolute) it else File(cwd, expanded) }
        if (!baseDir.exists()) return SearchResult(error = "Path not found: $path")

        return try {
            val regex = try { Regex(pattern, RegexOption.IGNORE_CASE) } catch (_unused: Exception) { null }
            val globRegex = fileGlob?.let { glob ->
                val r = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".")
                Regex("^$r$", RegexOption.IGNORE_CASE)
            }
            val matches = mutableListOf<SearchMatch>()
            val files = mutableListOf<String>()
            val counts = mutableMapOf<String, Int>()
            var totalCount = 0

            val filesToSearch = if (baseDir.isFile) listOf(baseDir)
            else baseDir.walkTopDown()
                .filter { it.isFile }
                .filter { f -> globRegex == null || f.name.matches(globRegex) }
                .filter { !hasBinaryExtension(it.name) }
                .take(1000)
                .toList()

            for (file in filesToSearch) {
                val lines = try { file.readLines(Charsets.UTF_8) } catch (_unused: Exception) { continue }
                var fileMatches = 0
                for ((idx, line) in lines.withIndex()) {
                    val matchesPattern = if (regex != null) regex.containsMatchIn(line)
                    else line.contains(pattern, ignoreCase = true)
                    if (matchesPattern) {
                        fileMatches++
                        totalCount++
                        if (matches.size < limit + offset) {
                            matches.add(SearchMatch(file.path, idx + 1, line.trim()))
                        }
                    }
                }
                if (fileMatches > 0) {
                    files.add(file.path)
                    counts[file.path] = fileMatches
                }
            }

            SearchResult(
                matches = if (offset > 0) matches.drop(offset) else matches,
                files = files,
                counts = counts,
                totalCount = totalCount,
                truncated = totalCount > limit + offset)
        } catch (e: Exception) {
            SearchResult(error = "Search failed: ${e.message}")
        }
    }

    /** Execute command via local shell. */
    fun _exec(command: String, cwd: String? = null, timeout: Int? = null, stdinData: String? = null): ExecuteResult {
        val processBuilder = ProcessBuilder("sh", "-c", command)
        processBuilder.directory(File(cwd ?: this.cwd))
        processBuilder.redirectErrorStream(false)
        return try {
            val process = processBuilder.start()
            if (stdinData != null) {
                process.outputStream.use { it.write(stdinData.toByteArray(Charsets.UTF_8)) }
            }
            val completed = if (timeout != null && timeout > 0) {
                process.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            } else {
                process.waitFor(); true
            }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            if (!completed) {
                process.destroyForcibly()
                ExecuteResult(stdout = stdout, exitCode = -1)
            } else {
                ExecuteResult(stdout = stdout + if (stderr.isNotEmpty()) "\n$stderr" else "", exitCode = process.exitValue())
            }
        } catch (e: Exception) {
            ExecuteResult(stdout = "", exitCode = -1)
        }
    }

    /** Check if a command exists in the environment. */
    fun _hasCommand(cmd: String): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("which", cmd))
            proc.waitFor() == 0
        } catch (_unused: Exception) { false }
    }

    /** Check if a file is likely binary. */
    fun _isLikelyBinary(path: String, contentSample: String? = null): Boolean {
        val ext = "." + File(path).extension.lowercase()
        if (hasBinaryExtension(path)) return true
        if (contentSample != null) {
            val sample = contentSample.take(1000)
            val nonPrintable = sample.count { it.code < 32 && it !in "\n\r\t" }
            return nonPrintable.toDouble() / sample.length.coerceAtLeast(1) > 0.30
        }
        return false
    }

    /** Check if file is an image we can return as base64. */
    fun _isImage(path: String): Boolean {
        val ext = "." + File(path).extension.lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    /** Add line numbers to content in LINE_NUM|CONTENT format. */
    fun _addLineNumbers(content: String, startLine: Int = 1): String {
        return content.lines().mapIndexed { i, line ->
            val truncated = if (line.length > MAX_LINE_LENGTH) line.substring(0, MAX_LINE_LENGTH) + "... [truncated]" else line
            "%6d|%s".format(startLine + i, truncated)
        }.joinToString("\n")
    }

    /** Expand shell-style paths like ~ and ~user to absolute paths. */
    fun _expandPath(path: String): String {
        if (path.isEmpty()) return path
        if (path.startsWith("~/")) {
            return _HOME + path.substring(1)
        }
        if (path == "~") return _HOME
        return path
    }

    /** Escape a string for safe use in shell commands. */
    fun _escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\"'\"'") + "'"
    }

    /** Generate unified diff between old and new content. */
    fun _unifiedDiff(oldContent: String, newContent: String, filename: String): String {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        val diff = StringBuilder()
        diff.append("--- a/$filename\n")
        diff.append("+++ b/$filename\n")
        var i = 0
        var j = 0
        while (i < oldLines.size || j < newLines.size) {
            if (i < oldLines.size && j < newLines.size && oldLines[i] == newLines[j]) {
                diff.append(" ${oldLines[i]}\n")
                i++; j++
            } else {
                if (i < oldLines.size) {
                    diff.append("-${oldLines[i]}\n")
                    i++
                }
                if (j < newLines.size) {
                    diff.append("+${newLines[j]}\n")
                    j++
                }
            }
        }
        return diff.toString()
    }

    /** Suggest similar files when the requested file is not found. */
    fun _suggestSimilarFiles(path: String): ReadResult {
        val expanded = _expandPath(path)
        val resolved = File(expanded).let { if (it.isAbsolute) it else File(cwd, expanded) }
        val dirPath = resolved.parent ?: "."
        val filename = File(path).name
        val basenameNoExt = filename.substringBeforeLast('.', "")
        val ext = "." + filename.substringAfterLast('.', "").lowercase()
        val lowerName = filename.lowercase()

        val dir = File(dirPath)
        if (!dir.isDirectory) return ReadResult(error = "File not found: $path")

        val scored = mutableListOf<Pair<Int, String>>()
        val files = dir.listFiles()?.take(50) ?: return ReadResult(error = "File not found: $path")
        for (f in files) {
            val lf = f.name.lowercase()
            val score = when {
                lf == lowerName -> 100
                f.name.substringBeforeLast('.', "").lowercase() == basenameNoExt.lowercase() -> 90
                lf.startsWith(lowerName) || lowerName.startsWith(lf) -> 70
                lowerName in lf -> 60
                lf in lowerName && lf.length > 2 -> 40
                ext.length > 1 && "." + f.name.substringAfterLast('.', "").lowercase() == ext -> 30
                else -> 0
            }
            if (score > 0) scored.add(score to f.absolutePath)
        }

        scored.sortByDescending { it.first }
        val similar = scored.take(5).map { it.second }
        return ReadResult(error = "File not found: $path", similarFiles = similar)
    }

    /** Run syntax check on a file after editing. */
    fun _checkLint(path: String): LintResult {
        val ext = "." + File(path).extension.lowercase()
        val linterCmd = LINTERS[ext]
            ?: return LintResult(skipped = true, message = "No linter for $ext files")
        val baseCmd = linterCmd.split(" ").first()
        if (!_hasCommand(baseCmd)) {
            return LintResult(skipped = true, message = "$baseCmd not available")
        }
        val cmd = linterCmd.replace("{file}", _escapeShellArg(path))
        val result = _exec(cmd, timeout = 30)
        return LintResult(
            success = result.exitCode == 0,
            output = result.stdout.trim().ifEmpty { "" }
        )
    }

    /** Search for files by name pattern (glob-like). */
    fun _searchFiles(pattern: String, path: String, limit: Int, offset: Int): SearchResult {
        val expanded = _expandPath(path)
        val baseDir = File(expanded).let { if (it.isAbsolute) it else File(cwd, expanded) }
        if (!baseDir.exists()) return SearchResult(error = "Path not found: $path")
        val searchPattern = if (!pattern.startsWith("**/") && '/' !in pattern) pattern else pattern.substringAfterLast('/')
        val regexStr = searchPattern.replace(".", "\\.").replace("*", ".*").replace("?", ".")
        val regex = Regex("^$regexStr$", RegexOption.IGNORE_CASE)
        val allFiles = (if (baseDir.isFile) listOf(baseDir) else baseDir.walkTopDown().filter { it.isFile }.take(1000).toList())
            .filter { regex.matches(it.name) }
        val page = allFiles.drop(offset).take(limit).map { it.path }
        return SearchResult(files = page, totalCount = allFiles.size, truncated = allFiles.size > offset + limit)
    }

    /** Search for files by name using ripgrep's --files mode. */
    fun _searchFilesRg(pattern: String, path: String, limit: Int, offset: Int): SearchResult {
        return _searchFiles(pattern, path, limit + offset, 0).let {
            SearchResult(files = it.files.drop(offset).take(limit), totalCount = it.totalCount, truncated = it.truncated)
        }
    }

    /** Search for content inside files (grep-like). */
    fun _searchContent(pattern: String, path: String, fileGlob: String?, limit: Int, offset: Int, outputMode: String, context: Int): SearchResult {
        return search(pattern, path, "content", fileGlob, limit, offset, outputMode, context)
    }

    /** Search using ripgrep. */
    fun _searchWithRg(pattern: String, path: String, fileGlob: String?, limit: Int, offset: Int, outputMode: String, context: Int): SearchResult {
        return search(pattern, path, "content", fileGlob, limit, offset, outputMode, context)
    }

    /** Fallback search using grep. */
    fun _searchWithGrep(pattern: String, path: String, fileGlob: String?, limit: Int, offset: Int, outputMode: String, context: Int): SearchResult {
        return search(pattern, path, "content", fileGlob, limit, offset, outputMode, context)
    }
}
