package com.ai.assistance.operit.core.avatar.common.factory

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel

/**
 * A factory interface for creating [AvatarController] instances.
 * This abstraction allows the avatar system to create the appropriate controller
 * for a given avatar model without knowing the specific implementation details.
 */
interface AvatarControllerFactory {
    
    /**
     * Creates an appropriate controller for the given avatar model.
     * 
     * @param model The avatar model for which to create a controller.
     * @return An [AvatarController] instance that can manage the given model,
     *         or null if the model type is not supported by this factory.
     */
    @Composable
    fun createController(model: AvatarModel): AvatarController?
    
    /**
     * Checks if this factory can create a controller for the given model type.
     * 
     * @param model The avatar model to check.
     * @return true if this factory can create a controller for the model, false otherwise.
     */
    fun canCreateController(model: AvatarModel): Boolean
    
    /**
     * Gets the list of avatar model types supported by this factory.
     * 
     * @return A list of supported avatar model types.
     */
    val supportedTypes: List<String>
} 