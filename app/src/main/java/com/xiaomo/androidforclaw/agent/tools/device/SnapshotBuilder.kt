package com.xiaomo.androidforclaw.agent.tools.device

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/browser/(all)
 *
 * AndroidForClaw adaptation: build snapshot from Android accessibility tree.
 * Converts ViewNode list to RefNode list with Playwright-style ref IDs.
 */

import android.graphics.Rect
import android.util.Log
import com.xiaomo.androidforclaw.accessibility.service.ViewNode

object SnapshotBuilder {
    private const val TAG = "SnapshotBuilder"

    /**
     * Build ref nodes from ViewNode list (from AccessibilityProxy.dumpViewTree).
     */
    fun buildFromViewNodes(nodes: List<ViewNode>): List<RefNode> {
        val refNodes = mutableListOf<RefNode>()
        var refCounter = 1

        for (viewNode in nodes) {
            val displayText = viewNode.text?.takeIf { it.isNotBlank() }
                ?: viewNode.contentDesc?.takeIf { it.isNotBlank() }

            val isInteractive = viewNode.clickable || viewNode.focusable || viewNode.scrollable
            val hasText = !displayText.isNullOrBlank()

            if (isInteractive || hasText) {
                val shortClass = viewNode.className?.substringAfterLast('.') ?: "View"
                val role = mapToRole(shortClass, viewNode)
                val ref = "e${refCounter++}"

                refNodes.add(RefNode(
                    ref = ref,
                    role = role,
                    text = displayText?.take(100),
                    bounds = Rect(viewNode.left, viewNode.top, viewNode.right, viewNode.bottom),
                    clickable = viewNode.clickable,
                    editable = viewNode.focusable && shortClass.contains("Edit", ignoreCase = true),
                    scrollable = viewNode.scrollable,
                    focusable = viewNode.focusable,
                    depth = 0,  // ViewNode doesn't carry depth
                    className = shortClass,
                    packageName = viewNode.packageName
                ))
            }
        }

        Log.d(TAG, "Built ${refNodes.size} ref nodes from ${nodes.size} view nodes")
        return refNodes
    }

    /**
     * Map Android class names to Playwright-style roles.
     */
    private fun mapToRole(className: String, node: ViewNode): String {
        return when {
            className.contains("Button", ignoreCase = true) -> "button"
            className.contains("EditText", ignoreCase = true) -> "input"
            className.contains("TextView", ignoreCase = true) -> {
                if (node.clickable) "link" else "text"
            }
            className.contains("ImageView", ignoreCase = true) -> "image"
            className.contains("ImageButton", ignoreCase = true) -> "button"
            className.contains("CheckBox", ignoreCase = true) -> "checkbox"
            className.contains("RadioButton", ignoreCase = true) -> "radio"
            className.contains("Switch", ignoreCase = true) -> "switch"
            className.contains("SeekBar", ignoreCase = true) -> "slider"
            className.contains("Spinner", ignoreCase = true) -> "select"
            className.contains("RecyclerView", ignoreCase = true) -> "list"
            className.contains("ListView", ignoreCase = true) -> "list"
            className.contains("ScrollView", ignoreCase = true) -> "scrollable"
            className.contains("WebView", ignoreCase = true) -> "webview"
            node.scrollable -> "scrollable"
            node.clickable -> "button"
            node.focusable -> "input"
            else -> "element"
        }
    }
}
