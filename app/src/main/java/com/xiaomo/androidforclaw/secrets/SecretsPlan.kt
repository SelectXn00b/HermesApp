package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/plan.ts
 * - src/secrets/configure-plan.ts
 *
 * Secrets apply plan: defines the structure for configuring secret references.
 */

/**
 * A single target in a secrets apply plan.
 * Aligned with TS SecretsPlanTarget.
 */
data class SecretsPlanTarget(
    val type: String,
    val path: String,
    val pathSegments: List<String>? = null,
    val ref: SecretRef,
    val agentId: String? = null,
    val providerId: String? = null,
    val accountId: String? = null,
    val authProfileProvider: String? = null
)

/**
 * A complete secrets apply plan.
 * Aligned with TS SecretsApplyPlan.
 */
data class SecretsApplyPlan(
    val version: Int = 1,
    val protocolVersion: Int = 1,
    val generatedAt: String,
    val generatedBy: String = "openclaw secrets configure",
    val providerUpserts: Map<String, SecretProviderConfig>? = null,
    val providerDeletes: List<String>? = null,
    val targets: List<SecretsPlanTarget>,
    val options: SecretsApplyPlanOptions = SecretsApplyPlanOptions()
)

/**
 * Options controlling how the plan is applied.
 * Aligned with TS SecretsApplyPlan.options.
 */
data class SecretsApplyPlanOptions(
    val scrubEnv: Boolean = true,
    val scrubAuthProfilesForProviderTargets: Boolean = true,
    val scrubLegacyAuthJson: Boolean = true
)

/**
 * Normalize plan options with defaults.
 * Aligned with TS normalizeSecretsPlanOptions.
 */
fun normalizeSecretsPlanOptions(options: SecretsApplyPlanOptions?): SecretsApplyPlanOptions {
    return SecretsApplyPlanOptions(
        scrubEnv = options?.scrubEnv ?: true,
        scrubAuthProfilesForProviderTargets = options?.scrubAuthProfilesForProviderTargets ?: true,
        scrubLegacyAuthJson = options?.scrubLegacyAuthJson ?: true
    )
}

private val FORBIDDEN_PATH_SEGMENTS = setOf("__proto__", "prototype", "constructor")

/**
 * Validate a plan target candidate.
 * Aligned with TS resolveValidatedPlanTarget.
 */
fun resolveValidatedPlanTarget(
    type: String?,
    path: String?,
    pathSegments: List<String>? = null,
    providerId: String? = null,
    accountId: String? = null
): ResolvedPlanTarget? {
    if (!isKnownSecretTargetType(type)) return null
    val trimmedPath = path?.trim() ?: return null
    if (trimmedPath.isEmpty()) return null

    val segments = if (!pathSegments.isNullOrEmpty()) {
        pathSegments.map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        parseDotPath(trimmedPath)
    }
    if (segments.isEmpty()) return null
    if (segments.any { it in FORBIDDEN_PATH_SEGMENTS }) return null
    if (trimmedPath != toDotPath(segments)) return null

    // Find matching registry entry
    val entry = CORE_SECRET_TARGET_REGISTRY.find {
        it.targetType == type || type in it.targetTypeAliases
    } ?: return null

    return ResolvedPlanTarget(
        entry = entry,
        pathSegments = segments,
        providerId = providerId,
        accountId = accountId
    )
}

/**
 * Validate that a value is a well-formed secrets apply plan.
 * Aligned with TS isSecretsApplyPlan.
 */
fun isSecretsApplyPlan(plan: SecretsApplyPlan): Boolean {
    if (plan.version != 1 || plan.protocolVersion != 1) return false
    for (target in plan.targets) {
        val resolved = resolveValidatedPlanTarget(
            type = target.type,
            path = target.path,
            pathSegments = target.pathSegments
        ) ?: return false

        if (target.ref.id.trim().isEmpty()) return false
        if (target.ref.source == SecretRefSource.EXEC && !isValidExecSecretRefId(target.ref.id)) {
            return false
        }
    }
    return true
}

// ---------- Configure plan types ----------

/**
 * Configure candidate: a config path discovered as holding or needing a secret.
 * Aligned with TS ConfigureCandidate.
 */
data class ConfigureCandidate(
    val type: String,
    val path: String,
    val pathSegments: List<String>,
    val label: String,
    val configFile: SecretTargetConfigFile,
    val expectedResolvedValue: SecretExpectedResolvedValue,
    val existingRef: SecretRef? = null,
    val isDerived: Boolean = false,
    val agentId: String? = null,
    val providerId: String? = null,
    val accountId: String? = null,
    val authProfileProvider: String? = null
)

/**
 * A selected configure target (candidate + assigned ref).
 * Aligned with TS ConfigureSelectedTarget.
 */
data class ConfigureSelectedTarget(
    val candidate: ConfigureCandidate,
    val ref: SecretRef
)

/**
 * Provider changes computed by the configure flow.
 * Aligned with TS ConfigureProviderChanges.
 */
data class ConfigureProviderChanges(
    val upserts: Map<String, SecretProviderConfig> = emptyMap(),
    val deletes: List<String> = emptyList()
)

/**
 * Check if a configure plan has any changes.
 * Aligned with TS hasConfigurePlanChanges.
 */
fun hasConfigurePlanChanges(
    selectedTargets: Map<String, ConfigureSelectedTarget>,
    providerChanges: ConfigureProviderChanges
): Boolean =
    selectedTargets.isNotEmpty() ||
            providerChanges.upserts.isNotEmpty() ||
            providerChanges.deletes.isNotEmpty()

/**
 * Build a secrets apply plan from configure selections.
 * Aligned with TS buildSecretsConfigurePlan.
 */
fun buildSecretsConfigurePlan(
    selectedTargets: Map<String, ConfigureSelectedTarget>,
    providerChanges: ConfigureProviderChanges,
    generatedAt: String? = null
): SecretsApplyPlan {
    return SecretsApplyPlan(
        generatedAt = generatedAt ?: java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            java.util.Locale.US
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date()),
        targets = selectedTargets.values.map { entry ->
            SecretsPlanTarget(
                type = entry.candidate.type,
                path = entry.candidate.path,
                pathSegments = entry.candidate.pathSegments,
                ref = entry.ref,
                agentId = entry.candidate.agentId,
                providerId = entry.candidate.providerId,
                accountId = entry.candidate.accountId,
                authProfileProvider = entry.candidate.authProfileProvider
            )
        },
        providerUpserts = providerChanges.upserts.takeIf { it.isNotEmpty() },
        providerDeletes = providerChanges.deletes.takeIf { it.isNotEmpty() },
        options = SecretsApplyPlanOptions(
            scrubEnv = true,
            scrubAuthProfilesForProviderTargets = true,
            scrubLegacyAuthJson = true
        )
    )
}
