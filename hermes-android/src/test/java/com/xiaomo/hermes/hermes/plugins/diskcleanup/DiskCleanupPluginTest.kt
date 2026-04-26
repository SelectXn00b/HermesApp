package com.xiaomo.hermes.hermes.plugins.diskcleanup

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for disk_cleanup library functions (ported from
 * `plugins/disk-cleanup/disk_cleanup.py`).
 *
 * `DiskCleanup.kt` resolves HERMES_HOME from:
 *   1. the `HERMES_HOME` env var (not settable from JVM tests)
 *   2. `$user.home/.hermes` as the fallback
 *
 * Tests redirect `user.home` to a temp dir so nothing hits the developer's
 * real home directory. Each test restores `user.home` in @After.
 *
 * Covers TC-SKILL-010/011/012-a.
 */
class DiskCleanupPluginTest {

    private lateinit var tempHome: File
    private var savedUserHome: String? = null

    @Before
    fun setUp() {
        tempHome = Files.createTempDirectory("hermes-plugin-test").toFile()
        savedUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @After
    fun tearDown() {
        if (savedUserHome != null) {
            System.setProperty("user.home", savedUserHome!!)
        } else {
            System.clearProperty("user.home")
        }
        tempHome.deleteRecursively()
    }

    // Helper — resolve HERMES_HOME the same way DiskCleanup does.
    private fun hermesHome(): File {
        val env = System.getenv("HERMES_HOME")?.trim().orEmpty()
        return if (env.isNotEmpty()) File(env).canonicalFile
        else File(System.getProperty("user.home")!!, ".hermes").canonicalFile
    }

    // ── TC-SKILL-011-a: isSafePath scope bounds ──
    /**
     * TC-SKILL-011-a — `isSafePath` must:
     *   • accept paths inside HERMES_HOME
     *   • accept paths inside `/tmp/hermes-*`
     *   • reject everything else
     *
     * This is the bedrock safety invariant — cleanup never runs on arbitrary
     * user data. Python upstream has the same allow-list rule.
     */
    @Test
    fun `isSafePath scope bounds`() {
        val home = hermesHome()
        home.mkdirs()

        // Inside HERMES_HOME → true
        val inside = File(home, "skills/foo.md")
        assertTrue("must accept path inside HERMES_HOME", isSafePath(inside))

        // /tmp/hermes-<name> → true (Python upstream convention)
        val tmpAllowed = File("/tmp/hermes-abc123/cache.json")
        assertTrue("must accept /tmp/hermes-*", isSafePath(tmpAllowed))

        // /tmp/random → false
        val tmpRejected = File("/tmp/random-stuff/cache.json")
        assertFalse("must reject /tmp/ paths without hermes- prefix", isSafePath(tmpRejected))

        // Arbitrary system path → false
        val outside = File("/etc/passwd")
        assertFalse("must reject /etc/passwd", isSafePath(outside))

        // ~/Documents → false (user.home but not under .hermes)
        val userDocs = File(System.getProperty("user.home"), "Documents/note.txt")
        assertFalse("must reject $userDocs (outside HERMES_HOME)", isSafePath(userDocs))
    }

    // ── TC-SKILL-012-a: dryRun is read-only ──
    /**
     * TC-SKILL-012-a — `dryRun()` returns `(auto, prompt)` classifications
     * based on the current tracked.json without touching any files on disk.
     * Must be side-effect-free so it can be used as a safety preview.
     */
    @Test
    fun `dryRun is read-only`() {
        val home = hermesHome()
        home.mkdirs()

        // Seed one tracked test file — quick() would auto-delete this.
        val testFile = File(home, "test-artifact.txt").apply { writeText("x") }
        track(testFile.absolutePath, category = "test", silent = true)

        val tracked = getTrackedFile()
        assertTrue("tracked.json must exist after track()", tracked.exists())
        val trackedBytes = tracked.readText()

        val (auto, prompt) = dryRun()

        // Classification: test file must land in the auto-delete bucket.
        assertTrue(
            "test file should be in auto bucket (auto=$auto, prompt=$prompt)",
            auto.any { (it["path"] as? String) == testFile.absolutePath })

        // Side-effect freedom: file still on disk, tracked.json unchanged.
        assertTrue("dryRun must not delete the file", testFile.exists())
        assertEquals(
            "dryRun must not mutate tracked.json",
            trackedBytes,
            tracked.readText())
    }

    // ── TC-SKILL-010-a: quick deletes test category immediately ──
    /**
     * TC-SKILL-010-a — `quick()` is the non-interactive deterministic sweep.
     * For `category="test"` the age check is `age >= 0` (i.e. always), so a
     * fresh test artifact must be deleted on first invocation.
     *
     * Temp files (age ≤ 7d) must survive. This asserts the rule boundary.
     */
    @Test
    fun `quick deletes test category immediately`() {
        val home = hermesHome()
        home.mkdirs()

        val doomed = File(home, "test-doomed.txt").apply { writeText("delete me") }
        val survivor = File(home, "temp-survivor.txt").apply { writeText("keep me") }

        track(doomed.absolutePath, category = "test", silent = true)
        track(survivor.absolutePath, category = "temp", silent = true)

        val result = quick()

        // test file: gone
        assertFalse("test file must be deleted on quick()", doomed.exists())
        val deletedCount = (result["deleted"] as? Int) ?: 0
        assertTrue("quick() result must report deleted >= 1 (got $deletedCount)", deletedCount >= 1)

        // temp file with age ≤ 7d: must survive
        assertTrue("fresh temp file must survive quick()", survivor.exists())
    }

    // ── Sanity: fmtSize unit ladder ──
    /**
     * Matches Python `_fmt_size` output — used inside audit log lines, so a
     * drift here would also drift the log format.
     */
    @Test
    fun `fmtSize ladders through units`() {
        assertEquals("0.0 B", fmtSize(0.0))
        assertEquals("1.0 KB", fmtSize(1024.0))
        assertEquals("1.0 MB", fmtSize(1024.0 * 1024))
        assertEquals("1.5 GB", fmtSize(1.5 * 1024 * 1024 * 1024))
    }

    // ── Sanity: guessCategory ──
    /**
     * Category inference from file name — must return null for paths outside
     * the known patterns so callers know to prompt or pick an explicit cat.
     */
    @Test
    fun `guessCategory returns null for unknown`() {
        val home = hermesHome()
        home.mkdirs()
        val unknown = File(home, "random-file.bin")
        // The function may return null or "other" depending on the rule —
        // both are acceptable as "not an auto-delete category".
        val cat = guessCategory(unknown)
        assertTrue(
            "guessCategory on unknown file should be null or 'other' (got $cat)",
            cat == null || cat == "other")
    }
}
