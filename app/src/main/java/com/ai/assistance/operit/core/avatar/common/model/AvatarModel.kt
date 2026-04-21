package com.ai.assistance.operit.core.avatar.common.model

/**
 * The base interface for any avatar data model.
 * All specific avatar model contracts will extend this interface.
 */
interface AvatarModel {
    /** A unique identifier for the avatar. */
    val id: String

    /** The display name of the avatar. */
    val name: String

    /** The underlying rendering technology used by this avatar. */
    val type: AvatarType
} 