package com.xiaomo.hermes.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


import android.content.Context
import android.content.Intent
import android.util.Log
import com.xiaomo.hermes.selfcontrol.Skill
import com.xiaomo.hermes.selfcontrol.SkillResult
import com.xiaomo.hermes.selfcontrol.FunctionDefinition
import com.xiaomo.hermes.selfcontrol.ParametersSchema
import com.xiaomo.hermes.selfcontrol.PropertySchema
import com.xiaomo.hermes.selfcontrol.ToolDefinition

/**
 * Self-Control Navigation Skill
 *
 * 暴露 PhoneForClaw 自身的页面导航功能，让 AI Agent 能够：
 * - 跳转到各个配置页面
 * - 打开功能设置界面
 * - 访问日志和历史记录
 * - 管理 Channels 和会话
 *
 * 使用场景：
 * - AI 自我开发迭代（修改配置、查看日志）
 * - 远程配置和管理
 * - 自动化测试和调试
 */
class NavigationSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "NavigationSkill"

        // 可用的页面目标
        object Pages {
            const val MAIN = "main"                    // 主界面
            const val CONFIG = "config"                // 配置页面
            const val PERMISSIONS = "permissions"      // 权限管理
            const val CHAT_HISTORY = "chat_history"    // 对话历史
            const val CHAT_LOG = "chat_log"            // 对话日志
            const val FEISHU_CHANNEL = "feishu"        // 飞书通道
            const val CHANNEL_LIST = "channels"        // 通道列表
            const val RESULT = "result"                // 结果页面
        }
    }

    override val name = "navigate_app"

    override val description = """
        导航到 PhoneForClaw 应用内的各个页面。

        可用页面：
        - main: 主界面
        - config: 配置页面（API、模型设置）
        - permissions: 权限管理页面
        - chat_history: 对话历史记录
        - chat_log: 详细对话日志
        - feishu: 飞书通道配置
        - channels: 通道列表管理
        - result: 结果展示页面

        使用场景：
        - 修改应用配置
        - 检查权限状态
        - 查看运行日志
        - 管理通道连接

        注意：需要应用在前台或有悬浮窗权限。
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
                        "page" to PropertySchema(
                            type = "string",
                            description = "目标页面名称",
                            enum = listOf(
                                Pages.MAIN,
                                Pages.CONFIG,
                                Pages.PERMISSIONS,
                                Pages.CHAT_HISTORY,
                                Pages.CHAT_LOG,
                                Pages.FEISHU_CHANNEL,
                                Pages.CHANNEL_LIST,
                                Pages.RESULT
                            )
                        ),
                        "extras" to PropertySchema(
                            type = "object",
                            description = "可选的 Intent extras（JSON 对象）"
                        )
                    ),
                    required = listOf("page")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val page = args["page"] as? String
            ?: return SkillResult.error("Missing required parameter: page")

        val extras = args["extras"] as? Map<String, Any?>

        return try {
            val intent = createIntentForPage(page)
                ?: return SkillResult.error("Unknown page: $page")

            // 添加额外参数
            extras?.forEach { (key, value) ->
                when (value) {
                    is String -> intent.putExtra(key, value)
                    is Int -> intent.putExtra(key, value)
                    is Long -> intent.putExtra(key, value)
                    is Boolean -> intent.putExtra(key, value)
                    is Float -> intent.putExtra(key, value)
                    is Double -> intent.putExtra(key, value)
                }
            }

            // 启动 Activity
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "Successfully navigated to page: $page")

            SkillResult.success(
                "已跳转到页面: $page",
                mapOf(
                    "page" to page,
                    "extras" to (extras ?: emptyMap<String, Any>())
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to page: $page", e)
            SkillResult.error("页面跳转失败: ${e.message}")
        }
    }

    private fun createIntentForPage(page: String): Intent? {
        val packageName = context.packageName
        val className = when (page) {
            Pages.MAIN -> "$packageName.ui.activity.MainActivity"
            Pages.CONFIG -> "$packageName.ui.activity.ConfigActivity"
            Pages.PERMISSIONS -> "$packageName.ui.activity.PermissionsActivity"
            Pages.CHAT_HISTORY -> "$packageName.ui.activity.ChatHistoryActivity"
            Pages.CHAT_LOG -> "$packageName.ui.activity.ChatLogActivity"
            Pages.FEISHU_CHANNEL -> "$packageName.ui.activity.FeishuChannelActivity"
            Pages.CHANNEL_LIST -> "$packageName.ui.activity.ChannelListActivity"
            Pages.RESULT -> "$packageName.ui.activity.ResultActivity"
            else -> return null
        }

        return Intent().apply {
            setClassName(packageName, className)
        }
    }
}
