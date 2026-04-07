package com.xiaomo.androidforclaw.imagegeneration

/**
 * OpenClaw module: image-generation
 * Source: OpenClaw/src/image-generation/types.ts
 *
 * Full type definitions for image generation capability.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ---------------------------------------------------------------------------
// Generated asset
// ---------------------------------------------------------------------------

/**
 * A single generated image returned by a provider.
 */
data class GeneratedImageAsset(
    val buffer: ByteArray,
    val mimeType: String = "image/png",
    val fileName: String? = null,
    val metadata: Map<String, Any?>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedImageAsset) return false
        return buffer.contentEquals(other.buffer) && mimeType == other.mimeType && fileName == other.fileName
    }

    override fun hashCode(): Int = buffer.contentHashCode()
}

// ---------------------------------------------------------------------------
// Request / Result
// ---------------------------------------------------------------------------

/**
 * Provider-level image generation request.
 */
data class ImageGenerationRequest(
    val provider: String,
    val model: String,
    val prompt: String,
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val count: Int? = null,
    val size: String? = null,
    val quality: String? = null,
    val style: String? = null,
    val aspectRatio: String? = null
)

/**
 * Provider-level image generation result.
 */
data class ImageGenerationResult(
    val images: List<GeneratedImageAsset>,
    val model: String? = null,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Ignored override (runtime feedback)
// ---------------------------------------------------------------------------

/**
 * An override parameter that was requested but not supported by the provider.
 */
data class ImageGenerationIgnoredOverride(
    val key: String,
    val value: String
)

// ---------------------------------------------------------------------------
// Provider capabilities
// ---------------------------------------------------------------------------

/**
 * Declares what optional parameters a provider supports.
 */
data class ImageGenerationProviderCapabilities(
    val maxImages: Int? = null,
    val supportsSize: Boolean? = null,
    val supportsQuality: Boolean? = null,
    val supportsStyle: Boolean? = null,
    val supportsAspectRatio: Boolean? = null
)

// ---------------------------------------------------------------------------
// Provider configured context
// ---------------------------------------------------------------------------

/**
 * Context passed to a provider's [isConfigured] check.
 */
data class ImageGenerationProviderConfiguredContext(
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null
)

// ---------------------------------------------------------------------------
// Provider interface
// ---------------------------------------------------------------------------

/**
 * A pluggable image generation provider.
 *
 * Providers register into [ImageGenerationProviderRegistry] and are resolved
 * by the runtime during candidate failover.
 */
interface ImageGenerationProvider {
    /** Canonical provider identifier (e.g. "openai", "stability"). */
    val id: String

    /** Alternative names this provider responds to. */
    val aliases: List<String>
        get() = emptyList()

    /** Human-readable label. */
    val label: String?
        get() = null

    /** Default model ID when none is specified in the ref. */
    val defaultModel: String?
        get() = null

    /** List of model IDs this provider supports (informational). */
    val models: List<String>
        get() = emptyList()

    /** Capability flags for optional request parameters. */
    val capabilities: ImageGenerationProviderCapabilities

    /**
     * Check whether this provider is configured / usable.
     * Returns `true` by default.
     */
    fun isConfigured(context: ImageGenerationProviderConfiguredContext): Boolean = true

    /**
     * Generate images for the given request.
     */
    suspend fun generateImage(request: ImageGenerationRequest): ImageGenerationResult
}
