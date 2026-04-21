package com.ai.assistance.operit.core.tools.calculator

import java.util.Date

/**
 * JavaScript风格的计算器
 *
 * 基于语法树解析与执行表达式，支持JavaScript语法特性
 */
class JsCalculator {
    companion object {
        /**
         * 计算表达式并返回结果
         *
         * @param expression 要计算的表达式字符串
         * @return 计算结果
         */
        fun evaluate(expression: String): Double {
            try {
                val parser = ExpressionParser(expression)
                val expressionTree = parser.parse()
                return expressionTree.evaluate()
            } catch (e: Exception) {
                throw IllegalArgumentException("Error evaluating expression: ${e.message}", e)
            }
        }

        /**
         * 格式化计算结果为字符串
         *
         * @param result 计算结果
         * @return 格式化后的字符串
         */
        fun formatResult(result: Double): String {
            return ExpressionContext.formatResult(result)
        }

        /**
         * 计算表达式并返回格式化结果
         *
         * @param expression 要计算的表达式字符串
         * @return 格式化后的计算结果
         */
        fun calc(expression: String): String {
            val result = evaluate(expression)
            return formatResult(result)
        }

        /**
         * 设置变量值
         *
         * @param name 变量名
         * @param value 变量值
         */
        fun setVariable(name: String, value: Double) {
            ExpressionContext.setVariable(name, value)
        }

        /**
         * 获取变量值
         *
         * @param name 变量名
         * @return 变量值
         */
        fun getVariable(name: String): Double {
            return ExpressionContext.getVariable(name)
        }

        /** 清除所有变量 */
        fun clearVariables() {
            ExpressionContext.clearVariables()
        }

        /**
         * 格式化日期
         *
         * @param date 日期对象
         * @param format 格式字符串
         * @return 格式化后的日期字符串
         */
        fun formatDate(date: Date, format: String): String {
            val formatter = java.text.SimpleDateFormat(format, java.util.Locale.getDefault())
            return formatter.format(date)
        }

        /** 获取支持的单位转换类型 */
        fun getSupportedUnits(): Map<String, List<String>> {
            return mapOf(
                    "Temperature" to listOf("c (Celsius)", "f (Fahrenheit)", "k (Kelvin)"),
                    "Length" to
                            listOf(
                                    "km (kilometers)",
                                    "mi (miles)",
                                    "m (meters)",
                                    "ft (feet)",
                                    "cm (centimeters)",
                                    "in (inches)"
                            ),
                    "Weight" to listOf("kg (kilograms)", "lb (pounds)", "g (grams)", "oz (ounces)"),
                    "Volume" to
                            listOf(
                                    "l (liters)",
                                    "gal (gallons)",
                                    "ml (milliliters)",
                                    "oz (fluid ounces)"
                            ),
                    "Speed" to listOf("kph (kilometers per hour)", "mph (miles per hour)")
            )
        }

        /** 获取支持的日期函数 */
        fun getSupportedDateFunctions(): List<String> {
            return listOf(
                    "today() - Current date",
                    "now() - Current timestamp in milliseconds",
                    "date(2023-01-01) - Parse date string",
                    "date_diff(date1, date2) - Days between dates",
                    "date_add(date, days) - Add days to date",
                    "weekday(date) - Get day of week (1-7)",
                    "month(date) - Get month (1-12)",
                    "year(date) - Get year",
                    "day(date) - Get day of month"
            )
        }

        /** 获取支持的统计函数 */
        fun getSupportedStatFunctions(): List<String> {
            return listOf(
                    "stats.mean(values...) - Calculate average",
                    "stats.median(values...) - Find middle value",
                    "stats.min(values...) - Find minimum value",
                    "stats.max(values...) - Find maximum value",
                    "stats.sum(values...) - Sum values",
                    "stats.stdev(values...) - Calculate standard deviation"
            )
        }

        /** 获取支持的JavaScript风格特性 */
        fun getSupportedJsFeatures(): List<String> {
            return listOf(
                    "condition ? value1 : value2 - Ternary operator",
                    "Math.sin(), Math.cos() - Math functions",
                    "\${a + b} - Template string",
                    "true, false, null, NaN - JavaScript keywords",
                    "a = 5; a += 2 - Assignment operators",
                    "2 ** 3 or 2 ^ 3 - Exponentiation",
                    "\"string\"[0] - String index",
                    "array.length - Length property"
            )
        }
    }
}
