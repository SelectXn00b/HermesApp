/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/mcp/(all)
 *
 * AndroidForClaw adaptation: MCP client integration.
 */
package com.xiaomo.androidforclaw.mcp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP (Model Context Protocol) HTTP Client
 *
 * 支持标准 MCP over HTTP transport
 * 使用 JSON-RPC 2.0 协议
 */
class McpClient(
    private val baseUrl: String,
    private val clientName: String = "AndroidForClaw",
    private val clientVersion: String = "1.0.0"
) {
    companion object {
        private const val TAG = "McpClient"
        private const val MCP_ENDPOINT = "/mcp"
        private const val PROTOCOL_VERSION = "2024-11-05"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val requestIdCounter = AtomicLong(1)
    private var initialized = false

    /**
     * Initialize MCP connection
     */
    suspend fun initialize(): Result<McpInitializeResult> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf(
                "protocolVersion" to PROTOCOL_VERSION,
                "capabilities" to mapOf(
                    "tools" to emptyMap<String, Any>()
                ),
                "clientInfo" to mapOf(
                    "name" to clientName,
                    "version" to clientVersion
                )
            )

            val response = sendRequest("initialize", params)

            if (response.isSuccess) {
                val result = response.getOrNull()!!
                val resultMap = result.result as? Map<*, *>

                if (resultMap != null) {
                    initialized = true
                    val initResult = McpInitializeResult(
                        protocolVersion = resultMap["protocolVersion"] as? String ?: PROTOCOL_VERSION,
                        capabilities = (resultMap["capabilities"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                            ?.mapValues { it.value } ?: emptyMap(),
                        serverInfo = McpInitializeResult.ServerInfo(
                            name = ((resultMap["serverInfo"] as? Map<*, *>)?.get("name") as? String) ?: "Unknown",
                            version = ((resultMap["serverInfo"] as? Map<*, *>)?.get("version") as? String) ?: "Unknown"
                        )
                    )
                    Result.success(initResult)
                } else {
                    Result.failure(Exception("Invalid initialize response"))
                }
            } else {
                Result.failure(response.exceptionOrNull() ?: Exception("Initialize failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed", e)
            Result.failure(e)
        }
    }

    /**
     * List available tools
     */
    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            val response = sendRequest("tools/list", null)

            if (response.isSuccess) {
                val result = response.getOrNull()!!
                val resultMap = result.result as? Map<*, *>
                val toolsList = (resultMap?.get("tools") as? List<*>) ?: emptyList<Any>()

                val tools = toolsList.mapNotNull { toolData ->
                    val toolMap = toolData as? Map<*, *> ?: return@mapNotNull null
                    McpTool(
                        name = toolMap["name"] as? String ?: return@mapNotNull null,
                        description = toolMap["description"] as? String ?: "",
                        inputSchema = (toolMap["inputSchema"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                            ?.mapValues { it.value } ?: emptyMap()
                    )
                }

                Result.success(tools)
            } else {
                Result.failure(response.exceptionOrNull() ?: Exception("List tools failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "List tools failed", e)
            Result.failure(e)
        }
    }

    /**
     * Call a tool
     */
    suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>? = null
    ): Result<McpToolCallResult> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            val params = mutableMapOf<String, Any?>("name" to name)
            if (arguments != null) {
                params["arguments"] = arguments
            }

            val response = sendRequest("tools/call", params)

            if (response.isSuccess) {
                val result = response.getOrNull()!!
                val resultMap = result.result as? Map<*, *>

                if (resultMap != null) {
                    val contentList = (resultMap["content"] as? List<*>) ?: emptyList<Any>()
                    val isError = resultMap["isError"] as? Boolean ?: false

                    val content = contentList.mapNotNull { contentData ->
                        val contentMap = contentData as? Map<*, *> ?: return@mapNotNull null
                        McpToolCallResult.ContentItem(
                            type = contentMap["type"] as? String ?: return@mapNotNull null,
                            text = contentMap["text"] as? String,
                            data = contentMap["data"] as? String,
                            mimeType = contentMap["mimeType"] as? String
                        )
                    }

                    Result.success(McpToolCallResult(content, isError))
                } else {
                    Result.failure(Exception("Invalid tool call response"))
                }
            } else {
                Result.failure(response.exceptionOrNull() ?: Exception("Tool call failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool call failed: $name", e)
            Result.failure(e)
        }
    }

    /**
     * Send JSON-RPC request
     */
    private suspend fun sendRequest(
        method: String,
        params: Map<String, Any?>?
    ): Result<JsonRpcResponse> = withContext(Dispatchers.IO) {
        try {
            val requestId = requestIdCounter.getAndIncrement()
            val request = JsonRpcRequest(
                id = requestId,
                method = method,
                params = params
            )

            Log.d(TAG, "Sending request: $method (id=$requestId)")

            val requestBody = request.toJson().toString()
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl$MCP_ENDPOINT")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Response status: ${response.code}")
            Log.d(TAG, "Response body: ${responseBody.take(500)}")

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            // Parse response
            val responseJson = JSONObject(responseBody)

            if (responseJson.has("error")) {
                val error = JsonRpcError.fromJson(responseJson)
                return@withContext Result.failure(
                    Exception("JSON-RPC Error ${error.error.code}: ${error.error.message}")
                )
            }

            val rpcResponse = JsonRpcResponse.fromJson(responseJson)
            Result.success(rpcResponse)

        } catch (e: Exception) {
            Log.e(TAG, "Request failed: $method", e)
            Result.failure(e)
        }
    }

    /**
     * Ensure client is initialized
     */
    private suspend fun ensureInitialized() {
        if (!initialized) {
            val result = initialize()
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Initialization failed")
            }
        }
    }

    /**
     * Check server health
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.d(TAG, "Health check failed: ${e.message}")
            false
        }
    }
}
