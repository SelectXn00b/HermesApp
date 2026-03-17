package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.DeviceController
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.accessibility.AccessibilityProxy

/**
 * Get View Tree Skill
 * Get current screen UI tree structure (processed clean version)
 *
 * Prefer using this tool to understand interface - it's lighter and faster than screenshot.
 * Only use screenshot when visual information is needed or operation fails.
 */
class GetViewTreeSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "GetViewTreeSkill"
    }

    override val name = "get_view_tree"
    override val description = "Get screen UI tree with element positions (preferred for screen operations)"

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
        Log.d(TAG, "Getting view tree (processed)...")
        return try {
            if (!AccessibilityProxy.isServiceReady()) {
                return SkillResult.error("Accessibility service not ready")
            }

            // Get original UI tree and processed UI tree
            val iconResult = DeviceController.detectIcons(context)
            if (iconResult == null) {
                return SkillResult.error("无法获取 UI 树。请检查：\n1. 无障碍服务是否已启用\n2. 当前应用是否允许访问")
            }
            val (originalNodes, processedNodes) = iconResult

            Log.d(TAG, "Original nodes: ${originalNodes.size}, Processed nodes: ${processedNodes.size}")

            // Use processed nodes (deduplicated, empty removed)
            val uiInfo = buildString {
                appendLine("【屏幕 UI 元素列表】（共 ${processedNodes.size} 个可用元素）")
                appendLine()

                processedNodes.forEachIndexed { index, node ->
                    appendLine("[$index] ${formatNode(node)}")
                }

                appendLine()
                appendLine("提示：使用元素的坐标 (x,y) 进行 tap 操作")
            }

            SkillResult.success(
                uiInfo,
                mapOf(
                    "view_count" to processedNodes.size,
                    "original_count" to originalNodes.size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get view tree failed", e)
            SkillResult.error("Get view tree failed: ${e.message}")
        }
    }

    /**
     * Format single node information
     */
    private fun formatNode(node: com.xiaomo.androidforclaw.accessibility.service.ViewNode): String {
        return buildString {
            // Type (simplified)
            val simpleClass = node.className?.substringAfterLast('.') ?: "View"
            append("<$simpleClass>")

            // Text content
            val text = node.text?.takeIf { it.isNotBlank() }
            val desc = node.contentDesc?.takeIf { it.isNotBlank() }
            if (text != null) {
                append(" text=\"$text\"")
            }
            if (desc != null && desc != text) {
                append(" desc=\"$desc\"")
            }

            // Resource ID (very useful for buttons without text)
            val resId = node.resourceId?.takeIf { it.isNotBlank() }
            if (resId != null) {
                append(" id=$resId")
            }

            // Coordinates + bounds
            append(" center=(${node.point.x},${node.point.y})")
            append(" bounds=[${node.left},${node.top},${node.right},${node.bottom}]")

            // Clickable / scrollable state
            if (node.clickable) append(" [可点击]")
            if (node.scrollable) append(" [可滚动]")
        }
    }
}
