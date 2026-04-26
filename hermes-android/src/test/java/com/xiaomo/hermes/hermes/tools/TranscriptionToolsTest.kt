package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * Tests for TranscriptionTools.kt — Android stub; no provider is reachable
 * (`_getProvider` always returns "none"). Pure helpers `_validateAudioFile` +
 * `_normalizeLocalModel` + `isSttEnabled` + `transcribeAudio` dispatch
 * branches are covered here.
 *
 * Covers TC-TOOL-312-a (文件尺寸超上限 → 拒绝 with "File too large").
 */
class TranscriptionToolsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── Module constants ──
    @Test
    fun `MAX_FILE_SIZE is 25MB`() {
        assertEquals(25L * 1024L * 1024L, MAX_FILE_SIZE)
    }

    @Test
    fun `default local model is base`() {
        assertEquals("base", DEFAULT_LOCAL_MODEL)
    }

    @Test
    fun `default local language is english`() {
        assertEquals("en", DEFAULT_LOCAL_STT_LANGUAGE)
    }

    @Test
    fun `local command and language env names`() {
        assertEquals("HERMES_LOCAL_STT_COMMAND", LOCAL_STT_COMMAND_ENV)
        assertEquals("HERMES_LOCAL_STT_LANGUAGE", LOCAL_STT_LANGUAGE_ENV)
    }

    @Test
    fun `common local bin dirs`() {
        assertTrue("/opt/homebrew/bin" in COMMON_LOCAL_BIN_DIRS)
        assertTrue("/usr/local/bin" in COMMON_LOCAL_BIN_DIRS)
    }

    // ── SUPPORTED_FORMATS ──
    @Test
    fun `SUPPORTED_FORMATS includes common audio types`() {
        assertTrue(".mp3" in SUPPORTED_FORMATS)
        assertTrue(".wav" in SUPPORTED_FORMATS)
        assertTrue(".m4a" in SUPPORTED_FORMATS)
        assertTrue(".ogg" in SUPPORTED_FORMATS)
        assertTrue(".flac" in SUPPORTED_FORMATS)
    }

    @Test
    fun `LOCAL_NATIVE_AUDIO_FORMATS is wav family`() {
        assertEquals(setOf(".wav", ".aiff", ".aif"), LOCAL_NATIVE_AUDIO_FORMATS)
    }

    @Test
    fun `OPENAI_MODELS and GROQ_MODELS populated`() {
        assertTrue("whisper-1" in OPENAI_MODELS)
        assertTrue("gpt-4o-transcribe" in OPENAI_MODELS)
        assertTrue("whisper-large-v3-turbo" in GROQ_MODELS)
    }

    // ── isSttEnabled — default true ──
    @Test
    fun `isSttEnabled default true`() {
        assertTrue(isSttEnabled(emptyMap()))
    }

    @Test
    fun `isSttEnabled bool false`() {
        assertFalse(isSttEnabled(mapOf("enabled" to false)))
    }

    @Test
    fun `isSttEnabled string parse`() {
        assertTrue(isSttEnabled(mapOf("enabled" to "yes")))
        assertTrue(isSttEnabled(mapOf("enabled" to "1")))
        assertTrue(isSttEnabled(mapOf("enabled" to "true")))
        assertFalse(isSttEnabled(mapOf("enabled" to "0")))
        assertFalse(isSttEnabled(mapOf("enabled" to "no")))
    }

    // ── transcribeAudio — file-not-found / wrong-type / unsupported format ──
    @Test
    fun `transcribeAudio missing file returns error`() {
        val result = transcribeAudio(tmp.root.absolutePath + "/not-there.wav")
        assertEquals(false, result["success"])
        assertTrue(
            "must mention file not found: ${result["error"]}",
            (result["error"] as String).contains("not found"))
    }

    @Test
    fun `transcribeAudio directory rejected`() {
        val result = transcribeAudio(tmp.root.absolutePath)
        assertEquals(false, result["success"])
        assertTrue(
            "must reject directory: ${result["error"]}",
            (result["error"] as String).contains("not a file") ||
                (result["error"] as String).contains("not found"))
    }

    @Test
    fun `transcribeAudio unsupported format rejected`() {
        val f = tmp.newFile("audio.txt")
        f.writeText("not really audio")
        val result = transcribeAudio(f.absolutePath)
        assertEquals(false, result["success"])
        assertTrue(
            "must mention unsupported format: ${result["error"]}",
            (result["error"] as String).contains("Unsupported format"))
    }

    // ── R-TOOL-312 / TC-TOOL-312-a: > 25 MB rejected ──
    /**
     * TC-TOOL-312-a — `_validateAudioFile` must reject files larger than
     * `MAX_FILE_SIZE` (25 MB) with an error string containing "File too
     * large". Uses `RandomAccessFile.setLength` to cheaply sparse-allocate a
     * 26 MB file without actually writing 26 MB of bytes.
     */
    @Test
    fun `file size cap`() {
        val big = tmp.newFile("huge.wav")
        RandomAccessFile(big, "rw").use { raf ->
            raf.setLength(MAX_FILE_SIZE + 1) // 25 MB + 1 byte — just over cap
        }
        assertTrue("sparse file should be > 25MB", big.length() > MAX_FILE_SIZE)

        val result = transcribeAudio(big.absolutePath)
        assertEquals(false, result["success"])
        assertEquals("", result["transcript"])
        val err = result["error"] as String
        assertTrue("must mention file too large: $err", err.contains("File too large"))
        assertTrue("must mention 25MB cap: $err", err.contains("25MB"))
    }

    @Test
    fun `file exactly at limit is not rejected for size`() {
        // Boundary check — MAX_FILE_SIZE bytes exactly passes the size gate.
        // The call will still fail at provider dispatch (no STT on Android),
        // but the error must NOT be "File too large".
        val f = tmp.newFile("exact.wav")
        RandomAccessFile(f, "rw").use { raf -> raf.setLength(MAX_FILE_SIZE) }
        val result = transcribeAudio(f.absolutePath)
        val err = result["error"] as String
        // Whatever the error is, it's not a size-cap error.
        assertFalse(
            "size-cap must NOT fire at exactly MAX_FILE_SIZE: $err",
            err.contains("File too large"))
    }

    // ── transcribeAudio with disabled STT config ──
    @Test
    fun `transcribeAudio with supported-size-but-no-provider falls through to no-provider error`() {
        val f = tmp.newFile("ok.wav")
        f.writeBytes(ByteArray(1024)) // 1 KB, well under cap
        val result = transcribeAudio(f.absolutePath)
        assertEquals(false, result["success"])
        val err = result["error"] as String
        // On Android: no provider available. Must NOT be a file-validation error.
        assertFalse(err.contains("File too large"))
        assertFalse(err.contains("not found"))
        assertFalse(err.contains("Unsupported format"))
        // Expected error references "STT disabled" OR "No STT provider"
        val shape = err.contains("STT") || err.contains("provider")
        assertTrue("should mention STT/provider unavailability: $err", shape)
    }
}
