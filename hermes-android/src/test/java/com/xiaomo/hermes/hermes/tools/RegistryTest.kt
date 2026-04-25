package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryTest {

    private fun freshRegistry() = ToolRegistry()

    @Test
    fun `register and getEntry round-trip`() {
        val r = freshRegistry()
        r.register(
            name = "foo",
            toolset = "core",
            schema = mapOf("description" to "foo tool"),
            handler = { _ -> "ok" },
        )
        val entry = r.getEntry("foo")
        assertNotNull(entry)
        assertEquals("foo", entry!!.name)
        assertEquals("core", entry.toolset)
        assertEquals("foo tool", entry.description)
    }

    @Test
    fun `register pulls description from schema when not supplied`() {
        val r = freshRegistry()
        r.register(
            name = "bar",
            toolset = "core",
            schema = mapOf("description" to "from schema"),
            handler = { _ -> "" },
        )
        assertEquals("from schema", r.getEntry("bar")!!.description)
    }

    @Test
    fun `register rejects shadowing tool in different toolset`() {
        val r = freshRegistry()
        r.register(name = "shared", toolset = "a", schema = emptyMap(), handler = { _ -> "a" })
        r.register(name = "shared", toolset = "b", schema = emptyMap(), handler = { _ -> "b" })
        // Second registration should be rejected → entry still from 'a'.
        assertEquals("a", r.getEntry("shared")!!.toolset)
    }

    @Test
    fun `register allows overwrite within same toolset`() {
        val r = freshRegistry()
        r.register(name = "dup", toolset = "same", schema = emptyMap(), handler = { _ -> "1" })
        r.register(name = "dup", toolset = "same", schema = emptyMap(), handler = { _ -> "2" })
        assertEquals("2", r.dispatch("dup", emptyMap()))
    }

    @Test
    fun `register allows overwrite across two mcp toolsets`() {
        val r = freshRegistry()
        r.register(name = "mcp_tool_x", toolset = "mcp-a", schema = emptyMap(), handler = { _ -> "a" })
        r.register(name = "mcp_tool_x", toolset = "mcp-b", schema = emptyMap(), handler = { _ -> "b" })
        assertEquals("mcp-b", r.getEntry("mcp_tool_x")!!.toolset)
    }

    @Test
    fun `deregister removes tool and strips toolset from requirements map`() {
        val r = freshRegistry()
        r.register(
            name = "t",
            toolset = "ts_dereg",
            schema = emptyMap(),
            handler = { _ -> "" },
            checkFn = { true },
        )
        r.deregister("t")
        assertNull(r.getEntry("t"))
        // After deregister, toolset is no longer tracked — not present in
        // checkToolsetRequirements (which iterates current tools).
        assertFalse("ts_dereg" in r.checkToolsetRequirements())
    }

    @Test
    fun `deregister of unknown name is a no-op`() {
        val r = freshRegistry()
        r.deregister("no_such_tool")  // should not throw
    }

    @Test
    fun `dispatch returns JSON error for unknown tool`() {
        val r = freshRegistry()
        val result = r.dispatch("no_such", emptyMap())
        val obj = JSONObject(result)
        assertTrue("Unknown tool" in obj.getString("error"))
    }

    @Test
    fun `dispatch wraps handler exceptions as JSON error`() {
        val r = freshRegistry()
        r.register(
            name = "exploder",
            toolset = "ts",
            schema = emptyMap(),
            handler = { _ -> throw IllegalStateException("boom") },
        )
        val result = r.dispatch("exploder", emptyMap())
        val obj = JSONObject(result)
        assertTrue("Tool execution failed" in obj.getString("error"))
        assertTrue("IllegalStateException" in obj.getString("error"))
        assertTrue("boom" in obj.getString("error"))
    }

    @Test
    fun `dispatch returns handler output verbatim on success`() {
        val r = freshRegistry()
        r.register(
            name = "ok",
            toolset = "ts",
            schema = emptyMap(),
            handler = { args -> "received=${args["k"]}" },
        )
        assertEquals("received=v", r.dispatch("ok", mapOf("k" to "v")))
    }

    @Test
    fun `getMaxResultSize returns entry override when set`() {
        val r = freshRegistry()
        r.register(
            name = "big",
            toolset = "ts",
            schema = emptyMap(),
            handler = { _ -> "" },
            maxResultSizeChars = 12345,
        )
        assertEquals(12345, r.getMaxResultSize("big"))
    }

    @Test
    fun `getMaxResultSize returns provided default for unknown tool`() {
        val r = freshRegistry()
        assertEquals(999, r.getMaxResultSize("nope", default = 999))
    }

    @Test
    fun `getMaxResultSize returns 50000 fallback when nothing set`() {
        val r = freshRegistry()
        assertEquals(50000, r.getMaxResultSize("nope"))
    }

    @Test
    fun `getAllToolNames returns sorted list`() {
        val r = freshRegistry()
        r.register(name = "zulu", toolset = "t", schema = emptyMap(), handler = { _ -> "" })
        r.register(name = "alpha", toolset = "t", schema = emptyMap(), handler = { _ -> "" })
        r.register(name = "mike", toolset = "t", schema = emptyMap(), handler = { _ -> "" })
        assertEquals(listOf("alpha", "mike", "zulu"), r.getAllToolNames())
    }

    @Test
    fun `getSchema returns null for unknown tool`() {
        val r = freshRegistry()
        assertNull(r.getSchema("unknown"))
    }

    @Test
    fun `getSchema returns schema map for registered tool`() {
        val r = freshRegistry()
        r.register(
            name = "has_schema",
            toolset = "t",
            schema = mapOf("description" to "d", "parameters" to mapOf("type" to "object")),
            handler = { _ -> "" },
        )
        val schema = r.getSchema("has_schema")
        assertNotNull(schema)
        assertEquals("d", schema!!["description"])
    }

    @Test
    fun `getToolsetForTool returns null for unknown tool`() {
        val r = freshRegistry()
        assertNull(r.getToolsetForTool("unknown"))
    }

    @Test
    fun `getEmoji returns entry emoji when present`() {
        val r = freshRegistry()
        r.register(
            name = "emoji_tool",
            toolset = "t",
            schema = emptyMap(),
            handler = { _ -> "" },
            emoji = "🚀",
        )
        assertEquals("🚀", r.getEmoji("emoji_tool"))
    }

    @Test
    fun `getEmoji returns default when none set`() {
        val r = freshRegistry()
        r.register(name = "plain", toolset = "t", schema = emptyMap(), handler = { _ -> "" })
        assertEquals("⚡", r.getEmoji("plain"))
        assertEquals("🔧", r.getEmoji("plain", default = "🔧"))
    }

    @Test
    fun `isToolsetAvailable true when check passes`() {
        val r = freshRegistry()
        r.register(
            name = "t",
            toolset = "avail",
            schema = emptyMap(),
            handler = { _ -> "" },
            checkFn = { true },
        )
        assertTrue(r.isToolsetAvailable("avail"))
    }

    @Test
    fun `isToolsetAvailable false when check throws`() {
        val r = freshRegistry()
        r.register(
            name = "t",
            toolset = "flaky",
            schema = emptyMap(),
            handler = { _ -> "" },
            checkFn = { throw RuntimeException("x") },
        )
        assertFalse(r.isToolsetAvailable("flaky"))
    }

    @Test
    fun `isToolsetAvailable true when no check registered`() {
        val r = freshRegistry()
        // Toolset with no checkFn → evaluateToolsetCheck(null) returns true.
        r.register(name = "t", toolset = "nocheck", schema = emptyMap(), handler = { _ -> "" })
        assertTrue(r.isToolsetAvailable("nocheck"))
    }

    @Test
    fun `registerToolsetAlias stores alias and retrieves target`() {
        val r = freshRegistry()
        r.registerToolsetAlias("short", "very_long_toolset_name")
        assertEquals("very_long_toolset_name", r.getToolsetAliasTarget("short"))
        assertEquals(mapOf("short" to "very_long_toolset_name"), r.getRegisteredToolsetAliases())
    }

    @Test
    fun `registerToolsetAlias overwrites collision`() {
        val r = freshRegistry()
        r.registerToolsetAlias("a", "one")
        r.registerToolsetAlias("a", "two")
        assertEquals("two", r.getToolsetAliasTarget("a"))
    }

    @Test
    fun `getToolsetAliasTarget returns null for unknown alias`() {
        val r = freshRegistry()
        assertNull(r.getToolsetAliasTarget("missing"))
    }

    @Test
    fun `getRegisteredToolsetNames is sorted and distinct`() {
        val r = freshRegistry()
        r.register(name = "a1", toolset = "b", schema = emptyMap(), handler = { _ -> "" })
        r.register(name = "a2", toolset = "a", schema = emptyMap(), handler = { _ -> "" })
        r.register(name = "a3", toolset = "a", schema = emptyMap(), handler = { _ -> "" })
        assertEquals(listOf("a", "b"), r.getRegisteredToolsetNames())
    }

    @Test
    fun `getToolNamesForToolset returns sorted names`() {
        val r = freshRegistry()
        r.register(name = "zulu", toolset = "shared", schema = emptyMap(), handler = { _ -> "" })
        r.register(name = "alpha", toolset = "shared", schema = emptyMap(), handler = { _ -> "" })
        r.register(name = "other", toolset = "different", schema = emptyMap(), handler = { _ -> "" })
        assertEquals(listOf("alpha", "zulu"), r.getToolNamesForToolset("shared"))
    }

    @Test
    fun `getDefinitions filters by checkFn`() {
        val r = freshRegistry()
        r.register(
            name = "ok_tool",
            toolset = "ok",
            schema = mapOf("description" to "d"),
            handler = { _ -> "" },
            checkFn = { true },
        )
        r.register(
            name = "blocked_tool",
            toolset = "blocked",
            schema = mapOf("description" to "d"),
            handler = { _ -> "" },
            checkFn = { false },
        )
        val defs = r.getDefinitions(setOf("ok_tool", "blocked_tool"))
        assertEquals(1, defs.size)
        @Suppress("UNCHECKED_CAST")
        val fn = defs[0]["function"] as Map<String, Any?>
        assertEquals("ok_tool", fn["name"])
    }

    @Test
    fun `getDefinitions skips unknown tool names`() {
        val r = freshRegistry()
        val defs = r.getDefinitions(setOf("no_such_tool"))
        assertTrue(defs.isEmpty())
    }

    @Test
    fun `checkToolsetRequirements evaluates each toolset once`() {
        val r = freshRegistry()
        r.register(
            name = "a",
            toolset = "ts_a",
            schema = emptyMap(),
            handler = { _ -> "" },
            checkFn = { true },
        )
        r.register(
            name = "b",
            toolset = "ts_b",
            schema = emptyMap(),
            handler = { _ -> "" },
            checkFn = { false },
        )
        val result = r.checkToolsetRequirements()
        assertEquals(true, result["ts_a"])
        assertEquals(false, result["ts_b"])
    }

    @Test
    fun `getAvailableToolsets returns tools grouped by toolset`() {
        val r = freshRegistry()
        r.register(
            name = "tool_1",
            toolset = "grp",
            schema = emptyMap(),
            handler = { _ -> "" },
            requiresEnv = listOf("ENV_A"),
        )
        r.register(
            name = "tool_2",
            toolset = "grp",
            schema = emptyMap(),
            handler = { _ -> "" },
            requiresEnv = listOf("ENV_A", "ENV_B"),
        )
        val result = r.getAvailableToolsets()
        val grp = result["grp"]!!
        @Suppress("UNCHECKED_CAST")
        val tools = grp["tools"] as List<String>
        assertTrue("tool_1" in tools)
        assertTrue("tool_2" in tools)
        @Suppress("UNCHECKED_CAST")
        val reqs = grp["requirements"] as List<String>
        assertTrue("ENV_A" in reqs)
        assertTrue("ENV_B" in reqs)
    }

    @Test
    fun `getToolsetRequirements exposes env_vars and tools`() {
        val r = freshRegistry()
        r.register(
            name = "only",
            toolset = "ts",
            schema = emptyMap(),
            handler = { _ -> "" },
            requiresEnv = listOf("FOO"),
        )
        val reqs = r.getToolsetRequirements()["ts"]!!
        assertEquals("ts", reqs["name"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("FOO"), reqs["env_vars"] as List<String>)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("only"), reqs["tools"] as List<String>)
    }

    @Test
    fun `checkToolAvailability partitions toolsets`() {
        val r = freshRegistry()
        r.register(
            name = "good",
            toolset = "ok_ts",
            schema = emptyMap(),
            handler = { _ -> "" },
            checkFn = { true },
        )
        r.register(
            name = "bad",
            toolset = "bad_ts",
            schema = emptyMap(),
            handler = { _ -> "" },
            checkFn = { false },
        )
        val (available, unavailable) = r.checkToolAvailability()
        assertTrue("ok_ts" in available)
        assertTrue(unavailable.any { it["name"] == "bad_ts" })
    }

    @Test
    fun `getToolToToolsetMap returns name-to-toolset mapping`() {
        val r = freshRegistry()
        r.register(name = "x", toolset = "t1", schema = emptyMap(), handler = { _ -> "" })
        r.register(name = "y", toolset = "t2", schema = emptyMap(), handler = { _ -> "" })
        val m = r.getToolToToolsetMap()
        assertEquals("t1", m["x"])
        assertEquals("t2", m["y"])
    }

    @Test
    fun `ToolEntry has sensible defaults`() {
        val e = ToolEntry()
        assertEquals("", e.name)
        assertEquals("", e.toolset)
        assertTrue(e.schema.isEmpty())
        assertNull(e.handler)
        assertNull(e.checkFn)
        assertTrue(e.requiresEnv.isEmpty())
        assertFalse(e.isAsync)
        assertNull(e.maxResultSizeChars)
    }

    @Test
    fun `toolError returns JSON with error message`() {
        val json = toolError("boom")
        val obj = JSONObject(json)
        assertEquals("boom", obj.getString("error"))
    }

    @Test
    fun `toolError merges extra keys`() {
        val json = toolError("boom", mapOf("code" to 42))
        val obj = JSONObject(json)
        assertEquals("boom", obj.getString("error"))
        assertEquals(42, obj.getInt("code"))
    }

    @Test
    fun `toolResult with data returns data as JSON`() {
        val json = toolResult(data = mapOf("hello" to "world"))
        val obj = JSONObject(json)
        assertEquals("world", obj.getString("hello"))
    }

    @Test
    fun `toolResult with kwargs returns kwargs JSON when data is null`() {
        val json = toolResult(kwargs = mapOf("a" to 1, "b" to "two"))
        val obj = JSONObject(json)
        assertEquals(1, obj.getInt("a"))
        assertEquals("two", obj.getString("b"))
    }

    @Test
    fun `discoverBuiltinTools returns empty list on Android`() {
        assertTrue(discoverBuiltinTools().isEmpty())
    }

    @Test
    fun `module-level registry singleton is reusable`() {
        // Don't dirty global singleton; just check it exists and responds.
        assertNotNull(registry)
        val name = "registry_singleton_test_${System.nanoTime()}"
        registry.register(name = name, toolset = "t", schema = emptyMap(), handler = { _ -> "ok" })
        try {
            assertEquals("ok", registry.dispatch(name, emptyMap()))
        } finally {
            registry.deregister(name)
        }
    }
}
