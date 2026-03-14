package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: install and manage packaged skills.
 */


import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Skill Installer
 *
 * Provides skill download, extraction, installation, and uninstallation functions
 */
class SkillInstaller(private val context: Context) {
    companion object {
        private const val TAG = "SkillInstaller"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    }

    private val clawHubClient = ClawHubClient()
    private val workspacePath = "/sdcard/.androidforclaw/workspace"
    private val managedSkillsDir = "/sdcard/.androidforclaw/skills"
    private val downloadCacheDir = File(context.cacheDir, "skill-downloads")
    private val lockManager = SkillLockManager(workspacePath)

    init {
        downloadCacheDir.mkdirs()
    }

    /**
     * Install skill from ClawHub
     *
     * @param slug Skill slug
     * @param version Version number (default "latest")
     * @param progressCallback Progress callback
     */
    suspend fun installFromClawHub(
        slug: String,
        version: String = "latest",
        progressCallback: ((InstallProgress) -> Unit)? = null
    ): Result<InstallResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Installing skill from ClawHub: $slug@$version")

            // 1. Check if already installed
            val existingEntry = lockManager.getSkill(slug)
            if (existingEntry != null) {
                progressCallback?.invoke(InstallProgress.Info("Skill already installed: ${existingEntry.version}"))
                Log.d(TAG, "Skill already installed: $slug@${existingEntry.version}")
            }

            // 2. Get skill details
            progressCallback?.invoke(InstallProgress.FetchingDetails)
            val detailsResult = clawHubClient.getSkillDetails(slug)
            if (detailsResult.isFailure) {
                return@withContext Result.failure(detailsResult.exceptionOrNull()!!)
            }
            val details = detailsResult.getOrNull()!!
            Log.d(TAG, "Skill details: ${details.name} - ${details.description}")

            // 3. Download skill package
            progressCallback?.invoke(InstallProgress.Downloading(0, 0))
            val downloadFile = File(downloadCacheDir, "$slug-$version.zip")
            val downloadResult = clawHubClient.downloadSkill(
                slug = slug,
                version = version,
                targetFile = downloadFile
            ) { downloaded, total ->
                progressCallback?.invoke(InstallProgress.Downloading(downloaded, total))
            }

            if (downloadResult.isFailure) {
                return@withContext Result.failure(downloadResult.exceptionOrNull()!!)
            }

            // 4. Calculate file hash
            progressCallback?.invoke(InstallProgress.VerifyingHash)
            val hash = calculateFileHash(downloadFile)
            Log.d(TAG, "Downloaded file hash: $hash")

            // 5. Extract skill package
            progressCallback?.invoke(InstallProgress.Extracting)
            val targetDir = File(managedSkillsDir, slug)
            val extractResult = extractZip(downloadFile, targetDir)
            if (extractResult.isFailure) {
                return@withContext Result.failure(extractResult.exceptionOrNull()!!)
            }

            // 6. Verify SKILL.md exists
            val skillMdFile = File(targetDir, "SKILL.md")
            if (!skillMdFile.exists()) {
                targetDir.deleteRecursively()
                return@withContext Result.failure(
                    Exception("Invalid skill package: SKILL.md not found")
                )
            }

            // 7. Update lock file
            progressCallback?.invoke(InstallProgress.UpdatingLock)
            val lockEntry = SkillLockEntry(
                name = details.name,
                slug = slug,
                version = version,
                hash = hash,
                installedAt = DATE_FORMAT.format(Date()),
                source = "clawhub"
            )
            lockManager.addOrUpdateSkill(lockEntry)

            // 8. Clean up download cache
            downloadFile.delete()

            progressCallback?.invoke(InstallProgress.Complete)
            Log.i(TAG, "✅ Skill installed successfully: $slug@$version")

