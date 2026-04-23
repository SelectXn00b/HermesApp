package com.xiaomo.hermes.hermes.tools

/**
 * Session Search Tool — long-term conversation recall.
 *
 * Searches past session transcripts in SQLite via FTS5, then summarizes the
 * top matching sessions using a cheap/fast model (same pattern as
 * web_extract). Returns focused summaries of past conversations rather than
 * raw transcripts, keeping the main model's context window clean.
 *
 * Ported from tools/session_search_tool.py
 */

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val _TAG = "session_search"

const val MAX_SESSION_CHARS = 100_000
const val MAX_SUMMARY_TOKENS = 10000

/** Read auxiliary.session_search.max_concurrency with sane bounds. */
private fun _getSessionSearchMaxConcurrency(default: Int = 3): Int {
    // TODO: port hermes_cli.config.load_config
    return default
}

/**
 * Convert a Unix timestamp (float/int) or ISO string to a human-readable date.
 *
 * Returns "unknown" for null, str(ts) if conversion fails.
 */
private fun _formatTimestamp(ts: Any?): String {
    if (ts == null) return "unknown"
    val fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH)
    try {
        if (ts is Number) {
            val inst = Instant.ofEpochMilli((ts.toDouble() * 1000).toLong())
            return inst.atZone(ZoneId.systemDefault()).format(fmt)
        }
        if (ts is String) {
            val trimmed = ts.trim()
            if (trimmed.replace(".", "").replace("-", "").all { it.isDigit() }) {
                val inst = Instant.ofEpochMilli((trimmed.toDouble() * 1000).toLong())
                return inst.atZone(ZoneId.systemDefault()).format(fmt)
            }
            return trimmed
        }
    } catch (e: Exception) {
        Log.d(_TAG, "Failed to format timestamp $ts: ${e.message}")
    }
    return ts.toString()
}

/** Format session messages into a readable transcript for summarization. */
@Suppress("UNCHECKED_CAST")
private fun _formatConversation(messages: List<Map<String, Any?>>): String {
    val parts = mutableListOf<String>()
    for (msg in messages) {
        val role = (msg["role"] as? String ?: "unknown").uppercase()
        var content = (msg["content"] as? String) ?: ""
        val toolName = msg["tool_name"] as? String

        if (role == "TOOL" && toolName != null) {
            if (content.length > 500) {
                content = content.substring(0, 250) + "\n...[truncated]...\n" + content.substring(content.length - 250)
            }
            parts.add("[TOOL:$toolName]: $content")
        } else if (role == "ASSISTANT") {
            val toolCalls = msg["tool_calls"] as? List<Any?>
            if (toolCalls != null) {
                val tcNames = mutableListOf<String>()
                for (tc in toolCalls) {
                    if (tc is Map<*, *>) {
                        val direct = tc["name"] as? String
                        val name = direct ?: run {
                            val fn = tc["function"] as? Map<String, Any?>
                            (fn?.get("name") as? String) ?: "?"
                        }
                        tcNames.add(name)
                    }
                }
                if (tcNames.isNotEmpty()) {
                    parts.add("[ASSISTANT]: [Called: ${tcNames.joinToString(", ")}]")
                }
                if (content.isNotEmpty()) {
                    parts.add("[ASSISTANT]: $content")
                }
            } else {
                parts.add("[ASSISTANT]: $content")
            }
        } else {
            parts.add("[$role]: $content")
        }
    }
    return parts.joinToString("\n\n")
}

/**
 * Truncate a conversation transcript to [maxChars], choosing a window that
 * maximises coverage of positions where the [query] actually appears.
 */
