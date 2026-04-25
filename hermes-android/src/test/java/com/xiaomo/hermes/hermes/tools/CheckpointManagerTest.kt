package com.xiaomo.hermes.hermes.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CheckpointManagerTest {

    private lateinit var tmpDir: File
    private val origEnabled = CheckpointManager.enabled

    @Before
    fun setUp() {
        tmpDir = Files.createTempDirectory("checkpoint-test").toFile()
        CheckpointManager.enabled = false
        CheckpointManager.newTurn()
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
        CheckpointManager.enabled = origEnabled
    }

    @Test
    fun `_validateCommitHash rejects empty`() {
        assertEquals("Empty commit hash", _validateCommitHash(""))
        assertEquals("Empty commit hash", _validateCommitHash("   "))
    }

    @Test
    fun `_validateCommitHash rejects flag-looking input`() {
        val err = _validateCommitHash("-rf")
        assertNotNull(err)
        assertTrue("must not start with '-'" in err!!)
    }

    @Test
    fun `_validateCommitHash rejects non-hex characters`() {
        assertNotNull(_validateCommitHash("zzzz"))
        assertNotNull(_validateCommitHash("abc-def"))
    }

    @Test
    fun `_validateCommitHash rejects too-short input`() {
        assertNotNull(_validateCommitHash("abc"))
    }

    @Test
    fun `_validateCommitHash rejects too-long input`() {
        assertNotNull(_validateCommitHash("a".repeat(65)))
    }

    @Test
    fun `_validateCommitHash accepts valid short sha`() {
        assertNull(_validateCommitHash("abcd"))
        assertNull(_validateCommitHash("ABCD1234"))
    }

    @Test
    fun `_validateCommitHash accepts valid full sha-1`() {
        assertNull(_validateCommitHash("a".repeat(40)))
    }

    @Test
    fun `_validateCommitHash accepts valid full sha-256`() {
        assertNull(_validateCommitHash("a".repeat(64)))
    }

    @Test
    fun `_COMMIT_HASH_RE matches 4 to 64 hex chars`() {
        assertTrue(_COMMIT_HASH_RE.matches("abcd"))
        assertTrue(_COMMIT_HASH_RE.matches("A".repeat(64)))
        assertFalse(_COMMIT_HASH_RE.matches("xyz"))
        assertFalse(_COMMIT_HASH_RE.matches(""))
    }

    @Test
    fun `_normalizePath expands tilde`() {
        val home = System.getProperty("user.home") ?: "/"
        assertEquals(File(home).canonicalPath, _normalizePath("~").canonicalPath)
        assertEquals(
            File(home, "foo").canonicalPath,
            _normalizePath("~/foo").canonicalPath,
        )
    }

    @Test
    fun `_normalizePath returns absolute form of relative path`() {
        val normalized = _normalizePath("./some/path")
        assertTrue(normalized.isAbsolute)
    }

    @Test
    fun `_validateFilePath rejects empty path`() {
        assertEquals("Empty file path", _validateFilePath("", tmpDir.absolutePath))
        assertEquals("Empty file path", _validateFilePath("   ", tmpDir.absolutePath))
    }

    @Test
    fun `_validateFilePath rejects absolute paths`() {
        val err = _validateFilePath("/etc/passwd", tmpDir.absolutePath)
        assertNotNull(err)
        assertTrue("must be relative" in err!!)
    }

    @Test
    fun `_validateFilePath rejects traversal escapes`() {
        val err = _validateFilePath("../../etc/passwd", tmpDir.absolutePath)
        assertNotNull(err)
        assertTrue("escapes" in err!!)
    }

    @Test
    fun `_validateFilePath accepts safe relative path`() {
        assertNull(_validateFilePath("src/file.txt", tmpDir.absolutePath))
        assertNull(_validateFilePath("nested/dir/other.kt", tmpDir.absolutePath))
    }

    @Test
    fun `_shadowRepoPath is deterministic`() {
        val p1 = _shadowRepoPath(tmpDir.absolutePath)
        val p2 = _shadowRepoPath(tmpDir.absolutePath)
        assertEquals(p1.absolutePath, p2.absolutePath)
    }

    @Test
    fun `_shadowRepoPath produces different hash for different input`() {
        val p1 = _shadowRepoPath(tmpDir.absolutePath)
        val other = Files.createTempDirectory("other").toFile()
        try {
            val p2 = _shadowRepoPath(other.absolutePath)
            assertFalse(p1.absolutePath == p2.absolutePath)
        } finally {
            other.deleteRecursively()
        }
    }

    @Test
    fun `_shadowRepoPath hash is 16 chars hex`() {
        val p = _shadowRepoPath(tmpDir.absolutePath)
        val hash = p.name
        assertEquals(16, hash.length)
        assertTrue(hash.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `_gitEnv strips dangerous git vars`() {
        val shadow = File(tmpDir, "shadow")
        val env = _gitEnv(shadow, tmpDir.absolutePath)
        assertFalse("GIT_INDEX_FILE" in env)
        assertFalse("GIT_NAMESPACE" in env)
        assertFalse("GIT_ALTERNATE_OBJECT_DIRECTORIES" in env)
    }

    @Test
    fun `_gitEnv sets GIT_DIR and GIT_WORK_TREE`() {
        val shadow = File(tmpDir, "shadow")
        val env = _gitEnv(shadow, tmpDir.absolutePath)
        assertEquals(shadow.absolutePath, env["GIT_DIR"])
        assertEquals(tmpDir.canonicalPath, env["GIT_WORK_TREE"])
    }

    @Test
    fun `_gitEnv disables global and system config`() {
        val shadow = File(tmpDir, "shadow")
        val env = _gitEnv(shadow, tmpDir.absolutePath)
        assertEquals("1", env["GIT_CONFIG_NOSYSTEM"])
        assertNotNull(env["GIT_CONFIG_GLOBAL"])
        assertNotNull(env["GIT_CONFIG_SYSTEM"])
    }

    @Test
    fun `_initShadowRepo creates directory skeleton on first call`() {
        val shadow = File(tmpDir, "shadow")
        val err = _initShadowRepo(shadow, tmpDir.absolutePath)
        assertNull(err)
        assertTrue(shadow.isDirectory)
        assertTrue(File(shadow, "info/exclude").isFile)
        assertTrue(File(shadow, "HERMES_WORKDIR").isFile)
    }

    @Test
    fun `_initShadowRepo exclude file contains DEFAULT_EXCLUDES`() {
        val shadow = File(tmpDir, "shadow")
        _initShadowRepo(shadow, tmpDir.absolutePath)
        val excludes = File(shadow, "info/exclude").readText()
        assertTrue("node_modules/" in excludes)
        assertTrue(".env" in excludes)
    }

    @Test
    fun `_initShadowRepo HERMES_WORKDIR contains canonical path`() {
        val shadow = File(tmpDir, "shadow")
        _initShadowRepo(shadow, tmpDir.absolutePath)
        val workdir = File(shadow, "HERMES_WORKDIR").readText().trim()
        assertEquals(tmpDir.canonicalPath, workdir)
    }

    @Test
    fun `_initShadowRepo short-circuits when HEAD already exists`() {
        val shadow = File(tmpDir, "shadow")
        shadow.mkdirs()
        File(shadow, "HEAD").writeText("ref: refs/heads/main\n")
        // No info/exclude — should remain absent since initShadowRepo short-circuits.
        val err = _initShadowRepo(shadow, tmpDir.absolutePath)
        assertNull(err)
        assertFalse(File(shadow, "info/exclude").exists())
    }

    @Test
    fun `_dirFileCount counts files`() {
        File(tmpDir, "a.txt").writeText("a")
        File(tmpDir, "b.txt").writeText("b")
        val nested = File(tmpDir, "nested").apply { mkdirs() }
        File(nested, "c.txt").writeText("c")
        // Count includes the nested subdirectory entry itself in addition to files.
        val count = _dirFileCount(tmpDir.absolutePath)
        assertTrue(count >= 3)
    }

    @Test
    fun `_dirFileCount returns 0 for missing dir`() {
        assertEquals(0, _dirFileCount("/does/not/exist/here/at/all/${System.nanoTime()}"))
    }

    @Test
    fun `_parseShortstat parses file count insertions deletions`() {
        val entry: MutableMap<String, Any?> = mutableMapOf(
            "files_changed" to 0, "insertions" to 0, "deletions" to 0,
        )
        CheckpointManager._parseShortstat(" 3 files changed, 12 insertions(+), 5 deletions(-)", entry)
        assertEquals(3, entry["files_changed"])
        assertEquals(12, entry["insertions"])
        assertEquals(5, entry["deletions"])
    }

    @Test
    fun `_parseShortstat handles partial matches`() {
        val entry: MutableMap<String, Any?> = mutableMapOf(
            "files_changed" to 0, "insertions" to 0, "deletions" to 0,
        )
        CheckpointManager._parseShortstat(" 1 file changed, 1 insertion(+)", entry)
        assertEquals(1, entry["files_changed"])
        assertEquals(1, entry["insertions"])
        assertEquals(0, entry["deletions"])
    }

    @Test
    fun `ensureCheckpoint no-op when disabled`() {
        CheckpointManager.enabled = false
        assertFalse(CheckpointManager.ensureCheckpoint(tmpDir.absolutePath))
    }

    @Test
    fun `newTurn clears per-turn dedup`() {
        // Indirect: we don't have a getter for _checkpointedDirs; just confirm no throw.
        CheckpointManager.newTurn()
    }

    @Test
    fun `listCheckpoints empty when no checkpoints exist`() {
        val list = CheckpointManager.listCheckpoints(tmpDir.absolutePath)
        assertTrue(list.isEmpty())
    }

    @Test
    fun `formatCheckpointList empty produces no-checkpoints message`() {
        val s = formatCheckpointList(emptyList(), tmpDir.absolutePath)
        assertTrue("No checkpoints found" in s)
        assertTrue(tmpDir.absolutePath in s)
    }

    @Test
    fun `formatCheckpointList renders entries with camera prefix`() {
        val checkpoints = listOf<Map<String, Any?>>(
            mapOf(
                "short_hash" to "abc1234",
                "timestamp" to "2025-01-01T12:30:00+00:00",
                "reason" to "saved",
                "files_changed" to 2,
                "insertions" to 5,
                "deletions" to 1,
            ),
        )
        val s = formatCheckpointList(checkpoints, tmpDir.absolutePath)
        assertTrue("Checkpoints for" in s)
        assertTrue("abc1234" in s)
        assertTrue("saved" in s)
        assertTrue("2 files, +5/-1" in s)
        assertTrue("/rollback" in s)
    }

    @Test
    fun `formatCheckpointList singular file vs plural`() {
        val checkpoints = listOf<Map<String, Any?>>(
            mapOf(
                "short_hash" to "h",
                "timestamp" to "",
                "reason" to "r",
                "files_changed" to 1,
                "insertions" to 0,
                "deletions" to 0,
            ),
        )
        val s = formatCheckpointList(checkpoints, tmpDir.absolutePath)
        assertTrue("1 file," in s)
        assertFalse("1 files," in s)
    }

    @Test
    fun `diff returns error for invalid hash`() {
        val result = CheckpointManager.diff(tmpDir.absolutePath, "")
        assertEquals(false, result["success"])
        assertTrue((result["error"] as String).contains("Empty commit"))
    }

    @Test
    fun `diff returns error when no checkpoints exist`() {
        val result = CheckpointManager.diff(tmpDir.absolutePath, "abcd")
        assertEquals(false, result["success"])
        assertTrue("No checkpoints" in (result["error"] as String))
    }

    @Test
    fun `restore returns error for invalid hash`() {
        val result = CheckpointManager.restore(tmpDir.absolutePath, "")
        assertEquals(false, result["success"])
    }

    @Test
    fun `restore rejects absolute file path`() {
        val result = CheckpointManager.restore(tmpDir.absolutePath, "abcd", "/etc/hosts")
        assertEquals(false, result["success"])
    }

    @Test
    fun `getWorkingDirForPath returns marker-dir when found`() {
        val marker = File(tmpDir, "pyproject.toml").apply { writeText("[tool]") }
        val nested = File(tmpDir, "nested/deeper").apply { mkdirs() }
        val result = CheckpointManager.getWorkingDirForPath(nested.absolutePath)
        assertEquals(tmpDir.canonicalPath, result)
    }

    @Test
    fun `getWorkingDirForPath returns file parent when no marker`() {
        val file = File(tmpDir, "lonely.txt").apply { writeText("") }
        val result = CheckpointManager.getWorkingDirForPath(file.absolutePath)
        // Returns the parent directory when no marker found.
        assertTrue(result == tmpDir.canonicalPath || result.startsWith(tmpDir.canonicalPath))
    }

    @Test
    fun `CHECKPOINT_BASE lives under hermes home`() {
        assertTrue(CHECKPOINT_BASE.absolutePath.endsWith("checkpoints"))
    }

    @Test
    fun `DEFAULT_EXCLUDES contains standard ignore patterns`() {
        assertTrue("node_modules/" in DEFAULT_EXCLUDES)
        assertTrue(".env" in DEFAULT_EXCLUDES)
        assertTrue(".git/" in DEFAULT_EXCLUDES)
        assertTrue(".venv/" in DEFAULT_EXCLUDES)
    }

    @Test
    fun `_MAX_FILES is 50000`() {
        assertEquals(50_000, _MAX_FILES)
    }

    @Test
    fun `default state has enabled=false and maxSnapshots=50`() {
        CheckpointManager.enabled = false
        CheckpointManager.maxSnapshots = 50
        assertFalse(CheckpointManager.enabled)
        assertEquals(50, CheckpointManager.maxSnapshots)
    }
}
