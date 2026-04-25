package com.xiaomo.hermes.hermes.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuDriveToolTest {

    @After
    fun tearDown() {
        setClient(null)
    }

    @Test
    fun `getClient returns null when unset`() {
        setClient(null)
        assertNull(getClient())
    }

    @Test
    fun `setClient and getClient round-trip on same thread`() {
        val sentinel = Any()
        setClient(sentinel)
        assertTrue(getClient() === sentinel)
    }

    @Test
    fun `FEISHU_DRIVE_LIST_COMMENTS_SCHEMA has expected shape`() {
        val schema = FEISHU_DRIVE_LIST_COMMENTS_SCHEMA
        assertEquals("feishu_drive_list_comments", schema["name"])
        assertNotNull(schema["description"])
        @Suppress("UNCHECKED_CAST")
        val params = schema["parameters"] as Map<String, Any>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any>
        assertTrue("file_token" in props)
        assertTrue("file_type" in props)
        assertTrue("is_whole" in props)
        assertTrue("page_size" in props)
        assertTrue("page_token" in props)
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertEquals(listOf("file_token"), required)
    }

    @Test
    fun `FEISHU_DRIVE_LIST_REPLIES_SCHEMA has expected shape`() {
        val schema = FEISHU_DRIVE_LIST_REPLIES_SCHEMA
        assertEquals("feishu_drive_list_comment_replies", schema["name"])
        @Suppress("UNCHECKED_CAST")
        val params = schema["parameters"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertEquals(listOf("file_token", "comment_id"), required)
    }

    @Test
    fun `FEISHU_DRIVE_REPLY_SCHEMA has expected shape`() {
        val schema = FEISHU_DRIVE_REPLY_SCHEMA
        assertEquals("feishu_drive_reply_comment", schema["name"])
        @Suppress("UNCHECKED_CAST")
        val params = schema["parameters"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertEquals(listOf("file_token", "comment_id", "content"), required)
    }

    @Test
    fun `FEISHU_DRIVE_ADD_COMMENT_SCHEMA has expected shape`() {
        val schema = FEISHU_DRIVE_ADD_COMMENT_SCHEMA
        assertEquals("feishu_drive_add_comment", schema["name"])
        @Suppress("UNCHECKED_CAST")
        val params = schema["parameters"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertEquals(listOf("file_token", "content"), required)
    }

    @Test
    fun `registerFeishuDriveTools adds four entries to a fresh registry`() {
        val reg = ToolRegistry()
        registerFeishuDriveTools(reg)
        assertNotNull(reg.getEntry("feishu_drive_list_comments"))
        assertNotNull(reg.getEntry("feishu_drive_list_comment_replies"))
        assertNotNull(reg.getEntry("feishu_drive_reply_comment"))
        assertNotNull(reg.getEntry("feishu_drive_add_comment"))
        assertEquals(4, reg.getToolNamesForToolset("feishu_drive").size)
    }

    @Test
    fun `registered entries are sync and belong to feishu_drive toolset`() {
        val reg = ToolRegistry()
        registerFeishuDriveTools(reg)
        for (name in listOf(
            "feishu_drive_list_comments",
            "feishu_drive_list_comment_replies",
            "feishu_drive_reply_comment",
            "feishu_drive_add_comment",
        )) {
            val entry = reg.getEntry(name)!!
            assertEquals("feishu_drive", entry.toolset)
            assertFalse(entry.isAsync)
            assertNotNull(entry.handler)
        }
    }

    @Test
    fun `list_comments handler rejects missing file_token when client unset`() {
        setClient(null)
        val reg = ToolRegistry()
        registerFeishuDriveTools(reg)
        val result = reg.dispatch("feishu_drive_list_comments", emptyMap())
        // No client → "Feishu client not available".
        assertTrue(result.contains("Feishu client not available") || result.contains("error"))
    }

    @Test
    fun `list_comments handler rejects empty file_token when client present`() {
        setClient(Any())
        val reg = ToolRegistry()
        registerFeishuDriveTools(reg)
        val result = reg.dispatch("feishu_drive_list_comments", mapOf("file_token" to ""))
        assertTrue(result.contains("file_token is required"))
    }

    @Test
    fun `reply_comment handler rejects empty fields`() {
        setClient(Any())
        val reg = ToolRegistry()
        registerFeishuDriveTools(reg)
        val result = reg.dispatch(
            "feishu_drive_reply_comment",
            mapOf("file_token" to "abc", "comment_id" to "", "content" to "")
        )
        assertTrue(result.contains("required"))
    }

    @Test
    fun `add_comment handler rejects missing content`() {
        setClient(Any())
        val reg = ToolRegistry()
        registerFeishuDriveTools(reg)
        val result = reg.dispatch(
            "feishu_drive_add_comment",
            mapOf("file_token" to "abc")
        )
        assertTrue(result.contains("required"))
    }

    @Test
    fun `list_replies handler rejects missing comment_id`() {
        setClient(Any())
        val reg = ToolRegistry()
        registerFeishuDriveTools(reg)
        val result = reg.dispatch(
            "feishu_drive_list_comment_replies",
            mapOf("file_token" to "abc")
        )
        assertTrue(result.contains("required"))
    }

    @Test
    fun `list_comments returns error when SDK stub returns code -1`() {
        setClient(Any())
        val reg = ToolRegistry()
        registerFeishuDriveTools(reg)
        val result = reg.dispatch(
            "feishu_drive_list_comments",
            mapOf("file_token" to "abc")
        )
        // _doRequest stub returns (-1, "Lark SDK not available ...").
        assertTrue(result.contains("Lark SDK not available") || result.contains("List comments failed"))
    }
}
