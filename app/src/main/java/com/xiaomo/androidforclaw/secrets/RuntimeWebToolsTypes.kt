package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/runtime-web-tools.types.ts
 * - src/secrets/runtime-web-tools-state.ts
 *
 * Web tools metadata types and state management.
 */

/**
 * Diagnostic code for web tool resolution.
 * Aligned with TS RuntimeWebDiagnosticCode.
 */
enum class RuntimeWebDiagnosticCode(val value: String) {
    WEB_SEARCH_PROVIDER_INVALID_AUTODETECT("WEB_SEARCH_PROVIDER_INVALID_AUTODETECT"),
    WEB_SEARCH_AUTODETECT_SELECTED("WEB_SEARCH_AUTODETECT_SELECTED"),
    WEB_SEARCH_KEY_UNRESOLVED_FALLBACK_USED("WEB_SEARCH_KEY_UNRESOLVED_FALLBACK_USED"),
    WEB_SEARCH_KEY_UNRESOLVED_NO_FALLBACK("WEB_SEARCH_KEY_UNRESOLVED_NO_FALLBACK"),
    WEB_FETCH_PROVIDER_INVALID_AUTODETECT("WEB_FETCH_PROVIDER_INVALID_AUTODETECT"),
    WEB_FETCH_AUTODETECT_SELECTED("WEB_FETCH_AUTODETECT_SELECTED"),
    WEB_FETCH_PROVIDER_KEY_UNRESOLVED_FALLBACK_USED("WEB_FETCH_PROVIDER_KEY_UNRESOLVED_FALLBACK_USED"),
    WEB_FETCH_PROVIDER_KEY_UNRESOLVED_NO_FALLBACK("WEB_FETCH_PROVIDER_KEY_UNRESOLVED_NO_FALLBACK")
}

/**
 * A single diagnostic entry.
 * Aligned with TS RuntimeWebDiagnostic.
 */
data class RuntimeWebDiagnostic(
    val code: RuntimeWebDiagnosticCode,
    val message: String,
    val path: String? = null
)

/**
 * Web search metadata.
 * Aligned with TS RuntimeWebSearchMetadata.
 */
data class RuntimeWebSearchMetadata(
    val providerConfigured: String? = null,
    val providerSource: String = "none", // "configured" | "auto-detect" | "none"
    val selectedProvider: String? = null,
    val selectedProviderKeySource: String? = null, // "config" | "secretRef" | "env" | "missing"
    val perplexityTransport: String? = null, // "search_api" | "chat_completions"
    val diagnostics: List<RuntimeWebDiagnostic> = emptyList()
)

/**
 * Web fetch metadata.
 * Aligned with TS RuntimeWebFetchMetadata.
 */
data class RuntimeWebFetchMetadata(
    val providerConfigured: String? = null,
    val providerSource: String = "none", // "configured" | "auto-detect" | "none"
    val selectedProvider: String? = null,
    val selectedProviderKeySource: String? = null, // "config" | "secretRef" | "env" | "missing"
    val diagnostics: List<RuntimeWebDiagnostic> = emptyList()
)

/**
 * Composite web tools metadata.
 * Aligned with TS RuntimeWebToolsMetadata.
 */
data class RuntimeWebToolsMetadata(
    val search: RuntimeWebSearchMetadata = RuntimeWebSearchMetadata(),
    val fetch: RuntimeWebFetchMetadata = RuntimeWebFetchMetadata(),
    val diagnostics: List<RuntimeWebDiagnostic> = emptyList()
)

/**
 * Module-level state for active web tools metadata.
 * Aligned with TS runtime-web-tools-state.ts.
 */
object RuntimeWebToolsState {
    @Volatile
    private var activeMetadata: RuntimeWebToolsMetadata? = null

    fun clear() {
        activeMetadata = null
    }

    fun set(metadata: RuntimeWebToolsMetadata) {
        activeMetadata = metadata.copy()
    }

    fun get(): RuntimeWebToolsMetadata? =
        activeMetadata?.copy()
}
