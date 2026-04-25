package com.ai.assistance.operit.hermes.gateway

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferences for the Hermes gateway feature.
 *
 * Split into two stores:
 * - [DataStore] `hermes_gateway_preferences` for non-secret policy / tunables.
 * - [EncryptedSharedPreferences] `hermes_gateway_secrets` for credentials
 *   (bot tokens, app secrets, webhook signing keys).
 *
 * All state is read via [Flow]s; writes are `suspend` and go through [edit].
 * Callers should use [getInstance] to obtain a singleton bound to the
 * application context.
 */
private val Context.hermesGatewayDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "hermes_gateway_preferences")

class HermesGatewayPreferences private constructor(private val context: Context) {

    private val _secrets: SharedPreferences by lazy { openSecretStore(context) }

    // ── Service management ─────────────────────────────────────────────

    val serviceEnabledFlow: Flow<Boolean> =
        context.hermesGatewayDataStore.data.map { it[SERVICE_ENABLED] ?: DEFAULT_SERVICE_ENABLED }

    val autoStartOnBootFlow: Flow<Boolean> =
        context.hermesGatewayDataStore.data.map { it[AUTO_START_ON_BOOT] ?: DEFAULT_AUTO_START_ON_BOOT }

    suspend fun saveServiceEnabled(enabled: Boolean) {
        context.hermesGatewayDataStore.edit { it[SERVICE_ENABLED] = enabled }
    }

    suspend fun saveAutoStartOnBoot(enabled: Boolean) {
        context.hermesGatewayDataStore.edit { it[AUTO_START_ON_BOOT] = enabled }
    }

    // ── Agent tunables ─────────────────────────────────────────────────

    val agentMaxTurnsFlow: Flow<Int> =
        context.hermesGatewayDataStore.data.map { it[AGENT_MAX_TURNS] ?: DEFAULT_AGENT_MAX_TURNS }

    suspend fun saveAgentMaxTurns(turns: Int) {
        context.hermesGatewayDataStore.edit { it[AGENT_MAX_TURNS] = turns.coerceIn(1, 200) }
    }

    // ── Per-platform enabled flags ─────────────────────────────────────

    fun platformEnabledFlow(platformKey: String): Flow<Boolean> {
        val key = booleanPreferencesKey(platformEnabledKey(platformKey))
        return context.hermesGatewayDataStore.data.map { it[key] ?: false }
    }

    suspend fun savePlatformEnabled(platformKey: String, enabled: Boolean) {
        val key = booleanPreferencesKey(platformEnabledKey(platformKey))
        context.hermesGatewayDataStore.edit { it[key] = enabled }
    }

    // ── Per-platform policy (dm_policy / group_policy / allow-list CSV) ──

    fun platformPolicyFieldFlow(platformKey: String, field: String, default: String = ""): Flow<String> {
        val key = stringPreferencesKey(platformPolicyKey(platformKey, field))
        return context.hermesGatewayDataStore.data.map { it[key] ?: default }
    }

    suspend fun savePlatformPolicyField(platformKey: String, field: String, value: String) {
        val key = stringPreferencesKey(platformPolicyKey(platformKey, field))
        context.hermesGatewayDataStore.edit { it[key] = value }
    }

    // ── Per-platform secrets (encrypted) ───────────────────────────────

    fun readSecret(platformKey: String, field: String): String =
        _secrets.getString(secretKey(platformKey, field), "") ?: ""

    fun writeSecret(platformKey: String, field: String, value: String) {
        _secrets.edit().putString(secretKey(platformKey, field), value).apply()
    }

    fun clearSecrets(platformKey: String) {
        val prefix = "${platformKey}__"
        val editor = _secrets.edit()
        _secrets.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    companion object {
        @Volatile private var INSTANCE: HermesGatewayPreferences? = null

        fun getInstance(context: Context): HermesGatewayPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HermesGatewayPreferences(context.applicationContext).also { INSTANCE = it }
            }

        // DataStore keys
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val AGENT_MAX_TURNS = intPreferencesKey("agent_max_turns")

        const val DEFAULT_SERVICE_ENABLED = false
        const val DEFAULT_AUTO_START_ON_BOOT = false
        const val DEFAULT_AGENT_MAX_TURNS = 30

        // Platform identifiers (lowercase, matches Python Platform.value)
        const val PLATFORM_FEISHU = "feishu"
        const val PLATFORM_WEIXIN = "weixin"

        // Policy field names
        const val FIELD_DM_POLICY = "dm_policy"
        const val FIELD_DM_ALLOW_FROM = "dm_allow_from"
        const val FIELD_GROUP_POLICY = "group_policy"
        const val FIELD_GROUP_ALLOW_FROM = "group_allow_from"
        const val FIELD_REPLY_TO_MODE = "reply_to_mode"
        const val FIELD_REQUIRE_MENTION = "require_mention"

        // Feishu secret fields
        const val SECRET_FEISHU_APP_ID = "app_id"
        const val SECRET_FEISHU_APP_SECRET = "app_secret"
        const val SECRET_FEISHU_VERIFICATION_TOKEN = "verification_token"
        const val SECRET_FEISHU_ENCRYPT_KEY = "encrypt_key"
        const val SECRET_FEISHU_DOMAIN = "domain"
        const val SECRET_FEISHU_BOT_OPEN_ID = "bot_open_id"
        const val SECRET_FEISHU_BOT_NAME = "bot_name"

        // Weixin secret fields
        const val SECRET_WEIXIN_ACCOUNT_ID = "account_id"
        const val SECRET_WEIXIN_LOGIN_TOKEN = "login_token"

        private fun platformEnabledKey(platformKey: String) = "platform_${platformKey}_enabled"
        private fun platformPolicyKey(platformKey: String, field: String) = "platform_${platformKey}_${field}"
        private fun secretKey(platformKey: String, field: String) = "${platformKey}__${field}"

        private fun openSecretStore(context: Context): SharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "hermes_gateway_secrets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Throwable) {
            Log.w("HermesGatewayPreferences", "EncryptedSharedPreferences unavailable, falling back to plain store: ${e.message}")
            context.getSharedPreferences("hermes_gateway_secrets_plain", Context.MODE_PRIVATE)
        }
    }
}
