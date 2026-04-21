package com.ai.assistance.operit.util.stream.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.stream.asCharStream
import com.ai.assistance.operit.util.stream.splitBy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamJsonPluginTest {

    // --- Tests for StreamJsonPlugin (extracts full JSON) ---

    @Test
    fun testSimpleJsonObject() = runBlocking {
        val json = """{"key":"value","number":123}"""
        val stream = "Some text before $json and some after".asCharStream()
        val plugin = StreamJsonPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)
        assertNull(groups[0].tag)
        assertEquals("Some text before ", groups[0].content)

        assertSame(plugin, groups[1].tag)
        assertEquals(json, groups[1].content)

        assertNull(groups[2].tag)
        assertEquals(" and some after", groups[2].content)
    }

    @Test
    fun testSimpleJsonArray() = runBlocking {
        val json = """["apple", "banana", 1, true]"""
        val stream = "Data: $json".asCharStream()
        val plugin = StreamJsonPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(2, groups.size)
        assertEquals("Data: ", groups[0].content)
        assertSame(plugin, groups[1].tag)
        assertEquals(json, groups[1].content)
    }

    @Test
    fun testNestedJsonObject() = runBlocking {
        val json = """{"user":{"name":"test","roles":["admin","editor"]}}"""
        val stream = json.asCharStream()
        val plugin = StreamJsonPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(1, groups.size)
        assertSame(plugin, groups[0].tag)
        assertEquals(json, groups[0].content)
    }

    @Test
    fun testJsonWithEscapedChars() = runBlocking {
        val json = """{"key":"value with \"escaped quote\" and \\ backslash"}"""
        val stream = json.asCharStream()
        val plugin = StreamJsonPlugin()

        val groups = collectGroups(stream, plugin)
        assertEquals(1, groups.size)
        assertEquals(json, groups[0].content)
    }

    @Test
    fun testJsonWithStringsContainingBraces() = runBlocking {
        val json = """{"q":"SELECT * FROM users WHERE name = 'John {Doe}'"}"""
        val stream = json.asCharStream()
        val plugin = StreamJsonPlugin()

        val groups = collectGroups(stream, plugin)
        assertEquals(1, groups.size)
        assertEquals(json, groups[0].content)
    }

    // --- Tests for StreamPureJsonPlugin (extracts and filters content) ---

    @Test
    fun testSimpleJsonObject_Pure() = runBlocking {
        val json = """{"key": "value", "number": 123}"""
        val stream = "Ignore this. $json And this.".asCharStream()
        val plugin = StreamPureJsonPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals("Should produce 3 groups: before, json, after", 3, groups.size)
        assertEquals("keyvalue123", groups[1].content)
        assertEquals(" And this.", groups[2].content)
    }

    @Test
    fun testSimpleJsonArray_Pure() = runBlocking {
        val json = """[ "apple", "banana", 1, true ]"""
        val stream = json.asCharStream()
        val plugin = StreamPureJsonPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(1, groups.size)
        assertSame(plugin, groups[0].tag)
        assertEquals("applebanana1true", groups[0].content)
    }

    @Test
    fun testNestedJsonObject_Pure() = runBlocking {
        val json =
                """
        {
            "user": {
                "name": "test",
                "active": true
            },
            "permissions": [ "read", "write" ]
        }
        """.trimIndent()
        val stream = json.asCharStream()
        val plugin = StreamPureJsonPlugin()

        val groups = collectGroups(stream, plugin)
        assertEquals(1, groups.size)
        assertSame(plugin, groups[0].tag)
        assertEquals("usernametestactivepermissionsreadwrite", groups[0].content)
    }

    @Test
    fun testPureWithEscapedChars() = runBlocking {
        val json = """{"key":"value with \"escaped quote\" and \\ backslash"}"""
        val stream = json.asCharStream()
        val plugin = StreamPureJsonPlugin()

        val groups = collectGroups(stream, plugin)
        assertEquals(1, groups.size)
        assertEquals("""keyvaluewith"escaped quote"and\backslash""", groups[0].content)
    }

    // --- Helper function ---
    private suspend fun collectGroups(
        stream: com.ai.assistance.operit.util.stream.Stream<Char>,
        plugin: StreamPlugin
    ): List<GroupInfo> {
        val groupList = mutableListOf<GroupInfo>()
        stream.splitBy(listOf(plugin)).collect { group ->
            val content = StringBuilder()
            group.stream.collect { content.append(it) }
            groupList.add(GroupInfo(group.tag, content.toString()))
        }
        return groupList
    }

    private data class GroupInfo(val tag: StreamPlugin?, val content: String)
}
