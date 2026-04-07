package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/render.ts
 *
 * Line-by-line parser that converts raw markdown text into MarkdownBlock IR.
 * Detects block types, extracts inline spans, and tracks source offsets.
 */
object MarkdownRender {

    // -----------------------------------------------------------------------
    // Block-level patterns
    // -----------------------------------------------------------------------

    private val FENCE_OPEN = Regex("""^```(\w*)""")
    private val HEADING = Regex("""^(#{1,6})\s+(.+)""")
    private val HR = Regex("""^[-*_]{3,}\s*$""")
    private val BLOCKQUOTE = Regex("""^>\s?(.*)""")
    private val LIST_ITEM = Regex("""^(\s*[-*+]|\s*\d+\.)\s+(.+)""")
    private val IMAGE = Regex("""^!\[([^\]]*)]\(([^)]+)\)""")
    private val TABLE_SEP = Regex("""^\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?\s*$""")
    private val TABLE_ROW = Regex("""^\|(.+)\|?\s*$""")
    private val SPOILER_OPEN = Regex("""^\|\|(.*)""")
    private val SPOILER_CLOSE = Regex("""(.*)\|\|\s*$""")

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    fun parseMarkdown(raw: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = raw.split("\n")
        var i = 0
        var offset = 0 // running byte offset in [raw]

        while (i < lines.size) {
            val line = lines[i]
            val lineLen = line.length + 1 // +1 for the newline consumed by split

            // --- Fenced code block ---
            val fenceMatch = FENCE_OPEN.find(line)
            if (fenceMatch != null) {
                val lang = fenceMatch.groupValues[1].ifEmpty { null }
                val codeLines = mutableListOf<String>()
                val blockStart = offset
                i++; offset += lineLen
                while (i < lines.size && !lines[i].startsWith("```")) {
                    codeLines.add(lines[i])
                    offset += lines[i].length + 1
                    i++
                }
                if (i < lines.size) { offset += lines[i].length + 1; i++ } // skip closing ```
                val text = codeLines.joinToString("\n")
                blocks.add(
                    MarkdownBlock(
                        type = MarkdownBlockType.CODE_BLOCK,
                        text = text,
                        language = lang,
                        offset = blockStart,
                        length = offset - blockStart
                    )
                )
                continue
            }

            // --- Blank line ---
            if (line.isBlank()) { i++; offset += lineLen; continue }

            // --- Horizontal rule ---
            if (HR.matches(line)) {
                blocks.add(
                    MarkdownBlock(
                        type = MarkdownBlockType.HORIZONTAL_RULE,
                        text = "---",
                        offset = offset,
                        length = lineLen
                    )
                )
                i++; offset += lineLen; continue
            }

            // --- Heading ---
            val headingMatch = HEADING.find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2]
                blocks.add(
                    MarkdownBlock(
                        type = MarkdownBlockType.HEADING,
                        text = text,
                        level = level,
                        spans = listOf(
                            StyleSpan(MarkdownStyle.HEADING, 0, text.length, level = level)
                        ),
                        offset = offset,
                        length = lineLen
                    )
                )
                i++; offset += lineLen; continue
            }

