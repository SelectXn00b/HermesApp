package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslRenderResult
import com.ai.assistance.operit.ui.common.composedsl.RenderToolPkgComposeDslNode
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import android.content.Context

sealed class XmlRenderResult {
    data class ComposableRender(
        val render: @Composable (Modifier, Color, Stream<String>?) -> Unit
    ) : XmlRenderResult()

    data class Text(val text: String) : XmlRenderResult()

    data class ComposeDslScreen(
        val containerPackageName: String,
        val screenPath: String,
        val state: Map<String, Any?> = emptyMap(),
        val memo: Map<String, Any?> = emptyMap(),
        val moduleSpec: Map<String, Any?>? = null
    ) : XmlRenderResult()
}

interface XmlRenderPlugin {
    val id: String

    fun supports(tagName: String): Boolean

    suspend fun resolve(
        context: Context,
        xmlContent: String,
        tagName: String,
        textColor: Color,
        xmlStream: Stream<String>?
    ): XmlRenderResult?
}

private fun buildXmlRenderComposeDslExecutionContextKey(
    containerPackageName: String,
    screenPath: String
): String =
    "toolpkg_xml_render:${containerPackageName.trim().ifBlank { "default" }}:${screenPath.trim().ifBlank { "default" }}"

object XmlRenderPluginRegistry {
    private const val TAG = "XmlRenderPluginRegistry"
    private val plugins = CopyOnWriteArrayList<XmlRenderPlugin>()
    private val changeVersionMutable = MutableStateFlow(0)
    val changeVersion: StateFlow<Int> = changeVersionMutable.asStateFlow()

    @Synchronized
    fun register(plugin: XmlRenderPlugin) {
        unregister(plugin.id)
        plugins.add(plugin)
        notifyChanged()
    }

    @Synchronized
    fun unregister(pluginId: String) {
        val changed = plugins.removeAll { it.id == pluginId }
        if (changed) {
            notifyChanged()
        }
    }

    fun notifyChanged() {
        changeVersionMutable.update { current -> current + 1 }
    }

