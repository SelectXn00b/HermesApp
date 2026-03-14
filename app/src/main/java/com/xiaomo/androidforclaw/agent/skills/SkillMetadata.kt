package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: in-app skill metadata model.
 */


/**
 * Skill Install Specification (aligns with OpenClaw SkillInstallSpec)
 */
data class SkillInstallSpec(
    val id: String? = null,
    val kind: InstallKind,
    val label: String? = null,
    val bins: List<String>? = null,
    val os: List<String>? = null,

    // brew install
    val formula: String? = null,

    // npm/yarn/pnpm/bun install
    val `package`: String? = null,

    // go install
    val module: String? = null,

    // download install
    val url: String? = null,
    val archive: String? = null,               // tar.gz, tar.bz2, zip
    val extract: Boolean? = null,
    val stripComponents: Int? = null,
    val targetDir: String? = null
)

/**
 * Installer Type
 */
enum class InstallKind {
    BREW,       // Homebrew (macOS/Linux)
    NODE,       // npm/yarn/pnpm/bun
    GO,         // go install
    UV,         // uv (Python)
    DOWNLOAD,   // Direct download
    APK         // Android APK (Android-specific)
}

/**
 * Skill Status Report (aligns with OpenClaw SkillStatusReport)
 */
data class SkillStatusReport(
    val workspaceDir: String,
    val managedSkillsDir: String,
    val skills: List<SkillStatusEntry>
)

/**
 * Skill Status Entry (aligns with OpenClaw SkillStatusEntry)
 */
data class SkillStatusEntry(
    val name: String,
    val description: String,
    val source: SkillSource,
    val bundled: Boolean,
    val filePath: String,
    val baseDir: String,
    val skillKey: String,
    val primaryEnv: String? = null,
    val emoji: String? = null,
    val homepage: String? = null,
    val always: Boolean,
    val disabled: Boolean,
    val blockedByAllowlist: Boolean,
    val eligible: Boolean,
    val requirements: SkillRequires? = null,
    val missing: SkillRequires? = null,
    val configChecks: List<SkillConfigCheck>,
    val install: List<SkillInstallOption>
)

/**
 * Config Check Result
 */
data class SkillConfigCheck(
    val path: String,
    val exists: Boolean,
    val value: Any? = null
)

/**
 * Available Install Option
 */
data class SkillInstallOption(
    val installId: String,
    val kind: InstallKind,
    val label: String,
    val available: Boolean,
    val reason: String? = null
)

/**
 * Skills Limits Configuration (aligns with OpenClaw default limits)
 */
data class SkillsLimits(
    val maxCandidatesPerRoot: Int = 300,
    val maxSkillsLoadedPerSource: Int = 200,
    val maxSkillsInPrompt: Int = 150,
    val maxSkillsPromptChars: Int = 30_000,
    val maxSkillFileBytes: Int = 256_000
)
