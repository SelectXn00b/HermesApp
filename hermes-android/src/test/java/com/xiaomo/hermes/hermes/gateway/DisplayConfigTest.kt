package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers DisplayConfig.kt::resolveDisplaySetting — the 4-tier layered
 * resolver for per-platform display settings:
 *
 *   1. explicit display.platforms.<platform>.<key>
 *   2. global display.<key>   (except setting == "streaming")
 *   3. built-in _PLATFORM_DEFAULTS[<platform>][<key>]
 *   4. built-in _GLOBAL_DEFAULTS[<key>]
 *   then fallback
 *
 * Also covers _normalise branches: tool_progress bool→string,
 * show_reasoning/streaming truthy parsing, tool_preview_length int coercion.
 */
class DisplayConfigTest {

    // ─── Tier 1: explicit per-platform override ───────────────────────────

    @Test
    fun `explicit per-platform override wins`() {
        val cfg = mapOf(
            "display" to mapOf(
                "platforms" to mapOf(
                    "telegram" to mapOf("tool_progress" to "new")
                ),
                "tool_progress" to "all",
            )
        )
        assertEquals("new", resolveDisplaySetting(cfg, "telegram", "tool_progress"))
    }

    // ─── Tier 1b: legacy tool_progress_overrides ─────────────────────────

    @Test
    fun `legacy tool_progress_overrides still honored`() {
        val cfg = mapOf(
            "display" to mapOf(
                "tool_progress_overrides" to mapOf("slack" to "new")
            )
        )
        assertEquals("new", resolveDisplaySetting(cfg, "slack", "tool_progress"))
    }

    @Test
    fun `explicit platforms beats legacy overrides`() {
        val cfg = mapOf(
            "display" to mapOf(
                "platforms" to mapOf(
                    "slack" to mapOf("tool_progress" to "all")
                ),
                "tool_progress_overrides" to mapOf("slack" to "new"),
            )
        )
        assertEquals("all", resolveDisplaySetting(cfg, "slack", "tool_progress"))
    }

    // ─── Tier 2: global user setting ─────────────────────────────────────

    @Test
    fun `global user setting used when no platform override`() {
        val cfg = mapOf(
            "display" to mapOf("tool_progress" to "off")
        )
        // Telegram's built-in default is "all", but global user setting wins.
        assertEquals("off", resolveDisplaySetting(cfg, "telegram", "tool_progress"))
    }

    @Test
    fun `streaming setting ignores global user tier`() {
        // display.streaming is CLI-only; gateway must skip tier 2 for streaming.
        val cfg = mapOf(
            "display" to mapOf("streaming" to true)
        )
        // No per-platform override → skips tier 2 → falls to built-in default
        // which for "telegram" (TIER_HIGH) is null → then _GLOBAL_DEFAULTS is
        // null too → returns fallback (null).
        assertNull(resolveDisplaySetting(cfg, "telegram", "streaming"))
    }

    @Test
    fun `streaming per-platform override still honored`() {
        val cfg = mapOf(
            "display" to mapOf(
                "platforms" to mapOf("telegram" to mapOf("streaming" to true))
            )
        )
        assertEquals(true, resolveDisplaySetting(cfg, "telegram", "streaming"))
    }

    // ─── Tier 3: built-in platform default ───────────────────────────────

    @Test
    fun `platform default used when no user setting`() {
        val cfg = mapOf<String, Any?>()
        // telegram → TIER_HIGH → tool_progress="all"
        assertEquals("all", resolveDisplaySetting(cfg, "telegram", "tool_progress"))
        // slack → TIER_MEDIUM → tool_progress="new"
        assertEquals("new", resolveDisplaySetting(cfg, "slack", "tool_progress"))
        // email → TIER_MINIMAL → tool_progress="off"
        assertEquals("off", resolveDisplaySetting(cfg, "email", "tool_progress"))
    }

