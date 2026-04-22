package com.xiaomo.hermes.hermes.agent

import java.io.File

/**
 * Context References - @file 引用解析
 * 1:1 对齐 hermes/agent/context_references.py
 *
 * 解析消息中的 @file/path 引用，读取文件内容并注入上下文。
 */

data class FileReference(
    val originalRef: String,   // 原始 @file 引用文本
    val filePath: String,      // 解析后的文件路径
    val content: String,       // 文件内容
    val lineStart: Int? = null, // 起始行号
    val lineEnd: Int? = null,   // 结束行号
    val exists: Boolean = true, // 文件是否存在
    val error: String? = null   // 错误信息
)

class ContextReferences(
    private val workingDir: String = "."
) {

    companion object {
        // @file 引用模式: @file/path:1-10, @file/path, @dir/path
        private val FILE_REF_PATTERN = Regex(
            """@(?:file|dir|folder)[:\s]+([^\s,;!?]+)(?::(\d+)(?:-(\d+))?)?""",
            RegexOption.IGNORE_CASE
        )

        // 最大文件大小（字符数）
        private const val MAX_FILE_SIZE = 100_000

        // 最大读取行数
        private const val MAX_LINES = 500
    }

    /**
     * 从文本中提取所有 @file 引用
     *
     * @param text 输入文本
     * @return 文件引用列表
     */
    fun extractReferences(text: String): List<FileReference> {
        val matches = FILE_REF_PATTERN.findAll(text)
        return matches.map { match ->
            val originalRef = match.value
            val filePath = match.groupValues[1]
            val lineStart = match.groupValues[2].toIntOrNull()
            val lineEnd = match.groupValues[3].toIntOrNull()
            resolveReference(originalRef, filePath, lineStart, lineEnd)
        }.toList()
    }

    /**
     * 解析单个文件引用
     *
     * @param originalRef 原始引用文本
     * @param filePath 文件路径
     * @param lineStart 起始行号
     * @param lineEnd 结束行号
     * @return 文件引用结果
     */
    private fun resolveReference(
        originalRef: String,
        filePath: String,
        lineStart: Int?,
        lineEnd: Int?
    ): FileReference {
        val file = File(workingDir, filePath)
        if (!file.exists()) {
            return FileReference(
                originalRef = originalRef,
                filePath = filePath,
                content = "",
                lineStart = lineStart,
                lineEnd = lineEnd,
                exists = false,
                error = "File not found: $filePath"
            )
        }

        if (!file.isFile) {
            // 目录引用：列出文件
            val files = file.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()
            return FileReference(
                originalRef = originalRef,
                filePath = filePath,
                content = files.joinToString("\n"),
                lineStart = null,
                lineEnd = null,
                exists = true
            )
        }

        if (file.length() > MAX_FILE_SIZE) {
            return FileReference(
                originalRef = originalRef,
                filePath = filePath,
                content = "[File too large: ${file.length()} bytes]",
                lineStart = lineStart,
                lineEnd = lineEnd,
                exists = true,
                error = "File exceeds max size ($MAX_FILE_SIZE chars)"
            )
        }

        return try {
            val lines = file.readLines(Charsets.UTF_8)
            val selectedLines = if (lineStart != null) {
                val start = (lineStart - 1).coerceAtLeast(0)
                val end = (lineEnd ?: lineStart).coerceAtMost(lines.size)
                lines.subList(start, end.coerceAtMost(start + MAX_LINES))
            } else {
                lines.take(MAX_LINES)
            }

            FileReference(
                originalRef = originalRef,
                filePath = filePath,
                content = selectedLines.joinToString("\n"),
                lineStart = lineStart,
                lineEnd = lineEnd,
                exists = true
            )
        } catch (e: Exception) {
            FileReference(
                originalRef = originalRef,
                filePath = filePath,
                content = "",
                lineStart = lineStart,
                lineEnd = lineEnd,
                exists = true,
                error = "Failed to read file: ${e.message}"
            )
        }
    }

    /**
     * 将文本中的 @file 引用替换为实际文件内容
     *
     * @param text 输入文本
     * @return 替换后的文本
     */
    fun resolveAll(text: String): String {
        val references = extractReferences(text)
        var result = text
        for (ref in references) {
            val replacement = if (ref.exists && ref.error == null) {
                "File: ${ref.filePath}\n```\n${ref.content}\n```"
            } else {
                "File: ${ref.filePath}\n[Error: ${ref.error ?: "File not found"}]"
            }
            result = result.replace(ref.originalRef, replacement)
        }
        return result
    }

    /**
     * 获取文件内容（直接路径）
     *
     * @param path 文件路径
     * @param lineStart 起始行号（可选）
     * @param lineEnd 结束行号（可选）
     * @return 文件内容，失败返回 null
     */
    fun readFile(path: String, lineStart: Int? = null, lineEnd: Int? = null): String? {
        val ref = resolveReference("@file:$path", path, lineStart, lineEnd)
        return if (ref.exists && ref.error == null) ref.content else null
    }


}

data class ContextReference(
    val type: String = "",
    val path: String = "",
    val content: String = "",
    val lineRange: Pair<Int, Int>? = null
)

data class ContextReferenceResult(
    val references: List<ContextReference> = emptyList(),
    val resolvedText: String = "",
    val errorCount: Int = 0
)
