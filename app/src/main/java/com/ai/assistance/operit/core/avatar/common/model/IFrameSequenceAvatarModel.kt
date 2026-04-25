package com.ai.assistance.operit.core.avatar.common.model

/**
 * An abstract contract for avatar models that use frame sequence animation (e.g., WebP, GIF).
 *
 * This interface defines the asset paths and animation configurations required to load
 * and render a frame-based animated avatar.
 */
interface IFrameSequenceAvatarModel : AvatarModel {
    /** The asset path to the animation file (e.g., "pets/emoji/happy.webp"). */
    val animationPath: String
    
    /** Whether the animation should loop infinitely. */
    val shouldLoop: Boolean
        get() = true
    
    /** The repeat count for the animation (0 means infinite loop). */
    val repeatCount: Int
        get() = 0
} 