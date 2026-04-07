package com.xiaomo.androidforclaw.mediaunderstanding

/**
 * OpenClaw module: media-understanding
 * Source: OpenClaw/src/media-understanding/types.ts
 *
 * Full type definitions for media understanding capability:
 * audio transcription, video description, image description.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ---------------------------------------------------------------------------
// Kind — the string union from TS
// ---------------------------------------------------------------------------

/**
 * The specific kind of media understanding output.
 * Aligned with TS: "audio.transcription" | "video.description" | "image.description"
 */
enum class MediaUnderstandingKind(val value: String) {
    AUDIO_TRANSCRIPTION("audio.transcription"),
    VIDEO_DESCRIPTION("video.description"),
    IMAGE_DESCRIPTION("image.description");

    companion object {
        fun fromValue(value: String): MediaUnderstandingKind? =
            entries.firstOrNull { it.value == value }
    }
}

// ---------------------------------------------------------------------------
// Capability — coarser grouping used for provider matching
// ---------------------------------------------------------------------------

enum class MediaUnderstandingCapability {
    IMAGE,
    AUDIO,
    VIDEO
}

// ---------------------------------------------------------------------------
// Media attachment
// ---------------------------------------------------------------------------

/**
 * A media file attached to a message for understanding/analysis.
 */
data class MediaAttachment(
    val path: String? = null,
    val url: String? = null,
    val mime: String? = null,
    val index: Int = 0,
    val alreadyTranscribed: Boolean? = null
)

// ---------------------------------------------------------------------------
// Output
// ---------------------------------------------------------------------------

/**
 * The result of processing a single media attachment.
 */
data class MediaUnderstandingOutput(
    val kind: MediaUnderstandingKind,
    val attachmentIndex: Int,
    val text: String,
    val provider: String,
    val model: String? = null
)

// ---------------------------------------------------------------------------
// Decision types (for the decision engine)
// ---------------------------------------------------------------------------

enum class MediaUnderstandingDecisionOutcome {
    /** The attachment should be processed. */
    PROCESS,
    /** The attachment should be skipped. */
    SKIP,
    /** The attachment was already transcribed (no-op). */
    ALREADY_TRANSCRIBED,
    /** The mime type is not supported. */
    UNSUPPORTED_MIME,
    /** No provider available for this kind. */
    NO_PROVIDER
}

/**
 * Model decision for a specific attachment and capability.
 */
data class MediaUnderstandingModelDecision(
    val provider: String,
    val model: String
)

/**
 * Per-attachment decision: what to do and with which model.
 */
data class MediaUnderstandingAttachmentDecision(
    val attachment: MediaAttachment,
    val outcome: MediaUnderstandingDecisionOutcome,
    val kind: MediaUnderstandingKind? = null,
    val modelDecision: MediaUnderstandingModelDecision? = null,
    val reason: String? = null
)

/**
 * The complete decision for all attachments in a message.
 */
data class MediaUnderstandingDecision(
    val attachmentDecisions: List<MediaUnderstandingAttachmentDecision>
)

// ---------------------------------------------------------------------------
// Audio Transcription
// ---------------------------------------------------------------------------

data class AudioTranscriptionRequest(
    val provider: String,
    val model: String,
    val filePath: String,
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val language: String? = null,
    val prompt: String? = null,
    val maxChars: Int? = null,
    val maxBytes: Long? = null,
    val timeoutMs: Long? = null
)

data class AudioTranscriptionResult(
    val text: String,
    val provider: String,
    val model: String? = null,
    val language: String? = null,
    val durationMs: Long? = null,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Video Description
// ---------------------------------------------------------------------------

data class VideoDescriptionRequest(
    val provider: String,
    val model: String,
    val filePath: String,
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val prompt: String? = null,
    val maxChars: Int? = null,
    val maxBytes: Long? = null,
    val timeoutMs: Long? = null
)

data class VideoDescriptionResult(
    val text: String,
    val provider: String,
    val model: String? = null,
    val durationMs: Long? = null,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Image Description
// ---------------------------------------------------------------------------

data class ImageDescriptionRequest(
    val provider: String,
    val model: String,
    val filePath: String,
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val prompt: String? = null,
    val maxChars: Int? = null,
    val maxBytes: Long? = null,
    val timeoutMs: Long? = null
)

data class ImageDescriptionResult(
    val text: String,
    val provider: String,
    val model: String? = null,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Provider interface
// ---------------------------------------------------------------------------

/**
 * A pluggable media understanding provider.
 *
 * A provider may implement one or more capabilities (image, audio, video).
 * Only the methods for declared capabilities need functional implementations.
 */
interface MediaUnderstandingProvider {
    /** Canonical provider identifier. */
    val id: String

    /** Aliases for this provider. */
    val aliases: List<String>
        get() = emptyList()

    /** Human-readable label. */
    val label: String?
        get() = null

    /** Which capabilities this provider supports. */
    val capabilities: Set<MediaUnderstandingCapability>
        get() = emptySet()

    /**
     * Transcribe an audio file.
     * Only called if [MediaUnderstandingCapability.AUDIO] is in [capabilities].
     */
    suspend fun transcribeAudio(request: AudioTranscriptionRequest): AudioTranscriptionResult {
        throw UnsupportedOperationException("$id does not support audio transcription")
    }

    /**
     * Describe a video file.
     * Only called if [MediaUnderstandingCapability.VIDEO] is in [capabilities].
     */
    suspend fun describeVideo(request: VideoDescriptionRequest): VideoDescriptionResult {
        throw UnsupportedOperationException("$id does not support video description")
    }

    /**
     * Describe an image file.
     * Only called if [MediaUnderstandingCapability.IMAGE] is in [capabilities].
     */
    suspend fun describeImage(request: ImageDescriptionRequest): ImageDescriptionResult {
        throw UnsupportedOperationException("$id does not support image description")
    }
}
