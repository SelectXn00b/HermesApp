package com.xiaomo.hermes.hermes.gateway

/**
 * Session mirroring for cross-platform message delivery.
 *
 * When a message is sent to a platform (via send_message or cron delivery),
 * this module appends a "delivery-mirror" record to the target session's
 * transcript so the receiving-side agent has context about what was sent.
 *
 * Standalone -- works from CLI, cron, and gateway contexts without needing
 * the full SessionStore machinery.
 *
 * Ported from gateway/mirror.py
 */

import android.util.Log
import com.xiaomo.hermes.hermes.SessionDB
import com.xiaomo.hermes.hermes.getHermesHome
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val _TAG = "Mirror"

private val _SESSIONS_DIR: File get() = File(getHermesHome(), "sessions")
private val _SESSIONS_INDEX: File get() = File(_SESSIONS_DIR, "sessions.json")

/**
 * Append a delivery-mirror message to the target session's transcript.
 *
 * Finds the gateway session that matches the given platform + chatId,
 * then writes a mirror entry to both the JSONL transcript and SQLite DB.
 *
 * Returns true if mirrored successfully, false if no matching session or error.
 * All errors are caught -- this is never fatal.
 */
fun mirrorToSession(
    platform: String,
    chatId: String,
    messageText: String,
    sourceLabel: String = "cli",
    threadId: String? = null): Boolean {
    return try {
        val sessionId = _findSessionId(platform, chatId, threadId = threadId)
        if (sessionId == null) {
            Log.d(_TAG, "Mirror: no session found for $platform:$chatId:$threadId")
            return false
        }

        val mirrorMsg = mapOf<String, Any?>(
            "role" to "assistant",
            "content" to messageText,
            "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "mirror" to true,
            "mirror_source" to sourceLabel)

        _appendToJsonl(sessionId, mirrorMsg)
        _appendToSqlite(sessionId, mirrorMsg)

        Log.d(_TAG, "Mirror: wrote to session $sessionId (from $sourceLabel)")
        true
    } catch (e: Exception) {
        Log.d(_TAG, "Mirror failed for $platform:$chatId:$threadId: ${e.message}")
        false
    }
}

/**
 * Find the active sessionId for a platform + chatId pair.
 *
 * Scans sessions.json entries and matches where origin.chat_id == chatId
 * on the right platform.  DM session keys don't embed the chatId
 * (e.g. "agent:main:telegram:dm"), so we check the origin dict.
 */
private fun _findSessionId(platform: String, chatId: String, threadId: String? = null): String? {
    if (!_SESSIONS_INDEX.exists()) return null

    val data: JSONObject = try {
        JSONObject(_SESSIONS_INDEX.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        return null
    }

    val platformLower = platform.lowercase()
    var bestMatch: String? = null
    var bestUpdated = ""

    for (key in data.keys()) {
        val entry = data.optJSONObject(key) ?: continue
        val origin = entry.optJSONObject("origin") ?: JSONObject()
        val entryPlatform = (origin.optString("platform").ifEmpty { entry.optString("platform", "") }).lowercase()

        if (entryPlatform != platformLower) continue

        val originChatId = origin.optString("chat_id", "")
        if (originChatId == chatId) {
            val originThreadId = origin.optString("thread_id", "")
            if (threadId != null && originThreadId != threadId) continue
            val updated = entry.optString("updated_at", "")
            if (updated > bestUpdated) {
                bestUpdated = updated
                bestMatch = entry.optString("session_id")
            }
        }
    }

    return bestMatch
}

/** Append a message to the JSONL transcript file. */
private fun _appendToJsonl(sessionId: String, message: Map<String, Any?>) {
    val transcriptPath = File(_SESSIONS_DIR, "$sessionId.jsonl")
    try {
        transcriptPath.parentFile?.mkdirs()
        val json = JSONObject(message).toString()
        transcriptPath.appendText(json + "\n", Charsets.UTF_8)
    } catch (e: Exception) {
        Log.d(_TAG, "Mirror JSONL write failed: %s".format(e.message))
    }
}

/** Append a message to the SQLite session database. */
private fun _appendToSqlite(sessionId: String, message: Map<String, Any?>) {
    var db: SessionDB? = null
    try {
        db = SessionDB()
        db.appendMessage(
            sessionId = sessionId,
            role = (message["role"] as? String) ?: "assistant",
            content = message["content"] as? String)
    } catch (e: Exception) {
        Log.d(_TAG, "Mirror SQLite write failed: %s".format(e.message))
    } finally {
        db?.close()
    }
}
