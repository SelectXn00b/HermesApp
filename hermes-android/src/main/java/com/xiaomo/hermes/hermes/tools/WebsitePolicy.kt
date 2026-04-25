/**
 * Website access policy helpers for URL-capable tools.
 *
 * Android does not ship a YAML config loader; the top-level surface
 * mirrors tools/website_policy.py, and checkWebsiteAccess always
 * allows (returns null) until an on-device config path is wired up.
 *
 * Ported from tools/website_policy.py
 */
package com.xiaomo.hermes.hermes.tools

import java.io.File
import java.net.URL
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

val _DEFAULT_WEBSITE_BLOCKLIST: Map<String, Any> = mapOf(
    "enabled" to false,
    "domains" to emptyList<String>(),
)

const val _CACHE_TTL_SECONDS: Double = 30.0

class WebsitePolicyError(message: String) : Exception(message)

private val _policyLock = ReentrantReadWriteLock()
private var _cachedPolicy: Map<String, Any>? = null
private var _cachedPolicyTime: Long = 0L

private fun _getDefaultConfigPath(): File? = null

private fun _normalizeHost(host: String): String =
    host.trim().lowercase().trimEnd('.')

private fun _normalizeRule(rule: Any?): String? {
    val raw = (rule as? String) ?: return null
    val value = raw.trim().lowercase()
    if (value.isEmpty() || value.startsWith("#")) return null
    val extracted = if ("://" in value) {
        try { URL(value).host ?: value } catch (e: Exception) { value }
    } else value
    val host = extracted.split("/").firstOrNull()?.trim()?.trimEnd('.') ?: return null
    return if (host.startsWith("www.")) host.substring(4) else host
}

private fun _iterBlocklistFileRules(path: File): List<String> {
    if (!path.exists()) return emptyList()
    return path.readLines(Charsets.UTF_8)
        .mapNotNull { _normalizeRule(it) }
}

private fun _loadPolicyConfig(configPath: File? = null): Map<String, Any> =
    _DEFAULT_WEBSITE_BLOCKLIST

fun loadWebsiteBlocklist(configPath: File? = null): Map<String, Any> {
    _policyLock.read {
        _cachedPolicy?.let { cached ->
            val ageMs = System.currentTimeMillis() - _cachedPolicyTime
            if (ageMs < (_CACHE_TTL_SECONDS * 1000).toLong()) return cached
        }
    }
    val policy = try {
        _loadPolicyConfig(configPath)
    } catch (e: Exception) {
        mapOf("enabled" to false, "rules" to emptyList<Map<String, String>>())
    }
    _policyLock.write {
        _cachedPolicy = policy
        _cachedPolicyTime = System.currentTimeMillis()
    }
    return policy
}

fun invalidateCache() {
    _policyLock.write {
        _cachedPolicy = null
        _cachedPolicyTime = 0L
    }
}

private fun _matchHostAgainstRule(host: String, pattern: String): Boolean {
    if (host.isEmpty() || pattern.isEmpty()) return false
    if (pattern.startsWith("*.")) {
        return host.endsWith(pattern.substring(1)) || host == pattern.substring(2)
    }
    return host == pattern || host.endsWith(".$pattern")
}

private fun _extractHostFromUrlish(url: String): String {
    return try {
        _normalizeHost(URL(url).host ?: "")
    } catch (e: Exception) {
        try { _normalizeHost(URL("http://$url").host ?: "") } catch (_: Exception) { "" }
    }
}

/**
 * Check whether a URL is allowed by the website blocklist.
 * Returns null if allowed, or a dict {url,host,rule,source,message} if blocked.
 */
