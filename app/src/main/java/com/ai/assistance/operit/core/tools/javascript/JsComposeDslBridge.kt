package com.ai.assistance.operit.core.tools.javascript

internal fun buildComposeDslContextBridgeDefinition(): String {
    return """
        var OperitComposeDslRuntime = (function() {
            function cloneObject(input) {
                if (!input || typeof input !== 'object' || Array.isArray(input)) {
                    return {};
                }
                var out = {};
                for (var key in input) {
                    if (Object.prototype.hasOwnProperty.call(input, key)) {
                        out[key] = input[key];
                    }
                }
                return out;
            }

            function normalizeChildren(children) {
                if (!children) {
                    return [];
                }
                if (Array.isArray(children)) {
                    return children;
                }
                return [children];
            }

            function invokeNative(methodName, args) {
                try {
                    if (
                        typeof NativeInterface === 'undefined' ||
                        !NativeInterface ||
                        typeof NativeInterface[methodName] !== 'function'
                    ) {
                        return undefined;
                    }
                    return NativeInterface[methodName].apply(NativeInterface, args || []);
                } catch (e) {
                    console.error('Native bridge call failed for ' + methodName + ':', e);
                    return undefined;
                }
            }

            function __operit_define_unit_getter(unitName) {
                try {
                    if (!unitName || typeof unitName !== 'string') {
                        return;
                    }
                    if (Object.prototype.hasOwnProperty.call(Number.prototype, unitName)) {
                        return;
                    }
                    Object.defineProperty(Number.prototype, unitName, {
                        configurable: true,
                        enumerable: false,
                        get: function() {
                            return { __unit: unitName, value: this.valueOf() };
                        }
                    });
                } catch (e) {
                }
            }

            function __operit_define_array_unit_getter(unitName) {
                try {
                    if (!unitName || typeof unitName !== 'string') {
                        return;
                    }
                    if (Object.prototype.hasOwnProperty.call(Array.prototype, unitName)) {
                        return;
                    }
                    Object.defineProperty(Array.prototype, unitName, {
                        configurable: true,
                        enumerable: false,
                        get: function() {
                            if (!Array.isArray(this)) {
                                return [];
                            }
                            return this.map(function(item) {
                                return { __unit: unitName, value: item };
                            });
                        }
                    });
                } catch (e) {
                }
            }

            __operit_define_unit_getter('px');
            __operit_define_unit_getter('dp');
            __operit_define_unit_getter('fraction');
            __operit_define_array_unit_getter('px');
            __operit_define_array_unit_getter('dp');
            __operit_define_array_unit_getter('fraction');

            function formatTemplateInternal(template, values) {
                var result = String(template || '');
                var source = values && typeof values === 'object' ? values : {};
                for (var key in source) {
                    if (Object.prototype.hasOwnProperty.call(source, key)) {
                        var value = source[key];
                        var placeholder = '{' + key + '}';
                        result = result.split(placeholder).join(value == null ? '' : String(value));
                    }
                }
                return result;
            }

            function createContext(runtimeOptions) {
                var options = runtimeOptions && typeof runtimeOptions === 'object' ? runtimeOptions : {};
                var runtime = {
                    stateStore: cloneObject(options.state),
                    memoStore: cloneObject(options.memo),
                    moduleSpec:
                        options.moduleSpec && typeof options.moduleSpec === 'object'
                            ? options.moduleSpec
                            : {},
                    packageName: String(options.packageName || options.__operit_ui_package_name || ''),
                    toolPkgId: String(options.toolPkgId || options.__operit_ui_toolpkg_id || ''),
                    uiModuleId: String(options.uiModuleId || options.__operit_ui_module_id || ''),
                    callRuntime:
                        options.__operit_call_runtime && typeof options.__operit_call_runtime === 'object'
                            ? options.__operit_call_runtime
                            : null,
                    actionStore: {},
                    actionCounter: 0,
                    stateChangeListeners: []
                };

                function registerAction(handler) {
                    runtime.actionCounter += 1;
                    var actionId = '__action_' + runtime.actionCounter;
                    runtime.actionStore[actionId] = handler;
                    return actionId;
                }

                function notifyStateChanged() {
                    if (!runtime.stateChangeListeners || runtime.stateChangeListeners.length <= 0) {
                        return;
                    }
                    var listeners = runtime.stateChangeListeners.slice();
                    for (var i = 0; i < listeners.length; i += 1) {
                        try {
                            listeners[i]();
                        } catch (e) {
                            try {
                                console.warn('compose_dsl state listener failed:', e);
                            } catch (__ignore) {
                            }
                        }
                    }
                }

                function subscribeStateChange(listener) {
                    if (typeof listener !== 'function') {
                        return function() {};
                    }
                    runtime.stateChangeListeners.push(listener);
                    var active = true;
                    return function() {
                        if (!active) {
                            return;
                        }
                        active = false;
                        var index = runtime.stateChangeListeners.indexOf(listener);
                        if (index >= 0) {
                            runtime.stateChangeListeners.splice(index, 1);
                        }
                    };
                }

                function normalizePropValue(value) {
                    if (typeof value === 'function') {
                        return { __actionId: registerAction(value) };
                    }
                    if (Array.isArray(value)) {
                        return value.map(function(item) {
                            return normalizePropValue(item);
                        });
                    }
                    if (value && typeof value === 'object') {
                        var normalized = {};
                        for (var key in value) {
                            if (Object.prototype.hasOwnProperty.call(value, key)) {
                                normalized[key] = normalizePropValue(value[key]);
                            }
                        }
                        return normalized;
                    }
                    return value;
                }

                function createNode(type, props, children) {
                    var rawProps = props && typeof props === 'object' ? props : {};
                    var normalizedProps = {};
                    for (var key in rawProps) {
                        if (Object.prototype.hasOwnProperty.call(rawProps, key)) {
                            normalizedProps[key] = normalizePropValue(rawProps[key]);
                        }
                    }
                    return {
                        type: String(type || ''),
                        props: normalizedProps,
                        children: normalizeChildren(children)
                    };
                }

                function resolvePackageName(value) {
                    var name = String(value || runtime.packageName || '').trim();
                    return name;
                }

                function useState(key, initialValue) {
                    var stateKey = String(key || '').trim();
                    if (!stateKey) {
                        throw new Error('useState key is required');
                    }
                    if (!Object.prototype.hasOwnProperty.call(runtime.stateStore, stateKey)) {
                        runtime.stateStore[stateKey] = initialValue;
                    }
                    return [
                        runtime.stateStore[stateKey],
                        function(nextValue) {
                            runtime.stateStore[stateKey] = nextValue;
                            notifyStateChanged();
                        }
                    ];
                }

                function useMutable(key, initialValue) {
                    var stateKey = String(key || '').trim();
                    if (!stateKey) {
                        throw new Error('useMutable key is required');
                    }
                    if (!Object.prototype.hasOwnProperty.call(runtime.memoStore, stateKey)) {
                        runtime.memoStore[stateKey] = initialValue;
                    }
                    return [
                        runtime.memoStore[stateKey],
                        function(nextValue) {
                            runtime.memoStore[stateKey] = nextValue;
                        }
                    ];
                }

                function useRef(key, initialValue) {
                    var stateKey = String(key || '').trim();
                    if (!stateKey) {
                        throw new Error('useRef key is required');
                    }
                    if (!Object.prototype.hasOwnProperty.call(runtime.memoStore, stateKey)) {
                        runtime.memoStore[stateKey] = { current: initialValue };
                    }
                    return runtime.memoStore[stateKey];
                }

                function useMemo(key, factory, deps) {
                    var memoKey = String(key || '').trim();
                    if (!memoKey) {
                        throw new Error('useMemo key is required');
                    }
                    if (!Object.prototype.hasOwnProperty.call(runtime.memoStore, memoKey)) {
                        runtime.memoStore[memoKey] =
                            typeof factory === 'function' ? factory() : factory;
                    }
                    return runtime.memoStore[memoKey];
                }

                function normalizeToolName(targetPackage, toolName) {
                    var basePackage = String(targetPackage || '').trim();
                    var normalizedTool = String(toolName || '').trim();
                    if (!normalizedTool) {
                        return '';
                    }
                    if (normalizedTool.indexOf(':') >= 0 || !basePackage) {
                        return normalizedTool;
                    }
                    return basePackage + ':' + normalizedTool;
                }

                function createNodeFactory(type) {
                    var nodeType = String(type || '');
                    return function(props, children) {
                        return createNode(nodeType, props, children);
                    };
                }

                function createModifierProxy(ops) {
                    var chain = Array.isArray(ops) ? ops.slice() : [];
                    var target = {
                        __modifierOps: chain
                    };
                    return new Proxy(target, {
                        get: function(_target, key) {
                            if (key === '__modifierOps') {
                                return chain;
                            }
                            if (key === 'toJSON') {
                                return function() {
                                    return { __modifierOps: chain.slice() };
                                };
                            }
                            if (key === 'then') {
                                return undefined;
                            }
                            if (typeof key !== 'string') {
                                return undefined;
                            }
                            return function() {
                                var args = [];
                                for (var i = 0; i < arguments.length; i += 1) {
                                    args.push(normalizePropValue(arguments[i]));
                                }
                                var next = chain.slice();
                                next.push({
                                    name: key,
                                    args: args
                                });
                                return createModifierProxy(next);
                            };
                        }
                    });
                }

                function createColorToken(name, alpha) {
                    return {
                        __colorToken: String(name || ''),
                        alpha: typeof alpha === 'number' ? alpha : undefined,
                        copy: function(options) {
                            var nextAlpha = options && typeof options.alpha === 'number' ? options.alpha : alpha;
                            return createColorToken(name, nextAlpha);
                        }
                    };
                }

                var MaterialTheme = {
                    colorScheme: new Proxy({}, {
                        get: function(_target, key) {
                            if (typeof key !== 'string') {
                                return undefined;
                            }
                            return createColorToken(key);
                        }
                    })
                };

                var uiProxy = new Proxy({}, {
                    get: function(_target, key) {
                        if (typeof key !== 'string') {
                            return undefined;
                        }
                        return createNodeFactory(key);
                    }
                });

                var ctx = {
                    MaterialTheme: MaterialTheme,
                    useState: useState,
                    useMutable: useMutable,
                    useRef: useRef,
                    useMemo: useMemo,
                    callTool: function(toolName, params) {
                        return toolCall(toolName, params || {});
                    },
                    toolCall: function() {
                        return toolCall.apply(null, arguments);
                    },
                    getEnv: function(key) {
                        if (runtime.callRuntime && typeof runtime.callRuntime.getEnv === 'function') {
                            return runtime.callRuntime.getEnv(key);
                        }
                        return undefined;
                    },
                    setEnv: function(key, value) {
                        invokeNative('setEnv', [
                            String(key || ''),
                            value === undefined || value === null ? '' : String(value)
                        ]);
                        return Promise.resolve();
                    },
                    setEnvs: function(values) {
                        var payload = values && typeof values === 'object' ? values : {};
                        invokeNative('setEnvs', [JSON.stringify(payload)]);
                        return Promise.resolve();
                    },
                    navigate: function(route, args) {
                        return Promise.resolve();
                    },
                    showToast: function(message) {
                        return toolCall('toast', { message: String(message || '') });
                    },
                    reportError: function(error) {
                        console.error('compose_dsl reportError:', error);
                        return Promise.resolve();
                    },
                    measureText: function(options) {
                        var payload = options && typeof options === 'object' ? options : {};
                        var json = invokeNative('measureComposeText', [JSON.stringify(payload)]);
                        if (typeof json !== 'string' || !json.trim()) {
                            throw new Error('measureText failed to return data');
                        }
                        return JSON.parse(json);
                    },
                    getModuleSpec: function() {
                        return runtime.moduleSpec;
                    },
                    formatTemplate: function(template, values) {
                        return formatTemplateInternal(template, values);
                    },
                    getCurrentPackageName: function() {
                        return runtime.packageName || undefined;
                    },
                    getCurrentToolPkgId: function() {
                        return runtime.toolPkgId || undefined;
                    },
                    getCurrentUiModuleId: function() {
                        return runtime.uiModuleId || undefined;
                    },
                    isPackageImported: function(packageName) {
                        var target = resolvePackageName(packageName);
                        if (!target) {
                            return Promise.resolve(false);
                        }
                        var result = invokeNative('isPackageImported', [target]);
                        if (result === true || result === false || result === 'true' || result === 'false') {
                            return Promise.resolve(result === true || result === 'true');
                        }
                        return toolCall('is_package_imported', { package_name: target });
                    },
                    importPackage: function(packageName) {
                        var target = resolvePackageName(packageName);
                        if (!target) {
                            return Promise.resolve('');
                        }
                        var result = invokeNative('importPackage', [target]);
                        if (result !== undefined && result !== null) {
                            return Promise.resolve(result);
                        }
                        return toolCall('import_package', { package_name: target });
                    },
                    removePackage: function(packageName) {
                        var target = resolvePackageName(packageName);
                        if (!target) {
                            return Promise.resolve('');
                        }
                        var result = invokeNative('removePackage', [target]);
                        if (result !== undefined && result !== null) {
                            return Promise.resolve(result);
                        }
                        return toolCall('remove_package', { package_name: target });
                    },
                    usePackage: function(packageName) {
                        var target = resolvePackageName(packageName);
                        if (!target) {
                            return Promise.resolve('');
                        }
                        var result = invokeNative('usePackage', [target]);
                        if (result !== undefined && result !== null) {
                            return Promise.resolve(result);
                        }
                        return toolCall('use_package', { package_name: target });
                    },
                    listImportedPackages: function() {
                        var json = invokeNative('listImportedPackagesJson', []);
                        if (typeof json === 'string' && json.trim()) {
                            try {
                                return Promise.resolve(JSON.parse(json));
                            } catch (e) {
                                return Promise.resolve([]);
                            }
                        }
                        return toolCall('list_imported_packages', {});
                    },
                    resolveToolName: function(request) {
                        var req = request && typeof request === 'object' ? request : {};
                        var packageName = String(req.packageName || runtime.packageName || '');
                        var subpackageId = String(req.subpackageId || '');
                        var toolName = String(req.toolName || '');
                        var preferImported = req.preferImported === false ? 'false' : 'true';
                        if (!toolName) {
                            return Promise.resolve('');
                        }
                        var result = invokeNative('resolveToolName', [
                            packageName,
                            subpackageId,
                            toolName,
                            preferImported
                        ]);
                        if (typeof result === 'string' && result.trim()) {
                            return Promise.resolve(result);
                        }
                        return Promise.resolve(normalizeToolName(packageName, toolName));
                    },
                    h: function(type, props, children) {
                        return createNode(String(type || ''), props, children);
                    },
                    Modifier: createModifierProxy([]),
                    UI: uiProxy
                };

                return {
                    ctx: ctx,
                    state: runtime.stateStore,
                    memo: runtime.memoStore,
                    setCallRuntime: function(nextCallRuntime) {
                        if (nextCallRuntime && typeof nextCallRuntime === 'object') {
                            runtime.callRuntime = nextCallRuntime;
                        }
                    },
                    invokeAction: function(actionId, payload) {
                        var id = String(actionId || '').trim();
                        if (!id) {
                            throw new Error('compose action id is required');
                        }
                        var handler = runtime.actionStore[id];
                        if (typeof handler !== 'function') {
                            throw new Error('compose action not found: ' + id);
                        }
                        return handler(payload);
                    },
                    subscribeStateChange: subscribeStateChange
                };
            }

            return {
                createContext: createContext
            };
        })();
    """.trimIndent()
}
