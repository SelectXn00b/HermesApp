package com.ai.assistance.operit.data.db

import android.content.Context
import com.ai.assistance.operit.data.model.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ObjectBoxManager {
    private val stores = ConcurrentHashMap<String, BoxStore>()
    private val storeLock = Any()

    fun get(context: Context, profileId: String): BoxStore {
        synchronized(storeLock) {
            stores[profileId]?.let { return it }
            val store = buildStore(context, profileId)
            stores[profileId] = store
            return store
        }
    }

    private fun buildStore(context: Context, profileId: String): BoxStore {
        // 如果profileId是"default"，我们使用旧的数据库位置以实现向后兼容
        val dbName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
        val dbDir = File(context.filesDir, dbName)

        return MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .directory(dbDir)
            .build()
    }

    fun close(profileId: String) {
        synchronized(storeLock) {
            val store = stores.remove(profileId) ?: return
            store.close()
        }
    }

    /**
     * 物理删除指定profileId的数据库（包括关闭store和删除文件夹）。
     */
    fun delete(context: Context, profileId: String) {
        synchronized(storeLock) {
            stores.remove(profileId)?.close() // 先关闭
            val dbName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
            val dbDir = File(context.filesDir, dbName)
            if (dbDir.exists()) {
                dbDir.deleteRecursively()
            }
        }
    }

    fun closeAll() {
        synchronized(storeLock) {
            stores.values.forEach { it.close() }
            stores.clear()
        }
    }
} 
