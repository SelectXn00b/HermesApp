package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.TOOLPKG_EVENT_MESSAGE_PROCESSING
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.OperitPaths
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * JavaScript 引擎 - 通过 QuickJS 执行 JavaScript 脚本并提供与 Android 原生代码的交互机制
 */
class JsEngine(private val context: Context) {
    companion object {
        private const val TAG = "JsEngine"
        private const val TOOLPKG_TAG = "ToolPkg"
        private const val BINARY_DATA_THRESHOLD = 32 * 1024
        private const val BINARY_HANDLE_PREFIX = "@binary_handle:"
        private const val DIRECT_SCRIPT_EXECUTION_FUNCTION = "__operit_run_inline_code__"
        private const val DIRECT_SCRIPT_EXECUTION_SOURCE = "function(params){ return undefined; }"
    }

    private val bitmapRegistry = ConcurrentHashMap<String, Bitmap>()
    private val binaryDataRegistry = ConcurrentHashMap<String, ByteArray>()
    private val javaObjectRegistry = ConcurrentHashMap<String, Any>()
    private val externalJavaCodeLoader = JsExternalJavaCodeLoader(context)

    private val toolHandler = AIToolHandler.getInstance(context)
    private val packageManager by lazy { PackageManager.getInstance(context, toolHandler) }
    private val toolCallInterface = JsToolCallInterface()

    @Volatile
    private var quickJsThread: Thread? = null

