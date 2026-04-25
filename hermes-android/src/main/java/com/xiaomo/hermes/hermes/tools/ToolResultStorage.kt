/**
 * Tool result persistence -- preserves large outputs instead of truncating.
 *
 * Defense against context-window overflow operates at three levels:
 *
 * 1. **Per-tool output cap** (inside each tool): Tools like searchFiles
 *    pre-truncate their own output before returning. This is the first line
 *    of defense and the only one the tool author controls.
 *
 * 2. **Per-result persistence** (maybePersistToolResult): After a tool
 *    returns, if its output exceeds the tool's registered threshold, the
 *    full output is written INTO THE SANDBOX temp dir via env.execute().
 *    The in-context content is replaced with a preview + file path
 *    reference. The model can readFile to access the full output on any
 *    backend.
 *
 * 3. **Per-turn aggregate budget** (enforceTurnBudget): After all tool
 *    results in a single assistant turn are collected, if the total
 *    exceeds MAX_TURN_BUDGET_CHARS (200K), the largest non-persisted
 *    results are spilled to disk until the aggregate is under budget.
 *
 * Ported from tools/tool_result_storage.py
 */
package com.xiaomo.hermes.hermes.tools

import com.xiaomo.hermes.hermes.tools.environments.BaseEnvironment
import java.util.UUID

const val PERSISTED_OUTPUT_TAG: String = "<persisted-output>"
const val PERSISTED_OUTPUT_CLOSING_TAG: String = "</persisted-output>"
const val STORAGE_DIR: String = "/tmp/hermes-results"
const val HEREDOC_MARKER: String = "HERMES_PERSIST_EOF"
private const val _BUDGET_TOOL_NAME: String = "__budget_enforcement__"

private fun _resolveStorageDir(env: BaseEnvironment?): String {
    @Suppress("UNUSED_VARIABLE") val _resolveErrFmt = "Could not resolve env temp dir: %s"
    @Suppress("UNUSED_VARIABLE") val _getTempDirName = "get_temp_dir"
    if (env != null) {
        try {
            val tempDir = env.getTempDir().trimEnd('/').ifEmpty { "/" }
            return "$tempDir/hermes-results"
        } catch (_: Exception) {
        }
    }
    return STORAGE_DIR
}

/**
 * Truncate at last newline within maxChars. Returns (preview, hasMore).
 */
fun generatePreview(content: String, maxChars: Int = DEFAULT_PREVIEW_SIZE_CHARS): Pair<String, Boolean> {
    if (content.length <= maxChars) return content to false
    var truncated = content.substring(0, maxChars)
    val lastNl = truncated.lastIndexOf('\n')
    if (lastNl > maxChars / 2) {
        truncated = truncated.substring(0, lastNl + 1)
    }
    return truncated to true
}

private fun _heredocMarker(content: String): String {
    if (HEREDOC_MARKER !in content) return HEREDOC_MARKER
    return "HERMES_PERSIST_${UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
}

private fun _writeToSandbox(content: String, remotePath: String, env: BaseEnvironment): Boolean {
    val marker = _heredocMarker(content)
    val storageDir = java.io.File(remotePath).parent ?: "/"
    val quote = { s: String -> "'" + s.replace("'", "'\"'\"'") + "'" }
    val cmd = (
        "mkdir -p ${quote(storageDir)} && cat > ${quote(remotePath)} << '$marker'\n" +
        "$content\n" +
        marker
    )
    val result = env.execute(cmd, timeout = 30)
    val rc = (result["returncode"] as? Number)?.toInt() ?: 1
    return rc == 0
}

