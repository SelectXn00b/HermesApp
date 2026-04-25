package com.ai.assistance.operit.core.avatar.common.factory

import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType

/**
 * A factory interface for creating [AvatarModel] instances from various data sources.
 * This abstraction allows the avatar system to convert data-layer models into
 * core-layer avatar models without coupling to specific data implementations.
 */
interface AvatarModelFactory {
    
    /**
     * Creates an avatar model from raw data.
     * 
     * @param id The unique identifier for the avatar.
     * @param name The display name for the avatar.
     * @param type The type of avatar to create.
     * @param data A map containing the configuration data for the avatar.
     * @return An [AvatarModel] instance, or null if the data is invalid or type is unsupported.
     */
    fun createModel(
        id: String,
        name: String,
        type: AvatarType,
        data: Map<String, Any>
    ): AvatarModel?
    
    /**
     * Creates an avatar model from a data layer object.
     * This method uses reflection or type checking to convert data objects
     * into appropriate avatar models.
     * 
     * @param dataModel The data layer model object.
     * @return An [AvatarModel] instance, or null if the data model type is unsupported.
     */
    fun createModelFromData(dataModel: Any): AvatarModel?
    
    /**
     * Creates a default avatar model for the specified type.
     * This is useful for creating placeholder or example avatars.
     * 
     * @param type The type of avatar to create.
     * @param baseName The base name for the default avatar (optional).
     * @return An [AvatarModel] instance with default settings for the type.
     */
    fun createDefaultModel(type: AvatarType, baseName: String = "Default"): AvatarModel?
    
    /**
     * Validates if the provided data can be used to create an avatar model.
     * 
     * @param type The avatar type to validate for.
     * @param data The data to validate.
     * @return true if the data is valid for creating the specified avatar type.
     */
    fun validateData(type: AvatarType, data: Map<String, Any>): Boolean
    
    /**
     * Gets the list of avatar types supported by this factory.
     * 
     * @return A list of supported [AvatarType] values.
     */
    val supportedTypes: List<AvatarType>
    
    /**
     * Gets the required data keys for creating a specific avatar type.
     * 
     * @param type The avatar type to get requirements for.
     * @return A list of required data keys, or empty list if type is unsupported.
     */
    fun getRequiredDataKeys(type: AvatarType): List<String>
} 