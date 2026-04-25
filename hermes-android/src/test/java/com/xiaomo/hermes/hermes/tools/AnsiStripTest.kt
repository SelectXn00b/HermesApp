package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AnsiStripTest {

    @Test
    fun `empty string returns as-is`() {
        val input = ""
        assertSame(input, stripAnsi(input))
    }

    @Test
    fun `plain text with no escapes returns as-is`() {
        val input = "hello world\nno escapes here"
        assertSame(input, stripAnsi(input))
    }

    @Test
    fun `CSI color codes stripped`() {
        val input = "\u001B[31mred\u001B[0m text"
        assertEquals("red text", stripAnsi(input))
    }

    @Test
    fun `CSI with colon-separated params stripped`() {
        val input = "\u001B[38:2:255:0:0mhi\u001B[0m"
        assertEquals("hi", stripAnsi(input))
    }

    @Test
    fun `private mode CSI with question mark prefix stripped`() {
        val input = "\u001B[?25l hidden \u001B[?25h"
        assertEquals(" hidden ", stripAnsi(input))
    }

    @Test
    fun `OSC with BEL terminator stripped`() {
        val input = "\u001B]0;window title\u0007prompt"
        assertEquals("prompt", stripAnsi(input))
    }

    @Test
    fun `OSC with ST terminator stripped`() {
        val input = "\u001B]0;title\u001B\\text"
        assertEquals("text", stripAnsi(input))
    }

    @Test
    fun `8-bit CSI 0x9b stripped`() {
        val input = "before\u009B31mafter"
        assertEquals("beforeafter", stripAnsi(input))
    }

    @Test
    fun `fast path skips regex when no escape byte present`() {
        val input = "no escape here"
        assertSame("no regex scan happens when _HAS_ESCAPE misses", input, stripAnsi(input))
    }

    @Test
    fun `multiple sequences stripped`() {
        val input = "\u001B[1mbold\u001B[0m and \u001B[4munderline\u001B[0m"
        assertEquals("bold and underline", stripAnsi(input))
    }
}
