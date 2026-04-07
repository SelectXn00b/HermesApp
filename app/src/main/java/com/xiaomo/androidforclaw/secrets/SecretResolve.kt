package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/resolve.ts
 *
 * Secret reference resolution: env, file, exec sources.
 * Android adaptation: no exec spawning (not supported on Android),
 * file source reads directly, env from System.getenv().
 */

import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// ---------- Constants ----------

private const val DEFAULT_PROVIDER_CONCURRENCY = 4
private const val DEFAULT_MAX_REFS_PER_PROVIDER = 512
private const val DEFAULT_MAX_BATCH_BYTES = 256 * 1024
private const val DEFAULT_FILE_MAX_BYTES = 1024 * 1024
private const val DEFAULT_FILE_TIMEOUT_MS = 5_000L

// ---------- Error types ----------

/**
 * Provider-scoped resolution error.
 * Aligned with TS SecretProviderResolutionError.
 */
class SecretProviderResolutionError(
    val source: SecretRefSource,
    val provider: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    val scope: String = "provider"
}

/**
 * Ref-scoped resolution error.
 * Aligned with TS SecretRefResolutionError.
 */
class SecretRefResolutionError(
    val source: SecretRefSource,
    val provider: String,
    val refId: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    val scope: String = "ref"
}

fun isProviderScopedSecretResolutionError(value: Any?): Boolean =
    value is SecretProviderResolutionError

// ---------- Cache ----------

/**
 * Cache for secret ref resolution.
 * Aligned with TS SecretRefResolveCache.
 */
class SecretRefResolveCache {
    val resolvedByRefKey = ConcurrentHashMap<String, Any?>()
    val filePayloadByProvider = ConcurrentHashMap<String, Any?>()
}

// ---------- Resolution limits ----------

private data class ResolutionLimits(
    val maxProviderConcurrency: Int,
    val maxRefsPerProvider: Int,
    val maxBatchBytes: Int
)

// ---------- Provider config types ----------

/**
 * Secret provider configuration.
 * Aligned with TS SecretProviderConfig (env/file/exec).
 */
sealed class SecretProviderConfig {
    abstract val source: SecretRefSource

    data class Env(
        val allowlist: Set<String>? = null
    ) : SecretProviderConfig() {
        override val source = SecretRefSource.ENV
    }

    data class FileConfig(
        val path: String,
        val mode: String = "json", // "json" | "singleValue"
        val timeoutMs: Long = DEFAULT_FILE_TIMEOUT_MS,
        val maxBytes: Long = DEFAULT_FILE_MAX_BYTES.toLong()
    ) : SecretProviderConfig() {
        override val source = SecretRefSource.FILE
    }

    data class Exec(
        val command: String,
        val args: List<String> = emptyList(),
        val timeoutMs: Long = 5_000,
        val jsonOnly: Boolean = true
    ) : SecretProviderConfig() {
        override val source = SecretRefSource.EXEC
    }
}

// ---------- Resolve options ----------

data class ResolveSecretRefOptions(
    val secretProviders: Map<String, SecretProviderConfig> = emptyMap(),
    val envOverrides: Map<String, String> = emptyMap(),
    val cache: SecretRefResolveCache = SecretRefResolveCache()
)

// ---------- Private helpers ----------

private fun toProviderKey(source: SecretRefSource, provider: String): String =
    "${source.value}:$provider"

private fun resolveConfiguredProvider(
    ref: SecretRef,
    options: ResolveSecretRefOptions,
    defaultsCarrier: SecretRefDefaultsCarrier? = null
): SecretProviderConfig {
    val providerConfig = options.secretProviders[ref.provider]
    if (providerConfig != null) {
        if (providerConfig.source != ref.source) {
            throw SecretProviderResolutionError(
                source = ref.source,
                provider = ref.provider,
                message = "Secret provider \"${ref.provider}\" has source \"${providerConfig.source.value}\" " +
                        "but ref requests \"${ref.source.value}\"."
            )
        }
        return providerConfig
    }
    // Default env provider
    if (ref.source == SecretRefSource.ENV) {
        val defaultAlias = if (defaultsCarrier != null) {
            resolveDefaultSecretProviderAlias(defaultsCarrier, SecretRefSource.ENV)
        } else {
            DEFAULT_SECRET_PROVIDER_ALIAS
        }
        if (ref.provider == defaultAlias) {
            return SecretProviderConfig.Env()
        }
    }
    throw SecretProviderResolutionError(
        source = ref.source,
        provider = ref.provider,
        message = "Secret provider \"${ref.provider}\" is not configured " +
                "(ref: ${ref.source.value}:${ref.provider}:${ref.id})."
    )
}

