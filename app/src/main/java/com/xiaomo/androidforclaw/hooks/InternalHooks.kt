package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - src/hooks/internal-hooks.ts
 *   (InternalHookEvent, registerInternalHook, triggerInternalHook, clearInternalHooks)
 *
 * AndroidForClaw adaptation: event-driven hook system for agent lifecycle events.
 * Uses ConcurrentHashMap for thread-safe handler registry (process-scoped singleton).
 */

import com.xiaomo.androidforclaw.logging.Log
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// ============================================================================
// Event types
// ============================================================================

/**
 * Internal hook event types.
 * Aligned with OpenClaw InternalHookEventType.
 */
enum class InternalHookEventType(val key: String) {
    COMMAND("command"),
    SESSION("session"),
    AGENT("agent"),
    GATEWAY("gateway"),
    MESSAGE("message");

    companion object {
        fun fromString(value: String?): InternalHookEventType? =
            entries.find { it.key == value?.lowercase() }
    }
}

// ============================================================================
// Hook event
// ============================================================================

/**
 * Internal hook event.
 * Aligned with OpenClaw InternalHookEvent.
 */
data class InternalHookEvent(
    val type: InternalHookEventType,
    val action: String,
    val sessionKey: String = "",
    val context: MutableMap<String, Any?> = mutableMapOf(),
    val timestamp: Date = Date(),
    val messages: MutableList<String> = mutableListOf()
)

/**
 * Hook handler function type.
 * Aligned with OpenClaw InternalHookHandler.
 */
typealias InternalHookHandler = suspend (InternalHookEvent) -> Unit

// ============================================================================
// Context types for typed hook events
// ============================================================================

/**
 * Agent bootstrap hook context.
 * Aligned with OpenClaw AgentBootstrapHookContext.
 */
data class AgentBootstrapHookContext(
    val workspaceDir: String,
    val bootstrapFiles: List<String> = emptyList(),
    val sessionKey: String? = null,
    val sessionId: String? = null,
    val agentId: String? = null
)

/**
 * Gateway startup hook context.
 * Aligned with OpenClaw GatewayStartupHookContext.
 */
data class GatewayStartupHookContext(
    val workspaceDir: String? = null
)

/**
 * Message received hook context.
 * Aligned with OpenClaw MessageReceivedHookContext.
 */
data class MessageReceivedHookContext(
    val from: String,
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
    val to: String,
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
    val to: String? = null,
    val body: String? = null,
    val bodyForAgent: String? = null,
    val timestamp: Long? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null,
    val senderUsername: String? = null,
    val provider: String? = null,
    val surface: String? = null,
    val mediaPath: String? = null,
    val mediaType: String? = null
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
    val to: String? = null,
    val body: String? = null,
    val bodyForAgent: String? = null,
    val timestamp: Long? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null,
    val senderUsername: String? = null,
    val provider: String? = null,
    val surface: String? = null,
    val mediaPath: String? = null,
    val mediaType: String? = null
)

/**
 * Session patch hook context.
 * Aligned with OpenClaw SessionPatchHookContext.
 */
data class SessionPatchHookContext(
    val sessionKey: String,
    val patch: Map<String, Any?>,
    val cfg: Map<String, Any?>
)

// ============================================================================
// Registry (process-scoped singleton)
// ============================================================================

/**
 * InternalHooks -- Event-driven hook system.
 * Aligned with OpenClaw internal-hooks.ts.
 *
 * Uses ConcurrentHashMap<String, CopyOnWriteArrayList> for thread-safe
 * handler registration and iteration.
 */
object InternalHooks {

    private const val TAG = "InternalHooks"

    /**
     * Registry of hook handlers by event key.
     * Uses CopyOnWriteArrayList so iteration during trigger is safe
     * even if registration/unregistration happens concurrently.
     */
    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<InternalHookHandler>>()

    // ── Registration ──

    /**
     * Register a hook handler for a specific event type or event:action combination.
     * Aligned with OpenClaw registerInternalHook.
     */
    fun register(eventKey: String, handler: InternalHookHandler) {
        handlers.getOrPut(eventKey) { CopyOnWriteArrayList() }.add(handler)
        Log.d(TAG, "Registered hook handler for: $eventKey")
    }

