package com.xiaomo.androidforclaw.mediaunderstanding

/**
 * OpenClaw module: media-understanding
 * Source: OpenClaw/src/media-understanding/resolve.ts
 *
 * Portable resolution helpers for media understanding parameters:
 * - Default prompts per capability
 * - Max chars / bytes / timeout defaults
 * - Model entry resolution
 * - Concurrency limits
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ---------------------------------------------------------------------------
// Constants (aligned with resolve.ts)
// ---------------------------------------------------------------------------

/** Default timeout for a single media understanding operation (seconds). */
const val DEFAULT_TIMEOUT_SECONDS: Int = 120

/** Default timeout in milliseconds. */
const val DEFAULT_TIMEOUT_MS: Long = DEFAULT_TIMEOUT_SECONDS * 1000L

/** Default maximum output bytes for any transcription/description. */
const val DEFAULT_MAX_BYTES: Long = 50_000L

/** Default concurrency for parallel media understanding operations. */
const val DEFAULT_CONCURRENCY: Int = 3

// ---------------------------------------------------------------------------
// Default max chars per capability
// ---------------------------------------------------------------------------

val DEFAULT_MAX_CHARS_BY_CAPABILITY: Map<MediaUnderstandingCapability, Int> = mapOf(
    MediaUnderstandingCapability.AUDIO to 10_000,
    MediaUnderstandingCapability.VIDEO to 5_000,
    MediaUnderstandingCapability.IMAGE to 2_000
)

// ---------------------------------------------------------------------------
// Default prompts per capability
// ---------------------------------------------------------------------------

private val DEFAULT_PROMPTS: Map<MediaUnderstandingCapability, String> = mapOf(
    MediaUnderstandingCapability.AUDIO to "Transcribe this audio accurately. Preserve speaker labels if possible.",
    MediaUnderstandingCapability.VIDEO to "Describe this video in detail, including visual content, actions, and any text/speech.",
    MediaUnderstandingCapability.IMAGE to "Describe this image in detail."
)

// ---------------------------------------------------------------------------
// Resolution helpers
// ---------------------------------------------------------------------------

/**
 * Resolve the prompt for a capability, with optional user override.
 *
 * @param capability The media understanding capability.
 * @param custom     User-provided prompt override (takes priority).
 * @param maxChars   Maximum character count for the prompt. If the resolved
 *                   prompt exceeds this, it is truncated.
 */
fun resolvePrompt(
    capability: MediaUnderstandingCapability,
    custom: String? = null,
    maxChars: Int? = null
): String {
    val base = if (!custom.isNullOrBlank()) custom else DEFAULT_PROMPTS[capability] ?: ""
    return if (maxChars != null && base.length > maxChars) {
        base.take(maxChars)
    } else {
        base
    }
}

/**
 * Resolve the maximum output character count for a capability.
 *
 * @param capability The media understanding capability.
 * @param override   Explicit override from config/caller.
 */
fun resolveMaxChars(
    capability: MediaUnderstandingCapability,
    override: Int? = null
): Int {
    return override ?: DEFAULT_MAX_CHARS_BY_CAPABILITY[capability] ?: 5_000
}

/**
 * Resolve the maximum output byte count.
 *
 * @param override Explicit override from config/caller.
 */
fun resolveMaxBytes(override: Long? = null): Long {
    return override ?: DEFAULT_MAX_BYTES
}

/**
 * Resolve the operation timeout in milliseconds.
 *
 * @param override Explicit override from config/caller (in ms).
 */
fun resolveTimeoutMs(override: Long? = null): Long {
    return override ?: DEFAULT_TIMEOUT_MS
}

// ---------------------------------------------------------------------------
// Model entry resolution
// ---------------------------------------------------------------------------

/**
 * A resolved model entry for media understanding.
 */
data class MediaUnderstandingModelEntry(
    val provider: String,
    val model: String
)

/**
 * Resolve model entries from config for a given capability.
 *
 * Looks at `agents.defaults.<capability>.model` in the config.
 * Returns an ordered list of model entries (primary first, then fallbacks).
 *
 * @param cfg        OpenClaw config.
 * @param capability The target capability.
 * @param override   Explicit model override (highest priority).
 */
fun resolveModelEntries(
    cfg: OpenClawConfig?,
    capability: MediaUnderstandingCapability,
    override: String? = null
): List<MediaUnderstandingModelEntry> {
    val entries = mutableListOf<MediaUnderstandingModelEntry>()
    val seen = mutableSetOf<String>()

    fun add(raw: String?) {
        if (raw.isNullOrBlank()) return
        val parts = raw.trim().split("/", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return
        val key = "${parts[0]}/${parts[1]}"
        if (seen.add(key)) {
            entries.add(MediaUnderstandingModelEntry(provider = parts[0], model = parts[1]))
        }
    }

    // 1. Explicit override
    add(override)

    // 2. Config-based model selection (future: read from cfg.agents.defaults.mediaUnderstanding.model)
    // Currently no dedicated config path; callers pass model refs directly.

    return entries
}

/**
 * Resolve the concurrency limit for parallel media understanding.
 *
 * @param override Explicit override from config/caller.
 */
fun resolveConcurrency(override: Int? = null): Int {
    return override ?: DEFAULT_CONCURRENCY
}
