package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/auth-health.ts
 *
 * AndroidForClaw adaptation: auth profile health evaluation.
 */

/**
 * Health status for a single auth profile.
 */
enum class AuthProfileHealthStatus {
    OK,          // Credential is valid and working
    EXPIRING,    // Token will expire soon (within warning threshold)
    EXPIRED,     // Token has expired
    MISSING,     // No credential stored
    STATIC       // API key (no expiration concept)
}

/**
 * Health info for a single auth profile.
 * Aligned with OpenClaw AuthProfileHealth.
 */
data class AuthProfileHealth(
    val profileId: String,
    val provider: String,
    val status: AuthProfileHealthStatus,
    val remainingMs: Long? = null,
    val email: String? = null
)

/**
 * Health info for a provider (aggregated across profiles).
 * Aligned with OpenClaw AuthProviderHealth.
 */
data class AuthProviderHealth(
    val provider: String,
    val profiles: List<AuthProfileHealth>,
    val hasHealthy: Boolean,
    val hasExpiring: Boolean,
    val hasExpired: Boolean
)

/**
 * Overall auth health summary.
 * Aligned with OpenClaw AuthHealthSummary.
 */
data class AuthHealthSummary(
    val providers: List<AuthProviderHealth>,
    val totalProfiles: Int,
    val healthyCount: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val missingCount: Int
)

/**
 * Auth profile health evaluation.
 * Aligned with OpenClaw auth-health.ts.
 */
object AuthHealth {

    /** Warning threshold: 24 hours before expiry. */
    const val DEFAULT_OAUTH_WARN_MS = 24 * 60 * 60 * 1000L

    /**
     * Build a health summary for all stored auth profiles.
     * Aligned with OpenClaw buildAuthHealthSummary.
     */
    fun buildAuthHealthSummary(
        store: AuthProfileStore,
        now: Long = System.currentTimeMillis(),
        warnMs: Long = DEFAULT_OAUTH_WARN_MS
    ): AuthHealthSummary {
        val profileHealths = mutableListOf<AuthProfileHealth>()

        for ((profileId, credential) in store.profiles) {
            val health = evaluateProfileHealth(profileId, credential, now, warnMs)
            profileHealths.add(health)
        }

        // Group by provider
        val byProvider = profileHealths.groupBy { it.provider }
        val providerHealths = byProvider.map { (provider, profiles) ->
            AuthProviderHealth(
                provider = provider,
                profiles = profiles,
                hasHealthy = profiles.any { it.status == AuthProfileHealthStatus.OK || it.status == AuthProfileHealthStatus.STATIC },
                hasExpiring = profiles.any { it.status == AuthProfileHealthStatus.EXPIRING },
                hasExpired = profiles.any { it.status == AuthProfileHealthStatus.EXPIRED }
            )
        }

        return AuthHealthSummary(
            providers = providerHealths,
            totalProfiles = profileHealths.size,
            healthyCount = profileHealths.count { it.status == AuthProfileHealthStatus.OK || it.status == AuthProfileHealthStatus.STATIC },
            expiringCount = profileHealths.count { it.status == AuthProfileHealthStatus.EXPIRING },
            expiredCount = profileHealths.count { it.status == AuthProfileHealthStatus.EXPIRED },
            missingCount = profileHealths.count { it.status == AuthProfileHealthStatus.MISSING }
        )
    }

    /**
     * Format remaining time as human-readable short string.
     * Aligned with OpenClaw formatRemainingShort.
     */
    fun formatRemainingShort(ms: Long): String {
        if (ms <= 0) return "expired"
        val minutes = ms / (60 * 1000)
        val hours = ms / (60 * 60 * 1000)
        val days = ms / (24 * 60 * 60 * 1000)
        return when {
            days > 0 -> "${days}d"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    /**
     * Resolve the source label for an auth profile.
     * Aligned with OpenClaw resolveAuthProfileSource.
     */
    fun resolveAuthProfileSource(profileId: String): String {
        return when {
            profileId == CLAUDE_CLI_PROFILE_ID -> "Claude CLI"
            profileId == CODEX_CLI_PROFILE_ID -> "Codex CLI"
            profileId == QWEN_CLI_PROFILE_ID -> "Qwen CLI"
            profileId == MINIMAX_CLI_PROFILE_ID -> "MiniMax CLI"
            profileId.contains(":") -> profileId.substringAfter(":")
            else -> profileId
        }
    }

    // ── Internal ──

    private fun evaluateProfileHealth(
        profileId: String,
        credential: AuthProfileCredential,
        now: Long,
        warnMs: Long
    ): AuthProfileHealth {
        val provider = credential.provider

        return when (credential) {
            is AuthProfileCredential.ApiKey -> {
                if (credential.key.isNullOrBlank() && credential.keyRef == null) {
                    AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.MISSING)
                } else {
                    AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.STATIC, email = credential.email)
                }
            }
            is AuthProfileCredential.Token -> {
                if (credential.token.isNullOrBlank() && credential.tokenRef == null) {
                    return AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.MISSING)
                }
                val expires = credential.expires
                if (expires == null) {
                    return AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.OK, email = credential.email)
                }
                val remaining = expires - now
                when {
                    remaining <= 0 -> AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.EXPIRED, remaining, credential.email)
                    remaining <= warnMs -> AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.EXPIRING, remaining, credential.email)
                    else -> AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.OK, remaining, credential.email)
                }
            }
            is AuthProfileCredential.OAuth -> {
                if (credential.accessToken.isNullOrBlank() && credential.refreshToken.isNullOrBlank()) {
                    return AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.MISSING)
                }
                // OAuth with valid refresh token is OK even if access token expired
                if (!credential.refreshToken.isNullOrBlank()) {
                    val expiresAt = credential.expiresAt
                    if (expiresAt != null) {
                        val remaining = expiresAt - now
                        if (remaining <= warnMs && remaining > 0) {
                            return AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.EXPIRING, remaining, credential.email)
                        }
                    }
                    return AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.OK, email = credential.email)
                }
                // No refresh token — check access token expiry
                val expiresAt = credential.expiresAt
                if (expiresAt != null) {
                    val remaining = expiresAt - now
                    when {
                        remaining <= 0 -> return AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.EXPIRED, remaining, credential.email)
                        remaining <= warnMs -> return AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.EXPIRING, remaining, credential.email)
                    }
                }
                AuthProfileHealth(profileId, provider, AuthProfileHealthStatus.OK, email = credential.email)
            }
        }
    }
}
