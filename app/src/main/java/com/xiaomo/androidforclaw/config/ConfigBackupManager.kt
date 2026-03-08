package com.xiaomo.androidforclaw.config

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 配置备份管理器
 * 对齐 OpenClaw 的配置容错机制
 *
 * 功能:
 * 1. openclaw.last-known-good.json - 自动备份最后一次成功配置
 * 2. config-backups/ - 历史备份（带时间戳）
 * 3. 启动失败自动恢复
 */
class ConfigBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "ConfigBackup"

        private const val CONFIG_DIR = "/sdcard/.androidforclaw/config"
        private const val CONFIG_FILE = "$CONFIG_DIR/openclaw.json"
        private const val LAST_KNOWN_GOOD_FILE = "$CONFIG_DIR/openclaw.last-known-good.json"
        private const val BACKUPS_DIR = "/sdcard/.androidforclaw/config-backups"

        private const val MAX_BACKUPS = 10 // 最多保留10个历史备份
    }

    init {
        ensureDirectoriesExist()
    }

    /**
     * 备份当前配置为 last-known-good
     * 在配置成功加载后调用
     */
    fun backupAsLastKnownGood(): Boolean {
        val configFile = File(CONFIG_FILE)
        val lastKnownGoodFile = File(LAST_KNOWN_GOOD_FILE)

        return try {
            if (!configFile.exists()) {
                Log.w(TAG, "配置文件不存在，无法备份")
                return false
            }

            configFile.copyTo(lastKnownGoodFile, overwrite = true)
            Log.i(TAG, "✅ 配置已备份到 last-known-good")
            true
        } catch (e: Exception) {
            Log.e(TAG, "备份 last-known-good 失败", e)
            false
        }
    }

    /**
     * 从 last-known-good 恢复配置
     */
    fun restoreFromLastKnownGood(): Boolean {
        val lastKnownGoodFile = File(LAST_KNOWN_GOOD_FILE)
        val configFile = File(CONFIG_FILE)

        return try {
            if (!lastKnownGoodFile.exists()) {
                Log.e(TAG, "❌ 没有可用的 last-known-good 备份")
                return false
            }

            lastKnownGoodFile.copyTo(configFile, overwrite = true)
            Log.i(TAG, "✅ 已从 last-known-good 恢复配置")
            true
        } catch (e: Exception) {
            Log.e(TAG, "从 last-known-good 恢复失败", e)
            false
        }
    }

    /**
     * 创建历史备份（带时间戳）
     * 在用户手动编辑配置前调用
     */
    fun createHistoricalBackup(): String? {
        val configFile = File(CONFIG_FILE)
        if (!configFile.exists()) {
            Log.w(TAG, "配置文件不存在，无法创建备份")
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val backupName = "openclaw-$timestamp.json"
        val backupFile = File(BACKUPS_DIR, backupName)

        return try {
            configFile.copyTo(backupFile, overwrite = false)
            Log.i(TAG, "✅ 配置已备份: $backupName")

            // 清理旧备份
            cleanOldBackups()

            backupName
        } catch (e: Exception) {
            Log.e(TAG, "创建历史备份失败", e)
            null
        }
    }

    /**
     * 列出所有历史备份
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
     * 恢复指定的历史备份
     */
    fun restoreFromHistoricalBackup(backupName: String): Boolean {
        val backupFile = File(BACKUPS_DIR, backupName)
        val configFile = File(CONFIG_FILE)

        return try {
            if (!backupFile.exists()) {
                Log.e(TAG, "❌ 备份文件不存在: $backupName")
                return false
            }

            // 先备份当前配置
            createHistoricalBackup()

            // 恢复指定备份
            backupFile.copyTo(configFile, overwrite = true)
            Log.i(TAG, "✅ 已恢复备份: $backupName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "恢复备份失败: $backupName", e)
            false
        }
    }

    /**
     * 删除指定备份
     */
    fun deleteBackup(backupName: String): Boolean {
        val backupFile = File(BACKUPS_DIR, backupName)
        return try {
            val deleted = backupFile.delete()
            if (deleted) {
                Log.i(TAG, "已删除备份: $backupName")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "删除备份失败: $backupName", e)
            false
        }
    }

    /**
     * 安全加载配置（带自动恢复）
     * 在 ConfigLoader 中使用
     */
    fun <T> loadConfigSafely(loader: () -> T): T? {
        return try {
            // 尝试加载配置
            val config = loader()

            // 加载成功，备份为 last-known-good
            backupAsLastKnownGood()

            config
        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "❌ 配置加载失败: ${e.message}")
            Log.e(TAG, "========================================")

            // 尝试从 last-known-good 恢复
            if (restoreFromLastKnownGood()) {
                try {
                    Log.i(TAG, "尝试使用 last-known-good 配置重新加载...")
                    loader()
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ last-known-good 配置也无法加载", e2)
                    null
                }
            } else {
                Log.e(TAG, "❌ 无可用的备份配置")
                null
            }
        }
    }

    /**
     * 获取备份统计信息
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

    // ==================== 私有方法 ====================

    private fun ensureDirectoriesExist() {
        File(CONFIG_DIR).mkdirs()
        File(BACKUPS_DIR).mkdirs()
    }

    /**
     * 清理旧备份，只保留最近的 MAX_BACKUPS 个
     */
    private fun cleanOldBackups() {
        val backups = listBackups()
        if (backups.size <= MAX_BACKUPS) return

        val toDelete = backups.drop(MAX_BACKUPS)
        toDelete.forEach { backup ->
            deleteBackup(backup.name)
        }

        Log.i(TAG, "清理了 ${toDelete.size} 个旧备份")
    }

    /**
     * 从文件名提取时间戳
     * openclaw-20260308-143022.json -> 2026-03-08T14:30:22Z
     */
    private fun extractTimestamp(filename: String): String {
        return try {
            // 提取时间戳部分: 20260308-143022
            val timestampPart = filename.removePrefix("openclaw-")
                .removeSuffix(".json")

            // 解析为日期
            val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            val date = dateFormat.parse(timestampPart)

            // 转换为 ISO 8601
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            isoFormat.format(date ?: Date())
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * 备份信息
 */
data class BackupInfo(
    val name: String,
    val timestamp: String,
    val size: Long,
    val path: String
)

/**
 * 备份统计
 */
data class BackupStats(
    val historicalBackupCount: Int,
    val hasLastKnownGood: Boolean,
    val totalBackupSize: Long,
    val oldestBackup: String?,
    val newestBackup: String?
)
