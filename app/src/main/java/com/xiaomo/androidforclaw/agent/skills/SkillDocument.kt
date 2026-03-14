package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: loaded skill document model.
 */


/**
 * Skill Document Data Model
 * Corresponds to AgentSkills.io format
 *
 * File format:
 * ---
 * name: skill-name
 * description: Skill description
 * metadata:
 *   {
 *     "openclaw": {
 *       "always": true,
 *       "emoji": "📱",
 *       "skillKey": "custom-key",
 *       "primaryEnv": "API_KEY",
 *       "homepage": "https://...",
 *       "os": ["darwin", "linux", "android"],
 *       "requires": {
 *         "bins": ["binary"],
 *         "anyBins": ["alt1", "alt2"],
 *         "env": ["ENV_VAR"],
 *         "config": ["config.key"]
 *       },
 *       "install": [...]
 *     }
 *   }
 * ---
 * # Skill Content
 * ...
 */
data class SkillDocument(
    /**
     * Skill name (unique identifier)
     * e.g.: "mobile-operations", "app-testing"
     */
    val name: String,

    /**
     * Skill description (1-2 sentences)
     * e.g.: "Core mobile device operation skills"
     */
    val description: String,

    /**
     * Skill metadata (parsed from metadata.openclaw)
     */
    val metadata: SkillMetadata,

    /**
     * Skill body content (Markdown format)
     * This part will be injected into system prompt
     */
    val content: String,

    /**
     * Skill file path (for status reporting / debugging)
     */
    val filePath: String = "",

    /**
     * Skill source
     * "bundled" - Built-in at assets/skills/
     * "managed" - From /sdcard/.androidforclaw/skills/ (aligns with ~/.openclaw/skills/)
     * "workspace" - From /sdcard/.androidforclaw/workspace/skills/ (aligns with ~/.openclaw/workspace/)
     * "extra" - From extraDirs configuration
     */
    val source: SkillSource = SkillSource.BUNDLED
) {
    /**
     * Get formatted content (with title)
     */
    fun getFormattedContent(): String {
        val emoji = metadata.emoji ?: ""
        val title = if (emoji.isNotEmpty()) "$emoji $name" else name
        return """
# $title

$content
        """.trim()
    }

    /**
     * Estimate token count (rough estimate: 1 token ≈ 4 characters)
     */
    fun estimateTokens(): Int {
        return (content.length / 4.0).toInt()
    }

    /**
     * Get the effective skill key (skillKey from metadata, fallback to name)
     * Aligns with OpenClaw: entries.<skillKey> maps to skill
     */
    fun effectiveSkillKey(): String {
        return metadata.skillKey ?: name
    }
}

/**
 * Skill Metadata — unified model covering all metadata.openclaw fields.
 * Aligns with OpenClaw's OpenClawSkillMetadata.
 */
data class SkillMetadata(
    /**
     * Whether to always load (load at startup)
     * true: Load into all system prompts
     * false: Load on demand
     */
    val always: Boolean = false,

    /**
     * Custom skill key for config entries lookup
     * e.g.: entries.<skillKey>.enabled
     */
    val skillKey: String? = null,

    /**
     * Primary environment variable name
     * Used with apiKey convenience in config: entries.<key>.apiKey
     */
    val primaryEnv: String? = null,

    /**
     * Skill's emoji icon
     * e.g.: "📱", "🧪", "🐛"
     */
    val emoji: String? = null,

    /**
     * Homepage URL
     */
    val homepage: String? = null,

    /**
     * Supported OS platforms
     * e.g.: ["darwin", "linux", "win32", "android"]
     * null = no platform restriction
     */
    val os: List<String>? = null,

    /**
     * Skill dependency requirements
     */
    val requires: SkillRequires? = null,

    /**
     * Install specifications
     */
    val install: List<SkillInstallSpec>? = null
)

/**
 * Skill Source Enum
 * Aligns with OpenClaw's multi-tier architecture
 */
enum class SkillSource(val displayName: String) {
    BUNDLED("bundled"),      // assets/skills/
    MANAGED("managed"),      // /sdcard/.androidforclaw/skills/ (aligns with ~/.openclaw/skills/)
    WORKSPACE("workspace"),  // /sdcard/.androidforclaw/workspace/skills/ (aligns with ~/.openclaw/workspace/)
    EXTRA("extra"),          // extraDirs configuration (lowest priority)
    PLUGIN("plugin")         // Plugin-provided skills (aligns with openclaw.plugin.json skills dirs)
}

/**
 * Skill Dependency Requirements
 * Aligns with OpenClaw's requires field
 */
data class SkillRequires(
    /**
     * Required binary tools (all must exist)
     * e.g.: ["adb", "ffmpeg"]
     */
    val bins: List<String> = emptyList(),

    /**
     * At least one of these binaries must exist
     * e.g.: ["npm", "pnpm", "yarn"]
     */
    val anyBins: List<String> = emptyList(),

    /**
     * Required environment variables
     * e.g.: ["ANDROID_HOME", "PATH"]
     */
    val env: List<String> = emptyList(),

    /**
     * Required config paths (openclaw.json path checks)
     * e.g.: ["channels.feishu.appId"]
     */
    val config: List<String> = emptyList()
) {
    /**
     * Check if there are any dependencies
     */
    fun hasRequirements(): Boolean {
        return bins.isNotEmpty() || anyBins.isNotEmpty() || env.isNotEmpty() || config.isNotEmpty()
    }
}
