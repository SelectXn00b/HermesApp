package com.xiaomo.hermes.hermes.gateway.platforms.qqbot

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic subset of qqbot/Onboard.kt:
 *   BindStatus.fromCode — enum lookup + unknown fallback
 *   buildConnectUrl — URL encodes taskId into QR_URL_TEMPLATE
 *
 * The HTTPS-backed createBindTask / pollBindResult calls live sockets and
 * are covered by integration tests.
 */
class OnboardTest {

    // ─── BindStatus.fromCode ──────────────────────────────────────────────

    @Test
    fun `fromCode maps known codes`() {
        assertEquals(Onboard.BindStatus.NONE, Onboard.BindStatus.fromCode(0))
        assertEquals(Onboard.BindStatus.PENDING, Onboard.BindStatus.fromCode(1))
        assertEquals(Onboard.BindStatus.COMPLETED, Onboard.BindStatus.fromCode(2))
        assertEquals(Onboard.BindStatus.EXPIRED, Onboard.BindStatus.fromCode(3))
    }

    @Test
    fun `fromCode unknown code falls back to NONE`() {
        assertEquals(Onboard.BindStatus.NONE, Onboard.BindStatus.fromCode(-1))
        assertEquals(Onboard.BindStatus.NONE, Onboard.BindStatus.fromCode(99))
    }

    // ─── buildConnectUrl ──────────────────────────────────────────────────

    @Test
    fun `buildConnectUrl substitutes taskId`() {
        val url = Onboard.buildConnectUrl("abc123")
        assertTrue(url, url.contains("task_id=abc123"))
        assertTrue(url, url.startsWith("https://q.qq.com/qqbot/openclaw/connect.html"))
        assertTrue(url, url.contains("source=hermes"))
    }

    @Test
    fun `buildConnectUrl url-encodes special chars`() {
        val url = Onboard.buildConnectUrl("a b/c")
        // Space → "+", "/" → "%2F" under URLEncoder
        assertTrue(url, url.contains("task_id=a+b%2Fc"))
    }

    @Test
    fun `buildConnectUrl leaves template shape intact`() {
        val url = Onboard.buildConnectUrl("t")
        // _wv=2 flag is always present in the template.
        assertTrue(url, url.contains("_wv=2"))
    }
}
