package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: build skill status/introspection responses.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import java.io.File

/**
 * Skill Status Builder
 *
 * Aligns with OpenClaw's buildWorkspaceSkillStatus()
 * Scans skill directories, evaluates skill eligibility, generates status report.
 *
 * Uses the unified SkillParser (no more SkillFrontmatterParser duplication).
 */
class SkillStatusBuilder(private val context: Context) {
    companion object {
        private const val TAG = "SkillStatusBuilder"
    }

    private val configLoader = ConfigLoader(context)

    /**
     * Build skill status report
     *
     * @param workspacePath Workspace path (default: /sdcard/.androidforclaw/workspace)
     * @return SkillStatusReport
     */
    fun buildStatus(workspacePath: String = "/sdcard/.androidforclaw/workspace"): SkillStatusReport {
        val managedSkillsDir = "/sdcard/.androidforclaw/skills"
        val bundledSkillsPath = "skills" // assets path
        val config = configLoader.loadOpenClawConfig()

        val skillEntries = mutableListOf<SkillStatusEntry>()

        // 1. Load from extraDirs (lowest priority)
        config.skills.extraDirs.forEach { extraDir ->
            val dir = File(extraDir)
            if (dir.exists() && dir.isDirectory) {
                Log.d(TAG, "Loading extra skills from $extraDir")
                loadSkillsFromDirectory(dir, SkillSource.EXTRA).forEach { doc ->
                    skillEntries.add(buildStatusEntry(doc, config))
                }
            }
        }

        // 2. Load bundled skills
        Log.d(TAG, "Loading bundled skills from assets://$bundledSkillsPath")
        loadSkillsFromAssets(bundledSkillsPath, SkillSource.BUNDLED).forEach { doc ->
            skillEntries.add(buildStatusEntry(doc, config))
        }

        // 3. Load managed skills
        val managedDir = File(managedSkillsDir)
        if (managedDir.exists() && managedDir.isDirectory) {
            Log.d(TAG, "Loading managed skills from $managedSkillsDir")
            loadSkillsFromDirectory(managedDir, SkillSource.MANAGED).forEach { doc ->
                skillEntries.add(buildStatusEntry(doc, config))
            }
        }

        // 4. Load plugin skills
        for ((pluginName, pluginEntry) in config.plugins.entries) {
            if (!pluginEntry.enabled) continue
            val skillDirs = pluginEntry.skills.ifEmpty { listOf("skills") }
            for (skillDir in skillDirs) {
                val assetsPath = "extensions/$pluginName/$skillDir"
                loadSkillsFromAssets(assetsPath, SkillSource.PLUGIN).forEach { doc ->
                    skillEntries.add(buildStatusEntry(doc, config))
                }
                // Also check filesystem
                val fsPath = File("/sdcard/.androidforclaw/extensions/$pluginName/$skillDir")
                if (fsPath.exists() && fsPath.isDirectory) {
                    loadSkillsFromDirectory(fsPath, SkillSource.PLUGIN).forEach { doc ->
                        skillEntries.add(buildStatusEntry(doc, config))
                    }
                }
            }
        }

        // 5. Load workspace skills (highest priority)
        val workspaceSkillsDir = File(workspacePath, "skills")
        if (workspaceSkillsDir.exists() && workspaceSkillsDir.isDirectory) {
            Log.d(TAG, "Loading workspace skills from ${workspaceSkillsDir.absolutePath}")
            loadSkillsFromDirectory(workspaceSkillsDir, SkillSource.WORKSPACE).forEach { doc ->
                skillEntries.add(buildStatusEntry(doc, config))
            }
        }

        Log.i(TAG, "✅ Loaded ${skillEntries.size} skills total")

        return SkillStatusReport(
            workspaceDir = workspacePath,
            managedSkillsDir = managedSkillsDir,
            skills = skillEntries
        )
    }

