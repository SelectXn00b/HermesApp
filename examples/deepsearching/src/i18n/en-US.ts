import type { DeepSearchI18n } from "./types";

export const DEEPSEARCH_EN_US: DeepSearchI18n = {
  menuTitle: "Deep Search",
  menuDescription: "Enable planning mode for deeper analysis and decomposition",

  executionPlanTitle: "Deep Search",
  executionPlanPreparing: "Preparing execution plan...",
  executionPlanParseFailed: "Execution plan parsing failed. Please check the plan data format.",
  executionPlanError: error => `Error: ${error}`,
  executionPlanTarget: instruction => `Target: ${instruction}`,
  executionPlanTools: count => `tools: ${count}`,

  planModeExecutingDeepSearch: "Executing deep search",
  planModeStarting: "Starting deep search",
  planModeAnalyzingRequest: "Analyzing request",
  planModeTaskCancelled: "Task cancelled",
  planModeFailedToGeneratePlan: "Failed to generate plan",
  planModeExecutingSubtasks: "Executing subtasks",
  planModeCancelling: "Cancelling",
  planModeAllTasksCompleted: "All tasks completed",
  planModeSummarizingResults: "Summarizing results",
  planModeCancelled: "Cancelled",
  planModeExecutionFailed: "Execution failed",

  planGenerateDetailedPlan: "Output the execution plan as raw JSON only. Do not ask questions, do not explain, and do not use Markdown code fences.",
  planGenerationPrompt: `
You are a task planning expert. The user will describe a complex task or problem, and you need to break it down into multiple subtasks that can be executed in parallel or in sequence.
You must output a JSON object that can be parsed successfully by JSON.parse. Do not output any text outside the JSON object. Do not add explanations, apologies, clarifying questions, or Markdown code fences.
If the user's request is short, ambiguous, or underspecified, make the smallest reasonable assumptions needed to continue planning. Never ask the user for more information.
Do not say things like "I need more information" or "please clarify".
Please return the execution plan in the following JSON format:
\n\`\`\`json
{
  "tasks": [
    {
      "id": "task_1",
      "name": "Task description",
      "instruction": "Concrete execution instruction that will be sent to the AI",
      "dependencies": [],
      "type": "chat"
    },
    {
      "id": "task_2",
      "name": "Task description",
      "instruction": "Concrete execution instruction",
      "dependencies": ["task_1"],
      "type": "chat"
    }
  ],
  "finalSummaryInstruction": "Provide the final complete answer based on all subtasks"
}
\`\`\`
\nPlanning principles:
1. Split complex tasks into 3-6 relatively independent subtasks
2. Ensure each subtask has clear execution instructions
3. Set dependencies reasonably, prefer parallel execution
4. Set all task types to "chat"
5. Each instruction should be a complete, independently executable directive
6. The final summary instruction should integrate results from all subtasks
7. If the request is short, continue planning based on the most likely intent instead of asking a follow-up question
8. The output must be valid JSON, and the top level must be a single object
Analyze the user's request and generate an execution plan.`.trim(),
  planGenerationUserRequestPrefix: "User request:\n",

  planErrorGraphValidationFailed: "Execution graph validation failed",
  planErrorTopologicalSortFailed: "Topological sort failed",
  planLogStartingExecution: count => `Starting execution of ${count} tasks`,
  planErrorNoExecutableTasks: "No executable tasks",
  planErrorSummaryFailed: "Summary failed",
  planErrorTaskCancelled: "Task cancelled",

  taskErrorExecutionFailed: error => `Task execution failed: ${error}`,
  taskContextOriginalRequest: request => `Original request: ${request}`,
  taskContextCurrentTask: taskName => `Current task: ${taskName}`,
  taskContextDependencyResults: "Dependency results:",
  taskContextTaskResult: (taskId, result) => `${taskId}: ${result}`,
  taskInstructionWithContext: (context, instruction) => `${context}\n\nTask instruction: ${instruction}`,
  taskSummaryKeyResults: "Key results:",

  finalSummaryInstructionPrefix: "Based on all subtasks above, complete the following summary task:",
  finalSummaryInstructionSuffix: "Please provide a complete and coherent final answer.",

  planTaskDependencyNotExist: (taskId, depId) => `Task ${taskId} dependency does not exist: ${depId}`,
  planCircularDependency: "Circular dependency detected",
  planTaskIdNotUnique: "Task ID is not unique",
  planExecutionGraphValid: "Execution graph is valid"
};
