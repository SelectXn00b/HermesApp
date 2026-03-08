package com.xiaomo.androidforclaw.gateway.protocol

/**
 * Protocol version (aligned with OpenClaw)
 */
const val PROTOCOL_VERSION = 45

/**
 * Base Frame type
 */
sealed class Frame {
    abstract val type: String
}

/**
 * Request Frame - client to server RPC call
 */
data class RequestFrame(
    override val type: String = "request",
    val id: String,
    val method: String,
    val params: Map<String, Any?>? = null,
    val timeout: Long? = null
) : Frame()

/**
 * Response Frame - server response to client request
 */
data class ResponseFrame(
    override val type: String = "response",
    val id: String?,
    val result: Any? = null,
    val error: Map<String, Any?>? = null
) : Frame()

/**
 * Event Frame - server to client event broadcast
 */
data class EventFrame(
    override val type: String = "event",
    val event: String,
    val data: Any? = null
) : Frame()

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
    val createdAt: String,
    val updatedAt: String
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
