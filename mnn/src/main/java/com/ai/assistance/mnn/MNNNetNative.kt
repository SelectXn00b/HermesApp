package com.ai.assistance.mnn

import android.graphics.Bitmap

/**
 * MNN Native JNI Interface
 * MNN 原生 JNI 接口层
 */
object MNNNetNative {

    init {
        MNNLibraryLoader.loadLibraries()
    }

    // Net
    @JvmStatic
    external fun nativeCreateNetFromFile(modelName: String): Long

    @JvmStatic
    external fun nativeCreateNetFromBuffer(buffer: ByteArray): Long

    @JvmStatic
    external fun nativeReleaseNet(netPtr: Long): Long

    // Session
    @JvmStatic
    external fun nativeCreateSession(
        netPtr: Long,
        forwardType: Int,
        numThread: Int,
        saveTensors: Array<String>?,
        outputTensors: Array<String>?
    ): Long

    @JvmStatic
    external fun nativeReleaseSession(netPtr: Long, sessionPtr: Long)

    @JvmStatic
    external fun nativeRunSession(netPtr: Long, sessionPtr: Long): Int

    @JvmStatic
    external fun nativeRunSessionWithCallback(
        netPtr: Long,
        sessionPtr: Long,
        nameArray: Array<String>,
        tensorAddr: LongArray
    ): Int

    @JvmStatic
    external fun nativeReshapeSession(netPtr: Long, sessionPtr: Long): Int

    @JvmStatic
    external fun nativeGetSessionInput(netPtr: Long, sessionPtr: Long, name: String?): Long

    @JvmStatic
    external fun nativeGetSessionOutput(netPtr: Long, sessionPtr: Long, name: String?): Long

    // Tensor
    @JvmStatic
    external fun nativeReshapeTensor(netPtr: Long, tensorPtr: Long, dims: IntArray)

    @JvmStatic
    external fun nativeTensorGetDimensions(tensorPtr: Long): IntArray

    @JvmStatic
    external fun nativeSetInputIntData(netPtr: Long, tensorPtr: Long, data: IntArray)

    @JvmStatic
    external fun nativeSetInputFloatData(netPtr: Long, tensorPtr: Long, data: FloatArray)

    @JvmStatic
    external fun nativeTensorGetData(tensorPtr: Long, dest: FloatArray?): Int

    @JvmStatic
    external fun nativeTensorGetIntData(tensorPtr: Long, dest: IntArray?): Int

    @JvmStatic
    external fun nativeTensorGetUINT8Data(tensorPtr: Long, dest: ByteArray?): Int

    // ImageProcess
    @JvmStatic
    external fun nativeConvertBitmapToTensor(
        srcBitmap: Bitmap,
        tensorPtr: Long,
        destFormat: Int,
        filterType: Int,
        wrap: Int,
        matrixValue: FloatArray,
        mean: FloatArray,
        normal: FloatArray
    ): Boolean

    @JvmStatic
    external fun nativeConvertBufferToTensor(
        bufferData: ByteArray,
        width: Int,
        height: Int,
        tensorPtr: Long,
        srcFormat: Int,
        destFormat: Int,
        filterType: Int,
        wrap: Int,
        matrixValue: FloatArray,
        mean: FloatArray,
        normal: FloatArray
    ): Boolean
}

