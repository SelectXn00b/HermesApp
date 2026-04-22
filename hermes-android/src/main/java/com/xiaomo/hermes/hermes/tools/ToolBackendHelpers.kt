package com.xiaomo.hermes.hermes.tools

/**
 * Shared helpers for tool backend selection.
 * Ported from tool_backend_helpers.py
 */
object ToolBackendHelpers {

    private val VALID_MODAL_MODES = setOf("auto", "direct", "managed")
    private const val DEFAULT_BROWSER_PROVIDER = "local"
    private const val DEFAULT_MODAL_MODE = "auto"

    /**
     * Return True when the hidden Nous-managed tools feature flag is enabled.
     */
    fun managedNousToolsEnabled(): Boolean =
        System.getenv("HERMES_ENABLE_NOUS_MANAGED_TOOLS")?.lowercase()?.let {
            it == "true" || it == "1" || it == "yes"
        } ?: false

    /**
     * Return a normalized browser provider key.
     */
    fun normalizeBrowserCloudProvider(value: String?): String {
        val provider = (value ?: DEFAULT_BROWSER_PROVIDER).trim().lowercase()
        return provider.ifEmpty { DEFAULT_BROWSER_PROVIDER }
    }

    /**
     * Return the requested modal mode when valid, else the default.
     */
    fun coerceModalMode(value: String?): String {
        val mode = (value ?: DEFAULT_MODAL_MODE).trim().lowercase()
        return if (mode in VALID_MODAL_MODES) mode else DEFAULT_MODAL_MODE
    }

    /**
     * Return True when direct Modal credentials/config are available.
     */
    fun hasDirectModalCredentials(): Boolean {
        val tokenId = System.getenv("MODAL_TOKEN_ID")
        val tokenSecret = System.getenv("MODAL_TOKEN_SECRET")
        if (!tokenId.isNullOrEmpty() && !tokenSecret.isNullOrEmpty()) return true
        val home = System.getProperty("user.home") ?: return false
        return java.io.File(home, ".modal.toml").exists()
    }

    /**
     * Data class for modal backend state.
     */
    data class ModalBackendState(
        val requestedMode: String,
        val mode: String,
        val hasDirect: Boolean,
        val managedReady: Boolean,
        val managedModeBlocked: Boolean,
        val selectedBackend: String?)

    /**
     * Resolve direct vs managed Modal backend selection.
     */
    fun resolveModalBackendState(
        modalMode: String?,
        hasDirect: Boolean = hasDirectModalCredentials(),
        managedReady: Boolean): ModalBackendState {
        val requestedMode = coerceModalMode(modalMode)
        val managedModeBlocked = requestedMode == "managed" && !managedNousToolsEnabled()

        val selectedBackend = when (requestedMode) {
            "managed" -> if (managedNousToolsEnabled() && managedReady) "managed" else null
            "direct" -> if (hasDirect) "direct" else null
            else -> when {
                managedNousToolsEnabled() && managedReady -> "managed"
                hasDirect -> "direct"
                else -> null
            }
        }

        return ModalBackendState(
            requestedMode = requestedMode,
            mode = requestedMode,
            hasDirect = hasDirect,
            managedReady = managedReady,
            managedModeBlocked = managedModeBlocked,
            selectedBackend = selectedBackend)
    }

    /**
     * Prepend the voice-tools key, falling back to the normal key.
     */
    fun resolveOpenaiAudioApiKey(): String =
        (System.getenv("VOICE_TOOLS_OPENAI_KEY") ?: "").ifEmpty {
            System.getenv("OPENAI_API_KEY") ?: ""
        }.trim()


}
