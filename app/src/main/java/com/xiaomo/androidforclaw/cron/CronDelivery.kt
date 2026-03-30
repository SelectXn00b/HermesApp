package com.xiaomo.androidforclaw.cron

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/delivery.ts
 *   (resolveCronDeliveryPlan, resolveFailureDestination, sendFailureNotificationAnnounce)
 *
 * AndroidForClaw adaptation: cron job output delivery to channels.
 */

import com.xiaomo.androidforclaw.logging.Log

/**
 * Resolved delivery plan — where to send cron job output.
 * Aligned with OpenClaw CronDeliveryPlan.
 */
data class CronDeliveryPlan(
    val mode: DeliveryMode,
    val channel: String? = null,
    val to: String? = null,
    val accountId: String? = null,
    val source: String = "delivery",  // "delivery" | "payload"
    val requested: Boolean = false
)

/**
 * Failure delivery plan.
 * Aligned with OpenClaw CronFailureDeliveryPlan.
 */
data class CronFailureDeliveryPlan(
    val mode: String,  // "announce" | "webhook"
    val channel: String? = null,
    val to: String? = null,
    val accountId: String? = null
)

/** Failure notification timeout */
const val FAILURE_NOTIFICATION_TIMEOUT_MS = 30_000L

/**
 * CronDeliveryResolver — Cron job output delivery resolution.
 * Aligned with OpenClaw cron/delivery.ts.
 */
object CronDeliveryResolver {

    private const val TAG = "CronDeliveryResolver"

    // ── Normalization helpers (aligned with OpenClaw) ──

    private fun normalizeChannel(value: Any?): String? {
        if (value !is String) return null
        val trimmed = value.trim().lowercase()
        return trimmed.ifEmpty { null }
    }

    private fun normalizeTo(value: Any?): String? {
        if (value !is String) return null
        val trimmed = value.trim()
        return trimmed.ifEmpty { null }
    }

    private fun normalizeAccountId(value: Any?): String? = normalizeTo(value)

    private fun normalizeMode(value: Any?): String? {
        if (value !is String) return null
        return when (value.trim().lowercase()) {
            "announce" -> "announce"
            "webhook" -> "webhook"
            "none" -> "none"
            "deliver" -> "announce"  // legacy alias
            else -> null
        }
    }

    /**
     * Resolve delivery plan for a cron job.
     * Aligned with OpenClaw resolveCronDeliveryPlan.
     */
    fun resolveDeliveryPlan(job: CronJob): CronDeliveryPlan {
        // Extract delivery and payload
        val delivery = job.delivery
        val payload = job.payload

        val payloadChannel = if (payload is CronPayload.AgentTurn) normalizeChannel(payload.channel) else null
        val payloadTo = if (payload is CronPayload.AgentTurn) normalizeTo(payload.to) else null

        // Check explicit delivery config
        if (delivery != null) {
            val rawMode = normalizeMode(delivery.mode.name) ?: "announce"
            val deliveryChannel = normalizeChannel(delivery.channel) ?: payloadChannel ?: "last"
            val deliveryTo = normalizeTo(delivery.to) ?: payloadTo

            val mode = when (rawMode) {
                "announce" -> DeliveryMode.ANNOUNCE
                "webhook" -> DeliveryMode.WEBHOOK
                "none" -> DeliveryMode.NONE
                else -> DeliveryMode.ANNOUNCE
            }

            return CronDeliveryPlan(
                mode = mode,
                channel = if (mode == DeliveryMode.ANNOUNCE) deliveryChannel else null,
                to = deliveryTo,
                accountId = normalizeAccountId(delivery.accountId),
                source = "delivery",
                requested = mode == DeliveryMode.ANNOUNCE
            )
        }

        // Legacy: check payload for delivery hints
        if (payload is CronPayload.AgentTurn) {
            val legacyMode = when (payload.deliver) {
                true -> "explicit"
                false -> "off"
                else -> "auto"
            }

            if (legacyMode == "explicit" || (legacyMode == "auto" && payloadChannel != null)) {
                return CronDeliveryPlan(
                    mode = DeliveryMode.ANNOUNCE,
                    channel = payloadChannel ?: "last",
                    to = payloadTo,
                    source = "payload",
                    requested = legacyMode == "explicit"
                )
            }
            if (legacyMode == "off") {
                return CronDeliveryPlan(mode = DeliveryMode.NONE, source = "payload")
            }
        }

        return CronDeliveryPlan(mode = DeliveryMode.NONE)
    }

    /**
     * Resolve failure destination for a cron job.
     * Aligned with OpenClaw resolveFailureDestination.
     *
     * Layers global config as base, then overlays job-level failureDestination.
     */
    fun resolveFailureDestination(
        job: CronJob,
        globalFailureMode: String? = null,
        globalFailureChannel: String? = null,
        globalFailureTo: String? = null,
        globalFailureAccountId: String? = null
    ): CronFailureDeliveryPlan? {
        // Start with global defaults
        var mode = normalizeMode(globalFailureMode)
        var channel = normalizeChannel(globalFailureChannel)
        var to = normalizeTo(globalFailureTo)
        var accountId = normalizeAccountId(globalFailureAccountId)

        // Overlay job-level failure destination
        val jobFailure = job.delivery?.failureDestination
        if (jobFailure != null) {
            val jobMode = normalizeMode(jobFailure.mode)
            val prevMode = mode

            if (jobMode != null) mode = jobMode
            if (jobFailure.channel != null) channel = normalizeChannel(jobFailure.channel)
            if (jobFailure.to != null) to = normalizeTo(jobFailure.to)
            if (jobFailure.accountId != null) accountId = normalizeAccountId(jobFailure.accountId)

            // When mode changes between global and job level, clear inherited `to`
            // (URL semantics differ between announce and webhook)
            if (prevMode != null && jobMode != null && prevMode != jobMode && jobFailure.to == null) {
                to = null
            }
        }

        // No fields set at all
        if (mode == null && channel == null && to == null) return null

        // Default mode
        if (mode == null) mode = "announce"

        // Webhook mode requires a URL
        if (mode == "webhook" && to == null) return null

        // Announce mode defaults channel to "last"
        if (mode == "announce" && channel == null) channel = "last"

        // Check if failure destination is same as primary delivery
        val delivery = job.delivery
        if (delivery != null && delivery.mode != DeliveryMode.NONE) {
            val isSame = when (mode) {
                "webhook" -> delivery.mode == DeliveryMode.WEBHOOK && normalizeTo(delivery.to) == to
                "announce" -> delivery.mode == DeliveryMode.ANNOUNCE &&
                    (normalizeChannel(delivery.channel) ?: "last") == (channel ?: "last") &&
                    normalizeTo(delivery.to) == to &&
                    normalizeAccountId(delivery.accountId) == accountId
                else -> false
            }
            if (isSame) return null
        }

        return CronFailureDeliveryPlan(
            mode = mode,
            channel = channel,
            to = to,
            accountId = accountId
        )
    }

    fun formatResultMessage(jobId: String, jobDescription: String?, result: String): String {
        val desc = jobDescription ?: jobId
        return "[Cron: $desc]\n$result"
    }

    fun formatFailureMessage(
        jobId: String,
        jobDescription: String?,
        error: String,
        consecutiveErrors: Int
    ): String {
        val desc = jobDescription ?: jobId
        return "[Cron Failure: $desc]\nError: $error\nConsecutive failures: $consecutiveErrors"
    }
}
