package com.ai.assistance.operit.core.avatar.impl.mmd.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.mmd.MmdNative
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.control.AvatarSettingKeys
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarMoodTypes
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import java.io.File
import kotlin.math.roundToLong
import com.ai.assistance.operit.core.avatar.impl.mmd.model.MmdAvatarModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MmdAvatarController(
    private val model: MmdAvatarModel
) : AvatarController {

    private val _state = MutableStateFlow(AvatarState())
    override val state: StateFlow<AvatarState> = _state.asStateFlow()

    private val _scale = MutableStateFlow(1.0f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _translateX = MutableStateFlow(0.0f)
    val translateX: StateFlow<Float> = _translateX.asStateFlow()

    private val _translateY = MutableStateFlow(0.0f)
    val translateY: StateFlow<Float> = _translateY.asStateFlow()

    private val _initialRotationX = MutableStateFlow(0.0f)
    val initialRotationX: StateFlow<Float> = _initialRotationX.asStateFlow()

    private val _initialRotationY = MutableStateFlow(0.0f)
    val initialRotationY: StateFlow<Float> = _initialRotationY.asStateFlow()

    private val _initialRotationZ = MutableStateFlow(0.0f)
    val initialRotationZ: StateFlow<Float> = _initialRotationZ.asStateFlow()

    private val _cameraDistanceScale = MutableStateFlow(1.0f)
    val cameraDistanceScale: StateFlow<Float> = _cameraDistanceScale.asStateFlow()

    private val _cameraTargetHeight = MutableStateFlow(0.0f)
    val cameraTargetHeight: StateFlow<Float> = _cameraTargetHeight.asStateFlow()

    override val availableAnimations: List<String>
        get() = model.displayMotionNames

    private var emotionAnimationMapping: Map<AvatarEmotion, String> = emptyMap()
    private var triggerAnimationMapping: Map<String, String> = emptyMap()

    override fun setEmotion(newEmotion: AvatarEmotion) {
        playEmotion(newEmotion, loop = 0)
    }

    override fun playEmotion(emotion: AvatarEmotion, loop: Int) {
        _state.value = _state.value.copy(emotion = emotion)

        resolveAnimationForEmotion(emotion)?.let { animationName ->
            playAnimation(animationName, loop)
        }
    }

    override fun playTrigger(triggerName: String, loop: Int): Boolean {
        val normalizedTrigger = AvatarMoodTypes.normalizeKey(triggerName)
        val animationName = resolveAnimationForTrigger(normalizedTrigger) ?: return false
        _state.value =
            _state.value.copy(
                emotion = AvatarMoodTypes.builtInFallbackEmotion(normalizedTrigger) ?: _state.value.emotion
            )
        playAnimation(animationName, loop)
        return true
    }

    override fun estimateEmotionDurationMillis(emotion: AvatarEmotion): Long? {
        val animationName = resolveAnimationForEmotion(emotion) ?: return null
        val motionPath = File(model.basePath, animationName).absolutePath
        val maxFrame = MmdNative.nativeReadMotionMaxFrame(motionPath)
        if (maxFrame <= 0) {
            return null
        }

        return ((maxFrame / 30f) * 1000f).roundToLong().coerceAtLeast(1L)
    }

    override fun estimateTriggerDurationMillis(triggerName: String): Long? {
        val animationName =
            resolveAnimationForTrigger(AvatarMoodTypes.normalizeKey(triggerName)) ?: return null
        val motionPath = File(model.basePath, animationName).absolutePath
        val maxFrame = MmdNative.nativeReadMotionMaxFrame(motionPath)
        if (maxFrame <= 0) {
            return null
        }

        return ((maxFrame / 30f) * 1000f).roundToLong().coerceAtLeast(1L)
    }

    override fun playAnimation(animationName: String, loop: Int) {
        if (!availableAnimations.contains(animationName)) {
            return
        }

        _state.value = _state.value.copy(
            currentAnimation = null,
            isLooping = false
        )
        _state.value = _state.value.copy(
            currentAnimation = animationName,
            isLooping = loop == 0
        )
    }

    override fun lookAt(x: Float, y: Float) {
    }

    override fun updateSettings(settings: Map<String, Any>) {
        settings[AvatarSettingKeys.SCALE]?.let { if (it is Number) _scale.value = it.toFloat() }
        settings[AvatarSettingKeys.TRANSLATE_X]?.let { if (it is Number) _translateX.value = it.toFloat() }
        settings[AvatarSettingKeys.TRANSLATE_Y]?.let { if (it is Number) _translateY.value = it.toFloat() }

        settings[AvatarSettingKeys.MMD_INITIAL_ROTATION_X]?.let {
            if (it is Number) {
                _initialRotationX.value = it.toFloat()
            }
        }
        settings[AvatarSettingKeys.MMD_INITIAL_ROTATION_Y]?.let {
            if (it is Number) {
                _initialRotationY.value = it.toFloat()
            }
        }
        settings[AvatarSettingKeys.MMD_INITIAL_ROTATION_Z]?.let {
            if (it is Number) {
                _initialRotationZ.value = it.toFloat()
            }
        }

        settings[AvatarSettingKeys.MMD_CAMERA_DISTANCE_SCALE]?.let {
            if (it is Number) {
                _cameraDistanceScale.value = it.toFloat().coerceIn(0.02f, 12.0f)
            }
        }
        settings[AvatarSettingKeys.MMD_CAMERA_TARGET_HEIGHT]?.let {
            if (it is Number) {
                _cameraTargetHeight.value = it.toFloat().coerceIn(-2.0f, 2.0f)
            }
        }
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

    private fun resolveAnimationForEmotion(emotion: AvatarEmotion): String? {
        val preferred = emotionAnimationMapping[emotion]
        if (!preferred.isNullOrBlank() && availableAnimations.contains(preferred)) {
            return preferred
        }

        val idleFallback = emotionAnimationMapping[AvatarEmotion.IDLE]
        if (!idleFallback.isNullOrBlank() && availableAnimations.contains(idleFallback)) {
            return idleFallback
        }

        return null
    }

    private fun resolveAnimationForTrigger(triggerName: String): String? {
        val preferred = triggerAnimationMapping[triggerName]
        if (!preferred.isNullOrBlank() && availableAnimations.contains(preferred)) {
            return preferred
        }

        return availableAnimations.firstOrNull { animationName ->
            animationName.equals(triggerName, ignoreCase = true)
        }
    }
}

@Composable
fun rememberMmdAvatarController(model: MmdAvatarModel): MmdAvatarController {
    return remember(model) { MmdAvatarController(model) }
}
