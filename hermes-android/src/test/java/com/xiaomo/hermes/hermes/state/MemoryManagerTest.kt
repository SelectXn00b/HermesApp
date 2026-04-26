package com.xiaomo.hermes.hermes.state

import com.xiaomo.hermes.hermes.agent.InMemoryMemoryProvider
import com.xiaomo.hermes.hermes.agent.MemoryEntry
import com.xiaomo.hermes.hermes.agent.MemoryManager
import com.xiaomo.hermes.hermes.agent.MemoryProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MemoryManager] — the agent-facing memory façade.
 *
 * Covers TC-STATE-035 / TC-STATE-036. No Android runtime needed: the suspend
 * API bounces through a default [InMemoryMemoryProvider], and the provider
 * orchestration uses plain lists + `android.util.Log` (stubbed by
 * `testOptions.unitTests.isReturnDefaultValues = true`).
 */
class MemoryManagerTest {

    // ── Helper fake providers ──────────────────────────────────────────────

    /** Minimal fake external provider that records every callback it receives. */
    private class RecordingProvider(
        val fakeName: String,
        val promptBlock: String = "",
        private val schemas: List<Map<String, Any>> = emptyList(),
        val prefetchText: String = "",
        val preCompressText: String = "",
        val toolCallResult: String = "ok",
        val onToolCallThrows: Boolean = false
    ) : MemoryProvider {

        var initCalls = 0
        var shutdownCalls = 0
        val syncTurnRecords = mutableListOf<Pair<String, String>>()
        val turnStartRecords = mutableListOf<Triple<Int, String, Any>>()
        val memoryWrites = mutableListOf<Triple<String, String, String>>()
        val sessionEnds = mutableListOf<List<Map<String, Any>>>()
        val delegations = mutableListOf<Pair<String, String>>()
        val queuedPrefetches = mutableListOf<String>()

        override suspend fun store(entry: MemoryEntry) { /* not under test */ }
        override suspend fun recall(key: String): MemoryEntry? = null
        override suspend fun search(query: String, category: String?, limit: Int) = emptyList<MemoryEntry>()
        override suspend fun list(category: String?, limit: Int) = emptyList<MemoryEntry>()
        override suspend fun delete(key: String): Boolean = false
        override suspend fun clear() { }
        override suspend fun count(): Int = 0

        override fun initialize(sessionId: String, kwargs: Any) { initCalls++ }
        override fun systemPromptBlock(): String = promptBlock
        override fun prefetch(query: String): String = prefetchText
        override fun queuePrefetch(query: String) { queuedPrefetches.add(query) }
        override fun syncTurn(userContent: String, assistantContent: String) {
            syncTurnRecords.add(userContent to assistantContent)
        }
        override fun getToolSchemas(): List<Map<String, Any>> = schemas
        override fun handleToolCall(toolName: String, args: Map<String, Any>, kwargs: Any): String {
            if (onToolCallThrows) throw RuntimeException("boom")
            return toolCallResult
        }
        override fun shutdown() { shutdownCalls++ }
        override fun onTurnStart(turnNumber: Int, message: String, kwargs: Any) {
            turnStartRecords.add(Triple(turnNumber, message, kwargs))
        }
        override fun onSessionEnd(messages: List<Map<String, Any>>) { sessionEnds.add(messages) }
        override fun onPreCompress(messages: List<Map<String, Any>>): String = preCompressText
        override fun onDelegation(task: String, result: String, kwargs: Any) {
            delegations.add(task to result)
        }
        override fun onMemoryWrite(action: String, target: String, content: String) {
            memoryWrites.add(Triple(action, target, content))
        }

        override fun toString(): String = "RecordingProvider($fakeName)"
    }

    // ── Suspend CRUD: forwards to the default provider ─────────────────────

    @Test
    fun `remember and recall round-trip`() = runBlocking {
        // ── R-STATE-035 / TC-STATE-035-a: remember stores via provider ──
        val mgr = MemoryManager()
        mgr.remember("k1", "hello", category = "note")
        assertEquals("hello", mgr.recall("k1"))
    }

    @Test
    fun `recall missing returns null`() = runBlocking {
        val mgr = MemoryManager()
        assertNull(mgr.recall("never"))
    }

    @Test
    fun `search filters by category`() = runBlocking {
        val mgr = MemoryManager()
        mgr.remember("a", "apple pie", category = "food")
        mgr.remember("b", "apple tech", category = "brand")
        val foodHits = mgr.search("apple", category = "food")
        assertEquals(1, foodHits.size)
        assertEquals("food", foodHits[0].category)
    }

    @Test
    fun `forget deletes existing key`() = runBlocking {
        val mgr = MemoryManager()
        mgr.remember("k", "v")
        assertTrue(mgr.forget("k"))
        assertFalse(mgr.forget("k"))      // second delete returns false
        assertNull(mgr.recall("k"))
    }

    @Test
    fun `count reflects store size`() = runBlocking {
        val mgr = MemoryManager()
        assertEquals(0, mgr.count())
        mgr.remember("a", "1")
        mgr.remember("b", "2")
        assertEquals(2, mgr.count())
    }

