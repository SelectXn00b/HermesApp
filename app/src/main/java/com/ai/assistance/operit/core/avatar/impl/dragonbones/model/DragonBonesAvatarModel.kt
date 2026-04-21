package com.ai.assistance.operit.core.avatar.impl.dragonbones.model

import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.model.ISkeletalAvatarModel
import java.io.File
import com.ai.assistance.operit.data.model.DragonBonesModel as DragonBonesDataModel

/**
 * A concrete implementation of [ISkeletalAvatarModel] for DragonBones avatars.
 * This class acts as an adapter, wrapping the data-layer [DragonBonesDataModel]
 * to provide the necessary asset paths required by the avatar system.
 *
 * @property dataModel The underlying data model from the repository.
 */
data class DragonBonesAvatarModel(
    val dataModel: DragonBonesDataModel
) : ISkeletalAvatarModel {

    override val id: String
        get() = dataModel.id

    override val name: String
        get() = dataModel.name

    override val type: AvatarType
        get() = AvatarType.DRAGONBONES

    override val skeletonPath: String
        get() = File(dataModel.folderPath, dataModel.skeletonFile).absolutePath

    override val textureAtlasPath: String
        get() = File(dataModel.folderPath, dataModel.textureJsonFile).absolutePath

    override val texturePath: String
        get() = File(dataModel.folderPath, dataModel.textureImageFile).absolutePath
}

/**
 * Extension function to conveniently convert a data-layer [DragonBonesDataModel]
 * into a core-layer [DragonBonesAvatarModel].
 */
fun DragonBonesDataModel.toAvatarModel(): DragonBonesAvatarModel {
    return DragonBonesAvatarModel(this)
} 