            // --- Table (look ahead for separator row) ---
            if (i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1]) && TABLE_ROW.containsMatchIn(line)) {
                val blockStart = offset
                val rows = mutableListOf<List<String>>()
                // Header row
                rows.add(parseTableRow(line))
                i++; offset += lineLen
                // Separator row — skip
                offset += lines[i].length + 1; i++
                // Body rows
                while (i < lines.size && TABLE_ROW.containsMatchIn(lines[i])) {
                    rows.add(parseTableRow(lines[i]))
                    offset += lines[i].length + 1; i++
                }
                val text = rows.joinToString("\n") { it.joinToString(" | ") }
                blocks.add(
                    MarkdownBlock(
                        type = MarkdownBlockType.TABLE,
                        text = text,
                        rows = rows,
                        offset = blockStart,
                        length = offset - blockStart
                    )
                )
                continue
            }

            // --- Blockquote ---
            val bqMatch = BLOCKQUOTE.find(line)
            if (bqMatch != null) {
                val blockStart = offset
                val bqLines = mutableListOf(bqMatch.groupValues[1])
                i++; offset += lineLen
                while (i < lines.size) {
                    val next = BLOCKQUOTE.find(lines[i])
                    if (next != null) {
                        bqLines.add(next.groupValues[1])
                        offset += lines[i].length + 1; i++
                    } else break
                }
                val text = bqLines.joinToString("\n")
                blocks.add(
                    MarkdownBlock(
                        type = MarkdownBlockType.BLOCKQUOTE,
                        text = text,
                        spans = parseInlineSpans(text),
                        links = parseInlineLinks(text),
                        offset = blockStart,
                        length = offset - blockStart
                    )
                )
                continue
            }

            // --- Image ---
            val imgMatch = IMAGE.find(line)
            if (imgMatch != null) {
                val alt = imgMatch.groupValues[1]
                val url = imgMatch.groupValues[2]
                blocks.add(
                    MarkdownBlock(
                        type = MarkdownBlockType.IMAGE,
                        text = alt,
                        links = listOf(LinkSpan(url, alt, 0, alt.length)),
                        offset = offset,
                        length = lineLen
                    )
                )
                i++; offset += lineLen; continue
            }

            // --- Spoiler (||text||) ---
            if (SPOILER_OPEN.containsMatchIn(line) && SPOILER_CLOSE.containsMatchIn(line)) {
                val inner = line
                    .replace(Regex("""^\|\|"""), "")
                    .replace(Regex("""\|\|\s*$"""), "")
                    .trim()
                blocks.add(
                    MarkdownBlock(
                        type = MarkdownBlockType.SPOILER,
                        text = inner,
                        spans = listOf(StyleSpan(MarkdownStyle.SPOILER, 0, inner.length)),
                        offset = offset,
                        length = lineLen
                    )
                )
                i++; offset += lineLen; continue
            }

            // --- List item ---
            val listMatch = LIST_ITEM.find(line)
            if (listMatch != null) {
                val text = listMatch.groupValues[2]
                blocks.add(
                    MarkdownBlock(
                        type = MarkdownBlockType.LIST_ITEM,
                        text = text,
                        spans = parseInlineSpans(text),
                        links = parseInlineLinks(text),
                        offset = offset,
                        length = lineLen
                    )
                )
                i++; offset += lineLen; continue
            }

            // --- Paragraph: collect contiguous non-blank, non-special lines ---
            val blockStart = offset
            val paraLines = mutableListOf(line)
            i++; offset += lineLen
            while (i < lines.size && lines[i].isNotBlank()
                && !HEADING.containsMatchIn(lines[i])
                && !FENCE_OPEN.containsMatchIn(lines[i])
                && !HR.matches(lines[i])
                && !BLOCKQUOTE.containsMatchIn(lines[i])
                && !IMAGE.containsMatchIn(lines[i])
                && !LIST_ITEM.containsMatchIn(lines[i])
            ) {
                paraLines.add(lines[i])
                offset += lines[i].length + 1
                i++
            }
            val text = paraLines.joinToString("\n")
            blocks.add(
                MarkdownBlock(
                    type = MarkdownBlockType.PARAGRAPH,
                    text = text,
                    spans = parseInlineSpans(text),
                    links = parseInlineLinks(text),
                    offset = blockStart,
                    length = offset - blockStart
                )
            )
        }
        return blocks
    }

    // -----------------------------------------------------------------------
    // Inline span detection
    // -----------------------------------------------------------------------

    private val BOLD = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val ITALIC = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)""")
    private val STRIKE = Regex("""~~(.+?)~~""")
    private val CODE_INLINE = Regex("""`([^`]+)`""")
    private val LINK_INLINE = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val SPOILER_INLINE = Regex("""\|\|(.+?)\|\|""")

    internal fun parseInlineSpans(text: String): List<StyleSpan> {
        val spans = mutableListOf<StyleSpan>()
        for (m in BOLD.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.BOLD, m.range.first, m.range.last + 1))
        }
        for (m in ITALIC.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.ITALIC, m.range.first, m.range.last + 1))
        }
        for (m in STRIKE.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.STRIKETHROUGH, m.range.first, m.range.last + 1))
        }
        for (m in CODE_INLINE.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.CODE, m.range.first, m.range.last + 1))
        }
        for (m in LINK_INLINE.findAll(text)) {
            spans.add(
                StyleSpan(
                    MarkdownStyle.LINK,
                    m.range.first,
                    m.range.last + 1,
                    url = m.groupValues[2]
                )
            )
        }
        for (m in SPOILER_INLINE.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.SPOILER, m.range.first, m.range.last + 1))
        }
        return spans
    }

    internal fun parseInlineLinks(text: String): List<LinkSpan> {
        return LINK_INLINE.findAll(text).map { m ->
            LinkSpan(
                url = m.groupValues[2],
                label = m.groupValues[1],
                start = m.range.first,
                end = m.range.last + 1
            )
        }.toList()
    }

    // -----------------------------------------------------------------------
    // Table helpers
    // -----------------------------------------------------------------------

    private fun parseTableRow(line: String): List<String> {
        return line
            .trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }
    }

    // -----------------------------------------------------------------------
    // Convenience accessors
    // -----------------------------------------------------------------------

    /** Render blocks back to plain text (strip all formatting). */
    fun renderBlocksToPlainText(blocks: List<MarkdownBlock>): String {
        return blocks.joinToString("\n\n") { it.text }
    }

    /** Extract only code blocks from the IR. */
    fun extractCodeBlocks(blocks: List<MarkdownBlock>): List<MarkdownBlock> {
        return blocks.filter { it.type == MarkdownBlockType.CODE_BLOCK }
    }
}
