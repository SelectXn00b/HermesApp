package com.xiaomo.hermes.hermes.gateway

/**
 * Delivery router — sends outbound messages to the correct platform adapter.
 *
 * The router maintains a map of platform-name → adapter and provides
 * convenience methods for sending text, images, and documents.
 *
 * Ported from gateway/delivery.py
 */

import android.util.Log
import com.xiaomo.hermes.hermes.gateway.platforms.BasePlatformAdapter

/**
 * Result of a delivery attempt.
 */
data class DeliveryResult(
    /** Whether the message was successfully delivered. */
    val success: Boolean,
    /** Platform-specific message id (if available). */
    val messageId: String? = null,
    /** Error message (if success == false). */
    val error: String? = null,
    /** Raw response from the platform (if available). */
    val rawResponse: Any? = null)

/**
 * Delivery router.
 *
 * Platform adapters register themselves on startup.  The router dispatches
 * outbound messages to the correct adapter based on the platform name
 * embedded in the session key.
 */
class DeliveryRouter {
    companion object {
        private const val _TAG = "DeliveryRouter"
    }

    /** Platform name → adapter. */
    private val _adapters: java.util.concurrent.ConcurrentHashMap<String, BasePlatformAdapter> = java.util.concurrent.ConcurrentHashMap()

    /** Register an adapter. */
    fun register(adapter: BasePlatformAdapter) {
        _adapters[adapter.name] = adapter
        Log.i(_TAG, "Registered adapter for platform: ${adapter.name}")
    }

    /** Unregister an adapter. */
    fun unregister(platformName: String) {
        _adapters.remove(platformName)
        Log.i(_TAG, "Unregistered adapter for platform: $platformName")
    }

    /** Get an adapter by platform name. */
    fun getAdapter(platformName: String): BasePlatformAdapter? = _adapters[platformName]

    /** True when at least one adapter is registered. */
    val hasAdapters: Boolean get() = _adapters.isNotEmpty()

    /** All registered platform names. */
    val platformNames: Set<String> get() = _adapters.keys.toSet()

    /**
     * Deliver a text message.
     */
    suspend fun deliverText(
        platform: String,
        chatId: String,
        text: String,
        replyTo: String? = null): DeliveryResult {
        val adapter = _adapters[platform]
        if (adapter == null) {
            Log.w(_TAG, "No adapter for platform: $platform")
            return DeliveryResult(success = false, error = "No adapter for platform: $platform")
        }
        return try {
            val result = adapter.send(chatId, text, replyTo, null)
            DeliveryResult(success = result.success, messageId = result.messageId, error = result.error, rawResponse = result.rawResponse)
        } catch (e: Exception) {
            Log.e(_TAG, "Delivery failed for $platform:$chatId: ${e.message}")
            DeliveryResult(success = false, error = e.message)
        }
    }

    /**
     * Send a typing indicator.
     */
    suspend fun sendTyping(platform: String, chatId: String) {
        val adapter = _adapters[platform] ?: return
        try {
            adapter.sendTyping(chatId, null)
        } catch (e: Exception) {
            Log.w(_TAG, "Typing indicator failed for $platform:$chatId: ${e.message}")
        }
    }
}

/**
 * A single delivery target.
 * Ported from DeliveryTarget in gateway/delivery.py
 */
data class DeliveryTarget(
    val platform: String,
    val chatId: String? = null,
    val threadId: String? = null,
    val isOrigin: Boolean = false,
    val isExplicit: Boolean = false) {
    companion object {
        /** Parse a delivery target string. */
        fun parse(target: String, originPlatform: String? = null, originChatId: String? = null, originThreadId: String? = null): DeliveryTarget {
            val t = target.trim().lowercase()
            if (t == "origin") {
                if (originPlatform != null) return DeliveryTarget(platform = originPlatform, chatId = originChatId, threadId = originThreadId, isOrigin = true)
                return DeliveryTarget(platform = "local", isOrigin = true)
            }
            if (t == "local") return DeliveryTarget(platform = "local")
            if (":" in t) {
                val parts = t.split(":", limit = 3)
                return DeliveryTarget(platform = parts[0], chatId = parts.getOrNull(1), threadId = parts.getOrNull(2), isExplicit = true)
            }
            return DeliveryTarget(platform = t)
        }
    }
}
