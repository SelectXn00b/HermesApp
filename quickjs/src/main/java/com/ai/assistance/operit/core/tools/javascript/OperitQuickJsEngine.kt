package com.ai.assistance.operit.core.tools.javascript

import java.io.Closeable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class OperitQuickJsEngine : Closeable {

    private val runtimeRef = AtomicReference<QuickJsNativeRuntime?>()
    private val nativeInterfaceRef = AtomicReference<Any?>()
    private val methodCache = ConcurrentHashMap<String, Method>()
    private val hostDispatcher = QuickJsNativeHostDispatcher(
        runtimeProvider = { runtimeRef.get() ?: error("QuickJS runtime is not ready") },
        forwardCall = ::dispatchNativeCall
    )
    private val runtime = QuickJsNativeRuntime.create(hostDispatcher).also { quickJs ->
        runtimeRef.set(quickJs)
        quickJs.installCompatLayerOrThrow()
    }

    fun bindNativeInterface(instance: Any) {
        nativeInterfaceRef.set(instance)
        methodCache.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> evaluate(script: String, fileName: String = "<eval>"): T? {
        val result = runtime.eval(script, fileName)
        runtime.executePendingJobs()
        if (!result.success) {
            error(result.describeFailure("QuickJS evaluation failed"))
        }
        return decodeJsonValue(result.valueJson) as T?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> callFunction(
        functionName: String,
        argsJson: String,
        callSite: String = "<call:$functionName>"
    ): T? {
        val result = runtime.callFunction(functionName, argsJson, callSite)
        runtime.executePendingJobs()
        if (!result.success) {
            error(result.describeFailure("QuickJS function call failed"))
        }
        return decodeJsonValue(result.valueJson) as T?
    }

    fun interrupt() {
        runtime.interrupt()
    }

    override fun close() {
        runCatching { runtime.clearAllTimers() }
        hostDispatcher.close()
        runtime.close()
        runtimeRef.set(null)
        nativeInterfaceRef.set(null)
        methodCache.clear()
    }

    private fun dispatchNativeCall(methodName: String, argsJson: String?): String? {
        val target = nativeInterfaceRef.get() ?: error("NativeInterface is not bound")
        val args = decodeArgs(argsJson)
        val method = resolveMethod(target, methodName, args.size)
        val convertedArgs = method.parameterTypes.mapIndexed { index, type ->
            convertArg(args[index], type)
        }.toTypedArray()
        return method.invoke(target, *convertedArgs)?.toString()
    }

    private fun resolveMethod(target: Any, methodName: String, argCount: Int): Method {
        val cacheKey = "${target.javaClass.name}#$methodName/$argCount"
        return methodCache.getOrPut(cacheKey) {
            target.javaClass.methods.firstOrNull { method ->
                method.name == methodName && method.parameterTypes.size == argCount
            } ?: error("NativeInterface method not found: $methodName/$argCount")
        }
    }

    private fun decodeArgs(argsJson: String?): List<Any?> {
        if (argsJson.isNullOrBlank()) {
            return emptyList()
        }
        val parsed = JSONTokener(argsJson).nextValue()
        if (parsed !is JSONArray) {
            return emptyList()
        }
        return List(parsed.length()) { index -> normalizeJsonValue(parsed.opt(index)) }
    }

    private fun decodeJsonValue(valueJson: String?): Any? {
        if (valueJson.isNullOrBlank()) {
            return null
        }
        return normalizeJsonValue(JSONTokener(valueJson).nextValue())
    }

    private fun normalizeJsonValue(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONArray -> List(value.length()) { index -> normalizeJsonValue(value.opt(index)) }
            is JSONObject -> value.toString()
            else -> value
        }
    }

    private fun convertArg(value: Any?, parameterType: Class<*>): Any? {
        return when (parameterType) {
            java.lang.String::class.java -> value?.toString() ?: ""
            java.lang.Integer.TYPE,
            java.lang.Integer::class.java -> (value as? Number)?.toInt()
                ?: value?.toString()?.toIntOrNull()
                ?: 0
            java.lang.Long.TYPE,
            java.lang.Long::class.java -> (value as? Number)?.toLong()
                ?: value?.toString()?.toLongOrNull()
                ?: 0L
            java.lang.Boolean.TYPE,
            java.lang.Boolean::class.java -> when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                else -> value?.toString()?.toBooleanStrictOrNull() ?: false
            }
            java.lang.Double.TYPE,
            java.lang.Double::class.java -> (value as? Number)?.toDouble()
                ?: value?.toString()?.toDoubleOrNull()
                ?: 0.0
            java.lang.Float.TYPE,
            java.lang.Float::class.java -> (value as? Number)?.toFloat()
                ?: value?.toString()?.toFloatOrNull()
                ?: 0f
            else -> value?.toString()
        }
    }
}
