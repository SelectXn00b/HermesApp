package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for Homeassistant.kt — source-level + companion-object guards for
 * the behaviors asserted by TC-GW-182/183/184-a. Pure-JVM; adapter
 * requires Context so connect/send can't be exercised without Robolectric.
 */
class HomeAssistantAdapterTest {

    private val src: String by lazy {
        File("src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Homeassistant.kt")
            .readText()
    }

    // ── R-GW-005 / TC-GW-182-a: backoff ladder matches Python ──
    /**
     * TC-GW-182-a — Python `_BACKOFF_STEPS = [5, 10, 30, 60]` (see
     * `reference/hermes-agent/gateway/platforms/homeassistant.py:63`). The
     * Kotlin port must stay 1:1 with the Python list so reconnect cadence
     * is identical. Earlier Kotlin had extra steps (120, 300) — a drift
     * fixed as part of this TC.
     */
    @Test
    fun `backoff ladder`() {
        assertEquals(
            "BACKOFF_STEPS must match Python exactly",
            listOf(5, 10, 30, 60),
            HomeAssistantAdapter.BACKOFF_STEPS)
        // And must be wired as a reconnect schedule — the min(idx, len-1)
        // clamp pattern would live in the reconnect loop once it's ported.
        assertTrue(
            "BACKOFF_STEPS constant must be declared",
            src.contains("BACKOFF_STEPS = listOf(5, 10, 30, 60)"))
    }

    // ── R-GW-005 / TC-GW-183-a: auth header uses Bearer scheme ──
    /**
     * TC-GW-183-a — Home Assistant long-lived access tokens go in an
     * `Authorization: Bearer <token>` header. Must be present on both the
     * connect probe (`/api/`) and the send path.
     */
    @Test
    fun `auth header`() {
        val bearerCount = Regex("Bearer \\\$_hassToken").findAll(src).count()
        assertTrue(
            "Bearer auth header must appear on both connect and send paths",
            bearerCount >= 2)
        assertTrue(
            "Authorization header literal must be correct",
            src.contains("\"Authorization\", \"Bearer \$_hassToken\""))
    }

    // ── R-GW-005 / TC-GW-184-a: notify path is persistent_notification.create ──
    /**
     * TC-GW-184-a — Python upstream chooses `persistent_notification.create`
     * over the `notify.*` services to avoid a race with the event loop
     * reading the shared WS. The Kotlin port must POST to the same path.
     * (See `reference/hermes-agent/gateway/platforms/homeassistant.py:398`.)
     */
    @Test
    fun `notify path`() {
        assertTrue(
            "send() must POST to persistent_notification/create per Python upstream",
            src.contains("\$_hassUrl/api/services/persistent_notification/create"))
        // Must send JSON with title + message keys.
        assertTrue(
            "payload must include Hermes Agent title",
            src.contains("put(\"title\", \"Hermes Agent\")"))
        assertTrue(
            "payload must include message field",
            src.contains("put(\"message\", content)"))
    }

    // ── Guards: cooldown + watch filters wired from config ──
    @Test
    fun `cooldown default is 10s`() {
        assertEquals(10L, HomeAssistantAdapter.COOLDOWN_SECONDS)
    }

    @Test
    fun `watch filters parse from comma-separated config`() {
        // All three filter sets follow the same split/trim/filter/toSet idiom.
        val occurrences = Regex("\\.split\\(\",\"\\)\\?\\.map \\{ it\\.trim\\(\\) \\}")
            .findAll(src).count()
        assertTrue(
            "watch_domains / watch_entities / ignore_entities must all parse",
            occurrences >= 3)
    }
}
