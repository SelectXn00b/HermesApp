package com.xiaomo.androidforclaw.pairing

import android.util.Base64
import org.json.JSONObject
import java.net.URL

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/setup-code.ts
 *
 * Pairing setup code resolution and encoding.
 * Aligned 1:1 with TS setup-code.ts.
 * Android adaptation: URL validation uses java.net.URL, base64 uses android.util.Base64.
 */
object SetupCode {

    // ---------------------------------------------------------------------------
    // encodePairingSetupCode — aligned with TS encodePairingSetupCode
    // ---------------------------------------------------------------------------

    /**
     * Encode a pairing setup payload as a base64url string.
     */
    fun encodePairingSetupCode(payload: PairingSetupPayload): String {
        val json = JSONObject().apply {
            put("url", payload.url)
            put("bootstrapToken", payload.bootstrapToken)
        }.toString()
        val base64 = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return base64
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }

    /**
     * Decode a base64url pairing setup code back to a payload.
     */
    fun decodePairingSetupCode(code: String): PairingSetupPayload? {
        return try {
            val padded = code.replace('-', '+').replace('_', '/')
            val json = String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
            val obj = JSONObject(json)
            PairingSetupPayload(
                url = obj.getString("url"),
                bootstrapToken = obj.getString("bootstrapToken"),
            )
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------
    // URL helpers — aligned with TS setup-code.ts
    // ---------------------------------------------------------------------------

    /**
     * Normalize a URL, converting http->ws and https->wss.
     * Aligned with TS normalizeUrl.
     */
    fun normalizeUrl(raw: String, schemeFallback: String = "ws"): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val parsed = URL(trimmed)
            val scheme = parsed.protocol ?: return null
            val resolvedScheme = when (scheme) {
                "http" -> "ws"
                "https" -> "wss"
                "ws" -> "ws"
                "wss" -> "wss"
                else -> return null
            }
            val host = parsed.host
            if (host.isNullOrEmpty()) return null
            val port = if (parsed.port > 0) ":${parsed.port}" else ""
            "$resolvedScheme://$host$port"
        } catch (_: Exception) {
            // Fall through to host:port parsing
            val withoutPath = trimmed.split("/").firstOrNull() ?: return null
            if (withoutPath.isEmpty()) return null
            "$schemeFallback://$withoutPath"
        }
    }

    /**
     * Check if a URL uses the secure WebSocket scheme (wss://).
     */
    fun isSecureWebSocketUrl(url: String): Boolean {
        return url.trim().lowercase().startsWith("wss://")
    }

    /**
     * Check if a host is a loopback address.
     */
    fun isLoopbackHost(host: String): Boolean {
        val h = host.trim().lowercase()
        return h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "[::1]"
    }

    /**
     * Check if a host is the Android emulator host.
     */
    fun isEmulatorHost(host: String): Boolean {
        return host.trim() == "10.0.2.2"
    }

    /**
     * Validate a mobile pairing URL: must be wss:// or target a local/LAN host.
     * Aligned with TS validateMobilePairingUrl.
     */
    fun validateMobilePairingUrl(url: String): String? {
        if (isSecureWebSocketUrl(url)) return null

        return try {
            val parsed = URL(url.replace("ws://", "http://").replace("wss://", "https://"))
            val host = parsed.host ?: return "Resolved mobile pairing URL is invalid."

            if (isLoopbackHost(host) || isEmulatorHost(host)) null
            else "Mobile pairing requires a secure gateway URL (wss://) or a local/LAN host."
        } catch (_: Exception) {
            "Resolved mobile pairing URL is invalid."
        }
    }

    // ---------------------------------------------------------------------------
    // Auth label resolution — aligned with TS resolvePairingSetupAuthLabel
    // ---------------------------------------------------------------------------

    /**
     * Resolve the auth label from config values.
     * Returns "token", "password", or null if not configured.
     */
    fun resolveAuthLabel(
        authMode: String?,
        hasToken: Boolean,
        hasPassword: Boolean,
    ): String? {
        return when (authMode?.trim()?.lowercase()) {
            "password" -> if (hasPassword) "password" else null
            "token" -> if (hasToken) "token" else null
            else -> when {
                hasToken -> "token"
                hasPassword -> "password"
                else -> null
            }
        }
    }
}
