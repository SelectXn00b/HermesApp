package com.ai.assistance.mmd

enum class MmdModelFormat {
    PMD,
    PMX,
    UNKNOWN
}

data class MmdModelInfo(
    val format: MmdModelFormat,
    val modelName: String,
    val vertexCount: Int,
    val faceCount: Int,
    val materialCount: Int,
    val boneCount: Int,
    val morphCount: Int,
    val rigidBodyCount: Int,
    val jointCount: Int
)

data class VmdMotionInfo(
    val modelName: String,
    val motionCount: Int,
    val morphCount: Int,
    val cameraCount: Int,
    val lightCount: Int,
    val shadowCount: Int,
    val ikCount: Int
)

object MmdInspector {
    private const val MODEL_SUMMARY_SIZE = 8
    private const val MOTION_SUMMARY_SIZE = 6

    private const val FORMAT_PMD = 1
    private const val FORMAT_PMX = 2

    fun isAvailable(): Boolean = MmdNative.nativeIsAvailable()

    fun unavailableReason(): String = MmdNative.nativeGetUnavailableReason()

    fun getLastError(): String = MmdNative.nativeGetLastError()

    fun inspectModel(pathModel: String): MmdModelInfo? {
        val summary = MmdNative.nativeReadModelSummary(pathModel) ?: return null
        if (summary.size < MODEL_SUMMARY_SIZE) return null

        val format = when (summary[0].toInt()) {
            FORMAT_PMD -> MmdModelFormat.PMD
            FORMAT_PMX -> MmdModelFormat.PMX
            else -> MmdModelFormat.UNKNOWN
        }

        val modelName = MmdNative.nativeReadModelName(pathModel).orEmpty()

        return MmdModelInfo(
            format = format,
            modelName = modelName,
            vertexCount = summary[1].toInt(),
            faceCount = summary[2].toInt(),
            materialCount = summary[3].toInt(),
            boneCount = summary[4].toInt(),
            morphCount = summary[5].toInt(),
            rigidBodyCount = summary[6].toInt(),
            jointCount = summary[7].toInt()
        )
    }

    fun inspectMotion(pathMotion: String): VmdMotionInfo? {
        val summary = MmdNative.nativeReadMotionSummary(pathMotion) ?: return null
        if (summary.size < MOTION_SUMMARY_SIZE) return null

        val modelName = MmdNative.nativeReadMotionModelName(pathMotion).orEmpty()

        return VmdMotionInfo(
            modelName = modelName,
            motionCount = summary[0].toInt(),
            morphCount = summary[1].toInt(),
            cameraCount = summary[2].toInt(),
            lightCount = summary[3].toInt(),
            shadowCount = summary[4].toInt(),
            ikCount = summary[5].toInt()
        )
    }
}
