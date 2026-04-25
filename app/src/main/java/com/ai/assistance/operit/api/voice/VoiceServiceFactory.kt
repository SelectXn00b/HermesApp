package com.ai.assistance.operit.api.voice

import android.content.Context
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** 语音服务工厂，用于创建不同类型的语音服务实例 */
object VoiceServiceFactory {
    /** 语音服务类型枚举 */
    enum class VoiceServiceType {
        /** 基于Android系统TTS的简单语音实现 */
        SIMPLE_TTS,
        /** 基于HTTP请求的TTS实现 */
        HTTP_TTS,
        /** 基于 OpenAI Realtime WebSocket 的 TTS 实现 */
        OPENAI_WS_TTS,
        /** 硅基流动TTS服务 */
        SILICONFLOW_TTS,
        /** MiniMax TTS 服务 */
        MINIMAX_TTS,
        OPENAI_TTS,
    }

    /**
     * 创建语音服务实例 (现在从Preferences中读取配置)
     *
     * @param context 应用上下文
     * @return 对应类型的VoiceService实例
     */
    fun createVoiceService(
        context: Context
    ): VoiceService {
        val prefs = SpeechServicesPreferences(context)
        // 使用runBlocking同步获取配置，这在工厂方法中是可接受的
        return runBlocking {
            val type = prefs.ttsServiceTypeFlow.first()
            
            when (type) {
                VoiceServiceType.SIMPLE_TTS -> {
                    val httpConfig = prefs.ttsHttpConfigFlow.first()
                    SimpleVoiceProvider(
                        context = context,
                        initialLocaleTag = httpConfig.localeTag,
                        initialVoiceId = httpConfig.voiceId
                    )
                }
                VoiceServiceType.HTTP_TTS -> {
                    val httpConfig = prefs.ttsHttpConfigFlow.first()
                    HttpVoiceProvider(context).apply {
                        setConfiguration(httpConfig)
                    }
                }
                VoiceServiceType.OPENAI_WS_TTS -> {
                    val httpConfig = prefs.ttsHttpConfigFlow.first()
                    OpenAIRealtimeVoiceProvider(
                        context = context,
                        endpointUrl = httpConfig.urlTemplate,
                        apiKey = httpConfig.apiKey,
                        model = httpConfig.modelName,
                        initialVoiceId = httpConfig.voiceId
                    )
                }
                VoiceServiceType.SILICONFLOW_TTS -> {
                    val httpConfig = prefs.ttsHttpConfigFlow.first()
                    SiliconFlowVoiceProvider(
                        context = context,
                        apiKey = httpConfig.apiKey,
                        initialVoiceId = httpConfig.voiceId,
                        initialModelName = httpConfig.modelName
                    )
                }
                VoiceServiceType.MINIMAX_TTS -> {
                    val httpConfig = prefs.ttsHttpConfigFlow.first()
                    MiniMaxVoiceProvider(
                        context = context,
                        config = httpConfig
                    )
                }
                VoiceServiceType.OPENAI_TTS -> {
                    val httpConfig = prefs.ttsHttpConfigFlow.first()
                    OpenAIVoiceProvider(
                        context = context,
                        endpointUrl = httpConfig.urlTemplate,
                        apiKey = httpConfig.apiKey,
                        model = httpConfig.modelName,
                        initialVoiceId = httpConfig.voiceId
                    )
                }
            }
        }
    }

    // 单例实例缓存
    private var instance: VoiceService? = null
    private var currentType: VoiceServiceType? = null

    /**
     * 获取语音服务单例实例
     *
     * @param context 应用上下文
     * @return VoiceService实例
     */
    fun getInstance(context: Context): VoiceService {
        val prefs = SpeechServicesPreferences(context)
        val selectedType = runBlocking { prefs.ttsServiceTypeFlow.first() }

        if (instance == null || selectedType != currentType) {
            instance?.shutdown()
            instance = createVoiceService(context)
            currentType = selectedType
        }
        return instance!!
    }

    /** 重置单例实例 在需要更改语音服务类型或释放资源时调用 */
    fun resetInstance() {
        instance?.shutdown()
        instance = null
        currentType = null
    }
}
