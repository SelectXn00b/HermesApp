package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TtsTool.kt — Android stub module. Python ships edge-tts /
 * ElevenLabs / OpenAI / xAI / Minimax / Mistral / Gemini / KittenTTS / NeuTTS.
 * On Android none of those backends exist, so every high-level call returns
 * toolError. The pure helpers that survive are `_getProvider` (always "edge")
 * and the TTS_SCHEMA shape.
 *
 * Covers TC-TOOL-311-a (默认 tts 提供商 = "edge" per Python upstream).
 */
class TtsToolTest {

    // ── R-TOOL-311 / TC-TOOL-311-a: default provider constant ──
    /**
     * TC-TOOL-311-a — Python `tts_tool.py` uses `_DEFAULT_PROVIDER = "edge"`.
     * Kotlin must match. The constant is module-private; we grab it via
     * reflection on `TtsToolKt` and also via behavioural call to
     * `_getProvider()` which returns `DEFAULT_PROVIDER` on Android.
     */
    @Test
    fun `default provider`() {
        // (a) reflection on the private module-level const
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.TtsToolKt")
        val field = clazz.getDeclaredField("DEFAULT_PROVIDER").apply { isAccessible = true }
        assertEquals("edge", field.get(null))

        // (b) behavioural: _getProvider() must echo the default on Android
        val getProvider = clazz.getDeclaredMethod("_getProvider", Map::class.java).apply {
            isAccessible = true
        }
        assertEquals("edge", getProvider.invoke(null, emptyMap<String, Any?>()))
        assertEquals("edge", getProvider.invoke(null, mapOf("provider" to "elevenlabs")))
    }

    // ── Provider default voices / models ──
    @Test
    fun `default edge voice`() {
        assertEquals("en-US-AriaNeural", DEFAULT_EDGE_VOICE)
    }

    @Test
    fun `default openai voice and model`() {
        assertEquals("gpt-4o-mini-tts", DEFAULT_OPENAI_MODEL)
        assertEquals("alloy", DEFAULT_OPENAI_VOICE)
    }

    @Test
    fun `default elevenlabs voice and models`() {
        assertEquals("pNInz6obpgDQGcFmaJgB", DEFAULT_ELEVENLABS_VOICE_ID)
        assertEquals("eleven_multilingual_v2", DEFAULT_ELEVENLABS_MODEL_ID)
        assertEquals("eleven_flash_v2_5", DEFAULT_ELEVENLABS_STREAMING_MODEL_ID)
    }

    @Test
    fun `default xai and minimax settings`() {
        assertEquals("eve", DEFAULT_XAI_VOICE_ID)
        assertEquals("en", DEFAULT_XAI_LANGUAGE)
        assertEquals(24_000, DEFAULT_XAI_SAMPLE_RATE)
        assertEquals(128_000, DEFAULT_XAI_BIT_RATE)
        assertEquals("https://api.x.ai/v1", DEFAULT_XAI_BASE_URL)
        assertEquals("speech-2.8-hd", DEFAULT_MINIMAX_MODEL)
        assertEquals("English_Graceful_Lady", DEFAULT_MINIMAX_VOICE_ID)
    }

    @Test
    fun `default gemini tts settings`() {
        assertEquals("gemini-2.5-flash-preview-tts", DEFAULT_GEMINI_TTS_MODEL)
        assertEquals("Kore", DEFAULT_GEMINI_TTS_VOICE)
        assertEquals(24_000, GEMINI_TTS_SAMPLE_RATE)
        assertEquals(1, GEMINI_TTS_CHANNELS)
        assertEquals(2, GEMINI_TTS_SAMPLE_WIDTH)
    }

    @Test
    fun `MAX_TEXT_LENGTH is 4000`() {
        assertEquals(4000, MAX_TEXT_LENGTH)
    }

    // ── TTS_SCHEMA shape ──
    @Test
    fun `TTS_SCHEMA name and required`() {
        assertEquals("text_to_speech", TTS_SCHEMA["name"])
        @Suppress("UNCHECKED_CAST")
        val params = TTS_SCHEMA["parameters"] as Map<String, Any?>
        assertEquals("object", params["type"])
        assertEquals(listOf("text"), params["required"])
    }

    @Test
    fun `TTS_SCHEMA description mentions 4000 char limit`() {
        @Suppress("UNCHECKED_CAST")
        val params = TTS_SCHEMA["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val text = props["text"] as Map<String, Any?>
        val desc = text["description"] as String
        assertTrue("should mention 4000 chars: $desc", desc.contains("4000"))
    }

    @Test
    fun `TTS_SCHEMA output_path property present`() {
        @Suppress("UNCHECKED_CAST")
        val params = TTS_SCHEMA["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        assertTrue("output_path" in props.keys)
    }

    // ── Sentence / markdown regex survival tests (plain-text normalization) ──
    @Test
    fun `_MD_CODE_BLOCK matches triple-backtick`() {
        val src = "text ```code``` text"
        assertTrue(_MD_CODE_BLOCK.containsMatchIn(src))
    }

    @Test
    fun `_MD_LINK matches markdown link`() {
        val match = _MD_LINK.find("see [here](https://ex.com) please")
        assertNotNull(match)
        assertEquals("here", match!!.groupValues[1])
    }

    @Test
    fun `_MD_URL matches bare URL`() {
        assertTrue(_MD_URL.containsMatchIn("visit https://example.com/page"))
    }

    @Test
    fun `_MD_BOLD matches bold pair`() {
        val m = _MD_BOLD.find("**bold text** here")
        assertNotNull(m)
        assertEquals("bold text", m!!.groupValues[1])
    }

    @Test
    fun `_MD_HEADER regex strips header hash`() {
        val stripped = "## Title\nbody".replace(_MD_HEADER, "")
        assertEquals("Title\nbody", stripped)
    }

    @Test
    fun `_MD_EXCESS_NL collapses 3 or more newlines`() {
        assertTrue(_MD_EXCESS_NL.containsMatchIn("a\n\n\nb"))
        assertTrue(_MD_EXCESS_NL.containsMatchIn("a\n\n\n\nb"))
        val noMatch = _MD_EXCESS_NL.containsMatchIn("a\n\nb")
        assertEquals(false, noMatch)
    }

    // ── Android stub: textToSpeechTool returns toolError ──
    @Test
    fun `textToSpeechTool returns error JSON on Android`() {
        val result = textToSpeechTool("hello world")
        val json = JSONObject(result)
        assertTrue("must have error key: $result", json.has("error"))
    }
}
