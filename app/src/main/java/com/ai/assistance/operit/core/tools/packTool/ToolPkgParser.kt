package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import android.provider.DocumentsContract
import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.ToolPackage
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hjson.JsonValue

private val TOOLPKG_DIRECTORY_RESOURCE_MIME_TYPES =
    setOf(
        DocumentsContract.Document.MIME_TYPE_DIR.lowercase(),
        "inode/directory",
        "application/x-directory"
    )

internal enum class ToolPkgSourceType {
    ASSET,
    EXTERNAL
}

internal data class ToolPkgResourceRuntime(
    val key: String,
    val path: String,
    val mime: String
)

internal data class ToolPkgUiModuleRuntime(
    val id: String,
    val runtime: String,
    val screen: String,
    val title: LocalizedText
)

internal data class ToolPkgAppLifecycleHookRuntime(
    val id: String,
    val event: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgFunctionHookRuntime(
    val id: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgTagFunctionHookRuntime(
    val id: String,
    val tag: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgSubpackageRuntime(
    val packageName: String,
    val containerPackageName: String,
    val subpackageId: String,
    val displayName: LocalizedText,
    val description: LocalizedText,
    val enabledByDefault: Boolean,
    val toolCount: Int
)

internal data class ToolPkgContainerRuntime(
    val packageName: String,
    val displayName: LocalizedText,
    val description: LocalizedText,
    val version: String,
    val mainEntry: String,
    val sourceType: ToolPkgSourceType,
    val sourcePath: String,
    val subpackages: List<ToolPkgSubpackageRuntime>,
    val resources: List<ToolPkgResourceRuntime>,
    val uiModules: List<ToolPkgUiModuleRuntime>,
    val appLifecycleHooks: List<ToolPkgAppLifecycleHookRuntime>,
    val messageProcessingPlugins: List<ToolPkgFunctionHookRuntime>,
    val xmlRenderPlugins: List<ToolPkgTagFunctionHookRuntime>,
    val inputMenuTogglePlugins: List<ToolPkgFunctionHookRuntime>,
    val toolLifecycleHooks: List<ToolPkgFunctionHookRuntime>,
    val promptInputHooks: List<ToolPkgFunctionHookRuntime>,
    val promptHistoryHooks: List<ToolPkgFunctionHookRuntime>,
    val promptEstimateHistoryHooks: List<ToolPkgFunctionHookRuntime>,
    val systemPromptComposeHooks: List<ToolPkgFunctionHookRuntime>,
    val toolPromptComposeHooks: List<ToolPkgFunctionHookRuntime>,
    val promptFinalizeHooks: List<ToolPkgFunctionHookRuntime>,
    val promptEstimateFinalizeHooks: List<ToolPkgFunctionHookRuntime>
)

internal data class ToolPkgLoadResult(
    val containerPackage: ToolPackage,
    val subpackagePackages: List<ToolPackage>,
    val containerRuntime: ToolPkgContainerRuntime
)

@Serializable
internal data class ToolPkgManifest(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("toolpkg_id") val toolpkgId: String,
    val version: String = "",
    val main: String = "",
    @SerialName("display_name") val displayName: LocalizedText = LocalizedText.of(""),
    val description: LocalizedText = LocalizedText.of(""),
    @SerialName("enabled_by_default") val enabledByDefault: Boolean = true,
    val subpackages: List<ToolPkgManifestSubpackage> = emptyList(),
    val resources: List<ToolPkgManifestResource> = emptyList()
)

@Serializable
internal data class ToolPkgManifestSubpackage(
    val id: String,
    val entry: String
)

@Serializable
internal data class ToolPkgManifestResource(
    val key: String,
    val path: String,
    val mime: String = ""
)

internal data class ToolPkgRegisteredUiModule(
    val id: String,
    val runtime: String,
    val screen: String,
    val title: LocalizedText
)

internal data class ToolPkgRegisteredAppLifecycleHook(
    val id: String,
    val event: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgRegisteredFunctionHook(
    val id: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgRegisteredTagFunctionHook(
    val id: String,
    val tag: String,
    val function: String,
    val functionSource: String? = null
)

internal data class ToolPkgMainRegistration(
    val toolboxUiModules: List<ToolPkgRegisteredUiModule> = emptyList(),
    val appLifecycleHooks: List<ToolPkgRegisteredAppLifecycleHook> = emptyList(),
    val messageProcessingPlugins: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val xmlRenderPlugins: List<ToolPkgRegisteredTagFunctionHook> = emptyList(),
    val inputMenuTogglePlugins: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val toolLifecycleHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptInputHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptHistoryHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptEstimateHistoryHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val systemPromptComposeHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val toolPromptComposeHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptFinalizeHooks: List<ToolPkgRegisteredFunctionHook> = emptyList(),
    val promptEstimateFinalizeHooks: List<ToolPkgRegisteredFunctionHook> = emptyList()
)

internal object ToolPkgArchiveParser {
    fun parseToolPkgFromEntries(
        entries: Map<String, ByteArray>,
        sourceType: ToolPkgSourceType,
        sourcePath: String,
        isBuiltIn: Boolean,
        parseJsPackage: (String, (String, String) -> Unit) -> ToolPackage?,
        parseMainRegistration: (String, String, String) -> ToolPkgMainRegistration?,
        reportPackageLoadError: (String, String) -> Unit
    ): ToolPkgLoadResult {
        val manifestEntryName = findManifestEntry(entries)
            ?: throw IllegalArgumentException("manifest.hjson or manifest.json not found")
        val manifestBytes =
            entries[manifestEntryName]
                ?: throw IllegalArgumentException("Failed to read manifest entry")
        val manifestText = manifestBytes.toString(StandardCharsets.UTF_8)
        val manifest = parseToolPkgManifest(manifestText, manifestEntryName)

        if (manifest.toolpkgId.isBlank()) {
            throw IllegalArgumentException("manifest.toolpkg_id is required")
        }
        val normalizedMainEntry =
            normalizeZipEntryPath(manifest.main)
                ?: throw IllegalArgumentException("manifest.main is required")
        if (!containsZipEntry(entries, normalizedMainEntry)) {
            throw IllegalArgumentException("Cannot find manifest.main entry '${manifest.main}'")
        }
        val mainScriptText =
            findZipEntryContent(entries, normalizedMainEntry)
                ?.toString(StandardCharsets.UTF_8)
                ?: throw IllegalArgumentException("Failed to read manifest.main entry '${manifest.main}'")

        val subpackagePackages = mutableListOf<ToolPackage>()
        val subpackageRuntimes = mutableListOf<ToolPkgSubpackageRuntime>()

        manifest.subpackages.forEach { subpackage ->
            val rawSubpackageId = subpackage.id.trim()
            val subpackageErrorKey =
                if (rawSubpackageId.isNotBlank()) rawSubpackageId else "${manifest.toolpkgId}:unknown_subpackage"

            if (rawSubpackageId.isBlank()) {
                reportPackageLoadError(
                    subpackageErrorKey,
                    "$sourcePath: subpackage.id is required"
                )
                return@forEach
            }
            if (subpackage.entry.isBlank()) {
                reportPackageLoadError(
                    subpackageErrorKey,
                    "$sourcePath: subpackage.entry is required for '$rawSubpackageId'"
                )
                return@forEach
            }

            val normalizedSubpackageId = rawSubpackageId
            val packageName = normalizedSubpackageId

            try {
                val entryBytes =
                    findZipEntryContent(entries, subpackage.entry)
                        ?: throw IllegalArgumentException(
                            "Cannot find subpackage entry '${subpackage.entry}'"
                        )
                val jsContent = entryBytes.toString(StandardCharsets.UTF_8)

                val parsedPackage =
                    parseJsPackage(jsContent) { _, error ->
                        reportPackageLoadError(packageName, "$sourcePath:${subpackage.entry}: $error")
                    }
                        ?: throw IllegalArgumentException(
                            "Failed to parse subpackage script '${subpackage.entry}'"
                        )

                val resolvedDescription = parsedPackage.description

                val resolvedDisplayName =
                    if (hasLocalizedTextContent(parsedPackage.displayName)) {
                        parsedPackage.displayName
                    } else {
                        LocalizedText.of(parsedPackage.name)
                    }

                val normalizedPackage =
                    parsedPackage.copy(
                        name = packageName,
                        isBuiltIn = isBuiltIn
                    )

                subpackagePackages.add(normalizedPackage)
                subpackageRuntimes.add(
                    ToolPkgSubpackageRuntime(
                        packageName = packageName,
                        containerPackageName = manifest.toolpkgId,
                        subpackageId = normalizedSubpackageId,
                        displayName = resolvedDisplayName,
                        description = resolvedDescription,
                        enabledByDefault = normalizedPackage.enabledByDefault,
                        toolCount = normalizedPackage.tools.size
                    )
                )
            } catch (e: Exception) {
                reportPackageLoadError(
                    packageName,
                    "$sourcePath:${subpackage.entry}: ${e.message ?: e.stackTraceToString()}"
                )
            }
        }

        if (manifest.subpackages.isNotEmpty() && subpackagePackages.isEmpty()) {
            throw IllegalArgumentException(
                "No valid subpackages were loaded from toolpkg '${manifest.toolpkgId}'"
            )
        }

        val resources =
            manifest.resources.map { resource ->
                if (resource.key.isBlank()) {
                    throw IllegalArgumentException("resource.key is required")
                }
                if (resource.path.isBlank()) {
                    throw IllegalArgumentException(
                        "resource.path is required for key '${resource.key}'"
                    )
                }
                val normalizedPath =
                    normalizeResourcePath(resource.path)
                        ?: throw IllegalArgumentException("Invalid resource path: ${resource.path}")
                if (isDirectoryResourceMime(resource.mime)) {
                    if (!containsZipEntriesUnderDirectory(entries, normalizedPath)) {
                        throw IllegalArgumentException("Cannot find resource directory '${resource.path}'")
                    }
                } else if (!containsZipEntry(entries, normalizedPath)) {
                    throw IllegalArgumentException("Cannot find resource path '${resource.path}'")
                }
                ToolPkgResourceRuntime(
                    key = resource.key,
                    path = normalizedPath,
                    mime = resource.mime
                )
            }

        val containerDisplayName =
            if (hasLocalizedTextContent(manifest.displayName)) {
                manifest.displayName
            } else {
                LocalizedText.of(manifest.toolpkgId)
            }
        val mainRegistration =
            parseMainRegistration(mainScriptText, manifest.toolpkgId, normalizedMainEntry)
                ?: throw IllegalArgumentException(
                    "Failed to parse main registration from '${manifest.main}'. " +
                        "main script must export registerToolPkg()"
                )

        val uiModules = mutableListOf<ToolPkgUiModuleRuntime>()
        val uiModuleIds = linkedSetOf<String>()
        mainRegistration.toolboxUiModules.forEachIndexed { index, module ->
            val id = module.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOLBOX_UI_MODULE[$index].id is required")
            }
            if (!uiModuleIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate toolbox ui module id: $id")
            }

            val runtimeName = module.runtime.trim().ifBlank { TOOLPKG_RUNTIME_COMPOSE_DSL }
            val normalizedScreenPath =
                normalizeZipEntryPath(module.screen)
                    ?: throw IllegalArgumentException(
                        "$TOOLPKG_REGISTRATION_TOOLBOX_UI_MODULE[$index].screen is invalid: ${module.screen}"
                    )
            if (!containsZipEntry(entries, normalizedScreenPath)) {
                throw IllegalArgumentException(
                    "$TOOLPKG_REGISTRATION_TOOLBOX_UI_MODULE[$index].screen not found: ${module.screen}"
                )
            }

            uiModules.add(
                ToolPkgUiModuleRuntime(
                    id = id,
                    runtime = runtimeName,
                    screen = normalizedScreenPath,
                    title = module.title
                )
            )
        }

        val appLifecycleHooks = mutableListOf<ToolPkgAppLifecycleHookRuntime>()
        val hookIds = linkedSetOf<String>()
        mainRegistration.appLifecycleHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].id is required")
            }
            if (!hookIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate app lifecycle hook id: $id")
            }

            val event = hook.event.trim().lowercase()
            val function = hook.function.trim()
            if (event.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].event is required")
            }
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK[$index].function is required")
            }

            appLifecycleHooks.add(
                ToolPkgAppLifecycleHookRuntime(
                    id = id,
                    event = event,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val messageProcessingPlugins = mutableListOf<ToolPkgFunctionHookRuntime>()
        val messageProcessingIds = linkedSetOf<String>()
        mainRegistration.messageProcessingPlugins.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_MESSAGE_PROCESSING_PLUGIN[$index].id is required")
            }
            if (!messageProcessingIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate message processing plugin id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_MESSAGE_PROCESSING_PLUGIN[$index].function is required")
            }
            messageProcessingPlugins.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val xmlRenderPlugins = mutableListOf<ToolPkgTagFunctionHookRuntime>()
        val xmlRenderIds = linkedSetOf<String>()
        mainRegistration.xmlRenderPlugins.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_XML_RENDER_PLUGIN[$index].id is required")
            }
            if (!xmlRenderIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate xml render plugin id: $id")
            }

            val tag = hook.tag.trim().lowercase()
            val function = hook.function.trim()
            if (tag.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_XML_RENDER_PLUGIN[$index].tag is required")
            }
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_XML_RENDER_PLUGIN[$index].function is required")
            }
            xmlRenderPlugins.add(
                ToolPkgTagFunctionHookRuntime(
                    id = id,
                    tag = tag,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val inputMenuTogglePlugins = mutableListOf<ToolPkgFunctionHookRuntime>()
        val inputMenuToggleIds = linkedSetOf<String>()
        mainRegistration.inputMenuTogglePlugins.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_INPUT_MENU_TOGGLE_PLUGIN[$index].id is required")
            }
            if (!inputMenuToggleIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate input menu toggle plugin id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_INPUT_MENU_TOGGLE_PLUGIN[$index].function is required")
            }
            inputMenuTogglePlugins.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val toolLifecycleHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val toolLifecycleIds = linkedSetOf<String>()
        mainRegistration.toolLifecycleHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOL_LIFECYCLE_HOOK[$index].id is required")
            }
            if (!toolLifecycleIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate tool lifecycle hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOL_LIFECYCLE_HOOK[$index].function is required")
            }
            toolLifecycleHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptInputHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptInputIds = linkedSetOf<String>()
        mainRegistration.promptInputHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_INPUT_HOOK[$index].id is required")
            }
            if (!promptInputIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt input hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_INPUT_HOOK[$index].function is required")
            }
            promptInputHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptHistoryHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptHistoryIds = linkedSetOf<String>()
        mainRegistration.promptHistoryHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_HISTORY_HOOK[$index].id is required")
            }
            if (!promptHistoryIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt history hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_HISTORY_HOOK[$index].function is required")
            }
            promptHistoryHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptEstimateHistoryHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptEstimateHistoryIds = linkedSetOf<String>()
        mainRegistration.promptEstimateHistoryHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_HISTORY_HOOK[$index].id is required")
            }
            if (!promptEstimateHistoryIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt estimate history hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_HISTORY_HOOK[$index].function is required")
            }
            promptEstimateHistoryHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val systemPromptComposeHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val systemPromptComposeIds = linkedSetOf<String>()
        mainRegistration.systemPromptComposeHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_SYSTEM_PROMPT_COMPOSE_HOOK[$index].id is required")
            }
            if (!systemPromptComposeIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate system prompt compose hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_SYSTEM_PROMPT_COMPOSE_HOOK[$index].function is required")
            }
            systemPromptComposeHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val toolPromptComposeHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val toolPromptComposeIds = linkedSetOf<String>()
        mainRegistration.toolPromptComposeHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOL_PROMPT_COMPOSE_HOOK[$index].id is required")
            }
            if (!toolPromptComposeIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate tool prompt compose hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_TOOL_PROMPT_COMPOSE_HOOK[$index].function is required")
            }
            toolPromptComposeHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptFinalizeHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptFinalizeIds = linkedSetOf<String>()
        mainRegistration.promptFinalizeHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_FINALIZE_HOOK[$index].id is required")
            }
            if (!promptFinalizeIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt finalize hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_FINALIZE_HOOK[$index].function is required")
            }
            promptFinalizeHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val promptEstimateFinalizeHooks = mutableListOf<ToolPkgFunctionHookRuntime>()
        val promptEstimateFinalizeIds = linkedSetOf<String>()
        mainRegistration.promptEstimateFinalizeHooks.forEachIndexed { index, hook ->
            val id = hook.id.trim()
            if (id.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_FINALIZE_HOOK[$index].id is required")
            }
            if (!promptEstimateFinalizeIds.add(id.lowercase())) {
                throw IllegalArgumentException("Duplicate prompt estimate finalize hook id: $id")
            }

            val function = hook.function.trim()
            if (function.isBlank()) {
                throw IllegalArgumentException("$TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_FINALIZE_HOOK[$index].function is required")
            }
            promptEstimateFinalizeHooks.add(
                ToolPkgFunctionHookRuntime(
                    id = id,
                    function = function,
                    functionSource = hook.functionSource
                )
            )
        }

        val containerDescription =
            when {
                hasLocalizedTextContent(manifest.description) -> manifest.description
                hasLocalizedTextContent(manifest.displayName) -> manifest.displayName
                else -> LocalizedText.of(manifest.toolpkgId)
            }

        val containerPackage =
            ToolPackage(
                name = manifest.toolpkgId,
                description = containerDescription,
                tools = emptyList(),
                isBuiltIn = isBuiltIn,
                enabledByDefault = manifest.enabledByDefault,
                displayName = containerDisplayName,
                category = "ToolPkg"
            )

        val runtime =
            ToolPkgContainerRuntime(
                packageName = manifest.toolpkgId,
                displayName = containerDisplayName,
                description = containerDescription,
                version = manifest.version,
                mainEntry = normalizedMainEntry,
                sourceType = sourceType,
                sourcePath = sourcePath,
                subpackages = subpackageRuntimes,
                resources = resources,
                uiModules = uiModules,
                appLifecycleHooks = appLifecycleHooks,
                messageProcessingPlugins = messageProcessingPlugins,
                xmlRenderPlugins = xmlRenderPlugins,
                inputMenuTogglePlugins = inputMenuTogglePlugins,
                toolLifecycleHooks = toolLifecycleHooks,
                promptInputHooks = promptInputHooks,
                promptHistoryHooks = promptHistoryHooks,
                promptEstimateHistoryHooks = promptEstimateHistoryHooks,
                systemPromptComposeHooks = systemPromptComposeHooks,
                toolPromptComposeHooks = toolPromptComposeHooks,
                promptFinalizeHooks = promptFinalizeHooks,
                promptEstimateFinalizeHooks = promptEstimateFinalizeHooks
            )

        return ToolPkgLoadResult(
            containerPackage = containerPackage,
            subpackagePackages = subpackagePackages,
            containerRuntime = runtime
        )
    }

    fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                val normalizedName = normalizeZipEntryPath(entry.name)
                if (!entry.isDirectory && normalizedName != null) {
                    entries[normalizedName] = zipInput.readBytes()
                }
                zipInput.closeEntry()
            }
        }
        return entries
    }

    fun normalizeZipEntryPath(rawPath: String): String? {
        val normalized = rawPath.replace('\\', '/').trim().trimStart('/')
        if (normalized.isBlank()) {
            return null
        }
        if (normalized.contains("..")) {
            return null
        }
        return normalized
    }

    fun normalizeResourcePath(rawPath: String): String? {
        val normalized = normalizeZipEntryPath(rawPath) ?: return null
        return normalized.trimEnd('/').ifBlank { null }
    }

    fun isDirectoryResourceMime(mime: String?): Boolean {
        val normalizedMime = mime?.trim()?.lowercase().orEmpty()
        return TOOLPKG_DIRECTORY_RESOURCE_MIME_TYPES.contains(normalizedMime)
    }

    fun findZipEntryContent(entries: Map<String, ByteArray>, rawPath: String): ByteArray? {
        val normalizedPath = normalizeZipEntryPath(rawPath) ?: return null
        entries[normalizedPath]?.let { return it }
        return entries.entries.firstOrNull { it.key.equals(normalizedPath, ignoreCase = true) }?.value
    }

    fun extractZipEntriesFromExternal(zipFilePath: String, destinationDir: File): Boolean {
        val zipFile = File(zipFilePath)
        if (!zipFile.exists()) {
            return false
        }

        ZipFile(zipFile).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) {
                    continue
                }
                val normalizedEntry = normalizeZipEntryPath(entry.name) ?: continue
                val outputFile = File(destinationDir, normalizedEntry)
                val parent = outputFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                archive.getInputStream(entry).use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        return true
    }

    fun extractZipEntriesFromAsset(
        context: Context,
        assetPath: String,
        destinationDir: File
    ): Boolean {
        context.assets.open(assetPath).use { input ->
            ZipInputStream(input.buffered()).use { zipInput ->
                while (true) {
                    val entry = zipInput.nextEntry ?: break
                    if (entry.isDirectory) {
                        zipInput.closeEntry()
                        continue
                    }
                    val normalizedEntry = normalizeZipEntryPath(entry.name)
                    if (normalizedEntry != null) {
                        val outputFile = File(destinationDir, normalizedEntry)
                        val parent = outputFile.parentFile
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs()
                        }
                        outputFile.outputStream().use { output ->
                            zipInput.copyTo(output)
                        }
                    }
                    zipInput.closeEntry()
                }
            }
        }
        return true
    }

    private fun containsZipEntry(entries: Map<String, ByteArray>, normalizedPath: String): Boolean {
        if (entries.containsKey(normalizedPath)) {
            return true
        }
        return entries.keys.any { it.equals(normalizedPath, ignoreCase = true) }
    }

    private fun containsZipEntriesUnderDirectory(
        entries: Map<String, ByteArray>,
        normalizedDirectoryPath: String
    ): Boolean {
        val prefix = normalizedDirectoryPath.trimEnd('/') + "/"
        return entries.keys.any { it.startsWith(prefix, ignoreCase = true) }
    }

    private fun findManifestEntry(entries: Map<String, ByteArray>): String? {
        val exactHjson = entries.keys.firstOrNull { it.equals("manifest.hjson", ignoreCase = true) }
        if (exactHjson != null) return exactHjson

        val exactJson = entries.keys.firstOrNull { it.equals("manifest.json", ignoreCase = true) }
        if (exactJson != null) return exactJson

        val nestedHjson =
            entries.keys.firstOrNull {
                it.substringAfterLast('/').equals("manifest.hjson", ignoreCase = true)
            }
        if (nestedHjson != null) return nestedHjson

        return entries.keys.firstOrNull {
            it.substringAfterLast('/').equals("manifest.json", ignoreCase = true)
        }
    }

    private fun parseToolPkgManifest(content: String, manifestEntryName: String): ToolPkgManifest {
        val manifestJson =
            if (manifestEntryName.endsWith(".hjson", ignoreCase = true)) {
                JsonValue.readHjson(content).toString()
            } else {
                content
            }

        val jsonConfig = Json { ignoreUnknownKeys = true }
        return jsonConfig.decodeFromString<ToolPkgManifest>(manifestJson)
    }

    private fun hasLocalizedTextContent(text: LocalizedText?): Boolean {
        return text?.values?.values?.any { it.isNotBlank() } == true
    }
}
