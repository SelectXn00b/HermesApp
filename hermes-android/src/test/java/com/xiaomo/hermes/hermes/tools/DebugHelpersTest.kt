package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DebugHelpersTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `disabled session returns stub sessionInfo`() {
        val session = DebugSession(toolName = "test_tool", enabled = false)
        assertFalse(session.active)
        val info = session.getSessionInfo()
        assertEquals(false, info["enabled"])
        assertNull(info["session_id"])
        assertNull(info["log_path"])
        assertEquals(0, info["total_calls"])
    }

    @Test
    fun `enabled session populates sessionInfo`() {
        val logDir = tmp.newFolder("logs")
        val session = DebugSession(toolName = "test_tool", logDir = logDir, enabled = true)
        assertTrue(session.active)
        val info = session.getSessionInfo()
        assertEquals(true, info["enabled"])
        assertNotNull(info["session_id"])
        assertTrue((info["session_id"] as String).isNotEmpty())
        assertEquals(0, info["total_calls"])
        assertTrue((info["log_path"] as String).contains("test_tool_debug_"))
    }

    @Test
    fun `logCall no-op when disabled`() {
        val session = DebugSession(toolName = "t", enabled = false)
        session.logCall("some_call", mapOf("arg" to "val"))
        assertEquals(0, session.getSessionInfo()["total_calls"])
    }

    @Test
    fun `logCall increments total_calls when enabled`() {
        val session = DebugSession(toolName = "t", logDir = tmp.newFolder("logs"), enabled = true)
        session.logCall("call1", mapOf("a" to 1))
        session.logCall("call2", mapOf("b" to 2))
        assertEquals(2, session.getSessionInfo()["total_calls"])
    }

    @Test
    fun `save writes JSON file with expected shape`() {
        val logDir = tmp.newFolder("logs")
        val session = DebugSession(toolName = "mytool", logDir = logDir, enabled = true)
        session.logCall("fetch", mapOf("url" to "https://x.com"))
        session.save()

        val sessionId = session.sessionId
        val out = File(logDir, "mytool_debug_${sessionId}.json")
        assertTrue("expected log file to exist at ${out.absolutePath}", out.exists())
        val content = out.readText(Charsets.UTF_8)
        assertTrue(content.contains("\"session_id\""))
        assertTrue(content.contains("\"tool_calls\""))
        assertTrue(content.contains("\"total_calls\""))
        assertTrue(content.contains("fetch"))
        assertTrue(content.contains("https://x.com"))
    }

    @Test
    fun `save no-op when disabled`() {
        val logDir = tmp.newFolder("logs")
        val session = DebugSession(toolName = "quiet", logDir = logDir, enabled = false)
        session.save()
        assertEquals(0, logDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `save swallows IO errors gracefully when logDir is null`() {
        val session = DebugSession(toolName = "nodir", logDir = null, enabled = true)
        session.logCall("x", mapOf("y" to 1))
        // Should not throw
        session.save()
    }
}
