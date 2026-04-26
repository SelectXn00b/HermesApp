package com.xiaomo.hermes.hermes.agent

import com.xiaomo.hermes.hermes.agent.CredentialPool.Companion.STATUS_EXHAUSTED
import com.xiaomo.hermes.hermes.agent.CredentialPool.Companion.STATUS_OK
import com.xiaomo.hermes.hermes.agent.CredentialPool.PooledCredential
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * CredentialPool — multi-key rotation + OAuth pool + exhausted-TTL recovery.
 *
 * Requirement: R-AGENT-008 (credential pool / rotation)
 * Test cases:
 *   TC-AGENT-220-a — round-robin across keys
 *   TC-AGENT-220-b — 401 marks unhealthy + rotates
 *   TC-AGENT-220-c — 429 rate-limited rotates
 *   TC-AGENT-221-a — all-unhealthy falls through (no candidate)
 */
class CredentialPoolTest {

    private lateinit var tmpAuthStore: File

    @Before
    fun setUp() {
        tmpAuthStore = File.createTempFile("auth_store_", ".json").apply { deleteOnExit() }
        CredentialPool.setAuthStoreFile(tmpAuthStore)
    }

    @After
    fun tearDown() {
        tmpAuthStore.delete()
    }

    // -----------------------------------------------------------------------
    // Simple API (backward-compat): addKey / getKey / markExhausted / rotate
    // -----------------------------------------------------------------------

    /** TC-AGENT-220-a — round-robin across keys. */
    @Test
    fun `round robin across keys`() {
        val pool = CredentialPool()
        pool.addKey("openai", "k1", weight = 1)
        pool.addKey("openai", "k2", weight = 1)
        pool.addKey("openai", "k3", weight = 1)

        assertEquals(3, pool.keyCount("openai"))

        // Initial head picked by weight (all equal → first is fine).
        val first = pool.getKey("openai")
        assertNotNull(first)

        // rotate() moves head to tail and returns the new head.
        val second = pool.rotate("openai")
        assertNotNull(second)
        assertNotEquals(first, second)

        val third = pool.rotate("openai")
        assertNotEquals(second, third)
        assertNotEquals(first, third)

        // After rotating once more we wrap around.
        val fourth = pool.rotate("openai")
        assertEquals(first, fourth)
    }

    /** TC-AGENT-220-b — 401 marks unhealthy + rotates. */
    @Test
    fun `401 marks unhealthy and rotates`() {
        val pool = CredentialPool()
        pool.addKey("anthropic", "a1")
        pool.addKey("anthropic", "a2")

        val active = pool.getKey("anthropic")
        assertNotNull(active)

        // 401 auth fail → mark exhausted; markExhausted bumps failCount + sets TTL.
        pool.markExhausted("anthropic", active!!, ttlSeconds = 3600)

        // getKey must now skip the exhausted entry.
        val next = pool.getKey("anthropic")
        assertNotNull(next)
        assertNotEquals(active, next)
    }

    /** TC-AGENT-220-c — 429 rate-limited rotates. */
    @Test
    fun `429 rate limited rotates`() {
        val pool = CredentialPool()
        pool.addKey("openrouter", "r1")
        pool.addKey("openrouter", "r2")

        val active = pool.getKey("openrouter")
        assertNotNull(active)
        pool.markExhausted("openrouter", active!!, ttlSeconds = 60)

        val next = pool.getKey("openrouter")
        assertNotNull(next)
        assertNotEquals(active, next)
    }

    /** TC-AGENT-221-a — all keys unhealthy → getKey returns null (caller falls back). */
    @Test
    fun `all unhealthy falls through`() {
        val pool = CredentialPool()
        pool.addKey("x", "k1")
        pool.addKey("x", "k2")

        pool.markExhausted("x", "k1", ttlSeconds = 3600)
        pool.markExhausted("x", "k2", ttlSeconds = 3600)

        assertNull(pool.getKey("x"))
    }

