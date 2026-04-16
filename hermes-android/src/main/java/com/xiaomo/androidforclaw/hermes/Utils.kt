package com.xiaomo.androidforclaw.hermes

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 共享工具函数
 * 1:1 对齐 hermes-agent/utils.py
 */

// ── 全局 Gson 实例 ──────────────────────────────────────────────────────
val prettyGson: Gson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create()

// ── Truthy 字符串 ────────────────────────────────────────────────────────
private val TRUTHY_STRINGS = setOf("1", "true", "yes", "on")

/**
 * 判断布尔式值
 * Python: is_truthy_value(value, default=False)
 */
fun isTruthyValue(value: Any?, default: Boolean = false): Boolean {
    if (value == null) return default
    if (value is Boolean) return value
    if (value is String) return value.trim().lowercase() in TRUTHY_STRINGS
    return value != 0 && value != ""
}

/**
 * 检查环境变量是否启用
 * Python: env_var_enabled(name, default="")
 */
fun envVarEnabled(name: String, default: String = ""): Boolean {
    val value = System.getenv(name) ?: default
    return isTruthyValue(value, default = false)
}

// ── 原子写入 ──────────────────────────────────────────────────────────────

/**
 * 原子写入 JSON 文件
 * Python: atomic_json_write(path, data, indent=2)
 *
 * 使用临时文件 + rename 保证原子性。
 */
fun atomicJsonWrite(
    path: File,
    data: Any,
    indent: Int = 2,
    gson: Gson = prettyGson) {
    path.parentFile.mkdirs()

    val tmpFile = File.createTempFile(".${path.nameWithoutExtension}_", ".tmp", path.parentFile)
    try {
        tmpFile.writeText(gson.toJson(data), Charsets.UTF_8)
        // Android 不支持 os.fsync，但 rename 是原子的
        if (!tmpFile.renameTo(path)) {
            // 如果 rename 失败（跨文件系统），复制后删除
            tmpFile.copyTo(path, overwrite = true)
            tmpFile.delete()
        }
    } catch (e: Exception) {
        try {
            tmpFile.delete()
        } catch (ignored: Exception) {}
        throw e
    }
}

/**
 * 原子写入 YAML 文件
 * Python: atomic_yaml_write(path, data)
 *
 * 使用 snakeyaml 库。
 */
fun atomicYamlWrite(
    path: File,
    data: Any,
    sortKeys: Boolean = false,
    extraContent: String? = null) {
    path.parentFile.mkdirs()

    val options = DumperOptions()
    options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
    options.isPrettyFlow = true
    options.splitLines = true
    if (sortKeys) {
        options.isCanonical = false
    }

    val yaml = Yaml(options)

    val tmpFile = File.createTempFile(".${path.nameWithoutExtension}_", ".tmp", path.parentFile)
    try {
        val content = StringBuilder()
        content.append(yaml.dump(data))
        if (extraContent != null) {
            content.append(extraContent)
        }
        tmpFile.writeText(content.toString(), Charsets.UTF_8)

        if (!tmpFile.renameTo(path)) {
            tmpFile.copyTo(path, overwrite = true)
            tmpFile.delete()
        }
    } catch (e: Exception) {
        try {
            tmpFile.delete()
        } catch (ignored: Exception) {}
        throw e
    }
}

// ── JSON 工具 ──────────────────────────────────────────────────────────────

/**
 * 安全解析 JSON
 * Python: safe_json_loads(text, default=None)
 */
fun safeJsonLoads(text: String, default: Any? = null): Any? {
    return try {
        gson.fromJson(text, Any::class.java) ?: default
    } catch (e: JsonSyntaxException) {
        default
    }
}

/**
 * 安全解析 JSON 为 Map
 */
fun safeJsonLoadsMap(text: String): Map<String, Any>? {
    return try {
        val type = com.google.gson.reflect.TypeToken.getParameterized(
            Map::class.java, String::class.java, Any::class.java
        ).type
        gson.fromJson(text, type)
    } catch (e: JsonSyntaxException) {
        null
    }
}

/**
 * 安全解析 JSON 为 List
 */
fun safeJsonLoadsList(text: String): List<Any>? {
    return try {
        val type = com.google.gson.reflect.TypeToken.getParameterized(
            List::class.java, Any::class.java
        ).type
        gson.fromJson(text, type)
    } catch (e: JsonSyntaxException) {
        null
    }
}

/**
 * 对象转 JSON 字符串
 */
fun toJson(data: Any?, pretty: Boolean = false): String {
    return if (pretty) {
        prettyGson.toJson(data)
    } else {
        gson.toJson(data)
    }
}

// ── 环境变量工具 ──────────────────────────────────────────────────────────

/**
 * 读取环境变量为整数
 * Python: env_int(key, default=0)
 */
fun envInt(key: String, default: Int = 0): Int {
    val raw = System.getenv(key)?.trim() ?: ""
    if (raw.isEmpty()) return default
    return try {
        raw.toInt()
    } catch (e: NumberFormatException) {
        default
    }
}

/**
 * 读取环境变量为布尔值
 * Python: env_bool(key, default=False)
 */
fun envBool(key: String, default: Boolean = false): Boolean {
    val raw = System.getenv(key) ?: ""
    return isTruthyValue(raw, default = default)
}

/**
 * 读取环境变量为字符串
 * Python: os.getenv(key, default="")
 */
fun envString(key: String, default: String = ""): String {
    return System.getenv(key) ?: default
}

// ── 文件工具 ──────────────────────────────────────────────────────────────

/**
 * 安全读取文件
 */
fun readFile(path: File, default: String = ""): String {
    return try {
        if (path.exists()) path.readText(Charsets.UTF_8) else default
    } catch (e: Exception) {
        default
    }
}

/**
 * 安全写入文件
 */
fun writeFile(path: File, content: String) {
    path.parentFile.mkdirs()
    path.writeText(content, Charsets.UTF_8)
}

/**
 * 安全删除文件
 */
fun deleteFile(path: File): Boolean {
    return try {
        if (path.exists()) path.delete() else true
    } catch (e: Exception) {
        false
    }
}

/**
 * 确保目录存在
 */
fun ensureDir(path: File): File {
    if (!path.exists()) {
        path.mkdirs()
    }
    return path
}

/**
 * 获取文件扩展名
 */
fun File.extension(): String {
    return this.extension
}

/**
 * 获取文件名（不含扩展名）
 */
fun File.stem(): String {
    return this.nameWithoutExtension
}
