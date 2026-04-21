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

// __GENERATED_COMPONENT_RENDERERS__
