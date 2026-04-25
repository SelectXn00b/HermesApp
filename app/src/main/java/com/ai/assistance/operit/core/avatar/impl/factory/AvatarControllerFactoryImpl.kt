package com.ai.assistance.operit.core.avatar.impl.factory

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.factory.AvatarControllerFactory
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.model.ISkeletalAvatarModel
import com.ai.assistance.operit.core.avatar.impl.dragonbones.control.rememberDragonBonesAvatarController
import com.ai.assistance.operit.core.avatar.impl.fbx.control.rememberFbxAvatarController
import com.ai.assistance.operit.core.avatar.impl.fbx.model.FbxAvatarModel
import com.ai.assistance.operit.core.avatar.impl.gltf.control.rememberGltfAvatarController
import com.ai.assistance.operit.core.avatar.impl.gltf.model.GltfAvatarModel
import com.ai.assistance.operit.core.avatar.impl.mmd.control.rememberMmdAvatarController
import com.ai.assistance.operit.core.avatar.impl.mmd.model.MmdAvatarModel
import com.ai.assistance.operit.core.avatar.impl.mp4.control.rememberMp4AvatarController
import com.ai.assistance.operit.core.avatar.impl.mp4.model.Mp4AvatarModel
import com.ai.assistance.operit.core.avatar.impl.webp.control.rememberWebPAvatarController
import com.ai.assistance.operit.core.avatar.impl.webp.model.WebPAvatarModel

class AvatarControllerFactoryImpl : AvatarControllerFactory {

    @Composable
    override fun createController(model: AvatarModel): AvatarController? {
        return when (model.type) {
            AvatarType.DRAGONBONES -> {
                val skeletalModel = model as? ISkeletalAvatarModel
                if (skeletalModel != null) {
                    rememberDragonBonesAvatarController()
                } else {
                    null
                }
            }
            AvatarType.WEBP -> {
                val webpModel = model as? WebPAvatarModel
                if (webpModel != null) {
                    rememberWebPAvatarController(webpModel)
                } else {
                    null
                }
            }
            AvatarType.MP4 -> {
                val mp4Model = model as? Mp4AvatarModel
                if (mp4Model != null) {
                    rememberMp4AvatarController(mp4Model)
                } else {
                    null
                }
            }
            AvatarType.MMD -> {
                val mmdModel = model as? MmdAvatarModel
                if (mmdModel != null) {
                    rememberMmdAvatarController(mmdModel)
                } else {
                    null
                }
            }
            AvatarType.GLTF -> {
                val gltfModel = model as? GltfAvatarModel
                if (gltfModel != null) {
                    rememberGltfAvatarController(gltfModel)
                } else {
                    null
                }
            }
            AvatarType.FBX -> {
                val fbxModel = model as? FbxAvatarModel
                if (fbxModel != null) {
                    rememberFbxAvatarController(fbxModel)
                } else {
                    null
                }
            }
        }
    }

    override fun canCreateController(model: AvatarModel): Boolean {
        return when (model.type) {
            AvatarType.DRAGONBONES -> model is ISkeletalAvatarModel
            AvatarType.WEBP -> model is WebPAvatarModel
            AvatarType.MP4 -> model is Mp4AvatarModel
            AvatarType.MMD -> model is MmdAvatarModel
            AvatarType.GLTF -> model is GltfAvatarModel
            AvatarType.FBX -> model is FbxAvatarModel
        }
    }

    override val supportedTypes: List<String>
        get() = listOf(
            AvatarType.DRAGONBONES.name,
            AvatarType.WEBP.name,
            AvatarType.MP4.name,
            AvatarType.MMD.name,
            AvatarType.GLTF.name,
            AvatarType.FBX.name
        )
}
