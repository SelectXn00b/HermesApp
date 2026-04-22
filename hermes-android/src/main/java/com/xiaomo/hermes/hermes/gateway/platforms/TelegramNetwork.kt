package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * Telegram network helper — low-level HTTP/JSON plumbing for the
 * Telegram Bot API.
 *
 * This module is not a stand-alone adapter; it is consumed by
 * TelegramAdapter (and potentially other modules) to share common
 * retry/backoff logic and to expose a thin async wrapper around
 * ``requests`` / ``httpx``.
 *
 * Ported from gateway/platforms/telegram_network.py
 */

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Telegram network errors.
 */
sealed class TelegramNetworkError(message: String) : RuntimeException(message) {
    class NotFound(message: String) : TelegramNetworkError(message)
    class Forbidden(message: String) : TelegramNetworkError(message)
    class TooManyRequests(message: String, val retryAfter: Int) : TelegramNetworkError(message)
    class ServerError(message: String) : TelegramNetworkError(message)
    class NetworkError(message: String) : TelegramNetworkError(message)
    class RateLimitError(message: String, val retryAfter: Int) : TelegramNetworkError(message)
}

/**
 * Telegram network client — wraps OkHttp with retry/backoff logic.
 *
 * This is a lower-level utility used by TelegramAdapter and other
 * Telegram-related modules.
 */
