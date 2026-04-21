package com.ai.assistance.operit.core.avatar.common.model

/**
 * Enumerates the supported rendering technologies for virtual avatars.
 * This serves as a discriminator for factories to decide which renderer to use.
 */
enum class AvatarType {
    /** 2D skeletal animation (e.g., DragonBones, Spine). */
    DRAGONBONES,

    /** Frame-by-frame animation using WEBP format. */
    WEBP,

    /** Video-based animation using MP4 format. */
    MP4,

    /** Real-time 3D glTF/glb model animation. */
    GLTF,

    /** Real-time 3D FBX model animation. */
    FBX,

    /** 3D model animation (e.g., MikuMikuDance, VRM). */
    MMD
} 
