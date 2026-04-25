package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathSecurityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `path inside root returns null`() {
        val root = tmp.newFolder("sandbox")
        val inside = File(root, "sub/file.txt")
        inside.parentFile?.mkdirs()
        inside.writeText("ok")
        assertNull(validateWithinDir(inside, root))
    }

    @Test
    fun `path exactly at root returns null`() {
        val root = tmp.newFolder("sandbox")
        assertNull(validateWithinDir(root, root))
    }

    @Test
    fun `path outside root returns error message`() {
        val root = tmp.newFolder("sandbox")
        val sibling = tmp.newFolder("sibling")
        val msg = validateWithinDir(sibling, root)
        assertNotNull(msg)
        assertTrue(msg!!.contains("escapes"))
    }

    @Test
    fun `parent dir escape detected after canonicalization`() {
        val root = tmp.newFolder("sandbox")
        val escape = File(root, "../other")
        val msg = validateWithinDir(escape, root)
        assertNotNull(msg)
    }

    @Test
    fun `hasTraversalComponent catches dotdot in middle`() {
        assertTrue(hasTraversalComponent("a/b/../c"))
    }

    @Test
    fun `hasTraversalComponent catches leading dotdot`() {
        assertTrue(hasTraversalComponent("../secrets"))
    }

    @Test
    fun `hasTraversalComponent handles backslash separator`() {
        assertTrue(hasTraversalComponent("a\\b\\..\\c"))
    }

    @Test
    fun `hasTraversalComponent ignores dotdot as filename suffix`() {
        // "a..b" is a filename; not a traversal component
        assertFalse(hasTraversalComponent("a..b/c"))
    }

    @Test
    fun `hasTraversalComponent false for clean path`() {
        assertFalse(hasTraversalComponent("a/b/c.txt"))
    }

    @Test
    fun `hasTraversalComponent false for absolute clean path`() {
        assertFalse(hasTraversalComponent("/home/user/file.txt"))
    }
}
