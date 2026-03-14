package com.xiaomo.quickjs

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/(all)
 *
 * AndroidForClaw adaptation: QuickJS execution support.
 */


import android.content.Context
import android.util.Log
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * QuickJS JavaScript 执行器
 *
 * 提供完整的 JavaScript 运行时环境，支持:
 * - ES6+ 语法 (const, let, arrow functions, etc.)
 * - async/await 异步编程
 * - Promise
 * - 内置工具库 (lodash-like)
 * - Android 桥接 (文件、HTTP、系统调用)
 *
 * 使用场景:
 * - 数据处理和分析 (JSON, CSV, etc.)
 * - 字符串操作和文本处理
 * - 数组和对象操作
 * - 简单的网络请求
 * - 文件读写
 *
 * @param context Android Context
 */
class QuickJSExecutor(private val context: Context) {

    private var quickJs: QuickJs? = null
    private val bridge = QuickJSBridge(context)


    /**
     * 执行结果
     */
    data class ExecutionResult(
        val success: Boolean,
        val result: String? = null,
        val error: String? = null,
        val metadata: Map<String, Any?> = emptyMap()
    )

    /**
     * 初始化 QuickJS 引擎
     */
    private fun ensureQuickJsInitialized() {
        if (quickJs == null) {
            quickJs = QuickJs.create()
            injectStandardLibrary()
            // TODO: Android Bridge 需要接口，暂时禁用
            // injectAndroidBridge()
        }
    }

