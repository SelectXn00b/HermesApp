package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * WeCom (Enterprise WeChat) platform adapter.
 *
 * Uses the WeCom Bot API for sending messages and WebSocket for receiving.
 *
 * Ported from gateway/platforms/wecom.py
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WeCom(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.WECOM) {
    companion object { private const val _TAG = "WeCom" }

    private val _corpId: String = config.extra("corp_id") ?: System.getenv("WECOM_CORP_ID") ?: ""
    private val _corpSecret: String = config.extra("corp_secret") ?: System.getenv("WECOM_CORP_SECRET") ?: ""
    private val _agentId: String = config.extra("agent_id") ?: System.getenv("WECOM_AGENT_ID") ?: ""
    private val _token: String = config.extra("token") ?: System.getenv("WECOM_TOKEN") ?: ""
    private val _encodingAesKey: String = config.extra("encoding_aes_key") ?: System.getenv("WECOM_ENCODING_AES_KEY") ?: ""

    private val _httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var _accessToken: String = ""
    private var _accessTokenExpiry: Long = 0L

    override val isConnected: AtomicBoolean = AtomicBoolean(false)

    override suspend fun connect(): Boolean {
        if (_corpId.isEmpty() || _corpSecret.isEmpty()) {
            Log.e(_TAG, "WECOM_CORP_ID or WECOM_CORP_SECRET not set")
            return false
        }

        return _getAccessToken() != null
    }

    override suspend fun disconnect() {
        markDisconnected()
    }

    private suspend fun _getAccessToken(): String? = withContext(Dispatchers.IO) {
        if (_accessToken.isNotEmpty() && System.currentTimeMillis() / 1000 < _accessTokenExpiry) {
            return@withContext _accessToken
        }
        try {
            val request = Request.Builder()
                .url("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=$_corpId&corpsecret=$_corpSecret")
                .get()
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("errcode", 0) != 0) return@withContext null
                _accessToken = data.getString("access_token")
                _accessTokenExpiry = System.currentTimeMillis() / 1000 + data.getLong("expires_in") - 300
                Log.i(_TAG, "Access token refreshed")
                markConnected()
                return@withContext _accessToken
            }
        } catch (e: Exception) {
            Log.e(_TAG, "Token refresh failed: ${e.message}")
            return@withContext null
        }
    }

    override suspend fun send(
        chatId: String,
        content: String,
        replyTo: String?,
        metadata: JSONObject?): SendResult = withContext(Dispatchers.IO) {
        try {
            val token = _getAccessToken() ?: return@withContext SendResult(success = false, error = "no access token")

            val payload = JSONObject().apply {
                put("touser", chatId)
                put("msgtype", "text")
                put("agentid", _agentId.toIntOrNull() ?: 0)
                put("text", JSONObject().apply { put("content", content.take(2048)) })
                put("safe", 0)
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=$token")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("errcode", 0) != 0) return@withContext SendResult(success = false, error = data.optString("errmsg"))
                SendResult(success = true, messageId = data.optString("msgid"))
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }
}
