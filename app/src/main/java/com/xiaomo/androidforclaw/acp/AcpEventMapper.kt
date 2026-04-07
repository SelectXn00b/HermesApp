package com.xiaomo.androidforclaw.acp

/**
 * OpenClaw module: acp
 * Source: OpenClaw/src/acp/event-mapper.ts
 *
 * Maps gateway events to ACP protocol events: text extraction,
 * attachment handling, tool call content, tool kind inference, and
 * tool location extraction.
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

data class GatewayAttachment(
    val type: String,
    val mimeType: String,
    val content: String,
)

data class ToolCallLocation(
    val path: String,
    val line: Int? = null,
)

data class ToolCallContentEntry(
    val type: String = "content",
    val text: String,
)

// ---------------------------------------------------------------------------
// Tool kind  (aligned with TS ToolKind)
// ---------------------------------------------------------------------------
enum class ToolKind(val value: String) {
    READ("read"),
    EDIT("edit"),
    DELETE("delete"),
    MOVE("move"),
    SEARCH("search"),
    EXECUTE("execute"),
    FETCH("fetch"),
    OTHER("other"),
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private val TOOL_LOCATION_PATH_KEYS = listOf(
    "path", "filePath", "file_path", "targetPath", "target_path",
    "targetFile", "target_file", "sourcePath", "source_path",
    "destinationPath", "destination_path", "oldPath", "old_path",
    "newPath", "new_path", "outputPath", "output_path",
    "inputPath", "input_path",
)

private val TOOL_LOCATION_LINE_KEYS = listOf(
    "line", "lineNumber", "line_number", "startLine", "start_line",
)

private val TOOL_RESULT_PATH_MARKER_RE = Regex("""^(?:FILE|MEDIA):(.+)$""", RegexOption.MULTILINE)
private const val TOOL_LOCATION_MAX_DEPTH = 4
private const val TOOL_LOCATION_MAX_NODES = 100

// ---------------------------------------------------------------------------
// Inline control char escaping  (aligned with TS escapeInlineControlChars)
// ---------------------------------------------------------------------------
private val INLINE_CONTROL_ESCAPE_MAP = mapOf(
    '\u0000' to "\\0",
    '\r' to "\\r",
    '\n' to "\\n",
    '\t' to "\\t",
    '\u000B' to "\\v",
    '\u000C' to "\\f",
    '\u2028' to "\\u2028",
    '\u2029' to "\\u2029",
)

fun escapeInlineControlChars(value: String): String {
    val sb = StringBuilder(value.length)
    for (char in value) {
        val codePoint = char.code
        val isInlineControl = codePoint <= 0x1f ||
            (codePoint in 0x7f..0x9f) ||
            codePoint == 0x2028 ||
            codePoint == 0x2029

        if (!isInlineControl) {
            sb.append(char)
            continue
        }

        val mapped = INLINE_CONTROL_ESCAPE_MAP[char]
        if (mapped != null) {
            sb.append(mapped)
            continue
        }

        if (codePoint <= 0xff) {
            sb.append("\\x${codePoint.toString(16).padStart(2, '0')}")
        } else {
            sb.append("\\u${codePoint.toString(16).padStart(4, '0')}")
        }
    }
    return sb.toString()
}

private fun escapeResourceTitle(value: String): String {
    return escapeInlineControlChars(value).replace(Regex("[()\\[\\]]")) { "\\${it.value}" }
}

// ---------------------------------------------------------------------------
// Text extraction  (aligned with TS extractTextFromPrompt)
// ---------------------------------------------------------------------------

/**
 * Extract text from ACP content blocks.
 * @param blocks List of content block maps (type, text, resource, uri, title, etc.)
 * @param maxBytes Optional maximum byte limit.
 */
