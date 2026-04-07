package com.xiaomo.androidforclaw.pairing

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/pairing-challenge.ts
 *
 * Shared pairing challenge issuance for DM pairing policy pathways.
 * Ensures every channel follows the same create-if-missing + reply flow.
 * Aligned 1:1 with TS issuePairingChallenge.
 */
object PairingChallenge {

    /**
     * Issue a pairing challenge.
     * Creates a pairing request if one doesn't exist, then sends a reply.
     * Aligned with TS issuePairingChallenge.
     */
    suspend fun issuePairingChallenge(params: PairingChallengeParams): IssuePairingChallengeResult {
        val upsertResult = params.upsertPairingRequest(params.senderId, params.meta)

        if (!upsertResult.created) {
            return IssuePairingChallengeResult(created = false)
        }

        params.onCreated?.invoke(upsertResult.code)

        val replyText = params.buildReplyText?.invoke(upsertResult.code, params.senderIdLine)
            ?: PairingMessages.buildPairingReply(
                channel = params.channel,
                idLine = params.senderIdLine,
                code = upsertResult.code,
            )

        try {
            params.sendPairingReply(replyText)
        } catch (err: Throwable) {
            params.onReplyError?.invoke(err)
        }

        return IssuePairingChallengeResult(created = true, code = upsertResult.code)
    }
}
