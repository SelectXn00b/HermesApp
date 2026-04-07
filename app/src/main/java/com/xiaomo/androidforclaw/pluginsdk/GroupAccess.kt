package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/group-access.ts
 *
 * Group access policy evaluation for channel plugins.
 * Android adaptation: standalone functions, no import from config/runtime-group-policy.
 */

// ---------- Group Policy ----------

/**
 * Group policy type.
 * Aligned with TS GroupPolicy from config/types.base.ts.
 */
typealias GroupPolicy = String // "open" | "disabled" | "allowlist"

const val GROUP_POLICY_OPEN = "open"
const val GROUP_POLICY_DISABLED = "disabled"
const val GROUP_POLICY_ALLOWLIST = "allowlist"

// ---------- Sender Group Access ----------

/**
 * Sender group access reason.
 * Aligned with TS SenderGroupAccessReason.
 */
enum class SenderGroupAccessReason(val value: String) {
    ALLOWED("allowed"),
    DISABLED("disabled"),
    EMPTY_ALLOWLIST("empty_allowlist"),
    SENDER_NOT_ALLOWLISTED("sender_not_allowlisted"),
}

/**
 * Sender group access decision.
 * Aligned with TS SenderGroupAccessDecision.
 */
data class SenderGroupAccessDecision(
    val allowed: Boolean,
    val groupPolicy: GroupPolicy,
    val providerMissingFallbackApplied: Boolean,
    val reason: SenderGroupAccessReason,
)

// ---------- Group Route Access ----------

/**
 * Group route access reason.
 * Aligned with TS GroupRouteAccessReason.
 */
enum class GroupRouteAccessReason(val value: String) {
    ALLOWED("allowed"),
    DISABLED("disabled"),
    EMPTY_ALLOWLIST("empty_allowlist"),
    ROUTE_NOT_ALLOWLISTED("route_not_allowlisted"),
    ROUTE_DISABLED("route_disabled"),
}

/**
 * Group route access decision.
 * Aligned with TS GroupRouteAccessDecision.
 */
data class GroupRouteAccessDecision(
    val allowed: Boolean,
    val groupPolicy: GroupPolicy,
    val reason: GroupRouteAccessReason,
)

// ---------- Matched Group Access ----------

/**
 * Matched group access reason.
 * Aligned with TS MatchedGroupAccessReason.
 */
enum class MatchedGroupAccessReason(val value: String) {
    ALLOWED("allowed"),
    DISABLED("disabled"),
    MISSING_MATCH_INPUT("missing_match_input"),
    EMPTY_ALLOWLIST("empty_allowlist"),
    NOT_ALLOWLISTED("not_allowlisted"),
}

/**
 * Matched group access decision.
 * Aligned with TS MatchedGroupAccessDecision.
 */
data class MatchedGroupAccessDecision(
    val allowed: Boolean,
    val groupPolicy: GroupPolicy,
    val reason: MatchedGroupAccessReason,
)

// ---------- Policy Resolution ----------

/**
 * Downgrade sender-scoped group policy to open mode when no allowlist is configured.
 * Aligned with TS resolveSenderScopedGroupPolicy.
 */
fun resolveSenderScopedGroupPolicy(
    groupPolicy: GroupPolicy,
    groupAllowFrom: List<String>,
): GroupPolicy {
    if (groupPolicy == GROUP_POLICY_DISABLED) return GROUP_POLICY_DISABLED
    return if (groupAllowFrom.isNotEmpty()) GROUP_POLICY_ALLOWLIST else GROUP_POLICY_OPEN
}

// ---------- Route Access Evaluation ----------

/**
 * Evaluate route-level group access after policy, route match, and enablement checks.
 * Aligned with TS evaluateGroupRouteAccessForPolicy.
 */
fun evaluateGroupRouteAccessForPolicy(
    groupPolicy: GroupPolicy,
    routeAllowlistConfigured: Boolean,
    routeMatched: Boolean,
    routeEnabled: Boolean? = null,
): GroupRouteAccessDecision {
    if (groupPolicy == GROUP_POLICY_DISABLED) {
        return GroupRouteAccessDecision(
            allowed = false,
            groupPolicy = groupPolicy,
            reason = GroupRouteAccessReason.DISABLED,
        )
    }
    if (routeMatched && routeEnabled == false) {
        return GroupRouteAccessDecision(
            allowed = false,
            groupPolicy = groupPolicy,
            reason = GroupRouteAccessReason.ROUTE_DISABLED,
        )
    }
    if (groupPolicy == GROUP_POLICY_ALLOWLIST) {
        if (!routeAllowlistConfigured) {
            return GroupRouteAccessDecision(
                allowed = false,
                groupPolicy = groupPolicy,
                reason = GroupRouteAccessReason.EMPTY_ALLOWLIST,
            )
        }
        if (!routeMatched) {
            return GroupRouteAccessDecision(
                allowed = false,
                groupPolicy = groupPolicy,
                reason = GroupRouteAccessReason.ROUTE_NOT_ALLOWLISTED,
            )
        }
    }
    return GroupRouteAccessDecision(
        allowed = true,
        groupPolicy = groupPolicy,
        reason = GroupRouteAccessReason.ALLOWED,
    )
}

// ---------- Matched Group Access Evaluation ----------

