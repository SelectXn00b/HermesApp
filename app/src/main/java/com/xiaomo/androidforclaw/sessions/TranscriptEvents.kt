package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/transcript-events.ts
 *
 * AndroidForClaw adaptation: thread-safe session transcript update event bus.
 */

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Describes a transcript update for a session file.
 */
data class SessionTranscriptUpdate(
    val sessionFile: String,
    val sessionKey: String? = null,
    val message: Any? = null,
    val messageId: String? = null
)

/** Listener callback type for transcript updates. */
typealias SessionTranscriptListener = (SessionTranscriptUpdate) -> Unit

/** Thread-safe set of registered transcript listeners. */
private val SESSION_TRANSCRIPT_LISTENERS = CopyOnWriteArraySet<SessionTranscriptListener>()

/**
 * Register a listener for session transcript updates.
 *
 * @return An unsubscribe function; call it to remove the listener.
 */
fun onSessionTranscriptUpdate(listener: SessionTranscriptListener): () -> Unit {
    SESSION_TRANSCRIPT_LISTENERS.add(listener)
    return { SESSION_TRANSCRIPT_LISTENERS.remove(listener) }
}

/**
 * Emit a session transcript update to all registered listeners.
 *
 * Accepts either a bare session-file path [String] or a full [SessionTranscriptUpdate].
 * Fields are normalized (trimmed, blanks collapsed to null) before dispatch.
 */
fun emitSessionTranscriptUpdate(update: Any) {
    val normalized: SessionTranscriptUpdate = when (update) {
        is String -> SessionTranscriptUpdate(sessionFile = update)
        is SessionTranscriptUpdate -> update
        else -> return
    }

    val trimmedFile = normalized.sessionFile.trim()
    if (trimmedFile.isEmpty()) return

    val nextUpdate = SessionTranscriptUpdate(
        sessionFile = trimmedFile,
        sessionKey = normalized.sessionKey?.trim()?.takeIf { it.isNotEmpty() },
        message = normalized.message,
        messageId = normalized.messageId?.trim()?.takeIf { it.isNotEmpty() }
    )

    for (listener in SESSION_TRANSCRIPT_LISTENERS) {
        try {
            listener(nextUpdate)
        } catch (_: Exception) {
            // Ignore listener errors.
        }
    }
}
