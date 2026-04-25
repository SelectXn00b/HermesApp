package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers Restart.kt::parseRestartDrainTimeout — the duration parser used
 * for gateway restart drain timeout config/env values.
 *
 * Accepts: plain number (seconds), "<n>s" / "<n>sec(ond[s])", "<n>m" /
 * "<n>min(ute[s])". Falls back to DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT
 * on any parse failure or non-positive input.
 */
class RestartTest {

    // ─── Default fallbacks ────────────────────────────────────────────────

    @Test
    fun `null returns default`() {
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout(null), 0.0)
    }

    @Test
    fun `blank or empty returns default`() {
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout(""), 0.0)
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout("   "), 0.0)
    }

    @Test
    fun `unparseable garbage returns default`() {
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout("abc"), 0.0)
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout("10x"), 0.0)
    }

    @Test
    fun `zero or negative plain number returns default`() {
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout(0), 0.0)
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout(-10), 0.0)
    }

    // ─── Plain numbers ────────────────────────────────────────────────────

    @Test
    fun `plain int treated as seconds`() {
        assertEquals(15.0, parseRestartDrainTimeout(15), 0.0)
        assertEquals(120.0, parseRestartDrainTimeout("120"), 0.0)
    }

    @Test
    fun `plain double treated as seconds`() {
        assertEquals(15.5, parseRestartDrainTimeout(15.5), 0.0)
        assertEquals(2.5, parseRestartDrainTimeout("2.5"), 0.0)
    }

    // ─── Seconds suffix ───────────────────────────────────────────────────

    @Test
    fun `s suffix parsed as seconds`() {
        assertEquals(30.0, parseRestartDrainTimeout("30s"), 0.0)
        assertEquals(30.0, parseRestartDrainTimeout("30sec"), 0.0)
        assertEquals(30.0, parseRestartDrainTimeout("30second"), 0.0)
        assertEquals(30.0, parseRestartDrainTimeout("30seconds"), 0.0)
    }

    // ─── Minutes suffix ───────────────────────────────────────────────────

    @Test
    fun `m suffix multiplied by 60`() {
        assertEquals(60.0, parseRestartDrainTimeout("1m"), 0.0)
        assertEquals(120.0, parseRestartDrainTimeout("2min"), 0.0)
        assertEquals(180.0, parseRestartDrainTimeout("3minute"), 0.0)
        assertEquals(600.0, parseRestartDrainTimeout("10minutes"), 0.0)
    }

    // ─── Case / whitespace ────────────────────────────────────────────────

    @Test
    fun `case-insensitive matching`() {
        assertEquals(30.0, parseRestartDrainTimeout("30S"), 0.0)
        assertEquals(120.0, parseRestartDrainTimeout("2MIN"), 0.0)
    }

    @Test
    fun `whitespace between digits and suffix accepted`() {
        // "30 s" → lowercased "30 s" → strip suffix "s" → "30 " → trim → "30"
        assertEquals(30.0, parseRestartDrainTimeout("30 s"), 0.0)
    }

    // ─── Non-positive values ──────────────────────────────────────────────

    @Test
    fun `non-positive suffix value falls through to default`() {
        // "0s" → plain-number path matches first as 0.0 → non-positive → default.
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout("0s"), 0.0)
        assertEquals(DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, parseRestartDrainTimeout("-5s"), 0.0)
    }

    // ─── Constants ────────────────────────────────────────────────────────

    @Test
    fun `constants have expected values`() {
        assertEquals(75, GATEWAY_SERVICE_RESTART_EXIT_CODE)
        assertEquals(30.0, DEFAULT_GATEWAY_RESTART_DRAIN_TIMEOUT, 0.0)
    }
}
