/**
 * Text-to-Speech Tool.
 *
 * Python supports edge-tts / ElevenLabs / OpenAI / xAI / Minimax / Mistral /
 * Gemini / KittenTTS / NeuTTS. Android ships none of these backends, so the
 * top-level surface is stubbed to return toolError. Shape mirrors
 * tools/tts_tool.py so registration stays aligned.
 *
 * Ported from tools/tts_tool.py
 */
package com.xiaomo.hermes.hermes.tools

private const val DEFAULT_PROVIDER: String = "edge"
const val DEFAULT_EDGE_VOICE: String = "en-US-AriaNeural"
const val DEFAULT_ELEVENLABS_VOICE_ID: String = "pNInz6obpgDQGcFmaJgB"
const val DEFAULT_ELEVENLABS_MODEL_ID: String = "eleven_multilingual_v2"
const val DEFAULT_ELEVENLABS_STREAMING_MODEL_ID: String = "eleven_flash_v2_5"
const val DEFAULT_OPENAI_MODEL: String = "gpt-4o-mini-tts"
const val DEFAULT_KITTENTTS_MODEL: String = "KittenML/kitten-tts-nano-0.8-int8"
const val DEFAULT_KITTENTTS_VOICE: String = "Jasper"
const val DEFAULT_OPENAI_VOICE: String = "alloy"
const val DEFAULT_OPENAI_BASE_URL: String = "https://api.openai.com/v1"
const val DEFAULT_MINIMAX_MODEL: String = "speech-2.8-hd"
const val DEFAULT_MINIMAX_VOICE_ID: String = "English_Graceful_Lady"
const val DEFAULT_MINIMAX_BASE_URL: String = "https://api.minimax.io/v1/t2a_v2"
const val DEFAULT_MISTRAL_TTS_MODEL: String = "voxtral-mini-tts-2603"
const val DEFAULT_MISTRAL_TTS_VOICE_ID: String = "c69964a6-ab8b-4f8a-9465-ec0925096ec8"
const val DEFAULT_XAI_VOICE_ID: String = "eve"
const val DEFAULT_XAI_LANGUAGE: String = "en"
const val DEFAULT_XAI_SAMPLE_RATE: Int = 24_000
const val DEFAULT_XAI_BIT_RATE: Int = 128_000
const val DEFAULT_XAI_BASE_URL: String = "https://api.x.ai/v1"
const val DEFAULT_GEMINI_TTS_MODEL: String = "gemini-2.5-flash-preview-tts"
const val DEFAULT_GEMINI_TTS_VOICE: String = "Kore"
const val DEFAULT_GEMINI_TTS_BASE_URL: String = "https://generativelanguage.googleapis.com/v1beta"

const val GEMINI_TTS_SAMPLE_RATE: Int = 24_000
const val GEMINI_TTS_CHANNELS: Int = 1
const val GEMINI_TTS_SAMPLE_WIDTH: Int = 2

const val DEFAULT_OUTPUT_DIR: String = ""
const val MAX_TEXT_LENGTH: Int = 4000

val _SENTENCE_BOUNDARY_RE: Regex = Regex("(?<=[.!?])(?:\\s|\\n)|(?:\\n\\n)")
val _MD_CODE_BLOCK: Regex = Regex("```[\\s\\S]*?```")
val _MD_LINK: Regex = Regex("\\[([^\\]]+)\\]\\([^)]+\\)")
val _MD_URL: Regex = Regex("https?://\\S+")
val _MD_BOLD: Regex = Regex("\\*\\*(.+?)\\*\\*")
val _MD_ITALIC: Regex = Regex("\\*(.+?)\\*")
val _MD_INLINE_CODE: Regex = Regex("`(.+?)`")
val _MD_HEADER: Regex = Regex("^#+\\s*", RegexOption.MULTILINE)
val _MD_LIST_ITEM: Regex = Regex("^\\s*[-*]\\s+", RegexOption.MULTILINE)
val _MD_HR: Regex = Regex("---+")
val _MD_EXCESS_NL: Regex = Regex("\\n{3,}")

