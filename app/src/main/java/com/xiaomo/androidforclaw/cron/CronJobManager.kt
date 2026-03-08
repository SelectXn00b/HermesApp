package com.xiaomo.androidforclaw.cron

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Cron 定时任务管理器
 * 对齐 OpenClaw 的 cron/jobs.json
 *
 * 使用 Android WorkManager 实现定时任务
 */
class CronJobManager(private val context: Context) {

    companion object {
        private const val TAG = "CronJobManager"
        private const val CRON_FILE = "/sdcard/.androidforclaw/cron/jobs.json"
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val workManager = WorkManager.getInstance(context)

    init {
        ensureDirectoryExists()
    }

    /**
     * 加载所有 Cron 任务
     */
    fun loadJobs(): CronJobsConfig {
        val file = File(CRON_FILE)
        if (!file.exists()) {
            return CronJobsConfig(version = 1, jobs = emptyList())
        }

        return try {
            val json = file.readText()
            gson.fromJson(json, CronJobsConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "加载 cron jobs 失败", e)
            CronJobsConfig(version = 1, jobs = emptyList())
        }
    }

    /**
     * 保存 Cron 任务配置
     */
    fun saveJobs(config: CronJobsConfig) {
        val file = File(CRON_FILE)
        try {
            val json = gson.toJson(config)
            file.writeText(json)
            Log.i(TAG, "Cron jobs 已保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存 cron jobs 失败", e)
        }
    }

    /**
     * 添加新任务
     */
    fun addJob(job: CronJob): Boolean {
        val config = loadJobs()
        val updatedJobs = config.jobs.toMutableList()

        // 检查 ID 是否已存在
        if (updatedJobs.any { it.id == job.id }) {
            Log.w(TAG, "任务 ID 已存在: ${job.id}")
            return false
        }

        updatedJobs.add(job)
        saveJobs(config.copy(jobs = updatedJobs))

        // 如果启用，立即调度
        if (job.enabled) {
            scheduleJob(job)
        }

        Log.i(TAG, "添加任务: ${job.id}")
        return true
    }

    /**
     * 更新任务
     */
    fun updateJob(jobId: String, update: (CronJob) -> CronJob): Boolean {
        val config = loadJobs()
        val updatedJobs = config.jobs.map { job ->
            if (job.id == jobId) update(job) else job
        }

        saveJobs(config.copy(jobs = updatedJobs))

        // 重新调度
        val updatedJob = updatedJobs.find { it.id == jobId }
        if (updatedJob != null) {
            cancelJob(jobId)
            if (updatedJob.enabled) {
                scheduleJob(updatedJob)
            }
        }

        Log.i(TAG, "更新任务: $jobId")
        return true
    }

    /**
     * 删除任务
     */
    fun removeJob(jobId: String): Boolean {
        val config = loadJobs()
        val updatedJobs = config.jobs.filter { it.id != jobId }

        saveJobs(config.copy(jobs = updatedJobs))
        cancelJob(jobId)

        Log.i(TAG, "删除任务: $jobId")
        return true
    }

    /**
     * 启用任务
     */
    fun enableJob(jobId: String) {
        updateJob(jobId) { it.copy(enabled = true) }
    }

    /**
     * 禁用任务
     */
    fun disableJob(jobId: String) {
        updateJob(jobId) { it.copy(enabled = false) }
    }

    /**
     * 调度所有启用的任务
     */
    fun scheduleAllJobs() {
        val config = loadJobs()
        val enabledJobs = config.jobs.filter { it.enabled }

        Log.i(TAG, "========================================")
        Log.i(TAG, "开始调度 Cron 任务: ${enabledJobs.size} 个")
        Log.i(TAG, "========================================")

        enabledJobs.forEach { job ->
            scheduleJob(job)
        }

        Log.i(TAG, "✅ 所有任务已调度")
    }

    /**
     * 取消所有任务
     */
    fun cancelAllJobs() {
        val config = loadJobs()
        config.jobs.forEach { job ->
            cancelJob(job.id)
        }
        Log.i(TAG, "已取消所有任务")
    }

    /**
     * 获取任务状态
     */
    fun getJobStatus(jobId: String): JobStatus {
        val workInfo = workManager.getWorkInfosForUniqueWork(jobId).get()

        return if (workInfo.isEmpty()) {
            JobStatus.NOT_SCHEDULED
        } else {
            when (workInfo.first().state) {
                WorkInfo.State.ENQUEUED -> JobStatus.SCHEDULED
                WorkInfo.State.RUNNING -> JobStatus.RUNNING
                WorkInfo.State.SUCCEEDED -> JobStatus.COMPLETED
                WorkInfo.State.FAILED -> JobStatus.FAILED
                WorkInfo.State.BLOCKED -> JobStatus.BLOCKED
                WorkInfo.State.CANCELLED -> JobStatus.CANCELLED
            }
        }
    }

    // ==================== 私有方法 ====================

    private fun ensureDirectoryExists() {
        val dir = File(CRON_FILE).parentFile
        if (dir != null && !dir.exists()) {
            dir.mkdirs()
            Log.d(TAG, "创建 cron 目录")

            // 创建默认配置
            saveJobs(CronJobsConfig(
                version = 1,
                jobs = getDefaultJobs()
            ))
        }
    }

    /**
     * 调度单个任务
     */
    private fun scheduleJob(job: CronJob) {
        try {
            val interval = parseCronInterval(job.schedule)

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true) // 电量不低时执行
                .build()

            val workRequest = PeriodicWorkRequestBuilder<CronWorker>(
                interval.toMillis(),
                TimeUnit.MILLISECONDS
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(
                    "jobId" to job.id,
                    "command" to job.command
                ))
                .addTag("cron-job")
                .addTag(job.id)
                .build()

            workManager.enqueueUniquePeriodicWork(
                job.id,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.i(TAG, "✅ 调度任务: ${job.id} (${job.schedule})")
        } catch (e: Exception) {
            Log.e(TAG, "调度任务失败: ${job.id}", e)
        }
    }

    /**
     * 取消任务
     */
    private fun cancelJob(jobId: String) {
        workManager.cancelUniqueWork(jobId)
        Log.d(TAG, "取消任务: $jobId")
    }

    /**
     * 解析 Cron 表达式为时间间隔
     * 简化实现，支持常见模式
     */
    private fun parseCronInterval(schedule: String): Duration {
        return when (schedule) {
            // 每分钟
            "* * * * *" -> Duration.ofMinutes(15) // WorkManager 最小15分钟
            // 每小时
            "0 * * * *" -> Duration.ofHours(1)
            // 每天凌晨2点 (简化为每24小时)
            "0 2 * * *" -> Duration.ofHours(24)
            // 每周一凌晨2点 (简化为每7天)
            "0 2 * * 1" -> Duration.ofDays(7)
            // 每月1号凌晨2点 (简化为每30天)
            "0 2 1 * *" -> Duration.ofDays(30)
            // 默认：每小时
            else -> {
                Log.w(TAG, "未识别的 cron 表达式: $schedule，使用默认间隔（1小时）")
                Duration.ofHours(1)
            }
        }
    }

    /**
     * 默认任务列表
     */
    private fun getDefaultJobs(): List<CronJob> {
        return listOf(
            CronJob(
                id = "daily-cleanup",
                name = "每日清理",
                schedule = "0 2 * * *",
                command = "cleanup-logs",
                enabled = true,
                description = "每天凌晨2点清理旧日志"
            ),
            CronJob(
                id = "hourly-health-check",
                name = "健康检查",
                schedule = "0 * * * *",
                command = "health-check",
                enabled = false,
                description = "每小时检查系统健康状态"
            ),
            CronJob(
                id = "daily-backup",
                name = "每日备份",
                schedule = "0 3 * * *",
                command = "backup-workspace",
                enabled = false,
                description = "每天凌晨3点备份 workspace"
            )
        )
    }
}

/**
 * Cron Worker - 执行定时任务
 */
class CronWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "CronWorker"
    }

    override fun doWork(): Result {
        val jobId = inputData.getString("jobId") ?: return Result.failure()
        val command = inputData.getString("command") ?: return Result.failure()

        Log.i(TAG, "========================================")
        Log.i(TAG, "执行 Cron 任务: $jobId")
        Log.i(TAG, "命令: $command")
        Log.i(TAG, "========================================")

        return try {
            executeCommand(command)
            updateLastRun(jobId)
            Log.i(TAG, "✅ 任务执行成功: $jobId")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 任务执行失败: $jobId", e)
            Result.failure()
        }
    }

    private fun executeCommand(command: String) {
        when (command) {
            "cleanup-logs" -> cleanupLogs()
            "health-check" -> healthCheck()
            "backup-workspace" -> backupWorkspace()
            "check-updates" -> checkUpdates()
            "check-accessibility" -> checkAccessibility()
            else -> Log.w(TAG, "未知命令: $command")
        }
    }

    private fun cleanupLogs() {
        // 清理超过7天的日志
        val logsDir = File("/sdcard/.androidforclaw/logs")
        val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

        logsDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime && file.name.contains("-20")) {
                file.delete()
                Log.i(TAG, "删除旧日志: ${file.name}")
            }
        }
    }

    private fun healthCheck() {
        // 检查系统健康状态
        val stats = mapOf(
            "accessibility_service" to isAccessibilityServiceRunning(),
            "gateway_running" to isGatewayRunning(),
            "disk_space" to getDiskSpace()
        )

        Log.i(TAG, "健康检查结果: $stats")
    }

    private fun backupWorkspace() {
        // 备份 workspace
        val workspaceDir = File("/sdcard/.androidforclaw/workspace")
        val backupDir = File("/sdcard/.androidforclaw/backups/workspace-backup-${System.currentTimeMillis()}")

        if (workspaceDir.exists()) {
            workspaceDir.copyRecursively(backupDir)
            Log.i(TAG, "Workspace 已备份到: ${backupDir.absolutePath}")
        }
    }

    private fun checkUpdates() {
        // 检查应用更新（占位）
        Log.i(TAG, "检查更新...")
    }

    private fun checkAccessibility() {
        // 检查 AccessibilityService 状态
        val isRunning = isAccessibilityServiceRunning()
        Log.i(TAG, "AccessibilityService 状态: ${if (isRunning) "运行中" else "未运行"}")
    }

    private fun isAccessibilityServiceRunning(): Boolean {
        // 简化实现
        return true
    }

    private fun isGatewayRunning(): Boolean {
        // 简化实现
        return true
    }

    private fun getDiskSpace(): String {
        val file = File("/sdcard")
        val freeSpace = file.freeSpace / (1024 * 1024) // MB
        return "${freeSpace}MB"
    }

    private fun updateLastRun(jobId: String) {
        // 更新任务的最后运行时间
        val cronManager = CronJobManager(applicationContext)
        cronManager.updateJob(jobId) { job ->
            job.copy(lastRun = java.time.Instant.now().toString())
        }
    }
}

/**
 * Cron Jobs 配置
 */
data class CronJobsConfig(
    val version: Int,
    val jobs: List<CronJob>
)

/**
 * Cron Job 定义
 */
data class CronJob(
    val id: String,
    val name: String = "",
    val schedule: String,           // Cron 表达式
    val command: String,            // 要执行的命令
    val enabled: Boolean = true,
    val lastRun: String? = null,    // 最后运行时间
    val description: String = ""
)

/**
 * 任务状态
 */
enum class JobStatus {
    NOT_SCHEDULED,
    SCHEDULED,
    RUNNING,
    COMPLETED,
    FAILED,
    BLOCKED,
    CANCELLED
}
