package com.xiaomo.androidforclaw.plugins

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/manifest.ts (parsing section)
 *
 * Parses plugin manifest JSON into PluginManifestFull.
 * Android adaptation: uses org.json instead of JSON5 / node:fs.
 */
object PluginManifestParser {

    const val PLUGIN_MANIFEST_FILENAME = "openclaw.plugin.json"

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parse a JSON string into a PluginManifestFull.
     * Aligned with TS loadPluginManifest().
     */
    fun parse(json: String, manifestPath: String = ""): PluginManifestLoadResult {
        val raw: JSONObject = try {
            JSONObject(json)
        } catch (e: Exception) {
            return PluginManifestLoadResult.Failure(
                error = "failed to parse plugin manifest: ${e.message}",
                manifestPath = manifestPath,
            )
        }
        return parseObject(raw, manifestPath)
    }

    /**
     * Parse an already-deserialized JSONObject.
     */
    fun parseObject(raw: JSONObject, manifestPath: String = ""): PluginManifestLoadResult {
        val id = raw.optString("id", "").trim()
        if (id.isEmpty()) {
            return PluginManifestLoadResult.Failure("plugin manifest requires id", manifestPath)
        }
        val configSchemaRaw = raw.optJSONObject("configSchema")
        if (configSchemaRaw == null) {
            return PluginManifestLoadResult.Failure(
                "plugin manifest requires configSchema",
                manifestPath,
            )
        }
        val configSchema = jsonObjectToMap(configSchemaRaw)
        val kind = parsePluginKind(raw.opt("kind"))
        val enabledByDefault = raw.optBoolean("enabledByDefault", false)
        val legacyPluginIds = normalizeStringList(raw.optJSONArray("legacyPluginIds"))
        val autoEnableProviders =
            normalizeStringList(raw.optJSONArray("autoEnableWhenConfiguredProviders"))
        val name = raw.optString("name", "").trim().ifEmpty { null }
        val description = raw.optString("description", "").trim().ifEmpty { null }
        val version = raw.optString("version", "").trim().ifEmpty { null }
        val channels = normalizeStringList(raw.optJSONArray("channels"))
        val providers = normalizeStringList(raw.optJSONArray("providers"))
        val modelSupport = normalizeModelSupport(raw.optJSONObject("modelSupport"))
        val cliBackends = normalizeStringList(raw.optJSONArray("cliBackends"))
        val providerAuthEnvVars = normalizeStringListRecord(raw.optJSONObject("providerAuthEnvVars"))
        val providerAuthChoices = normalizeProviderAuthChoices(raw.optJSONArray("providerAuthChoices"))
        val skills = normalizeStringList(raw.optJSONArray("skills"))
        val contracts = normalizeManifestContracts(raw.optJSONObject("contracts"))
        val configContracts = normalizeManifestConfigContracts(raw.optJSONObject("configContracts"))
        val channelConfigs = normalizeChannelConfigs(raw.optJSONObject("channelConfigs"))
        val uiHints = raw.optJSONObject("uiHints")?.let { parseUiHints(it) }

        return PluginManifestLoadResult.Success(
            manifest = PluginManifestFull(
                id = id,
                configSchema = configSchema,
                enabledByDefault = enabledByDefault,
                legacyPluginIds = legacyPluginIds.ifEmpty { null },
                autoEnableWhenConfiguredProviders = autoEnableProviders.ifEmpty { null },
                kind = kind,
                channels = channels.ifEmpty { null },
                providers = providers.ifEmpty { null },
                modelSupport = modelSupport,
                cliBackends = cliBackends.ifEmpty { null },
                providerAuthEnvVars = providerAuthEnvVars,
                providerAuthChoices = providerAuthChoices,
                skills = skills.ifEmpty { null },
                name = name,
                description = description,
                version = version,
                uiHints = uiHints,
                contracts = contracts,
                configContracts = configContracts,
                channelConfigs = channelConfigs,
            ),
            manifestPath = manifestPath,
        )
    }

    // -----------------------------------------------------------------------
    // Normalization helpers  (aligned with TS normalizeStringList, etc.)
    // -----------------------------------------------------------------------

