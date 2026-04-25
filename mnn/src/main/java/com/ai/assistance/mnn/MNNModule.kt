package com.ai.assistance.mnn

import android.util.Log

/**
 * MNN Module 高级封装
 * 用于处理动态形状模型和复杂推理场景
 */
class MNNModule private constructor(
    private var modulePtr: Long,
    private val inputNames: List<String>,
    private val outputNames: List<String>
) {
    companion object {
        private const val TAG = "MNNModule"
        
        /**
         * 从文件加载模型
         * @param filePath 模型文件路径
         * @param config 模型配置
         * @return MNNModule 实例，失败返回 null
         */
        @JvmStatic
        fun load(filePath: String, config: Config): MNNModule? {
            val modulePtr = MNNModuleNative.nativeCreateModuleFromFile(
                filePath = filePath,
                inputs = config.inputNames.toTypedArray(),
                outputs = config.outputNames.toTypedArray(),
                forwardType = config.forwardType,
                numThread = config.numThread,
                precision = config.precision,
                memoryMode = config.memoryMode
            )
            
            if (modulePtr == 0L) {
                Log.e(TAG, "Failed to create module from file: $filePath")
                return null
            }
            
            Log.d(TAG, "Module created successfully from: $filePath")
            return MNNModule(modulePtr, config.inputNames, config.outputNames)
        }
    }
    
    /**
     * 模型配置
     */
    data class Config(
        val inputNames: List<String>,
        val outputNames: List<String>,
        val forwardType: Int = MNNForwardType.FORWARD_CPU.type,
        val numThread: Int = 4,
        val precision: Int = PrecisionMode.NORMAL,
        val memoryMode: Int = MemoryMode.NORMAL
    )
    
    /**
     * 精度模式
     */
    object PrecisionMode {
        const val NORMAL = 0
        const val HIGH = 1
        const val LOW = 2
        const val LOW_BF16 = 3
    }
    
    /**
     * 内存模式
     */
    object MemoryMode {
        const val NORMAL = 0
        const val LOW = 1
    }
    
    /**
     * 数据格式
     */
    object DataFormat {
        const val NCHW = 0
        const val NHWC = 1
        const val NC4HW4 = 2
    }
    
    /**
     * 数据类型编码
     */
    object DataType {
        // format: (code << 8) | bits
        const val FLOAT32 = (2 shl 8) or 32  // halide_type_float, 32 bits
        const val INT32 = (0 shl 8) or 32    // halide_type_int, 32 bits
        const val UINT8 = (1 shl 8) or 8     // halide_type_uint, 8 bits
        const val INT8 = (0 shl 8) or 8      // halide_type_int, 8 bits
    }
    
    /**
     * 变量（张量）包装类
     */
    class Variable internal constructor(private var varPtr: Long) {
        
        /**
         * 设置浮点数据
         */
        fun setFloatData(data: FloatArray): Boolean {
            checkValid()
            return MNNModuleNative.nativeSetVarFloatData(varPtr, data)
        }
        
        /**
         * 设置整型数据
         */
        fun setIntData(data: IntArray): Boolean {
            checkValid()
            return MNNModuleNative.nativeSetVarIntData(varPtr, data)
        }
        
        /**
         * 获取浮点数据
         */
        fun getFloatData(): FloatArray? {
            checkValid()
            return MNNModuleNative.nativeGetVarFloatData(varPtr)
        }
        
        /**
         * 获取形状
         */
        fun getShape(): IntArray? {
            checkValid()
            return MNNModuleNative.nativeGetVarShape(varPtr)
        }
        
        /**
         * 释放变量
         */
        fun release() {
            if (varPtr != 0L) {
                MNNModuleNative.nativeReleaseVar(varPtr)
                varPtr = 0L
            }
        }
        
        internal fun getPtr(): Long = varPtr
        
        private fun checkValid() {
            if (varPtr == 0L) {
                throw RuntimeException("Variable has been released")
            }
        }
        
        protected fun finalize() {
            release()
        }
    }
    
    /**
     * 创建输入变量
     * @param shape 张量形状
     * @param dataFormat 数据格式（默认 NCHW）
     * @param dataType 数据类型（默认 FLOAT32）
     * @return Variable 实例，失败返回 null
     */
    fun createInputVariable(
        shape: IntArray,
        dataFormat: Int = DataFormat.NCHW,
        dataType: Int = DataType.FLOAT32
    ): Variable? {
        checkValid()
        
        val varPtr = MNNModuleNative.nativeCreateInputVar(shape, dataFormat, dataType)
        if (varPtr == 0L) {
            Log.e(TAG, "Failed to create input variable")
            return null
        }
        
        return Variable(varPtr)
    }
    
    /**
     * 执行前向推理
     * @param inputs 输入变量列表
     * @return 输出变量列表，失败返回 null
     */
    fun forward(inputs: List<Variable>): List<Variable>? {
        checkValid()
        
        if (inputs.size != inputNames.size) {
            Log.e(TAG, "Input count mismatch: expected ${inputNames.size}, got ${inputs.size}")
            return null
        }
        
        // 获取输入指针数组
        val inputPtrs = LongArray(inputs.size) { inputs[it].getPtr() }
        
        // 执行推理
        val outputPtrs = MNNModuleNative.nativeForward(modulePtr, inputPtrs)
        if (outputPtrs == null) {
            Log.e(TAG, "Forward inference failed")
            return null
        }
        
        // 创建输出变量列表
        return outputPtrs.map { Variable(it) }
    }
    
    /**
     * 获取输入名称列表
     */
    fun getInputNames(): List<String> = inputNames
    
    /**
     * 获取输出名称列表
     */
    fun getOutputNames(): List<String> = outputNames
    
    /**
     * 释放模块
     */
    fun release() {
        if (modulePtr != 0L) {
            MNNModuleNative.nativeReleaseModule(modulePtr)
            modulePtr = 0L
            Log.d(TAG, "Module released")
        }
    }
    
    private fun checkValid() {
        if (modulePtr == 0L) {
            throw RuntimeException("Module has been released")
        }
    }
    
    protected fun finalize() {
        release()
    }
}

