package com.ai.assistance.operit.util.stream.plugins

/**
 * The possible states of a stream processing plugin.
 */
enum class PluginState {
    /** The plugin is inactive and not matching any pattern. */
    IDLE,
    /** The plugin has matched the beginning of a potential pattern and is consuming more characters to verify a full match. */
    TRYING,
    /** The plugin has confirmed a full pattern match and is actively processing the content of that pattern. */
    PROCESSING,
    /** 
     * The plugin is in a waiting state, accumulating characters that may need to be returned to the processor.
     * If the next character confirms the pattern, it continues as PROCESSING; otherwise, returns all accumulated characters.
     */
    WAITFOR
}

/**
 * 流处理插件接口
 * 用于实现各种针对字符流的处理逻辑
 */
interface StreamPlugin {
    /**
     * The current state of the plugin. This replaces the previous boolean flags
     * for better state management.
     */
    val state: PluginState
    
    /**
     * 处理单个字符，并决定是否应将其发射到流中。
     * @param c 要处理的字符
     * @param atStartOfLine 标记字符是否位于一行的开头
     * @return 如果该字符应被包含在最终的组流中，则返回 `true`；如果希望过滤掉（不发射），则返回 `false`。
     */
    fun processChar(c: Char, atStartOfLine: Boolean): Boolean
    
    /**
     * 初始化插件
     * @return 是否初始化成功
     */
    fun initPlugin(): Boolean
    
    /**
     * 销毁插件，释放资源
     */
    fun destroy()
    
    /**
     * 重置插件状态
     */
    fun reset()
} 