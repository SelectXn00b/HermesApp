package ai.openclaw.app.skill

import kotlinx.serialization.Serializable

/**
 * Represents a skill from the agency-agents repository.
 */
@Serializable
data class OnlineSkill(
    val name: String,
    val emoji: String,
    val specialty: String,
    val category: String,
    val filename: String,
)
