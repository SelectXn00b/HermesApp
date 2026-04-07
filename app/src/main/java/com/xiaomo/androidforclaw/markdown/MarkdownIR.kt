package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/ir.ts
 *
 * Intermediate representation for parsed markdown content.
 * Each block carries its type, raw text, inline style spans,
 * and positional offset/length within the source document.
 */

// ---------------------------------------------------------------------------
// Style span types — aligned with OpenClaw ir.ts StyleSpanType
// ---------------------------------------------------------------------------

enum class MarkdownStyle {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    CODE,
    LINK,
    HEADING,
    SPOILER
}

// ---------------------------------------------------------------------------
// Inline spans
// ---------------------------------------------------------------------------

/**
 * A styled range within a block's text.
 *
 * @param style  The span type.
 * @param start  Inclusive start offset within the owning block's [MarkdownBlock.text].
 * @param end    Exclusive end offset.
 * @param level  Heading level (1-6) when [style] == HEADING; null otherwise.
 * @param url    Link target when [style] == LINK; null otherwise.
 */
data class StyleSpan(
    val style: MarkdownStyle,
    val start: Int,
    val end: Int,
    val level: Int? = null,
    val url: String? = null
)

/**
 * A detected link within a block (convenience — can also be represented as a
 * LINK StyleSpan, but kept for backward compatibility and for callers that only
 * need link data).
 */
data class LinkSpan(
    val url: String,
    val label: String?,
    val start: Int,
    val end: Int
)

// ---------------------------------------------------------------------------
// Block types — aligned with OpenClaw MarkdownNodeType
// ---------------------------------------------------------------------------

enum class MarkdownBlockType {
    PARAGRAPH,
    HEADING,
    CODE_BLOCK,
    BLOCKQUOTE,
    LIST_ITEM,
    HORIZONTAL_RULE,
    IMAGE,
    TABLE,
    SPOILER
}

// ---------------------------------------------------------------------------
// MarkdownBlock — the AST node
// ---------------------------------------------------------------------------

/**
 * A single block-level element in the Markdown IR.
 *
 * @param type     Block type.
 * @param text     Plain content text (markup stripped where possible).
 * @param spans    Inline style spans within [text].
 * @param links    Detected links within [text].
 * @param language Code-block language hint (e.g. "kotlin").
 * @param level    Heading level (1-6) or list nesting depth.
 * @param offset   Byte offset of this block in the original source.
 * @param length   Byte length of this block in the original source.
 * @param rows     For TABLE blocks: list of rows, each row is a list of cell strings.
 */
data class MarkdownBlock(
    val type: MarkdownBlockType,
    val text: String,
    val spans: List<StyleSpan> = emptyList(),
    val links: List<LinkSpan> = emptyList(),
    val language: String? = null,
    val level: Int? = null,
    val offset: Int = 0,
    val length: Int = text.length,
    val rows: List<List<String>>? = null
)