fun extractTextFromContentBlocks(
    blocks: List<Map<String, Any?>>,
    maxBytes: Int? = null,
): String {
    val parts = mutableListOf<String>()
    var totalBytes = 0

    for (block in blocks) {
        val type = block["type"] as? String ?: continue
        val blockText: String? = when (type) {
            "text" -> block["text"] as? String
            "resource" -> {
                @Suppress("UNCHECKED_CAST")
                val resource = block["resource"] as? Map<String, Any?>
                resource?.get("text") as? String
            }
            "resource_link" -> {
                val title = (block["title"] as? String)?.let { " (${escapeResourceTitle(it)})" } ?: ""
                val uri = (block["uri"] as? String)?.let { escapeInlineControlChars(it) } ?: ""
                if (uri.isNotEmpty()) "[Resource link$title] $uri" else "[Resource link$title]"
            }
            else -> null
        }
        if (blockText != null) {
            if (maxBytes != null) {
                val separatorBytes = if (parts.isNotEmpty()) 1 else 0
                totalBytes += separatorBytes + blockText.toByteArray(Charsets.UTF_8).size
                if (totalBytes > maxBytes) {
                    throw IllegalArgumentException("Prompt exceeds maximum allowed size of $maxBytes bytes")
                }
            }
            parts.add(blockText)
        }
    }
    return parts.joinToString("\n")
}

// ---------------------------------------------------------------------------
// Attachment extraction  (aligned with TS extractAttachmentsFromPrompt)
// ---------------------------------------------------------------------------

fun extractAttachmentsFromContentBlocks(blocks: List<Map<String, Any?>>): List<GatewayAttachment> {
    val attachments = mutableListOf<GatewayAttachment>()
    for (block in blocks) {
        if (block["type"] != "image") continue
        val data = block["data"] as? String ?: continue
        val mimeType = block["mimeType"] as? String ?: continue
        attachments.add(GatewayAttachment("image", mimeType, data))
    }
    return attachments
}

// ---------------------------------------------------------------------------
// Tool title formatting  (aligned with TS formatToolTitle)
// ---------------------------------------------------------------------------

fun formatToolTitle(name: String?, args: Map<String, Any?>?): String {
    val base = name ?: "tool"
    if (args.isNullOrEmpty()) return base
    val parts = args.entries.map { (key, value) ->
        val raw = if (value is String) value else value?.toString() ?: "null"
        val safe = if (raw.length > 100) "${raw.take(100)}..." else raw
        "$key: $safe"
    }
    return escapeInlineControlChars("$base: ${parts.joinToString(", ")}")
}

// ---------------------------------------------------------------------------
// Tool kind inference  (aligned with TS inferToolKind)
// ---------------------------------------------------------------------------

fun inferToolKind(name: String?): ToolKind {
    if (name == null) return ToolKind.OTHER
    val normalized = name.lowercase()
    return when {
        "read" in normalized -> ToolKind.READ
        "write" in normalized || "edit" in normalized -> ToolKind.EDIT
        "delete" in normalized || "remove" in normalized -> ToolKind.DELETE
        "move" in normalized || "rename" in normalized -> ToolKind.MOVE
        "search" in normalized || "find" in normalized -> ToolKind.SEARCH
        "exec" in normalized || "run" in normalized || "bash" in normalized -> ToolKind.EXECUTE
        "fetch" in normalized || "http" in normalized -> ToolKind.FETCH
        else -> ToolKind.OTHER
    }
}

// ---------------------------------------------------------------------------
// Tool call content extraction  (aligned with TS extractToolCallContent)
// ---------------------------------------------------------------------------

fun extractToolCallContent(value: Any?): List<ToolCallContentEntry>? {
    if (value is String) {
        return if (value.trim().isNotEmpty()) {
            listOf(ToolCallContentEntry(text = value))
        } else null
    }

    @Suppress("UNCHECKED_CAST")
    val record = value as? Map<String, Any?> ?: return null

    @Suppress("UNCHECKED_CAST")
    val blocks = record["content"] as? List<Map<String, Any?>>
    if (blocks != null) {
        val contents = blocks.mapNotNull { block ->
            if (block["type"] == "text") {
                val text = block["text"] as? String
                if (text != null && text.trim().isNotEmpty()) {
                    ToolCallContentEntry(text = text)
                } else null
            } else null
        }
        if (contents.isNotEmpty()) return contents
    }

    // Fallback text extraction
    val fallbackText = (record["text"] as? String)
        ?: (record["message"] as? String)
        ?: (record["error"] as? String)

    return if (fallbackText != null && fallbackText.trim().isNotEmpty()) {
        listOf(ToolCallContentEntry(text = fallbackText))
    } else null
}

