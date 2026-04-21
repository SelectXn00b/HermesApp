package com.ai.assistance.operit.core.avatar.impl.mmd.model

import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import java.io.File

data class MmdAvatarModel(
    override val id: String,
    override val name: String,
    val basePath: String,
    val modelFile: String,
    val motionFile: String? = null,
    val motionFiles: List<String> = emptyList()
) : AvatarModel {

    override val type: AvatarType
        get() = AvatarType.MMD

    val modelPath: String
        get() = File(basePath, modelFile).absolutePath

    val availableMotionFiles: List<String>
        get() {
            val normalized = motionFiles.filter { it.isNotBlank() }.distinct()
            return if (normalized.isNotEmpty()) {
                normalized
            } else {
                motionFile?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }
        }

    val motionPath: String?
        get() = availableMotionFiles.firstOrNull()?.let { File(basePath, it).absolutePath }

    val motionPaths: List<String>
        get() = availableMotionFiles.map { File(basePath, it).absolutePath }

    val displayMotionName: String?
        get() = availableMotionFiles.firstOrNull()

    val displayMotionNames: List<String>
        get() = availableMotionFiles
}
