package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Camofox anti-detection browser backend.
 * Routes browser operations through the camofox REST API.
 * Ported from browser_camofox.py
 */
object BrowserCamofox {

    private const val _TAG = "BrowserCamofox"
    private const val TIMEOUT_SECONDS = 30L
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    private var _baseUrl: String? = null
    private var client: OkHttpClient? = null

    /**
     * Check if camofox mode is enabled (CAMOFOX_URL is set).
     */
    fun isCamofoxMode(): Boolean {
        return !getCamofoxUrl().isNullOrEmpty()
    }

    /**
     * Get the camofox API URL from environment.
     */
    fun getCamofoxUrl(): String? {
        return _baseUrl ?: System.getenv("CAMOFOX_URL")?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Initialize the camofox client.
     */
    fun initialize(url: String? = null) {
        _baseUrl = (url ?: getCamofoxUrl())?.trimEnd('/')
        client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Send a command to the camofox API.
     */
    fun sendCommand(action: String, params: Map<String, Any> = emptyMap()): Map<String, Any> {
        val url = getCamofoxUrl() ?: return mapOf("error" to "Camofox not configured")
        val c = client ?: OkHttpClient()

        return try {
            val payload = gson.toJson(mutableMapOf<String, Any>("action" to action).apply { putAll(params) })
            val request = Request.Builder()
                .url("$url/$action")
                .post(payload.toRequestBody(JSON))
                .header("Content-Type", "application/json")
                .build()

            c.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return mapOf("error" to "HTTP ${response.code}: ${body.take(200)}")
                }
                gson.fromJson(body, Map::class.java) as Map<String, Any>
            }
        } catch (e: Exception) {
            mapOf("error" to "Camofox command failed: ${e.message}")
        }
    }

    /**
     * Cleanup camofox resources.
     */
    fun cleanup() {
        _baseUrl = null
        client = null
    }


}
