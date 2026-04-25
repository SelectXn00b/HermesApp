package com.ai.assistance.operit.ui.features.chat.webview

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.PortProcessKiller
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.chat.webview.workspace.workspaceMimeTypeForPath
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Base64
import android.webkit.CookieManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Locale

@Serializable
data class FileApiEntry(val name: String, val isDirectory: Boolean)

/** LocalWebServer - 基于NanoHTTPD的本地Web服务器 用于显示工作空间目录中的文件 */
class LocalWebServer
private constructor(
    private val context: Context,
    private val port: Int,
    private var rootPath: String,
    private val type: ServerType
) : NanoHTTPD(port) {

    private var workspaceEnv: String? = null
    private val proxyClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    enum class ServerType {
        WORKSPACE
    }

    companion object {
        private const val TAG = "LocalWebServer"

        // Port constants
        const val WORKSPACE_PORT = 8093

        @Volatile
        private var instances = mutableMapOf<ServerType, LocalWebServer>()

        @Synchronized
        fun getInstance(context: Context, type: ServerType): LocalWebServer {
            return instances.getOrPut(type) {
                val server: LocalWebServer = when (type) {
                    ServerType.WORKSPACE -> {
                        val workspaceRoot = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Operit/workspace"
                        )
                        LocalWebServer(
                            context.applicationContext,
                            WORKSPACE_PORT,
                            workspaceRoot.absolutePath,
                            ServerType.WORKSPACE
                        )
                    }
                }
                server
            }
        }
    }

    private val isServerRunning = AtomicBoolean(false)

    @Throws(IOException::class)
    override fun start() {
        // 检查服务器是否已经在运行，避免重复启动
        if (isServerRunning.get()) {
            AppLogger.d(TAG, "服务器已在端口 $port 上运行，跳过启动")
            return
        }
        PortProcessKiller.killListeners(port)
        super.start(SOCKET_READ_TIMEOUT, false)
        AppLogger.d(TAG, "本地Web服务器已在端口 $port 上启动, 根目录: $rootPath")
        isServerRunning.set(true)
    }

    override fun stop() {
        super.stop()
        isServerRunning.set(false)
        AppLogger.d(TAG, "Local server stopped at port: $port")
    }

    fun updateChatWorkspace(newWorkspacePath: String, newWorkspaceEnv: String?) {
        // This is now specific to the workspace server.
        // A better approach would be to create a new instance if the path changes fundamentally,
        // but for now, we'll just update the path for the WORKSPACE instance.
        this.rootPath = newWorkspacePath
        this.workspaceEnv = newWorkspaceEnv
        ensureWorkspaceDirExists(newWorkspacePath)
        AppLogger.d(TAG, "Workspace path updated to: $rootPath env=$workspaceEnv")
    }

    fun isRunning(): Boolean {
        return isServerRunning.get()
    }

    override fun serve(session: IHTTPSession): Response {
        AppLogger.d(TAG, "Request received: ${session.uri} at port $port")

        // API route for file listing
        if (session.uri.startsWith("/api/")) {
            return handleApiRequest(session)
        }

        // Serve static files
        val uri = if (session.uri == "/") "/index.html" else session.uri
        val mimeType = getCustomMimeType(uri)

        return if (type == ServerType.WORKSPACE && !workspaceEnv.isNullOrBlank()) {
            serveWorkspaceFileViaTool(uri, mimeType)
        } else {
            serveFileFromDisk(uri, mimeType)
        }
    }

    private fun normalizeWebPath(path: String): String {
        var p = path.trim()
        if (!p.startsWith("/")) p = "/$p"
        while (p.contains("//")) p = p.replace("//", "/")
        return p
    }

    private fun isSafeRelativeWebPath(path: String): Boolean {
        val normalized = normalizeWebPath(path)
        if (normalized.contains("\\")) return false
        return !normalized.split('/').any { it == ".." }
    }

    private fun joinVirtualRoot(root: String, uri: String): String {
        val base = normalizeWebPath(root)
        val rel = normalizeWebPath(uri)
        return if (base == "/") rel else normalizeWebPath(base.trimEnd('/') + rel)
    }

    private fun serveWorkspaceFileViaTool(uri: String, mimeType: String): Response {
        if (!isSafeRelativeWebPath(uri)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied").addCorsHeaders()
        }

        return try {
            val toolHandler = AIToolHandler.getInstance(context)
            val fullPath = joinVirtualRoot(rootPath, uri)
            val tool = AITool(
                name = "read_file_binary",
                parameters = listOf(
                    ToolParameter("path", fullPath),
                    ToolParameter("environment", workspaceEnv ?: "")
                )
            )

            AppLogger.d(TAG, "execute read_file_binary path=$fullPath env=$workspaceEnv")
            val result = toolHandler.executeTool(tool)
            AppLogger.d(TAG, "result read_file_binary success=${result.success} error=${result.error}")

            if (!result.success || result.result !is BinaryFileContentData) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    result.error ?: "File not found"
                ).addCorsHeaders()
            }

            val data = result.result as BinaryFileContentData
            val base64Content = data.contentBase64
            val bytes = Base64.decode(base64Content, Base64.DEFAULT)

            if (mimeType == "text/html") {
                val htmlContent = String(bytes, Charsets.UTF_8)
                val injectedHtml = injectErudaIntoHtml(htmlContent)
                return newFixedLengthResponse(Response.Status.OK, mimeType, injectedHtml).addCorsHeaders()
            }

            val inputStream = ByteArrayInputStream(bytes)
            val response = newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, bytes.size.toLong())
            response.addCorsHeaders()
            response
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error serving workspace file via tool", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Could not read file.").addCorsHeaders()
        }
    }

    private fun serveFileFromDisk(uri: String, mimeType: String): Response {
        val file = File(rootPath, uri)

        if (!file.exists() || !isInRoot(file)) {
            AppLogger.w(TAG, "File not found or access denied: ${file.absolutePath}")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "File not found"
            ).addCorsHeaders()
        }

        return try {
            // Read the file into a byte array to serve it directly.
            // This avoids the GZIP streaming issue with WebView that causes "Broken pipe".
            val bytes = file.readBytes()

            if (mimeType == "text/html") {
                val htmlContent = String(bytes, Charsets.UTF_8)
                val injectedHtml = injectErudaIntoHtml(htmlContent)
                return newFixedLengthResponse(Response.Status.OK, mimeType, injectedHtml).addCorsHeaders()
            }

            // Serve the file from a byte array input stream. This is the robust way to avoid GZIP issues.
            val inputStream = ByteArrayInputStream(bytes)
            val response = newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, bytes.size.toLong())
            response.addCorsHeaders()
            response
        } catch (ioe: IOException) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Could not read file."
            ).addCorsHeaders()
        }
    }

    private fun injectErudaIntoHtml(htmlContent: String): String {
        val consoleBootstrapScript =
            """
            <script>
            (function() {
                if (window.__operitConsoleBootstrapInstalled) { return; }
                window.__operitConsoleBootstrapInstalled = true;
                var originalConsole = window.console || {};
                var levels = ['log', 'info', 'warn', 'error', 'debug'];
                var buffer = window.__operitEarlyConsoleBuffer = window.__operitEarlyConsoleBuffer || [];
                window.__operitConsoleReplayInProgress = false;
                window.__operitOriginalConsoleMethods = window.__operitOriginalConsoleMethods || {};

                levels.forEach(function(level) {
                    if (window.__operitOriginalConsoleMethods[level]) {
                        return;
                    }
                    var original = typeof originalConsole[level] === 'function'
                        ? originalConsole[level].bind(originalConsole)
                        : (typeof originalConsole.log === 'function'
                            ? originalConsole.log.bind(originalConsole)
                            : function() {});
                    window.__operitOriginalConsoleMethods[level] = original;
                    originalConsole[level] = function() {
                        if (!window.__operitConsoleReplayInProgress) {
                            buffer.push({
                                level: level,
                                args: Array.prototype.slice.call(arguments),
                                timestamp: Date.now()
                            });
                            if (buffer.length > 500) {
                                buffer.shift();
                            }
                        }
                        return original.apply(null, arguments);
                    };
                });

                window.addEventListener('error', function(event) {
                    if (window.__operitConsoleReplayInProgress) {
                        return;
                    }
                    buffer.push({
                        level: 'error',
                        args: [
                            event && event.message ? event.message : 'Script error',
                            event && event.filename ? event.filename : '',
                            event && typeof event.lineno === 'number' ? 'line ' + event.lineno : '',
                            event && typeof event.colno === 'number' ? 'col ' + event.colno : ''
                        ],
                        timestamp: Date.now()
                    });
                });

                window.addEventListener('unhandledrejection', function(event) {
                    if (window.__operitConsoleReplayInProgress) {
                        return;
                    }
                    buffer.push({
                        level: 'error',
                        args: ['Unhandled promise rejection', event ? event.reason : undefined],
                        timestamp: Date.now()
                    });
                });
            })();
            </script>
            """.trimIndent()

        val erudaLoaderScript =
            """
            <script>
            (function() {
                if (window.erudaInjected) { return; }
                window.erudaInjected = true;
                localStorage.removeItem('eruda-entry-btn');
                var script = document.createElement('script');
                script.src = 'https://cdn.jsdelivr.net/npm/eruda';
                script.onload = function() {
                    if (!window.eruda) return;
                    eruda.init();
                    var entryBtn = eruda.get('entry');
                    if (entryBtn) {
                        entryBtn.position({
                            x: 10,
                            y: window.innerHeight - 70
                        });
                    }
                    var buffer = window.__operitEarlyConsoleBuffer || [];
                    if (buffer.length > 0) {
                        var replayConsole = window.console || {};
                        window.__operitConsoleReplayInProgress = true;
                        try {
                            buffer.forEach(function(entry) {
                                var level = entry && entry.level ? entry.level : 'log';
                                var logger = typeof replayConsole[level] === 'function'
                                    ? replayConsole[level]
                                    : replayConsole.log;
                                if (typeof logger === 'function') {
                                    logger.apply(replayConsole, entry.args || []);
                                }
                            });
                        } finally {
                            window.__operitEarlyConsoleBuffer = [];
                            window.__operitConsoleReplayInProgress = false;
                        }
                    }
                };
                var parent = document.head || document.body || document.documentElement;
                if (!parent) {
                    return;
                }
                parent.appendChild(script);
            })();
            </script>
            """.trimIndent()

        val withBootstrap = injectHtmlAtHeadStart(htmlContent, consoleBootstrapScript)
        return injectHtmlBeforeBodyEnd(withBootstrap, erudaLoaderScript)
    }

    private fun injectHtmlAtHeadStart(
        htmlContent: String,
        snippet: String
    ): String {
        val headRegex = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE)
        val headMatch = headRegex.find(htmlContent)
        if (headMatch != null) {
            val insertIndex = headMatch.range.last + 1
            return htmlContent.substring(0, insertIndex) + snippet + htmlContent.substring(insertIndex)
        }

        val htmlRegex = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)
        val htmlMatch = htmlRegex.find(htmlContent)
        if (htmlMatch != null) {
            val insertIndex = htmlMatch.range.last + 1
            return htmlContent.substring(0, insertIndex) + "<head>$snippet</head>" + htmlContent.substring(insertIndex)
        }

        return snippet + htmlContent
    }

    private fun injectHtmlBeforeBodyEnd(
        htmlContent: String,
        snippet: String
    ): String {
        val bodyCloseRegex = Regex("</body>", RegexOption.IGNORE_CASE)
        val bodyCloseMatch = bodyCloseRegex.find(htmlContent)
        if (bodyCloseMatch != null) {
            val insertIndex = bodyCloseMatch.range.first
            return htmlContent.substring(0, insertIndex) + snippet + htmlContent.substring(insertIndex)
        }

        return htmlContent + snippet
    }

    private fun isInRoot(file: File): Boolean {
        return try {
            val rootDir = File(rootPath)
            file.canonicalPath.startsWith(rootDir.canonicalPath)
        } catch (e: IOException) {
            AppLogger.e(TAG, "Error checking file path: ${e.message}")
            false
        }
    }

    private fun handleApiRequest(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri.startsWith("/api/proxy") -> {
                handleProxyRequest(session)
            }
            uri.startsWith("/api/files") -> {
                val path = session.parameters["path"]?.get(0) ?: ""
                listDirectory(path)
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "API endpoint not found").addCorsHeaders()
            }
        }
    }

    private fun handleProxyRequest(session: IHTTPSession): Response {
        val targetUrl = session.parameters["url"]?.firstOrNull()?.trim().orEmpty()
        if (targetUrl.isBlank()) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing url").addCorsHeaders(session.headers["origin"])
        }
        val uri = kotlin.runCatching { java.net.URI(targetUrl) }.getOrNull()
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Unsupported url").addCorsHeaders(session.headers["origin"])
        }

        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").addCorsHeaders(session.headers["origin"])
        }

        val bodyBytes = readRequestBody(session)
        val contentType = session.headers["content-type"]
        val requestBody = if (session.method == Method.GET || session.method == Method.HEAD) {
            null
        } else {
            bodyBytes.toRequestBody(contentType?.toMediaTypeOrNull())
        }

        val requestBuilder = Request.Builder().url(targetUrl).method(session.method.name, requestBody)
        session.headers.forEach { (name, value) ->
            val lower = name.lowercase(Locale.US)
            if (lower in setOf("host", "connection", "content-length", "accept-encoding")) return@forEach
            requestBuilder.addHeader(name, value)
        }

        val cookie = CookieManager.getInstance().getCookie(targetUrl)
        if (!cookie.isNullOrBlank()) {
            requestBuilder.addHeader("Cookie", cookie)
        }

        return try {
            val response = proxyClient.newCall(requestBuilder.build()).execute()
            val status = Response.Status.lookup(response.code) ?: Response.Status.OK
            val body = response.body
            val mimeType = body?.contentType()?.toString() ?: "application/octet-stream"
            val responseStream = body?.byteStream()?.let { stream ->
                ResponseBodyInputStream(response, stream)
            }
            val nanoResponse = if (responseStream != null) {
                val contentLength = body?.contentLength() ?: -1L
                if (contentLength >= 0) {
                    newFixedLengthResponse(status, mimeType, responseStream, contentLength)
                } else {
                    newChunkedResponse(status, mimeType, responseStream)
                }
            } else {
                response.close()
                newFixedLengthResponse(status, mimeType, "")
            }

            response.headers.forEach { (name, value) ->
                val lower = name.lowercase(Locale.US)
                if (lower in setOf("content-length", "content-encoding", "transfer-encoding", "connection")) return@forEach
                nanoResponse.addHeader(name, value)
            }
            nanoResponse.addCorsHeaders(session.headers["origin"])
        } catch (e: Exception) {
            AppLogger.e(TAG, "Proxy request failed: $targetUrl", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Proxy error: ${e.message}").addCorsHeaders(session.headers["origin"])
        }
    }

    private fun readRequestBody(session: IHTTPSession): ByteArray {
        return try {
            val tempFiles = HashMap<String, String>()
            session.parseBody(tempFiles)
            val postDataPath = tempFiles["postData"]
            if (postDataPath != null) {
                File(postDataPath).readBytes()
            } else {
                ByteArray(0)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read proxy request body", e)
            ByteArray(0)
        }
    }

    private fun listDirectory(relativePath: String): Response {
        try {
            val toolHandler = AIToolHandler.getInstance(context)

            val requestedPath = if (type == ServerType.WORKSPACE && !workspaceEnv.isNullOrBlank()) {
                if (!isSafeRelativeWebPath(relativePath)) {
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied").addCorsHeaders()
                }
                joinVirtualRoot(rootPath, relativePath)
            } else {
                // Security check: ensure the path is within our root directory
                val requestedDir = File(rootPath, relativePath).canonicalFile
                if (!requestedDir.path.startsWith(File(rootPath).canonicalPath)) {
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied").addCorsHeaders()
                }
                requestedDir.absolutePath
            }

            val params = mutableListOf(ToolParameter("path", requestedPath))
            if (type == ServerType.WORKSPACE && !workspaceEnv.isNullOrBlank()) {
                params.add(ToolParameter("environment", workspaceEnv ?: ""))
            }

            val tool = AITool(
                name = "list_files",
                parameters = params
            )

            val result = toolHandler.executeTool(tool)

            if (result.success && result.result is DirectoryListingData) {
                // The result from list_files is already a JSON string of a list of file info.
                val directoryListing = result.result as DirectoryListingData
                val apiEntries = directoryListing.entries.map { FileApiEntry(it.name, it.isDirectory) }
                val jsonResult = Json.encodeToString(apiEntries)
                return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResult).addCorsHeaders()
            } else {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, result.error ?: "Failed to list files").addCorsHeaders()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error listing directory", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}").addCorsHeaders()
        }
    }

    private fun Response.addCorsHeaders(origin: String? = null): Response {
        val allowOrigin = origin ?: "*"
        this.addHeader("Access-Control-Allow-Origin", allowOrigin)
        this.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
        this.addHeader("Access-Control-Allow-Headers", "X-Requested-With, Content-Type, Authorization, Origin, Accept")
        this.addHeader("Access-Control-Max-Age", "3600")
        this.addHeader("Access-Control-Allow-Credentials", "true")
        if (origin != null) {
            this.addHeader("Vary", "Origin")
        }
        return this
    }

    private class ResponseBodyInputStream(
        private val response: okhttp3.Response,
        inputStream: InputStream
    ) : FilterInputStream(inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                response.close()
            }
        }
    }
    
    /**
     * 确保工作区目录存在
     */
    private fun ensureWorkspaceDirExists(path: String) {
        if (!workspaceEnv.isNullOrBlank()) return
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
            AppLogger.d(TAG, "创建工作区目录: $path")
        }
    }
    
    /**
     * 根据文件路径获取MIME类型
     */
    private fun getCustomMimeType(uri: String): String {
        return workspaceMimeTypeForPath(uri)
    }
}
