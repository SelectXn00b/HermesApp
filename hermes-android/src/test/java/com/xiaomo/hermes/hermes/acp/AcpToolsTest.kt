package com.xiaomo.hermes.hermes.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the pure-logic surface of the ACP Tools object:
 *   TOOL_KIND_MAP, getToolKind, makeToolCallId, buildToolTitle, extractLocations,
 *   buildToolStart, buildToolComplete, and their data classes.
 *
 * Requirement map: R-ACP-001..043 (see docs/hermes-requirements.md)
 * Test cases:      TC-ACP-001..043 (see docs/hermes-test-cases.md)
 */
class AcpToolsTest {

    // ---- TOOL_KIND_MAP + getToolKind ----

    @Test
    fun `TOOL_KIND_MAP has canonical file-op bindings`() {
        assertEquals("read", Tools.TOOL_KIND_MAP["read_file"])
        assertEquals("edit", Tools.TOOL_KIND_MAP["write_file"])
        assertEquals("edit", Tools.TOOL_KIND_MAP["patch"])
        assertEquals("search", Tools.TOOL_KIND_MAP["search_files"])
    }

    @Test
    fun `TOOL_KIND_MAP has execute bindings`() {
        assertEquals("execute", Tools.TOOL_KIND_MAP["terminal"])
        assertEquals("execute", Tools.TOOL_KIND_MAP["process"])
        assertEquals("execute", Tools.TOOL_KIND_MAP["execute_code"])
    }

    @Test
    fun `TOOL_KIND_MAP has web fetch bindings`() {
        assertEquals("fetch", Tools.TOOL_KIND_MAP["web_search"])
        assertEquals("fetch", Tools.TOOL_KIND_MAP["web_extract"])
        assertEquals("fetch", Tools.TOOL_KIND_MAP["browser_navigate"])
    }

    @Test
    fun `TOOL_KIND_MAP has thinking binding`() {
        assertEquals("think", Tools.TOOL_KIND_MAP["_thinking"])
    }

    @Test
    fun `getToolKind returns mapping when present`() {
        assertEquals("read", Tools.getToolKind("read_file"))
        assertEquals("edit", Tools.getToolKind("patch"))
    }

    @Test
    fun `getToolKind falls back to other for unknown tool`() {
        assertEquals("other", Tools.getToolKind("some_unknown_tool"))
        assertEquals("other", Tools.getToolKind(""))
    }

    // ---- makeToolCallId ----

    @Test
    fun `makeToolCallId produces prefixed id`() {
        val id = Tools.makeToolCallId()
        assertTrue("got: $id", id.startsWith("tc-"))
        // 3 (prefix) + 12 (take)
        assertEquals(15, id.length)
    }

    @Test
    fun `makeToolCallId returns fresh value each call`() {
        val a = Tools.makeToolCallId()
        val b = Tools.makeToolCallId()
        // Random UUID collision is astronomically unlikely in 2 calls
        assertTrue("ids collided unexpectedly", a != b)
    }

    // ---- buildToolTitle ----

    @Test
    fun `buildToolTitle terminal short command`() {
        val t = Tools.buildToolTitle("terminal", mapOf("command" to "ls -la"))
        assertEquals("terminal: ls -la", t)
    }

    @Test
    fun `buildToolTitle terminal long command truncates at 80`() {
        val cmd = "x".repeat(200)
        val t = Tools.buildToolTitle("terminal", mapOf("command" to cmd))
        // prefix "terminal: " + 77 chars + "..."
        assertEquals("terminal: ", t.take(10))
        assertTrue("got len ${t.length}: $t", t.endsWith("..."))
        // 80 cap happens inside, then prefix is added
        assertEquals(10 + 77 + 3, t.length)
    }

    @Test
    fun `buildToolTitle terminal with missing command`() {
        val t = Tools.buildToolTitle("terminal", emptyMap())
        assertEquals("terminal: ", t)
    }

    @Test
    fun `buildToolTitle read_file and write_file use path`() {
        assertEquals("read: /tmp/a", Tools.buildToolTitle("read_file", mapOf("path" to "/tmp/a")))
        assertEquals("write: /tmp/b", Tools.buildToolTitle("write_file", mapOf("path" to "/tmp/b")))
    }

    @Test
    fun `buildToolTitle read_file with missing path uses question mark`() {
        assertEquals("read: ?", Tools.buildToolTitle("read_file", emptyMap()))
    }

    @Test
    fun `buildToolTitle patch includes mode`() {
        val t = Tools.buildToolTitle("patch", mapOf("mode" to "append", "path" to "/tmp/c"))
        assertEquals("patch (append): /tmp/c", t)
    }

    @Test
    fun `buildToolTitle patch defaults mode to replace`() {
        val t = Tools.buildToolTitle("patch", mapOf("path" to "/tmp/c"))
        assertEquals("patch (replace): /tmp/c", t)
    }

