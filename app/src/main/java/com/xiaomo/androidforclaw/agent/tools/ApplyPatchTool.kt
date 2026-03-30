package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/apply-patch.ts
 * - ../openclaw/src/agents/apply-patch-update.ts
 *
 * AndroidForClaw adaptation: multi-file patch application tool.
 */

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.io.File

// ── Patch Format Markers (aligned with OpenClaw) ──

private const val BEGIN_PATCH_MARKER = "*** Begin Patch"
private const val END_PATCH_MARKER = "*** End Patch"
private const val ADD_FILE_MARKER = "*** Add File: "
private const val DELETE_FILE_MARKER = "*** Delete File: "
private const val UPDATE_FILE_MARKER = "*** Update File: "
private const val MOVE_TO_MARKER = "*** Move to: "
private const val EOF_MARKER = "*** End of File"
private const val CHANGE_CONTEXT_MARKER = "@@ "
private const val EMPTY_CHANGE_CONTEXT_MARKER = "@@"

// ── Hunk Types (aligned with OpenClaw) ──

sealed class Hunk {
    data class AddFile(val path: String, val contents: String) : Hunk()
    data class DeleteFile(val path: String) : Hunk()
    data class UpdateFile(
        val path: String,
        val movePath: String? = null,
        val chunks: List<UpdateFileChunk>
    ) : Hunk()
}

data class UpdateFileChunk(
    val changeContext: String? = null,
    val oldLines: List<String>,
    val newLines: List<String>,
    val isEndOfFile: Boolean = false
)

data class ApplyPatchSummary(
    val added: List<String> = emptyList(),
    val modified: List<String> = emptyList(),
    val deleted: List<String> = emptyList()
)

data class ApplyPatchResult(
    val summary: ApplyPatchSummary,
    val text: String
)

/**
 * ApplyPatchTool — multi-file patch application.
 * Aligned with OpenClaw apply-patch.ts + apply-patch-update.ts.
 */
