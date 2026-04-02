package com.xiaomo.feishu.messaging

/**
 * OpenClaw Source Reference:
 * - openclaw-lark/src/card/cardkit.js
 * - openclaw-lark/src/card/streaming-card-controller.js
 *
 * Manages a single streaming card session using Feishu Card Kit API (schema 2.0).
 * Lifecycle: start() → appendText() (repeated) → close()
 */

import android.util.Log
import com.google.gson.Gson
import com.xiaomo.feishu.FeishuClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 飞书流式卡片会话
 * 对齐 OpenClaw streaming-card-controller.js
 *
 * Uses Card Kit API to create a streaming card that updates in real-time:
 * 1. POST /cardkit/v1/cards — create card entity with streaming_mode: true
 * 2. PUT /cardkit/v1/cards/{cardId}/elements/{elementId}/content — stream content updates
 * 3. PUT /cardkit/v1/cards/{cardId}/settings — close streaming mode
 * 4. PUT /cardkit/v1/cards/{cardId} — final card update (optional)
 */
class FeishuStreamingCard(
    private val client: FeishuClient
) {
    companion object {
        private const val TAG = "FeishuStreamingCard"
        private const val ELEMENT_ID = "streaming_content"
        private const val THROTTLE_MS = 100L // Max 10 updates/second, aligned with OpenClaw CARDKIT_MS
    }

    private val gson = Gson()
    private val mutex = Mutex()

    // State
    var cardId: String? = null
        private set
    private var sequence: Int = 0
    private var isOpen: Boolean = false
    private var accumulatedText: String = ""
    private var lastUpdateTime: Long = 0

    /**
     * 创建流式卡片
     * 对齐 OpenClaw streaming-card-controller.js STREAMING_THINKING_CARD
     *
     * @return cardId on success
     */
    suspend fun start(initialText: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Card JSON 2.0 payload — aligned with OpenClaw STREAMING_THINKING_CARD
            val cardData = mapOf(
                "schema" to "2.0",
                "config" to mapOf(
                    "streaming_mode" to true,
                    "summary" to mapOf("content" to "Thinking...")
                ),
                "body" to mapOf(
                    "elements" to listOf(
                        mapOf(
                            "tag" to "markdown",
                            "content" to initialText,
                            "text_align" to "left",
                            "text_size" to "normal_v2",
                            "element_id" to ELEMENT_ID
                        )
                    )
                )
            )

            // Outer request body: type=card_json, data=JSON string
            val requestBody = mapOf(
                "type" to "card_json",
                "data" to gson.toJson(cardData)
            )

            val result = client.post("/open-apis/cardkit/v1/cards", requestBody)
            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val id = data?.get("card_id")?.asString
                ?: return@withContext Result.failure(Exception("Missing card_id in response"))

            cardId = id
            sequence = 1 // OpenClaw starts at 1
            isOpen = true
            accumulatedText = initialText
            lastUpdateTime = System.currentTimeMillis()

            Log.d(TAG, "Streaming card created: $id")
            Result.success(id)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create streaming card", e)
            Result.failure(e)
        }
    }

    /**
     * 追加文本到流式卡片
     * 对齐 OpenClaw streaming-card-controller.js performFlush()
     *
     * Throttled to max 10 updates/second. Text is accumulated and sent in batch.
     */
    suspend fun appendText(newText: String): Result<Unit> {
        if (!isOpen || cardId == null) {
            return Result.failure(Exception("Streaming card not open"))
        }

        return mutex.withLock {
            try {
                accumulatedText += newText

                // Throttle: skip if too soon since last update
                val now = System.currentTimeMillis()
                if (now - lastUpdateTime < THROTTLE_MS) {
                    return@withLock Result.success(Unit)
                }

                flushUpdate()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update streaming card: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * 关闭流式卡片，显示最终内容
     * 对齐 OpenClaw streaming-card-controller.js closeStreamingAndUpdate()
     *
     * Steps:
     * 1. Flush final content update
     * 2. Close streaming mode via PUT settings
     * 3. (Optional) Update full card with final layout
     */
    suspend fun close(finalText: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isOpen || cardId == null) {
            return@withContext Result.success(Unit)
        }

        try {
            // 1. Final content flush
            mutex.withLock {
                if (finalText != null && finalText != accumulatedText) {
                    accumulatedText = finalText
                    flushUpdate()
                } else if (accumulatedText.isNotEmpty()) {
                    flushUpdate()
                }
            }

            // 2. Close streaming mode — PUT with settings as JSON string + sequence
            sequence++
            val settingsPayload = mapOf(
                "settings" to gson.toJson(mapOf("streaming_mode" to false)),
                "sequence" to sequence
            )

            val result = client.put("/open-apis/cardkit/v1/cards/$cardId/settings", settingsPayload)
            if (result.isFailure) {
                Log.w(TAG, "Failed to close streaming mode: ${result.exceptionOrNull()?.message}")
            }

            isOpen = false
            Log.d(TAG, "Streaming card closed: $cardId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to close streaming card", e)
            isOpen = false
            Result.failure(e)
        }
    }

    fun isActive(): Boolean = isOpen && cardId != null

    /**
     * 刷新累积文本到卡片元素
     * 对齐 OpenClaw cardkit.js updateCardKitElement()
     */
    private suspend fun flushUpdate(): Result<Unit> = withContext(Dispatchers.IO) {
        val id = cardId ?: return@withContext Result.failure(Exception("No card_id"))

        sequence++
        val updatePayload = mapOf(
            "content" to accumulatedText,
            "sequence" to sequence
        )

        val result = client.put(
            "/open-apis/cardkit/v1/cards/$id/elements/$ELEMENT_ID/content",
            updatePayload
        )

        lastUpdateTime = System.currentTimeMillis()

        if (result.isFailure) {
            Log.w(TAG, "Card element update failed: ${result.exceptionOrNull()?.message}")
            return@withContext Result.failure(result.exceptionOrNull()!!)
        }

        Result.success(Unit)
    }
}
