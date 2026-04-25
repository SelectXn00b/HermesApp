package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Home Assistant platform adapter.
 *
 * Uses the Home Assistant REST API for receiving state change events
 * and sending persistent notifications.
 *
 * Ported from gateway/platforms/homeassistant.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.hermes.gateway.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class HomeAssistantAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.HOMEASSISTANT) {
    companion object {
        private const val _TAG = "Homeassistant"
        val BACKOFF_STEPS = listOf(5, 10, 30, 60, 120, 300)
        const val COOLDOWN_SECONDS = 10L
    }

    private val _hassUrl: String = config.extra("hass_url") ?: System.getenv("HASS_URL") ?: ""
    private val _hassToken: String = config.extra("hass_token") ?: System.getenv("HASS_TOKEN") ?: ""
    private val _watchDomains: Set<String> = config.extra("watch_domains")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    private val _watchEntities: Set<String> = config.extra("watch_entities")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    private val _ignoreEntities: Set<String> = config.extra("ignore_entities")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    private val _watchAll: Boolean = config.extraBool("watch_all", false)
    private val _cooldownSeconds: Long = config.extraInt("cooldown_seconds", COOLDOWN_SECONDS.toInt()).toLong()

    private val _httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _lastEventTime: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    private var _pollJob: Job? = null

    override val isConnected: AtomicBoolean = AtomicBoolean(false)

    override suspend fun connect(): Boolean {
        if (_hassUrl.isEmpty() || _hassToken.isEmpty()) {
            Log.e(_TAG, "HASS_URL or HASS_TOKEN not set")
            return false
        }

        // Test connection
        try {
            val request = Request.Builder()
                .url("$_hassUrl/api/")
                .header("Authorization", "Bearer $_hassToken")
                .get()
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(_TAG, "HA connection failed: HTTP ${resp.code}")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(_TAG, "HA connection failed: ${e.message}")
            return false
        }

        markConnected()
        Log.i(_TAG, "Connected to $_hassUrl")
        return true
    }

    override suspend fun disconnect() {
        _pollJob?.cancel()
        _pollJob = null
        markDisconnected()
        Log.i(_TAG, "Disconnected")
    }

    override suspend fun send(
        chatId: String,
        content: String,
        replyTo: String?,
        metadata: JSONObject?): SendResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("title", "Hermes Agent")
                put("message", content)
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$_hassUrl/api/services/persistent_notification/create")
                .header("Authorization", "Bearer $_hassToken")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    SendResult(success = true, messageId = java.util.UUID.randomUUID().toString().take(12))
                } else {
                    SendResult(success = false, error = "HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }
}
