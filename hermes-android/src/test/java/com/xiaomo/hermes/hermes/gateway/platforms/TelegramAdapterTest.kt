package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for TelegramAdapter.kt — the adapter requires an Android [Context] to
 * construct (cache dir seeding in `init`), so pure-JVM tests cover:
 *  • Companion-object constants (direct JVM access)
 *  • Source-level guards for runtime behavior that can't be exercised
 *    without Robolectric
 *
 * Covers TC-GW-135/136/137/138/139/140-a.
 */
class TelegramAdapterTest {

    private val src: String by lazy {
        File("src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Telegram.kt")
            .readText()
    }

    // ── R-GW-005 / TC-GW-135-a: empty token false ──
    /**
     * TC-GW-135-a — when config.token is blank and TELEGRAM_BOT_TOKEN env is
     * unset, `connect()` must return false without making any HTTP call.
     * Source-level guard: the method must check `_token.isEmpty()` and bail
     * before the OkHttp call.
     */
    @Test
    fun `empty token false`() {
        // The guard clause must exist and log+return false early.
        assertTrue(
            "connect() must short-circuit on empty token",
            src.contains("if (_token.isEmpty())") &&
                src.contains("TELEGRAM_BOT_TOKEN not set"))
        // `_token` must fall back to env var only when config.token is empty.
        assertTrue(
            "token resolution must read TELEGRAM_BOT_TOKEN env",
            src.contains("System.getenv(\"TELEGRAM_BOT_TOKEN\")"))
    }

    // ── R-GW-005 / TC-GW-136-a: URL constants ──
    /**
     * TC-GW-136-a — Telegram Bot API URL pattern must be
     * `https://api.telegram.org/bot{token}/...`. Verified via the companion
     * constant + the source template.
     */
    @Test
    fun `url constants`() {
        assertEquals("https://api.telegram.org", TelegramAdapter.API_BASE)
        assertTrue(
            "getMe URL must follow /bot\$token/ pattern",
            src.contains("\$API_BASE/bot\$_token/getMe"))
        assertTrue(
            "getUpdates URL must follow /bot\$token/ pattern",
            src.contains("\$API_BASE/bot\$_token/getUpdates"))
    }

    // ── R-GW-005 / TC-GW-137-a: offset monotonic ──
    /**
     * TC-GW-137-a — long-polling offset must be strictly monotonic:
     * every incoming update advances `_updateOffset` to `update_id + 1`.
     * Source-level guard: the assignment pattern must exist.
     */
    @Test
    fun `offset monotonic`() {
        assertTrue(
            "offset must advance via (update_id + 1)",
            src.contains("_updateOffset.set(update.getLong(\"update_id\") + 1)"))
        assertTrue(
            "offset must be AtomicLong for thread safety",
            src.contains("_updateOffset = AtomicLong"))
        assertTrue(
            "offset must be included in getUpdates query when > 0",
            src.contains("if (_updateOffset.get() > 0)") &&
                src.contains("&offset=\${_updateOffset.get()}"))
    }

    // ── R-GW-005 / TC-GW-138-a: caption cap 1024 UTF-16 ──
    /**
     * TC-GW-138-a — Telegram enforces a 1024-codepoint caption cap. The
     * adapter must truncate captions with `.take(MAX_CAPTION_UTF16)` where
     * the constant is exactly 1024.
     */
    @Test
    fun `caption cap`() {
        assertEquals(1024, TelegramAdapter.MAX_CAPTION_UTF16)
        // Every place that attaches a caption must truncate it.
        val occurrences = Regex("caption\\.take\\(MAX_CAPTION_UTF16\\)").findAll(src).count()
        assertTrue(
            "caption truncation must be applied (found $occurrences call sites)",
            occurrences >= 1)
    }

    // ── R-GW-005 / TC-GW-139-a: allowed_groups parses shape ──
    /**
     * TC-GW-139-a — the `allowed_groups` extra is parsed with the
     * split-trim-filter-toSet idiom so that blank entries are dropped and
     * whitespace around ids is stripped. Runtime gating on this set is a
     * later wiring task; for now we assert the config-parse shape that
     * allows the ACL to be populated without code edits.
     */
    @Test
    fun `allowed_groups parse shape`() {
        assertTrue(
            "must read allowed_groups from extras as Set<String>",
            src.contains("_allowedGroups: Set<String> = config.extra(\"allowed_groups\")"))
        // split-trim-filter-toSet idiom must be there verbatim.
        assertTrue(
            "split-trim-filter-toSet idiom must produce the set",
            src.contains(".split(\",\")") &&
                src.contains(".map { it.trim() }") &&
                src.contains(".filter { it.isNotEmpty() }") &&
                src.contains(".toSet()"))
        // Default to empty set when extra is missing.
        assertTrue(
            "must default to emptySet() when extra missing",
            src.contains("?: emptySet()"))
    }

    // ── R-GW-005 / TC-GW-140-a: drop_pending_updates=true ──
    /**
     * TC-GW-140-a — when `drop_pending_updates` is true (the default), the
     * adapter must call `_dropPendingUpdates()` during `connect()` so that
     * any stale updates queued while the bot was offline are skipped.
     */
    @Test
    fun `drop pending flag`() {
        assertTrue(
            "config flag must default to true",
            src.contains("config.extraBool(\"drop_pending_updates\", true)"))
        assertTrue(
            "connect() must invoke _dropPendingUpdates when flag set",
            src.contains("if (_dropPendingUpdates)") &&
                src.contains("_dropPendingUpdates()"))
    }

    // ── Sanity: companion constants ──
    @Test
    fun `MAX_MESSAGE_LENGTH matches Python`() {
        assertEquals(4096, TelegramAdapter.MAX_MESSAGE_LENGTH)
    }

    @Test
    fun `POLLING_TIMEOUT is 30s`() {
        assertEquals(30, TelegramAdapter.POLLING_TIMEOUT)
    }
}
