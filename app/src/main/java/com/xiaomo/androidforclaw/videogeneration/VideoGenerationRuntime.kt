package com.xiaomo.androidforclaw.videogeneration

/**
 * OpenClaw module: video-generation
 * Source: OpenClaw/src/video-generation/runtime.ts
 *
 * Provider-agnostic video generation runtime with model resolution,
 * parameter validation, failover, and consolidated error reporting.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.mediageneration.FallbackAttempt
import com.xiaomo.androidforclaw.mediageneration.buildNoCapabilityModelConfiguredMessage
import com.xiaomo.androidforclaw.mediageneration.resolveCapabilityModelCandidates
import com.xiaomo.androidforclaw.mediageneration.throwCapabilityGenerationFailure

// ---------------------------------------------------------------------------
// Params / Result
// ---------------------------------------------------------------------------

data class GenerateVideoParams(
    val cfg: OpenClawConfig? = null,
    val prompt: String,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val modelOverride: String? = null,
    val modelConfig: Any? = null,
    val sourceImage: VideoGenerationSourceImage? = null,
    val durationSeconds: Int? = null,
    val aspectRatio: String? = null,
    val resolution: String? = null
)

data class GenerateVideoRuntimeResult(
    val videos: List<GeneratedVideoAsset>,
    val provider: String,
    val model: String,
    val attempts: List<FallbackAttempt>,
    val ignoredOverrides: List<VideoGenerationIgnoredOverride>,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Override resolution
// ---------------------------------------------------------------------------

private fun resolveProviderVideoGenerationOverrides(
    capabilities: VideoGenerationProviderCapabilities,
    sourceImage: VideoGenerationSourceImage?,
    durationSeconds: Int?,
    aspectRatio: String?,
    resolution: String?
): List<VideoGenerationIgnoredOverride> {
    val ignored = mutableListOf<VideoGenerationIgnoredOverride>()

    if (sourceImage != null && capabilities.supportsImageToVideo != true) {
        ignored.add(VideoGenerationIgnoredOverride("sourceImage", "(binary)"))
    }
    if (durationSeconds != null && capabilities.supportsDuration != true) {
        ignored.add(VideoGenerationIgnoredOverride("durationSeconds", durationSeconds.toString()))
    }
    if (aspectRatio != null && capabilities.supportsAspectRatio != true) {
        ignored.add(VideoGenerationIgnoredOverride("aspectRatio", aspectRatio))
    }
    if (resolution != null && capabilities.supportsResolution != true) {
        ignored.add(VideoGenerationIgnoredOverride("resolution", resolution))
    }

    return ignored
}

// ---------------------------------------------------------------------------
// Runtime entry point
// ---------------------------------------------------------------------------

private const val CAPABILITY_LABEL = "Video Generation"
private const val MODEL_CONFIG_KEY = "agents.defaults.videoGeneration.model"

suspend fun generateVideo(params: GenerateVideoParams): GenerateVideoRuntimeResult {
    val candidates = resolveCapabilityModelCandidates(
        cfg = params.cfg,
        modelConfig = params.modelConfig,
        modelOverride = params.modelOverride,
        parseModelRef = ::parseVideoGenerationModelRef
    )

    if (candidates.isEmpty()) {
        val providers = listVideoGenerationProviders(params.cfg).map { it.id }
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
        val provider = getVideoGenerationProvider(candidate.provider, params.cfg)
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

        val configuredCtx = VideoGenerationProviderConfiguredContext(
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

        val ignoredOverrides = resolveProviderVideoGenerationOverrides(
            capabilities = provider.capabilities,
            sourceImage = params.sourceImage,
            durationSeconds = params.durationSeconds,
            aspectRatio = params.aspectRatio,
            resolution = params.resolution
        )

        val request = VideoGenerationRequest(
            provider = candidate.provider,
            model = candidate.model,
            prompt = params.prompt,
            cfg = params.cfg,
            agentDir = params.agentDir,
            authStore = params.authStore,
            sourceImage = if (provider.capabilities.supportsImageToVideo == true) params.sourceImage else null,
            durationSeconds = if (provider.capabilities.supportsDuration == true) params.durationSeconds else null,
            aspectRatio = if (provider.capabilities.supportsAspectRatio == true) params.aspectRatio else null,
            resolution = if (provider.capabilities.supportsResolution == true) params.resolution else null
        )

        try {
            val result = provider.generateVideo(request)
            return GenerateVideoRuntimeResult(
                videos = result.videos,
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

    throwCapabilityGenerationFailure(CAPABILITY_LABEL, attempts, lastError)
}
