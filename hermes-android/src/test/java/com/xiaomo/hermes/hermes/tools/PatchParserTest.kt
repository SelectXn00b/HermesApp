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

class PatchParserTest {

    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        tmpDir = Files.createTempDirectory("patch-parser-test").toFile()
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `parseV4aPatch empty input returns empty operations without error`() {
        val (ops, err) = parseV4aPatch("")
        assertTrue(ops.isEmpty())
        assertNull(err)
    }

    @Test
    fun `parseV4aPatch with only Begin Patch marker still returns empty`() {
        val patch = """
            *** Begin Patch
            *** End Patch
        """.trimIndent()
        val (ops, err) = parseV4aPatch(patch)
        assertTrue(ops.isEmpty())
        assertNull(err)
    }

    @Test
    fun `applyV4aOperations ADD creates new file with content`() {
        val op = PatchOperation(
            operation = OperationType.ADD,
            filePath = "new_file.txt",
            hunks = listOf(
                Hunk(lines = listOf(
                    HunkLine('+', "line 1"),
                    HunkLine('+', "line 2"),
                )),
            ),
        )
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertTrue(result.success)
        assertEquals(listOf("new_file.txt"), result.filesCreated)
        val created = File(tmpDir, "new_file.txt")
        assertTrue(created.exists())
        assertEquals("line 1\nline 2", created.readText())
    }

    @Test
    fun `applyV4aOperations ADD creates parent directories`() {
        val op = PatchOperation(
            operation = OperationType.ADD,
            filePath = "nested/dir/new.txt",
            hunks = listOf(Hunk(lines = listOf(HunkLine('+', "hi")))),
        )
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertTrue(result.success)
        assertTrue(File(tmpDir, "nested/dir/new.txt").exists())
    }

    @Test
    fun `applyV4aOperations DELETE removes existing file`() {
        val target = File(tmpDir, "victim.txt").apply { writeText("bye") }
        val op = PatchOperation(operation = OperationType.DELETE, filePath = "victim.txt")
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertTrue(result.success)
        assertEquals(listOf("victim.txt"), result.filesDeleted)
        assertFalse(target.exists())
    }

    @Test
    fun `applyV4aOperations DELETE reports error when file missing`() {
        val op = PatchOperation(operation = OperationType.DELETE, filePath = "ghost.txt")
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue("not found" in result.error!!)
    }

    @Test
    fun `applyV4aOperations MOVE renames file`() {
        File(tmpDir, "old.txt").writeText("keep")
        val op = PatchOperation(
            operation = OperationType.MOVE,
            filePath = "old.txt",
            newPath = "new.txt",
        )
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertTrue(result.success)
        assertFalse(File(tmpDir, "old.txt").exists())
        assertTrue(File(tmpDir, "new.txt").exists())
        assertEquals("keep", File(tmpDir, "new.txt").readText())
    }

    @Test
    fun `applyV4aOperations MOVE fails when source missing`() {
        val op = PatchOperation(
            operation = OperationType.MOVE,
            filePath = "nowhere.txt",
            newPath = "dest.txt",
        )
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertFalse(result.success)
    }

    @Test
    fun `applyV4aOperations UPDATE replaces matched hunk content`() {
        File(tmpDir, "u.txt").writeText("hello\nworld\n")
        val op = PatchOperation(
            operation = OperationType.UPDATE,
            filePath = "u.txt",
            hunks = listOf(
                Hunk(lines = listOf(
                    HunkLine('-', "hello"),
                    HunkLine('+', "goodbye"),
                    HunkLine(' ', "world"),
                )),
            ),
        )
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertTrue(result.success)
        val content = File(tmpDir, "u.txt").readText()
        assertTrue("goodbye" in content)
        assertFalse("hello\n" in content)
    }

    @Test
    fun `applyV4aOperations UPDATE fails when file missing`() {
        val op = PatchOperation(
            operation = OperationType.UPDATE,
            filePath = "nope.txt",
            hunks = listOf(Hunk(lines = listOf(HunkLine('-', "a"), HunkLine('+', "b")))),
        )
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertFalse(result.success)
        assertTrue("Cannot read file" in result.error!!)
    }

    @Test
    fun `applyV4aOperations UPDATE fails when hunk search text missing`() {
        File(tmpDir, "x.txt").writeText("different content\n")
        val op = PatchOperation(
            operation = OperationType.UPDATE,
            filePath = "x.txt",
            hunks = listOf(
                Hunk(lines = listOf(HunkLine('-', "not present"), HunkLine('+', "new"))),
            ),
        )
        val result = applyV4aOperations(listOf(op), tmpDir)
        assertFalse(result.success)
        assertTrue("Could not find hunk" in result.error!!)
    }

    @Test
    fun `applyV4aOperations batches results across multiple ops`() {
        File(tmpDir, "a.txt").writeText("a")
        File(tmpDir, "b.txt").writeText("b")
        val ops = listOf(
            PatchOperation(
                operation = OperationType.ADD,
                filePath = "c.txt",
                hunks = listOf(Hunk(lines = listOf(HunkLine('+', "c")))),
            ),
            PatchOperation(operation = OperationType.DELETE, filePath = "a.txt"),
            PatchOperation(
                operation = OperationType.MOVE,
                filePath = "b.txt",
                newPath = "b-renamed.txt",
            ),
        )
        val result = applyV4aOperations(ops, tmpDir)
        assertTrue(result.success)
        assertTrue("c.txt" in result.filesCreated)
        assertTrue("a.txt" in result.filesDeleted)
        assertTrue(result.filesModified.any { it.contains("->") })
    }

    @Test
    fun `PatchOperation toDict shape via applyV4aOperations result`() {
        val op = PatchOperation(
            operation = OperationType.ADD,
            filePath = "p.txt",
            hunks = listOf(Hunk(lines = listOf(HunkLine('+', "x")))),
        )
        val result = applyV4aOperations(listOf(op), tmpDir)
        val dict = result.toDict()
        assertEquals(true, dict["success"])
        @Suppress("UNCHECKED_CAST")
        assertTrue("p.txt" in (dict["files_created"] as List<String>))
    }

    @Test
    fun `OperationType has four enum values`() {
        assertEquals(4, OperationType.values().size)
        assertNotNull(OperationType.valueOf("ADD"))
        assertNotNull(OperationType.valueOf("UPDATE"))
        assertNotNull(OperationType.valueOf("DELETE"))
        assertNotNull(OperationType.valueOf("MOVE"))
    }

    @Test
    fun `HunkLine data class holds prefix and content`() {
        val line = HunkLine('+', "content")
        assertEquals('+', line.prefix)
        assertEquals("content", line.content)
    }

    @Test
    fun `Hunk has empty defaults`() {
        val h = Hunk()
        assertNull(h.contextHint)
        assertTrue(h.lines.isEmpty())
    }
}