    @Test
    fun `buildToolTitle search_files`() {
        assertEquals("search: foo", Tools.buildToolTitle("search_files", mapOf("pattern" to "foo")))
    }

    @Test
    fun `buildToolTitle web_search`() {
        assertEquals(
            "web search: weather",
            Tools.buildToolTitle("web_search", mapOf("query" to "weather"))
        )
    }

    @Test
    fun `buildToolTitle web_extract single url`() {
        val t = Tools.buildToolTitle("web_extract", mapOf("urls" to listOf("http://a")))
        assertEquals("extract: http://a", t)
    }

    @Test
    fun `buildToolTitle web_extract multiple urls shows count`() {
        val t = Tools.buildToolTitle(
            "web_extract",
            mapOf("urls" to listOf("http://a", "http://b", "http://c"))
        )
        assertEquals("extract: http://a (+2)", t)
    }

    @Test
    fun `buildToolTitle web_extract empty falls back`() {
        val t = Tools.buildToolTitle("web_extract", mapOf("urls" to emptyList<String>()))
        assertEquals("web extract", t)
    }

    @Test
    fun `buildToolTitle delegate_task short goal`() {
        val t = Tools.buildToolTitle("delegate_task", mapOf("goal" to "hello"))
        assertEquals("delegate: hello", t)
    }

    @Test
    fun `buildToolTitle delegate_task long goal truncates at 60`() {
        val goal = "g".repeat(100)
        val t = Tools.buildToolTitle("delegate_task", mapOf("goal" to goal))
        // "delegate: " (10) + 57 chars + "..." = 70
        assertEquals(10 + 57 + 3, t.length)
        assertTrue(t.endsWith("..."))
    }

    @Test
    fun `buildToolTitle delegate_task missing goal uses generic label`() {
        assertEquals("delegate task", Tools.buildToolTitle("delegate_task", emptyMap()))
    }

    @Test
    fun `buildToolTitle execute_code`() {
        assertEquals("execute code", Tools.buildToolTitle("execute_code", emptyMap()))
    }

    @Test
    fun `buildToolTitle vision_analyze truncates question`() {
        val q = "q".repeat(200)
        val t = Tools.buildToolTitle("vision_analyze", mapOf("question" to q))
        // "analyze image: " (15) + first 50 chars of q
        assertEquals("analyze image: " + "q".repeat(50), t)
    }

    @Test
    fun `buildToolTitle vision_analyze missing uses placeholder`() {
        assertEquals("analyze image: ?", Tools.buildToolTitle("vision_analyze", emptyMap()))
    }

    @Test
    fun `buildToolTitle falls back to tool name when unhandled`() {
        assertEquals("random_tool", Tools.buildToolTitle("random_tool", emptyMap()))
    }

    // ---- extractLocations ----

    @Test
    fun `extractLocations returns empty when no path`() {
        val locs = Tools.extractLocations(mapOf("other" to "thing"))
        assertTrue(locs.isEmpty())
    }

    @Test
    fun `extractLocations picks up path only`() {
        val locs = Tools.extractLocations(mapOf("path" to "/tmp/x"))
        assertEquals(1, locs.size)
        assertEquals("/tmp/x", locs[0].path)
        assertNull(locs[0].line)
    }

    @Test
    fun `extractLocations picks up path plus offset as line`() {
        val locs = Tools.extractLocations(mapOf("path" to "/tmp/x", "offset" to 42))
        assertEquals(42, locs[0].line)
    }

    @Test
    fun `extractLocations prefers offset over line when both present`() {
        // reads "offset ?: line", so offset wins
        val locs = Tools.extractLocations(mapOf("path" to "/tmp/x", "offset" to 10, "line" to 20))
        assertEquals(10, locs[0].line)
    }

    @Test
    fun `extractLocations accepts bare line when no offset`() {
        val locs = Tools.extractLocations(mapOf("path" to "/tmp/x", "line" to 7))
        assertEquals(7, locs[0].line)
    }

    // ---- buildToolStart ----

    @Test
    fun `buildToolStart wires toolCallId title kind locations rawInput`() {
        val start = Tools.buildToolStart(
            toolCallId = "tc-abc",
            toolName = "read_file",
            arguments = mapOf("path" to "/tmp/a", "offset" to 3)
        )
        assertEquals("tc-abc", start.toolCallId)
        assertEquals("read: /tmp/a", start.title)
        assertEquals("read", start.kind)
        assertEquals(1, start.locations.size)
        assertEquals("/tmp/a", start.locations[0].path)
        assertEquals(3, start.locations[0].line)
        assertEquals(mapOf("path" to "/tmp/a", "offset" to 3), start.rawInput)
    }

