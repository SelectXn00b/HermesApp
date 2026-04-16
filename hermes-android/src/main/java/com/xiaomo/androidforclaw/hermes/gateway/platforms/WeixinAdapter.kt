package com.xiaomo.androidforclaw.hermes.gateway.platforms

/**
 * Weixin (WeChat Official Account) platform adapter.
 *
 * Uses the WeChat Official Account API for sending messages and
 * webhook callbacks for receiving messages.
 *
 * Ported from gateway/platforms/weixin.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WeixinAdapter(
    context: Context,
    config: PlatformConfig) : BasePlatformAdapter(config, Platform.WEIXIN) {
    companion object { private const val TAG = "WeixinAdapter" }

    private val _appId: String = config.extra("app_id") ?: System.getenv("WEIXIN_APP_ID") ?: ""
    private val _appSecret: String = config.extra("app_secret") ?: System.getenv("WEIXIN_APP_SECRET") ?: ""

    private val _httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var _accessToken: String = ""
    private var _accessTokenExpiry: Long = 0L

    override val isConnected: AtomicBoolean = AtomicBoolean(false)

    override suspend fun connect(): Boolean {
        if (_appId.isEmpty() || _appSecret.isEmpty()) {
            Log.e(TAG, "WEIXIN_APP_ID or WEIXIN_APP_SECRET not set")
            return false
        }
        return _refreshAccessToken()
    }

    override suspend fun disconnect() {
        markDisconnected()
    }

    private suspend fun _refreshAccessToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=$_appId&secret=$_appSecret")
                .get()
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val data = JSONObject(resp.body!!.string())
                if (data.has("errcode")) return@withContext false
                _accessToken = data.getString("access_token")
                _accessTokenExpiry = System.currentTimeMillis() / 1000 + data.getLong("expires_in") - 300
                Log.i(TAG, "Access token refreshed")
                markConnected()
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}")
            return@withContext false
        }
    }

    private suspend fun _ensureAccessToken() {
        if (_accessToken.isEmpty() || System.currentTimeMillis() / 1000 >= _accessTokenExpiry) {
            _refreshAccessToken()
        }
    }

    override suspend fun send(
        chatId: String,
        content: String,
        replyTo: String?,
        metadata: JSONObject?): SendResult = withContext(Dispatchers.IO) {
        try {
            _ensureAccessToken()

            val payload = JSONObject().apply {
                put("touser", chatId)
                put("msgtype", "text")
                put("text", JSONObject().apply { put("content", content) })
            }

            val body = payload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=$_accessToken")
                .post(body)
                .build()

            _httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SendResult(success = false, error = "HTTP ${resp.code}")
                val data = JSONObject(resp.body!!.string())
                if (data.optInt("errcode", 0) != 0) return@withContext SendResult(success = false, error = data.optString("errmsg"))
                SendResult(success = true)
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message)
        }
    }
}
