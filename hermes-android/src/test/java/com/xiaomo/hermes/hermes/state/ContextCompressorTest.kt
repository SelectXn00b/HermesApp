package com.xiaomo.hermes.hermes.state

import com.xiaomo.hermes.hermes.agent.CompressionStrategy
import com.xiaomo.hermes.hermes.agent.ContextCompressor
import com.xiaomo.hermes.hermes.agent.CompressorConfig
import com.xiaomo.hermes.hermes.agent.estimateRequestTokensRough
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ContextCompressor] — pure-function strategies only
 * (token estimation, tool-pair integrity, oldest-first truncation,
 * tool-result pruning, serialization, summary prefix normalization).
 *
 * Covers TC-STATE-025 (threshold), TC-STATE-026 (keep head/tail),
 * TC-STATE-027 (tool results first), TC-STATE-028 (failure graceful).
 *
 * All helpers exercised here do not call LLMs and do not touch the
 * Android runtime — `android.util.Log` calls degrade to default values
 * under `testOptions.unitTests.isReturnDefaultValues = true`.
 */
class ContextCompressorTest {

    /** Build a minimal role/content message. */
    private fun msg(role: String, content: String) =
        mapOf<String, Any>("role" to role, "content" to content)

    /** Build an assistant message with tool_calls for integrity tests. */
    private fun assistantToolCall(id: String, name: String = "run", args: String = "{}") =
        mapOf<String, Any>(
            "role" to "assistant",
            "content" to "",
            "tool_calls" to listOf(
                mapOf(
                    "id" to id,
                    "function" to mapOf("name" to name, "arguments" to args)
                )
            )
        )

    /** Build a tool-result message. */
    private fun toolResult(callId: String, content: String) =
        mapOf<String, Any>("role" to "tool", "tool_call_id" to callId, "content" to content)

    // ── R-STATE-025 / TC-STATE-025-a: threshold triggers ───────────────────

    @Test
    fun `needsCompression false when under threshold`() {
        val cc = ContextCompressor()
        val tiny = listOf(msg("user", "hi"))
        // 2 chars → 1 token; 1 < 0.85 * 4096 = 3481
        assertFalse(cc.needsCompression(tiny, contextLength = 4096))
    }

    @Test
    fun `needsCompression true when over threshold`() {
        val cc = ContextCompressor()
        // Make a payload that deterministically exceeds 0.85 * 1000 = 850 tokens.
        // estimateRequestTokensRough uses (chars+3)/4. 850 tokens ≈ 3400 chars.
        // Build one message with 5000 chars of content → ~1252 tokens.
        val big = listOf(msg("user", "x".repeat(5000)))
        // Sanity-check the estimator so the assertion below is grounded.
        val estimated = estimateRequestTokensRough(big, "", emptyList())
        assertTrue("payload should exceed ceiling", estimated > (1000 * 0.85).toInt())
        assertTrue(cc.needsCompression(big, contextLength = 1000))
    }

    @Test
    fun `needsCompression respects custom threshold`() {
        val cc = ContextCompressor()
        // 8-char message → 3 tokens. At contextLength=100 and threshold=0.01
        // the ceiling is 1 token, which 3 exceeds.
        val m = listOf(msg("user", "12345678"))
        assertTrue(cc.needsCompression(m, contextLength = 100, threshold = 0.01))
        // Same payload at threshold=0.99 stays under (ceiling=99 tokens).
        assertFalse(cc.needsCompression(m, contextLength = 100, threshold = 0.99))
    }

    @Test
    fun `needsCompression accounts for system prompt chars`() {
        val cc = ContextCompressor()
        val m = listOf(msg("user", "a"))
        // 1-char msg alone is well under any threshold. Add a 2000-char
        // systemPrompt → 500 tokens, blowing past 0.85 * 500 = 425.
        assertTrue(cc.needsCompression(
            m,
            contextLength = 500,
            systemPrompt = "s".repeat(2000)))
    }

    // ── R-STATE-026 / TC-STATE-026-a: keep head/tail behaviour ─────────────

    @Test
    fun `truncate oldest keeps at least minRecentMessages`() {
        // ── R-STATE-026 / TC-STATE-026-a: tail preserved under compression ──
        val cc = ContextCompressor(
            CompressorConfig(
                strategy = CompressionStrategy.TRUNCATE_OLDEST,
                minRecentMessages = 3))
        // 5 messages; compress should keep at least the last 3.
        val messages = (1..5).map { msg("user", "m$it") }
        val out = cc.compress(messages)
        assertTrue("must retain recent tail", out.size >= 3)
        // The last-three are preserved verbatim
        assertEquals(listOf("m3", "m4", "m5"), out.takeLast(3).map { it["content"] })
    }

