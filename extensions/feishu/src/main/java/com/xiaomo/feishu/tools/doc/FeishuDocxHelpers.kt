package com.xiaomo.feishu.tools.doc

/**
 * OpenClaw Source Reference:
 * - ../openclaw/extensions/feishu/src/docx.ts (core helpers)
 *
 * AndroidForClaw adaptation: Markdown conversion, block insertion, document clearing.
 */

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient

private const val TAG = "FeishuDocxHelpers"

/** Max blocks per documentBlockChildren.create request */
const val MAX_BLOCKS_PER_INSERT = 50

/** Max recursive retry depth for markdown conversion */
const val MAX_CONVERT_RETRY_DEPTH = 8

/** Block type names aligned with OpenClaw BLOCK_TYPE_NAMES */
val BLOCK_TYPE_NAMES: Map<Int, String> = mapOf(
    1 to "Page",
    2 to "Text",
    3 to "Heading1",
    4 to "Heading2",
    5 to "Heading3",
    12 to "Bullet",
    13 to "Ordered",
    14 to "Code",
    15 to "Quote",
    17 to "Todo",
    18 to "Bitable",
    21 to "Diagram",
    22 to "Divider",
    23 to "File",
    27 to "Image",
    30 to "Sheet",
    31 to "Table",
    32 to "TableCell"
)

/** Block types that cannot be created via documentBlockChildren.create API */
val UNSUPPORTED_CREATE_TYPES = setOf(31, 32)

/**
 * Clean blocks for insertion: remove unsupported types and read-only fields.
 * Aligned with OpenClaw cleanBlocksForInsert.
 */
fun cleanBlocksForInsert(blocks: JsonArray): Pair<JsonArray, List<String>> {
    val cleaned = JsonArray()
    val skipped = mutableListOf<String>()

    for (i in 0 until blocks.size()) {
        val block = blocks[i].asJsonObject
        val blockType = block.get("block_type")?.asInt ?: continue

        if (blockType in UNSUPPORTED_CREATE_TYPES) {
            skipped.add(BLOCK_TYPE_NAMES[blockType] ?: "type_$blockType")
            continue
        }
        cleaned.add(block)
    }

    return cleaned to skipped
}

/**
 * Convert markdown via Feishu document.convert API.
 * Aligned with OpenClaw convertMarkdown.
 */
suspend fun convertMarkdownViaApi(
    client: FeishuClient,
    markdown: String
): ConvertResult {
    val body = mapOf(
        "content_type" to "markdown",
        "content" to markdown
    )

    val result = client.post("/open-apis/docx/v1/documents/convert", body)
    if (result.isFailure) {
        throw result.exceptionOrNull() ?: Exception("Convert failed")
    }

    val data = result.getOrNull()?.getAsJsonObject("data")
    val blocks = data?.getAsJsonArray("blocks") ?: JsonArray()
    val firstLevelBlockIds = data?.getAsJsonArray("first_level_block_ids")
        ?.map { it.asString } ?: emptyList()

    return ConvertResult(blocks, firstLevelBlockIds)
}

data class ConvertResult(
    val blocks: JsonArray,
    val firstLevelBlockIds: List<String>
)

/**
 * Sort blocks by first-level ID order.
 * Aligned with OpenClaw sortBlocksByFirstLevel.
 */
fun sortBlocksByFirstLevel(blocks: JsonArray, firstLevelIds: List<String>): JsonArray {
    if (firstLevelIds.isEmpty()) return blocks

    val blockMap = mutableMapOf<String, JsonObject>()
    for (i in 0 until blocks.size()) {
        val block = blocks[i].asJsonObject
        val id = block.get("block_id")?.asString ?: continue
        blockMap[id] = block
    }

    val sorted = JsonArray()
    val sortedIds = firstLevelIds.toSet()

    for (id in firstLevelIds) {
        blockMap[id]?.let { sorted.add(it) }
    }
    for (i in 0 until blocks.size()) {
        val block = blocks[i].asJsonObject
        val id = block.get("block_id")?.asString ?: continue
        if (id !in sortedIds) sorted.add(block)
    }

    return sorted
}

/**
 * Insert blocks one at a time to preserve document order.
 * Aligned with OpenClaw insertBlocks.
 */
