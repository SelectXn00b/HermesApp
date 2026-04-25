package com.ai.assistance.operit.core.tools.calculator

/**
 * 计算器测试类
 *
 * 包含一些示例，展示计算器的功能
 */
class CalculatorTest {
    companion object {
        /** 运行测试示例 */
        fun runTests() {
            // 基本算术
            testExpression("2 + 3 * 4", "14")
            testExpression("(2 + 3) * 4", "20")
            testExpression("10 / 2 - 3", "2")

            // JavaScript特性
            testExpression("2 ** 3", "8") // 指数运算
            testExpression("true ? 10 : 20", "10") // 三元运算符
            testExpression("false ? 10 : 20", "20")
            testExpression("Math.sin(Math.PI / 2)", "1") // Math函数

            // 变量
            JsCalculator.setVariable("x", 5.0)
            testExpression("x + 10", "15")
            testExpression("x *= 2", "10") // 复合赋值
            testExpression("x", "10") // 检查变量值是否已更新

            // 模板字符串
            testExpression("\${10 + 20}", "30")

            // 日期函数
            testExpression("now() > 0", "1") // 当前时间戳必须大于0

            // 清理
            JsCalculator.clearVariables()

            println("所有测试完成!")
        }

        /** 测试单个表达式 */
        private fun testExpression(expression: String, expected: String) {
            try {
                val result = JsCalculator.calc(expression)
                if (result == expected) {
                    println("测试通过: $expression = $result")
                } else {
                    println("测试失败: $expression = $result, 期望值: $expected")
                }
            } catch (e: Exception) {
                println("测试出错: $expression, 错误: ${e.message}")
                e.printStackTrace()
            }
        }

        /** 主函数，用于直接运行测试 */
        @JvmStatic
        fun main(args: Array<String>) {
            runTests()
        }
    }
}
