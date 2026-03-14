package com.xiaomo.androidforclaw.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ADB Self-Control Skill
 *
 * 给开发电脑使用，通过 ADB 远程调用 PhoneForClaw 的 Self-Control 功能。
 *
 * 这个 Skill 封装了 ADB 命令，让开发者可以从电脑上远程控制 PhoneForClaw。
 *
 * 使用场景：
 * - CI/CD 自动化测试
 * - 远程调试和配置
 * - 开发过程中的快速测试
 * - 多设备批量控制
 *
 * 注意：
 * - 这个 Skill 运行在开发电脑上（不是 Android 设备上）
 * - 需要安装 ADB（Android Debug Bridge）
 * - 需要通过 USB 连接或网络 ADB 连接设备
 *
 * 示例（Python/Shell）：
 * ```python
 * # 使用 Python
 * import subprocess
 *
 * def adb_self_control(skill, **kwargs):
 *     args = " ".join([f"--extra {k}:s:{v}" for k, v in kwargs.items()])
 *     cmd = f'adb shell content call --uri content://com.xiaomo.androidforclaw.selfcontrol/execute --method {skill} {args}'
 *     result = subprocess.check_output(cmd, shell=True)
 *     return result.decode()
 *
 * # 页面导航
 * adb_self_control("navigate_app", page="config")
 *
 * # 配置管理
 * adb_self_control("manage_config", operation="get", key="exploration_mode")
 * ```
 *
 * ```bash
 * # 使用 Shell 脚本
 * ./self-control-adb.sh navigate_app page=config
 * ./self-control-adb.sh manage_config operation=get key=exploration_mode
 * ```
 */
class ADBSelfControlSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "ADBSelfControlSkill"

        /**
         * 生成 ADB 命令字符串
         */
        fun generateADBCommand(skill: String, args: Map<String, Any?>): String {
            val extras = args.map { (key, value) ->
                when (value) {
                    is Int -> "--extra $key:i:$value"
                    is Long -> "--extra $key:l:$value"
                    is Boolean -> "--extra $key:b:$value"
                    is Float -> "--extra $key:f:$value"
                    is Double -> "--extra $key:d:$value"
                    else -> "--extra $key:s:$value"
                }
            }.joinToString(" ")

            return "adb shell content call " +
                    "--uri content://com.xiaomo.androidforclaw.selfcontrol/execute " +
                    "--method $skill " +
                    extras
        }

        /**
         * 生成 Broadcast 命令字符串
         */
        fun generateBroadcastCommand(skill: String, args: Map<String, Any?>): String {
            val extras = args.map { (key, value) ->
                when (value) {
                    is Int -> "--ei $key $value"
                    is Long -> "--el $key $value"
                    is Boolean -> "--ez $key $value"
                    is Float -> "--ef $key $value"
                    is Double -> "--ed $key $value"
                    else -> "--es $key $value"
                }
            }.joinToString(" ")

            return "adb shell am broadcast " +
                    "-a com.xiaomo.androidforclaw.SELF_CONTROL " +
                    "--es skill $skill " +
                    extras
        }

        /**
         * 生成辅助脚本命令字符串
         */
        fun generateScriptCommand(skill: String, args: Map<String, Any?>): String {
            val params = args.map { (key, value) ->
                when (value) {
                    is Int -> "$key:i=$value"
                    is Long -> "$key:l=$value"
                    is Boolean -> "$key:b=$value"
                    is Float -> "$key:f=$value"
                    is Double -> "$key:d=$value"
                    else -> "$key=$value"
                }
            }.joinToString(" ")

            return "./self-control-adb.sh $skill $params"
        }
    }

    override val name = "adb_self_control"

    override val description = """
        通过 ADB 远程调用 PhoneForClaw 的 Self-Control 功能（开发电脑使用）。

        此 Skill 生成 ADB 命令，用于从开发电脑远程控制 Android 设备上的 PhoneForClaw。

        支持 3 种命令格式：
        - ContentProvider (推荐)：返回结构化数据
        - Broadcast：兼容性好，异步执行
        - Shell Script：封装简化，易于使用

        使用场景：
        - CI/CD 自动化测试
        - 远程调试和配置
        - 快速测试和验证
        - 多设备批量控制

        示例：
        ```
        # 获取 ContentProvider 命令
        {
          "method": "provider",
          "skill": "navigate_app",
          "args": {"page": "config"}
        }
        返回：adb shell content call --uri ... --method navigate_app --extra page:s:config

        # 获取 Broadcast 命令
        {
          "method": "broadcast",
          "skill": "manage_config",
          "args": {"operation": "get", "key": "exploration_mode"}
        }
        返回：adb shell am broadcast -a ... --es skill manage_config --es operation get --es key exploration_mode

        # 获取脚本命令
        {
          "method": "script",
          "skill": "control_service",
          "args": {"operation": "hide_float"}
        }
        返回：./self-control-adb.sh control_service operation=hide_float
        ```

        注意：
        - 此 Skill 仅生成命令字符串，不实际执行
        - 需要在开发电脑上运行生成的命令
        - 设备需要通过 USB 或网络 ADB 连接
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
                        "method" to PropertySchema(
                            type = "string",
                            description = "ADB 调用方法",
                            enum = listOf("provider", "broadcast", "script")
                        ),
                        "skill" to PropertySchema(
                            type = "string",
                            description = "目标 Skill 名称"
                        ),
                        "args" to PropertySchema(
                            type = "object",
                            description = "Skill 参数（JSON 对象）"
                        )
                    ),
                    required = listOf("method", "skill")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val method = args["method"] as? String
            ?: return SkillResult.error("Missing required parameter: method")

        val skill = args["skill"] as? String
            ?: return SkillResult.error("Missing required parameter: skill")

        val skillArgs = (args["args"] as? Map<*, *>)?.mapKeys { it.key.toString() }
            ?: emptyMap()

        return try {
            val command = when (method) {
                "provider" -> generateADBCommand(skill, skillArgs)
                "broadcast" -> generateBroadcastCommand(skill, skillArgs)
                "script" -> generateScriptCommand(skill, skillArgs)
                else -> return SkillResult.error("Unknown method: $method. Use: provider, broadcast, script")
            }

            Log.d(TAG, "Generated ADB command: $command")

            val output = buildString {
                appendLine("【ADB Self-Control Command】")
                appendLine()
                appendLine("Method: $method")
                appendLine("Skill: $skill")
                appendLine("Args: $skillArgs")
                appendLine()
                appendLine("Command:")
                appendLine(command)
                appendLine()
                appendLine("📋 Copy and run this command on your development machine")
                appendLine()
                appendLine("Alternative methods:")
                when (method) {
                    "provider" -> {
                        appendLine("• Broadcast: ${generateBroadcastCommand(skill, skillArgs)}")
                        appendLine("• Script: ${generateScriptCommand(skill, skillArgs)}")
                    }
                    "broadcast" -> {
                        appendLine("• Provider: ${generateADBCommand(skill, skillArgs)}")
                        appendLine("• Script: ${generateScriptCommand(skill, skillArgs)}")
                    }
                    "script" -> {
                        appendLine("• Provider: ${generateADBCommand(skill, skillArgs)}")
                        appendLine("• Broadcast: ${generateBroadcastCommand(skill, skillArgs)}")
                    }
                }
            }

            SkillResult.success(
                output,
                mapOf(
                    "command" to command,
                    "method" to method,
                    "skill" to skill,
                    "args" to skillArgs
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate ADB command", e)
            SkillResult.error("Failed to generate command: ${e.message}")
        }
    }
}