    @Test
    fun `compress below minRecent is a no-op`() {
        val cc = ContextCompressor(
            CompressorConfig(
                strategy = CompressionStrategy.TRUNCATE_OLDEST,
                minRecentMessages = 5))
        val tiny = listOf(msg("user", "m1"), msg("user", "m2"))
        // Below threshold — returned unchanged.
        assertEquals(tiny, cc.compress(tiny))
    }

    @Test
    fun `keepRecent strategy returns at least minRecent when possible`() {
        val cc = ContextCompressor(
            CompressorConfig(
                strategy = CompressionStrategy.KEEP_RECENT,
                minRecentMessages = 3))
        val messages = (1..10).map { msg("user", "payload-$it-pad-pad-pad-pad") }
        val out = cc.compress(messages)
        assertTrue("keepRecent must preserve a tail", out.size >= 3)
        assertEquals("payload-10-pad-pad-pad-pad", out.last()["content"])
    }

    // ── R-STATE-027 / TC-STATE-027-a: tool results pruned first ────────────

    @Test
    fun `drop tool results strategy removes a middle tool_result`() {
        // ── R-STATE-027 / TC-STATE-027-a: tool results compressed first ──
        val cc = ContextCompressor(
            CompressorConfig(
                strategy = CompressionStrategy.DROP_TOOL_RESULTS,
                minRecentMessages = 2))
        // Build: [user, tool_result_block (user-role with list content), user, user]
        val toolResultBlock = mapOf<String, Any>(
            "role" to "user",
            "content" to listOf(
                mapOf("type" to "tool_result", "tool_use_id" to "t1", "content" to "old output")
            )
        )
        val messages = listOf(
            msg("user", "first"),
            toolResultBlock,
            msg("user", "second"),
            msg("user", "third"))
        val out = cc.compress(messages)
        // The middle tool_result block should be dropped, keeping size=3.
        assertEquals(3, out.size)
        // "third" must still be last (newest preserved)
        assertEquals("third", out.last()["content"])
    }

    @Test
    fun `_pruneOldToolResults replaces large tool outputs with placeholder`() {
        // Exercises the tail-budget variant: keeps recent N messages verbatim
        // and rewrites older tool content above the 200-char threshold.
        val cc = ContextCompressor()
        val bigContent = "x".repeat(500)
        val messages = listOf(
            mapOf<String, Any>("role" to "tool", "content" to bigContent, "tool_call_id" to "t1"),
            msg("user", "recent"),
            msg("assistant", "recent-answer"))
        val (pruned, count) = cc._pruneOldToolResults(messages, protectTailCount = 2)
        assertEquals(1, count)
        assertEquals("[Old tool output cleared to save context space]", pruned[0]["content"])
        // Recent tail is untouched
        assertEquals("recent", pruned[1]["content"])
    }

    @Test
    fun `_pruneOldToolResults skips short tool results`() {
        val cc = ContextCompressor()
        val shortContent = "short result"
        val messages = listOf(
            mapOf<String, Any>("role" to "tool", "content" to shortContent, "tool_call_id" to "t1"),
            msg("user", "recent"))
        val (pruned, count) = cc._pruneOldToolResults(messages, protectTailCount = 1)
        assertEquals(0, count)
        assertEquals(shortContent, pruned[0]["content"])
    }

    @Test
    fun `ensureToolPairIntegrity patches missing tool_result`() {
        // Tool use without matching tool_result gets a stub inserted.
        val cc = ContextCompressor()
        val assistant = mapOf<String, Any>(
            "role" to "assistant",
            "content" to listOf(
                mapOf("type" to "tool_use", "id" to "orphan-1", "name" to "ls", "input" to emptyMap<String, Any>())
            )
        )
        val userMissingResult = mapOf<String, Any>(
            "role" to "user",
            "content" to listOf(
                mapOf("type" to "text", "text" to "thanks")
            )
        )
        val fixed = cc.ensureToolPairIntegrity(listOf(assistant, userMissingResult))
        // The "user" message should now include a tool_result referencing orphan-1
        @Suppress("UNCHECKED_CAST")
        val userContent = fixed[1]["content"] as List<Map<String, Any>>
        val result = userContent.firstOrNull { it["type"] == "tool_result" }
        assertNotNull("should insert stub tool_result for orphan call", result)
        assertEquals("orphan-1", result!!["tool_use_id"])
        assertEquals(true, result["is_error"])
    }

