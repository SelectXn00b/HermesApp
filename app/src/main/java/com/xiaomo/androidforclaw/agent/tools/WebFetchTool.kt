package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/web-fetch.ts
 *
 * AndroidForClaw adaptation: web fetch tool.
 */


import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.webfetch.WebFetchRequest
import com.xiaomo.androidforclaw.webfetch.WebFetchRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Web Fetch Tool - Fetch web page content
 * Reference: nanobot's WebFetchTool
 */
class WebFetchTool(
    private val maxChars: Int = 50000
) : Tool {
    companion object {
        private const val TAG = "WebFetchTool"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override val name = "web_fetch"
    override val description = "Fetch and extract content from a URL"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "url" to PropertySchema("string", "要获取的 URL"),
                        "max_chars" to PropertySchema("integer", "最大返回字符数，默认 50000")
                    ),
                    required = listOf("url")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val url = args["url"] as? String
        val maxCharsParam = (args["max_chars"] as? Number)?.toInt() ?: maxChars

        if (url == null) {
            return ToolResult.error("Missing required parameter: url")
        }

        // URL validation
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.error("URL must start with http:// or https://")
        }

        Log.d(TAG, "Fetching URL: $url")
        return withContext(Dispatchers.IO) {
            try {
                // Delegate to WebFetchRuntime (Wave module) for URL normalization and fetch
                val fetchResult = WebFetchRuntime.fetchWebPage(WebFetchRequest(
                    url = url,
                    timeoutMs = 30_000,
                    maxBytes = maxCharsParam.toLong() * 2 // bytes > chars, allow extra
                ))

                val contentType = fetchResult.contentType
                val body = fetchResult.content

                if (fetchResult.statusCode !in 200..399) {
                    return@withContext ToolResult.error("HTTP ${fetchResult.statusCode}")
                }

                // Simple content extraction (strip HTML tags)
                val content = when {
                    contentType.contains("application/json", ignoreCase = true) -> body
                    contentType.contains("text/html", ignoreCase = true) -> stripHtmlTags(body)
                    else -> body
                }

                // Truncate overly long content
                val finalContent = if (content.length > maxCharsParam) {
                    content.take(maxCharsParam) + "\n... (truncated, ${content.length - maxCharsParam} more chars)"
                } else {
                    content
                }

                val metadata = mutableMapOf<String, Any>(
                    "url" to fetchResult.url,
                    "length" to content.length
                )
                if (fetchResult.truncated) metadata["truncated"] = true

                ToolResult.success(finalContent, metadata)
            } catch (e: Exception) {
                Log.e(TAG, "Web fetch failed", e)
                ToolResult.error("Web fetch failed: ${e.message}")
            }
        }
    }

    /**
     * Simple HTML tag cleanup
     */
    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<[^>]+>"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