// ---------------------------------------------------------------------------
// Tool location extraction  (aligned with TS extractToolCallLocations)
// ---------------------------------------------------------------------------

fun extractToolCallLocations(vararg values: Any?): List<ToolCallLocation>? {
    val locations = LinkedHashMap<String, ToolCallLocation>()
    for (value in values) {
        collectToolLocations(value, locations, intArrayOf(0), 0)
    }
    return if (locations.isNotEmpty()) locations.values.toList() else null
}

private fun normalizeToolLocationPath(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.length > 4096) return null
    if ('\u0000' in trimmed || '\r' in trimmed || '\n' in trimmed) return null
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) return null
    if (trimmed.startsWith("file://", ignoreCase = true)) {
        return try {
            val uri = java.net.URI(trimmed)
            uri.path?.ifEmpty { null }
        } catch (_: Exception) { null }
    }
    return trimmed
}

private fun normalizeToolLocationLine(value: Any?): Int? {
    if (value !is Number) return null
    val line = value.toInt()
    return if (line > 0) line else null
}

private fun addToolLocation(
    locations: MutableMap<String, ToolCallLocation>,
    rawPath: String,
    line: Int? = null,
) {
    val path = normalizeToolLocationPath(rawPath) ?: return
    // Dedup
    for ((existingKey, existing) in locations.entries.toList()) {
        if (existing.path != path) continue
        if (line == null || existing.line == line) return
        if (existing.line == null) {
            locations.remove(existingKey)
        }
    }
    val locationKey = "$path:${line ?: ""}"
    if (locations.containsKey(locationKey)) return
    locations[locationKey] = ToolCallLocation(path, line)
}

private fun collectLocationsFromTextMarkers(
    text: String,
    locations: MutableMap<String, ToolCallLocation>,
) {
    for (match in TOOL_RESULT_PATH_MARKER_RE.findAll(text)) {
        val candidate = match.groupValues[1].trim()
        if (candidate.isNotEmpty()) {
            addToolLocation(locations, candidate)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun collectToolLocations(
    value: Any?,
    locations: MutableMap<String, ToolCallLocation>,
    visited: IntArray,
    depth: Int,
) {
    if (visited[0] >= TOOL_LOCATION_MAX_NODES || depth > TOOL_LOCATION_MAX_DEPTH) return
    visited[0]++

    if (value is String) {
        collectLocationsFromTextMarkers(value, locations)
        return
    }
    if (value == null || value !is Map<*, *>) {
        if (value is List<*>) {
            for (item in value) {
                collectToolLocations(item, locations, visited, depth + 1)
                if (visited[0] >= TOOL_LOCATION_MAX_NODES) return
            }
        }
        return
    }

    val record = value as Map<String, Any?>

    // Extract line number
    var line: Int? = null
    for (key in TOOL_LOCATION_LINE_KEYS) {
        line = normalizeToolLocationLine(record[key])
        if (line != null) break
    }

    // Extract path keys
    for (key in TOOL_LOCATION_PATH_KEYS) {
        val rawPath = record[key]
        if (rawPath is String) {
            addToolLocation(locations, rawPath, line)
        }
    }

    // Extract from content blocks
    val content = record["content"]
    if (content is List<*>) {
        for (block in content) {
            if (block is Map<*, *> && block["type"] == "text") {
                val text = block["text"] as? String
                if (text != null) {
                    collectLocationsFromTextMarkers(text, locations)
                }
            }
        }
    }

    // Recurse into other fields
    for ((key, nested) in record) {
        if (key == "content") continue
        collectToolLocations(nested, locations, visited, depth + 1)
        if (visited[0] >= TOOL_LOCATION_MAX_NODES) return
    }
}
