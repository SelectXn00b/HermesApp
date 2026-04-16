package com.xiaomo.androidforclaw.hermes.tools

/**
 * Budget and cost configuration for API usage tracking.
 * Ported from budget_config.py
 */
object BudgetConfig {

    /**
     * Budget alert levels.
     */
    enum class BudgetAlertLevel {
        NORMAL,
        WARNING,
        CRITICAL,
        EXCEEDED
    }

    /**
     * Budget configuration for a session or user.
     */
    data class Budget(
        val maxTokensPerSession: Long = 1_000_000L,
        val maxCostPerSessionUsd: Double = 10.0,
        val maxCostPerDayUsd: Double = 50.0,
        val warningThresholdPercent: Double = 80.0,
        val criticalThresholdPercent: Double = 95.0)

    /**
     * Current budget usage state.
     */
    data class BudgetState(
        val tokensUsed: Long = 0L,
        val costUsd: Double = 0.0,
        val dailyCostUsd: Double = 0.0,
        val sessionTurns: Int = 0) {
        val alertLevel: BudgetAlertLevel
            get() = when {
                costUsd >= Budget().maxCostPerSessionUsd -> BudgetAlertLevel.EXCEEDED
                costUsd >= Budget().maxCostPerSessionUsd * Budget().criticalThresholdPercent / 100.0 -> BudgetAlertLevel.CRITICAL
                costUsd >= Budget().maxCostPerSessionUsd * Budget().warningThresholdPercent / 100.0 -> BudgetAlertLevel.WARNING
                else -> BudgetAlertLevel.NORMAL
            }

        val remainingBudgetUsd: Double
            get() = maxOf(0.0, Budget().maxCostPerSessionUsd - costUsd)

        val usagePercent: Double
            get() = if (Budget().maxCostPerSessionUsd > 0) {
                (costUsd / Budget().maxCostPerSessionUsd) * 100.0
            } else 0.0
    }

    private var _budget = Budget()
    private var _state = BudgetState()

    fun getBudget(): Budget = _budget
    fun getState(): BudgetState = _state

    fun updateBudget(budget: Budget) {
        _budget = budget
    }

    fun recordUsage(tokens: Long, costUsd: Double) {
        _state = _state.copy(
            tokensUsed = _state.tokensUsed + tokens,
            costUsd = _state.costUsd + costUsd,
            dailyCostUsd = _state.dailyCostUsd + costUsd,
            sessionTurns = _state.sessionTurns + 1)
    }

    fun resetDaily() {
        _state = _state.copy(dailyCostUsd = 0.0)
    }

    fun resetSession() {
        _state = BudgetState()
    }

    /**
     * Check if the budget allows another turn.
     */
    fun canContinue(): Boolean {
        return _state.alertLevel != BudgetAlertLevel.EXCEEDED
    
    /** Resolve the threshold value for a given tier. */
    fun resolveThreshold(tier: String): Double = 1.0
}

}
