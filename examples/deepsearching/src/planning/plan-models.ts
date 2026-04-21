export interface TaskNode {
  id: string;
  name: string;
  instruction: string;
  dependencies: string[];
  type: string;
}

export interface ExecutionGraph {
  tasks: TaskNode[];
  finalSummaryInstruction: string;
}