fun _truncateAroundMatches(
    fullText: String,
    query: String,
    maxChars: Int = MAX_SESSION_CHARS): String {
    if (fullText.length <= maxChars) return fullText

    val textLower = fullText.lowercase()
    val queryLower = query.lowercase().trim()
    val matchPositions = mutableListOf<Int>()

    // --- 1. Full-phrase search ----------------------------------------------
    val phrasePat = Regex(Regex.escape(queryLower))
    matchPositions.addAll(phrasePat.findAll(textLower).map { it.range.first })

    // --- 2. Proximity co-occurrence of all terms (within 200 chars) ---------
    if (matchPositions.isEmpty()) {
        val terms = queryLower.split(" ").filter { it.isNotEmpty() }
        if (terms.size > 1) {
            val termPositions = mutableMapOf<String, List<Int>>()
            for (t in terms) {
                termPositions[t] = Regex(Regex.escape(t)).findAll(textLower).map { it.range.first }.toList()
            }
            val rarest = terms.minByOrNull { termPositions[it]?.size ?: 0 } ?: terms.first()
            for (pos in termPositions[rarest] ?: emptyList()) {
                val allNearby = terms.filter { it != rarest }.all { t ->
                    (termPositions[t] ?: emptyList()).any { p -> kotlin.math.abs(p - pos) < 200 }
                }
                if (allNearby) matchPositions.add(pos)
            }
        }
    }

    // --- 3. Individual term positions (last resort) -------------------------
    if (matchPositions.isEmpty()) {
        val terms = queryLower.split(" ").filter { it.isNotEmpty() }
        for (t in terms) {
            for (m in Regex(Regex.escape(t)).findAll(textLower)) {
                matchPositions.add(m.range.first)
            }
        }
    }

    if (matchPositions.isEmpty()) {
        val truncated = fullText.substring(0, maxChars)
        val suffix = if (maxChars < fullText.length) "\n\n...[later conversation truncated]..." else ""
        return truncated + suffix
    }

    matchPositions.sort()

    var bestStart = 0
    var bestCount = 0
    for (candidate in matchPositions) {
        var ws = maxOf(0, candidate - maxChars / 4)
        var we = ws + maxChars
        if (we > fullText.length) {
            ws = maxOf(0, fullText.length - maxChars)
            we = fullText.length
        }
        val count = matchPositions.count { it in ws until we }
        if (count > bestCount) {
            bestCount = count
            bestStart = ws
        }
    }

    val start = bestStart
    val end = minOf(fullText.length, start + maxChars)
    val truncated = fullText.substring(start, end)
    val prefix = if (start > 0) "...[earlier conversation truncated]...\n\n" else ""
    val suffix = if (end < fullText.length) "\n\n...[later conversation truncated]..." else ""
    return prefix + truncated + suffix
}

/** Summarize a single session conversation focused on the search query. */
private suspend fun _summarizeSession(
    conversationText: String,
    query: String,
    sessionMeta: Map<String, Any?>): String? {
    @Suppress("UNUSED_VARIABLE")
    val systemPrompt = (
        "You are reviewing a past conversation transcript to help recall what happened. " +
        "Summarize the conversation with a focus on the search topic. Include:\n" +
        "1. What the user asked about or wanted to accomplish\n" +
        "2. What actions were taken and what the outcomes were\n" +
        "3. Key decisions, solutions found, or conclusions reached\n" +
        "4. Any specific commands, files, URLs, or technical details that were important\n" +
        "5. Anything left unresolved or notable\n\n" +
        "Be thorough but concise. Preserve specific details (commands, paths, error messages) " +
        "that would be useful to recall. Write in past tense as a factual recap.")

    val source = (sessionMeta["source"] as? String) ?: "unknown"
    val started = _formatTimestamp(sessionMeta["started_at"])

    @Suppress("UNUSED_VARIABLE")
    val userPrompt = (
        "Search topic: $query\n" +
        "Session source: $source\n" +
        "Session date: $started\n\n" +
        "CONVERSATION TRANSCRIPT:$conversationText\n\n" +
        "Summarize this conversation with focus on: $query")

    val maxRetries = 3
    for (attempt in 0 until maxRetries) {
        try {
            // TODO: port agent.auxiliary_client.async_call_llm +
            // extract_content_or_reasoning.
            val content: String? = null
            if (!content.isNullOrEmpty()) return content
            Log.w(_TAG, "Session search LLM returned empty content (attempt ${attempt + 1}/$maxRetries)")
            if (attempt < maxRetries - 1) {
                delay(1000L * (attempt + 1))
                continue
            }
            return content
        } catch (_: NotImplementedError) {
            Log.w(_TAG, "No auxiliary model available for session summarization")
            return null
        } catch (e: Exception) {
            if (attempt < maxRetries - 1) {
                delay(1000L * (attempt + 1))
            } else {
                Log.w(_TAG, "Session summarization failed after $maxRetries attempts: ${e.message}")
                return null
            }
        }
    }
    return null
}

private val _HIDDEN_SESSION_SOURCES = listOf("tool")

