package com.xiaomo.hermes.hermes.gateway.platforms

/**
 * WeCom BizMsgCrypt-compatible AES-CBC encryption for callback mode.
 *
 * Implements the same wire format as Tencent's official WXBizMsgCrypt
 * SDK so that WeCom can verify, encrypt, and decrypt callback payloads.
 *
 * Ported from gateway/platforms/wecom_crypto.py
 */

import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * WeCom crypto errors.
 */
sealed class WeComCryptoError(message: String) : Exception(message)
class SignatureError(message: String) : WeComCryptoError(message)
class DecryptError(message: String) : WeComCryptoError(message)
class EncryptError(message: String) : WeComCryptoError(message)

/**
 * PKCS7 encoder/decoder for AES-CBC padding.
 */
object PKCS7Encoder {
    const val BLOCK_SIZE = 32

    fun encode(text: ByteArray): ByteArray {
        val amountToPad = BLOCK_SIZE - (text.size % BLOCK_SIZE)
        val pad = ByteArray(amountToPad) { amountToPad.toByte() }
        return text + pad
    }

    fun decode(decrypted: ByteArray): ByteArray {
        if (decrypted.isEmpty()) throw DecryptError("empty decrypted payload")
        val pad = decrypted.last().toInt() and 0xFF
        if (pad < 1 || pad > BLOCK_SIZE) throw DecryptError("invalid PKCS7 padding")
        if (decrypted.takeLast(pad).any { (it.toInt() and 0xFF) != pad }) {
            throw DecryptError("malformed PKCS7 padding")
        }
        return decrypted.dropLast(pad).toByteArray()
    }
}

/**
 * SHA1 signature helper.
 */
private fun sha1Signature(token: String, timestamp: String, nonce: String, encrypt: String): String {
    val parts = listOf(token, timestamp, nonce, encrypt).sorted()
    val digest = MessageDigest.getInstance("SHA-1")
    val hash = digest.digest(parts.joinToString("").toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}

/**
 * WeCom BizMsgCrypt helper.
 *
 * Compatible with Tencent's official WXBizMsgCrypt SDK wire format.
 */
class WXBizMsgCrypt(
    /** Verification token. */
    val token: String,
    /** Encoding AES key (43 chars, base64-encoded). */
    encodingAesKey: String,
    /** Corp ID or receive ID. */
    val receiveId: String) {
    companion object {
        private const val TAG = "WXBizMsgCrypt"

        /**
         * Generate a random nonce.
         */
        fun randomNonce(length: Int = 10): String {
            val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            return (1..length).map { alphabet.random() }.joinToString("")
        }
    }

    /** AES key (decoded from encodingAesKey). */
    val key: ByteArray

    /** IV (first 16 bytes of key). */
    val iv: ByteArray

    init {
        require(token.isNotEmpty()) { "token is required" }
        require(encodingAesKey.isNotEmpty()) { "encoding_aes_key is required" }
        require(encodingAesKey.length == 43) { "encoding_aes_key must be 43 chars" }
        require(receiveId.isNotEmpty()) { "receive_id is required" }

        key = Base64.decode(encodingAesKey + "=", Base64.DEFAULT)
        iv = key.copyOfRange(0, 16)
    }

    /**
     * Verify URL for WeCom callback setup.
     *
     * @param msg_signature  Signature from WeCom.
     * @param timestamp      Timestamp from WeCom.
     * @param nonce          Nonce from WeCom.
     * @param echostr        Encrypted echo string from WeCom.
     * @return Decrypted plaintext.
     */
    fun verifyUrl(msgSignature: String, timestamp: String, nonce: String, echostr: String): String {
        val plain = decrypt(msgSignature, timestamp, nonce, echostr)
        return String(plain, Charsets.UTF_8)
    }

    /**
     * Decrypt an encrypted payload.
     *
     * @param msg_signature  Signature from WeCom.
     * @param timestamp      Timestamp from WeCom.
     * @param nonce          Nonce from WeCom.
     * @param encrypt        Encrypted payload.
     * @return Decrypted bytes.
     */
    fun decrypt(msgSignature: String, timestamp: String, nonce: String, encrypt: String): ByteArray {
        val expected = sha1Signature(token, timestamp, nonce, encrypt)
        if (expected != msgSignature) {
            throw SignatureError("signature mismatch")
        }

        try {
            val cipherText = Base64.decode(encrypt, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val padded = cipher.doFinal(cipherText)
            val plain = PKCS7Encoder.decode(padded)

            // Skip 16-byte random prefix
            val content = plain.copyOfRange(16, plain.size)

            // Read 4-byte length (network byte order)
            val xmlLength = ((content[0].toInt() and 0xFF) shl 24) or
                ((content[1].toInt() and 0xFF) shl 16) or
                ((content[2].toInt() and 0xFF) shl 8) or
                (content[3].toInt() and 0xFF)

            val xmlContent = content.copyOfRange(4, 4 + xmlLength)
            val receiveIdBytes = content.copyOfRange(4 + xmlLength, content.size)
            val receiveIdStr = String(receiveIdBytes, Charsets.UTF_8)

            if (receiveIdStr != receiveId) {
                throw DecryptError("receive_id mismatch")
            }

            return xmlContent
        } catch (e: WeComCryptoError) {
            throw e
        } catch (e: Exception) {
            throw DecryptError("decrypt failed: ${e.message}")
        }
    }

    /**
     * Encrypt a plaintext message.
     *
     * @param plaintext  The plaintext to encrypt.
     * @param nonce      Optional nonce (generated if null).
     * @param timestamp  Optional timestamp (current time if null).
     * @return XML string with encrypted payload.
     */
    fun encrypt(plaintext: String, nonce: String? = null, timestamp: String? = null): String {
        val actualNonce = nonce ?: randomNonce()
        val actualTimestamp = timestamp ?: (System.currentTimeMillis() / 1000).toString()
        val encrypt = encryptBytes(plaintext.toByteArray(Charsets.UTF_8))
        val signature = sha1Signature(token, actualTimestamp, actualNonce, encrypt)

        return buildString {
            append("<xml>")
            append("<Encrypt><![CDATA[$encrypt]]></Encrypt>")
            append("<MsgSignature><![CDATA[$signature]]></MsgSignature>")
            append("<TimeStamp>$actualTimestamp</TimeStamp>")
            append("<Nonce><![CDATA[$actualNonce]]></Nonce>")
            append("</xml>")
        }
    }

    /**
     * Encrypt raw bytes.
     */
    private fun encryptBytes(raw: ByteArray): String {
        try {
            val randomPrefix = ByteArray(16) { Random.nextInt(256).toByte() }
            val msgLen = ByteArray(4).apply {
                val len = raw.size
                this[0] = ((len shr 24) and 0xFF).toByte()
                this[1] = ((len shr 16) and 0xFF).toByte()
                this[2] = ((len shr 8) and 0xFF).toByte()
                this[3] = (len and 0xFF).toByte()
            }
            val payload = randomPrefix + msgLen + raw + receiveId.toByteArray(Charsets.UTF_8)
            val padded = PKCS7Encoder.encode(payload)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val encrypted = cipher.doFinal(padded)
            return Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw EncryptError("encrypt failed: ${e.message}")
        }
    }
}
