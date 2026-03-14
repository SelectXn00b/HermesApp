package com.xiaomo.quickjs

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: QuickJS execution support.
 */


import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * QuickJS Bridge
 * 提供 JavaScript 调用 Android 原生功能的桥接接口
 */
class QuickJSBridge(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "QuickJSBridge"
    }

    /**
     * 读取文件
     */
    @Suppress("unused")
    fun readFile(path: String): String {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file: $path", e)
            throw e
        }
    }

    /**
     * 写入文件
     */
    @Suppress("unused")
    fun writeFile(path: String, content: String): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file: $path", e)
            false
        }
    }

    /**
     * 检查文件是否存在
     */
    @Suppress("unused")
    fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    /**
     * 列出目录内容
     */
    @Suppress("unused")
    fun listDir(path: String): String {
        return try {
            val dir = File(path)
            if (!dir.isDirectory) {
                return "[]"
            }

            val files = dir.listFiles()?.map { file ->
                mapOf(
                    "name" to file.name,
                    "path" to file.absolutePath,
                    "isDirectory" to file.isDirectory,
                    "size" to file.length(),
                    "lastModified" to file.lastModified()
                )
            } ?: emptyList()

            com.google.gson.Gson().toJson(files)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list directory: $path", e)
            "[]"
        }
    }

    /**
     * 删除文件
     */
    @Suppress("unused")
    fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: $path", e)
            false
        }
    }

    /**
     * HTTP 请求
     */
    @Suppress("unused")
    fun httpRequest(url: String, method: String, headersJson: String, body: String?): String {
        return try {
            val headers = com.google.gson.Gson().fromJson(headersJson, Map::class.java) as? Map<String, String> ?: emptyMap()

            val requestBuilder = Request.Builder().url(url)

            // 添加请求头
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // 添加请求体
            when (method.uppercase()) {
                "POST", "PUT", "PATCH" -> {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = (body ?: "").toRequestBody(mediaType)
                    requestBuilder.method(method.uppercase(), requestBody)
                }
                else -> {
                    requestBuilder.method(method.uppercase(), null)
                }
            }

            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()

            val responseBody = response.body?.string() ?: ""

            val result = mapOf(
                "status" to response.code,
                "statusText" to response.message,
                "body" to responseBody,
                "headers" to response.headers.toMultimap()
            )

            com.google.gson.Gson().toJson(result)
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: $url", e)
            com.google.gson.Gson().toJson(mapOf(
                "status" to 500,
                "statusText" to "Internal Error",
                "body" to "",
                "error" to e.message
            ))
        }
    }

    /**
     * 日志输出
     */
    @Suppress("unused")
    fun log(message: String) {
        Log.d(TAG, "[JS] $message")
    }

    /**
     * 获取当前时间戳
     */
    @Suppress("unused")
    fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}
