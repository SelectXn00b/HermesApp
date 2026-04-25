"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TaskExecutor = void 0;
const plan_parser_1 = require("./plan-parser");
const i18n_1 = require("../i18n");
const prompt_turns_1 = require("../prompt-turns");
const FunctionType = Java.com.ai.assistance.operit.data.model.FunctionType;
const PromptFunctionType = Java.com.ai.assistance.operit.data.model.PromptFunctionType;
const SystemPromptConfig = Java.com.ai.assistance.operit.core.config.SystemPromptConfig;
const Unit = Java.kotlin.Unit;
const TAG = "TaskExecutor";
const TOOL_TAG = /<tool\b[\s\S]*?<\/tool>/gi;
const TOOL_SELF_CLOSING = /<tool\b[^>]*\/>/gi;
const TOOL_RESULT_TAG = /<tool_result\b[\s\S]*?<\/tool_result>/gi;
const TOOL_RESULT_SELF = /<tool_result\b[^>]*\/>/gi;
const STATUS_TAG = /<status\b[\s\S]*?<\/status>/gi;
const STATUS_SELF = /<status\b[^>]*\/>/gi;
const THINK_TAG = /<think(?:ing)?>[\s\S]*?(<\/think(?:ing)?>|\z)/gi;
const SEARCH_TAG = /<search>[\s\S]*?(<\/search>|\z)/gi;
function removeThinkingContent(raw) {
    return raw.replace(THINK_TAG, "").replace(SEARCH_TAG, "").trim();
}
function stripMarkup(text) {
    return text
        .replace(TOOL_TAG, "")
        .replace(TOOL_SELF_CLOSING, "")
        .replace(TOOL_RESULT_TAG, "")
        .replace(TOOL_RESULT_SELF, "")
        .replace(STATUS_TAG, "")
        .replace(STATUS_SELF, "")
        .trim();
}
function extractFinalNonToolAssistantContent(raw) {
    const noThinking = removeThinkingContent(raw.trim());
    const lastToolLike = /(<tool\s+name="([^"]+)"[\s\S]*?<\/tool>)|(<tool_result([^>]*)>[\s\S]*?<\/tool_result>)/gi;
    let lastMatch = null;
    let match;
    while ((match = lastToolLike.exec(noThinking)) !== null) {
        lastMatch = match;
    }
    const tail = lastMatch ? noThinking.substring((lastMatch.index || 0) + lastMatch[0].length) : noThinking;
    const tailStripped = stripMarkup(tail);
    if (tailStripped)
        return tailStripped;
    const fullStripped = stripMarkup(noThinking);
    if (!fullStripped)
        return "";
    const parts = fullStripped.split(/\n\s*\n+/).map(s => s.trim()).filter(Boolean);
    return parts.length > 0 ? parts[parts.length - 1] : fullStripped;
}
function getI18n() {
    const locale = getLang();
    return (0, i18n_1.resolveDeepSearchI18n)(locale);
}
async function collectStreamToString(stream, onChunk) {
    let buffer = "";
    const collector = {
        emit: function (value) {
            const chunk = String(value ?? "");
            buffer += chunk;
            if (onChunk) {
                try {
                    onChunk(chunk);
                }
                catch (_e) { }
            }
            return Unit.INSTANCE;
        }
    };
    await stream.callSuspend("collect", collector);
    return buffer;
}
async function sendMessage(enhancedAIService, options) {
    const onNonFatalError = (_value) => Unit.INSTANCE;
    const onToolInvocation = options.onToolInvocation
        ? (toolName) => {
            options.onToolInvocation?.(toolName);
            return Unit.INSTANCE;
        }
        : null;
    const stream = await enhancedAIService.callSuspend("sendMessage", options.message, null, (0, prompt_turns_1.toKotlinPromptTurnList)(options.chatHistory), options.workspacePath ?? null, null, FunctionType.CHAT, PromptFunctionType.CHAT, false, false, false, options.maxTokens, options.tokenUsageThreshold, onNonFatalError, null, options.customSystemPromptTemplate ?? null, options.isSubTask, null, null, null, false, null, options.proxySenderName ?? null, onToolInvocation, null, null, true);
    return collectStreamToString(stream, options.onChunk);
}
class TaskExecutor {
    constructor(context, enhancedAIService, onChunk) {
        this.taskResults = {};
        this.isCancelled = false;
        this.context = context;
        this.enhancedAIService = enhancedAIService;
        this.onChunk = onChunk;
    }
    setChunkEmitter(onChunk) {
        this.onChunk = onChunk;
    }
    emitChunk(chunk) {
        if (!chunk || !this.onChunk)
            return;
        try {
            this.onChunk(chunk);
        }
        catch (_e) { }
    }
    cancelAllTasks() {
        this.isCancelled = true;
        this.taskResults = {};
    }
    async executeSubtasks(graph, originalMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold) {
        this.isCancelled = false;
        this.taskResults = {};
        const validation = (0, plan_parser_1.validateExecutionGraph)(graph);
        if (!validation.ok) {
            return `<error>❌ ${getI18n().planErrorGraphValidationFailed}: ${validation.error}</error>\n`;
        }
        const sortedTasks = (0, plan_parser_1.topologicalSort)(graph);
        if (sortedTasks.length === 0) {
            return `<error>❌ ${getI18n().planErrorTopologicalSortFailed}</error>\n`;
        }
        let output = "";
        const startLog = `<log>📋 ${getI18n().planLogStartingExecution(String(sortedTasks.length))}</log>\n`;
        output += startLog;
        this.emitChunk(startLog);
        console.log(`${TAG} executeSubtasks start taskCount=${sortedTasks.length}`);
        const completed = new Set();
        const pending = [...sortedTasks];
        while (pending.length > 0 && !this.isCancelled) {
            const ready = pending.filter(task => (task.dependencies || []).every(dep => completed.has(dep)));
            if (ready.length === 0) {
                output += `<error>❌ ${getI18n().planErrorNoExecutableTasks}</error>\n`;
                break;
            }
            console.log(`${TAG} executeSubtasks readyBatch size=${ready.length} ids=${ready.map(task => task.id).join(",")}`);
            ready.forEach(task => {
                console.log(`${TAG} executeSubtasks taskReady id=${task.id} deps=${(task.dependencies || []).join(",")}`);
            });
            const batchResults = await Promise.all(ready.map(task => this.executeTask(task, originalMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold)));
            output += batchResults.join("");
            if (this.isCancelled)
                break;
            ready.forEach(task => {
                completed.add(task.id);
                const idx = pending.findIndex(t => t.id === task.id);
                if (idx >= 0)
                    pending.splice(idx, 1);
            });
        }
        this.isCancelled = false;
        console.log(`${TAG} executeSubtasks done completed=${completed.size}`);
        return output;
    }
    async summarize(graph, originalMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold) {
        try {
            return await this.executeFinalSummary(graph, originalMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold);
        }
        catch (e) {
            console.log(`${TAG} summary error`, String(e));
            return `${getI18n().planErrorSummaryFailed}: ${String(e)}`;
        }
    }
    async executeTask(task, originalMessage, _chatHistory, workspacePath, maxTokens, tokenUsageThreshold) {
        if (this.isCancelled) {
            return `<update id="${task.id}" status="FAILED" error="${getI18n().planErrorTaskCancelled}"/>\n`;
        }
        const outputParts = [];
        let toolCount = 0;
        const initialUpdate = `<update id="${task.id}" status="IN_PROGRESS" tool_count="0"/>\n`;
        outputParts.push(initialUpdate);
        this.emitChunk(initialUpdate);
        console.log(`${TAG} executeTask start id=${task.id} name=${task.name} workspaceBound=${Boolean(workspacePath)}`);
        const contextInfo = this.buildTaskContext(task, originalMessage);
        const fullInstruction = this.buildFullInstruction(task, contextInfo);
        try {
            const raw = await sendMessage(this.enhancedAIService, {
                message: fullInstruction,
                chatHistory: [],
                workspacePath: workspacePath ?? null,
                maxTokens,
                tokenUsageThreshold,
                customSystemPromptTemplate: String(SystemPromptConfig.SUBTASK_AGENT_PROMPT_TEMPLATE || ""),
                isSubTask: true,
                onToolInvocation: (toolName) => {
                    toolCount += 1;
                    console.log(`${TAG} executeTask toolInvocation id=${task.id} tool=${toolName} count=${toolCount}`);
                    const progressUpdate = `<update id="${task.id}" status="IN_PROGRESS" tool_count="${toolCount}"/>\n`;
                    outputParts.push(progressUpdate);
                    this.emitChunk(progressUpdate);
                }
            });
            const finalText = extractFinalNonToolAssistantContent(raw);
            this.taskResults[task.id] = finalText;
            console.log(`${TAG} executeTask done id=${task.id} toolCount=${toolCount} resultLength=${finalText.length}`);
            const completedUpdate = `<update id="${task.id}" status="COMPLETED" tool_count="${toolCount}"/>\n`;
            outputParts.push(completedUpdate);
            this.emitChunk(completedUpdate);
        }
        catch (e) {
            const errMsg = String(e || "Unknown error").replace(/"/g, "&quot;");
            console.log(`${TAG} executeTask failed id=${task.id} toolCount=${toolCount} error=${String(e)}`);
            const failedUpdate = `<update id="${task.id}" status="FAILED" tool_count="${toolCount}" error="${errMsg}"/>\n`;
            outputParts.push(failedUpdate);
            this.emitChunk(failedUpdate);
            this.taskResults[task.id] = getI18n().taskErrorExecutionFailed(String(e || ""));
        }
        return outputParts.join("");
    }
    buildTaskContext(task, originalMessage) {
        let contextText = "";
        contextText += `${getI18n().taskContextOriginalRequest(originalMessage)}\n`;
        contextText += `${getI18n().taskContextCurrentTask(task.name)}\n`;
        if ((task.dependencies || []).length > 0) {
            contextText += `${getI18n().taskContextDependencyResults}\n`;
            task.dependencies.forEach(depId => {
                const depResult = this.taskResults[depId];
                if (depResult) {
                    contextText += `${getI18n().taskContextTaskResult(depId, depResult)}\n`;
                }
            });
        }
        return contextText;
    }
    buildFullInstruction(task, contextInfo) {
        const toolUseHint = [
            "执行要求：",
            "1. 如果任务涉及查找事实、资料、网页、数据、案例或外部信息，优先使用可用工具获取信息，不要只凭记忆作答。",
            "2. 如果可用工具不足以完成任务，再明确说明限制。",
            "3. 最终输出保持简洁，直接给出对当前子任务有用的结果。"
        ].join("\n");
        return getI18n().taskInstructionWithContext(contextInfo, `${task.instruction}\n\n${toolUseHint}`).trim();
    }
    async executeFinalSummary(graph, originalMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold) {
        const summaryContext = this.buildSummaryContext(originalMessage, graph);
        const i18n = getI18n();
        const fullSummaryInstruction = `${summaryContext}\n\n${i18n.finalSummaryInstructionPrefix}\n${graph.finalSummaryInstruction}\n\n${i18n.finalSummaryInstructionSuffix}`;
        return sendMessage(this.enhancedAIService, {
            message: fullSummaryInstruction,
            chatHistory,
            workspacePath: workspacePath ?? null,
            maxTokens,
            tokenUsageThreshold,
            customSystemPromptTemplate: null,
            isSubTask: false,
            onChunk: (chunk) => this.emitChunk(chunk)
        });
    }
    buildSummaryContext(originalMessage, graph) {
        let contextText = "";
        contextText += `${getI18n().taskContextOriginalRequest(originalMessage)}\n`;
        const allDependencyIds = new Set();
        (graph.tasks || []).forEach(task => (task.dependencies || []).forEach(dep => allDependencyIds.add(dep)));
        const allTaskIds = new Set((graph.tasks || []).map(t => t.id));
        const leafTaskIds = Array.from(allTaskIds).filter(id => !allDependencyIds.has(id));
        contextText += `${getI18n().taskSummaryKeyResults}\n`;
        const taskIdsToSummarize = leafTaskIds.length > 0 ? leafTaskIds : Array.from(allTaskIds);
        taskIdsToSummarize.forEach(taskId => {
            const result = this.taskResults[taskId];
            if (result) {
                const task = (graph.tasks || []).find(t => t.id === taskId);
                const taskName = task ? task.name : taskId;
                contextText += `- ${taskName}: ${result}\n\n`;
            }
        });
        return contextText;
    }
}
exports.TaskExecutor = TaskExecutor;
