package com.xiaomo.androidforclaw.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/auth-profiles/types.ts
 * - ../openclaw/src/agents/auth-profiles/constants.ts
 * - ../openclaw/src/agents/auth-profiles/credential-state.ts
 * - ../openclaw/src/agents/auth-profiles/profiles.ts
 * - ../openclaw/src/agents/auth-profiles/usage.ts
 * - ../openclaw/src/agents/auth-profiles/order.ts
 *
 * AndroidForClaw adaptation: multi-provider credential profile management.
 */

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.secrets.SecretRef
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// ── Constants (aligned with OpenClaw auth-profiles/constants.ts) ──

const val AUTH_STORE_VERSION = 1
const val AUTH_PROFILE_FILENAME = "auth-profiles.json"
const val LEGACY_AUTH_FILENAME = "auth.json"
const val CLAUDE_CLI_PROFILE_ID = "anthropic:claude-cli"
const val CODEX_CLI_PROFILE_ID = "openai-codex:codex-cli"
const val QWEN_CLI_PROFILE_ID = "qwen-portal:qwen-cli"
const val MINIMAX_CLI_PROFILE_ID = "minimax-portal:minimax-cli"
const val EXTERNAL_CLI_SYNC_TTL_MS = 15 * 60 * 1000L    // 15 minutes
const val EXTERNAL_CLI_NEAR_EXPIRY_MS = 10 * 60 * 1000L  // 10 minutes

/**
 * Credential types.
 * Aligned with OpenClaw AuthProfileCredential.
 * Now includes SecretRef support (keyRef, tokenRef).
 */
sealed class AuthProfileCredential {
    abstract val provider: String
    abstract val email: String?

    data class ApiKey(
        override val provider: String,
        val key: String? = null,
        val keyRef: SecretRef? = null,
        override val email: String? = null,
        val metadata: Map<String, String>? = null
    ) : AuthProfileCredential()

    data class Token(
        override val provider: String,
        val token: String? = null,
        val tokenRef: SecretRef? = null,
        val expires: Long? = null,
        override val email: String? = null
    ) : AuthProfileCredential()

    data class OAuth(
        override val provider: String,
        val clientId: String? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresAt: Long? = null,
        override val email: String? = null
    ) : AuthProfileCredential()
}

/**
 * Profile failure reasons.
 * Aligned with OpenClaw AuthProfileFailureReason.
 */
enum class AuthProfileFailureReason {
    AUTH,
    AUTH_PERMANENT,
    FORMAT,
    OVERLOADED,
    RATE_LIMIT,
    BILLING,
    TIMEOUT,
    MODEL_NOT_FOUND,
    SESSION_EXPIRED,
    UNKNOWN
}

/**
 * Credential eligibility reason codes.
 * Aligned with OpenClaw AuthCredentialReasonCode.
 */
enum class AuthCredentialReasonCode {
    OK,
    MISSING_CREDENTIAL,
    INVALID_EXPIRES,
    EXPIRED,
    UNRESOLVED_REF
}

/**
 * Token expiry state.
 * Aligned with OpenClaw TokenExpiryState.
 */
enum class TokenExpiryState {
    MISSING,
    VALID,
    EXPIRED,
    INVALID_EXPIRES
}

/**
 * Per-profile usage statistics.
 * Aligned with OpenClaw ProfileUsageStats.
 */
data class ProfileUsageStats(
    var lastUsed: Long? = null,
    var cooldownUntil: Long? = null,
    var disabledUntil: Long? = null,
    var disabledReason: AuthProfileFailureReason? = null,
    var errorCount: Int = 0,
    var failureCounts: MutableMap<String, Int> = mutableMapOf(),
    var lastFailureAt: Long? = null
)

/**
 * Auth profile store (serialized to JSON).
 * Aligned with OpenClaw AuthProfileStore.
 */
data class AuthProfileStore(
    val version: Int = AUTH_STORE_VERSION,
    val profiles: MutableMap<String, AuthProfileCredential> = mutableMapOf(),
    val order: MutableMap<String, MutableList<String>> = mutableMapOf(),
    val lastGood: MutableMap<String, String> = mutableMapOf(),
    val usageStats: MutableMap<String, ProfileUsageStats> = mutableMapOf()
)

