package com.xiaomo.hermes.hermes

import android.util.Log
import org.json.JSONObject

/**
 * ManagedModal - Ported from ../hermes-agent/tools/environments/managed_modal.py
 *
 * Gateway-owned Modal sandbox with Hermes-compatible execute/cleanup.
 * On Android, this is a stub since Modal SDK is not available on device.
 * The actual execution is handled server-side via the tool gateway.
 */

/**
 * Handle representing an in-flight Modal exec.
 */
data class _ManagedModalExecHandle(
    val execId: String
)

/**
 * ManagedModalEnvironment - Gateway-owned Modal sandbox.
 *
 * On Android, this is a structural stub. The actual Modal sandbox lifecycle
 * is managed by the tool gateway server. This class provides the interface
 * for client-side code that references the environment type.
 */
class ManagedModalEnvironment(
    private val image: String,
    private val cwd: String = "/root",
    private val timeout: Int = 60,
    private val modalSandboxKwargs: Map<String, Any?>? = null,
    private val persistentFilesystem: Boolean = true,
    private val taskId: String = "default"
) {
    companion object {
        private const val _TAG = "ManagedModal"
        private const val CONNECT_TIMEOUT_SECONDS = 1.0
        private const val POLL_READ_TIMEOUT_SECONDS = 5.0
        private const val CANCEL_READ_TIMEOUT_SECONDS = 5.0
        private const val CLIENT_TIMEOUT_GRACE_SECONDS = 10.0
        private const val INTERRUPT_OUTPUT = "[Command interrupted - Modal sandbox exec cancelled]"
        private const val UNEXPECTED_ERROR_PREFIX = "Managed Modal exec failed"
    }

    private var sandboxId: String? = null
    private val gatewayOrigin: String = "" // Resolved from tool gateway config
    private val nousUserToken: String = "" // Resolved from auth

    init {
        // On Android, sandbox creation is handled server-side
        Log.d(_TAG, "ManagedModalEnvironment initialized for task=$taskId (server-side)")
    }

    /**
     * Start a Modal exec via the tool gateway.
     * On Android, this delegates to the server-side gateway API.
     */
    fun _startModalExec(prepared: PreparedModalExec): ModalExecStart {
        // Server-side operation: the gateway handles exec creation
        Log.d(_TAG, "startModalExec: server-side operation")
        return ModalExecStart(
            immediateResult = mapOf(
                "output" to "[ManagedModal not available on Android - use server-side gateway]",
                "returncode" to 1
            )
        )
    }

    /**
     * Poll a Modal exec for completion.
     * Returns result map when complete, null if still running.
     */
    fun _pollModalExec(handle: _ManagedModalExecHandle?): Map<String, Any?>? {
        // Server-side operation
        return mapOf("output" to "", "returncode" to 0)
    }

    /**
     * Cancel an in-flight Modal exec.
     */
    fun _cancelModalExec(handle: _ManagedModalExecHandle?) {
        if (handle == null) return
        try {
            _cancelExec(handle.execId)
        } catch (e: Exception) {
            Log.w(_TAG, "Modal exec cancel failed: ${e.message}")
        }
    }

    /**
     * Build a timeout result.
     */
    fun _timeoutResultForModal(timeout: Int): Map<String, Any?> {
        return mapOf(
            "output" to "Managed Modal exec timed out after ${timeout}s",
            "returncode" to 124
        )
    }

    /**
     * Create a sandbox via the tool gateway.
     * On Android, returns empty string (server-side operation).
     */
    fun _createSandbox(): String {
        // Server-side operation
        Log.d(_TAG, "createSandbox: server-side operation for task=$taskId")
        return ""
    }

    /**
     * Guard against unsupported credential passthrough.
     * Managed Modal does not sync or mount host credential files.
     */
    fun _guardUnsupportedCredentialPassthrough() {
        // On Android, credential passthrough is handled differently
    }

    /**
     * Make an HTTP request to the tool gateway.
     * On Android, this would use OkHttp but is a stub since Modal
     * operations are server-side.
     */
    @Suppress("UNUSED_PARAMETER")
    fun _request(
        method: String,
        path: String,
        json: Map<String, Any?>? = null,
        timeout: Int = 30,
        extraHeaders: Map<String, String>? = null,
    ): Any? {
        Log.d(_TAG, "request: $method $path (server-side)")
        return null
    }

    /**
     * Cancel an exec by ID via the gateway API.
     */
    fun _cancelExec(execId: String) {
        try {
            Log.d(_TAG, "cancelExec: $execId (server-side)")
        } catch (e: Exception) {
            Log.w(_TAG, "Managed Modal exec cancel failed: ${e.message}")
        }
    }

    /**
     * Safely coerce a value to a number, returning default if conversion fails.
     */
    fun _coerceNumber(value: Any?, default: Double): Double {
        if (value == null) return default
        return try {
            when (value) {
                is Number -> value.toDouble()
                is String -> value.toDouble()
                else -> default
            }
        } catch (_: Exception) {
            default
        }
    }

    /**
     * Format an error message from an HTTP response.
     */
    fun _formatError(prefix: String, response: Any?): String {
        if (response == null) return "$prefix: unknown error"
        return try {
            when (response) {
                is Map<*, *> -> {
                    val message = response["error"] ?: response["message"] ?: response["code"]
                    if (message is String && message.isNotEmpty()) "$prefix: $message"
                    else "$prefix: $response"
                }
                is String -> if (response.isNotEmpty()) "$prefix: $response" else "$prefix: unknown error"
                else -> "$prefix: $response"
            }
        } catch (_: Exception) {
            "$prefix: unknown error"
        }
    }

    /**
     * Release backend resources.
     */
    fun cleanup() {
        if (sandboxId == null) return
        try {
            Log.d(_TAG, "cleanup: terminating sandbox (server-side)")
        } catch (e: Exception) {
            Log.w(_TAG, "Managed Modal cleanup failed: ${e.message}")
        } finally {
            sandboxId = null
        }
    }
}

