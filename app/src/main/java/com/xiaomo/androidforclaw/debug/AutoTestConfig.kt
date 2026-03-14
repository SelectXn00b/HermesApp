/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: debug and test runners.
 */
package com.xiaomo.androidforclaw.core

/**
 * Auto test configuration
 *
 * Used to configure auto test behavior after application launch
 */
object AutoTestConfig {

    /**
     * Whether to enable auto test
     */
    const val AUTO_TEST_ENABLED = true  // ✅ API fixed, enable auto test

    /**
     * Auto test delay (milliseconds)
     * Wait for app to fully launch and permission requests to complete
     */
    const val AUTO_TEST_DELAY_MS = 5000L

    /**
     * Default test app
     */
    const val DEFAULT_TEST_APP_NAME = "淘宝"
    const val DEFAULT_TEST_APP_PACKAGE = "com.taobao.taobao"

    /**
     * Default test task
     */
    const val DEFAULT_TEST_TASK = """全面能力测试 - 分阶段执行：

        【阶段1：基础工具测试（无需权限）】
        1. exec: 执行 'date' 命令获取当前时间
        2. write_file: 在工作区写入 test.txt 内容为 'Hello from Agent'
        3. read_file: 读取刚才写入的 test.txt
        4. edit_file: 修改 test.txt，将 Agent 改为 AI Assistant
        5. read_file: 再次读取验证修改
        6. list_dir: 列出工作区目录内容
        7. log: 输出阶段1完成信息

        【阶段2：网络工具测试】
        8. web_fetch: 获取 https://www.baidu.com 首页内容（提取标题）
        9. log: 输出阶段2完成信息

        【阶段3：系统交互测试（需要权限，跳过如无权限）】
        10. wait: 等待1秒
        11. home: 返回主屏幕
        12. wait: 等待1秒
        13. back: 返回
        14. log: 输出阶段3完成信息

        【阶段4：应用操作测试（需要权限，跳过如无权限）】
        15. open_app: 打开设置应用 (com.android.settings)
        16. wait: 等待2秒让界面加载
        17. get_view_tree: 获取当前界面的UI树（如果启用）
        18. screenshot: 截图当前界面（如果启用）
        19. home: 返回主屏幕
        20. log: 输出阶段4完成信息

        【完成】
        21. stop: 输出完整测试报告并结束

        注意：如果遇到权限错误，继续执行后续步骤，最后在报告中标注哪些能力可用"""

    /**
     * Screenshot capability configuration
     */
    object ScreenshotConfig {
        /**
         * Whether to enable screenshot
         * true = Auto screenshot to observe interface
         * false = No screenshot, only observe via UI Tree
         */
        const val ENABLED = false  // Temporarily disabled, waiting for accessibility permission

        /**
         * Screenshot interval (milliseconds)
         * Avoid too frequent screenshots
         */
        const val MIN_INTERVAL_MS = 2000L
    }

    /**
     * UI Tree capability configuration
     */
    object UITreeConfig {
        /**
         * Whether to enable UI Tree retrieval
         * true = Get UI hierarchy tree
         * false = Don't get UI Tree
         */
        const val ENABLED = false  // Temporarily disabled, waiting for accessibility permission

        /**
         * UI Tree max depth
         * Avoid UI Tree being too large
         */
        const val MAX_DEPTH = 10

        /**
         * Whether to include invisible nodes
         */
        const val INCLUDE_INVISIBLE = false
    }

    /**
     * Test configuration
     */
    object TestConfig {
        /**
         * Maximum iterations
         */
        const val MAX_ITERATIONS = 20

        /**
         * Whether to enable Extended Thinking
         */
        const val REASONING_ENABLED = true

        /**
         * Reasoning Budget Tokens
         */
        const val REASONING_BUDGET_TOKENS = 10000
    }
}