    @Test
    fun `buildToolStart write_file produces diff content`() {
        val start = Tools.buildToolStart(
            "tc-1",
            "write_file",
            mapOf("path" to "/tmp/out", "content" to "hi")
        )
        assertEquals(1, start.content.size)
        val diff = start.content[0] as Tools.DiffContent
        assertEquals("/tmp/out", diff.path)
        assertEquals("hi", diff.newText)
        assertNull(diff.oldText)
    }

    @Test
    fun `buildToolStart patch replace mode produces diff content`() {
        val start = Tools.buildToolStart(
            "tc-1",
            "patch",
            mapOf(
                "mode" to "replace",
                "path" to "/tmp/p",
                "old_string" to "a",
                "new_string" to "b"
            )
        )
        val diff = start.content[0] as Tools.DiffContent
        assertEquals("/tmp/p", diff.path)
        assertEquals("a", diff.oldText)
        assertEquals("b", diff.newText)
    }

    @Test
    fun `buildToolStart terminal renders command with dollar prefix`() {
        val start = Tools.buildToolStart("tc-1", "terminal", mapOf("command" to "ls"))
        val wrapper = start.content[0] as Tools.ToolContent
        val block = wrapper.block as Tools.TextBlock
        assertEquals("$ ls", block.text)
    }

    @Test
    fun `buildToolStart read_file renders reading message`() {
        val start = Tools.buildToolStart("tc-1", "read_file", mapOf("path" to "/tmp/z"))
        val block = (start.content[0] as Tools.ToolContent).block as Tools.TextBlock
        assertEquals("Reading /tmp/z", block.text)
    }

    @Test
    fun `buildToolStart search_files renders searching message`() {
        val start = Tools.buildToolStart(
            "tc-1",
            "search_files",
            mapOf("pattern" to "foo", "target" to "files")
        )
        val block = (start.content[0] as Tools.ToolContent).block as Tools.TextBlock
        assertEquals("Searching for 'foo' (files)", block.text)
    }

    @Test
    fun `buildToolStart search_files defaults target to content`() {
        val start = Tools.buildToolStart("tc-1", "search_files", mapOf("pattern" to "foo"))
        val block = (start.content[0] as Tools.ToolContent).block as Tools.TextBlock
        assertTrue("got: ${block.text}", block.text.endsWith("(content)"))
    }

    @Test
    fun `buildToolStart generic tool falls through to json dump`() {
        val start = Tools.buildToolStart("tc-1", "mystery", mapOf("k" to "v"))
        val block = (start.content[0] as Tools.ToolContent).block as Tools.TextBlock
        // JSON output (indent 2) contains the key and value
        assertTrue("got: ${block.text}", block.text.contains("\"k\""))
        assertTrue("got: ${block.text}", block.text.contains("\"v\""))
    }

    // ---- buildToolComplete ----

    @Test
    fun `buildToolComplete wires id kind status`() {
        val done = Tools.buildToolComplete("tc-1", "read_file", result = "hello")
        assertEquals("tc-1", done.toolCallId)
        assertEquals("read", done.kind)
        assertEquals("completed", done.status)
        assertEquals("hello", done.rawOutput)
    }

    @Test
    fun `buildToolComplete null result becomes empty text block`() {
        val done = Tools.buildToolComplete("tc-1", "read_file", result = null)
        val block = (done.content[0] as Tools.ToolContent).block as Tools.TextBlock
        assertEquals("", block.text)
    }

    @Test
    fun `buildToolComplete truncates long result`() {
        val big = "x".repeat(6000)
        val done = Tools.buildToolComplete("tc-1", "read_file", result = big)
        val block = (done.content[0] as Tools.ToolContent).block as Tools.TextBlock
        // 4900 chars + "\n... (6000 chars total, truncated)"
        assertTrue("got len ${block.text.length}", block.text.length < big.length)
        assertTrue(block.text.contains("truncated"))
        assertTrue(block.text.contains("6000 chars total"))
    }

    @Test
    fun `buildToolComplete short result passes through`() {
        val done = Tools.buildToolComplete("tc-1", "read_file", result = "hi")
        val block = (done.content[0] as Tools.ToolContent).block as Tools.TextBlock
        assertEquals("hi", block.text)
    }

    // ---- data class sanity ----

    @Test
    fun `ToolCallLocation equality`() {
        assertEquals(
            Tools.ToolCallLocation("/a", 1),
            Tools.ToolCallLocation("/a", 1)
        )
    }

    @Test
    fun `TextBlock and DiffContent equality`() {
        assertEquals(Tools.TextBlock("x"), Tools.TextBlock("x"))
        assertEquals(
            Tools.DiffContent("/p", oldText = "a", newText = "b"),
            Tools.DiffContent("/p", oldText = "a", newText = "b")
        )
    }
}