class TelegramNetworkClient(
    /** Bot token. */
    private val token: String,
    /** Base URL for the Telegram API. */
    private val baseUrl: String = "https://api.telegram.org",
    /** Maximum number of retries. */
    private val maxRetries: Int = 3,
    /** Base delay for exponential backoff (milliseconds). */
    private val baseDelayMs: Long = 1000,
    /** Maximum delay for exponential backoff (milliseconds). */
    private val maxDelayMs: Long = 30000,
    /** Connection timeout (seconds). */
    private val connectTimeoutSeconds: Long = 15,
    /** Read timeout (seconds). */
    private val readTimeoutSeconds: Long = 30) {
    companion object {
        private const val _TAG = "TelegramNetworkClient"
    }

    private val _httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Number of requests made. */
    val requestCount = AtomicInteger(0)

    /** Number of failed requests. */
    val errorCount = AtomicInteger(0)

    /**
     * Make a GET request to the Telegram API.
     *
     * @param endpoint  API endpoint (e.g. "getMe").
     * @param params    Optional query parameters.
     * @return JSON response.
     */
    suspend fun get(endpoint: String, params: Map<String, String>? = null): JSONObject {
        val url = buildString {
            append("$baseUrl/bot$token/$endpoint")
            if (params != null && params.isNotEmpty()) {
                append("?")
                append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
            }
        }

        return _executeWithRetry {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            _httpClient.newCall(request).execute()
        }
    }

    /**
     * Make a POST request to the Telegram API.
     *
     * @param endpoint  API endpoint (e.g. "sendMessage").
     * @param body      Request body (JSON or form data).
     * @return JSON response.
     */
    suspend fun post(endpoint: String, body: RequestBody): JSONObject {
        val url = "$baseUrl/bot$token/$endpoint"

        return _executeWithRetry {
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            _httpClient.newCall(request).execute()
        }
    }

    /**
     * Make a POST request with JSON body.
     */
    suspend fun postJson(endpoint: String, json: JSONObject): JSONObject {
        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        return post(endpoint, body)
    }

    /**
     * Download a file from the Telegram API.
     *
     * @param filePath  File path from getFile response.
     * @return File bytes.
     */
    suspend fun downloadFile(filePath: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "$baseUrl/file/bot$token/$filePath"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        _httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw TelegramNetworkError.NetworkError("Download failed: HTTP ${resp.code}")
            }
            resp.body!!.bytes()
        }
    }

    /**
     * Get the file URL for a file_id.
     *
     * @param fileId  File ID from a message.
     * @return File URL or null.
     */
    suspend fun getFileUrl(fileId: String): String? {
        val response = get("getUpdates", mapOf("file_id" to fileId))
        if (!response.optBoolean("ok", false)) return null
        val result = response.optJSONObject("result") ?: return null
        val filePath = result.optString("file_path") ?: return null
        return "$baseUrl/file/bot$token/$filePath"
    }

    // ------------------------------------------------------------------
    // Retry/backoff logic
    // ------------------------------------------------------------------

    /**
     * Execute an HTTP request with retry/backoff logic.
     *
     * Retries on:
     * - HTTP 429 (Too Many Requests)
     * - HTTP 5xx (Server errors)
     * - Network errors
     *
     * Does NOT retry on:
     * - HTTP 400 (Bad request)
     * - HTTP 401 (Unauthorized)
     * - HTTP 403 (Forbidden)
     * - HTTP 404 (Not found)
     */
    private suspend fun _executeWithRetry(
        block: () -> okhttp3.Response): JSONObject = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        for (attempt in 0 until maxRetries) {
            requestCount.incrementAndGet()

            try {
                block().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""

                    when (resp.code) {
                        in 200..299 -> {
                            return@withContext JSONObject(bodyStr)
                        }
                        400 -> {
                            throw TelegramNetworkError.NetworkError("Bad request: HTTP 400: $bodyStr")
                        }
                        401 -> {
                            throw TelegramNetworkError.Forbidden("Unauthorized: HTTP 401")
                        }
                        403 -> {
                            throw TelegramNetworkError.Forbidden("Forbidden: HTTP 403: $bodyStr")
                        }
                        404 -> {
                            throw TelegramNetworkError.NotFound("Not found: HTTP 404: $bodyStr")
                        }
                        429 -> {
                            val data = JSONObject(bodyStr)
                            val retryAfter = data.optInt("retry_after", 1)
                            if (attempt < maxRetries - 1) {
                                Log.w(_TAG, "Rate limited, retrying after ${retryAfter}s")
                                delay(retryAfter * 1000L)
                                continue
                            }
                            throw TelegramNetworkError.TooManyRequests("Rate limited", retryAfter)
                        }
                        in 500..599 -> {
                            if (attempt < maxRetries - 1) {
                                val delayMs = baseDelayMs * (1 shl attempt)
                                Log.w(_TAG, "Server error HTTP ${resp.code}, retrying after ${delayMs}ms")
                                delay(delayMs)
                                continue
                            }
                            throw TelegramNetworkError.ServerError("Server error: HTTP ${resp.code}: $bodyStr")
                        }
                        else -> {
                            throw TelegramNetworkError.NetworkError("Unexpected HTTP ${resp.code}: $bodyStr")
                        }
                    }
                }
            } catch (e: TelegramNetworkError) {
                throw e
            } catch (e: Exception) {
                lastError = e
                errorCount.incrementAndGet()
                if (attempt < maxRetries - 1) {
                    val delayMs = baseDelayMs * (1 shl attempt)
                    Log.w(_TAG, "Network error: ${e.message}, retrying after ${delayMs}ms")
                    delay(delayMs)
                    continue
                }
                throw TelegramNetworkError.NetworkError("Network error after $maxRetries retries: ${e.message}")
            }
        }

        throw lastError ?: TelegramNetworkError.NetworkError("Unknown error after $maxRetries retries")
    }
}

/**
 * Telegram-specific network errors.
 */
sealed class TelegramError(message: String) : RuntimeException(message) {
    /** File not found or too large. */
    class FileNotFound(message: String) : TelegramError(message)

    /** Forbidden: bot was blocked or kicked. */
    class BotBlocked(message: String) : TelegramError(message)

    /** Forbidden: not enough rights. */
    class NotEnoughRights(message: String) : TelegramError(message)

    /** Forbidden: user is deactivated. */
    class UserDeactivated(message: String) : TelegramError(message)

    /** Chat not found. */
    class ChatNotFound(message: String) : TelegramError(message)

    /** Message not found. */
    class MessageNotFound(message: String) : TelegramError(message)

    /** Message is not modified. */
    class MessageNotModified(message: String) : TelegramError(message)

    /** Message to edit not found. */
    class MessageToEditNotFound(message: String) : TelegramError(message)

    /** Message to delete not found. */
    class MessageToDeleteNotFound(message: String) : TelegramError(message)