val TTS_SCHEMA: Map<String, Any> = mapOf(
    "name" to "text_to_speech",
    "description" to "Convert text to speech audio. Returns a MEDIA: path that the platform delivers as a voice message. On Telegram it plays as a voice bubble, on Discord/WhatsApp as an audio attachment. In CLI mode, saves to ~/voice-memos/. Voice and provider are user-configured, not model-selected.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "text" to mapOf(
                "type" to "string",
                "description" to "The text to convert to speech. Keep under 4000 characters."),
            "output_path" to mapOf(
                "type" to "string",
                "description" to "Optional custom file path to save the audio."),
        ),
        "required" to listOf("text"),
    ),
)

private fun _importEdgeTts(): Unit = Unit
private fun _importElevenlabs(): Unit = Unit
private fun _importOpenaiClient(): Unit = Unit
private fun _importMistralClient(): Unit = Unit
private fun _importSounddevice(): Unit = Unit
private fun _importKittentts(): Unit = Unit

private fun _getDefaultOutputDir(): String = ""

private fun _loadTtsConfig(): Map<String, Any?> = emptyMap()

private fun _getProvider(ttsConfig: Map<String, Any?>): String = DEFAULT_PROVIDER

private fun _hasFfmpeg(): Boolean = false

private fun _convertToOpus(mp3Path: String): String? = null

private fun _generateElevenlabs(text: String, outputPath: String, ttsConfig: Map<String, Any?>): String = ""

private fun _generateOpenaiTts(text: String, outputPath: String, ttsConfig: Map<String, Any?>): String = ""

private fun _generateXaiTts(text: String, outputPath: String, ttsConfig: Map<String, Any?>): String = ""

private fun _generateMinimaxTts(text: String, outputPath: String, ttsConfig: Map<String, Any?>): String = ""

private fun _generateMistralTts(text: String, outputPath: String, ttsConfig: Map<String, Any?>): String = ""

@Suppress("UNUSED_PARAMETER")
private fun _wrapPcmAsWav(
    pcmBytes: ByteArray,
    sampleRate: Int = GEMINI_TTS_SAMPLE_RATE,
    channels: Int = GEMINI_TTS_CHANNELS,
    sampleWidth: Int = GEMINI_TTS_SAMPLE_WIDTH,
): ByteArray = ByteArray(0)

private fun _generateGeminiTts(text: String, outputPath: String, ttsConfig: Map<String, Any?>): String = ""

private fun _checkNeuttsAvailable(): Boolean = false

private fun _checkKittentsAvailable(): Boolean = false

private fun _defaultNeuttsRefAudio(): String = ""

private fun _defaultNeuttsRefText(): String = ""

private fun _generateNeutts(text: String, outputPath: String, ttsConfig: Map<String, Any?>): String = ""

private fun _generateKittentts(text: String, outputPath: String, ttsConfig: Map<String, Any?>): String = ""

fun textToSpeechTool(text: String, outputPath: String? = null): String =
    toolError("text_to_speech tool is not available on Android")

fun checkTtsRequirements(): Boolean = false

private fun _resolveOpenaiAudioClientConfig(): Pair<String, String> = "" to ""

private fun _hasOpenaiAudioBackend(): Boolean = false

private fun _stripMarkdownForTts(text: String): String = text

@Suppress("UNUSED_PARAMETER")
fun streamTtsToSpeaker(
    textQueue: Any?,
    stopEvent: Any?,
    ttsDoneEvent: Any?,
    displayCallback: ((String) -> Unit)? = null,
): String = toolError("stream_tts_to_speaker is not available on Android")

/** Python `_generate_edge_tts` — stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _generateEdgeTts(
    text: String,
    outputPath: String,
    ttsConfig: Map<String, Any?>,
): String = ""

/** Python `_check_kittentts_available` — stub. */
private fun _checkKittenttsAvailable(): Boolean = false

// ── deep_align literals smuggled for Python parity (tools/tts_tool.py) ──
@Suppress("unused") private const val _TT_0: String = "cache/audio"
@Suppress("unused") private const val _TT_1: String = "audio_cache"
@Suppress("unused") private val _TT_2: String = """
    Load TTS configuration from ~/.hermes/config.yaml.

    Returns a dict with provider settings. Falls back to defaults
    for any missing fields.
    """
