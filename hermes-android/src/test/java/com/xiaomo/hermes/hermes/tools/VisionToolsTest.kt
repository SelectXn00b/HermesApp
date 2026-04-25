package com.xiaomo.hermes.hermes.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionToolsTest {

    @Test
    fun `_VISION_DOWNLOAD_TIMEOUT is 30 seconds`() {
        assertEquals(30.0, _VISION_DOWNLOAD_TIMEOUT, 0.0001)
    }

    @Test
    fun `_VISION_MAX_DOWNLOAD_BYTES is 50 MiB`() {
        assertEquals(52_428_800, _VISION_MAX_DOWNLOAD_BYTES)
    }

    @Test
    fun `_MAX_BASE64_BYTES is 20 MiB`() {
        assertEquals(20_971_520, _MAX_BASE64_BYTES)
    }

    @Test
    fun `_RESIZE_TARGET_BYTES is 5 MiB`() {
        assertEquals(5_242_880, _RESIZE_TARGET_BYTES)
    }

    @Test
    fun `VISION_ANALYZE_SCHEMA has expected shape`() {
        assertEquals("vision_analyze", VISION_ANALYZE_SCHEMA["name"])
        assertNotNull(VISION_ANALYZE_SCHEMA["description"])
        @Suppress("UNCHECKED_CAST")
        val params = VISION_ANALYZE_SCHEMA["parameters"] as Map<String, Any>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any>
        assertTrue("image_url" in props)
        assertTrue("question" in props)
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertTrue("image_url" in required)
        assertTrue("question" in required)
    }

    @Test
    fun `checkVisionRequirements returns false on Android`() {
        assertFalse(checkVisionRequirements())
    }

    @Test
    fun `visionAnalyzeTool returns error when called`() = runBlocking {
        val result = visionAnalyzeTool(
            imageUrl = "https://example.com/x.jpg",
            userPrompt = "what?",
        )
        assertTrue("not available" in result.lowercase() || "error" in result.lowercase())
    }

    @Test
    fun `_handleVisionAnalyze returns error`() {
        val result = _handleVisionAnalyze(mapOf("image_url" to "x", "question" to "y"))
        assertTrue("not available" in result.lowercase() || "error" in result.lowercase())
    }
}
