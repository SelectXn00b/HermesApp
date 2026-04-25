package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic subset of TelegramNetwork.kt:
 *   _normalizeFallbackIps — drop private/loopback/link-local/malformed IPs
 *   parseFallbackIpEnv — CSV → normalized list
 *   _rewriteRequestForIp — map shape
 *   _isRetryableConnectError — TCP error classifier
 *   TelegramRateLimiter — pure per-endpoint state
 *   TelegramFallbackTransport.handleAsyncRequest — Android-side null stub
 *
 * Network-bound helpers (_resolveSystemDns, _queryDohProvider) require DNS
 * lookups and live sockets — not covered here.
 */
class TelegramNetworkTest {

    // ─── _normalizeFallbackIps ────────────────────────────────────────────

    @Test
    fun `normalizeFallbackIps keeps public IPv4 addresses`() {
        val out = _normalizeFallbackIps(listOf("8.8.8.8", "1.1.1.1", "149.154.167.41"))
        assertEquals(listOf("8.8.8.8", "1.1.1.1", "149.154.167.41"), out)
    }

    @Test
    fun `normalizeFallbackIps drops loopback`() {
        assertEquals(emptyList<String>(), _normalizeFallbackIps(listOf("127.0.0.1")))
    }

    @Test
    fun `normalizeFallbackIps drops link-local`() {
        assertEquals(emptyList<String>(), _normalizeFallbackIps(listOf("169.254.1.2")))
    }

    @Test
    fun `normalizeFallbackIps drops unspecified zero address`() {
        assertEquals(emptyList<String>(), _normalizeFallbackIps(listOf("0.0.0.0")))
    }

    @Test
    fun `normalizeFallbackIps drops RFC1918 private networks`() {
        val out = _normalizeFallbackIps(listOf(
            "10.0.0.1",
            "192.168.1.1",
            "172.16.0.1",
            "172.31.255.255",
        ))
        assertTrue("expected all private IPs dropped: $out", out.isEmpty())
    }

    @Test
    fun `normalizeFallbackIps keeps 172 addresses outside 16-31 range`() {
        // 172.15 and 172.32 are public.
        val out = _normalizeFallbackIps(listOf("172.15.1.1", "172.32.1.1"))
        assertEquals(listOf("172.15.1.1", "172.32.1.1"), out)
    }

    @Test
    fun `normalizeFallbackIps drops non-IPv4 strings`() {
        val out = _normalizeFallbackIps(listOf(
            "notanip",
            "1.2.3",
            "1.2.3.4.5",
            "::1",
        ))
        assertEquals(emptyList<String>(), out)
    }

    @Test
    fun `normalizeFallbackIps drops out-of-range octets`() {
        val out = _normalizeFallbackIps(listOf("256.0.0.1", "1.2.3.-1"))
        assertEquals(emptyList<String>(), out)
    }

    @Test
    fun `normalizeFallbackIps trims whitespace and skips empties`() {
        val out = _normalizeFallbackIps(listOf(" 1.2.3.4 ", "", "   "))
        assertEquals(listOf("1.2.3.4"), out)
    }

    // ─── parseFallbackIpEnv ───────────────────────────────────────────────

    @Test
    fun `parseFallbackIpEnv empty on null or blank`() {
        assertEquals(emptyList<String>(), parseFallbackIpEnv(null))
        assertEquals(emptyList<String>(), parseFallbackIpEnv(""))
    }

    @Test
    fun `parseFallbackIpEnv splits comma-separated csv`() {
        val out = parseFallbackIpEnv("8.8.8.8, 1.1.1.1,9.9.9.9")
        assertEquals(listOf("8.8.8.8", "1.1.1.1", "9.9.9.9"), out)
    }

