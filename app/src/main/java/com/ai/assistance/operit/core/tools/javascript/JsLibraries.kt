package com.ai.assistance.operit.core.tools.javascript

import android.content.Context

internal data class JsBootstrapModule(
    val fileName: String,
    val source: String,
    val globals: List<String> = emptyList()
)

internal fun buildRuntimeBootstrapModules(
    context: Context,
    operitDownloadDir: String,
    operitCleanOnExitDir: String
): List<JsBootstrapModule> {
    return buildInitRuntimeModules(
        operitDownloadDir = operitDownloadDir,
        operitCleanOnExitDir = operitCleanOnExitDir
    ) + listOf(
        module(
            fileName = "quickjs/init/execution-runtime.js",
            source = buildExecutionRuntimeBridgeScript()
        ),
        module(
            fileName = "quickjs/init/toolpkg-bridge.js",
            source = buildToolPkgRegistrationBridgeScript(),
            globals = listOf("ToolPkg")
        ),
        module(
            fileName = "quickjs/init/tools.js",
            source = getJsToolsDefinition(),
            globals = listOf("Tools")
        ),
        module(
            fileName = "quickjs/init/compose-dsl-bridge.js",
            source = buildComposeDslContextBridgeDefinition(),
            globals = listOf("OperitComposeDslRuntime")
        ),
        module(
            fileName = "quickjs/init/java-bridge.js",
            source = buildJavaClassBridgeDefinition(),
            globals = listOf("Java", "Kotlin")
        ),
        module(
            fileName = "quickjs/init/third-party-libs.js",
            source = getJsThirdPartyLibraries(),
            globals = listOf("_", "dataUtils")
        ),
        module(
            fileName = "assets/js/CryptoJS.js",
            source = loadCryptoJs(context),
            globals = listOf("CryptoJS")
        ),
        module(
            fileName = "assets/js/Jimp.js",
            source = loadJimpJs(context),
            globals = listOf("Jimp")
        ),
        module(
            fileName = "assets/js/UINode.js",
            source = loadUINodeJs(context),
            globals = listOf("UINode")
        ),
        module(
            fileName = "assets/js/AndroidUtils.js",
            source = loadAndroidUtilsJs(context),
            globals = listOf(
                "Android",
                "Intent",
                "PackageManager",
                "ContentProvider",
                "SystemManager",
                "DeviceController"
            )
        ),
        module(
            fileName = "assets/js/OkHttp3.js",
            source = loadOkHttp3Js(context),
            globals = listOf(
                "OkHttpClientBuilder",
                "OkHttpClient",
                "RequestBuilder",
                "OkHttp"
            )
        ),
        module(
            fileName = "assets/js/pako.js",
            source = loadPakoJs(context),
            globals = listOf("pako")
        )
    )
}

private fun module(
    fileName: String,
    source: String,
    globals: List<String> = emptyList()
): JsBootstrapModule {
    return JsBootstrapModule(
        fileName = fileName,
        source = source.trim(),
        globals = globals
    )
}

internal fun getJsThirdPartyLibraries(): String {
    return """
        var _ = {
            isEmpty: function(value) {
                return value == null ||
                    (Array.isArray(value) && value.length === 0) ||
                    (typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0);
            },
            isString: function(value) { return typeof value === 'string'; },
            isNumber: function(value) { return typeof value === 'number' && !isNaN(value); },
            isBoolean: function(value) { return typeof value === 'boolean'; },
            isObject: function(value) { return value != null && typeof value === 'object' && !Array.isArray(value); },
            isArray: function(value) { return Array.isArray(value); },
            forEach: function(collection, iteratee) {
                if (Array.isArray(collection)) {
                    for (var index = 0; index < collection.length; index += 1) {
                        iteratee(collection[index], index, collection);
                    }
                    return collection;
                }
                if (collection && typeof collection === 'object') {
                    var keys = Object.keys(collection);
                    for (var keyIndex = 0; keyIndex < keys.length; keyIndex += 1) {
                        var key = keys[keyIndex];
                        iteratee(collection[key], key, collection);
                    }
                }
                return collection;
            },
            map: function(collection, iteratee) {
                var output = [];
                _.forEach(collection, function(item, key, source) {
                    output.push(iteratee(item, key, source));
                });
                return output;
            }
        };

        var dataUtils = {
            parseJson: function(text) {
                try { return JSON.parse(text); } catch (_error) { return null; }
            },
            stringifyJson: function(value) {
                try { return JSON.stringify(value); } catch (_error) { return '{}'; }
            },
            formatDate: function(value) {
                var date = value ? new Date(value) : new Date();
                function pad(part) { return String(part).padStart(2, '0'); }
                return [
                    date.getFullYear(),
                    pad(date.getMonth() + 1),
                    pad(date.getDate())
                ].join('-') + ' ' + [
                    pad(date.getHours()),
                    pad(date.getMinutes()),
                    pad(date.getSeconds())
                ].join(':');
            }
        };
    """.trimIndent()
}
