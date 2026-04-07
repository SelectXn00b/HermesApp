package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/provider-env-vars.ts
 *
 * Mapping from provider IDs to their known environment variable names.
 * On Android, these are used by the secret resolution layer to probe
 * for API keys available in the runtime environment.
 */

/**
 * Core provider auth env var candidates.
 * Aligned with TS CORE_PROVIDER_AUTH_ENV_VAR_CANDIDATES.
 */
private val CORE_PROVIDER_AUTH_ENV_VAR_CANDIDATES: Map<String, List<String>> = mapOf(
    "voyage" to listOf("VOYAGE_API_KEY"),
    "cerebras" to listOf("CEREBRAS_API_KEY"),
    "anthropic-openai" to listOf("ANTHROPIC_API_KEY"),
    "qwen-dashscope" to listOf("DASHSCOPE_API_KEY")
)

/**
 * Provider env vars used for setup/default secret refs and broad secret scrubbing.
 * Aligned with TS CORE_PROVIDER_SETUP_ENV_VAR_OVERRIDES.
 */
private val CORE_PROVIDER_SETUP_ENV_VAR_OVERRIDES: Map<String, List<String>> = mapOf(
    "minimax-cn" to listOf("MINIMAX_API_KEY")
)

/**
 * Extra known provider auth env vars not tied to a specific provider registry entry.
 * Aligned with TS EXTRA_PROVIDER_AUTH_ENV_VARS.
 */
private val EXTRA_PROVIDER_AUTH_ENV_VARS = listOf(
    "MINIMAX_CODE_PLAN_KEY",
    "MINIMAX_CODING_API_KEY"
)

/**
 * Resolve provider auth env var candidates.
 * Aligned with TS resolveProviderAuthEnvVarCandidates.
 */
fun resolveProviderAuthEnvVarCandidates(): Map<String, List<String>> {
    // On Android, we skip manifest registry loading and return core candidates only.
    return CORE_PROVIDER_AUTH_ENV_VAR_CANDIDATES.toMap()
}

/**
 * Resolve all known provider env vars (auth + setup overrides).
 * Aligned with TS resolveProviderEnvVars.
 */
fun resolveProviderEnvVars(): Map<String, List<String>> {
    return resolveProviderAuthEnvVarCandidates() + CORE_PROVIDER_SETUP_ENV_VAR_OVERRIDES
}

/**
 * Provider auth env candidates used by generic auth resolution.
 * Aligned with TS PROVIDER_AUTH_ENV_VAR_CANDIDATES.
 */
val PROVIDER_AUTH_ENV_VAR_CANDIDATES: Map<String, List<String>> by lazy {
    resolveProviderAuthEnvVarCandidates()
}

/**
 * Full provider env vars map.
 * Aligned with TS PROVIDER_ENV_VARS.
 */
val PROVIDER_ENV_VARS: Map<String, List<String>> by lazy {
    resolveProviderEnvVars()
}

/**
 * Get the known env var names for a specific provider.
 * Aligned with TS getProviderEnvVars.
 */
fun getProviderEnvVars(providerId: String): List<String> {
    return resolveProviderEnvVars()[providerId] ?: emptyList()
}

/**
 * List all known provider auth env var names (flattened).
 * Aligned with TS listKnownProviderAuthEnvVarNames.
 */
fun listKnownProviderAuthEnvVarNames(): List<String> {
    val set = mutableSetOf<String>()
    resolveProviderAuthEnvVarCandidates().values.forEach { set.addAll(it) }
    resolveProviderEnvVars().values.forEach { set.addAll(it) }
    set.addAll(EXTRA_PROVIDER_AUTH_ENV_VARS)
    return set.toList()
}

/**
 * List all known secret env var names (flattened).
 * Aligned with TS listKnownSecretEnvVarNames.
 */
fun listKnownSecretEnvVarNames(): List<String> {
    val set = mutableSetOf<String>()
    resolveProviderEnvVars().values.forEach { set.addAll(it) }
    return set.toList()
}

/**
 * Omit env keys from a map, case-insensitive.
 * Aligned with TS omitEnvKeysCaseInsensitive.
 */
fun omitEnvKeysCaseInsensitive(
    baseEnv: Map<String, String>,
    keys: Iterable<String>
): Map<String, String> {
    val denied = keys.mapNotNull { it.trim().takeIf { k -> k.isNotEmpty() }?.uppercase() }.toSet()
    if (denied.isEmpty()) return baseEnv.toMap()
    return baseEnv.filterKeys { it.uppercase() !in denied }
}
