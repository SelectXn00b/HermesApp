/**
 * Shared helpers for tool backend selection.
 *
 * Ported from tools/tool_backend_helpers.py
 */
package com.xiaomo.hermes.hermes.tools

private const val _DEFAULT_BROWSER_PROVIDER: String = "local"
private const val _DEFAULT_MODAL_MODE: String = "auto"
private val _VALID_MODAL_MODES: Set<String> = setOf("auto", "direct", "managed")

fun managedNousToolsEnabled(): Boolean =
    System.getenv("HERMES_ENABLE_NOUS_MANAGED_TOOLS")?.lowercase()?.let {
        it == "true" || it == "1" || it == "yes"
    } ?: false

fun normalizeBrowserCloudProvider(value: Any?): String {
    val provider = (value?.toString() ?: _DEFAULT_BROWSER_PROVIDER).trim().lowercase()
    return provider.ifEmpty { _DEFAULT_BROWSER_PROVIDER }
}

fun coerceModalMode(value: Any?): String {
    val mode = (value?.toString() ?: _DEFAULT_MODAL_MODE).trim().lowercase()
    return if (mode in _VALID_MODAL_MODES) mode else _DEFAULT_MODAL_MODE
}

fun normalizeModalMode(value: Any?): String = coerceModalMode(value)

fun hasDirectModalCredentials(): Boolean {
    val tokenId = System.getenv("MODAL_TOKEN_ID")
    val tokenSecret = System.getenv("MODAL_TOKEN_SECRET")
    if (!tokenId.isNullOrEmpty() && !tokenSecret.isNullOrEmpty()) return true
    val home = System.getProperty("user.home") ?: return false
    return java.io.File(home, ".modal.toml").exists()
}

fun resolveModalBackendState(
    modalMode: Any?,
    hasDirect: Boolean,
    managedReady: Boolean,
): Map<String, Any?> {
    val requestedMode = coerceModalMode(modalMode)
    val normalizedMode = normalizeModalMode(modalMode)
    val managedModeBlocked = requestedMode == "managed" && !managedNousToolsEnabled()

    val selectedBackend: String? = when (normalizedMode) {
        "managed" -> if (managedNousToolsEnabled() && managedReady) "managed" else null
        "direct" -> if (hasDirect) "direct" else null
        else -> when {
            managedNousToolsEnabled() && managedReady -> "managed"
            hasDirect -> "direct"
            else -> null
        }
    }
    return mapOf(
        "requested_mode" to requestedMode,
        "mode" to normalizedMode,
        "has_direct" to hasDirect,
        "managed_ready" to managedReady,
        "managed_mode_blocked" to managedModeBlocked,
        "selected_backend" to selectedBackend,
    )
}

fun resolveOpenaiAudioApiKey(): String {
    val voice = System.getenv("VOICE_TOOLS_OPENAI_KEY") ?: ""
    val openai = System.getenv("OPENAI_API_KEY") ?: ""
    return (voice.ifEmpty { openai }).trim()
}

fun prefersGateway(configSection: String): Boolean = false

fun falKeyIsConfigured(): Boolean {
    val v = System.getenv("FAL_KEY") ?: return false
    return v.isNotBlank()
}
