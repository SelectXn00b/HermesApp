package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryExtensionsTest {

    @Test
    fun `png flagged as binary`() {
        assertTrue(hasBinaryExtension("image.png"))
    }

    @Test
    fun `uppercase extension flagged via lowercase check`() {
        assertTrue(hasBinaryExtension("photo.JPG"))
    }

    @Test
    fun `path with directory and binary name flagged`() {
        assertTrue(hasBinaryExtension("/a/b/archive.tar.gz"))
    }

    @Test
    fun `pdf treated as non-binary per comment`() {
        // PDF is intentionally excluded from BINARY_EXTENSIONS — agent may want to read.
        assertFalse(hasBinaryExtension("doc.pdf"))
    }

    @Test
    fun `kotlin source file not flagged`() {
        assertFalse(hasBinaryExtension("Foo.kt"))
    }

    @Test
    fun `filename with no extension returns false`() {
        assertFalse(hasBinaryExtension("Makefile"))
    }

    @Test
    fun `empty string returns false`() {
        assertFalse(hasBinaryExtension(""))
    }

    @Test
    fun `class file flagged`() {
        assertTrue(hasBinaryExtension("Foo.class"))
    }

    @Test
    fun `sqlite database flagged`() {
        assertTrue(hasBinaryExtension("app.sqlite"))
    }

    @Test
    fun `set contains png via public constant`() {
        assertTrue(".png" in BINARY_EXTENSIONS)
        assertTrue(".zip" in BINARY_EXTENSIONS)
        assertFalse(".pdf" in BINARY_EXTENSIONS)
    }
}
