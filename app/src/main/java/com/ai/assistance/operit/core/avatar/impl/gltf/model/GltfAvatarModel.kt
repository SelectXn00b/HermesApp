package com.ai.assistance.operit.core.avatar.impl.gltf.model

import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import java.io.File

data class GltfAvatarModel(
    override val id: String,
    override val name: String,
    val basePath: String,
    val modelFile: String,
    val defaultAnimation: String? = null,
    val declaredAnimationNames: List<String> = emptyList()
) : AvatarModel {

    override val type: AvatarType
        get() = AvatarType.GLTF

    val modelPath: String
        get() = File(basePath, modelFile).absolutePath

    val isBinaryGlb: Boolean
        get() = modelFile.endsWith(".glb", ignoreCase = true)

    val normalizedDeclaredAnimationNames: List<String>
        get() = declaredAnimationNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}
