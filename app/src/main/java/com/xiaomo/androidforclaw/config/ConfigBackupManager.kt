package com.xiaomo.androidforclaw.config

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/(all)
 *
 * AndroidForClaw adaptation: config backup/restore helpers.
 */


import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Config Backup Manager
 * Aligned with OpenClaw's config fault-tolerance mechanism
 *
 * Features:
 * 1. openclaw.last-known-good.json - Automatically backup last successful config
 * 2. config-backups/ - Historical backups (with timestamps)
 * 3. Auto-recovery on startup failure
 */
class ConfigBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "ConfigBackup"

        private const val CONFIG_DIR = "/sdcard/.androidforclaw/config"
        private const val CONFIG_FILE = "$CONFIG_DIR/openclaw.json"
        private const val LAST_KNOWN_GOOD_FILE = "$CONFIG_DIR/openclaw.last-known-good.json"
        private const val BACKUPS_DIR = "/sdcard/.androidforclaw/config-backups"

        private const val MAX_BACKUPS = 10 // Keep maximum 10 historical backups
    }

    init {
        ensureDirectoriesExist()
    }

    /**
     * Backup current config as last-known-good
     * Called after config is successfully loaded
     */
    fun backupAsLastKnownGood(): Boolean {
        val configFile = File(CONFIG_FILE)
        val lastKnownGoodFile = File(LAST_KNOWN_GOOD_FILE)

        return try {
            if (!configFile.exists()) {
                Log.w(TAG, "Config file does not exist, cannot backup")
                return false
            }

            // Delete old file first (if exists) to ensure successful copy
            if (lastKnownGoodFile.exists()) {
                lastKnownGoodFile.delete()
            }

            configFile.copyTo(lastKnownGoodFile, overwrite = false)
            Log.i(TAG, "✅ Config backed up to last-known-good")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup last-known-good", e)
            false
        }
    }

    /**
     * Restore config from last-known-good
     */
    fun restoreFromLastKnownGood(): Boolean {
        val lastKnownGoodFile = File(LAST_KNOWN_GOOD_FILE)
        val configFile = File(CONFIG_FILE)

        return try {
            if (!lastKnownGoodFile.exists()) {
                Log.e(TAG, "❌ No available last-known-good backup")
                return false
            }

            lastKnownGoodFile.copyTo(configFile, overwrite = true)
            Log.i(TAG, "✅ Config restored from last-known-good")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from last-known-good", e)
            false
        }
    }

    /**
     * Create historical backup (with timestamp)
     * Called before user manually edits config
     */
    fun createHistoricalBackup(): String? {
        val configFile = File(CONFIG_FILE)
        if (!configFile.exists()) {
            Log.w(TAG, "Config file does not exist, cannot create backup")
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val backupName = "openclaw-$timestamp.json"
        val backupFile = File(BACKUPS_DIR, backupName)

        return try {
            configFile.copyTo(backupFile, overwrite = false)
            Log.i(TAG, "✅ Config backed up: $backupName")

            // Clean old backups
            cleanOldBackups()

            backupName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create historical backup", e)
            null
        }
    }

    /**
     * List all historical backups
     */
    fun listBackups(): List<BackupInfo> {
        val backupsDir = File(BACKUPS_DIR)
        if (!backupsDir.exists()) return emptyList()

        return backupsDir.listFiles()
            ?.filter { it.name.startsWith("openclaw-") && it.name.endsWith(".json") }
            ?.map { file ->
                BackupInfo(
                    name = file.name,
                    timestamp = extractTimestamp(file.name),
                    size = file.length(),
                    path = file.absolutePath
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    /**
     * Restore from specified historical backup
     */
    fun restoreFromHistoricalBackup(backupName: String): Boolean {
        val backupFile = File(BACKUPS_DIR, backupName)
        val configFile = File(CONFIG_FILE)

        return try {
            if (!backupFile.exists()) {
                Log.e(TAG, "❌ Backup file does not exist: $backupName")
                return false
            }

            // Backup current config first
            createHistoricalBackup()

            // Restore specified backup
            backupFile.copyTo(configFile, overwrite = true)
            Log.i(TAG, "✅ Backup restored: $backupName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup: $backupName", e)
            false
        }
    }

    /**
     * Delete specified backup
     */
    fun deleteBackup(backupName: String): Boolean {
        val backupFile = File(BACKUPS_DIR, backupName)
        return try {
            val deleted = backupFile.delete()
            if (deleted) {
                Log.i(TAG, "Deleted backup: $backupName")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete backup: $backupName", e)
            false
        }
    }

    /**
     * Safely load config (with auto-recovery)
     * Used in ConfigLoader
     */
    fun <T> loadConfigSafely(loader: () -> T): T? {
        return try {
            // Try to load config
            val config = loader()

            // If successful, backup as last-known-good
            backupAsLastKnownGood()

            config
        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "❌ Config loading failed: ${e.message}")
            Log.e(TAG, "========================================")

            // Try to restore from last-known-good
            if (restoreFromLastKnownGood()) {
                try {
                    Log.i(TAG, "Trying to reload with last-known-good config...")
                    loader()
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ last-known-good config also cannot be loaded", e2)
                    null
                }
            } else {
                Log.e(TAG, "❌ No available backup config")
                null
            }
        }
    }

    /**
     * Get backup statistics
     */
    fun getBackupStats(): BackupStats {
        val backups = listBackups()
        val hasLastKnownGood = File(LAST_KNOWN_GOOD_FILE).exists()
        val totalSize = backups.sumOf { it.size }

        return BackupStats(
            historicalBackupCount = backups.size,
            hasLastKnownGood = hasLastKnownGood,
            totalBackupSize = totalSize,
            oldestBackup = backups.lastOrNull()?.timestamp,
            newestBackup = backups.firstOrNull()?.timestamp
        )
    }

    // ==================== Private Methods ====================

    private fun ensureDirectoriesExist() {
        File(CONFIG_DIR).mkdirs()
        File(BACKUPS_DIR).mkdirs()
    }

    /**
     * Clean old backups, keep only the most recent MAX_BACKUPS
     */
    private fun cleanOldBackups() {
        val backups = listBackups()
        if (backups.size <= MAX_BACKUPS) return

        val toDelete = backups.drop(MAX_BACKUPS)
        toDelete.forEach { backup ->
            deleteBackup(backup.name)
        }

        Log.i(TAG, "Cleaned ${toDelete.size} old backups")
    }

    /**
     * Extract timestamp from filename
     * openclaw-20260308-143022.json -> 2026-03-08T14:30:22Z
     */
    private fun extractTimestamp(filename: String): String {
        return try {
            // Extract timestamp part: 20260308-143022
            val timestampPart = filename.removePrefix("openclaw-")
                .removeSuffix(".json")

            // Parse as date
            val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            val date = dateFormat.parse(timestampPart)

            // Convert to ISO 8601
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            isoFormat.format(date ?: Date())
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Backup information
 */
data class BackupInfo(
    val name: String,
    val timestamp: String,
    val size: Long,
    val path: String
)

/**
 * Backup statistics
 */
data class BackupStats(
    val historicalBackupCount: Int,
    val hasLastKnownGood: Boolean,
    val totalBackupSize: Long,
    val oldestBackup: String?,
    val newestBackup: String?
)