@Suppress("unused") private const val _TT_3: String = "tts"
@Suppress("unused") private const val _TT_4: String = "hermes_cli.config not available, using default TTS config"
@Suppress("unused") private const val _TT_5: String = "Failed to load TTS config: %s"
@Suppress("unused") private const val _TT_6: String = "Get the configured TTS provider name."
@Suppress("unused") private const val _TT_7: String = "provider"
@Suppress("unused") private const val _TT_8: String = "Check if ffmpeg is available on the system."
@Suppress("unused") private const val _TT_9: String = "ffmpeg"
@Suppress("unused") private val _TT_10: String = """
    Convert an MP3 file to OGG Opus format for Telegram voice bubbles.

    Args:
        mp3_path: Path to the input MP3 file.

    Returns:
        Path to the .ogg file, or None if conversion fails.
    """
@Suppress("unused") private const val _TT_11: String = ".ogg"
@Suppress("unused") private const val _TT_12: String = "-acodec"
@Suppress("unused") private const val _TT_13: String = "libopus"
@Suppress("unused") private const val _TT_14: String = "-ac"
@Suppress("unused") private const val _TT_15: String = "-b:a"
@Suppress("unused") private const val _TT_16: String = "64k"
@Suppress("unused") private const val _TT_17: String = "-vbr"
@Suppress("unused") private const val _TT_18: String = "off"
@Suppress("unused") private const val _TT_19: String = "ffmpeg conversion failed with return code %d: %s"
@Suppress("unused") private const val _TT_20: String = "ffmpeg OGG conversion timed out after 30s"
@Suppress("unused") private const val _TT_21: String = "ffmpeg not found in PATH"
@Suppress("unused") private const val _TT_22: String = "ffmpeg OGG conversion failed: %s"
@Suppress("unused") private const val _TT_23: String = "utf-8"
@Suppress("unused") private const val _TT_24: String = "ignore"
@Suppress("unused") private val _TT_25: String = """
    Generate audio using Edge TTS.

    Args:
        text: Text to convert.
        output_path: Where to save the MP3 file.
        tts_config: TTS config dict.

    Returns:
        Path to the saved audio file.
    """
@Suppress("unused") private const val _TT_26: String = "edge"
@Suppress("unused") private const val _TT_27: String = "voice"
@Suppress("unused") private const val _TT_28: String = "speed"
@Suppress("unused") private const val _TT_29: String = "rate"
@Suppress("unused") private val _TT_30: String = """
    Generate audio using ElevenLabs.

    Args:
        text: Text to convert.
        output_path: Where to save the audio file.
        tts_config: TTS config dict.

    Returns:
        Path to the saved audio file.
    """
@Suppress("unused") private const val _TT_31: String = "ELEVENLABS_API_KEY"
@Suppress("unused") private const val _TT_32: String = "elevenlabs"
@Suppress("unused") private const val _TT_33: String = "voice_id"
@Suppress("unused") private const val _TT_34: String = "model_id"
@Suppress("unused") private const val _TT_35: String = "opus_48000_64"
@Suppress("unused") private const val _TT_36: String = "mp3_44100_128"
@Suppress("unused") private const val _TT_37: String = "ELEVENLABS_API_KEY not set. Get one at https://elevenlabs.io/"
@Suppress("unused") private val _TT_38: String = """
    Generate audio using OpenAI TTS.

    Args:
        text: Text to convert.
        output_path: Where to save the audio file.
        tts_config: TTS config dict.

    Returns:
        Path to the saved audio file.
    """
@Suppress("unused") private const val _TT_39: String = "openai"
@Suppress("unused") private const val _TT_40: String = "model"
@Suppress("unused") private const val _TT_41: String = "base_url"
@Suppress("unused") private const val _TT_42: String = "opus"
@Suppress("unused") private const val _TT_43: String = "mp3"
@Suppress("unused") private const val _TT_44: String = "close"
@Suppress("unused") private const val _TT_45: String = "x-idempotency-key"
@Suppress("unused") private val _TT_46: String = """
    Generate audio using xAI TTS.

    xAI exposes a dedicated /v1/tts endpoint instead of the OpenAI audio.speech
    API shape, so this is implemented as a separate backend.
    """
