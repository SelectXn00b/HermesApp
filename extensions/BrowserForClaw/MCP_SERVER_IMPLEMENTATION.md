# BrowserForClaw MCP Server 实现指南

本文档描述如何为 BrowserForClaw 添加标准 MCP (Model Context Protocol) 支持。

## 概述

MCP 是基于 JSON-RPC 2.0 的标准协议,用于 AI 模型与工具之间的通信。

**当前状态**: BrowserForClaw 使用自定义 REST API
**目标状态**: 同时支持 MCP 和 REST API (向后兼容)

## 架构设计

```
HTTP Request
    ↓
SimpleBrowserHttpServer.kt
    ↓
├─ /mcp (POST) → McpRequestHandler.kt (新增)
│   ├─ initialize
│   ├─ tools/list
│   └─ tools/call
│
└─ /api/browser/execute (POST) → 现有 REST API (保留)
```

## 实现步骤

### Step 1: 创建 MCP 协议类

在 `info.plateaukao.einkbro.mcp` 包下创建:

#### 1.1 JsonRpcModels.kt

```kotlin
package info.plateaukao.einkbro.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON-RPC 2.0 请求
 */
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Any,
    val method: String,
    val params: JSONObject?
) {
    companion object {
        fun fromJson(json: JSONObject): JsonRpcRequest {
            return JsonRpcRequest(
                jsonrpc = json.optString("jsonrpc", "2.0"),
                id = json.opt("id") ?: 0,
                method = json.getString("method"),
                params = json.optJSONObject("params")
            )
        }
    }
}

/**
 * JSON-RPC 2.0 响应
 */
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Any,
    val result: Any?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", jsonrpc)
            put("id", id)
            put("result", result)
        }
    }
}

/**
 * JSON-RPC 2.0 错误响应
 */
data class JsonRpcError(
    val jsonrpc: String = "2.0",
    val id: Any?,
    val error: ErrorObject
) {
    data class ErrorObject(
        val code: Int,
        val message: String,
        val data: Any? = null
    )

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", jsonrpc)
            put("id", id)
            put("error", JSONObject().apply {
                put("code", error.code)
                put("message", error.message)
                error.data?.let { put("data", it) }
            })
        }
    }

    companion object {
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
    }
}
```

#### 1.2 McpRequestHandler.kt

```kotlin
package info.plateaukao.einkbro.mcp

import android.util.Log
import com.forclaw.browser.control.executor.BrowserToolsExecutor
import com.forclaw.browser.control.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP Request Handler
 *
 * 处理 MCP 协议请求 (JSON-RPC 2.0)
 */
class McpRequestHandler(private val toolsExecutor: BrowserToolsExecutor) {

    companion object {
        private const val TAG = "McpRequestHandler"
        private const val PROTOCOL_VERSION = "2024-11-05"
        private const val SERVER_NAME = "BrowserForClaw"
        private const val SERVER_VERSION = "1.0.0"
    }

    /**
     * Handle MCP request
     */
    fun handleRequest(requestBody: String): String {
        return try {
            val json = JSONObject(requestBody)
            val request = JsonRpcRequest.fromJson(json)

            Log.d(TAG, "MCP Request: ${request.method} (id=${request.id})")

            val response = when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> handleToolsList(request)
                "tools/call" -> handleToolCall(request)
                else -> createErrorResponse(
                    request.id,
                    JsonRpcError.METHOD_NOT_FOUND,
                    "Method not found: ${request.method}"
                )
            }

            response.toJson().toString()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling MCP request", e)
            createErrorResponse(
                null,
                JsonRpcError.PARSE_ERROR,
                "Parse error: ${e.message}"
            ).toJson().toString()
        }
    }

    /**
     * Handle initialize
     */
    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        val result = JSONObject().apply {
            put("protocolVersion", PROTOCOL_VERSION)
            put("capabilities", JSONObject().apply {
                put("tools", JSONObject())  // Support tools
            })
            put("serverInfo", JSONObject().apply {
                put("name", SERVER_NAME)
                put("version", SERVER_VERSION)
            })
        }

        return JsonRpcResponse(
            id = request.id,
            result = result
        )
    }

    /**
     * Handle tools/list
     */
    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = JSONArray().apply {
            // 13 browser tools
            put(createToolDefinition("browser_navigate", "Navigate to a URL",
                mapOf("url" to "string")))
            put(createToolDefinition("browser_click", "Click an element",
                mapOf("selector" to "string")))
            put(createToolDefinition("browser_type", "Type text into an element",
                mapOf("selector" to "string", "text" to "string")))
            put(createToolDefinition("browser_get_content", "Get page content",
                mapOf("format" to "string (text/html/markdown)", "selector" to "string?")))
            put(createToolDefinition("browser_screenshot", "Take a screenshot",
                mapOf("fullPage" to "boolean?")))
            put(createToolDefinition("browser_scroll", "Scroll the page",
                mapOf("direction" to "string (up/down)", "amount" to "number?")))
            put(createToolDefinition("browser_wait", "Wait for condition",
                mapOf("timeMs" to "number?", "selector" to "string?", "text" to "string?", "url" to "string?")))
            put(createToolDefinition("browser_execute", "Execute JavaScript",
                mapOf("script" to "string")))
            put(createToolDefinition("browser_hover", "Hover over an element",
                mapOf("selector" to "string")))
            put(createToolDefinition("browser_select", "Select dropdown option",
                mapOf("selector" to "string", "value" to "string")))
            put(createToolDefinition("browser_press", "Press a key",
                mapOf("key" to "string")))
            put(createToolDefinition("browser_get_cookies", "Get cookies",
                mapOf()))
            put(createToolDefinition("browser_set_cookies", "Set cookies",
                mapOf("cookies" to "array")))
        }

        val result = JSONObject().apply {
            put("tools", tools)
        }

        return JsonRpcResponse(
            id = request.id,
            result = result
        )
    }

    /**
     * Handle tools/call
     */
    private fun handleToolCall(request: JsonRpcRequest): Any {
        return try {
            val params = request.params ?: return createErrorResponse(
                request.id,
                JsonRpcError.INVALID_PARAMS,
                "Missing params"
            )

            val toolName = params.optString("name", "")
            val arguments = params.optJSONObject("arguments") ?: JSONObject()

            if (toolName.isEmpty()) {
                return createErrorResponse(
                    request.id,
                    JsonRpcError.INVALID_PARAMS,
                    "Missing tool name"
                )
            }

            Log.d(TAG, "Calling tool: $toolName")

            // Execute tool (synchronously)
            val result = runBlocking {
                toolsExecutor.executeTool(toolName, arguments)
            }

            // Convert ToolResult to MCP format
            val mcpResult = convertToolResultToMcp(result)

            JsonRpcResponse(
                id = request.id,
                result = mcpResult
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool", e)
            createErrorResponse(
                request.id,
                JsonRpcError.INTERNAL_ERROR,
                "Tool execution error: ${e.message}"
            )
        }
    }

    /**
     * Create tool definition
     */
    private fun createToolDefinition(
        name: String,
        description: String,
        properties: Map<String, String>
    ): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    properties.forEach { (key, type) ->
                        put(key, JSONObject().apply {
                            put("type", type.split(" ")[0])
                            if (type.contains("?")) {
                                put("optional", true)
                            }
                        })
                    }
                })
            })
        }
    }

    /**
     * Convert ToolResult to MCP format
     */
    private fun convertToolResultToMcp(result: ToolResult): JSONObject {
        return JSONObject().apply {
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    if (result.success) {
                        // Success: return data as text
                        val content = result.data?.get("content")?.toString()
                            ?: result.data?.toString()
                            ?: "Success"
                        put("text", content)
                    } else {
                        // Error: return error message
                        put("text", result.error ?: "Unknown error")
                    }
                })
            })
            put("isError", !result.success)
        }
    }

    /**
     * Create error response
     */
    private fun createErrorResponse(id: Any?, code: Int, message: String): JsonRpcError {
        return JsonRpcError(
            id = id,
            error = JsonRpcError.ErrorObject(code, message)
        )
    }
}
```

