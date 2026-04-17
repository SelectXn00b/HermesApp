package com.xiaomo.hermes.selfcontrol

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: self-control runtime support.
 */


import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Self-Control Demo Activity
 *
 * 演示如何使用 Self-Control Skills 的示例 Activity。
 *
 * 这个 Activity 展示了：
 * 1. 如何初始化 SelfControlRegistry
 * 2. 如何调用各个 Self-Control Skills
 * 3. 典型使用场景的代码示例
 *
 * 注意：这是一个可选的演示类，主应用不需要依赖它。
 */
class SelfControlDemoActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SelfControlDemo"
    }

    private lateinit var registry: SelfControlRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Registry
        registry = SelfControlRegistry(this)

        // 演示各个功能
        demonstrateSelfControl()
    }

    private fun demonstrateSelfControl() {
        lifecycleScope.launch {
            Log.d(TAG, "=== Self-Control Demo Start ===")
            Log.d(TAG, registry.getSummary())

            // 示例 1: 页面导航
            demoNavigation()

            // 示例 2: 配置管理
            demoConfigManagement()

            // 示例 3: 服务控制
            demoServiceControl()

            // 示例 4: 日志查询
            demoLogQuery()

            Log.d(TAG, "=== Self-Control Demo End ===")
        }
    }

    /**
     * 示例 1: 页面导航
     */
    private suspend fun demoNavigation() {
        Log.d(TAG, "\n--- Demo 1: Navigation ---")

        // 打开配置页面
        val result = registry.execute(
            "navigate_app",
            mapOf("page" to "config")
        )

        Log.d(TAG, "Navigate to config: ${result?.content}")
        showToast("导航到配置页面: ${result?.success}")
    }

    /**
     * 示例 2: 配置管理
     */
    private suspend fun demoConfigManagement() {
        Log.d(TAG, "\n--- Demo 2: Config Management ---")

        // 2.1 列出所有 feature 配置
        val listResult = registry.execute(
            "manage_config",
            mapOf(
                "operation" to "list",
                "category" to "feature"
            )
        )
        Log.d(TAG, "List config:\n${listResult?.content}")

        // 2.2 读取 exploration_mode
        val getResult = registry.execute(
            "manage_config",
            mapOf(
                "operation" to "get",
                "key" to "exploration_mode"
            )
        )
        Log.d(TAG, "Get exploration_mode: ${getResult?.content}")

        // 2.3 修改配置
        val setResult = registry.execute(
            "manage_config",
            mapOf(
                "operation" to "set",
                "key" to "self_control_demo",
                "value" to "true"
            )
        )
        Log.d(TAG, "Set config: ${setResult?.content}")

        // 2.4 验证修改
        val verifyResult = registry.execute(
            "manage_config",
            mapOf(
                "operation" to "get",
                "key" to "self_control_demo"
            )
        )
        Log.d(TAG, "Verify config: ${verifyResult?.content}")

        showToast("配置管理演示完成")
    }

    /**
     * 示例 3: 服务控制
     */
    private suspend fun demoServiceControl() {
        Log.d(TAG, "\n--- Demo 3: Service Control ---")

        // 3.1 检查服务状态
        val statusResult = registry.execute(
            "control_service",
            mapOf("operation" to "check_status")
        )
        Log.d(TAG, "Service status:\n${statusResult?.content}")

        // 3.2 隐藏悬浮窗（模拟截图前操作）
        val hideResult = registry.execute(
            "control_service",
            mapOf("operation" to "hide_float")
        )
        Log.d(TAG, "Hide float: ${hideResult?.content}")

        // 3.3 等待 1 秒
        kotlinx.coroutines.delay(1000)

        // 3.4 显示悬浮窗（模拟截图后操作）
        val showResult = registry.execute(
            "control_service",
            mapOf("operation" to "show_float")
        )
        Log.d(TAG, "Show float: ${showResult?.content}")

        showToast("服务控制演示完成")
    }

    /**
     * 示例 4: 日志查询
     */
    private suspend fun demoLogQuery() {
        Log.d(TAG, "\n--- Demo 4: Log Query ---")

        // 4.1 查询错误日志
        val errorResult = registry.execute(
            "query_logs",
            mapOf(
                "level" to "E",
                "lines" to 20
            )
        )
        Log.d(TAG, "Error logs:\n${errorResult?.content}")

        // 4.2 搜索特定 TAG
        val filterResult = registry.execute(
            "query_logs",
            mapOf(
                "level" to "D",
                "filter" to "SelfControl",
                "lines" to 50
            )
        )
        Log.d(TAG, "Filtered logs:\n${filterResult?.content}")

        showToast("日志查询演示完成")
    }

    /**
     * 示例 5: 组合使用（完整流程）
     */
    private suspend fun demoCompleteWorkflow() {
        Log.d(TAG, "\n--- Demo 5: Complete Workflow ---")
        Log.d(TAG, "Scenario: AI Agent 自我诊断和调优")

        // 步骤 1: 检查服务状态
        Log.d(TAG, "Step 1: Check service status")
        val status = registry.execute(
            "control_service",
            mapOf("operation" to "check_status")
        )
        Log.d(TAG, status?.content ?: "Failed")

        // 步骤 2: 查看错误日志
        Log.d(TAG, "Step 2: Query error logs")
        val errors = registry.execute(
            "query_logs",
            mapOf("level" to "E", "lines" to 50)
        )

        // 模拟发现问题：screenshot_delay 太短
        if (errors?.content?.contains("screenshot", ignoreCase = true) == true) {
            Log.d(TAG, "Step 3: Found issue - screenshot delay too short")

            // 步骤 3: 调整配置
            Log.d(TAG, "Step 4: Increase screenshot_delay")
            registry.execute(
                "manage_config",
                mapOf(
                    "operation" to "set",
                    "key" to "screenshot_delay",
                    "value" to "200"
                )
            )

            // 步骤 4: 验证配置
            Log.d(TAG, "Step 5: Verify config change")
            val verify = registry.execute(
                "manage_config",
                mapOf(
                    "operation" to "get",
                    "key" to "screenshot_delay"
                )
            )
            Log.d(TAG, verify?.content ?: "Failed")
        }

        // 步骤 5: 打开配置页面让用户确认
        Log.d(TAG, "Step 6: Open config page for user confirmation")
        registry.execute(
            "navigate_app",
            mapOf("page" to "config")
        )

        showToast("完整工作流演示完成")
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
