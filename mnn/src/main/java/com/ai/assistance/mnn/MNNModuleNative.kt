package com.ai.assistance.mnn

/**
 * MNN Module Native JNI 接口
 * 用于处理动态形状模型和需要输入内容计算形状的模型
 */
object MNNModuleNative {
    
    init {
        MNNLibraryLoader.loadLibraries()
    }
    
    /**
     * 从文件创建 Module
     * @param filePath 模型文件路径
     * @param inputs 输入张量名称列表
     * @param outputs 输出张量名称列表
     * @param forwardType 推理类型 (CPU/GPU等)
     * @param numThread 线程数
     * @param precision 精度模式
     * @param memoryMode 内存模式
     * @return Module 指针，失败返回 0
     */
    @JvmStatic
    external fun nativeCreateModuleFromFile(
        filePath: String,
        inputs: Array<String>,
        outputs: Array<String>,
        forwardType: Int,
        numThread: Int,
        precision: Int,
        memoryMode: Int
    ): Long
    
    /**
     * 释放 Module
     * @param modulePtr Module 指针
     */
    @JvmStatic
    external fun nativeReleaseModule(modulePtr: Long)
    
    /**
     * 执行前向推理
     * @param modulePtr Module 指针
     * @param inputVarPtrs 输入 VARP 指针数组
     * @return 输出 VARP 指针数组
     */
    @JvmStatic
    external fun nativeForward(modulePtr: Long, inputVarPtrs: LongArray): LongArray?
    
    /**
     * 创建输入变量
     * @param shape 张量形状
     * @param dataFormat 数据格式 (NCHW/NHWC等)
     * @param dataType 数据类型
     * @return VARP 指针，失败返回 0
     */
    @JvmStatic
    external fun nativeCreateInputVar(shape: IntArray, dataFormat: Int, dataType: Int): Long
    
    /**
     * 设置变量的浮点数据
     * @param varPtr VARP 指针
     * @param data 浮点数组
     * @return 是否成功
     */
    @JvmStatic
    external fun nativeSetVarFloatData(varPtr: Long, data: FloatArray): Boolean
    
    /**
     * 设置变量的整型数据
     * @param varPtr VARP 指针
     * @param data 整型数组
     * @return 是否成功
     */
    @JvmStatic
    external fun nativeSetVarIntData(varPtr: Long, data: IntArray): Boolean
    
    /**
     * 获取变量的浮点数据
     * @param varPtr VARP 指针
     * @return 浮点数组
     */
    @JvmStatic
    external fun nativeGetVarFloatData(varPtr: Long): FloatArray?
    
    /**
     * 获取变量的形状
     * @param varPtr VARP 指针
     * @return 形状数组
     */
    @JvmStatic
    external fun nativeGetVarShape(varPtr: Long): IntArray?
    
    /**
     * 释放变量
     * @param varPtr VARP 指针
     */
    @JvmStatic
    external fun nativeReleaseVar(varPtr: Long)
}

