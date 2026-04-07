package com.xiaomo.androidforclaw.websearch

/**
 * OpenClaw module: web-search
 * Source: OpenClaw/src/web-search/runtime.ts (types portion, ~217 LOC)
 *
 * Request / result types for the web-search skill, provider entries,
 * and individual search result entries.
 */

// ---------------------------------------------------------------------------
// Request — aligned with OpenClaw WebSearchRequest
// ---------------------------------------------------------------------------

data class WebSearchRequest(
    val query: String,
    val maxResults: Int = 10,
    val provider: String? = null,
    val language: String? = null,
    val region: String? = null,
    val timeoutMs: Long = 15_000
)

// ---------------------------------------------------------------------------
// Result — aligned with OpenClaw WebSearchResult / WebSearchResultEntry
// ---------------------------------------------------------------------------

data class WebSearchResultEntry(
    val title: String,
    val url: String,
    val snippet: String? = null
)

data class WebSearchResult(
    val query: String,
    val results: List<WebSearchResultEntry>,
    val providerId: String
)

// Legacy alias
typealias WebSearchResultItem = WebSearchResultEntry

// ---------------------------------------------------------------------------
// Provider resolution types
// ---------------------------------------------------------------------------

data class WebSearchProviderEntry(
    val id: String,
    val label: String?,
    val configured: Boolean
)

data class WebSearchDefinitionResult(
    val providerId: String,
    val enabled: Boolean
)
