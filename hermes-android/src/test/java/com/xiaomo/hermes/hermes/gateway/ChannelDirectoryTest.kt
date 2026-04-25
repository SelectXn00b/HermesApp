package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic subset of gateway/ChannelDirectory.kt:
 *   _normalizeChannelQuery — strip leading # + lowercase
 *   _channelTargetName — Discord guild prepends "#"
 *   _sessionEntryId — origin.chat_id
 *   _sessionEntryName — user_name → chat_name → chat_id → ""
 *
 * The build/load/lookup helpers are Android stubs (always return empty)
 * per gateway/channel_directory.py's Android-platform difference — we
 * assert they return the documented safe empties.
 */
class ChannelDirectoryTest {

    // ─── _normalizeChannelQuery ───────────────────────────────────────────

    @Test
    fun `normalizeChannelQuery strips leading hash and lowercases`() {
        assertEquals("general", _normalizeChannelQuery("#General"))
        assertEquals("bot-home", _normalizeChannelQuery("##Bot-Home"))
    }

    @Test
    fun `normalizeChannelQuery trims surrounding whitespace`() {
        assertEquals("general", _normalizeChannelQuery("  General  "))
        // Python: lstrip("#").strip().lower() — leading whitespace blocks
        // the lstrip from removing the inner '#', so it stays.
        assertEquals("#general", _normalizeChannelQuery("  #general  "))
    }

    @Test
    fun `normalizeChannelQuery leaves mid-string hashes alone`() {
        assertEquals("a#b", _normalizeChannelQuery("a#B"))
    }

    // ─── _channelTargetName ───────────────────────────────────────────────

    @Test
    fun `channelTargetName prepends hash for Discord with guild`() {
        val ch = mapOf<String, Any?>("name" to "bot-home", "guild" to "MyServer")
        assertEquals("#bot-home", _channelTargetName("discord", ch))
    }

    @Test
    fun `channelTargetName no hash when Discord has no guild`() {
        val ch = mapOf<String, Any?>("name" to "dm-channel", "guild" to null)
        assertEquals("dm-channel", _channelTargetName("discord", ch))
    }

    @Test
    fun `channelTargetName no hash for non-Discord`() {
        val ch = mapOf<String, Any?>("name" to "engineering", "guild" to "X")
        assertEquals("engineering", _channelTargetName("slack", ch))
    }

    @Test
    fun `channelTargetName returns empty when no name`() {
        assertEquals("", _channelTargetName("slack", mapOf<String, Any?>()))
    }

    // ─── _sessionEntryId ──────────────────────────────────────────────────

    @Test
    fun `sessionEntryId extracts chat_id`() {
        assertEquals("123", _sessionEntryId(mapOf("chat_id" to "123")))
    }

    @Test
    fun `sessionEntryId null when missing`() {
        assertNull(_sessionEntryId(mapOf<String, Any?>()))
    }

    @Test
    fun `sessionEntryId null when not a string`() {
        // Python `origin.get("chat_id")` returns any type; Kotlin cast coerces
        // non-string to null.
        assertNull(_sessionEntryId(mapOf("chat_id" to 42)))
    }

    // ─── _sessionEntryName ────────────────────────────────────────────────

    @Test
    fun `sessionEntryName prefers user_name`() {
        val origin = mapOf<String, Any?>(
            "user_name" to "Alice",
            "chat_name" to "group-x",
            "chat_id" to "chat-1",
        )
        assertEquals("Alice", _sessionEntryName(origin))
    }

    @Test
    fun `sessionEntryName falls back to chat_name`() {
        val origin = mapOf<String, Any?>(
            "chat_name" to "group-x",
            "chat_id" to "chat-1",
        )
        assertEquals("group-x", _sessionEntryName(origin))
    }

    @Test
    fun `sessionEntryName falls back to chat_id`() {
        val origin = mapOf<String, Any?>("chat_id" to "chat-1")
        assertEquals("chat-1", _sessionEntryName(origin))
    }

    @Test
    fun `sessionEntryName empty when nothing set`() {
        assertEquals("", _sessionEntryName(mapOf<String, Any?>()))
    }

    // ─── Android stub behavior ────────────────────────────────────────────

    @Test
    fun `build and load helpers return empty on Android`() {
        assertEquals(emptyMap<String, Any?>(), buildChannelDirectory(emptyMap<Any?, Any?>()))
        assertEquals(emptyList<Map<String, String>>(), _buildDiscord(null))
        assertEquals(emptyList<Map<String, String>>(), _buildSlack(null))
        assertEquals(emptyList<Map<String, String>>(), _buildFromSessions("telegram"))
        assertEquals(emptyMap<String, Any?>(), loadDirectory())
    }

    @Test
    fun `lookup helpers return null on Android`() {
        assertNull(lookupChannelType("telegram", "chat-1"))
        assertNull(resolveChannelName("telegram", "general"))
    }

    @Test
    fun `formatDirectoryForDisplay returns empty on Android`() {
        assertEquals("", formatDirectoryForDisplay())
    }
}
