package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Home Assistant integration tool.
 * Ported from homeassistant_tool.py
 */
object HomeassistantTool {

    private const val TAG = "HomeAssistant"
    private const val TIMEOUT_SECONDS = 10L
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    data class HaResult(
        val success: Boolean = false,
        val data: Any? = null,
        val error: String? = null)

    private var baseUrl: String? = null
    private var apiToken: String? = null
    private var client: OkHttpClient? = null

    /**
     * Configure the Home Assistant connection.
     */
    fun configure(url: String, token: String) {
        baseUrl = url.trimEnd('/')
        apiToken = token
        client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Get all entity states.
     */
    fun getStates(): HaResult = apiGet("/api/states")

    /**
     * Get state of a specific entity.
     */
    fun getState(entityId: String): HaResult = apiGet("/api/states/$entityId")

    /**
     * Call a service (e.g., turn on a light).
     */
    fun callService(domain: String, service: String, entityId: String? = null, data: Map<String, Any>? = null): HaResult {
        val url = "$baseUrl/api/services/$domain/$service"
        val payload = mutableMapOf<String, Any>()
        if (entityId != null) payload["entity_id"] = entityId
        if (data != null) payload.putAll(data)

        return apiPost(url, payload)
    }

    private fun apiGet(path: String): HaResult {
        val url = baseUrl ?: return HaResult(error = "Home Assistant not configured")
        val token = apiToken ?: return HaResult(error = "No API token")
        val c = client ?: return HaResult(error = "HTTP client not initialized")

        return try {
            val request = Request.Builder()
                .url("$url$path")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()

            c.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return HaResult(error = "HTTP ${response.code}: ${body.take(200)}")
                }
                val parsed = gson.fromJson(body, Any::class.java)
                HaResult(success = true, data = parsed)
            }
        } catch (e: Exception) {
            HaResult(error = "Request failed: ${e.message}")
        }
    }

    private fun apiPost(url: String, payload: Any): HaResult {
        val token = apiToken ?: return HaResult(error = "No API token")
        val c = client ?: return HaResult(error = "HTTP client not initialized")

        return try {
            val body = gson.toJson(payload).toRequestBody(JSON)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()

            c.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return HaResult(error = "HTTP ${response.code}: ${respBody.take(200)}")
                }
                val parsed = gson.fromJson(respBody, Any::class.java)
                HaResult(success = true, data = parsed)
            }
        } catch (e: Exception) {
            HaResult(error = "Request failed: ${e.message}")
        }
    }


}
