package com.xiaomo.androidforclaw.webfetch

/**
 * OpenClaw module: web-fetch
 * Source: OpenClaw/src/web-fetch/runtime.ts (types portion)
 *
 * Request / result types for the web-fetch skill, provider entry types,
 * and URL normalization.
 */

// ---------------------------------------------------------------------------
// Request / Result — aligned with OpenClaw WebFetchRequest, WebFetchResult
// ---------------------------------------------------------------------------

data class WebFetchRequest(
    val url: String,
    val agentDir: String? = null,
    val timeoutMs: Long = 15_000,
    val maxBytes: Long = 1_048_576, // 1 MiB default
    val headers: Map<String, String> = emptyMap()
)

data class WebFetchResult(
    val content: String,
    val contentType: String,
    val statusCode: Int,
    val url: String,
    val truncated: Boolean = false
)

// ---------------------------------------------------------------------------
// Provider resolution types (kept for backward compatibility with WebFetchRuntime)
// ---------------------------------------------------------------------------

data class WebFetchProviderEntry(
    val id: String,
    val label: String?,
    val configured: Boolean
)

data class WebFetchDefinitionResult(
    val providerId: String,
    val enabled: Boolean
)

// ---------------------------------------------------------------------------
// URL helpers
// ---------------------------------------------------------------------------

/**
 * Validate and normalize a raw URL string.
 *
 * - Trims whitespace.
 * - Ensures the scheme is http or https (prepends https:// when missing).
 * - Returns null for clearly invalid input.
 */
fun normalizeWebFetchUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()

    // Already has a scheme
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return try {
            java.net.URL(trimmed) // validates
            trimmed
        } catch (_: Exception) {
            null
        }
    }

    // Looks like a domain (contains a dot, no spaces)
    if (trimmed.contains('.') && !trimmed.contains(' ')) {
        val withScheme = "https://$trimmed"
        return try {
            java.net.URL(withScheme)
            withScheme
        } catch (_: Exception) {
            null
        }
    }

    return null
}
