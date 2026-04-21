package com.ai.assistance.operit.core.avatar.common.view

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.factory.AvatarRendererFactory

/**
 * The single, unified Composable for displaying any virtual avatar.
 *
 * This view is completely decoupled from any specific rendering technology. It uses a
 * [AvatarRendererFactory] (which must be provided, likely via dependency injection)
 * to obtain the correct renderer Composable for the given [AvatarModel].
 *
 * @param modifier The modifier to be applied to the avatar container.
 * @param model The data model of the avatar to render.
 * @param controller The controller instance for interacting with the avatar's state.
 * @param rendererFactory The factory that provides the concrete rendering implementation.
 * @param onError A callback to handle errors, such as when a renderer is not available for the model type.
 */
@Composable
fun AvatarView(
    modifier: Modifier = Modifier,
    model: AvatarModel,
    controller: AvatarController,
    rendererFactory: AvatarRendererFactory,
    onError: (String) -> Unit = {}
) {
    val renderer = rendererFactory.createRenderer(model)

    if (renderer != null) {
        renderer(modifier, controller)
    } else {
        val errorMessage = "Unsupported AvatarModel type: ${model.type}"
        onError(errorMessage)
        // Display a fallback UI
        Text(modifier = modifier, text = errorMessage)
    }
} 