    @Composable
    fun RenderIfMatched(
        xmlContent: String,
        tagName: String,
        modifier: Modifier,
        textColor: Color,
        xmlStream: Stream<String>?,
        renderInstanceKey: Any? = null
    ): Boolean {
        val registryVersion = changeVersion.collectAsState().value
        val plugin = plugins.firstOrNull { it.supports(tagName) } ?: return false

        val context = LocalContext.current
        var result by remember(renderInstanceKey, tagName, plugin.id, registryVersion) {
            mutableStateOf<XmlRenderResult?>(null)
        }
        var errorMessage by remember(renderInstanceKey, tagName, plugin.id, registryVersion) {
            mutableStateOf<String?>(null)
        }
        var resolutionFinished by remember(renderInstanceKey, tagName, plugin.id, registryVersion) {
            mutableStateOf(false)
        }

        LaunchedEffect(renderInstanceKey, xmlContent, tagName, plugin.id, registryVersion) {
            if (result == null && errorMessage.isNullOrBlank()) {
                resolutionFinished = false
            }
            runCatching {
                plugin.resolve(
                    context = context,
                    xmlContent = xmlContent,
                    tagName = tagName,
                    textColor = textColor,
                    xmlStream = xmlStream
                )
            }.onSuccess { resolved ->
                result = resolved
                errorMessage = null
            }.onFailure { error ->
                result = null
                errorMessage = error.message
                AppLogger.e(TAG, "Xml render plugin failed: ${plugin.id}", error)
            }
            resolutionFinished = true
        }

        return when (val resolved = result) {
            is XmlRenderResult.ComposableRender -> {
                resolved.render(modifier, textColor, xmlStream)
                true
            }
            is XmlRenderResult.Text -> {
                Text(
                    text = resolved.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    modifier = modifier
                )
                true
            }
            is XmlRenderResult.ComposeDslScreen -> {
                RenderComposeDslScreen(
                    result = resolved,
                    modifier = modifier,
                    xmlStream = xmlStream,
                    renderInstanceKey = renderInstanceKey
                )
                true
            }
            null -> {
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = textColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = modifier
                    )
                    true
                } else if (!resolutionFinished) {
                    Box(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    @Composable
    private fun RenderComposeDslScreen(
        result: XmlRenderResult.ComposeDslScreen,
        modifier: Modifier,
        xmlStream: Stream<String>?,
        renderInstanceKey: Any?
    ) {
        val context = LocalContext.current
        val packageManager = remember(result.containerPackageName) {
            PackageManager.getInstance(context, AIToolHandler.getInstance(context))
        }
        val executionContextKey = remember(result.containerPackageName, result.screenPath) {
            buildXmlRenderComposeDslExecutionContextKey(
                containerPackageName = result.containerPackageName,
                screenPath = result.screenPath
            )
        }
        val jsEngine = remember(packageManager, executionContextKey) {
            packageManager.getToolPkgExecutionEngine(executionContextKey)
        }

        var renderResult by remember(renderInstanceKey, result.containerPackageName, result.screenPath) {
            mutableStateOf<ToolPkgComposeDslRenderResult?>(null)
        }
        var errorMessage by remember(renderInstanceKey, result.containerPackageName, result.screenPath) {
            mutableStateOf<String?>(null)
        }
        val initialXmlContent = remember(result.state) {
            result.state["xmlContent"]?.toString().orEmpty()
        }
        var liveXmlContent by remember(
            renderInstanceKey,
            result.containerPackageName,
            result.screenPath,
            initialXmlContent
        ) {
            mutableStateOf(initialXmlContent)
        }

        LaunchedEffect(renderInstanceKey, result.containerPackageName, result.screenPath, xmlStream, initialXmlContent) {
            if (xmlStream == null) {
                liveXmlContent = initialXmlContent
                return@LaunchedEffect
            }

            val builder = StringBuilder()
            xmlStream.collect { chunk ->
                builder.append(chunk)
                val nextContent = builder.toString()
                if (
                    nextContent.isNotEmpty() &&
                    nextContent.length >= liveXmlContent.length &&
                    nextContent != liveXmlContent
                ) {
                    liveXmlContent = nextContent
                }
            }
        }

        fun buildModuleSpec(screenPath: String): Map<String, Any?> {
            val provided = result.moduleSpec
            if (provided != null && provided.isNotEmpty()) {
                return provided
            }
            return mapOf(
                "id" to "xml_render",
                "runtime" to "compose_dsl",
                "screen" to screenPath,
                "title" to screenPath,
                "toolPkgId" to result.containerPackageName
            )
        }

        fun buildActionRuntimeOptions(screenPath: String): Map<String, Any?> =
            mapOf(
                "packageName" to result.containerPackageName,
                "containerPackageName" to result.containerPackageName,
                "toolPkgId" to result.containerPackageName,
                "__operit_ui_package_name" to result.containerPackageName,
                "__operit_ui_toolpkg_id" to result.containerPackageName,
                "uiModuleId" to "xml_render",
                "__operit_ui_module_id" to "xml_render",
                "__operit_script_screen" to screenPath,
                "moduleSpec" to buildModuleSpec(screenPath)
            )

        fun dispatchAction(actionId: String, payload: Any?) {
            val normalizedActionId = actionId.trim()
            if (normalizedActionId.isBlank()) {
                return
            }
            val screenPath = result.screenPath.trim()
            jsEngine.dispatchComposeDslActionAsync(
                actionId = normalizedActionId,
                payload = payload,
                runtimeOptions = buildActionRuntimeOptions(screenPath),
                onIntermediateResult = { intermediateResult ->
                    val parsed = ToolPkgComposeDslParser.parseRenderResult(intermediateResult)
                    if (parsed != null) {
                        renderResult = parsed
                        errorMessage = null
                    }
                },
                onComplete = {},
                onError = { error ->
                    errorMessage = "compose_dsl runtime error: $error"
                    AppLogger.e(TAG, "compose_dsl action failed: $error")
                }
            )
        }

        LaunchedEffect(renderInstanceKey, result.containerPackageName, result.screenPath, liveXmlContent, result.state, result.memo) {
            errorMessage = null
            val screenPath = result.screenPath.trim()
            if (screenPath.isBlank()) {
                errorMessage = "compose_dsl screen path is blank"
                return@LaunchedEffect
            }
            val script =
                withContext(Dispatchers.IO) {
                    packageManager.readToolPkgTextResource(
                        packageNameOrSubpackageId = result.containerPackageName,
                        resourcePath = screenPath
                    )
                }
            if (script.isNullOrBlank()) {
                errorMessage = "compose_dsl screen not found: ${result.containerPackageName}:$screenPath"
                return@LaunchedEffect
            }

            val effectiveState = linkedMapOf<String, Any?>().apply {
                putAll(result.state)
                if (liveXmlContent.isNotEmpty()) {
                    put("xmlContent", liveXmlContent)
                }
            }
            val mergedState = linkedMapOf<String, Any?>().apply {
                putAll(renderResult?.state ?: emptyMap())
                putAll(effectiveState)
            }
            val mergedMemo = linkedMapOf<String, Any?>().apply {
                putAll(renderResult?.memo ?: emptyMap())
                putAll(result.memo)
            }

            val rawResult =
                withContext(Dispatchers.IO) {
                    jsEngine.executeComposeDslScript(
                        script = script,
                        runtimeOptions =
                            mapOf(
                                "packageName" to result.containerPackageName,
                                "toolPkgId" to result.containerPackageName,
                                "uiModuleId" to "xml_render",
                                "__operit_script_screen" to screenPath,
                                "moduleSpec" to buildModuleSpec(screenPath),
                                "state" to mergedState,
                                "memo" to mergedMemo
                            )
                    )
                }

            val parsed = ToolPkgComposeDslParser.parseRenderResult(rawResult)
            if (parsed == null) {
                val rawText = rawResult?.toString()?.trim().orEmpty()
                errorMessage =
                    if (rawText.isNotBlank()) "Invalid compose_dsl result: $rawText" else "Invalid compose_dsl result"
                renderResult = null
            } else {
                renderResult = parsed
                errorMessage = null
                val onLoadActionId = ToolPkgComposeDslParser.extractActionId(parsed.tree.props["onLoad"])
                if (!onLoadActionId.isNullOrBlank()) {
                    dispatchAction(onLoadActionId, null)
                }
            }
        }

        Box(modifier = modifier) {
            when {
                renderResult?.tree != null -> {
                    RenderToolPkgComposeDslNode(
                        node = renderResult!!.tree,
                        modifier = Modifier.align(Alignment.TopStart),
                        onAction = ::dispatchAction
                    )
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