/**
 * Evaluate generic allowlist match state for channels that compare derived group identifiers.
 * Aligned with TS evaluateMatchedGroupAccessForPolicy.
 */
fun evaluateMatchedGroupAccessForPolicy(
    groupPolicy: GroupPolicy,
    allowlistConfigured: Boolean,
    allowlistMatched: Boolean,
    requireMatchInput: Boolean = false,
    hasMatchInput: Boolean = true,
): MatchedGroupAccessDecision {
    if (groupPolicy == GROUP_POLICY_DISABLED) {
        return MatchedGroupAccessDecision(
            allowed = false,
            groupPolicy = groupPolicy,
            reason = MatchedGroupAccessReason.DISABLED,
        )
    }
    if (groupPolicy == GROUP_POLICY_ALLOWLIST) {
        if (requireMatchInput && !hasMatchInput) {
            return MatchedGroupAccessDecision(
                allowed = false,
                groupPolicy = groupPolicy,
                reason = MatchedGroupAccessReason.MISSING_MATCH_INPUT,
            )
        }
        if (!allowlistConfigured) {
            return MatchedGroupAccessDecision(
                allowed = false,
                groupPolicy = groupPolicy,
                reason = MatchedGroupAccessReason.EMPTY_ALLOWLIST,
            )
        }
        if (!allowlistMatched) {
            return MatchedGroupAccessDecision(
                allowed = false,
                groupPolicy = groupPolicy,
                reason = MatchedGroupAccessReason.NOT_ALLOWLISTED,
            )
        }
    }
    return MatchedGroupAccessDecision(
        allowed = true,
        groupPolicy = groupPolicy,
        reason = MatchedGroupAccessReason.ALLOWED,
    )
}

// ---------- Sender Group Access Evaluation ----------

/**
 * Evaluate sender access for an already-resolved group policy and allowlist.
 * Aligned with TS evaluateSenderGroupAccessForPolicy.
 */
fun evaluateSenderGroupAccessForPolicy(
    groupPolicy: GroupPolicy,
    providerMissingFallbackApplied: Boolean = false,
    groupAllowFrom: List<String>,
    senderId: String,
    isSenderAllowed: (senderId: String, allowFrom: List<String>) -> Boolean,
): SenderGroupAccessDecision {
    if (groupPolicy == GROUP_POLICY_DISABLED) {
        return SenderGroupAccessDecision(
            allowed = false,
            groupPolicy = groupPolicy,
            providerMissingFallbackApplied = providerMissingFallbackApplied,
            reason = SenderGroupAccessReason.DISABLED,
        )
    }
    if (groupPolicy == GROUP_POLICY_ALLOWLIST) {
        if (groupAllowFrom.isEmpty()) {
            return SenderGroupAccessDecision(
                allowed = false,
                groupPolicy = groupPolicy,
                providerMissingFallbackApplied = providerMissingFallbackApplied,
                reason = SenderGroupAccessReason.EMPTY_ALLOWLIST,
            )
        }
        if (!isSenderAllowed(senderId, groupAllowFrom)) {
            return SenderGroupAccessDecision(
                allowed = false,
                groupPolicy = groupPolicy,
                providerMissingFallbackApplied = providerMissingFallbackApplied,
                reason = SenderGroupAccessReason.SENDER_NOT_ALLOWLISTED,
            )
        }
    }
    return SenderGroupAccessDecision(
        allowed = true,
        groupPolicy = groupPolicy,
        providerMissingFallbackApplied = providerMissingFallbackApplied,
        reason = SenderGroupAccessReason.ALLOWED,
    )
}

/**
 * Resolve provider fallback policy, then evaluate sender access against that result.
 * Aligned with TS evaluateSenderGroupAccess.
 */
fun evaluateSenderGroupAccess(
    providerConfigPresent: Boolean,
    configuredGroupPolicy: GroupPolicy? = null,
    defaultGroupPolicy: GroupPolicy? = null,
    groupAllowFrom: List<String>,
    senderId: String,
    isSenderAllowed: (senderId: String, allowFrom: List<String>) -> Boolean,
): SenderGroupAccessDecision {
    val (resolvedPolicy, fallbackApplied) = resolveOpenProviderRuntimeGroupPolicy(
        providerConfigPresent = providerConfigPresent,
        groupPolicy = configuredGroupPolicy,
        defaultGroupPolicy = defaultGroupPolicy,
    )
    return evaluateSenderGroupAccessForPolicy(
        groupPolicy = resolvedPolicy,
        providerMissingFallbackApplied = fallbackApplied,
        groupAllowFrom = groupAllowFrom,
        senderId = senderId,
        isSenderAllowed = isSenderAllowed,
    )
}

// ---------- Provider Runtime Group Policy ----------

/**
 * Resolve open provider runtime group policy with missing-provider fallback.
 * Aligned with TS resolveOpenProviderRuntimeGroupPolicy.
 */
fun resolveOpenProviderRuntimeGroupPolicy(
    providerConfigPresent: Boolean,
    groupPolicy: GroupPolicy? = null,
    defaultGroupPolicy: GroupPolicy? = null,
): Pair<GroupPolicy, Boolean> {
    if (groupPolicy != null) {
        return groupPolicy to false
    }
    if (!providerConfigPresent && defaultGroupPolicy != null) {
        return defaultGroupPolicy to true
    }
    return GROUP_POLICY_OPEN to false
}
