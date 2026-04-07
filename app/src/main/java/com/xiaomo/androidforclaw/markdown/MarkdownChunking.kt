package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/chunking.ts
 *
 * Splits markdown into size-limited chunks that respect block boundaries
 * and span offsets.
 */
object MarkdownChunking {

    // -----------------------------------------------------------------------
    // Raw-text chunking (paragraph-boundary aware)
    // -----------------------------------------------------------------------

    /**
     * Split raw markdown text into chunks of at most [maxChunkLength] characters,
     * breaking only at paragraph boundaries (double newlines).
     */
    fun chunkMarkdown(text: String, maxChunkLength: Int = 4000): List<String> {
        if (text.length <= maxChunkLength) return listOf(text)

        val chunks = mutableListOf<String>()
        val paragraphs = text.split(Regex("\n{2,}"))
        val current = StringBuilder()

        for (para in paragraphs) {
            if (current.length + para.length + 2 > maxChunkLength && current.isNotEmpty()) {
                chunks.add(current.toString().trimEnd())
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(para)
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trimEnd())
        return chunks
    }

    // -----------------------------------------------------------------------
    // Block-level chunking
    // -----------------------------------------------------------------------

    /**
     * Split a list of [MarkdownBlock]s into groups where each group's total
     * text length does not exceed [maxChunkLength].
     */
    fun chunkMarkdownBlocks(
        blocks: List<MarkdownBlock>,
        maxChunkLength: Int = 4000
    ): List<List<MarkdownBlock>> {
        if (blocks.isEmpty()) return emptyList()

        val chunks = mutableListOf<List<MarkdownBlock>>()
        var current = mutableListOf<MarkdownBlock>()
        var currentLen = 0

        for (block in blocks) {
            val blockLen = block.text.length + 2 // +2 for paragraph separator
            if (currentLen + blockLen > maxChunkLength && current.isNotEmpty()) {
                chunks.add(current)
                current = mutableListOf()
                currentLen = 0
            }
            current.add(block)
            currentLen += blockLen
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }

    // -----------------------------------------------------------------------
    // Offset-aware splitting — aligned with OpenClaw chunking.splitByOffset
    // -----------------------------------------------------------------------

    /**
     * Split [blocks] at a given [offsetLimit], respecting span boundaries.
     *
     * All blocks whose [MarkdownBlock.offset] + [MarkdownBlock.length] fit within
     * [offsetLimit] go into the first chunk; the remainder forms the second chunk.
     * If a block straddles the boundary, it is kept whole in the second chunk to
     * avoid breaking inline spans.
     *
     * @return A pair of (before, after). Either list may be empty.
     */
    fun splitByOffset(
        blocks: List<MarkdownBlock>,
        offsetLimit: Int
    ): Pair<List<MarkdownBlock>, List<MarkdownBlock>> {
        val before = mutableListOf<MarkdownBlock>()
        val after = mutableListOf<MarkdownBlock>()

        for (block in blocks) {
            if (block.offset + block.length <= offsetLimit) {
                before.add(block)
            } else {
                after.add(block)
            }
        }
        return Pair(before, after)
    }
}
