package com.ai.assistance.operit.core.avatar.impl.fbx.model

import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import java.io.File

data class FbxAvatarModel(
    override val id: String,
    override val name: String,
    val basePath: String,
    val modelFile: String,
    val defaultAnimation: String? = null,
    val declaredAnimationNames: List<String> = emptyList()
) : AvatarModel {

    override val type: AvatarType
        get() = AvatarType.FBX

    val modelPath: String
        get() = File(basePath, modelFile).absolutePath

    val normalizedDeclaredAnimationNames: List<String>
        get() = declaredAnimationNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}
