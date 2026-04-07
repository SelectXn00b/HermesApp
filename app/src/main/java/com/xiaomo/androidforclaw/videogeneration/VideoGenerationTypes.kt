package com.xiaomo.androidforclaw.videogeneration

/**
 * OpenClaw module: video-generation
 * Source: OpenClaw/src/video-generation/types.ts
 *
 * Full type definitions for video generation capability.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ---------------------------------------------------------------------------
// Source image (for image-to-video)
// ---------------------------------------------------------------------------

/**
 * An input image used as a starting frame for video generation.
 */
data class VideoGenerationSourceImage(
    val buffer: ByteArray,
    val mimeType: String = "image/png"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VideoGenerationSourceImage) return false
        return buffer.contentEquals(other.buffer) && mimeType == other.mimeType
    }

    override fun hashCode(): Int = buffer.contentHashCode()
}

// ---------------------------------------------------------------------------
// Generated asset
// ---------------------------------------------------------------------------

/**
 * A single generated video returned by a provider.
 */
data class GeneratedVideoAsset(
    val buffer: ByteArray,
    val mimeType: String = "video/mp4",
    val fileName: String? = null,
    val durationMs: Long? = null,
    val metadata: Map<String, Any?>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedVideoAsset) return false
        return buffer.contentEquals(other.buffer) && mimeType == other.mimeType
    }

    override fun hashCode(): Int = buffer.contentHashCode()
}

// ---------------------------------------------------------------------------
// Request / Result
// ---------------------------------------------------------------------------

/**
 * Provider-level video generation request.
 */
data class VideoGenerationRequest(
    val provider: String,
    val model: String,
    val prompt: String,
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val sourceImage: VideoGenerationSourceImage? = null,
    val durationSeconds: Int? = null,
    val aspectRatio: String? = null,
    val resolution: String? = null
)

/**
 * Provider-level video generation result.
 */
data class VideoGenerationResult(
    val videos: List<GeneratedVideoAsset>,
    val model: String? = null,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Ignored override
// ---------------------------------------------------------------------------

data class VideoGenerationIgnoredOverride(
    val key: String,
    val value: String
)

// ---------------------------------------------------------------------------
// Provider capabilities
// ---------------------------------------------------------------------------

data class VideoGenerationProviderCapabilities(
    val supportsImageToVideo: Boolean? = null,
    val supportsDuration: Boolean? = null,
    val supportsAspectRatio: Boolean? = null,
    val supportsResolution: Boolean? = null,
    val maxDurationSeconds: Int? = null
)

// ---------------------------------------------------------------------------
// Provider configured context
// ---------------------------------------------------------------------------

data class VideoGenerationProviderConfiguredContext(
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null
)

// ---------------------------------------------------------------------------
// Provider interface
// ---------------------------------------------------------------------------

interface VideoGenerationProvider {
    val id: String
    val aliases: List<String>
        get() = emptyList()
    val label: String?
        get() = null
    val defaultModel: String?
        get() = null
    val models: List<String>
        get() = emptyList()
    val capabilities: VideoGenerationProviderCapabilities

    fun isConfigured(context: VideoGenerationProviderConfiguredContext): Boolean = true

    suspend fun generateVideo(request: VideoGenerationRequest): VideoGenerationResult
}
