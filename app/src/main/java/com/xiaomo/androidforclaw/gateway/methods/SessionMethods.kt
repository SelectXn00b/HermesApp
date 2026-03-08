package com.xiaomo.androidforclaw.gateway.methods

import com.xiaomo.androidforclaw.agent.session.SessionManager
import com.xiaomo.androidforclaw.providers.LegacyMessage
import com.xiaomo.androidforclaw.gateway.protocol.*

/**
 * Session RPC methods implementation
 */
class SessionMethods(
    private val sessionManager: SessionManager
) {
    /**
     * sessions.list() - List all sessions
     */
    fun sessionsList(params: Map<String, Any?>?): SessionListResult {
        val keys = sessionManager.getAllKeys()
        val sessions = keys.map { key ->
            val session = sessionManager.get(key)
            SessionInfo(
                key = key,
                messageCount = session?.messageCount() ?: 0,
                createdAt = session?.createdAt ?: "",
                updatedAt = session?.updatedAt ?: ""
            )
        }
        return SessionListResult(sessions = sessions)
    }

    /**
     * sessions.preview() - Preview a session's messages
     */
    fun sessionsPreview(params: Map<String, Any?>?): SessionPreviewResult {
        val key = params?.get("key") as? String
            ?: throw IllegalArgumentException("key required")

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
    fun sessionsReset(params: Map<String, Any?>?): Map<String, Boolean> {
        val key = params?.get("key") as? String
            ?: throw IllegalArgumentException("key required")

        sessionManager.clear(key)
        return mapOf("success" to true)
    }

    /**
     * sessions.delete() - Delete a session
     */
    fun sessionsDelete(params: Map<String, Any?>?): Map<String, Boolean> {
        val key = params?.get("key") as? String
            ?: throw IllegalArgumentException("key required")

        sessionManager.clear(key)
        return mapOf("success" to true)
    }

    /**
     * sessions.patch() - Patch a session
     */
    fun sessionsPatch(params: Map<String, Any?>?): Map<String, Boolean> {
        // TODO: Implement patch logic
        return mapOf("success" to true)
    }
}
