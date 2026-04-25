package com.ai.assistance.operit.core.tools.calculator

import java.util.Date

/** Calculator类，作为对JsCalculator的适配器，保持API兼容性 */
class Calculator {
    companion object {
        /** 计算表达式 */
        fun evalExpression(expression: String): Double {
            return JsCalculator.evaluate(expression)
        }

        /** 获取变量值 */
        fun getVariable(name: String): Double? {
            return try {
                JsCalculator.getVariable(name)
            } catch (e: Exception) {
                null
            }
        }

        /** 设置变量值 */
        fun setVariable(name: String, value: Double) {
            JsCalculator.setVariable(name, value)
        }

        /** 清除所有变量 */
        fun clearVariables() {
            JsCalculator.clearVariables()
        }

        /** 格式化日期 */
        fun formatDate(date: Date, format: String): String {
            return JsCalculator.formatDate(date, format)
        }

        /** 格式化结果 */
        fun formatResult(result: Double): String {
            return JsCalculator.formatResult(result)
        }

        /** 获取支持的单位列表 */
        fun getSupportedUnits(): Map<String, List<String>> {
            return JsCalculator.getSupportedUnits()
        }

        /** 获取支持的日期函数 */
        fun getSupportedDateFunctions(): List<String> {
            return JsCalculator.getSupportedDateFunctions()
        }

        /** 获取支持的统计函数 */
        fun getSupportedStatFunctions(): List<String> {
            return JsCalculator.getSupportedStatFunctions()
        }

        /** 获取支持的JavaScript特性 */
        fun getSupportedJsFeatures(): List<String> {
            return JsCalculator.getSupportedJsFeatures()
        }
    }
}