### Step 2: 修改 SimpleBrowserHttpServer.kt

在 `handleClient()` 方法中添加 MCP 路由:

```kotlin
// Add at top of class
private val mcpHandler by lazy {
    McpRequestHandler(BrowserToolsExecutor(browserManager))
}

// In handleClient() method, update routing:
val response = when {
    path == "/health" && method == "GET" -> {
        createJsonResponse(200, mapOf("status" to "ok"))
    }
    path == "/mcp" && method == "POST" -> {
        // MCP endpoint (new)
        handleMcpRequest(body)
    }
    path == "/api/browser/execute" && method == "POST" -> {
        // REST API endpoint (keep for backward compatibility)
        handleExecuteRequest(body)
    }
    method == "OPTIONS" -> {
        createCorsResponse()
    }
    else -> {
        createJsonResponse(404, mapOf("error" to "Not found"))
    }
}

// Add new method
private fun handleMcpRequest(body: String): String {
    return try {
        val mcpResponse = mcpHandler.handleRequest(body)
        "HTTP/1.1 200 OK\r\n" +
        "Content-Type: application/json\r\n" +
        "Access-Control-Allow-Origin: *\r\n" +
        "Content-Length: ${mcpResponse.length}\r\n" +
        "\r\n" +
        mcpResponse
    } catch (e: Exception) {
        Log.e(TAG, "MCP request failed", e)
        createJsonResponse(500, mapOf("error" to e.message))
    }
}
```

## 测试

### 测试 MCP Initialize

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {"tools": {}},
      "clientInfo": {"name": "AndroidForClaw", "version": "1.0.0"}
    }
  }'
```

### 测试 Tools List

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list"
  }'
```

### 测试 Tool Call

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "browser_navigate",
      "arguments": {"url": "https://example.com"}
    }
  }'
```

## 向后兼容

- 旧的 REST API (`/api/browser/execute`) 保持不变
- 现有客户端无需修改即可继续工作
- 新客户端可以选择使用 MCP 协议

## 优势

✅ **标准化** - 使用业界标准 JSON-RPC 2.0 和 MCP
✅ **互操作性** - 可与任何 MCP 客户端兼容
✅ **向后兼容** - 不影响现有集成
✅ **工具发现** - 通过 tools/list 自动发现可用工具
✅ **错误处理** - 标准化的错误码和格式

## 参考资料

- [MCP Specification](https://spec.modelcontextprotocol.io/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
