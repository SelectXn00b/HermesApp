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

const val DEFAULT_PROVIDER: String = "edge"
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

private fun _wrapPcmAsWav(vararg args: Any?): ByteArray = ByteArray(0)

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

fun streamTtsToSpeaker(vararg args: Any?): String =
    toolError("stream_tts_to_speaker is not available on Android")
