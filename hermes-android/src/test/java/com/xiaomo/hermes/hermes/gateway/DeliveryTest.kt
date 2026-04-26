package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryTest {

    // ---- Module-level constants ----

    @Test
    fun `MAX_PLATFORM_OUTPUT is 4000`() {
        assertEquals(4000, MAX_PLATFORM_OUTPUT)
    }

    @Test
    fun `TRUNCATED_VISIBLE is 3800`() {
        assertEquals(3800, TRUNCATED_VISIBLE)
    }

    // ---- DeliveryTarget.parse ----

    @Test
    fun `parse origin without source returns local with isOrigin true`() {
        val t = DeliveryTarget.parse("origin")
        assertEquals("local", t.platform)
        assertTrue(t.isOrigin)
        assertNull(t.chatId)
    }

    @Test
    fun `parse origin with SessionSource returns concrete platform`() {
        val src = SessionSource(
            platform = "telegram",
            chatId = "1234",
            threadId = "t1"
        )
        val t = DeliveryTarget.parse("origin", origin = src)
        assertEquals("telegram", t.platform)
        assertEquals("1234", t.chatId)
        assertEquals("t1", t.threadId)
        assertTrue(t.isOrigin)
    }

    @Test
    fun `parse local returns platform local`() {
        val t = DeliveryTarget.parse("local")
        assertEquals("local", t.platform)
        assertFalse(t.isOrigin)
        assertFalse(t.isExplicit)
        assertNull(t.chatId)
    }

    @Test
    fun `parse platform colon chat`() {
        val t = DeliveryTarget.parse("telegram:1234")
        assertEquals("telegram", t.platform)
        assertEquals("1234", t.chatId)
        assertNull(t.threadId)
        assertTrue(t.isExplicit)
    }

    @Test
    fun `parse platform colon chat colon thread`() {
        val t = DeliveryTarget.parse("feishu:oc_abc:th_123")
        assertEquals("feishu", t.platform)
        assertEquals("oc_abc", t.chatId)
        assertEquals("th_123", t.threadId)
    }

    @Test
    fun `parse bare platform is not explicit`() {
        val t = DeliveryTarget.parse("discord")
        assertEquals("discord", t.platform)
        assertNull(t.chatId)
        assertFalse(t.isExplicit)
    }

    @Test
    fun `parse lowercases input`() {
        val t = DeliveryTarget.parse("Telegram:1234")
        assertEquals("telegram", t.platform)
        assertEquals("1234", t.chatId)
    }

    @Test
    fun `parse trims whitespace`() {
        val t = DeliveryTarget.parse("  telegram:1234  ")
        assertEquals("telegram", t.platform)
        assertEquals("1234", t.chatId)
    }

    // ---- DeliveryTarget.toString ----

    @Test
    fun `toString platform only`() {
        assertEquals("local", DeliveryTarget(platform = "local").toString())
    }

    @Test
    fun `toString platform chat`() {
        assertEquals(
            "telegram:1234",
            DeliveryTarget(platform = "telegram", chatId = "1234").toString()
        )
    }

    @Test
    fun `toString platform chat thread`() {
        assertEquals(
            "feishu:oc:th",
            DeliveryTarget(platform = "feishu", chatId = "oc", threadId = "th").toString()
        )
    }

    @Test
    fun `toString platform without chat but with thread still omits thread when no chat`() {
        // chatId null => base is just platform. thread gets appended.
        val s = DeliveryTarget(platform = "x", threadId = "th").toString()
        assertEquals("x:th", s)
    }

    // ---- DeliveryRouter basic registry ----

    @Test
    fun `router starts empty`() {
        val r = DeliveryRouter()
        assertFalse(r.hasAdapters)
        assertTrue(r.platformNames.isEmpty())
        assertNull(r.getAdapter("telegram"))
    }

    @Test
    fun `router unregister missing platform is no-op`() {
        val r = DeliveryRouter()
        r.unregister("nope") // should not throw
        assertFalse(r.hasAdapters)
    }
}
