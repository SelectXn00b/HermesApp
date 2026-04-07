package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/web-search.ts
 *
 * AndroidForClaw adaptation: web search tool using Brave Search API.
 * Aligned with OpenClaw web_search tool schema and behavior.
 */

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.websearch.WebSearchRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Web Search Tool — Search the web using Brave Search API.
 * Aligned with OpenClaw web_search tool.
 *
 * Requires BRAVE_API_KEY in openclaw.json config:
 *   tools.web.search.apiKey or env BRAVE_API_KEY
 */
class WebSearchTool(private val apiKeyProvider: () -> String?) : Tool {
    companion object {
        private const val TAG = "WebSearchTool"
        private const val BRAVE_API_URL = "https://api.search.brave.com/res/v1/web/search"
        private const val MAX_COUNT = 10
        private const val DEFAULT_COUNT = 5
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override val name = "web_search"
    override val description = "Search the web using Brave Search API. Supports region-specific and localized search via country and language parameters. Returns titles, URLs, and snippets for fast research."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "query" to PropertySchema(
                            type = "string",
                            description = "Search query string."
                        ),
                        "count" to PropertySchema(
                            type = "number",
                            description = "Number of results to return (1-10)."
                        ),
                        "country" to PropertySchema(
                            type = "string",
                            description = "2-letter country code for region-specific results (e.g., 'DE', 'US', 'ALL'). Default: 'US'."
                        ),
                        "language" to PropertySchema(
                            type = "string",
                            description = "ISO 639-1 language code for results (e.g., 'en', 'de', 'fr')."
                        ),
                        "freshness" to PropertySchema(
                            type = "string",
                            description = "Filter by time: 'day' (24h), 'week', 'month', or 'year'."
                        )
                    ),
                    required = listOf("query")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.error("Missing required parameter: query")

        val count = ((args["count"] as? Number)?.toInt() ?: DEFAULT_COUNT).coerceIn(1, MAX_COUNT)
        val country = args["country"] as? String
        val language = args["language"] as? String
        val freshness = args["freshness"] as? String

        // Check provider resolution via WebSearchRuntime
        val resolvedProvider = WebSearchRuntime.resolveWebSearchProviderId()
        Log.d(TAG, "WebSearchRuntime resolved provider: ${resolvedProvider ?: "none (using direct Brave API)"}")

        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return ToolResult.error("Brave Search API key not configured. Set tools.web.search.apiKey in openclaw.json or BRAVE_API_KEY environment variable.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = StringBuilder(BRAVE_API_URL)
                urlBuilder.append("?q=").append(URLEncoder.encode(query, "UTF-8"))
                urlBuilder.append("&count=").append(count)

                if (!country.isNullOrBlank()) {
                    urlBuilder.append("&country=").append(URLEncoder.encode(country, "UTF-8"))
                }
                if (!language.isNullOrBlank()) {
                    urlBuilder.append("&search_lang=").append(URLEncoder.encode(language, "UTF-8"))
                }
                if (!freshness.isNullOrBlank()) {
                    // Map OpenClaw freshness values to Brave API
                    val braveFreshness = when (freshness.lowercase()) {
                        "day" -> "pd"
                        "week" -> "pw"
                        "month" -> "pm"
                        "year" -> "py"
                        else -> freshness // pass through raw value
                    }
                    urlBuilder.append("&freshness=").append(URLEncoder.encode(braveFreshness, "UTF-8"))
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .header("X-Subscription-Token", apiKey)
                    .get()
                    .build()

                Log.d(TAG, "Searching: $query (count=$count)")

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    Log.e(TAG, "Search failed: ${response.code}")
                    return@withContext ToolResult.error("Search failed: HTTP ${response.code}")
                }

                val json = JSONObject(body)
                val webResults = json.optJSONObject("web")?.optJSONArray("results")

                if (webResults == null || webResults.length() == 0) {
                    return@withContext ToolResult.success("No results found for: $query")
                }

                val formatted = buildString {
                    for (i in 0 until webResults.length()) {
                        val result = webResults.getJSONObject(i)
                        val title = result.optString("title", "")
                        val url = result.optString("url", "")
                        val snippet = result.optString("description", "")

                        appendLine("${i + 1}. **$title**")
                        appendLine("   $url")
                        if (snippet.isNotBlank()) {
                            appendLine("   $snippet")
                        }
                        appendLine()
                    }
                }

                ToolResult.success(formatted.trim())

            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                ToolResult.error("Search failed: ${e.message}")
            }
        }
    }
}
