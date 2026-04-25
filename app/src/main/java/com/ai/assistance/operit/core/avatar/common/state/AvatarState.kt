package com.ai.assistance.operit.core.avatar.common.state

/**
 * Represents a snapshot of the avatar's current state.
 * This is an immutable data class, making state management predictable.
 *
 * @param emotion The current high-level emotional state of the avatar.
 * @param currentAnimation The name of the specific animation currently playing.
 *                         This could be null if no animation is active.
 * @param isLooping Whether the current animation is set to loop indefinitely.
 * @param playbackNonce A monotonically increasing token that allows renderers to restart the same animation.
 */
data class AvatarState(
    val emotion: AvatarEmotion = AvatarEmotion.IDLE,
    val currentAnimation: String? = null,
    val isLooping: Boolean = false,
    val playbackNonce: Long = 0L
)
