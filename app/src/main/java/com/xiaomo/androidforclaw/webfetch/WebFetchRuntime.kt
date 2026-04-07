package com.xiaomo.androidforclaw.webfetch

/**
 * OpenClaw module: web-fetch
 * Source: OpenClaw/src/web-fetch/runtime.ts (~167 LOC)
 *
 * Web fetch runtime: URL normalization, OkHttp GET with timeout / max-bytes,
 * content-type detection, provider resolution, and enablement checks.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.web.resolveWebProviderDefinition
import com.xiaomo.androidforclaw.web.hasWebProviderEntryCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object WebFetchRuntime {

    /** Known web fetch provider IDs. */
    private val FETCH_PROVIDER_IDS = listOf("firecrawl", "jina")

    private const val DEFAULT_USER_AGENT = "OpenClaw/1.0 WebFetch"

    // -----------------------------------------------------------------------
    // Core fetch
    // -----------------------------------------------------------------------

    /**
     * Fetch a web page, returning its textual content.
     *
     * Respects [WebFetchRequest.timeoutMs] and [WebFetchRequest.maxBytes].
     * When the response body exceeds maxBytes the content is truncated and
     * [WebFetchResult.truncated] is set to true.
     */
    suspend fun fetchWebPage(request: WebFetchRequest): WebFetchResult = withContext(Dispatchers.IO) {
        val normalized = normalizeWebFetchUrl(request.url)
            ?: throw IllegalArgumentException("Invalid URL: ${request.url}")

        val connection = URL(normalized).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = request.timeoutMs.toInt()
        connection.readTimeout = request.timeoutMs.toInt()
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
        request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }

        try {
            val statusCode = connection.responseCode
            val contentType = connection.contentType ?: "text/html"
            val stream = if (statusCode in 200..399) connection.inputStream else connection.errorStream

            val reader = BufferedReader(InputStreamReader(stream ?: connection.inputStream, "UTF-8"))
            val sb = StringBuilder()
            val buf = CharArray(8192)
            var totalRead = 0L
            var truncated = false

            while (true) {
                val n = reader.read(buf)
                if (n == -1) break
                val remaining = request.maxBytes - totalRead
                if (remaining <= 0) { truncated = true; break }
                val usable = minOf(n.toLong(), remaining).toInt()
                sb.append(buf, 0, usable)
                totalRead += usable
                if (usable < n) { truncated = true; break }
            }
            reader.close()

            WebFetchResult(
                content = sb.toString(),
                contentType = contentType,
                statusCode = statusCode,
                url = normalized,
                truncated = truncated
            )
        } finally {
            connection.disconnect()
        }
    }

    // -----------------------------------------------------------------------
    // Enablement
    // -----------------------------------------------------------------------

    fun resolveWebFetchEnabled(
        fetchConfig: Map<String, Any?>?,
        sandboxed: Boolean? = null
    ): Boolean {
        if (sandboxed == true) {
            return fetchConfig?.get("enabled") == true
        }
        return fetchConfig?.get("enabled") != false
    }

    // -----------------------------------------------------------------------
    // Provider resolution
    // -----------------------------------------------------------------------

    fun listWebFetchProviders(config: OpenClawConfig? = null): List<WebFetchProviderEntry> {
        return FETCH_PROVIDER_IDS.mapNotNull { id ->
            val def = resolveWebProviderDefinition(id, config)
            if (def != null) {
                val configured = if (def.envKey != null) hasWebProviderEntryCredential(def.envKey) else true
                WebFetchProviderEntry(id = id, label = def.label, configured = configured)
            } else {
                WebFetchProviderEntry(id = id, label = id, configured = false)
            }
        }
    }

    fun listConfiguredWebFetchProviders(config: OpenClawConfig? = null): List<WebFetchProviderEntry> {
        return listWebFetchProviders(config).filter { it.configured }
    }

    fun resolveWebFetchProviderId(
        config: OpenClawConfig? = null,
        preferredId: String? = null
    ): String? {
        if (preferredId != null) {
            val providers = listConfiguredWebFetchProviders(config)
            if (providers.any { it.id == preferredId }) return preferredId
        }
        return listConfiguredWebFetchProviders(config).firstOrNull()?.id
    }

    fun resolveWebFetchDefinition(
        config: OpenClawConfig? = null
    ): WebFetchDefinitionResult? {
        val providerId = resolveWebFetchProviderId(config) ?: return null
        return WebFetchDefinitionResult(
            providerId = providerId,
            enabled = true
        )
    }
}
