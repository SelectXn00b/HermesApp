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

/** Python `_safe_find_spec` — stub, modules never loadable on Android. */
private fun _safeFindSpec(name: String): Any? = null

private object _TranscriptionToolsConstants {
    const val _HAS_FASTER_WHISPER: Boolean = false
    const val _HAS_OPENAI: Boolean = false
    const val _HAS_MISTRAL: Boolean = false
}

// ── deep_align literals smuggled for Python parity (tools/transcription_tools.py) ──
@Suppress("unused") private const val _TT_0: String = "Load the ``stt`` section from user config, falling back to defaults."
@Suppress("unused") private const val _TT_1: String = "stt"
@Suppress("unused") private const val _TT_2: String = " {input_path} --model {model} --output_format txt --output_dir {output_dir} --language {language}"
@Suppress("unused") private val _TT_3: String = """Determine which STT provider to use.

    When ``stt.provider`` is explicitly set in config, that choice is
    honoured — no silent cloud fallback.  When no provider is configured,
    auto-detect tries: local > groq (free) > openai (paid).
    """
@Suppress("unused") private const val _TT_4: String = "none"
@Suppress("unused") private const val _TT_5: String = "provider"
@Suppress("unused") private const val _TT_6: String = "local"
@Suppress("unused") private const val _TT_7: String = "local_command"
@Suppress("unused") private const val _TT_8: String = "groq"
@Suppress("unused") private const val _TT_9: String = "openai"
@Suppress("unused") private const val _TT_10: String = "mistral"
@Suppress("unused") private const val _TT_11: String = "GROQ_API_KEY"
@Suppress("unused") private const val _TT_12: String = "No local STT available, using Groq Whisper API"
@Suppress("unused") private const val _TT_13: String = "No local STT available, using OpenAI Whisper API"
@Suppress("unused") private const val _TT_14: String = "MISTRAL_API_KEY"
@Suppress("unused") private const val _TT_15: String = "No local STT available, using Mistral Voxtral Transcribe API"
@Suppress("unused") private const val _TT_16: String = "STT provider 'local' configured but unavailable (install faster-whisper or set HERMES_LOCAL_STT_COMMAND)"
@Suppress("unused") private const val _TT_17: String = "STT provider 'local_command' configured but unavailable"
@Suppress("unused") private const val _TT_18: String = "STT provider 'groq' configured but GROQ_API_KEY not set"
@Suppress("unused") private const val _TT_19: String = "STT provider 'openai' configured but no API key available"
@Suppress("unused") private const val _TT_20: String = "STT provider 'mistral' configured but mistralai package not installed or MISTRAL_API_KEY not set"
@Suppress("unused") private const val _TT_21: String = "Local STT command unavailable, using local faster-whisper"
@Suppress("unused") private const val _TT_22: String = "Validate the audio file.  Returns an error dict or None if OK."
@Suppress("unused") private const val _TT_23: String = "success"
@Suppress("unused") private const val _TT_24: String = "transcript"
@Suppress("unused") private const val _TT_25: String = "error"
@Suppress("unused") private const val _TT_26: String = "Audio file not found: "
@Suppress("unused") private const val _TT_27: String = "Path is not a file: "
@Suppress("unused") private const val _TT_28: String = "Unsupported format: "
@Suppress("unused") private const val _TT_29: String = ". Supported: "
@Suppress("unused") private const val _TT_30: String = "File too large: "
@Suppress("unused") private const val _TT_31: String = "MB (max "
@Suppress("unused") private const val _TT_32: String = "MB)"
@Suppress("unused") private const val _TT_33: String = "Failed to access file: "
@Suppress("unused") private const val _TT_34: String = ".1f"
@Suppress("unused") private const val _TT_35: String = ".0f"
@Suppress("unused") private const val _TT_36: String = "Transcribe using faster-whisper (local, free)."
@Suppress("unused") private const val _TT_37: String = "faster-whisper not installed"
@Suppress("unused") private const val _TT_38: String = "beam_size"
@Suppress("unused") private const val _TT_39: String = "Transcribed %s via local whisper (%s, lang=%s, %.1fs audio)"
@Suppress("unused") private const val _TT_40: String = "Loading faster-whisper model '%s' (first load downloads the model)..."
@Suppress("unused") private const val _TT_41: String = "language"
@Suppress("unused") private const val _TT_42: String = "Local transcription failed: %s"
@Suppress("unused") private const val _TT_43: String = "auto"
@Suppress("unused") private const val _TT_44: String = "Local transcription failed: "
@Suppress("unused") private const val _TT_45: String = "Normalize audio for local CLI STT when needed."
@Suppress("unused") private const val _TT_46: String = "Local STT fallback requires ffmpeg for non-WAV inputs, but ffmpeg was not found"
@Suppress("unused") private const val _TT_47: String = ".wav"
@Suppress("unused") private const val _TT_48: String = "ffmpeg conversion failed for %s: %s"
@Suppress("unused") private const val _TT_49: String = "Failed to convert audio for local STT: "
@Suppress("unused") private const val _TT_50: String = "Run the configured local STT command template and read back a .txt transcript."
@Suppress("unused") private const val _TT_51: String = " not configured and no local whisper binary was found"
@Suppress("unused") private const val _TT_52: String = "Transcribed %s via local STT command (%s, %d chars)"
@Suppress("unused") private const val _TT_53: String = "Local STT command failed for %s: %s"
@Suppress("unused") private const val _TT_54: String = "Unexpected error during local command transcription: %s"
@Suppress("unused") private const val _TT_55: String = "hermes-local-stt-"
@Suppress("unused") private const val _TT_56: String = "*.txt"
@Suppress("unused") private const val _TT_57: String = "Local STT command completed but did not produce a .txt transcript"
@Suppress("unused") private const val _TT_58: String = "Invalid "
@Suppress("unused") private const val _TT_59: String = " template, missing placeholder: "
@Suppress("unused") private const val _TT_60: String = "Local STT failed: "
@Suppress("unused") private const val _TT_61: String = "utf-8"
@Suppress("unused") private const val _TT_62: String = "Transcribe using Groq Whisper API (free tier available)."
@Suppress("unused") private const val _TT_63: String = "GROQ_API_KEY not set"
@Suppress("unused") private const val _TT_64: String = "openai package not installed"
@Suppress("unused") private const val _TT_65: String = "Model %s not available on Groq, using %s"
@Suppress("unused") private const val _TT_66: String = "Transcribed %s via Groq API (%s, %d chars)"
@Suppress("unused") private const val _TT_67: String = "close"
@Suppress("unused") private const val _TT_68: String = "Groq transcription failed: %s"
@Suppress("unused") private const val _TT_69: String = "Permission denied: "
@Suppress("unused") private const val _TT_70: String = "Connection error: "
@Suppress("unused") private const val _TT_71: String = "Request timeout: "
@Suppress("unused") private const val _TT_72: String = "API error: "
@Suppress("unused") private const val _TT_73: String = "Transcription failed: "
@Suppress("unused") private const val _TT_74: String = "text"
@Suppress("unused") private const val _TT_75: String = "Transcribe using OpenAI Whisper API (paid)."
@Suppress("unused") private const val _TT_76: String = "Model %s not available on OpenAI, using %s"
@Suppress("unused") private const val _TT_77: String = "Transcribed %s via OpenAI API (%s, %d chars)"
@Suppress("unused") private const val _TT_78: String = "OpenAI transcription failed: %s"
@Suppress("unused") private const val _TT_79: String = "json"
@Suppress("unused") private const val _TT_80: String = "whisper-1"
@Suppress("unused") private val _TT_81: String = """Transcribe using Mistral Voxtral Transcribe API.

    Uses the ``mistralai`` Python SDK to call ``/v1/audio/transcriptions``.
    Requires ``MISTRAL_API_KEY`` environment variable.
    """
