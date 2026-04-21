package com.ai.assistance.operit.core.tools.javascript

internal fun buildQuickJsCompatScript(): String =
    """
        (function() {
            var root = typeof globalThis !== 'undefined' ? globalThis : this;
            if (typeof root.globalThis === 'undefined') root.globalThis = root;
            if (typeof root.window === 'undefined') root.window = root;
            if (typeof root.self === 'undefined') root.self = root;
            if (typeof root.global === 'undefined') root.global = root;

            var nativeBridge = root.NativeInterface;
            function callHost(method, args) {
                if (!nativeBridge || typeof nativeBridge.__call !== 'function') {
                    return null;
                }
                return nativeBridge.__call(method, args == null ? null : JSON.stringify(args));
            }

            root.NativeInterface = new Proxy({}, {
                get: function(_, property) {
                    if (property === '__call') {
                        return nativeBridge && nativeBridge.__call;
                    }
                    return function() {
                        return callHost(String(property), Array.prototype.slice.call(arguments));
                    };
                }
            });

            root.console = root.console || {};
            ['log', 'info', 'warn', 'error', 'debug'].forEach(function(level) {
                if (typeof root.console[level] === 'function') {
                    return;
                }
                root.console[level] = function() {
                    var args = Array.prototype.slice.call(arguments).map(function(value) {
                        if (typeof value === 'string') return value;
                        try { return JSON.stringify(value); } catch (_error) { return String(value); }
                    });
                    callHost('console.' + level, args);
                };
            });

            if (typeof root.queueMicrotask !== 'function') {
                root.queueMicrotask = function(callback) {
                    Promise.resolve().then(callback);
                };
            }

            function createStorage() {
                var store = {};
                return {
                    get length() { return Object.keys(store).length; },
                    key: function(index) {
                        var keys = Object.keys(store);
                        return index >= 0 && index < keys.length ? keys[index] : null;
                    },
                    getItem: function(key) {
                        var normalized = String(key);
                        return Object.prototype.hasOwnProperty.call(store, normalized)
                            ? store[normalized]
                            : null;
                    },
                    setItem: function(key, value) { store[String(key)] = String(value); },
                    removeItem: function(key) { delete store[String(key)]; },
                    clear: function() { store = {}; }
                };
            }

            if (!root.localStorage) root.localStorage = createStorage();
            if (!root.sessionStorage) root.sessionStorage = createStorage();
            if (!root.performance) root.performance = {};
            if (typeof root.performance.now !== 'function') {
                var start = Date.now();
                root.performance.now = function() { return Date.now() - start; };
            }

            var timerState = root.__operitTimerState;
            if (!timerState || typeof timerState !== 'object') {
                timerState = { nextId: 1, entries: {} };
                root.__operitTimerState = timerState;
            }

            function normalizeTimerCallback(callback) {
                if (typeof callback === 'function') return callback;
                if (typeof callback === 'string') {
                    return function() { return eval(callback); };
                }
                throw new Error('Timer callback must be a function or string');
            }

            function normalizeDelay(delay) {
                var numericDelay = Number(delay);
                if (!isFinite(numericDelay)) return 0;
                return Math.max(0, Math.floor(numericDelay));
            }

            function clearTimer(timerId) {
                var normalizedId = Number(timerId) || 0;
                delete timerState.entries[normalizedId];
                if (root.NativeInterface && typeof root.NativeInterface.cancelTimer === 'function') {
                    root.NativeInterface.cancelTimer(String(normalizedId));
                }
            }

            function scheduleTimer(callback, delay, repeat, args) {
                var timerId = timerState.nextId++;
                timerState.entries[timerId] = {
                    callback: normalizeTimerCallback(callback),
                    args: args || [],
                    repeat: !!repeat
                };
                if (!root.NativeInterface || typeof root.NativeInterface.scheduleTimer !== 'function') {
                    throw new Error('NativeInterface.scheduleTimer is unavailable');
                }
                root.NativeInterface.scheduleTimer(
                    String(timerId),
                    normalizeDelay(delay),
                    !!repeat
                );
                return timerId;
            }

            root.__operitDispatchTimer = function(timerId) {
                var normalizedId = Number(timerId) || 0;
                var entry = timerState.entries[normalizedId];
                if (!entry) {
                    return;
                }
                if (!entry.repeat) {
                    delete timerState.entries[normalizedId];
                }
                try {
                    return entry.callback.apply(root, entry.args || []);
                } catch (error) {
                    if (typeof root.reportDetailedError === 'function') {
                        root.reportDetailedError(error, 'Timer callback');
                    } else if (root.console && typeof root.console.error === 'function') {
                        root.console.error(String(error && error.message ? error.message : error));
                    }
                }
            };

            root.__operitClearAllTimers = function() {
                Object.keys(timerState.entries).forEach(clearTimer);
            };

            root.setTimeout = function(callback, delay) {
                return scheduleTimer(callback, delay, false, Array.prototype.slice.call(arguments, 2));
            };
            root.clearTimeout = clearTimer;
            root.setInterval = function(callback, delay) {
                return scheduleTimer(callback, delay, true, Array.prototype.slice.call(arguments, 2));
            };
            root.clearInterval = clearTimer;
        })();
    """.trimIndent()
