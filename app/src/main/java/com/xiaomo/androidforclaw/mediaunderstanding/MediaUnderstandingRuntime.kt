package com.xiaomo.androidforclaw.mediaunderstanding

/**
 * OpenClaw module: media-understanding
 * Source: OpenClaw/src/media-understanding/runtime.ts
 *
 * Simplified runtime for Android: dispatches media understanding requests
 * to registered providers based on file type / capability.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ---------------------------------------------------------------------------
// Result types
// ---------------------------------------------------------------------------

/**
 * Result of running media understanding on a single file.
 */
data class RunMediaUnderstandingFileResult(
    val output: MediaUnderstandingOutput?,
    val error: Throwable? = null
)

// ---------------------------------------------------------------------------
// Params
// ---------------------------------------------------------------------------

data class MediaUnderstandingFileParams(
    val filePath: String,
    val capability: MediaUnderstandingCapability,
    val provider: String,
    val model: String,
    val cfg: OpenClawConfig? = null,
    val agentDir: String? = null,
    val authStore: Map<String, Any?>? = null,
    val prompt: String? = null,
    val maxChars: Int? = null,
    val maxBytes: Long? = null,
    val timeoutMs: Long? = null,
    val attachmentIndex: Int = 0
)

// ---------------------------------------------------------------------------
// Convenience wrappers
// ---------------------------------------------------------------------------

/**
 * Describe an image file using a registered provider.
 */
suspend fun describeImageFile(
    filePath: String,
    provider: String,
    model: String,
    cfg: OpenClawConfig? = null,
    agentDir: String? = null,
    authStore: Map<String, Any?>? = null,
    prompt: String? = null,
    maxChars: Int? = null,
    maxBytes: Long? = null,
    timeoutMs: Long? = null
): ImageDescriptionResult {
    val p = getMediaUnderstandingProvider(provider)
        ?: throw IllegalStateException("Media understanding provider not found: $provider")

    if (MediaUnderstandingCapability.IMAGE !in p.capabilities) {
        throw IllegalStateException("Provider $provider does not support image description")
    }

    return p.describeImage(
        ImageDescriptionRequest(
            provider = provider,
            model = model,
            filePath = filePath,
            cfg = cfg,
            agentDir = agentDir,
            authStore = authStore,
            prompt = resolvePrompt(MediaUnderstandingCapability.IMAGE, prompt),
            maxChars = resolveMaxChars(MediaUnderstandingCapability.IMAGE, maxChars),
            maxBytes = resolveMaxBytes(maxBytes),
            timeoutMs = resolveTimeoutMs(timeoutMs)
        )
    )
}

/**
 * Describe a video file using a registered provider.
 */
suspend fun describeVideoFile(
    filePath: String,
    provider: String,
    model: String,
    cfg: OpenClawConfig? = null,
    agentDir: String? = null,
    authStore: Map<String, Any?>? = null,
    prompt: String? = null,
    maxChars: Int? = null,
    maxBytes: Long? = null,
    timeoutMs: Long? = null
): VideoDescriptionResult {
    val p = getMediaUnderstandingProvider(provider)
        ?: throw IllegalStateException("Media understanding provider not found: $provider")

    if (MediaUnderstandingCapability.VIDEO !in p.capabilities) {
        throw IllegalStateException("Provider $provider does not support video description")
    }

    return p.describeVideo(
        VideoDescriptionRequest(
            provider = provider,
            model = model,
            filePath = filePath,
            cfg = cfg,
            agentDir = agentDir,
            authStore = authStore,
            prompt = resolvePrompt(MediaUnderstandingCapability.VIDEO, prompt),
            maxChars = resolveMaxChars(MediaUnderstandingCapability.VIDEO, maxChars),
            maxBytes = resolveMaxBytes(maxBytes),
            timeoutMs = resolveTimeoutMs(timeoutMs)
        )
    )
}

/**
 * Transcribe an audio file using a registered provider.
 */