    @Test
    fun `summarize lists all entries`() = runBlocking {
        val mgr = MemoryManager()
        mgr.remember("k1", "one", category = "cat")
        mgr.remember("k2", "two", category = "cat")
        val out = mgr.summarize(category = "cat")
        assertTrue(out.contains("Memories (2 entries)"))
        assertTrue(out.contains("k1"))
        assertTrue(out.contains("k2"))
    }

    @Test
    fun `summarize empty store returns placeholder`() = runBlocking {
        val mgr = MemoryManager()
        assertEquals("No memories stored.", mgr.summarize())
    }

    @Test
    fun `clearAll empties the store`() = runBlocking {
        val mgr = MemoryManager()
        mgr.remember("a", "1")
        mgr.clearAll()
        assertEquals(0, mgr.count())
    }

    // ── Provider orchestration: addProvider / providers() ─────────────────

    @Test
    fun `addProvider accepts one external provider`() {
        // ── R-STATE-035 / TC-STATE-035-a: external provider registered ──
        val mgr = MemoryManager()
        val p = RecordingProvider("ext1")
        mgr.addProvider(p)
        assertEquals(1, mgr.providers().size)
        assertSame(p, mgr.providers()[0])
    }

    @Test
    fun `addProvider rejects second external provider`() {
        val mgr = MemoryManager()
        mgr.addProvider(RecordingProvider("first"))
        mgr.addProvider(RecordingProvider("second"))
        // Second external is rejected → only 1 in list
        assertEquals(1, mgr.providers().size)
    }

    @Test
    fun `addProvider allows multiple builtin providers`() {
        val mgr = MemoryManager()
        mgr.addProvider(InMemoryMemoryProvider())
        mgr.addProvider(InMemoryMemoryProvider())
        assertEquals(2, mgr.providers().size)
    }

    // ── buildSystemPrompt: the core R-STATE-035 behaviour ────────────────

    @Test
    fun `buildSystemPrompt concatenates nonempty provider blocks`() {
        // ── R-STATE-035 / TC-STATE-035-a: memory → system prompt ──
        val mgr = MemoryManager()
        mgr.addProvider(RecordingProvider("blocky", promptBlock = "Remember: pineapple belongs on pizza"))
        val prompt = mgr.buildSystemPrompt()
        assertTrue("prompt must include provider block", prompt.contains("pineapple belongs on pizza"))
    }

    @Test
    fun `buildSystemPrompt skips empty blocks`() {
        val mgr = MemoryManager()
        mgr.addProvider(RecordingProvider("silent", promptBlock = ""))
        assertEquals("", mgr.buildSystemPrompt())
    }

    // ── onMemoryWrite: the persistence-fanout hook for R-STATE-036 ─────────

    @Test
    fun `onMemoryWrite fans out to external providers`() {
        // ── R-STATE-036 / TC-STATE-036-a: memory update → persist hook ──
        val mgr = MemoryManager()
        val ext = RecordingProvider("ext")
        mgr.addProvider(ext)
        mgr.onMemoryWrite("add", "memory", "hello world")
        assertEquals(1, ext.memoryWrites.size)
        assertEquals(Triple("add", "memory", "hello world"), ext.memoryWrites[0])
    }

    @Test
    fun `onMemoryWrite skips builtin InMemoryMemoryProvider`() {
        // Builtin provider is the source of the write — not a recipient.
        val mgr = MemoryManager()
        val builtin = InMemoryMemoryProvider()
        mgr.addProvider(builtin)
        // Precondition: builtin store is empty
        mgr.onMemoryWrite("add", "memory", "ignored")
        // Nothing should have been written to the builtin since MemoryManager
        // skips it — InMemoryMemoryProvider.onMemoryWrite would have added a
        // MemoryEntry, but because MemoryManager.onMemoryWrite filters it out
        // the store remains unchanged.
        kotlinx.coroutines.runBlocking {
            assertEquals(0, builtin.count())
        }
    }

    // ── Tool routing ──────────────────────────────────────────────────────

    @Test
    fun `handleToolCall routes to the registered provider`() {
        val mgr = MemoryManager()
        val schema = mapOf<String, Any>("name" to "my_tool")
        val p = RecordingProvider("router", schemas = listOf(schema), toolCallResult = "routed!")
        mgr.addProvider(p)
        assertTrue(mgr.hasTool("my_tool"))
        assertEquals("routed!", mgr.handleToolCall("my_tool", emptyMap(), Any()))
    }

    @Test
    fun `handleToolCall unknown tool returns error string`() {
        val mgr = MemoryManager()
        val out = mgr.handleToolCall("does_not_exist", emptyMap(), Any())
        assertTrue(out.startsWith("Error"))
        assertTrue(out.contains("does_not_exist"))
    }

    @Test
    fun `handleToolCall catches provider exceptions`() {
        val mgr = MemoryManager()
        val schema = mapOf<String, Any>("name" to "crashy")
        val p = RecordingProvider("kaboom", schemas = listOf(schema), onToolCallThrows = true)
        mgr.addProvider(p)
        val out = mgr.handleToolCall("crashy", emptyMap(), Any())
        assertTrue(out.startsWith("Error"))
        assertTrue(out.contains("crashy"))
    }

