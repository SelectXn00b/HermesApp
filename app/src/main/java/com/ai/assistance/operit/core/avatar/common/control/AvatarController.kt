package com.ai.assistance.operit.core.avatar.common.control

import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import kotlinx.coroutines.flow.StateFlow

/**
 * A universal interface for controlling an avatar's state and behavior.
 * This abstracts away the specific implementation details of the rendering engine.
 */
interface AvatarController {

    /** A flow representing the current state of the avatar. UI components can collect this flow to react to state changes. */
    val state: StateFlow<AvatarState>

    /** For skeletal models, this provides a list of all available animation names. For other types, it may be empty. */
    val availableAnimations: List<String>

    /**
     * Sets the avatar's emotional state.
     * The controller's implementation is responsible for selecting and playing an appropriate animation
     * that corresponds to this emotion.
     *
     * @param newEmotion The new emotional state to set.
     */
    fun setEmotion(newEmotion: AvatarEmotion)

    /**
     * Plays the animation mapped from a high-level emotion.
     *
     * @param emotion The target emotion.
     * @param loop The number of times to loop the mapped animation. Use 0 for infinite looping.
     */
    fun playEmotion(emotion: AvatarEmotion, loop: Int = 0) {
        setEmotion(emotion)
    }

    /**
     * Directly plays a specific animation by name.
     * This is primarily useful for skeletal animation systems that have a rich set of named animations.
     *
     * @param animationName The name of the animation to play.
     * @param loop The number of times to loop the animation. Use 0 for infinite looping.
     */
    fun playAnimation(animationName: String, loop: Int = 1)

    /**
     * Returns the estimated duration, in milliseconds, of the animation mapped from the given emotion.
     * Controllers can return null when they cannot determine a precise duration.
     */
    fun estimateEmotionDurationMillis(emotion: AvatarEmotion): Long? = null

    /**
     * Plays an animation trigger identified by an arbitrary string key, such as a custom
     * `<mood>` tag value returned by the model.
     *
     * @return true if the trigger could be resolved and playback started.
     */
    fun playTrigger(triggerName: String, loop: Int = 0): Boolean = false

    /**
     * Returns the estimated duration for a trigger-based animation, in milliseconds.
     */
    fun estimateTriggerDurationMillis(triggerName: String): Long? = null

    /**
     * Instructs the avatar to look at a specific point on the screen.
     * This is an advanced feature that may only be supported by certain avatar types.
     * Implementations for unsupported types should do nothing.
     *
     * @param x The normalized x-coordinate (-1 to 1).
     * @param y The normalized y-coordinate (-1 to 1).
     */
    fun lookAt(x: Float, y: Float)
    
    /**
     * Updates avatar-specific settings, such as scale or position.
     * Each controller implementation should handle the settings relevant to it.
     *
     * @param settings A map of setting keys to values.
     */
    fun updateSettings(settings: Map<String, Any>) {}

    /**
     * Updates an optional mapping from high-level emotions to model-specific animation names.
     * Controllers that don't need this behavior can ignore it.
     */
    fun updateEmotionAnimationMapping(mapping: Map<AvatarEmotion, String>) {}

    /**
     * Updates an optional mapping from arbitrary trigger keys (for example, `<mood>sleepy</mood>`)
     * to model-specific animation names.
     */
    fun updateTriggerAnimationMapping(mapping: Map<String, String>) {}
} 
