package com.xiaomo.hermes.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

/**
 * Self-Control Content Provider
 *
 * 通过 ContentProvider 暴露 Self-Control 能力给外部调用（如 ADB）。
 *
 * 使用方式：
 *
 * 1. 通过 ADB 调用 (使用 content:// URI)
 * ```bash
 * # 页面导航
 * adb shell content call --uri content://com.xiaomo.hermes.selfcontrol/execute \
 *   --method navigate_app \
 *   --extra page:s:config
 *
 * # 配置管理
 * adb shell content call --uri content://com.xiaomo.hermes.selfcontrol/execute \
 *   --method manage_config \
 *   --extra operation:s:get \
 *   --extra key:s:exploration_mode
 *
 * # 服务控制
 * adb shell content call --uri content://com.xiaomo.hermes.selfcontrol/execute \
 *   --method control_service \
 *   --extra operation:s:hide_float
 *
 * # 日志查询
 * adb shell content call --uri content://com.xiaomo.hermes.selfcontrol/execute \
 *   --method query_logs \
 *   --extra level:s:E \
 *   --extra lines:i:50
 * ```
 *
 * 2. 通过应用内部调用
 * ```kotlin
 * val registry = SelfControlRegistry(context)
 * val result = registry.execute("navigate_app", mapOf("page" to "config"))
 * ```
 *
 * 注意：
 * - 需要在 AndroidManifest.xml 中声明 provider
 * - 可以配置权限保护
 */
class SelfControlProvider : ContentProvider() {
    companion object {
        private const val TAG = "SelfControlProvider"
        const val AUTHORITY = "com.xiaomo.hermes.selfcontrol"

        private const val EXECUTE = 1
        private const val LIST_SKILLS = 2
        private const val HEALTH = 3

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "execute", EXECUTE)
            addURI(AUTHORITY, "skills", LIST_SKILLS)
            addURI(AUTHORITY, "health", HEALTH)
        }
    }

    private lateinit var registry: SelfControlRegistry
    private val gson = Gson()

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        registry = SelfControlRegistry(ctx)
        Log.d(TAG, "SelfControlProvider initialized")
        return true
    }

    /**
     * 核心方法：通过 call() 执行 Self-Control Skills
     *
     * ADB 调用格式：
     * adb shell content call --uri content://AUTHORITY/execute \
     *   --method SKILL_NAME \
     *   --extra arg1:type:value \
     *   --extra arg2:type:value
     *
     * 类型标记：
     * - s: String
     * - i: Integer
     * - l: Long
     * - b: Boolean
     * - f: Float
     * - d: Double
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        Log.d(TAG, "call() method=$method, arg=$arg, extras=$extras")

        return try {
            when (method) {
                // 执行 Skill
                in registry.getAllSkillNames() -> {
                    executeSkill(method, extras)
                }

                // 列出所有 Skills
                "list_skills" -> {
                    listSkills()
                }

                // 健康检查
                "health" -> {
                    healthCheck()
                }

                else -> {
                    errorBundle("Unknown method: $method")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "call() failed", e)
            errorBundle("Execution failed: ${e.message}")
        }
    }

    /**
     * 执行 Skill
     */
    private fun executeSkill(skillName: String, extras: Bundle?): Bundle {
        // 将 Bundle 转换为 Map
        val args = extras?.let { bundleToMap(it) } ?: emptyMap()

        Log.d(TAG, "Executing skill: $skillName with args: $args")

        // 执行 Skill（阻塞调用）
        val result = runBlocking {
            registry.execute(skillName, args)
        }

        // 构建返回 Bundle
        return Bundle().apply {
            putBoolean("success", result?.success == true)
            putString("content", result?.content ?: "")

            // 将 metadata 转换为 JSON
            if (result?.metadata?.isNotEmpty() == true) {
                putString("metadata", gson.toJson(result.metadata))
            }

            Log.d(TAG, "Skill result: success=${result?.success}, content=${result?.content}")
        }
    }

    /**
     * 列出所有可用的 Skills
     */
    private fun listSkills(): Bundle {
        val skills = registry.getAllSkillNames()
        val toolDefs = registry.getAllToolDefinitions()

        val skillsInfo = toolDefs.map { tool ->
            mapOf(
                "name" to tool.function.name,
                "description" to tool.function.description,
                "parameters" to tool.function.parameters.properties.keys
            )
        }

        return Bundle().apply {
            putBoolean("success", true)
            putStringArray("skills", skills.toTypedArray())
            putString("details", gson.toJson(skillsInfo))
            putString("summary", registry.getSummary())
        }
    }

    /**
     * 健康检查
     */
    private fun healthCheck(): Bundle {
        return Bundle().apply {
            putBoolean("success", true)
            putString("status", "healthy")
            putInt("skill_count", registry.getAllSkillNames().size)
            putString("provider_version", "1.0")
        }
    }

    /**
     * Bundle 转 Map
     */
    private fun bundleToMap(bundle: Bundle): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        bundle.keySet().forEach { key ->
            val value = bundle.get(key)
            map[key] = value
        }

        return map
    }

    /**
     * 构建错误 Bundle
     */
    private fun errorBundle(message: String): Bundle {
        return Bundle().apply {
            putBoolean("success", false)
            putString("content", "Error: $message")
        }
    }

    // ========== ContentProvider 标准方法（不使用）==========

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // 可选：通过 query 返回结构化数据
        return when (uriMatcher.match(uri)) {
            LIST_SKILLS -> {
                querySkills()
            }
            HEALTH -> {
                queryHealth()
            }
            else -> null
        }
    }

    private fun querySkills(): Cursor {
        val cursor = MatrixCursor(arrayOf("name", "description"))

        registry.getAllToolDefinitions().forEach { tool ->
            cursor.addRow(arrayOf(
                tool.function.name,
                tool.function.description
            ))
        }

        return cursor
    }

    private fun queryHealth(): Cursor {
        val cursor = MatrixCursor(arrayOf("status", "skill_count"))
        cursor.addRow(arrayOf(
            "healthy",
            registry.getAllSkillNames().size
        ))
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            LIST_SKILLS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.skills"
            HEALTH -> "vnd.android.cursor.item/vnd.$AUTHORITY.health"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
