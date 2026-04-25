package com.xiaomo.hermes.hermes.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuDocToolTest {

    @After
    fun tearDown() {
        FeishuDocTool.setClient(null)
    }

    @Test
    fun `getClient returns null when unset`() {
        FeishuDocTool.setClient(null)
        assertNull(FeishuDocTool.getClient())
    }

    @Test
    fun `setClient and getClient round-trip on same thread`() {
        val sentinel = Any()
        FeishuDocTool.setClient(sentinel)
        assertTrue(FeishuDocTool.getClient() === sentinel)
    }

    @Test
    fun `_checkFeishu does not throw`() {
        // Whether lark_oapi is on the test classpath depends on build config;
        // just verify the function returns a Boolean without throwing.
        FeishuDocTool._checkFeishu()
    }

    @Test
    fun `_handleFeishuDocRead rejects empty doc_token`() {
        FeishuDocTool.setClient(null)
        val result = FeishuDocTool._handleFeishuDocRead(mapOf("doc_token" to ""))
        assertEquals(false, result["success"])
        assertTrue((result["error"] as String).contains("doc_token"))
    }

    @Test
    fun `_handleFeishuDocRead rejects whitespace-only doc_token`() {
        FeishuDocTool.setClient(null)
        val result = FeishuDocTool._handleFeishuDocRead(mapOf("doc_token" to "   "))
        assertEquals(false, result["success"])
    }

    @Test
    fun `_handleFeishuDocRead rejects missing doc_token`() {
        FeishuDocTool.setClient(null)
        val result = FeishuDocTool._handleFeishuDocRead(emptyMap())
        assertEquals(false, result["success"])
        assertTrue((result["error"] as String).contains("doc_token"))
    }

    @Test
    fun `_handleFeishuDocRead fails when no client set`() {
        FeishuDocTool.setClient(null)
        val result = FeishuDocTool._handleFeishuDocRead(mapOf("doc_token" to "abc123"))
        assertEquals(false, result["success"])
        assertTrue((result["error"] as String).contains("client"))
    }

    @Test
    fun `_handleFeishuDocRead returns error when SDK stub returns null`() {
        FeishuDocTool.setClient(Any())
        val result = FeishuDocTool._handleFeishuDocRead(mapOf("doc_token" to "abc123"))
        // executeDocRequest stub returns null → "No content returned" error.
        assertEquals(false, result["success"])
        assertTrue((result["error"] as String).contains("No content"))
    }

    @Test
    fun `FEISHU_DOC_READ_SCHEMA has expected shape`() {
        val schema = FeishuDocTool.FEISHU_DOC_READ_SCHEMA
        assertEquals("feishu_doc_read", schema["name"])
        assertNotNull(schema["description"])
        @Suppress("UNCHECKED_CAST")
        val params = schema["parameters"] as Map<String, Any>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any>
        assertTrue("doc_token" in props)
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertTrue("doc_token" in required)
    }

    @Test
    fun `REGISTRATION has expected shape`() {
        val reg = FeishuDocTool.REGISTRATION
        assertEquals("feishu_doc_read", reg["name"])
        assertEquals("feishu_doc", reg["toolset"])
        assertEquals(false, reg["isAsync"])
        assertNotNull(reg["description"])
        assertNotNull(reg["emoji"])
        assertNotNull(reg["schema"])
    }
}