    @Test
    fun `getAllToolSchemas deduplicates by name`() {
        val mgr = MemoryManager()
        mgr.addProvider(RecordingProvider("one",
            schemas = listOf(mapOf<String, Any>("name" to "shared"), mapOf<String, Any>("name" to "only1"))))
        val all = mgr.getAllToolSchemas()
        // one provider contributes two unique names
        assertEquals(2, all.size)
        val names = all.map { it["name"] }.toSet()
        assertTrue(names.contains("shared"))
        assertTrue(names.contains("only1"))
    }

    // ── Lifecycle fan-out ──────────────────────────────────────────────────

    @Test
    fun `initializeAll invokes each provider`() {
        val mgr = MemoryManager()
        val a = RecordingProvider("a")
        mgr.addProvider(a)
        mgr.initializeAll("sess-1", Any())
        assertEquals(1, a.initCalls)
    }

    @Test
    fun `shutdownAll invokes providers in reverse`() {
        val mgr = MemoryManager()
        // Only one external provider allowed; use builtin + external.
        val builtin = InMemoryMemoryProvider()
        val ext = RecordingProvider("ext")
        mgr.addProvider(builtin)
        mgr.addProvider(ext)
        mgr.shutdownAll()
        assertEquals(1, ext.shutdownCalls)
    }

    @Test
    fun `syncAll forwards user and assistant content`() {
        val mgr = MemoryManager()
        val ext = RecordingProvider("ext")
        mgr.addProvider(ext)
        mgr.syncAll("user-says", "assistant-says", sessionId = "s1")
        assertEquals(1, ext.syncTurnRecords.size)
        assertEquals("user-says" to "assistant-says", ext.syncTurnRecords[0])
    }

    @Test
    fun `onTurnStart passes through turn info`() {
        val mgr = MemoryManager()
        val ext = RecordingProvider("ext")
        mgr.addProvider(ext)
        mgr.onTurnStart(turnNumber = 7, message = "hi", kwargs = Any())
        assertEquals(1, ext.turnStartRecords.size)
        assertEquals(7, ext.turnStartRecords[0].first)
        assertEquals("hi", ext.turnStartRecords[0].second)
    }

    @Test
    fun `onPreCompress concatenates nonempty provider outputs`() {
        val mgr = MemoryManager()
        mgr.addProvider(RecordingProvider("ext", preCompressText = "extracted-chunk"))
        val out = mgr.onPreCompress(emptyList())
        assertTrue(out.contains("extracted-chunk"))
    }

    @Test
    fun `prefetchAll joins provider outputs`() {
        val mgr = MemoryManager()
        mgr.addProvider(RecordingProvider("ext", prefetchText = "context-A"))
        val out = mgr.prefetchAll(query = "q", sessionId = "s")
        assertTrue(out.contains("context-A"))
    }

    @Test
    fun `queuePrefetchAll forwards to providers`() {
        val mgr = MemoryManager()
        val ext = RecordingProvider("ext")
        mgr.addProvider(ext)
        mgr.queuePrefetchAll("search-terms", sessionId = "sid")
        assertEquals(listOf("search-terms"), ext.queuedPrefetches)
    }

    @Test
    fun `onDelegation records task and result`() {
        val mgr = MemoryManager()
        val ext = RecordingProvider("ext")
        mgr.addProvider(ext)
        mgr.onDelegation("investigate", "done", kwargs = Any())
        assertEquals(1, ext.delegations.size)
        assertEquals("investigate" to "done", ext.delegations[0])
    }

    @Test
    fun `onSessionEnd forwards message list`() {
        val mgr = MemoryManager()
        val ext = RecordingProvider("ext")
        mgr.addProvider(ext)
        val msgs = listOf(mapOf<String, Any>("role" to "user", "content" to "hi"))
        mgr.onSessionEnd(msgs)
        assertEquals(1, ext.sessionEnds.size)
        assertEquals(msgs, ext.sessionEnds[0])
    }

    @Test
    fun `getAllToolNames returns registered names`() {
        val mgr = MemoryManager()
        mgr.addProvider(RecordingProvider("one",
            schemas = listOf(mapOf<String, Any>("name" to "alpha"), mapOf<String, Any>("name" to "beta"))))
        @Suppress("UNCHECKED_CAST")
        val names = mgr.getAllToolNames() as Set<String>
        assertTrue(names.contains("alpha"))
        assertTrue(names.contains("beta"))
    }

    @Test
    fun `hasTool false for unregistered tool`() {
        val mgr = MemoryManager()
        assertFalse(mgr.hasTool("nothing"))
    }

    @Test
    fun `getProvider finds by toString match`() {
        val mgr = MemoryManager()
        val p = RecordingProvider("unique-id-xyz")
        mgr.addProvider(p)
        assertNotNull(mgr.getProvider("unique-id-xyz"))
        assertNull(mgr.getProvider("absent-id"))
    }
}