/** Return metadata for the most recent sessions (no LLM calls). */
private fun _listRecentSessions(db: Any?, limit: Int, currentSessionId: String?): String {
    // TODO: port SessionDb.list_sessions_rich once the Kotlin DB layer exists.
    return JSONObject(mapOf(
        "success" to true,
        "mode" to "recent",
        "results" to JSONArray(),
        "count" to 0,
        "message" to "Showing 0 most recent sessions. Use a keyword query to search specific topics."))
        .toString()
}

/**
 * Search past sessions and return focused summaries of matching conversations.
 *
 * Uses FTS5 to find matches, then summarizes the top sessions with Gemini
 * Flash. The current session is excluded from results since the agent already
 * has that context.
 */
fun sessionSearch(
    query: String,
    roleFilter: String? = null,
    limit: Any? = 3,
    db: Any? = null,
    currentSessionId: String? = null): String {
    if (db == null) {
        return toolError("Session database not available.", mapOf("success" to false))
    }

    var safeLimit = if (limit is Int) limit else {
        try {
            (limit as? Number)?.toInt() ?: limit.toString().toInt()
        } catch (_: Exception) {
            3
        }
    }
    safeLimit = maxOf(1, minOf(safeLimit, 5))

    val q = (query).trim()
    if (q.isEmpty()) {
        return _listRecentSessions(db, safeLimit, currentSessionId)
    }

    try {
        @Suppress("UNUSED_VARIABLE")
        val roleList: List<String>? = if (!roleFilter.isNullOrBlank()) {
            roleFilter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else null

        // TODO: port db.search_messages FTS5 call
        val rawResults: List<Map<String, Any?>> = emptyList()

        if (rawResults.isEmpty()) {
            return JSONObject(mapOf(
                "success" to true,
                "query" to q,
                "results" to JSONArray(),
                "count" to 0,
                "message" to "No matching sessions found.")).toString()
        }

        fun _resolveToParent(sessionId: String): String {
            val visited = mutableSetOf<String>()
            var sid: String? = sessionId
            while (sid != null && sid !in visited) {
                visited.add(sid)
                try {
                    // TODO: port db.get_session
                    val session: Map<String, Any?>? = null
                    val parent = session?.get("parent_session_id") as? String
                    if (!parent.isNullOrEmpty()) {
                        sid = parent
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    Log.d(_TAG, "Error resolving parent for session $sid: ${e.message}")
                    break
                }
            }
            return sid ?: sessionId
        }

        val currentLineageRoot = if (!currentSessionId.isNullOrEmpty()) {
            _resolveToParent(currentSessionId)
        } else null

        val seenSessions = linkedMapOf<String, MutableMap<String, Any?>>()
        for (result in rawResults) {
            val rawSid = (result["session_id"] as? String) ?: continue
            val resolvedSid = _resolveToParent(rawSid)
            if (currentLineageRoot != null && resolvedSid == currentLineageRoot) continue
            if (currentSessionId != null && rawSid == currentSessionId) continue
            if (resolvedSid !in seenSessions) {
                val copy = result.toMutableMap()
                copy["session_id"] = resolvedSid
                seenSessions[resolvedSid] = copy
            }
            if (seenSessions.size >= safeLimit) break
        }

        data class SessionTask(
            val sessionId: String,
            val matchInfo: Map<String, Any?>,
            val conversationText: String,
            val sessionMeta: Map<String, Any?>)

        val tasks = mutableListOf<SessionTask>()
        for ((sessionId, matchInfo) in seenSessions) {
            try {
                // TODO: port db.get_messages_as_conversation + db.get_session
                val messages: List<Map<String, Any?>> = emptyList()
                if (messages.isEmpty()) continue
                val sessionMeta: Map<String, Any?> = emptyMap()
                var conversationText = _formatConversation(messages)
                conversationText = _truncateAroundMatches(conversationText, q)
                tasks.add(SessionTask(sessionId, matchInfo, conversationText, sessionMeta))
            } catch (e: Exception) {
                Log.w(_TAG, "Failed to prepare session $sessionId: ${e.message}")
            }
        }

        val results: List<Any?> = runBlocking {
            coroutineScope {
                val maxConcurrency = minOf(
                    _getSessionSearchMaxConcurrency(),
                    maxOf(1, tasks.size))
                val semaphore = Semaphore(maxConcurrency)
                tasks.map { task ->
                    async(Dispatchers.Default) {
                        semaphore.withPermit {
                            try {
                                _summarizeSession(task.conversationText, q, task.sessionMeta)
                            } catch (e: Exception) {
                                e
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        val summaries = JSONArray()
        for ((task, result) in tasks.zip(results)) {
            val failed = result is Exception
            if (failed) {
                Log.w(_TAG, "Failed to summarize session ${task.sessionId}: ${(result as Exception).message}")
            }

            val entry = mutableMapOf<String, Any?>(
                "session_id" to task.sessionId,
                "when" to _formatTimestamp(task.matchInfo["session_started"]),
                "source" to (task.matchInfo["source"] ?: "unknown"),
                "model" to task.matchInfo["model"])

            val summary = if (!failed) result as? String else null
            if (!summary.isNullOrEmpty()) {
                entry["summary"] = summary
            } else {
                val preview = if (task.conversationText.isNotEmpty()) {
                    task.conversationText.take(500) + "\n…[truncated]"
                } else "No preview available."
                entry["summary"] = "[Raw preview — summarization unavailable]\n$preview"
            }
            summaries.put(JSONObject(entry))
        }

        return JSONObject(mapOf(
            "success" to true,
            "query" to q,
            "results" to summaries,
            "count" to summaries.length(),
            "sessions_searched" to seenSessions.size)).toString()
    } catch (e: Exception) {
        Log.e(_TAG, "Session search failed: ${e.message}")
        return toolError("Search failed: ${e.message}", mapOf("success" to false))
    }
}

/** Requires SQLite state database and an auxiliary text model. */
fun checkSessionSearchRequirements(): Boolean {
    return try {
        com.xiaomo.hermes.hermes.SessionDB.DEFAULT_DB_PATH.parentFile?.exists() == true
    } catch (_: Exception) {
        false
    }
}

val SESSION_SEARCH_SCHEMA: Map<String, Any?> = mapOf(
    "name" to "session_search",
    "description" to (
        "Search your long-term memory of past conversations, or browse recent sessions. This is your recall -- " +
        "every past session is searchable, and this tool summarizes what happened.\n\n" +
        "TWO MODES:\n" +
        "1. Recent sessions (no query): Call with no arguments to see what was worked on recently. " +
        "Returns titles, previews, and timestamps. Zero LLM cost, instant. " +
        "Start here when the user asks what were we working on or what did we do recently.\n" +
        "2. Keyword search (with query): Search for specific topics across all past sessions. " +
        "Returns LLM-generated summaries of matching sessions.\n\n" +
        "USE THIS PROACTIVELY when:\n" +
        "- The user says 'we did this before', 'remember when', 'last time', 'as I mentioned'\n" +
        "- The user asks about a topic you worked on before but don't have in current context\n" +
        "- The user references a project, person, or concept that seems familiar but isn't in memory\n" +
        "- You want to check if you've solved a similar problem before\n" +
        "- The user asks 'what did we do about X?' or 'how did we fix Y?'\n\n" +
        "Don't hesitate to search when it is actually cross-session -- it's fast and cheap. " +
        "Better to search and confirm than to guess or ask the user to repeat themselves.\n\n" +
        "Search syntax: keywords joined with OR for broad recall (elevenlabs OR baseten OR funding), " +
        "phrases for exact match (\"docker networking\"), boolean (python NOT java), prefix (deploy*). " +
        "IMPORTANT: Use OR between keywords for best results — FTS5 defaults to AND which misses " +
        "sessions that only mention some terms. If a broad OR query returns nothing, try individual " +
        "keyword searches in parallel. Returns summaries of the top matching sessions."),
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "Search query — keywords, phrases, or boolean expressions to find in past sessions. Omit this parameter entirely to browse recent sessions instead (returns titles, previews, timestamps with no LLM cost)."),
            "role_filter" to mapOf(
                "type" to "string",
                "description" to "Optional: only search messages from specific roles (comma-separated). E.g. 'user,assistant' to skip tool outputs."),
            "limit" to mapOf(
                "type" to "integer",
                "description" to "Max sessions to summarize (default: 3, max: 5).",
                "default" to 3)),
        "required" to emptyList<String>()))

// Module-load side-effect: register with the tool registry.
// TODO: registry.register once handler contract is finalized.
