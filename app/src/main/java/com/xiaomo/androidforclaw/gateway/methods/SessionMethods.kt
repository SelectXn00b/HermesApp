/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/session-utils.ts
 */
package com.xiaomo.androidforclaw.gateway.methods

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.gateway.protocol.*
import com.xiaomo.androidforclaw.sessions.looksLikeSessionId
import com.xiaomo.androidforclaw.sessions.ParsedSessionLabel
import com.xiaomo.androidforclaw.sessions.parseSessionLabel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Session RPC methods implementation
 */
class SessionMethods(
    private val sessionManager: SessionManager
) {
    /**
     * sessions.list() - List all sessions
     */
    @Suppress("UNCHECKED_CAST")
    fun sessionsList(params: Any?): SessionListResult {
        val p = params as? Map<String, Any?> ?: emptyMap()
        val limit = (p["limit"] as? Number)?.toInt() ?: Int.MAX_VALUE

        val keys = sessionManager.getAllKeys()
        val sessions = keys.map { key ->
            val session = sessionManager.get(key)
            val rawDisplayName = session?.metadata?.get("displayName") as? String
            // Validate display name via sessions.SessionLabel
            val displayName = if (rawDisplayName != null) {
                when (val parsed = parseSessionLabel(rawDisplayName)) {
                    is ParsedSessionLabel.Ok -> parsed.label
                    is ParsedSessionLabel.Error -> rawDisplayName // keep as-is on parse failure
                }
            } else null
            SessionInfo(
                key = key,
                messageCount = session?.messageCount() ?: 0,
                createdAt = parseIso8601(session?.createdAt),
                updatedAt = parseIso8601(session?.updatedAt),
                displayName = displayName
            )
        }.sortedByDescending { it.updatedAt }.take(limit)
        return SessionListResult(sessions = sessions)
    }

    private fun parseIso8601(isoString: String?): Long {
        if (isoString.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(isoString)?.time
                ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * sessions.preview() - Preview a session's messages
     */
    @Suppress("UNCHECKED_CAST")
    fun sessionsPreview(params: Any?): SessionPreviewResult {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object")
        val key = paramsMap["key"] as? String
            ?: throw IllegalArgumentException("key required")

        // Validate session key format using sessions.SessionId if it looks UUID-like
        if (looksLikeSessionId(key)) {
            // Key is a raw session ID, not a session key — still allowed, just noted
        }

        val session = sessionManager.get(key)
            ?: throw IllegalArgumentException("Session not found: $key")

        val messages: List<SessionMessage> = session.messages.map { msg: LegacyMessage ->
            SessionMessage(
                role = msg.role,
                content = msg.content?.toString() ?: "",
                timestamp = System.currentTimeMillis()
            )
        }

        return SessionPreviewResult(key = key, messages = messages)
    }

    /**
     * sessions.reset() - Reset a session
     */
    @Suppress("UNCHECKED_CAST")
    fun sessionsReset(params: Any?): Map<String, Boolean> {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object")
        val key = paramsMap["key"] as? String
            ?: throw IllegalArgumentException("key required")

        sessionManager.clear(key)
        return mapOf("success" to true)
    }

    /**
     * sessions.delete() - Delete a session
     */
    @Suppress("UNCHECKED_CAST")
    fun sessionsDelete(params: Any?): Map<String, Boolean> {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object")
        val key = paramsMap["key"] as? String
            ?: throw IllegalArgumentException("key required")

        sessionManager.clear(key)
        return mapOf("success" to true)
    }

    /**
     * sessions.patch() - Patch a session
     *
     * Supported operations:
     * - metadata: Update session metadata
     * - messages: Manipulate message list (add, remove, update)
     */
    @Suppress("UNCHECKED_CAST")
    fun sessionsPatch(params: Any?): Map<String, Boolean> {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object")
        val key = paramsMap["key"] as? String
            ?: throw IllegalArgumentException("key required")

        val session = sessionManager.get(key)
            ?: throw IllegalArgumentException("Session not found: $key")

        // Update metadata (validate label via sessions.SessionLabel)
        val metadata = paramsMap["metadata"] as? Map<String, Any?>
        if (metadata != null) {
            val patchedMetadata = metadata.toMutableMap()
            // Validate displayName/label if provided
            val rawLabel = patchedMetadata["displayName"]
            if (rawLabel != null) {
                when (val parsed = parseSessionLabel(rawLabel)) {
                    is ParsedSessionLabel.Ok -> patchedMetadata["displayName"] = parsed.label
                    is ParsedSessionLabel.Error -> throw IllegalArgumentException(parsed.error)
                }
            }
            session.metadata.putAll(patchedMetadata)
        }

        // Manipulate messages
        val messagesOp = paramsMap["messages"] as? Map<String, Any?>
        if (messagesOp != null) {
            val operation = messagesOp["op"] as? String

            when (operation) {
                "add" -> {
                    // Add message
                    val role = messagesOp["role"] as? String ?: "user"
                    val content = messagesOp["content"] as? String ?: ""
                    session.addMessage(LegacyMessage(role = role, content = content))
                }
                "remove" -> {
                    // Remove message at specified index
                    val index = (messagesOp["index"] as? Number)?.toInt()
                    if (index != null && index >= 0 && index < session.messages.size) {
                        session.messages.removeAt(index)
                    }
                }
                "clear" -> {
                    // Clear all messages
                    session.clearMessages()
                }
                "truncate" -> {
                    // Keep last N messages
                    val count = (messagesOp["count"] as? Number)?.toInt() ?: 10
                    if (session.messages.size > count) {
                        val keep = session.messages.takeLast(count)
                        session.messages.clear()
                        session.messages.addAll(keep)
                    }
                }
            }
        }

        // Save session
        sessionManager.save(session)

        return mapOf("success" to true)
    }
}
