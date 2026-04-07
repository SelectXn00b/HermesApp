package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/target-registry-types.ts
 * - src/secrets/target-registry-pattern.ts
 * - src/secrets/unsupported-surface-policy.ts
 *
 * Secret target registry: defines config paths that hold secret values.
 */

/**
 * Config file that a secret target lives in.
 * Aligned with TS SecretTargetConfigFile.
 */
enum class SecretTargetConfigFile(val value: String) {
    OPENCLAW_JSON("openclaw.json"),
    AUTH_PROFILES_JSON("auth-profiles.json")
}

/**
 * Shape of the secret at the target location.
 * Aligned with TS SecretTargetShape.
 */
enum class SecretTargetShape(val value: String) {
    SECRET_INPUT("secret_input"),
    SIBLING_REF("sibling_ref")
}

/**
 * Auth profile type for a secret target.
 * Aligned with TS AuthProfileType.
 */
enum class AuthProfileType(val value: String) {
    API_KEY("api_key"),
    TOKEN("token")
}

/**
 * A single entry in the secret target registry.
 * Aligned with TS SecretTargetRegistryEntry.
 */
data class SecretTargetRegistryEntry(
    val id: String,
    val targetType: String,
    val targetTypeAliases: List<String> = emptyList(),
    val configFile: SecretTargetConfigFile,
    val pathPattern: String,
    val refPathPattern: String? = null,
    val secretShape: SecretTargetShape,
    val expectedResolvedValue: SecretExpectedResolvedValue,
    val includeInPlan: Boolean,
    val includeInConfigure: Boolean,
    val includeInAudit: Boolean,
    val providerIdPathSegmentIndex: Int? = null,
    val accountIdPathSegmentIndex: Int? = null,
    val authProfileType: AuthProfileType? = null,
    val trackProviderShadowing: Boolean = false
)

/**
 * A resolved plan target after matching against the registry.
 * Aligned with TS ResolvedPlanTarget.
 */
data class ResolvedPlanTarget(
    val entry: SecretTargetRegistryEntry,
    val pathSegments: List<String>,
    val refPathSegments: List<String>? = null,
    val providerId: String? = null,
    val accountId: String? = null
)

/**
 * A discovered config secret target.
 * Aligned with TS DiscoveredConfigSecretTarget.
 */
data class DiscoveredConfigSecretTarget(
    val entry: SecretTargetRegistryEntry,
    val path: String,
    val pathSegments: List<String>,
    val refPath: String? = null,
    val refPathSegments: List<String>? = null,
    val value: Any? = null,
    val refValue: Any? = null,
    val providerId: String? = null,
    val accountId: String? = null
)

// ---------- Core Secret Targets (Android-relevant subset) ----------

/**
 * Core list of secret target registry entries.
 * Aligned with TS target-registry-data.ts (subset).
 */
val CORE_SECRET_TARGET_REGISTRY: List<SecretTargetRegistryEntry> = listOf(
    // Provider API keys
    SecretTargetRegistryEntry(
        id = "models.providers.*.apiKey",
        targetType = "provider-api-key",
        configFile = SecretTargetConfigFile.OPENCLAW_JSON,
        pathPattern = "models.providers.*.apiKey",
        refPathPattern = "models.providers.*.apiKeyRef",
        secretShape = SecretTargetShape.SECRET_INPUT,
        expectedResolvedValue = SecretExpectedResolvedValue.STRING,
        includeInPlan = true,
        includeInConfigure = true,
        includeInAudit = true,
        providerIdPathSegmentIndex = 2,
        trackProviderShadowing = true
    ),
    // Gateway auth token
    SecretTargetRegistryEntry(
        id = "gateway.auth.token",
        targetType = "gateway-auth-token",
        configFile = SecretTargetConfigFile.OPENCLAW_JSON,
        pathPattern = "gateway.auth.token",
        secretShape = SecretTargetShape.SECRET_INPUT,
        expectedResolvedValue = SecretExpectedResolvedValue.STRING,
        includeInPlan = true,
        includeInConfigure = true,
        includeInAudit = true
    ),
    // Hooks token
    SecretTargetRegistryEntry(
        id = "hooks.token",
        targetType = "hooks-token",
        configFile = SecretTargetConfigFile.OPENCLAW_JSON,
        pathPattern = "hooks.token",
        secretShape = SecretTargetShape.SECRET_INPUT,
        expectedResolvedValue = SecretExpectedResolvedValue.STRING,
        includeInPlan = false,
        includeInConfigure = false,
        includeInAudit = true
    ),
    // Auth profiles: api_key
    SecretTargetRegistryEntry(
        id = "auth-profiles.profiles.*.credentials.apiKey",
        targetType = "auth-profile-api-key",
        configFile = SecretTargetConfigFile.AUTH_PROFILES_JSON,
        pathPattern = "profiles.*.credentials.apiKey",
        secretShape = SecretTargetShape.SECRET_INPUT,
        expectedResolvedValue = SecretExpectedResolvedValue.STRING,
        includeInPlan = true,
        includeInConfigure = true,
        includeInAudit = true,
        authProfileType = AuthProfileType.API_KEY
    ),
    // Auth profiles: token
    SecretTargetRegistryEntry(
        id = "auth-profiles.profiles.*.credentials.token",
        targetType = "auth-profile-token",
        configFile = SecretTargetConfigFile.AUTH_PROFILES_JSON,
        pathPattern = "profiles.*.credentials.token",
        secretShape = SecretTargetShape.SECRET_INPUT,
        expectedResolvedValue = SecretExpectedResolvedValue.STRING_OR_OBJECT,
        includeInPlan = true,
        includeInConfigure = true,
        includeInAudit = true,
        authProfileType = AuthProfileType.TOKEN
    )
)

/**
 * Check if a target type string is known.
 * Aligned with TS isKnownSecretTargetType.
 */
fun isKnownSecretTargetType(type: String?): Boolean {
    if (type == null) return false
    return CORE_SECRET_TARGET_REGISTRY.any {
        it.targetType == type || type in it.targetTypeAliases
    }
}

/**
 * List all secret target registry entries.
 * Aligned with TS listSecretTargetRegistryEntries.
 */
fun listSecretTargetRegistryEntries(): List<SecretTargetRegistryEntry> =
    CORE_SECRET_TARGET_REGISTRY.toList()

// ---------- Unsupported surface patterns ----------

/**
 * Core unsupported secret ref surface patterns.
 * Aligned with TS CORE_UNSUPPORTED_SECRETREF_SURFACE_PATTERNS.
 */
private val CORE_UNSUPPORTED_SECRETREF_SURFACE_PATTERNS = listOf(
    "commands.ownerDisplaySecret",
    "hooks.token",
    "hooks.gmail.pushToken",
    "hooks.mappings[].sessionKey",
    "auth-profiles.oauth.*"
)

/**
 * Get the list of unsupported secret ref surface patterns.
 * Aligned with TS getUnsupportedSecretRefSurfacePatterns.
 */
fun getUnsupportedSecretRefSurfacePatterns(): List<String> =
    CORE_UNSUPPORTED_SECRETREF_SURFACE_PATTERNS.toList()