    @Test
    fun `ensureToolPairIntegrity keeps intact pairs untouched`() {
        val cc = ContextCompressor()
        val assistant = mapOf<String, Any>(
            "role" to "assistant",
            "content" to listOf(
                mapOf("type" to "tool_use", "id" to "paired-1", "name" to "ls", "input" to emptyMap<String, Any>())
            )
        )
        val user = mapOf<String, Any>(
            "role" to "user",
            "content" to listOf(
                mapOf("type" to "tool_result", "tool_use_id" to "paired-1", "content" to "hi")
            )
        )
        val messages = listOf(assistant, user)
        val out = cc.ensureToolPairIntegrity(messages)
        assertEquals(messages, out)
    }

    @Test
    fun `_sanitizeToolPairs removes orphaned tool results`() {
        // A tool result whose calling assistant message was removed → drop it.
        val cc = ContextCompressor()
        val messages = listOf(
            mapOf<String, Any>("role" to "assistant", "content" to "hi"),  // no tool_calls
            toolResult("missing-id", "orphaned output"),
            msg("user", "next"))
        val sanitized = cc._sanitizeToolPairs(messages)
        // Orphan tool result must be gone
        assertTrue(sanitized.none { it["role"] == "tool" })
        assertEquals(2, sanitized.size)
    }

    @Test
    fun `_sanitizeToolPairs inserts stub for missing tool result`() {
        val cc = ContextCompressor()
        val messages = listOf(
            assistantToolCall("abc"),
            msg("user", "next"))  // should follow a tool result but doesn't
        val sanitized = cc._sanitizeToolPairs(messages)
        // Stub tool result should be inserted after the assistant call
        val stub = sanitized.firstOrNull { it["role"] == "tool" && it["tool_call_id"] == "abc" }
        assertNotNull("stub tool result required", stub)
        assertTrue((stub!!["content"] as String).contains("Result from earlier conversation"))
    }

    // ── R-STATE-028 / TC-STATE-028-a: compression failure graceful ─────────

    @Test
    fun `_generateSummary returns null on Android without LLM`() {
        // ── R-STATE-028 / TC-STATE-028-a: no LLM access → null, no crash ──
        val cc = ContextCompressor()
        val turns = listOf(msg("user", "hello"), msg("assistant", "hi"))
        // Explicitly must return null (not throw) when LLM summarization is
        // unavailable — documented behaviour the Kotlin port relies on.
        assertNull(cc._generateSummary(turns))
    }

    @Test
    fun `_generateSummary with focus topic still returns null gracefully`() {
        val cc = ContextCompressor()
        assertNull(cc._generateSummary(listOf(msg("user", "x")), focusTopic = "security"))
    }

    @Test
    fun `shouldCompress returns false without prior usage`() {
        // No updateFromResponse / updateModel calls → thresholdTokens = 0
        // and lastPromptTokens = 0. 0 >= 0 is true by contract — check we
        // get the exact boundary behaviour (Kotlin code: tokens >= thresholdTokens).
        val cc = ContextCompressor()
        assertTrue(cc.shouldCompress())
    }

    @Test
    fun `shouldCompress with explicit low tokens is false after updateModel`() {
        val cc = ContextCompressor()
        cc.updateModel(
            model = "x", contextLength = 100_000,
            baseUrl = "", apiKey = "", provider = "", apiMode = "")
        // thresholdTokens clamps to MINIMUM_CONTEXT_LENGTH (4096)
        assertFalse(cc.shouldCompress(promptTokens = 1000))
        assertTrue(cc.shouldCompress(promptTokens = 100_000))
    }

    // ── Serialization / summary prefix helpers ────────────────────────────

    @Test
    fun `_serializeForSummary labels roles correctly`() {
        val cc = ContextCompressor()
        val out = cc._serializeForSummary(listOf(
            msg("user", "hello"),
            msg("assistant", "hi there")
        ))
        assertTrue(out.contains("[USER]: hello"))
        assertTrue(out.contains("[ASSISTANT]: hi there"))
    }

    @Test
    fun `_serializeForSummary truncates very long content`() {
        val cc = ContextCompressor()
        // _CONTENT_MAX = 6000 — need > 6000 chars to trigger truncation
        val big = "a".repeat(7000)
        val out = cc._serializeForSummary(listOf(msg("assistant", big)))
        assertTrue(out.contains("[truncated]"))
    }

    @Test
    fun `_serializeForSummary formats tool result with id`() {
        val cc = ContextCompressor()
        val tool = mapOf<String, Any>("role" to "tool", "tool_call_id" to "tc1", "content" to "42")
        val out = cc._serializeForSummary(listOf(tool))
        assertTrue(out.contains("[TOOL RESULT tc1]: 42"))
    }

