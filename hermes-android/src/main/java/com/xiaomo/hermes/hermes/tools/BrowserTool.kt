package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Browser Tool — browser automation for agent use.
 * Supports multiple backends: local Chromium, Browserbase, Browser Use.
 * Ported from browser_tool.py
 */
object BrowserTool {

    private const val TAG = "BrowserTool"
    private const val DEFAULT_COMMAND_TIMEOUT = 30
    private const val SNAPSHOT_SUMMARIZE_THRESHOLD = 8000
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    /**
     * Browser session state.
     */
    data class BrowserSession(
        val taskId: String,
        val provider: String,  // "local", "browserbase", "browser_use", "camofox"
        var currentUrl: String? = null,
        var isActive: Boolean = true)

    /**
     * Snapshot result from a browser page.
     */
    data class BrowserSnapshot(
        val url: String? = null,
        val title: String? = null,
        val content: String = "",
        val elements: List<Map<String, String>> = emptyList(),
        val error: String? = null)

    /**
     * Navigation result.
     */
    data class NavigationResult(
        val success: Boolean = false,
        val url: String = "",
        val title: String? = null,
        val error: String? = null)

    private val _sessions = ConcurrentHashMap<String, BrowserSession>()
    private val _httpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_COMMAND_TIMEOUT.toLong(), TimeUnit.SECONDS)
        .readTimeout(DEFAULT_COMMAND_TIMEOUT.toLong(), TimeUnit.SECONDS)
        .build()

    private var _baseUrl: String? = null

    /**
     * Set the base URL for the browser backend (e.g., camofox REST API).
     */
    fun setBaseUrl(url: String) {
        _baseUrl = url.trimEnd('/')
    }

    /**
     * Check if the browser backend is available.
     */
    fun isAvailable(): Boolean {
        val url = _baseUrl ?: System.getenv("CAMOFOX_URL") ?: System.getenv("BROWSER_BACKEND_URL")
        return !url.isNullOrEmpty()
    }

    /**
     * Navigate to a URL.
     */
    fun navigate(url: String, taskId: String = "default"): NavigationResult {
        if (!isSafeUrl(url)) {
            return NavigationResult(error = "URL blocked by SSRF protection: $url")
        }

        val blocked = WebsitePolicy.checkWebsiteAccess(url)
        if (blocked != null) {
            return NavigationResult(error = blocked.message)
        }

        val baseUrl = _baseUrl ?: return NavigationResult(error = "Browser backend not configured")

        return try {
            val payload = gson.toJson(mapOf("action" to "navigate", "url" to url, "task_id" to taskId))
            val request = Request.Builder()
                .url("$baseUrl/navigate")
                .post(payload.toRequestBody(JSON))
                .build()

            _httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return NavigationResult(error = "HTTP ${response.code}: ${body.take(200)}")
                }
                val result = gson.fromJson(body, Map::class.java) as Map<String, Any>
                NavigationResult(
                    success = true,
                    url = result["url"] as? String ?: url,
                    title = result["title"] as? String)
            }
        } catch (e: Exception) {
            NavigationResult(error = "Navigation failed: ${e.message}")
        }
    }

    /**
     * Get a snapshot (accessibility tree) of the current page.
     */
    fun snapshot(taskId: String = "default"): BrowserSnapshot {
        val baseUrl = _baseUrl ?: return BrowserSnapshot(error = "Browser backend not configured")

        return try {
            val payload = gson.toJson(mapOf("action" to "snapshot", "task_id" to taskId))
            val request = Request.Builder()
                .url("$baseUrl/snapshot")
                .post(payload.toRequestBody(JSON))
                .build()

            _httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return BrowserSnapshot(error = "HTTP ${response.code}: ${body.take(200)}")
                }
                val result = gson.fromJson(body, Map::class.java) as Map<String, Any>
                BrowserSnapshot(
                    url = result["url"] as? String,
                    title = result["title"] as? String,
                    content = result["content"] as? String ?: "")
            }
        } catch (e: Exception) {
            BrowserSnapshot(error = "Snapshot failed: ${e.message}")
        }
    }

    /**
     * Click an element by ref selector.
     */
    fun click(ref: String, taskId: String = "default"): Boolean {
        val baseUrl = _baseUrl ?: return false
        return try {
            val payload = gson.toJson(mapOf("action" to "click", "ref" to ref, "task_id" to taskId))
            val request = Request.Builder()
                .url("$baseUrl/click")
                .post(payload.toRequestBody(JSON))
                .build()

            _httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Click failed: ${e.message}")
            false
        }
    }

    /**
     * Type text into an element.
     */
    fun type(ref: String, text: String, taskId: String = "default"): Boolean {
        val baseUrl = _baseUrl ?: return false
        return try {
            val payload = gson.toJson(mapOf("action" to "type", "ref" to ref, "text" to text, "task_id" to taskId))
            val request = Request.Builder()
                .url("$baseUrl/type")
                .post(payload.toRequestBody(JSON))
                .build()

            _httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Type failed: ${e.message}")
            false
        }
    }

    /**
     * Close a browser session.
     */
    fun close(taskId: String = "default") {
        _sessions.remove(taskId)
        val baseUrl = _baseUrl ?: return
        try {
            val payload = gson.toJson(mapOf("action" to "close", "task_id" to taskId))
            val request = Request.Builder()
                .url("$baseUrl/close")
                .post(payload.toRequestBody(JSON))
                .build()
            _httpClient.newCall(request).execute().close()
        } catch (_unused: Exception) {}
    }

    /**
     * Cleanup all sessions.
     */
    fun cleanupAll() {
        val taskIds = _sessions.keys.toList()
        for (taskId in taskIds) {
            close(taskId)
        }
        _sessions.clear()
    }


    // === Missing constants (auto-generated stubs) ===
    val _SANE_PATH = ""
    val BROWSER_SESSION_INACTIVITY_TIMEOUT = 0
    val BROWSER_TOOL_SCHEMAS = ""
    val _BROWSER_SCHEMA_MAP = ""

    // === Missing methods (auto-generated stubs) ===
    private fun discoverHomebrewNodeDirs(): Unit {
    // Hermes: _discover_homebrew_node_dirs
}
}
