package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/session-lifecycle-events.ts
 *
 * AndroidForClaw adaptation: thread-safe session lifecycle event bus.
 */

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Represents a session lifecycle event (created, destroyed, etc.).
 */
data class SessionLifecycleEvent(
    val sessionKey: String,
    val reason: String,
    val parentSessionKey: String? = null,
    val label: String? = null,
    val displayName: String? = null
)

/** Listener callback type for session lifecycle events. */
typealias SessionLifecycleListener = (SessionLifecycleEvent) -> Unit

/** Thread-safe set of registered lifecycle listeners. */
private val SESSION_LIFECYCLE_LISTENERS = CopyOnWriteArraySet<SessionLifecycleListener>()

/**
 * Register a listener for session lifecycle events.
 *
 * @return An unsubscribe function; call it to remove the listener.
 */
fun onSessionLifecycleEvent(listener: SessionLifecycleListener): () -> Unit {
    SESSION_LIFECYCLE_LISTENERS.add(listener)
    return { SESSION_LIFECYCLE_LISTENERS.remove(listener) }
}

/**
 * Emit a session lifecycle event to all registered listeners.
 * Listener errors are silently caught (best-effort delivery).
 */
fun emitSessionLifecycleEvent(event: SessionLifecycleEvent) {
    for (listener in SESSION_LIFECYCLE_LISTENERS) {
        try {
            listener(event)
        } catch (_: Exception) {
            // Best-effort, do not propagate listener errors.
        }
    }
}
