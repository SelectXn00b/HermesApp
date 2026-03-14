/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/discord/(all)
 *
 * AndroidForClaw adaptation: Discord channel runtime.
 */
package com.xiaomo.discord

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Discord API 客户端
 * 基于 Discord REST API v10
 */
class DiscordClient(
    private val token: String
) {
    companion object {
        private const val TAG = "DiscordClient"
        private const val BASE_URL = "https://discord.com/api/v10"
        private const val USER_AGENT = "AndroidForClaw (https://github.com/xiaomochn/AndroidForClaw, 3.0)"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 发送消息到 Discord 频道
     */
    suspend fun sendMessage(
        channelId: String,
        content: String,
        embeds: List<Map<String, Any>>? = null,
        components: List<Map<String, Any>>? = null,
        messageReference: Map<String, Any>? = null
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val payload = mutableMapOf<String, Any>(
                "content" to content
            )

            embeds?.let { payload["embeds"] = it }
            components?.let { payload["components"] = it }
            messageReference?.let { payload["message_reference"] = it }

            val body = gson.toJson(payload).toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$BASE_URL/channels/$channelId/messages")
                .header("Authorization", "Bot $token")
                .header("User-Agent", USER_AGENT)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                Log.d(TAG, "Message sent successfully to channel $channelId")
                Result.success(json)
            } else {
                Log.e(TAG, "Failed to send message: ${response.code} - $responseBody")
                Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }

    /**
     * 发送 DM (私聊消息)
     */
    suspend fun sendDirectMessage(
        userId: String,
        content: String
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            // 1. 创建 DM Channel
            val dmChannel = createDMChannel(userId).getOrThrow()
            val channelId = dmChannel.get("id").asString

            // 2. 发送消息
            sendMessage(channelId, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DM to user $userId", e)
            Result.failure(e)
        }
    }

    /**
     * 创建 DM Channel
     */
    private suspend fun createDMChannel(userId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf("recipient_id" to userId)
            val body = gson.toJson(payload).toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$BASE_URL/users/@me/channels")
                .header("Authorization", "Bot $token")
                .header("User-Agent", USER_AGENT)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                Result.success(json)
            } else {
                Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 添加反应 (Emoji)
     */
    suspend fun addReaction(
        channelId: String,
        messageId: String,
        emoji: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val encodedEmoji = java.net.URLEncoder.encode(emoji, "UTF-8")
            val request = Request.Builder()
                .url("$BASE_URL/channels/$channelId/messages/$messageId/reactions/$encodedEmoji/@me")
                .header("Authorization", "Bot $token")
                .header("User-Agent", USER_AGENT)
                .put("".toRequestBody(null))
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "Reaction added: $emoji")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to add reaction: ${response.code}")
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding reaction", e)
            Result.failure(e)
        }
    }

    /**
     * 移除反应
     */
    suspend fun removeReaction(
        channelId: String,
        messageId: String,
        emoji: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val encodedEmoji = java.net.URLEncoder.encode(emoji, "UTF-8")
            val request = Request.Builder()
                .url("$BASE_URL/channels/$channelId/messages/$messageId/reactions/$encodedEmoji/@me")
                .header("Authorization", "Bot $token")
                .header("User-Agent", USER_AGENT)
                .delete()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "Reaction removed: $emoji")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to remove reaction: ${response.code}")
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing reaction", e)
            Result.failure(e)
        }
    }

    /**
     * 触发输入状态 (Typing Indicator)
     */
    suspend fun triggerTyping(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/channels/$channelId/typing")
                .header("Authorization", "Bot $token")
                .header("User-Agent", USER_AGENT)
                .post("".toRequestBody(null))
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取当前 Bot 用户信息
     */
    suspend fun getCurrentUser(): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/users/@me")
                .header("Authorization", "Bot $token")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                Result.success(json)
            } else {
                Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取 Guild (服务器) 信息
     */
    suspend fun getGuild(guildId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/guilds/$guildId")
                .header("Authorization", "Bot $token")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                Result.success(json)
            } else {
                Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取 Channel 信息
     */
    suspend fun getChannel(channelId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/channels/$channelId")
                .header("Authorization", "Bot $token")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                Result.success(json)
            } else {
                Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
