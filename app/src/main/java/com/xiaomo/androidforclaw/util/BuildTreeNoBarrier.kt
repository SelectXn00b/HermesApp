/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.xiaomo.androidforclaw.accessibility.service.ViewNode

object BuildTreeNoBarrier {
    /**
     * Multi-way tree node definition
     */
    private data class TreeNode(
        val viewNode: ViewNode,
        val children: MutableList<TreeNode> = mutableListOf()
    )

    /**
     * Get node related attributes: coordinates, classname, resource id, text, content description
     */
    private fun getNodeKey(nodeInfo: AccessibilityNodeInfo?): String? {
        if (nodeInfo == null) return null
        return try {
            val rect = Rect()
            nodeInfo.getBoundsInScreen(rect)
            "${rect.left},${rect.top},${rect.right},${rect.bottom}|" +
                    "${nodeInfo.className ?: ""}|" +
                    "${nodeInfo.viewIdResourceName ?: ""}|" +
                    "${nodeInfo.text ?: ""}|" +
                    "${nodeInfo.contentDescription ?: ""}"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract node type (e.g. button, textView)
     */
    private fun getTreeDisplayType(viewNode: ViewNode): String {
        return viewNode.className?.substringAfterLast('.') ?: "View"
    }

    /**
     * Determine if it's a purely decorative image
     * Characteristics of decorative images:
     * 1. Is ImageView type
     * 2. Not clickable (clickable=false)
     * 3. No text (text is empty)
     * 4. No contentDesc (or empty)
     * 5. May be background image, divider line, etc decorative elements
     */
    private fun isDecorativeImage(node: ViewNode): Boolean {
        val className = node.className?.lowercase() ?: return false

        // Must be ImageView related type
        val isImageView = className.contains("imageview") ||
                         className.contains("imagebutton")
        if (!isImageView) return false

        // Not clickable
        if (node.clickable) return false

        // No text
        if (!node.text.isNullOrEmpty()) return false

        // No contentDesc (or empty string)
        if (!node.contentDesc.isNullOrEmpty()) return false

        return true
    }

    /**
     * Determine if it's a divider line or meaningless line
     * Characteristics of divider lines:
     * 1. Not clickable (clickable=false)
     * 2. No text (text is empty)
     * 3. No contentDesc (or empty)
     * 4. Small dimensions (height or width very small, usually 1-5 pixels)
     * 5. May be View, ViewGroup, etc types
     */
    private fun isDividerLine(node: ViewNode): Boolean {
        // Not clickable
        if (node.clickable) return false

        // No text
        if (!node.text.isNullOrEmpty()) return false

        // No contentDesc (or empty string)
        if (!node.contentDesc.isNullOrEmpty()) return false

        // Calculate node width and height
        val width = node.right - node.left
        val height = node.bottom - node.top

        // Divider lines are usually thin: width or height very small (1-5 pixels)
        // Or very thin lines (large width but small height, or large height but small width)
        val isThinLine = (width <= 5 && height > 10) ||  // Vertical thin line
                        (height <= 5 && width > 10) ||  // Horizontal thin line
                        (width <= 5 && height <= 5)      // Very small dot or decorative element

        return isThinLine
    }

    /**
     * Create ViewNode copy with markers
     * For decorative images and divider lines, set contentDesc to "null" string
     */
    private fun markDecorativeImages(nodes: List<ViewNode>): List<ViewNode> {
        return nodes.map { node ->
            if (isDecorativeImage(node) || isDividerLine(node)) {
                // Create new copy, set contentDesc to "null"
                node.copy(contentDesc = "null")
            } else {
                node
            }
        }
    }

    /**
     * Append node state information: checked, selected, progress
     */
    private fun appendStateInfo(builder: StringBuilder, node: ViewNode, nodeTypeLabel: String) {
        val accessibilityNode = node.node ?: return
        try {
            val lowerLabel = nodeTypeLabel.lowercase()
            when {
                lowerLabel == "switch" || lowerLabel == "checkbox" -> {
                    builder.append(", checked:${accessibilityNode.isChecked}")
                }
                lowerLabel == "button" || lowerLabel == "text" || lowerLabel == "textview" -> {
                    if (accessibilityNode.isSelected) {
                        builder.append(", selected:true")
                    }
                }
                lowerLabel == "progress" || lowerLabel == "progressbar" -> {
                    accessibilityNode.rangeInfo?.let {
                        builder.append(", progress:${it.current}/${it.max}")
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore exceptions, don't affect main flow
        }
    }

    /**
     * Format node output: indentation, type, text, description, coordinates, clickable status, state information
     */
    private fun formatTreeNodeLine(node: ViewNode, depth: Int): String {
        val builder = StringBuilder()
        val indent = "  ".repeat(depth)
        val nodeType = getTreeDisplayType(node)
        builder.append(indent).append("- [").append(nodeType).append("] ")

        // If text and contentDesc content are the same, only output contentDesc
        val text = node.text?.trim()
        val contentDesc = node.contentDesc?.trim()
        val isSame = !text.isNullOrEmpty() && !contentDesc.isNullOrEmpty() && text == contentDesc

        if (!isSame && !text.isNullOrEmpty()) {
            builder.append("text=\"${node.text}\" ")
        }
        // If contentDesc is "null", indicates decorative image, output marker
        if (!contentDesc.isNullOrEmpty()) {
            if (contentDesc == "null") {
                builder.append("contentDesc=\"null\" ") // Decorative image marker
            } else {
                builder.append("contentDesc=\"${node.contentDesc}\" ")
            }
        }

        builder.append("Center (${node.point.x}, ${node.point.y}), l: ${node.left}, r: ${node.right}, t: ${node.top}, b: ${node.bottom}; ")
        builder.append("[clickable:${node.clickable}")
        appendStateInfo(builder, node, nodeType)
        builder.append("]\n")
        return builder.toString()
    }
    /**
     * Filter invalid system status bar information
     */
    private fun isSystemStatusBar(node: ViewNode): Boolean {
        if (node.top >= 100) return false

        val contentDesc = node.contentDesc?.lowercase() ?: ""
        if (SYSTEM_STATUS_KEYWORDS.any { contentDesc.contains(it) }) {
            return true
        }

        return node.text?.matches(Regex("\\d{1,2}:\\d{2}")) == true && node.contentDesc.isNullOrEmpty()
    }

    private val SYSTEM_STATUS_KEYWORDS = listOf(
        "android 系统通知",
        "系统通知",
        "通知",
        "wlan",
        "信号",
        "充电",
        "sim 卡",
        "振铃器",
        "振动",
        "nfc"
    )

    /**
     * Build tree main flow, core main function
     */
    fun buildComponentTreeDescription(nodes: List<ViewNode>): String {
        //Filter out system status bar
        var filteredNodes = nodes.filter { !isSystemStatusBar(it) }
        
        // Mark purely decorative images and divider lines: set contentDesc to "null"
        filteredNodes = markDecorativeImages(filteredNodes)
        
        // Exclude elements with contentDesc attribute (including decorative images marked as "null")
        filteredNodes = filteredNodes.filter { node ->
            val contentDesc = node.contentDesc?.trim()
            // 排除有 contentDesc 的元素（包括标记为 "null" 的装饰性图片）
            // Only keep elements without contentDesc
            contentDesc.isNullOrEmpty()
        }
        
        if (filteredNodes.isEmpty()) {
            return "(无可用数据)\n"
        }
        /**
         * nodeOrder: record node order index in original list
         * treeNodeMap: record ViewNode to TreeNode mapping relationship
         * nodeKeyMap: store unique node identifier to ViewNode mapping
         */
        val nodeOrder = filteredNodes.withIndex().associate { it.value to it.index }
        val treeNodeMap = mutableMapOf<ViewNode, TreeNode>()
        val nodeKeyMap = mutableMapOf<String, ViewNode>()

        /**
         * Create corresponding TreeNode object for each filtered node
         * Generate unique node identifier via getNodeKey and establish mapping
         */
        filteredNodes.forEach { viewNode ->
            treeNodeMap[viewNode] = TreeNode(viewNode)
            getNodeKey(viewNode.node)?.let { key ->
                nodeKeyMap[key] = viewNode
            }
        }
        /**
         * Traverse all TreeNodes to establish parent-child relationships, nodes without parent as root nodes
         */
        val rootNodes = mutableListOf<TreeNode>()
        treeNodeMap.values.forEach { treeNode ->
            val parentKey = getNodeKey(treeNode.viewNode.node?.parent)
            val parentTreeNode = parentKey?.let { nodeKeyMap[it] }?.let { treeNodeMap[it] }
            if (parentTreeNode != null && parentTreeNode !== treeNode) {
                parentTreeNode.children.add(treeNode)
            } else {
                rootNodes.add(treeNode)
            }
        }
        /**
         * Node sorting rule: original order index -> vertical position -> horizontal position
         */
        val comparator = compareBy<TreeNode> { nodeOrder[it.viewNode] ?: Int.MAX_VALUE }
            .thenBy { it.viewNode.top }
            .thenBy { it.viewNode.left }

        /**
         * Tree traversal output
         */
        val rootsToProcess = if (rootNodes.isNotEmpty()) rootNodes.distinct() else treeNodeMap.values.distinct()
        val builder = StringBuilder()
        rootsToProcess.sortedWith(comparator).forEach { appendTreeNode(builder, it, comparator) }
        /**
         * Return result
         */
        if (builder.isEmpty()) {
            builder.append("(无可用数据)\n")
        }
        return builder.toString()
    }

    /**
     * For recursively outputting tree structure
     * Step 1: Collapse redundant chain
     * Step 2: Skip empty leaf containers
     * Step 3: Check and filter nodes with contentDesc
     * Step 4: Format current node
     * Step 5: Filter duplicate button child nodes
     * Step 6: Recursively process child nodes (depth + 1)
     */
    private fun appendTreeNode(builder: StringBuilder, treeNode: TreeNode, comparator: Comparator<TreeNode>, depth: Int = 0) {
        val effectiveNode = collapseRedundantChain(treeNode)
        
        // Check if node has contentDesc, if yes skip (including decorative elements marked as "null")
        val contentDesc = effectiveNode.viewNode.contentDesc?.trim()
        if (!contentDesc.isNullOrEmpty()) {
            // Nodes with contentDesc should be excluded, not output
            return
        }
        
        if (shouldSkipLeafContainer(effectiveNode.viewNode, effectiveNode.children)) {
            return
        }
        builder.append(formatTreeNodeLine(effectiveNode.viewNode, depth))
        val remainingChildren = effectiveNode.children.filterNot {
            shouldBypassButtonChild(effectiveNode.viewNode, it.viewNode)
        }.filter { childNode ->
            // Recursive filtering: exclude child nodes with contentDesc
            val childContentDesc = childNode.viewNode.contentDesc?.trim()
            childContentDesc.isNullOrEmpty()
        }
        remainingChildren.distinct().sortedWith(comparator).forEach {
            appendTreeNode(builder, it, comparator, depth + 1)
        }
    }

    /**
     * Redundant chain collapse: when parent has only one child and both are equivalent or parent is empty, skip intermediate layers and only show meaningful nodes
     */
    private fun collapseRedundantChain(node: TreeNode): TreeNode {
        var current = node
        while (true) {
            val singleChild = current.children.singleOrNull() ?: break
            val isCurrentButton = current.viewNode.className?.lowercase()?.contains("button") == true
            if (isCurrentButton && shouldBypassButtonChild(current.viewNode, singleChild.viewNode)) {
                break
            }
            if (areNodesEquivalent(current.viewNode, singleChild.viewNode) ||
                shouldBypassContainer(current.viewNode, singleChild.viewNode)
            ) {
                current = singleChild
                continue
            }
            break
        }
        return if (current === node) node else TreeNode(current.viewNode, current.children)
    }

    /**
     * Determine if two nodes are equivalent, used for collapsing chain deduplication
     */
    private fun areNodesEquivalent(first: ViewNode, second: ViewNode): Boolean {
        return first.className == second.className &&
                first.left == second.left &&
                first.right == second.right &&
                first.top == second.top &&
                first.bottom == second.bottom &&
                first.clickable == second.clickable &&
                first.text == second.text &&
                first.contentDesc == second.contentDesc
    }

    /**
     * Skip empty container classes (empty layout, ViewGroup, etc)
     */
    private fun shouldBypassContainer(container: ViewNode, child: ViewNode): Boolean {
        val isStructural = isStructuralClass(container.className)
        if (!isStructural) return false
        val containerHasContent = !container.text.isNullOrEmpty() || !container.contentDesc.isNullOrEmpty()
        val childHasContent = !child.text.isNullOrEmpty() || !child.contentDesc.isNullOrEmpty()
        val childIsStructural = isStructuralClass(child.className)
        return !containerHasContent && (childHasContent || childIsStructural)
    }

    /**
     * Determine if it is structural class
     */
    private fun isStructuralClass(className: String?): Boolean {
        val lower = className?.lowercase() ?: return false
        return lower.contains("layout") ||
                lower.contains("viewgroup") ||
                lower.contains("frame")
    }

    /**
     * Remove textView under button (same meaning), simplify prompt
     */
    private fun shouldBypassButtonChild(parent: ViewNode, child: ViewNode): Boolean {
        val parentClass = parent.className?.lowercase() ?: return false
        if (!parentClass.contains("button")) return false

        val childClass = child.className?.lowercase() ?: return false
        if (!childClass.contains("textview") && !childClass.contains("text")) return false

        val parentLabel = (parent.text ?: parent.contentDesc)?.trim() ?: return false
        val childLabel = (child.text ?: child.contentDesc)?.trim() ?: return false

        return parentLabel == childLabel
    }

    /**
     * Determine if empty leaf node should be skipped
     */
    private fun shouldSkipLeafContainer(node: ViewNode, children: List<TreeNode>): Boolean {
        if (children.isNotEmpty()) return false
        val isStructural = isStructuralClass(node.className)
        val hasContent = !node.text.isNullOrEmpty() || !node.contentDesc.isNullOrEmpty()
        return isStructural && !hasContent
    }

    /**
     * Filter off-screen nodes obtained, only keep on-screen nodes
     */
    fun isNodeWithinScreen(
        node: ViewNode,
        screenWidth: Int,
        screenHeight: Int,
        tolerance: Int = 20
    ): Boolean {
        if (node.left >= node.right || node.top >= node.bottom) return false
        if (screenWidth > 0 && (node.right < -tolerance || node.left > screenWidth + tolerance)) return false
        if (screenHeight > 0 && (node.bottom < -tolerance || node.top > screenHeight + tolerance)) return false
        return true
    }

    // buildTreeFromImageDetail() has been deleted
    // ImageDetail is a class from old architecture (deleted), no longer used
    // New architecture directly uses buildComponentTreeDescription(nodes: List<ViewNode>)
}