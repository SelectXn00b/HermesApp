package com.xiaomo.androidforclaw.interactive

/**
 * OpenClaw module: interactive
 * Source: OpenClaw/src/interactive/payload.ts
 *
 * Defines and normalizes interactive reply payloads (buttons, select menus,
 * text blocks) for rich chat responses across channels.
 *
 * The normalization functions accept loosely-typed data (typically parsed from
 * JSON / Map structures coming from skill tool-call results or provider
 * responses) and produce strongly-typed Kotlin models.
 */

// ---------------------------------------------------------------------------
// Style enum
// ---------------------------------------------------------------------------

enum class InteractiveButtonStyle {
    PRIMARY, SECONDARY, SUCCESS, DANGER;

    companion object {
        /** Normalize a style string (case-insensitive) to enum, null if invalid. */
        fun fromStringOrNull(value: String?): InteractiveButtonStyle? {
            if (value == null) return null
            return when (value.lowercase()) {
                "primary" -> PRIMARY
                "secondary" -> SECONDARY
                "success" -> SUCCESS
                "danger" -> DANGER
                else -> null
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Block models
// ---------------------------------------------------------------------------

data class InteractiveReplyButton(
    val label: String,
    val value: String,
    val style: InteractiveButtonStyle? = null
)

data class InteractiveReplyOption(
    val label: String,
    val value: String,
    val description: String? = null
)

sealed class InteractiveReplyBlock {
    data class Text(val text: String) : InteractiveReplyBlock()
    data class Buttons(val buttons: List<InteractiveReplyButton>) : InteractiveReplyBlock()
    data class Select(
        val placeholder: String? = null,
        val options: List<InteractiveReplyOption>,
        val maxValues: Int? = null
    ) : InteractiveReplyBlock()
}

data class InteractiveReply(
    val blocks: List<InteractiveReplyBlock> = emptyList()
)

// ---------------------------------------------------------------------------
// Normalization — single elements
// ---------------------------------------------------------------------------

/**
 * Normalize a button from a loosely-typed map.
 *
 * Reads label from "label" or "text"; value from "value", "callbackData",
 * or "callback_data"; style from "style".
 */
fun normalizeInteractiveButton(raw: Map<*, *>?): InteractiveReplyButton? {
    if (raw == null) return null
    val label = (raw["label"] ?: raw["text"])?.toString() ?: return null
    val value = (raw["value"] ?: raw["callbackData"] ?: raw["callback_data"])?.toString()
        ?: label // fallback: use label as value
    val style = normalizeButtonStyle(raw["style"]?.toString())
    return InteractiveReplyButton(label = label, value = value, style = style)
}

/**
 * Normalize a select option from a loosely-typed map.
 *
 * Reads label from "label" or "text"; value from "value" (fallback to label);
 * description from "description".
 */
fun normalizeInteractiveOption(raw: Map<*, *>?): InteractiveReplyOption? {
    if (raw == null) return null
    val label = (raw["label"] ?: raw["text"])?.toString() ?: return null
    val value = raw["value"]?.toString() ?: label
    val description = raw["description"]?.toString()
    return InteractiveReplyOption(label = label, value = value, description = description)
}

/**
 * Normalize a button style string: lowercase, validate against known values.
 * Returns null for unknown styles.
 */
fun normalizeButtonStyle(raw: String?): InteractiveButtonStyle? {
    return InteractiveButtonStyle.fromStringOrNull(raw)
}

// ---------------------------------------------------------------------------
// Normalization — full payload
// ---------------------------------------------------------------------------

/**
 * Normalize a raw interactive reply value into an [InteractiveReply].
 *
 * Accepts:
 * - null -> null
 * - [InteractiveReply] -> pass-through
 * - Map with "blocks" key -> parse each block by its "type" field
 * - List (treated as a blocks array directly)
 *
 * Block types recognized: "text", "buttons", "select".
 */
@Suppress("UNCHECKED_CAST")
fun normalizeInteractiveReply(raw: Any?): InteractiveReply? {
    if (raw == null) return null
    if (raw is InteractiveReply) return raw

    val blocksList: List<*> = when (raw) {
        is Map<*, *> -> {
            val blocks = raw["blocks"]
            if (blocks is List<*>) blocks else return null
        }
        is List<*> -> raw
        else -> return null
    }

    val normalizedBlocks = blocksList.mapNotNull { entry ->
        if (entry !is Map<*, *>) return@mapNotNull null
        val type = entry["type"]?.toString()?.lowercase() ?: return@mapNotNull null
        when (type) {
            "text" -> {
                val text = entry["text"]?.toString() ?: return@mapNotNull null
                InteractiveReplyBlock.Text(text)
            }
            "buttons" -> {
                val buttonsRaw = entry["buttons"] as? List<*> ?: return@mapNotNull null
                val buttons = buttonsRaw.mapNotNull { btn ->
                    if (btn is Map<*, *>) normalizeInteractiveButton(btn) else null
                }
                if (buttons.isEmpty()) null else InteractiveReplyBlock.Buttons(buttons)
            }
            "select" -> {
                val optionsRaw = entry["options"] as? List<*> ?: return@mapNotNull null
                val options = optionsRaw.mapNotNull { opt ->
                    if (opt is Map<*, *>) normalizeInteractiveOption(opt) else null
                }
                if (options.isEmpty()) return@mapNotNull null
                val placeholder = entry["placeholder"]?.toString()
                val maxValues = (entry["maxValues"] ?: entry["max_values"])?.let {
                    when (it) {
                        is Number -> it.toInt()
                        is String -> it.toIntOrNull()
                        else -> null
                    }
                }
                InteractiveReplyBlock.Select(
                    placeholder = placeholder,
                    options = options,
                    maxValues = maxValues
                )
            }
            else -> null
        }
    }

    if (normalizedBlocks.isEmpty()) return null
    return InteractiveReply(blocks = normalizedBlocks)
}

// ---------------------------------------------------------------------------
// Content detection helpers
// ---------------------------------------------------------------------------

/**
 * Returns `true` if the interactive reply has at least one block.
 */
fun hasInteractiveReplyBlocks(value: Any?): Boolean {
    if (value == null) return false
    if (value is InteractiveReply) return value.blocks.isNotEmpty()
    // Try normalizing
    val reply = normalizeInteractiveReply(value)
    return reply != null && reply.blocks.isNotEmpty()
}

/**
 * Returns `true` if [value] is a non-empty, non-array object (Map) — indicating
 * channel-specific data is present.
 */
fun hasReplyChannelData(value: Any?): Boolean {
    if (value == null) return false
    if (value is List<*>) return false
    return value is Map<*, *> && value.isNotEmpty()
}

/**
 * Returns `true` if any of the reply content fields carry meaningful data.
 *
 * @param text            text body
 * @param mediaUrl        single media URL
 * @param mediaUrls       list of media URLs
 * @param interactive     interactive reply payload
 * @param hasChannelData  whether channel-specific data is present
 * @param extraContent    any other extra content
 */
fun hasReplyContent(
    text: String? = null,
    mediaUrl: String? = null,
    mediaUrls: List<String>? = null,
    interactive: Any? = null,
    hasChannelData: Boolean = false,
    extraContent: Any? = null
): Boolean {
    if (!text.isNullOrBlank()) return true
    if (!mediaUrl.isNullOrBlank()) return true
    if (!mediaUrls.isNullOrEmpty()) return true
    if (hasInteractiveReplyBlocks(interactive)) return true
    if (hasChannelData) return true
    if (extraContent != null) return true
    return false
}

/**
 * Returns `true` if the given [payload] map contains any meaningful reply content.
 *
 * @param payload  a map representation of a reply payload
 * @param options  optional map of extra check options (currently unused, reserved)
 */
@Suppress("UNCHECKED_CAST")
fun hasReplyPayloadContent(
    payload: Map<String, Any?>?,
    options: Map<String, Any?>? = null
): Boolean {
    if (payload == null) return false
    return hasReplyContent(
        text = payload["text"]?.toString(),
        mediaUrl = payload["mediaUrl"]?.toString(),
        mediaUrls = (payload["mediaUrls"] as? List<*>)?.mapNotNull { it?.toString() },
        interactive = payload["interactive"],
        hasChannelData = hasReplyChannelData(payload["channelData"]),
        extraContent = payload["extraContent"]
    )
}

// ---------------------------------------------------------------------------
// Text fallback
// ---------------------------------------------------------------------------

/**
 * Resolve a text fallback from interactive blocks.
 *
 * If [text] is non-blank, returns it as-is.  Otherwise, extracts all text
 * blocks from [interactive] and joins them with newlines.
 */
fun resolveInteractiveTextFallback(
    text: String?,
    interactive: InteractiveReply?
): String? {
    if (!text.isNullOrBlank()) return text
    if (interactive == null) return null
    val textBlocks = interactive.blocks.filterIsInstance<InteractiveReplyBlock.Text>()
    if (textBlocks.isEmpty()) return null
    return textBlocks.joinToString("\n") { it.text }.ifBlank { null }
}
