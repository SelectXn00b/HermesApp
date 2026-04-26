package com.xiaomo.hermes.hermes.plugins.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-SKILL-020-a — `MemoryProvider` interface must expose the same surface as
 * the Python abstract base in `hermes/plugins/memory/__init__.py`:
 *
 *   - `providerName` property
 *   - `suspend fun initialize(config: Map<String, Any>)`
 *   - `suspend fun store(content, metadata = {}): String`
 *   - `suspend fun retrieve(query, limit = 10, threshold = 0.7): List<MemoryItem>`
 *   - `suspend fun delete(memoryId): Boolean`
 *   - `suspend fun list(limit = 100, offset = 0): List<MemoryItem>`
 *   - `suspend fun close()`
 *
 * The original TC was written assuming `save/load/delete` (3 methods) — that
 * was wrong; the Python upstream and the Kotlin port both expose 6 methods
 * plus a `provider_name` / `providerName` property. Reflection is used here
 * to keep the assertion unambiguous even if a future rename sneaks in.
 */
class MemoryProviderTest {

    @Test
    fun `interface surface`() {
        val methods = MemoryProvider::class.java.declaredMethods
        val names = methods.map { it.name }.toSet()

        // providerName exposed as a Kotlin property → getProviderName getter
        assertTrue("providerName getter must exist", names.any { it == "getProviderName" })

        // All 6 suspend methods expected by the Python abstract base.
        val required = listOf("initialize", "store", "retrieve", "delete", "list", "close")
        for (name in required) {
            assertTrue(
                "MemoryProvider must declare suspend fun $name (missing from $names)",
                name in names)
        }
    }

    @Test
    fun `memory item default fields`() {
        // MemoryItem must carry id + content + optional metadata/score/timestamps.
        val item = MemoryItem(id = "m1", content = "hello")
        assertEquals("m1", item.id)
        assertEquals("hello", item.content)
        assertTrue("metadata defaults to empty map", item.metadata.isEmpty())
        // score null by default (only populated on retrieval)
        assertEquals(null, item.score)
        // createdAt/updatedAt populated with now(); just assert > 0.
        assertTrue("createdAt must be positive", item.createdAt > 0L)
        assertTrue("updatedAt must be positive", item.updatedAt > 0L)
    }

    @Test
    fun `retrieve default params match Python`() {
        val retrieveMethod = MemoryProvider::class.java.declaredMethods
            .firstOrNull { it.name == "retrieve" }
        assertNotNull("retrieve method must exist", retrieveMethod)
        // Parameters: query, limit, threshold, + Continuation (suspend) → 4.
        assertEquals(
            "retrieve must take (query, limit, threshold) + Continuation",
            4,
            retrieveMethod!!.parameterCount)
    }
}
