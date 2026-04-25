package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolResultStorageTest {

    @Test
    fun `generatePreview returns whole content when shorter than max`() {
        val short = "hello"
        val (preview, hasMore) = generatePreview(short, maxChars = 100)
        assertEquals(short, preview)
        assertFalse(hasMore)
    }

    @Test
    fun `generatePreview truncates content longer than max`() {
        val long = "x".repeat(2000)
        val (preview, hasMore) = generatePreview(long, maxChars = 500)
        assertTrue(preview.length <= 500)
        assertTrue(hasMore)
    }

    @Test
    fun `generatePreview truncates at last newline when past halfway`() {
        // "aaaa\nbbbb\ncccc" len=14; maxChars=12 → truncated=first 12 ("aaaa\nbbbb\ncc"),
        // last newline at idx 9 > 6 (half) → return "aaaa\nbbbb\n" (through newline inclusive).
        val content = "aaaa\nbbbb\ncccc"
        val (preview, hasMore) = generatePreview(content, maxChars = 12)
        assertTrue(preview.endsWith("\n"))
        assertTrue(hasMore)
    }

    @Test
    fun `generatePreview keeps hard truncate when no newline in second half`() {
        val content = "aaaaaaa\n" + "x".repeat(100)  // newline only in first 8 chars
        val (preview, _) = generatePreview(content, maxChars = 50)
        // Last newline is at idx 7, which is NOT > maxChars/2 (25) → keep hard truncate at 50.
        assertEquals(50, preview.length)
    }

    @Test
    fun `maybePersistToolResult returns content untouched below threshold`() {
        val content = "tiny"
        val result = maybePersistToolResult(
            content = content,
            toolName = "some_tool",
            toolUseId = "id-1",
            env = null,
            threshold = 1000.0,
        )
        assertEquals(content, result)
    }

    @Test
    fun `maybePersistToolResult returns content when threshold is infinity`() {
        val content = "x".repeat(10_000)
        val result = maybePersistToolResult(
            content = content,
            toolName = "some_tool",
            toolUseId = "id-2",
            env = null,
            threshold = Double.POSITIVE_INFINITY,
        )
        assertEquals(content, result)
    }

    @Test
    fun `maybePersistToolResult returns truncated message when env is null and over threshold`() {
        val content = "x".repeat(5000)
        val result = maybePersistToolResult(
            content = content,
            toolName = "some_tool",
            toolUseId = "id-3",
            env = null,
            threshold = 100.0,
        )
        assertTrue("Truncated" in result)
        assertTrue("5000" in result)
        assertFalse(PERSISTED_OUTPUT_TAG in result)  // no env → no persisted-output wrapping
    }

    @Test
    fun `maybePersistToolResult uses registry threshold via resolveThreshold for read_file (infinity)`() {
        val big = "x".repeat(500_000)
        val result = maybePersistToolResult(
            content = big,
            toolName = "read_file",  // PINNED_THRESHOLDS puts read_file at +inf
            toolUseId = "id-4",
            env = null,
        )
        // +inf threshold → always returned as-is.
        assertEquals(big, result)
    }

    @Test
    fun `PERSISTED_OUTPUT_TAG constants have expected values`() {
        assertEquals("<persisted-output>", PERSISTED_OUTPUT_TAG)
        assertEquals("</persisted-output>", PERSISTED_OUTPUT_CLOSING_TAG)
    }

    @Test
    fun `STORAGE_DIR default is under tmp`() {
        assertEquals("/tmp/hermes-results", STORAGE_DIR)
    }

    @Test
    fun `HEREDOC_MARKER has fixed sentinel value`() {
        assertEquals("HERMES_PERSIST_EOF", HEREDOC_MARKER)
    }

    @Test
    fun `enforceTurnBudget no-op when under budget`() {
        val msgs = mutableListOf(
            mutableMapOf<String, Any?>("content" to "short 1", "tool_call_id" to "a"),
            mutableMapOf<String, Any?>("content" to "short 2", "tool_call_id" to "b"),
        )
        val config = BudgetConfig(turnBudget = 10_000)
        val result = enforceTurnBudget(msgs, env = null, config = config)
        // Under 10K chars — both unchanged.
        assertEquals("short 1", result[0]["content"])
        assertEquals("short 2", result[1]["content"])
    }

    @Test
    fun `enforceTurnBudget spills largest non-persisted content when over budget`() {
        val big = "x".repeat(5000)
        val msgs = mutableListOf(
            mutableMapOf<String, Any?>("content" to big, "tool_call_id" to "big"),
            mutableMapOf<String, Any?>("content" to "small", "tool_call_id" to "small"),
        )
        val config = BudgetConfig(turnBudget = 2000, previewSize = 500)
        val result = enforceTurnBudget(msgs, env = null, config = config)
        // Big one should have been shortened (env is null → truncated message returned).
        val bigContent = result[0]["content"] as String
        assertTrue(bigContent.length < big.length)
        assertTrue("Truncated" in bigContent)
        // Small one unchanged.
        assertEquals("small", result[1]["content"])
    }

    @Test
    fun `enforceTurnBudget skips already-persisted content`() {
        val alreadyPersisted =
            "$PERSISTED_OUTPUT_TAG\nfoo\n$PERSISTED_OUTPUT_CLOSING_TAG" + "x".repeat(5000)
        val msgs = mutableListOf(
            mutableMapOf<String, Any?>("content" to alreadyPersisted, "tool_call_id" to "p"),
            mutableMapOf<String, Any?>("content" to "other", "tool_call_id" to "o"),
        )
        val originalContent = alreadyPersisted
        val config = BudgetConfig(turnBudget = 100)
        val result = enforceTurnBudget(msgs, env = null, config = config)
        // Already-persisted content must be left untouched.
        assertEquals(originalContent, result[0]["content"])
    }
}
