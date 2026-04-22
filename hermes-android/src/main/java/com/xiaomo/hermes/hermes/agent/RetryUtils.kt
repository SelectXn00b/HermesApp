package com.xiaomo.hermes.hermes.agent

import java.util.concurrent.ThreadLocalRandom

/**
 * Retry Utilities - 重试工具
 * 1:1 对齐 hermes/agent/retry_utils.py
 *
 * 用于计算重试间隔、判定是否需要重试、生成 jitter。
 */

/** Python itertools.count() equivalent */
class CountIterator(private var start: Int = 0) : Iterator<Int> {
    override fun hasNext(): Boolean = true
    override fun next(): Int = start++
}

/**
 * 计算指数退避间隔（毫秒）
 *
 * @param attempt 当前重试次数（从 0 开始）
 * @param baseMs 基础间隔（毫秒），默认 1000
 * @param maxMs 最大间隔（毫秒），默认 60000
 * @param jitter 是否添加随机抖动，默认 true
 * @return 下一次重试的等待时间（毫秒）
 */
fun calculateRetryDelayMs(
    attempt: Int,
    baseMs: Long = 1000L,
    maxMs: Long = 60000L,
    jitter: Boolean = true
): Long {
    val exponential = baseMs * (1L shl attempt.coerceAtMost(20))
    val delay = exponential.coerceAtMost(maxMs)
    if (!jitter) return delay
    val jitterMs = ThreadLocalRandom.current().nextLong(0, delay / 2)
    return delay + jitterMs
}

/**
 * 判断一个异常是否可重试
 *
 * @param throwable 异常
 * @param maxRetries 最大重试次数，默认 3
 * @param attempt 当前重试次数
 * @return 是否应该重试
 */
fun shouldRetry(throwable: Throwable, maxRetries: Int = 3, attempt: Int = 0): Boolean {
    if (attempt >= maxRetries) return false
    // 可重试的异常类型
    return when (throwable) {
        is java.io.IOException -> true
        is java.net.SocketTimeoutException -> true
        is java.net.ConnectException -> true
        is retrofit2.HttpException -> {
            val code = throwable.code()
            code == 429 || code in 500..599
        }
        else -> false
    }
}

/**
 * 带重试的执行器
 *
 * @param maxRetries 最大重试次数
 * @param baseMs 基础退避间隔（毫秒）
 * @param block 要执行的代码块
 * @return 执行结果
 */
suspend fun <T> withRetry(
    maxRetries: Int = 3,
    baseMs: Long = 1000L,
    block: suspend (attempt: Int) -> T
): T {
    var lastException: Throwable? = null
    for (attempt in 0 until maxRetries) {
        try {
            return block(attempt)
        } catch (e: Throwable) {
            lastException = e
            if (!shouldRetry(e, maxRetries, attempt)) throw e
            val delay = calculateRetryDelayMs(attempt, baseMs)
            kotlinx.coroutines.delay(delay)
        }
    }
    throw lastException ?: IllegalStateException("Retry failed")


}

/** Python `jittered_backoff` — stub. */
fun jitteredBackoff(attempt: Int, base: Double = 1.0, cap: Double = 30.0): Double {
    val exp = base * Math.pow(2.0, attempt.toDouble())
    return minOf(cap, exp) * (0.5 + Math.random() * 0.5)
}
