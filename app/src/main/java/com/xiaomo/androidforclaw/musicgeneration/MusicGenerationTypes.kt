package com.xiaomo.androidforclaw.musicgeneration

/**
 * OpenClaw module: music-generation
 * Source: OpenClaw/src/music-generation/types.ts
 *
 * Full type definitions for music generation capability.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ---------------------------------------------------------------------------
// Output format
// ---------------------------------------------------------------------------

enum class MusicGenerationOutputFormat {
    MP3, WAV, OGG, FLAC;

    fun toExtension(): String = name.lowercase()
    fun toMimeType(): String = when (this) {
        MP3 -> "audio/mpeg"
        WAV -> "audio/wav"
        OGG -> "audio/ogg"
        FLAC -> "audio/flac"
    }
}

// ---------------------------------------------------------------------------
// Source image (for conditioned generation)
// ---------------------------------------------------------------------------

data class MusicGenerationSourceImage(
    val buffer: ByteArray,
    val mimeType: String = "image/png"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MusicGenerationSourceImage) return false
        return buffer.contentEquals(other.buffer) && mimeType == other.mimeType
    }

    override fun hashCode(): Int = buffer.contentHashCode()
}

// ---------------------------------------------------------------------------
// Generated asset
// ---------------------------------------------------------------------------

data class GeneratedMusicAsset(
    val buffer: ByteArray,
    val mimeType: String = "audio/mpeg",
    val fileName: String? = null,
    val durationMs: Long? = null,
    val format: MusicGenerationOutputFormat = MusicGenerationOutputFormat.MP3,
    val metadata: Map<String, Any?>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedMusicAsset) return false
        return buffer.contentEquals(other.buffer) && format == other.format
    }

    override fun hashCode(): Int = buffer.contentHashCode()
}

// ---------------------------------------------------------------------------
// Request / Result
// ---------------------------------------------------------------------------

data class MusicGenerationRequest(
    val provider: String,
    val model: String,
    val prompt: String,
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val durationMs: Long? = null,
    val outputFormat: MusicGenerationOutputFormat? = null,
    val sourceImage: MusicGenerationSourceImage? = null,
    val edit: Boolean? = null,
    val inputAudio: ByteArray? = null
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode(): Int = prompt.hashCode()
}

data class MusicGenerationResult(
    val assets: List<GeneratedMusicAsset>,
    val model: String? = null,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Ignored override
// ---------------------------------------------------------------------------

data class MusicGenerationIgnoredOverride(
    val key: String,
    val value: String
)

// ---------------------------------------------------------------------------
// Provider capabilities
// ---------------------------------------------------------------------------

data class MusicGenerationProviderCapabilities(
    val supportsEdit: Boolean? = null,
    val supportsDuration: Boolean? = null,
    val supportsOutputFormat: Boolean? = null,
    val supportsSourceImage: Boolean? = null,
    val maxDurationMs: Long? = null,
    val supportedFormats: List<MusicGenerationOutputFormat>? = null
)

// ---------------------------------------------------------------------------
// Provider configured context
// ---------------------------------------------------------------------------

data class MusicGenerationProviderConfiguredContext(
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null
)

// ---------------------------------------------------------------------------
// Provider interface
// ---------------------------------------------------------------------------

interface MusicGenerationProvider {
    val id: String
    val aliases: List<String>
        get() = emptyList()
    val label: String?
        get() = null
    val defaultModel: String?
        get() = null
    val models: List<String>
        get() = emptyList()
    val capabilities: MusicGenerationProviderCapabilities

    fun isConfigured(context: MusicGenerationProviderConfiguredContext): Boolean = true

    suspend fun generateMusic(request: MusicGenerationRequest): MusicGenerationResult
}
