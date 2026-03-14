package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: agent tool implementation.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import com.xiaomo.androidforclaw.service.AdbIMEManager

/**
 * AdbIME 输入法工具
 * 通过内置 AdbIME 输入法发送文本
 *
 * 工作原理:
 * 1. 用户先用 tap() 点击输入框，让其获得焦点
 * 2. 如果 AdbIME 已启用，键盘会自动弹出
 * 3. 调用此工具通过 AdbIMEManager 直接调用 AdbIME 的方法
 * 4. AdbIME 将文本输入到焦点输入框
 */
class AdbImeInputSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "AdbImeInputSkill"
    }

    override val name = "adb_ime_input"
    override val description: String
        get() {
            val isEnabled = AdbIMEManager.isAdbImeEnabled(context)
            val isConnected = AdbIMEManager.isConnected()
            val statusNote = when {
                !isEnabled -> " ⚠️ **不可用** - AdbIME 输入法未启用"
                !isConnected -> " ⚠️ **不可用** - AdbIME 未连接 (键盘未弹出)"
                else -> " ✅ AdbIME 已就绪"
            }
            return "Input text via AdbIME (supports all characters including Chinese)$statusNote"
        }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "text" to PropertySchema("string", "要输入的文本内容"),
                        "action" to PropertySchema(
                            "string",
                            "操作类型: 'input'(输入文本,默认) | 'send'(输入后发送) | 'clear'(清空输入框)",
                            enum = listOf("input", "send", "clear")
                        )
                    ),
                    required = listOf("text")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val text = args["text"] as? String
        val action = args["action"] as? String ?: "input"

        if (text == null && action != "clear") {
            return SkillResult.error("Missing required parameter: text")
        }

        // 检查 AdbIME 是否启用
        if (!AdbIMEManager.isAdbImeEnabled(context)) {
            return SkillResult.error("AdbIME 输入法未启用。请先切换到 AdbIME 输入法")
        }

        // 检查 AdbIME 是否已连接
        if (!AdbIMEManager.isConnected()) {
            return SkillResult.error("AdbIME 未连接。请确保已点击输入框并弹出键盘")
        }

        return try {
            when (action) {
                "clear" -> {
                    // 清空输入框
                    val success = AdbIMEManager.clearText()
                    if (success) {
                        kotlinx.coroutines.delay(100)
                        SkillResult.success("已清空输入框")
                    } else {
                        SkillResult.error("清空输入框失败")
                    }
                }
                "send" -> {
                    // 输入文本并发送
                    val inputSuccess = AdbIMEManager.inputText(text!!)
                    if (!inputSuccess) {
                        return SkillResult.error("输入文本失败")
                    }
                    kotlinx.coroutines.delay(200)

                    val sendSuccess = AdbIMEManager.sendMessage()
                    if (!sendSuccess) {
                        return SkillResult.error("发送消息失败")
                    }
                    kotlinx.coroutines.delay(100)

                    SkillResult.success(
                        "已输入并发送: $text (${text.length} chars)",
                        mapOf(
                            "text" to text,
                            "length" to text.length,
                            "action" to "send"
                        )
                    )
                }
                else -> {
                    // 仅输入文本
                    val success = AdbIMEManager.inputText(text!!)
                    if (!success) {
                        return SkillResult.error("输入文本失败")
                    }

                    val waitTime = 100L + (text.length * 5L).coerceAtMost(300L)
                    kotlinx.coroutines.delay(waitTime)

                    SkillResult.success(
                        "已输入: $text (${text.length} chars)",
                        mapOf(
                            "text" to text,
                            "length" to text.length,
                            "wait_time_ms" to waitTime
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AdbIME input failed", e)
            SkillResult.error("输入失败: ${e.message}")
        }
    }

}
