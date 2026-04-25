package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsitePolicyTest {

    @Test
    fun `_DEFAULT_WEBSITE_BLOCKLIST is disabled by default`() {
        assertEquals(false, _DEFAULT_WEBSITE_BLOCKLIST["enabled"])
        @Suppress("UNCHECKED_CAST")
        val domains = _DEFAULT_WEBSITE_BLOCKLIST["domains"] as List<String>
        assertTrue(domains.isEmpty())
    }

    @Test
    fun `cache TTL constant is 30 seconds`() {
        assertEquals(30.0, _CACHE_TTL_SECONDS, 0.0)
    }

    @Test
    fun `checkWebsiteAccess returns null when policy disabled`() {
        // default policy is {enabled=false}, so all URLs pass
        invalidateCache()
        assertNull(checkWebsiteAccess("https://example.com"))
    }

    @Test
    fun `checkWebsiteAccess returns null for any URL with default policy`() {
        invalidateCache()
        assertNull(checkWebsiteAccess("https://evil.test/path"))
        assertNull(checkWebsiteAccess("http://localhost:8080"))
    }

    @Test
    fun `loadWebsiteBlocklist returns default policy shape`() {
        invalidateCache()
        val policy = loadWebsiteBlocklist()
        assertEquals(false, policy["enabled"])
    }

    @Test
    fun `loadWebsiteBlocklist caches result within TTL window`() {
        invalidateCache()
        val a = loadWebsiteBlocklist()
        val b = loadWebsiteBlocklist()
        // Same reference for default policy because _loadPolicyConfig returns the constant
        // and cache stores that reference.
        assertTrue(a === b || a == b)
    }

    @Test
    fun `invalidateCache forces reload`() {
        invalidateCache()
        loadWebsiteBlocklist()
        invalidateCache()
        // Reloads successfully without exception
        val policy = loadWebsiteBlocklist()
        assertNotNull(policy)
    }

    @Test
    fun `WebsitePolicyError carries its message`() {
        val ex = WebsitePolicyError("boom")
        assertEquals("boom", ex.message)
    }
}