    @Test
    fun `parseFallbackIpEnv drops private entries in csv`() {
        val out = parseFallbackIpEnv("8.8.8.8,10.0.0.1,1.1.1.1")
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), out)
    }

    // ─── _rewriteRequestForIp ─────────────────────────────────────────────

    @Test
    fun `rewriteRequestForIp builds descriptor map with Telegram host`() {
        val out = _rewriteRequestForIp(request = "original-req", ip = "149.154.167.41")
        assertEquals("149.154.167.41", out["ip"])
        assertEquals("api.telegram.org", out["host"])
        assertEquals("api.telegram.org", out["sni_hostname"])
        assertEquals("original-req", out["original"])
    }

    @Test
    fun `rewriteRequestForIp preserves null request`() {
        val out = _rewriteRequestForIp(request = null, ip = "1.2.3.4")
        assertNull(out["original"])
    }

    // ─── _isRetryableConnectError ─────────────────────────────────────────

    @Test
    fun `isRetryableConnectError null returns false`() {
        assertFalse(_isRetryableConnectError(null))
    }

    @Test
    fun `isRetryableConnectError true on ConnectException`() {
        assertTrue(_isRetryableConnectError(java.net.ConnectException("refused")))
    }

    @Test
    fun `isRetryableConnectError true on SocketTimeoutException`() {
        assertTrue(_isRetryableConnectError(java.net.SocketTimeoutException("timeout")))
    }

    @Test
    fun `isRetryableConnectError matches connect+timeout message`() {
        assertTrue(_isRetryableConnectError(RuntimeException("failed to connect: timeout")))
        assertTrue(_isRetryableConnectError(RuntimeException("connect refused by peer")))
        assertTrue(_isRetryableConnectError(RuntimeException("host unreachable during connect")))
    }

    @Test
    fun `isRetryableConnectError false on unrelated errors`() {
        assertFalse(_isRetryableConnectError(IllegalArgumentException("bad arg")))
        assertFalse(_isRetryableConnectError(RuntimeException("parse failed")))
    }

    // ─── TelegramRateLimiter ──────────────────────────────────────────────

    @Test
    fun `TelegramRateLimiter returns 0 when no rate limit recorded`() {
        val rl = TelegramRateLimiter()
        assertEquals(0L, rl.checkRateLimit("sendMessage"))
    }

    @Test
    fun `TelegramRateLimiter computes remaining delay after record`() {
        val rl = TelegramRateLimiter()
        rl.recordRateLimit("sendMessage", retryAfterSeconds = 30)
        val delay = rl.checkRateLimit("sendMessage")
        // Elapsed ~0, min delay = 30000 ms → returned delay ≈ 30000.
        assertTrue("expected 29s-30s remaining, got $delay ms", delay in 29_000L..30_000L)
    }

    @Test
    fun `TelegramRateLimiter returns 0 once delay has elapsed`() {
        val rl = TelegramRateLimiter()
        // retryAfterSeconds = 0 → min delay is 0 ms → immediately elapsed.
        rl.recordRateLimit("sendMessage", retryAfterSeconds = 0)
        assertEquals(0L, rl.checkRateLimit("sendMessage"))
    }

    @Test
    fun `TelegramRateLimiter clearRateLimit drops endpoint state`() {
        val rl = TelegramRateLimiter()
        rl.recordRateLimit("sendMessage", retryAfterSeconds = 30)
        rl.clearRateLimit("sendMessage")
        assertEquals(0L, rl.checkRateLimit("sendMessage"))
    }

    @Test
    fun `TelegramRateLimiter clearAll drops every endpoint`() {
        val rl = TelegramRateLimiter()
        rl.recordRateLimit("a", 10)
        rl.recordRateLimit("b", 20)
        rl.clearAll()
        assertEquals(0L, rl.checkRateLimit("a"))
        assertEquals(0L, rl.checkRateLimit("b"))
    }

    @Test
    fun `TelegramRateLimiter endpoints are independent`() {
        val rl = TelegramRateLimiter()
        rl.recordRateLimit("a", 30)
        // b was never recorded, so it should still be 0.
        assertEquals(0L, rl.checkRateLimit("b"))
        assertTrue(rl.checkRateLimit("a") > 0)
    }

    // ─── TelegramFallbackTransport ────────────────────────────────────────

    @Test
    fun `TelegramFallbackTransport handleAsyncRequest returns null on Android`() {
        val transport = TelegramFallbackTransport()
        val result = kotlinx.coroutines.runBlocking {
            transport.handleAsyncRequest(request = "anything")
        }
        assertNull(result)
    }
}
