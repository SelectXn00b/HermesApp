package com.xiaomo.androidforclaw.hermes.plugins.memory.holographic

import com.google.gson.reflect.TypeToken
import com.xiaomo.androidforclaw.hermes.gson
import com.xiaomo.androidforclaw.hermes.getLogger
import com.xiaomo.androidforclaw.hermes.prettyGson
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Holographic 持久化存储层
 * 1:1 对齐 hermes-agent/plugins/memory/holographic/store.py
 *
 * 管理记忆数据的磁盘存储、索引和同步。
 */

private val logger = getLogger("holographic.store")

// ── 存储配置 ──────────────────────────────────────────────────────────────
data class StoreConfig(
    val storageDir: File,
    val indexFile: String = "index.json",
    val dataFile: String = "memories.json",
    val backupDir: String = "backups",
    val maxBackups: Int = 5,
    val autoBackup: Boolean = true,
    val compactThreshold: Int = 1000)

// ── 存储索引 ──────────────────────────────────────────────────────────────
data class StoreIndex(
    val version: Int = 1,
    val totalCount: Int = 0,
    val lastModified: Long = System.currentTimeMillis(),
    val checksum: String = "",
    val tags: Map<String, List<String>> = emptyMap(), // tag -> memory IDs
)

// ── 存储引擎 ──────────────────────────────────────────────────────────────
class HolographicStore(
    private val config: StoreConfig) {

    private val _memories = ConcurrentHashMap<String, HolographicMemory>()
    private var _index: StoreIndex = StoreIndex()
    private var _dirty = false

    init {
        config.storageDir.mkdirs()
        loadIndex()
        loadData()
    }

    // ── 公开方法 ──────────────────────────────────────────────────────────

    /**
     * 加载所有记忆到内存
     * Python: store.py -> load_all()
     */
    fun loadAll(): Map<String, HolographicMemory> {
        return _memories.toMap()
    }

    /**
     * 保存单个记忆
     * Python: store.py -> save(memory)
     */
    fun save(memory: HolographicMemory) {
        _memories[memory.id] = memory
        _dirty = true
        persistData()
        updateIndex()
    }

    /**
     * 批量保存
     */
    fun saveBatch(memories: List<HolographicMemory>) {
        for (memory in memories) {
            _memories[memory.id] = memory
        }
        _dirty = true
        persistData()
        updateIndex()
    }

    /**
     * 删除记忆
     * Python: store.py -> delete(memory_id)
     */
    fun delete(memoryId: String): Boolean {
        val removed = _memories.remove(memoryId) != null
        if (removed) {
            _dirty = true
            persistData()
            updateIndex()
        }
        return removed
    }

    /**
     * 批量删除
     */
    fun deleteBatch(memoryIds: List<String>): Int {
        var count = 0
        for (id in memoryIds) {
            if (_memories.remove(id) != null) count++
        }
        if (count > 0) {
            _dirty = true
            persistData()
            updateIndex()
        }
        return count
    }

    /**
     * 获取单个记忆
     */
    fun get(memoryId: String): HolographicMemory? {
        return _memories[memoryId]
    }

    /**
     * 检查记忆是否存在
     */
    fun exists(memoryId: String): Boolean {
        return _memories.containsKey(memoryId)
    }

    /**
     * 获取记忆数量
     */
    fun count(): Int {
        return _memories.size
    }

    /**
     * 获取所有记忆 ID
     */
    fun listIds(): Set<String> {
        return _memories.keys.toSet()
    }

    /**
     * 按标签过滤
     */
    fun getByTag(tag: String): List<HolographicMemory> {
        return _memories.values.filter { tag in it.tags }
    }

    /**
     * 按标签批量过滤
     */
    fun getByTags(tags: List<String>): List<HolographicMemory> {
        return _memories.values.filter { memory ->
            tags.any { it in memory.tags }
        }
    }

    /**
     * 按时间范围过滤
     */
    fun getByTimeRange(startTime: Long, endTime: Long): List<HolographicMemory> {
        return _memories.values.filter {
            it.createdAt in startTime..endTime
        }
    }

    /**
     * 清空所有记忆
     */
    fun clear() {
        _memories.clear()
        _dirty = true
        persistData()
        updateIndex()
    }

    /**
     * 压缩存储（清理过期和重复数据）
     */
    fun compact(): Int {
        var removed = 0
        val cutoff = System.currentTimeMillis() - (90 * 24 * 3600 * 1000L) // 90 天

        val toRemove = _memories.filter { (_, memory) ->
            memory.importance < 0.1 && memory.createdAt < cutoff && memory.accessCount == 0
        }

        for ((id, _) in toRemove) {
            _memories.remove(id)
            removed++
        }

        if (removed > 0) {
            _dirty = true
            persistData()
            updateIndex()
            logger.info("Compacted store: removed $removed memories")
        }

        return removed
    }

    /**
     * 创建备份
     */
    fun backup(): Boolean {
        val backupDir = File(config.storageDir, config.backupDir)
        backupDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        val backupFile = File(backupDir, "memories_${timestamp}.json.bak")

        return try {
            val content = prettyGson.toJson(_memories)
            backupFile.writeText(content, Charsets.UTF_8)

            // 清理旧备份
            val backups = backupDir.listFiles()
                ?.filter { it.name.endsWith(".json.bak") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            if (backups.size > config.maxBackups) {
                backups.drop(config.maxBackups).forEach { it.delete() }
            }

            logger.info("Backup created: ${backupFile.name}")
            true
        } catch (e: Exception) {
            logger.error("Backup failed: ${e.message}")
            false
        }
    }

    /**
     * 从备份恢复
     */
    fun restore(backupFile: File): Boolean {
        if (!backupFile.exists()) return false

        return try {
            val content = backupFile.readText(Charsets.UTF_8)
            val type = object : TypeToken<Map<String, HolographicMemory>>() {}.type
            val restored: Map<String, HolographicMemory> = gson.fromJson(content, type)

            _memories.clear()
            _memories.putAll(restored)
            _dirty = true
            persistData()
            updateIndex()

            logger.info("Restored ${_memories.size} memories from backup")
            true
        } catch (e: Exception) {
            logger.error("Restore failed: ${e.message}")
            false
        }
    }

    /**
     * 获取存储统计
     */
    fun getStats(): Map<String, Any> {
        val dataFile = File(config.storageDir, config.dataFile)
        return mapOf(
            "totalMemories" to _memories.size,
            "storageDir" to config.storageDir.absolutePath,
            "dataFileSize" to if (dataFile.exists()) dataFile.length() else 0,
            "indexVersion" to _index.version,
            "lastModified" to _index.lastModified,
            "dirty" to _dirty)
    }

    // ── 内部方法 ──────────────────────────────────────────────────────────

    /**
     * 加载索引文件
     */
    private fun loadIndex() {
        val indexFile = File(config.storageDir, config.indexFile)
        if (!indexFile.exists()) return

        try {
            val content = indexFile.readText(Charsets.UTF_8)
            _index = gson.fromJson(content, StoreIndex::class.java) ?: StoreIndex()
        } catch (e: Exception) {
            logger.warning("Failed to load index: ${e.message}")
        }
    }

    /**
     * 加载数据文件
     */
    private fun loadData() {
        val dataFile = File(config.storageDir, config.dataFile)
        if (!dataFile.exists()) return

        acquireLock(dataFile) { _ ->
            try {
                val content = dataFile.readBytes().toString(Charsets.UTF_8)
                if (content.isBlank()) return@acquireLock

                val type = object : TypeToken<Map<String, HolographicMemory>>() {}.type
                val loaded: Map<String, HolographicMemory> = gson.fromJson(content, type)
                _memories.putAll(loaded)
                logger.info("Loaded ${_memories.size} memories from disk")
            } catch (e: Exception) {
                logger.warning("Failed to load data: ${e.message}")
            }
        }
    }

    /**
     * 持久化数据到磁盘
     */
    private fun persistData() {
        if (!_dirty) return

        val dataFile = File(config.storageDir, config.dataFile)
        acquireLock(dataFile) { file ->
            try {
                val content = prettyGson.toJson(_memories)
                file.setLength(0)
                file.write(content.toByteArray(Charsets.UTF_8))
                file.fd.sync()
                _dirty = false
            } catch (e: Exception) {
                logger.error("Failed to persist data: ${e.message}")
            }
        }
    }

    /**
     * 更新索引
     */
    private fun updateIndex() {
        // 按标签建立索引
        val tagIndex = mutableMapOf<String, MutableList<String>>()
        for ((id, memory) in _memories) {
            for (tag in memory.tags) {
                tagIndex.getOrPut(tag) { mutableListOf() }.add(id)
            }
        }

        _index = StoreIndex(
            version = _index.version,
            totalCount = _memories.size,
            lastModified = System.currentTimeMillis(),
            tags = tagIndex)

        val indexFile = File(config.storageDir, config.indexFile)
        try {
            indexFile.writeText(prettyGson.toJson(_index), Charsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Failed to update index: ${e.message}")
        }
    }

    /**
     * 文件锁操作
     */
    private fun <T> acquireLock(file: File, block: (RandomAccessFile) -> T): T {
        val lockFile = File(file.parent, ".${file.name}.lock")
        val raf = RandomAccessFile(lockFile, "rw")
        var channel: FileChannel? = null
        var lock: FileLock? = null

        try {
            channel = raf.channel
            lock = channel.lock()
            return block(raf)
        } finally {
            lock?.release()
            channel?.close()
            raf.close()
        }
    }
}
