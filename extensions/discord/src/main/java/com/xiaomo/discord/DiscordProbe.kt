/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/discord/(all)
 *
 * AndroidForClaw adaptation: Discord channel runtime.
 */
package com.xiaomo.discord

import android.util.Log
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Discord 连接探测
 * 参考 Feishu FeishuProbe.kt
 *
 * 用于健康检查和状态监控
 */
object DiscordProbe {
    private const val TAG = "DiscordProbe"
    private const val DEFAULT_TIMEOUT_MS = 5000L

    /**
     * 探测结果
     */
    data class ProbeResult(
        val ok: Boolean,
        val latencyMs: Long? = null,
        val error: String? = null,
        val bot: BotInfo? = null,
        val application: ApplicationInfo? = null
    )

    data class BotInfo(
        val id: String,
        val username: String,
        val discriminator: String,
        val bot: Boolean = true
    )

    data class ApplicationInfo(
        val id: String,
        val name: String,
        val verifyKey: String? = null,
        val intents: IntentsInfo? = null
    )

    data class IntentsInfo(
        val messageContent: String? = null // "enabled", "disabled", "limited"
    )

    /**
     * 探测 Discord 连接
     */
    suspend fun probe(
        token: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        includeApplication: Boolean = false
    ): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()

            // 创建临时客户端
            val client = DiscordClient(token)

            // 获取当前 Bot 信息
            val result = withTimeoutOrNull(timeoutMs) {
                client.getCurrentUser()
            }

            if (result == null) {
                return@withContext ProbeResult(
                    ok = false,
                    error = "Timeout after ${timeoutMs}ms"
                )
            }

            if (result.isFailure) {
                return@withContext ProbeResult(
                    ok = false,
                    error = result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }

            val latency = System.currentTimeMillis() - startTime
            val userData = result.getOrNull()

            if (userData == null) {
                return@withContext ProbeResult(
                    ok = false,
                    error = "No user data returned"
                )
            }

            val botInfo = BotInfo(
                id = userData.get("id")?.asString ?: "",
                username = userData.get("username")?.asString ?: "",
                discriminator = userData.get("discriminator")?.asString ?: "0",
                bot = userData.get("bot")?.asBoolean ?: false
            )

            Log.i(TAG, "✅ Discord probe successful: ${botInfo.username} (${latency}ms)")

            ProbeResult(
                ok = true,
                latencyMs = latency,
                bot = botInfo,
                application = if (includeApplication) {
                    // TODO: Fetch application info from Discord API
                    // 需要额外的 API 调用: GET /api/v10/oauth2/applications/@me
                    null
                } else null
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Discord probe failed", e)
            ProbeResult(
                ok = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 快速健康检查
     */
    suspend fun healthCheck(token: String): Boolean {
        val result = probe(token, timeoutMs = 3000L, includeApplication = false)
        return result.ok
    }
}
