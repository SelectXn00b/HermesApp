package com.xiaomo.hermes.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.hermes.selfcontrol.Skill
import com.xiaomo.hermes.selfcontrol.SkillResult
import com.xiaomo.hermes.selfcontrol.FunctionDefinition
import com.xiaomo.hermes.selfcontrol.ParametersSchema
import com.xiaomo.hermes.selfcontrol.PropertySchema
import com.xiaomo.hermes.selfcontrol.ToolDefinition
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Self-Control Log Query Skill
 *
 * 查询 PhoneForClaw 的运行日志，让 AI Agent 能够：
 * - 读取应用日志（logcat）
 * - 查询错误和异常
 * - 分析运行状态
 * - 自我诊断问题
 *
 * 使用场景：
 * - 调试失败的操作
 * - 分析崩溃原因
 * - 性能监控
 * - 自动化问题诊断
 */
class LogQuerySkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "LogQuerySkill"
        private const val MAX_LOG_LINES = 200

        object LogLevel {
            const val VERBOSE = "V"
            const val DEBUG = "D"
            const val INFO = "I"
            const val WARN = "W"
            const val ERROR = "E"
            const val FATAL = "F"
        }
    }

    override val name = "query_logs"

    override val description = """
        查询 PhoneForClaw 应用的运行日志。

        支持参数：
        - level: 日志级别（V/D/I/W/E/F），默认 I（Info 及以上）
        - filter: 过滤关键字（TAG 或消息内容）
        - lines: 返回行数，默认 100（最多 200）
        - source: 日志来源（logcat/file），默认 logcat

        日志级别：
        - V: Verbose（详细）
        - D: Debug（调试）
        - I: Info（信息）
        - W: Warning（警告）
        - E: Error（错误）
        - F: Fatal（致命）

        示例：
        - 查看最近错误: {"level": "E", "lines": 50}
        - 搜索特定 TAG: {"filter": "AgentLoop", "lines": 100}
        - 查看全部日志: {"level": "V", "lines": 200}

        注意：需要 READ_LOGS 权限（System UID）。
    """.trimIndent()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "level" to PropertySchema(
                            type = "string",
                            description = "日志级别",
                            enum = listOf(
                                LogLevel.VERBOSE,
                                LogLevel.DEBUG,
                                LogLevel.INFO,
                                LogLevel.WARN,
                                LogLevel.ERROR,
                                LogLevel.FATAL
                            )
                        ),
                        "filter" to PropertySchema(
                            type = "string",
                            description = "过滤关键字（TAG 或消息内容）"
                        ),
                        "lines" to PropertySchema(
                            type = "integer",
                            description = "返回行数（1-200）"
                        ),
                        "source" to PropertySchema(
                            type = "string",
                            description = "日志来源",
                            enum = listOf("logcat", "file")
                        )
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val level = args["level"] as? String ?: LogLevel.INFO
        val filter = args["filter"] as? String
        val lines = ((args["lines"] as? Number)?.toInt() ?: 100).coerceIn(1, MAX_LOG_LINES)
        val source = args["source"] as? String ?: "logcat"

        return try {
            when (source) {
                "logcat" -> queryLogcat(level, filter, lines)
                "file" -> queryLogFile(filter, lines)
                else -> SkillResult.error("Unknown log source: $source")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query logs", e)
            SkillResult.error("日志查询失败: ${e.message}")
        }
    }

    private fun queryLogcat(level: String, filter: String?, lines: Int): SkillResult {
        return try {
            val packageName = context.packageName
            val levelFilter = when (level) {
                LogLevel.VERBOSE -> "*:V"
                LogLevel.DEBUG -> "*:D"
                LogLevel.INFO -> "*:I"
                LogLevel.WARN -> "*:W"
                LogLevel.ERROR -> "*:E"
                LogLevel.FATAL -> "*:F"
                else -> "*:I"
            }

            // 构建 logcat 命令
            val command = mutableListOf(
                "logcat",
                "-d",                           // dump 模式
                "-t", lines.toString(),         // 最后 N 行
                levelFilter,                    // 日志级别
                "--pid=${android.os.Process.myPid()}"  // 仅本进程
            )

            // 执行命令
            val process = Runtime.getRuntime().exec(command.toTypedArray())
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            val logLines = mutableListOf<String>()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue

                // 应用过滤器
                if (filter != null && !currentLine.contains(filter, ignoreCase = true)) {
                    continue
                }

                logLines.add(currentLine)
            }

            reader.close()
            process.waitFor()

            if (logLines.isEmpty()) {
                return SkillResult.success(
                    "没有找到匹配的日志${if (filter != null) "（过滤: $filter）" else ""}",
                    mapOf("count" to 0)
                )
            }

            val summary = buildString {
                appendLine("【日志查询结果】")
                appendLine("级别: $level")
                if (filter != null) {
                    appendLine("过滤: $filter")
                }
                appendLine("行数: ${logLines.size}")
                appendLine()
                appendLine("--- 日志内容 ---")
                logLines.takeLast(lines).forEach { appendLine(it) }
            }

            SkillResult.success(
                summary,
                mapOf(
                    "level" to level,
                    "filter" to (filter ?: "none"),
                    "count" to logLines.size,
                    "lines" to logLines.takeLast(lines)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query logcat", e)
            SkillResult.error("Logcat 查询失败: ${e.message}")
        }
    }

    private fun queryLogFile(filter: String?, lines: Int): SkillResult {
        return try {
            // 尝试读取应用日志文件（如果存在）
            val logDir = File(context.getExternalFilesDir(null), "logs")

            if (!logDir.exists() || !logDir.isDirectory) {
                return SkillResult.error("日志目录不存在: ${logDir.absolutePath}")
            }

            val logFiles = logDir.listFiles { file ->
                file.isFile && file.extension == "log"
            }?.sortedByDescending { it.lastModified() }

            if (logFiles.isNullOrEmpty()) {
                return SkillResult.error("没有找到日志文件")
            }

            // 读取最新的日志文件
            val latestLog = logFiles.first()
            val logLines = mutableListOf<String>()

            latestLog.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    if (filter == null || line.contains(filter, ignoreCase = true)) {
                        logLines.add(line)
                    }
                }
            }

            if (logLines.isEmpty()) {
                return SkillResult.success(
                    "日志文件中没有找到匹配内容${if (filter != null) "（过滤: $filter）" else ""}",
                    mapOf("file" to latestLog.name, "count" to 0)
                )
            }

            val summary = buildString {
                appendLine("【日志文件查询结果】")
                appendLine("文件: ${latestLog.name}")
                appendLine("大小: ${latestLog.length() / 1024} KB")
                appendLine("修改时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(latestLog.lastModified())}")
                if (filter != null) {
                    appendLine("过滤: $filter")
                }
                appendLine("匹配行数: ${logLines.size}")
                appendLine()
                appendLine("--- 日志内容（最后 $lines 行）---")
                logLines.takeLast(lines).forEach { appendLine(it) }
            }

            SkillResult.success(
                summary,
                mapOf(
                    "file" to latestLog.name,
                    "filter" to (filter ?: "none"),
                    "count" to logLines.size,
                    "lines" to logLines.takeLast(lines)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query log file", e)
            SkillResult.error("日志文件查询失败: ${e.message}")
        }
    }
}
