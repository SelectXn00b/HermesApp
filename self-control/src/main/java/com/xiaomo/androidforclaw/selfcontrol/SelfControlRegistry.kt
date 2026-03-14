package com.xiaomo.androidforclaw.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.selfcontrol.Skill
import com.xiaomo.androidforclaw.selfcontrol.SkillResult
import com.xiaomo.androidforclaw.selfcontrol.ToolDefinition

/**
 * Self-Control Skill Registry
 *
 * 集中管理 PhoneForClaw 自我控制相关的 Skills：
 * - NavigationSkill: 页面导航
 * - ConfigSkill: 配置管理
 * - ServiceControlSkill: 服务控制
 * - LogQuerySkill: 日志查询
 *
 * 使用方式：
 * ```kotlin
 * val registry = SelfControlRegistry(context)
 * val tools = registry.getAllToolDefinitions()
 * val result = registry.execute("navigate_app", mapOf("page" to "config"))
 * ```
 *
 * 集成到主应用：
 * ```kotlin
 * // 在 SkillRegistry 中注册
 * class SkillRegistry(...) {
 *     private val selfControlRegistry = SelfControlRegistry(context)
 *
 *     fun getAllToolDefinitions(): List<ToolDefinition> {
 *         return baseSkills + selfControlRegistry.getAllToolDefinitions()
 *     }
 *
 *     suspend fun execute(name: String, args: Map<String, Any?>): SkillResult {
 *         return selfControlRegistry.execute(name, args)
 *             ?: baseSkills[name]?.execute(args)
 *             ?: SkillResult.error("Unknown skill: $name")
 *     }
 * }
 * ```
 */
class SelfControlRegistry(private val context: Context) {
    companion object {
        private const val TAG = "SelfControlRegistry"
    }

    private val skills: Map<String, Skill> = mapOf(
        // 基础 Self-Control Skills
        "navigate_app" to NavigationSkill(context),
        "manage_config" to ConfigSkill(context),
        "control_service" to ServiceControlSkill(context),
        "query_logs" to LogQuerySkill(context),

        // 元级别 Skills（给 Agent 自己调用）
        "self_control" to InternalSelfControlSkill(context),

        // ADB 远程调用 Skill（给开发电脑使用）
        "adb_self_control" to ADBSelfControlSkill(context)
    )

    /**
     * 获取所有 Self-Control 工具定义
     */
    fun getAllToolDefinitions(): List<ToolDefinition> {
        return skills.values.map { it.getToolDefinition() }
    }

    /**
     * 执行指定的 Self-Control Skill
     *
     * @param name Skill 名称
     * @param args 参数 Map
     * @return SkillResult，如果 Skill 不存在返回 null
     */
    suspend fun execute(name: String, args: Map<String, Any?>): SkillResult? {
        val skill = skills[name] ?: return null

        Log.d(TAG, "Executing self-control skill: $name")

        return try {
            skill.execute(args)
        } catch (e: Exception) {
            Log.e(TAG, "Self-control skill execution failed: $name", e)
            SkillResult.error("Skill 执行失败: ${e.message}")
        }
    }

    /**
     * 检查是否包含指定的 Skill
     */
    fun contains(name: String): Boolean {
        return skills.containsKey(name)
    }

    /**
     * 获取所有 Skill 名称列表
     */
    fun getAllSkillNames(): List<String> {
        return skills.keys.toList()
    }

    /**
     * 获取 Self-Control 功能摘要（用于 system prompt）
     */
    fun getSummary(): String {
        return buildString {
            appendLine("=== Self-Control Skills ===")
            appendLine()
            appendLine("PhoneForClaw 自我控制能力（共 ${skills.size} 个）：")
            appendLine()

            skills.values.forEach { skill ->
                appendLine("- ${skill.name}: ${skill.description.lines().first()}")
            }

            appendLine()
            appendLine("使用这些工具可以让 AI Agent：")
            appendLine("1. 导航到应用内各个页面进行配置")
            appendLine("2. 读取和修改运行时配置参数")
            appendLine("3. 控制悬浮窗显示/隐藏（如截图前隐藏）")
            appendLine("4. 查询运行日志进行自我诊断")
            appendLine()
            appendLine("典型使用流程：")
            appendLine("1. navigate_app → config (打开配置页面)")
            appendLine("2. manage_config → get/set (查看/修改配置)")
            appendLine("3. control_service → hide_float (截图前隐藏悬浮窗)")
            appendLine("4. query_logs → level=E (查看错误日志)")
        }
    }
}
