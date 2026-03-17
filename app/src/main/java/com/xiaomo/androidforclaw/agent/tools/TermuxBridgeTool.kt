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
        private const val STATUS_FILE = "$CONFIG_DIR/termux_setup_status.json"
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
        ).also { persistStatus(it) }
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
        // RUN_COMMAND is a dangerous permission defined by Termux.
        // We need both: declared in manifest AND runtime-granted.
        val declared = try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            pkgInfo.requestedPermissions?.contains(RUN_COMMAND_PERMISSION) == true
        } catch (e: Exception) { false }

        if (!declared) return false

        // Check if runtime permission is granted
        return context.checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if RUN_COMMAND permission needs runtime request.
     * Call this from an Activity to trigger the permission dialog.
     */
    fun needsRuntimePermissionRequest(): Boolean {
        val declared = try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            pkgInfo.requestedPermissions?.contains(RUN_COMMAND_PERMISSION) == true
        } catch (e: Exception) { false }

        if (!declared) return false
        return context.checkSelfPermission(RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED
    }

    private fun isRunCommandServiceAvailable(): Boolean {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            component = ComponentName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
        }
        return context.packageManager.resolveService(intent, 0) != null
    }

    private fun isSSHReachable(): Boolean {
        return try {
            // Use NIO SocketChannel for non-blocking connect with reliable timeout
            val ch = java.nio.channels.SocketChannel.open()
            ch.configureBlocking(false)
            ch.connect(java.net.InetSocketAddress(SSH_HOST, SSH_PORT))
            val connected = ch.finishConnect() || run {
                // Wait up to 1 second for connection
                val sel = java.nio.channels.Selector.open()
                ch.register(sel, java.nio.channels.SelectionKey.OP_CONNECT)
                val ready = sel.select(1000L) > 0 && ch.finishConnect()
                sel.close()
                ready
            }
            ch.close()
            connected
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

                // Pre-warm the persistent SSH connection pool
                TermuxSSHPool.warmUp(context)
                return true
            }
        }

        Log.w(TAG, "SSH not reachable after auto-setup wait")
        return false
    }

    private fun ensureKeypair() {
        val privFile = File(PRIVATE_KEY)
        val pubFile = File(PUBLIC_KEY)
        if (privFile.exists() && pubFile.exists()) return

        val keyDir = File(KEY_DIR)
        keyDir.mkdirs()

        // Strategy 1: ssh-keygen (available on most Android devices)
        try {
            val pb = ProcessBuilder("sh", "-c",
                "ssh-keygen -t ed25519 -f '${privFile.absolutePath}' -N '' -q 2>/dev/null; echo \$?")
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor(5, TimeUnit.SECONDS)

            if (privFile.exists() && pubFile.exists()) {
                Log.i(TAG, "Generated SSH keypair via ssh-keygen at $KEY_DIR")
                return
            }
            Log.w(TAG, "ssh-keygen attempt failed: $output")
        } catch (e: Exception) {
            Log.w(TAG, "ssh-keygen not available: ${e.message}")
        }

        // Strategy 2: BouncyCastle Ed25519 keypair generation
        try {
            ensureBouncyCastle()
            val gen = org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator()
            gen.init(org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters(java.security.SecureRandom()))
            val pair = gen.generateKeyPair()

            val privParams = pair.private as org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
            val pubParams = pair.public as org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

            // Write public key in OpenSSH format: "ssh-ed25519 <base64>"
            val pubBlob = java.io.ByteArrayOutputStream()
            fun writeSSHString(out: java.io.ByteArrayOutputStream, data: ByteArray) {
                val len = data.size
                out.write(byteArrayOf((len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte()))
                out.write(data)
            }
            writeSSHString(pubBlob, "ssh-ed25519".toByteArray())
            writeSSHString(pubBlob, pubParams.encoded)
            val pubB64 = android.util.Base64.encodeToString(pubBlob.toByteArray(), android.util.Base64.NO_WRAP)
            pubFile.writeText("ssh-ed25519 $pubB64 androidforclaw@device\n")

            // Write private key in OpenSSH PEM format (sshj-compatible)
            val privBlob = buildOpenSSHPrivateKey(privParams, pubParams)
            privFile.writeBytes(privBlob)
            // Set restrictive permissions (best-effort on Android)
            privFile.setReadable(false, false)
            privFile.setReadable(true, true)

            if (privFile.exists() && pubFile.exists()) {
                Log.i(TAG, "Generated SSH keypair via BouncyCastle at $KEY_DIR")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "BouncyCastle keypair generation failed: ${e.message}")
        }

        Log.e(TAG, "All keypair generation strategies failed")
    }

    /**
     * Build an OpenSSH-format private key blob for Ed25519.
     *
     * Format: "openssh-key-v1\0" magic, then:
     *   ciphername="none", kdfname="none", kdf="", nkeys=1,
     *   public key blob, private section (checkint×2 + keytype + pub + priv + comment + padding).
     *
     * This produces a file that sshj and standard `ssh` can read directly.
     */
    private fun buildOpenSSHPrivateKey(
        privParams: org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters,
        pubParams: org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
    ): ByteArray {
        val pubRaw = pubParams.encoded   // 32 bytes
        val privRaw = privParams.encoded  // 32 bytes (seed)
        val comment = "androidforclaw@device"

        // SSH wire format helpers
        fun sshPutInt(out: java.io.ByteArrayOutputStream, v: Int) {
            out.write(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()))
        }
        fun sshPutBytes(out: java.io.ByteArrayOutputStream, b: ByteArray) { sshPutInt(out, b.size); out.write(b) }
        fun sshPutString(out: java.io.ByteArrayOutputStream, s: String) { sshPutBytes(out, s.toByteArray()) }

        // --- public key blob (for the "public key" section) ---
        val pubBlob = java.io.ByteArrayOutputStream().also { buf ->
            sshPutString(buf, "ssh-ed25519")
            sshPutBytes(buf, pubRaw)
        }.toByteArray()

        // --- private section (unencrypted) ---
        val rng = java.security.SecureRandom()
        val checkInt = rng.nextInt()
        val privSection = java.io.ByteArrayOutputStream().also { buf ->
            sshPutInt(buf, checkInt)
            sshPutInt(buf, checkInt)
            sshPutString(buf, "ssh-ed25519")
            sshPutBytes(buf, pubRaw)
            // Ed25519 private key in OpenSSH = 64 bytes: seed(32) || public(32)
            sshPutBytes(buf, privRaw + pubRaw)
            sshPutString(buf, comment)
        }
        // Pad to block size 8 (cipher "none" uses blocksize=8)
        var pad = 1
        while (privSection.size() % 8 != 0) {
            privSection.write(pad++)
        }
        val privSectionBytes = privSection.toByteArray()

        // --- assemble full blob ---
        val out = java.io.ByteArrayOutputStream()
        out.write("openssh-key-v1\u0000".toByteArray()) // AUTH_MAGIC
        sshPutString(out, "none")       // ciphername
        sshPutString(out, "none")       // kdfname
        sshPutBytes(out, ByteArray(0))  // kdf (empty)
        sshPutInt(out, 1)               // number of keys
        sshPutBytes(out, pubBlob)       // public key
        sshPutBytes(out, privSectionBytes) // private section

        val raw = out.toByteArray()
        val b64 = android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
        val pem = buildString {
            appendLine("-----BEGIN OPENSSH PRIVATE KEY-----")
            b64.chunked(70).forEach { appendLine(it) }
            appendLine("-----END OPENSSH PRIVATE KEY-----")
        }
        return pem.toByteArray()
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

    private fun persistStatus(status: TermuxStatus) {
        try {
            val json = org.json.JSONObject().apply {
                put("termuxInstalled", status.termuxInstalled)
                put("termuxApiInstalled", status.termuxApiInstalled)
                put("runCommandPermissionDeclared", status.runCommandPermissionDeclared)
                put("runCommandServiceAvailable", status.runCommandServiceAvailable)
                put("sshReachable", status.sshReachable)
                put("sshConfigPresent", status.sshConfigPresent)
                put("keypairPresent", status.keypairPresent)
                put("lastStep", status.lastStep.name)
                put("message", status.message)
                put("ready", status.ready)
                put("updatedAt", System.currentTimeMillis())
            }
            val file = File(STATUS_FILE)
            file.parentFile?.mkdirs()
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist Termux status: ${e.message}")
        }
    }

    // ==================== SSH Execution (delegated to TermuxSSHPool) ====================

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
            return ToolResult(
                success = false,
                content = TermuxStatusFormatter.userFacingMessage(status),
                metadata = mapOf("backend" to "termux", "status" to status.message, "step" to status.lastStep.name)
            )
        }

        // 4. Execute via SSH (persistent connection pool)
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(timeout * 1000L + 5000L) {
                    val result = TermuxSSHPool.exec(resolvedCommand, cwd, timeout)
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

}
