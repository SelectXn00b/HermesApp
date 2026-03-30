package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/content-blocks.ts
 *
 * AndroidForClaw adaptation: content block text extraction.
 */

import org.json.JSONArray
import org.json.JSONObject

/**
 * Content block utilities.
 * Aligned with OpenClaw content-blocks.ts.
 */
object ContentBlocks {

    /**
     * Collect text content from content blocks.
     * Handles both string content and array-of-blocks content.
     *
     * Aligned with OpenClaw collectTextContentBlocks.
     */
    fun collectTextContentBlocks(content: Any?): List<String> {
        return when (content) {
            is String -> if (content.isNotEmpty()) listOf(content) else emptyList()
            is JSONArray -> {
                val texts = mutableListOf<String>()
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: continue
                    if (block.optString("type") == "text") {
                        val text = block.optString("text", "")
                        if (text.isNotEmpty()) texts.add(text)
                    }
                }
                texts
            }
            is List<*> -> {
                content.mapNotNull { block ->
                    when (block) {
                        is String -> block.takeIf { it.isNotEmpty() }
                        is Map<*, *> -> {
                            if (block["type"] == "text") {
                                (block["text"] as? String)?.takeIf { it.isNotEmpty() }
                            } else null
                        }
                        else -> null
                    }
                }
            }
            else -> emptyList()
        }
    }

    /**
     * Extract the first text block from content.
     */
    fun extractFirstTextBlock(content: Any?): String? {
        return collectTextContentBlocks(content).firstOrNull()
    }

    /**
     * Join all text content blocks into a single string.
     */
    fun joinTextContent(content: Any?, separator: String = "\n"): String {
        return collectTextContentBlocks(content).joinToString(separator)
    }
}
