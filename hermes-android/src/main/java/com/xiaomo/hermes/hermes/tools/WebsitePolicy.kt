package com.xiaomo.hermes.hermes.tools

import android.util.Log
import java.io.File
import java.net.URL
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Website access policy helpers for URL-capable tools.
 * Ported from website_policy.py
 */
object WebsitePolicy {

    private const val TAG = "WebsitePolicy"
    private const val CACHE_TTL_MS = 30_000L

    data class PolicyRule(
        val pattern: String,
        val source: String = "config")

    data class WebsiteBlocklist(
        val enabled: Boolean = false,
        val rules: List<PolicyRule> = emptyList())

    data class BlockedResult(
        val url: String,
        val host: String,
        val rule: String,
        val source: String,
        val message: String)

    private val lock = ReentrantReadWriteLock()
    private var cachedPolicy: WebsiteBlocklist? = null
    private var cachedPolicyTime: Long = 0L

    private fun normalizeHost(host: String): String =
        host.trim().lowercase().trimEnd('.')

    private fun normalizeRule(rule: String): String? {
        val value = rule.trim().lowercase()
        if (value.isEmpty() || value.startsWith("#")) return null
        val extracted = if ("://" in value) {
            try { URL(value).host ?: value } catch (e: Exception) { value }
        } else value
        val host = extracted.split("/").firstOrNull()?.trim()?.trimEnd('.') ?: return null
        return if (host.startsWith("www.")) host.substring(4) else host
    }

    private fun extractHost(url: String): String {
        return try {
            val parsed = URL(url)
            normalizeHost(parsed.host ?: "")
        } catch (e: Exception) {
            // Try as schemeless URL
            try {
                val schemeless = URL("http://$url")
                normalizeHost(schemeless.host ?: "")
            } catch (e2: Exception) {
                ""
            }
        }
    }

    private fun matchHost(host: String, pattern: String): Boolean {
        if (host.isEmpty() || pattern.isEmpty()) return false
        if (pattern.startsWith("*.")) {
            return host.endsWith(pattern.substring(1)) || host == pattern.substring(2)
        }
        return host == pattern || host.endsWith(".$pattern")
    }

    /**
     * Load website blocklist from config file.
     * In Android context, this reads from a YAML or JSON config file.
     */
    fun loadBlocklist(configFile: File? = null): WebsiteBlocklist {
        lock.read {
            cachedPolicy?.let { policy ->
                if (System.currentTimeMillis() - cachedPolicyTime < CACHE_TTL_MS) {
                    return policy
                }
            }
        }

        val policy = try {
            loadBlocklistFromFile(configFile)
        } catch (e: Exception) {
            Log.w(TAG, "Error loading website policy (failing open): ${e.message}")
            WebsiteBlocklist(enabled = false)
        }

        lock.write {
            cachedPolicy = policy
            cachedPolicyTime = System.currentTimeMillis()
        }

        return policy
    }

    private fun loadBlocklistFromFile(configFile: File?): WebsiteBlocklist {
        val file = configFile ?: return WebsiteBlocklist(enabled = false)
        if (!file.exists()) return WebsiteBlocklist(enabled = false)

        // Parse YAML config (simplified — expects standard structure)
        val lines = file.readLines(Charsets.UTF_8)
        val rules = mutableListOf<PolicyRule>()
        var enabled = false
        var inBlocklist = false
        var inDomains = false

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "website_blocklist:" -> { inBlocklist = true; inDomains = false }
                trimmed == "domains:" && inBlocklist -> inDomains = true
                trimmed.startsWith("enabled:") && inBlocklist -> {
                    enabled = trimmed.substringAfter(":").trim().lowercase() == "true"
                }
                inDomains && trimmed.startsWith("- ") -> {
                    val rule = normalizeRule(trimmed.substring(2))
                    if (rule != null) {
                        rules.add(PolicyRule(rule, configFile.absolutePath))
                    }
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("-") && !trimmed.startsWith("#") -> {
                    inDomains = false
                    if (!trimmed.startsWith("domains:") && !trimmed.startsWith("enabled:")) {
                        inBlocklist = false
                    }
                }
            }
        }

        return WebsiteBlocklist(enabled = enabled, rules = rules)
    }

    /**
     * Check whether a URL is allowed by the website blocklist.
     * Returns null if allowed, or BlockedResult if blocked.
     */
    fun checkWebsiteAccess(url: String, configFile: File? = null): BlockedResult? {
        // Fast path: if cached policy is disabled, skip work
        lock.read {
            cachedPolicy?.let { policy ->
                if (!policy.enabled) return null
            }
        }

        val host = extractHost(url)
        if (host.isEmpty()) return null

        val policy = loadBlocklist(configFile)
        if (!policy.enabled) return null

        for (rule in policy.rules) {
            if (matchHost(host, rule.pattern)) {
                Log.i(TAG, "Blocked URL $url — matched rule '${rule.pattern}' from ${rule.source}")
                return BlockedResult(
                    url = url,
                    host = host,
                    rule = rule.pattern,
                    source = rule.source,
                    message = "Blocked by website policy: '$host' matched rule '${rule.pattern}' from ${rule.source}")
            }
        }
        return null
    }

    /**
     * Set a blocklist directly (for programmatic configuration).
     */
    fun setBlocklist(blocklist: WebsiteBlocklist) {
        lock.write {
            cachedPolicy = blocklist
            cachedPolicyTime = System.currentTimeMillis()
        }
    }

    fun invalidateCache() {
        lock.write {
            cachedPolicy = null
        }
    }


}

/**
 * Raised when a website policy file is malformed.
 * Ported from WebsitePolicyError in website_policy.py.
 */
class WebsitePolicyError(message: String) : Exception(message)
