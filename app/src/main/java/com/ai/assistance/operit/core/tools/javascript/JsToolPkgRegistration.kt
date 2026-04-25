package com.ai.assistance.operit.core.tools.javascript

import org.json.JSONObject
import org.json.JSONTokener

data class ToolPkgMainRegistrationCapture(
    val toolboxUiModules: List<String>,
    val appLifecycleHooks: List<String>,
    val messageProcessingPlugins: List<String>,
    val xmlRenderPlugins: List<String>,
    val inputMenuTogglePlugins: List<String>,
    val toolLifecycleHooks: List<String>,
    val promptInputHooks: List<String>,
    val promptHistoryHooks: List<String>,
    val promptEstimateHistoryHooks: List<String>,
    val systemPromptComposeHooks: List<String>,
    val toolPromptComposeHooks: List<String>,
    val promptFinalizeHooks: List<String>,
    val promptEstimateFinalizeHooks: List<String>
)

private enum class RegistrationBucket {
    TOOLBOX_UI,
    APP_LIFECYCLE,
    MESSAGE_PROCESSING,
    XML_RENDER,
    INPUT_MENU_TOGGLE,
    TOOL_LIFECYCLE,
    PROMPT_INPUT,
    PROMPT_HISTORY,
    PROMPT_ESTIMATE_HISTORY,
    SYSTEM_PROMPT_COMPOSE,
    TOOL_PROMPT_COMPOSE,
    PROMPT_FINALIZE,
    PROMPT_ESTIMATE_FINALIZE
}

internal class JsToolPkgRegistrationSession {
    private val lock = Any()
    private var capture: MutableMap<RegistrationBucket, MutableList<String>>? = null

    fun begin() {
        synchronized(lock) {
            capture = mutableMapOf()
        }
    }

    fun appendToolboxUiModule(specJson: String) = append(RegistrationBucket.TOOLBOX_UI, specJson)
    fun appendAppLifecycleHook(specJson: String) = append(RegistrationBucket.APP_LIFECYCLE, specJson)
    fun appendMessageProcessingPlugin(specJson: String) =
        append(RegistrationBucket.MESSAGE_PROCESSING, specJson)

    fun appendXmlRenderPlugin(specJson: String) = append(RegistrationBucket.XML_RENDER, specJson)
    fun appendInputMenuTogglePlugin(specJson: String) =
        append(RegistrationBucket.INPUT_MENU_TOGGLE, specJson)

    fun appendToolLifecycleHook(specJson: String) =
        append(RegistrationBucket.TOOL_LIFECYCLE, specJson)

    fun appendPromptInputHook(specJson: String) = append(RegistrationBucket.PROMPT_INPUT, specJson)
    fun appendPromptHistoryHook(specJson: String) =
        append(RegistrationBucket.PROMPT_HISTORY, specJson)
    fun appendPromptEstimateHistoryHook(specJson: String) =
        append(RegistrationBucket.PROMPT_ESTIMATE_HISTORY, specJson)

    fun appendSystemPromptComposeHook(specJson: String) =
        append(RegistrationBucket.SYSTEM_PROMPT_COMPOSE, specJson)

    fun appendToolPromptComposeHook(specJson: String) =
        append(RegistrationBucket.TOOL_PROMPT_COMPOSE, specJson)

    fun appendPromptFinalizeHook(specJson: String) =
        append(RegistrationBucket.PROMPT_FINALIZE, specJson)
    fun appendPromptEstimateFinalizeHook(specJson: String) =
        append(RegistrationBucket.PROMPT_ESTIMATE_FINALIZE, specJson)

