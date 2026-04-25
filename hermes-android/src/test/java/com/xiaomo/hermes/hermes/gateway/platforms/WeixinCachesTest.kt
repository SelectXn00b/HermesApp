package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Covers ContextTokenStore and TypingTicketCache from weixin.py.
 */
class WeixinCachesTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = File.createTempFile("weixin-cache", "").also {
            it.delete()
            it.mkdirs()
        }
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun `ContextTokenStore set-get round-trip`() {
        val store = ContextTokenStore(tmp.absolutePath)
        store.set("bot_1", "user_a", "token_a")
        store.set("bot_1", "user_b", "token_b")
        assertEquals("token_a", store.get("bot_1", "user_a"))
        assertEquals("token_b", store.get("bot_1", "user_b"))
    }

    @Test
    fun `ContextTokenStore get returns null when missing`() {
        val store = ContextTokenStore(tmp.absolutePath)
        assertNull(store.get("bot_1", "nobody"))
    }

    @Test
    fun `ContextTokenStore persists across instances`() {
        val first = ContextTokenStore(tmp.absolutePath)
        first.set("bot_x", "user_1", "persisted_token")

        val second = ContextTokenStore(tmp.absolutePath)
        assertNull(second.get("bot_x", "user_1"))

        second.restore("bot_x")
        assertEquals("persisted_token", second.get("bot_x", "user_1"))
    }

    @Test
    fun `ContextTokenStore restore handles missing file gracefully`() {
        val store = ContextTokenStore(tmp.absolutePath)
        store.restore("never_persisted")
        assertNull(store.get("never_persisted", "anyone"))
    }

    @Test
    fun `ContextTokenStore restore handles corrupt file gracefully`() {
        val dir = _accountDir(tmp.absolutePath)
        File(dir, "corrupt.context-tokens.json").writeText("{not json", Charsets.UTF_8)

        val store = ContextTokenStore(tmp.absolutePath)
        store.restore("corrupt")
        assertNull(store.get("corrupt", "anyone"))
    }

    @Test
    fun `ContextTokenStore restore skips empty token strings`() {
        val dir = _accountDir(tmp.absolutePath)
        File(dir, "bot_y.context-tokens.json").writeText(
            """{"user_x":"","user_y":"valid"}""",
            Charsets.UTF_8,
        )
        val store = ContextTokenStore(tmp.absolutePath)
        store.restore("bot_y")
        assertNull(store.get("bot_y", "user_x"))
        assertEquals("valid", store.get("bot_y", "user_y"))
    }

    @Test
    fun `ContextTokenStore persist keys remove account prefix`() {
        val store = ContextTokenStore(tmp.absolutePath)
        store.set("acct", "user_1", "token_1")

        // Read the persisted file back directly to verify format.
        val path = File(_accountDir(tmp.absolutePath), "acct.context-tokens.json")
        val content = path.readText(Charsets.UTF_8)
        // The key under the account prefix should be stripped to just "user_1"
        assertTrue("expected user_1 key in: $content", content.contains("user_1"))
        assertTrue(content.contains("token_1"))
        // Should NOT contain the "acct:user_1" composite key
        assertFalse("composite key should be stripped: $content", content.contains("acct:user_1"))
    }

    @Test
    fun `TypingTicketCache get returns set value within TTL`() {
        val cache = TypingTicketCache(ttlSeconds = 100.0)
        cache.set("user_1", "ticket_xyz")
        assertEquals("ticket_xyz", cache.get("user_1"))
    }

    @Test
    fun `TypingTicketCache get returns null when empty`() {
        val cache = TypingTicketCache()
        assertNull(cache.get("nobody"))
    }

    @Test
    fun `TypingTicketCache expires after TTL`() {
        // Use a subclass to force time to advance past the TTL.
        val cache = object : TypingTicketCache(ttlSeconds = 1.0) {
            var fakeNow: Double = 100.0
            override fun _now(): Double = fakeNow
        }
        cache.set("user_1", "ticket")
        assertEquals("ticket", cache.get("user_1"))
        cache.fakeNow = 200.0
        assertNull(cache.get("user_1"))
    }

    @Test
    fun `TypingTicketCache set overwrites stale value`() {
        val cache = TypingTicketCache()
        cache.set("user_1", "v1")
        cache.set("user_1", "v2")
        assertEquals("v2", cache.get("user_1"))
    }
}
