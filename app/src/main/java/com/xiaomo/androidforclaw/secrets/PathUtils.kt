package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/path-utils.ts
 *
 * Config path traversal utilities for reading, setting, and deleting
 * values at dot-separated paths in nested Map structures.
 */

private fun isArrayIndexSegment(segment: String): Boolean =
    segment.matches(Regex("^\\d+\$"))

/**
 * Get a value at a dot-separated path from a root object.
 * Aligned with TS getPath.
 */
fun getPath(root: Any?, segments: List<String>): Any? {
    if (segments.isEmpty()) return null
    var cursor: Any? = root
    for (segment in segments) {
        when {
            cursor is List<*> -> {
                if (!isArrayIndexSegment(segment)) return null
                val index = segment.toIntOrNull() ?: return null
                if (index < 0 || index >= cursor.size) return null
                cursor = cursor[index]
            }
            cursor is Map<*, *> -> {
                if (!cursor.containsKey(segment)) return null
                cursor = cursor[segment]
            }
            else -> return null
        }
    }
    return cursor
}

/**
 * Set a value at a path, creating intermediate containers as needed.
 * Returns true if any change was made.
 * Aligned with TS setPathCreateStrict.
 */
fun setPathCreateStrict(
    root: MutableMap<String, Any?>,
    segments: List<String>,
    value: Any?
): Boolean {
    if (segments.isEmpty()) throw IllegalArgumentException("Target path is empty.")

    var cursor: Any? = root
    var changed = false

    for (i in 0 until segments.size - 1) {
        val segment = segments[i]
        val nextSegment = segments.getOrElse(i + 1) { "" }
        val needsArray = isArrayIndexSegment(nextSegment)

        when {
            cursor is MutableList<*> -> {
                if (!isArrayIndexSegment(segment)) {
                    throw IllegalArgumentException(
                        "Invalid array index segment \"$segment\" at ${segments.joinToString(".")}."
                    )
                }
                val arrayIndex = segment.toInt()
                @Suppress("UNCHECKED_CAST")
                val list = cursor as MutableList<Any?>
                val existing = list.getOrNull(arrayIndex)
                if (existing == null) {
                    while (list.size <= arrayIndex) list.add(null)
                    list[arrayIndex] = if (needsArray) mutableListOf<Any?>() else mutableMapOf<String, Any?>()
                    changed = true
                }
                cursor = list[arrayIndex]
            }
            cursor is MutableMap<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = cursor as MutableMap<String, Any?>
                val existing = map[segment]
                if (existing == null) {
                    map[segment] = if (needsArray) mutableListOf<Any?>() else mutableMapOf<String, Any?>()
                    changed = true
                }
                cursor = map[segment]
            }
            else -> throw IllegalArgumentException(
                "Invalid path shape at ${segments.take(i).joinToString(".").ifEmpty { "<root>" }}."
            )
        }
    }

    val leaf = segments.last()
    when {
        cursor is MutableList<*> -> {
            if (!isArrayIndexSegment(leaf)) {
                throw IllegalArgumentException(
                    "Invalid array index segment \"$leaf\" at ${segments.joinToString(".")}."
                )
            }
            @Suppress("UNCHECKED_CAST")
            val list = cursor as MutableList<Any?>
            val index = leaf.toInt()
            while (list.size <= index) list.add(null)
            if (list[index] != value) {
                list[index] = value
                changed = true
            }
        }
        cursor is MutableMap<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val map = cursor as MutableMap<String, Any?>
            if (map[leaf] != value) {
                map[leaf] = value
                changed = true
            }
        }
        else -> throw IllegalArgumentException(
            "Invalid path shape at ${segments.dropLast(1).joinToString(".").ifEmpty { "<root>" }}."
        )
    }
    return changed
}

/**
 * Delete a value at a path. Returns true if a value was deleted.
 * Aligned with TS deletePathStrict.
 */
fun deletePathStrict(root: MutableMap<String, Any?>, segments: List<String>): Boolean {
    if (segments.isEmpty()) throw IllegalArgumentException("Target path is empty.")

    var cursor: Any? = root
    for (i in 0 until segments.size - 1) {
        val segment = segments[i]
        when {
            cursor is List<*> -> {
                if (!isArrayIndexSegment(segment)) return false
                val index = segment.toIntOrNull() ?: return false
                if (index < 0 || index >= cursor.size) return false
                cursor = cursor[index]
            }
            cursor is Map<*, *> -> {
                if (!cursor.containsKey(segment)) return false
                cursor = cursor[segment]
            }
            else -> return false
        }
    }

    val leaf = segments.last()
    when {
        cursor is MutableList<*> -> {
            if (!isArrayIndexSegment(leaf)) return false
            val index = leaf.toIntOrNull() ?: return false
            if (index < 0 || index >= cursor.size) return false
            cursor.removeAt(index)
            return true
        }
        cursor is MutableMap<*, *> -> {
            if (!cursor.containsKey(leaf)) return false
            cursor.remove(leaf)
            return true
        }
        else -> return false
    }
}
