package com.xiaomo.hermes.hermes.tools

import java.io.File
import java.io.IOException

/**
 * File Operations Module.
 * Provides file manipulation capabilities (read, write, patch, search).
 * Ported from file_operations.py
 */
abstract class FileOperations {
    abstract fun readFile(path: String, offset: Int = 1, limit: Int = 500): ReadResult
    abstract fun readFileRaw(path: String): ReadResult
    abstract fun writeFile(path: String, content: String): WriteResult
    abstract fun patchReplace(path: String, oldString: String, newString: String, replaceAll: Boolean = false): FilePatchResult
    abstract fun patchV4a(patchContent: String): FilePatchResult
    abstract fun deleteFile(path: String): WriteResult
    abstract fun moveFile(src: String, dst: String): WriteResult
    abstract fun search(pattern: String, path: String = ".", target: String = "content", fileGlob: String? = null, limit: Int = 50, offset: Int = 0, outputMode: String = "content", context: Int = 0): SearchResult
}

// --- Result data classes ---

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
    fun toMap(): Map<String, Any?> {
        return buildMap {
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
}

data class WriteResult(
    val bytesWritten: Int = 0,
    val dirsCreated: Boolean = false,
    val error: String? = null,
    val warning: String? = null) {
    fun toMap(): Map<String, Any?> = buildMap {
        if (bytesWritten > 0) put("bytes_written", bytesWritten)
        if (dirsCreated) put("dirs_created", true)
        error?.let { put("error", it) }
        warning?.let { put("warning", it) }
    }
}

data class FilePatchResult(
    val success: Boolean = false,
    val diff: String = "",
    val filesModified: List<String> = emptyList(),
    val filesCreated: List<String> = emptyList(),
    val filesDeleted: List<String> = emptyList(),
    val lint: Map<String, Any>? = null,
    val error: String? = null) {
    fun toMap(): Map<String, Any?> = buildMap {
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
    fun toMap(): Map<String, Any?> = buildMap {
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
    fun toMap(): Map<String, Any> = if (skipped) {
        mapOf("status" to "skipped", "message" to message)
    } else {
        mapOf("status" to if (success) "ok" else "error", "output" to output)
    }
}

/**
 * Write-path deny list — blocks writes to sensitive system/credential files.
 */
object WritePathDenial {
    private val home = System.getProperty("user.home") ?: ""

    private val WRITE_DENIED_PATHS: Set<String> by lazy {
        val paths = listOf(
            "$home/.ssh/authorized_keys",
            "$home/.ssh/id_rsa",
            "$home/.ssh/id_ed25519",
            "$home/.ssh/config",
            "$home/.bashrc",
            "$home/.zshrc",
            "$home/.profile",
            "$home/.bash_profile",
            "$home/.zprofile",
            "$home/.netrc",
            "$home/.pgpass",
            "$home/.npmrc",
            "$home/.pypirc",
            "/etc/sudoers",
            "/etc/passwd",
            "/etc/shadow")
        paths.mapNotNull {
            try { File(it).canonicalPath } catch (_unused: Exception) { null }
        }.toSet()
    }

    private val WRITE_DENIED_PREFIXES: List<String> by lazy {
        val prefixes = listOf(
            "$home/.ssh",
            "$home/.aws",
            "$home/.gnupg",
            "$home/.kube",
            "/etc/sudoers.d",
            "/etc/systemd",
            "$home/.docker",
            "$home/.azure",
            "$home/.config/gh")
        prefixes.mapNotNull {
            try { File(it).canonicalPath + File.separator } catch (_unused: Exception) { null }
        }
    }

    fun isWriteDenied(path: String): Boolean {
        val resolved = try { File(path).canonicalPath } catch (_unused: Exception) { path }
        if (resolved in WRITE_DENIED_PATHS) return true
        for (prefix in WRITE_DENIED_PREFIXES) {
            if (resolved.startsWith(prefix)) return true
        }
        val safeRoot = System.getenv("HERMES_WRITE_SAFE_ROOT")
        if (!safeRoot.isNullOrEmpty()) {
            val root = try { File(safeRoot).canonicalPath } catch (_unused: Exception) { safeRoot }
            if (resolved != root && !resolved.startsWith(root + File.separator)) return true
        }
        return false
    }
}

/**
 * Local filesystem implementation of FileOperations.
 */
class LocalFileOperations(
    private val workingDir: String = File(".").absolutePath) : FileOperations() {

    private val IMAGE_EXTENSIONS = setOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico")
    private val SUPPORTED_IMAGE_MIMES = mapOf(
        ".png" to "image/png", ".jpg" to "image/jpeg", ".jpeg" to "image/jpeg",
        ".gif" to "image/gif", ".webp" to "image/webp", ".bmp" to "image/bmp", ".ico" to "image/x-icon")

    override fun readFile(path: String, offset: Int, limit: Int): ReadResult {
        val file = File(resolvePath(path))
        if (!file.exists()) {
            return ReadResult(error = "File not found: $path", similarFiles = findSimilarFiles(path))
        }
        if (!file.isFile) return ReadResult(error = "Not a file: $path")
        if (BinaryExtensions.isBinaryExtension(path)) {
            return ReadResult(isBinary = true, error = "File is binary: $path")
        }

        return try {
            val allLines = file.readLines(Charsets.UTF_8)
            val totalLines = allLines.size
            val startIdx = (offset - 1).coerceIn(0, totalLines)
            val endIdx = (startIdx + limit.coerceAtMost(2000)).coerceAtMost(totalLines)
            val selectedLines = allLines.subList(startIdx, endIdx)
            val truncated = endIdx < totalLines

            val numbered = selectedLines.mapIndexed { idx, line ->
                val lineNum = startIdx + idx + 1
                val displayLine = if (line.length > 2000) line.substring(0, 2000) + "... [truncated]" else line
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
        val file = File(resolvePath(path))
        if (!file.exists()) return ReadResult(error = "File not found: $path")
        if (!file.isFile) return ReadResult(error = "Not a file: $path")
        if (BinaryExtensions.isBinaryExtension(path)) return ReadResult(isBinary = true, error = "File is binary: $path")

        return try {
            val content = file.readText(Charsets.UTF_8)
            ReadResult(content = content, totalLines = content.lines().size, fileSize = content.length)
        } catch (e: Exception) {
            ReadResult(error = "Failed to read file: ${e.message}")
        }
    }

    override fun writeFile(path: String, content: String): WriteResult {
        val resolvedPath = resolvePath(path)
        if (WritePathDenial.isWriteDenied(resolvedPath)) {
            return WriteResult(error = "Write to '$path' is blocked (denied path)")
        }

        return try {
            val file = File(resolvedPath)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            WriteResult(bytesWritten = content.toByteArray(Charsets.UTF_8).size, dirsCreated = true)
        } catch (e: IOException) {
            WriteResult(error = "Failed to write file: ${e.message}")
        }
    }

    override fun patchReplace(path: String, oldString: String, newString: String, replaceAll: Boolean): FilePatchResult {
        val resolvedPath = resolvePath(path)
        if (WritePathDenial.isWriteDenied(resolvedPath)) {
            return FilePatchResult(error = "Write to '$path' is blocked (denied path)")
        }
        val file = File(resolvedPath)
        if (!file.exists()) return FilePatchResult(error = "File not found: $path")

        return try {
            val content = file.readText(Charsets.UTF_8)
            val result = FuzzyMatch.fuzzyFindAndReplace(content, oldString, newString, replaceAll)
            if (result.error != null) {
                FilePatchResult(error = result.error)
            } else {
                file.writeText(result.content, Charsets.UTF_8)
                val diff = generateDiff(content, result.content, path)
                FilePatchResult(success = true, diff = diff, filesModified = listOf(path))
            }
        } catch (e: Exception) {
            FilePatchResult(error = "Patch failed: ${e.message}")
        }
    }

    override fun patchV4a(patchContent: String): FilePatchResult {
        val (operations, error) = PatchParser.parseV4aPatch(patchContent)
        if (error != null) return FilePatchResult(error = error)
        val baseDir = File(workingDir)
        return PatchParser.applyV4aOperations(operations, baseDir).let {
            FilePatchResult(success = it.success, diff = it.diff, filesModified = it.filesModified,
                filesCreated = it.filesCreated, filesDeleted = it.filesDeleted, error = it.error)
        }
    }

    override fun deleteFile(path: String): WriteResult {
        val resolvedPath = resolvePath(path)
        if (WritePathDenial.isWriteDenied(resolvedPath)) {
            return WriteResult(error = "Delete of '$path' is blocked (denied path)")
        }
        return try {
            val file = File(resolvedPath)
            if (!file.exists()) return WriteResult(error = "File not found: $path")
            file.delete()
            WriteResult()
        } catch (e: Exception) {
            WriteResult(error = "Failed to delete file: ${e.message}")
        }
    }

    override fun moveFile(src: String, dst: String): WriteResult {
        val srcResolved = resolvePath(src)
        val dstResolved = resolvePath(dst)
        if (WritePathDenial.isWriteDenied(dstResolved)) {
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
        val baseDir = File(resolvePath(path))
        if (!baseDir.exists()) return SearchResult(error = "Path not found: $path")

        return try {
            val regex = try { Regex(pattern, RegexOption.IGNORE_CASE) } catch (_unused: Exception) { null }
            val matches = mutableListOf<SearchMatch>()
            val files = mutableListOf<String>()
            val counts = mutableMapOf<String, Int>()
            var totalCount = 0

            val filesToSearch = if (baseDir.isFile) listOf(baseDir)
            else baseDir.walkTopDown()
                .filter { it.isFile }
                .filter { f -> fileGlob == null || f.name.matches(globToRegex(fileGlob)) }
                .filter { !BinaryExtensions.isBinaryExtension(it.name) }
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

    private fun resolvePath(path: String): String {
        val file = File(path)
        return if (file.isAbsolute) file.absolutePath else File(workingDir, path).absolutePath
    }

    private fun findSimilarFiles(path: String): List<String> {
        val name = File(path).name
        val parent = File(resolvePath(path)).parentFile ?: return emptyList()
        if (!parent.exists()) return emptyList()
        return parent.listFiles()
            ?.filter { it.name.contains(name, ignoreCase = true) || name.contains(it.name, ignoreCase = true) }
            ?.map { it.path }
            ?.take(5) ?: emptyList()
    }

    private fun globToRegex(glob: String): Regex {
        val regex = glob
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex("^$regex$", RegexOption.IGNORE_CASE)
    }

    private fun generateDiff(oldContent: String, newContent: String, filename: String): String {
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




    /** Check if a file is likely binary. */
    fun isLikelyBinary(path: String): Boolean {
        return try {
            val bytes = java.io.File(path).readBytes().take(8096)
            bytes.any { it == 0.toByte() }
        } catch (_unused: Exception) { true }
    }

    /** Check if a file is an image by extension. */
    fun isImage(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico")
    }

    /** Expand ~ to home directory. */
    fun expandPath(path: String): String {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1)
        }
        return path
    }

    /** Add line numbers to content. */
    fun addLineNumbers(content: String, startLine: Int = 1): String {
        return content.lines().mapIndexed { i, line ->
            String.format("%4d  %s", startLine + i, line)
        }.joinToString("\n")
    }

    /** Generate a unified diff between two texts. */
    fun unifiedDiff(oldText: String, newText: String, path: String = ""): String {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val sb = StringBuilder()
        if (path.isNotEmpty()) {
            sb.appendLine("--- a/$path")
            sb.appendLine("+++ b/$path")
        }
        // Simple line-by-line diff
        val maxLen = maxOf(oldLines.size, newLines.size)
        var changes = false
        for (i in 0 until maxLen) {
            val old = oldLines.getOrNull(i)
            val new = newLines.getOrNull(i)
            if (old != new) {
                changes = true
                old?.let { sb.appendLine("-$it") }
                new?.let { sb.appendLine("+$it") }
            }
        }
        return if (changes) sb.toString() else ""
    }

    /** Escape a string for shell execution. */
    fun escapeShellArg(arg: String): String {
        return "'${arg.replace("'", "'\''")}'"
    }

    /** Check if a command exists on PATH. */
    fun hasCommand(cmd: String): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("which", cmd))
            proc.waitFor() == 0
        } catch (_unused: Exception) { false }
    }

    /** Suggest similar files when a path is not found. */
    fun suggestSimilarFiles(path: String, directory: String): List<String> {
        val dir = java.io.File(directory)
        if (!dir.isDirectory) return emptyList()
        val basename = path.substringAfterLast('/').lowercase()
        return dir.listFiles()
            ?.filter { it.name.lowercase().contains(basename) || basename.contains(it.name.lowercase()) }
            ?.map { it.absolutePath }
            ?.take(5)
            ?: emptyList()
    }

    /** Convert to dictionary. */
    fun toDict(): Map<String, Any?> = mapOf("path" to "")


    /** Execute command via terminal backend. */
    fun _exec(command: String, cwd: String? = null, timeout: Int? = null, stdinData: String? = null): ExecuteResult {
        val processBuilder = ProcessBuilder("sh", "-c", command)
        if (cwd != null) processBuilder.directory(java.io.File(cwd))
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

    /** Check if a command exists in the environment (cached). */
    fun _hasCommand(cmd: String): Boolean {
        return hasCommand(cmd)
    }

    /** Check if a file is likely binary. */
    fun _isLikelyBinary(path: String, contentSample: String? = null): Boolean {
        val ext = java.io.File(path).extension.lowercase()
        val binaryExts = setOf("exe", "dll", "so", "dylib", "bin", "o", "a", "zip", "tar", "gz", "bz2", "xz", "7z", "rar", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
        if (ext in binaryExts) return true
        if (contentSample != null) {
            val sample = contentSample.take(1000)
            val nonPrintable = sample.count { it.code < 32 && it !in "\n\r\t" }
            return nonPrintable.toDouble() / sample.length.coerceAtLeast(1) > 0.30
        }
        return false
    }

    /** Check if file is an image we can return as base64. */
    fun _isImage(path: String): Boolean {
        return isImage(path)
    }

    /** Add line numbers to content in LINE_NUM|CONTENT format. */
    fun _addLineNumbers(content: String, startLine: Int = 1): String {
        return addLineNumbers(content, startLine)
    }

    /** Expand shell-style paths like ~ and ~user to absolute paths. */
    fun _expandPath(path: String): String {
        return expandPath(path)
    }

    /** Escape a string for safe use in shell commands. */
    fun _escapeShellArg(arg: String): String {
        return escapeShellArg(arg)
    }

    /** Generate unified diff between old and new content. */
    fun _unifiedDiff(oldContent: String, newContent: String, filename: String): String {
        return unifiedDiff(oldContent, newContent, filename)
    }

    /** Suggest similar files when the requested file is not found. */
    fun _suggestSimilarFiles(path: String): ReadResult {
        val dirPath = java.io.File(resolvePath(path)).parent ?: "."
        val filename = java.io.File(path).name
        val basenameNoExt = filename.substringBeforeLast('.', "")
        val ext = "." + filename.substringAfterLast('.', "").lowercase()
        val lowerName = filename.lowercase()

        val dir = java.io.File(dirPath)
        if (!dir.isDirectory) return ReadResult(error = "Directory not found: $dirPath")

        val scored = mutableListOf<Pair<Int, String>>()
        val files = dir.listFiles()?.take(50) ?: return ReadResult()
        for (f in files) {
            val lf = f.name.lowercase()
            val score = when {
                lf == lowerName -> 100
                f.name.substringBeforeLast('.', "").lowercase() == basenameNoExt.lowercase() -> 90
                lf.startsWith(lowerName) || lowerName.startsWith(lf) -> 70
                lowerName in lf -> 60
                lf in lowerName && lf.length > 2 -> 40
                ext.isNotEmpty() && ".$lf".substringAfterLast('.', "") == ext.substring(1) -> 30
                else -> 0
            }
            if (score > 0) scored.add(score to f.absolutePath)
        }

        scored.sortByDescending { it.first }
        val similar = scored.take(5).map { it.second }
        return ReadResult(similarFiles = similar)
    }

    /** Run syntax check on a file after editing. */
    fun _checkLint(path: String): LintResult {
        val ext = java.io.File(path).extension.lowercase()
        val linters = mapOf(
            "py" to "python3 -m py_compile {file}",
            "js" to "node --check {file}",
            "ts" to "tsc --noEmit {file}",
            "json" to "python3 -m json.tool {file}",
            "kt" to "kotlinc {file}",
            "java" to "javac {file}")
        val linterCmd = linters[ext]
            ?: return LintResult(skipped = true, message = "No linter for .$ext files")
        val baseCmd = linterCmd.split(" ").first()
        if (!_hasCommand(baseCmd)) {
            return LintResult(skipped = true, message = "$baseCmd not available")
        }
        val cmd = linterCmd.replace("{file}", escapeShellArg(path))
        val result = _exec(cmd, timeout = 30)
        return LintResult(
            success = result.exitCode == 0,
            output = result.stdout.trim().ifEmpty { "" }
        )
    }

    /** Search for files by name pattern (glob-like). */
    fun _searchFiles(pattern: String, path: String, limit: Int, offset: Int): SearchResult {
        val baseDir = java.io.File(resolvePath(path))
        if (!baseDir.exists()) return SearchResult(error = "Path not found: $path")
        val searchPattern = if (!pattern.startsWith("**/") && '/' !in pattern) pattern else pattern.substringAfterLast('/')
        val regex = globToRegex(searchPattern)
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

    data class ExecuteResult(
        val stdout: String = "",
        val exitCode: Int = 0)

}

/**
 * Result from patching a file.
 * Ported from PatchResult in file_operations.py.
 */
data class PatchResult(
    val success: Boolean = false,
    val diff: String = "",
    val filesModified: List<String> = emptyList(),
    val filesCreated: List<String> = emptyList(),
    val filesDeleted: List<String> = emptyList(),
    val lint: Map<String, Any>? = null,
    val error: String? = null
) {
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

/**
 * File operations implemented via shell commands.
 * Works with ANY terminal backend that has execute(command, cwd) method.
 * Ported from ShellFileOperations in file_operations.py.
 *
 * On Android, this delegates to the local file system since subprocess-based
 * terminal environments are handled differently.
 */
class ShellFileOperations(
    private val terminalEnv: Any? = null,
    private val cwd: String? = null
) : FileOperations() {

    private val localOps = LocalFileOperations(cwd ?: java.io.File(".").absolutePath)

    override fun readFile(path: String, offset: Int, limit: Int): ReadResult {
        return localOps.readFile(path, offset, limit)
    }

    override fun readFileRaw(path: String): ReadResult {
        return localOps.readFileRaw(path)
    }

    override fun writeFile(path: String, content: String): WriteResult {
        return localOps.writeFile(path, content)
    }

    override fun patchReplace(path: String, oldString: String, newString: String, replaceAll: Boolean): FilePatchResult {
        return localOps.patchReplace(path, oldString, newString, replaceAll)
    }

    override fun patchV4a(patchContent: String): FilePatchResult {
        return localOps.patchV4a(patchContent)
    }

    override fun deleteFile(path: String): WriteResult {
        return localOps.deleteFile(path)
    }

    override fun moveFile(src: String, dst: String): WriteResult {
        return localOps.moveFile(src, dst)
    }

    override fun search(
        pattern: String, path: String, target: String, fileGlob: String?,
        limit: Int, offset: Int, outputMode: String, context: Int
    ): SearchResult {
        return localOps.search(pattern, path, target, fileGlob, limit, offset, outputMode, context)
    }
}
