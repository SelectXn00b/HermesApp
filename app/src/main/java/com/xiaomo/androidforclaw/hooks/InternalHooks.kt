package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/hooks/internal-hooks.ts
 *   (InternalHookEvent, registerInternalHook, triggerInternalHook, clearInternalHooks)
 *
 * AndroidForClaw adaptation: event-driven hook system for agent lifecycle events.
 */

import com.xiaomo.androidforclaw.logging.Log
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal hook event types.
 * Aligned with OpenClaw InternalHookEventType.
 */
enum class InternalHookEventType {
    COMMAND,
    SESSION,
    AGENT,
    GATEWAY,
    MESSAGE
}

/**
 * Internal hook event.
 * Aligned with OpenClaw InternalHookEvent.
 */
data class InternalHookEvent(
    val type: InternalHookEventType,
    val action: String,
    val sessionKey: String? = null,
    val context: Map<String, Any?> = emptyMap(),
    val timestamp: Date = Date(),
    val messages: List<String> = emptyList()
)

/**
 * Hook handler function type.
 * Aligned with OpenClaw InternalHookHandler.
 */
typealias InternalHookHandler = suspend (InternalHookEvent) -> Unit

/**
 * Agent bootstrap hook context.
 * Aligned with OpenClaw AgentBootstrapHookContext.
 */
data class AgentBootstrapHookContext(
    val workspaceDir: String,
    val bootstrapFiles: List<String>,
    val sessionKey: String? = null,
    val sessionId: String? = null,
    val agentId: String? = null
)

/**
 * Gateway startup hook context.
 * Aligned with OpenClaw GatewayStartupHookContext.
 */
data class GatewayStartupHookContext(
    val port: Int? = null,
    val bindAddress: String? = null,
    val workspaceDir: String? = null
)

/**
 * Message received hook context.
 * Aligned with OpenClaw MessageReceivedHookContext.
 */
data class MessageReceivedHookContext(
    val from: String?,
    val content: String,
    val channelId: String,
    val timestamp: Long? = null,
    val accountId: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val metadata: Map<String, Any?>? = null
)

/**
 * Message sent hook context.
 * Aligned with OpenClaw MessageSentHookContext.
 */
data class MessageSentHookContext(
    val to: String?,
    val content: String,
    val success: Boolean,
    val error: String? = null,
    val channelId: String,
    val accountId: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val isGroup: Boolean? = null,
    val groupId: String? = null
)

/**
 * Message transcribed hook context.
 * Aligned with OpenClaw MessageTranscribedHookContext.
 */
data class MessageTranscribedHookContext(
    val transcript: String,
    val channelId: String,
    val from: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null
)

/**
 * Message preprocessed hook context.
 * Aligned with OpenClaw MessagePreprocessedHookContext.
 */
data class MessagePreprocessedHookContext(
    val channelId: String,
    val transcript: String? = null,
    val isGroup: Boolean? = null,
    val groupId: String? = null,
    val from: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null
)

/**
 * InternalHooks — Event-driven hook system.
 * Aligned with OpenClaw internal-hooks.ts.
 */
object InternalHooks {

    private const val TAG = "InternalHooks"

    private val handlers = ConcurrentHashMap<String, MutableList<InternalHookHandler>>()

    fun register(eventKey: String, handler: InternalHookHandler) {
        handlers.getOrPut(eventKey) { mutableListOf() }.add(handler)
        Log.d(TAG, "Registered hook handler for: $eventKey")
    }

    fun unregister(eventKey: String, handler: InternalHookHandler) {
        handlers[eventKey]?.remove(handler)
        if (handlers[eventKey]?.isEmpty() == true) handlers.remove(eventKey)
    }

    fun clear() {
        handlers.clear()
        Log.d(TAG, "All hooks cleared")
    }

    fun getRegisteredEventKeys(): Set<String> = handlers.keys.toSet()

    /**
     * Trigger a hook event.
     * Fires handlers for both the type key and the type:action key.
     * Aligned with OpenClaw triggerInternalHook.
     */
    suspend fun trigger(event: InternalHookEvent) {
        val typeKey = event.type.name.lowercase()
        val actionKey = "$typeKey:${event.action}"

        val typeHandlers = handlers[typeKey] ?: emptyList()
        val actionHandlers = handlers[actionKey] ?: emptyList()

        for (handler in typeHandlers + actionHandlers) {
            try {
                handler(event)
            } catch (e: Exception) {
                Log.w(TAG, "Hook handler error for $actionKey: ${e.message}")
            }
        }
    }

    fun createEvent(
        type: InternalHookEventType,
        action: String,
        sessionKey: String? = null,
        context: Map<String, Any?> = emptyMap()
    ): InternalHookEvent {
        return InternalHookEvent(type = type, action = action, sessionKey = sessionKey, context = context)
    }

    // ── Type guard convenience methods ──

    fun isAgentBootstrapEvent(event: InternalHookEvent): Boolean =
        event.type == InternalHookEventType.AGENT && event.action == "bootstrap" &&
            event.context["workspaceDir"] is String

    fun isGatewayStartupEvent(event: InternalHookEvent): Boolean =
        event.type == InternalHookEventType.GATEWAY && event.action == "startup"

    fun isMessageReceivedEvent(event: InternalHookEvent): Boolean =
        event.type == InternalHookEventType.MESSAGE && event.action == "received" &&
            event.context["from"] is String && event.context["channelId"] is String

    fun isMessageSentEvent(event: InternalHookEvent): Boolean =
        event.type == InternalHookEventType.MESSAGE && event.action == "sent" &&
            event.context["to"] is String && event.context["channelId"] is String &&
            event.context["success"] is Boolean

    fun isMessageTranscribedEvent(event: InternalHookEvent): Boolean =
        event.type == InternalHookEventType.MESSAGE && event.action == "transcribed" &&
            event.context["transcript"] is String && event.context["channelId"] is String

    fun isMessagePreprocessedEvent(event: InternalHookEvent): Boolean =
        event.type == InternalHookEventType.MESSAGE && event.action == "preprocessed" &&
            event.context["channelId"] is String
}