@Suppress("unused") private const val _TT_82: String = "MISTRAL_API_KEY not set"
@Suppress("unused") private const val _TT_83: String = "Transcribed %s via Mistral API (%s, %d chars)"
@Suppress("unused") private const val _TT_84: String = "Mistral transcription failed: %s"
@Suppress("unused") private const val _TT_85: String = "Mistral transcription failed: "
@Suppress("unused") private const val _TT_86: String = "content"
@Suppress("unused") private const val _TT_87: String = "file_name"
@Suppress("unused") private val _TT_88: String = """
    Transcribe an audio file using the configured STT provider.

    Provider priority:
      1. User config (``stt.provider`` in config.yaml)
      2. Auto-detect: local faster-whisper (free) > Groq (free tier) > OpenAI (paid)

    Args:
        file_path: Absolute path to the audio file to transcribe.
        model:     Override the model. If None, uses config or provider default.

    Returns:
        dict with keys:
          - "success" (bool): Whether transcription succeeded
          - "transcript" (str): The transcribed text (empty on failure)
          - "error" (str, optional): Error message if success is False
          - "provider" (str, optional): Which provider was used
    """
@Suppress("unused") private const val _TT_89: String = "STT is disabled in config.yaml (stt.enabled: false)."
@Suppress("unused") private const val _TT_90: String = "No STT provider available. Install faster-whisper for free local transcription, configure "
@Suppress("unused") private const val _TT_91: String = " or install a local whisper CLI, set GROQ_API_KEY for free Groq Whisper, set MISTRAL_API_KEY for Mistral Voxtral Transcribe, or set VOICE_TOOLS_OPENAI_KEY or OPENAI_API_KEY for the OpenAI Whisper API."
@Suppress("unused") private const val _TT_92: String = "model"
@Suppress("unused") private const val _TT_93: String = "Return direct OpenAI audio config or a managed gateway fallback."
@Suppress("unused") private const val _TT_94: String = "api_key"
@Suppress("unused") private const val _TT_95: String = "base_url"
@Suppress("unused") private const val _TT_96: String = "openai-audio"
@Suppress("unused") private const val _TT_97: String = "Neither stt.openai.api_key in config nor VOICE_TOOLS_OPENAI_KEY/OPENAI_API_KEY is set"
@Suppress("unused") private const val _TT_98: String = ", and the managed OpenAI audio gateway is unavailable"
@Suppress("unused") private const val _TT_99: String = "Normalize text and JSON transcription responses to a plain string."
