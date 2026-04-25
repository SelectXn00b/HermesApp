package com.ai.assistance.operit.api.voice

import android.content.Context
import com.ai.assistance.operit.R
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 基于Android系统TextToSpeech API的语音服务实现
 *
 * 此实现利用Android的TextToSpeech API进行文本转语音
 */
class SimpleVoiceProvider(
    private val context: Context,
    initialLocaleTag: String = "",
    initialVoiceId: String = ""
) : VoiceService {
    companion object {
        private const val TAG = "SimpleVoiceProvider"
    }

    // TextToSpeech引擎实例
    private var tts: TextToSpeech? = null

    // 初始化状态
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: Boolean
        get() = _isInitialized.value

    // 播放状态
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Boolean
        get() = _isSpeaking.value

    // 播放状态Flow
    override val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()

    // 当前语音参数
    private var currentRate: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentVoiceId: String? = initialVoiceId.takeIf { it.isNotBlank() }
    private var currentLocaleTag: String = initialLocaleTag.trim()

    /** 初始化TTS引擎 */
    override suspend fun initialize(): Boolean =
            withContext(Dispatchers.IO) {
                return@withContext suspendCancellableCoroutine { continuation ->
                    // 如果已经初始化，直接返回
                    if (_isInitialized.value && tts != null) {
                        continuation.resume(true)
                        return@suspendCancellableCoroutine
                    }

                    // 创建新的TTS实例
                    tts =
                            TextToSpeech(context) { status ->
                                if (status == TextToSpeech.SUCCESS) {
                                    // 设置默认语速和音调
                                    tts?.setSpeechRate(currentRate)
                                    tts?.setPitch(currentPitch)

                                    // 设置进度监听器
                                    tts?.setOnUtteranceProgressListener(
                                            object : UtteranceProgressListener() {
                                                override fun onStart(utteranceId: String) {
                                                    _isSpeaking.value = true
                                                }

                                                override fun onDone(utteranceId: String) {
                                                    _isSpeaking.value = false
                                                }

                                                @Deprecated("Deprecated in Java")
                                                override fun onError(utteranceId: String) {
                                                    _isSpeaking.value = false
                                                }

                                                // 在API 23以上的设备，需要实现此方法
                                                override fun onError(
                                                        utteranceId: String,
                                                        errorCode: Int
                                                ) {
                                                    super.onError(utteranceId, errorCode)
                                                    _isSpeaking.value = false
                                                    AppLogger.e(
                                                            TAG,
                                                            "TTS错误: utteranceId=$utteranceId, errorCode=$errorCode"
                                                    )
                                                }
                                            }
                                    )

                                    _isInitialized.value = true
                                    continuation.resume(true)
                                } else {
                                    AppLogger.e(TAG, "TTS初始化失败: $status")
                                    _isInitialized.value = false
                                    continuation.resumeWith(Result.failure(
                                        TtsException(context.getString(R.string.accessibility_tts_init_failed, status))
                                    ))
                                }
                            }

                    // 如果协程被取消，关闭TTS
                    continuation.invokeOnCancellation { shutdown() }
                }
            }

    /** 将文本转换为语音并播放 */
    override suspend fun speak(
            text: String,
            interrupt: Boolean,
            rate: Float?,
            pitch: Float?,
            extraParams: Map<String, String>
    ): Boolean =
            withContext(Dispatchers.IO) {
                // 检查初始化状态
                if (!isInitialized) {
                    val initResult = initialize()
                    if (!initResult) {
                        return@withContext false
                    }
                }

                val prefs = SpeechServicesPreferences(context.applicationContext)
                val effectiveRate = rate ?: prefs.ttsSpeechRateFlow.first()
                val effectivePitch = pitch ?: prefs.ttsPitchFlow.first()

                return@withContext suspendCancellableCoroutine { continuation ->
                    tts?.let { textToSpeech ->
                        ensureVoiceAndLocaleReady(textToSpeech)

                        // 设置语速和音调
                        if (currentRate != effectiveRate) {
                            textToSpeech.setSpeechRate(effectiveRate)
                            currentRate = effectiveRate
                        }

                        if (currentPitch != effectivePitch) {
                            textToSpeech.setPitch(effectivePitch)
                            currentPitch = effectivePitch
                        }

                        // 生成唯一的话语ID
                        val utteranceId = UUID.randomUUID().toString()

                        // 准备参数
                        val params = HashMap<String, String>()
                        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId

                        // 中断当前语音
                        if (interrupt && isSpeaking) {
                            textToSpeech.stop()
                        }

                        // 根据Android版本选择适当的speak方法
                        val result =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    textToSpeech.speak(
                                            text,
                                            TextToSpeech.QUEUE_ADD,
                                            null,
                                            utteranceId
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, params)
                                }

                        // 检查结果
                        val success = result == TextToSpeech.SUCCESS
                        if (!success) {
                            AppLogger.e(TAG, "TTS播放失败: $result")
                        }

                        continuation.resume(success)
                    }
                            ?: run {
                                AppLogger.e(TAG, "TTS引擎未初始化")
                                continuation.resume(false)
                            }
                }
            }

    /** 停止当前正在播放的语音 */
    override suspend fun stop(): Boolean =
            withContext(Dispatchers.IO) {
                if (!isInitialized) return@withContext false

                tts?.let {
                    val result = it.stop() == TextToSpeech.SUCCESS
                    if (result) {
                        _isSpeaking.value = false
                    }
                    return@withContext result
                }
                return@withContext false
            }

    /** 暂停当前正在播放的语音（并非所有TTS引擎都支持此功能） */
    override suspend fun pause(): Boolean =
            withContext(Dispatchers.IO) {
                if (!isInitialized) return@withContext false

                // 标准的TextToSpeech不直接支持暂停，我们可以停止并记录状态
                tts?.let {
                    val result = it.stop() == TextToSpeech.SUCCESS
                    if (result) {
                        _isSpeaking.value = false
                        AppLogger.d(TAG, "TTS暂停 (通过stop方法实现)")
                    }
                    return@withContext result
                }
                return@withContext false
            }

    /** 继续播放暂停的语音（并非所有TTS引擎都支持此功能） */
    override suspend fun resume(): Boolean =
            withContext(Dispatchers.IO) {
                if (!isInitialized) return@withContext false

                // 标准的TextToSpeech不直接支持恢复播放
                // 这里我们只能返回false，因为一旦停止就无法恢复到之前的状态
                AppLogger.w(TAG, "当前TTS引擎不支持恢复播放功能")
                return@withContext false
            }

    /** 释放TTS引擎资源 */
    override fun shutdown() {
        tts?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (e: Exception) {
                AppLogger.e(TAG, "关闭TTS引擎失败", e)
            } finally {
                tts = null
                _isInitialized.value = false
                _isSpeaking.value = false
            }
        }
    }

    /** 获取可用的语音列表 */
    override suspend fun getAvailableVoices(): List<VoiceService.Voice> =
            withContext(Dispatchers.IO) {
                val result = mutableListOf<VoiceService.Voice>()

                // 检查初始化状态
                if (!isInitialized) {
                    val initResult = initialize()
                    if (!initResult) {
                        return@withContext emptyList<VoiceService.Voice>()
                    }
                }

                // 获取可用语音
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts?.let { textToSpeech ->
                        val voices = textToSpeech.voices
                        if (voices != null) {
                            for (voice in voices) {
                                // 转换为VoiceService.Voice
                                val gender =
                                        when {
                                            voice.name.contains("female", ignoreCase = true) ->
                                                    "FEMALE"
                                            voice.name.contains("male", ignoreCase = true) -> "MALE"
                                            else -> "NEUTRAL"
                                        }

                                result.add(
                                        VoiceService.Voice(
                                                id = voice.name,
                                                name = voice.name,
                                                locale = voice.locale.toLanguageTag(),
                                                gender = gender
                                        )
                                )
                            }
                        }
                    }
                } else {
                    // 低版本Android不支持获取语音列表
                    AppLogger.w(TAG, "当前Android版本不支持获取TTS语音列表")
                }

                return@withContext result
            }

    /** 设置当前使用的语音 */
    override suspend fun setVoice(voiceId: String): Boolean =
            withContext(Dispatchers.IO) {
                // 检查初始化状态
                if (!isInitialized) {
                    val initResult = initialize()
                    if (!initResult) {
                        return@withContext false
                    }
                }

                // 设置语音
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts?.let { textToSpeech ->
                        val voices = textToSpeech.voices
                        if (voices != null) {
                            // 查找匹配的语音
                            val voice = voices.find { it.name == voiceId }
                            if (voice != null) {
                                val result = textToSpeech.setVoice(voice) == TextToSpeech.SUCCESS
                                if (result) {
                                    currentVoiceId = voiceId
                                    currentLocaleTag = voice.locale.toLanguageTag()
                                }
                                return@withContext result
                            } else {
                                AppLogger.e(TAG, "未找到ID为'$voiceId'的语音")
                                return@withContext false
                            }
                        }
                    }
                } else {
                    // 低版本Android不支持设置语音
                    AppLogger.w(TAG, "当前Android版本不支持设置TTS语音")
                }

                return@withContext false
            }

    private fun ensureVoiceAndLocaleReady(textToSpeech: TextToSpeech) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !currentVoiceId.isNullOrBlank()) {
            val voices = textToSpeech.voices
            val selectedVoice = voices?.firstOrNull { it.name == currentVoiceId }
            if (selectedVoice != null) {
                val result = textToSpeech.setVoice(selectedVoice)
                if (result == TextToSpeech.SUCCESS) {
                    currentLocaleTag = selectedVoice.locale.toLanguageTag()
                    return
                }
            }
        }

        val requestedTag = currentLocaleTag.ifBlank { Locale.getDefault().toLanguageTag() }
        val targetLocale = resolveBestLocale(textToSpeech, requestedTag)
        if (targetLocale == null) {
            throw TtsException(
                context.getString(R.string.accessibility_tts_locale_not_supported, requestedTag)
            )
        }

        val result = textToSpeech.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw TtsException(
                context.getString(R.string.accessibility_tts_locale_not_supported, requestedTag)
            )
        }

        currentLocaleTag = targetLocale.toLanguageTag()
    }

    private fun resolveBestLocale(textToSpeech: TextToSpeech, requestedTag: String): Locale? {
        val normalizedTag = requestedTag.trim().replace('_', '-')
        if (normalizedTag.isBlank()) {
            return Locale.getDefault()
        }

        val requestedLocale = Locale.forLanguageTag(normalizedTag)
        val availableLocales = linkedSetOf<Locale>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.voices
                ?.mapNotNull { it.locale }
                ?.forEach { availableLocales.add(it) }
        }

        textToSpeech.availableLanguages
            ?.forEach { availableLocales.add(it) }

        availableLocales.firstOrNull { it.toLanguageTag().equals(normalizedTag, ignoreCase = true) }?.let {
            return it
        }

        val requestedLanguage = requestedLocale.language
        if (requestedLanguage.isNotBlank()) {
            availableLocales.firstOrNull {
                it.language.equals(requestedLanguage, ignoreCase = true)
            }?.let {
                return it
            }
        }

        return if (textToSpeech.isLanguageAvailable(requestedLocale) >= TextToSpeech.LANG_AVAILABLE) {
            requestedLocale
        } else {
            null
        }
    }
}
