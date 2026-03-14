/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: browser tool client.
 */
package com.forclaw.browser.control.tools

import com.forclaw.browser.control.manager.BrowserManager
import com.forclaw.browser.control.model.ToolResult

/**
 * 浏览器选择工具
 *
 * 选择下拉框选项
 *
 * 参数:
 * - selector: String (必需) - CSS 选择器 (指向 <select> 元素)
 * - values: List<String> (必需) - 要选择的值列表
 *
 * 返回:
 * - selector: String - 使用的选择器
 * - values: List<String> - 选择的值
 * - selected: Boolean - 是否成功选择
 */
class BrowserSelectTool : BrowserTool {
    override val name = "browser_select"

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // 1. 验证参数
        val selector = args["selector"] as? String
            ?: return ToolResult.error("Missing required parameter: selector")

        if (selector.isBlank()) {
            return ToolResult.error("Parameter 'selector' cannot be empty")
        }

        @Suppress("UNCHECKED_CAST")
        val values = (args["values"] as? List<*>)?.mapNotNull { it as? String }
            ?: return ToolResult.error("Missing required parameter: values")

        if (values.isEmpty()) {
            return ToolResult.error("Parameter 'values' cannot be empty")
        }

        // 2. 检查浏览器实例
        if (!BrowserManager.isActive()) {
            return ToolResult.error("Browser is not active")
        }

        // 3. 构造 JavaScript 代码
        val escapedSelector = selector.replace("'", "\\'")
        val valuesJson = values.joinToString(",") { "'${it.replace("'", "\\'")}'" }

        val script = """
            (function() {
                try {
                    const select = document.querySelector('$escapedSelector');
                    if (!select || select.tagName !== 'SELECT') return false;

                    const valuesToSelect = [$valuesJson];

                    // 清除所有选项
                    Array.from(select.options).forEach(option => {
                        option.selected = false;
                    });

                    // 选择指定的值
                    let selectedCount = 0;
                    valuesToSelect.forEach(value => {
                        Array.from(select.options).forEach(option => {
                            if (option.value === value || option.text === value) {
                                option.selected = true;
                                selectedCount++;
                            }
                        });
                    });

                    // 触发 change 事件
                    if (selectedCount > 0) {
                        select.dispatchEvent(new Event('change', { bubbles: true }));
                        select.dispatchEvent(new Event('input', { bubbles: true }));
                        return true;
                    }

                    return false;
                } catch (e) {
                    return false;
                }
            })()
        """.trimIndent()

        // 4. 执行 JavaScript
        try {
            val result = BrowserManager.evaluateJavascript(script)
            val selected = result?.trim() == "true"

            // 5. 返回结果
            return if (selected) {
                ToolResult.success(
                    "selector" to selector,
                    "values" to values,
                    "selected" to true
                )
            } else {
                ToolResult.error("Select element not found or selection failed: $selector")
            }
        } catch (e: Exception) {
            return ToolResult.error("Select failed: ${e.message}")
        }
    }
}
