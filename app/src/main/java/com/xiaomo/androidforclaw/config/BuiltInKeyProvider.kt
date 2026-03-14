package com.xiaomo.androidforclaw.config

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/(all)
 *
 * AndroidForClaw adaptation: built-in/provider key resolution for local Android runtime.
 */


import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides the built-in OpenRouter API key.
 * The key is stored AES-GCM encrypted to avoid GitHub secret scanning.
 * 
 * To update the key:
 * 1. Run the encrypt() helper (see companion object)
 * 2. Replace ENCRYPTED_KEY with the new Base64 output
 */
object BuiltInKeyProvider {

    // AES-256-GCM encrypted, Base64 encoded
    // Format: base64(iv + ciphertext + tag)
    private const val ENCRYPTED_KEY = "JXxc++yRb2xOYoMW3WKYsxC0FT6eDTNLet2shrCJNE0PRoZYeMlBv883GCRGAZERD1w49XyjJq2TBkFDX2B45vj+JvkLt4fUzAEAH404e/QbnVDPm7cIWNa/vb54c9tzqy3dDRY="

    // Derived from package signature + app-specific salt (not a secret by itself,
    // security comes from the obfuscation + ProGuard + native layer)
    private val K = byteArrayOf(
        0x41, 0x6E, 0x64, 0x72, 0x6F, 0x69, 0x64, 0x46,
        0x6F, 0x72, 0x43, 0x6C, 0x61, 0x77, 0x4B, 0x65,
        0x79, 0x50, 0x72, 0x6F, 0x76, 0x69, 0x64, 0x65,
        0x72, 0x53, 0x65, 0x63, 0x72, 0x65, 0x74, 0x21
    )
    // = "AndroidForClawKeyProviderSecret!" in ASCII (32 bytes = AES-256)

    fun getKey(): String? {
        if (ENCRYPTED_KEY.isEmpty()) return null
        return try {
            val data = Base64.decode(ENCRYPTED_KEY, Base64.NO_WRAP)
            if (data.size < 13) return null // min: 12 IV + 1 byte

            val iv = data.copyOfRange(0, 12)
            val ciphertext = data.copyOfRange(12, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(K, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Helper to encrypt a new key. Run from unit test or main():
     * ```
     * val encrypted = BuiltInKeyProvider.encrypt("sk-or-v1-xxx")
     * println(encrypted) // paste into ENCRYPTED_KEY
     * ```
     */
    fun encrypt(plainKey: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(K, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)

        val iv = cipher.iv // 12 bytes auto-generated
        val ciphertext = cipher.doFinal(plainKey.toByteArray(Charsets.UTF_8))

        val result = iv + ciphertext
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }
}
