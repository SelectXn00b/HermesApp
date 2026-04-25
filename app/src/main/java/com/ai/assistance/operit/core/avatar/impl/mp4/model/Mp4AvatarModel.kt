package com.ai.assistance.operit.core.avatar.impl.mp4.model

import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion

data class Mp4AvatarModel(
    override val id: String,
    override val name: String,
    val basePath: String,
    val emotionToFileMap: Map<AvatarEmotion, String>,
    val availableFiles: List<String> =
        emotionToFileMap.values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct(),
    val currentEmotion: AvatarEmotion = AvatarEmotion.IDLE
) : AvatarModel {

    override val type: AvatarType
        get() = AvatarType.MP4

    fun animationPathFor(animationFile: String?): String {
        val fileName =
            animationFile?.trim()?.takeIf { it.isNotEmpty() }
                ?: animationFileForEmotion(currentEmotion)
                ?: animationFileForEmotion(AvatarEmotion.IDLE)
                ?: availableFiles.firstOrNull()
                ?: ""
        return if (fileName.isNotEmpty()) {
            "$basePath/$fileName"
        } else {
            ""
        }
    }

    fun animationFileForEmotion(emotion: AvatarEmotion): String? {
        return emotionToFileMap[emotion]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        fun createStandard(
            id: String,
            name: String,
            basePath: String,
            fileExtension: String = "mp4"
        ): Mp4AvatarModel {
            val emotionMap = mapOf(
                AvatarEmotion.IDLE to "idle.$fileExtension",
                AvatarEmotion.LISTENING to "listening.$fileExtension",
                AvatarEmotion.THINKING to "thinking.$fileExtension",
                AvatarEmotion.HAPPY to "happy.$fileExtension",
                AvatarEmotion.SAD to "sad.$fileExtension",
                AvatarEmotion.CONFUSED to "confused.$fileExtension",
                AvatarEmotion.SURPRISED to "surprised.$fileExtension"
            )
            return Mp4AvatarModel(
                id = id,
                name = name,
                basePath = basePath,
                emotionToFileMap = emotionMap,
                availableFiles = emotionMap.values.toList()
            )
        }
    }
}
