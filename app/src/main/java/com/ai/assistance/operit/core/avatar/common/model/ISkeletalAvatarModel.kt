package com.ai.assistance.operit.core.avatar.common.model

/**
 * An abstract contract for avatar models that use skeletal animation (e.g., DragonBones, Spine).
 *
 * This interface defines the asset paths required to load and render a skeletal avatar.
 * The concrete implementation will provide these paths.
 */
interface ISkeletalAvatarModel : AvatarModel {
    /** The asset path to the skeleton data file (e.g., "avatars/my_db_char/character_ske.json"). */
    val skeletonPath: String

    /** The asset path to the texture atlas data file (e.g., "avatars/my_db_char/character_tex.json"). */
    val textureAtlasPath: String

    /** The asset path to the texture atlas image file (e.g., "avatars/my_db_char/character_tex.png"). */
    val texturePath: String
} 