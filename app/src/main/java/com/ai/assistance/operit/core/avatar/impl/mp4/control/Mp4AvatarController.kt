package com.ai.assistance.operit.core.avatar.impl.mp4.control

import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarMoodTypes
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import com.ai.assistance.operit.core.avatar.impl.mp4.model.Mp4AvatarModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class Mp4AvatarController(
    private val model: Mp4AvatarModel
) : AvatarController {

    private val _state = MutableStateFlow(AvatarState())
    override val state: StateFlow<AvatarState> = _state.asStateFlow()

    private val _scale = MutableStateFlow(1.0f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _translateX = MutableStateFlow(0.0f)
    val translateX: StateFlow<Float> = _translateX.asStateFlow()

    private val _translateY = MutableStateFlow(0.0f)
    val translateY: StateFlow<Float> = _translateY.asStateFlow()

    override val availableAnimations: List<String>
        get() = model.availableFiles

    private var emotionAnimationMapping: Map<AvatarEmotion, String> = emptyMap()
    private var triggerAnimationMapping: Map<String, String> = emptyMap()

    init {
        val initialAnimation =
            model.animationFileForEmotion(model.currentEmotion)
                ?: model.animationFileForEmotion(AvatarEmotion.IDLE)
                ?: model.availableFiles.firstOrNull()

        if (!initialAnimation.isNullOrBlank()) {
            _state.value = _state.value.copy(
                emotion = model.currentEmotion,
                currentAnimation = initialAnimation,
                isLooping = true
            )
        }
    }

    override fun setEmotion(newEmotion: AvatarEmotion) {
        playEmotion(newEmotion, loop = 0)
    }

    override fun playEmotion(emotion: AvatarEmotion, loop: Int) {
        val targetAnimation = resolveAnimationForEmotion(emotion) ?: return
        applyAnimation(emotion, targetAnimation, isLooping = loop == 0)
    }

    override fun playTrigger(triggerName: String, loop: Int): Boolean {
        val normalizedTrigger = AvatarMoodTypes.normalizeKey(triggerName)
        val targetAnimation = resolveAnimationForTrigger(normalizedTrigger) ?: return false
        val displayEmotion =
            AvatarMoodTypes.builtInFallbackEmotion(normalizedTrigger)
                ?: resolveModelEmotion(targetAnimation)
                ?: _state.value.emotion

        applyAnimation(displayEmotion, targetAnimation, isLooping = loop == 0)
        return true
    }

    override fun playAnimation(animationName: String, loop: Int) {
        val normalizedName = animationName.trim()
        if (normalizedName.isEmpty() || !model.availableFiles.contains(normalizedName)) {
            return
        }

        applyAnimation(
            displayEmotion = resolveModelEmotion(normalizedName) ?: _state.value.emotion,
            animationName = normalizedName,
            isLooping = loop == 0
        )
    }

    override fun estimateEmotionDurationMillis(emotion: AvatarEmotion): Long? {
        val animationName = resolveAnimationForEmotion(emotion) ?: return null
        val animationPath = File(model.basePath, animationName).absolutePath
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(animationPath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun estimateTriggerDurationMillis(triggerName: String): Long? {
        val animationName =
            resolveAnimationForTrigger(AvatarMoodTypes.normalizeKey(triggerName)) ?: return null
        val animationPath = File(model.basePath, animationName).absolutePath
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(animationPath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun lookAt(x: Float, y: Float) {
        // MP4 avatars do not support lookAt.
    }

    override fun updateSettings(settings: Map<String, Any>) {
        settings["scale"]?.let { if (it is Number) _scale.value = it.toFloat() }
        settings["translateX"]?.let { if (it is Number) _translateX.value = it.toFloat() }
        settings["translateY"]?.let { if (it is Number) _translateY.value = it.toFloat() }
    }

    override fun updateEmotionAnimationMapping(mapping: Map<AvatarEmotion, String>) {
        emotionAnimationMapping = mapping
            .mapValues { (_, animationName) -> animationName.trim() }
            .filterValues { animationName -> animationName.isNotBlank() }
    }

    override fun updateTriggerAnimationMapping(mapping: Map<String, String>) {
        triggerAnimationMapping =
            mapping.entries.mapNotNull { (rawKey, rawAnimationName) ->
                val key = AvatarMoodTypes.normalizeKey(rawKey)
                val animationName = rawAnimationName.trim()
                if (key.isBlank() || animationName.isBlank()) {
                    return@mapNotNull null
                }
                key to animationName
            }.toMap()
    }

    fun onAnimationPlaybackCompleted(animationName: String) {
        val currentState = _state.value
        if (currentState.isLooping) {
            return
        }
        if (currentState.currentAnimation != animationName) {
            return
        }

        setEmotion(AvatarEmotion.IDLE)
    }

    private fun resolveAnimationForEmotion(emotion: AvatarEmotion): String? {
        val preferred = emotionAnimationMapping[emotion]
        if (!preferred.isNullOrBlank() && model.availableFiles.contains(preferred)) {
            return preferred
        }

        val direct = model.animationFileForEmotion(emotion)
        if (!direct.isNullOrBlank() && model.availableFiles.contains(direct)) {
            return direct
        }

        if (emotion != AvatarEmotion.IDLE) {
            val idlePreferred = emotionAnimationMapping[AvatarEmotion.IDLE]
            if (!idlePreferred.isNullOrBlank() && model.availableFiles.contains(idlePreferred)) {
                return idlePreferred
            }

            val idleDirect = model.animationFileForEmotion(AvatarEmotion.IDLE)
            if (!idleDirect.isNullOrBlank() && model.availableFiles.contains(idleDirect)) {
                return idleDirect
            }
        }

        return model.availableFiles.firstOrNull()
    }

    private fun resolveAnimationForTrigger(triggerName: String): String? {
        val preferred = triggerAnimationMapping[triggerName]
        if (!preferred.isNullOrBlank() && model.availableFiles.contains(preferred)) {
            return preferred
        }

        return null
    }

    private fun resolveModelEmotion(animationName: String): AvatarEmotion? {
        return model.emotionToFileMap.entries
            .find { it.value == animationName }
            ?.key
    }

    private fun applyAnimation(
        displayEmotion: AvatarEmotion,
        animationName: String,
        isLooping: Boolean
    ) {
        if (!model.availableFiles.contains(animationName)) {
            return
        }

        _state.value = _state.value.copy(
            emotion = displayEmotion,
            currentAnimation = animationName,
            isLooping = isLooping,
            playbackNonce = _state.value.playbackNonce + 1
        )
    }
}

@Composable
fun rememberMp4AvatarController(model: Mp4AvatarModel): Mp4AvatarController {
    return remember(model) { Mp4AvatarController(model) }
}
