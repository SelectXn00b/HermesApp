package com.ai.assistance.operit.core.avatar.impl.dragonbones.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.model.ISkeletalAvatarModel
import com.ai.assistance.operit.core.avatar.impl.dragonbones.control.DragonBonesAvatarController
import com.ai.assistance.operit.ui.components.ManagedDragonBonesView
import com.dragonbones.DragonBonesModel

/**
 * A Composable function responsible for rendering a DragonBones avatar.
 * It bridges the abstract avatar system with the concrete `ManagedDragonBonesView`.
 *
 * @param modifier The modifier to be applied to the view.
 * @param model The skeletal avatar model containing the asset paths.
 * @param controller The avatar controller that manages the avatar's state. It must be a
 *   [DragonBonesAvatarController] for this renderer to function.
 * @param onError A callback for handling rendering errors.
 */
@Composable
fun DragonBonesRenderer(
    modifier: Modifier,
    model: ISkeletalAvatarModel,
    controller: AvatarController,
    onError: (String) -> Unit
) {
    // This renderer requires a specific controller implementation.
    val dbController = controller as? DragonBonesAvatarController
        ?: throw IllegalArgumentException("DragonBonesRenderer requires a DragonBonesAvatarController")

    val dbModel = DragonBonesModel(
        skeletonPath = model.skeletonPath,
        textureJsonPath = model.textureAtlasPath,
        textureImagePath = model.texturePath
    )

    ManagedDragonBonesView(
        modifier = modifier,
        model = dbModel,
        controller = dbController.libController,
        onError = onError
    )
} 