/**
 * Credential eligibility result.
 * Aligned with OpenClaw evaluateStoredCredentialEligibility.
 */
data class CredentialEligibility(
    val eligible: Boolean,
    val reasonCode: AuthCredentialReasonCode
)

/**
 * AuthProfiles — Multi-provider credential profile management.
 * Aligned with OpenClaw auth-profiles.
 */
object AuthProfiles {

    private const val TAG = "AuthProfiles"

    const val DEFAULT_RATE_LIMIT_COOLDOWN_MS = 60_000L
    const val DEFAULT_AUTH_COOLDOWN_MS = 5 * 60_000L
    const val MAX_CONSECUTIVE_ERRORS = 5

    private var store = AuthProfileStore()
    private val runtimeStats = ConcurrentHashMap<String, ProfileUsageStats>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ── Store I/O ──

    fun load(workspaceDir: File) {
        val file = File(workspaceDir, AUTH_PROFILE_FILENAME)
        if (file.exists()) {
            try {
                store = gson.fromJson(file.readText(), AuthProfileStore::class.java) ?: AuthProfileStore()
                Log.d(TAG, "Loaded ${store.profiles.size} auth profiles")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load auth profiles: ${e.message}")
                store = AuthProfileStore()
            }
        }
    }

    fun save(workspaceDir: File) {
        val file = File(workspaceDir, AUTH_PROFILE_FILENAME)
        try {
            file.writeText(gson.toJson(store))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save auth profiles: ${e.message}")
        }
    }

    // ── CRUD ──

    fun upsert(profileId: String, credential: AuthProfileCredential) {
        store.profiles[profileId] = credential
        Log.d(TAG, "Upserted profile: $profileId (provider=${credential.provider})")
    }

    fun listForProvider(provider: String): List<String> {
        return store.profiles.entries
            .filter { it.value.provider.equals(provider, ignoreCase = true) }
            .map { it.key }
    }

    fun get(profileId: String): AuthProfileCredential? = store.profiles[profileId]

    fun remove(profileId: String) {
        store.profiles.remove(profileId)
        store.usageStats.remove(profileId)
        runtimeStats.remove(profileId)
    }

    // ── Usage tracking ──

    fun markGood(provider: String, profileId: String) {
        store.lastGood[provider] = profileId
        val stats = getOrCreateStats(profileId)
        stats.lastUsed = System.currentTimeMillis()
        stats.errorCount = 0
    }

    fun recordFailure(profileId: String, reason: AuthProfileFailureReason) {
        val stats = getOrCreateStats(profileId)
        stats.errorCount++
        stats.lastFailureAt = System.currentTimeMillis()
        stats.failureCounts[reason.name] = (stats.failureCounts[reason.name] ?: 0) + 1

        val cooldownMs = when (reason) {
            AuthProfileFailureReason.RATE_LIMIT -> DEFAULT_RATE_LIMIT_COOLDOWN_MS
            AuthProfileFailureReason.AUTH, AuthProfileFailureReason.BILLING -> DEFAULT_AUTH_COOLDOWN_MS
            AuthProfileFailureReason.AUTH_PERMANENT -> Long.MAX_VALUE
            else -> DEFAULT_RATE_LIMIT_COOLDOWN_MS
        }

        if (cooldownMs < Long.MAX_VALUE) {
            stats.cooldownUntil = System.currentTimeMillis() + cooldownMs
        } else {
            stats.disabledUntil = Long.MAX_VALUE
            stats.disabledReason = reason
        }

        if (stats.errorCount >= MAX_CONSECUTIVE_ERRORS) {
            stats.disabledUntil = System.currentTimeMillis() + DEFAULT_AUTH_COOLDOWN_MS * 2
            stats.disabledReason = reason
            Log.w(TAG, "Profile $profileId disabled after ${stats.errorCount} consecutive errors")
        }

        Log.d(TAG, "Profile $profileId failure recorded: $reason (errors=${stats.errorCount})")
    }