    /**
     * Load skills from assets using SkillParser
     */
    private fun loadSkillsFromAssets(assetsPath: String, source: SkillSource): List<SkillDocument> {
        val docs = mutableListOf<SkillDocument>()

        try {
            val assetManager = context.assets
            val skillDirs = assetManager.list(assetsPath) ?: emptyArray()

            for (skillDir in skillDirs) {
                val skillPath = "$assetsPath/$skillDir"
                val files = assetManager.list(skillPath) ?: emptyArray()

                if ("SKILL.md" in files) {
                    val skillMdPath = "$skillPath/SKILL.md"
                    try {
                        val content = assetManager.open(skillMdPath).bufferedReader().use { it.readText() }
                        val doc = SkillParser.parse(content, "assets://$skillMdPath")
                            .copy(source = source, filePath = "assets://$skillMdPath")
                        docs.add(doc)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse $skillMdPath: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load skills from assets", e)
        }

        return docs
    }

    /**
     * Load skills from filesystem directory using SkillParser
     */
    private fun loadSkillsFromDirectory(directory: File, source: SkillSource): List<SkillDocument> {
        val docs = mutableListOf<SkillDocument>()

        directory.listFiles()?.forEach { skillDir ->
            if (skillDir.isDirectory) {
                val skillMdFile = File(skillDir, "SKILL.md")
                if (skillMdFile.exists()) {
                    try {
                        val content = skillMdFile.readText()
                        val doc = SkillParser.parse(content, skillMdFile.absolutePath)
                            .copy(source = source, filePath = skillMdFile.absolutePath)
                        docs.add(doc)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse ${skillMdFile.absolutePath}: ${e.message}")
                    }
                }
            }
        }

        return docs
    }

    /**
     * Build status entry from a SkillDocument
     */
    private fun buildStatusEntry(
        doc: SkillDocument,
        config: com.xiaomo.androidforclaw.config.OpenClawConfig
    ): SkillStatusEntry {
        val skillKey = doc.effectiveSkillKey()
        val skillConfig = config.skills.entries[skillKey]

        // Check if disabled by config
        val disabled = skillConfig?.enabled == false

        // Check if blocked by allowlist (bundled skills need to be in allowlist)
        val blockedByAllowlist = if (doc.source == SkillSource.BUNDLED) {
            val allowBundled = config.skills.allowBundled
            allowBundled != null && doc.name !in allowBundled
        } else {
            false
        }

        // Check requirements
        val requires = doc.metadata.requires
        val missing = checkMissing(requires)

        // Check config paths
        val configChecks = checkConfigPaths(requires?.config, config)

        // Check platform compatibility
        val platformCompatible = checkPlatformCompatibility(doc.metadata.os)

        // Evaluate eligibility
        val eligible = !disabled &&
                !blockedByAllowlist &&
                platformCompatible &&
                missing == null

        // Build install options
        val installOptions = buildInstallOptions(doc.metadata.install)

        return SkillStatusEntry(
            name = doc.name,
            description = doc.description,
            source = doc.source,
            bundled = doc.source == SkillSource.BUNDLED,
            filePath = doc.filePath,
            baseDir = File(doc.filePath).parent ?: "",
            skillKey = skillKey,
            primaryEnv = doc.metadata.primaryEnv,
            emoji = doc.metadata.emoji,
            homepage = doc.metadata.homepage,
            always = doc.metadata.always,
            disabled = disabled,
            blockedByAllowlist = blockedByAllowlist,
            eligible = eligible,
            requirements = requires,
            missing = missing,
            configChecks = configChecks,
            install = installOptions
        )
    }

    /**
     * Check missing requirements, returns null if all satisfied.
     */
    private fun checkMissing(requires: SkillRequires?): SkillRequires? {
        if (requires == null) return null

        // bins: skip on Android (no PATH binaries)
        // anyBins: skip on Android

        // env: check System.getenv
        val missingEnv = requires.env.filter { System.getenv(it) == null }

        // config: checked separately via checkConfigPaths

        if (missingEnv.isEmpty()) return null

        return SkillRequires(env = missingEnv)
    }

    /**
     * Check config paths
     */
    private fun checkConfigPaths(
        configPaths: List<String>?,
        config: com.xiaomo.androidforclaw.config.OpenClawConfig
    ): List<SkillConfigCheck> {
        if (configPaths.isNullOrEmpty()) return emptyList()

        return configPaths.map { path ->
            val value = getConfigValue(path, config)
            SkillConfigCheck(
                path = path,
                exists = value != null,
                value = value
            )
        }
    }

    /**
     * Get config value by dot-path
     *
     * Supports common paths like:
     * - gateway.enabled
     * - gateway.feishu.appId
     * - agent.maxIterations
     * - skills.entries.<key>.enabled
     */
    private fun getConfigValue(path: String, config: com.xiaomo.androidforclaw.config.OpenClawConfig): Any? {
        val parts = path.split(".")
        return when {
            parts.size >= 2 && parts[0] in listOf("gateway", "channels") -> {
                when (parts.getOrNull(1)) {
                    "enabled" -> true
                    "feishu" -> when (parts.getOrNull(2)) {
                        "appId" -> config.channels.feishu.appId.takeIf { it.isNotEmpty() }
                        "appSecret" -> config.channels.feishu.appSecret.takeIf { it.isNotEmpty() }
                        "enabled" -> config.channels.feishu.enabled
                        else -> null
                    }
                    else -> null
                }
            }
            parts.size == 2 && parts[0] == "agent" -> {
                when (parts[1]) {
                    "maxIterations" -> config.agent.maxIterations
                    "defaultModel" -> config.agent.defaultModel
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Check platform compatibility
     */
    private fun checkPlatformCompatibility(osList: List<String>?): Boolean {
        if (osList == null) return true // No restrictions
        return "android" in osList.map { it.lowercase() }
    }

    /**
     * Build install options
     */
    private fun buildInstallOptions(installSpecs: List<SkillInstallSpec>?): List<SkillInstallOption> {
        if (installSpecs == null) return emptyList()

        return installSpecs.map { spec ->
            val (available, reason) = checkInstallAvailability(spec)

            SkillInstallOption(
                installId = spec.id ?: "${spec.kind.name.lowercase()}-default",
                kind = spec.kind,
                label = spec.label ?: "Install via ${spec.kind.name}",
                available = available,
                reason = reason
            )
        }
    }

    /**
     * Check installer availability
     */
    private fun checkInstallAvailability(spec: SkillInstallSpec): Pair<Boolean, String?> {
        val platformCompatible = spec.os?.let { osList ->
            "android" in osList.map { it.lowercase() }
        } ?: true

        if (!platformCompatible) {
            return Pair(false, "Platform not supported")
        }

        return when (spec.kind) {
            InstallKind.APK -> {
                if (spec.url != null) Pair(true, null)
                else Pair(false, "Missing APK URL")
            }
            InstallKind.DOWNLOAD -> {
                if (spec.url != null) Pair(true, null)
                else Pair(false, "Missing download URL")
            }
            else -> Pair(false, "${spec.kind.name} not available on Android")
        }
    }
}
