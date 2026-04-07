package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/json-pointer.ts
 *
 * JSON Pointer (RFC 6901) read/write operations for secrets file providers.
 */

/**
 * Decode a JSON pointer token: ~1 -> /, ~0 -> ~
 * Aligned with TS decodeJsonPointerToken.
 */
fun decodeJsonPointerToken(token: String): String =
    token.replace("~1", "/").replace("~0", "~")

/**
 * Encode a JSON pointer token: ~ -> ~0, / -> ~1
 * Aligned with TS encodeJsonPointerToken.
 */
fun encodeJsonPointerToken(token: String): String =
    token.replace("~", "~0").replace("/", "~1")

/**
 * Read a value at a JSON pointer path from a root object.
 * Aligned with TS readJsonPointer.
 *
 * @param root The root object to traverse
 * @param pointer The JSON pointer string (must start with "/")
 * @param throwOnMissing If true, throws on missing segments; if false, returns null
 */
fun readJsonPointer(
    root: Any?,
    pointer: String,
    throwOnMissing: Boolean = true
): Any? {
    if (!pointer.startsWith("/")) {
        if (throwOnMissing) {
            throw IllegalArgumentException(
                "File-backed secret ids must be absolute JSON pointers " +
                        "(for example: \"/providers/openai/apiKey\")."
            )
        }
        return null
    }

    val tokens = pointer.substring(1)
        .split("/")
        .map { decodeJsonPointerToken(it) }

    var current: Any? = root
    for (token in tokens) {
        when {
            current is List<*> -> {
                val index = token.toIntOrNull()
                if (index == null || index < 0 || index >= current.size) {
                    if (throwOnMissing) {
                        throw IllegalArgumentException(
                            "JSON pointer segment \"$token\" is out of bounds."
                        )
                    }
                    return null
                }
                current = current[index]
            }
            current is Map<*, *> -> {
                if (!current.containsKey(token)) {
                    if (throwOnMissing) {
                        throw IllegalArgumentException(
                            "JSON pointer segment \"$token\" does not exist."
                        )
                    }
                    return null
                }
                current = current[token]
            }
            else -> {
                if (throwOnMissing) {
                    throw IllegalArgumentException(
                        "JSON pointer segment \"$token\" does not exist."
                    )
                }
                return null
            }
        }
    }
    return current
}

/**
 * Set a value at a JSON pointer path in a mutable map.
 * Aligned with TS setJsonPointer.
 */
fun setJsonPointer(
    root: MutableMap<String, Any?>,
    pointer: String,
    value: Any?
) {
    if (!pointer.startsWith("/")) {
        throw IllegalArgumentException("Invalid JSON pointer \"$pointer\".")
    }

    val tokens = pointer.substring(1)
        .split("/")
        .map { decodeJsonPointerToken(it) }

    var current: MutableMap<String, Any?> = root
    for (i in tokens.indices) {
        val token = tokens[i]
        val isLast = i == tokens.size - 1
        if (isLast) {
            current[token] = value
            return
        }
        val child = current[token]
        if (child !is MutableMap<*, *>) {
            current[token] = mutableMapOf<String, Any?>()
        }
        @Suppress("UNCHECKED_CAST")
        current = current[token] as MutableMap<String, Any?>
    }
}
