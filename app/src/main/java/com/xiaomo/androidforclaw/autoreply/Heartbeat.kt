package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw Source Reference:
 * - src/auto-reply/heartbeat.ts
 *
 * Heartbeat task management: parsing HEARTBEAT.md, scheduling,
 * token stripping, prompt resolution.
 */

// ============================================================================
// Types (aligned with OpenClaw heartbeat.ts)
// ============================================================================

/**
 * A parsed heartbeat task definition.
 * Aligned with OpenClaw HeartbeatTask.
 */
data class HeartbeatTask(
    val name: String,
    val interval: String,
    val prompt: String
)

/**
 * Mode for heartbeat token stripping.
 * Aligned with OpenClaw StripHeartbeatMode.
 */
enum class StripHeartbeatMode { HEARTBEAT, MESSAGE }

/**
 * Result of stripping the heartbeat token from text.
 */
data class StripHeartbeatResult(
    val shouldSkip: Boolean,
    val text: String,
    val didStrip: Boolean
)

// ============================================================================
// Constants (aligned with OpenClaw heartbeat.ts)
// ============================================================================

val HEARTBEAT_PROMPT =
    "Read HEARTBEAT.md if it exists (workspace context). Follow it strictly. Do not infer or repeat old tasks from prior chats. If nothing needs attention, reply HEARTBEAT_OK."

const val DEFAULT_HEARTBEAT_EVERY = "30m"
const val DEFAULT_HEARTBEAT_ACK_MAX_CHARS = 300

// ============================================================================
// HEARTBEAT.md content analysis (aligned with OpenClaw)
// ============================================================================

/**
 * Check if HEARTBEAT.md content is "effectively empty" - no actionable tasks.
 * Aligned with OpenClaw isHeartbeatContentEffectivelyEmpty.
 */
fun isHeartbeatContentEffectivelyEmpty(content: String?): Boolean {
    if (content == null) return false

    val lines = content.split("\n")
    for (line in lines) {
        val trimmed = line.trim()
        // Skip empty lines
        if (trimmed.isEmpty()) continue
        // Skip markdown header lines (# followed by space or EOL)
        if (Regex("^#+(?:\\s|$)").containsMatchIn(trimmed)) continue
        // Skip empty markdown list items like "- [ ]" or "* [ ]" or just "- "
        if (Regex("^[-*+]\\s*(?:\\[[\\sXx]?]\\s*)?$").containsMatchIn(trimmed)) continue
        // Found a non-empty, non-comment line - there's actionable content
        return false
    }
    // All lines were either empty or comments
    return true
}

// ============================================================================
// Prompt resolution (aligned with OpenClaw)
// ============================================================================

/**
 * Resolve the heartbeat prompt from config or use the default.
 * Aligned with OpenClaw resolveHeartbeatPrompt.
 */
fun resolveHeartbeatPrompt(raw: String? = null): String {
    val trimmed = raw?.trim() ?: ""
    return trimmed.ifEmpty { HEARTBEAT_PROMPT }
}

// ============================================================================
// Token stripping (aligned with OpenClaw stripHeartbeatToken)
// ============================================================================

private fun stripTokenAtEdges(raw: String): Pair<String, Boolean> {
    var text = raw.trim()
    if (text.isEmpty()) return "" to false

    val token = HEARTBEAT_TOKEN
    val tokenAtEndWithTrailingPunct = Regex("${Regex.escape(token)}[^\\w]{0,4}$")
    if (!text.contains(token)) return text to false

    var didStrip = false
    var changed = true
    while (changed) {
        changed = false
        val next = text.trim()

        // Strip token at start
        if (next.startsWith(token)) {
            val after = next.substring(token.length).trimStart()
            text = after
            didStrip = true
            changed = true
            continue
        }

        // Strip token at end (with up to 4 trailing non-word chars)
        if (tokenAtEndWithTrailingPunct.containsMatchIn(next)) {
            val idx = next.lastIndexOf(token)
            val before = next.substring(0, idx).trimEnd()
            if (before.isEmpty()) {
                text = ""
            } else {
                val after = next.substring(idx + token.length).trimStart()
                text = "$before$after".trimEnd()
            }
            didStrip = true
            changed = true
        }
    }

    val collapsed = text.replace(Regex("\\s+"), " ").trim()
    return collapsed to didStrip
}

/**
 * Strip HEARTBEAT_OK token from text, returning whether the reply should be skipped.
 * Aligned with OpenClaw stripHeartbeatToken.
 */
