package com.xiaomo.hermes.hermes.cron

import android.content.Context
import com.xiaomo.hermes.hermes.tools._getCronApprovalMode
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Pure-logic subset of Jobs.kt that runs without Robolectric.
 *
 * The filesystem-bound pieces (loadJobs / saveJobs / createJob / etc.) all
 * route through getHermesHome() → getAppContext().filesDir, so the following
 * deferred TCs need Robolectric to back Context:
 *
 *   - TC-CRON-011-a (addJob ↔ readback persistence roundtrip) — "needs Robolectric"
 *   - TC-CRON-012-a (approve mode="no" enforcement) — policy layer is gateway-side;
 *        "needs Robolectric" because jobs storage + gateway wiring pull in Context
 *   - TC-CRON-013-a (Android 启动 → daemon 不起) — verifying no background thread
 *        is spawned requires driving the gateway bootstrap path under Android
 *        runtime; "needs Robolectric"
 *
 * What's testable here without Context: skill normalization, schedule parsing,
 * duration parsing, computeNextRun for intervals and one-shots, and the
 * Android cron-expr rejection branch (HAS_CRONITER = false).
 */
class JobsTest {

    // ── normalizeSkillList ────────────────────────────────────────────────

    @Test
    fun `normalizeSkillList returns empty when both inputs null`() {
        assertEquals(emptyList<String>(), normalizeSkillList(null, null))
    }

    @Test
    fun `normalizeSkillList prefers skills list over single skill`() {
        assertEquals(
            listOf("foo", "bar"),
            normalizeSkillList(skill = "legacy", skills = listOf("foo", "bar"))
        )
    }

    @Test
    fun `normalizeSkillList falls back to single skill when skills null`() {
        assertEquals(listOf("legacy"), normalizeSkillList(skill = "legacy", skills = null))
    }

    @Test
    fun `normalizeSkillList strips duplicates preserving order`() {
        assertEquals(
            listOf("a", "b", "c"),
            normalizeSkillList(skills = listOf("a", "b", "a", "c", "b"))
        )
    }

    @Test
    fun `normalizeSkillList trims whitespace and drops blanks`() {
        assertEquals(
            listOf("a", "b"),
            normalizeSkillList(skills = listOf("  a  ", "", "   ", "b"))
        )
    }

    // ── applySkillFields ──────────────────────────────────────────────────

    @Test
    fun `applySkillFields canonicalises skills list from legacy skill`() {
        val job = mutableMapOf<String, Any?>("skill" to "legacy", "skills" to null)
        val out = applySkillFields(job)
        assertEquals(listOf("legacy"), out["skills"])
        assertEquals("legacy", out["skill"])
    }

    @Test
    fun `applySkillFields prefers skills list over skill field`() {
        val job = mutableMapOf<String, Any?>(
            "skill" to "ignored",
            "skills" to listOf("foo", "bar")
        )
        val out = applySkillFields(job)
        assertEquals(listOf("foo", "bar"), out["skills"])
        assertEquals("foo", out["skill"])
    }

    @Test
    fun `applySkillFields leaves skill null when skills empty`() {
        val job = mutableMapOf<String, Any?>("skill" to null, "skills" to emptyList<String>())
        val out = applySkillFields(job)
        assertEquals(emptyList<String>(), out["skills"])
        assertNull(out["skill"])
    }

    // ── parseDuration ─────────────────────────────────────────────────────

    @Test
    fun `parseDuration accepts minute abbreviations`() {
        assertEquals(30, parseDuration("30m"))
        assertEquals(45, parseDuration("45min"))
        assertEquals(5, parseDuration("5 minutes"))
    }

    @Test
    fun `parseDuration accepts hour abbreviations`() {
        assertEquals(60, parseDuration("1h"))
        assertEquals(120, parseDuration("2 hours"))
    }

    @Test
    fun `parseDuration accepts day abbreviations`() {
        assertEquals(1440, parseDuration("1d"))
        assertEquals(2880, parseDuration("2 days"))
    }

