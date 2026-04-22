package com.xiaomo.hermes.hermes.tools

/**
 * Hermes-managed Camofox state helpers.
 *
 * Provides profile-scoped identity and state directory paths for Camofox
 * persistent browser profiles.  When managed persistence is enabled, Hermes
 * sends a deterministic userId derived from the active profile so that
 * Camofox can map it to the same persistent browser profile directory
 * across restarts.
 *
 * Ported 1:1 from tools/browser_camofox_state.py
 */

import com.xiaomo.hermes.hermes.getHermesHome
import java.io.File
import java.security.MessageDigest

const val CAMOFOX_STATE_DIR_NAME = "browser_auth"
const val CAMOFOX_STATE_SUBDIR = "camofox"

/** Return the profile-scoped root directory for Camofox persistence. */
fun getCamofoxStateDir(): File =
    File(File(getHermesHome(), CAMOFOX_STATE_DIR_NAME), CAMOFOX_STATE_SUBDIR)

/**
 * Return the stable Hermes-managed Camofox identity for this profile.
 *
 * The user identity is profile-scoped (same Hermes profile = same userId).
 * The session key is scoped to the logical browser task so newly created
 * tabs within the same profile reuse the same identity contract.
 */
fun getCamofoxIdentity(taskId: String? = null): Map<String, String> {
    val scopeRoot = getCamofoxStateDir().absolutePath
    val logicalScope = taskId ?: "default"
    // Python uses uuid.uuid5(NAMESPACE_URL, name) which is SHA1-based.
    // Java's UUID.nameUUIDFromBytes is MD5-based (v3), so inline v5 here.
    val namespaceUrlBytes = byteArrayOf(
        0x6b.toByte(), 0xa7.toByte(), 0xb8.toByte(), 0x11.toByte(),
        0x9d.toByte(), 0xad.toByte(), 0x11.toByte(), 0xd1.toByte(),
        0x80.toByte(), 0xb4.toByte(), 0x00.toByte(), 0xc0.toByte(),
        0x4f.toByte(), 0xd4.toByte(), 0x30.toByte(), 0xc8.toByte())
    val uuid5Hex = { name: String ->
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(namespaceUrlBytes)
        sha1.update(name.toByteArray(Charsets.UTF_8))
        val digest = sha1.digest().copyOf(16)
        digest[6] = ((digest[6].toInt() and 0x0F) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
        digest.joinToString("") { String.format("%02x", it.toInt() and 0xFF) }
    }
    val userDigest = uuid5Hex("camofox-user:$scopeRoot").take(10)
    val sessionDigest = uuid5Hex("camofox-session:$scopeRoot:$logicalScope").take(16)
    return mapOf(
        "user_id" to "hermes_$userDigest",
        "session_key" to "task_$sessionDigest")
}
