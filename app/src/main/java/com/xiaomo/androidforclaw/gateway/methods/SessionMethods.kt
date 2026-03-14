/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
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
    @Suppress("UNUSED_PARAMETER")
    fun sessionsList(params: Any?): SessionListResult {
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
    @Suppress("UNCHECKED_CAST")
    fun sessionsPreview(params: Any?): SessionPreviewResult {
        val paramsMap = params as? Map<String, Any?>
            ?: throw IllegalArgumentException("params must be an object")
        val key = paramsMap["key"] as? String
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

        // Update metadata
        val metadata = paramsMap["metadata"] as? Map<String, Any?>
        if (metadata != null) {
            session.metadata.putAll(metadata)
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
