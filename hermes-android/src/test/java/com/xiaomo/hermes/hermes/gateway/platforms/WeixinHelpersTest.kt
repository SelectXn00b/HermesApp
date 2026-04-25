package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the top-level helpers ported from weixin.py:
 *   _safeId / _pkcs7Pad / _aes128EcbEncrypt / _aes128EcbDecrypt
 *   _aesPaddedSize / _randomWechatUin / _cdnDownloadUrl / _cdnUploadUrl
 *   _parseAesKey / _guessChatType
 */
class WeixinHelpersTest {

    @Test
    fun `safeId truncates long values`() {
        assertEquals("wxid_abc", _safeId("wxid_abcdefghij", keep = 8))
    }

    @Test
    fun `safeId preserves short values`() {
        assertEquals("short", _safeId("short"))
    }

    @Test
    fun `safeId returns question mark for empty`() {
        assertEquals("?", _safeId(null))
        assertEquals("?", _safeId(""))
        assertEquals("?", _safeId("   "))
    }

    @Test
    fun `pkcs7Pad adds padding to multiple of block size`() {
        val result = _pkcs7Pad(byteArrayOf(0x01, 0x02, 0x03))
        assertEquals(16, result.size)
        // Last 13 bytes should all be 13 (pad count)
        for (i in 3 until 16) assertEquals(13, result[i].toInt())
    }

    @Test
    fun `pkcs7Pad adds full block when already aligned`() {
        val input = ByteArray(16) { 0x00 }
        val result = _pkcs7Pad(input)
        assertEquals(32, result.size)
        // Last 16 bytes should all be 16
        for (i in 16 until 32) assertEquals(16, result[i].toInt())
    }

