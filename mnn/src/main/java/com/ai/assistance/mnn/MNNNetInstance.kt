package com.ai.assistance.mnn

import android.util.Log

/**
 * MNN Net Instance
 * MNN 网络实例，封装了 MNN 推理引擎的核心功能
 */
class MNNNetInstance private constructor(private var netInstance: Long) {

    companion object {
        private const val TAG = "MNNNetInstance"

        /**
         * 从文件创建 MNN 网络实例
         * @param fileName 模型文件路径
         * @return MNN 网络实例，失败返回 null
         */
        @JvmStatic
        fun createFromFile(fileName: String): MNNNetInstance? {
            val instance = MNNNetNative.nativeCreateNetFromFile(fileName)
            if (instance == 0L) {
                Log.e(TAG, "Create Net Failed from file $fileName")
                return null
            }
            return MNNNetInstance(instance)
        }

        /**
         * 从字节数组创建 MNN 网络实例
         * @param buffer 模型数据
         * @return MNN 网络实例，失败返回 null
         */
        @JvmStatic
        fun createFromBuffer(buffer: ByteArray): MNNNetInstance? {
            val instance = MNNNetNative.nativeCreateNetFromBuffer(buffer)
            if (instance == 0L) {
                Log.e(TAG, "Create Net Failed from buffer, buffer maybe null or invalid")
                return null
            }
            return MNNNetInstance(instance)
        }
    }

    /**
     * Session 配置
     */
    data class Config(
        var forwardType: Int = MNNForwardType.FORWARD_CPU.type,
        var numThread: Int = 4,
        var saveTensors: Array<String>? = null,
        var outputTensors: Array<String>? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Config

            if (forwardType != other.forwardType) return false
            if (numThread != other.numThread) return false
            if (saveTensors != null) {
                if (other.saveTensors == null) return false
                if (!saveTensors.contentEquals(other.saveTensors)) return false
            } else if (other.saveTensors != null) return false
            if (outputTensors != null) {
                if (other.outputTensors == null) return false
                if (!outputTensors.contentEquals(other.outputTensors)) return false
            } else if (other.outputTensors != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = forwardType
            result = 31 * result + numThread
            result = 31 * result + (saveTensors?.contentHashCode() ?: 0)
            result = 31 * result + (outputTensors?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Session 会话
     */
    inner class Session internal constructor(private var sessionInstance: Long) {

        /**
         * Tensor 张量
         */
        inner class Tensor internal constructor(private val tensorInstance: Long) {
            private var data: FloatArray? = null
            private var intData: IntArray? = null
            private var uint8Data: ByteArray? = null

            internal fun instance(): Long = tensorInstance

            /**
             * 重塑张量形状
             */
            fun reshape(dims: IntArray) {
                MNNNetNative.nativeReshapeTensor(netInstance, tensorInstance, dims)
                data = null
            }

            /**
             * 设置整型输入数据
             */
            fun setInputIntData(data: IntArray) {
                MNNNetNative.nativeSetInputIntData(netInstance, tensorInstance, data)
                this.data = null
            }

            /**
             * 设置浮点输入数据
             */
            fun setInputFloatData(data: FloatArray) {
                MNNNetNative.nativeSetInputFloatData(netInstance, tensorInstance, data)
                this.data = null
            }

            /**
             * 获取张量维度
             */
            fun getDimensions(): IntArray {
                return MNNNetNative.nativeTensorGetDimensions(tensorInstance)
            }

            /**
             * 获取浮点数据
             */
            fun getFloatData(): FloatArray {
                getData()
                return data!!
            }

            /**
             * 获取整型数据
             */
            fun getIntData(): IntArray {
                if (intData == null) {
                    val size = MNNNetNative.nativeTensorGetIntData(tensorInstance, null)
                    intData = IntArray(size)
                }
                MNNNetNative.nativeTensorGetIntData(tensorInstance, intData)
                return intData!!
            }

            /**
             * 获取数据
             */
            fun getData() {
                if (data == null) {
                    val size = MNNNetNative.nativeTensorGetData(tensorInstance, null)
                    data = FloatArray(size)
                }
                MNNNetNative.nativeTensorGetData(tensorInstance, data)
            }

            /**
             * 获取 UINT8 数据
             */
            fun getUINT8Data(): ByteArray {
                if (uint8Data == null) {
                    val size = MNNNetNative.nativeTensorGetUINT8Data(tensorInstance, null)
                    uint8Data = ByteArray(size)
                }
                MNNNetNative.nativeTensorGetUINT8Data(tensorInstance, uint8Data)
                return uint8Data!!
            }
        }

        /**
         * 在所有输入张量 reshape 后调用此方法
         */
        fun reshape() {
            MNNNetNative.nativeReshapeSession(netInstance, sessionInstance)
        }

        /**
         * 运行推理
         */
        fun run() {
            MNNNetNative.nativeRunSession(netInstance, sessionInstance)
        }

        /**
         * 使用回调运行推理
         */
        fun runWithCallback(names: Array<String>): Array<Tensor> {
            val tensorPtr = LongArray(names.size)
            MNNNetNative.nativeRunSessionWithCallback(netInstance, sessionInstance, names, tensorPtr)
            return Array(names.size) { i -> Tensor(tensorPtr[i]) }
        }

        /**
         * 获取输入张量
         */
        fun getInput(name: String?): Tensor? {
            val tensorPtr = MNNNetNative.nativeGetSessionInput(netInstance, sessionInstance, name)
            if (tensorPtr == 0L) {
                Log.e(TAG, "Can't find session input: $name")
                return null
            }
            return Tensor(tensorPtr)
        }

        /**
         * 获取输出张量
         */
        fun getOutput(name: String?): Tensor? {
            val tensorPtr = MNNNetNative.nativeGetSessionOutput(netInstance, sessionInstance, name)
            if (tensorPtr == 0L) {
                Log.e(TAG, "Can't find session output: $name")
                return null
            }
            return Tensor(tensorPtr)
        }

        /**
         * 从网络实例中释放会话，如果调用了 net.release() 则不需要调用此方法
         */
        fun release() {
            checkValid()
            MNNNetNative.nativeReleaseSession(netInstance, sessionInstance)
            sessionInstance = 0
        }
    }

    /**
     * 创建会话
     */
    fun createSession(config: Config? = null): Session? {
        checkValid()

        val actualConfig = config ?: Config()
        val sessionId = MNNNetNative.nativeCreateSession(
            netInstance,
            actualConfig.forwardType,
            actualConfig.numThread,
            actualConfig.saveTensors,
            actualConfig.outputTensors
        )

        if (sessionId == 0L) {
            Log.e(TAG, "Create Session Error")
            return null
        }
        return Session(sessionId)
    }

    private fun checkValid() {
        if (netInstance == 0L) {
            throw RuntimeException("MNNNetInstance native pointer is null, it may has been released")
        }
    }

    /**
     * 释放网络实例
     */
    fun release() {
        checkValid()
        MNNNetNative.nativeReleaseNet(netInstance)
        netInstance = 0
    }
}

