package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Rect
import com.ai.assistance.operit.util.AppLogger

// 辅助函数：判断两个矩形是否相交
private fun Rect.intersects(other: Rect): Boolean {
    return left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

private data class NodeLayoutCacheKey(
    val nodeId: String,
    val label: String,
    val styleKey: Int
)

private data class NodeLayoutMetrics(
    val textLayoutResult: TextLayoutResult,
    val paddingX: Float,
    val paddingY: Float,
    val boxWidth: Float,
    val boxHeight: Float,
    val cornerRadius: CornerRadius
)

private data class EdgeLabelCacheKey(
    val label: String
)

private data class NodePalette(
    val textColor: Color,
    val fillColor: Color,
    val underlayColor: Color,
    val defaultBorderColor: Color
)

private const val NODE_HIT_PADDING_PX = 12f

private fun edgeSignature(edge: Edge): String {
    val scaledWeight = (edge.weight * 1000f).toInt()
    val labelPart = edge.label ?: ""
    return "${edge.id}|${edge.sourceId}|${edge.targetId}|$scaledWeight|${edge.isCrossFolderLink}|$labelPart"
}

private data class ClusterLayoutInfo(
    val clusterByNodeId: Map<String, Int>,
    val clusterSizes: Map<Int, Int>,
    val clusterIds: List<Int>
)

private fun buildClusterLayoutInfo(graph: Graph): ClusterLayoutInfo {
    if (graph.nodes.isEmpty()) {
        return ClusterLayoutInfo(
            clusterByNodeId = emptyMap(),
            clusterSizes = emptyMap(),
            clusterIds = emptyList()
        )
    }

    val adjacency = mutableMapOf<String, MutableList<String>>()
    graph.nodes.forEach { node -> adjacency[node.id] = mutableListOf() }
    graph.edges.forEach { edge ->
        // 使用实线边定义簇结构，虚线边作为弱关系不参与簇划分。
        if (edge.isCrossFolderLink) return@forEach
        if (!adjacency.containsKey(edge.sourceId) || !adjacency.containsKey(edge.targetId)) return@forEach
        adjacency[edge.sourceId]!!.add(edge.targetId)
        adjacency[edge.targetId]!!.add(edge.sourceId)
    }

    val visited = mutableSetOf<String>()
    val clusterByNodeId = mutableMapOf<String, Int>()
    val clusterSizes = mutableMapOf<Int, Int>()
    var clusterId = 0

    for (node in graph.nodes) {
        if (!visited.add(node.id)) continue
        clusterId += 1
        var size = 0
        val queue = ArrayDeque<String>()
        queue.addLast(node.id)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            clusterByNodeId[current] = clusterId
            size += 1
            adjacency[current]?.forEach { neighbor ->
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor)
                }
            }
        }
        clusterSizes[clusterId] = size
    }

    return ClusterLayoutInfo(
        clusterByNodeId = clusterByNodeId,
        clusterSizes = clusterSizes,
        clusterIds = clusterSizes.keys.sorted()
    )
}

@OptIn(ExperimentalTextApi::class)
private fun getNodeLayoutMetrics(
    node: Node,
    textMeasurer: TextMeasurer,
    nodeLayoutCache: MutableMap<NodeLayoutCacheKey, NodeLayoutMetrics>,
    nodePalette: NodePalette
): NodeLayoutMetrics {
    return nodeLayoutCache.getOrPut(
        NodeLayoutCacheKey(
            nodeId = node.id,
            label = node.label,
            styleKey = nodePalette.textColor.hashCode()
        )
    ) {
        val baseScale = 1f
        val textStyle = TextStyle(
            fontSize = (11.5f * baseScale).sp,
            lineHeight = (12.5f * baseScale).sp,
            color = nodePalette.textColor
        )
        val maxTextWidth = (280f * baseScale).toInt().coerceAtLeast(1)
        val textLayoutResult = textMeasurer.measure(
            text = AnnotatedString(node.label),
            style = textStyle,
            constraints = Constraints(maxWidth = maxTextWidth)
        )
        val paddingX = 14f * baseScale
        val paddingY = 4f * baseScale
        val textWidth = textLayoutResult.size.width.toFloat()
        val textHeight = textLayoutResult.size.height.toFloat()
        val boxWidth = textWidth + paddingX * 2
        val boxHeight = textHeight + paddingY * 2
        val rounded = min(boxHeight * 0.48f, 20f * baseScale)
        NodeLayoutMetrics(
            textLayoutResult = textLayoutResult,
            paddingX = paddingX,
            paddingY = paddingY,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            cornerRadius = CornerRadius(rounded, rounded)
        )
    }
}

private fun resolveNodeVisualScale(viewScale: Float): Float = viewScale.coerceIn(0.15f, 2.2f)

