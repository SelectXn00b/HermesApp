package com.ai.assistance.mnn

/**
 * MNN LLM Engine Native JNI 接口
 * 基于 MNN 官方 LLM 引擎实现
 */
object MNNLlmNative {
    
    init {
        MNNLibraryLoader.loadLibraries()
    }
    
    /**
     * 从配置文件创建 LLM 实例（不加载模型）
     * @param configPath 配置文件路径 (llm_config.json)
     * @return LLM 指针，失败返回 0
     * 注意：创建后需要先调用 nativeSetConfig 设置配置，然后调用 nativeLoadLlm 加载模型
     */
    @JvmStatic
    external fun nativeCreateLlm(configPath: String): Long
    
    /**
     * 加载 LLM 模型（必须在设置配置后调用）
     * @param llmPtr LLM 指针
     * @return 是否加载成功
     */
    @JvmStatic
    external fun nativeLoadLlm(llmPtr: Long): Boolean
    
    /**
     * 释放 LLM 实例
     * @param llmPtr LLM 指针
     */
    @JvmStatic
    external fun nativeReleaseLlm(llmPtr: Long)
    
    /**
     * 将文本编码为 token IDs
     * @param llmPtr LLM 指针
     * @param text 输入文本
     * @return token ID 数组
     */
    @JvmStatic
    external fun nativeTokenize(llmPtr: Long, text: String): IntArray?
    
    /**
     * 将单个 token ID 解码为文本
     * @param llmPtr LLM 指针
     * @param token token ID
     * @return 解码后的文本
     */
    @JvmStatic
    external fun nativeDetokenize(llmPtr: Long, token: Int): String?

    @JvmStatic
    external fun nativeCountTokens(llmPtr: Long, text: String): Int
    
    /**
     * 生成文本（非流式）
     * @param llmPtr LLM 指针
     * @param prompt 输入提示
     * @param maxTokens 最大生成 token 数
     * @param callback 回调接口（用于流式输出）
     * @return 生成的完整文本
     */
    @JvmStatic
    external fun nativeGenerate(
        llmPtr: Long,
        prompt: String,
        maxTokens: Int,
        callback: GenerationCallback?
    ): String?
    
    /**
     * 流式生成文本（带历史记录）
     * @param llmPtr LLM 指针
     * @param history 对话历史 (Pair<role, content>)
     * @param maxTokens 最大生成 token 数
     * @param callback 回调接口
     * @return 是否成功
     */
    @JvmStatic
    external fun nativeGenerateStream(
        llmPtr: Long,
        history: List<Pair<String, String>>,
        maxTokens: Int,
        callback: GenerationCallback
    ): Boolean

    @JvmStatic
    external fun nativeGenerateStreamStructured(
        llmPtr: Long,
        messagesJson: String,
        toolsJson: String?,
        maxTokens: Int,
        callback: GenerationCallback
    ): Boolean

    @JvmStatic
    external fun nativeApplyChatTemplateWithHistory(
        llmPtr: Long,
        history: List<Pair<String, String>>
    ): String?

    @JvmStatic
    external fun nativeApplyChatTemplateWithStructuredMessages(
        llmPtr: Long,
        messagesJson: String,
        toolsJson: String?
    ): String?

    @JvmStatic
    external fun nativeCountTokensWithHistory(
        llmPtr: Long,
        history: List<Pair<String, String>>
    ): Int

    @JvmStatic
    external fun nativeCountTokensWithStructuredMessages(
        llmPtr: Long,
        messagesJson: String,
        toolsJson: String?
    ): Int

    /**
     * 导出当前生效的 LLM 配置。
     * @param llmPtr LLM 指针
     * @return JSON 字符串，失败返回 null
     */
    @JvmStatic
    external fun nativeDumpConfig(llmPtr: Long): String?

    /**
     * 导出最近一次推理上下文信息。
     * @param llmPtr LLM 指针
     * @return JSON 字符串，失败返回 null
     */
    @JvmStatic
    external fun nativeGetContextInfo(llmPtr: Long): String?
    
    /**
     * 应用聊天模板
     * @param llmPtr LLM 指针
     * @param userContent 用户输入内容
     * @return 应用模板后的文本
     */
    @JvmStatic
    external fun nativeApplyChatTemplate(llmPtr: Long, userContent: String): String?
    
    /**
     * 重置 LLM 状态（清除 KV-Cache 和历史）
     * @param llmPtr LLM 指针
     */
    @JvmStatic
    external fun nativeReset(llmPtr: Long)
    
    /**
     * 设置 LLM 配置（用于运行时修改配置）
     * @param llmPtr LLM 指针
     * @param configJson JSON 格式的配置字符串
     * @return 是否设置成功
     */
    @JvmStatic
    external fun nativeSetConfig(llmPtr: Long, configJson: String): Boolean

    /**
     * 注册或清除音频数据回调。
     * @param llmPtr LLM 指针
     * @param callback 为 null 时表示清除回调
     * @return 是否设置成功
     */
    @JvmStatic
    external fun nativeSetAudioDataCallback(
        llmPtr: Long,
        callback: AudioDataCallback?
    ): Boolean

    /**
     * 触发语音波形生成。
     * 仅对支持语音输出的模型有效。
     * @param llmPtr LLM 指针
     * @return 是否执行成功
     */
    @JvmStatic
    external fun nativeGenerateWavform(llmPtr: Long): Boolean
    
    /**
     * 取消当前的生成任务
     * @param llmPtr LLM 指针
     */
    @JvmStatic
    external fun nativeCancel(llmPtr: Long)
    
    /**
     * 生成回调接口
     */
    interface GenerationCallback {
        /**
         * 当生成新的 token 时调用
         * @param token 生成的文本片段
         * @return true 继续生成，false 停止生成
         */
        fun onToken(token: String): Boolean
    }

    /**
     * 当模型输出音频波形时触发。
     * 返回 true 表示继续，false 表示停止音频输出。
     */
    interface AudioDataCallback {
        fun onAudioData(audioData: FloatArray, isLastChunk: Boolean): Boolean
    }
}

