package com.ai.assistance.operit.data.model

import com.google.gson.annotations.SerializedName

/**
 * The type of animation logic to apply to a model.
 */
enum class ModelType {
    /** Standard animation logic, suitable for humanoid or complex models (the "DragonBones-like" style). */
    STANDARD
}

/**
 * Represents a single DragonBones model's metadata.
 *
 * @param id A unique identifier for the model (e.g., "user_loong").
 * @param name The display name of the model (e.g., "loong").
 * @param folderPath The absolute path to the directory containing the model files.
 * @param skeletonFile The filename of the skeleton data (e.g., "loongbones-web.json").
 * @param textureJsonFile The filename of the texture atlas data (e.g., "loongbones-web_tex.json").
 * @param textureImageFile The filename of the texture atlas image (e.g., "loongbones-web_tex.png").
 * @param isBuiltIn True if the model is bundled with the app in assets, false if it's a user model.
 */
data class DragonBonesModel(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("folderPath") val folderPath: String,
        @SerializedName("skeletonFile") val skeletonFile: String,
        @SerializedName("textureJsonFile") val textureJsonFile: String,
        @SerializedName("textureImageFile") val textureImageFile: String,
        @SerializedName("isBuiltIn") val isBuiltIn: Boolean = false
)

/**
 * Represents the configuration for the DragonBones view.
 *
 * @param modelId The ID of the currently active model.
 * @param scale The global scale of the model.
 * @param translateX The global horizontal translation of the model.
 * @param translateY The global vertical translation of the model.
 */
data class DragonBonesConfig(
        @SerializedName("modelId") val modelId: String,
        @SerializedName("scale") val scale: Float = 1.0f,
        @SerializedName("translateX") val translateX: Float = 0f,
        @SerializedName("translateY") val translateY: Float = 0f
)
