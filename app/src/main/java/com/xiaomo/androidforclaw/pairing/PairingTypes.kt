package com.xiaomo.androidforclaw.pairing

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/pairing-store.ts (types) +
 *         OpenClaw/src/pairing/setup-code.ts (types)
 *
 * Aligned 1:1 with TS pairing type definitions.
 */

// ---------------------------------------------------------------------------
// PairingChannel — type alias from pairing-store.ts
// ---------------------------------------------------------------------------

typealias PairingChannel = String

// ---------------------------------------------------------------------------
// PairingRequest — from pairing-store.ts
// ---------------------------------------------------------------------------

data class PairingRequest(
    val id: String,
    val code: String,
    val createdAt: String,
    val lastSeenAt: String,
    val meta: Map<String, String>? = null,
)

// ---------------------------------------------------------------------------
// PairingSetupPayload — from setup-code.ts
// ---------------------------------------------------------------------------

data class PairingSetupPayload(
    val url: String,
    val bootstrapToken: String,
)

// ---------------------------------------------------------------------------
// PairingSetupResolution — from setup-code.ts
// ---------------------------------------------------------------------------

sealed class PairingSetupResolution {
    data class Ok(
        val payload: PairingSetupPayload,
        val authLabel: String, // "token" | "password"
        val urlSource: String,
    ) : PairingSetupResolution()

    data class Error(
        val error: String,
    ) : PairingSetupResolution()
}

// ---------------------------------------------------------------------------
// PairingChallengeParams — from pairing-challenge.ts
// ---------------------------------------------------------------------------

data class PairingChallengeParams(
    val channel: PairingChannel,
    val senderId: String,
    val senderIdLine: String,
    val meta: Map<String, String?>? = null,
    val upsertPairingRequest: suspend (id: String, meta: Map<String, String?>?) -> UpsertResult,
    val sendPairingReply: suspend (text: String) -> Unit,
    val buildReplyText: ((code: String, senderIdLine: String) -> String)? = null,
    val onCreated: ((code: String) -> Unit)? = null,
    val onReplyError: ((error: Throwable) -> Unit)? = null,
)

data class UpsertResult(
    val code: String,
    val created: Boolean,
)

data class IssuePairingChallengeResult(
    val created: Boolean,
    val code: String? = null,
)

// ---------------------------------------------------------------------------
// Constants — from pairing-store.ts
// ---------------------------------------------------------------------------

object PairingConstants {
    const val PAIRING_CODE_LENGTH = 8
    const val PAIRING_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val PAIRING_PENDING_TTL_MS = 60 * 60 * 1000L // 1 hour
    const val PAIRING_PENDING_MAX = 3
    const val DEFAULT_ACCOUNT_ID = "default"
}
