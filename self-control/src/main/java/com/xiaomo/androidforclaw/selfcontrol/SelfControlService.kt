package com.xiaomo.androidforclaw.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

/**
 * Self-Control Service
 *
 * 提供本地 Binder 接口，供应用内部和 ADB shell 调用。
 *
 * 使用方式：
 *
 * 1. 应用内部调用
 * ```kotlin
 * val connection = object : ServiceConnection {
 *     override fun onServiceConnected(name: ComponentName, service: IBinder) {
 *         val binder = service as SelfControlService.LocalBinder
 *         val result = binder.execute("navigate_app", mapOf("page" to "config"))
 *     }
 *     override fun onServiceDisconnected(name: ComponentName) {}
 * }
 * bindService(Intent(this, SelfControlService::class.java), connection, BIND_AUTO_CREATE)
 * ```
 *
 * 2. 通过 ADB Shell（配合 app_process）
 * ```bash
 * # 需要配合自定义的 shell 工具
 * adb shell /system/bin/phoneforclaw-ctl navigate_app page=config
 * ```
 */
class SelfControlService : Service() {
    companion object {
        private const val TAG = "SelfControlService"
    }

    private lateinit var registry: SelfControlRegistry
    private val gson = Gson()
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        registry = SelfControlRegistry(applicationContext)
        Log.d(TAG, "SelfControlService created")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "SelfControlService bound")
        return binder
    }

    /**
     * Local Binder for in-process calls
     */
    inner class LocalBinder : Binder() {
        /**
         * 执行 Skill（同步）
         */
        fun execute(skillName: String, args: Map<String, Any?>): SkillResult? {
            return runBlocking {
                registry.execute(skillName, args)
            }
        }

        /**
         * 执行 Skill（异步）
         */
        suspend fun executeAsync(skillName: String, args: Map<String, Any?>): SkillResult? {
            return registry.execute(skillName, args)
        }

        /**
         * 列出所有 Skills
         */
        fun listSkills(): List<String> {
            return registry.getAllSkillNames()
        }

        /**
         * 获取 Skill 定义
         */
        fun getSkillDefinitions(): List<ToolDefinition> {
            return registry.getAllToolDefinitions()
        }

        /**
         * 检查 Skill 是否存在
         */
        fun hasSkill(skillName: String): Boolean {
            return registry.contains(skillName)
        }

        /**
         * 获取摘要信息
         */
        fun getSummary(): String {
            return registry.getSummary()
        }

        /**
         * 健康检查
         */
        fun healthCheck(): Map<String, Any> {
            return mapOf(
                "status" to "healthy",
                "skill_count" to registry.getAllSkillNames().size,
                "skills" to registry.getAllSkillNames()
            )
        }
    }

    /**
     * 处理 START 命令（可用于 adb shell am startservice）
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra("action")
        val skill = intent.getStringExtra("skill")

        Log.d(TAG, "handleIntent: action=$action, skill=$skill")

        when {
            action == "list_skills" -> {
                val skills = registry.getAllSkillNames()
                Log.i(TAG, "Available skills: $skills")
            }

            action == "health" -> {
                val health = mapOf(
                    "status" to "healthy",
                    "skill_count" to registry.getAllSkillNames().size
                )
                Log.i(TAG, "Health: ${gson.toJson(health)}")
            }

            skill != null -> {
                val args = extractArgs(intent)
                runBlocking {
                    val result = registry.execute(skill, args)
                    Log.i(TAG, "Skill result: ${result?.content}")
                }
            }
        }
    }

    private fun extractArgs(intent: Intent): Map<String, Any?> {
        val args = mutableMapOf<String, Any?>()
        val extras = intent.extras ?: return args

        extras.keySet().forEach { key ->
            if (key !in setOf("action", "skill")) {
                args[key] = extras.get(key)
            }
        }

        return args
    }
}
