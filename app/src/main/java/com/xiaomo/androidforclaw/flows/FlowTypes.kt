package com.xiaomo.androidforclaw.flows

/**
 * OpenClaw module: flows
 * Source: OpenClaw/src/flows/types.ts
 *
 * Type definitions for interactive setup flows: contributions, surfaces,
 * option groups, and merge/sort utilities.
 */

// ---------------------------------------------------------------------------
// Supporting types
// ---------------------------------------------------------------------------

data class FlowDocsLink(val path: String, val label: String? = null)

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class FlowContributionKind { CHANNEL, CORE, PROVIDER, SEARCH }

enum class FlowContributionSurface {
    AUTH_CHOICE,
    HEALTH,
    MODEL_PICKER,
    SETUP,
    SEARCH_SETUP
}

// ---------------------------------------------------------------------------
// Option definitions
// ---------------------------------------------------------------------------

data class FlowOptionGroup(val id: String, val label: String, val hint: String? = null)

data class FlowOption(
    val value: String,
    val label: String,
    val hint: String? = null,
    val group: FlowOptionGroup? = null,
    val docs: FlowDocsLink? = null
)

// ---------------------------------------------------------------------------
// Contribution
// ---------------------------------------------------------------------------

data class FlowContribution(
    val id: String,
    val kind: FlowContributionKind,
    val surface: FlowContributionSurface,
    val option: FlowOption,
    val source: String? = null
)

// ---------------------------------------------------------------------------
// Merge & sort utilities
// ---------------------------------------------------------------------------

/**
 * Merge [primary] contributions with [fallbacks].  Fallback entries whose id
 * already appears in the primary list are dropped.
 */
fun mergeFlowContributions(
    primary: List<FlowContribution>,
    fallbacks: List<FlowContribution>? = null
): List<FlowContribution> {
    val primaryIds = primary.map { it.id }.toSet()
    val merged = primary.toMutableList()
    fallbacks?.filter { it.id !in primaryIds }?.let { merged.addAll(it) }
    return merged
}

/**
 * Sort contributions alphabetically by their option label.
 */
fun sortFlowContributionsByLabel(contributions: List<FlowContribution>): List<FlowContribution> =
    contributions.sortedBy { it.option.label.lowercase() }
