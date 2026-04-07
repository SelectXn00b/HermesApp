package com.xiaomo.androidforclaw.linkunderstanding

import com.xiaomo.androidforclaw.config.OpenClawConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenClaw module: link-understanding
 * Source: OpenClaw/src/link-understanding/runtime.ts
 *
 * Orchestrates link detection -> fetch -> og:meta parsing -> preview generation.
 * Supports configurable timeout and concurrent fetching.
 */
object LinkUnderstandingRuntime {

    // -----------------------------------------------------------------------
    // og:meta extraction regexes (handles both property-first and content-first orders)
    // -----------------------------------------------------------------------

    private fun ogRegex(property: String): Regex = Regex(
        """<meta\s+(?:property\s*=\s*["']og:${property}["']\s+content\s*=\s*["']([^"']*)["']|content\s*=\s*["']([^"']*)["']\s+property\s*=\s*["']og:${property}["'])""",
        RegexOption.IGNORE_CASE
    )

    private val OG_TITLE = ogRegex("title")
    private val OG_DESC = ogRegex("description")
    private val OG_IMAGE = ogRegex("image")
    private val OG_SITE = ogRegex("site_name")
    private val OG_TYPE = ogRegex("type")
    private val TITLE_TAG = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)

    private const val DEFAULT_TIMEOUT_MS = 5000
    private const val DEFAULT_USER_AGENT = "OpenClaw/1.0 LinkPreview"

    // -----------------------------------------------------------------------
    // OG meta extraction from raw HTML
    // -----------------------------------------------------------------------

    /**
     * Extract all og: meta tag values from an HTML string.
     * Returns a [LinkUnderstandingResult] populated with whatever tags were found.
     */
    fun extractOgMetaTags(html: String, url: String): LinkUnderstandingResult {
        fun firstGroup(regex: Regex): String? {
            val m = regex.find(html) ?: return null
            return m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { null } }
        }

        val ogTitle = firstGroup(OG_TITLE)
        val ogDesc = firstGroup(OG_DESC)
        val ogImage = firstGroup(OG_IMAGE)
        val ogSite = firstGroup(OG_SITE)
        val ogType = firstGroup(OG_TYPE)
        val fallbackTitle = TITLE_TAG.find(html)?.groupValues?.get(1)?.trim()

        return LinkUnderstandingResult(
            url = url,
            title = ogTitle ?: fallbackTitle,
            description = ogDesc,
            imageUrl = ogImage,
            siteName = ogSite,
            type = ogType
        )
    }

    // -----------------------------------------------------------------------
    // Single-link fetch
    // -----------------------------------------------------------------------

    /**
     * Fetch a URL and parse its og: metadata.
     *
     * @param url       The URL to fetch.
     * @param config    Optional OpenClawConfig (reserved for future provider routing).
     * @param timeoutMs Connect + read timeout in milliseconds.
     */
    suspend fun fetchLinkMetadata(
        url: String,
        config: OpenClawConfig? = null,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): LinkUnderstandingResult? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            connection.instanceFollowRedirects = true

            val statusCode = connection.responseCode
            if (statusCode !in 200..399) {
                connection.disconnect()
                return@withContext null
            }

            // Read only enough to parse <head> (first 64 KB is usually sufficient)
            val maxHeadBytes = 65_536
            val stream = connection.inputStream
            val buf = ByteArray(maxHeadBytes)
            var totalRead = 0
            while (totalRead < maxHeadBytes) {
                val n = stream.read(buf, totalRead, maxHeadBytes - totalRead)
                if (n == -1) break
                totalRead += n
            }
            stream.close()
            connection.disconnect()

            val html = String(buf, 0, totalRead, Charsets.UTF_8)
            extractOgMetaTags(html, url)
        } catch (_: Exception) {
            null
        }
    }

    // Backward-compat alias
    suspend fun fetchLinkPreview(url: String, config: OpenClawConfig): LinkUnderstandingResult? {
        return fetchLinkMetadata(url, config)
    }

    // -----------------------------------------------------------------------
    // Batch processing
    // -----------------------------------------------------------------------

    /**
     * Detect links in a message, fetch metadata for each (concurrently), and
     * return a list of previews.
     */
    suspend fun processMessageLinks(
        text: String,
        config: OpenClawConfig,
        maxLinks: Int = 3,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): List<LinkUnderstandingResult> = coroutineScope {
        val links = extractLinksFromMessage(text).take(maxLinks)
        links.map { detected ->
            async { fetchLinkMetadata(detected.url, config, timeoutMs) }
        }.awaitAll().filterNotNull()
    }

    // -----------------------------------------------------------------------
    // Enablement check
    // -----------------------------------------------------------------------

    fun isLinkUnderstandingEnabled(config: OpenClawConfig): Boolean {
        // Enabled by default; no explicit config field to disable yet.
        return true
    }
}
