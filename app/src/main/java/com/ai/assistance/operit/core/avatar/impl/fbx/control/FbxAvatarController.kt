package com.ai.assistance.operit.core.avatar.impl.fbx.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.control.AvatarSettingKeys
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarMoodTypes
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import com.ai.assistance.operit.core.avatar.impl.fbx.model.FbxAvatarModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FbxAvatarController(
    private val model: FbxAvatarModel
) : AvatarController {

    private val _state = MutableStateFlow(AvatarState())
    override val state: StateFlow<AvatarState> = _state.asStateFlow()

    private val _scale = MutableStateFlow(1.0f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _translateX = MutableStateFlow(0.0f)
    val translateX: StateFlow<Float> = _translateX.asStateFlow()

    private val _translateY = MutableStateFlow(0.0f)
    val translateY: StateFlow<Float> = _translateY.asStateFlow()

    private val _cameraPitch = MutableStateFlow(8.0f)
    val cameraPitch: StateFlow<Float> = _cameraPitch.asStateFlow()

    private val _cameraYaw = MutableStateFlow(0.0f)
    val cameraYaw: StateFlow<Float> = _cameraYaw.asStateFlow()

    private val _cameraDistanceScale = MutableStateFlow(1.0f)
    val cameraDistanceScale: StateFlow<Float> = _cameraDistanceScale.asStateFlow()

    private val _cameraTargetHeight = MutableStateFlow(0.0f)
    val cameraTargetHeight: StateFlow<Float> = _cameraTargetHeight.asStateFlow()

    private val _availableAnimations = MutableStateFlow(model.normalizedDeclaredAnimationNames)
    override val availableAnimations: List<String>
        get() = _availableAnimations.value

    private var emotionAnimationMapping: Map<AvatarEmotion, String> = emptyMap()
    private var triggerAnimationMapping: Map<String, String> = emptyMap()
    private var animationDurationMillisByName: Map<String, Long> = emptyMap()

    fun updateAnimationMetadata(
        discoveredAnimations: List<String>,
        durationMillisByName: Map<String, Long>
    ) {
        val normalizedAnimations =
            discoveredAnimations
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

        val mergedAnimations =
            if (normalizedAnimations.isNotEmpty()) {
                normalizedAnimations
            } else {
                model.normalizedDeclaredAnimationNames
            }

        _availableAnimations.value = mergedAnimations
        animationDurationMillisByName =
            durationMillisByName
                .filterKeys { animationName -> mergedAnimations.contains(animationName) }
                .mapValues { (_, durationMillis) -> durationMillis.coerceAtLeast(1L) }

        if (mergedAnimations.isEmpty()) {
            if (_state.value.currentAnimation != null) {
                _state.value = _state.value.copy(currentAnimation = null, isLooping = false)
            }
            return
        }

        val currentAnimation = _state.value.currentAnimation
        if (currentAnimation != null && mergedAnimations.contains(currentAnimation)) {
            return
        }

        val defaultAnimation = model.defaultAnimation?.trim()
        val nextAnimation =
            if (!defaultAnimation.isNullOrBlank() && mergedAnimations.contains(defaultAnimation)) {
                defaultAnimation
            } else {
                mergedAnimations.firstOrNull()
            }

        _state.value = _state.value.copy(currentAnimation = nextAnimation, isLooping = nextAnimation != null)
    }

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
        return animationDurationMillisByName[animationName]
    }

    override fun estimateTriggerDurationMillis(triggerName: String): Long? {
        val animationName = resolveAnimationForTrigger(AvatarMoodTypes.normalizeKey(triggerName)) ?: return null
        return animationDurationMillisByName[animationName]
    }

    override fun playAnimation(animationName: String, loop: Int) {
        val normalizedName = animationName.trim()
        if (normalizedName.isEmpty()) {
            return
        }
        if (availableAnimations.isNotEmpty() && !availableAnimations.contains(normalizedName)) {
            return
        }

        _state.value =
            _state.value.copy(
                currentAnimation = normalizedName,
                isLooping = loop == 0,
                playbackNonce = _state.value.playbackNonce + 1
            )
    }

    override fun lookAt(x: Float, y: Float) {
    }

    override fun updateSettings(settings: Map<String, Any>) {
        settings[AvatarSettingKeys.SCALE]?.let { if (it is Number) _scale.value = it.toFloat() }
        settings[AvatarSettingKeys.TRANSLATE_X]?.let { if (it is Number) _translateX.value = it.toFloat() }
        settings[AvatarSettingKeys.TRANSLATE_Y]?.let { if (it is Number) _translateY.value = it.toFloat() }

        settings[AvatarSettingKeys.FBX_CAMERA_PITCH]?.let {
            if (it is Number) {
                _cameraPitch.value = it.toFloat().coerceIn(-89f, 89f)
            }
        }
        settings[AvatarSettingKeys.FBX_CAMERA_YAW]?.let {
            if (it is Number) {
                _cameraYaw.value = it.toFloat().coerceIn(-180f, 180f)
            }
        }
        settings[AvatarSettingKeys.FBX_CAMERA_DISTANCE_SCALE]?.let {
            if (it is Number) {
                _cameraDistanceScale.value = it.toFloat().coerceIn(0.02f, 12.0f)
            }
        }
        settings[AvatarSettingKeys.FBX_CAMERA_TARGET_HEIGHT]?.let {
            if (it is Number) {
                _cameraTargetHeight.value = it.toFloat().coerceIn(-2.0f, 2.0f)
            }
        }
    }

    override fun updateEmotionAnimationMapping(mapping: Map<AvatarEmotion, String>) {
        emotionAnimationMapping =
            mapping
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
        if (!preferred.isNullOrBlank()) {
            if (availableAnimations.isEmpty() || availableAnimations.contains(preferred)) {
                return preferred
            }
        }

        val idleFallback = emotionAnimationMapping[AvatarEmotion.IDLE]
        if (!idleFallback.isNullOrBlank()) {
            if (availableAnimations.isEmpty() || availableAnimations.contains(idleFallback)) {
                return idleFallback
            }
        }

        return availableAnimations.firstOrNull()
    }

    private fun resolveAnimationForTrigger(triggerName: String): String? {
        val preferred = triggerAnimationMapping[triggerName]
        if (!preferred.isNullOrBlank()) {
            if (availableAnimations.isEmpty() || availableAnimations.contains(preferred)) {
                return preferred
            }
        }

        return availableAnimations.firstOrNull { animationName ->
            animationName.equals(triggerName, ignoreCase = true)
        }
    }
}

@Composable
fun rememberFbxAvatarController(model: FbxAvatarModel): FbxAvatarController {
    return remember(model) { FbxAvatarController(model) }
}
