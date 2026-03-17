package com.xiaomo.androidforclaw.agent.functions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: function/tool execution bridge.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.DeviceController
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.accessibility.service.AccessibilityBinderService
import com.google.gson.Gson
import kotlinx.coroutines.delay

/**
 * Function Executor
 *
 * Directly execute functions selected by LLM through function calling
 * No matching logic needed, LLM has already made the choice
 */
class FunctionExecutor(
    private val context: Context,
    private val taskDataManager: TaskDataManager
) {
    companion object {
        private const val TAG = "FunctionExecutor"
    }

    private val gson = Gson()

    /**
     * Execute function directly
     */
    suspend fun execute(functionName: String, argsJson: String): FunctionResult {
        Log.d(TAG, "Executing function: $functionName")
        Log.d(TAG, "Arguments: $argsJson")

        val args = try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(argsJson, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse arguments", e)
            return FunctionResult.error("Failed to parse arguments: ${e.message}")
        }

        return try {
            when (functionName) {
                // ===== Mobile Operations =====
                "screenshot" -> executeScreenshot()
                "tap" -> executeTap(args)
                "swipe" -> executeSwipe(args)
                "type" -> executeType(args)
                "long_press" -> executeLongPress(args)
                "wait" -> executeWait(args)

                // ===== Navigation =====
                "home" -> executeHome()
                "back" -> executeBack()
                "open_app" -> executeOpenApp(args)

                // ===== Verification =====
                "check_ui" -> executeCheckUI()
                "verify_goal" -> executeVerifyGoal(args)

                // ===== System =====
                "stop" -> executeStop(args)
                "log" -> executeLog(args)

                else -> FunctionResult.error("Unknown function: $functionName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Function execution failed: $functionName", e)
            FunctionResult.error("Execution failed: ${e.message}")
        }
    }

    // ===== Mobile Operation Functions =====

    private suspend fun executeScreenshot(): FunctionResult {
        Log.d(TAG, "Taking screenshot...")
        return try {
            // Use DeviceController to take screenshot
            // TODO: Need to return screenshot base64 or path
            FunctionResult.success("Screenshot captured")
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed", e)
            FunctionResult.error("Screenshot failed: ${e.message}")
        }
    }

    private suspend fun executeTap(args: Map<String, Any?>): FunctionResult {
        val x = (args["x"] as? Number)?.toInt()
        val y = (args["y"] as? Number)?.toInt()

        if (x == null || y == null) {
            return FunctionResult.error("Missing required parameters: x, y")
        }

        Log.d(TAG, "Tapping at ($x, $y)")
        return try {
            DeviceController.tap(x, y)
            FunctionResult.success("Tapped at ($x, $y)")
        } catch (e: Exception) {
            Log.e(TAG, "Tap failed", e)
            FunctionResult.error("Tap failed: ${e.message}")
        }
    }

    private suspend fun executeSwipe(args: Map<String, Any?>): FunctionResult {
        val startX = (args["startX"] as? Number)?.toInt()
        val startY = (args["startY"] as? Number)?.toInt()
        val endX = (args["endX"] as? Number)?.toInt()
        val endY = (args["endY"] as? Number)?.toInt()
        val duration = (args["duration"] as? Number)?.toLong() ?: 300L

        if (startX == null || startY == null || endX == null || endY == null) {
            return FunctionResult.error("Missing required parameters: startX, startY, endX, endY")
        }

        Log.d(TAG, "Swiping from ($startX, $startY) to ($endX, $endY)")
        return try {
            DeviceController.swipe(startX, startY, endX, endY, duration)
            FunctionResult.success("Swiped from ($startX, $startY) to ($endX, $endY)")
        } catch (e: Exception) {
            Log.e(TAG, "Swipe failed", e)
            FunctionResult.error("Swipe failed: ${e.message}")
        }
    }

    private suspend fun executeType(args: Map<String, Any?>): FunctionResult {
        val text = args["text"] as? String
        if (text == null) {
            return FunctionResult.error("Missing required parameter: text")
        }

        Log.d(TAG, "Typing text: $text")
        return try {
            DeviceController.inputText(text, context)
            FunctionResult.success("Typed text: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Type failed", e)
            FunctionResult.error("Type failed: ${e.message}")
        }
    }

    private suspend fun executeLongPress(args: Map<String, Any?>): FunctionResult {
        val x = (args["x"] as? Number)?.toInt()
        val y = (args["y"] as? Number)?.toInt()
        val duration = (args["duration"] as? Number)?.toLong() ?: 1000L

        if (x == null || y == null) {
            return FunctionResult.error("Missing required parameters: x, y")
        }

        Log.d(TAG, "Long pressing at ($x, $y) for ${duration}ms")
        return try {
            val service = AccessibilityBinderService.serviceInstance
            if (service != null) {
                val success = service.performClickAt(x.toFloat(), y.toFloat(), isLongClick = true)
                if (success) {
                    FunctionResult.success("Long pressed at ($x, $y)")
                } else {
                    FunctionResult.error("Long press gesture failed")
                }
            } else {
                FunctionResult.error("Accessibility service not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Long press failed", e)
            FunctionResult.error("Long press failed: ${e.message}")
        }
    }

    private suspend fun executeWait(args: Map<String, Any?>): FunctionResult {
        val duration = (args["duration"] as? Number)?.toLong() ?: 1000L

        Log.d(TAG, "Waiting for ${duration}ms")
        return try {
            delay(duration)
            FunctionResult.success("Waited for ${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Wait failed", e)
            FunctionResult.error("Wait failed: ${e.message}")
        }
    }

    // ===== Navigation Functions =====

    private suspend fun executeHome(): FunctionResult {
        Log.d(TAG, "Going home")
        return try {
            DeviceController.pressHome()
            FunctionResult.success("Pressed home button")
        } catch (e: Exception) {
            Log.e(TAG, "Home failed", e)
            FunctionResult.error("Home failed: ${e.message}")
        }
    }

    private suspend fun executeBack(): FunctionResult {
        Log.d(TAG, "Going back")
        return try {
            DeviceController.pressBack()
            FunctionResult.success("Pressed back button")
        } catch (e: Exception) {
            Log.e(TAG, "Back failed", e)
            FunctionResult.error("Back failed: ${e.message}")
        }
    }

    private suspend fun executeOpenApp(args: Map<String, Any?>): FunctionResult {
        val packageName = args["package_name"] as? String
        if (packageName == null) {
            return FunctionResult.error("Missing required parameter: package_name")
        }

        Log.d(TAG, "Opening app: $packageName")
        return try {
            // Use context to launch app
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                FunctionResult.success("Opened app: $packageName")
            } else {
                FunctionResult.error("App not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Open app failed", e)
            FunctionResult.error("Open app failed: ${e.message}")
        }
    }

    // ===== Verification Functions =====

    private suspend fun executeCheckUI(): FunctionResult {
        Log.d(TAG, "Checking UI")
        return try {
            // Get current UI info
            val service = AccessibilityBinderService.serviceInstance
            if (service != null) {
                val viewNodes = service.dumpView()
                val uiInfo = viewNodes.joinToString("\n") { node ->
                    "[${node.index}] ${node.className} text='${node.text}' desc='${node.contentDesc}' clickable=${node.clickable} (${node.point.x},${node.point.y})"
                }
                FunctionResult.success("UI check completed", mapOf("ui_info" to uiInfo))
            } else {
                FunctionResult.error("Accessibility service not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check UI failed", e)
            FunctionResult.error("Check UI failed: ${e.message}")
        }
    }

    private suspend fun executeVerifyGoal(args: Map<String, Any?>): FunctionResult {
        val goal = args["goal"] as? String
        if (goal == null) {
            return FunctionResult.error("Missing required parameter: goal")
        }

        Log.d(TAG, "Verifying goal: $goal")
        // Actual goal verification logic can be added here
        return FunctionResult.success("Goal verification: $goal")
    }

    // ===== System Functions =====

    private suspend fun executeStop(args: Map<String, Any?>): FunctionResult {
        val reason = args["reason"] as? String ?: "Task completed"

        Log.d(TAG, "Stopping: $reason")
        taskDataManager.getCurrentTaskData()?.setIsRunning(false)

        return FunctionResult.success(
            reason,
            mapOf("stopped" to true)
        )
    }

    private suspend fun executeLog(args: Map<String, Any?>): FunctionResult {
        val message = args["message"] as? String ?: ""
        val level = args["level"] as? String ?: "info"

        when (level.lowercase()) {
            "error" -> Log.e(TAG, "Agent Log: $message")
            "warn" -> Log.w(TAG, "Agent Log: $message")
            "debug" -> Log.d(TAG, "Agent Log: $message")
            else -> Log.i(TAG, "Agent Log: $message")
        }

        return FunctionResult.success("Logged: $message")
    }

    // ===== Function Definitions =====
    /**
     * Get all Function Definitions (Legacy LLM API format)
     */
    fun getFunctionDefinitions(): List<ToolDefinition> {
        return listOf(
            createFunctionDef(
                name = "screenshot",
                description = "截取当前屏幕，返回屏幕图片。每次操作前后都应该调用此函数观察界面。",
                properties = emptyMap()
            ),
            createFunctionDef(
                name = "tap",
                description = "点击屏幕上的坐标位置。用于点击按钮、输入框、列表项等。",
                properties = mapOf(
                    "x" to PropertySchema("integer", "X 坐标"),
                    "y" to PropertySchema("integer", "Y 坐标")
                ),
                required = listOf("x", "y")
            ),
            createFunctionDef(
                name = "swipe",
                description = "在屏幕上执行滑动手势。用于上下滚动、左右切换等。",
                properties = mapOf(
                    "startX" to PropertySchema("integer", "起始 X 坐标"),
                    "startY" to PropertySchema("integer", "起始 Y 坐标"),
                    "endX" to PropertySchema("integer", "结束 X 坐标"),
                    "endY" to PropertySchema("integer", "结束 Y 坐标"),
                    "duration" to PropertySchema("integer", "滑动时长（毫秒），默认 300")
                ),
                required = listOf("startX", "startY", "endX", "endY")
            ),
            createFunctionDef(
                name = "type",
                description = "在当前焦点输入框中输入文本。需要先 tap 输入框获得焦点。",
                properties = mapOf(
                    "text" to PropertySchema("string", "要输入的文本")
                ),
                required = listOf("text")
            ),
            createFunctionDef(
                name = "long_press",
                description = "长按屏幕上的坐标位置。用于触发长按菜单等。",
                properties = mapOf(
                    "x" to PropertySchema("integer", "X 坐标"),
                    "y" to PropertySchema("integer", "Y 坐标"),
                    "duration" to PropertySchema("integer", "长按时长（毫秒），默认 1000")
                ),
                required = listOf("x", "y")
            ),
            createFunctionDef(
                name = "wait",
                description = "等待指定时间。用于等待界面加载、动画完成等。",
                properties = mapOf(
                    "duration" to PropertySchema("integer", "等待时长（毫秒）")
                ),
                required = listOf("duration")
            ),
            createFunctionDef(
                name = "home",
                description = "按下 Home 键，返回桌面。",
                properties = emptyMap()
            ),
            createFunctionDef(
                name = "back",
                description = "按下 Back 键，返回上一页。",
                properties = emptyMap()
            ),
            createFunctionDef(
                name = "open_app",
                description = "打开指定包名的应用。",
                properties = mapOf(
                    "package_name" to PropertySchema("string", "应用包名")
                ),
                required = listOf("package_name")
            ),
            createFunctionDef(
                name = "check_ui",
                description = "检查当前 UI 状态，获取 UI 树信息。",
                properties = emptyMap()
            ),
            createFunctionDef(
                name = "verify_goal",
                description = "验证是否达成目标。",
                properties = mapOf(
                    "goal" to PropertySchema("string", "要验证的目标")
                ),
                required = listOf("goal")
            ),
            createFunctionDef(
                name = "stop",
                description = "停止任务。当任务完成或遇到无法解决的问题时调用。",
                properties = mapOf(
                    "reason" to PropertySchema("string", "停止原因")
                ),
                required = listOf("reason")
            ),
            createFunctionDef(
                name = "log",
                description = "记录日志信息。用于调试和记录重要信息。",
                properties = mapOf(
                    "message" to PropertySchema("string", "日志消息"),
                    "level" to PropertySchema("string", "日志级别: info, warn, error, debug", listOf("info", "warn", "error", "debug"))
                ),
                required = listOf("message")
            )
        )
    }

    private fun createFunctionDef(
        name: String,
        description: String,
        properties: Map<String, PropertySchema>,
        required: List<String> = emptyList()
    ): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = properties,
                    required = required
                )
            )
        )
    }
}

/**
 * Function Execution Result
 */
data class FunctionResult(
    val success: Boolean,
    val content: String,
    val metadata: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun success(content: String, metadata: Map<String, Any?> = emptyMap()) =
            FunctionResult(true, content, metadata)

        fun error(message: String) =
            FunctionResult(false, "Error: $message")
    }

    override fun toString(): String = content
}
