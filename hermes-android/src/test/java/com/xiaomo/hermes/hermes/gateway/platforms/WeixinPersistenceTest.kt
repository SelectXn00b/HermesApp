package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Covers the Weixin persistence + extract helpers ported from weixin.py:
 *   _accountDir / _accountFile / saveWeixinAccount / loadWeixinAccount
 *   _syncBufPath / _loadSyncBuf / _saveSyncBuf
 *   _extractText / _messageTypeFromMedia
 */
class WeixinPersistenceTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = File.createTempFile("weixin-test", "").also {
            it.delete()
            it.mkdirs()
        }
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun `accountDir creates directory`() {
        val dir = _accountDir(tmp.absolutePath)
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        assertTrue(dir.absolutePath.endsWith("weixin/accounts"))
    }

    @Test
    fun `accountFile points under accountDir`() {
        val f = _accountFile(tmp.absolutePath, "bot_99")
        assertTrue(f.absolutePath.endsWith("weixin/accounts/bot_99.json"))
    }

    @Test
    fun `saveWeixinAccount then loadWeixinAccount round-trips`() {
        saveWeixinAccount(
            tmp.absolutePath,
            accountId = "bot_1",
            token = "secret-token",
            baseUrl = "https://ilinkai.weixin.qq.com",
            userId = "user-42",
        )
        val loaded = loadWeixinAccount(tmp.absolutePath, "bot_1")
        assertNotNull(loaded)
        assertEquals("secret-token", loaded!!["token"])
        assertEquals("https://ilinkai.weixin.qq.com", loaded["base_url"])
        assertEquals("user-42", loaded["user_id"])
        assertNotNull(loaded["saved_at"])
    }

    @Test
    fun `loadWeixinAccount returns null for missing account`() {
        assertNull(loadWeixinAccount(tmp.absolutePath, "nonexistent"))
    }

    @Test
    fun `loadWeixinAccount returns null for corrupt JSON`() {
        val dir = _accountDir(tmp.absolutePath)
        File(dir, "corrupt.json").writeText("{not-valid-json", Charsets.UTF_8)
        assertNull(loadWeixinAccount(tmp.absolutePath, "corrupt"))
    }

    @Test
    fun `syncBuf round-trips`() {
        _saveSyncBuf(tmp.absolutePath, "bot_7", "cursor-data")
        assertEquals("cursor-data", _loadSyncBuf(tmp.absolutePath, "bot_7"))
    }

    @Test
    fun `loadSyncBuf returns empty for missing or corrupt`() {
        assertEquals("", _loadSyncBuf(tmp.absolutePath, "nonexistent"))
        val path = _syncBufPath(tmp.absolutePath, "corrupt")
        path.parentFile?.mkdirs()
        path.writeText("bad", Charsets.UTF_8)
        assertEquals("", _loadSyncBuf(tmp.absolutePath, "corrupt"))
    }

    @Test
    fun `syncBufPath uses accountDir with sync suffix`() {
        val path = _syncBufPath(tmp.absolutePath, "bot_3")
        assertTrue(path.absolutePath.endsWith("weixin/accounts/bot_3.sync.json"))
    }

    @Test
    fun `extractText prefers text_item text`() {
        val items = listOf(
            mapOf<String, Any?>("type" to ITEM_TEXT, "text_item" to mapOf("text" to "hello")),
        )
        assertEquals("hello", _extractText(items))
    }

    @Test
    fun `extractText falls back to voice transcript`() {
        val items = listOf<Map<String, Any?>>(
            mapOf("type" to ITEM_VOICE, "voice_item" to mapOf("text" to "transcript here")),
        )
        assertEquals("transcript here", _extractText(items))
    }

    @Test
    fun `extractText returns empty when no text or voice`() {
        val items = listOf<Map<String, Any?>>(
            mapOf("type" to ITEM_IMAGE, "image_item" to mapOf("media" to mapOf<String, Any?>())),
        )
        assertEquals("", _extractText(items))
    }

    @Test
    fun `extractText includes media ref prefix with title`() {
        val items = listOf(
            mapOf<String, Any?>(
                "type" to ITEM_TEXT,
                "text_item" to mapOf("text" to "my comment"),
                "ref_msg" to mapOf(
                    "title" to "原图",
                    "message_item" to mapOf("type" to ITEM_IMAGE),
                ),
            ),
        )
        val result = _extractText(items)
        assertTrue(result.startsWith("[引用媒体: 原图]"))
        assertTrue(result.endsWith("my comment"))
    }

    @Test
    fun `extractText includes media ref prefix without title`() {
        val items = listOf(
            mapOf<String, Any?>(
                "type" to ITEM_TEXT,
                "text_item" to mapOf("text" to "reply"),
                "ref_msg" to mapOf(
                    "message_item" to mapOf("type" to ITEM_FILE),
                ),
            ),
        )
        val result = _extractText(items)
        assertTrue(result.startsWith("[引用媒体]"))
    }

    @Test
    fun `extractText includes text ref quote`() {
        val items = listOf(
            mapOf<String, Any?>(
                "type" to ITEM_TEXT,
                "text_item" to mapOf("text" to "reply body"),
                "ref_msg" to mapOf(
                    "title" to "Alice",
                    "message_item" to mapOf(
                        "type" to ITEM_TEXT,
                        "text_item" to mapOf("text" to "original msg"),
                    ),
                ),
            ),
        )
        val result = _extractText(items)
        assertTrue(result.startsWith("[引用: Alice | original msg]"))
        assertTrue(result.endsWith("reply body"))
    }

    @Test
    fun `messageTypeFromMedia picks PHOTO for image`() {
        assertEquals(MessageType.PHOTO, _messageTypeFromMedia(listOf("image/png"), ""))
        assertEquals(MessageType.PHOTO, _messageTypeFromMedia(listOf("image/jpeg", "text/plain"), ""))
    }

    @Test
    fun `messageTypeFromMedia picks VIDEO for video`() {
        assertEquals(MessageType.VIDEO, _messageTypeFromMedia(listOf("video/mp4"), ""))
    }

    @Test
    fun `messageTypeFromMedia picks VOICE for audio`() {
        assertEquals(MessageType.VOICE, _messageTypeFromMedia(listOf("audio/mpeg"), ""))
    }

    @Test
    fun `messageTypeFromMedia picks DOCUMENT for other media`() {
        assertEquals(MessageType.DOCUMENT, _messageTypeFromMedia(listOf("application/pdf"), ""))
    }

    @Test
    fun `messageTypeFromMedia picks COMMAND for slash text`() {
        assertEquals(MessageType.COMMAND, _messageTypeFromMedia(emptyList(), "/start"))
    }

    @Test
    fun `messageTypeFromMedia picks TEXT by default`() {
        assertEquals(MessageType.TEXT, _messageTypeFromMedia(emptyList(), "hello"))
        assertEquals(MessageType.TEXT, _messageTypeFromMedia(emptyList(), ""))
    }
}
