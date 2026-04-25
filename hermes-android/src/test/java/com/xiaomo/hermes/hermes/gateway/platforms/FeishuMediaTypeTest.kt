package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the static media-type helpers ported from feishu.py:
 *   _normalizeMediaType / _defaultImageMediaType / _guessExtension
 *   _guessRemoteExtension / _deriveRemoteFilename
 *   _guessDocumentMediaType / _guessMediaTypeFromFilename
 *   _displayNameFromCachedPath / _mapChatType
 */
class FeishuMediaTypeTest {

    @Test
    fun `normalizeMediaType strips parameters and lowercases`() {
        assertEquals("image/png", _normalizeMediaType("Image/PNG; charset=binary", default = "application/octet-stream"))
    }

    @Test
    fun `normalizeMediaType falls back to default on empty`() {
        assertEquals("image/jpeg", _normalizeMediaType(null, default = "image/jpeg"))
        assertEquals("image/jpeg", _normalizeMediaType("   ", default = "image/jpeg"))
    }

    @Test
    fun `defaultImageMediaType maps jpg and jpeg to image_jpeg`() {
        assertEquals("image/jpeg", _defaultImageMediaType(".jpg"))
        assertEquals("image/jpeg", _defaultImageMediaType(".jpeg"))
    }

    @Test
    fun `defaultImageMediaType builds image subtype from other extensions`() {
        assertEquals("image/png", _defaultImageMediaType(".png"))
        assertEquals("image/gif", _defaultImageMediaType(".gif"))
    }

    @Test
    fun `defaultImageMediaType empty ext falls back to jpeg`() {
        assertEquals("image/jpeg", _defaultImageMediaType(""))
        assertEquals("image/jpeg", _defaultImageMediaType(null))
    }

    @Test
    fun `guessExtension prefers filename extension when allowed`() {
        assertEquals(
            ".png",
            _guessExtension("photo.PNG", "image/jpeg", default = ".jpg", allowed = _IMAGE_EXTENSIONS)
        )
    }

    @Test
    fun `guessExtension falls back to content-type when filename unknown`() {
        assertEquals(
            ".png",
            _guessExtension("weird.bin", "image/png", default = ".jpg", allowed = _IMAGE_EXTENSIONS)
        )
    }

    @Test
    fun `guessExtension returns default when neither matches allowed`() {
        assertEquals(
            ".jpg",
            _guessExtension("clip.mov", "video/quicktime", default = ".jpg", allowed = _IMAGE_EXTENSIONS)
        )
    }

    @Test
    fun `guessRemoteExtension picks suffix of known extensions`() {
        assertEquals(".mp3", _guessRemoteExtension("https://example.com/a/b/clip.mp3?x=1", default = ".bin"))
    }

    @Test
    fun `guessRemoteExtension returns default for unknown suffix`() {
        assertEquals(".bin", _guessRemoteExtension("https://example.com/x.unknown", default = ".bin"))
    }

    @Test
    fun `deriveRemoteFilename keeps URL basename and adds extension if absent`() {
        assertEquals(
            "report",
            _pathBaseName("https://x.com/report?a=1")
        )
        val result = _deriveRemoteFilename(
            fileUrl = "https://x.com/report?a=1",
            contentType = "application/pdf",
            defaultName = "file",
            defaultExt = ".bin",
        )
        assertEquals("report.pdf", result)
    }

    @Test
    fun `deriveRemoteFilename uses defaultName when URL has no segment`() {
        val result = _deriveRemoteFilename(
            fileUrl = "",
            contentType = "",
            defaultName = "untitled",
            defaultExt = ".bin",
        )
        assertEquals("untitled.bin", result)
    }

    @Test
    fun `deriveRemoteFilename keeps existing extension`() {
        val result = _deriveRemoteFilename(
            fileUrl = "https://x.com/path/photo.png",
            contentType = "image/jpeg",
            defaultName = "file",
            defaultExt = ".jpg",
        )
        assertEquals("photo.png", result)
    }

    @Test
    fun `guessDocumentMediaType maps supported document extensions`() {
        assertEquals("application/pdf", _guessDocumentMediaType("report.PDF"))
        assertEquals("text/markdown", _guessDocumentMediaType("readme.md"))
        assertEquals("application/zip", _guessDocumentMediaType("bundle.zip"))
    }

    @Test
    fun `guessDocumentMediaType falls back to mimetypes then octet-stream`() {
        assertEquals("image/png", _guessDocumentMediaType("poster.png"))
        assertEquals("application/octet-stream", _guessDocumentMediaType("weird.xyz"))
    }

    @Test
    fun `guessMediaTypeFromFilename resolves media categories`() {
        assertEquals("video/mp4", _guessMediaTypeFromFilename("clip.mp4"))
        assertEquals("audio/mpeg", _guessMediaTypeFromFilename("voice.mp3"))
        assertEquals("image/png", _guessMediaTypeFromFilename("pic.png"))
    }

    @Test
    fun `displayNameFromCachedPath strips cache prefix`() {
        // cached file layout: doc_{uuid12}_{original_filename}
        val input = "/data/user/0/app/files/documents/doc_ab12cd34ef56_My File (v2).pdf"
        val display = _displayNameFromCachedPath(input)
        // Space and dot and dash are preserved; parentheses → underscore
        assertEquals("My File _v2_.pdf", display)
    }

    @Test
    fun `mapChatType maps p2p group topic variants`() {
        assertEquals("dm", _mapChatType("p2p"))
        assertEquals("dm", _mapChatType("P2P"))
        assertEquals("group", _mapChatType("Group"))
        assertEquals("group", _mapChatType("topic"))
        assertEquals("unknown", _mapChatType(""))
        assertEquals("unknown", _mapChatType(null))
    }
}
