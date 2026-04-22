package com.xiaomo.hermes.hermes

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [HermesAgentLoop.beforeNextTurn] — the hook that
 * [EnhancedAIService.runViaHermes] uses to enforce the
 * `onTokenLimitExceeded` abort after a turn's context grows past the
 * configured usage ratio (§3.6 of the Operit-Hermes integration matrix).
 *
 * Rather than exercise the full Operit wiring (which depends on an
 * AIService + DataStore + token estimator), these tests lock in the
 * agent-loop-level contract that Operit's abort path relies on:
 *
 *   1. A `beforeNextTurn` returning false at the configured turn boundary
 *      must terminate the loop with `finishedNaturally=false` and
 *      `turnsUsed == turn` — no further LLM call, no tool dispatch.
 *   2. A throwing hook is treated as "continue" per the catch in the loop,
 *      so a misbehaving hook never stalls the conversation.
 *   3. The hook is invoked with the zero-based turn index starting at 0
 *      on every iteration, before the chat completion call — this is what
 *      lets Operit gate the token check on `turn > 0`.
 */
class HermesAgentLoopBeforeNextTurnTest {

    private class FakeDispatcher : ToolDispatcher {
        var dispatchedCount = 0
        override suspend fun dispatch(
            toolName: String,
            args: Map<String, Any?>,
            taskId: String,
            userTask: String?
        ): String {
            dispatchedCount += 1
            return """{"ok":true}"""
        }
    }

    /**
     * A server whose nth response comes from the nth slot in [responses];
     * if more turns happen than responses, returns a natural-stop message.
     */
    private class ScriptedServer(
        private val responses: List<ChatCompletionResponse>
    ) : ChatCompletionServer {
        var calls = 0
        override suspend fun chatCompletion(
            messages: List<Map<String, Any?>>,
            tools: List<Map<String, Any?>>?,
            temperature: Double,
            maxTokens: Int?,
            extraBody: Map<String, Any?>?
        ): ChatCompletionResponse? {
            val r = responses.getOrNull(calls) ?: ChatCompletionResponse(
                choices = listOf(Choice(AssistantMessage(content = "done", toolCalls = null)))
            )
            calls += 1
            return r
        }
    }

    private fun naturalStop(text: String) = ChatCompletionResponse(
        choices = listOf(Choice(AssistantMessage(content = text, toolCalls = null)))
    )

    @Test fun beforeNextTurn_returnsFalseOnTurn0_abortsBeforeFirstCall() = runBlocking {
        val server = ScriptedServer(responses = listOf(naturalStop("hi")))
        val dispatcher = FakeDispatcher()
        val calls = mutableListOf<Int>()
        val loop = HermesAgentLoop(
            server = server,
            toolDispatcher = dispatcher,
            maxTurns = 5,
            beforeNextTurn = { turn, _ -> calls.add(turn); false }
        )

        val result = loop.run(mutableListOf(mapOf("role" to "user", "content" to "hi")))

        assertEquals(listOf(0), calls)
        assertEquals(0, server.calls)
        assertEquals(0, dispatcher.dispatchedCount)
        assertFalse("aborted hooks never finish naturally", result.finishedNaturally)
        assertEquals(0, result.turnsUsed)
    }

