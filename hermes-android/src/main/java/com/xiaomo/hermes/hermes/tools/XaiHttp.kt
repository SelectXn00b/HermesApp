/** 1:1 对齐 hermes/tools/xai_http.py */
package com.xiaomo.hermes.hermes.tools

/**
 * Shared helpers for direct xAI HTTP integrations.
 */
object XaiHttp {

    /**
     * Return a stable Hermes-specific User-Agent for xAI HTTP calls.
     */
    fun hermesXaiUserAgent(): String {
        val version = try {
            // Attempt to read version from BuildConfig or similar
            Class.forName("com.xiaomo.hermes.BuildConfig")
                .getField("VERSION_NAME")
                .get(null) as? String ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        return "Hermes-Agent/$version"
    }
}
