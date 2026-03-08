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
 * browserforclaw 工具客户端
 * 用于 phoneforclaw 调用 browserforclaw 的浏览器工具
 *
 * 通信方式: HTTP API
 * - 端点: POST http://localhost:8080/api/browser/execute
 * - 格式: {"tool": "browser_navigate", "args": {"url": "https://..."}}
 */
class BrowserToolClient(private val context: Context) {

    companion object {
        private const val TAG = "BrowserToolClient"
        // Use port 8766 to avoid conflict with AndroidForClaw Gateway (8765)
        private const val BROWSER_API_URL = "http://localhost:8766/api/browser/execute"
        private const val HEALTH_CHECK_URL = "http://localhost:8766/health"
        private const val DEFAULT_TIMEOUT = 30000L  // 30 秒

        // BrowserForClaw 启动信息
        private const val BROWSER_PACKAGE = "info.plateaukao.einkbro"
        private const val BROWSER_ACTIVITY = "info.plateaukao.einkbro/.activity.BrowserActivity"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 工具执行结果
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
     * 检查 BrowserForClaw 是否运行
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
     * 启动 BrowserForClaw
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

            // 等待浏览器启动和 HTTP 服务启动
            // BrowserForClaw 需要时间启动 HTTP API 服务 (端口 8766)
            Log.d(TAG, "Waiting for browser and HTTP API to start...")

            // 轮询检查健康状态,最多等待 5 秒
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

            // 超时后最后一次检查
            val isRunning = checkBrowserHealth()
            Log.w(TAG, "❌ Browser HTTP API not responding after ${maxAttempts * 500}ms. Health: $isRunning")
            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start browser", e)
            false
        }
    }

    /**
     * 确保 BrowserForClaw 正在运行
     * 带超时保护,避免无限等待
     */
    private suspend fun ensureBrowserRunning(): ToolResult {
        return try {
            // 整体超时 10 秒
            withTimeout(10000L) {
                // 1. 检查是否已运行
                if (checkBrowserHealth()) {
                    Log.d(TAG, "Browser already running")
                    return@withTimeout ToolResult(success = true)
                }

                // 2. 尝试启动
                Log.d(TAG, "Browser not running, attempting to start...")
                val started = startBrowser()

                if (started) {
                    ToolResult(success = true, data = mapOf("message" to "Browser started"))
                } else {
                    ToolResult(
                        success = false,
                        error = "Failed to start BrowserForClaw HTTP API (port 8766). " +
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
     * 异步执行浏览器工具
     *
     * @param tool 工具名称（如 "browser_navigate"）
     * @param args 工具参数
     * @param timeout 超时时间（毫秒），默认 30 秒
     * @return 工具执行结果
     */
    suspend fun executeToolAsync(
        tool: String,
        args: Map<String, Any?>,
        timeout: Long = DEFAULT_TIMEOUT
    ): ToolResult {
        return try {
            withTimeout(timeout) {
                withContext(Dispatchers.IO) {
                    // 确保 BrowserForClaw 正在运行
                    val ensureResult = ensureBrowserRunning()
                    if (!ensureResult.success) {
                        return@withContext ensureResult
                    }

                    try {
                        Log.d(TAG, "执行工具: $tool")
                        Log.d(TAG, "参数: $args")

                        // 构建 JSON 请求
                        val requestJson = JSONObject().apply {
                            put("tool", tool)
                            put("args", JSONObject(args))
                        }

                        Log.d(TAG, "请求 JSON: $requestJson")

                        // 构建 HTTP 请求
                        val requestBody = requestJson.toString()
                            .toRequestBody("application/json".toMediaType())

                        val request = Request.Builder()
                            .url(BROWSER_API_URL)
                            .post(requestBody)
                            .build()

                        // 发送请求
                        val response = httpClient.newCall(request).execute()
                        val responseBody = response.body?.string() ?: ""

                        Log.d(TAG, "响应状态: ${response.code}")
                        Log.d(TAG, "响应体: ${responseBody.take(500)}")

                        if (!response.isSuccessful) {
                            return@withContext ToolResult(
                                success = false,
                                error = "HTTP ${response.code}: $responseBody"
                            )
                        }

                        // 解析响应
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

                        Log.d(TAG, "工具执行结果: success=${result.success}")
                        result

                    } catch (e: Exception) {
                        Log.e(TAG, "执行工具失败", e)
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
     * 获取页面内容
     */
    suspend fun getContent(format: String = "text", selector: String? = null): ToolResult {
        val toolArgs = mutableMapOf<String, Any?>("format" to format)
        if (selector != null) {
            toolArgs["selector"] = selector
        }
        return executeToolAsync("browser_get_content", toolArgs)
    }

    /**
     * 等待指定时间
     */
    suspend fun waitTime(timeMs: Long): ToolResult {
        return executeToolAsync("browser_wait", mapOf("timeMs" to timeMs))
    }

    /**
     * 等待元素出现
     */
    suspend fun waitForSelector(selector: String, timeout: Long = 10000L): ToolResult {
        return executeToolAsync("browser_wait", mapOf("selector" to selector, "timeout" to timeout), timeout)
    }

    /**
     * 等待文本出现
     */
    suspend fun waitForText(text: String, timeout: Long = 10000L): ToolResult {
        return executeToolAsync("browser_wait", mapOf("text" to text, "timeout" to timeout), timeout)
    }

    /**
     * 等待 URL 匹配
     */
    suspend fun waitForUrl(url: String, timeout: Long = 10000L): ToolResult {
        return executeToolAsync("browser_wait", mapOf("url" to url, "timeout" to timeout), timeout)
    }

    /**
     * 将 JSONObject 转换为 Map
     */
    private fun parseJsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = json.opt(key)
        }
        return map
    }
}
