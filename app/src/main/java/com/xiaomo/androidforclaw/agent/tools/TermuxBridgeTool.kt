package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * TermuxBridge Tool - Execute code in Termux environment
 *
 * Requirements:
 * - Termux installed from F-Droid or GitHub
 * - Termux:API installed
 * - phoneforclaw_server.py running in Termux
 *
 * Supported runtimes:
 * - python: Python 3.x
 * - nodejs: Node.js
 * - shell: Bash shell
 */
class TermuxBridgeTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "TermuxBridgeTool"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_API_PACKAGE = "com.termux.api"

        // 共享目录
        private const val SHARED_DIR = "/sdcard/.androidforclaw/.ipc"
        private const val REQUEST_FILE = "$SHARED_DIR/request.json"
        private const val RESPONSE_FILE = "$SHARED_DIR/response.json"
        private const val LOCK_FILE = "$SHARED_DIR/server.lock"

        // 超时配置
        private const val DEFAULT_TIMEOUT_MS = 60_000L // 60 秒
        private const val POLL_INTERVAL_MS = 500L
    }

    override val name = "exec"
    override val description = "Run shell commands via Termux when available"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "command" to PropertySchema(
                            type = "string",
                            description = "Shell command to execute in Termux"
                        ),
                        "working_dir" to PropertySchema(
                            type = "string",
                            description = "Working directory (optional, default: Termux home)"
                        ),
                        "timeout" to PropertySchema(
                            type = "number",
                            description = "Execution timeout in seconds (optional, default: 60)"
                        ),
                        "action" to PropertySchema(
                            type = "string",
                            description = "Compatibility action: exec (default) or setup_storage",
                            enum = listOf("exec", "setup_storage")
                        ),
                        "runtime" to PropertySchema(
                            type = "string",
                            description = "Backward-compatible runtime",
                            enum = listOf("python", "nodejs", "shell")
                        ),
                        "code" to PropertySchema(
                            type = "string",
                            description = "Backward-compatible code string"
                        ),
                        "cwd" to PropertySchema(
                            type = "string",
                            description = "Backward-compatible working directory alias"
                        )
                    ),
                    required = listOf("command")
                )
            )
        )
    }

    fun isAvailable(): Boolean = isTermuxInstalled()

    /**
     * 检查 Termux 是否已安装
     */
    private fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 检查 Termux:API 是否已安装
     */
    private fun isTermuxApiInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 检查 RPC 服务器是否运行
     */
    private fun isServerRunning(): Boolean {
        return File(LOCK_FILE).exists()
    }

    /**
     * 发送请求到 Termux
     */
    private suspend fun sendRequest(request: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        try {
            // 确保共享目录存在
            val sharedDir = File(SHARED_DIR)
            if (!sharedDir.exists()) {
                sharedDir.mkdirs()
            }

            // 清理旧的响应文件
            val responseFile = File(RESPONSE_FILE)
            if (responseFile.exists()) {
                responseFile.delete()
            }

            // 写入请求文件
            val requestFile = File(REQUEST_FILE)
            requestFile.writeText(request.toString(2))
            Log.d(TAG, "Request written to $REQUEST_FILE")

            // 使用 Termux:API 通知服务器（可选，服务器会轮询）
            notifyTermux("PhoneForClaw: New task")

            // 轮询等待响应
            val timeoutMs = request.optInt("timeout", 60) * 1000L
            val maxAttempts = (timeoutMs / POLL_INTERVAL_MS).toInt()
            var attempts = 0

            while (attempts < maxAttempts) {
                if (responseFile.exists()) {
                    // 读取响应
                    val responseText = responseFile.readText()
                    val response = JSONObject(responseText)

                    // 清理文件
                    responseFile.delete()
                    if (requestFile.exists()) {
                        requestFile.delete()
                    }

                    Log.d(TAG, "Response received after ${attempts * POLL_INTERVAL_MS}ms")
                    return@withContext response
                }

                delay(POLL_INTERVAL_MS)
                attempts++
            }

            // 超时
            if (requestFile.exists()) {
                requestFile.delete()
            }

            JSONObject().apply {
                put("success", false)
                put("error", "Timeout waiting for Termux response (${timeoutMs}ms)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Communication failed", e)
            JSONObject().apply {
                put("success", false)
                put("error", "Communication failed: ${e.message}")
            }
        }
    }

    /**
     * 使用 Termux:API 发送通知（可选）
     */
    private fun notifyTermux(message: String) {
        try {
            // 通过 termux-toast 发送通知（需要 Termux:API）
            // 注意：这需要 Termux 服务器监听通知，当前实现使用轮询所以这是可选的
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/termux-toast")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-s", message))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to notify Termux: ${e.message}")
        }
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 0. 解析 action
        val action = args["action"] as? String ?: "exec"

        // 1. 检查 Termux
        if (!isTermuxInstalled()) {
            return ToolResult(
                success = false,
                content = buildString {
                    appendLine("❌ Termux not installed")
                    appendLine()
                    appendLine("Please install Termux:")
                    appendLine("• F-Droid: https://f-droid.org/packages/com.termux/")
                    appendLine("• GitHub: https://github.com/termux/termux-app/releases")
                }
            )
        }

        // 2. 检查 Termux:API
        if (!isTermuxApiInstalled()) {
            return ToolResult(
                success = false,
                content = buildString {
                    appendLine("❌ Termux:API not installed")
                    appendLine()
                    appendLine("Please install Termux:API:")
                    appendLine("• F-Droid: https://f-droid.org/packages/com.termux.api/")
                    appendLine("• GitHub: https://github.com/termux/termux-api/releases")
                    appendLine()
                    appendLine("Then run in Termux:")
                    appendLine("  pkg install termux-api")
                }
            )
        }

        // 3. 检查 RPC 服务器
        if (!isServerRunning()) {
            return ToolResult(
                success = false,
                content = buildString {
                    appendLine("❌ PhoneForClaw Bridge Server not running")
                    appendLine()
                    appendLine("Please start the server in Termux:")
                    appendLine("  bash ~/start_bridge.sh")
                    appendLine()
                    appendLine("Setup instructions:")
                    appendLine("  https://github.com/xiaomochn/AndroidForClaw/blob/main/docs/termux-integration/README.md")
                }
            )
        }

        // 4. 处理特殊 action
        if (action == "setup_storage") {
            // 设置存储权限
            val request = JSONObject().apply {
                put("action", "setup_storage")
            }

            val response = sendRequest(request)
            val success = response.optBoolean("success", false)
            val message = response.optString("message", "")
            val error = response.optString("error", "")

            return if (success) {
                ToolResult(
                    success = true,
                    content = buildString {
                        appendLine("✅ Storage access setup initiated")
                        appendLine()
                        appendLine(message)
                        appendLine()
                        appendLine("After granting permission, you can access:")
                        appendLine("• /sdcard/ - Shared storage")
                        appendLine("• ~/storage/shared/ - Link to /sdcard/")
                        appendLine("• ~/storage/downloads/ - Downloads folder")
                        appendLine("• ~/storage/dcim/ - Camera photos")
                    }
                )
            } else {
                ToolResult.error(error.ifEmpty { "Failed to setup storage access" })
            }
        }

        // 5. 处理 exec action - 对齐 OpenClaw exec(command, working_dir)
        val command = args["command"] as? String
        val runtime = args["runtime"] as? String
        val code = args["code"] as? String
        val cwd = (args["working_dir"] as? String) ?: (args["cwd"] as? String)
        val timeout = (args["timeout"] as? Number)?.toInt() ?: 60

        val (resolvedRuntime, resolvedCode) = when {
            !command.isNullOrBlank() -> "shell" to command
            !runtime.isNullOrBlank() && !code.isNullOrBlank() -> runtime to code
            else -> return ToolResult.error("Missing required parameter: command")
        }

        if (resolvedRuntime !in listOf("python", "nodejs", "shell")) {
            return ToolResult.error("Invalid runtime: $resolvedRuntime (use python/nodejs/shell)")
        }

        // 6. 构建请求
        val request = JSONObject().apply {
            put("action", "exec")
            put("runtime", resolvedRuntime)
            put("code", resolvedCode)
            put("args", JSONObject().apply {
                if (cwd != null) put("cwd", cwd)
                put("timeout", timeout)
            })
        }

        Log.d(TAG, "Executing via Termux runtime=$resolvedRuntime (${resolvedCode.length} chars)")

        // 7. 发送请求
        val response = sendRequest(request)

        // 8. 解析响应
        val success = response.optBoolean("success", false)
        val stdout = response.optString("stdout", "")
        val stderr = response.optString("stderr", "")
        val returncode = response.optInt("returncode", -1)
        val error = response.optString("error", "")

        return if (success) {
            ToolResult(
                success = true,
                content = buildString {
                    if (stdout.isNotEmpty()) {
                        appendLine(stdout.trim())
                    }
                    if (stderr.isNotEmpty()) {
                        if (isNotEmpty()) appendLine()
                        appendLine("STDERR:")
                        appendLine(stderr.trim())
                    }
                    if (returncode != 0) {
                        if (isNotEmpty()) appendLine()
                        appendLine("Exit code: $returncode")
                    }
                }.ifEmpty { "(no output)" },
                metadata = mapOf(
                    "backend" to "termux",
                    "stdout" to stdout,
                    "stderr" to stderr,
                    "exitCode" to returncode,
                    "runtime" to resolvedRuntime,
                    "working_dir" to (cwd ?: ""),
                    "command" to (command ?: "")
                )
            )
        } else {
            ToolResult(
                success = false,
                content = buildString {
                    if (error.isNotEmpty()) {
                        appendLine("Error: $error")
                    }
                    if (stderr.isNotEmpty()) {
                        if (isNotEmpty()) appendLine()
                        appendLine("STDERR:")
                        appendLine(stderr.trim())
                    }
                    if (stdout.isNotEmpty()) {
                        if (isNotEmpty()) appendLine()
                        appendLine("STDOUT:")
                        appendLine(stdout.trim())
                    }
                },
                metadata = mapOf(
                    "backend" to "termux",
                    "stdout" to stdout,
                    "stderr" to stderr,
                    "exitCode" to returncode,
                    "runtime" to resolvedRuntime,
                    "working_dir" to (cwd ?: ""),
                    "command" to (command ?: ""),
                    "error" to error
                )
            )
        }
    }
}