fun stripHeartbeatToken(
    raw: String?,
    mode: StripHeartbeatMode = StripHeartbeatMode.MESSAGE,
    maxAckChars: Int? = null
): StripHeartbeatResult {
    if (raw.isNullOrBlank()) {
        return StripHeartbeatResult(shouldSkip = true, text = "", didStrip = false)
    }
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return StripHeartbeatResult(shouldSkip = true, text = "", didStrip = false)
    }

    val resolvedMaxAckChars = maxOf(0, maxAckChars ?: DEFAULT_HEARTBEAT_ACK_MAX_CHARS)

    // Normalize lightweight markup so HEARTBEAT_OK wrapped in HTML/Markdown still strips
    fun stripMarkup(text: String): String {
        return text
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("^[*`~_]+"), "")
            .replace(Regex("[*`~_]+$"), "")
    }

    val trimmedNormalized = stripMarkup(trimmed)
    val hasToken = trimmed.contains(HEARTBEAT_TOKEN) || trimmedNormalized.contains(HEARTBEAT_TOKEN)
    if (!hasToken) {
        return StripHeartbeatResult(shouldSkip = false, text = trimmed, didStrip = false)
    }

    val (strippedOrigText, strippedOrigDid) = stripTokenAtEdges(trimmed)
    val (strippedNormText, strippedNormDid) = stripTokenAtEdges(trimmedNormalized)

    val pickedText: String
    val pickedDid: Boolean
    if (strippedOrigDid && strippedOrigText.isNotEmpty()) {
        pickedText = strippedOrigText
        pickedDid = strippedOrigDid
    } else {
        pickedText = strippedNormText
        pickedDid = strippedNormDid
    }

    if (!pickedDid) {
        return StripHeartbeatResult(shouldSkip = false, text = trimmed, didStrip = false)
    }

    if (pickedText.isEmpty()) {
        return StripHeartbeatResult(shouldSkip = true, text = "", didStrip = true)
    }

    val rest = pickedText.trim()
    if (mode == StripHeartbeatMode.HEARTBEAT) {
        if (rest.length <= resolvedMaxAckChars) {
            return StripHeartbeatResult(shouldSkip = true, text = "", didStrip = true)
        }
    }

    return StripHeartbeatResult(shouldSkip = false, text = rest, didStrip = true)
}

// ============================================================================
// Task parsing (aligned with OpenClaw parseHeartbeatTasks)
// ============================================================================

/**
 * Parse heartbeat tasks from HEARTBEAT.md content.
 * Aligned with OpenClaw parseHeartbeatTasks.
 */
fun parseHeartbeatTasks(content: String): List<HeartbeatTask> {
    val tasks = mutableListOf<HeartbeatTask>()
    val lines = content.split("\n")
    var inTasksBlock = false

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Detect tasks block start
        if (trimmed == "tasks:") {
            inTasksBlock = true
            i++
            continue
        }

        if (!inTasksBlock) {
            i++
            continue
        }

        // End of tasks block
        val isTaskField = trimmed.startsWith("interval:") ||
            trimmed.startsWith("prompt:") ||
            trimmed.startsWith("- name:")
        if (!isTaskField &&
            !trimmed.startsWith(" ") &&
            !trimmed.startsWith("\t") &&
            trimmed.isNotEmpty() &&
            !trimmed.startsWith("-")
        ) {
            inTasksBlock = false
            i++
            continue
        }

        // Parse task entry
        if (trimmed.startsWith("- name:")) {
            val name = trimmed.removePrefix("- name:").trim()
                .removeSurrounding("\"").removeSurrounding("'")
            var interval = ""
            var prompt = ""

            // Look ahead for interval and prompt
            var j = i + 1
            while (j < lines.size) {
                val nextLine = lines[j]
                val nextTrimmed = nextLine.trim()

                if (nextTrimmed.startsWith("- name:")) break

                if (nextTrimmed.startsWith("interval:")) {
                    interval = nextTrimmed.removePrefix("interval:").trim()
                        .removeSurrounding("\"").removeSurrounding("'")
                } else if (nextTrimmed.startsWith("prompt:")) {
                    prompt = nextTrimmed.removePrefix("prompt:").trim()
                        .removeSurrounding("\"").removeSurrounding("'")
                } else if (!nextTrimmed.startsWith(" ") && !nextTrimmed.startsWith("\t") && nextTrimmed.isNotEmpty()) {
                    inTasksBlock = false
                    break
                }
                j++
            }

            if (name.isNotEmpty() && interval.isNotEmpty() && prompt.isNotEmpty()) {
                tasks.add(HeartbeatTask(name = name, interval = interval, prompt = prompt))
            }
        }

        i++
    }

    return tasks
}

// ============================================================================
// Task scheduling (aligned with OpenClaw isTaskDue)
// ============================================================================

/**
 * Check if a task is due based on its interval and last run time.
 * Aligned with OpenClaw isTaskDue.
 */
fun isTaskDue(lastRunMs: Long?, interval: String, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (lastRunMs == null) return true  // Never run, always due

    val intervalMs = parseDurationMs(interval) ?: return false
    return nowMs - lastRunMs >= intervalMs
}

/**
 * Parse a duration string (e.g. "30m", "1h", "2d") to milliseconds.
 * Aligned with OpenClaw parseDurationMs.
 */
fun parseDurationMs(duration: String, defaultUnit: String = "m"): Long? {
    val trimmed = duration.trim()
    if (trimmed.isEmpty()) return null

    val match = Regex("^(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]*)$").find(trimmed) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].ifEmpty { defaultUnit }.lowercase()

    val multiplier = when (unit) {
        "ms", "millisecond", "milliseconds" -> 1L
        "s", "sec", "second", "seconds" -> 1000L
        "m", "min", "minute", "minutes" -> 60 * 1000L
        "h", "hr", "hour", "hours" -> 60 * 60 * 1000L
        "d", "day", "days" -> 24 * 60 * 60 * 1000L
        "w", "week", "weeks" -> 7 * 24 * 60 * 60 * 1000L
        else -> return null
    }

    return (value * multiplier).toLong()
}
