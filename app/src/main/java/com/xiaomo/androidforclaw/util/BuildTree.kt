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

object BuildTree {
    /**
     * 多叉树节点定义
     */
    private data class TreeNode(
        val viewNode: ViewNode,
        val children: MutableList<TreeNode> = mutableListOf()
    )

    /**
     * 获取节点的相关属性：坐标，类名，资源id，文本，内容描述
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
     * 节点类型提取（如button，textView）
     */
    private fun getTreeDisplayType(viewNode: ViewNode): String {
        return viewNode.className?.substringAfterLast('.') ?: "View"
    }

    /**
     *  追加节点的状态信息：checked、selected、progress
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
            // 忽略异常，不影响主流程
        }
    }

    /**
     * 节点格式化输出：缩进、类型、文本、描述、坐标、可点击状态、状态信息
     */
    private fun formatTreeNodeLine(node: ViewNode, depth: Int): String {
        val builder = StringBuilder()
        val indent = "  ".repeat(depth)
        val nodeType = getTreeDisplayType(node)
        builder.append(indent).append("- [").append(nodeType).append("] ")
        
        // 如果 text 和 contentDesc 内容相同，只输出 contentDesc
        val text = node.text?.trim()
        val contentDesc = node.contentDesc?.trim()
        val isSame = !text.isNullOrEmpty() && !contentDesc.isNullOrEmpty() && text == contentDesc
        
        if (!isSame && !text.isNullOrEmpty()) {
            builder.append("text=\"${node.text}\" ")
        }
        if (!contentDesc.isNullOrEmpty()) {
            builder.append("contentDesc=\"${node.contentDesc}\" ")
        }
        
        builder.append("Center (${node.point.x}, ${node.point.y}), l: ${node.left}, r: ${node.right}, t: ${node.top}, b: ${node.bottom}; ")
        builder.append("[clickable:${node.clickable}")
        appendStateInfo(builder, node, nodeType)
        builder.append("]\n")
        return builder.toString()
    }
    /**
     * 过滤系统状态栏的无效信息
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
     * 构建树的主流程，核心主函数
     */
    fun buildComponentTreeDescription(nodes: List<ViewNode>): String {
        //过滤掉系统状态栏
        val filteredNodes = nodes.filter { !isSystemStatusBar(it) }
        if (filteredNodes.isEmpty()) {
            return "(无可用数据)\n"
        }
        /**
         * nodeOrder：记录节点在原列表中的顺序索引
         * treeNodeMap：记录ViewNode到TreeNode的映射关系
         * nodeKeyMap: 存储节点唯一标识到 ViewNode 的映射
         */
        val nodeOrder = filteredNodes.withIndex().associate { it.value to it.index }
        val treeNodeMap = mutableMapOf<ViewNode, TreeNode>()
        val nodeKeyMap = mutableMapOf<String, ViewNode>()

        /**
         * 为每个过滤后的节点创建对应的 TreeNode 对象
         * 通过 getNodeKey 生成节点唯一标识并建立映射
         */
        filteredNodes.forEach { viewNode ->
            treeNodeMap[viewNode] = TreeNode(viewNode)
            getNodeKey(viewNode.node)?.let { key ->
                nodeKeyMap[key] = viewNode
            }
        }
        /**
         * 遍历所有TreeNode建立父子关系，无父节点的节点作为根节点
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
         * 节点排序规则：原始顺序索引 -》垂直位置 -》水平位置
         */
        val comparator = compareBy<TreeNode> { nodeOrder[it.viewNode] ?: Int.MAX_VALUE }
            .thenBy { it.viewNode.top }
            .thenBy { it.viewNode.left }

        /**
         * 树遍历输出
         */
        val rootsToProcess = if (rootNodes.isNotEmpty()) rootNodes.distinct() else treeNodeMap.values.distinct()
        val builder = StringBuilder()
        rootsToProcess.sortedWith(comparator).forEach { appendTreeNode(builder, it, comparator) }
        /**
         * 结果返回
         */
        if (builder.isEmpty()) {
            builder.append("(无可用数据)\n")
        }
        return builder.toString()
    }

    /**
     * 用于递归输出树结构
     * 步骤1：折叠冗余链
     * 步骤2：跳过空叶子容器
     * 步骤3：格式化当前节点
     * 步骤4：过滤按钮重复子节点
     * 步骤5：递归处理子节点（depth + 1）
     */
    private fun appendTreeNode(builder: StringBuilder, treeNode: TreeNode, comparator: Comparator<TreeNode>, depth: Int = 0) {
        val effectiveNode = collapseRedundantChain(treeNode)
        if (shouldSkipLeafContainer(effectiveNode.viewNode, effectiveNode.children)) {
            return
        }
        builder.append(formatTreeNodeLine(effectiveNode.viewNode, depth))
        val remainingChildren = effectiveNode.children.filterNot {
            shouldBypassButtonChild(effectiveNode.viewNode, it.viewNode)
        }
        remainingChildren.distinct().sortedWith(comparator).forEach {
            appendTreeNode(builder, it, comparator, depth + 1)
        }
    }

    /**
     * 冗余链折叠：当父节点只有一个子节点，且二者等价或父节点为空节点时，则跳过中间层只显示有意义节点
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
     * 判断两节点是否等价，用于折叠链去重
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
     * 跳过空的容器类（空的layout，ViewGroup等）
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
     * 判断是否为结构类
     */
    private fun isStructuralClass(className: String?): Boolean {
        val lower = className?.lowercase() ?: return false
        return lower.contains("layout") ||
                lower.contains("viewgroup") ||
                lower.contains("frame")
    }

    /**
     * 去除button下的textView（表达含义相同），简化prompt
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
     * 判断是否跳过为空的叶子节点
     */
    private fun shouldSkipLeafContainer(node: ViewNode, children: List<TreeNode>): Boolean {
        if (children.isNotEmpty()) return false
        val isStructural = isStructuralClass(node.className)
        val hasContent = !node.text.isNullOrEmpty() || !node.contentDesc.isNullOrEmpty()
        return isStructural && !hasContent
    }

    /**
     * 过滤获取到的屏幕外节点，仅保留屏幕内的节点
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

    // buildTreeFromImageDetail() 已删除
    // ImageDetail 是旧架构的类（已删除），不再使用
    // 新架构直接使用 buildComponentTreeDescription(nodes: List<ViewNode>)
}