class ApplyPatchTool(
    private val workspace: File? = null
) : Tool {

    companion object {
        private const val TAG = "ApplyPatchTool"
    }

    override val name = "apply_patch"
    override val description = "Apply a patch to create, modify, or delete files"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "patch" to PropertySchema("string", "The patch content in OpenClaw patch format")
                    ),
                    required = listOf("patch")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val patchText = args["patch"] as? String
            ?: return ToolResult.error("'patch' parameter is required")

        return try {
            val result = applyPatch(patchText, workspace)
            ToolResult.success(result.text)
        } catch (e: Exception) {
            Log.e(TAG, "Patch application failed: ${e.message}")
            ToolResult.error("Patch application failed: ${e.message}")
        }
    }

    /**
     * Apply a patch from text.
     * Aligned with OpenClaw applyPatch.
     */
    fun applyPatch(input: String, workingDir: File? = null): ApplyPatchResult {
        val hunks = parsePatchText(input)
        val added = mutableListOf<String>()
        val modified = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val output = StringBuilder()

        val baseDir = workingDir ?: File("/")

        for (hunk in hunks) {
            when (hunk) {
                is Hunk.AddFile -> {
                    val file = File(baseDir, hunk.path)
                    file.parentFile?.mkdirs()
                    file.writeText(hunk.contents)
                    added.add(hunk.path)
                    output.appendLine("Added: ${hunk.path}")
                }
                is Hunk.DeleteFile -> {
                    val file = File(baseDir, hunk.path)
                    if (file.exists()) {
                        file.delete()
                        deleted.add(hunk.path)
                        output.appendLine("Deleted: ${hunk.path}")
                    } else {
                        output.appendLine("Warning: ${hunk.path} not found for deletion")
                    }
                }
                is Hunk.UpdateFile -> {
                    val file = File(baseDir, hunk.path)
                    if (!file.exists()) {
                        output.appendLine("Error: ${hunk.path} not found for update")
                        continue
                    }
                    val original = file.readText()
                    val updated = applyUpdateHunk(original, hunk.chunks, hunk.path)

                    val targetPath = hunk.movePath ?: hunk.path
                    val targetFile = File(baseDir, targetPath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(updated)

                    if (hunk.movePath != null && hunk.movePath != hunk.path) {
                        file.delete()
                        modified.add("${hunk.path} → ${hunk.movePath}")
                    } else {
                        modified.add(hunk.path)
                    }
                    output.appendLine("Updated: $targetPath")
                }
            }
        }

        return ApplyPatchResult(
            summary = ApplyPatchSummary(added = added, modified = modified, deleted = deleted),
            text = output.toString().trimEnd()
        )
    }

    // ── Parsing (aligned with OpenClaw parsePatchText) ──

    /**
     * Parse patch text into hunks.
     * Aligned with OpenClaw parsePatchText.
     */
    fun parsePatchText(input: String): List<Hunk> {
        val lines = checkPatchBoundariesLenient(input)
        val hunks = mutableListOf<Hunk>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith(ADD_FILE_MARKER) -> {
                    val path = line.removePrefix(ADD_FILE_MARKER).trim()
                    i++
                    val contents = StringBuilder()
                    while (i < lines.size && !lines[i].startsWith("***")) {
                        val addLine = lines[i]
                        if (addLine.startsWith("+")) {
                            contents.appendLine(addLine.substring(1))
                        }
                        i++
                    }
                    hunks.add(Hunk.AddFile(path = path, contents = contents.toString()))
                }
                line.startsWith(DELETE_FILE_MARKER) -> {
                    val path = line.removePrefix(DELETE_FILE_MARKER).trim()
                    hunks.add(Hunk.DeleteFile(path = path))
                    i++
                }
                line.startsWith(UPDATE_FILE_MARKER) -> {
                    val path = line.removePrefix(UPDATE_FILE_MARKER).trim()
                    i++

                    // Optional move
                    var movePath: String? = null
                    if (i < lines.size && lines[i].startsWith(MOVE_TO_MARKER)) {
                        movePath = lines[i].removePrefix(MOVE_TO_MARKER).trim()
                        i++
                    }

                    // Parse chunks
                    val chunks = mutableListOf<UpdateFileChunk>()
                    while (i < lines.size && !lines[i].startsWith("*** ") || (i < lines.size && (lines[i].startsWith(CHANGE_CONTEXT_MARKER) || lines[i] == EMPTY_CHANGE_CONTEXT_MARKER || lines[i] == EOF_MARKER))) {
                        if (lines[i].startsWith(CHANGE_CONTEXT_MARKER) || lines[i] == EMPTY_CHANGE_CONTEXT_MARKER) {
                            val result = parseUpdateChunk(lines, i)
                            chunks.add(result.first)
                            i = result.second
                        } else if (lines[i] == EOF_MARKER) {
                            i++
                        } else {
                            break
                        }
                    }
                    hunks.add(Hunk.UpdateFile(path = path, movePath = movePath, chunks = chunks))
                }
                else -> i++
            }
        }

        return hunks
    }

    private fun parseUpdateChunk(lines: List<String>, startIndex: Int): Pair<UpdateFileChunk, Int> {
        var i = startIndex
        val contextLine = lines[i]
        val changeContext = if (contextLine == EMPTY_CHANGE_CONTEXT_MARKER) {
            null
        } else {
            contextLine.removePrefix(CHANGE_CONTEXT_MARKER).trim().takeIf { it.isNotEmpty() }
        }
        i++

        val oldLines = mutableListOf<String>()
        val newLines = mutableListOf<String>()
        var isEndOfFile = false

        while (i < lines.size) {
            val line = lines[i]
            when {
                line == EOF_MARKER -> {
                    isEndOfFile = true
                    i++
                    break
                }
                line.startsWith(CHANGE_CONTEXT_MARKER) || line == EMPTY_CHANGE_CONTEXT_MARKER -> break
                line.startsWith("***") -> break
                line.startsWith("+") -> {
                    newLines.add(line.substring(1))
                    i++
                }
                line.startsWith("-") -> {
                    oldLines.add(line.substring(1))
                    i++
                }
                line.startsWith(" ") -> {
                    val content = line.substring(1)
                    oldLines.add(content)
                    newLines.add(content)
                    i++
                }
                line.isEmpty() || line.isBlank() -> {
                    oldLines.add("")
                    newLines.add("")
                    i++
                }
                else -> {
                    // Treat as context line
                    oldLines.add(line)
                    newLines.add(line)
                    i++
                }
            }
        }

        return UpdateFileChunk(
            changeContext = changeContext,
            oldLines = oldLines,
            newLines = newLines,
            isEndOfFile = isEndOfFile
        ) to i
    }

    /**
     * Check and strip patch boundaries, with lenient EOF wrapper handling.
     * Aligned with OpenClaw checkPatchBoundariesLenient.
     */
    private fun checkPatchBoundariesLenient(input: String): List<String> {
        var lines = input.trim().split(Regex("\\r?\\n"))

        // Strict check
        if (lines.isNotEmpty() && lines.first().trim() == BEGIN_PATCH_MARKER &&
            lines.last().trim() == END_PATCH_MARKER
        ) {
            return lines.drop(1).dropLast(1)
        }

        // Lenient: strip <<EOF wrappers
        val first = lines.firstOrNull()?.trim() ?: ""
        if (first.startsWith("<<") && (first.contains("EOF") || first.contains("'EOF'") || first.contains("\"EOF\""))) {
            lines = lines.drop(1)
        }
        val last = lines.lastOrNull()?.trim() ?: ""
        if (last == "EOF") {
            lines = lines.dropLast(1)
        }

        // Retry strict check after stripping
        if (lines.isNotEmpty() && lines.first().trim() == BEGIN_PATCH_MARKER &&
            lines.last().trim() == END_PATCH_MARKER
        ) {
            return lines.drop(1).dropLast(1)
        }

        // If no markers at all, return as-is
        return lines
    }

    // ── Update Application (aligned with OpenClaw apply-patch-update.ts) ──

    /**
     * Apply update chunks to original file content.
     * Aligned with OpenClaw applyUpdateHunk.
     */
    private fun applyUpdateHunk(original: String, chunks: List<UpdateFileChunk>, filePath: String): String {
        val originalLines = original.split("\n").toMutableList()
        // Pop trailing empty line (OpenClaw behavior)
        if (originalLines.isNotEmpty() && originalLines.last().isEmpty()) {
            originalLines.removeAt(originalLines.lastIndex)
        }

        val replacements = computeReplacements(originalLines, filePath, chunks)

        // Sort by start index
        val sorted = replacements.sortedBy { it.startIndex }

        // Apply in reverse order to preserve indices
        val result = originalLines.toMutableList()
        for (replacement in sorted.reversed()) {
            val end = replacement.startIndex + replacement.patternLength
            for (j in (end - 1) downTo replacement.startIndex) {
                if (j < result.size) result.removeAt(j)
            }
            result.addAll(replacement.startIndex, replacement.newLines)
        }

        // Ensure trailing newline
        val joined = result.joinToString("\n")
        return if (joined.endsWith("\n")) joined else "$joined\n"
    }

    private data class Replacement(
        val startIndex: Int,
        val patternLength: Int,
        val newLines: List<String>
    )

    private fun computeReplacements(
        originalLines: List<String>,
        filePath: String,
        chunks: List<UpdateFileChunk>
    ): List<Replacement> {
        val replacements = mutableListOf<Replacement>()
        var lineIndex = 0

        for (chunk in chunks) {
            // Seek to change context if present
            if (chunk.changeContext != null) {
                val contextIndex = seekLine(originalLines, chunk.changeContext, lineIndex)
                if (contextIndex >= 0) {
                    lineIndex = contextIndex + 1
                }
            }

            if (chunk.oldLines.isEmpty()) {
                // Pure insertion at current position (or end of file)
                val insertAt = if (chunk.isEndOfFile) originalLines.size else lineIndex
                replacements.add(Replacement(insertAt, 0, chunk.newLines))
                continue
            }

            // Find the old lines sequence
            val startAt = if (chunk.isEndOfFile) {
                maxOf(0, originalLines.size - chunk.oldLines.size)
            } else {
                lineIndex
            }

            val foundIndex = seekSequence(originalLines, chunk.oldLines, startAt)
            if (foundIndex >= 0) {
                replacements.add(Replacement(foundIndex, chunk.oldLines.size, chunk.newLines))
                lineIndex = foundIndex + chunk.oldLines.size
            } else {
                // Retry without trailing empty lines
                val trimmedOld = chunk.oldLines.dropLastWhile { it.isEmpty() }
                val trimmedNew = chunk.newLines.dropLastWhile { it.isEmpty() }
                if (trimmedOld.isNotEmpty()) {
                    val retryIndex = seekSequence(originalLines, trimmedOld, startAt)
                    if (retryIndex >= 0) {
                        replacements.add(Replacement(retryIndex, trimmedOld.size, trimmedNew))
                        lineIndex = retryIndex + trimmedOld.size
                    } else {
                        Log.w(TAG, "Could not find matching sequence in $filePath at line $startAt")
                    }
                }
            }
        }

        return replacements
    }

    /**
     * Seek a single line in the original, with progressive matching.
     * Aligned with OpenClaw seekSequence for single-line context.
     */
    private fun seekLine(lines: List<String>, target: String, startFrom: Int): Int {
        // Pass 1: exact match
        for (i in startFrom until lines.size) {
            if (lines[i] == target) return i
        }
        // Pass 2: trimEnd match
        val targetTrimmed = target.trimEnd()
        for (i in startFrom until lines.size) {
            if (lines[i].trimEnd() == targetTrimmed) return i
        }
        // Pass 3: trim match
        val targetFullTrim = target.trim()
        for (i in startFrom until lines.size) {
            if (lines[i].trim() == targetFullTrim) return i
        }
        return -1
    }

    /**
     * Seek a sequence of lines in the original, with progressive matching (4 passes).
     * Aligned with OpenClaw seekSequence.
     */
    private fun seekSequence(lines: List<String>, pattern: List<String>, startFrom: Int): Int {
        if (pattern.isEmpty()) return startFrom
        val maxStart = lines.size - pattern.size

        // Pass 1: exact match
        for (i in startFrom..maxStart) {
            if (matchesExact(lines, pattern, i)) return i
        }
        // Pass 2: trimEnd match
        for (i in startFrom..maxStart) {
            if (matchesTrimEnd(lines, pattern, i)) return i
        }
        // Pass 3: trim match
        for (i in startFrom..maxStart) {
            if (matchesTrim(lines, pattern, i)) return i
        }
        // Pass 4: normalized punctuation match
        for (i in startFrom..maxStart) {
            if (matchesNormalized(lines, pattern, i)) return i
        }
        return -1
    }

    private fun matchesExact(lines: List<String>, pattern: List<String>, at: Int): Boolean {
        for (j in pattern.indices) {
            if (lines[at + j] != pattern[j]) return false
        }
        return true
    }

    private fun matchesTrimEnd(lines: List<String>, pattern: List<String>, at: Int): Boolean {
        for (j in pattern.indices) {
            if (lines[at + j].trimEnd() != pattern[j].trimEnd()) return false
        }
        return true
    }

    private fun matchesTrim(lines: List<String>, pattern: List<String>, at: Int): Boolean {
        for (j in pattern.indices) {
            if (lines[at + j].trim() != pattern[j].trim()) return false
        }
        return true
    }

    private fun matchesNormalized(lines: List<String>, pattern: List<String>, at: Int): Boolean {
        for (j in pattern.indices) {
            if (normalizePunctuation(lines[at + j].trim()) != normalizePunctuation(pattern[j].trim())) return false
        }
        return true
    }

    /**
     * Normalize Unicode punctuation for fuzzy matching.
     * Aligned with OpenClaw normalizePunctuation.
     */
    private fun normalizePunctuation(text: String): String {
        return text
            .replace('\u2013', '-')  // en dash
            .replace('\u2014', '-')  // em dash
            .replace('\u2018', '\'') // left single quote
            .replace('\u2019', '\'') // right single quote
            .replace('\u201C', '"')  // left double quote
            .replace('\u201D', '"')  // right double quote
            .replace('\u00A0', ' ')  // non-breaking space
            .replace('\u2007', ' ')  // figure space
            .replace('\u202F', ' ')  // narrow no-break space
    }
}