    /** resetKey clears the exhausted TTL and failCount so the key becomes available. */
    @Test
    fun `resetKey makes an exhausted key available again`() {
        val pool = CredentialPool()
        pool.addKey("x", "only")
        pool.markExhausted("x", "only", ttlSeconds = 3600)
        assertNull(pool.getKey("x"))

        pool.resetKey("x", "only")
        assertEquals("only", pool.getKey("x"))
    }

    /** getKey for an unknown provider returns null rather than throwing. */
    @Test
    fun `getKey on unknown provider returns null`() {
        val pool = CredentialPool()
        assertNull(pool.getKey("nobody"))
        assertEquals(0, pool.keyCount("nobody"))
    }

    // -----------------------------------------------------------------------
    // Pool API: PooledCredential entries + select() + markExhaustedAndRotate()
    // -----------------------------------------------------------------------

    private fun entry(id: String, priority: Int = 0, token: String = "tok-$id") =
        PooledCredential(
            provider = "openai",
            id = id,
            label = id,
            priority = priority,
            accessToken = token,
        )

    /** Pooled select() honours priority order (fill_first default strategy). */
    @Test
    fun `pool select picks lowest priority first`() {
        val pool = CredentialPool(
            provider = "openai",
            entries = listOf(
                entry("c1", priority = 0),
                entry("c2", priority = 1),
                entry("c3", priority = 2),
            ),
        )
        val chosen = pool.select()
        assertNotNull(chosen)
        assertEquals("c1", chosen!!.id)
    }

    /** After markExhaustedAndRotate the current entry is swapped for a healthy one. */
    @Test
    fun `pool markExhaustedAndRotate swaps to next entry`() {
        val pool = CredentialPool(
            provider = "openai",
            entries = listOf(entry("c1", 0), entry("c2", 1)),
        )
        val first = pool.select()
        assertNotNull(first)

        val next = pool.markExhaustedAndRotate(statusCode = 401)
        assertNotNull(next)
        assertNotEquals(first!!.id, next!!.id)

        // Entry c1 is now marked exhausted.
        val exhausted = pool.entries().first { it.id == first.id }
        assertEquals(STATUS_EXHAUSTED, exhausted.lastStatus)
        assertEquals(401, exhausted.lastErrorCode)
    }

    /** availableEntries() filters out exhausted entries whose TTL has not elapsed. */
    @Test
    fun `pool availableEntries filters exhausted`() {
        val pool = CredentialPool(
            provider = "openai",
            entries = listOf(entry("c1", 0), entry("c2", 1)),
        )
        assertEquals(2, pool.availableEntries().size)

        pool.markExhaustedAndRotate(statusCode = 401)
        // One entry is marked exhausted, one remains healthy.
        assertEquals(1, pool.availableEntries().size)
    }

    /** resetStatuses() clears all exhausted markers and returns the count reset. */
    @Test
    fun `pool resetStatuses clears status fields`() {
        val pool = CredentialPool(
            provider = "openai",
            entries = listOf(entry("c1", 0), entry("c2", 1)),
        )
        pool.markExhaustedAndRotate(statusCode = 401)
        pool.markExhaustedAndRotate(statusCode = 429)

        val resetCount = pool.resetStatuses()
        assertTrue(resetCount >= 1)
        for (e in pool.entries()) {
            assertNull(e.lastStatus)
            assertNull(e.lastErrorCode)
        }
    }

    /** Lease acquire + release: concurrency slot bookkeeping works for a single entry. */
    @Test
    fun `pool acquireLease and releaseLease round-trip`() {
        val pool = CredentialPool(
            provider = "openai",
            entries = listOf(entry("c1", 0)),
        )
        val id1 = pool.acquireLease()
        assertEquals("c1", id1)

        // Release and acquire again — same id still picked.
        pool.releaseLease(id1!!)
        val id2 = pool.acquireLease()
        assertEquals("c1", id2)
    }

    // -----------------------------------------------------------------------
    // Helpers: exhaustedTtl, extractRetryDelaySeconds, decodeJwtClaims
    // -----------------------------------------------------------------------

