package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.*
import org.junit.Test

/**
 * Covers pure-logic subset of WecomCrypto that can run on the JVM without
 * Robolectric — PKCS7 padding + random nonce generation.
 *
 * The WXBizMsgCrypt encrypt/decrypt paths depend on `android.util.Base64`
 * which is stubbed as "return default value" under `isReturnDefaultValues`
 * and returns null for decode — so those paths need Robolectric / on-device
 * tests.
 */
class WecomCryptoTest {

    // ─── PKCS7Encoder — mirrors Python wecom_crypto.py ────────────────────

    @Test
    fun `encode pads up to BLOCK_SIZE multiple`() {
        val input = ByteArray(1) { 0x41 } // "A"
        val padded = PKCS7Encoder.encode(input)
        assertEquals(PKCS7Encoder.BLOCK_SIZE, padded.size)
        // Remaining bytes should all equal the pad value.
        val padVal = padded.last().toInt() and 0xFF
        assertEquals(PKCS7Encoder.BLOCK_SIZE - 1, padVal)
        for (i in 1 until padded.size) {
            assertEquals("byte $i should be pad", padVal, padded[i].toInt() and 0xFF)
        }
    }

    @Test
    fun `encode adds a full block when input is already a multiple`() {
        val input = ByteArray(PKCS7Encoder.BLOCK_SIZE) { 0x42 }
        val padded = PKCS7Encoder.encode(input)
        // Exact multiple → still append a whole block of BLOCK_SIZE padding.
        assertEquals(PKCS7Encoder.BLOCK_SIZE * 2, padded.size)
        val padVal = padded.last().toInt() and 0xFF
        assertEquals(PKCS7Encoder.BLOCK_SIZE, padVal)
    }

    @Test
    fun `decode strips PKCS7 pad`() {
        val input = "Hello, WeCom!".toByteArray(Charsets.UTF_8)
        val padded = PKCS7Encoder.encode(input)
        val stripped = PKCS7Encoder.decode(padded)
        assertArrayEquals(input, stripped)
    }

    @Test
    fun `decode rejects empty input`() {
        try {
            PKCS7Encoder.decode(ByteArray(0))
            fail("expected DecryptError")
        } catch (e: DecryptError) {
            // expected
        }
    }

    @Test
    fun `decode rejects pad byte outside valid range`() {
        // Last byte 0x00 → pad < 1 → rejected.
        try {
            PKCS7Encoder.decode(ByteArray(32) { 0x00 })
            fail("expected DecryptError")
        } catch (e: DecryptError) {
            // expected
        }
        // Last byte > BLOCK_SIZE → rejected.
        try {
            val bad = ByteArray(32) { 0x00 }
            bad[31] = 0xFF.toByte() // 255 > 32
            PKCS7Encoder.decode(bad)
            fail("expected DecryptError")
        } catch (e: DecryptError) {
            // expected
        }
    }

    @Test
    fun `decode rejects malformed pad bytes`() {
        // Claim pad=5 but only the last byte is 5, others aren't.
        val bad = ByteArray(32) { 0x41 }
        bad[31] = 5
        bad[30] = 5
        bad[29] = 5
        bad[28] = 5
        bad[27] = 4 // wrong!
        try {
            PKCS7Encoder.decode(bad)
            fail("expected DecryptError for malformed padding")
        } catch (e: DecryptError) {
            // expected
        }
    }

    // ─── WXBizMsgCrypt.randomNonce — pure Kotlin random ───────────────────

    @Test
    fun `randomNonce returns default-length alphanumeric`() {
        val nonce = WXBizMsgCrypt.randomNonce()
        assertEquals(10, nonce.length)
        assertTrue("nonce should be alphanumeric: $nonce", nonce.all { it.isLetterOrDigit() })
    }

    @Test
    fun `randomNonce honours requested length`() {
        val nonce = WXBizMsgCrypt.randomNonce(length = 24)
        assertEquals(24, nonce.length)
    }

    @Test
    fun `randomNonce produces varied output`() {
        // Two calls should (with overwhelming probability for length>=6) differ.
        val a = WXBizMsgCrypt.randomNonce(length = 16)
        val b = WXBizMsgCrypt.randomNonce(length = 16)
        assertNotEquals(a, b)
    }
}
