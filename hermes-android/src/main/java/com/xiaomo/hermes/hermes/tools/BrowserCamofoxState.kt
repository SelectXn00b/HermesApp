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
import java.util.UUID

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
    val userDigest = UUID.nameUUIDFromBytes(
        "camofox-user:$scopeRoot".toByteArray(Charsets.UTF_8)
    ).toString().replace("-", "").take(10)
    val sessionDigest = UUID.nameUUIDFromBytes(
        "camofox-session:$scopeRoot:$logicalScope".toByteArray(Charsets.UTF_8)
    ).toString().replace("-", "").take(16)
    return mapOf(
        "user_id" to "hermes_$userDigest",
        "session_key" to "task_$sessionDigest")
}