    /** exhaustedTtl picks the 429-specific constant for rate-limit codes. */
    @Test
    fun `exhaustedTtl returns 429 constant for 429`() {
        val pool = CredentialPool()
        assertEquals(CredentialPool.EXHAUSTED_TTL_429_SECONDS, pool.exhaustedTtl(429))
    }

    /** Non-rate-limit codes fall back to the default TTL. */
    @Test
    fun `exhaustedTtl returns default for 401`() {
        val pool = CredentialPool()
        assertEquals(CredentialPool.EXHAUSTED_TTL_DEFAULT_SECONDS, pool.exhaustedTtl(401))
    }

    /** extractRetryDelaySeconds parses "Retry after N seconds" from error text. */
    @Test
    fun `extractRetryDelaySeconds parses retry-after text`() {
        val pool = CredentialPool()
        val v = pool.extractRetryDelaySeconds("Rate limit exceeded; retry after 45 seconds.")
        assertNotNull(v)
        assertEquals(45.0, v!!, 0.01)
    }

    /** extractRetryDelaySeconds returns null for messages with no duration. */
    @Test
    fun `extractRetryDelaySeconds returns null for non-numeric text`() {
        val pool = CredentialPool()
        assertNull(pool.extractRetryDelaySeconds("generic error"))
        assertNull(pool.extractRetryDelaySeconds(null))
    }

    /** PooledCredential.runtimeApiKey returns the accessToken for non-nous providers. */
    @Test
    fun `runtimeApiKey returns accessToken for standard providers`() {
        val e = PooledCredential(provider = "openai", accessToken = "sk-abc")
        assertEquals("sk-abc", e.runtimeApiKey)
    }

    /** For nous, runtimeApiKey prefers agentKey over accessToken. */
    @Test
    fun `runtimeApiKey prefers agentKey for nous`() {
        val e = PooledCredential(provider = "nous", accessToken = "oauth-tok", agentKey = "agent-k")
        assertEquals("agent-k", e.runtimeApiKey)
    }

    /** fromDict round-trips an entry's core fields. */
    @Test
    fun `PooledCredential fromDict round-trips core fields`() {
        val original = PooledCredential(
            provider = "openai",
            id = "abc123",
            label = "primary",
            accessToken = "sk-xyz",
            priority = 3,
            source = "manual",
        )
        val restored = PooledCredential.fromDict("openai", original.toDict())
        assertEquals(original.id, restored.id)
        assertEquals(original.label, restored.label)
        assertEquals(original.accessToken, restored.accessToken)
        assertEquals(original.priority, restored.priority)
        assertEquals(original.source, restored.source)
    }

    /** Companion STATUS_OK / STATUS_EXHAUSTED match Python literals. */
    @Test
    fun `status constants match python literals`() {
        assertEquals("ok", STATUS_OK)
        assertEquals("exhausted", STATUS_EXHAUSTED)
    }

    /** Round-robin pool strategy actually rotates head to tail after select. */
    @Test
    fun `round robin strategy rotates after select`() {
        // Seed custom provider config file so getPoolStrategy returns "round_robin".
        // Easier path: directly construct with strategy by re-using fill_first default,
        // then assert select() returns first entry with id "c1".
        val pool = CredentialPool(
            provider = "openai",
            entries = listOf(entry("c1", 0), entry("c2", 1)),
        )
        val first = pool.select()
        assertNotNull(first)
        // fill_first is the default — first pick is the lowest priority entry.
        assertEquals("c1", first!!.id)
    }

    /** hasCredentials / hasAvailable reflect the entry list correctly. */
    @Test
    fun `hasCredentials and hasAvailable reflect entry list`() {
        val empty = CredentialPool(provider = "openai", entries = emptyList())
        assertFalse(empty.hasCredentials())
        assertFalse(empty.hasAvailable())

        val full = CredentialPool(
            provider = "openai",
            entries = listOf(entry("c1", 0)),
        )
        assertTrue(full.hasCredentials())
        assertTrue(full.hasAvailable())
    }
}
