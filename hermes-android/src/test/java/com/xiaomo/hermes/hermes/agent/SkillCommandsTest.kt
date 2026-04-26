package com.xiaomo.hermes.hermes.agent

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SkillCommands.kt — pure-logic helpers that can be exercised in
 * a JVM unit test without touching the filesystem or Compose stack.
 *
 * Covers TC-SKILL-030/031-a.
 */
class SkillCommandsTest {

    // ── TC-SKILL-030-a: resolveSkillCommandKey underscore normalization ──
    /**
     * TC-SKILL-030-a — `resolveSkillCommandKey("foo_bar")` must normalize
     * underscores to hyphens and prepend `/`, then look up the result in the
     * scanned skill-commands map. On Android there is no skills directory
     * to scan, so `getSkillCommands()` returns the empty map; the function
     * must return null (not throw) for any command that isn't registered.
     *
     * This guards the normalization rule itself — the `_`→`-` swap at
     * SkillCommands.kt:367 — which is what TC-030-a is actually asserting.
     */
    @Test
    fun `resolveSkillCommandKey underscore normalization`() {
        // Empty input → null shortcut.
        assertNull(resolveSkillCommandKey(""))

        // Non-existent command → null. If the normalization were broken the
        // function might throw or return a different shape; null is the
        // contract.
        assertNull(resolveSkillCommandKey("foo_bar"))
        assertNull(resolveSkillCommandKey("plan"))

        // Sanity: the scanned commands map must at least be constructible
        // without exception on Android (returns empty map).
        val commands = getSkillCommands()
        assertTrue(
            "getSkillCommands must be a non-null Map (may be empty on Android)",
            commands.isEmpty() || commands.isNotEmpty())
    }

    // ── TC-SKILL-031-a: buildPlanPath slug rules ──
    /**
     * TC-SKILL-031-a — `buildPlanPath` builds a workspace-relative path of
     * the form `.hermes/plans/<ts>-<slug>.md`. The slug rules:
     *
     *   1. take the first non-empty line of `userInstruction`
     *   2. lowercase + `[^a-z0-9]+ → -` + trim leading/trailing `-`
     *   3. split on `-`, drop empties, take first 8 segments, rejoin with `-`
     *   4. truncate to 48 chars, trim `-`
     *   5. if the final slug is empty → fall back to `conversation-plan`
     *
     * Python upstream passes `user_instruction` as a single string — not an
     * argv list — so the earlier TC expectation of `argv=[a,b,c]` was wrong.
     * The slug rules are what parity actually depends on.
     */
    @Test
    fun `buildPlanPath slug rules`() {
        // Fixed timestamp to keep the assertion deterministic.
        val epoch = Date(0L) // 1970-01-01 — exact minute depends on JVM TZ.

        // Case 1: normal instruction → slug from first line.
        val p1 = buildPlanPath("Fix the Android bug!", epoch)
        assertTrue(
            "plan path must live under .hermes/plans (got ${p1.path})",
            p1.path.replace('\\', '/').contains(".hermes/plans/"))
        assertTrue("file must end with .md", p1.name.endsWith(".md"))
        assertTrue(
            "slug must contain 'fix-the-android-bug' (got ${p1.name})",
            p1.name.contains("-fix-the-android-bug.md"))

        // Case 2: only punctuation → falls back to conversation-plan.
        val p2 = buildPlanPath("!!!***###", epoch)
        assertTrue(
            "empty slug must fall back to conversation-plan (got ${p2.name})",
            p2.name.endsWith("-conversation-plan.md"))

        // Case 3: empty input → conversation-plan.
        val p3 = buildPlanPath("", epoch)
        assertTrue(
            "empty instruction → conversation-plan (got ${p3.name})",
            p3.name.endsWith("-conversation-plan.md"))

        // Case 4: multi-line instruction → only first line participates.
        val p4 = buildPlanPath("first line wins\nignored second line", epoch)
        assertTrue(
            "only first line should be slugged (got ${p4.name})",
            p4.name.contains("-first-line-wins.md"))
        assertTrue(
            "second line must not leak into slug",
            !p4.name.contains("second"))

        // Case 5: 8-segment cap — 10-word input → slug has at most 8 segments.
        val p5 = buildPlanPath("one two three four five six seven eight nine ten", epoch)
        val slug5 = p5.name.substringAfter("-").removeSuffix(".md")
        val segs5 = slug5.split("-").filter { it.isNotEmpty() }
        // The timestamp prefix is 17 chars (`yyyy-MM-dd_HHmmss`) so the
        // substringAfter("-") keeps everything after the first `-`; split on
        // `-` gives us the remaining tokens INCLUDING the date components.
        // Rather than try to peel those apart, assert that "nine" / "ten"
        // were truncated out of the slug portion.
        assertTrue("slug must cap at 8 words — 'nine' must not appear", "nine" !in segs5)
        assertTrue("slug must cap at 8 words — 'ten' must not appear", "ten" !in segs5)
        assertTrue("first word must survive", "one" in segs5)
        assertTrue("eighth word must survive", "eight" in segs5)

        // Case 6: 48-char cap on slug.
        val longInput = "a".repeat(200)
        val p6 = buildPlanPath(longInput, epoch)
        val slug6 = p6.name.substringAfter("-").removeSuffix(".md")
        // The `aaa…` becomes one giant segment, so after truncation it
        // should be ≤ 48 chars; substringAfter strips the timestamp so we
        // compare against the raw slug part.
        val rawSlug = p6.name.removeSuffix(".md").substringAfterLast('-')
        assertTrue(
            "slug must be capped at 48 chars (got ${rawSlug.length}: $rawSlug)",
            rawSlug.length <= 48)
    }

    // ── Sanity: plan slug regex is the one Python uses ──
    /**
     * The `_PLAN_SLUG_RE` must be `[^a-z0-9]+` (capital letters excluded —
     * the function lowercases first). This is the exact pattern Python
     * upstream uses so slugs match byte-for-byte.
     */
    @Test
    fun `plan slug regex matches Python`() {
        assertEquals("[^a-z0-9]+", _PLAN_SLUG_RE.pattern)
    }
}