suspend fun insertBlocks(
    client: FeishuClient,
    docToken: String,
    blocks: JsonArray,
    parentBlockId: String? = null,
    index: Int? = null
): InsertResult {
    val (cleaned, skipped) = cleanBlocksForInsert(blocks)
    val blockId = parentBlockId ?: docToken

    if (cleaned.size() == 0) {
        return InsertResult(JsonArray(), skipped)
    }

    val allInserted = JsonArray()
    for (offset in 0 until cleaned.size()) {
        val block = cleaned[offset].asJsonObject
        val children = JsonArray().apply { add(block) }

        val body = mutableMapOf<String, Any>(
            "children" to children
        )
        if (index != null) {
            body["index"] = index + offset
        }

        val result = client.post(
            "/open-apis/docx/v1/documents/$docToken/blocks/$blockId/children",
            body
        )
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Insert block failed")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val insertedChildren = data?.getAsJsonArray("children")
        if (insertedChildren != null) {
            for (i in 0 until insertedChildren.size()) {
                allInserted.add(insertedChildren[i])
            }
        }
    }

    return InsertResult(allInserted, skipped)
}

data class InsertResult(
    val children: JsonArray,
    val skipped: List<String>
)

/**
 * Insert blocks in batches of MAX_BLOCKS_PER_INSERT.
 * Aligned with OpenClaw chunkedInsertBlocks.
 */
suspend fun chunkedInsertBlocks(
    client: FeishuClient,
    docToken: String,
    blocks: JsonArray,
    parentBlockId: String? = null
): InsertResult {
    val allChildren = JsonArray()
    val allSkipped = mutableListOf<String>()

    var i = 0
    while (i < blocks.size()) {
        val end = minOf(i + MAX_BLOCKS_PER_INSERT, blocks.size())
        val batch = JsonArray()
        for (j in i until end) {
            batch.add(blocks[j])
        }

        val (children, skipped) = insertBlocks(client, docToken, batch, parentBlockId)
        for (j in 0 until children.size()) {
            allChildren.add(children[j])
        }
        allSkipped.addAll(skipped)

        i = end
    }

    return InsertResult(allChildren, allSkipped)
}

/**
 * Split markdown into chunks at top-level headings (# or ##).
 * Aligned with OpenClaw splitMarkdownByHeadings.
 */
fun splitMarkdownByHeadings(markdown: String): List<String> {
    val lines = markdown.split("\n")
    val chunks = mutableListOf<String>()
    var current = mutableListOf<String>()
    var inFencedBlock = false
    val fencePattern = Regex("^(`{3,}|~{3,})")

    for (line in lines) {
        if (fencePattern.containsMatchIn(line)) {
            inFencedBlock = !inFencedBlock
        }
        if (!inFencedBlock && Regex("^#{1,2}\\s").containsMatchIn(line) && current.isNotEmpty()) {
            chunks.add(current.joinToString("\n"))
            current = mutableListOf()
        }
        current.add(line)
    }
    if (current.isNotEmpty()) {
        chunks.add(current.joinToString("\n"))
    }

    return chunks
}

/**
 * Split markdown by size, preferring to break outside fenced code blocks.
 * Aligned with OpenClaw splitMarkdownBySize.
 */
fun splitMarkdownBySize(markdown: String, maxChars: Int): List<String> {
    if (markdown.length <= maxChars) return listOf(markdown)

    val lines = markdown.split("\n")
    val chunks = mutableListOf<String>()
    var current = mutableListOf<String>()
    var currentLength = 0
    var inFencedBlock = false
    val fencePattern = Regex("^(`{3,}|~{3,})")

    for (line in lines) {
        if (fencePattern.containsMatchIn(line)) {
            inFencedBlock = !inFencedBlock
        }

        val lineLength = line.length + 1
        val wouldExceed = currentLength + lineLength > maxChars
        if (current.isNotEmpty() && wouldExceed && !inFencedBlock) {
            chunks.add(current.joinToString("\n"))
            current = mutableListOf()
            currentLength = 0
        }

        current.add(line)
        currentLength += lineLength
    }

    if (current.isNotEmpty()) {
        chunks.add(current.joinToString("\n"))
    }

    if (chunks.size > 1) return chunks

    // Degenerate case: no safe boundary
    val midpoint = lines.size / 2
    if (midpoint <= 0 || midpoint >= lines.size) return listOf(markdown)

    return listOf(
        lines.subList(0, midpoint).joinToString("\n"),
        lines.subList(midpoint, lines.size).joinToString("\n")
    )
}

