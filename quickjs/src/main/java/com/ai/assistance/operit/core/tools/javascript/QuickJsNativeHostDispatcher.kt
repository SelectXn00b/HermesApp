package com.ai.assistance.operit.core.tools.javascript

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max
import org.json.JSONArray

class QuickJsNativeHostDispatcher(
    private val runtimeProvider: () -> QuickJsNativeRuntime,
    private val forwardCall: (String, String?) -> String?
) : QuickJsNativeRuntime.HostBridge, Closeable {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "QuickJsNativeTimer").apply { isDaemon = true }
    }
    private val timerTasks = ConcurrentHashMap<Int, ScheduledFuture<*>>()

    override fun onCall(method: String, argsJson: String?): String? {
        return when {
            method.startsWith("console.") -> null
            method == "scheduleTimer" -> {
                schedule(argsJson)
                null
            }
            method == "cancelTimer" -> {
                cancel(argsJson)
                null
            }
            else -> forwardCall(method, argsJson)
        }
    }

    override fun close() {
        timerTasks.values.forEach { it.cancel(false) }
        timerTasks.clear()
        scheduler.shutdownNow()
    }

    private fun schedule(argsJson: String?) {
        val args = parseArgs(argsJson)
        val timerId = args.getOrNull(0)?.toIntOrNull() ?: return
        val delayMs = max(0L, args.getOrNull(1)?.toLongOrNull() ?: 0L)
        val repeat = args.getOrNull(2)?.let(::parseBoolean) ?: false

        timerTasks.remove(timerId)?.cancel(false)
        val task =
            if (repeat) {
                val safePeriod = max(1L, delayMs)
                scheduler.scheduleAtFixedRate(
                    { runtimeProvider().dispatchTimer(timerId) },
                    safePeriod,
                    safePeriod,
                    TimeUnit.MILLISECONDS
                )
            } else {
                scheduler.schedule(
                    {
                        timerTasks.remove(timerId)
                        runtimeProvider().dispatchTimer(timerId)
                    },
                    delayMs,
                    TimeUnit.MILLISECONDS
                )
            }
        timerTasks[timerId] = task
    }

    private fun cancel(argsJson: String?) {
        val timerId = parseArgs(argsJson).firstOrNull()?.toIntOrNull() ?: return
        timerTasks.remove(timerId)?.cancel(false)
    }

    private fun parseArgs(argsJson: String?): List<String> {
        if (argsJson.isNullOrBlank()) {
            return emptyList()
        }
        val array = JSONArray(argsJson)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.optString(index))
            }
        }
    }

    private fun parseBoolean(value: String): Boolean {
        return value.toBooleanStrictOrNull() ?: (value.toIntOrNull() ?: 0) != 0
    }
}
