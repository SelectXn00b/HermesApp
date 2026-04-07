package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/
 *
 * High-level convenience API over the markdown sub-modules:
 *  - MarkdownIR      (block types, span types)
 *  - MarkdownRender   (raw -> IR parser)
 *  - MarkdownChunking (split into size-limited chunks)
 *
 * Provides plain-text conversion, code-block stripping, and a shorthand
 * parse-then-chunk pipeline.
 */
object MarkdownRenderer {

    /**
     * Strip all markdown formatting and return plain text.
     * Uses regex stripping for speed when a full parse is not needed.
     */
    fun toPlainText(markdown: String): String {
        return markdown
            // Code blocks -> [code]
            .replace(Regex("```[\\s\\S]*?```"), "[code]")
            // Inline code -> unwrap
            .replace(Regex("`([^`]+)`")) { it.groupValues[1] }
            // Bold
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("__(.+?)__"), "$1")
            // Italic
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("_(.+?)_"), "$1")
            // Strikethrough
            .replace(Regex("~~(.+?)~~"), "$1")
            // Spoiler
            .replace(Regex("\\|\\|(.+?)\\|\\|"), "$1")
            // Images and links
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
            // Headings
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            // Blockquote markers
            .replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
            // Horizontal rules
            .replace(Regex("^[-*_]{3,}\\s*$", RegexOption.MULTILINE), "---")
            .trim()
    }

    /** Remove all fenced code blocks from the markdown. */
    fun stripCodeBlocks(markdown: String): String {
        return markdown.replace(Regex("```[\\s\\S]*?```"), "").trim()
    }

    /** Parse markdown into IR blocks, then convert blocks to plain text. */
    fun parseToPlainText(markdown: String): String {
        val blocks = MarkdownRender.parseMarkdown(markdown)
        return MarkdownRender.renderBlocksToPlainText(blocks)
    }
}