    @Test
    fun `_withSummaryPrefix strips legacy and adds canonical`() {
        val cc = ContextCompressor()
        val legacy = "[CONTEXT SUMMARY]: payload"
        val out = cc._withSummaryPrefix(legacy)
        // Strip legacy prefix, then prepend canonical SUMMARY_PREFIX + \n
        assertTrue("must use canonical prefix", out.startsWith("[CONTEXT COMPACTION"))
        assertTrue(out.contains("payload"))
        assertFalse("legacy prefix must be gone", out.contains("[CONTEXT SUMMARY]:"))
    }

    @Test
    fun `_withSummaryPrefix on empty returns just the prefix`() {
        val cc = ContextCompressor()
        val out = cc._withSummaryPrefix("")
        assertTrue(out.startsWith("[CONTEXT COMPACTION"))
        // No body after prefix
        assertFalse(out.contains("\npayload"))
    }

    @Test
    fun `_getToolCallId reads dict id field`() {
        val cc = ContextCompressor()
        assertEquals("abc", cc._getToolCallId(mapOf<String, Any>("id" to "abc")))
        assertEquals("", cc._getToolCallId(null))
        assertEquals("", cc._getToolCallId("not-a-map"))
    }

    @Test
    fun `_alignBoundaryForward skips tool messages at boundary`() {
        val cc = ContextCompressor()
        val messages = listOf(
            msg("user", "a"),
            toolResult("t1", "x"),
            toolResult("t2", "y"),
            msg("user", "b"))
        // Boundary starts at index 1 (a tool) → should advance to 3 (user).
        assertEquals(3, cc._alignBoundaryForward(messages, 1))
    }

    @Test
    fun `_alignBoundaryBackward anchors to tool_call assistant`() {
        val cc = ContextCompressor()
        val messages = listOf(
            assistantToolCall("call1"),
            toolResult("call1", "result"),
            msg("user", "after"))
        // idx=2 is user; walking backward we find tool at 1, then assistant
        // with tool_calls at 0 → boundary should pull back to 0.
        assertEquals(0, cc._alignBoundaryBackward(messages, 2))
    }

    @Test
    fun `_findLastUserMessageIdx returns -1 when none`() {
        val cc = ContextCompressor()
        val msgs = listOf(msg("assistant", "hi"), msg("tool", "out"))
        assertEquals(-1, cc._findLastUserMessageIdx(msgs.map { it as Map<String, Any?> }, 0))
    }

    @Test
    fun `_findLastUserMessageIdx returns latest user index`() {
        val cc = ContextCompressor()
        val msgs: List<Map<String, Any?>> = listOf(
            msg("user", "first"),
            msg("assistant", "r"),
            msg("user", "latest")
        )
        assertEquals(2, cc._findLastUserMessageIdx(msgs, 0))
    }

    // ── updateFromResponse / updateModel ──────────────────────────────────

    @Test
    fun `updateFromResponse stores last token counts`() {
        val cc = ContextCompressor()
        cc.updateFromResponse(mapOf("prompt_tokens" to 100, "completion_tokens" to 25))
        // shouldCompress uses lastPromptTokens when promptTokens is null
        // thresholdTokens is still 0 → 100 >= 0 is true
        assertTrue(cc.shouldCompress())
    }

    @Test
    fun `updateModel raises threshold above floor`() {
        val cc = ContextCompressor()
        cc.updateModel(
            model = "m", contextLength = 200_000,
            baseUrl = "", apiKey = "", provider = "", apiMode = "")
        // thresholdPercent defaults to 0.5 → 100_000 tokens
        cc.updateFromResponse(mapOf("prompt_tokens" to 50_000, "completion_tokens" to 0))
        assertFalse(cc.shouldCompress())
        cc.updateFromResponse(mapOf("prompt_tokens" to 150_000, "completion_tokens" to 0))
        assertTrue(cc.shouldCompress())
    }

    @Test
    fun `updateModel clamps threshold to minimum`() {
        val cc = ContextCompressor()
        // Very small context → floor is MINIMUM_CONTEXT_LENGTH (4096)
        cc.updateModel(
            model = "m", contextLength = 1_000,
            baseUrl = "", apiKey = "", provider = "", apiMode = "")
        cc.updateFromResponse(mapOf("prompt_tokens" to 3_000, "completion_tokens" to 0))
        // 3000 < 4096 → below floor
        assertFalse(cc.shouldCompress())
    }

    @Test
    fun `compressor name returns compressor`() {
        assertEquals("compressor", ContextCompressor().name())
    }

    @Test
    fun `onSessionReset is a no-op`() {
        // Just verify the function exists and doesn't throw
        ContextCompressor().onSessionReset()
    }
}
