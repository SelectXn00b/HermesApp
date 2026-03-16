/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: Termux exec bridge.
 */
package com.xiaomo.androidforclaw.agent.tools

import android.content.ComponentName
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
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File
import java.io.IOException
import java.security.Security
import java.util.concurrent.TimeUnit

/**
 * TermuxBridge Tool - Execute commands in Termux
 *
 * Internal transport: SSH to Termux sshd on localhost:8022.
 * All connection details are encapsulated; the model only sees
 * a simple exec interface with stdout/stderr/exitCode.
 */
class TermuxBridgeTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "TermuxBridgeTool"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_API_PACKAGE = "com.termux.api"
        private const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val SSH_HOST = "127.0.0.1"
        private const val SSH_PORT = 8022
        private const val DEFAULT_TIMEOUT_S = 60

        private const val CONFIG_DIR = "/sdcard/.androidforclaw"
        private const val SSH_CONFIG_FILE = "$CONFIG_DIR/termux_ssh.json"
        private const val KEY_DIR = "$CONFIG_DIR/.ssh"
        private const val PRIVATE_KEY = "$KEY_DIR/id_ed25519"
        private const val PUBLIC_KEY = "$KEY_DIR/id_ed25519.pub"

        private var bcRegistered = false
    }

    override val name = "exec"
    override val description = "Run shell commands via Termux"

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
                            description = "Working directory (optional)"
                        ),
                        "timeout" to PropertySchema(
                            type = "number",
                            description = "Timeout in seconds (default: 60)"
                        ),
                        "runtime" to PropertySchema(
                            type = "string",
                            description = "Runtime for code execution",
                            enum = listOf("python", "nodejs", "shell")
                        ),
                        "code" to PropertySchema(
                            type = "string",
                            description = "Code string (used with runtime)"
                        ),
                        "cwd" to PropertySchema(
                            type = "string",
                            description = "Working directory alias"
                        )
                    ),
                    required = listOf("command")
                )
            )
        )
    }

    fun isAvailable(): Boolean = isTermuxInstalled() && isSSHReachable()

    fun getStatus(): TermuxStatus {
        val termuxInstalled = isTermuxInstalled()
        val termuxApiInstalled = isTermuxApiInstalled()
        val runCommandPermissionDeclared = isRunCommandPermissionDeclared()
        val runCommandServiceAvailable = isRunCommandServiceAvailable()
        val sshReachable = isSSHReachable()
        val sshConfigPresent = File(SSH_CONFIG_FILE).exists()
        val keypairPresent = File(PRIVATE_KEY).exists() && File(PUBLIC_KEY).exists()

        val (step, message) = when {
            !termuxInstalled -> TermuxSetupStep.TERMUX_NOT_INSTALLED to "Termux 未安装"
            !termuxApiInstalled -> TermuxSetupStep.TERMUX_API_NOT_INSTALLED to "Termux:API 未安装"
            !runCommandPermissionDeclared -> TermuxSetupStep.RUN_COMMAND_PERMISSION_DENIED to "App 未声明 RUN_COMMAND 权限"
            !runCommandServiceAvailable -> TermuxSetupStep.RUN_COMMAND_SERVICE_MISSING to "Termux RUN_COMMAND 服务不可用"
            !keypairPresent -> TermuxSetupStep.KEYPAIR_MISSING to "SSH 密钥对未生成"
            !sshReachable && !sshConfigPresent -> TermuxSetupStep.SSHD_NOT_REACHABLE to "sshd 未启动，SSH 端口 8022 不可达"
            !sshReachable -> TermuxSetupStep.SSHD_NOT_REACHABLE to "SSH 端口 8022 不可达"
            !sshConfigPresent -> TermuxSetupStep.SSH_CONFIG_MISSING to "termux_ssh.json 未生成"
            else -> TermuxSetupStep.READY to "Termux 已就绪"
        }

        return TermuxStatus(
            termuxInstalled = termuxInstalled,
            termuxApiInstalled = termuxApiInstalled,
            runCommandPermissionDeclared = runCommandPermissionDeclared,
            runCommandServiceAvailable = runCommandServiceAvailable,
            sshReachable = sshReachable,
            sshConfigPresent = sshConfigPresent,
            keypairPresent = keypairPresent,
            lastStep = step,
            message = message
        )
    }

    private fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isTermuxApiInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isRunCommandPermissionDeclared(): Boolean {
        return context.packageManager.checkPermission(RUN_COMMAND_PERMISSION, context.packageName) == PackageManager.PERMISSION_GRANTED
    }

    private fun isRunCommandServiceAvailable(): Boolean {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            component = ComponentName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
        }
        return context.packageManager.resolveService(intent, 0) != null
    }

    private fun isSSHReachable(): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(SSH_HOST, SSH_PORT), 1000)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // ==================== Auto-Setup ====================

    /**
     * Ensure Termux SSH is ready. Auto-provisions if needed:
     * 1. Generate SSH keypair (if missing)
     * 2. Install openssh in Termux (via RUN_COMMAND)
     * 3. Deploy authorized_keys
     * 4. Start sshd
     */
    suspend fun triggerAutoSetup(): TermuxStatus {
        withContext(Dispatchers.IO) {
            ensureSSHReady()
        }
        return getStatus()
    }

    private suspend fun ensureSSHReady(): Boolean {
        if (isSSHReachable() && hasCredentials()) return true
        if (!isTermuxInstalled() || !isRunCommandPermissionDeclared() || !isRunCommandServiceAvailable()) return false

        Log.i(TAG, "SSH not ready, attempting auto-setup...")

        // Step 1: Generate keypair if missing
        ensureKeypair()

        // Step 2: Deploy keys and start sshd via RUN_COMMAND
        val setupScript = buildString {
            appendLine("#!/data/data/com.termux/files/usr/bin/bash")
            appendLine("# Auto-setup by AndroidForClaw")
            appendLine("pkg install -y openssh 2>/dev/null")
            appendLine("mkdir -p ~/.ssh")
            appendLine("chmod 700 ~/.ssh")
            // Copy public key from shared storage
            appendLine("cat '$PUBLIC_KEY' >> ~/.ssh/authorized_keys 2>/dev/null || " +
                "cat ~/storage/shared/.androidforclaw/.ssh/id_ed25519.pub >> ~/.ssh/authorized_keys 2>/dev/null")
            appendLine("sort -u ~/.ssh/authorized_keys -o ~/.ssh/authorized_keys")
            appendLine("chmod 600 ~/.ssh/authorized_keys")
            // Start sshd if not running
            appendLine("pgrep sshd > /dev/null || sshd")
            appendLine("echo SETUP_DONE")
        }

        // Write setup script to shared storage
        val scriptFile = File("$CONFIG_DIR/termux_setup.sh")
        withContext(Dispatchers.IO) {
            scriptFile.parentFile?.mkdirs()
            scriptFile.writeText(setupScript)
        }

        // Execute via Termux RUN_COMMAND
        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(scriptFile.absolutePath))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            context.startForegroundService(intent)
            Log.i(TAG, "Sent RUN_COMMAND for SSH auto-setup")
        } catch (e: Exception) {
            Log.w(TAG, "RUN_COMMAND failed: ${e.message}, trying startService")
            try {
                val intent = Intent("com.termux.RUN_COMMAND").apply {
                    setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(scriptFile.absolutePath))
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                }
                context.startService(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Auto-setup failed: ${e2.message}")
                return false
            }
        }

        // Wait for sshd to come up
        for (i in 1..15) {
            delay(1000)
            if (isSSHReachable()) {
                Log.i(TAG, "SSH is now reachable after ${i}s")

                // Write config with key auth
                writeSSHConfig()
                return true
            }
        }

        Log.w(TAG, "SSH not reachable after auto-setup wait")
        return false
    }

    private fun ensureKeypair() {
        val privFile = File(PRIVATE_KEY)
        if (privFile.exists()) return

        try {
            val keyDir = File(KEY_DIR)
            keyDir.mkdirs()

            // Generate ed25519 keypair using keytool-like approach
            val kpg = java.security.KeyPairGenerator.getInstance("Ed25519")
            val keyPair = kpg.generateKeyPair()

            // Write in OpenSSH format using SSHJ
            ensureBouncyCastle()
            val tempClient = SSHClient(DefaultConfig())
            // Use SSHJ's key writing
            // Actually, simpler: use ProcessBuilder to run ssh-keygen if available,
            // or fall back to writing raw keys
            
            // Simplest approach: generate via shell on device
            val pb = ProcessBuilder("sh", "-c",
                "ssh-keygen -t ed25519 -f '${privFile.absolutePath}' -N '' -q 2>/dev/null; echo \$?")
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor(5, TimeUnit.SECONDS)

            if (privFile.exists()) {
                Log.i(TAG, "Generated SSH keypair at $KEY_DIR")
            } else {
                Log.w(TAG, "ssh-keygen failed: $output")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate keypair: ${e.message}")
        }
    }

    private fun writeSSHConfig() {
        try {
            // Detect Termux username
            val whoami = runQuickSSHCommand("whoami")?.trim()
            val user = if (!whoami.isNullOrBlank()) whoami else "shell"

            val config = org.json.JSONObject().apply {
                put("user", user)
                put("key_file", PRIVATE_KEY)
            }
            File(SSH_CONFIG_FILE).writeText(config.toString(2))
            Log.i(TAG, "Wrote SSH config: user=$user, keyFile=$PRIVATE_KEY")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write SSH config: ${e.message}")
        }
    }

    private fun runQuickSSHCommand(command: String): String? {
        return try {
            ensureBouncyCastle()
            val client = SSHClient(DefaultConfig())
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 5000
            client.connect(SSH_HOST, SSH_PORT)

            // Try key auth
            val keyFile = File(PRIVATE_KEY)
            if (keyFile.exists()) {
                val keys = client.loadKeys(keyFile.absolutePath)
                // Try common Termux usernames
                for (user in listOf("shell", "u0_a408", "u0_a100")) {
                    try {
                        client.authPublickey(user, keys)
                        break
                    } catch (e: Exception) { continue }
                }
            }

            if (!client.isAuthenticated) {
                client.disconnect()
                return null
            }

            val session = client.startSession()
            val cmd = session.exec(command)
            cmd.join(5, TimeUnit.SECONDS)
            val result = cmd.inputStream.bufferedReader().readText()
            session.close()
            client.disconnect()
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun hasCredentials(): Boolean {
        val configFile = File(SSH_CONFIG_FILE)
        if (!configFile.exists()) return false
        return try {
            val json = org.json.JSONObject(configFile.readText())
            json.optString("user", "").isNotEmpty() &&
                (json.optString("password", "").isNotEmpty() || json.optString("key_file", "").isNotEmpty())
        } catch (e: Exception) { false }
    }

    // ==================== SSH Execution ====================

    private fun ensureBouncyCastle() {
        if (bcRegistered) return
        try {
            val bcProvider = org.bouncycastle.jce.provider.BouncyCastleProvider()
            Security.removeProvider(bcProvider.name)
            Security.insertProviderAt(bcProvider, 1)
            bcRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "BouncyCastle registration: ${e.message}")
        }
    }

    private fun loadSSHConfig(): SSHConfig {
        try {
            val file = File(SSH_CONFIG_FILE)
            if (file.exists()) {
                val json = org.json.JSONObject(file.readText())
                return SSHConfig(
                    user = json.optString("user", ""),
                    password = json.optString("password", ""),
                    keyFile = json.optString("key_file", "")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load SSH config: ${e.message}")
        }
        return SSHConfig()
    }

    private fun createSSHClient(config: SSHConfig): SSHClient {
        ensureBouncyCastle()

        val client = SSHClient(DefaultConfig())
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connectTimeout = 10_000
        client.connect(SSH_HOST, SSH_PORT)

        when {
            config.keyFile.isNotEmpty() && File(config.keyFile).exists() -> {
                val keyProvider = client.loadKeys(config.keyFile)
                client.authPublickey(config.user.ifEmpty { "shell" }, keyProvider)
            }
            config.password.isNotEmpty() -> {
                client.authPassword(config.user, config.password)
            }
            else -> {
                // Try default key locations
                val keyPaths = listOf(PRIVATE_KEY, "$CONFIG_DIR/termux_id_rsa", "$CONFIG_DIR/id_rsa")
                var authenticated = false
                for (path in keyPaths) {
                    try {
                        if (File(path).exists()) {
                            val keys = client.loadKeys(path)
                            client.authPublickey(config.user.ifEmpty { "shell" }, keys)
                            authenticated = true
                            break
                        }
                    } catch (e: Exception) { continue }
                }
                if (!authenticated) {
                    throw IOException("Termux connection not configured")
                }
            }
        }
        return client
    }

    private fun sshExec(command: String, cwd: String?, timeoutS: Int): ExecResult {
        val config = loadSSHConfig()
        val client = createSSHClient(config)
        try {
            val session = client.startSession()
            try {
                val fullCommand = if (cwd != null) {
                    "cd ${shellEscape(cwd)} && $command"
                } else {
                    command
                }
                val cmd = session.exec(fullCommand)
                cmd.join(timeoutS.toLong(), TimeUnit.SECONDS)
                val stdout = cmd.inputStream.bufferedReader().readText()
                val stderr = cmd.errorStream.bufferedReader().readText()
                val exitCode = cmd.exitStatus ?: -1
                return ExecResult(exitCode == 0, stdout, stderr, exitCode)
            } finally { session.close() }
        } finally { client.disconnect() }
    }

    private fun shellEscape(s: String) = "'" + s.replace("'", "'\\''") + "'"

    // ==================== Tool Interface ====================

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. Check Termux status first
        val initialStatus = getStatus()
        if (!initialStatus.termuxInstalled) {
            return ToolResult(
                success = false,
                content = "Termux is not installed. Install from F-Droid: https://f-droid.org/packages/com.termux/",
                metadata = mapOf("backend" to "termux", "status" to initialStatus.message, "step" to initialStatus.lastStep.name)
            )
        }

        // 2. Resolve command
        val command = args["command"] as? String
        val runtime = args["runtime"] as? String
        val code = args["code"] as? String
        val cwd = (args["working_dir"] as? String) ?: (args["cwd"] as? String)
        val timeout = (args["timeout"] as? Number)?.toInt() ?: DEFAULT_TIMEOUT_S

        val resolvedCommand = when {
            !command.isNullOrBlank() -> command
            !runtime.isNullOrBlank() && !code.isNullOrBlank() -> {
                when (runtime) {
                    "python" -> "python3 -c ${shellEscape(code)}"
                    "nodejs" -> "node -e ${shellEscape(code)}"
                    "shell" -> code
                    else -> return ToolResult.error("Invalid runtime: $runtime (use python/nodejs/shell)")
                }
            }
            else -> return ToolResult.error("Missing required parameter: command")
        }

        // 3. Ensure SSH is ready (auto-setup if needed)
        val sshReady = withContext(Dispatchers.IO) { ensureSSHReady() }
        if (!sshReady) {
            val status = getStatus()
            val fallback = when (status.lastStep) {
                TermuxSetupStep.TERMUX_API_NOT_INSTALLED -> "Termux:API is not installed yet."
                TermuxSetupStep.RUN_COMMAND_PERMISSION_DENIED -> "RUN_COMMAND permission is unavailable for this app build."
                TermuxSetupStep.RUN_COMMAND_SERVICE_MISSING -> "Termux RUN_COMMAND service is unavailable."
                TermuxSetupStep.KEYPAIR_MISSING -> "SSH keypair is missing."
                TermuxSetupStep.SSHD_NOT_REACHABLE -> "sshd is not reachable on 127.0.0.1:8022."
                TermuxSetupStep.SSH_CONFIG_MISSING -> "SSH config file was not generated."
                else -> "Please open Termux and run: pkg install openssh && sshd"
            }
            return ToolResult(
                success = false,
                content = "Termux is not ready: ${status.message} $fallback",
                metadata = mapOf("backend" to "termux", "status" to status.message, "step" to status.lastStep.name)
            )
        }

        // 4. Execute via SSH
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(timeout * 1000L + 5000L) {
                    val result = sshExec(resolvedCommand, cwd, timeout)
                    Log.d(TAG, "Exec completed: exitCode=${result.exitCode}, stdout=${result.stdout.length} chars")

                    ToolResult(
                        success = result.success,
                        content = buildString {
                            if (result.stdout.isNotEmpty()) appendLine(result.stdout.trim())
                            if (result.stderr.isNotEmpty()) {
                                if (isNotEmpty()) appendLine()
                                appendLine("STDERR:")
                                appendLine(result.stderr.trim())
                            }
                            if (result.exitCode != 0) {
                                if (isNotEmpty()) appendLine()
                                appendLine("Exit code: ${result.exitCode}")
                            }
                        }.ifEmpty { "(no output)" },
                        metadata = mapOf(
                            "backend" to "termux",
                            "stdout" to result.stdout,
                            "stderr" to result.stderr,
                            "exitCode" to result.exitCode,
                            "command" to resolvedCommand,
                            "working_dir" to (cwd ?: "")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exec failed", e)
                ToolResult(
                    success = false,
                    content = "Command execution failed: ${e.message}",
                    metadata = mapOf(
                        "backend" to "termux",
                        "error" to (e.message ?: "unknown"),
                        "command" to resolvedCommand
                    )
                )
            }
        }
    }

    data class SSHConfig(
        val user: String = "",
        val password: String = "",
        val keyFile: String = ""
    )

    data class ExecResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )
}
