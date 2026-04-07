package com.xiaomo.androidforclaw.acp

/**
 * OpenClaw module: acp
 * Source: OpenClaw/src/acp/meta.ts
 *
 * Typed metadata helpers for reading values from ACP metadata maps.
 */
object AcpMeta {

    /**
     * Read a string value from a metadata map, trying multiple keys.
     * Aligned with TS readString().
     */
    fun readString(meta: Map<String, Any?>?, keys: List<String>): String? {
        if (meta == null) return null
        for (key in keys) {
            val value = meta[key]
            if (value is String && value.trim().isNotEmpty()) {
                return value.trim()
            }
        }
        return null
    }

    /**
     * Read a boolean value from a metadata map, trying multiple keys.
     * Aligned with TS readBool().
     */
    fun readBool(meta: Map<String, Any?>?, keys: List<String>): Boolean? {
        if (meta == null) return null
        for (key in keys) {
            val value = meta[key]
            if (value is Boolean) {
                return value
            }
        }
        return null
    }

    /**
     * Read a number value from a metadata map, trying multiple keys.
     * Aligned with TS readNumber().
     */
    fun readNumber(meta: Map<String, Any?>?, keys: List<String>): Number? {
        if (meta == null) return null
        for (key in keys) {
            val value = meta[key]
            if (value is Number && value.toDouble().isFinite()) {
                return value
            }
        }
        return null
    }
}
