package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Web tools — fetch and extract web content.
 * Ported from web_tools.py
 */
object WebTools {

    private const val TAG = "WebTools"
    private const val TIMEOUT_SECONDS = 30L
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class FetchResult(
        val success: Boolean = false,
        val content: String = "",
        val url: String = "",
        val statusCode: Int = 0,
        val contentType: String? = null,
        val error: String? = null)

    /**
     * Fetch a URL and return the response body.
     */
    fun fetch(url: String, extractMode: String = "markdown", maxChars: Int = 50000): String {
        // SSRF protection
        if (!UrlSafety.isSafeUrl(url)) {
            return gson.toJson(mapOf("error" to "URL blocked by SSRF protection: $url"))
        }

        // Website policy check
        val blocked = WebsitePolicy.checkWebsiteAccess(url)
        if (blocked != null) {
            return gson.toJson(mapOf("error" to blocked.message))
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Hermes-Agent/1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val statusCode = response.code
                val contentType = response.header("Content-Type")

                if (!response.isSuccessful) {
                    return gson.toJson(mapOf(
                        "error" to "HTTP $statusCode",
                        "status_code" to statusCode))
                }

                val content = when (extractMode) {
                    "text" -> stripHtml(body).take(maxChars)
                    else -> body.take(maxChars)
                }

                gson.toJson(mapOf(
                    "success" to true,
                    "content" to content,
                    "url" to url,
                    "status_code" to statusCode,
                    "content_type" to contentType))
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to "Fetch failed: ${e.message}"))
        }
    }

    /**
     * Strip HTML tags to get plain text.
     */
    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }


    // === Missing constants (auto-generated stubs) ===
    val _TAVILY_BASE_URL = ""
    val DEFAULT_MIN_LENGTH_FOR_SUMMARIZATION = ""
    val WEB_SEARCH_SCHEMA = ""
    val WEB_EXTRACT_SCHEMA = ""

    // === Missing methods (auto-generated stubs) ===
    private fun hasEnv(name: String): Unit {
    // Hermes: _has_env
}
}
