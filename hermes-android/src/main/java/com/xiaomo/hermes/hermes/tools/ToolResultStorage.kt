package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Tool result persistence — preserves large outputs instead of truncating.
 * Ported from tool_result_storage.py
 */
object ToolResultStorage {

    const val PERSISTED_OUTPUT_TAG = "<persisted-output>"
    const val PERSISTED_OUTPUT_CLOSING_TAG = "</persisted-output>"
    private const val HEREDOC_MARKER = "HERMES_PERSIST_EOF"
    private const val DEFAULT_PREVIEW_SIZE = 2000
    private const val MAX_TURN_BUDGET = 200_000

    private val TAG = "ToolResultStorage"

    /**
     * Generate a preview of content, truncating at the last newline within maxChars.
     */
    fun generatePreview(content: String, maxChars: Int = DEFAULT_PREVIEW_SIZE): Pair<String, Boolean> {
        if (content.length <= maxChars) return content to false
        val truncated = content.substring(0, maxChars)
        val lastNl = truncated.lastIndexOf('\n')
        val cut = if (lastNl > maxChars / 2) lastNl + 1 else maxChars
        return content.substring(0, cut) to true
    }

    /**
     * Build a persisted-output replacement block.
     */
    fun buildPersistedMessage(preview: String, hasMore: Boolean, originalSize: Int, filePath: String): String {
        val sizeKb = originalSize / 1024.0
        val sizeStr = if (sizeKb >= 1024) "%.1f MB".format(sizeKb / 1024) else "%.1f KB".format(sizeKb)

        return buildString {
            appendLine(PERSISTED_OUTPUT_TAG)
            appendLine("This tool result was too large ($originalSize characters, $sizeStr).")
            appendLine("Full output saved to: $filePath")
            appendLine("Use file read tool with offset and limit to access specific sections.")
            appendLine()
            appendLine("Preview (first ${preview.length} chars):")
            append(preview)
            if (hasMore) append("\n...")
            appendLine()
            append(PERSISTED_OUTPUT_CLOSING_TAG)
        }
    }

    /**
     * Maybe persist a large tool result to a file, returning a preview + reference.
     */
    fun maybePersist(
        content: String,
        toolName: String,
        toolUseId: String,
        storageDir: File,
        threshold: Int = DEFAULT_PREVIEW_SIZE * 5): String {
        if (content.length <= threshold) return content

        val filePath = File(storageDir, "$toolUseId.txt")
        val (preview, hasMore) = generatePreview(content)

        try {
            storageDir.mkdirs()
            filePath.writeText(content, Charsets.UTF_8)
            Log.i(TAG, "Persisted large tool result: $toolName ($toolUseId, ${content.length} chars -> ${filePath.absolutePath})")
            return buildPersistedMessage(preview, hasMore, content.length, filePath.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist tool result: ${e.message}")
            return "$preview\n\n[Truncated: tool response was ${content.length} chars. Full output could not be saved.]"
        }
    }

    /**
     * Enforce aggregate budget across all tool results in a turn.
     */
    fun enforceTurnBudget(results: MutableList<MutableMap<String, String>>, storageDir: File): List<Map<String, String>> {
        var totalSize = 0
        val candidates = mutableListOf<Pair<Int, Int>>()

        for ((i, msg) in results.withIndex()) {
            val content = msg["content"] ?: ""
            val size = content.length
            totalSize += size
            if (!content.contains(PERSISTED_OUTPUT_TAG)) {
                candidates.add(i to size)
            }
        }

        if (totalSize <= MAX_TURN_BUDGET) return results

        candidates.sortByDescending { it.second }

        for ((idx, size) in candidates) {
            if (totalSize <= MAX_TURN_BUDGET) break
            val msg = results[idx]
            val content = msg["content"] ?: continue

            val replacement = maybePersist(content, "__budget__", "budget_$idx", storageDir, 0)
            if (replacement != content) {
                totalSize -= size
                totalSize += replacement.length
                results[idx]["content"] = replacement
            }
        }

        return results
    }


}
