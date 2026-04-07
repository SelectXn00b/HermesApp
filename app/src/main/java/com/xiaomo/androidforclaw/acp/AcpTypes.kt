package com.xiaomo.androidforclaw.acp

/**
 * OpenClaw module: acp
 * Source: OpenClaw/src/acp/types.ts
 *
 * Core ACP types: session, server options, provenance mode.
 */

// ---------------------------------------------------------------------------
// Provenance mode  (aligned with TS ACP_PROVENANCE_MODE_VALUES)
// ---------------------------------------------------------------------------
enum class AcpProvenanceMode(val value: String) {
    OFF("off"),
    META("meta"),
    META_RECEIPT("meta+receipt");

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun normalize(raw: String?): AcpProvenanceMode? {
            if (raw == null) return null
            return byValue[raw.trim().lowercase()]
        }
    }
}

// ---------------------------------------------------------------------------
// ACP session  (aligned with TS AcpSession)
// ---------------------------------------------------------------------------
data class AcpSession(
    val sessionId: String,
    val sessionKey: String,
    val cwd: String,
    val createdAt: Long,
    val lastTouchedAt: Long,
    val activeRunId: String? = null,
    /** On Android, cancellation uses kotlinx.coroutines Job instead of AbortController. */
    val cancellable: Boolean = false,
)

// ---------------------------------------------------------------------------
// ACP server options  (aligned with TS AcpServerOptions)
// ---------------------------------------------------------------------------
data class AcpServerOptions(
    val gatewayUrl: String? = null,
    val gatewayToken: String? = null,
    val gatewayPassword: String? = null,
    val defaultSessionKey: String? = null,
    val defaultSessionLabel: String? = null,
    val requireExistingSession: Boolean = false,
    val resetSession: Boolean = false,
    val prefixCwd: Boolean = false,
    val provenanceMode: AcpProvenanceMode? = null,
    val sessionCreateRateLimit: AcpRateLimitConfig? = null,
    val verbose: Boolean = false,
)

data class AcpRateLimitConfig(
    val maxRequests: Int? = null,
    val windowMs: Long? = null,
)

// ---------------------------------------------------------------------------
// ACP agent info  (aligned with TS ACP_AGENT_INFO)
// ---------------------------------------------------------------------------
object AcpAgentInfo {
    const val NAME = "androidforclaw-acp"
    const val TITLE = "AndroidForClaw ACP Gateway"
    const val VERSION = "1.0.0" // matches app version
}

// ---------------------------------------------------------------------------
// Session interaction mode  (aligned with TS AcpSessionInteractionMode)
// ---------------------------------------------------------------------------
enum class AcpSessionInteractionMode(val value: String) {
    INTERACTIVE("interactive"),
    PARENT_OWNED_BACKGROUND("parent-owned-background");

    companion object {
        fun resolve(
            acpMode: String?,
            spawnedBy: String?,
            parentSessionKey: String?,
        ): AcpSessionInteractionMode {
            if (acpMode != "oneshot") return INTERACTIVE
            val hasSpawner = !spawnedBy.isNullOrBlank()
            val hasParent = !parentSessionKey.isNullOrBlank()
            return if (hasSpawner || hasParent) PARENT_OWNED_BACKGROUND else INTERACTIVE
        }
    }
}

fun isParentOwnedBackgroundAcpSession(
    acpMode: String?,
    spawnedBy: String?,
    parentSessionKey: String?,
): Boolean {
    return AcpSessionInteractionMode.resolve(acpMode, spawnedBy, parentSessionKey) ==
        AcpSessionInteractionMode.PARENT_OWNED_BACKGROUND
}

// ---------------------------------------------------------------------------
// ACP runtime session mode  (aligned with TS AcpRuntimeSessionMode)
// ---------------------------------------------------------------------------
enum class AcpRuntimeSessionMode(val value: String) {
    PERSISTENT("persistent"),
    ONESHOT("oneshot");

    companion object {
        fun normalize(raw: String?): AcpRuntimeSessionMode {
            return if (raw?.trim()?.lowercase() == "oneshot") ONESHOT else PERSISTENT
        }
    }
}
