package com.xiaomo.hermes.hermes.gateway

/**
 * Gateway status reporting.
 *
 * Provides a snapshot of the running gateway: connected platforms, active
 * sessions, uptime, and per-platform send counters.
 *
 * Ported from gateway/status.py
 */

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe counters for a single platform.
 */
class PlatformCounters {
    val messagesReceived: AtomicLong = AtomicLong(0)
    val messagesSent: AtomicLong = AtomicLong(0)
    val sendErrors: AtomicLong = AtomicLong(0)

    fun recordReceived() {
        messagesReceived.incrementAndGet()
    }

    fun recordSent() {
        messagesSent.incrementAndGet()
    }

    fun recordError() {
        sendErrors.incrementAndGet()
    }
}

/**
 * Gateway-wide status snapshot.
 *
 * Updated by platform adapters and the delivery router.  Read by the
 * ``/status`` command and the ``GET /health`` endpoint.
 */
class GatewayStatus {
    val startedAt: Instant = Instant.now()

    val platformCounters: ConcurrentHashMap<String, PlatformCounters> = ConcurrentHashMap()

    val connectedPlatforms: ConcurrentHashMap.KeySetView<String, Boolean> =
        ConcurrentHashMap.newKeySet()

    @Volatile var activeSessions: Int = 0

    @Volatile var processingSessions: Int = 0

    val uptimeSeconds: Long
        get() = java.time.Duration.between(startedAt, Instant.now()).seconds

    fun countersFor(platform: String): PlatformCounters =
        platformCounters.getOrPut(platform) { PlatformCounters() }

    fun markConnected(platform: String) {
        connectedPlatforms.add(platform)
    }

    fun markDisconnected(platform: String) {
        connectedPlatforms.remove(platform)
    }

    companion object {
        /** Format seconds as "Xd Yh Zm". */
        fun formatDuration(seconds: Long): String {
            val days = seconds / 86400
            val hours = (seconds % 86400) / 3600
            val minutes = (seconds % 3600) / 60
            return buildString {
                if (days > 0) append("${days}d ")
                if (hours > 0) append("${hours}h ")
                append("${minutes}m")
            }.trim()
        }
    }
}
