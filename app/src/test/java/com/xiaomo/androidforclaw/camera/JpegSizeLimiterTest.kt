package com.xiaomo.androidforclaw.camera

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * JpegSizeLimiter 单元测试
 */
class JpegSizeLimiterTest {

    @Test
    fun `within limit returns immediately`() {
        val result = JpegSizeLimiter.compressToLimit(
            initialWidth = 100,
            initialHeight = 100,
            startQuality = 90,
            maxBytes = 1000,
            encode = { w, h, q ->
                // 返回一个小于限制的 byte array
                ByteArray(500)
            }
        )
        assertEquals(100, result.width)
        assertEquals(100, result.height)
        assertEquals(90, result.quality)
        assertEquals(500, result.bytes.size)
    }

    @Test
    fun `reduces quality when over limit`() {
        var callCount = 0
        val result = JpegSizeLimiter.compressToLimit(
            initialWidth = 100,
            initialHeight = 100,
            startQuality = 90,
            maxBytes = 500,
            encode = { w, h, q ->
                callCount++
                // 高质量时超限，低质量时正常
                if (q > 40) ByteArray(1000) else ByteArray(400)
            }
        )
        assertTrue(result.bytes.size <= 500)
        assertTrue(callCount > 1, "Should have tried multiple quality levels")
    }

    @Test
    fun `reduces size when quality alone is not enough`() {
        var minWidth = Int.MAX_VALUE
        val result = JpegSizeLimiter.compressToLimit(
            initialWidth = 1000,
            initialHeight = 1000,
            startQuality = 90,
            maxBytes = 500,
            minQuality = 50,
            encode = { w, h, q ->
                if (w < minWidth) minWidth = w
                // 大尺寸始终超限，只有缩小才行
                if (w > 600) ByteArray(1000) else ByteArray(400)
            }
        )
        assertTrue(result.bytes.size <= 500)
        assertTrue(result.width < 1000, "Width should have been reduced")
    }

    @Test
    fun `invalid size throws`() {
        assertFailsWith<IllegalArgumentException> {
            JpegSizeLimiter.compressToLimit(
                initialWidth = 0,
                initialHeight = 100,
                startQuality = 90,
                maxBytes = 1000,
                encode = { _, _, _ -> ByteArray(10) }
            )
        }
    }

    @Test
    fun `invalid maxBytes throws`() {
        assertFailsWith<IllegalArgumentException> {
            JpegSizeLimiter.compressToLimit(
                initialWidth = 100,
                initialHeight = 100,
                startQuality = 90,
                maxBytes = 0,
                encode = { _, _, _ -> ByteArray(10) }
            )
        }
    }

    @Test
    fun `clamps start quality to valid range`() {
        // startQuality > 100 should be clamped to 100
        val result = JpegSizeLimiter.compressToLimit(
            initialWidth = 100,
            initialHeight = 100,
            startQuality = 150,
            maxBytes = 1000,
            encode = { _, _, q ->
                assertTrue(q <= 100, "Quality should be clamped to 100")
                ByteArray(100)
            }
        )
        assertTrue(result.quality <= 100)
    }

    @Test
    fun `throws when cannot compress below limit`() {
        assertFailsWith<IllegalStateException> {
            JpegSizeLimiter.compressToLimit(
                initialWidth = 300,
                initialHeight = 300,
                startQuality = 90,
                maxBytes = 10,
                maxScaleAttempts = 2,
                maxQualityAttempts = 2,
                encode = { _, _, _ -> ByteArray(100) } // 始终超限
            )
        }
    }
}