    /** Message can't be deleted. */
    class MessageCantBeDeleted(message: String) : TelegramError(message)

    /** Message can't be edited. */
    class MessageCantBeEdited(message: String) : TelegramError(message)

    /** Message is too long. */
    class MessageTooLong(message: String) : TelegramError(message)

    /** Message text is empty. */
    class MessageTextEmpty(message: String) : TelegramError(message)

    /** Message identifier is not specified. */
    class MessageIdNotSpecified(message: String) : TelegramError(message)

    /** Message thread not found. */
    class MessageThreadNotFound(message: String) : TelegramError(message)

    /** Unknown error. */
    class Unknown(message: String) : TelegramError(message)

    companion object {
        /**
         * Map a Telegram API error description to a specific exception.
         */
        fun fromDescription(description: String, code: Int): TelegramError = when {
            "message is not modified" in description -> MessageNotModified(description)
            "message to edit not found" in description -> MessageToEditNotFound(description)
            "message to delete not found" in description -> MessageToDeleteNotFound(description)
            "message can't be deleted" in description -> MessageCantBeDeleted(description)
            "message can't be edited" in description -> MessageCantBeEdited(description)
            "message is too long" in description -> MessageTooLong(description)
            "message text is empty" in description -> MessageTextEmpty(description)
            "message identifier is not specified" in description -> MessageIdNotSpecified(description)
            "message thread not found" in description -> MessageThreadNotFound(description)
            "bot was blocked" in description -> BotBlocked(description)
            "not enough rights" in description -> NotEnoughRights(description)
            "user is deactivated" in description -> UserDeactivated(description)
            "chat not found" in description -> ChatNotFound(description)
            "message not found" in description -> MessageNotFound(description)
            "file not found" in description -> FileNotFound(description)
            else -> Unknown(description)
        }
    }
}

/**
 * Telegram-specific rate-limit helper.
 *
 * Tracks the last rate-limit response and enforces a minimum delay
 * between requests to the same endpoint.
 */
class TelegramRateLimiter {
    /** Endpoint → last rate-limit timestamp. */
    private val _lastLimits: MutableMap<String, Long> = mutableMapOf()

    /** Endpoint → minimum delay between requests (ms). */
    private val _minDelays: MutableMap<String, Long> = mutableMapOf()

    /**
     * Check whether a request to [endpoint] should be rate-limited.
     *
     * Returns the delay (ms) to wait before making the request, or 0 if
     * no delay is needed.
     */
    fun checkRateLimit(endpoint: String): Long {
        val lastLimit = _lastLimits[endpoint] ?: return 0
        val minDelay = _minDelays[endpoint] ?: return 0
        val elapsed = System.currentTimeMillis() - lastLimit
        return if (elapsed < minDelay) minDelay - elapsed else 0
    }

    /**
     * Record a rate-limit response for [endpoint].
     */
    fun recordRateLimit(endpoint: String, retryAfterSeconds: Int) {
        _lastLimits[endpoint] = System.currentTimeMillis()
        _minDelays[endpoint] = retryAfterSeconds * 1000L
    }

    /**
     * Clear rate-limit state for [endpoint].
     */
    fun clearRateLimit(endpoint: String) {
        _lastLimits.remove(endpoint)
        _minDelays.remove(endpoint)
    }

    /**
     * Clear all rate-limit state.
     */
    fun clearAll() {
        _lastLimits.clear()
        _minDelays.clear()
    }
}

class TelegramFallbackTransport {
    /**
     * Handle an async request with fallback IP retry logic.
     *
     * On Android, the httpx/asyncio-based fallback transport is not available.
     * This stub returns null, deferring to the primary OkHttp transport in
     * TelegramNetworkClient which already has retry/backoff built in.
     */
    suspend fun handleAsyncRequest(request: Any?): Any? {
        // Android: fallback IP transport not supported (requires httpx AsyncBaseTransport).
        // TelegramNetworkClient handles retries directly via OkHttp.
        return null
    }

    /**
     * Close the transport and release resources.
     *
     * No-op on Android since we don't hold httpx transports.
     */
    suspend fun aclose() {
        // No resources to release on Android
    }
}
