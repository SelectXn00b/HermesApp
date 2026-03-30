package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/payload-redaction.ts
 *
 * AndroidForClaw adaptation: image data redaction for diagnostics.
 */

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Payload redaction — replaces base64 image data with redaction placeholders.
 * Used for diagnostic logging and payload persistence.
 *
 * Aligned with OpenClaw payload-redaction.ts.
 */
object PayloadRedaction {

    const val REDACTED_IMAGE_DATA = "<redacted>"

    private val IMAGE_MIME_TYPES = setOf(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "image/svg+xml", "image/bmp", "image/tiff"
    )

    /**
     * Recursively redact base64 image data in a JSONObject for diagnostics.
     * When a record has type=image or an image MIME type and a data string field,
     * replaces data with "<redacted>" and adds estimated size + SHA-256 digest.
     *
     * Aligned with OpenClaw redactImageDataForDiagnostics.
     */
    fun redactImageDataForDiagnostics(obj: JSONObject): JSONObject {
        return redactObject(obj, mutableSetOf())
    }

    /**
     * Redact image data in a JSONArray.
     */
    fun redactImageDataForDiagnostics(arr: JSONArray): JSONArray {
        return redactArray(arr, mutableSetOf())
    }

    private fun redactObject(obj: JSONObject, visited: MutableSet<Int>): JSONObject {
        val id = System.identityHashCode(obj)
        if (id in visited) return obj
        visited.add(id)

        val result = JSONObject()
        val keys = obj.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)

            when {
                value is JSONObject -> {
                    result.put(key, redactObject(value, visited))
                }
                value is JSONArray -> {
                    result.put(key, redactArray(value, visited))
                }
                else -> {
                    result.put(key, value)
                }
            }
        }

        // Check if this object represents an image with base64 data
        if (isImageObject(result)) {
            val data = result.optString("data", "")
            if (data.isNotEmpty() && data != REDACTED_IMAGE_DATA) {
                val estimatedBytes = estimateBase64DecodedSize(data)
                val sha256 = sha256Hex(data)
                result.put("data", REDACTED_IMAGE_DATA)
                result.put("bytes", estimatedBytes)
                result.put("sha256", sha256)
            }
        }

        return result
    }

    private fun redactArray(arr: JSONArray, visited: MutableSet<Int>): JSONArray {
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val value = arr.opt(i)
            when (value) {
                is JSONObject -> result.put(redactObject(value, visited))
                is JSONArray -> result.put(redactArray(value, visited))
                else -> result.put(value)
            }
        }
        return result
    }

    private fun isImageObject(obj: JSONObject): Boolean {
        val type = obj.optString("type", "")
        if (type == "image") return true

        val mediaType = obj.optString("media_type", obj.optString("mediaType", ""))
        if (mediaType in IMAGE_MIME_TYPES) return true

        return false
    }

    private fun estimateBase64DecodedSize(base64: String): Long {
        // Base64 encodes 3 bytes in 4 chars; padding chars don't contribute
        val padding = base64.count { it == '=' }
        return ((base64.length.toLong() * 3) / 4) - padding
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
