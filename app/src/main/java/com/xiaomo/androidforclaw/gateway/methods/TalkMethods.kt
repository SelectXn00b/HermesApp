package com.xiaomo.androidforclaw.gateway.methods

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import com.xiaomo.androidforclaw.logging.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Talk RPC methods — Android TTS 实现
 *
 * 提供 talk.speak（文字转语音）和 talk.config（语音配置）。
 * 使用 Android 内置 TextToSpeech 引擎合成 WAV，base64 编码返回，
 * 与 OpenClaw gateway talk.speak 协议完全对齐。
 */
class TalkMethods private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TalkMethods"
        private const val TTS_INIT_TIMEOUT_MS = 5000L
        private const val TTS_SYNTH_TIMEOUT_MS = 30000L

        @Volatile
        private var instance: TalkMethods? = null

        /**
         * Get or create the shared TalkMethods instance.
         * Avoids creating duplicate Android TTS engines.
         */
        fun getInstance(context: Context): TalkMethods {
            return instance ?: synchronized(this) {
                instance ?: TalkMethods(context.applicationContext).also { instance = it }
            }
        }
    }

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private val initLatch = CountDownLatch(1)

    fun init() {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
                Log.i(TAG, "Android TTS engine initialized")
            } else {
                Log.e(TAG, "Android TTS init failed: $status")
            }
            initLatch.countDown()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        Log.i(TAG, "Android TTS engine shut down")
    }

    /**
     * talk.config — 返回语音配置（固定默认值）
     */
    fun talkConfig(@Suppress("UNUSED_PARAMETER") params: Any?): Map<String, Any?> {
        return mapOf(
            "config" to mapOf(
                "talk" to mapOf(
                    "interruptOnSpeech" to false,
                    "silenceTimeoutMs" to 700
                ),
                "session" to mapOf(
                    "mainKey" to "main"
                )
            )
        )
    }

    /**
     * talk.speak — 文字转语音
     *
     * 请求: { text, voiceId?, speed?, language?, ... }
     * 响应: { audioBase64, provider, mimeType, fileExtension }
     */
    fun talkSpeak(params: Any?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val p = params as? Map<String, Any?> ?: emptyMap()
        val text = p["text"] as? String
        if (text.isNullOrBlank()) {
            return mapOf("error" to "text is required")
        }

        // Wait for TTS engine to be ready
        if (!ttsReady) {
            initLatch.await(TTS_INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        val engine = tts
        if (engine == null || !ttsReady) {
            return mapOf("error" to "TTS engine not available")
        }

        // Apply optional parameters
        val speed = (p["speed"] as? Number)?.toFloat()
        if (speed != null && speed > 0.5f && speed < 2.0f) {
            engine.setSpeechRate(speed)
        } else {
            engine.setSpeechRate(1.0f)
        }

        val language = p["language"] as? String
        if (language != null && language.length == 2) {
            engine.language = Locale(language)
        }

        // Synthesize to temp WAV file
        val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")
        val synthLatch = CountDownLatch(1)
        var synthSuccess = false

        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                synthSuccess = true
                synthLatch.countDown()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                synthLatch.countDown()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS synthesis error: $errorCode")
                synthLatch.countDown()
            }
        })

        val utteranceId = "tts_${System.currentTimeMillis()}"
        val bundle = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = engine.synthesizeToFile(text, bundle, tempFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            tempFile.delete()
            return mapOf("error" to "TTS synthesizeToFile failed: $result")
        }

        // Wait for synthesis to complete
        synthLatch.await(TTS_SYNTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        if (!synthSuccess || !tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            return mapOf("error" to "TTS synthesis failed or produced empty audio")
        }

        // Read and base64 encode
        val audioBytes = tempFile.readBytes()
        tempFile.delete()
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        Log.d(TAG, "TTS synthesized ${audioBytes.size} bytes for ${text.take(50)}...")

        return mapOf(
            "audioBase64" to audioBase64,
            "provider" to "android-tts",
            "outputFormat" to "wav",
            "mimeType" to "audio/wav",
            "fileExtension" to ".wav"
        )
    }
}
