package com.xiaomo.androidforclaw.linkunderstanding

/**
 * OpenClaw module: link-understanding
 * Source: OpenClaw/src/link-understanding/format.ts
 *
 * Types for link metadata and formatting helpers for text/markdown output.
 */

// ---------------------------------------------------------------------------
// LinkUnderstandingResult — aligned with OpenClaw
// ---------------------------------------------------------------------------

/**
 * Result of fetching and parsing a link's metadata (og: tags, title, etc.).
 *
 * Fields match OpenClaw LinkUnderstandingResult:
 * title, description, imageUrl, siteName, type, url.
 */
data class LinkUnderstandingResult(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val type: String? = null
)

// Legacy alias
typealias LinkPreview = LinkUnderstandingResult

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

/** Render a link preview as plain text. */
fun formatLinkPreviewAsText(preview: LinkUnderstandingResult): String {
    val parts = mutableListOf<String>()
    preview.title?.let { parts.add(it) }
    preview.siteName?.let { parts.add("($it)") }
    preview.description?.let { parts.add(it) }
    parts.add(preview.url)
    return parts.joinToString("\n")
}

/** Render a link preview as a Markdown snippet. */
fun formatLinkPreviewAsMarkdown(preview: LinkUnderstandingResult): String {
    val title = preview.title ?: preview.url
    val md = StringBuilder()
    md.append("[${title}](${preview.url})")
    preview.siteName?.let { md.append(" _($it)_") }
    preview.description?.let { md.append("\n> $it") }
    return md.toString()
}