// ---------- Env resolution ----------

private fun resolveEnvRefs(
    refs: List<SecretRef>,
    providerName: String,
    providerConfig: SecretProviderConfig.Env,
    envOverrides: Map<String, String>
): Map<String, Any?> {
    val resolved = mutableMapOf<String, Any?>()
    for (ref in refs) {
        if (providerConfig.allowlist != null && ref.id !in providerConfig.allowlist) {
            throw SecretRefResolutionError(
                source = SecretRefSource.ENV,
                provider = providerName,
                refId = ref.id,
                message = "Environment variable \"${ref.id}\" is not allowlisted in " +
                        "secrets.providers.$providerName.allowlist."
            )
        }
        // Android: use overrides first, then System.getenv()
        val envValue = envOverrides[ref.id] ?: System.getenv(ref.id)
        if (envValue.isNullOrBlank()) {
            throw SecretRefResolutionError(
                source = SecretRefSource.ENV,
                provider = providerName,
                refId = ref.id,
                message = "Environment variable \"${ref.id}\" is missing or empty."
            )
        }
        resolved[ref.id] = envValue
    }
    return resolved
}

// ---------- File resolution ----------

private fun readFileProviderPayload(
    providerName: String,
    providerConfig: SecretProviderConfig.FileConfig,
    cache: SecretRefResolveCache
): Any? {
    val cacheKey = providerName
    cache.filePayloadByProvider[cacheKey]?.let { return it }

    val filePath = providerConfig.path
    val file = File(filePath)
    if (!file.exists()) {
        throw SecretProviderResolutionError(
            source = SecretRefSource.FILE,
            provider = providerName,
            message = "File provider \"$providerName\" path does not exist: $filePath"
        )
    }
    if (file.isDirectory) {
        throw SecretProviderResolutionError(
            source = SecretRefSource.FILE,
            provider = providerName,
            message = "File provider \"$providerName\" path must be a file: $filePath"
        )
    }
    if (file.length() > providerConfig.maxBytes) {
        throw SecretProviderResolutionError(
            source = SecretRefSource.FILE,
            provider = providerName,
            message = "File provider \"$providerName\" exceeded maxBytes (${providerConfig.maxBytes})."
        )
    }

    val text = file.readText()
    val payload: Any? = if (providerConfig.mode == "singleValue") {
        text.trimEnd('\n', '\r')
    } else {
        try {
            val json = JSONObject(text)
            // Convert JSONObject to Map
            val map = mutableMapOf<String, Any?>()
            for (key in json.keys()) {
                map[key] = json.opt(key)
            }
            map
        } catch (e: Exception) {
            throw SecretProviderResolutionError(
                source = SecretRefSource.FILE,
                provider = providerName,
                message = "File provider \"$providerName\" payload is not a JSON object."
            )
        }
    }

    cache.filePayloadByProvider[cacheKey] = payload
    return payload
}

private fun resolveFileRefs(
    refs: List<SecretRef>,
    providerName: String,
    providerConfig: SecretProviderConfig.FileConfig,
    cache: SecretRefResolveCache
): Map<String, Any?> {
    val payload = try {
        readFileProviderPayload(providerName, providerConfig, cache)
    } catch (err: Throwable) {
        if (err is SecretProviderResolutionError || err is SecretRefResolutionError) throw err
        throw SecretProviderResolutionError(
            source = SecretRefSource.FILE,
            provider = providerName,
            message = describeUnknownError(err),
            cause = err
        )
    }

    val mode = providerConfig.mode
    val resolved = mutableMapOf<String, Any?>()

    if (mode == "singleValue") {
        for (ref in refs) {
            if (ref.id != SINGLE_VALUE_FILE_REF_ID) {
                throw SecretRefResolutionError(
                    source = SecretRefSource.FILE,
                    provider = providerName,
                    refId = ref.id,
                    message = "singleValue file provider \"$providerName\" expects ref id \"$SINGLE_VALUE_FILE_REF_ID\"."
                )
            }
            resolved[ref.id] = payload
        }
        return resolved
    }

    for (ref in refs) {
        try {
            resolved[ref.id] = readJsonPointer(payload, ref.id, throwOnMissing = true)
        } catch (err: Throwable) {
            throw SecretRefResolutionError(
                source = SecretRefSource.FILE,
                provider = providerName,
                refId = ref.id,
                message = describeUnknownError(err),
                cause = err
            )
        }
    }
    return resolved
}

