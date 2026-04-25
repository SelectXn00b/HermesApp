package com.ai.assistance.mnn

import android.util.Log
import java.io.File
import org.json.JSONObject

/**
 * MNN LLM 会话封装
 * 提供高级 API 来管理 LLM 推理会话
 */
class MNNLlmSession private constructor(
    private var llmPtr: Long,
    private val modelPath: String
) {
    companion object {
        private const val TAG = "MNNLlmSession"
        
        /**
         * 从模型目录创建 LLM 会话
         * @param modelDir 模型目录（包含 llm_config.json）
         * @param backendType 后端类型（"cpu", "opencl", "metal"）
         * @param threadNum 线程数
         * @param precision 精度（"low", "normal", "high"）
         * @param memory 内存模式（"low", "normal", "high"）
         * @param tmpPath 临时文件目录（用于缓存文件），默认为模型目录
         * @return MNNLlmSession 实例，失败返回 null
         */
        @JvmStatic
        fun create(
            modelDir: String,
            backendType: String = "cpu",
            threadNum: Int = 4,
            precision: String = "low",
            memory: String = "low",
            tmpPath: String? = null
        ): MNNLlmSession? {
            val configFile = File(modelDir, "llm_config.json")
            
            if (!configFile.exists()) {
                Log.e(TAG, "Config file not found: ${configFile.absolutePath}")
                return null
            }
            
            Log.d(TAG, "Creating LLM session from: ${configFile.absolutePath}")
            Log.d(TAG, "Backend: $backendType, Threads: $threadNum, Precision: $precision, Memory: $memory")
            Log.d(TAG, "Cache path: ${tmpPath ?: modelDir}")
            
            // 步骤1: 创建LLM实例（不加载）
            val llmPtr = MNNLlmNative.nativeCreateLlm(configFile.absolutePath)
            if (llmPtr == 0L) {
                Log.e(TAG, "Failed to create LLM native instance")
                return null
            }
            
            // 步骤2: 设置配置（必须在load之前！）
            // 按照官方 llm_bench.cpp 的顺序设置配置
            // tmp_path 用于存放 mnn_cachefile.bin 等临时文件
            val cachePath = tmpPath ?: modelDir
            val configs = listOf(
                """{"tmp_path":"$cachePath"}""",
                """{"async":false}""",
                """{"precision":"$precision"}""",
                """{"memory":"$memory"}""",
                """{"backend_type":"$backendType"}""",
                """{"thread_num":$threadNum}"""
            )
            
            for (config in configs) {
                if (!MNNLlmNative.nativeSetConfig(llmPtr, config)) {
                    Log.e(TAG, "Failed to set config: $config")
                    MNNLlmNative.nativeReleaseLlm(llmPtr)
                    return null
                }
                Log.d(TAG, "Config set: $config")
            }
            
            // 步骤3: 加载模型（配置已设置）
            if (!MNNLlmNative.nativeLoadLlm(llmPtr)) {
                Log.e(TAG, "Failed to load LLM model")
                MNNLlmNative.nativeReleaseLlm(llmPtr)
                return null
            }
            
            Log.i(TAG, "LLM session created and loaded successfully")
            return MNNLlmSession(llmPtr, modelDir)
        }
    }
    
    @Volatile
    private var released = false

    private val lock = Any()

    private var activeCalls = 0

    private inline fun <T> withActiveCall(block: (Long) -> T): T {
        val ptr: Long
        synchronized(lock) {
            checkValid()
            activeCalls += 1
            ptr = llmPtr
        }

        try {
            return block(ptr)
        } finally {
            synchronized(lock) {
                activeCalls -= 1
                (lock as java.lang.Object).notifyAll()
            }
        }
    }
    
    /**
     * 检查会话是否有效
     */
    private fun checkValid() {
        if (released || llmPtr == 0L) {
            throw RuntimeException("LLM session has been released")
        }
    }
    
    /**
     * 将文本编码为 token IDs
     */
    fun tokenize(text: String): IntArray {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeTokenize(ptr, text)
                ?: throw RuntimeException("Tokenization failed")
        }
    }
    
    /**
     * 将 token ID 解码为文本
     */
    fun detokenize(token: Int): String {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeDetokenize(ptr, token)
                ?: throw RuntimeException("Detokenization failed")
        }
    }

    fun countTokens(text: String): Int {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeCountTokens(ptr, text)
        }
    }

    fun countTokensWithHistory(history: List<Pair<String, String>>): Int {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeCountTokensWithHistory(ptr, history)
        }
    }

    fun countTokensStructured(messagesJson: String, toolsJson: String? = null): Int {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeCountTokensWithStructuredMessages(ptr, messagesJson, toolsJson)
        }
    }

    /**
     * 导出当前生效的配置。
     */
    fun dumpConfig(): String {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeDumpConfig(ptr)
                ?: throw RuntimeException("Dump config failed")
        }
    }

    /**
     * 获取最近一次推理的上下文统计。
     * 在尚未执行推理时可能返回 null。
     */
    fun getContextInfo(): MNNLlmContextInfo? {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeGetContextInfo(ptr)?.let(MNNLlmContextInfo::fromJson)
        }
    }
    
    /**
     * 应用聊天模板
     */
    fun applyChatTemplate(userContent: String): String {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeApplyChatTemplate(ptr, userContent)
                ?: userContent
        }
    }

    /**
     * 对完整历史应用聊天模板。
     */
    fun applyChatTemplate(history: List<Pair<String, String>>): String {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeApplyChatTemplateWithHistory(ptr, history)
                ?: throw RuntimeException("Apply chat template with history failed")
        }
    }

    fun applyChatTemplateStructured(messagesJson: String, toolsJson: String? = null): String {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeApplyChatTemplateWithStructuredMessages(ptr, messagesJson, toolsJson)
                ?: throw RuntimeException("Apply chat template with structured messages failed")
        }
    }
    
    /**
     * 非流式生成
     * @param prompt 输入提示
     * @param maxTokens 最大生成 token 数（-1 表示使用默认值）
     * @return 生成的文本
     */
    fun generate(prompt: String, maxTokens: Int = -1): String {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeGenerate(ptr, prompt, maxTokens, null)
                ?: throw RuntimeException("Generation failed")
        }
    }
    
    /**
     * 流式生成（带历史记录）
     * @param history 对话历史 (Pair<role, content>)
     * @param maxTokens 最大生成 token 数（-1 表示使用默认值）
     * @param onToken 每个 token 的回调，返回 false 可以停止生成
     * @return 是否成功
     */
    fun generateStream(
        history: List<Pair<String, String>>,
        maxTokens: Int = -1,
        onToken: (String) -> Boolean
    ): Boolean {
        val callback = object : MNNLlmNative.GenerationCallback {
            override fun onToken(token: String): Boolean {
                return try {
                    onToken(token)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in token callback", e)
                    false
                }
            }
        }

        return withActiveCall { ptr ->
            MNNLlmNative.nativeGenerateStream(ptr, history, maxTokens, callback)
        }
    }

    fun generateStreamStructured(
        messagesJson: String,
        toolsJson: String? = null,
        maxTokens: Int = -1,
        onToken: (String) -> Boolean
    ): Boolean {
        val callback = object : MNNLlmNative.GenerationCallback {
            override fun onToken(token: String): Boolean {
                return try {
                    onToken(token)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in structured token callback", e)
                    false
                }
            }
        }

        return withActiveCall { ptr ->
            MNNLlmNative.nativeGenerateStreamStructured(ptr, messagesJson, toolsJson, maxTokens, callback)
        }
    }
    
    /**
     * 聊天生成（应用模板后生成）
     * @param userContent 用户输入
     * @param maxTokens 最大生成 token 数
     * @param onToken 流式回调
     * @return 是否成功
     */
    fun chat(
        userContent: String,
        maxTokens: Int = -1,
        onToken: (String) -> Boolean
    ): Boolean {
        // 将单个用户消息转换为历史记录格式
        val history = listOf("user" to userContent)
        return generateStream(history, maxTokens, onToken)
    }
    
    /**
     * 重置会话（清除历史和 KV-Cache）
     */
    fun reset() {
        withActiveCall { ptr ->
            MNNLlmNative.nativeReset(ptr)
            Log.d(TAG, "Session reset")
        }
    }
    
    /**
     * 取消当前的生成任务
     * 这会立即中断正在进行的推理过程
     */
    fun cancel() {
        val ptr = synchronized(lock) {
            if (released || llmPtr == 0L) {
                return
            }
            llmPtr
        }
        MNNLlmNative.nativeCancel(ptr)
        Log.d(TAG, "Session cancelled")
    }
    
    /**
     * 设置 LLM 配置
     * @param configJson JSON 格式的配置字符串
     * @return 是否设置成功
     */
    fun setConfig(configJson: String): Boolean {
        return withActiveCall { ptr ->
            val success = MNNLlmNative.nativeSetConfig(ptr, configJson)
            if (success) {
                Log.d(TAG, "Config set successfully: $configJson")
            } else {
                Log.e(TAG, "Failed to set config: $configJson")
            }
            success
        }
    }

    /**
     * 更新 max_new_tokens 配置。
     */
    fun setMaxNewTokens(maxNewTokens: Int): Boolean {
        return setConfig("""{"max_new_tokens":$maxNewTokens}""")
    }

    /**
     * 更新 system_prompt 配置。
     */
    fun setSystemPrompt(systemPrompt: String): Boolean {
        return setConfig("""{"system_prompt":${JSONObject.quote(systemPrompt)}}""")
    }

    /**
     * 更新 assistant_prompt_template 配置。
     */
    fun setAssistantPromptTemplate(template: String): Boolean {
        return setConfig("""{"assistant_prompt_template":${JSONObject.quote(template)}}""")
    }
    
    /**
     * 启用或禁用 thinking 模式（仅对支持的模型有效，如 Qwen3）
     * @param enabled 是否启用 thinking 模式
     * @return 是否设置成功
     */
    fun setThinkingMode(enabled: Boolean): Boolean {
        val configJson = """
        {
            "jinja": {
                "context": {
                    "enable_thinking": $enabled
                }
            }
        }
        """.trimIndent()
        return setConfig(configJson)
    }

    /**
     * 注册或清除音频波形回调。
     */
    fun setAudioDataCallback(callback: MNNLlmNative.AudioDataCallback?): Boolean {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeSetAudioDataCallback(ptr, callback)
        }
    }

    /**
     * 触发语音波形生成。
     */
    fun generateWavform(): Boolean {
        return withActiveCall { ptr ->
            MNNLlmNative.nativeGenerateWavform(ptr)
        }
    }
    
    /**
     * 释放会话
     */
    fun release() {
        val ptr = synchronized(lock) {
            if (released || llmPtr == 0L) {
                return
            }
            released = true
            val old = llmPtr
            llmPtr = 0L
            old
        }

        MNNLlmNative.nativeCancel(ptr)

        synchronized(lock) {
            while (activeCalls > 0) {
                try {
                    (lock as java.lang.Object).wait()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        MNNLlmNative.nativeReleaseLlm(ptr)
        Log.d(TAG, "Session released")
    }
    
    /**
     * 获取模型路径
     */
    fun getModelPath(): String = modelPath
    
    /**
     * 检查会话是否已释放
     */
    fun isReleased(): Boolean = released
    
    protected fun finalize() {
        release()
    }
}