    fun finish(executionResult: Any?): ToolPkgMainRegistrationCapture {
        if (executionResult is String && executionResult.trim().startsWith("Error:", ignoreCase = true)) {
            val message = executionResult.substringAfter(':', executionResult).trim()
            throw IllegalStateException(message.ifBlank { "toolpkg main registration failed" })
        }
        synchronized(lock) {
            val current = capture.orEmpty()
            fun read(bucket: RegistrationBucket): List<String> = current[bucket]?.toList().orEmpty()
            return ToolPkgMainRegistrationCapture(
                toolboxUiModules = read(RegistrationBucket.TOOLBOX_UI),
                appLifecycleHooks = read(RegistrationBucket.APP_LIFECYCLE),
                messageProcessingPlugins = read(RegistrationBucket.MESSAGE_PROCESSING),
                xmlRenderPlugins = read(RegistrationBucket.XML_RENDER),
                inputMenuTogglePlugins = read(RegistrationBucket.INPUT_MENU_TOGGLE),
                toolLifecycleHooks = read(RegistrationBucket.TOOL_LIFECYCLE),
                promptInputHooks = read(RegistrationBucket.PROMPT_INPUT),
                promptHistoryHooks = read(RegistrationBucket.PROMPT_HISTORY),
                promptEstimateHistoryHooks = read(RegistrationBucket.PROMPT_ESTIMATE_HISTORY),
                systemPromptComposeHooks = read(RegistrationBucket.SYSTEM_PROMPT_COMPOSE),
                toolPromptComposeHooks = read(RegistrationBucket.TOOL_PROMPT_COMPOSE),
                promptFinalizeHooks = read(RegistrationBucket.PROMPT_FINALIZE),
                promptEstimateFinalizeHooks = read(RegistrationBucket.PROMPT_ESTIMATE_FINALIZE)
            )
        }
    }

    fun end() {
        synchronized(lock) {
            capture = null
        }
    }

    private fun append(bucket: RegistrationBucket, specJson: String) {
        val normalized = normalizeRegistrationSpec(specJson)
        synchronized(lock) {
            val target = capture ?: error("toolpkg registration session is not active")
            target.getOrPut(bucket) { mutableListOf() }.add(normalized)
        }
    }

    private fun normalizeRegistrationSpec(specJson: String): String {
        val trimmed = specJson.trim()
        require(trimmed.isNotEmpty()) { "toolpkg registration payload is empty" }
        val parsed = JSONTokener(trimmed).nextValue()
        require(parsed is JSONObject) { "toolpkg registration payload must be a JSON object" }
        return parsed.toString()
    }
}

