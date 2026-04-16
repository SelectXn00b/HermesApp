package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenRouter API client.
 * Ported from openrouter_client.py
 */
object OpenrouterClient {

    private const val TAG = "OpenRouter"
    private const val BASE_URL = "https://openrouter.ai/api/v1"
    private const val TIMEOUT_SECONDS = 120L
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Call the OpenRouter chat completions API.
     */
    fun chatCompletion(
        model: String,
        messages: List<Map<String, Any>>,
        apiKey: String,
        maxTokens: Int? = null,
        temperature: Double? = null): String {
        val payload = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages)
        maxTokens?.let { payload["max_tokens"] = it }
        temperature?.let { payload["temperature"] = it }

        return try {
            val body = gson.toJson(payload).toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .post(body)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://hermes-agent.dev")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    """{"error":"HTTP ${response.code}: ${responseBody.take(500)}"}"""
                } else {
                    responseBody
                }
            }
        } catch (e: Exception) {
            """{"error":"OpenRouter request failed: ${e.message}"}"""
        }
    }

    /**
     * List available models.
     */
    fun listModels(apiKey: String): String {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .get()
                .header("Authorization", "Bearer $apiKey")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    """{"error":"HTTP ${response.code}: ${body.take(200)}"}"""
                } else body
            }
        } catch (e: Exception) {
            """{"error":"Failed to list models: ${e.message}"}"""
        }
    }


}
