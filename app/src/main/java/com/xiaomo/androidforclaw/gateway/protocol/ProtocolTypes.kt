/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server-ws-runtime.ts, server-shared.ts
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway.protocol

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

/**
 * Protocol version (aligned with OpenClaw)
 */
const val PROTOCOL_VERSION = 3

/**
 * Base Frame type
 */
sealed class Frame {
    abstract val type: String
}

/**
 * Request Frame - client to server RPC call
 * Aligned with OpenClaw Protocol v3
 */
data class RequestFrame(
    override val type: String = "req",  // OpenClaw uses "req" not "request"
    val id: String,
    val method: String,
    val params: Any? = null,  // OpenClaw uses Any? not Map
    val timeout: Long? = null
) : Frame()

/**
 * Response Frame - server response to client request
 * Aligned with OpenClaw Protocol v3
 */
data class ResponseFrame(
    override val type: String = "res",  // OpenClaw uses "res" not "response"
    val id: String,  // Required, not nullable
    val ok: Boolean,  // NEW: success flag (required in OpenClaw)
    val payload: Any? = null,  // OpenClaw uses "payload" not "result"
    val error: ErrorShape? = null  // OpenClaw uses ErrorShape not Map
) : Frame()

/**
 * Event Frame - server to client event broadcast
 * Aligned with OpenClaw Protocol v3
 */
data class EventFrame(
    override val type: String = "event",
    val event: String,
    val payload: Any? = null,  // OpenClaw uses "payload" not "data"
    val seq: Long? = null,  // NEW: sequence number for ordering
    val stateVersion: String? = null  // NEW: state version tracking
) : Frame()

/**
 * Hello-Ok Frame - sent on connection establishment
 * Aligned with OpenClaw Protocol v3
 */
data class HelloOkFrame(
    override val type: String = "hello-ok",
    val protocol: Int,
    val server: ServerInfo,
    val features: Features,
    val snapshot: Map<String, Any?>? = null,
    val canvasHostUrl: String? = null,
    val auth: HelloAuth? = null,
    val policy: Policy
) : Frame()

/**
 * Optional auth payload in hello-ok (aligned with OpenClaw)
 */
data class HelloAuth(
    val deviceToken: String,
    val role: String,
    val scopes: List<String>,
    val issuedAtMs: Long? = null
)

/**
 * Server information in Hello frame
 */
data class ServerInfo(
    val version: String,
    val connId: String
)

/**
 * Available features (methods and events)
 */
data class Features(
    val methods: List<String>,
    val events: List<String>
)

/**
 * Gateway policy configuration
 */
data class Policy(
    val maxPayload: Long = 26214400,  // 25MB (aligned with OpenClaw)
    val maxBufferedBytes: Long = 52428800,  // 50MB
    val tickIntervalMs: Long = 30000  // 30 seconds (aligned with OpenClaw)
)

/**
 * Error shape - structured error information
 * Aligned with OpenClaw Protocol v3
 */
data class ErrorShape(
    val code: String,  // Error code (e.g., "METHOD_NOT_FOUND", "INTERNAL_ERROR")
    val message: String,  // Human-readable error message
    val details: Any? = null,  // Additional error details
    val retryable: Boolean? = null,  // Whether the operation can be retried
    val retryAfterMs: Long? = null  // Suggested retry delay in milliseconds
)

// ===== Agent Method Types =====

/**
 * Agent execution parameters
 */
data class AgentParams(
    val sessionKey: String,
    val message: String,
    val thinking: String? = "medium",
    val model: String? = null
)

/**
 * Agent run response
 */
data class AgentRunResponse(
    val runId: String,
    val acceptedAt: Long
)

/**
 * Agent wait parameters
 */
data class AgentWaitParams(
    val runId: String,
    val timeout: Long? = null
)

/**
 * Agent wait response
 */
data class AgentWaitResponse(
    val runId: String,
    val status: String,
    val result: Any? = null
)

/**
 * Agent identity result
 */
data class AgentIdentityResult(
    val name: String,
    val version: String,
    val platform: String,
    val capabilities: List<String>
)

// ===== Session Method Types =====

/**
 * Session list result
 */
data class SessionListResult(
    val sessions: List<SessionInfo>
)

data class SessionInfo(
    val key: String,
    val messageCount: Int,
    val createdAt: Long,       // epoch ms (client expects Long)
    val updatedAt: Long,       // epoch ms (client expects Long)
    val displayName: String?   // client reads this for tab label
)

/**
 * Session preview result
 */
data class SessionPreviewResult(
    val key: String,
    val messages: List<SessionMessage>
)

data class SessionMessage(
    val role: String,
    val content: String,
    val timestamp: Long
)

// ===== Health Method Types =====

/**
 * Health check result
 */
data class HealthResult(
    val status: String,
    val version: String,
    val uptime: Long
)

/**
 * Status check result
 */
data class StatusResult(
    val gateway: GatewayStatus,
    val agent: AgentStatus,
    val sessions: SessionStatus,
    val system: SystemStatus
)

data class GatewayStatus(
    val running: Boolean,
    val port: Int,
    val connections: Int,
    val authenticated: Boolean
)

data class AgentStatus(
    val activeRuns: Int,
    val toolsLoaded: Int
)

data class SessionStatus(
    val total: Int,
    val active: Int
)

data class SystemStatus(
    val platform: String,
    val apiLevel: Int,
    val memory: MemoryInfo,
    val battery: BatteryInfo
)

data class MemoryInfo(
    val total: Long,
    val available: Long,
    val used: Long
)

data class BatteryInfo(
    val level: Int,
    val charging: Boolean
)
