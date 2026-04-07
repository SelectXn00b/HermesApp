package com.xiaomo.androidforclaw.realtimevoice

/**
 * OpenClaw module: realtime-voice
 * Source: OpenClaw/src/realtime-voice/types.ts
 *
 * Types, interfaces, and event definitions for real-time voice (duplex
 * audio) sessions with an AI backend.
 */

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class RealtimeVoiceRole { SYSTEM, USER, ASSISTANT }
enum class RealtimeVoiceCloseReason { NORMAL, ERROR, TIMEOUT, CANCELLED }

// ---------------------------------------------------------------------------
// Event — aligned with OpenClaw RealtimeVoiceEvent
// ---------------------------------------------------------------------------

/**
 * A voice event emitted during a live session.
 *
 * @param sessionKey  Opaque key identifying the voice session.
 * @param audioData   Raw audio bytes (PCM or codec-specific), null for text-only events.
 * @param text        Transcript or partial transcript, null for audio-only events.
 * @param isFinal     true when this is the final event for a turn.
 * @param role        Who produced this event (USER for input, ASSISTANT for output).
 */
data class RealtimeVoiceEvent(
    val sessionKey: String,
    val audioData: ByteArray? = null,
    val text: String? = null,
    val isFinal: Boolean = false,
    val role: RealtimeVoiceRole = RealtimeVoiceRole.ASSISTANT
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RealtimeVoiceEvent) return false
        return sessionKey == other.sessionKey &&
            (audioData?.contentEquals(other.audioData ?: ByteArray(0)) ?: (other.audioData == null)) &&
            text == other.text &&
            isFinal == other.isFinal &&
            role == other.role
    }

    override fun hashCode(): Int {
        var h = sessionKey.hashCode()
        h = 31 * h + (audioData?.contentHashCode() ?: 0)
        h = 31 * h + (text?.hashCode() ?: 0)
        h = 31 * h + isFinal.hashCode()
        h = 31 * h + role.hashCode()
        return h
    }
}

// ---------------------------------------------------------------------------
// Tool call support
// ---------------------------------------------------------------------------

data class RealtimeVoiceTool(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>? = null
)

data class RealtimeVoiceToolCallEvent(
    val callId: String,
    val toolName: String,
    val arguments: String
)

// ---------------------------------------------------------------------------
// Bridge callbacks — what the bridge reports back to the caller
// ---------------------------------------------------------------------------

interface RealtimeVoiceBridgeCallbacks {
    fun onAudio(data: ByteArray)
    fun onTranscript(role: RealtimeVoiceRole, text: String, isFinal: Boolean)
    fun onToolCall(event: RealtimeVoiceToolCallEvent)
    fun onError(error: Throwable)
    fun onClose(reason: RealtimeVoiceCloseReason)
}

// ---------------------------------------------------------------------------
// Bridge — a live duplex connection to a voice AI backend
// ---------------------------------------------------------------------------

interface RealtimeVoiceBridge {
    val isConnected: Boolean
    suspend fun connect()
    fun sendAudio(data: ByteArray)
    fun setMediaTimestamp(timestampMs: Long)
    fun submitToolResult(callId: String, result: String)
    fun acknowledgeMark(markId: String)
    fun close(reason: RealtimeVoiceCloseReason = RealtimeVoiceCloseReason.NORMAL)
}

// ---------------------------------------------------------------------------
// Provider — factory for bridges
// ---------------------------------------------------------------------------

interface RealtimeVoiceProvider {
    val id: String
    val aliases: List<String>
    val label: String?
    suspend fun createBridge(
        callbacks: RealtimeVoiceBridgeCallbacks,
        tools: List<RealtimeVoiceTool>? = null
    ): RealtimeVoiceBridge
}

// ---------------------------------------------------------------------------
// Event listener pattern — aligned with OpenClaw SessionLifecycleEvents
// ---------------------------------------------------------------------------

fun interface RealtimeVoiceListener {
    fun onEvent(event: RealtimeVoiceEvent)
}