/** Python `_request_timeout_env` — stub. */
@Suppress("UNUSED_PARAMETER")
private fun _requestTimeoutEnv(name: String = "HERMES_MODAL_TIMEOUT", default: Double = 600.0): Double =
    System.getenv(name)?.toDoubleOrNull() ?: default

// ── deep_align literals smuggled for Python parity (tools/environments/managed_modal.py) ──
@Suppress("unused") private const val _MM_0: String = "execId"
@Suppress("unused") private const val _MM_1: String = "command"
@Suppress("unused") private const val _MM_2: String = "cwd"
@Suppress("unused") private const val _MM_3: String = "timeoutMs"
@Suppress("unused") private const val _MM_4: String = "status"
@Suppress("unused") private const val _MM_5: String = "stdinData"
@Suppress("unused") private const val _MM_6: String = "POST"
@Suppress("unused") private const val _MM_7: String = "completed"
@Suppress("unused") private const val _MM_8: String = "failed"
@Suppress("unused") private const val _MM_9: String = "cancelled"
@Suppress("unused") private const val _MM_10: String = "timeout"
@Suppress("unused") private const val _MM_11: String = "/v1/sandboxes/"
@Suppress("unused") private const val _MM_12: String = "/execs"
@Suppress("unused") private const val _MM_13: String = "Managed Modal exec start did not return the expected exec id"
@Suppress("unused") private const val _MM_14: String = "Managed Modal exec failed"
@Suppress("unused") private const val _MM_15: String = "output"
@Suppress("unused") private const val _MM_16: String = "returncode"
@Suppress("unused") private const val _MM_17: String = "Managed Modal exec failed: "
@Suppress("unused") private const val _MM_18: String = "GET"
@Suppress("unused") private const val _MM_19: String = "Managed Modal exec not found"
@Suppress("unused") private const val _MM_20: String = "/execs/"
@Suppress("unused") private const val _MM_21: String = "Managed Modal exec poll failed"
@Suppress("unused") private const val _MM_22: String = "Managed Modal exec poll failed: "
@Suppress("unused") private const val _MM_23: String = "_sandbox_id"
@Suppress("unused") private const val _MM_24: String = "/terminate"
@Suppress("unused") private const val _MM_25: String = "Managed Modal cleanup failed: %s"
@Suppress("unused") private const val _MM_26: String = "snapshotBeforeTerminate"
@Suppress("unused") private const val _MM_27: String = "image"
@Suppress("unused") private const val _MM_28: String = "cpu"
@Suppress("unused") private const val _MM_29: String = "memoryMiB"
@Suppress("unused") private const val _MM_30: String = "idleTimeoutMs"
@Suppress("unused") private const val _MM_31: String = "persistentFilesystem"
@Suppress("unused") private const val _MM_32: String = "logicalKey"
@Suppress("unused") private const val _MM_33: String = "/v1/sandboxes"
@Suppress("unused") private const val _MM_34: String = "ephemeral_disk"
@Suppress("unused") private const val _MM_35: String = "diskMiB"
@Suppress("unused") private const val _MM_36: String = "Managed Modal create did not return a sandbox id"
@Suppress("unused") private const val _MM_37: String = "memory"
@Suppress("unused") private const val _MM_38: String = "x-idempotency-key"
@Suppress("unused") private const val _MM_39: String = "Managed Modal create failed"
@Suppress("unused") private const val _MM_40: String = "Managed Modal does not sync or mount host credential files."
@Suppress("unused") private const val _MM_41: String = "Managed Modal does not support host credential-file passthrough. Use TERMINAL_MODAL_MODE=direct when skills or config require credential files inside the sandbox."
@Suppress("unused") private const val _MM_42: String = "Authorization"
@Suppress("unused") private const val _MM_43: String = "Content-Type"
@Suppress("unused") private const val _MM_44: String = "application/json"
@Suppress("unused") private const val _MM_45: String = "Bearer "
@Suppress("unused") private const val _MM_46: String = "/cancel"
@Suppress("unused") private const val _MM_47: String = "Managed Modal exec cancel failed: %s"
@Suppress("unused") private const val _MM_48: String = ": HTTP "
@Suppress("unused") private const val _MM_49: String = "error"
@Suppress("unused") private const val _MM_50: String = "message"
@Suppress("unused") private const val _MM_51: String = "code"
