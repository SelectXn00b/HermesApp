package com.xiaomo.hermes.hermes.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCachingTest {

    // ---- buildCachedSystemPrompt ----

    @Test
    fun `buildCachedSystemPrompt disabled returns plain text block`() {
        val out = buildCachedSystemPrompt("hello", enabled = false)
        assertEquals(1, out.size)
        assertEquals("text", out[0]["type"])
        assertEquals("hello", out[0]["text"])
        assertFalse(out[0].containsKey("cache_control"))
    }

    @Test
    fun `buildCachedSystemPrompt enabled adds ephemeral cache_control`() {
        val out = buildCachedSystemPrompt("sys prompt", enabled = true)
        assertEquals(1, out.size)
        val entry = out[0]
        assertEquals("text", entry["type"])
        assertEquals("sys prompt", entry["text"])
        @Suppress("UNCHECKED_CAST")
        val cache = entry["cache_control"] as Map<String, Any>
        assertEquals("ephemeral", cache["type"])
    }

    @Test
    fun `buildCachedSystemPrompt default enabled true`() {
        val out = buildCachedSystemPrompt("sys")
        assertTrue(out[0].containsKey("cache_control"))
    }

    // ---- addCacheControlToMessages ----

    @Test
    fun `addCacheControlToMessages empty list returns empty`() {
        val out = addCacheControlToMessages(emptyList())
        assertTrue(out.isEmpty())
    }

    @Test
    fun `addCacheControlToMessages marks only last message with string content`() {
        val msgs = listOf(
            mapOf("role" to "user", "content" to "first"),
            mapOf("role" to "assistant", "content" to "second"),
            mapOf("role" to "user", "content" to "third")
        )
        val out = addCacheControlToMessages(msgs)
        assertEquals(3, out.size)
        // first two unchanged
        assertEquals("first", out[0]["content"])
        assertEquals("second", out[1]["content"])
        // last transformed into content list with cache_control
        @Suppress("UNCHECKED_CAST")
        val content = out[2]["content"] as List<Map<String, Any>>
        assertEquals(1, content.size)
        assertEquals("third", content[0]["text"])
        @Suppress("UNCHECKED_CAST")
        val cache = content[0]["cache_control"] as Map<String, Any>
        assertEquals("ephemeral", cache["type"])
    }

    @Test
    fun `addCacheControlToMessages non-string last content passes through unchanged`() {
        val complex = listOf(mapOf("type" to "text", "text" to "already list"))
        val msgs = listOf(mapOf("role" to "user", "content" to complex))
        val out = addCacheControlToMessages(msgs)
        assertEquals(msgs, out)
    }

    // ---- applyAnthropicCacheControl ----

    @Test
    fun `applyAnthropicCacheControl empty messages returns empty`() {
        val out = applyAnthropicCacheControl(emptyList())
        assertTrue(out.isEmpty())
    }

    @Test
    fun `applyAnthropicCacheControl marks system prompt as string content`() {
        val msgs = listOf(mapOf("role" to "system", "content" to "you are helpful"))
        val out = applyAnthropicCacheControl(msgs)
        @Suppress("UNCHECKED_CAST")
        val content = out[0]["content"] as List<Map<String, Any?>>
        assertEquals("text", content[0]["type"])
        assertEquals("you are helpful", content[0]["text"])
        assertNotNull(content[0]["cache_control"])
    }

    @Test
    fun `applyAnthropicCacheControl ttl 1h adds ttl field to marker`() {
        val msgs = listOf(mapOf<String, Any?>("role" to "system", "content" to "sys"))
        val out = applyAnthropicCacheControl(msgs, cacheTtl = "1h")
        @Suppress("UNCHECKED_CAST")
        val content = out[0]["content"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val marker = content[0]["cache_control"] as Map<String, Any?>
        assertEquals("1h", marker["ttl"])
    }

    @Test
    fun `applyAnthropicCacheControl 5m ttl does not include ttl key`() {
        val msgs = listOf(mapOf<String, Any?>("role" to "system", "content" to "sys"))
        val out = applyAnthropicCacheControl(msgs, cacheTtl = "5m")
        @Suppress("UNCHECKED_CAST")
        val content = out[0]["content"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val marker = content[0]["cache_control"] as Map<String, Any?>
        assertNull(marker["ttl"])
    }

    @Test
    fun `applyAnthropicCacheControl at most 4 breakpoints total`() {
        // 1 system + 10 user — expect system + last 3 non-system marked (= 4 total)
        val msgs = buildList {
            add(mapOf<String, Any?>("role" to "system", "content" to "s"))
            repeat(10) { i ->
                add(mapOf("role" to "user", "content" to "m$i"))
            }
        }
        val out = applyAnthropicCacheControl(msgs)
        val marked = out.count { msg ->
            val content = msg["content"]
            if (content is List<*>) {
                content.any { (it as? Map<*, *>)?.containsKey("cache_control") == true }
            } else {
                msg.containsKey("cache_control")
            }
        }
        assertEquals(4, marked)
    }

    @Test
    fun `applyAnthropicCacheControl without system marks last 4 messages`() {
        val msgs = buildList {
            repeat(6) { i -> add(mapOf("role" to "user", "content" to "m$i")) }
        }
        val out = applyAnthropicCacheControl(msgs)
        val marked = out.count { msg ->
            val content = msg["content"]
            if (content is List<*>) {
                content.any { (it as? Map<*, *>)?.containsKey("cache_control") == true }
            } else {
                msg.containsKey("cache_control")
            }
        }
        assertEquals(4, marked)
        // earliest two NOT marked
        assertEquals("m0", out[0]["content"])
        assertEquals("m1", out[1]["content"])
    }

    @Test
    fun `applyAnthropicCacheControl tool role native anthropic marks msg level`() {
        val msgs = listOf(
            mapOf<String, Any?>("role" to "tool", "content" to "tool-result", "tool_call_id" to "1")
        )
        val out = applyAnthropicCacheControl(msgs, nativeAnthropic = true)
        assertNotNull(out[0]["cache_control"])
        // content unchanged (string)
        assertEquals("tool-result", out[0]["content"])
    }

    @Test
    fun `applyAnthropicCacheControl tool role non-native skipped`() {
        val msgs = listOf(
            mapOf<String, Any?>("role" to "tool", "content" to "tool-result", "tool_call_id" to "1")
        )
        val out = applyAnthropicCacheControl(msgs, nativeAnthropic = false)
        assertFalse(out[0].containsKey("cache_control"))
        // string content untouched since tool branch returned early
        assertEquals("tool-result", out[0]["content"])
    }

    @Test
    fun `applyAnthropicCacheControl empty content gets msg level marker`() {
        val msgs = listOf(mapOf<String, Any?>("role" to "user", "content" to ""))
        val out = applyAnthropicCacheControl(msgs)
        assertNotNull(out[0]["cache_control"])
    }

    @Test
    fun `applyAnthropicCacheControl list content appends to last element`() {
        val blocks = mutableListOf<MutableMap<String, Any?>>(
            mutableMapOf("type" to "text", "text" to "first"),
            mutableMapOf("type" to "text", "text" to "last")
        )
        val msgs = listOf(mapOf<String, Any?>("role" to "user", "content" to blocks))
        val out = applyAnthropicCacheControl(msgs)
        @Suppress("UNCHECKED_CAST")
        val content = out[0]["content"] as List<Map<String, Any?>>
        assertFalse(content[0].containsKey("cache_control"))
        assertTrue(content[1].containsKey("cache_control"))
    }

    @Test
    fun `applyAnthropicCacheControl does not mutate input list reference`() {
        val original = listOf(mapOf<String, Any?>("role" to "system", "content" to "x"))
        val out = applyAnthropicCacheControl(original)
        // caller's map untouched
        assertFalse(original[0].containsKey("cache_control"))
        assertEquals("x", original[0]["content"])
        // output transformed
        assertNotNull(out)
    }
}
