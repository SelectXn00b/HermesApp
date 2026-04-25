package com.xiaomo.hermes.hermes.agent

/**
 * Smart Model Routing - 智能模型路由
 * 1:1 对齐 hermes/agent/smart_model_routing.py
 *
 * 根据消息内容自动选择便宜模型或昂贵模型
 */
class SmartModelRouting {

    data class RoutingResult(
        val model: String,
        val reason: String,
        val estimatedCost: Double
    )

    /**
     * 根据消息内容选择模型
     *
     * @param userMessage 用户消息
     * @param currentModel 当前模型
     * @param cheapModel 便宜模型
     * @param expensiveModel 昂贵模型
     * @return 路由结果
     */
    fun route(
        userMessage: String,
        currentModel: String,
        cheapModel: String = "gpt-4o-mini",
        expensiveModel: String = "claude-opus-4-6"
    ): RoutingResult {
        val complexity = analyzeComplexity(userMessage)

        return when {
            complexity <= 0.3 -> RoutingResult(
                model = cheapModel,
                reason = "Simple query, using cheap model",
                estimatedCost = 0.001
            )
            complexity <= 0.7 -> RoutingResult(
                model = currentModel,
                reason = "Medium complexity, using current model",
                estimatedCost = 0.01
            )
            else -> RoutingResult(
                model = expensiveModel,
                reason = "Complex task, using expensive model",
                estimatedCost = 0.1
            )
        }
    }

    /**
     * 分析消息复杂度 (0.0 - 1.0)
     */
    private fun analyzeComplexity(message: String): Double {
        var score = 0.0

        // 长度因素
        when {
            message.length < 50 -> score += 0.1
            message.length < 200 -> score += 0.3
            message.length < 1000 -> score += 0.5
            else -> score += 0.8
        }

        // 关键词因素
        val complexKeywords = listOf(
            "analyze", "complex", "detailed", "architecture", "design",
            "compare", "evaluate", "implement", "refactor", "optimize",
            "分析", "复杂", "详细", "架构", "设计", "对比", "评估", "实现", "重构", "优化"
        )
        if (complexKeywords.any { message.lowercase().contains(it) }) {
            score += 0.3
        }

        // 代码因素
        if (message.contains("```")) {
            score += 0.2
        }

        return minOf(score, 1.0)
    }


}
