package com.xiaomo.hermes.hermes.gateway

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Field

/**
 * Covers Hooks.kt pure-logic:
 *   HookEvent enum ordering (pipeline stage order is load-bearing)
 *   HookPipeline.run priority/event filtering + short-circuit on Halt
 *     + Replace chaining through ctx.text
 *   HookRegistry.loadedHooks / discoverAndLoad no-op / emit delegation
 *
 * Since HookPipeline has no public mutator for _hooks, tests either use
 * an empty pipeline (to prove nothing-to-do Continue) or reflect on the
 * private CopyOnWriteArrayList to register test hooks.
 */
class HooksTest {

    // ─── HookEvent ordering ───────────────────────────────────────────────

    @Test
    fun `HookEvent lifecycle stages declared in pipeline order`() {
        val names = HookEvent.values().map { it.name }
        // First 6 match the doc comment ordering.
        assertEquals(
            listOf(
                "PRE_VALIDATE", "POST_VALIDATE",
                "PRE_AGENT", "POST_AGENT",
                "PRE_SEND", "POST_SEND",
            ),
            names.take(6),
        )
        // Also assert full set includes startup/shutdown/session lifecycle.
        assertTrue(names.containsAll(listOf(
            "ON_START", "ON_STOP",
            "ON_PLATFORM_CONNECT", "ON_PLATFORM_DISCONNECT",
            "ON_SESSION_CREATE", "ON_SESSION_DESTROY",
        )))
    }

    // ─── HookPipeline: empty ──────────────────────────────────────────────

    @Test
    fun `empty pipeline reports isEmpty and size 0`() {
        val p = HookPipeline()
        assertTrue(p.isEmpty)
        assertEquals(0, p.size)
    }

    @Test
    fun `empty pipeline run returns Continue`() {
        val p = HookPipeline()
        val result = runBlocking {
            p.run(HookEvent.PRE_VALIDATE, HookContext(event = HookEvent.PRE_VALIDATE))
        }
        assertTrue(result is HookResult.Continue)
    }

    // ─── HookPipeline: convenience overload builds context ───────────────

    @Test
    fun `convenience run returns Continue for empty pipeline`() {
        val p = HookPipeline()
        val result = runBlocking {
            p.run(
                event = HookEvent.PRE_SEND,
                sessionKey = "s",
                text = "hello",
                platform = "telegram",
            )
        }
        assertTrue(result is HookResult.Continue)
    }

    // ─── HookPipeline: hook execution (reflect to inject) ────────────────

    @Test
    fun `hooks run only for matching events`() {
        val p = HookPipeline()
        val calls = mutableListOf<String>()

        val preValidate = HookEntry(
            name = "a", event = HookEvent.PRE_VALIDATE,
            handler = { calls += "a"; HookResult.Continue }
        )
        val postSend = HookEntry(
            name = "b", event = HookEvent.POST_SEND,
            handler = { calls += "b"; HookResult.Continue }
        )
        _injectHooks(p, listOf(preValidate, postSend))

        runBlocking {
            p.run(HookEvent.PRE_VALIDATE, HookContext(event = HookEvent.PRE_VALIDATE))
        }
        // Only the pre-validate hook fires.
        assertEquals(listOf("a"), calls)
    }

    @Test
    fun `hook Halt short-circuits pipeline`() {
        val p = HookPipeline()
        val calls = mutableListOf<String>()

        val first = HookEntry(
            name = "first", event = HookEvent.PRE_SEND,
            handler = { calls += "first"; HookResult.Halt("nope") }
        )
        val second = HookEntry(
            name = "second", event = HookEvent.PRE_SEND,
            handler = { calls += "second"; HookResult.Continue }
        )
        _injectHooks(p, listOf(first, second))

        val result = runBlocking {
            p.run(HookEvent.PRE_SEND, HookContext(event = HookEvent.PRE_SEND))
        }

        assertTrue(result is HookResult.Halt)
        assertEquals("nope", (result as HookResult.Halt).reason)
        assertEquals(listOf("first"), calls)
    }

    @Test
    fun `hook Replace propagates new text to next hook`() {
        val p = HookPipeline()
        val seenTexts = mutableListOf<String>()

        val first = HookEntry(
            name = "rewrite", event = HookEvent.PRE_AGENT,
            handler = { ctx ->
                seenTexts += ctx.text
                HookResult.Replace("rewritten")
            }
        )
        val second = HookEntry(
            name = "observe", event = HookEvent.PRE_AGENT,
            handler = { ctx ->
                seenTexts += ctx.text
                HookResult.Continue
            }
        )
        _injectHooks(p, listOf(first, second))

        runBlocking {
            p.run(HookEvent.PRE_AGENT, HookContext(event = HookEvent.PRE_AGENT, text = "orig"))
        }

        assertEquals(listOf("orig", "rewritten"), seenTexts)
    }

    // ─── HookRegistry ─────────────────────────────────────────────────────

    @Test
    fun `HookRegistry loadedHooks starts empty`() {
        val reg = HookRegistry()
        assertEquals(emptyList<String>(), reg.loadedHooks())
    }

    @Test
    fun `HookRegistry discoverAndLoad is a no-op on Android`() {
        val reg = HookRegistry()
        reg.discoverAndLoad()
        assertEquals(emptyList<String>(), reg.loadedHooks())
    }

    @Test
    fun `HookRegistry emit delegates to pipeline`() {
        val p = HookPipeline()
        val reg = HookRegistry(p)
        val result = runBlocking {
            reg.emit(HookEvent.PRE_VALIDATE, HookContext(event = HookEvent.PRE_VALIDATE))
        }
        assertTrue(result is HookResult.Continue)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun _injectHooks(p: HookPipeline, entries: List<HookEntry>) {
        val field: Field = HookPipeline::class.java.getDeclaredField("_hooks")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(p) as java.util.concurrent.CopyOnWriteArrayList<HookEntry>
        list.addAll(entries)
    }
}
