package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import java.io.File

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
    val fileOps = ops ?: LocalFileOperations()
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
    val fileOps = ops ?: LocalFileOperations()
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
fun patchTool(
    path: String,
    oldString: String,
    newString: String,
    replaceAll: Boolean = false,
    ops: FileOperations? = null): String {
    val fileOps = ops ?: LocalFileOperations()
    val result = fileOps.patchReplace(path, oldString, newString, replaceAll)
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
fun searchTool(
    pattern: String,
    path: String = ".",
    fileGlob: String? = null,
    limit: Int = 50,
    ops: FileOperations? = null): String {
    val fileOps = ops ?: LocalFileOperations()
    val result = fileOps.search(pattern, path, fileGlob = fileGlob, limit = limit)
    return _fileToolsGson.toJson(result.toMap())
}

/**
 * 列出目录内容（Android 端 list_dir 工具，Python 侧没有对应函数）。
 */
fun listDir(path: String, maxDepth: Int = 2, showHidden: Boolean = false): String {
    val dir = File(path)
    if (!dir.exists()) return _fileToolsGson.toJson(mapOf("error" to "Directory not found: $path"))
    if (!dir.isDirectory) return _fileToolsGson.toJson(mapOf("error" to "Not a directory: $path"))

    val entries = mutableListOf<Map<String, Any>>()
    fun walk(file: File, depth: Int) {
        if (depth > maxDepth) return
        val children = file.listFiles()?.sortedBy { it.name } ?: return
        for (child in children) {
            if (!showHidden && child.name.startsWith(".")) continue
            val entry = mutableMapOf<String, Any>(
                "name" to child.name,
                "path" to child.absolutePath,
                "type" to if (child.isDirectory) "directory" else "file"
            )
            if (child.isFile) entry["size"] = child.length()
            entries.add(entry)
            if (child.isDirectory && depth < maxDepth) {
                walk(child, depth + 1)
            }
        }
    }
    walk(dir, 1)
    return _fileToolsGson.toJson(mapOf("path" to dir.absolutePath, "entries" to entries, "count" to entries.size))
}
