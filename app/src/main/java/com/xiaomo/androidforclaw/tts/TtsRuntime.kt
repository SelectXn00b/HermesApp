package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/tts.ts, tts-core.ts, tts-auto-mode.ts
 *
 * Text-to-speech runtime: synthesis dispatch, voice listing, auto-mode,
 * TTS instruction markers ([[tts:text]]...[[/tts:text]]), text summarization
 * for provider length limits, and directive parsing.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object TtsRuntime {

    // -----------------------------------------------------------------------
    // TTS instruction markers — [[tts:text]]...[[/tts:text]]
    // -----------------------------------------------------------------------

    private val TTS_MARKER_REGEX = Regex(
        """\[\[tts:text]](.*?)\[\[/tts:text]]""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /** Extract text content between [[tts:text]] ... [[/tts:text]] markers. */
    fun extractTtsTaggedText(text: String): String? {
        val match = TTS_MARKER_REGEX.find(text) ?: return null
        return match.groupValues[1].trim()
    }

    /** Check whether the text contains TTS instruction markers. */
    fun hasTtsMarkers(text: String): Boolean = TTS_MARKER_REGEX.containsMatchIn(text)

    /** Strip TTS markers from text, returning the cleaned content. */
    fun stripTtsMarkers(text: String): String {
        return TTS_MARKER_REGEX.replace(text) { it.groupValues[1].trim() }.trim()
    }

    // -----------------------------------------------------------------------
    // Directive-style markers — [tts:provider voice=xxx]...[/tts]
    // (Legacy inline directives for provider/voice override)
    // -----------------------------------------------------------------------

    private val TTS_DIRECTIVE_REGEX = Regex(
        """\[tts(?::(\w+))?(?:\s+voice=(\w+))?\](.*?)\[/tts]""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    fun parseTtsDirectives(text: String): TtsDirectiveParseResult {
        val match = TTS_DIRECTIVE_REGEX.find(text)
        if (match == null) {
            return TtsDirectiveParseResult(
                cleanedText = text,
                ttsText = null,
                hasDirective = false
            )
        }
        val provider = match.groupValues[1].takeIf { it.isNotEmpty() }
        val voice = match.groupValues[2].takeIf { it.isNotEmpty() }
        val ttsContent = match.groupValues[3].trim()
        val cleanedText = text.replace(match.value, "").trim()

        return TtsDirectiveParseResult(
            cleanedText = cleanedText,
            ttsText = ttsContent,
            hasDirective = true,
            overrides = TtsDirectiveOverrides(
                ttsText = ttsContent,
                provider = provider,
                voice = voice
            )
        )
    }

    // -----------------------------------------------------------------------
    // Synthesis dispatch
    // -----------------------------------------------------------------------

    /** Synthesize speech via the resolved provider. */
    suspend fun synthesizeSpeech(request: TtsRequest): TtsResult {
        val provider = TtsProviderRegistry.getSpeechProvider(request.provider)
            ?: throw IllegalStateException(
                "No TTS provider found${request.provider?.let { " for ID: $it" } ?: ""}"
            )
        return provider.synthesize(request)
    }

    /** List available voices for a provider. */
    suspend fun listSpeechVoices(
        providerId: String? = null,
        config: OpenClawConfig? = null
    ): List<SpeechVoiceOption> {
        val provider = TtsProviderRegistry.getSpeechProvider(providerId, config)
            ?: return emptyList()
        return provider.listVoices()
    }

    // -----------------------------------------------------------------------
    // Length management
    // -----------------------------------------------------------------------

    fun getTtsMaxLength(config: OpenClawConfig? = null): Int {
        if (config != null) {
            return resolveTtsConfig(config).maxLength
        }
        return 4000
    }

    /**
     * Summarize / truncate text to fit within the provider's max-length limit.
     * A real implementation could delegate to an LLM for intelligent summarization.
     */
    suspend fun summarizeTextForTts(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        // Simple truncation with ellipsis as fallback
        return text.take(maxLength - 3) + "..."
    }

    // -----------------------------------------------------------------------
    // Mode resolution helper
    // -----------------------------------------------------------------------

    /**
     * Determine whether TTS should fire for a given message context.
     *
     * @param mode        The configured TTS mode.
     * @param isInbound   true when the original inbound message was a voice note.
     * @param messageText The outbound text to be spoken.
     */
    fun shouldSpeak(mode: TtsMode, isInbound: Boolean, messageText: String): Boolean {
        return when (mode) {
            TtsMode.OFF -> false
            TtsMode.ALWAYS -> true
            TtsMode.INBOUND -> isInbound
            TtsMode.TAGGED -> hasTtsMarkers(messageText)
        }
    }
}
