package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.nio.charset.StandardCharsets

internal class PackageManagerToolPkgFacade(
    private val packageManager: PackageManager
) {
    private fun buildToolPkgToolboxUiModules(
        container: ToolPkgContainerRuntime,
        localizationContext: Context,
        runtime: String
    ): List<PackageManager.ToolPkgToolboxUiModule> {
        val containerDisplayName =
            container.displayName.resolve(localizationContext).ifBlank { container.packageName }
        val containerDescription = container.description.resolve(localizationContext)
        return container.uiModules
            .filter { module ->
                module.runtime.equals(runtime, ignoreCase = true)
            }
            .map { module ->
                val moduleTitle =
                    module.title.resolve(localizationContext).trim().ifBlank { containerDisplayName }
                PackageManager.ToolPkgToolboxUiModule(
                    containerPackageName = container.packageName,
                    toolPkgId = container.packageName,
                    uiModuleId = module.id,
                    runtime = module.runtime,
                    screen = module.screen,
                    title = moduleTitle,
                    description = containerDescription,
                    moduleSpec =
                        mapOf(
                            "id" to module.id,
                            "runtime" to module.runtime,
                            "screen" to module.screen,
                            "title" to moduleTitle,
                            "toolPkgId" to container.packageName
                        )
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgToolboxUiModule::title,
                    PackageManager.ToolPkgToolboxUiModule::containerPackageName,
                    PackageManager.ToolPkgToolboxUiModule::uiModuleId
                )
            )
    }

    fun isToolPkgContainer(packageName: String): Boolean {
        packageManager.ensureInitialized()
        val normalizedPackageName = packageManager.normalizePackageName(packageName)
        return packageManager.toolPkgContainersInternal.containsKey(normalizedPackageName)
    }

    fun isToolPkgSubpackage(packageName: String): Boolean {
        packageManager.ensureInitialized()
        return packageManager.resolveToolPkgSubpackageRuntimeInternal(packageName) != null
    }

    fun getToolPkgContainerDetails(
        packageName: String,
        resolveContext: Context? = null
    ): PackageManager.ToolPkgContainerDetails? {
        packageManager.ensureInitialized()
        val normalizedPackageName = packageManager.normalizePackageName(packageName)
        val container = packageManager.toolPkgContainersInternal[normalizedPackageName] ?: return null
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        val localizationContext = resolveContext ?: packageManager.contextInternal
        val containerEnabled = enabledSet.contains(container.packageName)
        val toolboxUiModules =
            if (containerEnabled) {
                buildToolPkgToolboxUiModules(
                    container = container,
                    localizationContext = localizationContext,
                    runtime = TOOLPKG_RUNTIME_COMPOSE_DSL
                )
            } else {
                emptyList()
            }

        val subpackages =
            container.subpackages.map { subpackage ->
                PackageManager.ToolPkgSubpackageInfo(
                    packageName = subpackage.packageName,
                    subpackageId = subpackage.subpackageId,
                    displayName = subpackage.displayName.resolve(localizationContext),
                    description = subpackage.description.resolve(localizationContext),
                    enabledByDefault = subpackage.enabledByDefault,
                    toolCount = subpackage.toolCount,
                    enabled = containerEnabled && enabledSet.contains(subpackage.packageName)
                )
            }

        val result = PackageManager.ToolPkgContainerDetails(
            packageName = container.packageName,
            displayName = container.displayName.resolve(localizationContext),
            description = container.description.resolve(localizationContext),
            version = container.version,
            resourceCount = container.resources.size,
            uiModuleCount = container.uiModules.size,
            toolboxUiModules = toolboxUiModules,
            subpackages = subpackages
        )
        return result
    }

    fun getToolPkgToolboxUiModules(
        runtime: String = TOOLPKG_RUNTIME_COMPOSE_DSL,
        resolveContext: Context? = null
    ): List<PackageManager.ToolPkgToolboxUiModule> {
        packageManager.ensureInitialized()
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        val localizationContext = resolveContext ?: packageManager.contextInternal

        val result = packageManager.toolPkgContainersInternal.values
            .filter { container -> enabledSet.contains(container.packageName) }
            .flatMap { container ->
                buildToolPkgToolboxUiModules(
                    container = container,
                    localizationContext = localizationContext,
                    runtime = runtime
                )
            }
            .sortedWith(
                compareBy(
                    PackageManager.ToolPkgToolboxUiModule::title,
                    PackageManager.ToolPkgToolboxUiModule::containerPackageName,
                    PackageManager.ToolPkgToolboxUiModule::uiModuleId
                )
            )
        return result
    }

    fun setToolPkgSubpackageEnabled(subpackagePackageName: String, enabled: Boolean): Boolean {
        packageManager.ensureInitialized()
        val normalizedPackageName = packageManager.normalizePackageName(subpackagePackageName)
        val subpackageRuntime = packageManager.toolPkgSubpackageByPackageNameInternal[normalizedPackageName]
        if (subpackageRuntime == null) {
            return false
        }

        val enabledPackageNames = LinkedHashSet(packageManager.getEnabledPackageNames())
        val subpackageStates = packageManager.getToolPkgSubpackageStatesInternal().toMutableMap()
        val containerEnabled = enabledPackageNames.contains(subpackageRuntime.containerPackageName)

        subpackageStates[normalizedPackageName] = enabled

        if (containerEnabled && enabled) {
            enabledPackageNames.add(normalizedPackageName)
        } else {
            enabledPackageNames.remove(normalizedPackageName)
            packageManager.unregisterPackageTools(normalizedPackageName)
        }

        packageManager.saveEnabledPackageNames(enabledPackageNames.toList())
        packageManager.saveToolPkgSubpackageStates(subpackageStates)

        val stateSaved = packageManager.getToolPkgSubpackageStatesInternal()[normalizedPackageName] == enabled
        val importedMatches =
            if (containerEnabled) {
                packageManager.getEnabledPackageNames().contains(normalizedPackageName) == enabled
            } else {
                !packageManager.getEnabledPackageNames().contains(normalizedPackageName)
            }
        return stateSaved && importedMatches
    }

    fun findPreferredPackageNameForSubpackageId(
        subpackageId: String,
        preferEnabled: Boolean = true
    ): String? {
        packageManager.ensureInitialized()
        if (subpackageId.isBlank()) return null

        val directRuntime = packageManager.resolveToolPkgSubpackageRuntimeInternal(subpackageId)
        if (directRuntime != null) {
            if (preferEnabled) {
                if (packageManager.isPackageEnabled(directRuntime.packageName)) {
                    return directRuntime.packageName
                }
            }
            return directRuntime.packageName
        }

        val candidates =
            packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                it.subpackageId.equals(subpackageId, ignoreCase = true)
            }

        if (candidates.isEmpty()) {
            return null
        }

        if (preferEnabled) {
            val enabledCandidate = candidates.firstOrNull { packageManager.isPackageEnabled(it.packageName) }
            if (enabledCandidate != null) {
                return enabledCandidate.packageName
            }
        }

        return candidates.first().packageName
    }

    fun copyToolPkgResourceToFileBySubpackageId(
        subpackageId: String,
        resourceKey: String,
        destinationFile: File,
        preferEnabledContainer: Boolean = true
    ): Boolean {
        packageManager.ensureInitialized()
        if (subpackageId.isBlank() || resourceKey.isBlank()) {
            return false
        }

        val directSubpackage = packageManager.resolveToolPkgSubpackageRuntimeInternal(subpackageId)
        val subpackages =
            if (directSubpackage != null) {
                listOf(directSubpackage)
            } else {
                packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                    it.subpackageId.equals(subpackageId, ignoreCase = true)
                }
            }

        if (subpackages.isEmpty()) {
            return false
        }

        val candidateContainers =
            if (preferEnabledContainer) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                val enabledContainers =
                    subpackages
                        .map { it.containerPackageName }
                        .distinct()
                        .filter { enabledSet.contains(it) }
                if (enabledContainers.isNotEmpty()) {
                    enabledContainers
                } else {
                    subpackages.map { it.containerPackageName }.distinct()
                }
            } else {
                subpackages.map { it.containerPackageName }.distinct()
            }

        candidateContainers.forEach { containerName ->
            if (copyToolPkgResourceToFile(containerName, resourceKey, destinationFile)) {
                return true
            }
        }

        return false
    }

    fun copyToolPkgResourceToFile(
        containerPackageName: String,
        resourceKey: String,
        destinationFile: File
    ): Boolean {
        packageManager.ensureInitialized()
        val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
        val runtime = packageManager.toolPkgContainersInternal[normalizedContainerPackageName] ?: return false
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        if (!enabledSet.contains(runtime.packageName)) {
            return false
        }
        val resource =
            runtime.resources.firstOrNull {
                it.key.equals(resourceKey, ignoreCase = true)
            } ?: return false

        return try {
            packageManager.exportToolPkgResource(runtime, resource, destinationFile)
        } catch (e: Exception) {
            AppLogger.e("PackageManager", "Failed to export toolpkg resource: ${runtime.packageName}:${resource.key}", e)
            false
        }
    }

    fun getToolPkgResourceOutputFileName(
        packageNameOrSubpackageId: String,
        resourceKey: String,
        preferEnabledContainer: Boolean = true
    ): String? {
        packageManager.ensureInitialized()
        val target = packageNameOrSubpackageId.trim()
        val key = resourceKey.trim()
        if (target.isBlank() || key.isBlank()) {
            return null
        }

        fun resolveFromContainer(containerName: String): String? {
            val normalizedContainerName = packageManager.normalizePackageName(containerName)
            val runtime = packageManager.toolPkgContainersInternal[normalizedContainerName] ?: return null
            val resource =
                runtime.resources.firstOrNull {
                    it.key.equals(key, ignoreCase = true)
                } ?: return null
            val baseName =
                resource.path.substringAfterLast('/').substringAfterLast('\\').trim()
            if (baseName.isBlank()) {
                return null
            }
            return if (ToolPkgArchiveParser.isDirectoryResourceMime(resource.mime)) {
                if (baseName.endsWith(".zip", ignoreCase = true)) baseName else "$baseName.zip"
            } else {
                baseName
            }
        }

        resolveFromContainer(target)?.let { return it }

        val directSubpackage = packageManager.resolveToolPkgSubpackageRuntimeInternal(target)
        if (directSubpackage != null) {
            resolveFromContainer(directSubpackage.containerPackageName)?.let { return it }
        }

        val subpackages =
            packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                it.subpackageId.equals(target, ignoreCase = true)
            }
        if (subpackages.isEmpty()) {
            return null
        }

        val candidateContainers =
            if (preferEnabledContainer) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                val enabledContainers =
                    subpackages
                        .map { it.containerPackageName }
                        .distinct()
                        .filter { enabledSet.contains(it) }
                if (enabledContainers.isNotEmpty()) {
                    enabledContainers
                } else {
                    subpackages.map { it.containerPackageName }.distinct()
                }
            } else {
                subpackages.map { it.containerPackageName }.distinct()
            }

        candidateContainers.forEach { containerName ->
            resolveFromContainer(containerName)?.let { return it }
        }

        return null
    }

    fun getToolPkgComposeDslScriptBySubpackageId(
        subpackageId: String,
        uiModuleId: String? = null,
        preferEnabledContainer: Boolean = true
    ): String? {
        packageManager.ensureInitialized()
        if (subpackageId.isBlank()) {
            return null
        }

        val directSubpackage = packageManager.resolveToolPkgSubpackageRuntimeInternal(subpackageId)
        val subpackages =
            if (directSubpackage != null) {
                listOf(directSubpackage)
            } else {
                packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                    it.subpackageId.equals(subpackageId, ignoreCase = true)
                }
            }

        if (subpackages.isEmpty()) {
            return null
        }

        val candidateContainers =
            if (preferEnabledContainer) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                subpackages
                    .map { it.containerPackageName }
                    .distinct()
                    .filter { enabledSet.contains(it) }
            } else {
                subpackages.map { it.containerPackageName }.distinct()
            }

        candidateContainers.forEach { containerName ->
            val script = getToolPkgComposeDslScript(containerName, uiModuleId)
            if (!script.isNullOrBlank()) {
                return script
            }
        }

        return null
    }

    fun getToolPkgComposeDslScript(
        containerPackageName: String,
        uiModuleId: String? = null
    ): String? {
        packageManager.ensureInitialized()
        val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
        val runtime = packageManager.toolPkgContainersInternal[normalizedContainerPackageName] ?: return null
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        if (!enabledSet.contains(runtime.packageName)) {
            return null
        }

        val uiModule =
            if (!uiModuleId.isNullOrBlank()) {
                runtime.uiModules.firstOrNull { module ->
                    module.id.equals(uiModuleId, ignoreCase = true) &&
                        module.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)
                }
            } else {
                runtime.uiModules.firstOrNull { module ->
                    module.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)
                }
            } ?: return null

        if (uiModule.screen.isBlank()) {
            return null
        }

        return try {
            val bytes = packageManager.readToolPkgResourceBytes(runtime, uiModule.screen) ?: return null
            bytes.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.e(
                "PackageManager",
                "Failed to read toolpkg compose_dsl script: ${runtime.packageName}:${uiModule.id}",
                e
            )
            null
        }
    }

    fun getToolPkgComposeDslScreenPath(
        containerPackageName: String,
        uiModuleId: String? = null
    ): String? {
        packageManager.ensureInitialized()
        val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
        val runtime = packageManager.toolPkgContainersInternal[normalizedContainerPackageName] ?: return null
        val enabledSet = packageManager.getEnabledPackageNameSetInternal()
        if (!enabledSet.contains(runtime.packageName)) {
            return null
        }

        val uiModule =
            if (!uiModuleId.isNullOrBlank()) {
                runtime.uiModules.firstOrNull { module ->
                    module.id.equals(uiModuleId, ignoreCase = true) &&
                        module.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)
                }
            } else {
                runtime.uiModules.firstOrNull { module ->
                    module.runtime.equals(TOOLPKG_RUNTIME_COMPOSE_DSL, ignoreCase = true)
                }
            } ?: return null

        return uiModule.screen.trim().ifBlank { null }
    }

    fun runToolPkgMainHook(
        containerPackageName: String,
        functionName: String,
        event: String,
        eventName: String? = null,
        pluginId: String? = null,
        inlineFunctionSource: String? = null,
        eventPayload: Map<String, Any?> = emptyMap(),
        onIntermediateResult: ((Any?) -> Unit)? = null
    ): Result<Any?> {
        val normalizedPluginId = pluginId?.trim().orEmpty().ifBlank { null }
        val resolvedEventName = eventName?.trim().orEmpty().ifBlank { event }
        val shouldLogTiming = event.equals(TOOLPKG_EVENT_MESSAGE_PROCESSING, ignoreCase = true)
        val totalStartTime = if (shouldLogTiming) messageTimingNow() else 0L

        return runCatching {
            val normalizedContainerPackageName = packageManager.normalizePackageName(containerPackageName)
            val runtime =
                packageManager.toolPkgContainersInternal[normalizedContainerPackageName]
                    ?: throw IllegalArgumentException("ToolPkg container not found: $containerPackageName")

            val getMainScriptStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            val script =
                packageManager.getToolPkgMainScriptInternal(runtime.packageName)
                    ?: throw IllegalStateException("ToolPkg main script is unavailable: ${runtime.packageName}")
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.getMainScript",
                    startTimeMs = getMainScriptStartTime,
                    details = "container=${runtime.packageName}, plugin=${normalizedPluginId ?: "none"}, scriptLength=${script.length}"
                )
            }

            val resolveFunctionSourceStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            val functionSource = inlineFunctionSource?.trim().orEmpty().ifBlank { null }
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.resolveFunctionSource",
                    startTimeMs = resolveFunctionSourceStartTime,
                    details = "container=${runtime.packageName}, function=$functionName, hasInline=${!functionSource.isNullOrBlank()}"
                )
            }

            val timestampMs = System.currentTimeMillis()
            val params = mutableMapOf<String, Any?>(
                "event" to resolvedEventName,
                "eventName" to resolvedEventName,
                "eventPayload" to eventPayload,
                "timestampMs" to timestampMs,
                "functionName" to functionName,
                "toolPkgId" to runtime.packageName,
                "containerPackageName" to runtime.packageName,
                "__operit_ui_package_name" to runtime.packageName,
                "__operit_script_screen" to runtime.mainEntry
            )
            if (!normalizedPluginId.isNullOrBlank()) {
                params["pluginId"] = normalizedPluginId
            }
            eventPayload["chatId"]
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { chatId ->
                    params["__operit_package_chat_id"] = chatId
                }
            if (!functionSource.isNullOrBlank()) {
                params["__operit_inline_function_name"] = functionName
                params["__operit_inline_function_source"] = functionSource
            }

            val getExecutionEngineStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            val executionContextKey = resolveToolPkgExecutionContextKey(runtime.packageName, params)
            val executionEngine = packageManager.getToolPkgExecutionEngine(executionContextKey)
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.getExecutionEngine",
                    startTimeMs = getExecutionEngineStartTime,
                    details = "container=${runtime.packageName}, plugin=${normalizedPluginId ?: "none"}, contextKey=$executionContextKey"
                )
            }

            val executeScriptFunctionStartTime = if (shouldLogTiming) messageTimingNow() else 0L
            val executionResult = executionEngine.executeScriptFunction(
                script = script,
                functionName = functionName,
                params = params,
                onIntermediateResult = onIntermediateResult
            )
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.executeScriptFunction",
                    startTimeMs = executeScriptFunctionStartTime,
                    details = "container=${runtime.packageName}, plugin=${normalizedPluginId ?: "none"}, function=$functionName, resultType=${executionResult?.javaClass?.simpleName ?: "null"}"
                )
                logMessageTiming(
                    stage = "toolpkg.runMainHook.total",
                    startTimeMs = totalStartTime,
                    details = "container=${runtime.packageName}, plugin=${normalizedPluginId ?: "none"}, function=$functionName, success=true"
                )
            }
            executionResult
        }.onFailure { error ->
            if (shouldLogTiming) {
                logMessageTiming(
                    stage = "toolpkg.runMainHook.total",
                    startTimeMs = totalStartTime,
                    details = "container=$containerPackageName, plugin=${normalizedPluginId ?: "none"}, function=$functionName, success=false, reason=${error.message ?: error.javaClass.simpleName}"
                )
            }
            val pluginPart = if (normalizedPluginId.isNullOrBlank()) "" else ", plugin=$normalizedPluginId"
            AppLogger.e(
                "PackageManagerToolPkgFacade",
                "runToolPkgMainHook failed: container=$containerPackageName, function=$functionName, event=$event$pluginPart",
                error
            )
        }
    }

    private fun resolveToolPkgExecutionContextKey(
        containerPackageName: String,
        params: Map<String, Any?>
    ): String {
        val explicitContextKey =
            sequenceOf(params["__operit_execution_context_key"])
                .mapNotNull { it?.toString()?.trim() }
                .firstOrNull { it.isNotBlank() }
        if (!explicitContextKey.isNullOrBlank()) {
            return explicitContextKey
        }
        return "toolpkg_main:$containerPackageName"
    }

    fun readToolPkgTextResource(
        packageNameOrSubpackageId: String,
        resourcePath: String,
        preferEnabledContainer: Boolean = true
    ): String? {
        packageManager.ensureInitialized()
        val target = packageNameOrSubpackageId.trim()
        val normalizedPath =
            resourcePath
                .trim()
                .replace('\\', '/')
                .trimStart('/')

        if (target.isBlank() || normalizedPath.isBlank()) {
            return null
        }

        val containerRuntime = packageManager.toolPkgContainersInternal[target]
        if (containerRuntime != null) {
            val enabledSet = packageManager.getEnabledPackageNameSetInternal()
            if (!enabledSet.contains(containerRuntime.packageName)) {
                return null
            }
            return packageManager.readToolPkgResourceBytes(containerRuntime, normalizedPath)
                ?.toString(StandardCharsets.UTF_8)
        }

        val directSubpackageRuntime = packageManager.resolveToolPkgSubpackageRuntimeInternal(target)
        if (directSubpackageRuntime != null) {
            val directContainer = packageManager.toolPkgContainersInternal[directSubpackageRuntime.containerPackageName]
            if (directContainer != null) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                if (!enabledSet.contains(directContainer.packageName)) {
                    return null
                }
                return packageManager.readToolPkgResourceBytes(directContainer, normalizedPath)
                    ?.toString(StandardCharsets.UTF_8)
            }
        }

        val subpackages =
            packageManager.toolPkgSubpackageByPackageNameInternal.values.filter {
                it.subpackageId.equals(target, ignoreCase = true)
            }
        if (subpackages.isEmpty()) {
            return null
        }

        val candidateContainers =
            if (preferEnabledContainer) {
                val enabledSet = packageManager.getEnabledPackageNameSetInternal()
                subpackages
                    .map { it.containerPackageName }
                    .distinct()
                    .filter { enabledSet.contains(it) }
            } else {
                subpackages.map { it.containerPackageName }.distinct()
            }

        candidateContainers.forEach { containerName ->
            val runtime = packageManager.toolPkgContainersInternal[containerName] ?: return@forEach
            val text =
                packageManager.readToolPkgResourceBytes(runtime, normalizedPath)
                    ?.toString(StandardCharsets.UTF_8)
            if (!text.isNullOrEmpty()) {
                return text
            }
        }

        return null
    }
}