internal fun buildToolPkgRegistrationBridgeScript(): String {
    return """
        (function() {
            var root = typeof globalThis !== 'undefined'
                ? globalThis
                : (typeof window !== 'undefined' ? window : this);
            var inlineHookCounter = 0;

            function installGlobal(name, value) {
                var key = String(name || '').trim();
                if (!key || value === undefined) {
                    return;
                }
                try { globalThis[key] = value; } catch (_e) {}
                try { window[key] = value; } catch (_e2) {}
            }

            function requireNative(name) {
                if (
                    typeof NativeInterface === 'undefined' ||
                    !NativeInterface ||
                    typeof NativeInterface[name] !== 'function'
                ) {
                    throw new Error('NativeInterface.' + name + ' is unavailable');
                }
                return NativeInterface[name].bind(NativeInterface);
            }

            function copyObject(source, excludedKey) {
                var output = {};
                var keys = Object.keys(source || {});
                for (var i = 0; i < keys.length; i += 1) {
                    var key = keys[i];
                    if (key !== excludedKey) {
                        output[key] = source[key];
                    }
                }
                return output;
            }

            function getActiveExports() {
                return typeof root.__operitGetActiveModuleExports === 'function'
                    ? root.__operitGetActiveModuleExports()
                    : null;
            }

            function resolveExportedFunctionName(fn) {
                var exportsRef = getActiveExports();
                if (!exportsRef || typeof exportsRef !== 'object') {
                    return '';
                }
                var keys = Object.keys(exportsRef);
                for (var i = 0; i < keys.length; i += 1) {
                    if (exportsRef[keys[i]] === fn) {
                        return keys[i];
                    }
                }
                return '';
            }

            function buildInlineFunctionName(definition) {
                inlineHookCounter += 1;
                var rawId = String((definition && definition.id) || 'hook');
                var safeId = rawId.replace(/[^a-zA-Z0-9_$]/g, '_') || 'hook';
                return '__operit_inline_hook_' + safeId + '_' + inlineHookCounter;
            }

            function normalizeFunctionField(definition, fieldName, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                var normalized = copyObject(definition, fieldName);
                var fn = definition[fieldName];
                if (typeof fn !== 'function') {
                    throw new Error(label + ' requires a function reference');
                }
                var exportedName = resolveExportedFunctionName(fn);
                normalized[fieldName] = exportedName || buildInlineFunctionName(definition);
                if (!exportedName) {
                    normalized.function_source = String(fn);
                }
                return normalized;
            }

            function normalizeScreenField(definition, label) {
                if (!definition || typeof definition !== 'object' || Array.isArray(definition)) {
                    throw new Error(label + ' expects an object');
                }
                var normalized = copyObject(definition, 'screen');
                var screen = definition.screen;
                var path = '';
                if (typeof screen === 'string') {
                    path = screen.trim().replace(/\\/g, '/');
                } else if (typeof screen === 'function' && typeof screen.__operit_toolpkg_module_path === 'string') {
                    path = screen.__operit_toolpkg_module_path.trim().replace(/\\/g, '/');
                } else if (
                    screen &&
                    typeof screen === 'object' &&
                    typeof screen.default === 'function' &&
                    typeof screen.default.__operit_toolpkg_module_path === 'string'
                ) {
                    path = screen.default.__operit_toolpkg_module_path.trim().replace(/\\/g, '/');
                }
                if (!path) {
                    throw new Error(label + ' requires a serializable screen reference');
                }
                normalized.screen = path;
                return normalized;
            }

            function registerWithNative(definition, label, nativeMethod, fieldName) {
                var normalized = fieldName
                    ? normalizeFunctionField(definition, fieldName, label)
                    : normalizeScreenField(definition, label);
                requireNative(nativeMethod)(JSON.stringify(normalized));
            }

            function resolveCurrentToolPkgTarget() {
                var callId = String(root.__operitCurrentCallId || '').trim();
                var callState =
                    callId && typeof root.__operitGetCallState === 'function'
                        ? root.__operitGetCallState(callId)
                        : null;
                var params =
                    callState && callState.params && typeof callState.params === 'object'
                        ? callState.params
                        : null;
                if (!params) {
                    return '';
                }
                var candidates = [
                    params.__operit_ui_package_name,
                    params.toolPkgId,
                    params.containerPackageName,
                    params.__operit_toolpkg_subpackage_id,
                    params.__operit_package_name
                ];
                for (var i = 0; i < candidates.length; i += 1) {
                    var value = String(candidates[i] || '').trim();
                    if (value) {
                        return value;
                    }
                }
                return '';
            }

            function readToolPkgResource(key, outputFileName, internal) {
                var resourceKey = String(key || '').trim();
                if (!resourceKey) {
                    return Promise.reject(new Error('resource key is required'));
                }
                var target = resolveCurrentToolPkgTarget();
                if (!target) {
                    return Promise.reject(new Error('package/toolpkg runtime target is empty'));
                }
                var path = requireNative('readToolPkgResource')(
                    target,
                    resourceKey,
                    outputFileName == null ? '' : String(outputFileName).trim(),
                    internal === true ? 'true' : ''
                );
                if (typeof path === 'string' && path.trim()) {
                    return Promise.resolve(path);
                }
                return Promise.reject(new Error('resource not found: ' + resourceKey));
            }

            var api = {
                registerToolboxUiModule: function(definition) {
                    registerWithNative(
                        definition,
                        'registerToolPkgToolboxUiModule',
                        'registerToolPkgToolboxUiModule',
                        ''
                    );
                },
                readResource: readToolPkgResource
            };

            [
                ['registerAppLifecycleHook', 'registerToolPkgAppLifecycleHook'],
                ['registerMessageProcessingPlugin', 'registerToolPkgMessageProcessingPlugin'],
                ['registerXmlRenderPlugin', 'registerToolPkgXmlRenderPlugin'],
                ['registerInputMenuTogglePlugin', 'registerToolPkgInputMenuTogglePlugin'],
                ['registerToolLifecycleHook', 'registerToolPkgToolLifecycleHook'],
                ['registerPromptInputHook', 'registerToolPkgPromptInputHook'],
                ['registerPromptHistoryHook', 'registerToolPkgPromptHistoryHook'],
                ['registerPromptEstimateHistoryHook', 'registerToolPkgPromptEstimateHistoryHook'],
                ['registerSystemPromptComposeHook', 'registerToolPkgSystemPromptComposeHook'],
                ['registerToolPromptComposeHook', 'registerToolPkgToolPromptComposeHook'],
                ['registerPromptFinalizeHook', 'registerToolPkgPromptFinalizeHook'],
                ['registerPromptEstimateFinalizeHook', 'registerToolPkgPromptEstimateFinalizeHook']
            ].forEach(function(entry) {
                var apiName = entry[0];
                var nativeMethod = entry[1];
                api[apiName] = function(definition) {
                    registerWithNative(definition, apiName, nativeMethod, 'function');
                };
            });

            installGlobal('ToolPkg', api);
        })();
    """.trimIndent()
}
