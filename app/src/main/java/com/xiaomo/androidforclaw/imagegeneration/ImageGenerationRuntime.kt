package com.xiaomo.androidforclaw.imagegeneration

/**
 * OpenClaw module: image-generation
 * Source: OpenClaw/src/image-generation/runtime.ts
 *
 * Provider-agnostic image generation runtime with:
 * - Model candidate resolution via shared [resolveCapabilityModelCandidates]
 * - Per-candidate failover loop
 * - Ignored-override collection (capabilities check)
 * - Consolidated error reporting via [throwCapabilityGenerationFailure]
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.mediageneration.FallbackAttempt
import com.xiaomo.androidforclaw.mediageneration.ParsedProviderModelRef
import com.xiaomo.androidforclaw.mediageneration.buildNoCapabilityModelConfiguredMessage
import com.xiaomo.androidforclaw.mediageneration.resolveCapabilityModelCandidates
import com.xiaomo.androidforclaw.mediageneration.throwCapabilityGenerationFailure

// ---------------------------------------------------------------------------
// Params / Result
// ---------------------------------------------------------------------------

/**
 * High-level parameters for [generateImage].
 */
data class GenerateImageParams(
    val cfg: OpenClawConfig? = null,
    val prompt: String,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val modelOverride: String? = null,
    val modelConfig: Any? = null,
    val count: Int? = null,
    val size: String? = null,
    val quality: String? = null,
    val style: String? = null,
    val aspectRatio: String? = null
)

/**
 * Full runtime result, including failover metadata.
 */
data class GenerateImageRuntimeResult(
    val images: List<GeneratedImageAsset>,
    val provider: String,
    val model: String,
    val attempts: List<FallbackAttempt>,
    val ignoredOverrides: List<ImageGenerationIgnoredOverride>,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Override resolution
// ---------------------------------------------------------------------------

/**
 * Check which optional overrides the provider actually supports.
 * Returns ignored overrides for any unsupported parameter that was requested.
 */
private fun resolveProviderImageGenerationOverrides(
    capabilities: ImageGenerationProviderCapabilities,
    size: String?,
    quality: String?,
    style: String?,
    aspectRatio: String?
): List<ImageGenerationIgnoredOverride> {
    val ignored = mutableListOf<ImageGenerationIgnoredOverride>()

    if (size != null && capabilities.supportsSize != true) {
        ignored.add(ImageGenerationIgnoredOverride("size", size))
    }
    if (quality != null && capabilities.supportsQuality != true) {
        ignored.add(ImageGenerationIgnoredOverride("quality", quality))
    }
    if (style != null && capabilities.supportsStyle != true) {
        ignored.add(ImageGenerationIgnoredOverride("style", style))
    }
    if (aspectRatio != null && capabilities.supportsAspectRatio != true) {
        ignored.add(ImageGenerationIgnoredOverride("aspectRatio", aspectRatio))
    }

    return ignored
}

// ---------------------------------------------------------------------------
// Runtime entry point
// ---------------------------------------------------------------------------

private const val CAPABILITY_LABEL = "Image Generation"
private const val MODEL_CONFIG_KEY = "agents.defaults.imageGeneration.model"

/**
 * Generate images with automatic model resolution and failover.
 *
 * 1. Resolve ordered candidate list (override -> primary -> fallbacks).
 * 2. For each candidate, locate the provider, check overrides, call generateImage.
 * 3. On success, return immediately with full metadata.
 * 4. On failure, record the attempt and continue to next candidate.
 * 5. If all candidates are exhausted, throw a consolidated error.
 */
suspend fun generateImage(params: GenerateImageParams): GenerateImageRuntimeResult {
    val candidates = resolveCapabilityModelCandidates(
        cfg = params.cfg,
        modelConfig = params.modelConfig,
        modelOverride = params.modelOverride,
        parseModelRef = ::parseImageGenerationModelRef
    )

    if (candidates.isEmpty()) {
        val providers = listImageGenerationProviders(params.cfg).map { it.id }
        throw IllegalStateException(
            buildNoCapabilityModelConfiguredMessage(
                capabilityLabel = CAPABILITY_LABEL,
                modelConfigKey = MODEL_CONFIG_KEY,
                providers = providers
            )
        )
    }

    val attempts = mutableListOf<FallbackAttempt>()
    var lastError: Throwable? = null

    for (candidate in candidates) {
        val provider = getImageGenerationProvider(candidate.provider, params.cfg)
        if (provider == null) {
            attempts.add(
                FallbackAttempt(
                    provider = candidate.provider,
                    model = candidate.model,
                    reason = "Provider not found"
                )
            )
            continue
        }

        // Check provider is configured
        val configuredCtx = ImageGenerationProviderConfiguredContext(
            cfg = params.cfg,
            agentDir = params.agentDir
        )
        if (!provider.isConfigured(configuredCtx)) {
            attempts.add(
                FallbackAttempt(
                    provider = candidate.provider,
                    model = candidate.model,
                    reason = "Provider not configured"
                )
            )
            continue
        }

        // Collect ignored overrides
        val ignoredOverrides = resolveProviderImageGenerationOverrides(
            capabilities = provider.capabilities,
            size = params.size,
            quality = params.quality,
            style = params.style,
            aspectRatio = params.aspectRatio
        )

        // Build provider-level request
        val request = ImageGenerationRequest(
            provider = candidate.provider,
            model = candidate.model,
            prompt = params.prompt,
            cfg = params.cfg,
            agentDir = params.agentDir,
            authStore = params.authStore,
            count = params.count,
            size = if (provider.capabilities.supportsSize == true) params.size else null,
            quality = if (provider.capabilities.supportsQuality == true) params.quality else null,
            style = if (provider.capabilities.supportsStyle == true) params.style else null,
            aspectRatio = if (provider.capabilities.supportsAspectRatio == true) params.aspectRatio else null
        )

        try {
            val result = provider.generateImage(request)
            return GenerateImageRuntimeResult(
                images = result.images,
                provider = candidate.provider,
                model = result.model ?: candidate.model,
                attempts = attempts,
                ignoredOverrides = ignoredOverrides,
                metadata = result.metadata
            )
        } catch (e: Throwable) {
            lastError = e
            attempts.add(
                FallbackAttempt(
                    provider = candidate.provider,
                    model = candidate.model,
                    error = e,
                    reason = e.message
                )
            )
        }
    }

    // All candidates exhausted
    throwCapabilityGenerationFailure(CAPABILITY_LABEL, attempts, lastError)
}
