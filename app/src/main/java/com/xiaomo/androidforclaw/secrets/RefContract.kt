package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/ref-contract.ts
 *
 * Secret reference contracts: validation patterns, default provider aliases, ref key formatting.
 */

/** Default provider alias when none is configured. Aligned with TS DEFAULT_SECRET_PROVIDER_ALIAS. */
const val DEFAULT_SECRET_PROVIDER_ALIAS = "default"

/** Pattern for valid provider aliases. */
val SECRET_PROVIDER_ALIAS_PATTERN = Regex("^[a-z][a-z0-9_-]{0,63}\$")

/** Pattern for valid exec secret ref IDs. */
val EXEC_SECRET_REF_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}\$")

/** Pattern for valid file secret ref segments. */
private val FILE_SECRET_REF_SEGMENT_PATTERN = Regex("^(?:[^~]|~0|~1)*\$")

/** Pattern for file secret ref IDs. */
val FILE_SECRET_REF_ID_PATTERN = Regex("^(?:value|/(?:[^~]|~0|~1)*(?:/(?:[^~]|~0|~1)*)*)\$")

/** Single-value file ref ID. */
const val SINGLE_VALUE_FILE_REF_ID = "value"

/**
 * Secret reference source type.
 * Aligned with TS SecretRefSource.
 */
enum class SecretRefSource(val value: String) {
    ENV("env"),
    FILE("file"),
    EXEC("exec");

    companion object {
        fun fromString(s: String): SecretRefSource? = entries.find { it.value == s }
    }
}

/**
 * Secret reference — pointer to a secret value in external storage.
 * Aligned with TS SecretRef (from config/types.secrets.ts).
 */
data class SecretRef(
    val source: SecretRefSource,
    val provider: String,
    val id: String
)

/**
 * Compute a unique cache key for a secret ref.
 * Aligned with TS secretRefKey.
 */
fun secretRefKey(ref: SecretRef): String =
    "${ref.source.value}:${ref.provider}:${ref.id}"

/**
 * Carrier for secret ref default provider resolution.
 * Aligned with TS SecretRefDefaultsCarrier.
 */
data class SecretRefDefaultsCarrier(
    val defaults: SecretDefaults? = null,
    val providers: Map<String, SecretProviderConfigEntry>? = null
)

data class SecretDefaults(
    val env: String? = null,
    val file: String? = null,
    val exec: String? = null
)

data class SecretProviderConfigEntry(
    val source: String? = null
)

/**
 * Resolve the default secret provider alias for a given source.
 * Aligned with TS resolveDefaultSecretProviderAlias.
 */
fun resolveDefaultSecretProviderAlias(
    carrier: SecretRefDefaultsCarrier,
    source: SecretRefSource,
    preferFirstProviderForSource: Boolean = false
): String {
    val configured = when (source) {
        SecretRefSource.ENV -> carrier.defaults?.env
        SecretRefSource.FILE -> carrier.defaults?.file
        SecretRefSource.EXEC -> carrier.defaults?.exec
    }
    if (configured?.trim()?.isNotEmpty() == true) {
        return configured.trim()
    }

    if (preferFirstProviderForSource) {
        carrier.providers?.forEach { (providerName, provider) ->
            if (provider.source == source.value) {
                return providerName
            }
        }
    }

    return DEFAULT_SECRET_PROVIDER_ALIAS
}

/**
 * Validate a file secret ref ID.
 * Aligned with TS isValidFileSecretRefId.
 */
fun isValidFileSecretRefId(value: String): Boolean {
    if (value == SINGLE_VALUE_FILE_REF_ID) return true
    if (!value.startsWith("/")) return false
    return value.substring(1)
        .split("/")
        .all { FILE_SECRET_REF_SEGMENT_PATTERN.matches(it) }
}

/**
 * Validate a secret provider alias.
 * Aligned with TS isValidSecretProviderAlias.
 */
fun isValidSecretProviderAlias(value: String): Boolean =
    SECRET_PROVIDER_ALIAS_PATTERN.matches(value)

/**
 * Validation result for exec secret ref IDs.
 * Aligned with TS ExecSecretRefIdValidationResult.
 */
sealed class ExecSecretRefIdValidationResult {
    data object Ok : ExecSecretRefIdValidationResult()
    data class Invalid(val reason: String) : ExecSecretRefIdValidationResult()
}

/**
 * Validate an exec secret ref ID.
 * Aligned with TS validateExecSecretRefId.
 */
fun validateExecSecretRefId(value: String): ExecSecretRefIdValidationResult {
    if (!EXEC_SECRET_REF_ID_PATTERN.matches(value)) {
        return ExecSecretRefIdValidationResult.Invalid("pattern")
    }
    for (segment in value.split("/")) {
        if (segment == "." || segment == "..") {
            return ExecSecretRefIdValidationResult.Invalid("traversal-segment")
        }
    }
    return ExecSecretRefIdValidationResult.Ok
}

/**
 * Check if an exec secret ref ID is valid.
 * Aligned with TS isValidExecSecretRefId.
 */
fun isValidExecSecretRefId(value: String): Boolean =
    validateExecSecretRefId(value) is ExecSecretRefIdValidationResult.Ok

/**
 * Format a human-readable exec secret ref ID validation message.
 * Aligned with TS formatExecSecretRefIdValidationMessage.
 */
fun formatExecSecretRefIdValidationMessage(): String =
    "Exec secret reference id must match /^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}\$/ " +
            "and must not include \".\" or \"..\" path segments " +
            "(example: \"vault/openai/api-key\")."
