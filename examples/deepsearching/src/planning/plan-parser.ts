import type { ExecutionGraph, TaskNode } from "./plan-models";
import { resolveDeepSearchI18n } from "../i18n";

const TAG = "PlanParser";

function getI18n() {
  const locale = getLang();
  return resolveDeepSearchI18n(locale);
}

function extractJsonFromResponse(response: string): string {
  const firstBrace = response.indexOf("{");
  const lastBrace = response.lastIndexOf("}");
  if (firstBrace !== -1 && lastBrace !== -1 && firstBrace < lastBrace) {
    return response.substring(firstBrace, lastBrace + 1);
  }
  return response;
}

export function parseExecutionGraph(jsonString: string): ExecutionGraph | null {
  try {
    const cleaned = extractJsonFromResponse(jsonString);
    console.log(`${TAG} parse execution graph`, cleaned);
    const parsed = JSON.parse(cleaned) as
      | (Partial<ExecutionGraph> & { final_summary_instruction?: unknown })
      | null;
    if (!parsed || typeof parsed !== "object") {
      throw new Error("execution graph must be an object");
    }
    if (!Array.isArray(parsed.tasks)) {
      throw new Error("execution graph tasks must be an array");
    }
    const finalSummaryInstruction =
      typeof parsed.finalSummaryInstruction === "string" && parsed.finalSummaryInstruction.trim()
        ? parsed.finalSummaryInstruction.trim()
        : typeof parsed.final_summary_instruction === "string" && parsed.final_summary_instruction.trim()
          ? parsed.final_summary_instruction.trim()
          : "";
    if (!finalSummaryInstruction) {
      throw new Error("execution graph finalSummaryInstruction is required");
    }
    return {
      ...parsed,
      finalSummaryInstruction
    } as ExecutionGraph;
  } catch (e) {
    console.log(`${TAG} parse failed`, String(e));
    return null;
  }
}

export function topologicalSort(graph: ExecutionGraph): TaskNode[] {
  const tasks = graph.tasks || [];
  const taskMap: Record<string, TaskNode> = {};
  tasks.forEach(task => { taskMap[task.id] = task; });

  const inDegree: Record<string, number> = {};
  const adj: Record<string, string[]> = {};

  tasks.forEach(task => {
    inDegree[task.id] = 0;
    adj[task.id] = [];
  });

  tasks.forEach(task => {
    (task.dependencies || []).forEach(depId => {
      if (taskMap[depId]) {
        adj[depId].push(task.id);
        inDegree[task.id] = (inDegree[task.id] || 0) + 1;
      } else {
        const msg = getI18n().planTaskDependencyNotExist(task.id, depId);
        console.log(`${TAG} ${msg}`);
      }
    });
  });

  const queue: string[] = [];
  Object.keys(inDegree).forEach(taskId => {
    if (inDegree[taskId] === 0) queue.push(taskId);
  });

  const result: TaskNode[] = [];
  while (queue.length > 0) {
    const currentId = queue.shift();
    if (!currentId) break;
    const task = taskMap[currentId];
    if (task) result.push(task);
    (adj[currentId] || []).forEach(nei => {
      inDegree[nei] = (inDegree[nei] || 0) - 1;
      if (inDegree[nei] === 0) queue.push(nei);
    });
  }

  if (result.length !== tasks.length) {
    const msg = getI18n().planCircularDependency;
    console.log(`${TAG} ${msg}`);
    return [];
  }

  console.log(`${TAG} topological sort complete`, result.map(t => t.id));
  return result;
}

export function validateExecutionGraph(graph: ExecutionGraph): { ok: boolean; error: string } {
  const ids = (graph.tasks || []).map(t => t.id);
  const unique = new Set(ids);
  if (ids.length !== unique.size) {
    return {
      ok: false,
      error: getI18n().planTaskIdNotUnique
    };
  }

  const validIds = new Set(ids);
  for (const task of graph.tasks || []) {
    for (const dep of task.dependencies || []) {
      if (!validIds.has(dep)) {
        return {
          ok: false,
          error: getI18n().planTaskDependencyNotExist(task.id, dep)
        };
      }
    }
  }

  const sorted = topologicalSort(graph);
  if (sorted.length === 0 && (graph.tasks || []).length > 0) {
    return {
      ok: false,
      error: getI18n().planCircularDependency
    };
  }

  return {
    ok: true,
    error: getI18n().planExecutionGraphValid
  };
}
