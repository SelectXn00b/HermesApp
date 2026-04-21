package com.ai.assistance.operit.core.tools.javascript

import org.json.JSONObject

internal const val TOOLPKG_EXECUTION_ENTRY_FUNCTION = "__operitExecuteScriptFunction"

private fun buildExecutionPreludeSource(): String {
    return """
        function __operitGetActiveCallRuntime() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var runtime =
                root &&
                root.__operit_call_runtime_ref &&
                typeof root.__operit_call_runtime_ref === 'object'
                    ? root.__operit_call_runtime_ref
                    : __operit_call_runtime;
            return runtime && typeof runtime === 'object' ? runtime : __operit_call_runtime;
        }
        function __operitInvokeCallRuntime(methodName, argsLike) {
            var runtime = __operitGetActiveCallRuntime();
            var method = runtime ? runtime[methodName] : undefined;
            if (typeof method !== 'function') {
                return undefined;
            }
            return method.apply(runtime, Array.prototype.slice.call(argsLike || []));
        }
        function __operitInvokeCallRuntimeConsole(methodName, argsLike) {
            var runtime = __operitGetActiveCallRuntime();
            var runtimeConsole = runtime && runtime.console ? runtime.console : null;
            var method = runtimeConsole ? runtimeConsole[methodName] : undefined;
            if (typeof method !== 'function') {
                return undefined;
            }
            return method.apply(runtimeConsole, Array.prototype.slice.call(argsLike || []));
        }
        var sendIntermediateResult = function() { return __operitInvokeCallRuntime('sendIntermediateResult', arguments); };
        var emit = function() { return __operitInvokeCallRuntime('emit', arguments); };
        var delta = function() { return __operitInvokeCallRuntime('delta', arguments); };
        var log = function() { return __operitInvokeCallRuntime('log', arguments); };
        var update = function() { return __operitInvokeCallRuntime('update', arguments); };
        var done = function() { return __operitInvokeCallRuntime('done', arguments); };
        var complete = function() { return __operitInvokeCallRuntime('complete', arguments); };
        var getEnv = function() { return __operitInvokeCallRuntime('getEnv', arguments); };
        var getState = function() { return __operitInvokeCallRuntime('getState', arguments); };
        var getLang = function() { return __operitInvokeCallRuntime('getLang', arguments); };
        var getCallerName = function() { return __operitInvokeCallRuntime('getCallerName', arguments); };
        var getChatId = function() { return __operitInvokeCallRuntime('getChatId', arguments); };
        var getCallerCardId = function() { return __operitInvokeCallRuntime('getCallerCardId', arguments); };
        var __handleAsync = function() { return __operitInvokeCallRuntime('handleAsync', arguments); };
        var console = {
            log: function() { return __operitInvokeCallRuntimeConsole('log', arguments); },
            info: function() { return __operitInvokeCallRuntimeConsole('info', arguments); },
            warn: function() { return __operitInvokeCallRuntimeConsole('warn', arguments); },
            error: function() { return __operitInvokeCallRuntimeConsole('error', arguments); }
        };
        var reportDetailedError = function() { return __operitInvokeCallRuntime('reportDetailedError', arguments); };
        var ToolPkg = globalThis.ToolPkg;
        var Tools = globalThis.Tools;
        var Java = globalThis.Java;
        var Android = globalThis.Android;
        var Intent = globalThis.Intent;
        var PackageManager = globalThis.PackageManager;
        var ContentProvider = globalThis.ContentProvider;
        var SystemManager = globalThis.SystemManager;
        var DeviceController = globalThis.DeviceController;
        var OperitComposeDslRuntime = globalThis.OperitComposeDslRuntime;
        var CryptoJS = globalThis.CryptoJS;
        var Jimp = globalThis.Jimp;
        var UINode = globalThis.UINode;
        var OkHttpClientBuilder = globalThis.OkHttpClientBuilder;
        var OkHttpClient = globalThis.OkHttpClient;
        var RequestBuilder = globalThis.RequestBuilder;
        var OkHttp = globalThis.OkHttp;
        var pako = globalThis.pako;
        var _ = globalThis._;
        var dataUtils = globalThis.dataUtils;
        var toolCall = globalThis.toolCall;
    """.trimIndent()
}