    @Test
    fun `aes128EcbEncrypt decrypt round-trip`() {
        val key = ByteArray(16) { (it + 1).toByte() }
        val plaintext = "Hello, Weixin CDN!".toByteArray(Charsets.UTF_8)
        val ciphertext = _aes128EcbEncrypt(plaintext, key)
        // 18 bytes plaintext → padded to 32 → 32 byte ciphertext
        assertEquals(32, ciphertext.size)
        val decrypted = _aes128EcbDecrypt(ciphertext, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `aes128EcbEncrypt produces correct ciphertext size`() {
        val key = ByteArray(16) { 0x00 }
        // Empty plaintext: 0 bytes → padded to 16 bytes → 16 byte ciphertext
        assertEquals(16, _aes128EcbEncrypt(byteArrayOf(), key).size)
        // 15 bytes → padded to 16 → 16 byte ciphertext
        assertEquals(16, _aes128EcbEncrypt(ByteArray(15), key).size)
        // 16 bytes → padded to 32 → 32 byte ciphertext
        assertEquals(32, _aes128EcbEncrypt(ByteArray(16), key).size)
        // 17 bytes → padded to 32 → 32 byte ciphertext
        assertEquals(32, _aes128EcbEncrypt(ByteArray(17), key).size)
    }

    @Test
    fun `aesPaddedSize computes next multiple of 16 with at least 1 byte pad`() {
        assertEquals(16, _aesPaddedSize(0))
        assertEquals(16, _aesPaddedSize(15))
        assertEquals(32, _aesPaddedSize(16))
        assertEquals(32, _aesPaddedSize(31))
        assertEquals(48, _aesPaddedSize(32))
    }

    @Test
    fun `randomWechatUin returns a base64 string`() {
        val uin = _randomWechatUin()
        assertFalse(uin.isEmpty())
        // Valid base64: decode shouldn't throw and should produce valid digits
        val decoded = java.util.Base64.getDecoder().decode(uin)
        val text = String(decoded, Charsets.US_ASCII)
        assertTrue("decoded text should be a digit string but got '$text'", text.all { it.isDigit() })
    }

    @Test
    fun `cdnDownloadUrl builds the right url shape`() {
        val url = _cdnDownloadUrl("https://novac2c.cdn.weixin.qq.com/c2c/", "param1=foo&bar=baz")
        assertTrue(url.startsWith("https://novac2c.cdn.weixin.qq.com/c2c/download?encrypted_query_param="))
        // `=` is encoded to `%3D`, `&` encoded to `%26`
        assertTrue(url.contains("%3D"))
        assertTrue(url.contains("%26"))
    }

    @Test
    fun `cdnUploadUrl includes filekey`() {
        val url = _cdnUploadUrl("https://x.cdn.qq.com/c2c/", "p=1", "fk=2")
        assertTrue(url.contains("encrypted_query_param=p%3D1"))
        assertTrue(url.contains("filekey=fk%3D2"))
    }

    @Test
    fun `parseAesKey accepts 16-byte base64`() {
        val key = ByteArray(16) { (it + 1).toByte() }
        val b64 = java.util.Base64.getEncoder().encodeToString(key)
        assertArrayEquals(key, _parseAesKey(b64))
    }

    @Test
    fun `parseAesKey accepts 32-byte hex base64`() {
        val hexKey = "0123456789abcdef0123456789abcdef"
        val b64 = java.util.Base64.getEncoder().encodeToString(hexKey.toByteArray(Charsets.US_ASCII))
        val parsed = _parseAesKey(b64)
        assertEquals(16, parsed.size)
        assertEquals(0x01.toByte(), parsed[0])
        assertEquals(0xEF.toByte(), parsed[15])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseAesKey rejects wrong-sized keys`() {
        val b64 = java.util.Base64.getEncoder().encodeToString(ByteArray(24))
        _parseAesKey(b64)
    }

    @Test
    fun `guessChatType detects group by room_id`() {
        val (kind, id) = _guessChatType(
            mapOf("room_id" to "chatroom_1", "from_user_id" to "alice"),
            accountId = "bot"
        )
        assertEquals("group", kind)
        assertEquals("chatroom_1", id)
    }

    @Test
    fun `guessChatType falls back to dm for p2p messages`() {
        val (kind, id) = _guessChatType(
            mapOf("from_user_id" to "alice", "to_user_id" to "bot", "msg_type" to 1),
            accountId = "bot"
        )
        assertEquals("dm", kind)
        assertEquals("alice", id)
    }

    @Test
    fun `guessChatType detects group when to_user differs from account`() {
        val (kind, id) = _guessChatType(
            mapOf("from_user_id" to "alice", "to_user_id" to "carol", "msg_type" to 1),
            accountId = "bot"
        )
        assertEquals("group", kind)
        assertEquals("carol", id)
    }

    @Test
    fun `checkWeixinRequirements returns true on Android`() {
        assertTrue(checkWeixinRequirements())
    }

    @Test
    fun `makeSslConnector returns null on Android`() {
        // aiohttp has no Android equivalent; OkHttp uses the platform trust
        // manager directly. Returning null lets callers fall back to defaults.
        assertNull(_makeSslConnector())
    }

    // ─── _jsonDumps ──────────────────────────────────────────────────────

    @Test
    fun `jsonDumps emits compact JSON with no whitespace`() {
        val out = _jsonDumps(mapOf("a" to 1, "b" to "two"))
        // Two orderings are possible (JSONObject is not insertion-ordered),
        // but neither should contain spaces after : or ,
        assertFalse("no colon-space in: $out", out.contains(": "))
        assertFalse("no comma-space in: $out", out.contains(", "))
        assertTrue(out.contains("\"a\":1"))
        assertTrue(out.contains("\"b\":\"two\""))
    }

    // ─── _baseInfo ───────────────────────────────────────────────────────

    @Test
    fun `baseInfo carries the current channel version`() {
        val info = _baseInfo()
        assertEquals(CHANNEL_VERSION, info["channel_version"])
        assertEquals(1, info.size)
    }

    // ─── _headers ────────────────────────────────────────────────────────

    @Test
    fun `headers omits Authorization when token is null or blank`() {
        assertFalse(_headers(null, "{}").containsKey("Authorization"))
        assertFalse(_headers("", "{}").containsKey("Authorization"))
    }

    @Test
    fun `headers carries Bearer authorization when token given`() {
        val h = _headers("abc.def", "{}")
        assertEquals("Bearer abc.def", h["Authorization"])
    }

    @Test
    fun `headers sets Content-Length from UTF-8 byte count`() {
        // Ascii body — byte length == char length.
        assertEquals("7", _headers(null, "{\"x\":1}")["Content-Length"])
        // Multi-byte body — "你" is 3 bytes in UTF-8.
        assertEquals("3", _headers(null, "你")["Content-Length"])
    }

    @Test
    fun `headers always contains iLink identity + random uin`() {
        val h = _headers("t", "{}")
        assertEquals(ILINK_APP_ID, h["iLink-App-Id"])
        assertEquals(ILINK_APP_CLIENT_VERSION.toString(), h["iLink-App-ClientVersion"])
        assertEquals("ilink_bot_token", h["AuthorizationType"])
        assertTrue(h.containsKey("X-WECHAT-UIN"))
        assertTrue(h.getValue("X-WECHAT-UIN").isNotEmpty())
    }

    // ─── _assertWeixinCdnUrl ────────────────────────────────────────────

    @Test
    fun `assertWeixinCdnUrl accepts allowlisted WeChat CDN hosts`() {
        // Should NOT throw.
        _assertWeixinCdnUrl("https://wx.qlogo.cn/path")
        _assertWeixinCdnUrl("https://mmbiz.qpic.cn/thumbnail")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertWeixinCdnUrl rejects non-http scheme`() {
        _assertWeixinCdnUrl("ftp://wx.qlogo.cn/path")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertWeixinCdnUrl rejects non-allowlisted host`() {
        _assertWeixinCdnUrl("https://evil.example.com/steal")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertWeixinCdnUrl rejects file scheme`() {
        _assertWeixinCdnUrl("file:///etc/passwd")
    }

    // ─── _mediaReference ────────────────────────────────────────────────

    @Test
    fun `mediaReference plucks nested media dict`() {
        val item = mapOf<String, Any?>(
            "image" to mapOf(
                "media" to mapOf("md5" to "abc", "size" to 123),
            ),
        )
        val out = _mediaReference(item, "image")
        assertEquals("abc", out["md5"])
        assertEquals(123, out["size"])
    }

    @Test
    fun `mediaReference returns empty on missing outer key`() {
        assertEquals(emptyMap<String, Any?>(), _mediaReference(emptyMap(), "image"))
    }

    @Test
    fun `mediaReference returns empty when nested shape differs`() {
        val item = mapOf<String, Any?>("image" to "not-a-map")
        assertEquals(emptyMap<String, Any?>(), _mediaReference(item, "image"))
    }

    // ─── _coerceBool ─────────────────────────────────────────────────────

    @Test
    fun `coerceBool passes through Boolean`() {
        assertTrue(_coerceBool(true))
        assertFalse(_coerceBool(false))
    }

    @Test
    fun `coerceBool returns default on null`() {
        assertTrue(_coerceBool(null, default = true))
        assertFalse(_coerceBool(null, default = false))
    }

    @Test
    fun `coerceBool returns default on empty string`() {
        assertTrue(_coerceBool("   ", default = true))
    }

    @Test
    fun `coerceBool maps Number to boolean via zero check`() {
        assertTrue(_coerceBool(1))
        assertTrue(_coerceBool(0.5))
        assertFalse(_coerceBool(0))
        assertFalse(_coerceBool(0.0))
    }

    @Test
    fun `coerceBool recognises common truthy strings`() {
        assertTrue(_coerceBool("true"))
        assertTrue(_coerceBool("TRUE"))
        assertTrue(_coerceBool("yes"))
        assertTrue(_coerceBool("on"))
        assertTrue(_coerceBool("1"))
    }

    @Test
    fun `coerceBool recognises common falsy strings`() {
        assertFalse(_coerceBool("false"))
        assertFalse(_coerceBool("No"))
        assertFalse(_coerceBool("off"))
        assertFalse(_coerceBool("0"))
    }

    @Test
    fun `coerceBool unknown string returns default`() {
        assertTrue(_coerceBool("maybe", default = true))
        assertFalse(_coerceBool("maybe", default = false))
    }
}
