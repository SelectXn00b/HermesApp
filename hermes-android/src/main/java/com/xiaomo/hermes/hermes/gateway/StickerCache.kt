package com.xiaomo.hermes.hermes.gateway

/**
 * Sticker description cache for Telegram.
 *
 * When users send stickers, we describe them via the vision tool and cache
 * the descriptions keyed by file_unique_id so we don't re-analyze the same
 * sticker image on every send. Descriptions are concise (1-2 sentences).
 *
 * Cache location: ~/.hermes/sticker_cache.json
 *
 * Ported from gateway/sticker_cache.py
 */

// Vision prompt for describing stickers — kept concise to save tokens
const val STICKER_VISION_PROMPT: String =
    "Describe this sticker in 1-2 sentences. Focus on what it depicts -- " +
    "character, action, emotion. Be concise and objective."

fun _loadCache(): Map<String, Any?> {
    // Python: _load_cache
    return emptyMap()
}

fun _saveCache(cache: Map<String, Any?>) {
    // Python: _save_cache
}

fun getCachedDescription(fileUniqueId: String): Map<String, Any?>? {
    // Python: get_cached_description
    return null
}

fun cacheStickerDescription(
    fileUniqueId: String,
    description: String,
    emoji: String = "",
    setName: String = "",
) {
    // Python: cache_sticker_description
}

fun buildStickerInjection(
    description: String,
    emoji: String = "",
    setName: String = "",
): String {
    // Python: build_sticker_injection
    val context = when {
        setName.isNotEmpty() && emoji.isNotEmpty() -> " $emoji from \"$setName\""
        emoji.isNotEmpty() -> " $emoji"
        else -> ""
    }
    return "[The user sent a sticker$context~ It shows: \"$description\" (=^.w.^=)]"
}

fun buildAnimatedStickerInjection(emoji: String = ""): String {
    // Python: build_animated_sticker_injection
    if (emoji.isNotEmpty()) {
        return "[The user sent an animated sticker $emoji~ " +
            "I can't see animated ones yet, but the emoji suggests: $emoji]"
    }
    return "[The user sent an animated sticker~ I can't see animated ones yet]"
}

/** Filesystem path to the sticker description cache (Python `CACHE_PATH`). */
val CACHE_PATH: java.io.File by lazy {
    val env = (System.getenv("HERMES_HOME") ?: "").trim()
    val home = if (env.isNotEmpty()) java.io.File(env)
    else java.io.File(System.getProperty("user.home") ?: "/", ".hermes")
    java.io.File(home, "sticker_cache.json")
}
