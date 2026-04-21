package com.ai.assistance.fbx

object FbxNative {

    init {
        FbxLibraryLoader.loadLibraries()
    }

    @JvmStatic external fun nativeIsAvailable(): Boolean

    @JvmStatic external fun nativeGetUnavailableReason(): String

    @JvmStatic external fun nativeGetLastError(): String

    @JvmStatic external fun nativeInspectModel(pathModel: String): String?

    @JvmStatic external fun nativeCreatePreviewSession(pathModel: String): Long

    @JvmStatic external fun nativeDestroyPreviewSession(sessionHandle: Long)

    @JvmStatic external fun nativeReadPreviewInfo(sessionHandle: Long): String?

    @JvmStatic external fun nativeBuildPreviewFrame(
        sessionHandle: Long,
        animationName: String?,
        timeSeconds: Double
    ): FloatArray?

    @JvmStatic external fun nativeReadEmbeddedTextureBytes(
        sessionHandle: Long,
        textureIndex: Int
    ): ByteArray?
}
