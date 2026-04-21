import type { ExecutionGraph, TaskNode } from "../planning/plan-models";
import { parseExecutionGraph, topologicalSort } from "../planning/plan-parser";
import { resolveDeepSearchI18n } from "../i18n";

function getI18n() {
  const locale = getLang();
  return resolveDeepSearchI18n(locale);
}

// Matches Kotlin TaskStatus enum (line 79-81)
export type TaskStatus = "TODO" | "IN_PROGRESS" | "COMPLETED" | "FAILED";

// Matches Kotlin PlanExecutionState data class (lines 83-90)
export interface PlanExecutionState {
  graph: ExecutionGraph | null;
  taskStatuses: Record<string, TaskStatus>;
  taskToolCounts: Record<string, number>;
  logs: string[];
  summary: string | null;
  error: string | null;
}

// Matches Kotlin parsePlanStream function (lines 92-133)
function parsePlanStream(content: string): PlanExecutionState {
  const graphRegex = /<graph><!\[CDATA\[(.*?)]]><\/graph>/s;
  const updateRegex = /<update\s+id="([^"]+)"\s+status="([^"]+)"(?:\s+tool_count="([^"]+)")?(?:\s+error="([^"]*)")?\s*\/>/g;
  const logRegex = /<log>(.*?)<\/log>/g;
  const summaryRegex = /<summary>(.*?)<\/summary>/s;
  const errorRegex = /<error>(.*?)<\/error>/s;
  const i18n = getI18n();

  let graph: ExecutionGraph | null = null;
  let parseError: string | null = null;
  const graphMatch = content.match(graphRegex);
  if (graphMatch && graphMatch[1]) {
    graph = parseExecutionGraph(graphMatch[1]);
    if (!graph) {
      parseError = i18n.executionPlanParseFailed;
    }
  }

  const taskStatuses: Record<string, TaskStatus> = {};
  const taskToolCounts: Record<string, number> = {};

  let updateMatch: RegExpExecArray | null;
  while ((updateMatch = updateRegex.exec(content)) !== null) {
    const id = updateMatch[1];
    const statusRaw = updateMatch[2];
    const status: TaskStatus =
      statusRaw === "IN_PROGRESS"
        ? "IN_PROGRESS"
        : statusRaw === "COMPLETED"
          ? "COMPLETED"
          : statusRaw === "FAILED"
            ? "FAILED"
            : "TODO";
    taskStatuses[id] = status;

    const toolCountRaw = updateMatch[3] || "";
    const toolCount = Number(toolCountRaw);
    if (!Number.isNaN(toolCount) && toolCountRaw !== "") {
      taskToolCounts[id] = toolCount;
    }
  }

  const logs: string[] = [];
  let logMatch: RegExpExecArray | null;
  while ((logMatch = logRegex.exec(content)) !== null) {
    logs.push(logMatch[1]);
  }

  const summaryMatch = content.match(summaryRegex);
  const errorMatch = content.match(errorRegex);
  const error =
    errorMatch && errorMatch[1]
      ? errorMatch[1]
      : parseError;

  return {
    graph,
    taskStatuses,
    taskToolCounts,
    logs,
    summary: summaryMatch && summaryMatch[1] ? summaryMatch[1] : null,
    error
  };
}

// Matches Kotlin's PlanExecutionRenderer @Composable function (lines 136-212)
export function renderPlanExecution(ctx: ComposeDslContext, content: string): ComposeNode {
  const { UI } = ctx;
  const state = parsePlanStream(content || "");

  if (state.graph) {
    return renderExecutionGraphDisplay(
      ctx,
      state.graph,
      state.taskStatuses,
      state.taskToolCounts,
      state.logs,
      state.summary
    );
  }

  // Error state - matches Kotlin lines 154-181
  if (state.error) {
    const i18n = getI18n();
    return UI.Card(
      {
        fillMaxWidth: true,
        paddingHorizontal: 4,
        paddingVertical: 2,
        containerColor: ctx.MaterialTheme.colorScheme.errorContainer.copy({ alpha: 0.3 }),
        elevation: 1
      },
      UI.Row(
        {
          padding: 8,
          spacing: 6,
          verticalAlignment: "center"
        },
        [
          UI.Icon({ name: "Error", tint: ctx.MaterialTheme.colorScheme.error, size: 16 }),
          UI.Text({
            text: i18n.executionPlanError(state.error),
            style: "bodySmall",
            color: ctx.MaterialTheme.colorScheme.error,
            fontSize: 11
          })
        ]
      )
    );
  }

  // Loading state - matches Kotlin lines 183-211
  return UI.Card(
    {
      fillMaxWidth: true,
      paddingHorizontal: 4,
      paddingVertical: 2,
      containerColor: ctx.MaterialTheme.colorScheme.secondaryContainer.copy({ alpha: 0.3 }),
      elevation: 0
    },
    UI.Row(
      {
        padding: 8,
        spacing: 6,
        verticalAlignment: "center"
      },
      [
        UI.Icon({ name: "HourglassEmpty", tint: ctx.MaterialTheme.colorScheme.onSecondaryContainer, size: 16 }),
        UI.Text({
          text: getI18n().executionPlanPreparing,
          style: "bodySmall",
          color: ctx.MaterialTheme.colorScheme.onSecondaryContainer,
          fontSize: 11
        })
      ]
    )
  );
}

// Matches Kotlin's ExecutionGraphDisplay @Composable (lines 215-333)
function renderExecutionGraphDisplay(
  ctx: ComposeDslContext,
  graph: ExecutionGraph,
  taskStatuses: Record<string, TaskStatus>,
  taskToolCounts: Record<string, number>,
  logs: string[],
  summary: string | null
): ComposeNode {
  const { UI } = ctx;
  const children: ComposeNode[] = [];
  const i18n = getI18n();

  // Header with icon - matches lines 238-256
  children.push(
    UI.Row(
      {
        spacing: 6,
        verticalAlignment: "center",
        paddingBottom: 8
      },
      [
        UI.Icon({ name: "AccountTree", tint: ctx.MaterialTheme.colorScheme.primary, size: 18 }),
        UI.Text({
          text: i18n.executionPlanTitle,
          style: "titleSmall",
          fontWeight: "semibold",
          color: ctx.MaterialTheme.colorScheme.onSurface,
          fontSize: 13
        })
      ]
    )
  );

  // Compact graph area - matches lines 259-280
  children.push(
    UI.Box(
      {
        fillMaxWidth: true,
        height: 200,
        backgroundShape: { cornerRadius: 6 },
        backgroundBrush: {
          type: "verticalGradient",
          colors: [
            ctx.MaterialTheme.colorScheme.surfaceVariant.copy({ alpha: 0.3 }),
            ctx.MaterialTheme.colorScheme.surfaceVariant.copy({ alpha: 0.1 })
          ]
        },
        modifier: ctx.Modifier.padding(4)
      },
      renderWorkflowGraph(ctx, graph, taskStatuses, taskToolCounts)
    )
  );

  // Summary section - matches lines 283-310
  if (summary) {
    children.push(UI.Spacer({ height: 8 }));
    children.push(
      UI.Card(
        {
          fillMaxWidth: true,
          containerColor: ctx.MaterialTheme.colorScheme.primaryContainer.copy({ alpha: 0.3 }),
          elevation: 0
        },
        UI.Text({
          text: summary,
          style: "bodySmall",
          color: ctx.MaterialTheme.colorScheme.onPrimaryContainer,
          fontSize: 11,
          padding: 8
        })
      )
    );
  } else {
    children.push(UI.Spacer({ height: 6 }));
    children.push(
      UI.Text({
        text: i18n.executionPlanTarget(graph.finalSummaryInstruction),
        style: "bodySmall",
        color: ctx.MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize: 10,
        maxLines: 2
      })
    );
  }

  // Compact logs - matches lines 313-330
  if (logs.length > 0) {
    children.push(UI.Spacer({ height: 6 }));
    const recentLogs = logs.slice(-3);
    children.push(
      UI.Column(
        { height: 36, spacing: 2 },
        recentLogs.map(log =>
          UI.Text({
            text: log.replace(/<\/?log>/g, ""),
            style: "bodySmall",
            color: ctx.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize: 9,
            maxLines: 1
          })
        )
      )
    );
  }

  // Wrap in Card - matches lines 225-234
  return UI.Card(
    {
      fillMaxWidth: true,
      paddingHorizontal: 4,
      paddingVertical: 2,
      containerColor: ctx.MaterialTheme.colorScheme.surface.copy({ alpha: 0.95 }),
      elevation: 2,
      shape: { cornerRadius: 8 }
    },
    UI.Column({ padding: 10 }, children)
  );
}

// Node position data - matches lines 335-339
interface NodePositionData {
  taskNode: TaskNode;
  position: { x: number; y: number };
  size: { width: number; height: number };
}

// Matches Kotlin's WorkflowGraph @Composable (lines 341-436)
function renderWorkflowGraph(
  ctx: ComposeDslContext,
  graph: ExecutionGraph,
  taskStatuses: Record<string, TaskStatus>,
  taskToolCounts: Record<string, number>
): ComposeNode {
  const { UI } = ctx;
  const [scale, setScale] = ctx.useMutable("workflowScale", 1);
  const [offset, setOffset] = ctx.useMutable("workflowOffset", { x: 0, y: 0 });
  const [canvasSize, setCanvasSize] = ctx.useMutable("workflowCanvas", { width: 0, height: 0 });

  // Sort tasks - matches lines 360-366
  let sortedTasks: TaskNode[];
  try {
    sortedTasks = topologicalSort(graph);
  } catch (e) {
    sortedTasks = graph.tasks;
  }

  // Calculate node positions - matches lines 368-370
  const nodePositions = calculateNodePositions(sortedTasks, graph, canvasSize);

  // Professional color scheme - matches lines 372-381
  const todoColor = "#9CA3AF";       // Cool Gray 400
  const inProgressColor = "#3B82F6";  // Blue 500
  const completedColor = "#22C55E";   // Green 500
  const failedColor = "#EF4444";      // Red 500

  // Generate canvas commands - matches lines 394-436
  const commands: ComposeCanvasCommand[] = [];

  // Draw connections - matches line 407
  commands.push(...drawConnections(ctx, nodePositions));

  // Draw nodes - matches lines 410-432
  nodePositions.forEach(nodeData => {
    const status = taskStatuses[nodeData.taskNode.id] || "TODO";
    const toolCount = taskToolCounts[nodeData.taskNode.id] || 0;

    commands.push(
      ...drawTaskNode(
        ctx,
        nodeData,
        status,
        toolCount,
        todoColor,
        inProgressColor,
        completedColor,
        failedColor
      )
    );
  });

  return UI.Canvas({
    commands,
    fillMaxSize: true,
    transform: {
      scale,
      offsetX: offset.x,
      offsetY: offset.y,
      pivotX: canvasSize.width / 2,
      pivotY: canvasSize.height / 2
    },
    onTransform: event => {
      const nextScale = Math.min(2, Math.max(0.6, scale * event.zoom));
      setScale(nextScale);
      setOffset({
        x: offset.x + event.panX,
        y: offset.y + event.panY
      });
    },
    onSizeChanged: event => {
      if (event.width > 0 && event.height > 0) {
        setCanvasSize({ width: event.width, height: event.height });
      }
    }
  });
}

// Matches Kotlin's calculateNodePositions (lines 438-485)
function calculateNodePositions(
  sortedTasks: TaskNode[],
  graph: ExecutionGraph,
  canvasSize: { width: number; height: number }
): NodePositionData[] {
  if (canvasSize.width <= 0 || canvasSize.height <= 0 || sortedTasks.length === 0) {
    return [];
  }

  // Calculate levels - matches lines 447-451
  const levels: Record<string, number> = {};
  for (const task of sortedTasks) {
    const maxDepLevel = Math.max(-1, ...task.dependencies.map(dep => levels[dep] ?? -1));
    levels[task.id] = maxDepLevel + 1;
  }

  // Node dimensions - matches lines 453-457
  const nodeWidth = 300;
  const nodeHeight = 140;
  const horizontalSpacing = 50;
  const verticalSpacing = 60;

  // Group by level - matches line 459
  const nodesByLevel: Record<number, string[]> = {};
  for (const [taskId, level] of Object.entries(levels)) {
    if (!nodesByLevel[level]) nodesByLevel[level] = [];
    nodesByLevel[level].push(taskId);
  }

  const result: NodePositionData[] = [];
  const maxLevel = Math.max(...Object.keys(nodesByLevel).map(Number));
  const totalLevels = maxLevel + 1;
  const totalHeight = totalLevels * nodeHeight + Math.max(0, totalLevels - 1) * verticalSpacing;
  const initialY = (canvasSize.height - totalHeight) / 2;

  // Position nodes - matches lines 466-482
  for (const [levelStr, taskIds] of Object.entries(nodesByLevel)) {
    const level = Number(levelStr);
    const levelY = initialY + level * (nodeHeight + verticalSpacing);
    const levelTotalWidth = taskIds.length * nodeWidth + Math.max(0, taskIds.length - 1) * horizontalSpacing;
    let currentX = (canvasSize.width - levelTotalWidth) / 2;

    for (const taskId of taskIds) {
      const task = graph.tasks.find(t => t.id === taskId);
      if (task) {
        result.push({
          taskNode: task,
          position: { x: currentX, y: levelY },
          size: { width: nodeWidth, height: nodeHeight }
        });
        currentX += nodeWidth + horizontalSpacing;
      }
    }
  }

  return result;
}

// Matches Kotlin's drawConnections (lines 487-540)
function drawConnections(
  ctx: ComposeDslContext,
  nodes: NodePositionData[]
): ComposeCanvasCommand[] {
  const commands: ComposeCanvasCommand[] = [];
  const nodeMap: Record<string, NodePositionData> = {};
  for (const node of nodes) {
    nodeMap[node.taskNode.id] = node;
  }

  const arrowWidth = 8;
  const arrowHeight = 12;

  for (const toNodeData of nodes) {
    for (const fromNodeId of toNodeData.taskNode.dependencies) {
      const fromNodeData = nodeMap[fromNodeId];
      if (!fromNodeData) continue;

      const start = {
        x: fromNodeData.position.x + fromNodeData.size.width / 2,
        y: fromNodeData.position.y + fromNodeData.size.height
      };
      const end = {
        x: toNodeData.position.x + toNodeData.size.width / 2,
        y: toNodeData.position.y
      };

      // Bezier curve - matches lines 508-518
      const controlPoint1 = {
        x: start.x,
        y: start.y + (end.y - start.y) * 0.4
      };
      const controlPoint2 = {
        x: end.x,
        y: end.y - (end.y - start.y) * 0.4
      };

      // Draw path - matches line 520
      commands.push({
        type: "drawPath",
        path: [
          { type: "moveTo", x: start.x.px, y: start.y.px },
          {
            type: "cubicTo",
            x1: controlPoint1.x.px,
            y1: controlPoint1.y.px,
            x2: controlPoint2.x.px,
            y2: controlPoint2.y.px,
            x3: end.x.px,
            y3: end.y.px
          }
        ],
        color: ctx.MaterialTheme.colorScheme.outline.copy({ alpha: 0.5 }),
        strokeWidth: 1.5,
        style: "stroke"
      });

      // Arrow - matches lines 522-536
      const tangent = { x: end.x - controlPoint2.x, y: end.y - controlPoint2.y };
      const angle = Math.atan2(tangent.y, tangent.x) * (180 / Math.PI) - 90;

      commands.push({
        type: "drawPath",
        path: [
          { type: "moveTo", x: end.x.px, y: end.y.px },
          { type: "lineTo", x: (end.x - arrowWidth / 2).px, y: (end.y - arrowHeight).px },
          { type: "lineTo", x: (end.x + arrowWidth / 2).px, y: (end.y - arrowHeight).px },
          { type: "close" }
        ],
        color: ctx.MaterialTheme.colorScheme.outline.copy({ alpha: 0.5 }),
        style: "fill"
      });
    }
  }

  return commands;
}

// Matches Kotlin's drawTaskNode (lines 542-704)
function drawTaskNode(
  ctx: ComposeDslContext,
  nodeData: NodePositionData,
  status: TaskStatus,
  toolCount: number,
  todoColor: string,
  inProgressColor: string,
  completedColor: string,
  failedColor: string
): ComposeCanvasCommand[] {
  const commands: ComposeCanvasCommand[] = [];
  const { taskNode: task, position, size } = nodeData;
  const cornerRadius = 8;

  // Determine accent color - matches lines 559-564
  const accentColor =
    status === "TODO"
      ? todoColor
      : status === "IN_PROGRESS"
        ? inProgressColor
        : status === "COMPLETED"
          ? completedColor
          : failedColor;

  const statusIcon =
    status === "TODO"
      ? "⏳"
      : status === "IN_PROGRESS"
        ? "▶"
        : status === "COMPLETED"
          ? "✓"
          : "✗";

  const borderWidth = 2;
  const padding = 6;

  // Shadow - matches lines 570-575
  commands.push({
    type: "drawRoundRect",
    x: (position.x + 2).px,
    y: (position.y + 2).px,
    width: size.width.px,
    height: size.height.px,
    cornerRadius: cornerRadius.px,
    color: "#000000",
    alpha: 0.05,
    style: "fill"
  });

  // Background - matches lines 577-590
  commands.push({
    type: "drawRoundRect",
    x: position.x.px,
    y: position.y.px,
    width: size.width.px,
    height: size.height.px,
    cornerRadius: cornerRadius.px,
    brush: {
      type: "verticalGradient",
      colors: [
        ctx.MaterialTheme.colorScheme.surface,
        ctx.MaterialTheme.colorScheme.surface.copy({ alpha: 0.95 })
      ]
    },
    style: "fill"
  });

  // Pulsing effect for IN_PROGRESS - matches lines 593-601
  if (status === "IN_PROGRESS") {
    const pulse = 2; // Simplified pulse value
    commands.push({
      type: "drawRoundRect",
      x: (position.x - pulse * 0.5).px,
      y: (position.y - pulse * 0.5).px,
      width: (size.width + pulse).px,
      height: (size.height + pulse).px,
      cornerRadius: (cornerRadius + pulse * 0.5).px,
      color: accentColor,
      alpha: 0.2,
      strokeWidth: borderWidth + pulse * 0.3,
      style: "stroke"
    });
  }

  // Main border - matches lines 604-610
  commands.push({
    type: "drawRoundRect",
    x: position.x.px,
    y: position.y.px,
    width: size.width.px,
    height: size.height.px,
    cornerRadius: cornerRadius.px,
    color: accentColor,
    strokeWidth: borderWidth,
    style: "stroke"
  });

  // Status indicator circle - matches lines 612-623
  const indicatorRadius = 4;
  const indicatorOffset = {
    x: position.x + size.width - indicatorRadius - 4,
    y: position.y + indicatorRadius + 4
  };

  commands.push({
    type: "circle",
    cx: indicatorOffset.x.px,
    cy: indicatorOffset.y.px,
    radius: indicatorRadius.px,
    color: accentColor,
    filled: true
  });

  const textWidth = size.width - 2 * padding;

  const taskNameLayout = ctx.measureText({
    text: task.name,
    fontSize: 10,
    maxWidth: textWidth - 16,
    maxLines: 1,
    overflow: "ellipsis"
  });

  const taskIdLayout = ctx.measureText({
    text: task.id,
    fontSize: 7,
    maxWidth: textWidth,
    maxLines: 1,
    overflow: "ellipsis"
  });

  const i18n = getI18n();
  const toolCountText = i18n.executionPlanTools(toolCount);
  const toolCountLayout = ctx.measureText({
    text: toolCountText,
    fontSize: 7,
    maxWidth: textWidth,
    maxLines: 1,
    overflow: "ellipsis"
  });

  // Task name - matches lines 628-643
  commands.push({
    type: "drawText",
    text: task.name,
    x: (position.x + padding).px,
    y: (position.y + padding).px,
    color: ctx.MaterialTheme.colorScheme.onSurface,
    fontSize: (10).px,
    fontWeight: "semibold",
    maxLines: 1,
    minWidth: (textWidth - 16).px,
    maxWidth: (textWidth - 16).px,
    overflow: "ellipsis"
  });

  // Task ID - matches lines 645-660
  commands.push({
    type: "drawText",
    text: task.id,
    x: (position.x + padding).px,
    y: (position.y + padding + taskNameLayout.height + 2).px,
    color: ctx.MaterialTheme.colorScheme.onSurface.copy({ alpha: 0.6 }),
    fontSize: (7).px,
    maxLines: 1,
    minWidth: textWidth.px,
    maxWidth: textWidth.px,
    overflow: "ellipsis"
  });

  // Tool count - matches lines 662-679
  commands.push({
    type: "drawText",
    text: toolCountText,
    x: (position.x + padding).px,
    y: (position.y + padding + taskNameLayout.height + taskIdLayout.height + 4).px,
    color: ctx.MaterialTheme.colorScheme.onSurface.copy({ alpha: 0.7 }),
    fontSize: (7).px,
    maxLines: 1,
    minWidth: textWidth.px,
    maxWidth: textWidth.px,
    overflow: "ellipsis"
  });

  // Instruction - compact - matches lines 682-703
  const instructionTop =
    padding + taskNameLayout.height + taskIdLayout.height + toolCountLayout.height + 6;
  const instructionTextHeight = size.height - instructionTop - padding;

  if (instructionTextHeight > 10) {
    commands.push({
      type: "drawText",
      text: task.instruction,
      x: (position.x + padding).px,
      y: (position.y + instructionTop).px,
      color: ctx.MaterialTheme.colorScheme.onSurface.copy({ alpha: 0.8 }),
      fontSize: (8).px,
      maxWidth: textWidth.px,
      maxHeight: instructionTextHeight.px,
      overflow: "ellipsis"
    });
  }

  return commands;
}

export function parsePlanExecution(content: string): PlanExecutionState {
  return parsePlanStream(content || "");
}

export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const [xmlContent] = ctx.useState("xmlContent", "");
  return renderPlanExecution(ctx, String(xmlContent || ""));
}
