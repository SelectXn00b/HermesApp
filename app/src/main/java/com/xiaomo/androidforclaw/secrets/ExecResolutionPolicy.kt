package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/exec-resolution-policy.ts
 *
 * Policy for selecting which secret refs to resolve (skipping exec refs if not allowed).
 */

/**
 * Result of applying exec resolution policy to a list of refs.
 * Aligned with TS selectRefsForExecPolicy return type.
 */
data class ExecPolicyResult(
    val refsToResolve: List<SecretRef>,
    val skippedExecRefs: List<SecretRef>
)

/**
 * Filter refs based on exec resolution policy.
 * Aligned with TS selectRefsForExecPolicy.
 */
fun selectRefsForExecPolicy(refs: List<SecretRef>, allowExec: Boolean): ExecPolicyResult {
    val refsToResolve = mutableListOf<SecretRef>()
    val skippedExecRefs = mutableListOf<SecretRef>()
    for (ref in refs) {
        if (ref.source == SecretRefSource.EXEC && !allowExec) {
            skippedExecRefs.add(ref)
        } else {
            refsToResolve.add(ref)
        }
    }
    return ExecPolicyResult(refsToResolve, skippedExecRefs)
}

/**
 * Get a static error for a skipped exec ref, if any validation issue exists.
 * Aligned with TS getSkippedExecRefStaticError.
 */
fun getSkippedExecRefStaticError(
    ref: SecretRef,
    secretProviders: Map<String, SecretProviderConfigEntry>?
): String? {
    val id = ref.id.trim()
    val refLabel = "${ref.source.value}:${ref.provider}:$id"
    if (id.isEmpty()) {
        return "Error: Secret reference id is empty."
    }
    if (!isValidExecSecretRefId(id)) {
        return "Error: ${formatExecSecretRefIdValidationMessage()} (ref: $refLabel)."
    }
    val providerConfig = secretProviders?.get(ref.provider)
    if (providerConfig == null) {
        return "Error: Secret provider \"${ref.provider}\" is not configured (ref: $refLabel)."
    }
    if (providerConfig.source != ref.source.value) {
        return "Error: Secret provider \"${ref.provider}\" has source \"${providerConfig.source}\" " +
                "but ref requests \"${ref.source.value}\"."
    }
    return null
}