    @Test
    fun `api_server preview length override from TIER_HIGH+`() {
        val cfg = mapOf<String, Any?>()
        // api_server overrides TIER_HIGH to set tool_preview_length=0.
        assertEquals(0, resolveDisplaySetting(cfg, "api_server", "tool_preview_length"))
        // telegram (plain TIER_HIGH) keeps 40.
        assertEquals(40, resolveDisplaySetting(cfg, "telegram", "tool_preview_length"))
    }

    // ─── Tier 4: built-in global default ─────────────────────────────────

    @Test
    fun `unknown platform falls through to global default`() {
        val cfg = mapOf<String, Any?>()
        assertEquals("all", resolveDisplaySetting(cfg, "unknown-platform", "tool_progress"))
        assertEquals(0, resolveDisplaySetting(cfg, "unknown-platform", "tool_preview_length"))
    }

    // ─── Fallback ────────────────────────────────────────────────────────

    @Test
    fun `unknown setting returns fallback`() {
        assertEquals(
            "sentinel",
            resolveDisplaySetting(
                mapOf<String, Any?>(),
                "telegram",
                "no-such-setting",
                fallback = "sentinel",
            )
        )
    }

    // ─── _normalise: tool_progress ───────────────────────────────────────

    @Test
    fun `tool_progress boolean normalised to off or all`() {
        val cfgFalse = mapOf(
            "display" to mapOf(
                "platforms" to mapOf("telegram" to mapOf("tool_progress" to false))
            )
        )
        assertEquals("off", resolveDisplaySetting(cfgFalse, "telegram", "tool_progress"))

        val cfgTrue = mapOf(
            "display" to mapOf(
                "platforms" to mapOf("telegram" to mapOf("tool_progress" to true))
            )
        )
        assertEquals("all", resolveDisplaySetting(cfgTrue, "telegram", "tool_progress"))
    }

    @Test
    fun `tool_progress string lowered`() {
        val cfg = mapOf(
            "display" to mapOf(
                "platforms" to mapOf("telegram" to mapOf("tool_progress" to "NEW"))
            )
        )
        assertEquals("new", resolveDisplaySetting(cfg, "telegram", "tool_progress"))
    }

    // ─── _normalise: show_reasoning / streaming (truthy) ─────────────────

    @Test
    fun `show_reasoning string truthy parsing`() {
        fun v(raw: Any?): Any? {
            val cfg = mapOf(
                "display" to mapOf(
                    "platforms" to mapOf("telegram" to mapOf("show_reasoning" to raw))
                )
            )
            return resolveDisplaySetting(cfg, "telegram", "show_reasoning")
        }
        assertEquals(true, v("yes"))
        assertEquals(true, v("ON"))
        assertEquals(true, v("1"))
        assertEquals(true, v("true"))
        assertEquals(false, v("no"))
        assertEquals(false, v("off"))
        assertEquals(false, v("0"))
        assertEquals(true, v(true))
        assertEquals(false, v(false))
    }

    // ─── _normalise: tool_preview_length (int coercion) ──────────────────

    @Test
    fun `tool_preview_length int from number or string`() {
        fun v(raw: Any?): Any? {
            val cfg = mapOf(
                "display" to mapOf(
                    "platforms" to mapOf("telegram" to mapOf("tool_preview_length" to raw))
                )
            )
            return resolveDisplaySetting(cfg, "telegram", "tool_preview_length")
        }
        assertEquals(42, v(42))
        assertEquals(42, v(42.7))
        assertEquals(42, v("42"))
        // Unparseable string → caught → returns 0
        assertEquals(0, v("not-a-number"))
    }

    // ─── OVERRIDEABLE_KEYS ───────────────────────────────────────────────

    @Test
    fun `OVERRIDEABLE_KEYS matches expected set`() {
        assertEquals(
            setOf("tool_progress", "show_reasoning", "tool_preview_length", "streaming"),
            OVERRIDEABLE_KEYS,
        )
    }
}
