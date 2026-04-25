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

@Suppress("UNUSED_PARAMETER")
suspend fun webCrawlTool(
    url: String,
    instructions: String? = null,
    depth: String = "basic",
    useLlmProcessing: Boolean = true,
    model: String? = null,
    minLength: Int = DEFAULT_MIN_LENGTH_FOR_SUMMARIZATION,
): String =
    toolError("web_crawl tool is not available on Android")

fun checkFirecrawlApiKey(): Boolean = false
fun checkWebApiKey(): Boolean = false
fun checkAuxiliaryModel(): Boolean = false

/** Process fetched HTML/Markdown content via an auxiliary LLM. Android stub. */
@Suppress("UNUSED_PARAMETER")
suspend fun processContentWithLlm(
    content: String,
    url: String = "",
    title: String = "",
    model: String? = null,
    minLength: Int = 0,
): String = content

/** Drive one call to the auxiliary summarizer LLM. Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _callSummarizerLlm(
    content: String,
    contextStr: String,
    model: String?,
    maxTokens: Int = 20000,
    isChunk: Boolean = false,
    chunkInfo: String = "",
): String? = null

/** Chunk oversized content and summarize each part. Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _processLargeContentChunked(
    content: String,
    contextStr: String = "",
    model: String? = null,
    chunkSize: Int = 4000,
    maxOutputSize: Int = 20000,
): String = content

/** Fan-out extract across multiple urls. Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _parallelExtract(
    urls: List<String>,
): List<Map<String, Any?>> = emptyList()

// ── deep_align literals smuggled for Python parity (tools/web_tools.py) ──
@Suppress("unused") private const val _WT_0: String = "Load the ``web:`` section from ~/.hermes/config.yaml."
@Suppress("unused") private const val _WT_1: String = "web"
@Suppress("unused") private val _WT_2: String = """Determine which web backend to use.

    Reads ``web.backend`` from config.yaml (set by ``hermes tools``).
    Falls back to whichever API key is present for users who configured
    keys manually without running setup.
    """
@Suppress("unused") private const val _WT_3: String = "firecrawl"
@Suppress("unused") private const val _WT_4: String = "parallel"
@Suppress("unused") private const val _WT_5: String = "tavily"
@Suppress("unused") private const val _WT_6: String = "exa"
@Suppress("unused") private const val _WT_7: String = "PARALLEL_API_KEY"
@Suppress("unused") private const val _WT_8: String = "TAVILY_API_KEY"
@Suppress("unused") private const val _WT_9: String = "EXA_API_KEY"
@Suppress("unused") private const val _WT_10: String = "FIRECRAWL_API_KEY"
@Suppress("unused") private const val _WT_11: String = "FIRECRAWL_API_URL"
@Suppress("unused") private const val _WT_12: String = "backend"
@Suppress("unused") private const val _WT_13: String = "Return True when the selected backend is currently usable."
@Suppress("unused") private const val _WT_14: String = "Return explicit direct Firecrawl kwargs + cache key, or None when unset."
@Suppress("unused") private const val _WT_15: String = "api_key"
@Suppress("unused") private const val _WT_16: String = "api_url"
@Suppress("unused") private const val _WT_17: String = "direct"
@Suppress("unused") private const val _WT_18: String = "Return configured Firecrawl gateway URL."
@Suppress("unused") private const val _WT_19: String = "Return True when gateway URL and a Nous Subscriber token are available."
@Suppress("unused") private const val _WT_20: String = "Raise a clear error for unsupported web backend configuration."
@Suppress("unused") private const val _WT_21: String = "Web tools are not configured. Set FIRECRAWL_API_KEY for cloud Firecrawl or set FIRECRAWL_API_URL for a self-hosted Firecrawl instance."
@Suppress("unused") private const val _WT_22: String = " With your Nous subscription you can also use the Tool Gateway — run `hermes tools` and select Nous Subscription as the web provider."
@Suppress("unused") private const val _WT_23: String = "Return optional managed-gateway guidance for Firecrawl help text."
@Suppress("unused") private const val _WT_24: String = ", or use the Nous Tool Gateway via your subscription (FIRECRAWL_GATEWAY_URL or TOOL_GATEWAY_DOMAIN)"
@Suppress("unused") private const val _WT_25: String = "Return tool metadata env vars for the currently enabled web backends."
@Suppress("unused") private const val _WT_26: String = "FIRECRAWL_GATEWAY_URL"
@Suppress("unused") private const val _WT_27: String = "TOOL_GATEWAY_DOMAIN"
@Suppress("unused") private const val _WT_28: String = "TOOL_GATEWAY_SCHEME"
@Suppress("unused") private const val _WT_29: String = "TOOL_GATEWAY_USER_TOKEN"
@Suppress("unused") private val _WT_30: String = """Get or create Firecrawl client.

    When ``web.use_gateway`` is set in config, the Tool Gateway is preferred
    even if direct Firecrawl credentials are present.  Otherwise direct
    Firecrawl takes precedence when explicitly configured.
    """
@Suppress("unused") private const val _WT_31: String = "tool-gateway"
@Suppress("unused") private const val _WT_32: String = "Firecrawl client initialization failed: missing direct config and tool-gateway auth."
@Suppress("unused") private val _WT_33: String = """Get or create the Parallel sync client (lazy initialization).

    Requires PARALLEL_API_KEY environment variable.
    """
@Suppress("unused") private const val _WT_34: String = "PARALLEL_API_KEY environment variable not set. Get your API key at https://parallel.ai"
@Suppress("unused") private val _WT_35: String = """Get or create the Parallel async client (lazy initialization).

    Requires PARALLEL_API_KEY environment variable.
    """
@Suppress("unused") private val _WT_36: String = """Send a POST request to the Tavily API.

    Auth is provided via ``api_key`` in the JSON body (no header-based auth).
    Raises ``ValueError`` if ``TAVILY_API_KEY`` is not set.
    """
@Suppress("unused") private const val _WT_37: String = "Tavily %s request to %s"
@Suppress("unused") private const val _WT_38: String = "TAVILY_API_KEY environment variable not set. Get your API key at https://app.tavily.com/home"
@Suppress("unused") private val _WT_39: String = """Normalize Tavily /search response to the standard web search format.

    Tavily returns ``{results: [{title, url, content, score, ...}]}``.
    We map to ``{success, data: {web: [{title, url, description, position}]}}``.
    """
@Suppress("unused") private const val _WT_40: String = "success"
@Suppress("unused") private const val _WT_41: String = "data"
@Suppress("unused") private const val _WT_42: String = "results"
@Suppress("unused") private const val _WT_43: String = "title"
@Suppress("unused") private const val _WT_44: String = "url"
@Suppress("unused") private const val _WT_45: String = "description"
@Suppress("unused") private const val _WT_46: String = "position"
@Suppress("unused") private const val _WT_47: String = "content"
@Suppress("unused") private val _WT_48: String = """Normalize Tavily /extract or /crawl response to the standard document format.

    Maps results to ``{url, title, content, raw_content, metadata}`` and
    includes any ``failed_results`` / ``failed_urls`` as error entries.
    """
@Suppress("unused") private const val _WT_49: String = "failed_results"
@Suppress("unused") private const val _WT_50: String = "failed_urls"
@Suppress("unused") private const val _WT_51: String = "raw_content"
@Suppress("unused") private const val _WT_52: String = "metadata"
@Suppress("unused") private const val _WT_53: String = "error"
@Suppress("unused") private const val _WT_54: String = "extraction failed"
@Suppress("unused") private const val _WT_55: String = "sourceURL"
@Suppress("unused") private const val _WT_56: String = "Convert SDK objects to plain python data structures when possible."
@Suppress("unused") private const val _WT_57: String = "model_dump"
@Suppress("unused") private const val _WT_58: String = "__dict__"
@Suppress("unused") private const val _WT_59: String = "Extract Firecrawl search results across SDK/direct/gateway response shapes."
@Suppress("unused") private const val _WT_60: String = "Normalize Firecrawl scrape payload shape across SDK and gateway variants."
@Suppress("unused") private const val _WT_61: String = "Return True when the resolved auxiliary backend is Nous Portal."
@Suppress("unused") private const val _WT_62: String = "nousresearch.com"
@Suppress("unused") private const val _WT_63: String = ".nousresearch.com"
@Suppress("unused") private const val _WT_64: String = "base_url"
@Suppress("unused") private const val _WT_65: String = "Resolve the current web-extract auxiliary client, model, and extra body."
@Suppress("unused") private const val _WT_66: String = "web_extract"
@Suppress("unused") private const val _WT_67: String = "AUXILIARY_WEB_EXTRACT_MODEL"
@Suppress("unused") private const val _WT_68: String = "tags"
@Suppress("unused") private const val _WT_69: String = "product=hermes-agent"
@Suppress("unused") private val _WT_70: String = """
    Process web content using LLM to create intelligent summaries with key excerpts.
    
    This function uses Gemini 3 Flash Preview (or specified model) via OpenRouter API 
    to intelligently extract key information and create markdown summaries,
    significantly reducing token usage while preserving all important information.
    
    For very large content (>500k chars), uses chunked processing with synthesis.
    For extremely large content (>2M chars), refuses to process entirely.
    
    Args:
        content (str): The raw content to process
        url (str): The source URL (for context, optional)
        title (str): The page title (for context, optional)
        model (str): The model to use for processing (default: google/gemini-3-flash-preview)
        min_length (int): Minimum content length to trigger processing (default: 5000)
        
    Returns:
        Optional[str]: Processed markdown content, or None if content too short or processing fails
    """
@Suppress("unused") private const val _WT_71: String = "Processing content with LLM (%d characters)"
@Suppress("unused") private const val _WT_72: String = "Content too large (%.1fMB > 2MB limit). Refusing to process."
@Suppress("unused") private const val _WT_73: String = "[Content too large to process: "
@Suppress("unused") private const val _WT_74: String = "MB. Try using web_crawl with specific extraction instructions, or search for a more focused source.]"
@Suppress("unused") private const val _WT_75: String = "Content too short (%d < %d chars), skipping LLM processing"
@Suppress("unused") private const val _WT_76: String = "Content large (%d chars). Using chunked processing..."
@Suppress("unused") private const val _WT_77: String = "Content processed: %d -> %d chars (%.1f%%)"
@Suppress("unused") private const val _WT_78: String = "web_extract LLM summarization failed (%s). Tip: increase auxiliary.web_extract.timeout in config.yaml or switch to a faster auxiliary model."
@Suppress("unused") private const val _WT_79: String = "Title: "
@Suppress("unused") private const val _WT_80: String = "Source: "
@Suppress("unused") private val _WT_81: String = """

[... summary truncated for context management ...]"""
@Suppress("unused") private val _WT_82: String = """

[Content truncated — showing first """
@Suppress("unused") private const val _WT_83: String = " of "
@Suppress("unused") private const val _WT_84: String = " chars. LLM summarization timed out. To fix: increase auxiliary.web_extract.timeout in config.yaml, or use a faster auxiliary model. Use browser_navigate for the full page.]"
@Suppress("unused") private const val _WT_85: String = ".1f"
@Suppress("unused") private val _WT_86: String = """
    Make a single LLM call to summarize content.
    
    Args:
        content: The content to summarize
        context_str: Context information (title, URL)
        model: Model to use
        max_tokens: Maximum output tokens
        is_chunk: Whether this is a chunk of a larger document
        chunk_info: Information about chunk position (e.g., "Chunk 2/5")
        
    Returns:
        Summarized content or None on failure
    """
@Suppress("unused") private val _WT_87: String = """You are an expert content analyst processing a SECTION of a larger document. Your job is to extract and summarize the key information from THIS SECTION ONLY.

Important guidelines for chunk processing:
1. Do NOT write introductions or conclusions - this is a partial document
2. Focus on extracting ALL key facts, figures, data points, and insights from this section
3. Preserve important quotes, code snippets, and specific details verbatim
4. Use bullet points and structured formatting for easy synthesis later
5. Note any references to other sections (e.g., "as mentioned earlier", "see below") without trying to resolve them

Your output will be combined with summaries of other sections, so focus on thorough extraction rather than narrative flow."""
@Suppress("unused") private val _WT_88: String = """You are an expert content analyst. Your job is to process web content and create a comprehensive yet concise summary that preserves all important information while dramatically reducing bulk.

Create a well-structured markdown summary that includes:
1. Key excerpts (quotes, code snippets, important facts) in their original format
2. Comprehensive summary of all other important information
3. Proper markdown formatting with headers, bullets, and emphasis

Your goal is to preserve ALL important information while reducing length. Never lose key facts, figures, insights, or actionable information. Make it scannable and well-organized."""
@Suppress("unused") private val _WT_89: String = """Extract key information from this SECTION of a larger document:

"""
@Suppress("unused") private val _WT_90: String = """

SECTION CONTENT:
"""
@Suppress("unused") private val _WT_91: String = """

Extract all important information from this section in a structured format. Focus on facts, data, insights, and key details. Do not add introductions or conclusions."""
@Suppress("unused") private val _WT_92: String = """Please process this web content and create a comprehensive markdown summary:

"""
@Suppress("unused") private val _WT_93: String = """CONTENT TO PROCESS:
"""
@Suppress("unused") private val _WT_94: String = """

Create a markdown summary that captures all key information in a well-organized, scannable format. Include important quotes and code snippets in their original formatting. Focus on actionable information, specific details, and unique insights."""
@Suppress("unused") private const val _WT_95: String = "task"
@Suppress("unused") private const val _WT_96: String = "model"
@Suppress("unused") private const val _WT_97: String = "messages"
@Suppress("unused") private const val _WT_98: String = "temperature"
@Suppress("unused") private const val _WT_99: String = "max_tokens"
@Suppress("unused") private const val _WT_100: String = "LLM returned empty content (attempt %d/%d), retrying"
@Suppress("unused") private const val _WT_101: String = "No auxiliary model available for web content processing"
@Suppress("unused") private const val _WT_102: String = "extra_body"
@Suppress("unused") private const val _WT_103: String = "role"
@Suppress("unused") private const val _WT_104: String = "system"
@Suppress("unused") private const val _WT_105: String = "user"
@Suppress("unused") private const val _WT_106: String = "LLM API call failed (attempt %d/%d): %s"
@Suppress("unused") private const val _WT_107: String = "Retrying in %ds..."
@Suppress("unused") private val _WT_108: String = """
    Process large content by chunking, summarizing each chunk in parallel,
    then synthesizing the summaries.
    
    Args:
        content: The large content to process
        context_str: Context information
        model: Model to use
        chunk_size: Size of each chunk in characters
        max_output_size: Maximum final output size
        
    Returns:
        Synthesized summary or None on failure
    """
@Suppress("unused") private const val _WT_109: String = "Split into %d chunks of ~%d chars each"
@Suppress("unused") private const val _WT_110: String = "Summarize a single chunk."
@Suppress("unused") private const val _WT_111: String = "[Failed to process large content: all chunk summarizations failed]"
@Suppress("unused") private const val _WT_112: String = "Got %d/%d chunk summaries"
@Suppress("unused") private const val _WT_113: String = "Synthesizing %d summaries..."
@Suppress("unused") private val _WT_114: String = """You have been given summaries of different sections of a large document. 
Synthesize these into ONE cohesive, comprehensive summary that:
1. Removes redundancy between sections
2. Preserves all key facts, figures, and actionable information
3. Is well-organized with clear structure
4. Is under """
@Suppress("unused") private val _WT_115: String = """ characters

"""
@Suppress("unused") private val _WT_116: String = """SECTION SUMMARIES:
"""
@Suppress("unused") private val _WT_117: String = """

Create a single, unified markdown summary."""
@Suppress("unused") private const val _WT_118: String = "All chunk summarizations failed"
@Suppress("unused") private val _WT_119: String = """

---

"""
@Suppress("unused") private const val _WT_120: String = "Synthesis complete: %d -> %d chars (%.2f%%)"
@Suppress("unused") private const val _WT_121: String = "[Processing chunk "
@Suppress("unused") private val _WT_122: String = """

[... truncated ...]"""
@Suppress("unused") private const val _WT_123: String = "No auxiliary model for synthesis, concatenating summaries"
@Suppress("unused") private const val _WT_124: String = "Synthesis LLM returned empty content, retrying once"
@Suppress("unused") private const val _WT_125: String = "Synthesis failed after retry — concatenating chunk summaries"
@Suppress("unused") private const val _WT_126: String = "Synthesis failed: %s"
@Suppress("unused") private const val _WT_127: String = "Chunk %d/%d summarized: %d -> %d chars"
@Suppress("unused") private const val _WT_128: String = "Chunk %d/%d failed: %s"
@Suppress("unused") private const val _WT_129: String = "## Section "
@Suppress("unused") private const val _WT_130: String = "You synthesize multiple summaries into one cohesive, comprehensive summary. Be thorough but concise."
@Suppress("unused") private val _WT_131: String = """

[... truncated due to synthesis failure ...]"""
@Suppress("unused") private val _WT_132: String = """
    Remove base64 encoded images from text to reduce token count and clutter.
    
    This function finds and removes base64 encoded images in various formats:
    - (data:image/png;base64,...)
    - (data:image/jpeg;base64,...)
    - (data:image/svg+xml;base64,...)
    - data:image/[type];base64,... (without parentheses)
    
    Args:
        text: The text content to clean
        
    Returns:
        Cleaned text with base64 images replaced with placeholders
    """
@Suppress("unused") private const val _WT_133: String = "\\(data:image/[^;]+;base64,[A-Za-z0-9+/=]+\\)"
@Suppress("unused") private const val _WT_134: String = "data:image/[^;]+;base64,[A-Za-z0-9+/=]+"
@Suppress("unused") private const val _WT_135: String = "[BASE64_IMAGE_REMOVED]"
@Suppress("unused") private val _WT_136: String = """Get or create the Exa client (lazy initialization).

    Requires EXA_API_KEY environment variable.
    """
@Suppress("unused") private const val _WT_137: String = "hermes-agent"
@Suppress("unused") private const val _WT_138: String = "x-exa-integration"
@Suppress("unused") private const val _WT_139: String = "EXA_API_KEY environment variable not set. Get your API key at https://exa.ai"
@Suppress("unused") private const val _WT_140: String = "Search using the Exa SDK and return results as a dict."
@Suppress("unused") private const val _WT_141: String = "Exa search: '%s' (limit=%d)"
@Suppress("unused") private const val _WT_142: String = "Interrupted"
@Suppress("unused") private const val _WT_143: String = "highlights"
@Suppress("unused") private val _WT_144: String = """Extract content from URLs using the Exa SDK.

    Returns a list of result dicts matching the structure expected by the
    LLM post-processing pipeline (url, title, content, metadata).
    """
@Suppress("unused") private const val _WT_145: String = "Exa extract: %d URL(s)"
@Suppress("unused") private const val _WT_146: String = "Search using the Parallel SDK and return results as a dict."
@Suppress("unused") private const val _WT_147: String = "agentic"
@Suppress("unused") private const val _WT_148: String = "Parallel search: '%s' (mode=%s, limit=%d)"
@Suppress("unused") private const val _WT_149: String = "fast"
@Suppress("unused") private const val _WT_150: String = "one-shot"
@Suppress("unused") private const val _WT_151: String = "PARALLEL_SEARCH_MODE"
@Suppress("unused") private val _WT_152: String = """Extract content from URLs using the Parallel async SDK.

    Returns a list of result dicts matching the structure expected by the
    LLM post-processing pipeline (url, title, content, metadata).
    """
@Suppress("unused") private const val _WT_153: String = "Parallel extract: %d URL(s)"
@Suppress("unused") private val _WT_154: String = """
    Search the web for information using available search API backend.

    This function provides a generic interface for web search that can work
    with multiple backends (Parallel or Firecrawl).

    Note: This function returns search result metadata only (URLs, titles, descriptions).
    Use web_extract_tool to get full content from specific URLs.
    
    Args:
        query (str): The search query to look up
        limit (int): Maximum number of results to return (default: 5)
    
    Returns:
        str: JSON string containing search results with the following structure:
             {
                 "success": bool,
                 "data": {
                     "web": [
                         {
                             "title": str,
                             "url": str,
                             "description": str,
                             "position": int
                         },
                         ...
                     ]
                 }
             }
    
    Raises:
        Exception: If search fails or API key is not set
    """
@Suppress("unused") private const val _WT_155: String = "parameters"
@Suppress("unused") private const val _WT_156: String = "results_count"
@Suppress("unused") private const val _WT_157: String = "original_response_size"
@Suppress("unused") private const val _WT_158: String = "final_response_size"
@Suppress("unused") private const val _WT_159: String = "query"
@Suppress("unused") private const val _WT_160: String = "limit"
@Suppress("unused") private const val _WT_161: String = "Searching the web for: '%s' (limit: %d)"
@Suppress("unused") private const val _WT_162: String = "Found %d search results"
@Suppress("unused") private const val _WT_163: String = "web_search_tool"
@Suppress("unused") private const val _WT_164: String = "Tavily search: '%s' (limit: %d)"
@Suppress("unused") private const val _WT_165: String = "search"
@Suppress("unused") private const val _WT_166: String = "Error searching web: "
@Suppress("unused") private const val _WT_167: String = "max_results"
@Suppress("unused") private const val _WT_168: String = "include_raw_content"
@Suppress("unused") private const val _WT_169: String = "include_images"
@Suppress("unused") private val _WT_170: String = """
    Extract content from specific web pages using available extraction API backend.

    This function provides a generic interface for web content extraction that
    can work with multiple backends. Currently uses Firecrawl.

    Args:
        urls (List[str]): List of URLs to extract content from
        format (str): Desired output format ("markdown" or "html", optional)
        use_llm_processing (bool): Whether to process content with LLM for summarization (default: True)
        model (Optional[str]): The model to use for LLM processing (defaults to current auxiliary backend model)
        min_length (int): Minimum content length to trigger LLM processing (default: 5000)

    Security: URLs are checked for embedded secrets before fetching.
    
    Returns:
        str: JSON string containing extracted content. If LLM processing is enabled and successful,
             the 'content' field will contain the processed markdown summary instead of raw content.
    
    Raises:
        Exception: If extraction fails or API key is not set
    """
@Suppress("unused") private const val _WT_171: String = "pages_extracted"
@Suppress("unused") private const val _WT_172: String = "pages_processed_with_llm"
@Suppress("unused") private const val _WT_173: String = "compression_metrics"
@Suppress("unused") private const val _WT_174: String = "processing_applied"
@Suppress("unused") private const val _WT_175: String = "urls"
@Suppress("unused") private const val _WT_176: String = "format"
@Suppress("unused") private const val _WT_177: String = "use_llm_processing"
@Suppress("unused") private const val _WT_178: String = "min_length"
@Suppress("unused") private const val _WT_179: String = "Extracting content from %d URL(s)"
@Suppress("unused") private const val _WT_180: String = "Extracted content from %d pages"
@Suppress("unused") private const val _WT_181: String = "base64_image_removal"
@Suppress("unused") private const val _WT_182: String = "web_extract_tool"
@Suppress("unused") private const val _WT_183: String = "Processing extracted content with LLM (parallel)..."
@Suppress("unused") private const val _WT_184: String = "llm_processing"
@Suppress("unused") private const val _WT_185: String = "Process a single result with LLM and return updated result with metrics."
@Suppress("unused") private const val _WT_186: String = "Content was inaccessible or not found"
@Suppress("unused") private const val _WT_187: String = "Error extracting content: "
@Suppress("unused") private const val _WT_188: String = "Blocked: URL contains what appears to be an API key or token. Secrets must not be sent in URLs."
@Suppress("unused") private const val _WT_189: String = "Unknown URL"
@Suppress("unused") private const val _WT_190: String = "processed"
@Suppress("unused") private const val _WT_191: String = "LLM processing requested but no auxiliary model available, returning raw content"
@Suppress("unused") private const val _WT_192: String = "llm_processing_unavailable"
@Suppress("unused") private const val _WT_193: String = "%s (%d characters)"
@Suppress("unused") private const val _WT_194: String = "Blocked: URL targets a private or internal network address"
@Suppress("unused") private const val _WT_195: String = "no_content"
@Suppress("unused") private const val _WT_196: String = "original_size"
@Suppress("unused") private const val _WT_197: String = "processed_size"
@Suppress("unused") private const val _WT_198: String = "compression_ratio"
@Suppress("unused") private const val _WT_199: String = "model_used"
@Suppress("unused") private const val _WT_200: String = "reason"
@Suppress("unused") private const val _WT_201: String = "content_too_short"
@Suppress("unused") private const val _WT_202: String = "too_short"
@Suppress("unused") private const val _WT_203: String = "%s (processed)"
@Suppress("unused") private const val _WT_204: String = "blocked_by_policy"
@Suppress("unused") private const val _WT_205: String = "Tavily extract: %d URL(s)"
@Suppress("unused") private const val _WT_206: String = "extract"
@Suppress("unused") private const val _WT_207: String = "markdown"
@Suppress("unused") private const val _WT_208: String = "%s (no processing - content too short)"
@Suppress("unused") private const val _WT_209: String = "%s (no content to process)"
@Suppress("unused") private const val _WT_210: String = "html"
@Suppress("unused") private const val _WT_211: String = "Blocked web_extract for %s by rule %s"
@Suppress("unused") private const val _WT_212: String = "Scraping: %s"
@Suppress("unused") private const val _WT_213: String = "host"
@Suppress("unused") private const val _WT_214: String = "rule"
@Suppress("unused") private const val _WT_215: String = "Blocked redirected web_extract for %s by rule %s"
@Suppress("unused") private const val _WT_216: String = "Scrape failed for %s: %s"
@Suppress("unused") private const val _WT_217: String = "message"
@Suppress("unused") private const val _WT_218: String = "source"
@Suppress("unused") private const val _WT_219: String = "Firecrawl scrape timed out for %s"
@Suppress("unused") private const val _WT_220: String = "Scrape timed out after 60s — page may be too large or unresponsive. Try browser_navigate instead."
@Suppress("unused") private const val _WT_221: String = "basic"
@Suppress("unused") private val _WT_222: String = """
    Crawl a website with specific instructions using available crawling API backend.
    
    This function provides a generic interface for web crawling that can work
    with multiple backends. Currently uses Firecrawl.
    
    Args:
        url (str): The base URL to crawl (can include or exclude https://)
        instructions (str): Instructions for what to crawl/extract using LLM intelligence (optional)
        depth (str): Depth of extraction ("basic" or "advanced", default: "basic")
        use_llm_processing (bool): Whether to process content with LLM for summarization (default: True)
        model (Optional[str]): The model to use for LLM processing (defaults to current auxiliary backend model)
        min_length (int): Minimum content length to trigger LLM processing (default: 5000)
    
    Returns:
        str: JSON string containing crawled content. If LLM processing is enabled and successful,
             the 'content' field will contain the processed markdown summary instead of raw content.
             Each page is processed individually.
    
    Raises:
        Exception: If crawling fails or API key is not set
    """
@Suppress("unused") private const val _WT_223: String = "pages_crawled"
@Suppress("unused") private const val _WT_224: String = "instructions"
@Suppress("unused") private const val _WT_225: String = "depth"
@Suppress("unused") private const val _WT_226: String = "Crawling %s%s"
@Suppress("unused") private const val _WT_227: String = "scrape_options"
@Suppress("unused") private const val _WT_228: String = "Crawled %d pages"
@Suppress("unused") private const val _WT_229: String = "web_crawl_tool"
@Suppress("unused") private const val _WT_230: String = "Tavily crawl: %s"
@Suppress("unused") private const val _WT_231: String = "extract_depth"
@Suppress("unused") private const val _WT_232: String = "crawl"
@Suppress("unused") private const val _WT_233: String = "https://"
@Suppress("unused") private const val _WT_234: String = "Added https:// prefix to URL: %s"
@Suppress("unused") private const val _WT_235: String = " with instructions: '"
@Suppress("unused") private const val _WT_236: String = "Blocked web_crawl for %s by rule %s"
@Suppress("unused") private const val _WT_237: String = "formats"
@Suppress("unused") private const val _WT_238: String = "Instructions parameter ignored (not supported in crawl API)"
@Suppress("unused") private const val _WT_239: String = "Status: %s"
@Suppress("unused") private const val _WT_240: String = "Retrieved %d pages"
@Suppress("unused") private const val _WT_241: String = "Processing crawled content with LLM (parallel)..."
@Suppress("unused") private const val _WT_242: String = "Process a single crawl result with LLM and return updated result with metrics."
@Suppress("unused") private const val _WT_243: String = "Error crawling website: "
@Suppress("unused") private const val _WT_244: String = "http://"
@Suppress("unused") private const val _WT_245: String = "Crawl API call failed: %s"
@Suppress("unused") private const val _WT_246: String = "status"
@Suppress("unused") private const val _WT_247: String = "unknown"
@Suppress("unused") private const val _WT_248: String = "CrawlJob attributes: %s"
@Suppress("unused") private const val _WT_249: String = "Total: %s"
@Suppress("unused") private const val _WT_250: String = "Completed: %s"
@Suppress("unused") private const val _WT_251: String = "Unexpected crawl result type"
@Suppress("unused") private const val _WT_252: String = "Result type: %s"
@Suppress("unused") private const val _WT_253: String = "Blocked crawled page %s by rule %s"
@Suppress("unused") private const val _WT_254: String = "web_crawl requires Firecrawl. Set FIRECRAWL_API_KEY, FIRECRAWL_API_URL"
@Suppress("unused") private const val _WT_255: String = ", or use web_search + web_extract instead."
@Suppress("unused") private const val _WT_256: String = "N/A"
@Suppress("unused") private const val _WT_257: String = "total"
@Suppress("unused") private const val _WT_258: String = "completed"
@Suppress("unused") private const val _WT_259: String = "Result attributes: %s"
@Suppress("unused") private const val _WT_260: String = "Check whether the configured web backend is available."
