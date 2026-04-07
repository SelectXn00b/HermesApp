package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw Source Reference:
 * - src/memory-host-sdk/engine-qmd.ts
 * - src/memory-host-sdk/host/qmd-query-parser.ts
 * - src/memory-host-sdk/host/qmd-scope.ts
 * - src/memory-host-sdk/host/query-expansion.ts
 *
 * QMD (Query Memory Database) query parsing, scope derivation, and keyword extraction.
 * Android adaptation: no child process spawning; stubs for CLI invocation.
 */

import org.json.JSONArray
import org.json.JSONObject

// ---------- Query Expansion ----------

/**
 * Common English stop words for query filtering.
 * Aligned with TS isQueryStopWordToken.
 */
private val QUERY_STOP_WORDS = setOf(
    "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
    "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
    "been", "being", "have", "has", "had", "do", "does", "did", "will",
    "would", "could", "should", "may", "might", "shall", "can", "need",
    "dare", "ought", "used", "it", "its", "my", "your", "his", "her",
    "our", "their", "this", "that", "these", "those", "i", "me", "we",
    "you", "he", "she", "they", "them", "what", "which", "who", "whom",
    "where", "when", "why", "how", "not", "no", "nor", "if", "then",
    "else", "so", "just", "also", "very", "much", "about", "up", "out"
)

/**
 * Check if a token is a stop word.
 * Aligned with TS isQueryStopWordToken.
 */
fun isQueryStopWordToken(token: String): Boolean =
    token.lowercase().trim() in QUERY_STOP_WORDS

/**
 * Extract keywords from a query string.
 * Aligned with TS extractKeywords.
 */
fun extractKeywords(query: String): List<String> {
    return query.split(Regex("[\\s,;.!?]+"))
        .map { it.trim().lowercase() }
        .filter { it.length > 1 && !isQueryStopWordToken(it) }
        .distinct()
}

// ---------- QMD Query Parser ----------

/**
 * Result of a QMD query.
 * Aligned with TS QmdQueryResult.
 */
data class QmdQueryResult(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val score: Double,
    val text: String = "",
    val source: String = "qmd"
)

/**
 * Parse QMD query results from JSON output.
 * Aligned with TS parseQmdQueryJson.
 */
fun parseQmdQueryJson(json: String): List<QmdQueryResult> {
    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            QmdQueryResult(
                path = obj.optString("path", ""),
                startLine = obj.optInt("startLine", 0),
                endLine = obj.optInt("endLine", 0),
                score = obj.optDouble("score", 0.0),
                text = obj.optString("text", ""),
                source = obj.optString("source", "qmd")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// ---------- QMD Scope ----------

/**
 * Derive QMD scope channel from config.
 * Aligned with TS deriveQmdScopeChannel.
 */
fun deriveQmdScopeChannel(channel: String?): String? =
    channel?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

/**
 * Derive QMD scope chat type.
 * Aligned with TS deriveQmdScopeChatType.
 */
fun deriveQmdScopeChatType(chatType: String?): String? =
    chatType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

/**
 * Check if a QMD scope is allowed based on filter criteria.
 * Aligned with TS isQmdScopeAllowed.
 */
fun isQmdScopeAllowed(
    scope: String?,
    allowedScopes: Set<String>?
): Boolean {
    if (allowedScopes == null || allowedScopes.isEmpty()) return true
    if (scope == null) return true
    return scope in allowedScopes
}

// ---------- Session Files ----------

/**
 * Session file entry.
 * Aligned with TS SessionFileEntry.
 */
data class SessionFileEntry(
    val path: String,
    val agentId: String,
    val sessionKey: String,
    val modifiedMs: Long,
    val size: Long
)

/**
 * Build a session entry from path components.
 * Aligned with TS buildSessionEntry.
 */
fun buildSessionEntry(
    path: String,
    agentId: String,
    sessionKey: String,
    modifiedMs: Long,
    size: Long
): SessionFileEntry = SessionFileEntry(
    path = path,
    agentId = agentId,
    sessionKey = sessionKey,
    modifiedMs = modifiedMs,
    size = size
)
