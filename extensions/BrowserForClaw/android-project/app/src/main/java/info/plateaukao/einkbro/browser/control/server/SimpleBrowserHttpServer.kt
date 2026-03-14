/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package info.plateaukao.einkbro.browser.control.server

import android.util.Log
import com.forclaw.browser.control.executor.BrowserToolsExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Simple HTTP Server for Browser Control
 *
 * 监听端口 8080，处理浏览器控制请求
 */
class SimpleBrowserHttpServer(private val port: Int = 8080) {

    companion object {
        private const val TAG = "BrowserHttpServer"
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) {
            Log.w(TAG, "Server already running")
            return
        }

        thread {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.e(TAG, "✅ HTTP Server started on port $port")
                Log.e(TAG, "📡 Endpoint: POST http://localhost:$port/api/browser/execute")

                while (isRunning) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            thread { handleClient(client) }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start server", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            Log.d(TAG, "Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)

            // Read request line
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                val headerParts = line!!.split(": ", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].lowercase()] = headerParts[1]
                    if (headerParts[0].lowercase() == "content-length") {
                        contentLength = headerParts[1].toIntOrNull() ?: 0
                    }
                }
            }

            // Read body
            val body = if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                reader.read(buffer, 0, contentLength)
                String(buffer)
            } else ""

            Log.d(TAG, "Request: $method $path")

            // Handle request
            val response = when {
                path == "/health" && method == "GET" -> {
                    createJsonResponse(200, mapOf("status" to "ok"))
                }
                path == "/api/browser/execute" && method == "POST" -> {
                    handleExecuteRequest(body)
                }
                method == "OPTIONS" -> {
                    createCorsResponse()
                }
                else -> {
                    createJsonResponse(404, mapOf("error" to "Not found"))
                }
            }

            writer.write(response)
            writer.flush()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try {
                client.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun handleExecuteRequest(body: String): String {
        return try {
            if (body.isEmpty()) {
                return createJsonResponse(400, mapOf("error" to "Empty request body"))
            }

            // Parse JSON
            val json = JSONObject(body)
            val tool = json.optString("tool", "")
            val argsJson = json.optJSONObject("args")

            if (tool.isEmpty()) {
                return createJsonResponse(400, mapOf("error" to "Missing 'tool' field"))
            }

            // Parse args
            val args = mutableMapOf<String, Any?>()
            argsJson?.keys()?.forEach { key ->
                args[key] = argsJson.opt(key).let {
                    when (it) {
                        JSONObject.NULL -> null
                        is org.json.JSONArray -> {
                            // Convert JSONArray to List
                            val list = mutableListOf<Any?>()
                            for (i in 0 until it.length()) {
                                list.add(it.opt(i))
                            }
                            list
                        }
                        else -> it
                    }
                }
            }

            Log.d(TAG, "Executing tool: $tool with args: $args")

            // Execute tool
            val result = runBlocking {
                BrowserToolsExecutor.execute(tool, args)
            }

            Log.d(TAG, "Tool result: success=${result.success}")

            // Return result
            createJsonResponse(
                200,
                mapOf(
                    "success" to result.success,
                    "data" to result.data,
                    "error" to result.error
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool", e)
            createJsonResponse(500, mapOf("error" to "Internal error: ${e.message}"))
        }
    }

    private fun createJsonResponse(statusCode: Int, data: Map<String, Any?>): String {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }

        val json = JSONObject(data).toString()

        return buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${json.length}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
            append(json)
        }
    }

    private fun createCorsResponse(): String {
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
    }
}
