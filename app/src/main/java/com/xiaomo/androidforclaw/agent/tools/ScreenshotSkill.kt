package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.DeviceController
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import kotlinx.coroutines.delay

/**
 * Screenshot Skill
 * Capture current screen + UI tree (complete information)
 *
 * Note: This tool has high overhead (requires screenshot + UI tree), please use get_view_tree first.
 * Only use in the following cases:
 * - Need to view visual information (colors, icons, images)
 * - Operation failed and needs visual confirmation
 * - UI tree information is insufficient
 */
class ScreenshotSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "ScreenshotSkill"
    }

    override val name = "screenshot"
    override val description = "Capture screen image with UI tree (prefer get_view_tree for most cases)"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = emptyMap(),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        Log.d(TAG, "Taking screenshot with UI tree...")

        // Screenshot function is always enabled, controlled by MediaProjection permission

        return try {
            // 1. Get UI tree (always enabled)
            val (originalNodes, processedNodes) = run {
                val result = DeviceController.detectIcons(context)
                if (result == null) {
                    Log.w(TAG, "无法获取 UI 树（无障碍服务未启用或失败），继续截图")
                    Pair(emptyList(), emptyList())
                } else {
                    result
                }
            }
            Log.d(TAG, "UI tree captured: ${processedNodes.size} nodes")

            // 2. Brief delay to ensure UI stability
            // ⚡ Optimization: reduce to 50ms
            delay(50)

            // 3. Take screenshot
            val screenshotResult = DeviceController.getScreenshot(context)
            if (screenshotResult == null) {
                return SkillResult.error("Screenshot failed: result is null")
            }

            val (bitmap, path) = screenshotResult
            Log.d(TAG, "Screenshot captured: ${bitmap.width}x${bitmap.height}, path: $path")

            // 4. Combine output
            val output = buildString {
                appendLine("【截图信息】")
                appendLine("分辨率: ${bitmap.width}x${bitmap.height}")
                appendLine("路径: $path")
                appendLine()

                appendLine("【屏幕 UI 元素】（共 ${processedNodes.size} 个）")
                appendLine()

                processedNodes.forEachIndexed { index, node ->
                    val text = node.text?.takeIf { it.isNotBlank() }
                        ?: node.contentDesc?.takeIf { it.isNotBlank() }
                        ?: "[无文本]"

                    append("[$index] \"$text\" (${node.point.x}, ${node.point.y})")

                    if (node.clickable) {
                        append(" [可点击]")
                    }

                    appendLine()
                }

                appendLine()
                appendLine("提示：使用坐标 (x,y) 进行 tap 操作")
            }

            SkillResult.success(
                output,
                mapOf(
                    "screenshot_path" to path,
                    "width" to bitmap.width,
                    "height" to bitmap.height,
                    "view_count" to processedNodes.size,
                    "original_count" to originalNodes.size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot with UI tree failed", e)
            SkillResult.error("Screenshot with UI tree failed: ${e.message}")
        }
    }
}
