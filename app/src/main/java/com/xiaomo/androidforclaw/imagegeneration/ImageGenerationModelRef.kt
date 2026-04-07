package com.xiaomo.androidforclaw.imagegeneration

/**
 * OpenClaw module: image-generation
 * Source: OpenClaw/src/image-generation/model-ref.ts
 *
 * Parses a raw "provider/model" string into a [ParsedProviderModelRef].
 */

import com.xiaomo.androidforclaw.mediageneration.ParsedProviderModelRef

/**
 * Parse a raw image-generation model reference.
 *
 * Format: "provider/model" — standard slash-split.
 * Returns `null` if the input cannot be parsed into exactly two non-empty parts.
 */
fun parseImageGenerationModelRef(raw: String): ParsedProviderModelRef? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val slashIdx = trimmed.indexOf('/')
    if (slashIdx <= 0 || slashIdx >= trimmed.length - 1) return null

    val provider = trimmed.substring(0, slashIdx)
    val model = trimmed.substring(slashIdx + 1)
    return ParsedProviderModelRef(provider = provider, model = model)
}
