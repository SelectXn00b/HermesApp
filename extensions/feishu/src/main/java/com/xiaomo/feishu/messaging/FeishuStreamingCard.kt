package com.xiaomo.feishu.messaging

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/streaming-card.ts
 *
 * Manages a single streaming card session using Feishu Card Kit API (schema 2.0).
 * Lifecycle: start() → update() (repeated) → close()
 */

import android.util.Log
import com.google.gson.Gson
import com.xiaomo.feishu.FeishuClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 飞书流式卡片会话
 * 对齐 OpenClaw FeishuStreamingSession
 *
 * Uses Card Kit API to create a streaming card that updates in real-time:
 * 1. POST /cardkit/v1/cards — create card with streaming_mode: true
 * 2. PUT /cardkit/v1/cards/{cardId}/elements/{elementId}/content — update content
 * 3. PATCH /cardkit/v1/cards/{cardId}/settings — close streaming mode
 */
class FeishuStreamingCard(
    private val client: FeishuClient
) {
    companion object {
        private const val TAG = "FeishuStreamingCard"
        private const val ELEMENT_ID = "content"
        private const val THROTTLE_MS = 100L // Max 10 updates/second
    }

    private val gson = Gson()
    private val mutex = Mutex() // Serialize updates

    // State
    var cardId: String? = null
        private set
    private var sequence: Int = 0
    private var isOpen: Boolean = false
    private var accumulatedText: String = ""
    private var lastUpdateTime: Long = 0

    /**
     * 创建流式卡片
     * 对齐 OpenClaw FeishuStreamingSession.start()
     *
     * @return cardId on success
     */
    suspend fun start(initialText: String = "Thinking..."): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Aligned with OpenClaw streaming-card.ts:
            // - type: "card_json" (not "card_kit")
            // - data: JSON string (not object)
            // - streaming_mode/streaming_config inside config
            val cardJson = mapOf(
                "schema" to "2.0",
                "config" to mapOf(
                    "wide_screen_mode" to true,
                    "streaming_mode" to true,
                    "summary" to mapOf("content" to "[Generating...]"),
                    "streaming_config" to mapOf(
                        "print_frequency_ms" to mapOf("default" to 50),
                        "print_step" to mapOf("default" to 1)
                    )
                ),
                "body" to mapOf(
                    "elements" to listOf(
                        mapOf(
                            "tag" to "markdown",
                            "content" to initialText,
                            "element_id" to ELEMENT_ID
                        )
                    )
                )
            )

            val cardPayload = mapOf(
                "type" to "card_json",
                "data" to gson.toJson(cardJson)
            )

            val result = client.post("/open-apis/cardkit/v1/cards", cardPayload)
            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val id = data?.get("card_id")?.asString
                ?: return@withContext Result.failure(Exception("Missing card_id in response"))

            cardId = id
            sequence = 0
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
     * 对齐 OpenClaw FeishuStreamingSession.update()
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
                    return@withLock Result.success(Unit) // Text buffered, will be sent on next update
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
     * 对齐 OpenClaw FeishuStreamingSession.close()
     */
    suspend fun close(finalText: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isOpen || cardId == null) {
            return@withContext Result.success(Unit)
        }

        try {
            mutex.withLock {
                // Final content update if provided
                if (finalText != null && finalText != accumulatedText) {
                    accumulatedText = finalText
                    flushUpdate()
                } else if (accumulatedText.isNotEmpty()) {
                    // Flush any buffered text
                    flushUpdate()
                }
            }

            // Close streaming mode
            val settingsPayload = mapOf(
                "settings" to mapOf(
                    "streaming_mode" to false
                )
            )

            val result = client.patch("/open-apis/cardkit/v1/cards/$cardId/settings", settingsPayload)
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

    /**
     * 是否处于打开状态
     */
    fun isActive(): Boolean = isOpen && cardId != null

    /**
     * 刷新累积文本到卡片
     */
    private suspend fun flushUpdate(): Result<Unit> = withContext(Dispatchers.IO) {
        val id = cardId ?: return@withContext Result.failure(Exception("No card_id"))

        sequence++
        val updatePayload = mapOf(
            "content" to accumulatedText,
            "sequence" to sequence,
            "uuid" to UUID.randomUUID().toString()
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
