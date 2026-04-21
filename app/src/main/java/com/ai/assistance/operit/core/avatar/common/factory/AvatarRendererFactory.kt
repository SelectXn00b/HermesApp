package com.ai.assistance.operit.core.avatar.common.factory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel

/**
 * An interface for a factory that creates avatar renderers.
 * The concrete implementation of this factory will live in the implementation layer
 * and will know about all the concrete renderer classes.
 */
interface AvatarRendererFactory {
    /**
     * Creates a renderer instance capable of displaying the given avatar model.
     * @param model The avatar data model.
     * @return A composable function that renders the avatar, or null if the model type is not supported.
     */
    @Composable
    fun createRenderer(model: AvatarModel): @Composable ((modifier: Modifier, controller: AvatarController) -> Unit)?
} 