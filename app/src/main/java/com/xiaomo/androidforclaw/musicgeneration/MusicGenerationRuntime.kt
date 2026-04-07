package com.xiaomo.androidforclaw.musicgeneration

/**
 * OpenClaw module: music-generation
 * Source: OpenClaw/src/music-generation/runtime.ts
 *
 * Provider-agnostic music generation runtime with model resolution,
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

data class GenerateMusicParams(
    val cfg: OpenClawConfig? = null,
    val prompt: String,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val modelOverride: String? = null,
    val modelConfig: Any? = null,
    val durationMs: Long? = null,
    val outputFormat: MusicGenerationOutputFormat? = null,
    val sourceImage: MusicGenerationSourceImage? = null,
    val edit: Boolean? = null,
    val inputAudio: ByteArray? = null
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode(): Int = prompt.hashCode()
}

data class GenerateMusicRuntimeResult(
    val assets: List<GeneratedMusicAsset>,
    val provider: String,
    val model: String,
    val attempts: List<FallbackAttempt>,
    val ignoredOverrides: List<MusicGenerationIgnoredOverride>,
    val metadata: Map<String, Any?>? = null
)

// ---------------------------------------------------------------------------
// Override resolution
// ---------------------------------------------------------------------------

private fun resolveProviderMusicGenerationOverrides(
    capabilities: MusicGenerationProviderCapabilities,
    edit: Boolean?,
    durationMs: Long?,
    outputFormat: MusicGenerationOutputFormat?,
    sourceImage: MusicGenerationSourceImage?
): List<MusicGenerationIgnoredOverride> {
    val ignored = mutableListOf<MusicGenerationIgnoredOverride>()

    if (edit == true && capabilities.supportsEdit != true) {
        ignored.add(MusicGenerationIgnoredOverride("edit", "true"))
    }
    if (durationMs != null && capabilities.supportsDuration != true) {
        ignored.add(MusicGenerationIgnoredOverride("durationMs", durationMs.toString()))
    }
    if (outputFormat != null && capabilities.supportsOutputFormat != true) {
        ignored.add(MusicGenerationIgnoredOverride("outputFormat", outputFormat.name))
    }
    if (sourceImage != null && capabilities.supportsSourceImage != true) {
        ignored.add(MusicGenerationIgnoredOverride("sourceImage", "(binary)"))
    }

    return ignored
}

// ---------------------------------------------------------------------------
// Runtime entry point
// ---------------------------------------------------------------------------

private const val CAPABILITY_LABEL = "Music Generation"
private const val MODEL_CONFIG_KEY = "agents.defaults.musicGeneration.model"

suspend fun generateMusic(params: GenerateMusicParams): GenerateMusicRuntimeResult {
    val candidates = resolveCapabilityModelCandidates(
        cfg = params.cfg,
        modelConfig = params.modelConfig,
        modelOverride = params.modelOverride,
        parseModelRef = ::parseMusicGenerationModelRef
    )

    if (candidates.isEmpty()) {
        val providers = listMusicGenerationProviders(params.cfg).map { it.id }
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
        val provider = getMusicGenerationProvider(candidate.provider, params.cfg)
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

        val configuredCtx = MusicGenerationProviderConfiguredContext(
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

        val ignoredOverrides = resolveProviderMusicGenerationOverrides(
            capabilities = provider.capabilities,
            edit = params.edit,
            durationMs = params.durationMs,
            outputFormat = params.outputFormat,
            sourceImage = params.sourceImage
        )

        val request = MusicGenerationRequest(
            provider = candidate.provider,
            model = candidate.model,
            prompt = params.prompt,
            cfg = params.cfg,
            agentDir = params.agentDir,
            authStore = params.authStore,
            durationMs = if (provider.capabilities.supportsDuration == true) params.durationMs else null,
            outputFormat = if (provider.capabilities.supportsOutputFormat == true) params.outputFormat else null,
            sourceImage = if (provider.capabilities.supportsSourceImage == true) params.sourceImage else null,
            edit = if (provider.capabilities.supportsEdit == true) params.edit else null,
            inputAudio = params.inputAudio
        )

        try {
            val result = provider.generateMusic(request)
            return GenerateMusicRuntimeResult(
                assets = result.assets,
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
