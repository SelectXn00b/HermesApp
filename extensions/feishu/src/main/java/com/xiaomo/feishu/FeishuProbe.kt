package com.xiaomo.feishu

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 飞书连接探测
 * 对齐 OpenClaw probe.ts
 *
 * 功能：
 * - 探测飞书 API 连接状态
 * - 获取机器人信息
 * - 缓存探测结果以减少 API 调用
 */
object FeishuProbe {
    private const val TAG = "FeishuProbe"

    // 探测缓存
    private val probeCache = mutableMapOf<String, CachedProbeResult>()
    private const val PROBE_SUCCESS_TTL_MS = 10 * 60 * 1000L // 10 minutes
    private const val PROBE_ERROR_TTL_MS = 60 * 1000L // 1 minute
    private const val MAX_PROBE_CACHE_SIZE = 64
    private const val FEISHU_PROBE_REQUEST_TIMEOUT_MS = 10_000L

    /**
     * 探测结果
     */
    data class ProbeResult(
        val ok: Boolean,
        val appId: String?,
        val botName: String? = null,
        val botOpenId: String? = null,
        val error: String? = null
    )

    /**
     * 缓存的探测结果
     */
    private data class CachedProbeResult(
        val result: ProbeResult,
        val expiresAt: Long
    )

    /**
     * 机器人信息响应
     */
    private data class BotInfoResponse(
        val code: Int,
        val msg: String?,
        val bot: BotInfo?,
        val data: BotData?
    )

    private data class BotInfo(
        val bot_name: String?,
        val open_id: String?
    )

    private data class BotData(
        val bot: BotInfo?
    )

    /**
     * 探测飞书连接
     *
     * @param client Feishu 客户端
     * @param appId App ID
     * @param timeoutMs 超时时间（毫秒）
     * @return 探测结果
     */
    suspend fun probe(
        client: FeishuClient,
        appId: String,
        timeoutMs: Long = FEISHU_PROBE_REQUEST_TIMEOUT_MS
    ): ProbeResult = withContext(Dispatchers.IO) {
        // 检查缓存
        val cacheKey = appId
        val cached = probeCache[cacheKey]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            Log.d(TAG, "Returning cached probe result for $appId")
            return@withContext cached.result
        }

        try {
            // 调用机器人信息 API
            val result = withTimeout(timeoutMs) {
                client.get("/open-apis/bot/v3/info")
            }

            if (result.isFailure) {
                val error = result.exceptionOrNull()
                Log.w(TAG, "Probe failed for $appId: ${error?.message}")
                val probeResult = ProbeResult(
                    ok = false,
                    appId = appId,
                    error = error?.message ?: "Unknown error"
                )
                setCachedProbeResult(cacheKey, probeResult, PROBE_ERROR_TTL_MS)
                return@withContext probeResult
            }

            val response = result.getOrNull()
            val code = response?.get("code")?.asInt ?: -1

            if (code != 0) {
                val msg = response?.get("msg")?.asString ?: "code $code"
                Log.w(TAG, "Probe API error for $appId: $msg")
                val probeResult = ProbeResult(
                    ok = false,
                    appId = appId,
                    error = "API error: $msg"
                )
                setCachedProbeResult(cacheKey, probeResult, PROBE_ERROR_TTL_MS)
                return@withContext probeResult
            }

            // 解析机器人信息
            val bot = response?.getAsJsonObject("bot")
                ?: response?.getAsJsonObject("data")?.getAsJsonObject("bot")

            val botName = bot?.get("bot_name")?.asString
            val botOpenId = bot?.get("open_id")?.asString

            Log.d(TAG, "Probe successful for $appId: bot=$botName, openId=$botOpenId")
            val probeResult = ProbeResult(
                ok = true,
                appId = appId,
                botName = botName,
                botOpenId = botOpenId
            )
            setCachedProbeResult(cacheKey, probeResult, PROBE_SUCCESS_TTL_MS)
            return@withContext probeResult

        } catch (e: Exception) {
            Log.e(TAG, "Probe exception for $appId", e)
            val probeResult = ProbeResult(
                ok = false,
                appId = appId,
                error = e.message ?: "Unknown error"
            )
            setCachedProbeResult(cacheKey, probeResult, PROBE_ERROR_TTL_MS)
            return@withContext probeResult
        }
    }

    /**
     * 缓存探测结果
     */
    private fun setCachedProbeResult(
        cacheKey: String,
        result: ProbeResult,
        ttlMs: Long
    ): ProbeResult {
        probeCache[cacheKey] = CachedProbeResult(
            result = result,
            expiresAt = System.currentTimeMillis() + ttlMs
        )

        // 限制缓存大小
        if (probeCache.size > MAX_PROBE_CACHE_SIZE) {
            val oldest = probeCache.keys.firstOrNull()
            if (oldest != null) {
                probeCache.remove(oldest)
            }
        }

        return result
    }

    /**
     * 清除探测缓存（用于测试）
     */
    fun clearCache() {
        probeCache.clear()
    }
}
