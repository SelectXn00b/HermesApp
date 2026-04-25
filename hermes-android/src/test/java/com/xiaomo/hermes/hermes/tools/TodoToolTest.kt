package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoToolTest {

    @Test
    fun `VALID_STATUSES covers the four canonical values`() {
        assertEquals(
            setOf("pending", "in_progress", "completed", "cancelled"),
            VALID_STATUSES,
        )
    }

    @Test
    fun `empty TodoStore reads as empty`() {
        val store = TodoStore()
        assertTrue(store.read().isEmpty())
        assertFalse(store.hasItems())
    }

    @Test
    fun `write replace mode drops existing items`() {
        val store = TodoStore()
        store.write(listOf(mapOf("id" to "a", "content" to "first", "status" to "pending")))
        store.write(listOf(mapOf("id" to "b", "content" to "second", "status" to "pending")))
        assertEquals(1, store.read().size)
        assertEquals("b", store.read()[0]["id"])
    }

    @Test
    fun `write merge mode updates existing id`() {
        val store = TodoStore()
        store.write(listOf(mapOf("id" to "a", "content" to "old", "status" to "pending")))
        store.write(
            listOf(mapOf("id" to "a", "content" to "new", "status" to "in_progress")),
            merge = true,
        )
        assertEquals(1, store.read().size)
        assertEquals("new", store.read()[0]["content"])
        assertEquals("in_progress", store.read()[0]["status"])
    }

    @Test
    fun `write merge mode adds new ids`() {
        val store = TodoStore()
        store.write(listOf(mapOf("id" to "a", "content" to "first", "status" to "pending")))
        store.write(
            listOf(mapOf("id" to "b", "content" to "second", "status" to "pending")),
            merge = true,
        )
        assertEquals(2, store.read().size)
    }

    @Test
    fun `write merge ignores items with blank id`() {
        val store = TodoStore()
        store.write(listOf(mapOf("id" to "a", "content" to "x", "status" to "pending")))
        store.write(
            listOf(mapOf("id" to "   ", "content" to "garbage", "status" to "pending")),
            merge = true,
        )
        // Blank-id merge entry skipped; original "a" survives.
        assertEquals(1, store.read().size)
        assertEquals("a", store.read()[0]["id"])
    }

    @Test
    fun `_validate fills missing id and content with sentinels`() {
        val validated = TodoStore._validate(emptyMap<String, Any?>())
        assertEquals("?", validated["id"])
        assertEquals("(no description)", validated["content"])
        assertEquals("pending", validated["status"])
    }

    @Test
    fun `_validate coerces invalid status to pending`() {
        val validated = TodoStore._validate(
            mapOf("id" to "x", "content" to "y", "status" to "DONE_SOMEHOW"),
        )
        assertEquals("pending", validated["status"])
    }

    @Test
    fun `_validate lowercases status`() {
        val validated = TodoStore._validate(
            mapOf("id" to "x", "content" to "y", "status" to "IN_PROGRESS"),
        )
        assertEquals("in_progress", validated["status"])
    }

    @Test
    fun `_dedupeById keeps last occurrence and preserves order`() {
        val input = listOf(
            mapOf("id" to "a", "content" to "first"),
            mapOf("id" to "b", "content" to "second"),
            mapOf("id" to "a", "content" to "third"),  // replaces id=a above
        )
        val deduped = TodoStore._dedupeById(input)
        assertEquals(2, deduped.size)
        // Last occurrence of "a" (content=third) should be preserved.
        val a = deduped.first { it["id"] == "a" }
        assertEquals("third", a["content"])
    }

    @Test
    fun `formatForInjection returns null when empty`() {
        assertNull(TodoStore().formatForInjection())
    }

    @Test
    fun `formatForInjection returns null when all items completed`() {
        val store = TodoStore()
        store.write(
            listOf(
                mapOf("id" to "a", "content" to "done thing", "status" to "completed"),
                mapOf("id" to "b", "content" to "dropped", "status" to "cancelled"),
            ),
        )
        assertNull(store.formatForInjection())
    }

    @Test
    fun `formatForInjection lists pending and in_progress only`() {
        val store = TodoStore()
        store.write(
            listOf(
                mapOf("id" to "1", "content" to "alpha", "status" to "pending"),
                mapOf("id" to "2", "content" to "bravo", "status" to "in_progress"),
                mapOf("id" to "3", "content" to "charlie", "status" to "completed"),
                mapOf("id" to "4", "content" to "delta", "status" to "cancelled"),
            ),
        )
        val injected = store.formatForInjection()
        assertNotNull(injected)
        assertTrue("alpha" in injected!!)
        assertTrue("bravo" in injected)
        assertFalse("charlie" in injected)  // completed — excluded
        assertFalse("delta" in injected)  // cancelled — excluded
        assertTrue(injected.contains("[ ]"))  // pending marker
        assertTrue(injected.contains("[>]"))  // in_progress marker
    }

    @Test
    fun `todoTool returns error when store is null`() {
        val result = todoTool(todos = null, store = null)
        val obj = JSONObject(result)
        assertTrue(obj.has("error"))
    }

    @Test
    fun `todoTool read-only returns current list without mutations`() {
        val store = TodoStore()
        store.write(listOf(mapOf("id" to "a", "content" to "x", "status" to "pending")))
        val result = todoTool(todos = null, store = store)
        val obj = JSONObject(result)
        assertEquals(1, obj.getJSONArray("todos").length())
        val summary = obj.getJSONObject("summary")
        assertEquals(1, summary.getInt("total"))
        assertEquals(1, summary.getInt("pending"))
    }

    @Test
    fun `todoTool write computes summary counts`() {
        val store = TodoStore()
        val result = todoTool(
            todos = listOf(
                mapOf("id" to "1", "content" to "a", "status" to "pending"),
                mapOf("id" to "2", "content" to "b", "status" to "pending"),
                mapOf("id" to "3", "content" to "c", "status" to "in_progress"),
                mapOf("id" to "4", "content" to "d", "status" to "completed"),
                mapOf("id" to "5", "content" to "e", "status" to "cancelled"),
            ),
            store = store,
        )
        val summary = JSONObject(result).getJSONObject("summary")
        assertEquals(5, summary.getInt("total"))
        assertEquals(2, summary.getInt("pending"))
        assertEquals(1, summary.getInt("in_progress"))
        assertEquals(1, summary.getInt("completed"))
        assertEquals(1, summary.getInt("cancelled"))
    }

    @Test
    fun `checkTodoRequirements always returns true`() {
        assertTrue(checkTodoRequirements())
    }

    @Test
    fun `TODO_SCHEMA has expected shape`() {
        assertEquals("todo", TODO_SCHEMA["name"])
        assertNotNull(TODO_SCHEMA["description"])
        @Suppress("UNCHECKED_CAST")
        val params = TODO_SCHEMA["parameters"] as Map<String, Any>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any>
        assertTrue("todos" in props)
        assertTrue("merge" in props)
    }
}
