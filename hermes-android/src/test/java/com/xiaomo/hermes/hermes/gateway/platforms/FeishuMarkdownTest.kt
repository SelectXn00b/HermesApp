package com.xiaomo.hermes.hermes.gateway.platforms

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the top-level markdown post helpers ported from feishu.py:
 *   _buildMarkdownPostPayload / _buildMarkdownPostRows
 *   _renderPostElement / _renderNestedPost
 *
 * Helpers use `internal` visibility so test classes in the same Gradle
 * module can reach them directly, mirroring Python's `_` = module-private
 * convention.
 */
class FeishuMarkdownTest {

    @Test
    fun `buildMarkdownPostPayload with empty content returns md row with empty text`() {
        val json = JSONObject(_buildMarkdownPostPayload(""))
        val rows = json.getJSONObject("zh_cn").getJSONArray("content")
        assertEquals(1, rows.length())
        val firstRow: JSONArray = rows.getJSONArray(0)
        assertEquals(1, firstRow.length())
        val cell = firstRow.getJSONObject(0)
        assertEquals("md", cell.getString("tag"))
        assertEquals("", cell.getString("text"))
    }

    @Test
    fun `buildMarkdownPostPayload with plain content emits single md row`() {
        val json = JSONObject(_buildMarkdownPostPayload("hello world"))
        val rows = json.getJSONObject("zh_cn").getJSONArray("content")
        assertEquals(1, rows.length())
        assertEquals("hello world", rows.getJSONArray(0).getJSONObject(0).getString("text"))
    }

    @Test
    fun `buildMarkdownPostPayload isolates fenced code block into its own row`() {
        val content = "before\n```kotlin\nval x = 1\n```\nafter"
        val json = JSONObject(_buildMarkdownPostPayload(content))
        val rows = json.getJSONObject("zh_cn").getJSONArray("content")
        // Expect three rows: prose "before", fenced code block, prose "after"
        assertEquals(3, rows.length())
        val firstText = rows.getJSONArray(0).getJSONObject(0).getString("text")
        val midText = rows.getJSONArray(1).getJSONObject(0).getString("text")
        val lastText = rows.getJSONArray(2).getJSONObject(0).getString("text")
        assertEquals("before", firstText)
        assertTrue(midText.startsWith("```kotlin"))
        assertTrue(midText.endsWith("```"))
        assertEquals("after", lastText)
    }

    @Test
    fun `buildMarkdownPostPayload preserves original content when no fence`() {
        val content = "just some *markdown* with `inline` code"
        val json = JSONObject(_buildMarkdownPostPayload(content))
        val rows = json.getJSONObject("zh_cn").getJSONArray("content")
        assertEquals(1, rows.length())
        assertEquals(content, rows.getJSONArray(0).getJSONObject(0).getString("text"))
    }

    @Test
    fun `renderPostElement handles text element with bold style`() {
        val element = mapOf<String, Any?>(
            "tag" to "text",
            "text" to "hello",
            "style" to mapOf<String, Any?>("bold" to true),
        )
        val rendered = _renderPostElement(
            element, mutableListOf(), mutableListOf(), mutableListOf()
        )
        assertEquals("**hello**", rendered)
    }

    @Test
    fun `renderPostElement handles text element with code style`() {
        val element = mapOf<String, Any?>(
            "tag" to "text",
            "text" to "x = 1",
            "style" to mapOf<String, Any?>("code" to true),
        )
        val rendered = _renderPostElement(
            element, mutableListOf(), mutableListOf(), mutableListOf()
        )
        assertEquals("`x = 1`", rendered)
    }

    @Test
    fun `renderPostElement handles anchor element`() {
        val element = mapOf<String, Any?>(
            "tag" to "a",
            "text" to "Feishu",
            "href" to "https://feishu.cn",
        )
        val rendered = _renderPostElement(
            element, mutableListOf(), mutableListOf(), mutableListOf()
        )
        assertEquals("[Feishu](https://feishu.cn)", rendered)
    }

    @Test
    fun `renderPostElement at-mention appends open_id to mentionedIds`() {
        val element = mapOf<String, Any?>(
            "tag" to "at",
            "open_id" to "ou_abc123",
            "user_name" to "Alice",
        )
        val mentions = mutableListOf<String>()
        val rendered = _renderPostElement(
            element, mutableListOf(), mutableListOf(), mentions
        )
        assertEquals("@Alice", rendered)
        assertEquals(listOf("ou_abc123"), mentions)
    }

    @Test
    fun `renderPostElement image element adds image_key and returns alt text`() {
        val element = mapOf<String, Any?>(
            "tag" to "img",
            "image_key" to "img_xyz",
            "text" to "Screenshot",
        )
        val keys = mutableListOf<String>()
        val rendered = _renderPostElement(
            element, keys, mutableListOf(), mutableListOf()
        )
        assertEquals("[Image: Screenshot]", rendered)
        assertEquals(listOf("img_xyz"), keys)
    }

    @Test
    fun `renderPostElement media element captures FeishuPostMediaRef`() {
        val element = mapOf<String, Any?>(
            "tag" to "audio",
            "file_key" to "file_aaa",
            "file_name" to "clip.mp3",
        )
        val refs = mutableListOf<FeishuPostMediaRef>()
        val rendered = _renderPostElement(
            element, mutableListOf(), refs, mutableListOf()
        )
        assertEquals("[Attachment: clip.mp3]", rendered)
        assertEquals(1, refs.size)
        assertEquals("audio", refs[0].resource_type)
        assertEquals("file_aaa", refs[0].file_key)
        assertEquals("clip.mp3", refs[0].file_name)
    }

    @Test
    fun `renderPostElement br returns newline and hr returns divider`() {
        val br = _renderPostElement(
            mapOf("tag" to "br"), mutableListOf(), mutableListOf(), mutableListOf()
        )
        assertEquals("\n", br)
        val hr = _renderPostElement(
            mapOf("tag" to "hr"), mutableListOf(), mutableListOf(), mutableListOf()
        )
        assertEquals("\n\n---\n\n", hr)
    }

    @Test
    fun `escapeMarkdownText escapes special chars`() {
        val raw = "*bold* [link](x) `code`"
        val escaped = _escapeMarkdownText(raw)
        assertEquals("\\*bold\\* \\[link\\]\\(x\\) \\`code\\`", escaped)
    }
}
