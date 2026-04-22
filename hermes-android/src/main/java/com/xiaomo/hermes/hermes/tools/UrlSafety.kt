/**
 * URL safety checks — blocks requests to private/internal network addresses.
 *
 * Prevents SSRF (Server-Side Request Forgery) where a malicious prompt or
 * skill could trick the agent into fetching internal resources like cloud
 * metadata endpoints, localhost services, or private network hosts.
 *
 * Ported from tools/url_safety.py
 */
package com.xiaomo.hermes.hermes.tools

import java.net.InetAddress
import java.net.URL

private val _BLOCKED_HOSTNAMES: Set<String> = setOf(
    "metadata.google.internal",
    "metadata.goog",
)

private val _TRUSTED_PRIVATE_IP_HOSTS: Set<String> = setOf(
    "multimedia.nt.qq.com.cn",
)

private val _CGNAT_PREFIXES: List<String> = (64..127).map { "100.$it." }

private fun _isBlockedIp(ip: InetAddress): Boolean {
    if (ip.isAnyLocalAddress || ip.isLoopbackAddress || ip.isLinkLocalAddress ||
        ip.isSiteLocalAddress || ip.isMulticastAddress) {
        return true
    }
    val addr = ip.hostAddress ?: return false
    return _CGNAT_PREFIXES.any { addr.startsWith(it) }
}

private fun _allowsPrivateIpResolution(hostname: String, scheme: String): Boolean =
    scheme == "https" && hostname in _TRUSTED_PRIVATE_IP_HOSTS

/**
 * Return true if the URL target is not a private/internal address.
 * Fails closed: DNS errors block the request.
 */
fun isSafeUrl(url: String): Boolean {
    return try {
        val parsed = URL(url)
        val hostname = parsed.host?.lowercase()?.trim()?.trimEnd('.') ?: ""
        val scheme = parsed.protocol?.lowercase()?.trim() ?: ""
        if (hostname.isEmpty()) return false

        if (hostname in _BLOCKED_HOSTNAMES) return false
        if (hostname.endsWith(".local")) return false

        val allowPrivateIp = _allowsPrivateIpResolution(hostname, scheme)

        try {
            val addresses = InetAddress.getAllByName(hostname)
            for (addr in addresses) {
                if (!allowPrivateIp && _isBlockedIp(addr)) return false
            }
        } catch (e: Exception) {
            return false
        }

        true
    } catch (e: Exception) {
        false
    }
}