    @Test fun beforeNextTurn_returnsFalseAfterTurn0_abortsBeforeNthCall() = runBlocking {
        // Simulate a tool-using first turn so we reach turn 1 naturally, then
        // force-abort at turn 1 — mirroring the Operit `turn > 0` gate that
        // fires the actual onTokenLimitExceeded callback.
        val server = ScriptedServer(
            responses = listOf(
                ChatCompletionResponse(
                    choices = listOf(
                        Choice(
                            AssistantMessage(
                                content = "",
                                toolCalls = listOf(
                                    ToolCall(
                                        id = "call_1",
                                        function = ToolCallFunction(name = "noop", arguments = "{}")
                                    )
                                )
                            )
                        )
                    )
                ),
                naturalStop("should not be reached")
            )
        )
        val dispatcher = FakeDispatcher()
        val observedTurns = mutableListOf<Int>()
        val invocations = AtomicInteger(0)
        val loop = HermesAgentLoop(
            server = server,
            validToolNames = setOf("noop"),
            toolDispatcher = dispatcher,
            maxTurns = 5,
            beforeNextTurn = { turn, _ ->
                observedTurns.add(turn)
                // Continue on turn 0, abort on turn 1 — matching Operit's
                // `if (turn > 0 && maxTokens > 0) …` guard.
                invocations.incrementAndGet()
                turn == 0
            }
        )

        val result = loop.run(mutableListOf(mapOf("role" to "user", "content" to "go")))

        assertEquals(listOf(0, 1), observedTurns)
        assertEquals("only the first turn's LLM call happened", 1, server.calls)
        assertEquals("the first tool call was dispatched", 1, dispatcher.dispatchedCount)
        assertFalse(result.finishedNaturally)
        assertEquals("turnsUsed reflects the turn at which abort fired", 1, result.turnsUsed)
    }

    @Test fun beforeNextTurn_throwing_isCaughtAndTreatedAsContinue() = runBlocking {
        val server = ScriptedServer(responses = listOf(naturalStop("ok")))
        val dispatcher = FakeDispatcher()
        val throws = AtomicInteger(0)
        val loop = HermesAgentLoop(
            server = server,
            toolDispatcher = dispatcher,
            maxTurns = 5,
            beforeNextTurn = { _, _ ->
                throws.incrementAndGet()
                error("hook boom")
            }
        )

        val result = loop.run(mutableListOf(mapOf("role" to "user", "content" to "hi")))

        assertTrue("hook ran at least once", throws.get() >= 1)
        assertEquals("throwing hook doesn't abort", 1, server.calls)
        assertTrue("loop finished naturally on content-only response", result.finishedNaturally)
    }

    @Test fun beforeNextTurn_returningTrue_proceedsNormally() = runBlocking {
        val server = ScriptedServer(responses = listOf(naturalStop("done")))
        val dispatcher = FakeDispatcher()
        val calls = mutableListOf<Int>()
        val loop = HermesAgentLoop(
            server = server,
            toolDispatcher = dispatcher,
            maxTurns = 5,
            beforeNextTurn = { turn, _ -> calls.add(turn); true }
        )

        val result = loop.run(mutableListOf(mapOf("role" to "user", "content" to "hi")))

        assertEquals(listOf(0), calls)
        assertEquals(1, server.calls)
        assertTrue(result.finishedNaturally)
    }

    @Test fun beforeNextTurn_receivesMessagesSnapshot() = runBlocking {
        val server = ScriptedServer(responses = listOf(naturalStop("done")))
        val dispatcher = FakeDispatcher()
        var seenAtTurn0: List<Map<String, Any?>>? = null
        val loop = HermesAgentLoop(
            server = server,
            toolDispatcher = dispatcher,
            beforeNextTurn = { turn, msgs ->
                if (turn == 0) seenAtTurn0 = msgs
                true
            }
        )

        loop.run(mutableListOf(
            mapOf("role" to "system", "content" to "sys"),
            mapOf("role" to "user", "content" to "hello")
        ))

        assertEquals(2, seenAtTurn0!!.size)
        assertEquals("system", seenAtTurn0!![0]["role"])
        assertEquals("hello", seenAtTurn0!![1]["content"])
    }

    @Test fun noBeforeNextTurn_loopProceedsWithoutHook() = runBlocking {
        val server = ScriptedServer(responses = listOf(naturalStop("ok")))
        val dispatcher = FakeDispatcher()
        val loop = HermesAgentLoop(
            server = server,
            toolDispatcher = dispatcher,
            // beforeNextTurn intentionally omitted
        )
        val result = loop.run(mutableListOf(mapOf("role" to "user", "content" to "hi")))
        assertEquals(1, server.calls)
        assertTrue(result.finishedNaturally)
    }
}
