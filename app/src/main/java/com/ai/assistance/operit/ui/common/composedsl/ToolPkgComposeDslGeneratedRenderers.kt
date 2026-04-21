package com.ai.assistance.operit.ui.common.composedsl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser

/**
 * AUTO-GENERATED from Compose Material3/Foundation component bindings.
 * Do not edit manually. Regenerate via tools/compose_dsl/generate_compose_dsl_artifacts.py.
 */
@Composable
internal fun renderNodeChildren(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    node.children.forEachIndexed { index, child ->
        val childPath = "$nodePath/$index"
        renderComposeDslNode(
            node = child,
            onAction = onAction,
            nodePath = childPath
        )
    }
}

@Composable
internal fun ColumnScope.renderWeightedNodeChildren(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    node.children.forEachIndexed { index, child ->
        val childPath = "$nodePath/$index"
        val childWeight = child.props.floatOrNull("weight")
        if (childWeight != null) {
            Box(modifier = Modifier.weight(childWeight)) {
                renderComposeDslNode(
                    node = child,
                    onAction = onAction,
                    nodePath = childPath
                )
            }
        } else {
            renderComposeDslNode(
                node = child,
                onAction = onAction,
                nodePath = childPath
            )
        }
    }
}

private fun ToolPkgComposeDslNode.autoScrollSignature(): Int {
    var result = type.hashCode()
    result = 31 * result + (props["key"]?.hashCode() ?: 0)
    result = 31 * result + (props["text"]?.hashCode() ?: 0)
    result = 31 * result + (props["value"]?.hashCode() ?: 0)
    result = 31 * result + children.size
    children.forEach { child ->
        result = 31 * result + child.autoScrollSignature()
    }
    return result
}

@Composable
internal fun RowScope.renderWeightedNodeChildren(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    node.children.forEachIndexed { index, child ->
        val childPath = "$nodePath/$index"
        val childWeight = child.props.floatOrNull("weight")
        if (childWeight != null) {
            Box(modifier = Modifier.weight(childWeight)) {
                renderComposeDslNode(
                    node = child,
                    onAction = onAction,
                    nodePath = childPath
                )
            }
        } else {
            renderComposeDslNode(
                node = child,
                onAction = onAction,
                nodePath = childPath
            )
        }
    }
}