suspend fun transcribeAudioFile(
    filePath: String,
    provider: String,
    model: String,
    cfg: OpenClawConfig? = null,
    agentDir: String? = null,
    authStore: Map<String, Any?>? = null,
    prompt: String? = null,
    language: String? = null,
    maxChars: Int? = null,
    maxBytes: Long? = null,
    timeoutMs: Long? = null
): AudioTranscriptionResult {
    val p = getMediaUnderstandingProvider(provider)
        ?: throw IllegalStateException("Media understanding provider not found: $provider")

    if (MediaUnderstandingCapability.AUDIO !in p.capabilities) {
        throw IllegalStateException("Provider $provider does not support audio transcription")
    }

    return p.transcribeAudio(
        AudioTranscriptionRequest(
            provider = provider,
            model = model,
            filePath = filePath,
            cfg = cfg,
            agentDir = agentDir,
            authStore = authStore,
            language = language,
            prompt = resolvePrompt(MediaUnderstandingCapability.AUDIO, prompt),
            maxChars = resolveMaxChars(MediaUnderstandingCapability.AUDIO, maxChars),
            maxBytes = resolveMaxBytes(maxBytes),
            timeoutMs = resolveTimeoutMs(timeoutMs)
        )
    )
}

// ---------------------------------------------------------------------------
// Generic dispatch
// ---------------------------------------------------------------------------

/**
 * Run media understanding on a single file.
 *
 * Dispatches to the appropriate provider method based on [MediaUnderstandingFileParams.capability].
 * Wraps the result in [RunMediaUnderstandingFileResult]; errors are caught and returned
 * in the result rather than thrown.
 */
suspend fun runMediaUnderstandingFile(
    params: MediaUnderstandingFileParams
): RunMediaUnderstandingFileResult {
    return try {
        val output = when (params.capability) {
            MediaUnderstandingCapability.IMAGE -> {
                val result = describeImageFile(
                    filePath = params.filePath,
                    provider = params.provider,
                    model = params.model,
                    cfg = params.cfg,
                    agentDir = params.agentDir,
                    authStore = params.authStore,
                    prompt = params.prompt,
                    maxChars = params.maxChars,
                    maxBytes = params.maxBytes,
                    timeoutMs = params.timeoutMs
                )
                MediaUnderstandingOutput(
                    kind = MediaUnderstandingKind.IMAGE_DESCRIPTION,
                    attachmentIndex = params.attachmentIndex,
                    text = result.text,
                    provider = result.provider,
                    model = result.model
                )
            }

            MediaUnderstandingCapability.VIDEO -> {
                val result = describeVideoFile(
                    filePath = params.filePath,
                    provider = params.provider,
                    model = params.model,
                    cfg = params.cfg,
                    agentDir = params.agentDir,
                    authStore = params.authStore,
                    prompt = params.prompt,
                    maxChars = params.maxChars,
                    maxBytes = params.maxBytes,
                    timeoutMs = params.timeoutMs
                )
                MediaUnderstandingOutput(
                    kind = MediaUnderstandingKind.VIDEO_DESCRIPTION,
                    attachmentIndex = params.attachmentIndex,
                    text = result.text,
                    provider = result.provider,
                    model = result.model
                )
            }

            MediaUnderstandingCapability.AUDIO -> {
                val result = transcribeAudioFile(
                    filePath = params.filePath,
                    provider = params.provider,
                    model = params.model,
                    cfg = params.cfg,
                    agentDir = params.agentDir,
                    authStore = params.authStore,
                    prompt = params.prompt,
                    maxChars = params.maxChars,
                    maxBytes = params.maxBytes,
                    timeoutMs = params.timeoutMs
                )
                MediaUnderstandingOutput(
                    kind = MediaUnderstandingKind.AUDIO_TRANSCRIPTION,
                    attachmentIndex = params.attachmentIndex,
                    text = result.text,
                    provider = result.provider,
                    model = result.model
                )
            }
        }

        RunMediaUnderstandingFileResult(output = output)
    } catch (e: Throwable) {
        RunMediaUnderstandingFileResult(output = null, error = e)
    }
}
