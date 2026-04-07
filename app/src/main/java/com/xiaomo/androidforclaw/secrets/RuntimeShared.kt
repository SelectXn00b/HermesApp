package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/runtime-shared.ts
 *
 * Shared runtime types and helpers for secret ref resolution context.
 */

/**
 * Warning codes emitted during secret resolution.
 * Aligned with TS SecretResolverWarningCode.
 */
enum class SecretResolverWarningCode(val value: String) {
    SECRETS_REF_OVERRIDES_PLAINTEXT("SECRETS_REF_OVERRIDES_PLAINTEXT"),
    SECRETS_REF_IGNORED_INACTIVE_SURFACE("SECRETS_REF_IGNORED_INACTIVE_SURFACE"),
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
 * Secret resolver warning emitted during preparation.
 * Aligned with TS SecretResolverWarning.
 */
data class SecretResolverWarning(
    val code: SecretResolverWarningCode,
    val path: String,
    val message: String
)

/**
 * A pending assignment: a ref that, once resolved, should be applied.
 * Aligned with TS SecretAssignment.
 */
data class SecretAssignment(
    val ref: SecretRef,
    val path: String,
    val expected: SecretExpectedResolvedValue,
    val apply: (Any?) -> Unit
)

/**
 * Resolution context that collects assignments and warnings.
 * Aligned with TS ResolverContext.
 */
class ResolverContext(
    val envOverrides: Map<String, String> = emptyMap(),
    val cache: SecretRefResolveCache = SecretRefResolveCache()
) {
    val warnings = mutableListOf<SecretResolverWarning>()
    private val warningKeys = mutableSetOf<String>()
    val assignments = mutableListOf<SecretAssignment>()

    /**
     * Push an assignment.
     * Aligned with TS pushAssignment.
     */
    fun pushAssignment(assignment: SecretAssignment) {
        assignments.add(assignment)
    }

    /**
     * Push a warning (deduplicated).
     * Aligned with TS pushWarning.
     */
    fun pushWarning(warning: SecretResolverWarning) {
        val warningKey = "${warning.code.value}:${warning.path}:${warning.message}"
        if (warningKey in warningKeys) return
        warningKeys.add(warningKey)
        warnings.add(warning)
    }

    /**
     * Push an inactive surface warning.
     * Aligned with TS pushInactiveSurfaceWarning.
     */
    fun pushInactiveSurfaceWarning(path: String, details: String? = null) {
        pushWarning(SecretResolverWarning(
            code = SecretResolverWarningCode.SECRETS_REF_IGNORED_INACTIVE_SURFACE,
            path = path,
            message = if (details?.trim()?.isNotEmpty() == true) {
                "$path: $details"
            } else {
                "$path: secret ref is configured on an inactive surface; " +
                        "skipping resolution until it becomes active."
            }
        ))
    }
}

/**
 * Collect a secret input assignment if the value contains a ref.
 * Aligned with TS collectSecretInputAssignment.
 */
fun collectSecretInputAssignment(
    value: Any?,
    path: String,
    expected: SecretExpectedResolvedValue,
    defaults: SecretDefaults?,
    context: ResolverContext,
    active: Boolean = true,
    inactiveReason: String? = null,
    apply: (Any?) -> Unit
) {
    val ref = coerceSecretRef(value, defaults) ?: return
    if (!active) {
        context.pushInactiveSurfaceWarning(path, inactiveReason)
        return
    }
    context.pushAssignment(SecretAssignment(
        ref = ref,
        path = path,
        expected = expected,
        apply = apply
    ))
}

/**
 * Apply resolved values to previously-collected assignments.
 * Aligned with TS applyResolvedAssignments.
 */
fun applyResolvedAssignments(
    assignments: List<SecretAssignment>,
    resolved: Map<String, Any?>
) {
    for (assignment in assignments) {
        val key = secretRefKey(assignment.ref)
        if (!resolved.containsKey(key)) {
            throw IllegalStateException("Secret reference \"$key\" resolved to no value.")
        }
        val value = resolved[key]
        assertExpectedResolvedSecretValue(
            value = value,
            expected = assignment.expected,
            errorMessage = if (assignment.expected == SecretExpectedResolvedValue.STRING) {
                "${assignment.path} resolved to a non-string or empty value."
            } else {
                "${assignment.path} resolved to an unsupported value type."
            }
        )
        assignment.apply(value)
    }
}

/**
 * Check if a config value has the enabled flag set (or is not a map).
 * Aligned with TS isEnabledFlag.
 */
fun isEnabledFlag(value: Any?): Boolean {
    if (value !is Map<*, *>) return true
    return value["enabled"] != false
}

/**
 * Coerce a value to a SecretRef if it looks like one.
 * Aligned with TS coerceSecretRef from config/types.secrets.ts.
 */
fun coerceSecretRef(value: Any?, defaults: SecretDefaults?): SecretRef? {
    if (value !is Map<*, *>) return null
    val sourceStr = value["\$source"] as? String ?: return null
    val source = SecretRefSource.fromString(sourceStr) ?: return null
    val provider = value["\$provider"] as? String
        ?: when (source) {
            SecretRefSource.ENV -> defaults?.env ?: DEFAULT_SECRET_PROVIDER_ALIAS
            SecretRefSource.FILE -> defaults?.file ?: DEFAULT_SECRET_PROVIDER_ALIAS
            SecretRefSource.EXEC -> defaults?.exec ?: DEFAULT_SECRET_PROVIDER_ALIAS
        }
    val id = value["\$id"] as? String ?: return null
    if (id.isBlank()) return null
    return SecretRef(source = source, provider = provider, id = id)
}
