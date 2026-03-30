package com.xiaomo.androidforclaw.acp

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/acp/client.ts (createAcpClient, resolvePermissionRequest, AcpClientHandle)
 * - ../openclaw/src/acp/server.ts
 * - ../openclaw/src/acp/policy.ts
 * - ../openclaw/src/acp/session.ts
 *
 * AndroidForClaw adaptation: Agent Communication Protocol for inter-agent messaging.
 * On Android, ACP uses in-process message passing (no child process spawn).
 */

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.agent.context.DangerousTools
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * ACP message types.
 * Aligned with OpenClaw ACP protocol.
 */
sealed class AcpMessage {
    data class Request(
        val id: String,
        val method: String,
        val params: Map<String, Any?> = emptyMap()
    ) : AcpMessage()

    data class Response(
        val id: String,
        val result: Any? = null,
        val error: String? = null
    ) : AcpMessage()

    data class Notification(
        val method: String,
        val params: Map<String, Any?> = emptyMap()
    ) : AcpMessage()
}

/**
 * ACP permission request for tool execution.
 * Aligned with OpenClaw RequestPermissionRequest.
 */
data class AcpPermissionRequest(
    val toolName: String,
    val args: Map<String, Any?>?,
    val sessionId: String
)

/**
 * ACP permission response.
 */
data class AcpPermissionResponse(
    val approved: Boolean,
    val reason: String? = null
)

/**
 * ACP session handle.
 * Aligned with OpenClaw AcpClientHandle.
 */
data class AcpSessionHandle(
    val sessionId: String,
    val agentId: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * ACP access policy levels.
 * Aligned with OpenClaw acp/policy.ts.
 */
enum class AcpAccessLevel {
    /** Full access — all tools available */
    FULL,
    /** Safe access — only safe tools auto-approved */
    SAFE,
    /** Restricted — requires explicit approval for all tools */
    RESTRICTED,
    /** Blocked — no tool access */
    BLOCKED
}

/**
 * AcpClient — Agent Communication Protocol client.
 * Aligned with OpenClaw acp/client.ts.
 *
 * On Android, ACP communicates between agent sessions in-process
 * via the SubagentRegistry / SessionsSendTool rather than spawning
 * child processes.
 */
object AcpClient {

    private const val TAG = "AcpClient"

    /**
     * Safe tools that are auto-approved without permission prompt.
     * Aligned with OpenClaw SAFE_AUTO_APPROVE_TOOL_IDS.
     */
    val SAFE_AUTO_APPROVE_TOOLS = setOf(
        "read", "search", "web_search", "memory_search"
    )

    /**
     * Trusted safe tool aliases.
     * Aligned with OpenClaw TRUSTED_SAFE_TOOL_ALIASES.
     */
    val TRUSTED_SAFE_TOOL_ALIASES = setOf("search")

    /**
     * Tool kind mapping for safe tool identification.
     * Aligned with OpenClaw TOOL_KIND_BY_ID.
     */
    val TOOL_KIND_BY_ID = mapOf(
        "read" to "read",
        "search" to "search",
        "web_search" to "search",
        "memory_search" to "search"
    )

    /** Path keys that may contain file paths in read tool calls */
    val READ_TOOL_PATH_KEYS = listOf("path", "file_path", "filePath")

    /** Active ACP sessions */
    private val sessions = ConcurrentHashMap<String, AcpSessionHandle>()

    /** Pending permission requests */
    private val pendingPermissions = ConcurrentHashMap<String, CompletableDeferred<AcpPermissionResponse>>()

    /** Permission request timeout */
    const val PERMISSION_TIMEOUT_MS = 30_000L

    /** Tool name validation */
    private val TOOL_NAME_PATTERN = Regex("^[a-z0-9._-]+$")
    const val TOOL_NAME_MAX_LENGTH = 128

    /**
     * Resolve a permission request for tool execution.
     * Aligned with OpenClaw resolvePermissionRequest.
     *
     * Auto-approves safe tools; dangerous tools require explicit approval.
     */
    fun resolvePermission(request: AcpPermissionRequest): AcpPermissionResponse {
        val toolName = request.toolName

        // Validate tool name
        if (toolName.length > TOOL_NAME_MAX_LENGTH || !TOOL_NAME_PATTERN.matches(toolName)) {
            return AcpPermissionResponse(false, "Invalid tool name: $toolName")
        }

        // Block dangerous ACP tools
        if (toolName in DangerousTools.DANGEROUS_ACP_TOOLS) {
            Log.w(TAG, "Blocked dangerous ACP tool: $toolName")
            return AcpPermissionResponse(false, "Tool '$toolName' is blocked in ACP context")
        }

        // Auto-approve safe tools
        if (toolName in SAFE_AUTO_APPROVE_TOOLS) {
            return AcpPermissionResponse(true)
        }

        // For other tools, auto-approve in Android context (single-user device)
        Log.d(TAG, "Auto-approved ACP tool (Android single-user): $toolName")
        return AcpPermissionResponse(true)
    }

    /**
     * Create an ACP session.
     * Aligned with OpenClaw createAcpClient.
     */
    fun createSession(agentId: String): AcpSessionHandle {
        val sessionId = "acp:${agentId}:${System.currentTimeMillis()}"
        val handle = AcpSessionHandle(sessionId = sessionId, agentId = agentId)
        sessions[sessionId] = handle
        Log.d(TAG, "ACP session created: $sessionId")
        return handle
    }

    /**
     * Close an ACP session.
     */
    fun closeSession(sessionId: String) {
        sessions.remove(sessionId)
        pendingPermissions.remove(sessionId)?.complete(AcpPermissionResponse(false, "Session closed"))
        Log.d(TAG, "ACP session closed: $sessionId")
    }

    /**
     * Get an active session.
     */
    fun getSession(sessionId: String): AcpSessionHandle? = sessions[sessionId]

    /**
     * Get active session count.
     */
    fun activeSessionCount(): Int = sessions.size

    /**
     * Check if ACP tools should be stripped from env vars for security.
     * Aligned with OpenClaw shouldStripProviderAuthEnvVarsForAcpServer.
     */
    fun shouldStripAuthEnvVars(): Boolean = true
}
