package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/provider-types.ts, tts-types.ts, directives.ts
 *
 * Complete TTS type system: mode enum, provider interfaces, request/result
 * types, voice options, directive parsing types, and instruction markers.
 */

// ---------------------------------------------------------------------------
// Provider ID
// ---------------------------------------------------------------------------

typealias SpeechProviderId = String

// ---------------------------------------------------------------------------
// TTS Mode — aligned with OpenClaw TtsMode enum
// ---------------------------------------------------------------------------

/**
 * Controls when text-to-speech is triggered.
 * - OFF:      TTS never fires.
 * - ALWAYS:   Every outbound message is spoken.
 * - INBOUND:  Only speak when the inbound message was a voice note.
 * - TAGGED:   Only speak text wrapped in [[tts:text]]...[[/tts:text]] markers.
 */
enum class TtsMode(val raw: String) {
    OFF("off"),
    ALWAYS("always"),
    INBOUND("inbound"),
    TAGGED("tagged");

    companion object {
        /** Resolve a raw string (from config) into a TtsMode, defaulting to OFF. */
        fun fromRaw(raw: String?): TtsMode {
            if (raw == null) return OFF
            val normalized = raw.lowercase().trim()
            return entries.find { it.raw == normalized } ?: OFF
        }
    }
}

// ---------------------------------------------------------------------------
// Synthesis target
// ---------------------------------------------------------------------------

enum class SpeechSynthesisTarget { AUDIO_FILE, VOICE_NOTE }

// ---------------------------------------------------------------------------
// Voice listing
// ---------------------------------------------------------------------------

data class SpeechVoiceOption(
    val id: String,
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val locale: String? = null,
    val gender: String? = null,
    val personalities: List<String>? = null
)

// ---------------------------------------------------------------------------
// TTS Request / Result — aligned with OpenClaw TtsRequest, TtsResult
// ---------------------------------------------------------------------------

data class TtsRequest(
    val text: String,
    val provider: SpeechProviderId? = null,
    val voice: String? = null,
    val target: SpeechSynthesisTarget = SpeechSynthesisTarget.AUDIO_FILE,
    val timeoutMs: Long = 30_000
)

data class TtsResult(
    val audioData: ByteArray,
    val outputFormat: String,
    val fileExtension: String,
    val voiceCompatible: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TtsResult) return false
        return audioData.contentEquals(other.audioData) &&
            outputFormat == other.outputFormat &&
            fileExtension == other.fileExtension &&
            voiceCompatible == other.voiceCompatible
    }

    override fun hashCode(): Int = audioData.contentHashCode()
}

// Legacy aliases for backward compatibility
typealias SpeechSynthesisRequest = TtsRequest
typealias SpeechSynthesisResult = TtsResult

// ---------------------------------------------------------------------------
// Provider interface — aligned with OpenClaw SpeechProviderPlugin
// ---------------------------------------------------------------------------

interface SpeechProviderPlugin {
    val id: SpeechProviderId
    val aliases: List<String>
    val label: String?
    val defaultVoice: String?
    suspend fun synthesize(request: TtsRequest): TtsResult
    suspend fun listVoices(): List<SpeechVoiceOption>
}

// ---------------------------------------------------------------------------
// TTS Directive types (inline markers in LLM output)
// ---------------------------------------------------------------------------

data class TtsDirectiveOverrides(
    val ttsText: String? = null,
    val provider: SpeechProviderId? = null,
    val voice: String? = null
)

data class TtsDirectiveParseResult(
    val cleanedText: String,
    val ttsText: String? = null,
    val hasDirective: Boolean,
    val overrides: TtsDirectiveOverrides = TtsDirectiveOverrides(),
    val warnings: List<String> = emptyList()
)