/**
 * Convert markdown with recursive fallback on failure.
 * Aligned with OpenClaw convertMarkdownWithFallback.
 */
suspend fun convertMarkdownWithFallback(
    client: FeishuClient,
    markdown: String,
    depth: Int = 0
): ConvertResult {
    try {
        return convertMarkdownViaApi(client, markdown)
    } catch (error: Exception) {
        if (depth >= MAX_CONVERT_RETRY_DEPTH || markdown.length < 2) {
            throw error
        }

        val splitTarget = maxOf(256, markdown.length / 2)
        val chunks = splitMarkdownBySize(markdown, splitTarget)
        if (chunks.size <= 1) throw error

        val allBlocks = JsonArray()
        val allFirstLevelBlockIds = mutableListOf<String>()

        for (chunk in chunks) {
            val converted = convertMarkdownWithFallback(client, chunk, depth + 1)
            for (i in 0 until converted.blocks.size()) {
                allBlocks.add(converted.blocks[i])
            }
            allFirstLevelBlockIds.addAll(converted.firstLevelBlockIds)
        }

        return ConvertResult(allBlocks, allFirstLevelBlockIds)
    }
}

/**
 * Convert markdown in chunks to avoid content size limits.
 * Aligned with OpenClaw chunkedConvertMarkdown.
 */
suspend fun chunkedConvertMarkdown(
    client: FeishuClient,
    markdown: String
): ConvertResult {
    val chunks = splitMarkdownByHeadings(markdown)
    val allBlocks = JsonArray()
    val allFirstLevelBlockIds = mutableListOf<String>()

    for (chunk in chunks) {
        val (blocks, firstLevelBlockIds) = convertMarkdownWithFallback(client, chunk)
        val sorted = sortBlocksByFirstLevel(blocks, firstLevelBlockIds)
        for (i in 0 until sorted.size()) {
            allBlocks.add(sorted[i])
        }
        allFirstLevelBlockIds.addAll(firstLevelBlockIds)
    }

    return ConvertResult(allBlocks, allFirstLevelBlockIds)
}

/**
 * Clear all document content (except page block).
 * Aligned with OpenClaw clearDocumentContent.
 */
suspend fun clearDocumentContent(client: FeishuClient, docToken: String): Int {
    val result = client.get("/open-apis/docx/v1/documents/$docToken/blocks")
    if (result.isFailure) {
        throw result.exceptionOrNull() ?: Exception("Failed to list blocks")
    }

    val data = result.getOrNull()?.getAsJsonObject("data")
    val items = data?.getAsJsonArray("items") ?: return 0

    // Find child blocks of the document root (not the page block itself)
    val childIds = mutableListOf<String>()
    for (i in 0 until items.size()) {
        val block = items[i].asJsonObject
        val parentId = block.get("parent_id")?.asString
        val blockType = block.get("block_type")?.asInt ?: 0
        if (parentId == docToken && blockType != 1) {
            block.get("block_id")?.asString?.let { childIds.add(it) }
        }
    }

    if (childIds.isNotEmpty()) {
        val deleteBody = mapOf(
            "start_index" to 0,
            "end_index" to childIds.size
        )
        val deleteResult = client.post(
            "/open-apis/docx/v1/documents/$docToken/blocks/$docToken/children/batch_delete",
            deleteBody
        )
        if (deleteResult.isFailure) {
            throw deleteResult.exceptionOrNull() ?: Exception("Failed to clear document")
        }
    }

    Log.d(TAG, "Cleared ${ childIds.size} blocks from $docToken")
    return childIds.size
}

/**
 * Extract image URLs from markdown content.
 * Aligned with OpenClaw extractImageUrls.
 */
fun extractImageUrls(markdown: String): List<String> {
    val regex = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
    return regex.findAll(markdown)
        .map { it.groupValues[1].trim() }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .toList()
}
