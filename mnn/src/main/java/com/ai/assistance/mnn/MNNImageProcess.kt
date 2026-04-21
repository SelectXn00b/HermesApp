package com.ai.assistance.mnn

import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * MNN Image Process
 * MNN 图像处理工具类
 */
object MNNImageProcess {

    /**
     * 图像格式
     */
    enum class Format(val type: Int) {
        /** RGBA */
        RGBA(0),
        /** RGB */
        RGB(1),
        /** BGR */
        BGR(2),
        /** GRAY */
        GRAY(3),
        /** BGRA */
        BGRA(4),
        /** YUV420 */
        YUV_420(10),
        /** YUVNV21 */
        YUV_NV21(11)
    }

    /**
     * 滤波器类型
     */
    enum class Filter(val type: Int) {
        /** 最近邻 */
        NEAREST(0),
        /** 双线性 */
        BILINEAL(1),
        /** 双三次 */
        BICUBIC(2)
    }

    /**
     * 边界处理方式
     */
    enum class Wrap(val type: Int) {
        /** 边缘拉伸 */
        CLAMP_TO_EDGE(0),
        /** 填充零 */
        ZERO(1),
        /** 重复 */
        REPEAT(2)
    }

    /**
     * 图像处理配置
     */
    data class Config(
        var mean: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
        var normal: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
        var source: Format = Format.RGBA,
        var dest: Format = Format.BGR,
        var filter: Filter = Filter.NEAREST,
        var wrap: Wrap = Wrap.CLAMP_TO_EDGE
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Config

            if (!mean.contentEquals(other.mean)) return false
            if (!normal.contentEquals(other.normal)) return false
            if (source != other.source) return false
            if (dest != other.dest) return false
            if (filter != other.filter) return false
            if (wrap != other.wrap) return false

            return true
        }

        override fun hashCode(): Int {
            var result = mean.contentHashCode()
            result = 31 * result + normal.contentHashCode()
            result = 31 * result + source.hashCode()
            result = 31 * result + dest.hashCode()
            result = 31 * result + filter.hashCode()
            result = 31 * result + wrap.hashCode()
            return result
        }
    }

    /**
     * 设置输入 buffer
     * @param buffer 输入的 buffer
     * @param width 图像宽度
     * @param height 图像高度
     * @param tensor 输入的 Tensor
     * @param config 配置 mean、normal、图片目标格式
     * @param matrix 定义裁剪、缩放、旋转等
     * @return 是否成功
     */
    @JvmStatic
    fun convertBuffer(
        buffer: ByteArray,
        width: Int,
        height: Int,
        tensor: MNNNetInstance.Session.Tensor,
        config: Config,
        matrix: Matrix? = null
    ): Boolean {
        val actualMatrix = matrix ?: Matrix()
        val value = FloatArray(9)
        actualMatrix.getValues(value)

        return MNNNetNative.nativeConvertBufferToTensor(
            buffer, width, height, tensor.instance(),
            config.source.type, config.dest.type, config.filter.type, config.wrap.type,
            value, config.mean, config.normal
        )
    }

    /**
     * 设置 bitmap 输入
     * @param sourceBitmap bitmap
     * @param tensor 输入的 Tensor
     * @param config 配置 mean、normal、图片目标格式
     * @param matrix 定义裁剪、缩放、旋转等
     * @return 是否成功
     */
    @JvmStatic
    fun convertBitmap(
        sourceBitmap: Bitmap,
        tensor: MNNNetInstance.Session.Tensor,
        config: Config,
        matrix: Matrix? = null
    ): Boolean {
        val actualMatrix = matrix ?: Matrix()
        val value = FloatArray(9)
        actualMatrix.getValues(value)

        return MNNNetNative.nativeConvertBitmapToTensor(
            sourceBitmap, tensor.instance(),
            config.dest.type, config.filter.type, config.wrap.type,
            value, config.mean, config.normal
        )
    }
}

