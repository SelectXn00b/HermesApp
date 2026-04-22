package com.xiaomo.hermes.hermes.gateway

/**
 * Mirror bridge — reflects messages to an arbitrary "target" session.
 *
 * A user can set up mirroring so that every message they send to a
 * primary channel (e.g. Telegram) is also delivered to a secondary
 * session running under a different identity or on a different platform.
 *
 * Ported from gateway/mirror.py
 */

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Mirror rule — maps a source session to a target URL + key. */
data class MirrorRule(
    val targetUrl: String,
    val targetKey: String,
    val label: String = "")

/**
 * In-memory mirror bridge.
 *
 * Each rule maps a * (the session key of the incoming message)
 * to a * and * where the message should also be
 * delivered.  The actual delivery is fire-and-forget — a failure to mirror
 * never blocks the primary session.
 */
class MirrorBridge(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()) {
    companion object {
        private const val _TAG = "MirrorBridge"
    }

    /** source_key → MirrorRule */
    private val _rules: ConcurrentHashMap<String, MirrorRule> = ConcurrentHashMap()

    /** Register a mirror rule. */
    fun addRule(sourceKey: String, rule: MirrorRule) {
        _rules[sourceKey] = rule
    }

    /** Remove the mirror rule for *. */
    fun removeRule(sourceKey: String) {
        _rules.remove(sourceKey)
    }

    /** Return the rule for * or null. */
    fun getRule(sourceKey: String): MirrorRule? = _rules[sourceKey]

    /** True when at least one rule is active. */
    val hasRules: Boolean get() = _rules.isNotEmpty()

    /**
     * Mirror the given message if a rule exists for *.
     *
     * @param sourceKey  The session key of the original message.
     * @param text       The message text to mirror.
     * @param userId     Optional user-id for multi-tenant targets.
     */
    suspend fun mirror(sourceKey: String, text: String, userId: String? = null) {
        val rule = _rules[sourceKey] ?: return
        try {
            _deliver(rule, text, userId)
        } catch (e: Exception) {
            Log.w(_TAG, "Mirror delivery failed for $sourceKey: ${e.message}")
        }
    }

    private suspend fun _deliver(rule: MirrorRule, text: String, userId: String?) =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("text", text)
                put("target_key", rule.targetKey)
                userId?.let { put("user_id", it) }
            }
            val body = payload.toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(rule.targetUrl)
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(_TAG, "Mirror HTTP ${resp.code}: ${resp.body?.string()}")
                }
            }
        }


}


/**
 * Standalone mirror functions (ported from gateway/mirror.py).
 * These work without the full MirrorBridge class.
 */

/** Find the session ID for a platform + chatId pair. */
fun findSessionId(sessionsIndex: java.io.File, platform: String, chatId: String, threadId: String? = null): String? {
    if (!sessionsIndex.exists()) return null
    try {
        val data = org.json.JSONObject(sessionsIndex.readText())
        val platformLower = platform.lowercase()
        var bestMatch: String? = null
        var bestUpdated = ""
        for (key in data.keys()) {
            val entry = data.optJSONObject(key) ?: continue
            val origin = entry.optJSONObject("origin") ?: continue
            val entryPlatform = (origin.optString("platform") ?: entry.optString("platform", "")).lowercase()
            if (entryPlatform != platformLower) continue
            val originChatId = origin.optString("chat_id", "")
            if (originChatId != chatId) continue
            val originThreadId = origin.optString("thread_id", "")
            if (threadId != null && originThreadId != threadId) continue
            val updated = entry.optString("updated_at", "")
            if (updated > bestUpdated) {
                bestUpdated = updated
                bestMatch = entry.optString("session_id")
            }
        }
        return bestMatch
    } catch (_unused: Exception) { return null }
}

/** Append a message to a JSONL transcript file. */
fun appendToJsonl(transcriptPath: java.io.File, message: Map<String, Any?>) {
    try {
        val gson = com.google.gson.Gson()
        transcriptPath.parentFile?.mkdirs()
        transcriptPath.appendText(gson.toJson(message) + "\n")
    } catch (_unused: Exception) {}
}

/** Append a message to a SQLite session database. */
fun appendToSqlite(sessionId: String, message: Map<String, Any?>) {
    // Android: SQLite operations handled by SessionStore
    // No-op on Android platform
}
