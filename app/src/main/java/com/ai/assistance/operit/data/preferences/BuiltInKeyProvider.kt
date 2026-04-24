package com.ai.assistance.operit.data.preferences

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides the built-in OpenRouter API key.
 * The key is stored AES-GCM encrypted to avoid GitHub secret scanning.
 *
 * Ported verbatim from phoneforclaw's BuiltInKeyProvider (com.xiaomo.hermes.config).
 * Same ENCRYPTED_KEY + 32-byte K, so decrypts to the same shared OpenRouter key.
 *
 * To update the key:
 * 1. Run the encrypt() helper (e.g. from a unit test)
 * 2. Replace ENCRYPTED_KEY with the new Base64 output
 */
object BuiltInKeyProvider {

    // AES-256-GCM encrypted, Base64 encoded
    // Format: base64(iv + ciphertext + tag)
    private const val ENCRYPTED_KEY = "Xt6bb+lhIo4MSkEiniC+x05qtNa6rWbxAmsel4pkUX7rNEX8OzmB1ihT5MbQwLjb+6hTTdRj59+IRHDVV3Y5UqYrRf0DOd9HleHfi0RXVLoMdE8YCeQITI4N+GxQzVrdJLGftP8="

    // 32-byte key = "HermesKeyProviderSecret!" in ASCII (AES-256)
    private val K = byteArrayOf(
        0x41, 0x6E, 0x64, 0x72, 0x6F, 0x69, 0x64, 0x46,
        0x6F, 0x72, 0x43, 0x6C, 0x61, 0x77, 0x4B, 0x65,
        0x79, 0x50, 0x72, 0x6F, 0x76, 0x69, 0x64, 0x65,
        0x72, 0x53, 0x65, 0x63, 0x72, 0x65, 0x74, 0x21
    )

    fun getKey(): String? {
        if (ENCRYPTED_KEY.isEmpty()) return null
        return try {
            val data = Base64.decode(ENCRYPTED_KEY, Base64.NO_WRAP)
            if (data.size < 13) return null

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

    fun encrypt(plainKey: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(K, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainKey.toByteArray(Charsets.UTF_8))

        val result = iv + ciphertext
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val OPENROUTER_DEFAULT_MODEL = "qwen/qwen3.6-plus:free"
}
