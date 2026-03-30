package com.xiaomo.androidforclaw.agent.session

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SessionKeyUtils.
 * Verifies alignment with OpenClaw SessionKey.kt:
 * - normalizeMainKey null/blank/"default" → "main"
 * - isCanonicalMainSessionKey for "main"/"global"/"agent:*"
 * - isGroupSession for Feishu/Gateway/Telegram formats
 * - extractChatType from various session key formats
 */
class SessionKeyUtilsTest {

    // ── normalizeMainKey ───────────────────────────────────────

    @Test
    fun `normalizeMainKey null returns main`() {
        assertEquals("main", SessionKeyUtils.normalizeMainKey(null))
    }

    @Test
    fun `normalizeMainKey blank returns main`() {
        assertEquals("main", SessionKeyUtils.normalizeMainKey(""))
        assertEquals("main", SessionKeyUtils.normalizeMainKey("   "))
    }

    @Test
    fun `normalizeMainKey default returns main`() {
        assertEquals("main", SessionKeyUtils.normalizeMainKey("default"))
    }

    @Test
    fun `normalizeMainKey preserves non-default values`() {
        assertEquals("my-session", SessionKeyUtils.normalizeMainKey("my-session"))
        assertEquals("group:oc_xxx", SessionKeyUtils.normalizeMainKey("group:oc_xxx"))
        assertEquals("agent:main:main", SessionKeyUtils.normalizeMainKey("agent:main:main"))
    }

    @Test
    fun `normalizeMainKey trims whitespace`() {
        assertEquals("my-session", SessionKeyUtils.normalizeMainKey("  my-session  "))
    }

    // ── isCanonicalMainSessionKey ──────────────────────────────

    @Test
    fun `isCanonicalMainSessionKey for main`() {
        assertTrue(SessionKeyUtils.isCanonicalMainSessionKey("main"))
    }

    @Test
    fun `isCanonicalMainSessionKey for global`() {
        assertTrue(SessionKeyUtils.isCanonicalMainSessionKey("global"))
    }

    @Test
    fun `isCanonicalMainSessionKey for agent prefix`() {
        assertTrue(SessionKeyUtils.isCanonicalMainSessionKey("agent:main:main"))
        assertTrue(SessionKeyUtils.isCanonicalMainSessionKey("agent:sub1:task"))
    }

    @Test
    fun `isCanonicalMainSessionKey false for group sessions`() {
        assertFalse(SessionKeyUtils.isCanonicalMainSessionKey("group:oc_xxx"))
        assertFalse(SessionKeyUtils.isCanonicalMainSessionKey("p2p:ou_xxx"))
    }

    @Test
    fun `isCanonicalMainSessionKey false for null and blank`() {
        assertFalse(SessionKeyUtils.isCanonicalMainSessionKey(null))
        assertFalse(SessionKeyUtils.isCanonicalMainSessionKey(""))
        assertFalse(SessionKeyUtils.isCanonicalMainSessionKey("   "))
    }

    // ── isGroupSession ─────────────────────────────────────────

    @Test
    fun `isGroupSession Feishu format group colon`() {
        assertTrue(SessionKeyUtils.isGroupSession("group:oc_abcdefg"))
    }

    @Test
    fun `isGroupSession Gateway format underscore group`() {
        assertTrue(SessionKeyUtils.isGroupSession("oc_xxx_group"))
    }

    @Test
    fun `isGroupSession Telegram gateway format with g-`() {
        assertTrue(SessionKeyUtils.isGroupSession("telegram:g-123456789"))
    }

    @Test
    fun `isGroupSession false for p2p`() {
        assertFalse(SessionKeyUtils.isGroupSession("p2p:ou_xxx"))
    }

    @Test
    fun `isGroupSession false for main`() {
        assertFalse(SessionKeyUtils.isGroupSession("main"))
    }

    @Test
    fun `isGroupSession case insensitive`() {
        assertTrue(SessionKeyUtils.isGroupSession("GROUP:oc_xxx"))
        assertTrue(SessionKeyUtils.isGroupSession("OC_XXX_GROUP"))
    }

    // ── extractChatType ────────────────────────────────────────

    @Test
    fun `extractChatType Feishu group format`() {
        assertEquals("group", SessionKeyUtils.extractChatType("group:oc_abcdefg"))
    }

    @Test
    fun `extractChatType Feishu p2p format`() {
        assertEquals("direct", SessionKeyUtils.extractChatType("p2p:ou_xxx"))
    }

    @Test
    fun `extractChatType Gateway group format`() {
        assertEquals("group", SessionKeyUtils.extractChatType("oc_xxx_group"))
    }

    @Test
    fun `extractChatType Gateway p2p format`() {
        assertEquals("direct", SessionKeyUtils.extractChatType("oc_xxx_p2p"))
    }

    @Test
    fun `extractChatType Telegram group format`() {
        assertEquals("group", SessionKeyUtils.extractChatType("telegram:g-123456789"))
    }

    @Test
    fun `extractChatType main session returns null`() {
        assertNull(SessionKeyUtils.extractChatType("main"))
    }

    @Test
    fun `extractChatType agent session returns null`() {
        assertNull(SessionKeyUtils.extractChatType("agent:main:main"))
    }
}
