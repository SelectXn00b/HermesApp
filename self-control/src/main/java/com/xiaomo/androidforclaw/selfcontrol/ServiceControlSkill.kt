package com.xiaomo.androidforclaw.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


import android.content.Context
import android.content.Intent
import android.util.Log
import com.xiaomo.androidforclaw.selfcontrol.Skill
import com.xiaomo.androidforclaw.selfcontrol.SkillResult
import com.xiaomo.androidforclaw.selfcontrol.FunctionDefinition
import com.xiaomo.androidforclaw.selfcontrol.ParametersSchema
import com.xiaomo.androidforclaw.selfcontrol.PropertySchema
import com.xiaomo.androidforclaw.selfcontrol.ToolDefinition

/**
 * Self-Control Service Management Skill
 *
 * 控制 PhoneForClaw 的服务（悬浮窗、Accessibility 等），让 AI Agent 能够：
 * - 启动/停止悬浮窗服务
 * - 控制悬浮窗显示/隐藏
 * - 检查服务运行状态
 * - 管理后台服务
 *
 * 使用场景：
 * - 截图前隐藏悬浮窗
 * - 任务完成后显示结果
 * - 自动化测试时控制 UI
 * - 远程服务管理
 */
class ServiceControlSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "ServiceControlSkill"

        object Operations {
            const val SHOW_FLOAT = "show_float"        // 显示悬浮窗
            const val HIDE_FLOAT = "hide_float"        // 隐藏悬浮窗
            const val START_FLOAT = "start_float"      // 启动悬浮窗服务
            const val STOP_FLOAT = "stop_float"        // 停止悬浮窗服务
            const val CHECK_STATUS = "check_status"    // 检查服务状态
        }
    }

    override val name = "control_service"

    override val description = """
        控制 PhoneForClaw 的服务和 UI 组件。

        支持操作：
        - show_float: 显示悬浮窗（不启动服务）
        - hide_float: 隐藏悬浮窗（不停止服务）
        - start_float: 启动悬浮窗服务
        - stop_float: 停止悬浮窗服务
        - check_status: 检查服务运行状态

        使用场景：
        - 截图前需要隐藏悬浮窗: {"operation": "hide_float"}
        - 任务完成后显示结果: {"operation": "show_float"}
        - 清理后台服务: {"operation": "stop_float"}

        注意：部分操作需要悬浮窗权限。
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
                        "operation" to PropertySchema(
                            type = "string",
                            description = "操作类型",
                            enum = listOf(
                                Operations.SHOW_FLOAT,
                                Operations.HIDE_FLOAT,
                                Operations.START_FLOAT,
                                Operations.STOP_FLOAT,
                                Operations.CHECK_STATUS
                            )
                        ),
                        "delay_ms" to PropertySchema(
                            type = "integer",
                            description = "延迟执行时间（毫秒），用于 show/hide 操作"
                        )
                    ),
                    required = listOf("operation")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val operation = args["operation"] as? String
            ?: return SkillResult.error("Missing required parameter: operation")

        val delayMs = (args["delay_ms"] as? Number)?.toLong() ?: 0L

        return try {
            when (operation) {
                Operations.SHOW_FLOAT -> handleShowFloat(delayMs)
                Operations.HIDE_FLOAT -> handleHideFloat(delayMs)
                Operations.START_FLOAT -> handleStartFloat()
                Operations.STOP_FLOAT -> handleStopFloat()
                Operations.CHECK_STATUS -> handleCheckStatus()
                else -> SkillResult.error("Unknown operation: $operation")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service control failed: $operation", e)
            SkillResult.error("服务控制失败: ${e.message}")
        }
    }

    private fun handleShowFloat(delayMs: Long): SkillResult {
        return try {
            // 通过 MyApplication 管理悬浮窗
            val appClass = Class.forName("${context.packageName}.core.MyApplication")
            val method = appClass.getDeclaredMethod(
                "manageFloatingWindow",
                Boolean::class.java,
                Long::class.java,
                String::class.java,
                Function0::class.java
            )

            method.invoke(null, true, delayMs, "SelfControl: show_float", null)

            SkillResult.success(
                "悬浮窗已显示${if (delayMs > 0) "（延迟 ${delayMs}ms）" else ""}",
                mapOf("operation" to "show_float", "delay_ms" to delayMs)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating window", e)
            SkillResult.error("显示悬浮窗失败: ${e.message}")
        }
    }

    private fun handleHideFloat(delayMs: Long): SkillResult {
        return try {
            val appClass = Class.forName("${context.packageName}.core.MyApplication")
            val method = appClass.getDeclaredMethod(
                "manageFloatingWindow",
                Boolean::class.java,
                Long::class.java,
                String::class.java,
                Function0::class.java
            )

            method.invoke(null, false, delayMs, "SelfControl: hide_float", null)

            SkillResult.success(
                "悬浮窗已隐藏${if (delayMs > 0) "（延迟 ${delayMs}ms）" else ""}",
                mapOf("operation" to "hide_float", "delay_ms" to delayMs)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide floating window", e)
            SkillResult.error("隐藏悬浮窗失败: ${e.message}")
        }
    }

    private fun handleStartFloat(): SkillResult {
        return try {
            val serviceClass = Class.forName("${context.packageName}.service.FloatingWindowService")
            val intent = Intent(context, serviceClass)
            context.startService(intent)

            SkillResult.success(
                "悬浮窗服务已启动",
                mapOf("operation" to "start_float")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start floating window service", e)
            SkillResult.error("启动悬浮窗服务失败: ${e.message}")
        }
    }

    private fun handleStopFloat(): SkillResult {
        return try {
            val serviceClass = Class.forName("${context.packageName}.service.FloatingWindowService")
            val intent = Intent(context, serviceClass)
            context.stopService(intent)

            SkillResult.success(
                "悬浮窗服务已停止",
                mapOf("operation" to "stop_float")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop floating window service", e)
            SkillResult.error("停止悬浮窗服务失败: ${e.message}")
        }
    }

    private fun handleCheckStatus(): SkillResult {
        return try {
            // 通过 ActivityManager 检查服务状态
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)

            val floatingWindowRunning = services.any {
                it.service.className.contains("FloatingWindowService")
            }

            val accessibilityRunning = services.any {
                it.service.className.contains("PhoneAccessibilityService")
            }

            val status = buildString {
                appendLine("【服务状态】")
                appendLine("悬浮窗服务: ${if (floatingWindowRunning) "运行中 ✓" else "已停止 ✗"}")
                appendLine("无障碍服务: ${if (accessibilityRunning) "运行中 ✓" else "已停止 ✗"}")
            }

            SkillResult.success(
                status,
                mapOf(
                    "floating_window" to floatingWindowRunning,
                    "accessibility" to accessibilityRunning
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check service status", e)
            SkillResult.error("检查服务状态失败: ${e.message}")
        }
    }
}
