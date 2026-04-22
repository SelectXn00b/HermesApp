/**
 * Transcription Tools Module — speech-to-text.
 *
 * Python ships three backends (local faster-whisper, Groq Whisper,
 * OpenAI Whisper, Mistral Voxtral). None of those run on Android, so
 * the top-level surface is stubbed to return an error dict matching
 * Python shape. Shape mirrors tools/transcription_tools.py so
 * registration stays aligned.
 *
 * Ported from tools/transcription_tools.py
 */
package com.xiaomo.hermes.hermes.tools

private const val DEFAULT_PROVIDER: String = "local"
const val DEFAULT_LOCAL_MODEL: String = "base"
const val DEFAULT_LOCAL_STT_LANGUAGE: String = "en"
val DEFAULT_STT_MODEL: String = System.getenv("STT_OPENAI_MODEL") ?: "whisper-1"
val DEFAULT_GROQ_STT_MODEL: String = System.getenv("STT_GROQ_MODEL") ?: "whisper-large-v3-turbo"
val DEFAULT_MISTRAL_STT_MODEL: String = System.getenv("STT_MISTRAL_MODEL") ?: "voxtral-mini-latest"
const val LOCAL_STT_COMMAND_ENV: String = "HERMES_LOCAL_STT_COMMAND"
const val LOCAL_STT_LANGUAGE_ENV: String = "HERMES_LOCAL_STT_LANGUAGE"
val COMMON_LOCAL_BIN_DIRS: List<String> = listOf("/opt/homebrew/bin", "/usr/local/bin")

val GROQ_BASE_URL: String = System.getenv("GROQ_BASE_URL") ?: "https://api.groq.com/openai/v1"
val OPENAI_BASE_URL: String = System.getenv("STT_OPENAI_BASE_URL") ?: "https://api.openai.com/v1"

val SUPPORTED_FORMATS: Set<String> = setOf(".mp3", ".mp4", ".mpeg", ".mpga", ".m4a", ".wav", ".webm", ".ogg", ".aac", ".flac")
val LOCAL_NATIVE_AUDIO_FORMATS: Set<String> = setOf(".wav", ".aiff", ".aif")
const val MAX_FILE_SIZE: Long = 25L * 1024 * 1024

val OPENAI_MODELS: Set<String> = setOf("whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe")
val GROQ_MODELS: Set<String> = setOf("whisper-large-v3", "whisper-large-v3-turbo", "distil-whisper-large-v3-en")

private var _localModel: Any? = null
private var _localModelName: String? = null

private fun _loadSttConfig(): Map<String, Any?> = emptyMap()

fun isSttEnabled(sttConfig: Map<String, Any?>? = null): Boolean {
    val cfg = sttConfig ?: _loadSttConfig()
    val enabled = cfg["enabled"]
    return when (enabled) {
        null -> true
        is Boolean -> enabled
        is String -> enabled.lowercase() in setOf("1", "true", "yes", "on")
        else -> true
    }
}

private fun _hasOpenaiAudioBackend(): Boolean = false

private fun _findBinary(binaryName: String): String? = null

private fun _findFfmpegBinary(): String? = _findBinary("ffmpeg")

private fun _findWhisperBinary(): String? = _findBinary("whisper")

private fun _getLocalCommandTemplate(): String? = null

private fun _hasLocalCommand(): Boolean = _getLocalCommandTemplate() != null

private fun _normalizeLocalModel(modelName: String?): String {
    if (modelName.isNullOrEmpty() || modelName in OPENAI_MODELS || modelName in GROQ_MODELS) {
        return DEFAULT_LOCAL_MODEL
    }
    return modelName
}

private fun _normalizeLocalCommandModel(modelName: String?): String = _normalizeLocalModel(modelName)

private fun _getProvider(sttConfig: Map<String, Any?>): String {
    if (!isSttEnabled(sttConfig)) return "none"
    return "none"
}

private fun _validateAudioFile(filePath: String): Map<String, Any?>? {
    val file = java.io.File(filePath)
    if (!file.exists()) return mapOf("success" to false, "transcript" to "", "error" to "Audio file not found: $filePath")
    if (!file.isFile) return mapOf("success" to false, "transcript" to "", "error" to "Path is not a file: $filePath")
    val suffix = "." + file.extension.lowercase()
    if (suffix !in SUPPORTED_FORMATS) {
        return mapOf(
            "success" to false,
            "transcript" to "",
            "error" to "Unsupported format: $suffix. Supported: ${SUPPORTED_FORMATS.sorted().joinToString(", ")}"
        )
    }
    val size = file.length()
    if (size > MAX_FILE_SIZE) {
        return mapOf(
            "success" to false,
            "transcript" to "",
            "error" to "File too large: ${"%.1f".format(size / (1024.0 * 1024.0))}MB (max ${MAX_FILE_SIZE / (1024 * 1024)}MB)"
        )
    }
    return null
}

private fun _transcribeLocal(filePath: String, modelName: String): Map<String, Any?> =
    mapOf("success" to false, "transcript" to "", "error" to "faster-whisper not installed")

private fun _prepareLocalAudio(filePath: String, workDir: String): Pair<String?, String?> =
    null to "Local STT fallback requires ffmpeg for non-WAV inputs, but ffmpeg was not found"

private fun _transcribeLocalCommand(filePath: String, modelName: String): Map<String, Any?> =
    mapOf(
        "success" to false,
        "transcript" to "",
        "error" to "$LOCAL_STT_COMMAND_ENV not configured and no local whisper binary was found",
    )

private fun _transcribeGroq(filePath: String, modelName: String): Map<String, Any?> =
    mapOf("success" to false, "transcript" to "", "error" to "GROQ_API_KEY not set")

private fun _transcribeOpenai(filePath: String, modelName: String): Map<String, Any?> =
    mapOf("success" to false, "transcript" to "", "error" to "openai package not installed")

private fun _transcribeMistral(filePath: String, modelName: String): Map<String, Any?> =
    mapOf("success" to false, "transcript" to "", "error" to "MISTRAL_API_KEY not set")

fun transcribeAudio(filePath: String, model: String? = null): Map<String, Any?> {
    val error = _validateAudioFile(filePath)
    if (error != null) return error
    val sttConfig = _loadSttConfig()
    if (!isSttEnabled(sttConfig)) {
        return mapOf(
            "success" to false,
            "transcript" to "",
            "error" to "STT is disabled in config.yaml (stt.enabled: false).",
        )
    }
    return mapOf(
        "success" to false,
        "transcript" to "",
        "error" to (
            "No STT provider available. Install faster-whisper for free local " +
                "transcription, configure $LOCAL_STT_COMMAND_ENV or install a local whisper CLI, " +
                "set GROQ_API_KEY for free Groq Whisper, set MISTRAL_API_KEY for Mistral " +
                "Voxtral Transcribe, or set VOICE_TOOLS_OPENAI_KEY " +
                "or OPENAI_API_KEY for the OpenAI Whisper API."
        ),
    )
}

private fun _resolveOpenaiAudioClientConfig(): Pair<String, String> =
    throw IllegalStateException("OpenAI audio backend not configured on Android")

private fun _extractTranscriptText(transcription: Any?): String = transcription?.toString()?.trim() ?: ""