    /**
     * Unregister a specific hook handler.
     * Aligned with OpenClaw unregisterInternalHook.
     */
    fun unregister(eventKey: String, handler: InternalHookHandler) {
        val eventHandlers = handlers[eventKey] ?: return
        eventHandlers.remove(handler)
        if (eventHandlers.isEmpty()) {
            handlers.remove(eventKey)
        }
    }

    /**
     * Clear all registered hooks (useful for testing).
     * Aligned with OpenClaw clearInternalHooks.
     */
    fun clear() {
        handlers.clear()
        Log.d(TAG, "All hooks cleared")
    }

    /**
     * Get all registered event keys (useful for debugging).
     * Aligned with OpenClaw getRegisteredEventKeys.
     */
    fun getRegisteredEventKeys(): Set<String> = handlers.keys.toSet()

    /**
     * Check if there are any handlers for the given type/action.
     * Aligned with OpenClaw hasInternalHookListeners.
     */
    fun hasListeners(type: InternalHookEventType, action: String): Boolean {
        val typeKey = type.key
        return (handlers[typeKey]?.size ?: 0) > 0 ||
            (handlers["$typeKey:$action"]?.size ?: 0) > 0
    }

    // ── Triggering ──

    /**
     * Trigger a hook event.
     * Calls all handlers registered for:
     * 1. The general event type (e.g., 'command')
     * 2. The specific event:action combination (e.g., 'command:new')
     * Aligned with OpenClaw triggerInternalHook.
     */
    suspend fun trigger(event: InternalHookEvent) {
        if (!hasListeners(event.type, event.action)) return

        val typeKey = event.type.key
        val actionKey = "$typeKey:${event.action}"

        val typeHandlers = handlers[typeKey]?.toList() ?: emptyList()
        val actionHandlers = handlers[actionKey]?.toList() ?: emptyList()
        val allHandlers = typeHandlers + actionHandlers

        for (handler in allHandlers) {
            try {
                handler(event)
            } catch (e: Exception) {
                Log.w(TAG, "Hook error [$actionKey]: ${e.message}")
            }
        }
    }

    // ── Factory ──

    /**
     * Create a hook event with common fields filled in.
     * Aligned with OpenClaw createInternalHookEvent.
     */
    fun createEvent(
        type: InternalHookEventType,
        action: String,
        sessionKey: String = "",
        context: MutableMap<String, Any?> = mutableMapOf()
    ): InternalHookEvent {
        return InternalHookEvent(
            type = type,
            action = action,
            sessionKey = sessionKey,
            context = context
        )
    }

    // ── Type guards ──

    fun isAgentBootstrapEvent(event: InternalHookEvent): Boolean =
        event.type == InternalHookEventType.AGENT && event.action == "bootstrap" &&
            event.context["workspaceDir"] is String &&
            event.context["bootstrapFiles"] is List<*>

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

    fun isSessionPatchEvent(event: InternalHookEvent): Boolean =
        event.type == InternalHookEventType.SESSION && event.action == "patch" &&
            event.context["patch"] is Map<*, *> &&
            event.context["cfg"] is Map<*, *> &&
            event.context["sessionEntry"] is Map<*, *>
}

// ============================================================================
// Top-level aliases (aligned with OpenClaw hooks.ts re-exports)
// ============================================================================

fun registerHook(eventKey: String, handler: InternalHookHandler) =
    InternalHooks.register(eventKey, handler)

fun unregisterHook(eventKey: String, handler: InternalHookHandler) =
    InternalHooks.unregister(eventKey, handler)

fun clearHooks() = InternalHooks.clear()

fun getRegisteredHookEventKeys(): Set<String> = InternalHooks.getRegisteredEventKeys()

suspend fun triggerHook(event: InternalHookEvent) = InternalHooks.trigger(event)

fun createHookEvent(
    type: InternalHookEventType,
    action: String,
    sessionKey: String = "",
    context: MutableMap<String, Any?> = mutableMapOf()
): InternalHookEvent = InternalHooks.createEvent(type, action, sessionKey, context)