@Composable
internal fun renderColumnNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val spacing = props.dp("spacing")
    Column(
        modifier = applyCommonModifier(Modifier, props),
        horizontalAlignment = props.horizontalAlignment("horizontalAlignment"),
        verticalArrangement = props.verticalArrangement("verticalArrangement", spacing)
    ) {
        renderWeightedNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderRowNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val spacing = props.dp("spacing")
    val onClick = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    Row(
        modifier = applyCommonModifier(Modifier, props).let { modifier ->
            if (!onClick.isNullOrBlank()) {
                modifier.clickable { onAction(onClick, null) }
            } else {
                modifier
            }
        },
        horizontalArrangement = props.horizontalArrangement("horizontalArrangement", spacing),
        verticalAlignment = props.verticalAlignment("verticalAlignment")
    ) {
        renderWeightedNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderBoxNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    Box(
        modifier = applyCommonModifier(Modifier, props),
        contentAlignment = props.boxAlignment("contentAlignment")
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderSpacerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    Spacer(
        modifier =
            Modifier
                .width(props.dp("width"))
                .height(props.dp("height"))
    )
}

@Composable
internal fun renderLazyColumnNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val spacing = props.dp("spacing")
    val reverseLayout = props.bool("reverseLayout", false)
    val autoScrollToEnd = props.bool("autoScrollToEnd", false)
    val listState = rememberLazyListState()
    val autoScrollSignature =
        if (!autoScrollToEnd) {
            0
        } else {
            node.children.fold(1) { acc, child -> 31 * acc + child.autoScrollSignature() }
        }

    LaunchedEffect(nodePath, autoScrollToEnd, reverseLayout, autoScrollSignature) {
        if (autoScrollToEnd && node.children.isNotEmpty()) {
            listState.scrollToItem(if (reverseLayout) 0 else node.children.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = applyCommonModifier(Modifier.fillMaxSize(), props),
        horizontalAlignment = props.horizontalAlignment("horizontalAlignment"),
        reverseLayout = reverseLayout,
        verticalArrangement = props.verticalArrangement("verticalArrangement", spacing),
        contentPadding = PaddingValues(0.dp)
    ) {
        itemsIndexed(node.children) { index, child ->
            renderComposeDslNode(
                node = child,
                onAction = onAction,
                nodePath = "$nodePath/$index"
            )
        }
    }
}

@Composable
internal fun renderLazyRowNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val spacing = props.dp("spacing")
    androidx.compose.foundation.lazy.LazyRow(
        modifier = applyCommonModifier(Modifier, props),
        horizontalArrangement = props.horizontalArrangement("horizontalArrangement", spacing),
        verticalAlignment = props.verticalAlignment("verticalAlignment")
    ) {
        itemsIndexed(node.children) { index, child ->
            renderComposeDslNode(
                node = child,
                onAction = onAction,
                nodePath = "$nodePath/$index"
            )
        }
    }
}

@Composable
internal fun renderTextNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val textStyle = props.textStyle("style")
    val textColor = props.colorOrNull("color")
    val fontWeight = props.fontWeightOrNull("fontWeight")
    Text(
        text = props.string("text"),
        style = if (fontWeight != null) textStyle.copy(fontWeight = fontWeight) else textStyle,
        color = textColor ?: Color.Unspecified,
        maxLines = props.int("maxLines", Int.MAX_VALUE),
        overflow = TextOverflow.Ellipsis,
        modifier = applyCommonModifier(Modifier, props)
    )
}

@Composable
internal fun renderTextFieldNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val actionId = ToolPkgComposeDslParser.extractActionId(props["onValueChange"])
    val label = props.stringOrNull("label")
    val placeholder = props.stringOrNull("placeholder")
    val externalValue = props.string("value")
    val isPassword = props.bool("isPassword", false)
    val hasStyle = props["style"] != null

    var textFieldValue by remember(nodePath) {
        mutableStateOf(
            TextFieldValue(
                text = externalValue,
                selection = TextRange(externalValue.length)
            )
        )
    }
    LaunchedEffect(nodePath, externalValue) {
        if (externalValue != textFieldValue.text) {
            val start = textFieldValue.selection.start.coerceIn(0, externalValue.length)
            val end = textFieldValue.selection.end.coerceIn(0, externalValue.length)
            textFieldValue =
                TextFieldValue(
                    text = externalValue,
                    selection = TextRange(start, end)
                )
        }
    }

    if (hasStyle) {
        val styleMap = props["style"] as? Map<*, *>
        val fontSize = (styleMap?.get("fontSize") as? Number)?.toFloat() ?: 14f
        val fontWeight =
            styleMap?.get("fontWeight")?.toString()?.let { token ->
                mapOf<String, Any?>("fontWeight" to token).fontWeightOrNull("fontWeight")
            } ?: FontWeight.SemiBold
        val color = styleMap?.get("color")?.toString()?.let { resolveColorToken(it) }
            ?: MaterialTheme.colorScheme.primary

        androidx.compose.foundation.text.BasicTextField(
            value = textFieldValue,
            onValueChange = { nextValue ->
                if (!actionId.isNullOrBlank()) {
                    textFieldValue = nextValue
                    onAction(actionId, nextValue.text)
                }
            },
            singleLine = props.bool("singleLine", false),
            visualTransformation = if (isPassword) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = color,
                fontSize = fontSize.sp,
                fontWeight = fontWeight
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (textFieldValue.text.isEmpty() && !placeholder.isNullOrBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            },
            modifier = applyCommonModifier(Modifier.fillMaxWidth(), props)
        )
    } else {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { nextValue ->
                if (!actionId.isNullOrBlank()) {
                    textFieldValue = nextValue
                    onAction(actionId, nextValue.text)
                }
            },
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = props.bool("singleLine", false),
            minLines = props.int("minLines", 1),
            visualTransformation = if (isPassword) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            modifier = applyCommonModifier(Modifier.fillMaxWidth(), props)
        )
    }
}

@Composable
internal fun renderSwitchNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val actionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    Switch(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!actionId.isNullOrBlank()) {
                onAction(actionId, checked)
            }
        },
        enabled = !actionId.isNullOrBlank() && props.bool("enabled", true),
        modifier = applyCommonModifier(Modifier, props)
    )
}

@Composable
internal fun renderCheckboxNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val actionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    Checkbox(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!actionId.isNullOrBlank()) {
                onAction(actionId, checked)
            }
        },
        enabled = !actionId.isNullOrBlank() && props.bool("enabled", true),
        modifier = applyCommonModifier(Modifier, props)
    )
}

