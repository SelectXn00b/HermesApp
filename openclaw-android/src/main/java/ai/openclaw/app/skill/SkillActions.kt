package ai.openclaw.app.skill

/**
 * Interface for skill install/uninstall operations.
 * Implemented in the app module using SkillInstaller + SkillLockManager.
 */
interface SkillActions {

    /** Check if a skill (by slug) is installed on disk. */
    fun isInstalled(slug: String): Boolean

    /** Get all installed skill slugs (managed dir scan). */
    fun getInstalledSlugs(): Set<String>

    /**
     * Install a skill: write content to managed skills dir + update lock.json.
     * @param name  Display name
     * @param slug  Directory name / identifier
     * @param content  Full SKILL.md content
     */
    suspend fun install(name: String, slug: String, content: String)

    /**
     * Uninstall a skill: delete from managed skills dir + update lock.json.
     * @param slug  Directory name / identifier
     */
    suspend fun uninstall(slug: String)

    /** Get the on-disk directory for a skill (for "view in file manager"). */
    fun getSkillDir(slug: String): java.io.File
}

/**
 * No-op fallback — used when no implementation is injected.
 */
object NoOpSkillActions : SkillActions {
    override fun isInstalled(slug: String) = false
    override fun getInstalledSlugs() = emptySet<String>()
    override suspend fun install(name: String, slug: String, content: String) {}
    override suspend fun uninstall(slug: String) {}
    override fun getSkillDir(slug: String) = java.io.File("/dev/null")
}
