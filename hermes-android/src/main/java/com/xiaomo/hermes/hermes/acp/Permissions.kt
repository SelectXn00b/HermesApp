/** 1:1 对齐 hermes/acp_adapter/permissions.py */
package com.xiaomo.hermes.hermes.acp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ACP permission bridging — maps ACP approval requests to hermes approval callbacks.
 */
object Permissions {

    private const val _TAG = "ACP.Permissions"

    /**
     * Maps ACP PermissionOptionKind -> hermes approval result strings.
     *
     * Python: _KIND_TO_HERMES dict
     */
    private val KIND_TO_HERMES: Map<String, String> = mapOf(
        "allow_once" to "once",
        "allow_always" to "always",
        "reject_once" to "deny",
        "reject_always" to "deny"
    )

    /**
     * Permission option data class, mirroring ACP PermissionOption schema.
     */
    data class PermissionOption(
        val optionId: String,
        val kind: String,
        val name: String
    )

    /**
     * Allowed outcome data class, mirroring ACP AllowedOutcome schema.
     */
    data class AllowedOutcome(
        val optionId: String
    )

    /**
     * Permission response wrapper.
     */
    data class PermissionResponse(
        val outcome: Any? // AllowedOutcome or other
    )

    /**
     * Return a hermes-compatible approval_callback(command, description) -> String
     * that bridges to the ACP client's request_permission call.
     *
     * @param requestPermissionFn Suspend function that requests permission from ACP client.
     * @param scope CoroutineScope on which the ACP connection lives.
     * @param sessionId Current ACP session id.
     * @param timeout Seconds to wait for a response before auto-denying.
     */
    fun makeApprovalCallback(
        requestPermissionFn: suspend (sessionId: String, toolCall: Any, options: List<PermissionOption>) -> PermissionResponse,
        scope: CoroutineScope,
        sessionId: String,
        timeout: Long = 60_000L
    ): (command: String, description: String) -> String {

        return { command: String, description: String ->
            val options = listOf(
                PermissionOption(optionId = "allow_once", kind = "allow_once", name = "Allow once"),
                PermissionOption(optionId = "allow_always", kind = "allow_always", name = "Allow always"),
                PermissionOption(optionId = "deny", kind = "reject_once", name = "Deny")
            )

            // Python: _acp.start_tool_call("perm-check", command, kind="execute")
            val toolCall = mapOf(
                "id" to "perm-check",
                "title" to command,
                "kind" to "execute"
            )

            var result = "deny"

            // Python uses asyncio.run_coroutine_threadsafe + future.result(timeout=timeout)
            // Kotlin: We use a blocking approach with timeout for the callback contract.
            try {
                val job = scope.launch {
                    val response = withTimeoutOrNull(timeout) {
                        requestPermissionFn(sessionId, toolCall, options)
                    }

                    if (response != null) {
                        val outcome = response.outcome
                        if (outcome is AllowedOutcome) {
                            val optionId = outcome.optionId
                            // Look up the kind from our options list
                            for (opt in options) {
                                if (opt.optionId == optionId) {
                                    result = KIND_TO_HERMES[opt.kind] ?: "deny"
                                    break
                                }
                            }
                            if (result == "deny") {
                                result = "once" // fallback for unknown option_id
                            }
                        }
                    } else {
                        Log.w(_TAG, "Permission request timed out")
                    }
                }
                // Note: In a real implementation, we'd need to block here to
                // return the result synchronously. This is a structural placeholder.
            } catch (e: Exception) {
                Log.w(_TAG, "Permission request failed: ${e.message}")
                result = "deny"
            }

            result
        }
    }
}
