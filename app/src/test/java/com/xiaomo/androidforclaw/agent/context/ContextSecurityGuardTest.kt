package com.xiaomo.androidforclaw.agent.context

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ContextSecurityGuard.
 * Verifies alignment with OpenClaw multi-layer defense:
 * - isSharedContext correctly identifies group/channel/thread
 * - shouldLoadMemory blocks MEMORY.md in shared contexts
 * - redactForSharedContext delegates to SensitiveTextRedactor
 */
class ContextSecurityGuardTest {

    // ── isSharedContext ────────────────────────────────────────

    @Test
    fun `isSharedContext null context is not shared`() {
        assertFalse(ContextSecurityGuard.isSharedContext(null))
    }

    @Test
    fun `isSharedContext local android app is not shared`() {
        val ctx = ContextBuilder.ChannelContext(channel = "android")
        assertFalse(ContextSecurityGuard.isSharedContext(ctx))
    }

    @Test
    fun `isSharedContext p2p is not shared`() {
        val ctx = ContextBuilder.ChannelContext(channel = "feishu", chatType = "p2p")
        assertFalse(ContextSecurityGuard.isSharedContext(ctx))
    }

    @Test
    fun `isSharedContext direct is not shared`() {
        val ctx = ContextBuilder.ChannelContext(channel = "discord", chatType = "direct")
        assertFalse(ContextSecurityGuard.isSharedContext(ctx))
    }

    @Test
    fun `isSharedContext group is shared`() {
        val ctx = ContextBuilder.ChannelContext(channel = "feishu", chatType = "group")
        assertTrue(ContextSecurityGuard.isSharedContext(ctx))
    }

    @Test
    fun `isSharedContext channel is shared`() {
        val ctx = ContextBuilder.ChannelContext(channel = "discord", chatType = "channel")
        assertTrue(ContextSecurityGuard.isSharedContext(ctx))
    }

    @Test
    fun `isSharedContext thread is shared`() {
        val ctx = ContextBuilder.ChannelContext(channel = "discord", chatType = "thread")
        assertTrue(ContextSecurityGuard.isSharedContext(ctx))
    }

    @Test
    fun `isSharedContext is case insensitive`() {
        val ctx = ContextBuilder.ChannelContext(channel = "feishu", chatType = "GROUP")
        assertTrue(ContextSecurityGuard.isSharedContext(ctx))
    }

    @Test
    fun `isSharedContext null chatType is not shared`() {
        val ctx = ContextBuilder.ChannelContext(channel = "feishu", chatType = null)
        assertFalse(ContextSecurityGuard.isSharedContext(ctx))
    }

    // ── shouldLoadMemory ───────────────────────────────────────

    @Test
    fun `shouldLoadMemory true for null context`() {
        assertTrue(ContextSecurityGuard.shouldLoadMemory(null))
    }

    @Test
    fun `shouldLoadMemory true for DM`() {
        val ctx = ContextBuilder.ChannelContext(channel = "feishu", chatType = "p2p")
        assertTrue(ContextSecurityGuard.shouldLoadMemory(ctx))
    }

    @Test
    fun `shouldLoadMemory false for group`() {
        val ctx = ContextBuilder.ChannelContext(channel = "feishu", chatType = "group")
        assertFalse(ContextSecurityGuard.shouldLoadMemory(ctx))
    }

    @Test
    fun `shouldLoadMemory false for channel`() {
        val ctx = ContextBuilder.ChannelContext(channel = "discord", chatType = "channel")
        assertFalse(ContextSecurityGuard.shouldLoadMemory(ctx))
    }

    @Test
    fun `shouldLoadMemory false for thread`() {
        val ctx = ContextBuilder.ChannelContext(channel = "discord", chatType = "thread")
        assertFalse(ContextSecurityGuard.shouldLoadMemory(ctx))
    }

    @Test
    fun `shouldLoadMemory true for local android`() {
        val ctx = ContextBuilder.ChannelContext(channel = "android")
        assertTrue(ContextSecurityGuard.shouldLoadMemory(ctx))
    }

    // ── redactForSharedContext ──────────────────────────────────

    @Test
    fun `redactForSharedContext redacts secrets`() {
        val text = "Here is my key: sk-abcdefghij1234567890abcdef"
        val result = ContextSecurityGuard.redactForSharedContext(text)
        assertFalse(result.contains("sk-abcdefghij1234567890abcdef"))
    }

    @Test
    fun `redactForSharedContext preserves normal text`() {
        val text = "Hello, this is a normal message."
        val result = ContextSecurityGuard.redactForSharedContext(text)
        assertEquals(text, result)
    }

    @Test
    fun `redactForSharedContext handles empty text`() {
        assertEquals("", ContextSecurityGuard.redactForSharedContext(""))
    }

    @Test
    fun `redactForSharedContext redacts multiple provider keys`() {
        val text = "OpenAI: sk-abc12345678901234567890 GitHub: ghp_abcdefghijklmnopqrstuvwxyz1234"
        val result = ContextSecurityGuard.redactForSharedContext(text)
        assertFalse(result.contains("sk-abc12345678901234567890"))
        assertFalse(result.contains("ghp_abcdefghijklmnopqrstuvwxyz1234"))
    }
}