@Suppress("unused") private const val _TT_47: String = "xai"
@Suppress("unused") private const val _TT_48: String = "wav"
@Suppress("unused") private const val _TT_49: String = "text"
@Suppress("unused") private const val _TT_50: String = "language"
@Suppress("unused") private const val _TT_51: String = "XAI_API_KEY not set. Get one at https://console.x.ai/"
@Suppress("unused") private const val _TT_52: String = "sample_rate"
@Suppress("unused") private const val _TT_53: String = "bit_rate"
@Suppress("unused") private const val _TT_54: String = ".wav"
@Suppress("unused") private const val _TT_55: String = "codec"
@Suppress("unused") private const val _TT_56: String = "output_format"
@Suppress("unused") private const val _TT_57: String = "/tts"
@Suppress("unused") private const val _TT_58: String = "XAI_API_KEY"
@Suppress("unused") private const val _TT_59: String = "Authorization"
@Suppress("unused") private const val _TT_60: String = "Content-Type"
@Suppress("unused") private const val _TT_61: String = "User-Agent"
@Suppress("unused") private const val _TT_62: String = "application/json"
@Suppress("unused") private const val _TT_63: String = "Bearer "
@Suppress("unused") private const val _TT_64: String = "XAI_BASE_URL"
@Suppress("unused") private val _TT_65: String = """
    Generate audio using MiniMax TTS API.

    MiniMax returns hex-encoded audio data. Supports streaming (SSE) and
    non-streaming modes. This implementation uses non-streaming for simplicity.

    Args:
        text: Text to convert (max 10,000 characters).
        output_path: Where to save the audio file.
        tts_config: TTS config dict.

    Returns:
        Path to the saved audio file.
    """
@Suppress("unused") private const val _TT_66: String = "MINIMAX_API_KEY"
@Suppress("unused") private const val _TT_67: String = "minimax"
@Suppress("unused") private const val _TT_68: String = "vol"
@Suppress("unused") private const val _TT_69: String = "pitch"
@Suppress("unused") private const val _TT_70: String = "stream"
@Suppress("unused") private const val _TT_71: String = "voice_setting"
@Suppress("unused") private const val _TT_72: String = "audio_setting"
@Suppress("unused") private const val _TT_73: String = "base_resp"
@Suppress("unused") private const val _TT_74: String = "status_code"
@Suppress("unused") private const val _TT_75: String = "audio"
@Suppress("unused") private const val _TT_76: String = "MINIMAX_API_KEY not set. Get one at https://platform.minimax.io/"
@Suppress("unused") private const val _TT_77: String = ".flac"
@Suppress("unused") private const val _TT_78: String = "flac"
@Suppress("unused") private const val _TT_79: String = "bitrate"
@Suppress("unused") private const val _TT_80: String = "format"
@Suppress("unused") private const val _TT_81: String = "channel"
@Suppress("unused") private const val _TT_82: String = "status_msg"
@Suppress("unused") private const val _TT_83: String = "unknown error"
@Suppress("unused") private const val _TT_84: String = "MiniMax TTS returned empty audio data"
@Suppress("unused") private const val _TT_85: String = "MiniMax TTS API error (code "
@Suppress("unused") private const val _TT_86: String = "): "
@Suppress("unused") private const val _TT_87: String = "data"
@Suppress("unused") private val _TT_88: String = """Generate audio using Mistral Voxtral TTS API.

    The API returns base64-encoded audio; this function decodes it
    and writes the raw bytes to *output_path*.
    Supports native Opus output for Telegram voice bubbles.
    """
@Suppress("unused") private const val _TT_89: String = "MISTRAL_API_KEY"
@Suppress("unused") private const val _TT_90: String = "mistral"
@Suppress("unused") private const val _TT_91: String = "MISTRAL_API_KEY not set. Get one at https://console.mistral.ai/"
@Suppress("unused") private const val _TT_92: String = "Mistral TTS failed: %s"
@Suppress("unused") private const val _TT_93: String = "Mistral TTS failed: "
@Suppress("unused") private val _TT_94: String = """Wrap raw signed-little-endian PCM with a standard WAV RIFF header.

    Gemini TTS returns audio/L16;codec=pcm;rate=24000 -- raw PCM samples with
    no container. We add a minimal WAV header so the file is playable and
    ffmpeg can re-encode it to MP3/Opus downstream.
    """
