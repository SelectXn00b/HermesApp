package com.xiaomo.hermes.hermes.plugins.memory

/**
 * Memory Provider - 记忆后端接口
 * 1:1 对齐 hermes/plugins/memory/__init__.py
 *
 * 所有 memory 后端都实现此接口
 * 支持 8 个后端：holographic、honcho、mem0、byterover、hindsight、retaindb、openviking、supermemory
 */
interface MemoryProvider {

    /** 后端名称 */
    val providerName: String

    /**
     * 初始化记忆后端
     */
    suspend fun initialize(config: Map<String, Any>)

    /**
     * 存储记忆
     *
     * @param content 记忆内容
     * @param metadata 元数据
     * @return 记忆 ID
     */
    suspend fun store(content: String, metadata: Map<String, Any> = emptyMap()): String

    /**
     * 检索记忆（向量相似度搜索）
     *
     * @param query 查询内容
     * @param limit 返回数量
     * @param threshold 相似度阈值
     * @return 匹配的记忆列表
     */
    suspend fun retrieve(
        query: String,
        limit: Int = 10,
        threshold: Double = 0.7
    ): List<MemoryItem>

    /**
     * 删除记忆
     *
     * @param memoryId 记忆 ID
     * @return 是否删除成功
     */
    suspend fun delete(memoryId: String): Boolean

    /**
     * 列出所有记忆
     *
     * @param limit 返回数量
     * @param offset 偏移量
     * @return 记忆列表
     */
    suspend fun list(limit: Int = 100, offset: Int = 0): List<MemoryItem>

    /**
     * 关闭连接
     */
    suspend fun close()
}

/**
 * 记忆条目
 */
data class MemoryItem(
    val id: String,
    val content: String,
    val metadata: Map<String, Any> = emptyMap(),
    val score: Double? = null,  // 相似度分数（检索时返回）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
