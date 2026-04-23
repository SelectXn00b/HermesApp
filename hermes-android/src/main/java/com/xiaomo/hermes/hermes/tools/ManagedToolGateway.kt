/**
 * Generic managed-tool gateway helpers for Nous-hosted vendor passthroughs.
 *
 * Ported from tools/managed_tool_gateway.py
 */
package com.xiaomo.hermes.hermes.tools

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val _TAG = "managed_tool_gateway"
private const val _DEFAULT_TOOL_GATEWAY_DOMAIN: String = "nousresearch.com"
private const val _DEFAULT_TOOL_GATEWAY_SCHEME: String = "https"
private const val _NOUS_ACCESS_TOKEN_REFRESH_SKEW_SECONDS: Long = 120L

data class ManagedToolGatewayConfig(
    val vendor: String,
    val gatewayOrigin: String,
    val nousUserToken: String,
    val managedMode: Boolean,
)

fun authJsonPath(): File {
    val home = System.getenv("HERMES_HOME") ?: System.getProperty("user.home") ?: "."
    return File(home, ".hermes/auth.json")
}

private fun _readNousProviderState(): Map<String, Any?>? {
    return try {
        val path = authJsonPath()
        if (!path.isFile) return null
        val data = JSONObject(path.readText(Charsets.UTF_8))
        val providers = data.optJSONObject("providers") ?: return null
        val nous = providers.optJSONObject("nous") ?: return null
        val map = mutableMapOf<String, Any?>()
        for (key in nous.keys()) map[key] = nous.get(key)
        map
    } catch (_: Exception) {
        null
    }
}

private fun _parseTimestamp(value: Any?): ZonedDateTime? {
    if (value !is String || value.isBlank()) return null
    val normalized = if (value.trim().endsWith("Z")) value.trim().dropLast(1) + "+00:00" else value.trim()
    return try {
        ZonedDateTime.parse(normalized, DateTimeFormatter.ISO_ZONED_DATE_TIME)
    } catch (_: Exception) {
        null
    }
}

private fun _accessTokenIsExpiring(expiresAt: Any?, skewSeconds: Int): Boolean {
    val expires = _parseTimestamp(expiresAt) ?: return true
    val remaining = expires.toEpochSecond() - Instant.now().epochSecond
    return remaining <= maxOf(0, skewSeconds)
}

fun readNousAccessToken(): String? {
    val explicit = System.getenv("TOOL_GATEWAY_USER_TOKEN")
    if (!explicit.isNullOrBlank()) return explicit.trim()

    val nousProvider = try {
        _readNousProviderState() ?: emptyMap()
    } catch (exc: Exception) {
        Log.d(_TAG, "Nous access token refresh failed: %s".format(exc.message))
        emptyMap()
    }
    val accessToken = nousProvider["access_token"] as? String
    val cachedToken = accessToken?.trim()?.ifEmpty { null }

    if (cachedToken != null && !_accessTokenIsExpiring(
            nousProvider["expires_at"],
            _NOUS_ACCESS_TOKEN_REFRESH_SKEW_SECONDS.toInt(),
        )) {
        return cachedToken
    }
    return cachedToken
}

fun getToolGatewayScheme(): String {
    val scheme = (System.getenv("TOOL_GATEWAY_SCHEME") ?: "").trim().lowercase()
    if (scheme.isEmpty()) return _DEFAULT_TOOL_GATEWAY_SCHEME
    if (scheme in setOf("http", "https")) return scheme
    throw IllegalArgumentException("TOOL_GATEWAY_SCHEME must be 'http' or 'https'")
}

fun buildVendorGatewayUrl(vendor: String): String {
    val vendorKey = "${vendor.uppercase().replace('-', '_')}_GATEWAY_URL"
    val explicitVendorUrl = (System.getenv(vendorKey) ?: "").trim().trimEnd('/')
    if (explicitVendorUrl.isNotEmpty()) return explicitVendorUrl

    val sharedScheme = getToolGatewayScheme()
    val sharedDomain = (System.getenv("TOOL_GATEWAY_DOMAIN") ?: "").trim().trim('/')
    if (sharedDomain.isNotEmpty()) return "$sharedScheme://${vendor}-gateway.$sharedDomain"
    return "$sharedScheme://${vendor}-gateway.$_DEFAULT_TOOL_GATEWAY_DOMAIN"
}

fun resolveManagedToolGateway(
    vendor: String,
    gatewayBuilder: ((String) -> String)? = null,
    tokenReader: (() -> String?)? = null,
): ManagedToolGatewayConfig? {
    if (!managedNousToolsEnabled()) return null

    val gatewayOrigin = (gatewayBuilder ?: ::buildVendorGatewayUrl)(vendor)
    val nousUserToken = (tokenReader ?: ::readNousAccessToken)()
    if (gatewayOrigin.isEmpty() || nousUserToken.isNullOrEmpty()) return null

    return ManagedToolGatewayConfig(
        vendor = vendor,
        gatewayOrigin = gatewayOrigin,
        nousUserToken = nousUserToken,
        managedMode = true,
    )
}

fun isManagedToolGatewayReady(
    vendor: String,
    gatewayBuilder: ((String) -> String)? = null,
    tokenReader: (() -> String?)? = null,
): Boolean = resolveManagedToolGateway(vendor, gatewayBuilder, tokenReader) != null