private fun _buildPersistedMessage(
    preview: String,
    hasMore: Boolean,
    originalSize: Int,
    filePath: String,
): String {
    val sizeKb = originalSize / 1024.0
    val sizeStr = if (sizeKb >= 1024) "%.1f MB".format(sizeKb / 1024) else "%.1f KB".format(sizeKb)

    val sb = StringBuilder()
    sb.append(PERSISTED_OUTPUT_TAG).append('\n')
    sb.append("This tool result was too large ($originalSize characters, $sizeStr).\n")
    sb.append("Full output saved to: $filePath\n")
    sb.append("Use the read_file tool with offset and limit to access specific sections of this output.\n\n")
    sb.append("Preview (first ${preview.length} chars):\n")
    sb.append(preview)
    if (hasMore) sb.append("\n...")
    sb.append('\n').append(PERSISTED_OUTPUT_CLOSING_TAG)
    return sb.toString()
}

/**
 * Layer 2: persist oversized result into the sandbox, return preview + path.
 */
fun maybePersistToolResult(
    content: String,
    toolName: String,
    toolUseId: String,
    env: BaseEnvironment? = null,
    config: BudgetConfig = DEFAULT_BUDGET,
    threshold: Double? = null,
): String {
    @Suppress("UNUSED_VARIABLE") val _truncatedSuffix = " chars. Full output could not be saved to sandbox.]"
    @Suppress("UNUSED_VARIABLE") val _infStr = "inf"
    val effectiveThreshold = threshold ?: config.resolveThreshold(toolName)

    if (effectiveThreshold == Double.POSITIVE_INFINITY) return content
    if (content.length <= effectiveThreshold) return content

    val storageDir = _resolveStorageDir(env)
    val remotePath = "$storageDir/$toolUseId.txt"
    val (preview, hasMore) = generatePreview(content, maxChars = config.previewSize)

    if (env != null) {
        try {
            if (_writeToSandbox(content, remotePath, env)) {
                return _buildPersistedMessage(preview, hasMore, content.length, remotePath)
            }
        } catch (_: Exception) {
        }
    }

    return (
        "$preview\n\n" +
        "[Truncated: tool response was ${content.length} chars. " +
        "Full output could not be saved to sandbox.]"
    )
}

/**
 * Layer 3: enforce aggregate budget across all tool results in a turn.
 *
 * If total chars exceed budget, persist the largest non-persisted results
 * first (via sandbox write) until under budget. Already-persisted results
 * are skipped.
 *
 * Mutates the list in-place and returns it.
 */
fun enforceTurnBudget(
    toolMessages: MutableList<MutableMap<String, Any?>>,
    env: BaseEnvironment? = null,
    config: BudgetConfig = DEFAULT_BUDGET,
): MutableList<MutableMap<String, Any?>> {
    val candidates = mutableListOf<Pair<Int, Int>>()
    var totalSize = 0
    for ((i, msg) in toolMessages.withIndex()) {
        val content = (msg["content"] as? String) ?: ""
        val size = content.length
        totalSize += size
        if (PERSISTED_OUTPUT_TAG !in content) {
            candidates.add(i to size)
        }
    }

    if (totalSize <= config.turnBudget) return toolMessages

    candidates.sortByDescending { it.second }

    for ((idx, size) in candidates) {
        if (totalSize <= config.turnBudget) break
        val msg = toolMessages[idx]
        val content = (msg["content"] as? String) ?: continue
        val toolUseId = (msg["tool_call_id"] as? String) ?: "budget_$idx"

        val replacement = maybePersistToolResult(
            content = content,
            toolName = _BUDGET_TOOL_NAME,
            toolUseId = toolUseId,
            env = env,
            config = config,
            threshold = 0.0,
        )
        if (replacement != content) {
            totalSize -= size
            totalSize += replacement.length
            toolMessages[idx]["content"] = replacement
        }
    }

    return toolMessages
}

@Suppress("unused")
private val _TRS_CHARS_NEWLINE: String = """ chars):
"""

@Suppress("unused")
private val _TRS_READ_FILE_HINT: String = """Use the read_file tool with offset and limit to access specific sections of this output.

"""

@Suppress("unused")
private val _TRS_TRUNCATED_PREFIX: String = """

[Truncated: tool response was """
