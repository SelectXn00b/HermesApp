package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Define the DataStore at the module level
private val Context.apiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "api_settings")

class ApiPreferences private constructor(private val context: Context) {

    // Define our preferences keys
    companion object {
        @Volatile
        private var INSTANCE: ApiPreferences? = null

        fun getInstance(context: Context): ApiPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = ApiPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
        @JvmStatic
        fun getFeatureToggleBlocking(
            context: Context,
            featureKey: String,
            defaultValue: Boolean = false
        ): Boolean {
            val normalized = featureKey.trim()
            if (normalized.isEmpty()) {
                return defaultValue
            }
            return runBlocking {
                getInstance(context).featureToggleFlow(normalized, defaultValue).first()
            }
        }

        @JvmStatic
        fun setFeatureToggleBlocking(
            context: Context,
            featureKey: String,
            enabled: Boolean
        ) {
            val normalized = featureKey.trim()
            if (normalized.isEmpty()) {
                return
            }
            runBlocking {
                getInstance(context).saveFeatureToggle(normalized, enabled)
            }
        }

        // 动态生成供应商:模型的Token键
        fun getTokenInputKey(providerModel: String) =
                intPreferencesKey("token_input_${providerModel.replace(":", "_")}")

        fun getTokenCachedInputKey(providerModel: String) =
                intPreferencesKey("token_cached_input_${providerModel.replace(":", "_")}")

        fun getTokenOutputKey(providerModel: String) =
                intPreferencesKey("token_output_${providerModel.replace(":", "_")}")

        // 模型定价键
        fun getModelInputPriceKey(providerModel: String) =
                floatPreferencesKey("model_input_price_${providerModel.replace(":", "_")}")

        fun getModelCachedInputPriceKey(providerModel: String) =
                floatPreferencesKey("model_cached_input_price_${providerModel.replace(":", "_")}")

        fun getModelOutputPriceKey(providerModel: String) =
                floatPreferencesKey("model_output_price_${providerModel.replace(":", "_")}")

        // 请求次数统计键
        fun getRequestCountKey(providerModel: String) =
                intPreferencesKey("request_count_${providerModel.replace(":", "_")}")

        // 计费方式键
        fun getBillingModeKey(providerModel: String) =
                stringPreferencesKey("billing_mode_${providerModel.replace(":", "_")}")

        // 按次计费价格键
        fun getPricePerRequestKey(providerModel: String) =
                floatPreferencesKey("price_per_request_${providerModel.replace(":", "_")}")

        private val providerNameCandidates =
                ApiProviderType.values().map { it.name }.sortedByDescending { it.length }

        private fun decodeProviderModelFromKeySuffix(encoded: String): String {
                val matchedProvider = providerNameCandidates.firstOrNull {
                        encoded == it || encoded.startsWith("${it}_")
                }

                return if (matchedProvider != null) {
                        if (encoded.length == matchedProvider.length) {
                                matchedProvider
                        } else {
                                "$matchedProvider:${encoded.substring(matchedProvider.length + 1)}"
                        }
                } else {
                        encoded.replace("_", ":")
                }
        }

        val USD_TO_CNY_EXCHANGE_RATE = floatPreferencesKey("usd_to_cny_exchange_rate")

        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val USER_HAS_CONFIGURED_API = booleanPreferencesKey("user_has_configured_api")
        val FEATURE_TOGGLES_JSON = stringPreferencesKey("feature_toggles_json")
        // Default values
        const val DEFAULT_FEATURE_TOGGLE_STATE = false
        const val DEFAULT_KEEP_SCREEN_ON = true
        const val DEFAULT_USER_HAS_CONFIGURED_API = false
        // Keys for Thinking Mode and Thinking Guidance
        val ENABLE_THINKING_MODE = booleanPreferencesKey("enable_thinking_mode")
        val ENABLE_THINKING_GUIDANCE = booleanPreferencesKey("enable_thinking_guidance")
        val THINKING_QUALITY_LEVEL = intPreferencesKey("thinking_quality_level")

        // Key for Memory Attachment
        val ENABLE_MEMORY_QUERY = booleanPreferencesKey("enable_memory_query")

        // Key for Auto Read
        val ENABLE_AUTO_READ = booleanPreferencesKey("enable_auto_read")

        // Key for Tools Enable/Disable
        val ENABLE_TOOLS = booleanPreferencesKey("enable_tools")

        // Key for per-tool prompt visibility
        val TOOL_PROMPT_VISIBILITY_JSON = stringPreferencesKey("tool_prompt_visibility_json")

        // Key for Disable Stream Output
        val DISABLE_STREAM_OUTPUT = booleanPreferencesKey("disable_stream_output")

        // Key for Disable User Preference Description
        val DISABLE_USER_PREFERENCE_DESCRIPTION = booleanPreferencesKey("disable_user_preference_description")

        // Key for Disable Status Tags
        val DISABLE_STATUS_TAGS = booleanPreferencesKey("disable_status_tags")

        // Custom System Prompt Template (Advanced Configuration)
        val CUSTOM_SYSTEM_PROMPT_TEMPLATE = stringPreferencesKey("custom_system_prompt_template")

        val MAX_IMAGE_HISTORY_USER_TURNS = intPreferencesKey("max_image_history_user_turns")
        val MAX_MEDIA_HISTORY_USER_TURNS = intPreferencesKey("max_media_history_user_turns")

        // Default values for Thinking Mode and Thinking Guidance
        const val DEFAULT_ENABLE_THINKING_MODE = false
        const val DEFAULT_ENABLE_THINKING_GUIDANCE = false
        const val DEFAULT_THINKING_QUALITY_LEVEL = 2

        // Default value for Memory Attachment
        const val DEFAULT_ENABLE_MEMORY_QUERY = true

        // Default value for Auto Read
        const val DEFAULT_ENABLE_AUTO_READ = false

        // Default value for Tools Enable/Disable
        const val DEFAULT_ENABLE_TOOLS = true

        // Default value for Disable Stream Output (default false, meaning stream is enabled by default)
        const val DEFAULT_DISABLE_STREAM_OUTPUT = false

        // Default value for Disable User Preference Description
        const val DEFAULT_DISABLE_USER_PREFERENCE_DESCRIPTION = false

        // Default value for Disable Status Tags
        const val DEFAULT_DISABLE_STATUS_TAGS = false

        // Default system prompt template (empty means use built-in template)
        const val DEFAULT_SYSTEM_PROMPT_TEMPLATE = ""

        const val DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS = 2
        const val DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS = 1

        // 自定义参数存储键
        val CUSTOM_PARAMETERS = stringPreferencesKey("custom_parameters")

        private val SAF_BOOKMARKS_JSON = stringPreferencesKey("saf_bookmarks_json")

        // 默认空的自定义参数列表
        const val DEFAULT_CUSTOM_PARAMETERS = "[]"
        const val DEFAULT_TOOL_PROMPT_VISIBILITY_JSON = "{}"
        const val DEFAULT_FEATURE_TOGGLES_JSON = "{}"

        // API 配置默认值
        const val DEFAULT_API_ENDPOINT = "https://api.xiaomimimo.com/anthropic"
        const val DEFAULT_MODEL_NAME = "mimo-v2.5-pro"
        val DEFAULT_API_KEY: String = com.ai.assistance.operit.BuildConfig.MIMO_API_KEY
    }