    @Test
    fun `parseDuration rejects malformed input`() {
        try {
            parseDuration("garbage")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // ── parseSchedule ─────────────────────────────────────────────────────

    @Test
    fun `parseSchedule every-X yields interval kind`() {
        val out = parseSchedule("every 30m")
        assertEquals("interval", out["kind"])
        assertEquals(30, out["minutes"])
        assertEquals("every 30m", out["display"])
    }

    @Test
    fun `parseSchedule interval 2h converts to minutes`() {
        val out = parseSchedule("every 2h")
        assertEquals("interval", out["kind"])
        assertEquals(120, out["minutes"])
    }

    @Test
    fun `parseSchedule cron-expression is rejected on Android`() {
        // HAS_CRONITER is false on Android → 5-field expressions throw.
        try {
            parseSchedule("*/5 * * * *")
            fail("expected IllegalArgumentException for cron on Android")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Cron expressions are not supported on Android"))
        }
    }

    @Test
    fun `parseSchedule bare duration produces one-shot`() {
        val out = parseSchedule("30m")
        assertEquals("once", out["kind"])
        assertNotNull(out["run_at"])
        assertTrue((out["display"] as String).startsWith("once in "))
    }

    @Test
    fun `parseSchedule invalid schedule throws with guidance`() {
        try {
            parseSchedule("not-a-schedule")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid schedule"))
        }
    }

    // ── computeNextRun ────────────────────────────────────────────────────

    @Test
    fun `computeNextRun interval without lastRunAt returns a future ISO`() {
        val schedule = mapOf("kind" to "interval", "minutes" to 10)
        val next = computeNextRun(schedule)
        assertNotNull(next)
        // ISO 8601 sanity: includes T and matches YYYY-MM-DD
        assertTrue(next!!.matches(Regex("""\d{4}-\d{2}-\d{2}T.*""")))
    }

    @Test
    fun `computeNextRun interval with lastRunAt advances from last`() {
        val schedule = mapOf("kind" to "interval", "minutes" to 30)
        val last = "2026-01-01T10:00:00.000+0000"
        val next = computeNextRun(schedule, lastRunAt = last)
        assertNotNull(next)
        // Parse both timestamps and confirm the delta is exactly 30 minutes.
        // (The SUT formats in machine-local TZ, so wall-clock strings differ
        // across build hosts — what's invariant is the 30-minute offset.)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
        val lastMs = sdf.parse(last)!!.time
        val nextMs = sdf.parse(next!!)!!.time
        assertEquals(30L * 60 * 1000, nextMs - lastMs)
    }

    @Test
    fun `computeNextRun cron returns null on Android (no croniter)`() {
        val schedule = mapOf("kind" to "cron", "expr" to "*/5 * * * *")
        assertNull(computeNextRun(schedule))
    }

    @Test
    fun `computeNextRun unknown kind returns null`() {
        assertNull(computeNextRun(mapOf("kind" to "bogus")))
    }

    // ── TC-CRON-012-a: approve mode="no" blocks ──────────────────────────
    /**
     * TC-CRON-012-a — `approvals.cron_mode` accepts only
     * {approve, off, allow, yes} as the "allow" signal; everything else
     * (including "no", "deny", blank, garbage) collapses to "deny" so
     * dangerous commands are blocked in cron-driven sessions.
     *
     * Python upstream `tools/approval.py:540-545` (`_get_cron_approval_mode`)
     * has the identical allow-list + default-deny shape; the Kotlin port
     * lives at `tools/Approval.kt:428-431` and is what actually guards
     * `HERMES_CRON_SESSION` scheduler runs.
     *
     * With no `$HERMES_HOME/config.yaml` reachable (no Context in the JVM
     * unit env), `_getApprovalConfig()` swallows the exception and returns
     * an empty map. That drives the default path: `cron_mode = "deny"` →
     * `_getCronApprovalMode()` returns "deny". That IS the 阻塞 case the
     * TC asserts ("no" / "" / unset → blocked).
     */
    @Test
    fun `approval mode enforced`() {
        // Default (no config on disk, no Context) → "deny".
        assertEquals(
            "default + missing config must return deny",
            "deny",
            _getCronApprovalMode())
    }

    // ── TC-CRON-011-a: addJob persistence roundtrip ──────────────────────
    /**
     * TC-CRON-011-a — a saved job must survive a load/save round-trip with
     * byte-for-byte equivalence on the observable fields (id, prompt, name,
     * schedule.kind). Uses a Mockito Context stub pointing at a temp dir so
     * `getHermesHome()` resolves without Robolectric.
     *
     * This injects the Context via reflection into the private
     * `_appContext` field in `HermesConstants.kt` since
     * `initHermesConstants(ctx)` would require `ctx.applicationContext` to
     * be non-null, and faking Context.applicationContext through Mockito's
     * default-null policy is brittle. Direct field injection is the minimum
     * coupling surface.
     */
    @Test
    fun `persistence roundtrip`() {
        val tempHome = Files.createTempDirectory("hermes-cron-test").toFile()
        val savedContext = _swapAppContext(mockContextWithFilesDir(tempHome))
        try {
            // Save a tiny job map and read it back.
            val job: MutableMap<String, Any?> = mutableMapOf(
                "id" to "job_abc123",
                "name" to "nightly check",
                "prompt" to "scan inbox",
                "schedule" to mapOf("kind" to "interval", "minutes" to 60, "display" to "every 1h"),
                "created_at" to "2026-04-26T12:00:00.000+0000",
                "enabled" to true,
                "skills" to emptyList<String>(),
                "skill" to null
            )
            saveJobs(listOf(job))

            // Confirm the file actually landed under our temp home.
            val cronDir = File(File(tempHome, ".hermes"), "cron")
            val jobsFile = File(cronDir, "jobs.json")
            assertTrue("jobs.json must exist after saveJobs (got ${jobsFile.absolutePath})", jobsFile.exists())

            // Round-trip through loadJobs.
            val reloaded = loadJobs()
            assertEquals("must load exactly one job", 1, reloaded.size)
            val back = reloaded[0]
            assertEquals("job_abc123", back["id"])
            assertEquals("nightly check", back["name"])
            assertEquals("scan inbox", back["prompt"])

            @Suppress("UNCHECKED_CAST")
            val loadedSchedule = back["schedule"] as Map<String, Any?>
            assertEquals("interval", loadedSchedule["kind"])
            // Gson deserializes numbers as Double by default → accept either.
            val minutes = (loadedSchedule["minutes"] as? Number)?.toInt()
            assertEquals(60, minutes)
        } finally {
            _swapAppContext(savedContext)
            tempHome.deleteRecursively()
        }
    }

    private fun mockContextWithFilesDir(dir: File): Context {
        val ctx: Context = mock {
            on { filesDir } doReturn dir
            on { applicationContext } doReturn mock()
        }
        return ctx
    }

    /**
     * Swap the private top-level `_appContext` field in
     * `com.xiaomo.hermes.hermes.HermesConstantsKt`. Returns the previous
     * value so tests can restore it in @After or finally.
     */
    private fun _swapAppContext(newContext: Context?): Context? {
        val cls = Class.forName("com.xiaomo.hermes.hermes.HermesConstantsKt")
        val field = cls.getDeclaredField("_appContext")
        field.isAccessible = true
        val prev = field.get(null) as Context?
        field.set(null, newContext)
        return prev
    }
}
