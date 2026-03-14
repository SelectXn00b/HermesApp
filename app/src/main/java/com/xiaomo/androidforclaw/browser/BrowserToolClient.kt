/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.xiaomo.androidforclaw.browser

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * BrowserForClaw Tool Client
 * Used for phoneforclaw to call browserforclaw browser tools
 *
 * Communication method: HTTP API
 * - Endpoint: POST http://localhost:8080/api/browser/execute
 * - Format: {"tool": "browser_navigate", "args": {"url": "https://..."}}
 */
class BrowserToolClient(private val context: Context) {

    companion object {
        private const val TAG = "BrowserToolClient"
        // BrowserForClaw uses port 8765 (AndroidForClaw Gateway uses 8080)
        private const val BROWSER_API_URL = "http://localhost:8765/api/browser/execute"
        private const val HEALTH_CHECK_URL = "http://localhost:8765/health"
        private const val DEFAULT_TIMEOUT = 30000L  // 30 seconds

        // BrowserForClaw startup info
        private const val BROWSER_PACKAGE = "info.plateaukao.einkbro"
        private const val BROWSER_ACTIVITY = "info.plateaukao.einkbro/.activity.BrowserActivity"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Tool execution result
     */
    data class ToolResult(
        val success: Boolean,
        val data: Map<String, Any?>? = null,
        val error: String? = null
    ) {
        override fun toString(): String {
            return if (success) {
                data?.get("content")?.toString() ?: data.toString()
            } else {
                "Error: $error"
            }
        }

        fun toJsonString(): String {
            return if (success) {
                JSONObject(data ?: emptyMap<String, Any?>()).toString()
            } else {
                JSONObject(mapOf("error" to error)).toString()
            }
        }
    }

    /**
     * Check if BrowserForClaw is running
     */
    private suspend fun checkBrowserHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(HEALTH_CHECK_URL)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val isHealthy = response.isSuccessful
            Log.d(TAG, "Browser health check: $isHealthy")
            isHealthy
        } catch (e: Exception) {
            Log.d(TAG, "Browser not running: ${e.message}")
            false
        }
    }

    /**
     * Start BrowserForClaw
     */
    private suspend fun startBrowser(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting BrowserForClaw...")
            val intent = android.content.Intent.parseUri(
                "intent://#Intent;component=$BROWSER_ACTIVITY;end",
                0
            )
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            // Wait for browser and HTTP service to start
            // BrowserForClaw needs time to start HTTP API service (port 8766)
            Log.d(TAG, "Waiting for browser and HTTP API to start...")

            // Poll health status, wait up to 5 seconds
            var attempts = 0
            val maxAttempts = 10
            while (attempts < maxAttempts) {
                kotlinx.coroutines.delay(500)
                if (checkBrowserHealth()) {
                    Log.d(TAG, "✅ Browser HTTP API is ready (attempt ${attempts + 1})")
                    return@withContext true
                }
                attempts++
                Log.d(TAG, "⏳ Waiting for HTTP API... (attempt ${attempts}/$maxAttempts)")
            }

            // Final check after timeout
            val isRunning = checkBrowserHealth()
            Log.w(TAG, "❌ Browser HTTP API not responding after ${maxAttempts * 500}ms. Health: $isRunning")
            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start browser", e)
            false
        }
    }

    /**
     * Ensure BrowserForClaw is running
     * With timeout protection to avoid infinite waiting
     */
    private suspend fun ensureBrowserRunning(): ToolResult {
        return try {
            // Overall timeout 10 seconds
            withTimeout(10000L) {
                // 1. Check if already running
                if (checkBrowserHealth()) {
                    Log.d(TAG, "Browser already running")
                    return@withTimeout ToolResult(success = true)
                }

                // 2. Attempt to start
                Log.d(TAG, "Browser not running, attempting to start...")
                val started = startBrowser()

                if (started) {
                    ToolResult(success = true, data = mapOf("message" to "Browser started"))
                } else {
                    ToolResult(
                        success = false,
                        error = "Failed to start BrowserForClaw HTTP API (port 8765). " +
                                "App is installed ($BROWSER_PACKAGE) but API service not responding. " +
                                "Please ensure BrowserForClaw is the correct version with HTTP API support."
                    )
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Browser startup timeout")
            ToolResult(
                success = false,
                error = "Browser startup timeout (10s). BrowserForClaw may not be installed or not responding."
            )
        }
    }

    /**
     * Execute browser tool asynchronously
     *
     * @param tool Tool name (e.g. "browser_navigate")
     * @param args Tool arguments
     * @param timeout Timeout in milliseconds, default 30 seconds
     * @return Tool execution result
     */
    suspend fun executeToolAsync(
        tool: String,
        args: Map<String, Any?>,
        timeout: Long = DEFAULT_TIMEOUT
    ): ToolResult {
        return try {
            withTimeout(timeout) {
                withContext(Dispatchers.IO) {
                    // Ensure BrowserForClaw is running
                    val ensureResult = ensureBrowserRunning()
                    if (!ensureResult.success) {
                        return@withContext ensureResult
                    }

                    try {
                        Log.d(TAG, "Executing tool: $tool")
                        Log.d(TAG, "Arguments: $args")

                        // Build JSON request
                        val requestJson = JSONObject().apply {
                            put("tool", tool)
                            put("args", JSONObject(args))
                        }

                        Log.d(TAG, "Request JSON: $requestJson")

                        // Build HTTP request
                        val requestBody = requestJson.toString()
                            .toRequestBody("application/json".toMediaType())

                        val request = Request.Builder()
                            .url(BROWSER_API_URL)
                            .post(requestBody)
                            .build()

                        // Send request
                        val response = httpClient.newCall(request).execute()
                        val responseBody = response.body?.string() ?: ""

                        Log.d(TAG, "Response status: ${response.code}")
                        Log.d(TAG, "Response body: ${responseBody.take(500)}")

                        if (!response.isSuccessful) {
                            return@withContext ToolResult(
                                success = false,
                                error = "HTTP ${response.code}: $responseBody"
                            )
                        }

                        // Parse response
                        val responseJson = JSONObject(responseBody)
                        val success = responseJson.optBoolean("success", false)
                        val error = if (responseJson.has("error")) responseJson.optString("error") else null
                        val dataJson = responseJson.optJSONObject("data")

                        val result = if (success) {
                            val data = if (dataJson != null) parseJsonToMap(dataJson) else emptyMap()
                            ToolResult(success = true, data = data)
                        } else {
                            ToolResult(success = false, error = error ?: "Unknown error")
                        }

                        Log.d(TAG, "Tool execution result: success=${result.success}")
                        result

                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to execute tool", e)
                        ToolResult(success = false, error = "Exception: ${e.message}")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Browser tool execution timeout: $tool (timeout=${timeout}ms)")
            ToolResult(
                success = false,
                error = "Browser operation timeout (${timeout}ms). Tool: $tool"
            )
        }
    }

    /**
     * Get page content
     */
    suspend fun getContent(format: String = "text", selector: String? = null): ToolResult {
        val toolArgs = mutableMapOf<String, Any?>("format" to format)
        if (selector != null) {
            toolArgs["selector"] = selector
        }
        return executeToolAsync("browser_get_content", toolArgs)
    }

    /**
     * Wait for specified time
     */
    suspend fun waitTime(timeMs: Long): ToolResult {
        return executeToolAsync("browser_wait", mapOf("timeMs" to timeMs))
    }

    /**
     * Wait for element to appear
     */
    suspend fun waitForSelector(selector: String, timeout: Long = 10000L): ToolResult {
        return executeToolAsync("browser_wait", mapOf("selector" to selector, "timeout" to timeout), timeout)
    }

    /**
     * Wait for text to appear
     */
    suspend fun waitForText(text: String, timeout: Long = 10000L): ToolResult {
        return executeToolAsync("browser_wait", mapOf("text" to text, "timeout" to timeout), timeout)
    }

    /**
     * Wait for URL match
     */
    suspend fun waitForUrl(url: String, timeout: Long = 10000L): ToolResult {
        return executeToolAsync("browser_wait", mapOf("url" to url, "timeout" to timeout), timeout)
    }

    /**
     * Convert JSONObject to Map
     */
    private fun parseJsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = json.opt(key)
        }
        return map
    }
}