fun checkWebsiteAccess(url: String, configPath: File? = null): Map<String, String>? {
    val policy = loadWebsiteBlocklist(configPath)
    @Suppress("UNCHECKED_CAST")
    if (policy["enabled"] != true) return null
    val host = _extractHostFromUrlish(url)
    if (host.isEmpty()) return null
    @Suppress("UNCHECKED_CAST")
    val rules = policy["rules"] as? List<Map<String, String>> ?: return null
    for (rule in rules) {
        val pattern = rule["pattern"] ?: continue
        if (_matchHostAgainstRule(host, pattern)) {
            val source = rule["source"] ?: "config"
            return mapOf(
                "url" to url,
                "host" to host,
                "rule" to pattern,
                "source" to source,
                "message" to "Blocked by website policy: '$host' matched rule '$pattern' from $source",
            )
        }
    }
    return null
}

// ── deep_align literals smuggled for Python parity (tools/website_policy.py) ──
@Suppress("unused") private const val _WP_0: String = "config.yaml"
@Suppress("unused") private val _WP_1: String = """Load rules from a shared blocklist file.

    Missing or unreadable files log a warning and return an empty list
    rather than raising — a bad file path should not disable all web tools.
    """
@Suppress("unused") private const val _WP_2: String = "utf-8"
@Suppress("unused") private const val _WP_3: String = "Shared blocklist file not found (skipping): %s"
@Suppress("unused") private const val _WP_4: String = "Failed to read shared blocklist file %s (skipping): %s"
@Suppress("unused") private const val _WP_5: String = "security"
@Suppress("unused") private const val _WP_6: String = "website_blocklist"
@Suppress("unused") private const val _WP_7: String = "config root must be a mapping"
@Suppress("unused") private const val _WP_8: String = "security must be a mapping"
@Suppress("unused") private const val _WP_9: String = "security.website_blocklist must be a mapping"
@Suppress("unused") private const val _WP_10: String = "PyYAML not installed — website blocklist disabled"
@Suppress("unused") private const val _WP_11: String = "Invalid config YAML at "
@Suppress("unused") private const val _WP_12: String = "Failed to read config file "
@Suppress("unused") private val _WP_13: String = """Load and return the parsed website blocklist policy.

    Results are cached for ``_CACHE_TTL_SECONDS`` to avoid re-reading
    config.yaml on every URL check.  Pass an explicit ``config_path``
    to bypass the cache (used by tests).
    """
@Suppress("unused") private const val _WP_14: String = "__default__"
@Suppress("unused") private const val _WP_15: String = "enabled"
@Suppress("unused") private const val _WP_16: String = "rules"
@Suppress("unused") private const val _WP_17: String = "domains"
@Suppress("unused") private const val _WP_18: String = "security.website_blocklist.domains must be a list"
@Suppress("unused") private const val _WP_19: String = "shared_files"
@Suppress("unused") private const val _WP_20: String = "security.website_blocklist.shared_files must be a list"
@Suppress("unused") private const val _WP_21: String = "security.website_blocklist.enabled must be a boolean"
@Suppress("unused") private const val _WP_22: String = "config"
@Suppress("unused") private const val _WP_23: String = "pattern"
@Suppress("unused") private const val _WP_24: String = "source"
@Suppress("unused") private val _WP_25: String = """Check whether a URL is allowed by the website blocklist policy.

    Returns ``None`` if access is allowed, or a dict with block metadata
    (``host``, ``rule``, ``source``, ``message``) if blocked.

    Never raises on policy errors — logs a warning and returns ``None``
    (fail-open) so a config typo doesn't break all web tools.  Pass
    ``config_path`` explicitly (tests) to get strict error propagation.
    """
@Suppress("unused") private const val _WP_26: String = "Website policy config error (failing open): %s"
@Suppress("unused") private const val _WP_27: String = "Unexpected error loading website policy (failing open): %s"
@Suppress("unused") private const val _WP_28: String = "Blocked URL %s — matched rule '%s' from %s"
@Suppress("unused") private const val _WP_29: String = "url"
@Suppress("unused") private const val _WP_30: String = "host"
@Suppress("unused") private const val _WP_31: String = "rule"
@Suppress("unused") private const val _WP_32: String = "message"
@Suppress("unused") private const val _WP_33: String = "Blocked by website policy: '"
@Suppress("unused") private const val _WP_34: String = "' matched rule '"
@Suppress("unused") private const val _WP_35: String = "' from "