internal fun buildExecutionRuntimeBridgeScript(): String {
    val preludeSource = JSONObject.quote(buildExecutionPreludeSource())
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            if (typeof root.$TOOLPKG_EXECUTION_ENTRY_FUNCTION === 'function') {
                return;
            }

            var runtimePrelude = $preludeSource;

            function text(value) {
                return value == null ? '' : String(value);
            }

            function toBoolean(value) {
                if (value === true || value === false) {
                    return value;
                }
                if (typeof value === 'string') {
                    var normalized = value.trim().toLowerCase();
                    return normalized === 'true' || normalized === '1';
                }
                return !!value;
            }

            function safeSerialize(value) {
                try {
                    return JSON.stringify(value);
                } catch (error) {
                    return JSON.stringify({
                        error: 'Failed to serialize value',
                        message: text(error && error.message ? error.message : error),
                        value: text(value).slice(0, 1000)
                    });
                }
            }

            function getCallState(callId) {
                return typeof root.__operitGetCallState === 'function'
                    ? root.__operitGetCallState(callId)
                    : null;
            }

            function normalizePath(pathValue) {
                var parts = text(pathValue).replace(/\\/g, '/').split('/');
                var stack = [];
                for (var i = 0; i < parts.length; i += 1) {
                    var part = parts[i];
                    if (!part || part === '.') {
                        continue;
                    }
                    if (part === '..') {
                        if (stack.length > 0) {
                            stack.pop();
                        }
                        continue;
                    }
                    stack.push(part);
                }
                return stack.join('/');
            }

            function dirname(pathValue) {
                var normalized = normalizePath(pathValue);
                var index = normalized.lastIndexOf('/');
                return index < 0 ? '' : normalized.slice(0, index);
            }

            function resolveModulePath(request, fromPath) {
                var normalized = text(request).replace(/\\/g, '/').trim();
                if (!normalized) {
                    return '';
                }
                if (!(normalized.startsWith('.') || normalized.startsWith('/'))) {
                    return normalized;
                }
                if (normalized.startsWith('/')) {
                    return normalizePath(normalized);
                }
                var base = dirname(fromPath);
                return normalizePath(base ? base + '/' + normalized : normalized);
            }

            function buildCandidatePaths(modulePath) {
                var normalized = normalizePath(modulePath);
                if (!normalized) {
                    return [];
                }
                if (/\.[a-z0-9]+$/i.test(normalized)) {
                    return [normalized];
                }
                return [
                    normalized,
                    normalized + '.js',
                    normalized + '.json',
                    normalized + '/index.js',
                    normalized + '/index.json'
                ];
            }

            function hashText(value) {
                var textValue = text(value);
                var hash = 0;
                for (var i = 0; i < textValue.length; i += 1) {
                    hash = (((hash << 5) - hash) + textValue.charCodeAt(i)) | 0;
                }
                return (hash >>> 0).toString(16);
            }

            function ensureFactoryCache() {
                if (!root.__operitFactoryCache || typeof root.__operitFactoryCache !== 'object') {
                    root.__operitFactoryCache = Object.create(null);
                }
                return root.__operitFactoryCache;
            }

            function ensureModuleInstanceCache() {
                if (!root.__operitModuleInstanceCache || typeof root.__operitModuleInstanceCache !== 'object') {
                    root.__operitModuleInstanceCache = Object.create(null);
                }
                return root.__operitModuleInstanceCache;
            }

            function buildFactoryKey(kind, identity, source) {
                return [text(kind), text(identity), text(source).length, hashText(source)].join(':');
            }

            function buildModuleInstanceKey(kind, identity, source) {
                return ['instance', text(kind), text(identity), text(source).length, hashText(source)].join(':');
            }

            function createFactory(source) {
                return new Function(
                    'module',
                    'exports',
                    'require',
                    '__operit_call_runtime',
                    runtimePrelude + '\n' + source
                );
            }

            function getFactory(kind, identity, source) {
                var key = buildFactoryKey(kind, identity, source);
                var cache = ensureFactoryCache();
                if (typeof cache[key] === 'function') {
                    return cache[key];
                }
                var factory = createFactory(source);
                cache[key] = factory;
                return factory;
            }

            function tagModuleExports(modulePath, exportsRef) {
                if (typeof exportsRef === 'function') {
                    try { exportsRef.__operit_toolpkg_module_path = modulePath; } catch (_e) {}
                    return;
                }
                if (!exportsRef || typeof exportsRef !== 'object') {
                    return;
                }
                Object.keys(exportsRef).forEach(function(key) {
                    if (typeof exportsRef[key] === 'function') {
                        try { exportsRef[key].__operit_toolpkg_module_path = modulePath; } catch (_e) {}
                    }
                });
            }

            function createRegistrationScreenPlaceholder(modulePath) {
                function ScreenPlaceholder() {
                    return null;
                }
                try { ScreenPlaceholder.__operit_toolpkg_module_path = modulePath; } catch (_e) {}
                return ScreenPlaceholder;
            }

            function normalizeComposeResult(value) {
                if (!value || typeof value !== 'object' || !value.composeDsl || typeof value.composeDsl !== 'object') {
                    return value;
                }
                if (!Object.prototype.hasOwnProperty.call(value.composeDsl, 'screen')) {
                    return value;
                }
                var screenRef = value.composeDsl.screen;
                var resolved = '';
                if (typeof screenRef === 'function') {
                    resolved = text(screenRef.__operit_toolpkg_module_path).trim();
                } else if (
                    screenRef &&
                    typeof screenRef === 'object' &&
                    typeof screenRef.default === 'function'
                ) {
                    resolved = text(screenRef.default.__operit_toolpkg_module_path).trim();
                } else if (typeof screenRef === 'string') {
                    throw new Error('composeDsl.screen must be a compose_dsl screen function, not a string path');
                }
                if (!resolved) {
                    throw new Error('composeDsl.screen is missing a toolpkg module path marker');
                }
                value.composeDsl.screen = resolved.replace(/\\/g, '/');
                return value;
            }

            function findTargetFunction(exportsRef, moduleRef, functionName) {
                if (exportsRef && typeof exportsRef[functionName] === 'function') {
                    return exportsRef[functionName];
                }
                if (moduleRef && moduleRef.exports && typeof moduleRef.exports[functionName] === 'function') {
                    return moduleRef.exports[functionName];
                }
                if (typeof root[functionName] === 'function') {
                    return root[functionName];
                }
                return null;
            }

            function buildAvailableFunctions(exportsRef, moduleRef) {
                var names = [];
                function collect(target) {
                    if (!target || typeof target !== 'object') {
                        return;
                    }
                    Object.keys(target).forEach(function(key) {
                        if (typeof target[key] === 'function' && names.indexOf(key) < 0) {
                            names.push(key);
                        }
                    });
                }
                collect(exportsRef);
                collect(moduleRef && moduleRef.exports ? moduleRef.exports : null);
                return names;
            }

            root.$TOOLPKG_EXECUTION_ENTRY_FUNCTION = function(
                callId,
                params,
                scriptText,
                targetFunctionName,
                timeoutSec,
                preTimeoutMs
            ) {
                var registerCallSession =
                    typeof root.__operitRegisterCallSession === 'function'
                        ? root.__operitRegisterCallSession
                        : null;
                if (typeof registerCallSession !== 'function') {
                    NativeInterface.setCallError(callId, 'JS execution runtime bridge is unavailable');
                    return;
                }

                var safeTimeoutSec = Math.max(1, Number(timeoutSec) || 1);
                var safePreTimeoutMs = Math.max(1000, Number(preTimeoutMs) || 1000);
                var callState = registerCallSession(callId, params);
                var previousCallId = root.__operitCurrentCallId;
                var previousCallRuntime = root.__operit_call_runtime_ref;
                root.__operitCurrentCallId = callId;

                function markStage(stage) {
                    if (callState) {
                        callState.lastExecStage = text(stage);
                    }
                }

                function markFunction(name) {
                    if (callState) {
                        callState.lastExecFunction = text(name);
                    }
                }

                function markRequire(request, fromPath, resolvedPath) {
                    if (!callState) {
                        return;
                    }
                    callState.lastRequireRequest = text(request);
                    callState.lastRequireFrom = text(fromPath);
                    callState.lastRequireResolved = text(resolvedPath);
                }

                function markModule(modulePath) {
                    if (callState) {
                        callState.lastModulePath = text(modulePath);
                    }
                }

                function clearExecutionTimeouts() {
                    if (!callState) {
                        return;
                    }
                    try {
                        if (callState.safetyTimeout) clearTimeout(callState.safetyTimeout);
                        if (callState.safetyTimeoutFinal) clearTimeout(callState.safetyTimeoutFinal);
                    } catch (_e) {
                    }
                    callState.safetyTimeout = null;
                    callState.safetyTimeoutFinal = null;
                }

                function finalizeCall() {
                    clearExecutionTimeouts();
                    if (root.__operitCurrentCallId === callId) {
                        root.__operitCurrentCallId =
                            typeof previousCallId === 'string' ? previousCallId : '';
                    }
                    if (root.__operit_call_runtime_ref === callRuntime) {
                        if (
                            previousCallRuntime &&
                            typeof previousCallRuntime === 'object'
                        ) {
                            root.__operit_call_runtime_ref = previousCallRuntime;
                        } else {
                            try {
                                delete root.__operit_call_runtime_ref;
                            } catch (_deleteRuntimeError) {
                                root.__operit_call_runtime_ref = null;
                            }
                        }
                    }
                    if (typeof root.__operitCleanupCallSession === 'function') {
                        root.__operitCleanupCallSession(callId);
                    }
                }

                function isActive() {
                    var state = getCallState(callId);
                    return !!(state && !state.completed);
                }

                function emitSerializedResult(resultText) {
                    var state = getCallState(callId);
                    if (!state || state.completed) {
                        return;
                    }
                    state.completed = true;
                    NativeInterface.setCallResult(callId, resultText);
                    finalizeCall();
                }

                function emitError(message) {
                    var state = getCallState(callId);
                    if (!state || state.completed) {
                        return;
                    }
                    state.completed = true;
                    NativeInterface.setCallError(callId, text(message || 'Unknown error'));
                    finalizeCall();
                }

                function readCallValue(key, fallbackValue) {
                    var state = getCallState(callId);
                    var currentParams =
                        state && state.params && typeof state.params === 'object'
                            ? state.params
                            : null;
                    var value = currentParams ? currentParams[key] : undefined;
                    return value == null || value === '' ? fallbackValue : text(value);
                }

                callState.safetyTimeout = setTimeout(function() {
                    if (!isActive()) {
                        return;
                    }
                    callState.safetyTimeoutFinal = setTimeout(function() {
                        emitError('Script execution timed out after ' + safeTimeoutSec + ' seconds');
                    }, 5000);
                }, safePreTimeoutMs);

                function callRuntimeReport(error, context) {
                    if (typeof root.__operitReportDetailedErrorForCall === 'function') {
                        return root.__operitReportDetailedErrorForCall(callId, error, context);
                    }
                    return {
                        formatted: text(context) + ': ' + text(error),
                        details: { message: text(error), stack: text(error), lineNumber: 0 }
                    };
                }

                function emitIntermediate(value) {
                    if (isActive()) {
                        NativeInterface.sendCallIntermediateResult(callId, safeSerialize(value));
                    }
                }

                function complete(value) {
                    emitSerializedResult(safeSerialize(normalizeComposeResult(value)));
                }

                function handleAsync(value) {
                    if (!value || typeof value.then !== 'function') {
                        return false;
                    }
                    Promise.resolve(value)
                        .then(function(result) {
                            if (isActive()) {
                                complete(result);
                            }
                        })
                        .catch(function(error) {
                            if (!isActive()) {
                                return;
                            }
                            var report = callRuntimeReport(error, 'Async Promise Rejection');
                            emitError(
                                report && report.formatted
                                    ? JSON.stringify({
                                        error: 'Promise rejection',
                                        details: report.details,
                                        formatted: report.formatted
                                    })
                                    : 'Promise rejection: ' + text(error && error.stack ? error.stack : error)
                            );
                        });
                    return true;
                }

                function createRuntime() {
                    return {
                        emit: emitIntermediate,
                        delta: emitIntermediate,
                        log: emitIntermediate,
                        update: emitIntermediate,
                        sendIntermediateResult: emitIntermediate,
                        done: complete,
                        complete: complete,
                        getEnv: function(key) {
                            var value = NativeInterface.getEnvForCall(callId, text(key).trim());
                            return value == null || value === '' ? undefined : text(value);
                        },
                        getState: function() { return readCallValue('__operit_package_state', undefined); },
                        getLang: function() { return readCallValue('__operit_package_lang', 'en'); },
                        getCallerName: function() { return readCallValue('__operit_package_caller_name', undefined); },
                        getChatId: function() { return readCallValue('__operit_package_chat_id', undefined); },
                        getCallerCardId: function() { return readCallValue('__operit_package_caller_card_id', undefined); },
                        reportDetailedError: callRuntimeReport,
                        handleAsync: handleAsync,
                        console: {
                            log: function() { NativeInterface.logInfoForCall(callId, Array.prototype.slice.call(arguments).join(' ')); },
                            info: function() { NativeInterface.logInfoForCall(callId, Array.prototype.slice.call(arguments).join(' ')); },
                            warn: function() { NativeInterface.logInfoForCall(callId, Array.prototype.slice.call(arguments).join(' ')); },
                            error: function() { NativeInterface.logErrorForCall(callId, Array.prototype.slice.call(arguments).join(' ')); }
                        }
                    };
                }

                var callRuntime = createRuntime();
                root.__operit_call_runtime_ref = callRuntime;
                var registrationMode = toBoolean(readCallValue('__operit_registration_mode', false));
                var packageTarget =
                    readCallValue('__operit_ui_package_name', '') ||
                    readCallValue('toolPkgId', '');
                var screenPath = normalizePath(
                    readCallValue(
                        '__operit_script_screen',
                        params && params.moduleSpec && params.moduleSpec.screen
                            ? text(params.moduleSpec.screen)
                            : ''
                    )
                );
                var moduleCache = registrationMode
                    ? Object.create(null)
                    : ensureModuleInstanceCache();

                function readToolPkgModule(modulePath) {
                    if (
                        !packageTarget ||
                        typeof NativeInterface === 'undefined' ||
                        !NativeInterface ||
                        typeof NativeInterface.readToolPkgTextResource !== 'function'
                    ) {
                        return null;
                    }
                    var candidates = buildCandidatePaths(modulePath);
                    for (var i = 0; i < candidates.length; i += 1) {
                        var candidate = candidates[i];
                        var textResult = NativeInterface.readToolPkgTextResource(packageTarget, candidate);
                        if (typeof textResult === 'string' && textResult.length > 0) {
                            return { path: candidate, text: textResult };
                        }
                    }
                    return null;
                }

                function executeModule(modulePath, moduleText, requireInternal) {
                    markStage('execute_required_module');
                    markModule(modulePath);

                    var moduleKey = buildModuleInstanceKey('module', packageTarget + ':' + modulePath, moduleText);
                    if (moduleCache[moduleKey]) {
                        return moduleCache[moduleKey].exports;
                    }

                    var module = { exports: {} };
                    moduleCache[moduleKey] = module;

                    if (/\.json$/i.test(modulePath)) {
                        try {
                            module.exports = JSON.parse(moduleText);
                            return module.exports;
                        } catch (error) {
                            delete moduleCache[moduleKey];
                            throw error;
                        }
                    }

                    var localRequire = function(nextName) {
                        return requireInternal(nextName, modulePath);
                    };
                    var factory = getFactory('module', packageTarget + ':' + modulePath, moduleText);
                    var previousActiveModule = root.__operitActiveModule;
                    var previousActiveExports = root.__operitActiveModuleExports;
                    root.__operitActiveModule = module;
                    root.__operitActiveModuleExports = module.exports;
                    try {
                        factory(module, module.exports, localRequire, callRuntime);
                    } catch (error) {
                        delete moduleCache[moduleKey];
                        throw error;
                    } finally {
                        root.__operitActiveModule = previousActiveModule;
                        root.__operitActiveModuleExports = previousActiveExports;
                    }
                    tagModuleExports(modulePath, module.exports);
                    return module.exports;
                }

                function requireInternal(moduleName, fromPath) {
                    var request = text(moduleName).trim();
                    if (request === 'lodash') {
                        return root._;
                    }
                    if (request === 'uuid') {
                        return {
                            v4: function() {
                                return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(char) {
                                    var random = Math.random() * 16 | 0;
                                    var value = char === 'x' ? random : ((random & 0x3) | 0x8);
                                    return value.toString(16);
                                });
                            }
                        };
                    }
                    if (request === 'axios') {
                        return {
                            get: function(url, config) {
                                return root.toolCall('http_request', config ? Object.assign({ url: url }, config) : { url: url });
                            },
                            post: function(url, data, config) {
                                return root.toolCall('http_request', config ? Object.assign({ url: url, data: data }, config) : { url: url, data: data });
                            }
                        };
                    }
                    if (!(request.startsWith('.') || request.startsWith('/'))) {
                        return {};
                    }

                    var resolvedPath = resolveModulePath(request, fromPath || screenPath);
                    markStage('require_module');
                    markRequire(request, fromPath || screenPath || '<root>', resolvedPath);
                    markModule(resolvedPath);

                    if (registrationMode && /(^|\/)ui\/.+\.ui\.js$/i.test(resolvedPath)) {
                        return createRegistrationScreenPlaceholder(resolvedPath);
                    }

                    var loaded = readToolPkgModule(resolvedPath);
                    if (!loaded) {
                        throw new Error(
                            'Cannot resolve module "' + request + '" from "' + (fromPath || screenPath || '<root>') + '"'
                        );
                    }
                    return executeModule(loaded.path, loaded.text, requireInternal);
                }

                try {
                    markFunction(targetFunctionName);
                    var mainModuleIdentity = packageTarget + ':' + (screenPath || '<root>');
                    var mainModuleKey = buildModuleInstanceKey('main', mainModuleIdentity, scriptText);
                    var module = moduleCache[mainModuleKey];
                    var exports = module && module.exports ? module.exports : null;
                    var require = function(moduleName) {
                        markStage('require_request');
                        markRequire(moduleName, screenPath || '<root>', '');
                        return requireInternal(moduleName, screenPath);
                    };

                    if (!module) {
                        module = { exports: {} };
                        moduleCache[mainModuleKey] = module;
                        exports = module.exports;
                        markStage('compile_main_script');
                        var mainFactory = getFactory('main', packageTarget + ':' + screenPath, scriptText);
                        markStage('execute_main_script');
                        var previousActiveModule = root.__operitActiveModule;
                        var previousActiveExports = root.__operitActiveModuleExports;
                        root.__operitActiveModule = module;
                        root.__operitActiveModuleExports = exports;
                        try {
                            mainFactory(module, exports, require, callRuntime);
                        } catch (error) {
                            delete moduleCache[mainModuleKey];
                            throw error;
                        } finally {
                            root.__operitActiveModule = previousActiveModule;
                            root.__operitActiveModuleExports = previousActiveExports;
                        }
                    } else {
                        if (exports == null) {
                            exports = {};
                            module.exports = exports;
                        }
                        markStage('reuse_main_script');
                    }
                    var rootExports = module.exports || exports || {};
                    tagModuleExports(screenPath || '<root>', rootExports);

                    var inlineFunctionName = readCallValue('__operit_inline_function_name', '');
                    var inlineFunctionSource = readCallValue('__operit_inline_function_source', '');
                    if (inlineFunctionName && inlineFunctionSource) {
                        markStage('evaluate_inline_hook_function');
                        var inlineFunction = eval('(' + inlineFunctionSource + ')');
                        if (typeof inlineFunction !== 'function') {
                            throw new Error('inline hook source did not evaluate to function');
                        }
                        rootExports[inlineFunctionName] = inlineFunction;
                        module.exports[inlineFunctionName] = inlineFunction;
                    }

                    var targetFunction = findTargetFunction(rootExports, module, targetFunctionName);
                    if (typeof targetFunction !== 'function') {
                        emitError(
                            "Function '" +
                                targetFunctionName +
                                "' not found in script. Available functions: " +
                                buildAvailableFunctions(rootExports, module).join(', ')
                        );
                        return;
                    }

                    markStage('invoke_target_function');
                    var previousModule = callState.currentModule;
                    var previousExports = callState.currentModuleExports;
                    var previousActiveModule = root.__operitActiveModule;
                    var previousActiveExports = root.__operitActiveModuleExports;
                    callState.currentModule = module;
                    callState.currentModuleExports = rootExports;
                    root.__operitActiveModule = module;
                    root.__operitActiveModuleExports = rootExports;
                    var functionResult;
                    try {
                        functionResult = targetFunction(params);
                    } finally {
                        callState.currentModule = previousModule;
                        callState.currentModuleExports = previousExports;
                        root.__operitActiveModule = previousActiveModule;
                        root.__operitActiveModuleExports = previousActiveExports;
                    }

                    markStage('handle_function_result');
                    if (!handleAsync(functionResult)) {
                        complete(functionResult);
                    }
                } catch (error) {
                    var runtimeContext = typeof root.__operitBuildRuntimeContext === 'function'
                        ? text(root.__operitBuildRuntimeContext(callId))
                        : '';
                    emitError(
                        'Script error: ' +
                            text(error && error.message ? error.message : error) +
                            (runtimeContext ? '\nRuntime Context: ' + runtimeContext : '') +
                            (error && error.stack ? '\nStack: ' + text(error.stack) : '')
                    );
                }
            };
        })();
    """.trimIndent()
}
