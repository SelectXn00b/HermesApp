package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationToolTest {

    @Test
    fun `DEFAULT_MODEL and DEFAULT_ASPECT_RATIO constants`() {
        assertEquals("fal-ai/flux-2/klein/9b", DEFAULT_MODEL)
        assertEquals("landscape", DEFAULT_ASPECT_RATIO)
    }

    @Test
    fun `VALID_ASPECT_RATIOS contains the three expected values in order`() {
        assertEquals(listOf("landscape", "square", "portrait"), VALID_ASPECT_RATIOS)
    }

    @Test
    fun `upscaler constants match Python source`() {
        assertEquals("fal-ai/clarity-upscaler", UPSCALER_MODEL)
        assertEquals(2, UPSCALER_FACTOR)
        assertFalse(UPSCALER_SAFETY_CHECKER)
        assertEquals("masterpiece, best quality, highres", UPSCALER_DEFAULT_PROMPT)
        assertEquals("(worst quality, low quality, normal quality:2)", UPSCALER_NEGATIVE_PROMPT)
        assertEquals(0.35, UPSCALER_CREATIVITY, 1e-9)
        assertEquals(0.6, UPSCALER_RESEMBLANCE, 1e-9)
        assertEquals(4, UPSCALER_GUIDANCE_SCALE)
        assertEquals(18, UPSCALER_NUM_INFERENCE_STEPS)
    }

    @Test
    fun `checkFalApiKey returns false on Android`() {
        assertFalse(checkFalApiKey())
    }

    @Test
    fun `checkImageGenerationRequirements returns false on Android`() {
        assertFalse(checkImageGenerationRequirements())
    }

    @Test
    fun `imageGenerateTool returns error on Android`() {
        val result = imageGenerateTool(prompt = "a cat")
        assertTrue(result.contains("not available") || result.contains("error"))
    }

    @Test
    fun `IMAGE_GENERATE_SCHEMA has expected shape`() {
        val schema = IMAGE_GENERATE_SCHEMA
        assertEquals("image_generate", schema["name"])
        assertNotNull(schema["description"])
        @Suppress("UNCHECKED_CAST")
        val params = schema["parameters"] as Map<String, Any>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any>
        assertTrue("prompt" in props)
        assertTrue("aspect_ratio" in props)
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertEquals(listOf("prompt"), required)
        @Suppress("UNCHECKED_CAST")
        val aspect = props["aspect_ratio"] as Map<String, Any>
        assertEquals(VALID_ASPECT_RATIOS, aspect["enum"])
    }

    @Test
    fun `_ManagedFalSyncClient stores key and origin`() {
        val client = _ManagedFalSyncClient(key = "k", queueRunOrigin = "https://queue.example.com")
        assertEquals("k", client.key)
        assertEquals("https://queue.example.com", client.queueRunOrigin)
    }

    @Test
    fun `_ManagedFalSyncClient submit returns null on Android`() {
        val client = _ManagedFalSyncClient(key = "k", queueRunOrigin = "https://queue.example.com/")
        val result = client.submit(
            application = "fal-ai/flux/dev",
            arguments = mapOf("prompt" to "a cat"),
        )
        assertEquals(null, result)
    }

    @Test
    fun `_ManagedFalSyncClient accepts trailing-slash and non-slash origins`() {
        // Both should construct without throwing; _normalizeFalQueueUrlFormat strips/re-adds the slash.
        val c1 = _ManagedFalSyncClient(key = "k", queueRunOrigin = "https://queue.example.com")
        val c2 = _ManagedFalSyncClient(key = "k", queueRunOrigin = "https://queue.example.com/")
        assertEquals("https://queue.example.com", c1.queueRunOrigin)
        assertEquals("https://queue.example.com/", c2.queueRunOrigin)
    }
}
