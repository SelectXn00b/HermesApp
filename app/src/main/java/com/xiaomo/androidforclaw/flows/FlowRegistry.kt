package com.xiaomo.androidforclaw.flows

/**
 * OpenClaw module: flows
 * Source: OpenClaw/src/flows/
 *
 * Interactive setup flows and wizard orchestration for channel configuration,
 * provider selection, model picking, health checks, and search setup.
 *
 * The registry holds [FlowContribution] instances that are registered by
 * channels, providers, and plugins at startup, then queried by surface
 * when a wizard needs to present options to the user.
 */

object FlowRegistry {

    private val contributions = mutableListOf<FlowContribution>()

    // -----------------------------------------------------------------------
    // Mutation
    // -----------------------------------------------------------------------

    /**
     * Register a new flow contribution.  If a contribution with the same [FlowContribution.id]
     * already exists, it is replaced.
     */
    fun registerFlowContribution(contribution: FlowContribution) {
        // Remove existing with same id to avoid duplicates
        contributions.removeAll { it.id == contribution.id }
        contributions.add(contribution)
    }

    /**
     * Register multiple flow contributions at once.
     */
    fun registerFlowContributions(items: List<FlowContribution>) {
        items.forEach { registerFlowContribution(it) }
    }

    // -----------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------

    /**
     * List all contributions, optionally filtered by [surface].
     */
    fun listFlowContributions(
        surface: FlowContributionSurface? = null
    ): List<FlowContribution> =
        if (surface != null) contributions.filter { it.surface == surface }
        else contributions.toList()

    /**
     * Find a single contribution by its [id].  Returns null if not found.
     */
    fun findContribution(id: String): FlowContribution? =
        contributions.firstOrNull { it.id == id }

    // -----------------------------------------------------------------------
    // Removal
    // -----------------------------------------------------------------------

    /**
     * Remove a contribution by its [id].  Returns `true` if an entry was removed.
     */
    fun removeContribution(id: String): Boolean =
        contributions.removeAll { it.id == id }

    /**
     * Remove all contributions for a given [surface].
     */
    fun clearForSurface(surface: FlowContributionSurface) {
        contributions.removeAll { it.surface == surface }
    }

    /**
     * Remove all contributions.
     */
    fun clear() {
        contributions.clear()
    }
}
