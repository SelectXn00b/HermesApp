package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: serialized skill install/update locking.
 */


import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File

/**
 * Skill Lock File Manager
 *
 * Manages .clawhub/lock.json
 * Records version, hash, installation time and other info of installed skills
 */
class SkillLockManager(private val workspacePath: String) {
    companion object {
        private const val TAG = "SkillLockManager"
        private const val LOCK_FILE_NAME = "lock.json"
    }

    private val lockDir = File(workspacePath, ".clawhub")
    private val lockFile = File(lockDir, LOCK_FILE_NAME)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Read lock file
     */
    fun readLock(): SkillLockFile {
        if (!lockFile.exists()) {
            Log.d(TAG, "Lock file not found, creating empty")
            return SkillLockFile(skills = emptyList())
        }

        return try {
            val content = lockFile.readText()
            gson.fromJson(content, SkillLockFile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read lock file", e)
            SkillLockFile(skills = emptyList())
        }
    }

    /**
     * Write lock file
     */
    fun writeLock(lockFile: SkillLockFile): Result<Unit> {
        return try {
            // Ensure directory exists
            lockDir.mkdirs()

            // Write file
            val content = gson.toJson(lockFile)
            this.lockFile.writeText(content)

            Log.d(TAG, "✅ Lock file written: ${this.lockFile.absolutePath}")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to write lock file", e)
            Result.failure(e)
        }
    }

    /**
     * Add or update skill record
     */
    fun addOrUpdateSkill(entry: SkillLockEntry): Result<Unit> {
        return try {
            val lock = readLock()
            val existingIndex = lock.skills.indexOfFirst { it.slug == entry.slug }

            val updatedSkills = if (existingIndex >= 0) {
                // Update existing record
                lock.skills.toMutableList().apply {
                    set(existingIndex, entry)
                }
            } else {
                // Add new record
                lock.skills + entry
            }

            writeLock(lock.copy(skills = updatedSkills))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add/update skill", e)
            Result.failure(e)
        }
    }

    /**
     * Remove skill record
     */
    fun removeSkill(slug: String): Result<Unit> {
        return try {
            val lock = readLock()
            val updatedSkills = lock.skills.filter { it.slug != slug }

            if (updatedSkills.size == lock.skills.size) {
                Log.w(TAG, "Skill not found in lock: $slug")
            }

            writeLock(lock.copy(skills = updatedSkills))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove skill", e)
            Result.failure(e)
        }
    }

    /**
     * Get skill record
     */
    fun getSkill(slug: String): SkillLockEntry? {
        val lock = readLock()
        return lock.skills.find { it.slug == slug }
    }

    /**
     * List all installed skills
     */
    fun listSkills(): List<SkillLockEntry> {
        return readLock().skills
    }

    /**
     * Check if skill is installed
     */
    fun isInstalled(slug: String): Boolean {
        return getSkill(slug) != null
    }

    /**
     * Get installed version
     */
    fun getInstalledVersion(slug: String): String? {
        return getSkill(slug)?.version
    }
}

/**
 * Lock File Structure
 */
data class SkillLockFile(
    val skills: List<SkillLockEntry>
)

/**
 * Skill Lock Entry
 */
data class SkillLockEntry(
    val name: String,
    val slug: String,
    val version: String,
    val hash: String? = null,
    val installedAt: String,
    val source: String = "clawhub"  // clawhub, github, local
)