            Result.success(
                InstallResult(
                    slug = slug,
                    name = details.name,
                    version = version,
                    path = targetDir.absolutePath,
                    hash = hash
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            progressCallback?.invoke(InstallProgress.Error(e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }

    /**
     * Install skill from local file
     *
     * @param zipFile ZIP file path
     * @param name Skill name (optional, extracted from SKILL.md)
     */
    suspend fun installFromFile(
        zipFile: File,
        name: String? = null
    ): Result<InstallResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Installing skill from file: ${zipFile.absolutePath}")

            // 1. Extract to temporary directory
            val tempDir = File(downloadCacheDir, "temp-${System.currentTimeMillis()}")
            val extractResult = extractZip(zipFile, tempDir)
            if (extractResult.isFailure) {
                return@withContext Result.failure(extractResult.exceptionOrNull()!!)
            }

            // 2. Verify and parse SKILL.md
            val skillMdFile = File(tempDir, "SKILL.md")
            if (!skillMdFile.exists()) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(
                    Exception("Invalid skill package: SKILL.md not found")
                )
            }

            val skillDoc = try {
                SkillParser.parse(skillMdFile.readText(), skillMdFile.absolutePath)
            } catch (e: Exception) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(
                    Exception("Invalid SKILL.md: ${e.message}")
                )
            }

            val skillName = name ?: skillDoc.name

            // 3. Move to managed directory
            val targetDir = File(managedSkillsDir, skillName)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.parentFile?.mkdirs()

            if (!tempDir.renameTo(targetDir)) {
                // Rename failed, try copying
                tempDir.copyRecursively(targetDir, overwrite = true)
                tempDir.deleteRecursively()
            }

            // 4. Calculate hash
            val hash = calculateFileHash(zipFile)

            // 5. Update lock file
            val lockEntry = SkillLockEntry(
                name = skillName,
                slug = skillName,  // Use name as slug for local install
                version = "local",
                hash = hash,
                installedAt = DATE_FORMAT.format(Date()),
                source = "local"
            )
            lockManager.addOrUpdateSkill(lockEntry)

            Log.i(TAG, "✅ Skill installed from file: $skillName")

            Result.success(
                InstallResult(
                    slug = skillName,
                    name = skillName,
                    version = "local",
                    path = targetDir.absolutePath,
                    hash = hash
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Installation from file failed", e)
            Result.failure(e)
        }
    }

    /**
     * Uninstall skill
     *
     * @param slug Skill slug
     */
    suspend fun uninstall(slug: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Uninstalling skill: $slug")

            // 1. Check if installed
            val entry = lockManager.getSkill(slug)
                ?: return@withContext Result.failure(
                    Exception("Skill not installed: $slug")
                )

            // 2. Delete skill directory
            val skillDir = File(managedSkillsDir, slug)
            if (skillDir.exists()) {
                skillDir.deleteRecursively()
                Log.d(TAG, "Deleted skill directory: ${skillDir.absolutePath}")
            }

            // 3. Remove from lock file
            lockManager.removeSkill(slug)

            Log.i(TAG, "✅ Skill uninstalled: $slug")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Uninstallation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Extract ZIP file
     */
    private fun extractZip(zipFile: File, targetDir: File): Result<Unit> {
        return try {
            targetDir.mkdirs()

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry

                while (entry != null) {
                    val file = File(targetDir, entry.name)

                    // Security check: prevent ZIP path traversal attack
                    if (!file.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        throw SecurityException("Zip entry outside target directory: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            zis.copyTo(fos)
                        }
                    }

                    entry = zis.nextEntry
                }
            }

            Log.d(TAG, "✅ Extracted ZIP to ${targetDir.absolutePath}")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ZIP", e)
            Result.failure(e)
        }
    }

    /**
     * Calculate file SHA-256 hash
     */
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Installation Progress
 */
sealed class InstallProgress {
    data class Info(val message: String) : InstallProgress()
    object FetchingDetails : InstallProgress()
    data class Downloading(val downloaded: Long, val total: Long) : InstallProgress()
    object VerifyingHash : InstallProgress()
    object Extracting : InstallProgress()
    object UpdatingLock : InstallProgress()
    object Complete : InstallProgress()
    data class Error(val message: String) : InstallProgress()
}

/**
 * Installation Result
 */
data class InstallResult(
    val slug: String,
    val name: String,
    val version: String,
    val path: String,
    val hash: String
)
