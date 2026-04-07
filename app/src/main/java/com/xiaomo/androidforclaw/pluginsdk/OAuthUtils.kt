package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/oauth-utils.ts
 *
 * OAuth utility helpers: form encoding and PKCE challenge generation.
 * Android adaptation: uses java.security for crypto, android.util.Base64 for encoding.
 */

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

// ---------- Form Encoding ----------

/**
 * Encode a flat map as application/x-www-form-urlencoded form data.
 * Aligned with TS toFormUrlEncoded.
 */
fun toFormUrlEncoded(data: Map<String, String>): String =
    data.entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }

// ---------- PKCE ----------

/**
 * PKCE verifier/challenge pair.
 * Aligned with TS generatePkceVerifierChallenge return type.
 */
data class PkceVerifierChallenge(
    val verifier: String,
    val challenge: String,
)

/**
 * Generate a PKCE verifier/challenge pair suitable for OAuth authorization flows.
 * Aligned with TS generatePkceVerifierChallenge.
 */
fun generatePkceVerifierChallenge(): PkceVerifierChallenge {
    val random = SecureRandom()
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    val verifier = base64UrlEncode(bytes)
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    val challenge = base64UrlEncode(digest)
    return PkceVerifierChallenge(verifier = verifier, challenge = challenge)
}

/**
 * Base64 URL-safe encoding without padding.
 */
private fun base64UrlEncode(data: ByteArray): String {
    val base64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    return base64
}