    @Serializable
    data class SafBookmark(
        val uri: String,
        val name: String
    )

    val safBookmarksFlow: Flow<List<SafBookmark>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[SAF_BOOKMARKS_JSON] ?: "[]"
            runCatching { Json.decodeFromString<List<SafBookmark>>(json) }.getOrElse { emptyList() }
        }

    suspend fun addSafBookmark(uri: String, name: String) {
        context.apiDataStore.edit { preferences ->
            val existing =
                runCatching {
                    val json = preferences[SAF_BOOKMARKS_JSON] ?: "[]"
                    Json.decodeFromString<List<SafBookmark>>(json)
                }.getOrElse { emptyList() }

            val updated = (existing.filterNot { it.uri == uri } + SafBookmark(uri = uri, name = name))
                .sortedBy { it.name.lowercase() }
            preferences[SAF_BOOKMARKS_JSON] = Json.encodeToString(updated)
        }
    }

    suspend fun removeSafBookmark(uri: String) {
        context.apiDataStore.edit { preferences ->
            val existing =
                runCatching {
                    val json = preferences[SAF_BOOKMARKS_JSON] ?: "[]"
                    Json.decodeFromString<List<SafBookmark>>(json)
                }.getOrElse { emptyList() }
            val updated = existing.filterNot { it.uri == uri }
            preferences[SAF_BOOKMARKS_JSON] = Json.encodeToString(updated)
        }
    }

    val featureTogglesFlow: Flow<Map<String, Boolean>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[FEATURE_TOGGLES_JSON] ?: DEFAULT_FEATURE_TOGGLES_JSON
            runCatching {
                Json.decodeFromString<Map<String, Boolean>>(json)
            }.getOrElse { emptyMap() }
        }

    fun featureToggleFlow(featureKey: String, defaultValue: Boolean = false): Flow<Boolean> {
        val normalizedKey = featureKey.trim()
        if (normalizedKey.isEmpty()) {
            return featureTogglesFlow.map { defaultValue }
        }
        return featureTogglesFlow.map { toggles ->
            toggles[normalizedKey] ?: defaultValue
        }
    }

    // Get Keep Screen On setting as Flow
    val keepScreenOnFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON
            }

    val userHasConfiguredApiFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[USER_HAS_CONFIGURED_API] ?: DEFAULT_USER_HAS_CONFIGURED_API
            }

    // Flow for Thinking Mode
    val enableThinkingModeFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_THINKING_MODE] ?: DEFAULT_ENABLE_THINKING_MODE
        }

    // Flow for Thinking Guidance
    val enableThinkingGuidanceFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_THINKING_GUIDANCE] ?: DEFAULT_ENABLE_THINKING_GUIDANCE
            }

    val thinkingQualityLevelFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            (preferences[THINKING_QUALITY_LEVEL] ?: DEFAULT_THINKING_QUALITY_LEVEL).coerceIn(1, 4)
        }

    // Flow for Memory Attachment
    val enableMemoryQueryFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_MEMORY_QUERY] ?: DEFAULT_ENABLE_MEMORY_QUERY
        }

    // Flow for Auto Read
    val enableAutoReadFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_AUTO_READ] ?: DEFAULT_ENABLE_AUTO_READ
        }

    // Flow for Tools Enable/Disable
    val enableToolsFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_TOOLS] ?: DEFAULT_ENABLE_TOOLS
        }

    // Flow for per-tool prompt visibility
    val toolPromptVisibilityFlow: Flow<Map<String, Boolean>> =
        context.apiDataStore.data.map { preferences ->
            val json = preferences[TOOL_PROMPT_VISIBILITY_JSON] ?: DEFAULT_TOOL_PROMPT_VISIBILITY_JSON
            runCatching {
                Json.decodeFromString<Map<String, Boolean>>(json)
            }.getOrElse { emptyMap() }
        }

    // Flow for Disable Stream Output
    val disableStreamOutputFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[DISABLE_STREAM_OUTPUT] ?: DEFAULT_DISABLE_STREAM_OUTPUT
        }

    // Flow for Disable User Preference Description
    val disableUserPreferenceDescriptionFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[DISABLE_USER_PREFERENCE_DESCRIPTION] ?: DEFAULT_DISABLE_USER_PREFERENCE_DESCRIPTION
        }

    // Flow for Disable Status Tags
    val disableStatusTagsFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[DISABLE_STATUS_TAGS] ?: DEFAULT_DISABLE_STATUS_TAGS
        }

    // Custom System Prompt Template Flow
    val customSystemPromptTemplateFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] ?: DEFAULT_SYSTEM_PROMPT_TEMPLATE
            }

    val maxImageHistoryUserTurnsFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] ?: DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
        }

    val maxMediaHistoryUserTurnsFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] ?: DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
        }

    suspend fun saveFeatureToggle(featureKey: String, isEnabled: Boolean) {
        val normalizedKey = featureKey.trim()
        if (normalizedKey.isEmpty()) return

        context.apiDataStore.edit { preferences ->
            val currentMap =
                runCatching {
                    val json = preferences[FEATURE_TOGGLES_JSON] ?: DEFAULT_FEATURE_TOGGLES_JSON
                    Json.decodeFromString<Map<String, Boolean>>(json)
                }.getOrElse { emptyMap() }

            preferences[FEATURE_TOGGLES_JSON] =
                Json.encodeToString(currentMap + (normalizedKey to isEnabled))
        }
    }

    // Save Keep Screen On setting
    suspend fun saveKeepScreenOn(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[KEEP_SCREEN_ON] = isEnabled }
    }

    suspend fun saveUserHasConfiguredApi(configured: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[USER_HAS_CONFIGURED_API] = configured
        }
    }

    // Save Thinking Mode setting
    suspend fun saveEnableThinkingMode(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_THINKING_MODE] = isEnabled }
    }

    // Save Thinking Guidance setting
    suspend fun saveEnableThinkingGuidance(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_THINKING_GUIDANCE] = isEnabled }
    }

    suspend fun saveThinkingQualityLevel(level: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[THINKING_QUALITY_LEVEL] = level.coerceIn(1, 4)
        }
    }

    suspend fun updateThinkingSettings(
        enableThinkingMode: Boolean? = null,
        enableThinkingGuidance: Boolean? = null,
        thinkingQualityLevel: Int? = null
    ) {
        context.apiDataStore.edit { preferences ->
            val prevMode = preferences[ENABLE_THINKING_MODE] ?: DEFAULT_ENABLE_THINKING_MODE
            val prevGuidance = preferences[ENABLE_THINKING_GUIDANCE] ?: DEFAULT_ENABLE_THINKING_GUIDANCE

            var newMode = enableThinkingMode ?: prevMode
            var newGuidance = enableThinkingGuidance ?: prevGuidance

            // Enforce mutual exclusivity.
            if (enableThinkingMode == true) {
                newGuidance = false
            } else if (enableThinkingGuidance == true) {
                newMode = false
            } else if (newMode && newGuidance) {
                newGuidance = false
            }

            preferences[ENABLE_THINKING_MODE] = newMode
            preferences[ENABLE_THINKING_GUIDANCE] = newGuidance

            thinkingQualityLevel?.let { preferences[THINKING_QUALITY_LEVEL] = it.coerceIn(1, 4) }
        }
    }

    // Save Memory Attachment setting
    suspend fun saveEnableMemoryQuery(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_MEMORY_QUERY] = isEnabled }
    }

    // Save Auto Read setting
    suspend fun saveEnableAutoRead(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_AUTO_READ] = isEnabled }
    }

    // Save Tools Enable/Disable setting
    suspend fun saveEnableTools(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_TOOLS] = isEnabled }
    }

    // Save prompt visibility for a single tool
    suspend fun saveToolPromptVisibility(toolName: String, isVisible: Boolean) {
        context.apiDataStore.edit { preferences ->
            val currentMap = runCatching {
                val json = preferences[TOOL_PROMPT_VISIBILITY_JSON] ?: DEFAULT_TOOL_PROMPT_VISIBILITY_JSON
                Json.decodeFromString<Map<String, Boolean>>(json)
            }.getOrElse { emptyMap() }
            preferences[TOOL_PROMPT_VISIBILITY_JSON] = Json.encodeToString(currentMap + (toolName to isVisible))
        }
    }

    // Save prompt visibility map for all tools
    suspend fun saveToolPromptVisibilityMap(visibilityMap: Map<String, Boolean>) {
        context.apiDataStore.edit { preferences ->
            preferences[TOOL_PROMPT_VISIBILITY_JSON] = Json.encodeToString(visibilityMap)
        }
    }

    suspend fun getToolPromptVisibilityMap(): Map<String, Boolean> {
        val preferences = context.apiDataStore.data.first()
        val json = preferences[TOOL_PROMPT_VISIBILITY_JSON] ?: DEFAULT_TOOL_PROMPT_VISIBILITY_JSON
        return runCatching {
            Json.decodeFromString<Map<String, Boolean>>(json)
        }.getOrElse { emptyMap() }
    }

    // Save Disable Stream Output setting
    suspend fun saveDisableStreamOutput(isDisabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[DISABLE_STREAM_OUTPUT] = isDisabled }
    }

    // Save Disable User Preference Description setting
    suspend fun saveDisableUserPreferenceDescription(isDisabled: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[DISABLE_USER_PREFERENCE_DESCRIPTION] = isDisabled
        }
    }

    // Save Disable Status Tags setting
    suspend fun saveDisableStatusTags(isDisabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[DISABLE_STATUS_TAGS] = isDisabled }
    }

    /**
     * 更新指定供应商:模型的token计数
     * @param providerModel 供应商:模型标识符，格式如"DEEPSEEK:deepseek-chat"
     * @param inputTokens 新增的输入token
     * @param outputTokens 新增的输出token
     * @param cachedInputTokens 新增的缓存命中token
     */
    suspend fun updateTokensForProviderModel(
            providerModel: String,
            inputTokens: Int,
            outputTokens: Int,
            cachedInputTokens: Int = 0
    ) {
        context.apiDataStore.edit { preferences ->
            val inputKey = getTokenInputKey(providerModel)
            val cachedInputKey = getTokenCachedInputKey(providerModel)
            val outputKey = getTokenOutputKey(providerModel)

            val currentInputTokens = preferences[inputKey] ?: 0
            val currentCachedInputTokens = preferences[cachedInputKey] ?: 0
            val currentOutputTokens = preferences[outputKey] ?: 0

            preferences[inputKey] = currentInputTokens + inputTokens
            preferences[cachedInputKey] = currentCachedInputTokens + cachedInputTokens
            preferences[outputKey] = currentOutputTokens + outputTokens
        }
    }

    /**
     * 获取指定供应商:模型的输入token数量
     */
    suspend fun getInputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenInputKey(providerModel)] ?: 0
    }

    /**
     * 获取指定供应商:模型的缓存输入token数量
     */
    suspend fun getCachedInputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenCachedInputKey(providerModel)] ?: 0
    }

    /**
     * 获取指定供应商:模型的输出token数量
     */
    suspend fun getOutputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenOutputKey(providerModel)] ?: 0
    }

    /**
     * 获取所有供应商:模型的token统计
     * @return Map<供应商:模型, Triple<输入tokens, 输出tokens, 缓存tokens>>
     */
    suspend fun getAllProviderModelTokens(): Map<String, Triple<Int, Int, Int>> {
        val preferences = context.apiDataStore.data.first()
        val result = mutableMapOf<String, Triple<Int, Int, Int>>()
        
        // 遍历所有preferences，查找token相关的key
        preferences.asMap().forEach { (key, value) ->
            val keyName = key.name
            if (keyName.startsWith("token_input_")) {
                val providerModel =
                        decodeProviderModelFromKeySuffix(keyName.removePrefix("token_input_"))
                val inputTokens = value as? Int ?: 0
                val outputTokens = preferences[getTokenOutputKey(providerModel)] ?: 0
                val cachedInputTokens = preferences[getTokenCachedInputKey(providerModel)] ?: 0
                if (inputTokens > 0 || outputTokens > 0 || cachedInputTokens > 0) {
                    result[providerModel] = Triple(inputTokens, outputTokens, cachedInputTokens)
                }
            }
        }
        
        return result
    }

    /**
     * 获取所有供应商:模型的token统计的Flow
     * @return Flow<Map<供应商:模型, Triple<输入tokens, 输出tokens, 缓存tokens>>>
     */
    val allProviderModelTokensFlow: Flow<Map<String, Triple<Int, Int, Int>>> =
        context.apiDataStore.data.map { preferences ->
            val result = mutableMapOf<String, Triple<Int, Int, Int>>()
            
            // 遍历所有preferences，查找token相关的key
            preferences.asMap().forEach { (key, value) ->
                val keyName = key.name
                if (keyName.startsWith("token_input_")) {
                    val providerModel =
                            decodeProviderModelFromKeySuffix(keyName.removePrefix("token_input_"))
                    val inputTokens = value as? Int ?: 0
                    val outputTokens = preferences[getTokenOutputKey(providerModel)] ?: 0
                    val cachedInputTokens = preferences[getTokenCachedInputKey(providerModel)] ?: 0
                    if (inputTokens > 0 || outputTokens > 0 || cachedInputTokens > 0) {
                        result[providerModel] = Triple(inputTokens, outputTokens, cachedInputTokens)
                    }
                }
            }
            
            result
        }

    // Save custom system prompt template
    suspend fun saveCustomSystemPromptTemplate(template: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] = template
        }
    }

    // Reset custom system prompt template to default
    suspend fun resetCustomSystemPromptTemplate() {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] = DEFAULT_SYSTEM_PROMPT_TEMPLATE
        }
    }

    // 重置所有供应商:模型的token计数
    suspend fun resetAllProviderModelTokenCounts() {
        context.apiDataStore.edit { preferences ->
            val keysToRemove = mutableListOf<Preferences.Key<*>>()
            preferences.asMap().forEach { (key, _) ->
                val keyName = key.name
                if (keyName.startsWith("token_input_") || keyName.startsWith("token_output_") || keyName.startsWith("token_cached_input_") || keyName.startsWith("request_count_")) {
                    keysToRemove.add(key)
                }
            }
            keysToRemove.forEach { key ->
                preferences.remove(key)
            }
        }
    }

    // 重置指定供应商:模型的token计数
    suspend fun resetProviderModelTokenCounts(providerModel: String) {
        context.apiDataStore.edit { preferences ->
            preferences[getTokenInputKey(providerModel)] = 0
            preferences[getTokenCachedInputKey(providerModel)] = 0
            preferences[getTokenOutputKey(providerModel)] = 0
            preferences[getRequestCountKey(providerModel)] = 0
        }
    }

    // 获取模型输入价格（每百万tokens的美元价格）
    suspend fun getModelInputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelInputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 获取模型缓存输入价格（每百万tokens的美元价格）
    suspend fun getModelCachedInputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelCachedInputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 获取模型输出价格（每百万tokens的美元价格）
    suspend fun getModelOutputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelOutputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 设置模型输入价格（每百万tokens的美元价格）
    suspend fun setModelInputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelInputPriceKey(providerModel)] = price.toFloat()
        }
    }

    // 设置模型缓存输入价格（每百万tokens的美元价格）
    suspend fun setModelCachedInputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelCachedInputPriceKey(providerModel)] = price.toFloat()
        }
    }

    // 设置模型输出价格（每百万tokens的美元价格）
    suspend fun setModelOutputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelOutputPriceKey(providerModel)] = price.toFloat()
        }
    }

    // ===== Request Count Statistics 请求次数统计相关方法 =====

    /**
     * 增加指定供应商:模型的请求次数
     * @param providerModel 供应商:模型标识符，格式如"DEEPSEEK:deepseek-chat"
     */
    suspend fun incrementRequestCountForProviderModel(providerModel: String) {
        context.apiDataStore.edit { preferences ->
            val countKey = getRequestCountKey(providerModel)
            val currentCount = preferences[countKey] ?: 0
            preferences[countKey] = currentCount + 1
        }
    }

    /**
     * 获取指定供应商:模型的请求次数
     * @param providerModel 供应商:模型标识符
     * @return 请求次数
     */
    suspend fun getRequestCountForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getRequestCountKey(providerModel)] ?: 0
    }

    /**
     * 获取所有供应商:模型的请求次数统计
     * @return Map<供应商:模型, 请求次数>
     */
    suspend fun getAllProviderModelRequestCounts(): Map<String, Int> {
        val preferences = context.apiDataStore.data.first()
        val result = mutableMapOf<String, Int>()
        
        // 遍历所有preferences，查找请求次数相关的key
        preferences.asMap().forEach { (key, value) ->
            val keyName = key.name
            if (keyName.startsWith("request_count_")) {
                val providerModel =
                        decodeProviderModelFromKeySuffix(keyName.removePrefix("request_count_"))
                val count = value as? Int ?: 0
                if (count > 0) {
                    result[providerModel] = count
                }
            }
        }
        
        return result
    }

    /**
     * 重置指定供应商:模型的请求次数
     * @param providerModel 供应商:模型标识符
     */
    suspend fun resetProviderModelRequestCount(providerModel: String) {
        context.apiDataStore.edit { preferences ->
            preferences[getRequestCountKey(providerModel)] = 0
        }
    }

    // ===== Billing Mode 计费方式相关方法 =====

    /**
     * 获取指定供应商:模型的计费方式
     * @param providerModel 供应商:模型标识符
     * @return 计费方式，默认为TOKEN
     */
    suspend fun getBillingModeForProviderModel(providerModel: String): com.ai.assistance.operit.data.model.BillingMode {
        val preferences = context.apiDataStore.data.first()
        val modeString = preferences[getBillingModeKey(providerModel)]
        return com.ai.assistance.operit.data.model.BillingMode.fromString(modeString)
    }

    /**
     * 设置指定供应商:模型的计费方式
     * @param providerModel 供应商:模型标识符
     * @param mode 计费方式
     */
    suspend fun setBillingModeForProviderModel(providerModel: String, mode: com.ai.assistance.operit.data.model.BillingMode) {
        context.apiDataStore.edit { preferences ->
            preferences[getBillingModeKey(providerModel)] = mode.name
        }
    }

    // ===== Price Per Request 按次计费价格相关方法 =====

    /**
     * 获取指定供应商:模型的按次计费价格
     * @param providerModel 供应商:模型标识符
     * @return 每次请求的价格，未设置时返回0.0
     */
    suspend fun getPricePerRequestForProviderModel(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getPricePerRequestKey(providerModel)]?.toDouble() ?: 0.0
    }

    /**
     * 设置指定供应商:模型的按次计费价格（人民币）
     * @param providerModel 供应商:模型标识符
     * @param price 每次请求的价格
     */
    suspend fun setPricePerRequestForProviderModel(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getPricePerRequestKey(providerModel)] = price.toFloat()
        }
    }

    suspend fun getUsdToCnyExchangeRate(): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[USD_TO_CNY_EXCHANGE_RATE]?.toDouble() ?: 7.2
    }

    suspend fun setUsdToCnyExchangeRate(rate: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[USD_TO_CNY_EXCHANGE_RATE] = rate.toFloat()
        }
    }

    suspend fun saveMaxImageHistoryUserTurns(turns: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] = turns
        }
    }

    suspend fun saveMaxMediaHistoryUserTurns(turns: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] = turns
        }
    }

    suspend fun getMaxImageHistoryUserTurns(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_IMAGE_HISTORY_USER_TURNS] ?: DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
    }

    suspend fun getMaxMediaHistoryUserTurns(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_MEDIA_HISTORY_USER_TURNS] ?: DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
    }

    suspend fun resetHistoryRetentionSettings() {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] = DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] = DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
        }
    }
}
