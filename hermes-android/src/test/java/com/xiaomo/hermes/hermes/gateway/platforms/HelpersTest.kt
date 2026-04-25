package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the pure-logic subset of Helpers.kt that runs on the JVM without
 * Android stubs:
 *   MessageDeduplicator (TTL + size cap)
 *   stripMarkdown (regex-based plain-text conversion)
 *   redactPhone (log redaction shapes)
 *   ThreadParticipationTracker (file-backed persistent Set — note: this
 *     writes to `~/.hermes/` by design, so tests only check in-memory state)
 *
 * TextBatchAggregator needs coroutine machinery and belongs in a separate
 * batch using kotlinx-coroutines-test.
 */
class HelpersTest {

    // ─── MessageDeduplicator ──────────────────────────────────────────────

    @Test
    fun `isDuplicate returns false on first sighting`() {
        val dedup = MessageDeduplicator()
        assertFalse(dedup.isDuplicate("msg-1"))
    }

    @Test
    fun `isDuplicate returns true on second sighting within TTL`() {
        val dedup = MessageDeduplicator(ttlSeconds = 60.0)
        assertFalse(dedup.isDuplicate("msg-1"))
        assertTrue(dedup.isDuplicate("msg-1"))
    }

    @Test
    fun `isDuplicate returns false for expired entries`() {
        // ttlSeconds = 0.0 → any prior sighting is already outside the window.
        val dedup = MessageDeduplicator(ttlSeconds = 0.0)
        assertFalse(dedup.isDuplicate("msg-1"))
        // Re-seen but TTL elapsed → treat as new.
        assertFalse(dedup.isDuplicate("msg-1"))
    }

    @Test
    fun `isDuplicate returns false on empty id`() {
        val dedup = MessageDeduplicator()
        assertFalse(dedup.isDuplicate(""))
        // Repeated empties also return false.
        assertFalse(dedup.isDuplicate(""))
    }

    @Test
    fun `clear resets dedup state`() {
        val dedup = MessageDeduplicator(ttlSeconds = 60.0)
        dedup.isDuplicate("msg-1")
        assertTrue(dedup.isDuplicate("msg-1"))
        dedup.clear()
        assertFalse(dedup.isDuplicate("msg-1"))
    }

    @Test
    fun `distinct ids do not collide`() {
        val dedup = MessageDeduplicator()
        assertFalse(dedup.isDuplicate("a"))
        assertFalse(dedup.isDuplicate("b"))
        assertFalse(dedup.isDuplicate("c"))
        // All three should now be considered duplicates.
        assertTrue(dedup.isDuplicate("a"))
        assertTrue(dedup.isDuplicate("b"))
        assertTrue(dedup.isDuplicate("c"))
    }

    // ─── stripMarkdown ────────────────────────────────────────────────────

    @Test
    fun `stripMarkdown strips bold markers`() {
        assertEquals("hello world", stripMarkdown("**hello** world"))
        assertEquals("hello world", stripMarkdown("__hello__ world"))
    }

    @Test
    fun `stripMarkdown strips italic markers`() {
        assertEquals("hello world", stripMarkdown("*hello* world"))
        assertEquals("hello world", stripMarkdown("_hello_ world"))
    }

    @Test
    fun `stripMarkdown strips inline code`() {
        assertEquals("value x", stripMarkdown("`value` x"))
    }

    @Test
    fun `stripMarkdown strips fenced code block fences`() {
        val md = "```python\ncode body\n```"
        // Opening fence is consumed (with language + newline); trailing ``` is
        // matched by the same regex with no language + no newline.
        val out = stripMarkdown(md)
        assertFalse(out, out.contains("```"))
        assertTrue(out, out.contains("code body"))
    }

    @Test
    fun `stripMarkdown strips heading prefixes`() {
        assertEquals("Title", stripMarkdown("# Title"))
        assertEquals("Sub", stripMarkdown("### Sub"))
        // Multi-line headings both stripped.
        val md = "# A\n## B\ntext"
        assertEquals("A\nB\ntext", stripMarkdown(md))
    }

    @Test
    fun `stripMarkdown preserves link text and drops url`() {
        assertEquals("click here", stripMarkdown("[click here](https://example.com)"))
    }

    @Test
    fun `stripMarkdown collapses 3+ newlines to 2`() {
        assertEquals("a\n\nb", stripMarkdown("a\n\n\n\nb"))
        // Exactly 2 stays as-is.
        assertEquals("a\n\nb", stripMarkdown("a\n\nb"))
    }

    @Test
    fun `stripMarkdown trims leading and trailing whitespace`() {
        assertEquals("hello", stripMarkdown("   hello   "))
        assertEquals("hello", stripMarkdown("\n\nhello\n\n"))
    }

    @Test
    fun `stripMarkdown handles combined markers`() {
        val md = "# Title\n\nSome **bold** and *italic* with `code` and [a link](http://x)."
        assertEquals(
            "Title\n\nSome bold and italic with code and a link.",
            stripMarkdown(md),
        )
    }

    @Test
    fun `stripMarkdown empty input returns empty`() {
        assertEquals("", stripMarkdown(""))
        assertEquals("", stripMarkdown("   "))
    }

    // ─── redactPhone ──────────────────────────────────────────────────────

    @Test
    fun `redactPhone returns angle-none on empty`() {
        assertEquals("<none>", redactPhone(""))
    }

    @Test
    fun `redactPhone masks tiny numbers completely`() {
        assertEquals("****", redactPhone("1234"))
        assertEquals("****", redactPhone("12"))
    }

    @Test
    fun `redactPhone keeps 2+2 shape for 5-8 digit numbers`() {
        // 5 digits: "12345" → "12" + "****" + "45" (last 2 chars)
        assertEquals("12****45", redactPhone("12345"))
        // 8 digits: first-2 + stars + last-2
        assertEquals("12****78", redactPhone("12345678"))
    }

    @Test
    fun `redactPhone keeps 4+4 shape for long numbers`() {
        // 10 digits: "1234567890" → "1234****7890"
        assertEquals("1234****7890", redactPhone("1234567890"))
        // International: "+14155552671" → "+141****2671"
        assertEquals("+141****2671", redactPhone("+14155552671"))
    }

    // ─── ThreadParticipationTracker ───────────────────────────────────────
    // Note: these tests use a unique platform name per run so they don't
    // interfere with real runtime state, and they don't validate persistence
    // across instances (that would need a tmp-dir swap).

    @Test
    fun `ThreadParticipationTracker contains is false for unseen`() {
        val tracker = ThreadParticipationTracker("testplatform_${System.nanoTime()}")
        assertFalse("thread-x" in tracker)
    }

    @Test
    fun `ThreadParticipationTracker mark makes thread contained`() {
        val tracker = ThreadParticipationTracker("testplatform_${System.nanoTime()}")
        tracker.mark("thread-1")
        assertTrue("thread-1" in tracker)
        assertFalse("thread-2" in tracker)
    }

    @Test
    fun `ThreadParticipationTracker mark is idempotent`() {
        val tracker = ThreadParticipationTracker("testplatform_${System.nanoTime()}")
        tracker.mark("thread-1")
        tracker.mark("thread-1")
        tracker.mark("thread-1")
        assertTrue("thread-1" in tracker)
    }

    @Test
    fun `ThreadParticipationTracker clear removes in-memory entries`() {
        val tracker = ThreadParticipationTracker("testplatform_${System.nanoTime()}")
        tracker.mark("thread-1")
        tracker.mark("thread-2")
        tracker.clear()
        assertFalse("thread-1" in tracker)
        assertFalse("thread-2" in tracker)
    }
}
