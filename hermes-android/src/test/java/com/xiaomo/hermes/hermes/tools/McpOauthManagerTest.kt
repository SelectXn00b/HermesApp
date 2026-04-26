package com.xiaomo.hermes.hermes.tools

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for McpOauthManager.kt — central MCP OAuth state manager.
 * Verifies provider caching, 401 dedup behaviour, disk-watch stub, and
 * the module-level singleton lifecycle.
 * Covers TC-MCP-020-a (token cache + shared manager) and supporting
 * structural checks.
 */
class McpOauthManagerTest {

    @Before
    fun reset() {
        resetManagerForTests()
    }

    @After
    fun teardown() {
        resetManagerForTests()
    }

    /** TC-MCP-020-a: getManager returns the same singleton across calls. */
    @Test
    fun `getManager returns same singleton`() {
        val a = getManager()
        val b = getManager()
        assertSame(a, b)
    }

    /** TC-MCP-020-a: resetManagerForTests replaces the singleton. */
    @Test
    fun `resetManagerForTests drops singleton`() {
        val first = getManager()
        resetManagerForTests()
        val second = getManager()
        assertNotEquals(
            "after reset, new manager should differ",
            System.identityHashCode(first),
            System.identityHashCode(second),
        )
    }

    /**
     * TC-MCP-020-a: first getOrBuildProvider constructs a provider;
     * second call with the same URL reuses the cached instance.
     */
    @Test
    fun `getOrBuildProvider caches per server`() {
        val mgr = getManager()
        val p1 = mgr.getOrBuildProvider("srv", "https://a.example.com", null)
        val p2 = mgr.getOrBuildProvider("srv", "https://a.example.com", null)
        assertNotNull(p1)
        assertSame(p1, p2)
    }

    /**
     * TC-MCP-020-a: different serverName => independent cache entries.
     */
    @Test
    fun `different servers get different providers`() {
        val mgr = getManager()
        val a = mgr.getOrBuildProvider("a", "https://a.example.com", null)
        val b = mgr.getOrBuildProvider("b", "https://b.example.com", null)
        assertNotNull(a)
        assertNotNull(b)
        assertNotEquals(System.identityHashCode(a), System.identityHashCode(b))
    }

    /**
     * TC-MCP-020-a: when the URL for the same serverName changes, the
     * cache is discarded and a fresh provider is built.
     */
    @Test
    fun `url change invalidates cache`() {
        val mgr = getManager()
        val p1 = mgr.getOrBuildProvider("srv", "https://old.example.com", null)
        val p2 = mgr.getOrBuildProvider("srv", "https://new.example.com", null)
        assertNotNull(p1)
        assertNotNull(p2)
        assertNotEquals(
            System.identityHashCode(p1),
            System.identityHashCode(p2),
        )
    }

    /** TC-MCP-020-a: provider is a HermesMCPOAuthProvider carrying the server name. */
    @Test
    fun `provider is HermesMCPOAuthProvider with serverName`() {
        val mgr = getManager()
        val p = mgr.getOrBuildProvider("my-server", "https://x.example.com", null)
        assertTrue(p is HermesMCPOAuthProvider)
        assertEquals("my-server", (p as HermesMCPOAuthProvider).serverName)
    }

    /** TC-MCP-020-a: remove() evicts the cache entry so the next call rebuilds. */
    @Test
    fun `remove evicts cache entry`() {
        val mgr = getManager()
        val p1 = mgr.getOrBuildProvider("srv", "https://x.example.com", null)
        mgr.remove("srv")
        val p2 = mgr.getOrBuildProvider("srv", "https://x.example.com", null)
        assertNotNull(p1)
        assertNotNull(p2)
        assertNotEquals(System.identityHashCode(p1), System.identityHashCode(p2))
    }

    /** TC-MCP-020-a: invalidateIfDiskChanged returns false when server not tracked. */
    @Test
    fun `invalidateIfDiskChanged returns false for unknown server`() = runBlocking {
        val mgr = getManager()
        assertFalse(mgr.invalidateIfDiskChanged("never-registered"))
    }

    /** TC-MCP-020-a: invalidateIfDiskChanged returns false on Android placeholder. */
    @Test
    fun `invalidateIfDiskChanged returns false for tracked server stub`() = runBlocking {
        val mgr = getManager()
        mgr.getOrBuildProvider("srv", "https://x.example.com", null)
        // Android placeholder always returns false (mtime check not ported).
        assertFalse(mgr.invalidateIfDiskChanged("srv"))
    }

    /** TC-MCP-021-a (stub): handle401 on unknown server returns false immediately. */
    @Test
    fun `handle401 returns false for unknown server`() = runBlocking {
        val mgr = getManager()
        assertFalse(mgr.handle401("never-registered", "tok"))
    }

    /** TC-MCP-020-a: HermesMCPOAuthProvider.asyncAuthFlow swallows errors. */
    @Test
    fun `asyncAuthFlow does not throw when manager is healthy`() = runBlocking {
        val provider = HermesMCPOAuthProvider(serverName = "srv")
        // Should complete without exception even when serverName not registered —
        // the method catches and logs.
        provider.asyncAuthFlow()
    }

    /** TC-MCP-020-a: ProviderEntry data class carries the defaults we expect. */
    @Test
    fun `ProviderEntry defaults`() {
        val e = ProviderEntry(serverUrl = "https://x.com", oauthConfig = null)
        assertEquals("https://x.com", e.serverUrl)
        assertEquals(0L, e.lastMtimeNs)
        assertTrue(e.pending401.isEmpty())
    }

    /** TC-MCP-020-a: _ProviderEntry (structural alignment class) also accepts defaults. */
    @Test
    fun `_ProviderEntry structural alignment compiles`() {
        val e = _ProviderEntry()
        assertEquals("", e.serverUrl)
        assertTrue(e.pending401.isEmpty())
    }
}
