package com.ai.assistance.operit.core.avatar.common.state

/**
 * Defines the emotional states that an avatar can express.
 * This serves as a high-level abstraction for controlling avatar animations and behaviors.
 */
enum class AvatarEmotion {
    /** Neutral, default state. */
    IDLE,

    /** State of actively listening. */
    LISTENING,

    /** State of processing or thinking. */
    THINKING,

    /** A positive, happy emotion. */
    HAPPY,

    /** A negative, sad emotion. */
    SAD,

    /** Expressing confusion. */
    CONFUSED,

    /** Reacting with surprise. */
    SURPRISED
} 