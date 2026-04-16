package com.xiaomo.androidforclaw.hermes.tools

import java.net.InetAddress
import java.net.URL

/**
 * URL safety checks — blocks requests to private/internal network addresses.
 * Prevents SSRF attacks.
 * Ported from url_safety.py
 */
object UrlSafety {

    private val BLOCKED_HOSTNAMES = setOf(
        "metadata.google.internal",
        "metadata.goog",
        "localhost",
        "127.0.0.1",
        "::1")

    /**
     * Check if an IP address should be blocked for SSRF protection.
     */
    private fun isBlockedIp(ip: InetAddress): Boolean {
        return ip.isAnyLocalAddress ||
               ip.isLoopbackAddress ||
               ip.isLinkLocalAddress ||
               ip.isSiteLocalAddress ||
               ip.isMulticastAddress ||
               ip.hostAddress?.let { addr ->
                   // CGNAT range 100.64.0.0/10
                   addr.startsWith("100.64.") || addr.startsWith("100.65.") ||
                   addr.startsWith("100.66.") || addr.startsWith("100.67.") ||
                   addr.startsWith("100.68.") || addr.startsWith("100.69.") ||
                   addr.startsWith("100.70.") || addr.startsWith("100.71.") ||
                   addr.startsWith("100.72.") || addr.startsWith("100.73.") ||
                   addr.startsWith("100.74.") || addr.startsWith("100.75.") ||
                   addr.startsWith("100.76.") || addr.startsWith("100.77.") ||
                   addr.startsWith("100.78.") || addr.startsWith("100.79.") ||
                   addr.startsWith("100.80.") || addr.startsWith("100.81.") ||
                   addr.startsWith("100.82.") || addr.startsWith("100.83.") ||
                   addr.startsWith("100.84.") || addr.startsWith("100.85.") ||
                   addr.startsWith("100.86.") || addr.startsWith("100.87.") ||
                   addr.startsWith("100.88.") || addr.startsWith("100.89.") ||
                   addr.startsWith("100.90.") || addr.startsWith("100.91.") ||
                   addr.startsWith("100.92.") || addr.startsWith("100.93.") ||
                   addr.startsWith("100.94.") || addr.startsWith("100.95.") ||
                   addr.startsWith("100.96.") || addr.startsWith("100.97.") ||
                   addr.startsWith("100.98.") || addr.startsWith("100.99.") ||
                   addr.startsWith("100.100.") || addr.startsWith("100.101.") ||
                   addr.startsWith("100.102.") || addr.startsWith("100.103.") ||
                   addr.startsWith("100.104.") || addr.startsWith("100.105.") ||
                   addr.startsWith("100.106.") || addr.startsWith("100.107.") ||
                   addr.startsWith("100.108.") || addr.startsWith("100.109.") ||
                   addr.startsWith("100.110.") || addr.startsWith("100.111.") ||
                   addr.startsWith("100.112.") || addr.startsWith("100.113.") ||
                   addr.startsWith("100.114.") || addr.startsWith("100.115.") ||
                   addr.startsWith("100.116.") || addr.startsWith("100.117.") ||
                   addr.startsWith("100.118.") || addr.startsWith("100.119.") ||
                   addr.startsWith("100.120.") || addr.startsWith("100.121.") ||
                   addr.startsWith("100.122.") || addr.startsWith("100.123.") ||
                   addr.startsWith("100.124.") || addr.startsWith("100.125.") ||
                   addr.startsWith("100.126.") || addr.startsWith("100.127.")
               } ?: false
    }

    /**
     * Return true if the URL target is not a private/internal address.
     * Fails closed: DNS errors block the request.
     */
    fun isSafeUrl(url: String): Boolean {
        try {
            val parsed = URL(url)
            val hostname = parsed.host?.lowercase()?.trim() ?: ""
            if (hostname.isEmpty()) return false

            // Block known internal hostnames
            if (hostname in BLOCKED_HOSTNAMES) return false

            // Block .local domains
            if (hostname.endsWith(".local")) return false

            // Resolve and check IP
            try {
                val addresses = InetAddress.getAllByName(hostname)
                for (addr in addresses) {
                    if (isBlockedIp(addr)) return false
                }
            } catch (e: Exception) {
                // DNS resolution failed — fail closed
                return false
            }

            return true
        } catch (e: Exception) {
            // Fail closed on unexpected errors
            return false
        }
    }

    /**
     * Validate a URL and return a Result.
     */
    fun validateUrl(url: String): Result<String> {
        if (!isSafeUrl(url)) {
            return Result.failure(SecurityException("URL '$url' is not safe (SSRF protection)"))
        }
        return Result.success(url)
    }



    private object Constants {
        private val _BLOCKED_HOSTNAMES: Set<String> = setOf(
            "0.0.0.0", "127.0.0.1", "localhost", "169.254.169.254",
            "metadata.google.internal", "[::]", "[::1]")
        private val _CGNAT_NETWORK = """^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}$""".toRegex()
    }
}
