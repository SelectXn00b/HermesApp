package com.xiaomo.androidforclaw.agent.skills

import ai.openclaw.app.skill.SkillActions
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.workspace.StoragePaths
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Concrete SkillActions backed by the existing SkillLockManager + filesystem.
 * Reuses the same paths and lock format as SkillInstaller / ClawHub.
 */
class SkillActionsImpl : SkillActions {

    companion object {
        private const val TAG = "SkillActionsImpl"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    }

    private val managedDir: File get() = StoragePaths.skills
    private val lockManager = SkillLockManager(StoragePaths.workspace.absolutePath)

    override fun isInstalled(slug: String): Boolean {
        return File(managedDir, "$slug/SKILL.md").exists()
    }

    override fun getInstalledSlugs(): Set<String> {
        val dir = managedDir
        if (!dir.exists()) return emptySet()
        return dir.listFiles { f -> f.isDirectory && File(f, "SKILL.md").exists() }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    override suspend fun install(name: String, slug: String, content: String) {
        try {
            // 1. Write SKILL.md
            val skillDir = File(managedDir, slug)
            skillDir.mkdirs()
            File(skillDir, "SKILL.md").writeText(content)

            // 2. Update lock file (same format as SkillInstaller)
            lockManager.addOrUpdateSkill(
                SkillLockEntry(
                    name = name,
                    slug = slug,
                    version = "online",
                    hash = null,
                    installedAt = DATE_FORMAT.format(Date()),
                    source = "agency-agents",
                )
            )
            Log.i(TAG, "✅ Skill installed: $slug → ${skillDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install skill: $slug", e)
        }
    }

    override suspend fun uninstall(slug: String) {
        try {
            // 1. Delete directory
            val skillDir = File(managedDir, slug)
            if (skillDir.exists()) {
                skillDir.deleteRecursively()
                Log.i(TAG, "✅ Deleted skill directory: ${skillDir.absolutePath}")
            }
            // 2. Remove from lock
            lockManager.removeSkill(slug)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall skill: $slug", e)
        }
    }

    override fun getSkillDir(slug: String): File = File(managedDir, slug)
}
