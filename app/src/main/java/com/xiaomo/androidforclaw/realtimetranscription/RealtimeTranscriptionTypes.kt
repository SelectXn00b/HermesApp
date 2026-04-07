package com.xiaomo.androidforclaw.realtimetranscription

/**
 * OpenClaw module: realtime-transcription
 * Source: OpenClaw/src/realtime-transcription/types.ts
 *
 * Types, interfaces, and event definitions for real-time speech-to-text
 * transcription sessions.
 */

// ---------------------------------------------------------------------------
// Provider ID
// ---------------------------------------------------------------------------

typealias RealtimeTranscriptionProviderId = String

// ---------------------------------------------------------------------------
// Event — aligned with OpenClaw RealtimeTranscriptionEvent
// ---------------------------------------------------------------------------

/**
 * A transcription event emitted during a live session.
 *
 * @param sessionKey  Opaque key identifying the transcription session.
 * @param text        The transcribed text (partial or final).
 * @param isFinal     true when this is the final transcript for a speech segment.
 * @param language    Detected language code (e.g. "en", "zh"), if available.
 * @param startMs     Segment start time in milliseconds (relative to session start).
 * @param endMs       Segment end time in milliseconds.
 */
data class RealtimeTranscriptionEvent(
    val sessionKey: String,
    val text: String,
    val isFinal: Boolean = false,
    val language: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null
)

// Legacy alias
typealias TranscriptionSegment = RealtimeTranscriptionEvent

// ---------------------------------------------------------------------------
// Session callbacks
// ---------------------------------------------------------------------------

interface RealtimeTranscriptionSessionCallbacks {
    fun onTranscript(event: RealtimeTranscriptionEvent)
    fun onError(error: Throwable)
    fun onClose()
}

// ---------------------------------------------------------------------------
// Session — a live connection to a transcription service
// ---------------------------------------------------------------------------

interface RealtimeTranscriptionSession {
    val providerId: RealtimeTranscriptionProviderId
    val isConnected: Boolean
    suspend fun connect()
    fun sendAudio(data: ByteArray)
    fun close()
}

// ---------------------------------------------------------------------------
// Provider — factory for sessions
// ---------------------------------------------------------------------------

interface RealtimeTranscriptionProvider {
    val id: RealtimeTranscriptionProviderId
    val aliases: List<String>
    val label: String?
    suspend fun createSession(
        callbacks: RealtimeTranscriptionSessionCallbacks
    ): RealtimeTranscriptionSession
}

// ---------------------------------------------------------------------------
// Event listener pattern — aligned with OpenClaw SessionLifecycleEvents
// ---------------------------------------------------------------------------

fun interface RealtimeTranscriptionListener {
    fun onEvent(event: RealtimeTranscriptionEvent)
}
