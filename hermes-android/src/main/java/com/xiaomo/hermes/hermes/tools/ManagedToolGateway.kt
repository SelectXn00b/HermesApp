package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Generic managed-tool gateway helpers for Nous-hosted vendor passthroughs.
 * Ported from managed_tool_gateway.py
 */
object ManagedToolGateway {

    private const val TAG = "ManagedToolGateway"
    private const val DEFAULT_TOOL_GATEWAY_DOMAIN = "nousresearch.com"
    private const val DEFAULT_TOOL_GATEWAY_SCHEME = "https"
    private const val TOKEN_REFRESH_SKEW_SECONDS = 120L

    data class ManagedToolGatewayConfig(
        val vendor: String,
        val gatewayOrigin: String,
        val nousUserToken: String,
        val managedMode: Boolean)

    private val gson = Gson()

    /**
     * Read a Nous Subscriber OAuth access token from auth store or env override.
     */
    fun readNousAccessToken(authFile: File? = null): String? {
        val explicit = System.getenv("TOOL_GATEWAY_USER_TOKEN")
        if (!explicit.isNullOrBlank()) return explicit.trim()

        // Try auth file
        val file = authFile ?: getDefaultAuthFile()
        if (file?.exists() == true) {
            try {
                val json = gson.fromJson(file.readText(Charsets.UTF_8), JsonObject::class.java)
                val providers = json.getAsJsonObject("providers")
                val nous = providers?.getAsJsonObject("nous")
                val token = nous?.get("access_token")?.asString
                if (!token.isNullOrBlank()) {
                    val expiresAt = nous.get("expires_at")?.asString
                    if (!isExpiring(expiresAt, TOKEN_REFRESH_SKEW_SECONDS)) {
                        return token.trim()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to read auth file: ${e.message}")
            }
        }

        return null
    }

    /**
     * Return configured shared gateway URL scheme.
     */
    fun getToolGatewayScheme(): String {
        val scheme = System.getenv("TOOL_GATEWAY_SCHEME")?.trim()?.lowercase() ?: ""
        if (scheme in listOf("http", "https")) return scheme
        return DEFAULT_TOOL_GATEWAY_SCHEME
    }

    /**
     * Build the gateway origin for a specific vendor.
     */
    fun buildVendorGatewayUrl(vendor: String): String {
        val vendorKey = "${vendor.uppercase().replace('-', '_')}_GATEWAY_URL"
        val explicit = System.getenv(vendorKey)?.trim()?.trimEnd('/')
        if (!explicit.isNullOrEmpty()) return explicit

        val scheme = getToolGatewayScheme()
        val domain = System.getenv("TOOL_GATEWAY_DOMAIN")?.trim()?.trim('/')
        if (!domain.isNullOrEmpty()) return "$scheme://${vendor}-gateway.$domain"

        return "$scheme://${vendor}-gateway.$DEFAULT_TOOL_GATEWAY_DOMAIN"
    }

    /**
     * Resolve shared managed-tool gateway config for a vendor.
     */
    fun resolveManagedToolGateway(
        vendor: String,
        gatewayBuilder: ((String) -> String)? = null,
        tokenReader: (() -> String?)? = null): ManagedToolGatewayConfig? {
        if (!ToolBackendHelpers.managedNousToolsEnabled()) return null

        val gatewayOrigin = (gatewayBuilder ?: { buildVendorGatewayUrl(it) })(vendor)
        val nousUserToken = (tokenReader ?: { readNousAccessToken() })() ?: return null

        if (gatewayOrigin.isEmpty() || nousUserToken.isEmpty()) return null

        return ManagedToolGatewayConfig(
            vendor = vendor,
            gatewayOrigin = gatewayOrigin,
            nousUserToken = nousUserToken,
            managedMode = true)
    }

    /**
     * Return True when gateway URL and Nous access token are available.
     */
    fun isManagedToolGatewayReady(
        vendor: String,
        gatewayBuilder: ((String) -> String)? = null,
        tokenReader: (() -> String?)? = null): Boolean = resolveManagedToolGateway(vendor, gatewayBuilder, tokenReader) != null

    private fun getDefaultAuthFile(): File? {
        val home = System.getProperty("user.home") ?: return null
        return File(home, ".hermes/auth.json").takeIf { it.exists() }
    }

    private fun isExpiring(expiresAt: String?, skewSeconds: Long): Boolean {
        if (expiresAt.isNullOrBlank()) return true
        return try {
            val normalized = if (expiresAt.endsWith("Z")) {
                expiresAt.replace("Z", "+00:00")
            } else expiresAt
            val parsed = ZonedDateTime.parse(normalized, DateTimeFormatter.ISO_ZONED_DATE_TIME)
            val remaining = parsed.toEpochSecond() - Instant.now().epochSecond
            remaining <= skewSeconds
        } catch (e: Exception) {
            true
        }
    }


}
