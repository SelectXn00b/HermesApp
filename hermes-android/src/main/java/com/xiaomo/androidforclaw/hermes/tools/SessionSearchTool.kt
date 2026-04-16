package com.xiaomo.androidforclaw.hermes.tools

/**
 * Session search tool — search across conversation history.
 * Ported from session_search_tool.py
 */
object SessionSearchTool {

    data class SessionSearchResult(
        val sessions: List<SessionMatch> = emptyList(),
        val totalCount: Int = 0,
        val error: String? = null)

    data class SessionMatch(
        val sessionId: String,
        val messageCount: Int = 0,
        val preview: String = "",
        val timestamp: Long = 0L)

    /**
     * Callback interface for searching sessions.
     */
    fun interface SessionSearcher {
        fun search(query: String, limit: Int): List<SessionMatch>
    }

    /**
     * Search across conversation sessions.
     */
    fun search(
        query: String,
        limit: Int = 10,
        searcher: SessionSearcher? = null): SessionSearchResult {
        if (query.isBlank()) {
            return SessionSearchResult(error = "Search query is empty")
        }
        if (searcher == null) {
            return SessionSearchResult(error = "No session searcher configured")
        }
        return try {
            val matches = searcher.search(query, limit)
            SessionSearchResult(sessions = matches, totalCount = matches.size)
        } catch (e: Exception) {
            SessionSearchResult(error = "Search failed: ${e.message}")
        }
    }


}