@Composable
internal fun renderButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val actionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    val hasChildren = node.children.isNotEmpty()
    Button(
        onClick = {
            if (!actionId.isNullOrBlank()) {
                onAction(actionId, null)
            }
        },
        enabled = !actionId.isNullOrBlank() && props.bool("enabled", true),
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.shape
    ) {
        if (hasChildren) {
            renderNodeChildren(node, onAction, nodePath)
        } else {
            Text(props.string("text", "Button"))
        }
    }
}

@Composable
internal fun renderIconButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val actionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    val hasChildren = node.children.isNotEmpty()
    IconButton(
        onClick = {
            if (!actionId.isNullOrBlank()) {
                onAction(actionId, null)
            }
        },
        enabled = !actionId.isNullOrBlank() && props.bool("enabled", true),
        modifier = applyCommonModifier(Modifier, props)
    ) {
        if (hasChildren) {
            renderNodeChildren(node, onAction, nodePath)
        } else {
            val iconName = props.string("icon", props.string("name", "info"))
            Icon(
                imageVector = iconFromName(iconName),
                contentDescription = null
            )
        }
    }
}

@Composable
internal fun renderCardNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val containerColor = props.colorOrNull("containerColor")
    val containerAlpha = props.floatOrNull("containerAlpha")
    val alpha = props.floatOrNull("alpha")
    val contentColor = props.colorOrNull("contentColor")
    val contentAlpha = props.floatOrNull("contentAlpha")
    val spacing = props.dp("spacing")
    val finalContainerColor = containerColor?.let { color ->
        when {
            containerAlpha != null -> color.copy(alpha = containerAlpha)
            alpha != null -> color.copy(alpha = alpha)
            else -> color
        }
    }
    val finalContentColor = contentColor?.let { color ->
        if (contentAlpha != null) color.copy(alpha = contentAlpha) else color
    }
    val cardColors =
        when {
            finalContainerColor != null && finalContentColor != null ->
                CardDefaults.cardColors(
                    containerColor = finalContainerColor,
                    contentColor = finalContentColor
                )
            finalContainerColor != null ->
                CardDefaults.cardColors(containerColor = finalContainerColor)
            finalContentColor != null ->
                CardDefaults.cardColors(contentColor = finalContentColor)
            else -> CardDefaults.cardColors()
        }
    Card(
        colors = cardColors,
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: CardDefaults.shape,
        border = props.borderOrNull(),
        elevation = CardDefaults.cardElevation(defaultElevation = props.dp("elevation", 1.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            renderNodeChildren(node, onAction, nodePath)
        }
    }
}

@Composable
internal fun renderMaterialThemeNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.MaterialTheme(
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderSurfaceNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val containerColor = props.colorOrNull("containerColor")
    val contentColor = props.colorOrNull("contentColor")
    val alpha = props.floatOrNull("alpha") ?: 1f
    val spacing = props.dp("spacing")
    androidx.compose.material3.Surface(
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        color = containerColor?.copy(alpha = alpha) ?: Color.Transparent,
        contentColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            renderNodeChildren(node, onAction, nodePath)
        }
    }
}

@Composable
internal fun renderIconNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val iconName = props.string("name", props.string("icon", "info"))
    val tint = props.colorOrNull("tint") ?: MaterialTheme.colorScheme.onSurfaceVariant
    val size = props.floatOrNull("size")
    Icon(
        imageVector = iconFromName(iconName),
        contentDescription = null,
        tint = tint,
        modifier = if (size != null) {
            applyCommonModifier(Modifier, props).width(size.dp).height(size.dp)
        } else {
            applyCommonModifier(Modifier, props)
        }
    )
}

@Composable
internal fun renderLinearProgressIndicatorNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val progress = props.floatOrNull("progress")
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = applyCommonModifier(Modifier.fillMaxWidth(), props)
        )
    } else {
        LinearProgressIndicator(
            modifier = applyCommonModifier(Modifier.fillMaxWidth(), props)
        )
    }
}

@Composable
internal fun renderCircularProgressIndicatorNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val strokeWidth = props.floatOrNull("strokeWidth")
    val color = props.colorOrNull("color")
    CircularProgressIndicator(
        modifier = applyCommonModifier(Modifier, props),
        strokeWidth = if (strokeWidth != null) strokeWidth.dp else 4.dp,
        color = color ?: MaterialTheme.colorScheme.primary
    )
}

