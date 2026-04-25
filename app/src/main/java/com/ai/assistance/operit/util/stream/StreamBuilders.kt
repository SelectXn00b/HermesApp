package com.ai.assistance.operit.util.stream

import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * 创建一个空的Stream
 */
fun <T> emptyStream(): Stream<T> = object : AbstractStream<T>() {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("emptyStream", "收集空Stream")
        // 不发射任何值
    }
    
    override suspend fun emitBufferedItem(item: T) {
        // 空Stream不会有缓冲项
    }
}

/**
 * 从单个值创建Stream
 */
fun <T> streamOf(value: T): Stream<T> = object : AbstractStream<T>() {
    private var activeCollector: StreamCollector<T>? = null
    
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("streamOf", "创建单值Stream: $value")
        activeCollector = collector
        if (!tryBuffer(value)) {
            collector.emit(value)
        }
    }
    
    override suspend fun emitBufferedItem(item: T) {
        activeCollector?.emit(item)
    }
}

/**
 * 从多个值创建Stream
 */
fun <T> streamOf(vararg values: T): Stream<T> = object : AbstractStream<T>() {
    private var activeCollector: StreamCollector<T>? = null
    
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("streamOf", "创建多值Stream, 元素数量: ${values.size}")
        activeCollector = collector
        for (value in values) {
            StreamLogger.v("streamOf", "发射元素: $value")
            if (!tryBuffer(value)) {
                collector.emit(value)
            }
        }
    }
    
    override suspend fun emitBufferedItem(item: T) {
        activeCollector?.emit(item)
    }
}

/**
 * 从集合创建Stream
 */
fun <T> Collection<T>.asStream(): Stream<T> = object : AbstractStream<T>() {
    private var activeCollector: StreamCollector<T>? = null
    
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("Collection.asStream", "从集合创建Stream, 元素数量: ${this@asStream.size}")
        activeCollector = collector
        
        for (item in this@asStream) {
            StreamLogger.v("Collection.asStream", "发射元素: $item")
            if (!tryBuffer(item)) {
                collector.emit(item)
            }
        }
    }
    
    override suspend fun emitBufferedItem(item: T) {
        activeCollector?.emit(item)
    }
}

/**
 * 从序列创建Stream
 */
fun <T> Sequence<T>.asStream(): Stream<T> = object : AbstractStream<T>() {
    private var activeCollector: StreamCollector<T>? = null
    
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("Sequence.asStream", "从序列创建Stream")
        activeCollector = collector
        var count = 0
        for (item in this@asStream) {
            count++
            StreamLogger.v("Sequence.asStream", "发射元素[$count]: $item")
            if (!tryBuffer(item)) {
                collector.emit(item)
            }
        }
        StreamLogger.d("Sequence.asStream", "序列Stream收集完成, 共$count 个元素")
    }
    
    override suspend fun emitBufferedItem(item: T) {
        activeCollector?.emit(item)
    }
}

/**
 * 通过调用构建器创建Stream
 */
fun <T> stream(block: suspend StreamCollector<T>.() -> Unit): Stream<T> = object : AbstractStream<T>() {
    private var activeCollector: StreamCollector<T>? = null
    private val wrappedCollector = object : StreamCollector<T> {
        override suspend fun emit(value: T) {
            if (!isClosed() && !tryBuffer(value)) {
                activeCollector?.emit(value)
            }
        }
    }
    
    override suspend fun collect(collector: StreamCollector<T>) {
        try {
            activeCollector = collector
            block(wrappedCollector)
        } catch (e: Exception) {
            // 对于协程取消异常，这是正常流程，应当向上抛出以停止流
            if (e is kotlinx.coroutines.CancellationException) {
                StreamLogger.d("stream", "构建器Stream收集被取消")
                throw e
            }
            StreamLogger.e("stream", "构建器Stream收集出错", e)
            // 其他异常也应该抛出，以便上层可以处理
            throw e
        } finally {
            // 流收集完成时标记为关闭
            markClosed()
            
            // 如果流在关闭时处于锁定状态，解锁以处理缓冲的数据
            if (isLocked) {
                StreamLogger.i("stream", "流关闭时处于锁定状态，尝试解锁处理缓冲数据")
                try {
                    unlock()
                } catch (e: Exception) {
                    StreamLogger.w("stream", "流关闭时解锁失败: ${e.message}")
                }
            }
        }
    }
    
    override suspend fun emitBufferedItem(item: T) {
        // 即使流已关闭，也尝试发送缓冲的数据
        activeCollector?.emit(item)
    }
}

/**
 * 创建固定间隔发射整数的Stream
 */
fun intervalStream(period: Duration, initialDelay: Duration = Duration.ZERO): Stream<Long> = stream {
    var count = 0L
    StreamLogger.d("intervalStream", "创建间隔Stream, 周期: $period, 初始延迟: $initialDelay")
    delay(initialDelay)
    while (true) {
        StreamLogger.v("intervalStream", "发射计数: $count")
        emit(count++)
        delay(period)
    }
}

/**
 * 创建固定次数的Stream
 */
fun rangeStream(start: Int, count: Int): Stream<Int> = stream {
    StreamLogger.d("rangeStream", "创建范围Stream, 起始: $start, 数量: $count")
    for (i in start until start + count) {
        StreamLogger.v("rangeStream", "发射值: $i")
        emit(i)
    }
    StreamLogger.d("rangeStream", "范围Stream完成")
}

/**
 * 从异常创建Stream
 */
fun <T> streamError(exception: Throwable): Stream<T> = stream {
    StreamLogger.e("streamError", "创建错误Stream, 异常: ${exception.message}", exception)
    throw exception
} 