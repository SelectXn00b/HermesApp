package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/logging.ts
 *
 * Channel-specific logging helpers for inbound drops, typing failures,
 * and ack cleanup failures.
 */

typealias LogFn = (message: String) -> Unit

/**
 * Log an inbound message drop.
 * Aligned with TS logInboundDrop().
 */
fun logInboundDrop(
    log: LogFn,
    channel: String,
    reason: String,
    target: String? = null,
) {
    val targetSuffix = if (target != null) " target=$target" else ""
    log("$channel: drop $reason$targetSuffix")
}

/**
 * Log a typing indicator failure.
 * Aligned with TS logTypingFailure().
 */
fun logTypingFailure(
    log: LogFn,
    channel: String,
    target: String? = null,
    action: String? = null,
    error: Any?,
) {
    val targetSuffix = if (target != null) " target=$target" else ""
    val actionSuffix = if (action != null) " action=$action" else ""
    log("$channel typing$actionSuffix failed$targetSuffix: $error")
}

/**
 * Log an ack cleanup failure.
 * Aligned with TS logAckFailure().
 */
fun logAckFailure(
    log: LogFn,
    channel: String,
    target: String? = null,
    error: Any?,
) {
    val targetSuffix = if (target != null) " target=$target" else ""
    log("$channel ack cleanup failed$targetSuffix: $error")
}
