"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DEEPSEARCH_ZH_CN = void 0;
exports.DEEPSEARCH_ZH_CN = {
    menuTitle: "深度搜索",
    menuDescription: "启用计划模式进行更深入的分析与分解",
    executionPlanTitle: "深度搜索",
    executionPlanPreparing: "正在准备执行计划...",
    executionPlanParseFailed: "执行计划解析失败，请检查计划数据格式",
    executionPlanError: error => `错误: ${error}`,
    executionPlanTarget: instruction => `目标: ${instruction}`,
    executionPlanTools: count => `工具: ${count}`,
    planModeExecutingDeepSearch: "正在执行深度搜索",
    planModeStarting: "开始深度搜索",
    planModeAnalyzingRequest: "分析用户请求",
    planModeTaskCancelled: "任务已取消",
    planModeFailedToGeneratePlan: "生成计划失败",
    planModeExecutingSubtasks: "执行子任务",
    planModeCancelling: "正在取消",
    planModeAllTasksCompleted: "所有任务已完成",
    planModeSummarizingResults: "正在汇总结果",
    planModeCancelled: "已取消",
    planModeExecutionFailed: "执行失败",
    planGenerateDetailedPlan: "请直接输出执行计划 JSON，不要提问，不要解释，不要使用 Markdown 代码块。",
    planGenerationPrompt: `
你是一个任务规划专家。用户将向你描述一个复杂的任务或问题，你需要将其分解为多个可以并发或顺序执行的子任务。
你必须直接输出一个可被 JSON.parse 成功解析的 JSON 对象，不允许输出任何 JSON 之外的说明、前后缀、道歉、澄清问题或 Markdown 代码块。
如果用户请求过于简短、模糊或信息不足，你必须自行做出最小且合理的默认假设来完成规划，绝对不要向用户追问。
禁止回答“需要更多信息”“请告诉我更多信息”“我需要了解更多信息”之类的话。
请按照以下 JSON 格式返回执行计划：
\n\`\`\`json
{
  "tasks": [
    {
      "id": "task_1",
      "name": "任务描述",
      "instruction": "具体的执行指令，这将被发送给 AI 执行",
      "dependencies": [],
      "type": "chat"
    },
    {
      "id": "task_2",
      "name": "任务描述",
      "instruction": "具体的执行指令",
      "dependencies": ["task_1"],
      "type": "chat"
    }
  ],
  "finalSummaryInstruction": "根据所有子任务的结果，提供最终的完整回答"
}
\`\`\`
\n规划原则：
1. 将复杂任务分解为 3-6 个相对独立的子任务
2. 确保每个子任务都有明确的执行指令
3. 合理设置任务间的依赖关系，优先支持并发执行
4. 所有任务类型都设为 "chat"
5. 每个 instruction 应该是一个完整的、可以独立执行的指令
6. 最终汇总指令应能整合所有子任务的结果
7. 如果用户请求较短，也要基于最可能的意图继续规划，而不是提问
8. 输出必须是合法 JSON，且顶层只能是一个对象
请分析用户的请求并生成相应的执行计划。`.trim(),
    planGenerationUserRequestPrefix: "用户请求:\n",
    planErrorGraphValidationFailed: "执行图校验失败",
    planErrorTopologicalSortFailed: "拓扑排序失败",
    planLogStartingExecution: count => `开始执行 ${count} 个任务`,
    planErrorNoExecutableTasks: "没有可执行的任务",
    planErrorSummaryFailed: "总结失败",
    planErrorTaskCancelled: "任务已取消",
    taskErrorExecutionFailed: error => `任务执行失败: ${error}`,
    taskContextOriginalRequest: request => `原始请求: ${request}`,
    taskContextCurrentTask: taskName => `当前任务: ${taskName}`,
    taskContextDependencyResults: "依赖任务结果:",
    taskContextTaskResult: (taskId, result) => `${taskId}: ${result}`,
    taskInstructionWithContext: (context, instruction) => `${context}\n\n任务指令: ${instruction}`,
    taskSummaryKeyResults: "关键结果:",
    finalSummaryInstructionPrefix: "请根据以上所有子任务的执行结果，完成以下汇总任务:",
    finalSummaryInstructionSuffix: "请提供一个完整、连贯的最终回答。",
    planTaskDependencyNotExist: (taskId, depId) => `任务 ${taskId} 依赖不存在: ${depId}`,
    planCircularDependency: "检测到任务依赖循环",
    planTaskIdNotUnique: "任务 ID 不唯一",
    planExecutionGraphValid: "执行图校验通过"
};
