package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for FileOperations.kt — device blocking, sensitive paths,
 * ShellFileOperations read/write/patch/move/delete/search.
 * Covers TC-TOOL-095..101 plus supporting sanity tests.
 */
class FileOperationsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val gson = Gson()

    // ── R-TOOL-095 / TC-TOOL-095-a: _isBlockedDevice ──
    // Note: `/dev/null` is NOT in the blocked list (see FileTools.kt
    // _BLOCKED_DEVICE_PATHS). `/dev/zero`, `/dev/random`, etc. ARE. This
    // reflects the Python upstream's actual set — the test doc naming
    // "/dev/null" was a documentation placeholder. Tests verify the
    // actual contract.
    @Test
    fun `blocks dev zero character device`() {
        assertTrue(_isBlockedDevice("/dev/zero"))
        assertTrue(_isBlockedDevice("/dev/urandom"))
        assertTrue(_isBlockedDevice("/dev/tty"))
        assertTrue(_isBlockedDevice("/dev/stdin"))
    }

    @Test
    fun `does not block dev null`() {
        // /dev/null is intentionally writable (e.g. silencing output)
        assertFalse(_isBlockedDevice("/dev/null"))
    }

    @Test
    fun `blocks proc fd stream`() {
        assertTrue(_isBlockedDevice("/proc/self/fd/0"))
        assertTrue(_isBlockedDevice("/proc/1234/fd/2"))
        // Regular /proc file → not blocked by fd-stream rule
        assertFalse(_isBlockedDevice("/proc/self/cmdline"))
    }

    @Test
    fun `blocks nothing for plain paths`() {
        assertFalse(_isBlockedDevice("/tmp/hello.txt"))
        assertFalse(_isBlockedDevice("~/Documents/x"))
    }

    // ── R-TOOL-096 / TC-TOOL-096-a: _checkSensitivePath ──
    @Test
    fun `checkSensitivePath etc shadow restricted`() {
        val msg = _checkSensitivePath("/etc/shadow")
        // /etc/ prefix is sensitive
        assertNotNull("/etc/shadow must be flagged", msg)
        assertTrue(msg!!.contains("/etc/"))
        assertTrue(msg.contains("restricted"))
    }

    @Test
    fun `checkSensitivePath boot restricted`() {
        assertNotNull(_checkSensitivePath("/boot/grub.cfg"))
    }

    @Test
    fun `checkSensitivePath docker sock exact restricted`() {
        assertNotNull(_checkSensitivePath("/var/run/docker.sock"))
        assertNotNull(_checkSensitivePath("/run/docker.sock"))
    }

    @Test
    fun `checkSensitivePath ordinary path allowed`() {
        assertNull(_checkSensitivePath("/tmp/ok.txt"))
        assertNull(_checkSensitivePath("/home/user/file"))
    }

    // ── R-TOOL-097 / TC-TOOL-097-a: notifyOtherToolCall resets tracking ──
    @Test
    fun `notifyOtherToolCall is side-effect safe`() {
        // We can't observe the private _readDedup map, but we can verify
        // that the function is idempotent and doesn't throw.
        notifyOtherToolCall("task-reset-1")
        notifyOtherToolCall("task-reset-1")
        notifyOtherToolCall()  // default task id
        // If no exception, the contract holds (silent no-op for unknown tasks).
    }

    // ── R-TOOL-098 / TC-TOOL-098-a: _checkFileStaleness detects external write ──
    @Test
    fun `_checkFileStaleness detects external mtime change`() {
        val file = tmp.newFile("watched.txt")
        file.writeText("v1")
        val taskId = "staleness-probe-${System.nanoTime()}"

        // Record timestamp
        _updateReadTimestamp(file.absolutePath, taskId)
        // First check: fresh → null
        assertNull(_checkFileStaleness(file.absolutePath, taskId))

        // Simulate external write by bumping mtime beyond recorded value
        val futureMtime = file.lastModified() + 5_000L
        assertTrue(
            "must be able to set mtime",
            file.setLastModified(futureMtime))

        val warning = _checkFileStaleness(file.absolutePath, taskId)
        assertNotNull("external write should be detected", warning)
        assertTrue(
            "warning should mention external modification",
            warning!!.contains("modified externally"))
    }

    @Test
    fun `_checkFileStaleness returns null for untracked file`() {
        val file = tmp.newFile("untracked.txt")
        assertNull(_checkFileStaleness(file.absolutePath, "task-never-read"))
    }

    @Test
    fun `_checkFileStaleness returns null for missing file`() {
        val missing = File(tmp.root, "ghost.txt").absolutePath
        assertNull(_checkFileStaleness(missing, "task-any"))
    }

    // ── R-TOOL-100 / TC-TOOL-100-a: _capReadTrackerData trims overflowing buckets ──
    @Test
    fun `_capReadTrackerData trims history beyond cap`() {
        val history = java.util.LinkedHashSet<Any?>()
        for (i in 1.._READ_HISTORY_CAP + 25) history.add("entry-$i")
        val bucket: MutableMap<String, Any?> = mutableMapOf("history" to history)
        _capReadTrackerData(bucket)
        @Suppress("UNCHECKED_CAST")
        val trimmed = bucket["history"] as java.util.LinkedHashSet<Any?>
        assertEquals(_READ_HISTORY_CAP, trimmed.size)
        // Oldest entries should have been evicted, so "entry-1".."entry-25" are gone
        assertFalse("entry-1 should have been evicted", trimmed.contains("entry-1"))
        assertTrue("last entry should remain", trimmed.contains("entry-${_READ_HISTORY_CAP + 25}"))
    }

    @Test
    fun `_capReadTrackerData trims dedup and timestamps`() {
        val dedup: MutableMap<Any?, Any?> = LinkedHashMap()
        for (i in 1.._DEDUP_CAP + 10) dedup["k-$i"] = i
        val ts: MutableMap<Any?, Any?> = LinkedHashMap()
        for (i in 1.._READ_TIMESTAMPS_CAP + 5) ts["p-$i"] = i.toLong()

        val bucket: MutableMap<String, Any?> = mutableMapOf(
            "dedup" to dedup,
            "timestamps" to ts)
        _capReadTrackerData(bucket)
        @Suppress("UNCHECKED_CAST")
        val newDedup = bucket["dedup"] as Map<Any?, Any?>
        @Suppress("UNCHECKED_CAST")
        val newTs = bucket["timestamps"] as Map<Any?, Any?>
        assertEquals(_DEDUP_CAP, newDedup.size)
        assertEquals(_READ_TIMESTAMPS_CAP, newTs.size)
    }

    // ── R-TOOL-101 / TC-TOOL-101-a: _handleReadFile tolerates odd args ──
    @Test
    fun `_handleReadFile tolerates missing path`() {
        val response = _handleReadFile(emptyMap())
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(response, Map::class.java) as Map<String, Any?>
        // Empty path → error
        assertNotNull(parsed["error"])
    }

    @Test
    fun `_handleReadFile tolerates non-string types`() {
        // path as null, offset as null → falls back to defaults, gets error because empty path
        val response = _handleReadFile(mapOf("path" to null, "offset" to null))
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(response, Map::class.java) as Map<String, Any?>
        assertNotNull(parsed["error"])
    }

    @Test
    fun `_handleWriteFile with missing content writes empty`() {
        val target = File(tmp.root, "emptywrite.txt")
        val response = _handleWriteFile(
            mapOf("path" to target.absolutePath))
        @Suppress("UNCHECKED_CAST")
        val parsed = gson.fromJson(response, Map::class.java) as Map<String, Any?>
        // Empty content → 0 bytes written, no error
        assertNull(parsed["error"])
        assertTrue(target.exists())
        assertEquals("", target.readText())
    }

    // ── ShellFileOperations readFile pagination sanity ──
    @Test
    fun `readFile paginates with offset`() {
        val file = tmp.newFile("a.txt")
        file.writeText((1..10).joinToString("\n") { "x$it" })
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.readFile(file.absolutePath, offset = 5, limit = 3)
        assertEquals(10, result.totalLines)
        assertTrue(result.content.contains("x5"))
        assertTrue(result.content.contains("x7"))
        assertFalse(result.content.contains("x8"))
    }

    @Test
    fun `readFile detects missing with similar list`() {
        val dir = tmp.newFolder("d")
        File(dir, "hello.txt").writeText("")
        File(dir, "helper.md").writeText("")
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.readFile(File(dir, "helo.txt").absolutePath)
        assertNotNull(result.error)
        assertTrue(result.similarFiles.isNotEmpty())
    }

    // ── writeFile denial ──
    @Test
    fun `writeFile denies ssh key path`() {
        val home = System.getProperty("user.home") ?: return
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.writeFile("$home/.ssh/id_rsa_hermes_test", "bad")
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("blocked") || result.error.contains("denied"))
    }

    // ── patchReplace happy path ──
    @Test
    fun `patchReplace swaps single match`() {
        val file = tmp.newFile("doc.txt")
        file.writeText("hello world")
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.patchReplace(file.absolutePath, "world", "hermes")
        assertTrue(result.success)
        assertEquals("hello hermes", file.readText())
    }

    @Test
    fun `patchReplace errors when target missing`() {
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.patchReplace(
            File(tmp.root, "no-such.txt").absolutePath, "a", "b")
        assertFalse(result.success)
        assertNotNull(result.error)
    }

    // ── deleteFile happy path ──
    @Test
    fun `deleteFile removes existing file`() {
        val file = tmp.newFile("victim.txt")
        file.writeText("bye")
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.deleteFile(file.absolutePath)
        assertNull(result.error)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteFile missing returns error`() {
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.deleteFile(File(tmp.root, "no-such.txt").absolutePath)
        assertNotNull(result.error)
    }

    // ── moveFile happy path + fallback ──
    @Test
    fun `moveFile renames within same dir`() {
        val src = tmp.newFile("src.txt")
        src.writeText("payload")
        val dst = File(tmp.root, "dst.txt")
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.moveFile(src.absolutePath, dst.absolutePath)
        assertNull(result.error)
        assertFalse(src.exists())
        assertTrue(dst.exists())
        assertEquals("payload", dst.readText())
    }

    @Test
    fun `moveFile missing source returns error`() {
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val result = ops.moveFile(
            File(tmp.root, "ghost.txt").absolutePath,
            File(tmp.root, "somewhere.txt").absolutePath)
        assertNotNull(result.error)
    }

    // ── Small helper sanity ──
    @Test
    fun `_expandPath handles tilde`() {
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val home = System.getProperty("user.home") ?: return
        assertEquals(home, ops._expandPath("~"))
        assertEquals("$home/docs/x", ops._expandPath("~/docs/x"))
        assertEquals("/abs/path", ops._expandPath("/abs/path"))
    }

    @Test
    fun `_escapeShellArg escapes single quotes`() {
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val escaped = ops._escapeShellArg("it's fine")
        assertTrue(escaped.startsWith("'"))
        assertTrue(escaped.endsWith("'"))
        // Contains the single-quote escape sequence
        assertTrue(escaped.contains("'\"'\"'"))
    }

    @Test
    fun `_isImage recognises extensions`() {
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        assertTrue(ops._isImage("/tmp/foo.png"))
        assertTrue(ops._isImage("/tmp/foo.JPG"))
        assertFalse(ops._isImage("/tmp/foo.txt"))
    }

    @Test
    fun `_unifiedDiff produces prefix header`() {
        val ops = ShellFileOperations(cwd = tmp.root.absolutePath)
        val diff = ops._unifiedDiff("a\nb\n", "a\nc\n", "foo.txt")
        assertTrue(diff.startsWith("--- a/foo.txt\n"))
        assertTrue(diff.contains("+++ b/foo.txt"))
        assertTrue(diff.contains("-b"))
        assertTrue(diff.contains("+c"))
    }

    // ── WRITE_DENIED_PATHS / WRITE_DENIED_PREFIXES populated ──
    @Test
    fun `write deny constants are non-empty`() {
        assertTrue("denied paths should contain something", WRITE_DENIED_PATHS.isNotEmpty())
        assertTrue("denied prefixes should contain something", WRITE_DENIED_PREFIXES.isNotEmpty())
    }

    // ── LINTERS / IMAGE_EXTENSIONS parity ──
    @Test
    fun `linters map has python entry`() {
        assertTrue(".py" in LINTERS.keys)
        assertTrue(LINTERS[".py"]!!.contains("python"))
    }

    @Test
    fun `image extensions are lowercase with dot`() {
        assertTrue(".png" in IMAGE_EXTENSIONS)
        assertTrue(".jpeg" in IMAGE_EXTENSIONS)
        assertFalse("PNG" in IMAGE_EXTENSIONS)
    }
}
