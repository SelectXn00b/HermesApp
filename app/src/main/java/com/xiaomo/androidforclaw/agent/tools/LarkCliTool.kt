package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.workspace.StoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * LarkCli Tool — Execute lark-cli (飞书官方 CLI) commands.
 *
 * The binary is bundled as liblark-cli.so in jniLibs and extracted to nativeLibraryDir
 * at install time. Authentication is auto-configured from openclaw.json's
 * channels.feishu.appId / appSecret.
 */
class LarkCliTool(private val context: Context) : Tool {

    companion object {
        private const val TAG = "LarkCliTool"
        private const val BINARY_NAME = "liblark-cli.so"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val MAX_OUTPUT_CHARS = 10000
    }

    override val name = "lark_cli"
    override val description = "Run lark-cli commands to interact with Feishu/Lark platform " +
        "(calendar, approval, contacts, tasks, etc.). Auth is auto-configured from openclaw.json."

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
                            "string",
                            "lark-cli command and arguments, e.g. 'calendar list' or 'approval get --id xxx'"
                        ),
                        "timeout_seconds" to PropertySchema(
                            "number",
                            "Timeout in seconds (default 30, max 120)"
                        )
                    ),
                    required = listOf("command")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val command = args["command"] as? String
            ?: return ToolResult.error("Missing required parameter: command")

        val timeoutSec = ((args["timeout_seconds"] as? Number)?.toLong() ?: DEFAULT_TIMEOUT_SECONDS)
            .coerceIn(5, 120)

        // Locate the binary
        val binaryFile = resolveBinary()
            ?: return ToolResult.error(
                "lark-cli binary not found. Current device ABI may not be supported (arm64/x86_64 only)."
            )

        // Prepare feishu auth config
        val configDir = prepareConfig()
            ?: return ToolResult.error(
                "Feishu credentials not configured. Set channels.feishu.appId and appSecret in openclaw.json."
            )

        Log.d(TAG, "Executing: lark-cli $command (timeout=${timeoutSec}s)")

        return withContext(Dispatchers.IO) {
            try {
                val tmpDir = File(context.cacheDir, "lark-cli-tmp").apply { mkdirs() }

                val pb = ProcessBuilder(listOf("sh", "-c", "${binaryFile.absolutePath} $command"))
                pb.environment().apply {
                    put("HOME", configDir.absolutePath)
                    put("TMPDIR", tmpDir.absolutePath)
                }
                pb.redirectErrorStream(false)
                pb.directory(StoragePaths.workspace.apply { mkdirs() })

                val process = pb.start()
                val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@withContext ToolResult.error("lark-cli timed out after ${timeoutSec}s")
                }

                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.exitValue()

                val rendered = buildString {
                    if (stdout.isNotEmpty()) append(stdout)
                    if (stderr.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append("STDERR:\n$stderr")
                    }
                    if (exitCode != 0) {
                        if (isNotEmpty()) append("\n")
                        append("Exit code: $exitCode")
                    }
                }.ifEmpty { "(no output)" }

                val finalOutput = if (rendered.length > MAX_OUTPUT_CHARS) {
                    rendered.take(MAX_OUTPUT_CHARS) +
                        "\n... (truncated, ${rendered.length - MAX_OUTPUT_CHARS} more chars)"
                } else {
                    rendered
                }

                if (exitCode == 0) {
                    ToolResult.success(finalOutput)
                } else {
                    // Still return as success so the LLM can see the error output
                    ToolResult.success(finalOutput, metadata = mapOf("exitCode" to exitCode))
                }
            } catch (e: Exception) {
                Log.e(TAG, "lark-cli execution failed", e)

                // SELinux fallback: copy binary to filesDir and retry
                if (e.message?.contains("Permission denied") == true ||
                    e.message?.contains("EACCES") == true
                ) {
                    return@withContext executeFallback(binaryFile, command, configDir, timeoutSec)
                }

                ToolResult.error("lark-cli execution failed: ${e.message}")
            }
        }
    }

    /**
     * Find the lark-cli binary from nativeLibraryDir.
     */
    private fun resolveBinary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val binary = File(nativeDir, BINARY_NAME)
        if (binary.exists()) return binary
        Log.w(TAG, "Binary not found at ${binary.absolutePath}")
        return null
    }

    /**
     * Write lark-cli config.json from openclaw.json feishu credentials.
     * Returns the config HOME directory, or null if credentials are missing.
     */
    private fun prepareConfig(): File? {
        try {
            val configFile = StoragePaths.openclawConfig
            if (!configFile.exists()) return null

            val root = JSONObject(configFile.readText())
            val feishu = root.optJSONObject("channels")?.optJSONObject("feishu") ?: return null
            val appId = feishu.optString("appId", "").ifEmpty { return null }
            val appSecret = feishu.optString("appSecret", "").ifEmpty { return null }

            val homeDir = File(context.filesDir, "lark-cli-home")
            val larkDir = File(homeDir, ".lark-cli")
            larkDir.mkdirs()

            val larkConfig = JSONObject().apply {
                put("appId", appId)
                put("appSecret", appSecret)
                put("brand", "feishu")
                put("lang", "zh")
            }

            File(larkDir, "config.json").writeText(larkConfig.toString(2))
            return homeDir
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare lark-cli config", e)
            return null
        }
    }

    /**
     * SELinux fallback: copy binary to app's filesDir and execute from there.
     */
    private fun executeFallback(
        originalBinary: File,
        command: String,
        configDir: File,
        timeoutSec: Long
    ): ToolResult {
        return try {
            val fallbackBin = File(context.filesDir, BINARY_NAME)
            if (!fallbackBin.exists() || fallbackBin.length() != originalBinary.length()) {
                originalBinary.copyTo(fallbackBin, overwrite = true)
                fallbackBin.setExecutable(true)
            }

            val tmpDir = File(context.cacheDir, "lark-cli-tmp").apply { mkdirs() }
            val pb = ProcessBuilder(listOf("sh", "-c", "${fallbackBin.absolutePath} $command"))
            pb.environment().apply {
                put("HOME", configDir.absolutePath)
                put("TMPDIR", tmpDir.absolutePath)
            }
            pb.redirectErrorStream(false)
            pb.directory(StoragePaths.workspace)

            val process = pb.start()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ToolResult.error("lark-cli timed out after ${timeoutSec}s (fallback)")
            }

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            val rendered = buildString {
                if (stdout.isNotEmpty()) append(stdout)
                if (stderr.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("STDERR:\n$stderr")
                }
                if (exitCode != 0) {
                    if (isNotEmpty()) append("\n")
                    append("Exit code: $exitCode")
                }
            }.ifEmpty { "(no output)" }

            val finalOutput = if (rendered.length > MAX_OUTPUT_CHARS) {
                rendered.take(MAX_OUTPUT_CHARS) +
                    "\n... (truncated, ${rendered.length - MAX_OUTPUT_CHARS} more chars)"
            } else {
                rendered
            }

            ToolResult.success(finalOutput, metadata = mapOf("exitCode" to exitCode, "fallback" to true))
        } catch (e: Exception) {
            Log.e(TAG, "lark-cli fallback execution also failed", e)
            ToolResult.error("lark-cli execution failed (both paths): ${e.message}")
        }
    }
}
