package com.xiaomo.androidforclaw.agent.loop

/**
 * Auth Profile Rotation — 对齐 OpenClaw auth-controller.ts
 *
 * 管理多个 API Key 轮换：
 * - 配置多个 API Key profile
 * - Rate limit 时自动轮换
 * - 跟踪每个 profile 的健康状态
 *
 * OpenClaw 源: ../openclaw/src/agents/pi-embedded-runner/auth-controller.ts
 *
 * Android 适配：目前单 Key，但保留结构以备将来支持多 Key。
 */

// ── Types ──

/**
 * API Key Profile。
 * OpenClaw: AuthProfile
 */
data class AuthProfile(
    /** Profile 唯一 ID */
    val id: String,
    /** Provider 名称 */
    val provider: String,
    /** API Key（加密存储） */
    val apiKey: String,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 优先级（越小越高） */
    val priority: Int = 0,
    /** 标签（用于显示） */
    val label: String? = null
)

/**
 * Profile 健康状态。
 * OpenClaw: AuthProfileHealth
 */
data class AuthProfileHealth(
    val profileId: String,
    /** 是否健康 */
    val healthy: Boolean = true,
    /** 最近错误次数 */
    val recentErrors: Int = 0,
    /** 最近成功次数 */
    val recentSuccesses: Int = 0,
    /** 上次错误时间 */
    val lastErrorTime: Long? = null,
    /** 上次成功时间 */
    val lastSuccessTime: Long? = null,
    /** 冷却结束时间（rate limit 后） */
    val cooldownUntil: Long? = null
) {
    /** 是否在冷却中 */
    fun isInCooldown(): Boolean {
        val cd = cooldownUntil ?: return false
        return System.currentTimeMillis() < cd
    }
}

/**
 * Auth Controller — 管理 API Key 轮换。
 * OpenClaw: AuthController
 */
class AuthController(private val profiles: List<AuthProfile> = emptyList()) {
    private val healthMap = mutableMapOf<String, AuthProfileHealth>()

    /** 当前选中的 profile ID */
    var currentProfileId: String? = null
        private set

    /** 是否已轮换 */
    var profileRotated: Boolean = false
        private set

    /**
     * 获取当前可用的 profile。
     * 优先级：用户指定 > 健康 > 未冷却 > 轮回
     */
    fun getActiveProfile(): AuthProfile? {
        if (profiles.isEmpty()) return null

        // 如果有指定的 current profile，优先使用
        currentProfileId?.let { id ->
            val profile = profiles.find { it.id == id && it.enabled }
            if (profile != null) return profile
        }

        // 找第一个健康且未冷却的 profile
        val healthy = profiles
            .filter { it.enabled }
            .sortedBy { it.priority }
            .firstOrNull { profile ->
                val health = healthMap[profile.id]
                health == null || (health.healthy && !health.isInCooldown())
            }

        if (healthy != null) {
            currentProfileId = healthy.id
            return healthy
        }

        // Fallback: 返回任意启用的 profile
        val fallback = profiles.firstOrNull { it.enabled }
        currentProfileId = fallback?.id
        return fallback
    }

    /**
     * 轮换到下一个可用 profile。
     * OpenClaw: rotateProfile
     *
     * @return 新的 profile，如果没有可用的则返回 null
     */
    fun rotateProfile(): AuthProfile? {
        val currentId = currentProfileId
        val available = profiles
            .filter { it.enabled && it.id != currentId }
            .sortedBy { it.priority }
            .firstOrNull { profile ->
                val health = healthMap[profile.id]
                health == null || (health.healthy && !health.isInCooldown())
            }

        if (available != null) {
            currentProfileId = available.id
            profileRotated = true
            return available
        }
        return null
    }

    /**
     * 记录 profile 成功。
     */
    fun recordSuccess(profileId: String) {
        val health = healthMap[profileId] ?: AuthProfileHealth(profileId)
        healthMap[profileId] = health.copy(
            healthy = true,
            recentSuccesses = health.recentSuccesses + 1,
            lastSuccessTime = System.currentTimeMillis()
        )
    }

    /**
     * 记录 profile 失败。
     * @param cooldownMs 冷却时长（毫秒），0 表示不冷却
     */
    fun recordFailure(profileId: String, cooldownMs: Long = 0) {
        val health = healthMap[profileId] ?: AuthProfileHealth(profileId)
        val newErrors = health.recentErrors + 1
        healthMap[profileId] = health.copy(
            healthy = newErrors < 3,  // 3 次错误后标记为不健康
            recentErrors = newErrors,
            lastErrorTime = System.currentTimeMillis(),
            cooldownUntil = if (cooldownMs > 0) System.currentTimeMillis() + cooldownMs else health.cooldownUntil
        )
    }

    /**
     * 重置所有 profile 状态。
     */
    fun reset() {
        healthMap.clear()
        currentProfileId = null
        profileRotated = false
    }

    /**
     * 获取 profile 数量。
     */
    fun getProfileCount(): Int = profiles.size

    /**
     * 获取所有启用的 profile。
     */
    fun getEnabledProfiles(): List<AuthProfile> = profiles.filter { it.enabled }
}
