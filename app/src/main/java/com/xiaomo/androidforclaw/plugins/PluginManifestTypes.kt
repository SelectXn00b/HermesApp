package com.xiaomo.androidforclaw.plugins

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/manifest.ts
 *
 * Manifest types and parsing for plugin manifests.
 * Android adaptation: JSON parsing via org.json instead of JSON5/node:fs.
 */

// ---------------------------------------------------------------------------
// Manifest types (aligned with TS PluginManifest and sub-types)
// ---------------------------------------------------------------------------

data class PluginManifestChannelConfig(
    val schema: Map<String, Any?>,
    val uiHints: Map<String, PluginConfigUiHint>? = null,
    val label: String? = null,
    val description: String? = null,
    val preferOver: List<String>? = null,
)

data class PluginManifestModelSupport(
    val modelPrefixes: List<String>? = null,
    val modelPatterns: List<String>? = null,
)

data class PluginManifestDangerousConfigFlag(
    val path: String,
    val equals: Any?, // String | Number | Boolean | null
)

data class PluginManifestSecretInputPath(
    val path: String,
    val expected: String? = null, // "string"
)

data class PluginManifestSecretInputContracts(
    val bundledDefaultEnabled: Boolean? = null,
    val paths: List<PluginManifestSecretInputPath>,
)

data class PluginManifestConfigContracts(
    val dangerousFlags: List<PluginManifestDangerousConfigFlag>? = null,
    val secretInputs: PluginManifestSecretInputContracts? = null,
)

data class PluginManifestContracts(
    val memoryEmbeddingProviders: List<String>? = null,
    val speechProviders: List<String>? = null,
    val realtimeTranscriptionProviders: List<String>? = null,
    val realtimeVoiceProviders: List<String>? = null,
    val mediaUnderstandingProviders: List<String>? = null,
    val imageGenerationProviders: List<String>? = null,
    val videoGenerationProviders: List<String>? = null,
    val musicGenerationProviders: List<String>? = null,
    val webFetchProviders: List<String>? = null,
    val webSearchProviders: List<String>? = null,
    val tools: List<String>? = null,
)

enum class PluginManifestOnboardingScope(val value: String) {
    TEXT_INFERENCE("text-inference"),
    IMAGE_GENERATION("image-generation");

    companion object {
        fun fromString(raw: String): PluginManifestOnboardingScope? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

data class PluginManifestProviderAuthChoice(
    val provider: String,
    val method: String,
    val choiceId: String,
    val choiceLabel: String? = null,
    val choiceHint: String? = null,
    val assistantPriority: Int? = null,
    val assistantVisibility: String? = null, // "visible" | "manual-only"
    val deprecatedChoiceIds: List<String>? = null,
    val groupId: String? = null,
    val groupLabel: String? = null,
    val groupHint: String? = null,
    val optionKey: String? = null,
    val cliFlag: String? = null,
    val cliOption: String? = null,
    val cliDescription: String? = null,
    val onboardingScopes: List<PluginManifestOnboardingScope>? = null,
)

/**
 * Full plugin manifest.
 * Aligned with TS PluginManifest.
 */
data class PluginManifestFull(
    val id: String,
    val configSchema: Map<String, Any?>,
    val enabledByDefault: Boolean = false,
    val legacyPluginIds: List<String>? = null,
    val autoEnableWhenConfiguredProviders: List<String>? = null,
    val kind: Any? = null, // PluginKind | List<PluginKind> | null
    val channels: List<String>? = null,
    val providers: List<String>? = null,
    val modelSupport: PluginManifestModelSupport? = null,
    val cliBackends: List<String>? = null,
    val providerAuthEnvVars: Map<String, List<String>>? = null,
    val providerAuthChoices: List<PluginManifestProviderAuthChoice>? = null,
    val skills: List<String>? = null,
    val name: String? = null,
    val description: String? = null,
    val version: String? = null,
    val uiHints: Map<String, PluginConfigUiHint>? = null,
    val contracts: PluginManifestContracts? = null,
    val configContracts: PluginManifestConfigContracts? = null,
    val channelConfigs: Map<String, PluginManifestChannelConfig>? = null,
) {
    /** Parse the kind field into a list of PluginKind. */
    fun resolveKinds(): List<PluginKind> {
        return when (kind) {
            is PluginKind -> listOf(kind)
            is List<*> -> kind.mapNotNull { it as? PluginKind }
            is String -> PluginKind.fromString(kind)?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }

    fun hasKind(target: PluginKind): Boolean = resolveKinds().contains(target)
}

// ---------------------------------------------------------------------------
// Manifest load result
// ---------------------------------------------------------------------------
sealed class PluginManifestLoadResult {
    data class Success(
        val manifest: PluginManifestFull,
        val manifestPath: String,
    ) : PluginManifestLoadResult()

    data class Failure(
        val error: String,
        val manifestPath: String,
    ) : PluginManifestLoadResult()
}