    fun normalizeStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val v = arr.opt(i)
            if (v is String) v.trim().ifEmpty { null } else null
        }
    }

    private fun normalizeStringListRecord(obj: JSONObject?): Map<String, List<String>>? {
        if (obj == null) return null
        val result = mutableMapOf<String, List<String>>()
        for (key in obj.keys()) {
            val providerId = key.trim()
            if (providerId.isEmpty()) continue
            val values = normalizeStringList(obj.optJSONArray(key))
            if (values.isEmpty()) continue
            result[providerId] = values
        }
        return result.ifEmpty { null }
    }

    private fun normalizeModelSupport(obj: JSONObject?): PluginManifestModelSupport? {
        if (obj == null) return null
        val modelPrefixes = normalizeStringList(obj.optJSONArray("modelPrefixes"))
        val modelPatterns = normalizeStringList(obj.optJSONArray("modelPatterns"))
        if (modelPrefixes.isEmpty() && modelPatterns.isEmpty()) return null
        return PluginManifestModelSupport(
            modelPrefixes = modelPrefixes.ifEmpty { null },
            modelPatterns = modelPatterns.ifEmpty { null },
        )
    }

    private fun normalizeManifestContracts(obj: JSONObject?): PluginManifestContracts? {
        if (obj == null) return null
        val memoryEmbeddingProviders = normalizeStringList(obj.optJSONArray("memoryEmbeddingProviders"))
        val speechProviders = normalizeStringList(obj.optJSONArray("speechProviders"))
        val realtimeTranscriptionProviders =
            normalizeStringList(obj.optJSONArray("realtimeTranscriptionProviders"))
        val realtimeVoiceProviders = normalizeStringList(obj.optJSONArray("realtimeVoiceProviders"))
        val mediaUnderstandingProviders =
            normalizeStringList(obj.optJSONArray("mediaUnderstandingProviders"))
        val imageGenerationProviders =
            normalizeStringList(obj.optJSONArray("imageGenerationProviders"))
        val videoGenerationProviders =
            normalizeStringList(obj.optJSONArray("videoGenerationProviders"))
        val musicGenerationProviders =
            normalizeStringList(obj.optJSONArray("musicGenerationProviders"))
        val webFetchProviders = normalizeStringList(obj.optJSONArray("webFetchProviders"))
        val webSearchProviders = normalizeStringList(obj.optJSONArray("webSearchProviders"))
        val tools = normalizeStringList(obj.optJSONArray("tools"))

        val contracts = PluginManifestContracts(
            memoryEmbeddingProviders = memoryEmbeddingProviders.ifEmpty { null },
            speechProviders = speechProviders.ifEmpty { null },
            realtimeTranscriptionProviders = realtimeTranscriptionProviders.ifEmpty { null },
            realtimeVoiceProviders = realtimeVoiceProviders.ifEmpty { null },
            mediaUnderstandingProviders = mediaUnderstandingProviders.ifEmpty { null },
            imageGenerationProviders = imageGenerationProviders.ifEmpty { null },
            videoGenerationProviders = videoGenerationProviders.ifEmpty { null },
            musicGenerationProviders = musicGenerationProviders.ifEmpty { null },
            webFetchProviders = webFetchProviders.ifEmpty { null },
            webSearchProviders = webSearchProviders.ifEmpty { null },
            tools = tools.ifEmpty { null },
        )
        // Return null if all fields are null
        return if (contracts == PluginManifestContracts()) null else contracts
    }

    private fun normalizeManifestConfigContracts(obj: JSONObject?): PluginManifestConfigContracts? {
        if (obj == null) return null
        val dangerousFlags = normalizeDangerousConfigFlags(obj.optJSONArray("dangerousFlags"))
        val secretInputsObj = obj.optJSONObject("secretInputs")
        val secretInputs = if (secretInputsObj != null) {
            val paths = normalizeSecretInputPaths(secretInputsObj.optJSONArray("paths"))
            if (paths.isNullOrEmpty()) null
            else {
                val bundledDefaultEnabled = when {
                    secretInputsObj.has("bundledDefaultEnabled") ->
                        secretInputsObj.optBoolean("bundledDefaultEnabled")
                    else -> null
                }
                PluginManifestSecretInputContracts(
                    bundledDefaultEnabled = bundledDefaultEnabled,
                    paths = paths,
                )
            }
        } else null

        if (dangerousFlags == null && secretInputs == null) return null
        return PluginManifestConfigContracts(
            dangerousFlags = dangerousFlags,
            secretInputs = secretInputs,
        )
    }

    private fun normalizeDangerousConfigFlags(
        arr: JSONArray?,
    ): List<PluginManifestDangerousConfigFlag>? {
        if (arr == null) return null
        val result = mutableListOf<PluginManifestDangerousConfigFlag>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val path = entry.optString("path", "").trim()
            if (path.isEmpty()) continue
            val equalsVal = entry.opt("equals")
            if (equalsVal == null && !entry.isNull("equals")) continue
            if (equalsVal != null && equalsVal !is String && equalsVal !is Number
                && equalsVal !is Boolean && equalsVal != JSONObject.NULL
            ) continue
            val normalizedEquals = if (equalsVal == JSONObject.NULL) null else equalsVal
            result.add(PluginManifestDangerousConfigFlag(path, normalizedEquals))
        }
        return result.ifEmpty { null }
    }

    private fun normalizeSecretInputPaths(arr: JSONArray?): List<PluginManifestSecretInputPath>? {
        if (arr == null) return null
        val result = mutableListOf<PluginManifestSecretInputPath>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val path = entry.optString("path", "").trim()
            if (path.isEmpty()) continue
            val expected = if (entry.optString("expected", "") == "string") "string" else null
            result.add(PluginManifestSecretInputPath(path, expected))
        }
        return result.ifEmpty { null }
    }

    private fun normalizeProviderAuthChoices(
        arr: JSONArray?,
    ): List<PluginManifestProviderAuthChoice>? {
        if (arr == null) return null
        val result = mutableListOf<PluginManifestProviderAuthChoice>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val provider = entry.optString("provider", "").trim()
            val method = entry.optString("method", "").trim()
            val choiceId = entry.optString("choiceId", "").trim()
            if (provider.isEmpty() || method.isEmpty() || choiceId.isEmpty()) continue

            val onboardingScopes = normalizeStringList(entry.optJSONArray("onboardingScopes"))
                .mapNotNull { PluginManifestOnboardingScope.fromString(it) }

            result.add(
                PluginManifestProviderAuthChoice(
                    provider = provider,
                    method = method,
                    choiceId = choiceId,
                    choiceLabel = entry.optString("choiceLabel", "").trim().ifEmpty { null },
                    choiceHint = entry.optString("choiceHint", "").trim().ifEmpty { null },
                    assistantPriority = entry.opt("assistantPriority")?.let {
                        if (it is Number) it.toInt() else null
                    },
                    assistantVisibility = entry.optString("assistantVisibility", "").trim()
                        .let { if (it == "visible" || it == "manual-only") it else null },
                    deprecatedChoiceIds = normalizeStringList(
                        entry.optJSONArray("deprecatedChoiceIds")
                    ).ifEmpty { null },
                    groupId = entry.optString("groupId", "").trim().ifEmpty { null },
                    groupLabel = entry.optString("groupLabel", "").trim().ifEmpty { null },
                    groupHint = entry.optString("groupHint", "").trim().ifEmpty { null },
                    optionKey = entry.optString("optionKey", "").trim().ifEmpty { null },
                    cliFlag = entry.optString("cliFlag", "").trim().ifEmpty { null },
                    cliOption = entry.optString("cliOption", "").trim().ifEmpty { null },
                    cliDescription = entry.optString("cliDescription", "").trim().ifEmpty { null },
                    onboardingScopes = onboardingScopes.ifEmpty { null },
                )
            )
        }
        return result.ifEmpty { null }
    }

    private fun normalizeChannelConfigs(
        obj: JSONObject?,
    ): Map<String, PluginManifestChannelConfig>? {
        if (obj == null) return null
        val result = mutableMapOf<String, PluginManifestChannelConfig>()
        for (key in obj.keys()) {
            val channelId = key.trim()
            if (channelId.isEmpty()) continue
            val rawEntry = obj.optJSONObject(key) ?: continue
            val schema = rawEntry.optJSONObject("schema") ?: continue
            val uiHints = rawEntry.optJSONObject("uiHints")?.let { parseUiHints(it) }
            val label = rawEntry.optString("label", "").trim().ifEmpty { null }
            val description = rawEntry.optString("description", "").trim().ifEmpty { null }
            val preferOver = normalizeStringList(rawEntry.optJSONArray("preferOver"))
            result[channelId] = PluginManifestChannelConfig(
                schema = jsonObjectToMap(schema),
                uiHints = uiHints,
                label = label,
                description = description,
                preferOver = preferOver.ifEmpty { null },
            )
        }
        return result.ifEmpty { null }
    }

    private fun parsePluginKind(raw: Any?): Any? {
        if (raw is String) return PluginKind.fromString(raw)
        if (raw is JSONArray) {
            val kinds = (0 until raw.length()).mapNotNull {
                val s = raw.optString(it, "")
                PluginKind.fromString(s)
            }
            return when (kinds.size) {
                0 -> null
                1 -> kinds[0]
                else -> kinds
            }
        }
        return null
    }

    private fun parseUiHints(obj: JSONObject): Map<String, PluginConfigUiHint> {
        val result = mutableMapOf<String, PluginConfigUiHint>()
        for (key in obj.keys()) {
            val hintObj = obj.optJSONObject(key) ?: continue
            result[key] = PluginConfigUiHint(
                label = hintObj.optString("label", "").ifEmpty { null },
                help = hintObj.optString("help", "").ifEmpty { null },
                tags = normalizeStringList(hintObj.optJSONArray("tags")).ifEmpty { null },
                advanced = if (hintObj.has("advanced")) hintObj.optBoolean("advanced") else null,
                sensitive = if (hintObj.has("sensitive")) hintObj.optBoolean("sensitive") else null,
                placeholder = hintObj.optString("placeholder", "").ifEmpty { null },
            )
        }
        return result
    }

    // -----------------------------------------------------------------------
    // JSON utility
    // -----------------------------------------------------------------------

    fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        for (key in obj.keys()) {
            result[key] = jsonValueToKotlin(obj.opt(key))
        }
        return result
    }

    private fun jsonValueToKotlin(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> (0 until value.length()).map { jsonValueToKotlin(value.opt(it)) }
        else -> value
    }
}
