export interface DeepSearchI18n {
  menuTitle: string;
  menuDescription: string;

  executionPlanTitle: string;
  executionPlanPreparing: string;
  executionPlanParseFailed: string;
  executionPlanError: (error: string) => string;
  executionPlanTarget: (instruction: string) => string;
  executionPlanTools: (count: number) => string;

  planModeExecutingDeepSearch: string;
  planModeStarting: string;
  planModeAnalyzingRequest: string;
  planModeTaskCancelled: string;
  planModeFailedToGeneratePlan: string;
  planModeExecutingSubtasks: string;
  planModeCancelling: string;
  planModeAllTasksCompleted: string;
  planModeSummarizingResults: string;
  planModeCancelled: string;
  planModeExecutionFailed: string;

  planGenerateDetailedPlan: string;
  planGenerationPrompt: string;
  planGenerationUserRequestPrefix: string;

  planErrorGraphValidationFailed: string;
  planErrorTopologicalSortFailed: string;
  planLogStartingExecution: (count: string) => string;
  planErrorNoExecutableTasks: string;
  planErrorSummaryFailed: string;
  planErrorTaskCancelled: string;

  taskErrorExecutionFailed: (error: string) => string;
  taskContextOriginalRequest: (request: string) => string;
  taskContextCurrentTask: (taskName: string) => string;
  taskContextDependencyResults: string;
  taskContextTaskResult: (taskId: string, result: string) => string;
  taskInstructionWithContext: (context: string, instruction: string) => string;
  taskSummaryKeyResults: string;

  finalSummaryInstructionPrefix: string;
  finalSummaryInstructionSuffix: string;

  planTaskDependencyNotExist: (taskId: string, depId: string) => string;
  planCircularDependency: string;
  planTaskIdNotUnique: string;
  planExecutionGraphValid: string;
}