// ---------- Public API ----------

/**
 * Resolve multiple secret refs to their values.
 * Aligned with TS resolveSecretRefValues.
 *
 * Android adaptation: exec source is not supported (throws).
 */
suspend fun resolveSecretRefValues(
    refs: List<SecretRef>,
    options: ResolveSecretRefOptions
): Map<String, Any?> {
    if (refs.isEmpty()) return emptyMap()

    val uniqueRefs = mutableMapOf<String, SecretRef>()
    for (ref in refs) {
        val id = ref.id.trim()
        if (id.isEmpty()) throw IllegalArgumentException("Secret reference id is empty.")
        if (ref.source == SecretRefSource.EXEC && !isValidExecSecretRefId(id)) {
            throw IllegalArgumentException(
                "${formatExecSecretRefIdValidationMessage()} (ref: ${ref.source.value}:${ref.provider}:$id)."
            )
        }
        uniqueRefs[secretRefKey(ref)] = ref.copy(id = id)
    }

    // Group refs by provider
    data class ProviderGroup(
        val source: SecretRefSource,
        val providerName: String,
        val refs: MutableList<SecretRef> = mutableListOf()
    )

    val grouped = mutableMapOf<String, ProviderGroup>()
    for (ref in uniqueRefs.values) {
        val key = toProviderKey(ref.source, ref.provider)
        grouped.getOrPut(key) { ProviderGroup(ref.source, ref.provider) }.refs.add(ref)
    }

    val resolved = mutableMapOf<String, Any?>()
    for ((_, group) in grouped) {
        if (group.refs.size > DEFAULT_MAX_REFS_PER_PROVIDER) {
            throw SecretProviderResolutionError(
                source = group.source,
                provider = group.providerName,
                message = "Secret provider \"${group.providerName}\" exceeded maxRefsPerProvider ($DEFAULT_MAX_REFS_PER_PROVIDER)."
            )
        }
        val providerConfig = resolveConfiguredProvider(group.refs[0], options)
        val values = when (providerConfig) {
            is SecretProviderConfig.Env -> resolveEnvRefs(
                group.refs, group.providerName, providerConfig, options.envOverrides
            )
            is SecretProviderConfig.FileConfig -> resolveFileRefs(
                group.refs, group.providerName, providerConfig, options.cache
            )
            is SecretProviderConfig.Exec -> {
                // Android: exec source is not supported
                throw SecretProviderResolutionError(
                    source = SecretRefSource.EXEC,
                    provider = group.providerName,
                    message = "Exec secret provider source is not supported on Android."
                )
            }
        }
        for (ref in group.refs) {
            val key = secretRefKey(ref)
            if (!values.containsKey(ref.id)) {
                throw SecretRefResolutionError(
                    source = group.source,
                    provider = group.providerName,
                    refId = ref.id,
                    message = "Secret provider \"${group.providerName}\" did not return id \"${ref.id}\"."
                )
            }
            resolved[key] = values[ref.id]
        }
    }
    return resolved
}

/**
 * Resolve a single secret ref to its value.
 * Aligned with TS resolveSecretRefValue.
 */
suspend fun resolveSecretRefValue(
    ref: SecretRef,
    options: ResolveSecretRefOptions
): Any? {
    val cache = options.cache
    val key = secretRefKey(ref)
    cache.resolvedByRefKey[key]?.let { return it }

    val resolved = resolveSecretRefValues(listOf(ref), options)
    if (!resolved.containsKey(key)) {
        throw SecretRefResolutionError(
            source = ref.source,
            provider = ref.provider,
            refId = ref.id,
            message = "Secret reference \"$key\" resolved to no value."
        )
    }
    val value = resolved[key]
    cache.resolvedByRefKey[key] = value
    return value
}

/**
 * Resolve a single secret ref to a non-empty string value.
 * Aligned with TS resolveSecretRefString.
 */
suspend fun resolveSecretRefString(
    ref: SecretRef,
    options: ResolveSecretRefOptions
): String {
    val resolved = resolveSecretRefValue(ref, options)
    if (!isNonEmptyString(resolved)) {
        throw IllegalStateException(
            "Secret reference \"${ref.source.value}:${ref.provider}:${ref.id}\" " +
                    "resolved to a non-string or empty value."
        )
    }
    return resolved as String
}