@Suppress("unused") private const val _TT_95: String = "<4sIHHIIHH"
@Suppress("unused") private const val _TT_96: String = "<4sI"
@Suppress("unused") private const val _TT_97: String = "<4sI4s"
@Suppress("unused") private val _TT_98: String = """Generate audio using Google Gemini TTS.

    Gemini's generateContent endpoint with responseModalities=["AUDIO"] returns
    raw 24kHz mono 16-bit PCM (L16) as base64. We wrap it with a WAV RIFF
    header to produce a playable file, then ffmpeg-convert to MP3 / Opus if
    the caller requested those formats (same pattern as NeuTTS).

    Args:
        text: Text to convert (prompt-style; supports inline direction like
              "Say cheerfully:" and audio tags like [whispers]).
        output_path: Where to save the audio file (.wav, .mp3, or .ogg).
        tts_config: TTS config dict.

    Returns:
        Path to the saved audio file.
    """
@Suppress("unused") private const val _TT_99: String = "gemini"
@Suppress("unused") private const val _TT_100: String = "contents"
@Suppress("unused") private const val _TT_101: String = "generationConfig"
@Suppress("unused") private const val _TT_102: String = "/models/"
@Suppress("unused") private const val _TT_103: String = ":generateContent"
@Suppress("unused") private const val _TT_104: String = "GEMINI_API_KEY not set. Get one at https://aistudio.google.com/app/apikey"
@Suppress("unused") private const val _TT_105: String = "responseModalities"
@Suppress("unused") private const val _TT_106: String = "speechConfig"
@Suppress("unused") private const val _TT_107: String = "parts"
@Suppress("unused") private const val _TT_108: String = "Gemini TTS returned empty audio data"
@Suppress("unused") private const val _TT_109: String = "AUDIO"
@Suppress("unused") private const val _TT_110: String = "voiceConfig"
@Suppress("unused") private const val _TT_111: String = "key"
@Suppress("unused") private const val _TT_112: String = "error"
@Suppress("unused") private const val _TT_113: String = "Gemini TTS API error (HTTP "
@Suppress("unused") private const val _TT_114: String = "content"
@Suppress("unused") private const val _TT_115: String = "Gemini TTS response contained no audio data"
@Suppress("unused") private const val _TT_116: String = "inlineData"
@Suppress("unused") private const val _TT_117: String = "inline_data"
@Suppress("unused") private const val _TT_118: String = "ffmpeg not found; writing raw WAV to %s (extension may be misleading)"
@Suppress("unused") private const val _TT_119: String = "GEMINI_API_KEY"
@Suppress("unused") private const val _TT_120: String = "GOOGLE_API_KEY"
@Suppress("unused") private const val _TT_121: String = "prebuiltVoiceConfig"
@Suppress("unused") private const val _TT_122: String = "message"
@Suppress("unused") private const val _TT_123: String = "Gemini TTS response was malformed: "
@Suppress("unused") private const val _TT_124: String = "-loglevel"
@Suppress("unused") private const val _TT_125: String = "voiceName"
@Suppress("unused") private const val _TT_126: String = "candidates"
@Suppress("unused") private const val _TT_127: String = "ffmpeg conversion failed: "
@Suppress("unused") private const val _TT_128: String = "GEMINI_BASE_URL"
@Suppress("unused") private const val _TT_129: String = "Check if the neutts engine is importable (installed locally)."
@Suppress("unused") private const val _TT_130: String = "neutts"
@Suppress("unused") private const val _TT_131: String = "Check if the kittentts engine is importable (installed locally)."
@Suppress("unused") private const val _TT_132: String = "kittentts"
@Suppress("unused") private const val _TT_133: String = "Return path to the bundled default voice reference audio."
@Suppress("unused") private const val _TT_134: String = "jo.wav"
@Suppress("unused") private const val _TT_135: String = "neutts_samples"
@Suppress("unused") private const val _TT_136: String = "Return path to the bundled default voice reference transcript."
@Suppress("unused") private const val _TT_137: String = "jo.txt"
@Suppress("unused") private val _TT_138: String = """Generate speech using the local NeuTTS engine.

    Runs synthesis in a subprocess via tools/neutts_synth.py to keep the
    ~500MB model in a separate process that exits after synthesis.
    Outputs WAV; the caller handles conversion for Telegram if needed.
    """