@Composable
internal fun renderSnackbarHostNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    Spacer(modifier = applyCommonModifier(Modifier, node.props))
}

@Composable
internal fun renderBadgeNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.Badge(
        modifier = applyCommonModifier(Modifier, props),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderDismissibleDrawerSheetNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.DismissibleDrawerSheet(
        modifier = applyCommonModifier(Modifier, props),
        drawerContainerColor = props.colorOrNull("drawerContainerColor") ?: Color.Unspecified,
        drawerContentColor = props.colorOrNull("drawerContentColor") ?: Color.Unspecified,
        drawerTonalElevation = props.dp("drawerTonalElevation")
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderDividerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.Divider(
        modifier = applyCommonModifier(Modifier, props),
        thickness = props.dp("thickness"),
        color = props.colorOrNull("color") ?: Color.Unspecified
    )
}

@Composable
internal fun renderElevatedButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.ElevatedButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderElevatedCardNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.ElevatedCard(
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderExtendedFloatingActionButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderFilledIconButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FilledIconButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        if (node.children.isNotEmpty()) {
            renderNodeChildren(node, onAction, nodePath)
        } else {
            val iconName = props.string("icon", props.string("name", "info"))
            Icon(
                imageVector = iconFromName(iconName),
                contentDescription = null
            )
        }
    }
}

@Composable
internal fun renderFilledIconToggleButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onCheckedChangeActionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    androidx.compose.material3.FilledIconToggleButton(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!onCheckedChangeActionId.isNullOrBlank()) {
                onAction(onCheckedChangeActionId, checked)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderFilledTonalButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FilledTonalButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderFilledTonalIconButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FilledTonalIconButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        if (node.children.isNotEmpty()) {
            renderNodeChildren(node, onAction, nodePath)
        } else {
            val iconName = props.string("icon", props.string("name", "info"))
            Icon(
                imageVector = iconFromName(iconName),
                contentDescription = null
            )
        }
    }
}

@Composable
internal fun renderFilledTonalIconToggleButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onCheckedChangeActionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    androidx.compose.material3.FilledTonalIconToggleButton(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!onCheckedChangeActionId.isNullOrBlank()) {
                onAction(onCheckedChangeActionId, checked)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderFloatingActionButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.FloatingActionButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderHorizontalDividerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.HorizontalDivider(
        modifier = applyCommonModifier(Modifier, props),
        thickness = props.dp("thickness"),
        color = props.colorOrNull("color") ?: Color.Unspecified
    )
}

@Composable
internal fun renderIconToggleButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onCheckedChangeActionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    androidx.compose.material3.IconToggleButton(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!onCheckedChangeActionId.isNullOrBlank()) {
                onAction(onCheckedChangeActionId, checked)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true)
    ) {
        if (node.children.isNotEmpty()) {
            renderNodeChildren(node, onAction, nodePath)
        } else {
            val iconName = props.string("icon", props.string("name", "info"))
            Icon(
                imageVector = iconFromName(iconName),
                contentDescription = null
            )
        }
    }
}

