/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Thread.UncaughtExceptionHandler

class GlobalExceptionHandler : UncaughtExceptionHandler {

    private val defaultHandler: UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        private const val TAG = "GlobalExceptionHandler"
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        // OOM 特殊处理：不做任何上报，立刻崩溃，避免二次分配导致雪上加霜
        if (e is OutOfMemoryError) {
            try {
                Log.e(TAG, "========== OOM 触发，直接崩溃 ==========")
                Log.e(TAG, "线程: ${t.name}")
            } catch (_: Throwable) {
                // 尽量避免再分配
            }
            defaultHandler?.uncaughtException(t, e)
            return
        }

        // 非 OOM：记录日志
        Log.e(TAG, "========== 全局异常捕获 ==========")
        Log.e(TAG, "未捕获的异常: ${e.message}", e)
        Log.e(TAG, "线程: ${t.name}")

        // 生成错误总结
        val errorSummary = generateErrorSummary(e)
        Log.e(TAG, "错误总结:\n$errorSummary")

        // 调用默认处理器（会导致应用崩溃）
        e.printStackTrace()
        defaultHandler?.uncaughtException(t, e)
    }

    /**
     * 生成错误总结（提取关键信息）
     */
    private fun generateErrorSummary(e: Throwable): String {
        val summary = StringBuilder()

        // 异常类型
        val exceptionType = e.javaClass.simpleName
        summary.append("异常类型: $exceptionType")

        // 异常消息
        val message = e.message?.takeIf { it.isNotBlank() } ?: "无异常消息"
        summary.append("\n异常消息: $message")

        // 关键堆栈信息（取前3行，过滤掉系统类）
        val stackTrace = e.stackTrace
        val keyStackLines = stackTrace
            .filter {
                !it.className.startsWith("java.") &&
                !it.className.startsWith("android.") &&
                !it.className.startsWith("kotlin.")
            }
            .take(3)
            .joinToString("\n") {
                "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            }

        if (keyStackLines.isNotEmpty()) {
            summary.append("\n关键堆栈:\n$keyStackLines")
        } else {
            // 如果没有找到关键堆栈，使用前3行
            val fallbackStack = stackTrace.take(3).joinToString("\n") {
                "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            }
            summary.append("\n堆栈信息:\n$fallbackStack")
        }

        return summary.toString()
    }
}

