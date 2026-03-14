/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: gateway server and RPC methods.
 */
package com.xiaomo.androidforclaw.gateway.protocol

/**
 * Gateway error exception
 */
class GatewayError(
    val code: String,
    message: String,
    val details: Any? = null
) : Exception(message)
