package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manages free API usage limits based on a cumulative waiting period. */
class FreeUsagePreferences(private val context: Context) {
    private val PREFS_NAME = "free_usage_preferences"
    private val KEY_TOTAL_USAGE_COUNT = "total_usage_count"
    private val KEY_NEXT_AVAILABLE_DATE = "next_available_date"

    // 外部文件存储相关
    private val EXTERNAL_VERIFY_FILENAME = "usage_verification.dat"
    private val EXTERNAL_DIRECTORY = "Operit"

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _nextAvailableDateFlow = MutableStateFlow(getNextAvailableDate())
    val nextAvailableDateFlow: StateFlow<LocalDate> = _nextAvailableDateFlow.asStateFlow()

    init {
        // 初始化时检查外部验证文件，确保状态一致性
        checkExternalVerification()
    }

    /** 获取下次可用日期 */
    private fun getNextAvailableDate(): LocalDate {
        val dateStr = prefs.getString(KEY_NEXT_AVAILABLE_DATE, "") ?: ""
        return if (dateStr.isNotBlank()) {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
            // 默认是今天，意味着新用户可以直接使用
            LocalDate.now()
        }
    }

    /** 检查外部验证文件与SharedPreferences是否一致 */
    private fun checkExternalVerification() {
        try {
            val externalFile = getExternalVerificationFile()

            if (externalFile.exists()) {
                val externalData = FileInputStream(externalFile).use { it.readBytes() }
                val totalUsage = prefs.getInt(KEY_TOTAL_USAGE_COUNT, 0)
                val nextDate = prefs.getString(KEY_NEXT_AVAILABLE_DATE, "") ?: ""

                val expectedHash = generateVerificationHash(totalUsage, nextDate)
                val actualHash = externalData.toString(Charsets.UTF_8)

                // 如果哈希不匹配，可能是用户清除了应用数据，进行恢复
                if (actualHash != expectedHash) {
                    val parts = actualHash.split("|")
                    if (parts.size >= 2) {
                        val extTotalUsage = parts[0].toIntOrNull() ?: 0
                        val extNextDate = parts[1]

                        val currentTotalUsage = prefs.getInt(KEY_TOTAL_USAGE_COUNT, 0)
                        val updatedTotalUsage = maxOf(extTotalUsage, currentTotalUsage)

                        prefs.edit()
                                .putInt(KEY_TOTAL_USAGE_COUNT, updatedTotalUsage)
                                .putString(KEY_NEXT_AVAILABLE_DATE, extNextDate)
                                .apply()

                        _nextAvailableDateFlow.value = getNextAvailableDate()
                    }
                }
            } else {
                updateExternalVerificationFile()
            }
        } catch (e: Exception) {
            try {
                updateExternalVerificationFile()
            } catch (ex: Exception) {
                // Ignore errors
            }
        }
    }

    /** 获取外部验证文件 */
    private fun getExternalVerificationFile(): File {
        val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, EXTERNAL_DIRECTORY)
        if (!operitDir.exists()) {
            operitDir.mkdirs()
        }
        return File(operitDir, EXTERNAL_VERIFY_FILENAME)
    }

    /** 更新外部验证文件 */
    private fun updateExternalVerificationFile() {
        try {
            val totalUsage = prefs.getInt(KEY_TOTAL_USAGE_COUNT, 0)
            val nextDate = prefs.getString(KEY_NEXT_AVAILABLE_DATE, "") ?: ""

            val hash = generateVerificationHash(totalUsage, nextDate)

            val externalFile = getExternalVerificationFile()
            FileOutputStream(externalFile).use {
                it.write(hash.toByteArray(Charsets.UTF_8))
                it.flush()
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    /** 生成验证哈希值 */
    private fun generateVerificationHash(totalUsage: Int, nextDate: String): String {
        return "$totalUsage|$nextDate"
    }

    /** Check if the user can use the free API. */
    fun canUseFreeTier(): Boolean {
        val today = LocalDate.now()
        val nextAvailableDate = getNextAvailableDate()
        // 如果今天在下次可用日期之前，则不能使用
        return !today.isBefore(nextAvailableDate)
    }

    /**
     * Records a usage of the free API and calculates the next available date.
     */
    fun recordUsage() {
        val totalUsageCount = prefs.getInt(KEY_TOTAL_USAGE_COUNT, 0) + 1

        // 计算下次可用日期（指数退避）
        // 第1次使用后等1天, 第2次等2天, 第3次等4天...
        val waitDays = 1L shl (totalUsageCount - 1)
        val nextAvailableDate = LocalDate.now().plusDays(waitDays)

        // Update preferences
        prefs.edit()
                .putInt(KEY_TOTAL_USAGE_COUNT, totalUsageCount)
                .putString(KEY_NEXT_AVAILABLE_DATE, nextAvailableDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .apply()

        // 更新外部验证文件和Flow
        updateExternalVerificationFile()
        _nextAvailableDateFlow.value = nextAvailableDate
    }

    /** 获取下次可使用日期 */
    fun getNextAvailableDateString(): String {
        val nextDate = getNextAvailableDate()
        return nextDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /** 重置使用记录（仅用于调试） */
    fun resetUsage() {
        prefs.edit()
                .putInt(KEY_TOTAL_USAGE_COUNT, 0)
                .putString(KEY_NEXT_AVAILABLE_DATE, "")
                .apply()

        // 更新外部验证文件和Flow
        updateExternalVerificationFile()
        _nextAvailableDateFlow.value = LocalDate.now()
    }

    /** 获取等待天数 */
    fun getWaitDays(): Int {
        val today = LocalDate.now()
        val nextAvailable = getNextAvailableDate()

        if (!today.isBefore(nextAvailable)) {
            return 0
        }

        return today.until(nextAvailable).days
    }
}