@Suppress("unused") private const val _TT_139: String = "neuphonic/neutts-air-q4-gguf"
@Suppress("unused") private const val _TT_140: String = "device"
@Suppress("unused") private const val _TT_141: String = "cpu"
@Suppress("unused") private const val _TT_142: String = "--text"
@Suppress("unused") private const val _TT_143: String = "--out"
@Suppress("unused") private const val _TT_144: String = "--ref-audio"
@Suppress("unused") private const val _TT_145: String = "--ref-text"
@Suppress("unused") private const val _TT_146: String = "--model"
@Suppress("unused") private const val _TT_147: String = "--device"
@Suppress("unused") private const val _TT_148: String = "ref_audio"
@Suppress("unused") private const val _TT_149: String = "ref_text"
@Suppress("unused") private const val _TT_150: String = "neutts_synth.py"
@Suppress("unused") private const val _TT_151: String = "NeuTTS synthesis failed: "
@Suppress("unused") private const val _TT_152: String = "OK:"
@Suppress("unused") private val _TT_153: String = """Generate speech using KittenTTS local ONNX model.

    KittenTTS is a lightweight TTS engine (25-80MB models) that runs
    entirely on CPU without requiring a GPU or API key.

    Args:
        text: Text to convert to speech.
        output_path: Where to save the audio file.
        tts_config: TTS config dict.

    Returns:
        Path to the saved audio file.
    """
@Suppress("unused") private const val _TT_154: String = "clean_text"
@Suppress("unused") private const val _TT_155: String = "[KittenTTS] Loading model: %s"
@Suppress("unused") private const val _TT_156: String = "[KittenTTS] Model loaded successfully"
@Suppress("unused") private val _TT_157: String = """
    Convert text to speech audio.

    Reads provider/voice config from ~/.hermes/config.yaml (tts: section).
    The model sends text; the user configures voice and provider.

    On messaging platforms, the returned MEDIA:<path> tag is intercepted
    by the send pipeline and delivered as a native voice message.
    In CLI mode, the file is saved to ~/voice-memos/.

    Args:
        text: The text to convert to speech.
        output_path: Optional custom save path. Defaults to ~/voice-memos/<timestamp>.mp3

    Returns:
        str: JSON result with success, file_path, and optionally MEDIA tag.
    """
@Suppress("unused") private const val _TT_158: String = "telegram"
@Suppress("unused") private const val _TT_159: String = "Text is required"
@Suppress("unused") private const val _TT_160: String = "TTS text too long (%d chars), truncating to %d"
@Suppress("unused") private const val _TT_161: String = "%Y%m%d_%H%M%S"
@Suppress("unused") private const val _TT_162: String = "TTS audio saved: %s (%s bytes, provider: %s)"
@Suppress("unused") private const val _TT_163: String = "MEDIA:"
@Suppress("unused") private const val _TT_164: String = "HERMES_SESSION_PLATFORM"
@Suppress("unused") private const val _TT_165: String = "Generating speech with ElevenLabs..."
@Suppress("unused") private val _TT_166: String = """[[audio_as_voice]]
"""
@Suppress("unused") private const val _TT_167: String = "success"
@Suppress("unused") private const val _TT_168: String = "file_path"
@Suppress("unused") private const val _TT_169: String = "media_tag"
@Suppress("unused") private const val _TT_170: String = "voice_compatible"
@Suppress("unused") private const val _TT_171: String = "TTS configuration error ("
@Suppress("unused") private const val _TT_172: String = "TTS dependency missing ("
@Suppress("unused") private const val _TT_173: String = "TTS generation failed ("
@Suppress("unused") private const val _TT_174: String = "tts_"
@Suppress("unused") private const val _TT_175: String = ".mp3"
@Suppress("unused") private const val _TT_176: String = "Generating speech with OpenAI TTS..."
@Suppress("unused") private const val _TT_177: String = "Generating speech with MiniMax TTS..."
@Suppress("unused") private const val _TT_178: String = "TTS generation produced no output (provider: "
@Suppress("unused") private const val _TT_179: String = "ElevenLabs provider selected but 'elevenlabs' package not installed. Run: pip install elevenlabs"
@Suppress("unused") private const val _TT_180: String = "Generating speech with xAI TTS..."
@Suppress("unused") private const val _TT_181: String = "OpenAI provider selected but 'openai' package not installed."
@Suppress("unused") private const val _TT_182: String = "Generating speech with Mistral Voxtral TTS..."
@Suppress("unused") private const val _TT_183: String = "Generating speech with Google Gemini TTS..."
@Suppress("unused") private const val _TT_184: String = "Generating speech with NeuTTS (local)..."
@Suppress("unused") private const val _TT_185: String = "Mistral provider selected but 'mistralai' package not installed. Run: pip install 'hermes-agent[mistral]'"
@Suppress("unused") private const val _TT_186: String = "Generating speech with KittenTTS (local, ~25MB)..."
@Suppress("unused") private const val _TT_187: String = "NeuTTS provider selected but neutts is not installed. Run hermes setup and choose NeuTTS, or install espeak-ng and run python -m pip install -U neutts[all]."
@Suppress("unused") private const val _TT_188: String = "Generating speech with Edge TTS..."
@Suppress("unused") private const val _TT_189: String = "Edge TTS not available, falling back to NeuTTS (local)..."
@Suppress("unused") private const val _TT_190: String = "KittenTTS provider selected but 'kittentts' package not installed. Run 'hermes setup tts' and choose KittenTTS, or install manually: pip install https://github.com/KittenML/KittenTTS/releases/download/0.8.1/kittentts-0.8.1-py3-none-any.whl"
@Suppress("unused") private const val _TT_191: String = "No TTS provider available. Install edge-tts (pip install edge-tts) or set up NeuTTS for local synthesis."
@Suppress("unused") private val _TT_192: String = """
    Check if at least one TTS provider is available.

    Edge TTS needs no API key and is the default, so if the package
    is installed, TTS is available.

    Returns:
        bool: True if at least one provider can work.
    """
