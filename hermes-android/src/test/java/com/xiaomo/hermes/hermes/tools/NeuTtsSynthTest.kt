package com.xiaomo.hermes.hermes.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class NeuTtsSynthTest {

    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        tmpDir = Files.createTempDirectory("neutts-test").toFile()
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `_writeWav writes RIFF-WAVE header`() {
        val path = File(tmpDir, "out.wav").absolutePath
        val samples = FloatArray(100) { 0.0f }
        _writeWav(path, samples)
        val bytes = File(path).readBytes()
        // RIFF magic
        assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(bytes.copyOfRange(36, 40), Charsets.US_ASCII))
    }

    @Test
    fun `_writeWav data section matches sample count times 2 bytes`() {
        val path = File(tmpDir, "out.wav").absolutePath
        val samples = FloatArray(250) { 0.0f }
        _writeWav(path, samples)
        val bytes = File(path).readBytes()
        // Data size is at offset 40, little-endian int32.
        val buf = ByteBuffer.wrap(bytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN)
        val dataSize = buf.int
        assertEquals(samples.size * 2, dataSize)
    }

    @Test
    fun `_writeWav sample rate encoded correctly`() {
        val path = File(tmpDir, "out.wav").absolutePath
        _writeWav(path, FloatArray(10), sampleRate = 48000)
        val bytes = File(path).readBytes()
        // Sample rate is at offset 24, little-endian int32.
        val buf = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(48000, buf.int)
    }

    @Test
    fun `_writeWav default sample rate is 24000`() {
        val path = File(tmpDir, "out.wav").absolutePath
        _writeWav(path, FloatArray(10))
        val bytes = File(path).readBytes()
        val buf = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(24000, buf.int)
    }

    @Test
    fun `_writeWav clamps samples to -1 and +1`() {
        val path = File(tmpDir, "out.wav").absolutePath
        val samples = floatArrayOf(5.0f, -5.0f, 0.0f)
        _writeWav(path, samples)
        val bytes = File(path).readBytes()
        // PCM data starts at offset 44. Three int16 samples little-endian.
        val buf = ByteBuffer.wrap(bytes, 44, 6).order(ByteOrder.LITTLE_ENDIAN)
        val s0 = buf.short  // clamped +1 → 32767
        val s1 = buf.short  // clamped -1 → -32767
        val s2 = buf.short  // 0 → 0
        assertEquals(32767.toShort(), s0)
        assertEquals((-32767).toShort(), s1)
        assertEquals(0.toShort(), s2)
    }

    @Test
    fun `_writeWav creates parent directories`() {
        val nested = File(tmpDir, "a/b/c/out.wav")
        _writeWav(nested.absolutePath, FloatArray(10))
        assertTrue(nested.exists())
    }

    @Test
    fun `main does not throw`() {
        main()
    }
}