@Composable
internal fun renderLargeFloatingActionButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.LargeFloatingActionButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderModalDrawerSheetNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.ModalDrawerSheet(
        modifier = applyCommonModifier(Modifier, props),
        drawerContainerColor = props.colorOrNull("drawerContainerColor") ?: Color.Unspecified,
        drawerContentColor = props.colorOrNull("drawerContentColor") ?: Color.Unspecified,
        drawerTonalElevation = props.dp("drawerTonalElevation")
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderModalWideNavigationRailNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val spacing = props.dp("spacing")
    androidx.compose.material3.ModalWideNavigationRail(
        modifier = applyCommonModifier(Modifier, props),
        hideOnCollapse = props.bool("hideOnCollapse", false),
        expandedHeaderTopPadding = props.dp("expandedHeaderTopPadding"),
        arrangement = props.verticalArrangement("verticalArrangement", spacing)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderNavigationBarNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.NavigationBar(
        modifier = applyCommonModifier(Modifier, props),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        tonalElevation = props.dp("tonalElevation")
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderNavigationRailNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.NavigationRail(
        modifier = applyCommonModifier(Modifier, props),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderOutlinedButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.OutlinedButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderOutlinedCardNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.OutlinedCard(
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderOutlinedIconButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.OutlinedIconButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        if (node.children.isNotEmpty()) {
            renderNodeChildren(node, onAction, nodePath)
        } else {
            val iconName = props.string("icon", props.string("name", "info"))
            Icon(
                imageVector = iconFromName(iconName),
                contentDescription = null
            )
        }
    }
}

@Composable
internal fun renderOutlinedIconToggleButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onCheckedChangeActionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
    androidx.compose.material3.OutlinedIconToggleButton(
        checked = props.bool("checked", false),
        onCheckedChange = { checked ->
            if (!onCheckedChangeActionId.isNullOrBlank()) {
                onAction(onCheckedChangeActionId, checked)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderPermanentDrawerSheetNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.PermanentDrawerSheet(
        modifier = applyCommonModifier(Modifier, props),
        drawerContainerColor = props.colorOrNull("drawerContainerColor") ?: Color.Unspecified,
        drawerContentColor = props.colorOrNull("drawerContentColor") ?: Color.Unspecified,
        drawerTonalElevation = props.dp("drawerTonalElevation")
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderProvideTextStyleNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.ProvideTextStyle(
        value = props.textStyle("style")
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderScaffoldNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.Scaffold(
        modifier = applyCommonModifier(Modifier, props),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderShortNavigationBarNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.ShortNavigationBar(
        modifier = applyCommonModifier(Modifier, props),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderSmallFloatingActionButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.SmallFloatingActionButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderSnackbarNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.Snackbar(
        modifier = applyCommonModifier(Modifier, props),
        actionOnNewLine = props.bool("actionOnNewLine", false),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        containerColor = props.colorOrNull("containerColor") ?: Color.Unspecified,
        contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
        actionContentColor = props.colorOrNull("actionContentColor") ?: Color.Unspecified,
        dismissActionContentColor = props.colorOrNull("dismissActionContentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderTabNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.Tab(
        selected = props.bool("selected", false),
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        selectedContentColor = props.colorOrNull("selectedContentColor") ?: Color.Unspecified,
        unselectedContentColor = props.colorOrNull("unselectedContentColor") ?: Color.Unspecified
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderTextButtonNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val onClickActionId = ToolPkgComposeDslParser.extractActionId(props["onClick"])
    androidx.compose.material3.TextButton(
        onClick = {
            if (!onClickActionId.isNullOrBlank()) {
                onAction(onClickActionId, null)
            }
        },
        modifier = applyCommonModifier(Modifier, props),
        enabled = props.bool("enabled", true),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderVerticalDividerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.VerticalDivider(
        modifier = applyCommonModifier(Modifier, props),
        thickness = props.dp("thickness"),
        color = props.colorOrNull("color") ?: Color.Unspecified
    )
}

@Composable
internal fun renderVerticalDragHandleNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.material3.VerticalDragHandle(
        modifier = applyCommonModifier(Modifier, props)
    )
}

@Composable
internal fun renderWideNavigationRailNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    val spacing = props.dp("spacing")
    androidx.compose.material3.WideNavigationRail(
        modifier = applyCommonModifier(Modifier, props),
        shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        arrangement = props.verticalArrangement("verticalArrangement", spacing)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderBoxWithConstraintsNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = applyCommonModifier(Modifier, props),
        propagateMinConstraints = props.bool("propagateMinConstraints", false)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderBasicTextNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.foundation.text.BasicText(
        text = props.string("text"),
        modifier = applyCommonModifier(Modifier, props),
        style = props.textStyle("style"),
        softWrap = props.bool("softWrap", false),
        maxLines = props.int("maxLines", 0)
    )
}

@Composable
internal fun renderDisableSelectionNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.foundation.text.selection.DisableSelection(
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}

@Composable
internal fun renderImageNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.foundation.Image(
        imageVector = iconFromName(props.string("name", props.string("icon", "info"))),
        contentDescription = props.stringOrNull("contentDescription"),
        modifier = applyCommonModifier(Modifier, props),
        alpha = (props.floatOrNull("alpha") ?: 0f)
    )
}

@Composable
internal fun renderSelectionContainerNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val props = node.props
    androidx.compose.foundation.text.selection.SelectionContainer(
        modifier = applyCommonModifier(Modifier, props)
    ) {
        renderNodeChildren(node, onAction, nodePath)
    }
}
