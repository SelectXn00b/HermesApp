/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server-shared.ts
 */
package com.xiaomo.androidforclaw.gateway.protocol

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

/**
 * Gateway error exception
 */
class GatewayError(
    val code: String,
    message: String,
    val details: Any? = null
) : Exception(message)
