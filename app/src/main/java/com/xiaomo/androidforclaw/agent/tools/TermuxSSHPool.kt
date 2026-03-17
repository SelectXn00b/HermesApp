package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.json.JSONObject
import java.io.File
import java.security.Security
import java.util.concurrent.TimeUnit

/**
 * Singleton SSH connection pool for Termux.
 *
 * Maintains a single persistent SSH connection to Termux sshd (localhost:8022),
 * with automatic reconnection and keepalive.
 */
object TermuxSSHPool {
    private const val TAG = "TermuxSSHPool"
    private const val SSH_HOST = "127.0.0.1"
    private const val SSH_PORT = 8022
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val KEEPALIVE_INTERVAL_S = 30

    private const val CONFIG_DIR = "/sdcard/.androidforclaw"
    private const val SSH_CONFIG_FILE = "$CONFIG_DIR/termux_ssh.json"
    private const val KEY_DIR = "$CONFIG_DIR/.ssh"
    private const val PRIVATE_KEY = "$KEY_DIR/id_ed25519"

    private var client: SSHClient? = null
    private val lock = Mutex()
    private var bcRegistered = false

    val isConnected: Boolean
        get() = try {
            client?.isConnected == true && client?.isAuthenticated == true
        } catch (_: Exception) {
            false
        }

    /**
     * Get or create a connected & authenticated SSH client.
     */
    suspend fun getClient(): SSHClient = lock.withLock {
        val c = client
        if (c != null && c.isConnected && c.isAuthenticated) return@withLock c
        // Tear down stale client
        safeDisconnect(c)
        val newClient = connect()
        client = newClient
        newClient
    }

    /**
     * Execute a command over the persistent connection.
     * Retries once on connection failure.
     */
    suspend fun exec(command: String, cwd: String?, timeoutS: Int): ExecResult {
        return try {
            execOnce(command, cwd, timeoutS)
        } catch (e: Exception) {
            // Connection might be stale — reconnect and retry once
            Log.w(TAG, "exec failed (${e.message}), reconnecting and retrying...")
            lock.withLock {
                safeDisconnect(client)
                client = null
            }
            execOnce(command, cwd, timeoutS)
        }
    }

    /**
     * Pre-warm: establish the persistent connection eagerly.
     * Non-fatal — logs and returns on failure.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun warmUp(context: Context) {
        try {
            getClient()
            Log.i(TAG, "SSH connection warmed up")
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up failed: ${e.message}")
        }
    }

    /**
     * Tear down the persistent connection.
     */
    fun disconnect() {
        val c = client
        client = null
        safeDisconnect(c)
    }

    // ─── internals ───

    private suspend fun execOnce(command: String, cwd: String?, timeoutS: Int): ExecResult {
        val ssh = getClient()
        val session = ssh.startSession()
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
        } finally {
            try { session.close() } catch (_: Exception) {}
        }
    }

    private fun connect(): SSHClient {
        ensureBouncyCastle()
        val config = loadSSHConfig()

        Log.d(TAG, "Connecting to $SSH_HOST:$SSH_PORT...")
        val ssh = SSHClient(DefaultConfig())
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connectTimeout = CONNECT_TIMEOUT_MS
        ssh.connect(SSH_HOST, SSH_PORT)
        Log.d(TAG, "TCP connected, authenticating...")

        // Authenticate
        val user = config.user.ifEmpty { "shell" }
        when {
            config.keyFile.isNotEmpty() && File(config.keyFile).exists() -> {
                Log.d(TAG, "Authenticating with key: ${config.keyFile} user=$user")
                ssh.authPublickey(user, ssh.loadKeys(config.keyFile))
            }
            config.password.isNotEmpty() -> {
                Log.d(TAG, "Authenticating with password user=$user")
                ssh.authPassword(user, config.password)
            }
            else -> {
                // Try default key locations
                val keyPaths = listOf(PRIVATE_KEY, "$CONFIG_DIR/termux_id_rsa", "$CONFIG_DIR/id_rsa")
                var authenticated = false
                for (path in keyPaths) {
                    try {
                        if (File(path).exists()) {
                            Log.d(TAG, "Trying key: $path user=$user")
                            ssh.authPublickey(user, ssh.loadKeys(path))
                            authenticated = true
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Key $path failed: ${e.message}")
                        continue
                    }
                }
                if (!authenticated) {
                    throw java.io.IOException("No SSH credentials available for Termux")
                }
            }
        }

        // Set keepalive and timeouts after successful auth
        ssh.connection.keepAlive.keepAliveInterval = KEEPALIVE_INTERVAL_S
        Log.i(TAG, "SSH connected & authenticated (user=$user)")
        return ssh
    }

    private fun loadSSHConfig(): SSHConfig {
        try {
            val file = File(SSH_CONFIG_FILE)
            if (file.exists()) {
                val json = JSONObject(file.readText())
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

    private fun safeDisconnect(c: SSHClient?) {
        try { c?.disconnect() } catch (_: Exception) {}
    }

    private fun shellEscape(s: String) = "'" + s.replace("'", "'\\''") + "'"

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