    private val quickJsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "OperitQuickJsEngine").apply {
            isDaemon = true
            quickJsThread = this
        }
    }
    private val quickJsDispatcher = quickJsExecutor.asCoroutineDispatcher()
    private val engineScope = CoroutineScope(SupervisorJob() + quickJsDispatcher)
    private val quickJsInitLock = Any()

    @Volatile
    private var quickJs: OperitQuickJsEngine? = null

    private data class ExecutionSession(
        val callId: String,
        val future: CompletableFuture<Any?>,
        val intermediateResultCallback: ((Any?) -> Unit)?,
        val envOverrides: Map<String, String>,
        val packageChatId: String?,
        val toolPkgLogSnapshot: JsToolPkgExecutionContext.LogSnapshot,
        val executionListener: JsExecutionListener?
    )

    private val activeExecutionSessions = ConcurrentHashMap<String, ExecutionSession>()
    private var jsEnvironmentInitialized = false

    private val toolPkgExecutionContext = JsToolPkgExecutionContext()
    private val toolPkgRegistrationSession = JsToolPkgRegistrationSession()

    fun <T> withTemporaryToolPkgTextResourceResolver(
        resolver: (String, String) -> String?,
        block: () -> T
    ): T {
        return toolPkgExecutionContext.withTemporaryTextResourceResolver(resolver, block)
    }

    private fun resolveTemporaryToolPkgTextResource(
        packageNameOrSubpackageId: String,
        resourcePath: String
    ): String? {
        return toolPkgExecutionContext.resolveTemporaryTextResource(
            packageNameOrSubpackageId = packageNameOrSubpackageId,
            resourcePath = resourcePath,
            onResolverFailure = { e ->
                AppLogger.e(
                    TAG,
                    "Temporary toolpkg text resource resolver failed: package/subpackage=$packageNameOrSubpackageId, path=$resourcePath",
                    e
                )
            }
        )
    }

    private fun hasTemporaryToolPkgTextResourceResolver(): Boolean {
        return toolPkgExecutionContext.hasTemporaryTextResourceResolver()
    }

    private fun getJavaBridgeBaseClassLoader(): ClassLoader {
        return context.classLoader
            ?: this::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader()
    }

    private fun getJavaBridgeClassLoader(): ClassLoader {
        return externalJavaCodeLoader.getEffectiveClassLoader(getJavaBridgeBaseClassLoader())
    }

    private fun <T> runOnQuickJsThreadBlocking(block: () -> T): T {
        return if (Thread.currentThread() === quickJsThread) {
            block()
        } else {
            runBlocking(quickJsDispatcher) {
                block()
            }
        }
    }

    private fun ensureQuickJs() {
        if (quickJs != null) {
            return
        }
        synchronized(quickJsInitLock) {
            if (quickJs != null) {
                return
            }
            try {
                val engine = runOnQuickJsThreadBlocking {
                    OperitQuickJsEngine().also {
                        it.bindNativeInterface(toolCallInterface)
                    }
                }
                quickJs = engine
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error initializing QuickJS: ${e.message}", e)
                throw e
            }
        }
    }

    private fun disposeQuickJsForReinit(reason: String) {
        synchronized(quickJsInitLock) {
            val engine = quickJs
            if (engine == null) {
                jsEnvironmentInitialized = false
                return
            }
            try {
                runOnQuickJsThreadBlocking {
                    engine.close()
                }
            } catch (closeError: Exception) {
                AppLogger.e(TAG, "Error closing QuickJS for reinit: $reason, ${closeError.message}", closeError)
            } finally {
                quickJs = null
                jsEnvironmentInitialized = false
            }
        }
    }

    private fun <T> evaluateQuickJsBlocking(script: String, fileName: String = "<eval>"): T? {
        ensureQuickJs()
        val engine = quickJs ?: return null
        return if (Thread.currentThread() === quickJsThread) {
            runBlocking {
                engine.evaluate<T>(script, fileName)
            }
        } else {
            runBlocking(quickJsDispatcher) {
                engine.evaluate<T>(script, fileName)
            }
        }
    }

    private fun launchQuickJsEvaluation(
        script: String,
        fileName: String = "<eval>",
        onError: ((Exception) -> Unit)? = null
    ) {
        val engine = quickJs ?: return
        engineScope.launch {
            try {
                engine.evaluate<Any?>(script, fileName)
            } catch (e: Exception) {
                if (onError != null) {
                    onError(e)
                } else {
                    AppLogger.e(TAG, "QuickJS evaluation failed: ${e.message}", e)
                }
            }
        }
    }

    private fun launchQuickJsFunctionCall(
        functionName: String,
        argsJson: String,
        callSite: String = "<call:$functionName>",
        onError: ((Exception) -> Unit)? = null
    ) {
        val engine = quickJs ?: return
        engineScope.launch {
            try {
                engine.callFunction<Any?>(functionName, argsJson, callSite)
            } catch (e: Exception) {
                if (onError != null) {
                    onError(e)
                } else {
                    AppLogger.e(TAG, "QuickJS function call failed: ${e.message}", e)
                }
            }
        }
    }


    private fun nextExecutionCallId(): String {
        return "operit_call_${UUID.randomUUID().toString().replace("-", "")}" 
    }

    private fun createExecutionSession(
        callId: String,
        script: String,
        functionName: String,
        params: Map<String, Any?>,
        envOverrides: Map<String, String>,
        onIntermediateResult: ((Any?) -> Unit)?,
        executionListener: JsExecutionListener?
    ): ExecutionSession {
        return ExecutionSession(
            callId = callId,
            future = CompletableFuture(),
            intermediateResultCallback = onIntermediateResult,
            envOverrides = envOverrides,
            packageChatId =
                params["__operit_package_chat_id"]
                    ?.toString()
                    ?.trim()
                    ?.ifBlank { null },
            toolPkgLogSnapshot = toolPkgExecutionContext.capture(script, functionName, params),
            executionListener = executionListener
        )
    }

    private fun resolveExecutionSession(callId: String): ExecutionSession? {
        return activeExecutionSessions[callId.trim()]
    }

    private fun removeExecutionSession(callId: String): ExecutionSession? {
        return activeExecutionSessions.remove(callId.trim())
    }

    private fun cancelAllExecutionSessions(reason: String) {
        val sessions = activeExecutionSessions.values.toList()
        activeExecutionSessions.clear()
        sessions.forEach { session ->
            if (!session.future.isDone) {
                session.future.complete("Error: $reason")
            }
            cancelExecutionSessionInJs(
                callId = session.callId,
                reason = reason
            )
        }
    }

    private fun cancelExecutionSessionInJs(callId: String, reason: String) {
        ensureQuickJs()
        val safeCallId = JSONObject.quote(callId)
        val safeReason = JSONObject.quote(reason)
        launchQuickJsEvaluation(
            script =
                """
                    (function() {
                        var root = typeof globalThis !== 'undefined'
                            ? globalThis
                            : (typeof window !== 'undefined' ? window : this);
                        if (typeof root.__operitCancelCallSession === 'function') {
                            root.__operitCancelCallSession($safeCallId, $safeReason);
                        }
                    })();
                """.trimIndent(),
            fileName = "quickjs/runtime/cancel-call-session.js",
            onError = { e ->
                AppLogger.e(TAG, "Error canceling JS execution session $callId: ${e.message}", e)
            }
        )
    }

    private fun withToolPkgPluginTag(message: String): String {
        return toolPkgExecutionContext.withPluginTag(null, message)
    }

    private fun withToolPkgPluginTag(session: ExecutionSession?, message: String): String {
        return toolPkgExecutionContext.withPluginTag(session?.toolPkgLogSnapshot, message)
    }

    private fun withToolPkgCodeContext(session: ExecutionSession?, message: String): String {
        return toolPkgExecutionContext.withCodeContext(session?.toolPkgLogSnapshot, message)
    }

    private fun runtimeBootstrapModules(): List<JsBootstrapModule> {
        return buildRuntimeBootstrapModules(
            context = context,
            operitDownloadDir = OperitPaths.operitRootPathSdcard(),
            operitCleanOnExitDir = OperitPaths.cleanOnExitPathSdcard()
        )
    }

    private fun evaluateBootstrapModule(module: JsBootstrapModule) {
        if (module.source.isBlank()) {
            return
        }
        try {
            evaluateQuickJsBlocking<Any?>(module.source, module.fileName)
            exposeBootstrapGlobals(module)
        } catch (e: Exception) {
            val globalsSummary = module.globals.joinToString(prefix = "[", postfix = "]")
            AppLogger.e(
                TAG,
                "Bootstrap module failed: file=${module.fileName}, scriptLength=${module.source.length}, globals=$globalsSummary, preview=${summarizeJavaScriptForLog(module.source)}",
                e
            )
            throw IllegalStateException("Bootstrap failed for ${module.fileName}: ${e.message}", e)
        }
    }

    private fun summarizeJavaScriptForLog(source: String, maxLength: Int = 320): String {
        if (source.isBlank()) {
            return ""
        }
        val normalized =
            buildString(source.length) {
                source.forEach { ch ->
                    append(
                        when (ch) {
                            '\n', '\r', '\t' -> ' '
                            else -> ch
                        }
                    )
                }
            }.trim()
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength - 3) + "..."
        }
    }

    private fun exposeBootstrapGlobals(module: JsBootstrapModule) {
        if (module.globals.isEmpty()) {
            return
        }
        evaluateQuickJsBlocking<Any?>(
            buildBootstrapGlobalExposureScript(module.globals),
            "${module.fileName}#globals"
        )
    }

    private fun buildBootstrapGlobalExposureScript(globalNames: List<String>): String {
        val exposeStatements =
            globalNames.joinToString("\n") { name ->
                val quotedName = JSONObject.quote(name)
                "expose($quotedName, typeof $name !== 'undefined' ? $name : undefined);"
            }

        return """
            (function() {
                var root = typeof globalThis !== 'undefined'
                    ? globalThis
                    : (typeof window !== 'undefined' ? window : this);
                var expose = typeof root.__operitExpose === 'function'
                    ? root.__operitExpose
                    : function(name, value) {
                        var key = String(name || '').trim();
                        if (!key || value === undefined) {
                            return;
                        }
                        try { root[key] = value; } catch (_error) {}
                        try { window[key] = value; } catch (_error2) {}
                    };
                $exposeStatements
            })();
        """.trimIndent()
    }

    private fun invokeJavaBridgeJsObjectCallbackSync(
        jsObjectId: String,
        methodName: String,
        argsJson: String
    ): String {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return JSONObject()
                .put("success", false)
                .put("error", "java bridge callback cannot synchronously invoke JS on main thread")
                .toString()
        }

        ensureQuickJs()
        if (quickJs == null) {
            return JSONObject()
                .put("success", false)
                .put("error", "quickjs is not initialized")
                .toString()
        }

        val safeArgsJson = argsJson.trim().ifEmpty { "[]" }
        val callbackScript =
            """
            (function() {
                try {
                    var __invoker =
                        (typeof globalThis !== 'undefined' && typeof globalThis.__operitJavaBridgeInvokeJsObject === 'function')
                            ? globalThis.__operitJavaBridgeInvokeJsObject
                            : undefined;
                    if (!__invoker) {
                        return JSON.stringify({
                            success: false,
                            error: 'java bridge js callback runtime unavailable'
                        });
                    }
                    var __result = __invoker(
                        ${JSONObject.quote(jsObjectId)},
                        ${JSONObject.quote(methodName)},
                        $safeArgsJson
                    );
                    return JSON.stringify({
                        success: true,
                        data: __result
                    });
                } catch (e) {
                    return JSON.stringify({
                        success: false,
                        error: (e && e.message) ? e.message : String(e)
                    });
                }
            })();
            """.trimIndent()

        return try {
            val callbackResult =
                evaluateQuickJsBlocking<String>(
                    script = callbackScript,
                    fileName = "quickjs/runtime/java-bridge-callback.js"
                )
            callbackResult ?: JSONObject()
                .put("success", false)
                .put("error", "java bridge callback returned empty result")
                .toString()
        } catch (e: Exception) {
            JSONObject()
                .put("success", false)
                .put("error", "java bridge callback evaluation failed: ${e.message}")
                .toString()
        }
    }

    private fun releaseJavaBridgeJsObjectSync(jsObjectId: String): Boolean {
        val normalizedId = jsObjectId.trim()
        if (normalizedId.isEmpty() || !jsEnvironmentInitialized) {
            return false
        }

        ensureQuickJs()
        if (quickJs == null) {
            return false
        }

        val releaseScript =
            """
            (function() {
                try {
                    var __release =
                        (typeof globalThis !== 'undefined' && typeof globalThis.__operitJavaBridgeReleaseJsObject === 'function')
                            ? globalThis.__operitJavaBridgeReleaseJsObject
                            : undefined;
                    if (!__release) {
                        return false;
                    }
                    return !!__release(${JSONObject.quote(normalizedId)});
                } catch (_error) {
                    return false;
                }
            })();
            """.trimIndent()

        return try {
            when (
                val result =
                    evaluateQuickJsBlocking<Any?>(
                        script = releaseScript,
                        fileName = "quickjs/runtime/java-bridge-release.js"
                    )
            ) {
                is Boolean -> result
                is String -> result.equals("true", ignoreCase = true)
                is Number -> result.toInt() != 0
                else -> false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to auto-release JS interface object $normalizedId: ${e.message}", e)
            false
        }
    }

    private fun splitBridgeResult(raw: String): Pair<String?, Any?> {
        if (raw.isBlank()) {
            return Pair("empty bridge response", null)
        }
        return try {
            val token = JSONTokener(raw).nextValue()
            if (token is JSONObject) {
                val success = token.optBoolean("success", false)
                val data = token.opt("data")
                val error = token.optString("error").ifBlank { null }
                if (success) {
                    Pair(null, data)
                } else {
                    Pair(error ?: "bridge call failed", null)
                }
            } else {
                Pair("invalid bridge response format", null)
            }
        } catch (e: Exception) {
            Pair("failed to parse bridge response: ${e.message}", null)
        }
    }

    /** 初始化 JavaScript 环境，加上 QuickJS 兼容层、核心运行时与工具桥 */
    private fun initJavaScriptEnvironment() {
        synchronized(quickJsInitLock) {
            if (jsEnvironmentInitialized) {
                return
            }

            ensureQuickJs()
            try {
                runtimeBootstrapModules().forEach(::evaluateBootstrapModule)
                jsEnvironmentInitialized = true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to initialize JS environment: ${e.message}", e)
                disposeQuickJsForReinit("bootstrap initialization failure")
            }
            if (!jsEnvironmentInitialized) {
                AppLogger.e(TAG, "QuickJS init script failed to produce runtime bridge")
            }
        }
    }

    /**
     * 执行 JavaScript 脚本并调用其中的特定函数
     * @param script 完整的JavaScript脚本内容
     * @param functionName 要调用的函数名称
     * @param params 要传递给函数的参数
     * @return 函数执行结果
     */
    internal fun executeScriptFunction(
            script: String,
            functionName: String,
            params: Map<String, Any?>,
            envOverrides: Map<String, String> = emptyMap(),
            onIntermediateResult: ((Any?) -> Unit)? = null,
            timeoutSec: Long = JsTimeoutConfig.MAIN_TIMEOUT_SECONDS.toLong(),
            executionListener: JsExecutionListener? = null
    ): Any? {
        val effectiveParams = params.toMutableMap()
        val explicitLanguage = effectiveParams["__operit_package_lang"]?.toString()?.trim().orEmpty()
        if (explicitLanguage.isBlank()) {
            effectiveParams["__operit_package_lang"] =
                LocaleUtils.getCurrentLanguage(context).trim().ifBlank { "en" }
        }

        val timingEvent = effectiveParams["event"]?.toString()?.trim().orEmpty()
        val timingPluginId =
            effectiveParams["pluginId"]?.toString()?.trim().orEmpty()
                .ifBlank {
                    effectiveParams["__operit_plugin_id"]?.toString()?.trim().orEmpty()
                }
                .ifBlank { "none" }
        val shouldLogTiming = timingEvent.equals(TOOLPKG_EVENT_MESSAGE_PROCESSING, ignoreCase = true)
        val totalStartTime = if (shouldLogTiming) messageTimingNow() else 0L

        val initQuickJsStartTime = if (shouldLogTiming) messageTimingNow() else 0L
        ensureQuickJs()
        if (shouldLogTiming) {
            logMessageTiming(
                stage = "toolpkg.jsEngine.initQuickJs",
                startTimeMs = initQuickJsStartTime,
                details = "function=$functionName, plugin=$timingPluginId"
            )
        }

        if (!jsEnvironmentInitialized) {
            val initJavaScriptEnvironmentStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            initJavaScriptEnvironment()
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.jsEngine.initJavaScriptEnvironment",
                    startTimeMs = initJavaScriptEnvironmentStartTime,
                    details = "function=$functionName, plugin=$timingPluginId"
                )
            }
            if (!jsEnvironmentInitialized) {
                val failureReason = "QuickJS runtime initialization failed"
                if (shouldLogTiming) {
                    logMessageTiming(
                        stage = "toolpkg.jsEngine.total",
                        startTimeMs = totalStartTime,
                        details = "function=$functionName, plugin=$timingPluginId, success=false, reason=$failureReason"
                    )
                }
                return "Error: $failureReason"
            }
        }

        val callId = nextExecutionCallId()
        val session =
            createExecutionSession(
                callId = callId,
                script = script,
                functionName = functionName,
                params = effectiveParams,
                envOverrides = envOverrides,
                onIntermediateResult = onIntermediateResult,
                executionListener = executionListener
            )
        activeExecutionSessions[callId] = session

        val buildExecutionScriptStartTime = if (shouldLogTiming) messageTimingNow() else 0L
        val paramsObject = JSONObject(effectiveParams)
        val paramsJson = paramsObject.toString()
        val safeTimeoutSec = if (timeoutSec <= 0L) 1L else timeoutSec
        val preTimeoutMs = JsTimeoutConfig.PRE_TIMEOUT_SECONDS * 1000L
        val executionArgsJson =
            JSONArray()
                .put(callId)
                .put(paramsObject)
                .put(script)
                .put(functionName)
                .put(safeTimeoutSec)
                .put(preTimeoutMs)
                .toString()
        if (shouldLogTiming) {
            logMessageTiming(
                stage = "toolpkg.jsEngine.buildExecutionScript",
                startTimeMs = buildExecutionScriptStartTime,
                details = "function=$functionName, plugin=$timingPluginId, scriptLength=${script.length}, paramsLength=${paramsJson.length}, argsLength=${executionArgsJson.length}, directInvoke=true"
            )
        }

        launchQuickJsFunctionCall(
            functionName = TOOLPKG_EXECUTION_ENTRY_FUNCTION,
            argsJson = executionArgsJson,
            callSite = "quickjs/runtime/execute-script.call",
            onError = { e ->
                AppLogger.e(
                    TAG,
                    "Failed to dispatch script execution: callId=$callId, function=$functionName, reason=${e.message}",
                    e
                )
                removeExecutionSession(callId)
                session.executionListener?.onFailed(callId, "Error: ${e.message ?: "dispatch failed"}")
                if (!session.future.isDone) {
                    session.future.complete("Error: ${e.message ?: "dispatch failed"}")
                }
            }
        )

        val preTimeoutTimer = java.util.Timer()
        val waitResultStartTime = if (shouldLogTiming) messageTimingNow() else 0L
        return try {
            preTimeoutTimer.schedule(
                object : java.util.TimerTask() {
                    override fun run() {
                        if (!session.future.isDone) {
                            AppLogger.d(
                                TAG,
                                "Pre-timeout warning triggered: callId=$callId, function=$functionName"
                            )
                        }
                    }
                },
                JsTimeoutConfig.PRE_TIMEOUT_SECONDS * 1000
            )

            val result = session.future.get(safeTimeoutSec, TimeUnit.SECONDS)
            removeExecutionSession(callId)
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.jsEngine.waitResult",
                    startTimeMs = waitResultStartTime,
                    details = "function=$functionName, plugin=$timingPluginId, callId=$callId, success=true, resultType=${result?.javaClass?.simpleName ?: "null"}"
                )
                logMessageTiming(
                    stage = "toolpkg.jsEngine.total",
                    startTimeMs = totalStartTime,
                    details = "function=$functionName, plugin=$timingPluginId, callId=$callId, success=true"
                )
            }
            result
        } catch (e: Exception) {
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val failureReason =
                when (e) {
                    is java.util.concurrent.TimeoutException ->
                        "Script execution timed out after ${if (timeoutSec <= 0L) 1L else timeoutSec} seconds"
                    else -> e.message ?: e.javaClass.simpleName
                }
            AppLogger.e(
                TAG,
                "Script execution timed out or failed: callId=$callId, function=$functionName, reason=$failureReason",
                e
            )
            removeExecutionSession(callId)
            cancelExecutionSessionInJs(callId, failureReason)
            session.executionListener?.onFailed(callId, "Error: $failureReason")
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.jsEngine.waitResult",
                    startTimeMs = waitResultStartTime,
                    details = "function=$functionName, plugin=$timingPluginId, callId=$callId, success=false, reason=$failureReason"
                )
                logMessageTiming(
                    stage = "toolpkg.jsEngine.total",
                    startTimeMs = totalStartTime,
                    details = "function=$functionName, plugin=$timingPluginId, callId=$callId, success=false, reason=$failureReason"
                )
            }
            "Error: $failureReason"
        } finally {
            preTimeoutTimer.cancel()
        }
    }

    internal fun executeScriptCode(
            script: String,
            params: Map<String, Any?> = emptyMap(),
            envOverrides: Map<String, String> = emptyMap(),
            onIntermediateResult: ((Any?) -> Unit)? = null,
            timeoutSec: Long = JsTimeoutConfig.MAIN_TIMEOUT_SECONDS.toLong(),
            executionListener: JsExecutionListener? = null
    ): Any? {
        val directParams = params.toMutableMap()
        directParams["__operit_inline_function_name"] = DIRECT_SCRIPT_EXECUTION_FUNCTION
        directParams["__operit_inline_function_source"] = DIRECT_SCRIPT_EXECUTION_SOURCE
        return executeScriptFunction(
            script = script,
            functionName = DIRECT_SCRIPT_EXECUTION_FUNCTION,
            params = directParams,
            envOverrides = envOverrides,
            onIntermediateResult = onIntermediateResult,
            timeoutSec = timeoutSec,
            executionListener = executionListener
        )
    }

    fun executeToolPkgMainRegistrationFunction(
        script: String,
        functionName: String,
        params: Map<String, Any?> = emptyMap()
    ): ToolPkgMainRegistrationCapture {
        synchronized(toolPkgRegistrationSession) {
            toolPkgRegistrationSession.begin()
            try {
                val executionResult =
                    executeScriptFunction(
                        script = script,
                        functionName = functionName,
                        params = params,
                        timeoutSec = 12L
                    )
                return toolPkgRegistrationSession.finish(executionResult)
            } finally {
                toolPkgRegistrationSession.end()
            }
        }
    }

    fun executeComposeDslScript(
            script: String,
            runtimeOptions: Map<String, Any?> = emptyMap(),
            envOverrides: Map<String, String> = emptyMap()
    ): Any? {
        return executeScriptFunction(
                script = buildComposeDslRuntimeWrappedScript(script),
                functionName = "__operit_render_compose_dsl",
                params = runtimeOptions,
                envOverrides = envOverrides
        )
    }

    fun executeComposeDslAction(
            actionId: String,
            payload: Any? = null,
            runtimeOptions: Map<String, Any?> = emptyMap(),
            envOverrides: Map<String, String> = emptyMap(),
            onIntermediateResult: ((Any?) -> Unit)? = null
    ): Any? {
        val normalizedActionId = actionId.trim()
        if (normalizedActionId.isBlank()) {
            return "Error: compose action id is required"
        }
        val params = runtimeOptions.toMutableMap()
        params["__action_id"] = normalizedActionId
        if (payload != null) {
            params["__action_payload"] = payload
        }
        return executeScriptFunction(
                script = "",
                functionName = "__operit_dispatch_compose_dsl_action",
                params = params,
                envOverrides = envOverrides,
                onIntermediateResult = onIntermediateResult
        )
    }

    fun dispatchComposeDslActionAsync(
            actionId: String,
            payload: Any? = null,
            runtimeOptions: Map<String, Any?> = emptyMap(),
            envOverrides: Map<String, String> = emptyMap(),
            onIntermediateResult: ((Any?) -> Unit)? = null,
            onComplete: (() -> Unit)? = null,
            onError: ((String) -> Unit)? = null
    ): Boolean {
        val normalizedActionId = actionId.trim()
        if (normalizedActionId.isBlank()) {
            onError?.invoke("compose action id is required")
            onComplete?.invoke()
            return false
        }

        Thread {
            val result =
                try {
                    executeComposeDslAction(
                        actionId = normalizedActionId,
                        payload = payload,
                        runtimeOptions = runtimeOptions,
                        envOverrides = envOverrides,
                        onIntermediateResult = onIntermediateResult
                    )
                } catch (e: Exception) {
                    val errorText = e.message?.trim().orEmpty().ifBlank { "compose action dispatch failed" }
                    AppLogger.e(TAG, "dispatch compose action failed: actionId=$normalizedActionId, error=$errorText", e)
                    ContextCompat.getMainExecutor(context).execute {
                        onError?.invoke(errorText)
                        onComplete?.invoke()
                    }
                    return@Thread
                }

            val errorText =
                result?.toString()
                    ?.takeIf { it.startsWith("Error:", ignoreCase = true) }
                    ?.removePrefix("Error:")
                    ?.trim()
                    ?.ifBlank { "compose action dispatch failed" }
            ContextCompat.getMainExecutor(context).execute {
                if (errorText != null) {
                    AppLogger.e(
                        TAG,
                        "dispatch compose action failed: actionId=$normalizedActionId, error=$errorText"
                    )
                    onError?.invoke(errorText)
                } else if (result != null) {
                    onIntermediateResult?.invoke(result)
                }
                onComplete?.invoke()
            }
        }.start()

        return true
    }

    fun cancelCurrentExecution(reason: String = "Execution canceled: requested by caller") {
        AppLogger.d(TAG, "Cancel current JS execution: $reason")
        resetState(cancellationMessage = reason)
    }

    fun cancelExecutionsForChat(
        chatId: String,
        reason: String = "Execution canceled: requested by caller"
    ): Boolean {
        val normalizedChatId = chatId.trim()
        if (normalizedChatId.isEmpty()) {
            return false
        }

        val matchingSessions =
            activeExecutionSessions.values
                .filter { session -> session.packageChatId == normalizedChatId }
        if (matchingSessions.isEmpty()) {
            return false
        }

        matchingSessions.forEach { session ->
            removeExecutionSession(session.callId)
            if (!session.future.isDone) {
                session.future.complete("Error: $reason")
            }
            cancelExecutionSessionInJs(
                callId = session.callId,
                reason = reason
            )
        }
        return true
    }

    /** 重置引擎状态，避免多次调用时的状态干扰 */
    private fun resetState(cancellationMessage: String = "Execution canceled: new execution started") {
        cancelAllExecutionSessions(cancellationMessage)

        bitmapRegistry.values.forEach { it.recycle() }
        bitmapRegistry.clear()

        binaryDataRegistry.clear()
        javaObjectRegistry.clear()
        if (quickJs != null) {
            launchQuickJsEvaluation(
                script =
                    """
                        (function() {
                            var root = typeof globalThis !== 'undefined'
                                ? globalThis
                                : (typeof window !== 'undefined' ? window : this);
                            if (typeof root.__operitClearAllTimers === 'function') {
                                root.__operitClearAllTimers();
                            }
                        })();
                    """.trimIndent(),
                fileName = "quickjs/runtime/reset-state.js",
                onError = { e ->
                    AppLogger.e(TAG, "Error in QuickJS cleanup: ${e.message}", e)
                }
            )
        }
    }

    /** JavaScript 接口，提供 Native 调用方法 */
    @Keep
    inner class JsToolCallInterface {

        private val jsBridgeCallbackInvoker: (String, String, String) -> String =
            { jsObjectId, methodName, callbackArgsJson ->
                invokeJavaBridgeJsObjectCallbackSync(
                    jsObjectId = jsObjectId,
                    methodName = methodName,
                    argsJson = callbackArgsJson
                )
            }

        init {
            JsJavaBridgeDelegates.registerJsInterfaceReleaseInvoker(
                callbackInvoker = jsBridgeCallbackInvoker,
                releaseInvoker = ::releaseJavaBridgeJsObjectSync
            )
        }

        fun detachJavaBridgeLifecycle() {
            JsJavaBridgeDelegates.unregisterJsInterfaceReleaseInvoker(jsBridgeCallbackInvoker)
        }

        @JavascriptInterface
        fun decompress(data: String, algorithm: String): String {
            return JsNativeInterfaceDelegates.decompress(
                data = data,
                algorithm = algorithm,
                binaryDataRegistry = binaryDataRegistry,
                binaryHandlePrefix = BINARY_HANDLE_PREFIX
            )
        }

        @JavascriptInterface
        fun getEnvForCall(callId: String, key: String): String? {
            val session = resolveExecutionSession(callId)
            return JsNativeInterfaceDelegates.getEnv(
                context = context,
                key = key,
                envOverrides = session?.envOverrides ?: emptyMap()
            )
        }

        @JavascriptInterface
        fun setEnv(key: String, value: String?) {
            JsNativeInterfaceDelegates.setEnv(context = context, key = key, value = value)
        }

        @JavascriptInterface
        fun setEnvs(valuesJson: String) {
            JsNativeInterfaceDelegates.setEnvs(context = context, valuesJson = valuesJson)
        }

        @JavascriptInterface
        fun isPackageImported(packageName: String): Boolean {
            return JsNativeInterfaceDelegates.isPackageImported(
                    packageManager = packageManager,
                    packageName = packageName
            )
        }

        @JavascriptInterface
        fun importPackage(packageName: String): String {
            return JsNativeInterfaceDelegates.importPackage(
                    packageManager = packageManager,
                    packageName = packageName
            )
        }

        @JavascriptInterface
        fun removePackage(packageName: String): String {
            return JsNativeInterfaceDelegates.removePackage(
                    packageManager = packageManager,
                    packageName = packageName
            )
        }

        @JavascriptInterface
        fun usePackage(packageName: String): String {
            return JsNativeInterfaceDelegates.usePackage(
                    packageManager = packageManager,
                    packageName = packageName
            )
        }

        @JavascriptInterface
        fun listImportedPackagesJson(): String {
            return JsNativeInterfaceDelegates.listImportedPackagesJson(
                    packageManager = packageManager
            )
        }

        @JavascriptInterface
        fun resolveToolName(
                packageName: String,
                subpackageId: String,
                toolName: String,
                preferImported: String
        ): String {
            return JsNativeInterfaceDelegates.resolveToolName(
                    packageManager = packageManager,
                    packageName = packageName,
                    subpackageId = subpackageId,
                    toolName = toolName,
                    preferImported = preferImported
            )
        }

        @JavascriptInterface
        fun readToolPkgResource(
                packageNameOrSubpackageId: String,
                resourceKey: String,
                outputFileName: String,
                internal: String
        ): String {
            return JsNativeInterfaceDelegates.readToolPkgResource(
                    context = context,
                    packageManager = packageManager,
                    packageNameOrSubpackageId = packageNameOrSubpackageId,
                    resourceKey = resourceKey,
                    outputFileName = outputFileName,
                    internal = internal
            )
        }

        @JavascriptInterface
        fun readToolPkgTextResource(
                packageNameOrSubpackageId: String,
                resourcePath: String
        ): String {
            val temporaryResolverActive = hasTemporaryToolPkgTextResourceResolver()
            resolveTemporaryToolPkgTextResource(
                packageNameOrSubpackageId = packageNameOrSubpackageId,
                resourcePath = resourcePath
            )?.let { resolved -> return resolved }
            if (temporaryResolverActive) {
                // During toolpkg parsing we must not fall back into PackageManager.
                // That fallback can wait on initialization and deadlock JavaBridge thread.
                return ""
            }
            return JsNativeInterfaceDelegates.readToolPkgTextResource(
                    packageManager = packageManager,
                    packageNameOrSubpackageId = packageNameOrSubpackageId,
                    resourcePath = resourcePath
            )
        }

        @JavascriptInterface
        fun measureComposeText(payloadJson: String): String {
            return JsNativeInterfaceDelegates.measureComposeText(
                context = context,
                payloadJson = payloadJson
            )
        }

        @JavascriptInterface
        fun registerToolPkgToolboxUiModule(specJson: String) {
            toolPkgRegistrationSession.appendToolboxUiModule(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgAppLifecycleHook(specJson: String) {
            toolPkgRegistrationSession.appendAppLifecycleHook(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgMessageProcessingPlugin(specJson: String) {
            toolPkgRegistrationSession.appendMessageProcessingPlugin(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgXmlRenderPlugin(specJson: String) {
            toolPkgRegistrationSession.appendXmlRenderPlugin(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgInputMenuTogglePlugin(specJson: String) {
            toolPkgRegistrationSession.appendInputMenuTogglePlugin(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgToolLifecycleHook(specJson: String) {
            toolPkgRegistrationSession.appendToolLifecycleHook(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgPromptInputHook(specJson: String) {
            toolPkgRegistrationSession.appendPromptInputHook(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgPromptHistoryHook(specJson: String) {
            toolPkgRegistrationSession.appendPromptHistoryHook(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgPromptEstimateHistoryHook(specJson: String) {
            toolPkgRegistrationSession.appendPromptEstimateHistoryHook(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgSystemPromptComposeHook(specJson: String) {
            toolPkgRegistrationSession.appendSystemPromptComposeHook(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgToolPromptComposeHook(specJson: String) {
            toolPkgRegistrationSession.appendToolPromptComposeHook(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgPromptFinalizeHook(specJson: String) {
            toolPkgRegistrationSession.appendPromptFinalizeHook(specJson)
        }

        @JavascriptInterface
        fun registerToolPkgPromptEstimateFinalizeHook(specJson: String) {
            toolPkgRegistrationSession.appendPromptEstimateFinalizeHook(specJson)
        }

        private fun bridgeClassLoader(): ClassLoader = getJavaBridgeClassLoader()

        private fun exposeJavaObject(target: Any, failureLabel: String): String {
            return try {
                val handle = UUID.randomUUID().toString()
                javaObjectRegistry[handle] = target
                JSONObject()
                    .put("success", true)
                    .put(
                        "data",
                        JSONObject()
                            .put("__javaHandle", handle)
                            .put("__javaClass", target.javaClass.name)
                    )
                    .toString()
            } catch (e: Exception) {
                AppLogger.e(TAG, "$failureLabel: ${e.message}", e)
                JSONObject()
                    .put("success", false)
                    .put("error", e.message ?: failureLabel.lowercase())
                    .toString()
            }
        }

        private fun launchSuspendJavaBridgeCall(
            callbackId: String,
            block: (normalizedCallbackId: String, resultCallback: (String) -> Unit) -> Unit
        ) {
            val normalizedCallback = callbackId.trim()
            if (normalizedCallback.isEmpty()) {
                return
            }
            Thread {
                block(
                    normalizedCallback,
                    createSuspendJavaBridgeResultCallback(normalizedCallback)
                )
            }.start()
        }

        private fun createSuspendJavaBridgeResultCallback(callbackId: String): (String) -> Unit {
            return { resultJson ->
                val (error, data) = splitBridgeResult(resultJson)
                invokeJavaBridgeJsObjectCallbackSync(
                    jsObjectId = callbackId,
                    methodName = "",
                    argsJson = JSONArray().put(error).put(data).toString()
                )
            }
        }

        @JavascriptInterface
        fun javaClassExists(className: String): Boolean {
            return JsJavaBridgeDelegates.classExists(
                className = className,
                bridgeClassLoader = bridgeClassLoader()
            )
        }

        @JavascriptInterface
        fun javaLoadDex(path: String, optionsJson: String): String {
            return externalJavaCodeLoader.loadDex(
                path = path,
                optionsJson = optionsJson,
                baseClassLoader = getJavaBridgeBaseClassLoader()
            )
        }

        @JavascriptInterface
        fun javaLoadJar(path: String, optionsJson: String): String {
            return externalJavaCodeLoader.loadJar(
                path = path,
                optionsJson = optionsJson,
                baseClassLoader = getJavaBridgeBaseClassLoader()
            )
        }

        @JavascriptInterface
        fun javaListLoadedCodePaths(): String {
            return externalJavaCodeLoader.listLoadedArtifacts()
        }

        @JavascriptInterface
        fun javaGetApplicationContext(): String {
            return exposeJavaObject(
                target = context.applicationContext,
                failureLabel = "Failed to expose application context"
            )
        }

        @JavascriptInterface
        fun javaGetCurrentActivity(): String {
            val activity = ActivityLifecycleManager.getCurrentActivity()
                ?: return JSONObject()
                    .put("success", false)
                    .put("error", "current activity is null")
                    .toString()
            return exposeJavaObject(
                target = activity,
                failureLabel = "Failed to expose current activity"
            )
        }

        @JavascriptInterface
        fun javaNewInstance(className: String, argsJson: String): String {
            return JsJavaBridgeDelegates.newInstance(
                    className = className,
                    argsJson = argsJson,
                    objectRegistry = javaObjectRegistry,
                    jsCallbackInvoker = jsBridgeCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader()
            )
        }

        @JavascriptInterface
        fun javaCallStatic(className: String, methodName: String, argsJson: String): String {
            return JsJavaBridgeDelegates.callStatic(
                    className = className,
                    methodName = methodName,
                    argsJson = argsJson,
                    objectRegistry = javaObjectRegistry,
                    jsCallbackInvoker = jsBridgeCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader()
            )
        }

        @JavascriptInterface
        fun javaCallInstance(instanceHandle: String, methodName: String, argsJson: String): String {
            return JsJavaBridgeDelegates.callInstance(
                    instanceHandle = instanceHandle,
                    methodName = methodName,
                    argsJson = argsJson,
                    objectRegistry = javaObjectRegistry,
                    jsCallbackInvoker = jsBridgeCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader()
            )
        }

        @JavascriptInterface
        fun javaCallStaticSuspend(
                className: String,
                methodName: String,
                argsJson: String,
                callbackId: String
        ) {
            launchSuspendJavaBridgeCall(callbackId) { _normalizedCallback, resultCallback ->
                JsJavaBridgeDelegates.callStaticSuspend(
                    className = className,
                    methodName = methodName,
                    argsJson = argsJson,
                    objectRegistry = javaObjectRegistry,
                    callback = resultCallback,
                    jsCallbackInvoker = jsBridgeCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader()
                )
            }
        }

        @JavascriptInterface
        fun javaCallInstanceSuspend(
                instanceHandle: String,
                methodName: String,
                argsJson: String,
                callbackId: String
        ) {
            launchSuspendJavaBridgeCall(callbackId) { _normalizedCallback, resultCallback ->
                JsJavaBridgeDelegates.callInstanceSuspend(
                    instanceHandle = instanceHandle,
                    methodName = methodName,
                    argsJson = argsJson,
                    objectRegistry = javaObjectRegistry,
                    callback = resultCallback,
                    jsCallbackInvoker = jsBridgeCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader()
                )
            }
        }

        @JavascriptInterface
        fun javaGetStaticField(className: String, fieldName: String): String {
            return JsJavaBridgeDelegates.getStaticField(
                    className = className,
                    fieldName = fieldName,
                    objectRegistry = javaObjectRegistry,
                    bridgeClassLoader = bridgeClassLoader()
            )
        }

        @JavascriptInterface
        fun javaSetStaticField(className: String, fieldName: String, valueJson: String): String {
            return JsJavaBridgeDelegates.setStaticField(
                    className = className,
                    fieldName = fieldName,
                    valueJson = valueJson,
                    objectRegistry = javaObjectRegistry,
                    jsCallbackInvoker = jsBridgeCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader()
            )
        }

        @JavascriptInterface
        fun javaGetInstanceField(instanceHandle: String, fieldName: String): String {
            return JsJavaBridgeDelegates.getInstanceField(
                    instanceHandle = instanceHandle,
                    fieldName = fieldName,
                    objectRegistry = javaObjectRegistry
            )
        }

        @JavascriptInterface
        fun javaSetInstanceField(instanceHandle: String, fieldName: String, valueJson: String): String {
            return JsJavaBridgeDelegates.setInstanceField(
                    instanceHandle = instanceHandle,
                    fieldName = fieldName,
                    valueJson = valueJson,
                    objectRegistry = javaObjectRegistry,
                    jsCallbackInvoker = jsBridgeCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader()
            )
        }

        @JavascriptInterface
        fun __javaReleaseInstanceInternal(instanceHandle: String): String {
            return JsJavaBridgeDelegates.releaseInstance(
                    instanceHandle = instanceHandle,
                    objectRegistry = javaObjectRegistry
            )
        }

        @JavascriptInterface
        fun registerImageFromBase64(base64: String, mimeType: String): String {
            return try {
                val finalMime = if (mimeType.isNotBlank()) mimeType else "image/png"
                val id = ImagePoolManager.addImageFromBase64(base64, finalMime)
                if (id != "error") {
                    "<link type=\"image\" id=\"$id\"></link>"
                } else {
                    "[image registration failed]"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "registerImageFromBase64 failed: ${e.message}", e)
                "[image registration failed: ${e.message}]"
            }
        }

        @JavascriptInterface
        fun registerImageFromPath(path: String): String {
            return try {
                val id = ImagePoolManager.addImage(path)
                if (id != "error") {
                    "<link type=\"image\" id=\"$id\"></link>"
                } else {
                    "[image registration failed]"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "registerImageFromPath failed: ${e.message}", e)
                "[image registration failed: ${e.message}]"
            }
        }

        @JavascriptInterface
        fun image_processing(callbackId: String, operation: String, argsJson: String) {
            JsNativeInterfaceDelegates.imageProcessing(
                    callbackId = callbackId,
                    operation = operation,
                    argsJson = argsJson,
                    binaryDataRegistry = binaryDataRegistry,
                    bitmapRegistry = bitmapRegistry,
                    binaryHandlePrefix = BINARY_HANDLE_PREFIX
            ) { callback, result, isError ->
                sendToolResult(callback, result, isError)
            }
        }

        @JavascriptInterface
        fun crypto(algorithm: String, operation: String, argsJson: String): String {
            return JsNativeInterfaceDelegates.crypto(
                    algorithm = algorithm,
                    operation = operation,
                    argsJson = argsJson
            )
        }

        @JavascriptInterface
        fun sendCallIntermediateResult(callId: String, result: String) {
            try {
                val session = resolveExecutionSession(callId) ?: return
                session.executionListener?.onIntermediateResult(callId, result)
                ContextCompat.getMainExecutor(context).execute {
                    session.intermediateResultCallback?.invoke(result)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error processing call intermediate result: callId=$callId, reason=${e.message}", e)
            }
        }

        /** 同步工具调用 */
        @JavascriptInterface
        fun callTool(toolType: String, toolName: String, paramsJson: String): String {
            return JsNativeInterfaceDelegates.callToolSync(
                toolHandler = toolHandler,
                toolType = toolType,
                toolName = toolName,
                paramsJson = paramsJson,
                binaryDataRegistry = binaryDataRegistry,
                binaryHandlePrefix = BINARY_HANDLE_PREFIX,
                binaryDataThreshold = BINARY_DATA_THRESHOLD
            )
        }

        /** 异步工具调用（新版本，使用Promise） */
        @JavascriptInterface
        fun callToolAsync(
                callbackId: String,
                toolType: String,
                toolName: String,
                paramsJson: String
        ) {
            JsNativeInterfaceDelegates.callToolAsync(
                toolHandler = toolHandler,
                callbackId = callbackId,
                toolType = toolType,
                toolName = toolName,
                paramsJson = paramsJson,
                binaryDataRegistry = binaryDataRegistry,
                binaryHandlePrefix = BINARY_HANDLE_PREFIX,
                binaryDataThreshold = BINARY_DATA_THRESHOLD,
                sendToolResult = { callback, result, isError ->
                    sendToolResult(callback, result, isError)
                }
            )
        }

        /** 向JavaScript发送工具调用结果 */
        private fun sendToolResult(callbackId: String, result: String, isError: Boolean) {
            ensureQuickJs()
            try {
                val jsCode =
                    JsNativeInterfaceDelegates.buildToolResultCallbackScript(
                        callbackId = callbackId,
                        result = result,
                        isError = isError
                    )
                launchQuickJsEvaluation(
                    script = jsCode,
                    onError = { e ->
                        AppLogger.e(TAG, "Error sending tool result to JavaScript: ${e.message}", e)
                    }
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error sending tool result to JavaScript: ${e.message}", e)
            }
        }

        @JavascriptInterface
        fun setCallResult(callId: String, result: String) {
            try {
                val session = resolveExecutionSession(callId)
                AppLogger.d(
                    TAG,
                    "Bridge callback from JavaScript: callId=$callId, length=${result.length}, callback=${session != null}, isDone=${session?.future?.isDone}"
                )
                if (session == null) {
                    AppLogger.e(TAG, "Result callback is null when trying to complete: callId=$callId")
                    return
                }
                if (session.future.isDone) {
                    AppLogger.w(TAG, "Result callback is already completed when trying to set result: callId=$callId")
                    return
                }
                session.executionListener?.onCompleted(callId, result)
                completeCallFuture(
                    session = session,
                    value = result,
                    failureMessage = "Error completing result callback"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error setting result: callId=$callId, reason=${e.message}", e)
                resolveExecutionSession(callId)?.future?.completeExceptionally(e)
            }
        }

        @JavascriptInterface
        fun setCallError(callId: String, error: String) {
            try {
                val session = resolveExecutionSession(callId)
                AppLogger.d(
                    TAG,
                    "Bridge error from JavaScript: callId=$callId, length=${error.length}, callback=${session != null}, isDone=${session?.future?.isDone}"
                )
                if (session == null) {
                    AppLogger.e(TAG, "Result callback is null when trying to complete with error: callId=$callId")
                    return
                }
                if (session.future.isDone) {
                    AppLogger.w(TAG, "Result callback is already completed when trying to set error: callId=$callId")
                    return
                }

                val logMessage = extractErrorLogMessage(error)
                val enrichedLogMessage = withToolPkgCodeContext(session, logMessage)
                AppLogger.e(TOOLPKG_TAG, withToolPkgPluginTag(session, "JS ERROR: $enrichedLogMessage"))
                session.executionListener?.onFailed(callId, error)

                completeCallFuture(
                    session = session,
                    value = "Error: ${withToolPkgCodeContext(session, error)}",
                    failureMessage = "Error completing error callback"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error setting error result: callId=$callId, reason=${e.message}", e)
                resolveExecutionSession(callId)?.future?.completeExceptionally(e)
            }
        }

        private fun completeCallFuture(
            session: ExecutionSession,
            value: String,
            failureMessage: String
        ) {
            try {
                if (!session.future.isDone) {
                    removeExecutionSession(session.callId)
                    session.future.complete(value)
                } else {
                    AppLogger.w(TAG, "Callback became complete between check and execution: callId=${session.callId}")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "$failureMessage: ${e.message}", e)
                if (!session.future.isDone) {
                    session.future.completeExceptionally(e)
                }
            }
        }

        private fun extractErrorLogMessage(error: String): String {
            return try {
                if (error.startsWith("{") && error.endsWith("}")) {
                    val errorJson = JSONObject(error)
                    if (errorJson.has("formatted")) {
                        return errorJson.getString("formatted")
                    }
                    if (errorJson.has("error") && errorJson.has("message")) {
                        val errorType = errorJson.getString("error")
                        val errorMsg = errorJson.getString("message")
                        var message = "$errorType: $errorMsg"
                        if (errorJson.has("details")) {
                            val details = errorJson.getJSONObject("details")
                            if (details.has("fileName") && details.has("lineNumber")) {
                                message +=
                                    "\nAt ${details.getString("fileName")}:${details.getString("lineNumber")}"
                            }
                            if (details.has("stack")) {
                                message += "\nStack: ${details.getString("stack")}"
                            }
                        }
                        return message
                    }
                }
                error
            } catch (e: Exception) {
                AppLogger.d(TAG, "Error parsing error message as JSON: ${e.message}")
                error
            }
        }

        @JavascriptInterface
        fun logInfo(message: String) {
            AppLogger.i(TOOLPKG_TAG, withToolPkgPluginTag(message))
        }

        @JavascriptInterface
        fun logInfoForCall(callId: String, message: String) {
            val session = resolveExecutionSession(callId)
            session?.executionListener?.onCallLog(callId, "info", message)
            AppLogger.i(TOOLPKG_TAG, withToolPkgPluginTag(session, message))
        }

        @JavascriptInterface
        fun logError(message: String) {
            AppLogger.e(TOOLPKG_TAG, withToolPkgPluginTag(message))
        }

        @JavascriptInterface
        fun logErrorForCall(callId: String, message: String) {
            val session = resolveExecutionSession(callId)
            session?.executionListener?.onCallLog(callId, "error", message)
            AppLogger.e(TOOLPKG_TAG, withToolPkgPluginTag(session, message))
        }

        @JavascriptInterface
        fun logDebug(message: String, data: String) {
            AppLogger.d(TOOLPKG_TAG, withToolPkgPluginTag("$message | $data"))
        }

        @JavascriptInterface
        fun reportError(
                errorType: String,
                errorMessage: String,
                errorLine: Int,
                errorStack: String
        ) {
            AppLogger.e(
                    TOOLPKG_TAG,
                    withToolPkgPluginTag(
                        "DETAILED JS ERROR: \nType: $errorType\nMessage: $errorMessage\nLine: $errorLine\nStack: $errorStack"
                    )
            )
        }

        @JavascriptInterface
        fun reportErrorForCall(
                callId: String,
                errorType: String,
                errorMessage: String,
                errorLine: Int,
                errorStack: String
        ) {
            val session = resolveExecutionSession(callId)
            AppLogger.e(
                    TOOLPKG_TAG,
                    withToolPkgPluginTag(
                        session,
                        "DETAILED JS ERROR: \nType: $errorType\nMessage: $errorMessage\nLine: $errorLine\nStack: $errorStack"
                    )
            )
        }
    }

    /** 销毁引擎资源 */
    fun destroy() {
        try {
            // 确保任何挂起的回调被完成
            cancelAllExecutionSessions("Engine destroyed")
            toolCallInterface.detachJavaBridgeLifecycle()

            // 清理Bitmap注册表
            bitmapRegistry.values.forEach { it.recycle() }
            bitmapRegistry.clear()

            // 清理二进制数据注册表
            binaryDataRegistry.clear()
            javaObjectRegistry.clear()

            try {
                val engine = quickJs
                if (engine != null) {
                    runOnQuickJsThreadBlocking {
                        engine.close()
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error closing QuickJS: ${e.message}", e)
            }
            quickJs = null
            quickJsThread = null
            jsEnvironmentInitialized = false
            quickJsDispatcher.close()
            quickJsExecutor.shutdownNow()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error during JsEngine destruction: ${e.message}", e)
        }
    }

}
