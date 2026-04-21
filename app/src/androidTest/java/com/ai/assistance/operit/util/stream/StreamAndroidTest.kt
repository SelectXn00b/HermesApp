package com.ai.assistance.operit.util.stream

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Android测试类，用于测试Stream的基本操作符和功能 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class StreamAndroidTest {

    // 获取应用上下文
    private val appContext by lazy { 
        InstrumentationRegistry.getInstrumentation().targetContext
    }
    
    @Before
    fun setUp() {
        // 测试开始前的准备工作
    }

    @Test
    fun testStreamCreation() = runBlocking {
        // 测试单个值
        val collected = mutableListOf<Int>()
        streamOf(1).collect { collected.add(it) }
        assertEquals(listOf(1), collected)

        // 测试多个值
        collected.clear()
        streamOf(1, 2, 3).collect { collected.add(it) }
        assertEquals(listOf(1, 2, 3), collected)

        // 测试从集合创建
        collected.clear()
        listOf(1, 2, 3).asStream().collect { collected.add(it) }
        assertEquals(listOf(1, 2, 3), collected)
    }

    @Test
    fun testStreamMap() = runBlocking {
        val collected = mutableListOf<Int>()
        streamOf(1, 2, 3).map { it * 2 }.collect { collected.add(it) }
        assertEquals(listOf(2, 4, 6), collected)
    }

    @Test
    fun testStreamFilter() = runBlocking {
        val collected = mutableListOf<Int>()
        streamOf(1, 2, 3, 4, 5).filter { it % 2 == 0 }.collect { collected.add(it) }
        assertEquals(listOf(2, 4), collected)
    }

    @Test
    fun testStreamTakeAndDrop() = runBlocking {
        // 测试take
        val collected1 = mutableListOf<Int>()
        streamOf(1, 2, 3, 4, 5).take(3).collect { collected1.add(it) }
        assertEquals(listOf(1, 2, 3), collected1)

        // 测试drop
        val collected2 = mutableListOf<Int>()
        streamOf(1, 2, 3, 4, 5).drop(2).collect { collected2.add(it) }
        assertEquals(listOf(3, 4, 5), collected2)
    }

    @Test
    fun testStreamFlatMap() = runBlocking {
        val collected = mutableListOf<String>()
        streamOf(1, 2, 3).flatMap { streamOf("a$it", "b$it") }.collect { collected.add(it) }
        assertEquals(listOf("a1", "b1", "a2", "b2", "a3", "b3"), collected)
    }

    @Test
    fun testStreamOnEach() = runBlocking {
        val sideEffects = mutableListOf<Int>()
        val collected = mutableListOf<Int>()
        streamOf(1, 2, 3).onEach { sideEffects.add(it * 10) }.collect { collected.add(it) }
        assertEquals(listOf(10, 20, 30), sideEffects)
        assertEquals(listOf(1, 2, 3), collected)
    }

    @Test
    fun testStreamConcatenation() = runBlocking {
        val stream1 = streamOf(1, 2, 3)
        val stream2 = streamOf(4, 5, 6)
        val collected = mutableListOf<Int>()

        stream1.concatWith(stream2).collect { collected.add(it) }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), collected)
    }

    @Test
    fun testStreamErrorHandling() = runBlocking {
        val collected = mutableListOf<String>()
        val errors = mutableListOf<String>()

        stream<String> {
            emit("开始")
            throw RuntimeException("测试错误")
            emit("不应该到达这里")
        }
        .catch { errors.add(it.message ?: "未知错误") }
        .collect { collected.add(it) }

        assertEquals(listOf("开始"), collected)
        assertEquals(listOf("测试错误"), errors)
    }

    @Test
    fun testStreamDistinct() = runBlocking {
        val collected = mutableListOf<Int>()
        streamOf(1, 2, 2, 3, 3, 3, 4).distinctUntilChanged().collect { collected.add(it) }
        assertEquals(listOf(1, 2, 3, 4), collected)
    }

    @Test
    fun testStreamFlowInteroperation() = runBlocking {
        // Flow转Stream
        val myFlow = flow<Int> {
            emit(1)
            emit(2)
            emit(3)
        }

        val collected1 = mutableListOf<Int>()
        myFlow.asStream().collect { collected1.add(it) }
        assertEquals(listOf(1, 2, 3), collected1)

        // Stream转Flow
        val collected2 = mutableListOf<Int>()
        streamOf(4, 5, 6).asFlow().collect { collected2.add(it) }
        assertEquals(listOf(4, 5, 6), collected2)
    }

    @Test
    fun testStreamFinally() = runBlocking {
        var finallyExecuted = false
        streamOf(1, 2, 3).finally { finallyExecuted = true }.collect { /* 忽略 */ }
        assertTrue("finally块应该执行", finallyExecuted)

        // 测试异常情况下finally也执行
        finallyExecuted = false
        try {
            stream<Int> {
                emit(1)
                throw RuntimeException("测试错误")
            }
            .finally { finallyExecuted = true }
            .collect { /* 忽略 */ }
        } catch (e: RuntimeException) {
            // 忽略异常
        }

        assertTrue("异常情况下finally块也应该执行", finallyExecuted)
    }

    @Test
    fun testStreamThrottle() = runBlocking {
        val latch = CountDownLatch(1)
        val collected = mutableListOf<Int>()
        
        // 使用IO调度器代替Main调度器，避免在测试时阻塞主线程
        val scope = CoroutineScope(Dispatchers.IO)

        val job = scope.launch {
            try {
                // 使用withTimeout限制测试时间
                withTimeout(3000) {
                    streamOf(1, 2, 3, 4, 5).throttleFirst(100.milliseconds).collect {
                        collected.add(it)
                        delay(30) // 模拟处理时间
                    }
                }
            } finally {
                latch.countDown()
            }
        }

        // 等待收集完成或超时
        assertTrue("收集操作应该在5秒内完成", latch.await(5, TimeUnit.SECONDS))
        
        // 尝试取消任务
        runBlocking {
            job.cancelAndJoin()
        }
        
        // 由于throttle设置为100ms，收集时每项间延迟30ms，应该能收集到多个值
        assertTrue("应该至少收集到一个值", collected.size >= 1)
    }
}