@OptIn(ExperimentalTextApi::class)
private fun resolveNodeScreenRect(
    node: Node,
    center: Offset,
    viewScale: Float,
    textMeasurer: TextMeasurer,
    nodeLayoutCache: MutableMap<NodeLayoutCacheKey, NodeLayoutMetrics>,
    nodePalette: NodePalette,
    extraPadding: Float = 0f
): Rect {
    val layoutMetrics = getNodeLayoutMetrics(
        node = node,
        textMeasurer = textMeasurer,
        nodeLayoutCache = nodeLayoutCache,
        nodePalette = nodePalette
    )
    val visualScale = resolveNodeVisualScale(viewScale)
    val halfWidth = layoutMetrics.boxWidth * visualScale / 2f + extraPadding
    val halfHeight = layoutMetrics.boxHeight * visualScale / 2f + extraPadding
    return Rect(
        left = center.x - halfWidth,
        top = center.y - halfHeight,
        right = center.x + halfWidth,
        bottom = center.y + halfHeight
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphVisualizer(
    graph: Graph,
    modifier: Modifier = Modifier,
    selectedNodeId: String? = null,
    boxSelectedNodeIds: Set<String> = emptySet(),
    isBoxSelectionMode: Boolean = false, // 新增：是否处于框选模式
    linkingNodeIds: List<String> = emptyList(),
    selectedEdgeId: Long? = null,
    onNodeClick: (Node) -> Unit,
    onEdgeClick: (Edge) -> Unit,
    onNodesSelected: (Set<String>) -> Unit // 新增：框选完成后的回调
) {
    AppLogger.d("GraphVisualizer", "Recomposing. isBoxSelectionMode: $isBoxSelectionMode")
    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MaterialTheme.colorScheme
    val nodePalette = remember(colorScheme) {
        if (colorScheme.surface.luminance() < 0.42f) {
            NodePalette(
                textColor = Color(0xFFE5E7EB),
                fillColor = Color(0xFF2B313D),
                underlayColor = Color(0xFF1F2530),
                defaultBorderColor = Color(0xFF9CA3AF)
            )
        } else {
            NodePalette(
                textColor = Color(0xFF1F2937),
                fillColor = Color(0xFFE5E7EB),
                underlayColor = Color(0xFFD1D5DB),
                defaultBorderColor = Color(0xFF9CA3AF)
            )
        }
    }
    var nodePositions by remember { mutableStateOf(mapOf<String, Offset>()) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) } // 用于绘制选择框
    var previousNodeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var previousEdgeSignatures by remember { mutableStateOf<Set<String>>(emptySet()) }
    val nodeLayoutCache = remember { mutableMapOf<NodeLayoutCacheKey, NodeLayoutMetrics>() }
    val edgeLabelLayoutCache = remember { mutableMapOf<EdgeLabelCacheKey, TextLayoutResult>() }

    // 当退出框选模式时，确保清除选框
    LaunchedEffect(isBoxSelectionMode) {
        if (!isBoxSelectionMode) {
            selectionRect = null
        }
    }

    // 清理已移除节点的缓存，避免缓存无界增长。
    LaunchedEffect(graph.nodes) {
        val nodeLabelById = graph.nodes.associate { it.id to it.label }
        nodeLayoutCache.keys.removeAll { cacheKey ->
            nodeLabelById[cacheKey.nodeId] != cacheKey.label
        }
    }

    LaunchedEffect(graph.edges) {
        val validLabels = graph.edges.asSequence().mapNotNull { it.label }.toSet()
        edgeLabelLayoutCache.keys.removeAll { cacheKey -> cacheKey.label !in validLabels }
    }

    LaunchedEffect(colorScheme.onSurface, colorScheme.onSurfaceVariant) {
        nodeLayoutCache.clear()
        edgeLabelLayoutCache.clear()
    }

    val clusterLayoutInfo = remember(graph.nodes, graph.edges) {
        buildClusterLayoutInfo(graph)
    }
    val nodeById = remember(graph.nodes) {
        graph.nodes.associateBy { it.id }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // 计算可见区域（世界坐标）
        // 将屏幕坐标转换为世界坐标：worldPos = (screenPos - offset) / scale
        val visibleWorldRect = remember(scale, offset, width, height) {
            val left = -offset.x / scale
            val top = -offset.y / scale
            val right = (width - offset.x) / scale
            val bottom = (height - offset.y) / scale
            // 扩展可见区域，以便计算稍微超出屏幕的节点（扩展1.5倍）
            val padding = max(width, height) / scale * 0.5f
            Rect(
                left = left - padding,
                top = top - padding,
                right = right + padding,
                bottom = bottom + padding
            )
        }

        // Force-directed layout simulation
        LaunchedEffect(graph.nodes, graph.edges, width, height) {
            if (graph.nodes.isNotEmpty()) {
                // 智能地更新位置：保留现有节点位置，只为新节点分配位置
                val currentPositions = nodePositions
                val newPositions = mutableMapOf<String, Offset>()
                val center = Offset(width / 2, height / 2)
                val currentNodeIds = graph.nodes.asSequence().map { it.id }.toSet()
                val currentEdgeSignatures = graph.edges.asSequence().map(::edgeSignature).toSet()
                val hasPreviousSnapshot = previousNodeIds.isNotEmpty()
                val addedNodeCount = currentNodeIds.count { it !in previousNodeIds }
                val removedNodeCount = previousNodeIds.count { it !in currentNodeIds }
                val addedEdges = currentEdgeSignatures.count { it !in previousEdgeSignatures }
                val removedEdges = previousEdgeSignatures.count { it !in currentEdgeSignatures }
                val edgeChangeCount = addedEdges + removedEdges
                val changeScore = addedNodeCount + removedNodeCount + edgeChangeCount * 0.35f
                val incrementalUpdate =
                    hasPreviousSnapshot &&
                        changeScore <= max(6f, currentNodeIds.size * 0.12f)

                previousNodeIds = currentNodeIds
                previousEdgeSignatures = currentEdgeSignatures

                val clusterCount = clusterLayoutInfo.clusterIds.size.coerceAtLeast(1)
                val clusterRingRadius = min(width, height) * 0.34f
                val clusterCenters = clusterLayoutInfo.clusterIds.mapIndexed { index, clusterId ->
                    val angle = (2.0 * Math.PI * index / clusterCount).toFloat()
                    val clusterCenter = if (clusterCount == 1) {
                        center
                    } else {
                        center + Offset(cos(angle), sin(angle)) * clusterRingRadius
                    }
                    clusterId to clusterCenter
                }.toMap()
                val neighborsByNode = mutableMapOf<String, MutableList<String>>()
                graph.nodes.forEach { node -> neighborsByNode[node.id] = mutableListOf() }
                graph.edges.forEach { edge ->
                    neighborsByNode[edge.sourceId]?.add(edge.targetId)
                    neighborsByNode[edge.targetId]?.add(edge.sourceId)
                }

                graph.nodes.forEach { node ->
                    val oldPosition = currentPositions[node.id]
                    if (oldPosition != null) {
                        // 增量更新时保留已有节点位置，避免全图重置抖动。
                        newPositions[node.id] = oldPosition
                        return@forEach
                    }

                    val clusterId = clusterLayoutInfo.clusterByNodeId[node.id]
                    val clusterCenter = clusterCenters[clusterId] ?: center
                    val clusterSize = (clusterLayoutInfo.clusterSizes[clusterId] ?: 1).coerceAtLeast(1)
                    val neighborPositions = neighborsByNode[node.id]
                        .orEmpty()
                        .mapNotNull { neighborId -> currentPositions[neighborId] }

                    val basePosition = if (neighborPositions.isNotEmpty()) {
                        var sum = Offset.Zero
                        neighborPositions.forEach { p -> sum += p }
                        sum / neighborPositions.size.toFloat()
                    } else {
                        clusterCenter
                    }
                    val scatterRadius = if (neighborPositions.isNotEmpty()) {
                        (42f + neighborPositions.size * 5f).coerceAtMost(120f)
                    } else {
                        (70f + clusterSize * 4f).coerceAtMost(200f)
                    }
                    val randomAngle = (Math.random() * 2.0 * Math.PI).toFloat()
                    val randomRadius = (Math.random() * scatterRadius).toFloat()
                    val jitter = Offset(cos(randomAngle), sin(randomAngle)) * randomRadius
                    newPositions[node.id] = basePosition + jitter
                }

                withContext(Dispatchers.Main) {
                    nodePositions = newPositions
                }

                // Run simulation in a background coroutine
                launch(Dispatchers.Default) {
                    val positions = newPositions.toMutableMap()
                    var stableIterations = 0
                    val smoothedForces = mutableMapOf<String, Offset>()
                    graph.nodes.forEach { node -> smoothedForces[node.id] = Offset.Zero }

                    val nodeArray = graph.nodes.toTypedArray() // 转换为数组，提高访问速度
                    val nodeCount = graph.nodes.size
                    val nodeIdsByCluster = clusterLayoutInfo.clusterByNodeId.entries
                        .groupBy(
                            keySelector = { it.value },
                            valueTransform = { it.key }
                        )

                    // Tuned parameters for a more stable and visually pleasing layout
                    // 根据节点数量动态调整迭代次数，避免节点过多时计算时间过长
                    val baseIterations = when {
                        nodeCount > 200 -> 150  // 大量节点时减少迭代次数
                        nodeCount > 100 -> 200  // 中等数量节点
                        nodeCount > 50 -> 250   // 少量节点
                        else -> 300             // 很少节点，使用完整迭代
                    }
                    val iterations = if (incrementalUpdate) {
                        when {
                            changeScore <= 2.5f -> 28
                            changeScore <= 8f -> 46
                            else -> 72
                        }
                    } else {
                        baseIterations
                    }
                    val repulsionStrength = 380000f  // 进一步拉开节点间距
                    val attractionStrength = 0.07f   // 降低连边拉力，避免重新聚拢
                    val idealEdgeLength = 560f       // 提高全局目标距离
                    val gravityStrength = 0.005f     // 轻微中心收束，避免发散
                    val minNodeSeparation = 380f     // 提高硬性最小间距
                    val nodeEdgeClearance = minNodeSeparation * 0.62f // 节点与非关联连线的最小避让距离
                    val nodeEdgeRepulsionStrength = 105f              // 节点避让连线的强度
                    val nodeEdgeRepulsionStartIteration =
                        if (incrementalUpdate) max(2, iterations / 18) else iterations / 10
                    val edgeSamplingStep = when {
                        graph.edges.size > 2800 -> 6
                        graph.edges.size > 1600 -> 4
                        graph.edges.size > 900 -> 3
                        graph.edges.size > 450 -> 2
                        else -> 1
                    }
                    val initialTemperature =
                        if (incrementalUpdate) idealEdgeLength * 0.16f else idealEdgeLength * 0.42f
                    val minTemperature = if (incrementalUpdate) 0.55f else 0.9f
                    val stopAverageMove = if (incrementalUpdate) 0.3f else 0.42f
                    val forceSmoothingAlpha =
                        if (incrementalUpdate) 0.86f else 0.78f  // 增量阶段更强平滑，减少抖动
                    val minMoveDeadzone =
                        if (incrementalUpdate) 0.24f else 0.18f  // 增量阶段更早截断微抖动
                    
                    // 空间分区参数：网格大小（只计算网格内和相邻网格的节点）
                    val gridCellSize = idealEdgeLength * 2.5f // 网格大小约为理想边长的2.5倍
                    val maxRepulsionDistance = idealEdgeLength * 3f // 超过此距离的节点不计算排斥力

                    simulationLoop@ for (i in 0 until iterations) {
                        // 所有节点都参与计算
                        val forces = mutableMapOf<String, Offset>()
                        // 为所有节点初始化力
                        graph.nodes.forEach { node -> forces[node.id] = Offset.Zero }

                        // 空间分区优化：使用网格系统计算附近节点的排斥力
                        // 1. 构建网格：将所有节点分配到网格单元中
                        val grid = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
                        for (idx in nodeArray.indices) {
                            val pos = positions[nodeArray[idx].id] ?: continue
                            val gridX = (pos.x / gridCellSize).toInt()
                            val gridY = (pos.y / gridCellSize).toInt()
                            val key = gridX to gridY
                            grid.getOrPut(key) { mutableListOf() }.add(idx)
                        }

                        // 2. 只计算同一网格和相邻网格中的节点对
                        for ((gridKey, nodeIndices) in grid) {
                            val (gridX, gridY) = gridKey
                            
                            // 检查当前网格和8个相邻网格（3x3区域）
                            for (dx in -1..1) {
                                for (dy in -1..1) {
                                    val neighborKey = (gridX + dx) to (gridY + dy)
                                    val neighborIndices = grid[neighborKey] ?: continue
                                    
                                    // 计算当前网格和相邻网格中节点对的排斥力
                                    for (idx1 in nodeIndices) {
                                        val n1 = nodeArray[idx1]
                                        val p1 = positions[n1.id] ?: continue
                                        
                                        for (idx2 in neighborIndices) {
                                            // 避免重复计算和自比较：只处理 idx1 < idx2 的情况
                                            // 这样可以确保每对节点只计算一次
                                            if (idx1 >= idx2) continue
                                            
                                            val n2 = nodeArray[idx2]
                                            val p2 = positions[n2.id] ?: continue
                                            
                                            // 使用曼哈顿距离快速筛选：如果曼哈顿距离太远，跳过
                                            val deltaX = kotlin.math.abs(p1.x - p2.x)
                                            val deltaY = kotlin.math.abs(p1.y - p2.y)
                                            val manhattanDistance = deltaX + deltaY
                                            
                                            // 快速跳过：如果曼哈顿距离超过阈值，跳过精确计算
                                            if (manhattanDistance > maxRepulsionDistance * 1.5f) continue
                                            
                                            // 对于需要计算的节点，使用欧几里得距离（更精确）
                                            val delta = p1 - p2
                                            val distanceSq = deltaX * deltaX + deltaY * deltaY
                                            val distance = kotlin.math.sqrt(distanceSq).coerceAtLeast(1f)
                                            
                                            // 如果距离太远，跳过（使用距离平方比较，避免开方）
                                            if (distanceSq > maxRepulsionDistance * maxRepulsionDistance) continue
                                            
                                            val force = repulsionStrength / distanceSq
                                            val invDistance = 1f / distance
                                            val forceX = (p1.x - p2.x) * invDistance * force
                                            val forceY = (p1.y - p2.y) * invDistance * force
                                            
                                            // 排斥力是相互的，方向相反
                                            forces[n1.id] = forces[n1.id]!! + Offset(forceX, forceY)
                                            forces[n2.id] = forces[n2.id]!! - Offset(forceX, forceY)

                                            // 在阈值内施加额外分离力，避免节点视觉重叠或过近。
                                            if (distance < minNodeSeparation) {
                                                val overlapRatio =
                                                    (minNodeSeparation - distance) / minNodeSeparation
                                                val separationForce = overlapRatio.pow(2.25f) * 1200f
                                                val separationX = (p1.x - p2.x) * invDistance * separationForce
                                                val separationY = (p1.y - p2.y) * invDistance * separationForce
                                                forces[n1.id] = forces[n1.id]!! + Offset(separationX, separationY)
                                                forces[n2.id] = forces[n2.id]!! - Offset(separationX, separationY)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Attractive forces (spring model) along edges
                        // 计算所有边的吸引力
                        for (edge in graph.edges) {
                            val sourcePos = positions[edge.sourceId]
                            val targetPos = positions[edge.targetId]
                            if (sourcePos != null && targetPos != null) {
                                val delta = targetPos - sourcePos
                                val deltaX = delta.x
                                val deltaY = delta.y
                                val distanceSq = deltaX * deltaX + deltaY * deltaY
                                val distance = kotlin.math.sqrt(distanceSq).coerceAtLeast(1f)
                                
                                // Spring force: k * (x - ideal_length)
                                // Weight-based attraction: stronger edges pull nodes closer
                                val weightMultiplier = 1f + edge.weight * 0.35f
                                val edgeTypeAttractionMultiplier = if (edge.isCrossFolderLink) 0.45f else 1.95f
                                val edgeCompressionRepulsionMultiplier =
                                    if (edge.isCrossFolderLink) 0.8f else 2.15f
                                val edgeIdealLength = if (edge.isCrossFolderLink) idealEdgeLength * 1.35f else idealEdgeLength * 0.72f
                                val adjustedAttraction =
                                    attractionStrength * weightMultiplier * edgeTypeAttractionMultiplier
                                val invDistance = 1f / distance
                                val normalizedDelta = (distance - edgeIdealLength) / edgeIdealLength
                                val absNormalizedDelta = kotlin.math.abs(normalizedDelta)
                                var springForce = when {
                                    absNormalizedDelta < 0.06f -> {
                                        // 理想距离附近弱化斜率，减少来回抖动。
                                        adjustedAttraction * edgeIdealLength * normalizedDelta * 0.18f
                                    }
                                    normalizedDelta > 0f -> {
                                        // 距离偏大时非线性吸引，避免线性弹簧引发简谐。
                                        adjustedAttraction * edgeIdealLength *
                                            normalizedDelta.pow(1.35f)
                                    }
                                    else -> {
                                        // 距离偏小时使用更强的非线性反推，避免连边压扁堆叠。
                                        -adjustedAttraction * edgeIdealLength *
                                            absNormalizedDelta.pow(2.35f) *
                                            0.78f * edgeCompressionRepulsionMultiplier
                                    }
                                }

                                // 连接压缩保护：过度压缩时再额外给一段强非线性反推。
                                val compressionStartDistance = edgeIdealLength * 0.62f
                                if (distance < compressionStartDistance) {
                                    val compressionRatio =
                                        ((compressionStartDistance - distance) / compressionStartDistance)
                                            .coerceIn(0f, 1f)
                                    val extraRepulsion =
                                        adjustedAttraction * edgeIdealLength *
                                            compressionRatio.pow(2.8f) * 1.05f *
                                            edgeCompressionRepulsionMultiplier
                                    springForce -= extraRepulsion
                                }
                                val forceX = deltaX * invDistance * springForce
                                val forceY = deltaY * invDistance * springForce
                                
                                forces[edge.sourceId] = forces[edge.sourceId]!! + Offset(forceX, forceY)
                                forces[edge.targetId] = forces[edge.targetId]!! - Offset(forceX, forceY)
                            }
                        }

                        // 簇内收拢：基于实线连通分量的质心，对偏离过远的节点施加向心力。
                        val clusterCenterSums = mutableMapOf<Int, Offset>()
                        val clusterCenterCounts = mutableMapOf<Int, Int>()
                        for (node in graph.nodes) {
                            val clusterId = clusterLayoutInfo.clusterByNodeId[node.id] ?: continue
                            val pos = positions[node.id] ?: continue
                            clusterCenterSums[clusterId] = (clusterCenterSums[clusterId] ?: Offset.Zero) + pos
                            clusterCenterCounts[clusterId] = (clusterCenterCounts[clusterId] ?: 0) + 1
                        }
                        val clusterCentroids = mutableMapOf<Int, Offset>()
                        for ((clusterId, sum) in clusterCenterSums) {
                            val count = clusterCenterCounts[clusterId] ?: 1
                            clusterCentroids[clusterId] = sum / count.toFloat()
                        }
                        for (node in graph.nodes) {
                            val clusterId = clusterLayoutInfo.clusterByNodeId[node.id] ?: continue
                            val clusterSize = clusterLayoutInfo.clusterSizes[clusterId] ?: 1
                            if (clusterSize <= 1) continue
                            val clusterCentroid = clusterCentroids[clusterId] ?: continue
                            val nodePos = positions[node.id] ?: continue
                            val toCenter = clusterCentroid - nodePos
                            val centerDistance = toCenter.getDistance().coerceAtLeast(1f)
                            val desiredClusterRadius =
                                minNodeSeparation * 0.42f + (clusterSize - 1).coerceAtMost(24) * 3.2f
                            if (centerDistance <= desiredClusterRadius) continue
                            val normalizedClusterDistance =
                                (centerDistance - desiredClusterRadius) / desiredClusterRadius.coerceAtLeast(1f)
                            val cohesionForce = normalizedClusterDistance.pow(1.2f) * 46f
                            forces[node.id] =
                                forces[node.id]!! + toCenter / centerDistance * cohesionForce
                        }

                        // 簇间质心轻斥力：防止多个簇仍堆在一个区域。
                        val clusterIds = clusterCentroids.keys.toList()
                        for (i in clusterIds.indices) {
                            for (j in i + 1 until clusterIds.size) {
                                val clusterA = clusterIds[i]
                                val clusterB = clusterIds[j]
                                val centerA = clusterCentroids[clusterA] ?: continue
                                val centerB = clusterCentroids[clusterB] ?: continue
                                val delta = centerA - centerB
                                val distanceSq = delta.x * delta.x + delta.y * delta.y
                                if (distanceSq < 1f) continue
                                val distance = kotlin.math.sqrt(distanceSq)
                                val preferredClusterDistance = idealEdgeLength * 1.15f
                                if (distance >= preferredClusterDistance) continue

                                val overlapRatio =
                                    ((preferredClusterDistance - distance) / preferredClusterDistance)
                                        .coerceIn(0f, 1f)
                                val repulse = overlapRatio.pow(1.35f) * 38f
                                val direction = delta / distance
                                nodeIdsByCluster[clusterA]?.forEach { nodeId ->
                                    forces[nodeId] = forces[nodeId]!! + direction * repulse
                                }
                                nodeIdsByCluster[clusterB]?.forEach { nodeId ->
                                    forces[nodeId] = forces[nodeId]!! - direction * repulse
                                }
                            }
                        }
                        
                        // Gravity force towards the center
                        // 对所有节点计算重力
                        for (node in graph.nodes) {
                            val p = positions[node.id] ?: continue
                            val delta = center - p
                            // 重力使用简单的线性力，不需要归一化方向
                            forces[node.id] = forces[node.id]!! + delta * gravityStrength
                        }

                        // 节点-连线避让：节点会主动远离“非自己关联”的连线，减少线束压成一坨。
                        if (i >= nodeEdgeRepulsionStartIteration && i % 2 == 0) {
                            val edgePhase = if (edgeSamplingStep == 1) 0 else i % edgeSamplingStep
                            for (node in graph.nodes) {
                                val nodeId = node.id
                                val nodePos = positions[nodeId] ?: continue
                                var nodeForce = forces[nodeId] ?: Offset.Zero

                                for (edgeIndex in graph.edges.indices) {
                                    if (edgeSamplingStep > 1 && edgeIndex % edgeSamplingStep != edgePhase) continue
                                    val edge = graph.edges[edgeIndex]

                                    // 节点不避让和自己直接相连的边，避免破坏局部结构。
                                    if (edge.sourceId == nodeId || edge.targetId == nodeId) continue

                                    val a = positions[edge.sourceId] ?: continue
                                    val b = positions[edge.targetId] ?: continue

                                    val minX = min(a.x, b.x) - nodeEdgeClearance
                                    val maxX = max(a.x, b.x) + nodeEdgeClearance
                                    val minY = min(a.y, b.y) - nodeEdgeClearance
                                    val maxY = max(a.y, b.y) + nodeEdgeClearance
                                    if (nodePos.x !in minX..maxX || nodePos.y !in minY..maxY) continue

                                    val ab = b - a
                                    val abLenSq = ab.x * ab.x + ab.y * ab.y
                                    if (abLenSq < 1f) continue

                                    val ap = nodePos - a
                                    val t = ((ap.x * ab.x + ap.y * ab.y) / abLenSq).coerceIn(0f, 1f)
                                    val closest = a + ab * t
                                    val away = nodePos - closest
                                    val distance = away.getDistance().coerceAtLeast(0.001f)
                                    if (distance >= nodeEdgeClearance) continue

                                    val overlapRatio =
                                        ((nodeEdgeClearance - distance) / nodeEdgeClearance).coerceIn(0f, 1f)
                                    val edgeTypeMultiplier = if (edge.isCrossFolderLink) 0.6f else 1f
                                    val repel =
                                        nodeEdgeRepulsionStrength *
                                            edgeTypeMultiplier *
                                            overlapRatio.pow(1.95f)
                                    nodeForce += away / distance * repel
                                }

                                forces[nodeId] = nodeForce
                            }
                        }

                        // 使用降温步长的位移更新：温度单调下降，可显著抑制简谐震荡。
                        val coolingProgress =
                            i.toFloat() / (iterations - 1).coerceAtLeast(1).toFloat()
                        val temperature = max(
                            minTemperature,
                            initialTemperature * (1f - coolingProgress).pow(2.15f)
                        )
                        val temperatureFactor =
                            (temperature / initialTemperature).coerceIn(0.18f, 1f)
                        var totalMoveDistance = 0f
                        for (node in graph.nodes) {
                            val nodeId = node.id
                            val rawForce = forces[nodeId] ?: Offset.Zero
                            val previousSmoothed = smoothedForces[nodeId] ?: Offset.Zero
                            val nodeForce =
                                previousSmoothed * forceSmoothingAlpha +
                                    rawForce * (1f - forceSmoothingAlpha)
                            smoothedForces[nodeId] = nodeForce
                            val forceLength = nodeForce.getDistance()
                            if (forceLength < 0.0001f) continue
                            val updateGain = (0.62f + 0.28f * temperatureFactor).coerceIn(0.58f, 0.9f)
                            val limitedMove = min(forceLength, temperature) * updateGain
                            if (limitedMove < minMoveDeadzone) continue
                            val move = nodeForce * (limitedMove / forceLength)
                            positions[nodeId] = positions[nodeId]!! + move
                            totalMoveDistance += move.getDistance()
                        }

                        // 硬性间距校正：强制推开过近节点，避免高连接簇拥挤成团。
                        val collisionGridCellSize = minNodeSeparation
                        val collisionGrid = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
                        for (idx in nodeArray.indices) {
                            val pos = positions[nodeArray[idx].id] ?: continue
                            val gridX = (pos.x / collisionGridCellSize).toInt()
                            val gridY = (pos.y / collisionGridCellSize).toInt()
                            val key = gridX to gridY
                            collisionGrid.getOrPut(key) { mutableListOf() }.add(idx)
                        }
                        for ((gridKey, nodeIndices) in collisionGrid) {
                            val (gridX, gridY) = gridKey
                            for (dx in -1..1) {
                                for (dy in -1..1) {
                                    val neighborKey = (gridX + dx) to (gridY + dy)
                                    val neighborIndices = collisionGrid[neighborKey] ?: continue
                                    for (idx1 in nodeIndices) {
                                        val n1 = nodeArray[idx1]
                                        val p1 = positions[n1.id] ?: continue
                                        for (idx2 in neighborIndices) {
                                            if (idx1 >= idx2) continue
                                            val n2 = nodeArray[idx2]
                                            val p2 = positions[n2.id] ?: continue
                                            val delta = p2 - p1
                                            var distance = delta.getDistance()
                                            if (distance >= minNodeSeparation) continue

                                            val direction = if (distance < 0.001f) {
                                                // 完全重叠时给一个稳定方向，确保一定能分离。
                                                val seed = (idx1 * 73856093) xor (idx2 * 19349663)
                                                val angle = (seed % 6283) / 1000f // [0, 2pi) 近似
                                                Offset(cos(angle), sin(angle))
                                            } else {
                                                delta / distance
                                            }
                                            val overlapRatio =
                                                ((minNodeSeparation - distance) / minNodeSeparation)
                                                    .coerceIn(0f, 1f)
                                            val correctionGain =
                                                (0.07f + 0.30f * overlapRatio.pow(1.9f)) * temperatureFactor
                                            val overlap = (minNodeSeparation - distance) * correctionGain
                                            if (overlap < minNodeSeparation * 0.012f) continue
                                            val push = direction * overlap
                                            positions[n1.id] = p1 - push
                                            positions[n2.id] = p2 + push
                                            totalMoveDistance += (push * 2f).getDistance()
                                        }
                                    }
                                }
                            }
                        }

                        val averageMove = totalMoveDistance / nodeCount.coerceAtLeast(1).toFloat()
                        stableIterations = if (averageMove < stopAverageMove) {
                            stableIterations + 1
                        } else {
                            0
                        }

                        // Update UI
                        withContext(Dispatchers.Main) {
                            nodePositions = positions.toMap()
                        }
                        if (stableIterations >= 12) break@simulationLoop
                        delay(16)
                    }
                }
            } else {
                previousNodeIds = emptySet()
                previousEdgeSignatures = emptySet()
                 withContext(Dispatchers.Main) {
                    nodePositions = emptyMap()
                }
            }
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(isBoxSelectionMode) { // KEY CHANGE: Relaunch gestures when mode changes
                AppLogger.d("GraphVisualizer", "pointerInput recomposed/restarted. NEW MODE: ${if (isBoxSelectionMode) "BoxSelect" else "Normal"}")
                coroutineScope {
                    if (isBoxSelectionMode) {
                        AppLogger.d("GraphVisualizer", "Setting up GESTURES FOR BOX SELECTION mode.")
                        // --- 框选模式下的手势 ---
                        // 1. 拖拽框选 (排他性，禁用平移/缩放)
                        launch {
                            var dragStart: Offset? = null
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    AppLogger.d("GraphVisualizer", "BoxSelect: onDragStart")
                                    dragStart = startOffset
                                    selectionRect = createNormalizedRect(startOffset, startOffset)
                                },
                                onDrag = { change, _ ->
                                    dragStart?.let { start ->
                                        selectionRect =
                                            createNormalizedRect(start, change.position)
                                    }
                                },
                                onDragEnd = {
                                    AppLogger.d("GraphVisualizer", "BoxSelect: onDragEnd")
                                    selectionRect?.let { rect ->
                                        val selectedIds = graph.nodes
                                            .asSequence()
                                            .filter { node ->
                                                nodePositions[node.id]?.let { pos ->
                                                    val viewPos = pos * scale + offset
                                                    rect.intersects(
                                                        resolveNodeScreenRect(
                                                            node = node,
                                                            center = viewPos,
                                                            viewScale = scale,
                                                            textMeasurer = textMeasurer,
                                                            nodeLayoutCache = nodeLayoutCache,
                                                            nodePalette = nodePalette
                                                        )
                                                    )
                                                } ?: false
                                            }
                                            .map { it.id }
                                            .toSet()
                                        onNodesSelected(selectedIds)
                                    }
                                    selectionRect = null
                                    dragStart = null
                                },
                                onDragCancel = {
                                    AppLogger.d("GraphVisualizer", "BoxSelect: onDragCancel")
                                    selectionRect = null
                                    dragStart = null
                                }
                            )
                        }
                        // 2. 点击单选/取消
                        launch {
                            detectTapGestures(onTap = { tapOffset ->
                                AppLogger.d("GraphVisualizer", "BoxSelect: onTap")
                                val clickedNode = graph.nodes.findLast { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        resolveNodeScreenRect(
                                            node = node,
                                            center = viewPos,
                                            viewScale = scale,
                                            textMeasurer = textMeasurer,
                                            nodeLayoutCache = nodeLayoutCache,
                                            nodePalette = nodePalette,
                                            extraPadding = NODE_HIT_PADDING_PX
                                        ).contains(tapOffset)
                                    } ?: false
                                }
                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                }
                            })
                        }
                    } else {
                        AppLogger.d("GraphVisualizer", "Setting up GESTURES FOR NORMAL mode.")
                        // --- 普通模式下的手势 ---
                        // 1. 平移和缩放
                        launch {
                            AppLogger.d("GraphVisualizer", "Launching detectTransformGestures (Pan/Zoom).")
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(0.2f, 5f)
                                offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                                scale = newScale
                            }
                        }
                        // 2. 点击
                        launch {
                            detectTapGestures(onTap = { tapOffset ->
                                AppLogger.d("GraphVisualizer", "Normal Mode: onTap")
                                val clickedNode = graph.nodes.findLast { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        resolveNodeScreenRect(
                                            node = node,
                                            center = viewPos,
                                            viewScale = scale,
                                            textMeasurer = textMeasurer,
                                            nodeLayoutCache = nodeLayoutCache,
                                            nodePalette = nodePalette,
                                            extraPadding = NODE_HIT_PADDING_PX
                                        ).contains(tapOffset)
                                    } ?: false
                                }
                                val clickedEdge = if (clickedNode == null) {
                                    graph.edges.find { edge ->
                                        val sourcePos = nodePositions[edge.sourceId]
                                        val targetPos = nodePositions[edge.targetId]
                                        if (sourcePos != null && targetPos != null) {
                                            val sourceCenter = sourcePos * scale + offset
                                            val targetCenter = targetPos * scale + offset

                                            distanceToSegment(tapOffset, sourceCenter, targetCenter) < 20f
                                        } else false
                                    }
                                } else null

                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                } else if (clickedEdge != null) {
                                    onEdgeClick(clickedEdge)
                                }
                            })
                        }
                    }
                }
            }
        ) {
            // 计算屏幕可见区域（屏幕坐标）
            val screenVisibleRect = Rect(0f, 0f, size.width, size.height)
            
            // 绘制选框
            selectionRect?.let { rect ->
                drawRect(
                    color = colorScheme.primary.copy(alpha = 0.3f),
                    topLeft = rect.topLeft,
                    size = rect.size
                )
                drawRect(
                    color = colorScheme.primary,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 6f)
                )
            }

            // Edges - 只渲染至少有一个端点在可见区域内的边
            graph.edges.forEach { edge ->
                val sourcePos = nodePositions[edge.sourceId]
                val targetPos = nodePositions[edge.targetId]
                if (sourcePos != null && targetPos != null) {
                    // 计算屏幕坐标
                    val sourceCenter = sourcePos * scale + offset
                    val targetCenter = targetPos * scale + offset
                    val sourceRect = nodeById[edge.sourceId]?.let { sourceNode ->
                        resolveNodeScreenRect(
                            node = sourceNode,
                            center = sourceCenter,
                            viewScale = scale,
                            textMeasurer = textMeasurer,
                            nodeLayoutCache = nodeLayoutCache,
                            nodePalette = nodePalette
                        )
                    }
                    val targetRect = nodeById[edge.targetId]?.let { targetNode ->
                        resolveNodeScreenRect(
                            node = targetNode,
                            center = targetCenter,
                            viewScale = scale,
                            textMeasurer = textMeasurer,
                            nodeLayoutCache = nodeLayoutCache,
                            nodePalette = nodePalette
                        )
                    }
                    
                    // 检查是否至少有一个端点在可见区域内（使用节点真实矩形）
                    val sourceVisible = sourceRect?.let { screenVisibleRect.intersects(it) } == true
                    val targetVisible = targetRect?.let { screenVisibleRect.intersects(it) } == true
                    
                    // 如果两个端点都不在可见区域内，跳过渲染
                    if (!sourceVisible && !targetVisible) return@forEach
                    
                    // 连线直接连接节点中心，避免看起来仍然是“圆形边缘裁切”。
                    val start = sourceCenter
                    val end = targetCenter
                    
                    val strokeWidth = if (edge.isCrossFolderLink) {
                        (edge.weight * 2.6f * scale).coerceAtMost(8f)
                    } else {
                        (edge.weight * 5.8f * scale).coerceAtMost(18f)
                    }
                    
                    // 如果是跨文件夹连接，使用虚线绘制
                    if (edge.isCrossFolderLink) {
                        drawLine(
                            color = if (edge.id == selectedEdgeId) colorScheme.error else colorScheme.outline,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    } else {
                        drawLine(
                            color = if (edge.id == selectedEdgeId) colorScheme.error else colorScheme.outline,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth
                        )
                    }
                    
                    edge.label?.let { label ->
                        val center = (start + end) / 2f
                        // 只渲染标签中心在可见区域内的标签
                        if (screenVisibleRect.contains(center)) {
                            val angle = atan2(end.y - start.y, end.x - start.x)
                            val edgeLabelScale = scale.coerceIn(0.25f, 2f)

                            val textLayoutResult = edgeLabelLayoutCache.getOrPut(
                                EdgeLabelCacheKey(label = label)
                            ) {
                                textMeasurer.measure(
                                    text = AnnotatedString(label),
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            
                            // Simple collision avoidance for label, could be improved
                            val labelOffset = if (angle > -Math.PI / 2 && angle < Math.PI / 2) -textLayoutResult.size.height.toFloat() else 0f

                            withTransform({
                                scale(
                                    scaleX = edgeLabelScale,
                                    scaleY = edgeLabelScale,
                                    pivot = center
                                )
                            }) {
                                drawText(
                                    textLayoutResult = textLayoutResult,
                                    topLeft = Offset(
                                        x = center.x - textLayoutResult.size.width / 2,
                                        y = center.y + labelOffset
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Nodes - 只渲染可见区域内的节点
            graph.nodes.forEach { node ->
                val position = nodePositions[node.id]
                if (position != null) {
                    val screenPosition = position * scale + offset
                    
                    // 检查节点是否在可见区域内（使用节点真实矩形）
                    val nodeRect = resolveNodeScreenRect(
                        node = node,
                        center = screenPosition,
                        viewScale = scale,
                        textMeasurer = textMeasurer,
                        nodeLayoutCache = nodeLayoutCache,
                        nodePalette = nodePalette
                    )
                    
                    // 如果节点不在可见区域内，跳过渲染
                    if (!screenVisibleRect.intersects(nodeRect)) return@forEach
                    
                    val isSelected = node.id == selectedNodeId
                    val isLinkingCandidate = node.id in linkingNodeIds
                    val isBoxSelected = node.id in boxSelectedNodeIds // 新增：检查是否被框选
                    drawNode(
                        node = node,
                        position = screenPosition,
                        viewScale = scale,
                        textMeasurer = textMeasurer,
                        nodeLayoutCache = nodeLayoutCache,
                        nodePalette = nodePalette,
                        colorScheme = colorScheme,
                        isSelected = isSelected,
                        isLinkingCandidate = isLinkingCandidate,
                        isBoxSelected = isBoxSelected // 新增：传递框选状态
                    )
                }
            }
        }
    }
}

/**
 * 根据起始点和结束点创建标准化的矩形，确保left <= right, top <= bottom。
 */
private fun createNormalizedRect(start: Offset, end: Offset): Rect {
    return Rect(
        left = min(start.x, end.x),
        top = min(start.y, end.y),
        right = max(start.x, end.x),
        bottom = max(start.y, end.y)
    )
}

private fun distanceToSegment(p: Offset, start: Offset, end: Offset): Float {
    val l2 = (start - end).getDistanceSquared()
    if (l2 == 0f) return (p - start).getDistance()
    val t = ((p.x - start.x) * (end.x - start.x) + (p.y - start.y) * (end.y - start.y)) / l2
    val tClamped = t.coerceIn(0f, 1f)
    val projection = start + (end - start) * tClamped
    return (p - projection).getDistance()
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNode(
    node: Node,
    position: Offset,
    viewScale: Float,
    textMeasurer: TextMeasurer,
    nodeLayoutCache: MutableMap<NodeLayoutCacheKey, NodeLayoutMetrics>,
    nodePalette: NodePalette,
    colorScheme: androidx.compose.material3.ColorScheme,
    isSelected: Boolean,
    isLinkingCandidate: Boolean,
    isBoxSelected: Boolean // 新增：接收框选状态
) {
    val visualScale = resolveNodeVisualScale(viewScale)
    val layoutMetrics = getNodeLayoutMetrics(
        node = node,
        textMeasurer = textMeasurer,
        nodeLayoutCache = nodeLayoutCache,
        nodePalette = nodePalette
    )

    val textLayoutResult = layoutMetrics.textLayoutResult
    val paddingX = layoutMetrics.paddingX
    val paddingY = layoutMetrics.paddingY
    val boxWidth = layoutMetrics.boxWidth
    val boxHeight = layoutMetrics.boxHeight
    val boxTopLeft = Offset(
        x = position.x - boxWidth / 2f,
        y = position.y - boxHeight / 2f
    )
    val cornerRadius = layoutMetrics.cornerRadius

    withTransform({
        scale(
            scaleX = visualScale,
            scaleY = visualScale,
            pivot = position
        )
    }) {
        val borderColor = when {
            isLinkingCandidate -> colorScheme.error
            isSelected -> colorScheme.secondary
            else -> nodePalette.defaultBorderColor
        }

        // 浅灰底层，增强白底卡片层次感。
        drawRoundRect(
            color = nodePalette.underlayColor,
            topLeft = Offset(boxTopLeft.x, boxTopLeft.y + 1.2f),
            size = Size(boxWidth, boxHeight),
            cornerRadius = cornerRadius
        )

        drawRoundRect(
            color = nodePalette.fillColor,
            topLeft = boxTopLeft,
            size = Size(boxWidth, boxHeight),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = borderColor,
            topLeft = boxTopLeft,
            size = Size(boxWidth, boxHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = if (isSelected || isLinkingCandidate) 2.2f else 1.8f)
        )

        // 框选高亮
        if (isBoxSelected) {
            val highlightPadding = 5f
            drawRoundRect(
                color = colorScheme.tertiary, // 使用主题的第三色作为高亮
                topLeft = Offset(
                    boxTopLeft.x - highlightPadding,
                    boxTopLeft.y - highlightPadding
                ),
                size = Size(
                    boxWidth + highlightPadding * 2,
                    boxHeight + highlightPadding * 2
                ),
                cornerRadius = CornerRadius(
                    min((boxHeight + highlightPadding * 2) * 0.48f, 22f),
                    min((boxHeight + highlightPadding * 2) * 0.48f, 22f)
                ),
                style = Stroke(width = 2.8f)
            )
        }

        if (isLinkingCandidate) {
            drawRoundRect(
                color = colorScheme.error,
                topLeft = boxTopLeft,
                size = Size(boxWidth, boxHeight),
                cornerRadius = cornerRadius,
                style = Stroke(width = 2.8f)
            )
        }
        
        val textPosition = Offset(
            x = boxTopLeft.x + paddingX,
            y = boxTopLeft.y + paddingY
        )
        
        drawText(textLayoutResult, topLeft = textPosition)
    }
} 
