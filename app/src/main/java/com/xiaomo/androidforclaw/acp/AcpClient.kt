package com.xiaomo.androidforclaw.acp

/**
 * OpenClaw module: acp
 * Source: OpenClaw/src/acp/client.ts
 *        OpenClaw/src/acp/approval-classifier.ts
 *
 * ACP client: Agent Communication Protocol client for inter-agent messaging.
 * Android adaptation: in-process message passing instead of child process spawn.
 */

import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// ACP message types  (aligned with ACP protocol)
// ---------------------------------------------------------------------------
sealed class AcpMessage {
    data class Request(
        val id: String,
        val method: String,
        val params: Map<String, Any?> = emptyMap(),
    ) : AcpMessage()

    data class Response(
        val id: String,
        val result: Any? = null,
        val error: String? = null,
    ) : AcpMessage()

    data class Notification(
        val method: String,
        val params: Map<String, Any?> = emptyMap(),
    ) : AcpMessage()
}

// ---------------------------------------------------------------------------
// Permission types  (aligned with TS RequestPermissionRequest/Response)
// ---------------------------------------------------------------------------
data class AcpPermissionRequest(
    val toolName: String,
    val toolTitle: String? = null,
    val args: Map<String, Any?>? = null,
    val sessionId: String,
)

data class AcpPermissionResponse(
    val approved: Boolean,
    val reason: String? = null,
)

// ---------------------------------------------------------------------------
// Approval classifier  (aligned with TS AcpApprovalClass)
// ---------------------------------------------------------------------------
enum class AcpApprovalClass(val value: String) {
    SAFE("safe"),
    READONLY_SCOPED("readonly_scoped"),
    READONLY_SEARCH("readonly_search"),
    DANGEROUS("dangerous"),
    UNKNOWN("unknown"),
}

// ---------------------------------------------------------------------------
// Session handle  (aligned with TS AcpClientHandle)
// ---------------------------------------------------------------------------
data class AcpSessionHandle(
    val sessionId: String,
    val agentId: String,
    val createdAt: Long = System.currentTimeMillis(),
)

// ---------------------------------------------------------------------------
// AcpClient  (aligned with TS acp/client.ts)
// ---------------------------------------------------------------------------
object AcpClient {

    private const val TAG = "AcpClient"

    /** Max prompt size (2MB) to prevent DoS via memory exhaustion. */
    const val MAX_PROMPT_BYTES = 2 * 1024 * 1024

    /** Safe tools that are auto-approved without permission prompt. */
    val SAFE_AUTO_APPROVE_TOOLS = setOf(
        "read", "search", "web_search", "memory_search"
    )

    /** Tool kind mapping for safe tool identification. */
    val TOOL_KIND_BY_ID = mapOf(
        "read" to "read",
        "search" to "search",
        "web_search" to "search",
        "memory_search" to "search",
    )

    /** Path keys for read tool calls. */
    val READ_TOOL_PATH_KEYS = listOf("path", "file_path", "filePath")

    /** Active ACP sessions. */
    private val sessions = ConcurrentHashMap<String, AcpSessionHandle>()

    /** Pending permission requests. */
    private val pendingPermissions =
        ConcurrentHashMap<String, CompletableDeferred<AcpPermissionResponse>>()

    /** Permission request timeout. */
    const val PERMISSION_TIMEOUT_MS = 30_000L

    /** Tool name validation. */
    private val TOOL_NAME_PATTERN = Regex("^[a-z0-9._-]+$")
    const val TOOL_NAME_MAX_LENGTH = 128

    // -----------------------------------------------------------------------
    // Approval classification  (aligned with TS classifyAcpToolApproval)
    // -----------------------------------------------------------------------

    fun classifyToolApproval(toolName: String?): AcpApprovalClass {
        if (toolName == null) return AcpApprovalClass.UNKNOWN
        val normalized = toolName.lowercase()
        if (normalized in SAFE_AUTO_APPROVE_TOOLS) return AcpApprovalClass.SAFE
        val kind = inferToolKind(normalized)
        return when (kind) {
            ToolKind.READ -> AcpApprovalClass.READONLY_SCOPED
            ToolKind.SEARCH -> AcpApprovalClass.READONLY_SEARCH
            ToolKind.EXECUTE -> AcpApprovalClass.DANGEROUS
            else -> AcpApprovalClass.UNKNOWN
        }
    }

    // -----------------------------------------------------------------------
    // Permission resolution  (aligned with TS resolvePermissionRequest)
    // -----------------------------------------------------------------------

    fun resolvePermission(request: AcpPermissionRequest): AcpPermissionResponse {
        val toolName = request.toolName

        // Validate tool name
        if (toolName.length > TOOL_NAME_MAX_LENGTH || !TOOL_NAME_PATTERN.matches(toolName)) {
            return AcpPermissionResponse(false, "Invalid tool name: $toolName")
        }

        val classification = classifyToolApproval(toolName)

        // Auto-approve safe tools
        if (classification == AcpApprovalClass.SAFE) {
            Log.d(TAG, "Auto-approved safe ACP tool: $toolName")
            return AcpPermissionResponse(true)
        }

        // On Android (single-user device), auto-approve non-dangerous tools
        if (classification != AcpApprovalClass.DANGEROUS) {
            Log.d(TAG, "Auto-approved ACP tool (Android single-user): $toolName")
            return AcpPermissionResponse(true)
        }

        // Dangerous tools still auto-approved on Android but logged
        Log.w(TAG, "Auto-approved dangerous ACP tool (Android): $toolName")
        return AcpPermissionResponse(true)
    }

    // -----------------------------------------------------------------------
    // Session management  (aligned with TS createAcpClient)
    // -----------------------------------------------------------------------

    fun createSession(agentId: String): AcpSessionHandle {
        val sessionId = "acp:${agentId}:${System.currentTimeMillis()}"
        val handle = AcpSessionHandle(sessionId = sessionId, agentId = agentId)
        sessions[sessionId] = handle
        Log.d(TAG, "ACP session created: $sessionId")
        return handle
    }

    fun closeSession(sessionId: String) {
        sessions.remove(sessionId)
        pendingPermissions.remove(sessionId)
            ?.complete(AcpPermissionResponse(false, "Session closed"))
        Log.d(TAG, "ACP session closed: $sessionId")
    }

    fun getSession(sessionId: String): AcpSessionHandle? = sessions[sessionId]

    fun activeSessionCount(): Int = sessions.size

    fun clearAllSessions() {
        for (sessionId in sessions.keys.toList()) {
            closeSession(sessionId)
        }
    }

    /** Check if ACP auth env vars should be stripped for security. */
    fun shouldStripAuthEnvVars(): Boolean = true
}