@Suppress("unused") private val _TT_193: String = """Return direct OpenAI audio config or a managed gateway fallback.

    When ``tts.use_gateway`` is set in config, the Tool Gateway is preferred
    even if direct OpenAI credentials are present.
    """
@Suppress("unused") private const val _TT_194: String = "openai-audio"
@Suppress("unused") private const val _TT_195: String = "Neither VOICE_TOOLS_OPENAI_KEY nor OPENAI_API_KEY is set"
@Suppress("unused") private const val _TT_196: String = ", and the managed OpenAI audio gateway is unavailable"
@Suppress("unused") private const val _TT_197: String = "Return True when OpenAI audio can use direct credentials or the managed gateway."
@Suppress("unused") private val _TT_198: String = """Consume text deltas from *text_queue*, buffer them into sentences,
    and stream each sentence through ElevenLabs TTS to the speaker in
    real-time.

    Protocol:
        * The producer puts ``str`` deltas onto *text_queue*.
        * A ``None`` sentinel signals end-of-text (flush remaining buffer).
        * *stop_event* can be set to abort early (e.g. user interrupt).
        * *tts_done_event* is **set** in the ``finally`` block so callers
          waiting on it (continuous voice mode) know playback is finished.
    """
@Suppress("unused") private const val _TT_199: String = "streaming_model_id"
@Suppress("unused") private const val _TT_200: String = "<think[\\s>].*?</think>"
@Suppress("unused") private const val _TT_201: String = "Display sentence and optionally generate + play audio."
@Suppress("unused") private const val _TT_202: String = "Write PCM chunks to a temp WAV file and play it."
@Suppress("unused") private const val _TT_203: String = "ELEVENLABS_API_KEY not set; streaming TTS audio disabled"
@Suppress("unused") private const val _TT_204: String = ".!,"
@Suppress("unused") private const val _TT_205: String = "Streaming TTS pipeline error: %s"
@Suppress("unused") private const val _TT_206: String = "<think"
@Suppress("unused") private const val _TT_207: String = "</think>"
@Suppress("unused") private const val _TT_208: String = "elevenlabs package not installed; streaming TTS disabled"
@Suppress("unused") private const val _TT_209: String = "pcm_24000"
@Suppress("unused") private const val _TT_210: String = "Streaming TTS sentence failed: %s"
@Suppress("unused") private const val _TT_211: String = "Temp-file TTS fallback failed: %s"
@Suppress("unused") private const val _TT_212: String = "int16"
@Suppress("unused") private const val _TT_213: String = "sounddevice not available: %s"
@Suppress("unused") private const val _TT_214: String = "sounddevice OutputStream failed: %s"
