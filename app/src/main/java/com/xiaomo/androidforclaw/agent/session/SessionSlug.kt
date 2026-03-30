package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/session-slug.ts
 *
 * AndroidForClaw adaptation: human-readable session identifiers.
 */

import kotlin.random.Random

/**
 * Human-readable session slug generator.
 * Aligned with OpenClaw session-slug.ts.
 */
object SessionSlug {

    /**
     * Adjectives for slug generation (42 words).
     * Aligned with OpenClaw SLUG_ADJECTIVES.
     */
    private val SLUG_ADJECTIVES = listOf(
        "swift", "bright", "calm", "dark", "eager", "fair", "gentle", "happy",
        "keen", "lively", "merry", "noble", "proud", "quiet", "rapid", "sharp",
        "tender", "vivid", "warm", "young", "ancient", "bold", "clever", "daring",
        "elegant", "fierce", "grand", "hidden", "ivory", "jade", "kind", "lunar",
        "mystic", "nimble", "ornate", "pale", "regal", "serene", "true", "vast",
        "wild", "zealous"
    )

    /**
     * Nouns for slug generation (54 words).
     * Aligned with OpenClaw SLUG_NOUNS.
     */
    private val SLUG_NOUNS = listOf(
        "harbor", "meadow", "river", "summit", "valley", "forest", "island", "bridge",
        "castle", "garden", "lantern", "mirror", "ocean", "palace", "quartz", "ridge",
        "shadow", "temple", "breeze", "canyon", "delta", "ember", "falcon", "glacier",
        "horizon", "jade", "kindle", "lotus", "marble", "nebula", "opal", "prism",
        "quill", "reef", "sage", "thistle", "umber", "violet", "willow", "zenith",
        "aurora", "basalt", "cedar", "dusk", "echo", "fern", "grove", "helm",
        "iris", "jewel", "knoll", "lark", "moss", "nova"
    )

    private val random = Random.Default

    /**
     * Create a human-readable session slug.
     * Tries 2-word → 3-word → random suffix → timestamp fallback.
     *
     * Aligned with OpenClaw createSessionSlug.
     */
    fun createSessionSlug(isTaken: ((String) -> Boolean)? = null): String {
        // Try 2-word slugs first
        repeat(10) {
            val slug = "${randomAdj()}-${randomNoun()}"
            if (isTaken == null || !isTaken(slug)) return slug
        }

        // Try 3-word slugs
        repeat(10) {
            val slug = "${randomAdj()}-${randomAdj()}-${randomNoun()}"
            if (isTaken == null || !isTaken(slug)) return slug
        }

        // Random suffix fallback
        repeat(5) {
            val suffix = random.nextInt(1000, 9999)
            val slug = "${randomAdj()}-${randomNoun()}-$suffix"
            if (isTaken == null || !isTaken(slug)) return slug
        }

        // Timestamp fallback
        return "${randomAdj()}-${randomNoun()}-${System.currentTimeMillis()}"
    }

    private fun randomAdj(): String = SLUG_ADJECTIVES[random.nextInt(SLUG_ADJECTIVES.size)]
    private fun randomNoun(): String = SLUG_NOUNS[random.nextInt(SLUG_NOUNS.size)]
}
