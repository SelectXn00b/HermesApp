package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Transcription tools — speech-to-text via API providers.
 * Ported from transcription_tools.py
 */
object TranscriptionTools {

    private const val TAG = "Transcription"
    private const val TIMEOUT_SECONDS = 60L
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    data class TranscriptionResult(
        val success: Boolean = false,
        val text: String = "",
        val language: String? = null,
        val error: String? = null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribe audio file via OpenAI Whisper API.
     */
    fun transcribe(
        audioPath: String,
        apiKey: String? = null,
        model: String = "whisper-1",
        language: String? = null): String {
        val file = File(audioPath)
        if (!file.exists()) return gson.toJson(mapOf("error" to "Audio file not found: $audioPath"))
        if (apiKey.isNullOrBlank()) return gson.toJson(mapOf("error" to "No API key configured"))

        return try {
            // Simplified: actual implementation would use multipart upload
            val b64 = java.util.Base64.getEncoder().encodeToString(file.readBytes())
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "ogg" -> "audio/ogg"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                "webm" -> "audio/webm"
                else -> "audio/mpeg"
            }

            // Delegate to API (simplified — real impl needs multipart)
            gson.toJson(mapOf("error" to "Multipart upload not implemented in Android client. Use OpenAI SDK."))
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to "Transcription failed: ${e.message}"))
        }
    }

    /**
     * Callback interface for transcription.
     */
    fun interface Transcriber {
        fun transcribe(audioPath: String, language: String?): TranscriptionResult
    }

    /**
     * Transcribe with a callback (for platform-provided transcription).
     */
    fun transcribeWith(
        audioPath: String,
        language: String? = null,
        transcriber: Transcriber): String {
        val file = File(audioPath)
        if (!file.exists()) return gson.toJson(mapOf("error" to "Audio file not found"))
        return try {
            val result = transcriber.transcribe(audioPath, language)
            if (result.success) {
                gson.toJson(mapOf("success" to true, "text" to result.text, "language" to result.language))
            } else {
                gson.toJson(mapOf("error" to (result.error ?: "Transcription failed")))
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to "Transcription failed: ${e.message}"))
        }
    }


}
