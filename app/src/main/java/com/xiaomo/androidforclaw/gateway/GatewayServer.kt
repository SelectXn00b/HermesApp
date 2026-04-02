/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server.ts, server.impl.ts
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.channel.ChannelManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/**
 * Gateway Server - HTTP + WebSocket server
 *
 * Provides WebUI access interface:
 * - HTTP: Static file service (WebUI)
 * - WebSocket: Real-time communication (RPC + Events)
 */
class GatewayServer(
    private val context: Context,
    private val port: Int = 19789
) : NanoWSD(null, port) {  // null = listen on all network interfaces (0.0.0.0)

    companion object {
        private const val TAG = "GatewayServer"

        // Static instance for broadcasting messages from elsewhere
        @Volatile
        private var instance: GatewayServer? = null

        fun getInstance(): GatewayServer? = instance

        /**
         * Broadcast chat message to all connected clients
         */
        fun broadcastChatMessage(sessionId: String, role: String, content: String) {
            instance?.let { server ->
                val payload = JSONObject().apply {
                    put("id", "msg-${System.currentTimeMillis()}")
                    put("sessionId", sessionId)
                    put("role", role)
                    put("content", content)
                    put("timestamp", System.currentTimeMillis())
                }
                server.broadcast("chat.message", payload)
                Log.d(TAG, "📤 [Broadcast] Chat message: $sessionId, $role")
            }
        }
    }

    private val channelManager = ChannelManager(context)
    private val activeConnections = mutableSetOf<GatewayWebSocket>()

    // Web Clipboard: PC → Phone text transfer
    private val clipboardHistory = mutableListOf<Pair<String, Long>>() // (text, timestamp)
    private val clipboardMaxHistory = 20

    init {
        Log.d(TAG, "Gateway Server initialized on port $port")
        instance = this
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        Log.d(TAG, "HTTP Request: ${session.method} $uri")

        // WebSocket upgrade - check Upgrade header
        val headers = session.headers
        if (headers["upgrade"]?.lowercase()?.contains("websocket") == true) {
            return super.serve(session)
        }

        // HTTP API
        if (uri.startsWith("/api/")) {
            return handleApiRequest(session)
        }

        // Web Clipboard (for easy config input from PC)
        if (uri == "/clipboard" || uri == "/clipboard/") {
            return serveClipboardPage()
        }

        // Static files (WebUI)
        return serveWebUI(uri)
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        Log.d(TAG, "WebSocket connection opened")
        return GatewayWebSocket(handshake, this)
    }

    /**
     * Handle API requests
     */
    private fun handleApiRequest(session: IHTTPSession): Response {
        val uri = session.uri.removePrefix("/api")

        return when {
            uri == "/health" -> {
                val json = JSONObject().apply {
                    put("status", "ok")
                    put("version", "3.0.0")
                    put("timestamp", System.currentTimeMillis())
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }

            uri == "/clipboard/send" && session.method == NanoHTTPD.Method.POST -> {
                handleClipboardSend(session)
            }

            uri == "/clipboard/history" -> {
                val json = org.json.JSONArray()
                synchronized(clipboardHistory) {
                    clipboardHistory.forEach { item ->
                        json.put(JSONObject().apply {
                            put("text", item.first)
                            put("timestamp", item.second)
                        })
                    }
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }

            uri == "/device/status" -> {
                val status = channelManager.getCurrentAccount()
                val json = JSONObject().apply {
                    put("connected", status.connected)
                    put("deviceId", status.deviceId)
                    put("deviceModel", status.deviceModel)
                    put("androidVersion", status.androidVersion)
                    put("apiLevel", status.apiLevel)
                    put("permissions", JSONObject().apply {
                        put("accessibility", status.accessibilityEnabled)
                        put("overlay", status.overlayPermission)
                        put("mediaProjection", status.mediaProjection)
                    })
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }

            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "API not found: $uri")
            }
        }
    }

    /**
     * Handle clipboard send from PC
     */
    private fun handleClipboardSend(session: IHTTPSession): Response {
        try {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            val body = mutableMapOf<String, String>()
            session.parseBody(body)

            val postData = body["postData"] ?: ""
            val json = JSONObject(postData)
            val text = json.optString("text", "")

            if (text.isBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    """{"error":"empty text"}""")
            }

            // Save to history
            synchronized(clipboardHistory) {
                clipboardHistory.add(0, Pair(text, System.currentTimeMillis()))
                if (clipboardHistory.size > clipboardMaxHistory) {
                    clipboardHistory.removeAt(clipboardHistory.size - 1)
                }
            }

            // Copy to system clipboard on main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("web_clipboard", text)
                    clipboardManager.setPrimaryClip(clip)
                    Log.d(TAG, "📋 Web clipboard: copied ${text.length} chars to system clipboard")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy to clipboard", e)
                }
            }

            // Broadcast to connected WebSocket clients
            broadcast("clipboard.received", JSONObject().apply {
                put("text", text)
                put("timestamp", System.currentTimeMillis())
            })

            Log.d(TAG, "📋 Web clipboard received: ${text.take(50)}...")
            return newFixedLengthResponse(Response.Status.OK, "application/json",
                """{"ok":true,"length":${text.length}}""")
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard send failed", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                """{"error":"${e.message}"}""")
        }
    }

    /**
     * Get clipboard history (for external access)
     */
    fun getClipboardHistory(): List<Pair<String, Long>> {
        synchronized(clipboardHistory) {
            return clipboardHistory.toList()
        }
    }

    /**
     * Serve the web clipboard page (inline HTML, no external dependencies)
     */
    private fun serveClipboardPage(): Response {
        val html = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AndroidForClaw - Web Clipboard</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, system-ui, sans-serif; background: #0a0a0a; color: #e0e0e0; min-height: 100vh; }
.container { max-width: 640px; margin: 0 auto; padding: 24px 16px; }
h1 { font-size: 20px; font-weight: 600; margin-bottom: 4px; color: #fff; }
.subtitle { font-size: 13px; color: #888; margin-bottom: 24px; }
.input-area { position: relative; margin-bottom: 16px; }
textarea { width: 100%; min-height: 120px; padding: 14px; border: 1px solid #333; border-radius: 10px; background: #1a1a1a; color: #fff; font-size: 15px; font-family: 'SF Mono', monospace; resize: vertical; outline: none; transition: border-color 0.2s; }
textarea:focus { border-color: #4a9eff; }
textarea::placeholder { color: #555; }
.actions { display: flex; gap: 10px; margin-bottom: 24px; }
button { flex: 1; padding: 12px; border: none; border-radius: 10px; font-size: 15px; font-weight: 500; cursor: pointer; transition: all 0.2s; }
.btn-send { background: #4a9eff; color: #fff; }
.btn-send:hover { background: #3a8eef; }
.btn-send:active { transform: scale(0.98); }
.btn-send:disabled { background: #333; color: #666; cursor: not-allowed; }
.btn-clear { background: #2a2a2a; color: #aaa; flex: 0.4; }
.btn-clear:hover { background: #333; }
.status { text-align: center; font-size: 13px; color: #4a9eff; min-height: 20px; margin-bottom: 20px; transition: opacity 0.3s; }
.status.error { color: #ff4a4a; }
.status.ok { color: #4aff8a; }
h2 { font-size: 14px; color: #888; margin-bottom: 12px; font-weight: 500; }
.history { list-style: none; }
.history-item { background: #1a1a1a; border: 1px solid #222; border-radius: 8px; padding: 12px; margin-bottom: 8px; cursor: pointer; transition: all 0.2s; position: relative; }
.history-item:hover { border-color: #444; background: #222; }
.history-text { font-size: 14px; font-family: 'SF Mono', monospace; word-break: break-all; white-space: pre-wrap; max-height: 80px; overflow: hidden; }
.history-time { font-size: 11px; color: #555; margin-top: 6px; }
.history-copied { position: absolute; top: 12px; right: 12px; font-size: 11px; color: #4aff8a; opacity: 0; transition: opacity 0.3s; }
.history-item.copied .history-copied { opacity: 1; }
.empty { text-align: center; color: #444; font-size: 14px; padding: 40px 0; }
</style>
</head>
<body>
<div class="container">
<h1>📋 Web Clipboard</h1>
<p class="subtitle">在电脑上输入，手机上自动复制到剪切板</p>

<div class="input-area">
<textarea id="text" placeholder="粘贴 API Key、配置内容或任意文本..." autofocus></textarea>
</div>
<div class="actions">
<button class="btn-send" id="sendBtn" onclick="send()">发送到手机</button>
<button class="btn-clear" onclick="clearInput()">清空</button>
</div>
<div class="status" id="status"></div>

<h2>历史记录（点击复制）</h2>
<ul class="history" id="history"></ul>
<div class="empty" id="empty">暂无记录</div>
</div>

<script>
const textarea = document.getElementById('text');
const statusEl = document.getElementById('status');
const historyEl = document.getElementById('history');
const emptyEl = document.getElementById('empty');
const sendBtn = document.getElementById('sendBtn');

async function send() {
  const text = textarea.value.trim();
  if (!text) return;
  sendBtn.disabled = true;
  statusEl.className = 'status';
  statusEl.textContent = '发送中...';
  try {
    const res = await fetch('/api/clipboard/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text })
    });
    const data = await res.json();
    if (data.ok) {
      statusEl.className = 'status ok';
      statusEl.textContent = '✅ 已发送到手机剪切板 (' + data.length + ' 字符)';
      textarea.value = '';
      loadHistory();
    } else {
      throw new Error(data.error || 'unknown');
    }
  } catch (e) {
    statusEl.className = 'status error';
    statusEl.textContent = '❌ 发送失败: ' + e.message;
  }
  sendBtn.disabled = false;
  setTimeout(() => { statusEl.textContent = ''; }, 3000);
}

function clearInput() { textarea.value = ''; textarea.focus(); }

textarea.addEventListener('keydown', function(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); send(); }
});

async function loadHistory() {
  try {
    const res = await fetch('/api/clipboard/history');
    const items = await res.json();
    if (items.length === 0) {
      historyEl.innerHTML = '';
      emptyEl.style.display = 'block';
      return;
    }
    emptyEl.style.display = 'none';
    historyEl.innerHTML = items.map((item, i) => {
      const t = new Date(item.timestamp).toLocaleString('zh-CN');
      const preview = item.text.length > 200 ? item.text.substring(0, 200) + '...' : item.text;
      return '<li class="history-item" onclick="copyItem(this, ' + i + ')" data-text="' + escapeAttr(item.text) + '">'
        + '<div class="history-text">' + escapeHtml(preview) + '</div>'
        + '<div class="history-time">' + t + '</div>'
        + '<div class="history-copied">已复制</div></li>';
    }).join('');
  } catch (e) {}
}

function copyItem(el, idx) {
  const text = el.getAttribute('data-text');
  navigator.clipboard.writeText(text).then(() => {
    el.classList.add('copied');
    setTimeout(() => el.classList.remove('copied'), 1500);
  });
}

function escapeHtml(s) { return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
function escapeAttr(s) { return s.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

loadHistory();
</script>
</body>
</html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    /**
     * Serve WebUI static files
     */
    private fun serveWebUI(uri: String): Response {
        var path = uri
        if (path == "/") {
            path = "/index.html"
        }

        try {
            val assetPath = "webui$path"
            val inputStream = context.assets.open(assetPath)
            val mimeType = getMimeType(path)

            return newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: IOException) {
            Log.w(TAG, "WebUI file not found: $path", e)
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/html",
                """
                <html>
                <body>
                    <h1>404 - File Not Found</h1>
                    <p>File: $path</p>
                    <p>WebUI not built yet. Run: <code>cd ui && npm run build</code></p>
                </body>
                </html>
                """.trimIndent()
            )
        }
    }

    /**
     * Get MIME type
     */
    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }

    /**
     * WebSocket connection
     */
    inner class GatewayWebSocket(
        handshakeRequest: IHTTPSession,
        private val server: GatewayServer
    ) : NanoWSD.WebSocket(handshakeRequest) {

        private val connectionId = UUID.randomUUID().toString()
        private var pingTimer: java.util.Timer? = null

        override fun onOpen() {
            Log.d(TAG, "🔗 [Gateway] WebSocket opened: $connectionId")
            activeConnections.add(this)
            Log.d(TAG, "📊 [Gateway] Active connections: ${activeConnections.size}")

            // Send hello message
            val hello = JSONObject().apply {
                put("type", "event")
                put("event", "hello")
                put("payload", JSONObject().apply {
                    put("version", "3.0.0")
                    put("agent", "AndroidForClaw")
                    put("channel", "android-app")
                    put("deviceId", channelManager.getCurrentAccount().deviceId)
                    put("capabilities", listOf("screenshot", "tap", "swipe", "type"))
                })
            }
            Log.d(TAG, "👋 [Gateway] Sending hello message: ${hello.toString()}")
            send(hello.toString())
            Log.d(TAG, "✅ [Gateway] Hello message sent")

            // Start heartbeat - send ping every 30 seconds
            startHeartbeat()
        }

        private fun startHeartbeat() {
            pingTimer = java.util.Timer()
            pingTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    try {
                        if (isOpen) {
                            ping("ping".toByteArray())
                            Log.d(TAG, "💓 [Gateway] Heartbeat sent: $connectionId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [Gateway] Heartbeat failed: $connectionId", e)
                        cancel()
                    }
                }
            }, 30000, 30000) // 30 second interval
            Log.d(TAG, "💓 [Gateway] Heartbeat started: $connectionId")
        }

        private fun stopHeartbeat() {
            pingTimer?.cancel()
            pingTimer = null
            Log.d(TAG, "💓 [Gateway] Heartbeat stopped: $connectionId")
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            Log.d(TAG, "🔌 [Gateway] WebSocket closed: $connectionId")
            Log.d(TAG, "📊 [Gateway] Close info - code: $code, reason: $reason, initiatedByRemote: $initiatedByRemote")
            stopHeartbeat()
            activeConnections.remove(this)
            Log.d(TAG, "📊 [Gateway] Active connections: ${activeConnections.size}")
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            try {
                val text = message.textPayload
                Log.d(TAG, "📨 [Gateway] WebSocket message received")
                Log.d(TAG, "📦 [Gateway] Message content: $text")

                val frame = JSONObject(text)
                val type = frame.optString("type")
                Log.d(TAG, "🔍 [Gateway] Frame type: $type")

                when (type) {
                    "req" -> {
                        Log.d(TAG, "🎯 [Gateway] Handling request...")
                        handleRequest(frame)
                    }
                    "ping" -> {
                        // Received client heartbeat
                        Log.d(TAG, "💓 [Gateway] Received ping from client: $connectionId")
                        // NanoWSD will automatically send pong
                    }
                    else -> {
                        Log.w(TAG, "⚠️ [Gateway] Unknown frame type: $type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Gateway] Failed to handle WebSocket message", e)
                Log.e(TAG, "🔍 [Gateway] Error details: ${e.message}")
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) {
            Log.d(TAG, "💓 [Gateway] Pong received: $connectionId")
        }

        override fun onException(exception: IOException) {
            Log.e(TAG, "WebSocket exception: $connectionId", exception)
        }

        /**
         * Handle RPC request
         */
        private fun handleRequest(frame: JSONObject) {
            val id = frame.getString("id")
            val method = frame.getString("method")
            val params = frame.optJSONObject("params")

            Log.d(TAG, "🎯 [Gateway] RPC Request:")
            Log.d(TAG, "  📋 ID: $id")
            Log.d(TAG, "  🔧 Method: $method")
            Log.d(TAG, "  📦 Params: $params")

            try {
                val result = when (method) {
                    "device.status" -> {
                        Log.d(TAG, "📱 [Gateway] Handling device.status...")
                        handleDeviceStatus()
                    }
                    "channel.status" -> {
                        Log.d(TAG, "📡 [Gateway] Handling channel.status...")
                        handleChannelStatus()
                    }
                    "channel.restart" -> {
                        Log.d(TAG, "📡 [Gateway] Handling channel.restart...")
                        handleChannelRestart()
                    }
                    "sessions.list" -> {
                        Log.d(TAG, "📋 [Gateway] Handling sessions.list...")
                        handleSessionsList()
                    }
                    "sessions.history" -> {
                        Log.d(TAG, "📋 [Gateway] Handling sessions.history...")
                        handleSessionsHistory(params)
                    }
                    "chat.send" -> {
                        Log.d(TAG, "💬 [Gateway] Handling chat.send...")
                        handleChatSend(params)
                    }
                    else -> {
                        Log.w(TAG, "⚠️ [Gateway] Unknown method: $method")
                        throw IllegalArgumentException("Unknown method: $method")
                    }
                }

                Log.d(TAG, "✅ [Gateway] Request successful: $method")
                Log.d(TAG, "📦 [Gateway] Result: $result")

                val response = JSONObject().apply {
                    put("type", "res")
                    put("id", id)
                    put("ok", true)
                    put("payload", result)
                }
                Log.d(TAG, "📤 [Gateway] Sending response: ${response.toString()}")
                send(response.toString())
                Log.d(TAG, "✅ [Gateway] Response sent")

            } catch (e: Exception) {
                Log.e(TAG, "❌ [Gateway] Request failed: $method", e)
                Log.e(TAG, "🔍 [Gateway] Error details: ${e.message}")

                val response = JSONObject().apply {
                    put("type", "res")
                    put("id", id)
                    put("ok", false)
                    put("error", JSONObject().apply {
                        put("code", "internal_error")
                        put("message", e.message ?: "Unknown error")
                    })
                }
                Log.d(TAG, "📤 [Gateway] Sending error response: ${response.toString()}")
                send(response.toString())
            }
        }

        private fun handleDeviceStatus(): JSONObject {
            val account = channelManager.getCurrentAccount()
            return JSONObject().apply {
                put("connected", account.connected)
                put("deviceId", account.deviceId)
                put("deviceModel", account.deviceModel)
                put("androidVersion", account.androidVersion)
                put("apiLevel", account.apiLevel)
                put("architecture", account.architecture)
                put("permissions", JSONObject().apply {
                    put("accessibility", account.accessibilityEnabled)
                    put("overlay", account.overlayPermission)
                    put("mediaProjection", account.mediaProjection)
                })
            }
        }

        private fun handleChannelStatus(): JSONObject {
            val status = channelManager.getChannelStatus()
            return JSONObject().apply {
                put("timestamp", status.timestamp)
                put("channelId", status.channelId)
                put("accounts", status.accounts.map { account ->
                    JSONObject().apply {
                        put("accountId", account.accountId)
                        put("name", account.name)
                        put("connected", account.connected)
                        put("deviceId", account.deviceId)
                        put("deviceModel", account.deviceModel)
                    }
                })
            }
        }

        private fun handleChannelRestart(): JSONObject {
            val app = context.applicationContext as? com.xiaomo.androidforclaw.core.MyApplication
            if (app != null) {
                app.restartAllChannels()
                return JSONObject().apply {
                    put("ok", true)
                    put("message", "Channel restart triggered")
                }
            }
            return JSONObject().apply {
                put("ok", false)
                put("message", "MyApplication not available")
            }
        }

        private fun handleSessionsList(): JSONObject {
            // Return all session list
            return JSONObject().apply {
                put("sessions", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "default")
                        put("name", "默认会话")
                        put("lastActivity", System.currentTimeMillis())
                    })
                })
            }
        }

        private fun handleSessionsHistory(params: JSONObject?): JSONObject {
            val sessionId = params?.optString("sessionId") ?: "default"
            Log.d(TAG, "📋 [Gateway] 获取会话历史: $sessionId")

            try {
                // Get SessionManager from MainEntryNew
                val sessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
                val session = sessionManager?.get(sessionId)

                if (session != null) {
                    Log.d(TAG, "✅ [Gateway] Found session: ${session.messageCount()} messages")
                    return JSONObject().apply {
                        put("sessionId", sessionId)
                        put("messages", org.json.JSONArray().apply {
                            session.messages.forEach { msg ->
                                put(JSONObject().apply {
                                    put("role", msg.role)
                                    put("content", msg.content ?: "")
                                    // Ignore complex fields like tool_calls, only return basic conversation
                                })
                            }
                        })
                    }
                } else {
                    Log.w(TAG, "⚠️ [Gateway] Session not found: $sessionId")
                    return JSONObject().apply {
                        put("sessionId", sessionId)
                        put("messages", org.json.JSONArray())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Gateway] Failed to get session history", e)
                throw e
            }
        }

        private fun handleChatSend(params: JSONObject?): JSONObject {
            val message = params?.optString("message") ?: throw IllegalArgumentException("Missing message")
            val sessionId = params.optString("sessionId")

            Log.d(TAG, "💬 [Gateway] Chat message received:")
            Log.d(TAG, "  📝 Message: $message")
            Log.d(TAG, "  🆔 Session ID: $sessionId")

            // Send ADB command to trigger agent execution
            val adbCommand = if (sessionId.isNotEmpty()) {
                "adb shell am broadcast -a com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT --es message \"$message\" --es sessionId \"$sessionId\""
            } else {
                "adb shell am broadcast -a com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT --es message \"$message\""
            }

            Log.d(TAG, "📤 [Gateway] Broadcasting intent to agent...")
            Log.d(TAG, "🔧 [Gateway] ADB command: $adbCommand")

            try {
                // Broadcast to local app
                val intent = android.content.Intent("com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT").apply {
                    putExtra("message", message)
                    if (sessionId.isNotEmpty()) {
                        putExtra("sessionId", sessionId)
                    }
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "✅ [Gateway] Intent broadcast sent")

                return JSONObject().apply {
                    put("status", "queued")
                    put("message", "Message queued for processing")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Gateway] Failed to broadcast intent", e)
                throw e
            }
        }
    }

    /**
     * Broadcast event to all connections
     */
    fun broadcast(event: String, payload: Any) {
        val message = JSONObject().apply {
            put("type", "event")
            put("event", event)
            put("payload", payload)
        }

        val text = message.toString()
        activeConnections.forEach { connection ->
            try {
                connection.send(text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast to connection", e)
            }
        }
    }

    /**
     * Get server address
     */
    fun getServerUrl(): String {
        return "http://0.0.0.0:$port"
    }

    fun getActiveConnectionsCount(): Int {
        return activeConnections.size
    }
}
