package com.xiaomo.feishu.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel tool definitions.
 */


import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.bitable.FeishuBitableTools
import com.xiaomo.feishu.tools.chat.FeishuChatTools
import com.xiaomo.feishu.tools.doc.FeishuDocTools
import com.xiaomo.feishu.tools.drive.FeishuDriveTools
import com.xiaomo.feishu.tools.media.FeishuMediaTools
import com.xiaomo.feishu.tools.perm.FeishuPermTools
import com.xiaomo.feishu.tools.task.FeishuTaskTools
import com.xiaomo.feishu.tools.urgent.FeishuUrgentTools
import com.xiaomo.feishu.tools.wiki.FeishuWikiTools

/**
 * 飞书工具注册中心
 * 统一管理所有工具集
 */
class FeishuToolRegistry(
    private val config: FeishuConfig,
    private val client: FeishuClient
) {
    private val docTools = FeishuDocTools(config, client)
    private val wikiTools = FeishuWikiTools(config, client)
    private val driveTools = FeishuDriveTools(config, client)
    private val bitableTools = FeishuBitableTools(config, client)
    private val taskTools = FeishuTaskTools(config, client)
    private val chatTools = FeishuChatTools(config, client)
    private val permTools = FeishuPermTools(config, client)
    private val urgentTools = FeishuUrgentTools(config, client)
    private val mediaTools = FeishuMediaTools(config, client)

    /**
     * 获取所有工具
     */
    fun getAllTools(): List<FeishuToolBase> {
        return buildList {
            addAll(docTools.getAllTools())
            addAll(wikiTools.getAllTools())
            addAll(driveTools.getAllTools())
            addAll(bitableTools.getAllTools())
            addAll(taskTools.getAllTools())
            addAll(chatTools.getAllTools())
            addAll(permTools.getAllTools())
            addAll(urgentTools.getAllTools())
            addAll(mediaTools.getAllTools())
        }
    }

    /**
     * 获取所有启用的工具定义（用于 LLM）
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools()
            .filter { it.isEnabled() }
            .map { it.getToolDefinition() }
    }

    /**
     * 根据名称获取工具
     */
    fun getTool(name: String): FeishuToolBase? {
        return getAllTools().find { it.name == name }
    }

    /**
     * 执行工具
     */
    suspend fun execute(name: String, args: Map<String, Any?>): ToolResult {
        val tool = getTool(name)
            ?: return ToolResult.error("Tool not found: $name")

        if (!tool.isEnabled()) {
            return ToolResult.error("Tool is disabled: $name")
        }

        return tool.execute(args)
    }

    /**
     * 获取工具统计
     */
    fun getStats(): ToolStats {
        val allTools = getAllTools()
        val enabledTools = allTools.filter { it.isEnabled() }

        return ToolStats(
            totalTools = allTools.size,
            enabledTools = enabledTools.size,
            toolsByCategory = mapOf(
                "doc" to docTools.getAllTools().size,
                "wiki" to wikiTools.getAllTools().size,
                "drive" to driveTools.getAllTools().size,
                "bitable" to bitableTools.getAllTools().size,
                "task" to taskTools.getAllTools().size,
                "chat" to chatTools.getAllTools().size,
                "perm" to permTools.getAllTools().size,
                "urgent" to urgentTools.getAllTools().size,
                "media" to mediaTools.getAllTools().size
            )
        )
    }
}

/**
 * 工具统计
 */
data class ToolStats(
    val totalTools: Int,
    val enabledTools: Int,
    val toolsByCategory: Map<String, Int>
)
