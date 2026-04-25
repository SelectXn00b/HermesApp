package com.ai.assistance.operit.core.avatar.impl.dragonbones.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.control.AvatarSettingKeys
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarMoodTypes
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import com.dragonbones.JniBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.dragonbones.DragonBonesController as DragonBonesLibController

/**
 * A concrete implementation of [AvatarController] for DragonBones avatars.
 * It wraps the library-specific [DragonBonesLibController] to expose
 * a standardized API for avatar control.
 *
 * @param libController The underlying controller from the DragonBones rendering library.
 */
class DragonBonesAvatarController(
    val libController: DragonBonesLibController
) : AvatarController {

    private val _state = MutableStateFlow(AvatarState())
    override val state: StateFlow<AvatarState> = _state.asStateFlow()

    override val availableAnimations: List<String>
        get() = libController.animationNames

    private var emotionAnimationMapping: Map<AvatarEmotion, String> = emptyMap()
    private var triggerAnimationMapping: Map<String, String> = emptyMap()

    override fun setEmotion(newEmotion: AvatarEmotion) {
        playEmotion(newEmotion, loop = 0)
    }

    override fun playEmotion(emotion: AvatarEmotion, loop: Int) {
        val animationName = resolveAnimationForEmotion(emotion) ?: return

        libController.playAnimation(animationName, loop.toFloat())
        _state.value = _state.value.copy(
            emotion = emotion,
            currentAnimation = animationName,
            isLooping = loop == 0
        )
    }

    override fun playTrigger(triggerName: String, loop: Int): Boolean {
        val normalizedTrigger = AvatarMoodTypes.normalizeKey(triggerName)
        val animationName = resolveAnimationForTrigger(normalizedTrigger) ?: return false

        libController.playAnimation(animationName, loop.toFloat())
        _state.value = _state.value.copy(
            emotion =
                AvatarMoodTypes.builtInFallbackEmotion(normalizedTrigger)
                    ?: resolveEmotionFromAnimationName(animationName)
                    ?: _state.value.emotion,
            currentAnimation = animationName,
            isLooping = loop == 0
        )
        return true
    }

    override fun estimateEmotionDurationMillis(emotion: AvatarEmotion): Long? {
        val animationName = resolveAnimationForEmotion(emotion) ?: return null
        val durationSeconds = JniBridge.getAnimationDuration(animationName)
        if (!durationSeconds.isFinite() || durationSeconds <= 0f) {
            return null
        }

        return (durationSeconds * 1000f).toLong().coerceAtLeast(1L)
    }

    override fun estimateTriggerDurationMillis(triggerName: String): Long? {
        val animationName =
            resolveAnimationForTrigger(AvatarMoodTypes.normalizeKey(triggerName)) ?: return null
        val durationSeconds = JniBridge.getAnimationDuration(animationName)
        if (!durationSeconds.isFinite() || durationSeconds <= 0f) {
            return null
        }

        return (durationSeconds * 1000f).toLong().coerceAtLeast(1L)
    }

    override fun playAnimation(animationName: String, loop: Int) {
        if (availableAnimations.contains(animationName)) {
            libController.playAnimation(animationName, loop.toFloat())
            _state.value = _state.value.copy(
                currentAnimation = animationName,
                isLooping = loop == 0
            )
        }
    }

    // `lookAt` is not supported by the DragonBones implementation.
    override fun lookAt(x: Float, y: Float) {
        // No-op
    }

    override fun updateSettings(settings: Map<String, Any>) {
        settings[AvatarSettingKeys.SCALE]
            ?.let { it as? Number }
            ?.toFloat()
            ?.let { scale ->
                val normalizedScale =
                    if (scale.isFinite()) {
                        scale.coerceIn(0.1f, 5.0f)
                    } else {
                        0.5f
                    }
                libController.scale = normalizedScale
            }

        settings[AvatarSettingKeys.TRANSLATE_X]
            ?.let { it as? Number }
            ?.toFloat()
            ?.let { translateX ->
                if (translateX.isFinite()) {
                    libController.translationX = translateX.coerceIn(-2000f, 2000f)
                }
            }

        settings[AvatarSettingKeys.TRANSLATE_Y]
            ?.let { it as? Number }
            ?.toFloat()
            ?.let { translateY ->
                if (translateY.isFinite()) {
                    libController.translationY = translateY.coerceIn(-2000f, 2000f)
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

        val directName = emotion.name.lowercase()
        if (availableAnimations.contains(directName)) {
            return directName
        }

        if (emotion != AvatarEmotion.IDLE) {
            val idleFallback = emotionAnimationMapping[AvatarEmotion.IDLE]
            if (!idleFallback.isNullOrBlank() && availableAnimations.contains(idleFallback)) {
                return idleFallback
            }

            val idleName = AvatarEmotion.IDLE.name.lowercase()
            if (availableAnimations.contains(idleName)) {
                return idleName
            }
        }

        return null
    }

    private fun resolveAnimationForTrigger(triggerName: String): String? {
        val preferred = triggerAnimationMapping[triggerName]
        if (!preferred.isNullOrBlank() && availableAnimations.contains(preferred)) {
            return preferred
        }

        if (availableAnimations.contains(triggerName)) {
            return triggerName
        }

        return null
    }

    private fun resolveEmotionFromAnimationName(animationName: String): AvatarEmotion? {
        return AvatarEmotion.values().firstOrNull { emotion ->
            emotion.name.lowercase() == animationName
        }
    }
}

/**
 * A Composable function to create and remember a [DragonBonesAvatarController].
 * This follows the standard pattern for creating controllers in Jetpack Compose.
 * It ensures the controller instance is preserved across recompositions.
 *
 * @return An instance of [DragonBonesAvatarController].
 */
@Composable
fun rememberDragonBonesAvatarController(): DragonBonesAvatarController {
    val libController = com.dragonbones.rememberDragonBonesController()
    return remember { DragonBonesAvatarController(libController) }
} 
