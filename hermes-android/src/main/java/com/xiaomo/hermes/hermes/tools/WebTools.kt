/**
 * Web tools — fetch, search, and extract web content.
 *
 * Python supports Firecrawl / Tavily / Exa / Parallel / Nous auxiliary
 * LLM summarization. Android has no backend configured; the top-level
 * surface is stubbed to return toolError. Shape mirrors tools/web_tools.py
 * so registration stays aligned.
 *
 * Ported from tools/web_tools.py
 */
package com.xiaomo.hermes.hermes.tools

const val _TAVILY_BASE_URL: String = "https://api.tavily.com"
const val DEFAULT_MIN_LENGTH_FOR_SUMMARIZATION: Int = 5000

val WEB_SEARCH_SCHEMA: Map<String, Any> = mapOf(
    "name" to "web_search",
    "description" to "Search the web for information on any topic. Returns up to 5 relevant results with titles, URLs, and descriptions.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "The search query to look up on the web"),
        ),
        "required" to listOf("query"),
    ),
)

val WEB_EXTRACT_SCHEMA: Map<String, Any> = mapOf(
    "name" to "web_extract",
    "description" to "Extract content from web page URLs. Returns page content in markdown format. Also works with PDF URLs (arxiv papers, documents, etc.) — pass the PDF link directly and it converts to markdown text. Pages under 5000 chars return full markdown; larger pages are LLM-summarized and capped at ~5000 chars per page. Pages over 2M chars are refused. If a URL fails or times out, use the browser tool to access it instead.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "urls" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "List of URLs to extract content from (max 5 URLs per call)",
                "maxItems" to 5),
        ),
        "required" to listOf("urls"),
    ),
)

private fun _hasEnv(name: String): Boolean = !System.getenv(name).isNullOrBlank()

private fun _loadWebConfig(): Map<String, Any?> = emptyMap()

private fun _getBackend(): String = ""

private fun _isBackendAvailable(backend: String): Boolean = false

private fun _getDirectFirecrawlConfig(): Any? = null

private fun _getFirecrawlGatewayUrl(): String = ""

private fun _isToolGatewayReady(): Boolean = false

private fun _hasDirectFirecrawlConfig(): Boolean = false

private fun _raiseWebBackendConfigurationError(): Unit =
    throw IllegalStateException("web backend not configured")

private fun _firecrawlBackendHelpSuffix(): String = ""

private fun _webRequiresEnv(): List<String> = emptyList()

private fun _getFirecrawlClient(): Any? = null
private fun _getParallelClient(): Any? = null
private fun _getAsyncParallelClient(): Any? = null

private fun _tavilyRequest(endpoint: String, payload: Map<String, Any?>): Map<String, Any?> = emptyMap()
private fun _normalizeTavilySearchResults(response: Map<String, Any?>): Map<String, Any?> = emptyMap()
private fun _normalizeTavilyDocuments(response: Map<String, Any?>, fallbackUrl: String = ""): List<Map<String, Any?>> = emptyList()

private fun _toPlainObject(value: Any?): Any? = value
private fun _normalizeResultList(values: Any?): List<Map<String, Any?>> = emptyList()
private fun _extractWebSearchResults(response: Any?): List<Map<String, Any?>> = emptyList()
private fun _extractScrapePayload(scrapeResult: Any?): Map<String, Any?> = emptyMap()

private fun _isNousAuxiliaryClient(client: Any?): Boolean = false
private fun _resolveWebExtractAuxiliary(model: String? = null): Triple<Any?, String?, Map<String, Any>> =
    Triple(null, null, emptyMap())

private fun _getDefaultSummarizerModel(): String? = null

fun cleanBase64Images(text: String): String = text

private fun _getExaClient(): Any? = null
private fun _exaSearch(query: String, limit: Int = 10): Map<String, Any?> = emptyMap()
private fun _exaExtract(urls: List<String>): List<Map<String, Any?>> = emptyList()
private fun _parallelSearch(query: String, limit: Int = 5): Map<String, Any?> = emptyMap()

fun webSearchTool(query: String, limit: Int = 5): String =
    toolError("web_search tool is not available on Android")

suspend fun webExtractTool(
    urls: List<String>,
    format: String? = null,
    useLlmProcessing: Boolean = true,
    model: String? = null,
    minLength: Int = DEFAULT_MIN_LENGTH_FOR_SUMMARIZATION,
): String = toolError("web_extract tool is not available on Android")

suspend fun webCrawlTool(vararg args: Any?): String =
    toolError("web_crawl tool is not available on Android")

fun checkFirecrawlApiKey(): Boolean = false
fun checkWebApiKey(): Boolean = false
fun checkAuxiliaryModel(): Boolean = false