    /**
     * 执行 JavaScript 代码
     *
     * @param code JavaScript 代码
     * @param timeout 超时时间(毫秒)
     * @return ExecutionResult
     */
    suspend fun execute(code: String, timeout: Long = 30000): ExecutionResult = withContext(Dispatchers.IO) {
        if (code.isBlank()) {
            return@withContext ExecutionResult(
                success = false,
                error = "Code is empty"
            )
        }

        try {
            ensureQuickJsInitialized()
            val quickJs = this@QuickJSExecutor.quickJs ?: return@withContext ExecutionResult(
                success = false,
                error = "QuickJS not initialized"
            )

            // 包装代码以支持错误捕获和返回值处理
            val wrappedCode = """
                (function() {
                    try {
                        // 使用 eval 来执行用户代码，自动返回最后一个表达式的值
                        const __result__ = eval(`$code`);

                        // 将结果转换为字符串
                        if (typeof __result__ === 'object' && __result__ !== null) {
                            return JSON.stringify(__result__, null, 2);
                        }
                        return String(__result__ !== undefined ? __result__ : 'undefined');
                    } catch (error) {
                        return JSON.stringify({
                            __error__: true,
                            message: error.message,
                            stack: error.stack
                        });
                    }
                })();
            """.trimIndent()

            Log.d(TAG, "Executing JavaScript code (${code.length} chars)")

            // 执行代码
            val result = quickJs.evaluate(wrappedCode)
            val resultStr = result?.toString() ?: "null"

            Log.d(TAG, "JavaScript raw result: $resultStr")

            // 检查是否是错误
            if (resultStr.contains("\"__error__\":true")) {
                val errorObj = parseJSON(resultStr)
                val errorMsg = errorObj?.get("message") as? String ?: "Unknown error"
                val errorStack = errorObj?.get("stack") as? String

                Log.e(TAG, "JavaScript execution error: $errorMsg")
                if (errorStack != null) {
                    Log.e(TAG, "Stack trace: $errorStack")
                }

                return@withContext ExecutionResult(
                    success = false,
                    error = "JavaScript error: $errorMsg"
                )
            }

            Log.d(TAG, "JavaScript executed successfully")

            ExecutionResult(
                success = true,
                result = resultStr,
                metadata = mapOf(
                    "executionTime" to "completed",
                    "codeLength" to code.length
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute JavaScript", e)
            ExecutionResult(
                success = false,
                error = "Execution failed: ${e.message}"
            )
        }
    }

    /**
     * 注入标准工具库
     */
    private fun injectStandardLibrary() {
        val quickJs = this.quickJs ?: return

        try {
            quickJs.evaluate(STANDARD_LIBRARY_JS)
            Log.d(TAG, "Standard library injected successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject standard library", e)
        }
    }

    /**
     * 注入 Android 桥接接口
     */
    private fun injectAndroidBridge() {
        val quickJs = this.quickJs ?: return

        try {
            quickJs.set("Android", QuickJSBridge::class.java, bridge)
            Log.d(TAG, "Android bridge injected successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject Android bridge", e)
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        quickJs?.close()
        quickJs = null
    }

    private fun parseJSON(text: String): Map<String, Any?>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            com.google.gson.Gson().fromJson(text, Map::class.java) as? Map<String, Any?>
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "QuickJSExecutor"

        /**
         * 标准库 JavaScript 代码
         */
        private val STANDARD_LIBRARY_JS = """
// ==================== 工具函数库 ====================

// Lodash-like 数组/对象操作
const _ = {
    // 数组操作
    map: (arr, fn) => arr.map(fn),
    filter: (arr, fn) => arr.filter(fn),
    reduce: (arr, fn, init) => arr.reduce(fn, init),
    find: (arr, fn) => arr.find(fn),
    findIndex: (arr, fn) => arr.findIndex(fn),
    uniq: (arr) => [...new Set(arr)],
    flatten: (arr) => arr.flat(Infinity),
    chunk: (arr, size) => {
        const result = [];
        for (let i = 0; i < arr.length; i += size) {
            result.push(arr.slice(i, i + size));
        }
        return result;
    },
    compact: (arr) => arr.filter(Boolean),
    reverse: (arr) => [...arr].reverse(),

    // 对象操作
    groupBy: (arr, key) => arr.reduce((acc, item) => {
        const k = typeof key === 'function' ? key(item) : item[key];
        (acc[k] = acc[k] || []).push(item);
        return acc;
    }, {}),
    countBy: (arr, key) => arr.reduce((acc, item) => {
        const k = typeof key === 'function' ? key(item) : item[key];
        acc[k] = (acc[k] || 0) + 1;
        return acc;
    }, {}),
    keyBy: (arr, key) => arr.reduce((acc, item) => {
        const k = typeof key === 'function' ? key(item) : item[key];
        acc[k] = item;
        return acc;
    }, {}),

    // 统计函数
    sum: (arr) => arr.reduce((a, b) => a + b, 0),
    mean: (arr) => arr.reduce((a, b) => a + b, 0) / arr.length,
    max: (arr) => Math.max(...arr),
    min: (arr) => Math.min(...arr),

    // 字符串操作
    capitalize: (str) => str.charAt(0).toUpperCase() + str.slice(1).toLowerCase(),
    camelCase: (str) => str.replace(/[-_\s]+(.)?/g, (_, c) => c ? c.toUpperCase() : ''),
    snakeCase: (str) => str.replace(/[A-Z]/g, (c) => '_' + c.toLowerCase()).replace(/^_/, ''),
    kebabCase: (str) => str.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase()).replace(/^-/, ''),
};

// ==================== 数据解析 ====================

// CSV 解析器
function parseCSV(text, options = {}) {
    const delimiter = options.delimiter || ',';
    const hasHeader = options.hasHeader !== false;

    const lines = text.trim().split('\n');
    const data = lines.map(line => {
        return line.split(delimiter).map(cell => cell.trim());
    });

    if (hasHeader && data.length > 0) {
        const headers = data[0];
        return data.slice(1).map(row => {
            const obj = {};
            headers.forEach((header, i) => {
                obj[header] = row[i];
            });
            return obj;
        });
    }

    return data;
}

// JSON 安全解析
function parseJSON(text, defaultValue = null) {
    try {
        return JSON.parse(text);
    } catch (e) {
        return defaultValue;
    }
}

// ==================== 文件系统 API ====================

const fs = {
    readFile: (path) => {
        return Android.readFile(path);
    },

    writeFile: (path, content) => {
        return Android.writeFile(path, content);
    },

    exists: (path) => {
        return Android.fileExists(path);
    },

    listDir: (path) => {
        const result = Android.listDir(path);
        return parseJSON(result, []);
    },

    delete: (path) => {
        return Android.deleteFile(path);
    }
};

// ==================== HTTP 客户端 ====================

async function fetch(url, options = {}) {
    const method = options.method || 'GET';
    const headers = JSON.stringify(options.headers || {});
    const body = options.body || null;

    const result = Android.httpRequest(url, method, headers, body);
    const response = parseJSON(result);

    if (!response) {
        throw new Error('HTTP request failed');
    }

    return {
        ok: response.status >= 200 && response.status < 300,
        status: response.status,
        statusText: response.statusText || '',
        text: () => Promise.resolve(response.body || ''),
        json: () => Promise.resolve(parseJSON(response.body)),
        headers: response.headers || {}
    };
}

// ==================== 系统工具 ====================

const System = {
    log: (message) => {
        Android.log(String(message));
    },

    currentTimeMillis: () => {
        return Android.currentTimeMillis();
    },

    sleep: (ms) => {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
};

// ==================== 实用函数 ====================

// 重试函数
async function retry(fn, options = {}) {
    const maxAttempts = options.maxAttempts || 3;
    const delay = options.delay || 1000;

    for (let i = 0; i < maxAttempts; i++) {
        try {
            return await fn();
        } catch (error) {
            if (i === maxAttempts - 1) throw error;
            await System.sleep(delay);
        }
    }
}

// 超时包装
async function timeout(promise, ms) {
    return Promise.race([
        promise,
        new Promise((_, reject) =>
            setTimeout(() => reject(new Error('Timeout')), ms)
        )
    ]);
}

// 全局导出
globalThis._ = _;
globalThis.parseCSV = parseCSV;
globalThis.parseJSON = parseJSON;
globalThis.fs = fs;
globalThis.fetch = fetch;
globalThis.System = System;
globalThis.retry = retry;
globalThis.timeout = timeout;
        """.trimIndent()
    }
}
