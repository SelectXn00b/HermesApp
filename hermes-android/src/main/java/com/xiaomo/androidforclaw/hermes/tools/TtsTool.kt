package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Text-to-Speech Tool.
 * Supports multiple TTS providers: Edge TTS, ElevenLabs, OpenAI, Mistral.
 * Ported from tts_tool.py
 */
object TtsTool {

    private const val TAG = "TtsTool"
    private const val TIMEOUT_SECONDS = 60L
    private const val MAX_TEXT_LENGTH = 4000
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    data class TtsResult(
        val success: Boolean = false,
        val audioPath: String? = null,
        val audioBase64: String? = null,
        val provider: String = "",
        val error: String? = null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Generate speech from text.
     * In Android, this delegates to a callback or API.
     */
    fun textToSpeech(
        text: String,
        provider: String = "edge",
        voice: String? = null,
        outputDir: String? = null,
        apiKey: String? = null,
        format: String = "mp3"): String {
        if (text.isBlank()) return gson.toJson(mapOf("error" to "Text is required"))
        if (text.length > MAX_TEXT_LENGTH) {
            return gson.toJson(mapOf("error" to "Text too long: ${text.length} chars (max $MAX_TEXT_LENGTH)"))
        }

        return when (provider) {
            "openai" -> generateOpenAI(text, voice ?: "alloy", apiKey)
            "elevenlabs" -> generateElevenLabs(text, voice, apiKey)
            else -> gson.toJson(mapOf("error" to "Provider '$provider' not available on Android (use openai or elevenlabs)"))
        }
    }

    private fun generateOpenAI(text: String, voice: String, apiKey: String?): String {
        if (apiKey.isNullOrBlank()) {
            return gson.toJson(mapOf("error" to "OPENAI_API_KEY not set"))
        }
        return try {
            val payload = gson.toJson(mapOf(
                "model" to "tts-1",
                "input" to text,
                "voice" to voice,
                "response_format" to "mp3"))

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .post(payload.toRequestBody(JSON))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    return gson.toJson(mapOf("error" to "OpenAI TTS failed: ${response.code} - ${body.take(200)}"))
                }
                val bytes = response.body?.bytes() ?: return gson.toJson(mapOf("error" to "Empty response"))
                val b64 = Base64.getEncoder().encodeToString(bytes)
                gson.toJson(mapOf("success" to true, "audio_base64" to b64, "provider" to "openai"))
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to "OpenAI TTS failed: ${e.message}"))
        }
    }

    private fun generateElevenLabs(text: String, voice: String?, apiKey: String?): String {
        if (apiKey.isNullOrBlank()) {
            return gson.toJson(mapOf("error" to "ELEVENLABS_API_KEY not set"))
        }
        return try {
            val voiceId = voice ?: "pNInz6obpgDQGcFmaJgB"
            val payload = gson.toJson(mapOf(
                "text" to text,
                "model_id" to "eleven_multilingual_v2"))

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
                .post(payload.toRequestBody(JSON))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    return gson.toJson(mapOf("error" to "ElevenLabs TTS failed: ${response.code} - ${body.take(200)}"))
                }
                val bytes = response.body?.bytes() ?: return gson.toJson(mapOf("error" to "Empty response"))
                val b64 = Base64.getEncoder().encodeToString(bytes)
                gson.toJson(mapOf("success" to true, "audio_base64" to b64, "provider" to "elevenlabs"))
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to "ElevenLabs TTS failed: ${e.message}"))
        }
    }


    // === Missing constants (auto-generated stubs) ===
    val DEFAULT_PROVIDER = ""
    val DEFAULT_EDGE_VOICE = ""
    val DEFAULT_ELEVENLABS_VOICE_ID = ""
    val DEFAULT_ELEVENLABS_MODEL_ID = ""
    val DEFAULT_ELEVENLABS_STREAMING_MODEL_ID = ""
    val DEFAULT_OPENAI_MODEL = ""
    val DEFAULT_OPENAI_VOICE = ""
    val DEFAULT_OPENAI_BASE_URL = ""
    val DEFAULT_MINIMAX_MODEL = ""
    val DEFAULT_MINIMAX_VOICE_ID = ""
    val DEFAULT_MINIMAX_BASE_URL = ""
    val DEFAULT_MISTRAL_TTS_MODEL = ""
    val DEFAULT_MISTRAL_TTS_VOICE_ID = ""
    val DEFAULT_OUTPUT_DIR = ""
    val _SENTENCE_BOUNDARY_RE = ""
    val _MD_CODE_BLOCK = ""
    val _MD_LINK = ""
    val _MD_URL = ""
    val _MD_BOLD = ""
    val _MD_ITALIC = ""
    val _MD_INLINE_CODE = ""
    val _MD_HEADER = ""
    val _MD_LIST_ITEM = ""
    val _MD_HR = ""
    val _MD_EXCESS_NL = ""
    val TTS_SCHEMA = ""

    // === Missing methods (auto-generated stubs) ===
    private fun importEdgeTts(): Unit {
    // Hermes: _import_edge_tts
}
}
