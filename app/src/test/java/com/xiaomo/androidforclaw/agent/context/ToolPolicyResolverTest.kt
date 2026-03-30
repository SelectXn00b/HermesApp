package com.xiaomo.androidforclaw.agent.context

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ToolPolicyResolver.
 * Verifies alignment with OpenClaw resolveToolPolicy:
 * - DM/local → FULL access
 * - Group/channel/thread → RESTRICTED (memory_*, config_* blocked)
 * - Tool allow/deny logic per policy level
 */
class ToolPolicyResolverTest {

    // ── resolveToolPolicy ──────────────────────────────────────

    @Test
    fun `resolveToolPolicy null chatType is FULL`() {
        assertEquals(ToolPolicyLevel.FULL, ToolPolicyResolver.resolveToolPolicy(null))
    }

    @Test
    fun `resolveToolPolicy p2p is FULL`() {
        assertEquals(ToolPolicyLevel.FULL, ToolPolicyResolver.resolveToolPolicy("p2p"))
    }

    @Test
    fun `resolveToolPolicy direct is FULL`() {
        assertEquals(ToolPolicyLevel.FULL, ToolPolicyResolver.resolveToolPolicy("direct"))
    }

    @Test
    fun `resolveToolPolicy group is RESTRICTED`() {
        assertEquals(ToolPolicyLevel.RESTRICTED, ToolPolicyResolver.resolveToolPolicy("group"))
    }

    @Test
    fun `resolveToolPolicy channel is RESTRICTED`() {
        assertEquals(ToolPolicyLevel.RESTRICTED, ToolPolicyResolver.resolveToolPolicy("channel"))
    }

    @Test
    fun `resolveToolPolicy thread is RESTRICTED`() {
        assertEquals(ToolPolicyLevel.RESTRICTED, ToolPolicyResolver.resolveToolPolicy("thread"))
    }

    @Test
    fun `resolveToolPolicy is case insensitive`() {
        assertEquals(ToolPolicyLevel.RESTRICTED, ToolPolicyResolver.resolveToolPolicy("GROUP"))
        assertEquals(ToolPolicyLevel.FULL, ToolPolicyResolver.resolveToolPolicy("P2P"))
        assertEquals(ToolPolicyLevel.RESTRICTED, ToolPolicyResolver.resolveToolPolicy("Channel"))
    }

    @Test
    fun `resolveToolPolicy unknown type defaults to FULL`() {
        assertEquals(ToolPolicyLevel.FULL, ToolPolicyResolver.resolveToolPolicy("unknown"))
        assertEquals(ToolPolicyLevel.FULL, ToolPolicyResolver.resolveToolPolicy("custom"))
    }

    // ── isToolAllowed ──────────────────────────────────────────

    @Test
    fun `FULL policy allows all tools`() {
        assertTrue(ToolPolicyResolver.isToolAllowed("memory_search", ToolPolicyLevel.FULL))
        assertTrue(ToolPolicyResolver.isToolAllowed("memory_get", ToolPolicyLevel.FULL))
        assertTrue(ToolPolicyResolver.isToolAllowed("config_get", ToolPolicyLevel.FULL))
        assertTrue(ToolPolicyResolver.isToolAllowed("config_set", ToolPolicyLevel.FULL))
        assertTrue(ToolPolicyResolver.isToolAllowed("bash", ToolPolicyLevel.FULL))
    }

    @Test
    fun `RESTRICTED policy blocks memory tools`() {
        assertFalse(ToolPolicyResolver.isToolAllowed("memory_search", ToolPolicyLevel.RESTRICTED))
        assertFalse(ToolPolicyResolver.isToolAllowed("memory_get", ToolPolicyLevel.RESTRICTED))
    }

    @Test
    fun `RESTRICTED policy blocks config tools`() {
        assertFalse(ToolPolicyResolver.isToolAllowed("config_get", ToolPolicyLevel.RESTRICTED))
        assertFalse(ToolPolicyResolver.isToolAllowed("config_set", ToolPolicyLevel.RESTRICTED))
    }

    @Test
    fun `RESTRICTED policy allows non-sensitive tools`() {
        assertTrue(ToolPolicyResolver.isToolAllowed("bash", ToolPolicyLevel.RESTRICTED))
        assertTrue(ToolPolicyResolver.isToolAllowed("screenshot", ToolPolicyLevel.RESTRICTED))
        assertTrue(ToolPolicyResolver.isToolAllowed("tap", ToolPolicyLevel.RESTRICTED))
        assertTrue(ToolPolicyResolver.isToolAllowed("sessions_history", ToolPolicyLevel.RESTRICTED))
    }

    @Test
    fun `NONE policy blocks all tools`() {
        assertFalse(ToolPolicyResolver.isToolAllowed("bash", ToolPolicyLevel.NONE))
        assertFalse(ToolPolicyResolver.isToolAllowed("memory_search", ToolPolicyLevel.NONE))
        assertFalse(ToolPolicyResolver.isToolAllowed("screenshot", ToolPolicyLevel.NONE))
    }

    // ── getRestrictedToolNames ──────────────────────────────────

    @Test
    fun `getRestrictedToolNames returns exactly 4 tools`() {
        val restricted = ToolPolicyResolver.getRestrictedToolNames()
        assertEquals(4, restricted.size)
        assertTrue(restricted.contains("memory_search"))
        assertTrue(restricted.contains("memory_get"))
        assertTrue(restricted.contains("config_get"))
        assertTrue(restricted.contains("config_set"))
    }

    // ── Integration: resolveToolPolicy → isToolAllowed ─────────

    @Test
    fun `group chat blocks memory_search end to end`() {
        val policy = ToolPolicyResolver.resolveToolPolicy("group")
        assertFalse(ToolPolicyResolver.isToolAllowed("memory_search", policy))
    }

    @Test
    fun `DM allows memory_search end to end`() {
        val policy = ToolPolicyResolver.resolveToolPolicy("p2p")
        assertTrue(ToolPolicyResolver.isToolAllowed("memory_search", policy))
    }

    @Test
    fun `group chat allows bash end to end`() {
        val policy = ToolPolicyResolver.resolveToolPolicy("group")
        assertTrue(ToolPolicyResolver.isToolAllowed("bash", policy))
    }
}
