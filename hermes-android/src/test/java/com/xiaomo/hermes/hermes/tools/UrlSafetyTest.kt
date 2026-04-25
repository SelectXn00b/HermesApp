package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyTest {

    @Test
    fun `isSafeUrl blocks localhost`() {
        assertFalse(isSafeUrl("http://localhost/"))
        assertFalse(isSafeUrl("http://127.0.0.1/"))
    }

    @Test
    fun `isSafeUrl blocks metadata endpoints by hostname`() {
        // These short-circuit before DNS — no network needed for a reliable result.
        assertFalse(isSafeUrl("http://metadata.google.internal/"))
        assertFalse(isSafeUrl("http://metadata.goog/"))
    }

    @Test
    fun `isSafeUrl blocks mdns local suffix`() {
        assertFalse(isSafeUrl("http://my-printer.local/"))
        assertFalse(isSafeUrl("http://something.Local/"))  // case-insensitive
    }

    @Test
    fun `isSafeUrl rejects malformed URLs`() {
        assertFalse(isSafeUrl("not-a-url"))
        assertFalse(isSafeUrl(""))
    }

    @Test
    fun `isSafeUrl rejects URL with empty hostname`() {
        assertFalse(isSafeUrl("http:///nohost"))
    }

    @Test
    fun `isSafeUrl normalizes trailing dot and uppercase`() {
        // metadata.google.internal. with trailing dot / mixed case should still hit blocklist.
        assertFalse(isSafeUrl("http://METADATA.GOOGLE.INTERNAL./"))
    }

    @Test
    fun `isSafeUrl fails closed when DNS unresolvable`() {
        // Unresolvable hostname → catch DNS exception → return false.
        assertFalse(isSafeUrl("http://definitely.not.a.real.hostname.invalid.example.test.local.abc123/"))
    }

    @Test
    fun `isSafeUrl blocks private 10-dot IP`() {
        assertFalse(isSafeUrl("http://10.0.0.1/"))
    }

    @Test
    fun `isSafeUrl blocks private 192-168 IP`() {
        assertFalse(isSafeUrl("http://192.168.1.1/"))
    }

    @Test
    fun `isSafeUrl blocks CGNAT 100-64 IP`() {
        assertFalse(isSafeUrl("http://100.64.0.1/"))
        assertFalse(isSafeUrl("http://100.127.255.254/"))
    }

    @Test
    fun `isSafeUrl allows non-CGNAT 100-dot IPs below the range`() {
        // 100.63.x.x is not CGNAT; but it's public routable and DNS resolution
        // would be just the IP literal (no DNS). Should not be blocked by CGNAT
        // prefix logic (below the 64–127 range).
        assertTrue(isSafeUrl("http://100.63.0.1/"))
    }

    @Test
    fun `isSafeUrl blocks link-local addresses`() {
        assertFalse(isSafeUrl("http://169.254.169.254/"))
    }
}