    fun isCoolingDown(profileId: String): Boolean {
        val stats = runtimeStats[profileId] ?: return false
        val now = System.currentTimeMillis()

        if (stats.disabledUntil != null && now < stats.disabledUntil!!) return true
        if (stats.cooldownUntil != null && now < stats.cooldownUntil!!) return true

        if (stats.cooldownUntil != null && now >= stats.cooldownUntil!!) {
            stats.cooldownUntil = null
        }
        return false
    }

    fun clearCooldown(profileId: String) {
        runtimeStats[profileId]?.let {
            it.cooldownUntil = null
            it.disabledUntil = null
            it.disabledReason = null
        }
    }

    fun clearExpiredCooldowns() {
        val now = System.currentTimeMillis()
        for ((_, stats) in runtimeStats) {
            if (stats.cooldownUntil != null && now >= stats.cooldownUntil!!) {
                stats.cooldownUntil = null
            }
            if (stats.disabledUntil != null && stats.disabledUntil != Long.MAX_VALUE && now >= stats.disabledUntil!!) {
                stats.disabledUntil = null
                stats.disabledReason = null
            }
        }
    }

    // ── Ordering ──

    fun resolveOrder(provider: String): List<String> {
        val customOrder = store.order[provider]
        if (!customOrder.isNullOrEmpty()) {
            return customOrder.filter { !isCoolingDown(it) }
        }

        val all = listForProvider(provider)
        val lastGood = store.lastGood[provider]
        val available = all.filter { !isCoolingDown(it) }

        return if (lastGood != null && lastGood in available) {
            listOf(lastGood) + available.filter { it != lastGood }
        } else {
            available
        }
    }

    fun setOrder(provider: String, order: List<String>?) {
        if (order.isNullOrEmpty()) {
            store.order.remove(provider)
        } else {
            store.order[provider] = order.toMutableList()
        }
    }

    // ── Credential eligibility (aligned with OpenClaw) ──

    /**
     * Resolve token expiry state.
     * Aligned with OpenClaw resolveTokenExpiryState.
     */
    fun resolveTokenExpiryState(expires: Long?, now: Long = System.currentTimeMillis()): TokenExpiryState {
        if (expires == null) return TokenExpiryState.MISSING
        if (expires <= 0) return TokenExpiryState.INVALID_EXPIRES
        return if (now >= expires) TokenExpiryState.EXPIRED else TokenExpiryState.VALID
    }

    /**
     * Evaluate whether a stored credential is eligible for use.
     * Aligned with OpenClaw evaluateStoredCredentialEligibility.
     */
    fun evaluateCredentialEligibility(
        credential: AuthProfileCredential,
        now: Long = System.currentTimeMillis()
    ): CredentialEligibility {
        return when (credential) {
            is AuthProfileCredential.ApiKey -> {
                if (credential.key.isNullOrBlank() && credential.keyRef == null) {
                    CredentialEligibility(false, AuthCredentialReasonCode.MISSING_CREDENTIAL)
                } else {
                    CredentialEligibility(true, AuthCredentialReasonCode.OK)
                }
            }
            is AuthProfileCredential.Token -> {
                if (credential.token.isNullOrBlank() && credential.tokenRef == null) {
                    return CredentialEligibility(false, AuthCredentialReasonCode.MISSING_CREDENTIAL)
                }
                when (resolveTokenExpiryState(credential.expires, now)) {
                    TokenExpiryState.EXPIRED -> CredentialEligibility(false, AuthCredentialReasonCode.EXPIRED)
                    TokenExpiryState.INVALID_EXPIRES -> CredentialEligibility(false, AuthCredentialReasonCode.INVALID_EXPIRES)
                    else -> CredentialEligibility(true, AuthCredentialReasonCode.OK)
                }
            }
            is AuthProfileCredential.OAuth -> {
                if (credential.accessToken.isNullOrBlank() && credential.refreshToken.isNullOrBlank()) {
                    CredentialEligibility(false, AuthCredentialReasonCode.MISSING_CREDENTIAL)
                } else {
                    CredentialEligibility(true, AuthCredentialReasonCode.OK)
                }
            }
        }
    }

    fun profileCount(): Int = store.profiles.size

    private fun getOrCreateStats(profileId: String): ProfileUsageStats {
        return runtimeStats.getOrPut(profileId) { ProfileUsageStats() }
    }
}
