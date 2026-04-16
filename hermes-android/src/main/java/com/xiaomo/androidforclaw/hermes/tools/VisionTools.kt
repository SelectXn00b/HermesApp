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
 * Vision tools — image analysis using LLM vision capabilities.
 * Ported from vision_tools.py
 */
object VisionTools {

    private const val TAG = "VisionTools"
    private const val TIMEOUT_SECONDS = 60L
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    data class VisionResult(
        val success: Boolean = false,
        val analysis: String = "",
        val error: String? = null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Analyze an image with a vision model.
     * The actual API call is delegated to a callback (since Android apps
     * will handle their own API configuration).
     */
    fun analyzeImage(
        imagePath: String,
        prompt: String = "Describe this image in detail.",
        model: String? = null,
        apiKey: String? = null,
        baseUrl: String? = null): String {
        val file = File(imagePath)
        if (!file.exists()) return gson.toJson(mapOf("error" to "Image file not found: $imagePath"))

        val extension = file.extension.lowercase()
        val mimeTypes = mapOf(
            "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
            "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp")
        val mimeType = mimeTypes[extension] ?: "image/png"

        if (apiKey.isNullOrBlank()) {
            return gson.toJson(mapOf("error" to "No API key configured for vision analysis"))
        }

        return try {
            val imageBytes = file.readBytes()
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)
            val apiBase = baseUrl ?: "https://api.openai.com/v1"
            val modelName = model ?: "gpt-4o-mini"

            val payload = gson.toJson(mapOf(
                "model" to modelName,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf("type" to "text", "text" to prompt),
                            mapOf(
                                "type" to "image_url",
                                "image_url" to mapOf(
                                    "url" to "data:$mimeType;base64,$base64Image"))))),
                "max_tokens" to 1000))

            val request = Request.Builder()
                .url("$apiBase/chat/completions")
                .post(payload.toRequestBody(JSON))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return gson.toJson(mapOf("error" to "API error: ${response.code} - ${body.take(200)}"))
                }
                val json = gson.fromJson(body, Map::class.java) as Map<String, Any>
                val choices = json["choices"] as? List<Map<String, Any>> ?: emptyList()
                val content = choices.firstOrNull()
                    ?.let { it["message"] as? Map<String, Any> }
                    ?.get("content") as? String ?: ""
                gson.toJson(mapOf("success" to true, "analysis" to content))
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to "Vision analysis failed: ${e.message}"))
        }
    }

    /**
     * Read an image file and return its base64 representation.
     */
    fun imageToBase64(imagePath: String): Map<String, String?> {
        val file = File(imagePath)
        if (!file.exists()) return mapOf("error" to "Image file not found: $imagePath")
        return try {
            val bytes = file.readBytes()
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "image/png"
            }
            mapOf(
                "base64" to Base64.getEncoder().encodeToString(bytes),
                "mime_type" to mimeType)
        } catch (e: Exception) {
            mapOf("error" to "Failed to read image: ${e.message}")
        }
    }


}
