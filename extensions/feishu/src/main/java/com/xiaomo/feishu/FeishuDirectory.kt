package com.xiaomo.feishu

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel runtime.
 */


import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书联系人目录
 * 对齐 OpenClaw directory.ts
 *
 * 功能：
 * - 列出配置的用户和群组
 * - 从 API 实时获取用户和群组列表
 * - 支持搜索和限制数量
 */
object FeishuDirectory {
    private const val TAG = "FeishuDirectory"

    /**
     * 用户信息
     */
    data class DirectoryPeer(
        val kind: String = "user",
        val id: String,
        val name: String? = null
    )

    /**
     * 群组信息
     */
    data class DirectoryGroup(
        val kind: String = "group",
        val id: String,
        val name: String? = null
    )

    /**
     * 列出配置的用户
     *
     * @param config 飞书配置
     * @param query 搜索查询（可选）
     * @param limit 限制数量（可选）
     * @return 用户列表
     */
    fun listConfiguredPeers(
        config: FeishuConfig,
        query: String? = null,
        limit: Int? = null
    ): List<DirectoryPeer> {
        val q = query?.trim()?.lowercase() ?: ""
        val ids = mutableSetOf<String>()

        // 从 allowFrom 获取
        for (entry in config.allowFrom) {
            val trimmed = entry.trim()
            if (trimmed.isNotEmpty() && trimmed != "*") {
                ids.add(trimmed)
            }
        }

        // 过滤和限制
        return ids
            .filter { id -> q.isEmpty() || id.lowercase().contains(q) }
            .let { list -> if (limit != null && limit > 0) list.take(limit) else list }
            .map { id -> DirectoryPeer(id = id) }
    }

    /**
     * 列出配置的群组
     *
     * @param config 飞书配置
     * @param query 搜索查询（可选）
     * @param limit 限制数量（可选）
     * @return 群组列表
     */
    fun listConfiguredGroups(
        config: FeishuConfig,
        query: String? = null,
        limit: Int? = null
    ): List<DirectoryGroup> {
        val q = query?.trim()?.lowercase() ?: ""
        val ids = mutableSetOf<String>()

        // 从 groupAllowFrom 获取
        for (entry in config.groupAllowFrom) {
            val trimmed = entry.trim()
            if (trimmed.isNotEmpty() && trimmed != "*") {
                ids.add(trimmed)
            }
        }

        // 过滤和限制
        return ids
            .filter { id -> q.isEmpty() || id.lowercase().contains(q) }
            .let { list -> if (limit != null && limit > 0) list.take(limit) else list }
            .map { id -> DirectoryGroup(id = id) }
    }

    /**
     * 从 API 实时获取用户列表
     *
     * @param client Feishu 客户端
     * @param config 飞书配置
     * @param query 搜索查询（可选）
     * @param limit 限制数量（默认 50）
     * @return 用户列表
     */
    suspend fun listPeersLive(
        client: FeishuClient,
        config: FeishuConfig,
        query: String? = null,
        limit: Int = 50
    ): List<DirectoryPeer> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("/open-apis/contact/v3/users?page_size=${minOf(limit, 50)}")

            if (response.isFailure) {
                Log.w(TAG, "Failed to list users live: ${response.exceptionOrNull()?.message}")
                return@withContext listConfiguredPeers(config, query, limit)
            }

            val data = response.getOrNull()
            val code = data?.get("code")?.asInt ?: -1

            if (code != 0) {
                Log.w(TAG, "List users API error: ${data?.get("msg")?.asString}")
                return@withContext listConfiguredPeers(config, query, limit)
            }

            val dataMap = data?.getAsJsonObject("data")
            val items = dataMap?.getAsJsonArray("items") ?: return@withContext emptyList()

            val q = query?.trim()?.lowercase() ?: ""
            val peers = mutableListOf<DirectoryPeer>()

            for (item in items) {
                val user = item.asJsonObject
                val openId = user.get("open_id")?.asString ?: continue
                val name = user.get("name")?.asString ?: ""

                // 搜索过滤
                if (q.isNotEmpty() && !openId.lowercase().contains(q) && !name.lowercase().contains(q)) {
                    continue
                }

                peers.add(DirectoryPeer(id = openId, name = name.ifEmpty { null }))

                if (peers.size >= limit) {
                    break
                }
            }

            return@withContext peers

        } catch (e: Exception) {
            Log.e(TAG, "Exception listing users live", e)
            return@withContext listConfiguredPeers(config, query, limit)
        }
    }

    /**
     * 从 API 实时获取群组列表
     *
     * @param client Feishu 客户端
     * @param config 飞书配置
     * @param query 搜索查询（可选）
     * @param limit 限制数量（默认 50）
     * @return 群组列表
     */
    suspend fun listGroupsLive(
        client: FeishuClient,
        config: FeishuConfig,
        query: String? = null,
        limit: Int = 50
    ): List<DirectoryGroup> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("/open-apis/im/v1/chats?page_size=${minOf(limit, 100)}")

            if (response.isFailure) {
                Log.w(TAG, "Failed to list groups live: ${response.exceptionOrNull()?.message}")
                return@withContext listConfiguredGroups(config, query, limit)
            }

            val data = response.getOrNull()
            val code = data?.get("code")?.asInt ?: -1

            if (code != 0) {
                Log.w(TAG, "List groups API error: ${data?.get("msg")?.asString}")
                return@withContext listConfiguredGroups(config, query, limit)
            }

            val dataMap = data?.getAsJsonObject("data")
            val items = dataMap?.getAsJsonArray("items") ?: return@withContext emptyList()

            val q = query?.trim()?.lowercase() ?: ""
            val groups = mutableListOf<DirectoryGroup>()

            for (item in items) {
                val chat = item.asJsonObject
                val chatId = chat.get("chat_id")?.asString ?: continue
                val name = chat.get("name")?.asString ?: ""

                // 搜索过滤
                if (q.isNotEmpty() && !chatId.lowercase().contains(q) && !name.lowercase().contains(q)) {
                    continue
                }

                groups.add(DirectoryGroup(id = chatId, name = name.ifEmpty { null }))

                if (groups.size >= limit) {
                    break
                }
            }

            return@withContext groups

        } catch (e: Exception) {
            Log.e(TAG, "Exception listing groups live", e)
            return@withContext listConfiguredGroups(config, query, limit)
        }
    }
}
