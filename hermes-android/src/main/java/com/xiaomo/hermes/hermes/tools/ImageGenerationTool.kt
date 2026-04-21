package com.xiaomo.hermes.hermes.tools

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
 * Image generation tool — generate images using AI providers.
 * Ported from image_generation_tool.py
 */
object ImageGenerationTool {

    private const val TAG = "ImageGenTool"
    private const val TIMEOUT_SECONDS = 120L
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    data class ImageGenResult(
        val success: Boolean = false,
        val imagePath: String? = null,
        val base64Image: String? = null,
        val error: String? = null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Generate an image using an API provider.
     */
    fun generate(
        prompt: String,
        outputPath: String? = null,
        apiKey: String? = null,
        baseUrl: String? = null,
        model: String? = null,
        size: String = "1024x1024",
        quality: String = "standard"): String {
        if (prompt.isBlank()) return gson.toJson(mapOf("error" to "Prompt is required"))
        if (apiKey.isNullOrBlank()) return gson.toJson(mapOf("error" to "No API key configured"))

        return try {
            val apiBase = baseUrl ?: "https://api.openai.com/v1"
            val modelName = model ?: "dall-e-3"

            val payload = gson.toJson(mapOf(
                "model" to modelName,
                "prompt" to prompt,
                "size" to size,
                "quality" to quality,
                "response_format" to "b64_json",
                "n" to 1))

            val request = Request.Builder()
                .url("$apiBase/images/generations")
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
                val data = (json["data"] as? List<Map<String, Any>>)?.firstOrNull()
                val b64 = data?.get("b64_json") as? String

                if (b64.isNullOrBlank()) {
                    val url = data?.get("url") as? String
                    if (!url.isNullOrBlank()) {
                        return gson.toJson(mapOf("success" to true, "image_url" to url))
                    }
                    return gson.toJson(mapOf("error" to "No image data in response"))
                }

                val imageBytes = Base64.getDecoder().decode(b64)
                if (!outputPath.isNullOrBlank()) {
                    val file = File(outputPath)
                    file.parentFile?.mkdirs()
                    file.writeBytes(imageBytes)
                    gson.toJson(mapOf("success" to true, "image_path" to file.absolutePath))
                } else {
                    gson.toJson(mapOf("success" to true, "base64_image" to b64))
                }
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to "Image generation failed: ${e.message}"))
        }
    }

    /**
     * Edit an existing image using an API provider.
     */
    fun edit(
        prompt: String,
        imagePath: String,
        maskPath: String? = null,
        outputPath: String? = null,
        apiKey: String? = null,
        baseUrl: String? = null): String {
        // Simplified — actual implementation requires multipart upload
        return gson.toJson(mapOf("error" to "Image editing not yet implemented in Android client"))
    }



    fun submit(application: String, arguments: Map<String, Any>): Any? {
        return null
    }

}

/**
 * Small per-instance wrapper around fal_client.SyncClient for managed queue hosts.
 * Ported from _ManagedFalSyncClient in image_generation_tool.py.
 *
 * On Android, the FAL client SDK is not available, so this is a structural
 * placeholder that preserves the interface for alignment purposes.
 */
class _ManagedFalSyncClient(
    val key: String,
    val queueRunOrigin: String
) {
    private val _queueUrlFormat: String = queueRunOrigin.trimEnd('/') + "/"

    /**
     * Submit a request to the FAL queue.
     * On Android, this is a no-op placeholder.
     */
    fun submit(
        application: String,
        arguments: Map<String, Any>,
        path: String = "",
        hint: String? = null,
        webhookUrl: String? = null,
        priority: Any? = null,
        headers: Map<String, String>? = null,
        startTimeout: Number? = null
    ): Any? {
        // FAL client SDK is not available on Android.
        // This is a structural placeholder for 1:1 alignment.
        return null
    }
}
