"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PlanModeManager = void 0;
const plan_parser_1 = require("./plan-parser");
const task_executor_1 = require("./task-executor");
const i18n_1 = require("../i18n");
const prompt_turns_1 = require("../prompt-turns");
const FunctionType = Java.com.ai.assistance.operit.data.model.FunctionType;
const PromptFunctionType = Java.com.ai.assistance.operit.data.model.PromptFunctionType;
const Unit = Java.kotlin.Unit;
const InputProcessingStateBase = "com.ai.assistance.operit.data.model.InputProcessingState$";
const TAG = "PlanModeManager";
const THINK_TAG = /<think(?:ing)?>[\s\S]*?(<\/think(?:ing)?>|\z)/gi;
const SEARCH_TAG = /<search>[\s\S]*?(<\/search>|\z)/gi;
function removeThinkingContent(raw) {
    return raw.replace(THINK_TAG, "").replace(SEARCH_TAG, "").trim();
}
function getI18n() {
    const locale = getLang();
    return (0, i18n_1.resolveDeepSearchI18n)(locale);
}
function clipLogText(value, maxLength = 240) {
    const text = String(value ?? "").replace(/\s+/g, " ").trim();
    if (!text) {
        return "";
    }
    if (text.length <= maxLength) {
        return text;
    }
    return `${text.slice(0, Math.max(0, maxLength - 3)).trimEnd()}...`;
}
function describeBridgeCapabilities(target, methodNames) {
    if (!target || (typeof target !== "object" && typeof target !== "function")) {
        return "target=unavailable";
    }
    const record = target;
    return methodNames
        .map((name) => `${name}=${typeof record[name]}`)
        .join(", ");
}
function toErrorDetail(error) {
    const text = String(error ?? "");
    const stack = typeof error?.stack === "string"
        ? String(error.stack)
        : "";
    return stack ? `${text} stack=${stack}` : text;
}
async function collectStreamToString(stream) {
    let buffer = "";
    let chunkCount = 0;
    console.log(`${TAG} collectStreamToString start ${describeBridgeCapabilities(stream, ["callSuspend", "collect"])}`);
    const collector = {
        emit: function (value) {
            chunkCount += 1;
            buffer += String(value ?? "");
            return Unit.INSTANCE;
        }
    };
    await stream.callSuspend("collect", collector);
    console.log(`${TAG} collectStreamToString done chunkCount=${chunkCount} textLength=${buffer.length}`);
    return buffer;
}
function newInputProcessingState(kind, message) {
    const base = InputProcessingStateBase;
    if (kind === "Idle") {
        const idleCls = Java.type(base + "Idle");
        return idleCls.INSTANCE;
    }
    if (kind === "Completed") {
        const completedCls = Java.type(base + "Completed");
        return completedCls.INSTANCE;
    }
    return Java.newInstance(base + kind, String(message ?? ""));
}
async function sendPlanningMessage(enhancedAIService, chatHistory, maxTokens, tokenUsageThreshold) {
    const onNonFatalError = (_value) => Unit.INSTANCE;
    console.log(`${TAG} sendPlanningMessage start historySize=${chatHistory.length} maxTokens=${maxTokens} tokenUsageThreshold=${tokenUsageThreshold} ${describeBridgeCapabilities(enhancedAIService, ["callSuspend", "sendMessage", "getModelConfigForFunction"])}`);
    const stream = await enhancedAIService.callSuspend("sendMessage", getI18n().planGenerateDetailedPlan, null, (0, prompt_turns_1.toKotlinPromptTurnList)(chatHistory), null, null, FunctionType.CHAT, PromptFunctionType.CHAT, false, false, false, maxTokens, tokenUsageThreshold, onNonFatalError, null, null, true, null, null, null, false, null, "DeepSearch Planner", null, null, null, true);
    return collectStreamToString(stream);
}
class PlanModeManager {
    constructor(context, enhancedAIService) {
        this.isCancelled = false;
        this.context = context;
        this.enhancedAIService = enhancedAIService;
        this.taskExecutor = new task_executor_1.TaskExecutor(context, enhancedAIService);
    }
    cancel() {
        this.isCancelled = true;
        this.taskExecutor.cancelAllTasks();
        try {
            this.enhancedAIService.cancelConversation();
        }
        catch (_e) { }
        console.log(`${TAG} cancel called`);
    }
    shouldUseDeepSearchMode(message) {
        const startTime = Date.now();
        const normalized = String(message || "").trim();
        if (!normalized) {
            console.log(`${TAG} shouldUseDeepSearchMode empty message elapsedMs=${Date.now() - startTime}`);
            return false;
        }
        console.log(`${TAG} shouldUseDeepSearchMode elapsedMs=${Date.now() - startTime} matched=true mode=always_on`);
        return true;
    }
    async executeDeepSearchMode(userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold, onChunk) {
        this.isCancelled = false;
        let output = "";
        const append = (chunk) => {
            output += chunk;
            if (onChunk) {
                try {
                    onChunk(chunk);
                }
                catch (_e) { }
            }
        };
        this.taskExecutor.setChunkEmitter(append);
        try {
            const i18n = getI18n();
            const processingState = newInputProcessingState("Processing", i18n.planModeExecutingDeepSearch);
            this.enhancedAIService
                .setInputProcessingState(processingState);
            const executionGraph = await this.generateExecutionPlan(userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold);
            if (this.isCancelled) {
                append(`<log>🟡 ${i18n.planModeTaskCancelled}</log>\n`);
                return output;
            }
            if (!executionGraph) {
                append(`<error>❌ ${i18n.planModeFailedToGeneratePlan}</error>\n`);
                const idleState = newInputProcessingState("Idle");
                this.enhancedAIService
                    .setInputProcessingState(idleState);
                return output;
            }
            append(`<plan>\n`);
            append(`<graph><![CDATA[${JSON.stringify(executionGraph)}]]></graph>\n`);
            const executingState = newInputProcessingState("Processing", i18n.planModeExecutingSubtasks);
            this.enhancedAIService
                .setInputProcessingState(executingState);
            const executionOutput = await this.taskExecutor.executeSubtasks(executionGraph, userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold);
            console.log(`${TAG} executeDeepSearchMode subtasksOutputLength=${executionOutput.length}`);
            if (this.isCancelled) {
                append(`<log>🟡 ${i18n.planModeCancelling}</log>\n`);
                append(`</plan>\n`);
                return output;
            }
            append(`<log>🎯 ${i18n.planModeAllTasksCompleted}</log>\n`);
            append(`</plan>\n`);
            const summaryState = newInputProcessingState("Processing", i18n.planModeSummarizingResults);
            this.enhancedAIService
                .setInputProcessingState(summaryState);
            const summary = await this.taskExecutor.summarize(executionGraph, userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold);
            console.log(`${TAG} executeDeepSearchMode summaryLength=${summary.length}`);
            const completedState = newInputProcessingState("Completed");
            this.enhancedAIService
                .setInputProcessingState(completedState);
            return output;
        }
        catch (e) {
            if (this.isCancelled) {
                append(`<log>🟡 ${getI18n().planModeCancelled}</log>\n`);
            }
            else {
                append(`<error>❌ ${getI18n().planModeExecutionFailed}: ${String(e)}</error>\n`);
            }
            const idleState = newInputProcessingState("Idle");
            this.enhancedAIService
                .setInputProcessingState(idleState);
            return output;
        }
        finally {
            this.isCancelled = false;
            this.taskExecutor.setChunkEmitter(undefined);
        }
    }
    buildPlanningRequest(userMessage) {
        const i18n = getI18n();
        return `${i18n.planGenerationPrompt}\n\n${i18n.planGenerationUserRequestPrefix}${userMessage}`.trim();
    }
    async generateExecutionPlan(userMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold) {
        let currentStep = "start";
        try {
            console.log(`${TAG} generateExecutionPlan start userMessageLength=${userMessage.length} historySize=${chatHistory.length} workspaceBound=${Boolean(workspacePath)} maxTokens=${maxTokens} tokenUsageThreshold=${tokenUsageThreshold}`);
            currentStep = "build_planning_request";
            const planningRequest = this.buildPlanningRequest(userMessage);
            currentStep = "build_planning_history";
            const planningHistory = [
                (0, prompt_turns_1.createPromptTurn)("SYSTEM", planningRequest),
            ];
            console.log(`${TAG} generateExecutionPlan planningHistoryBuilt turns=${planningHistory.length} requestLength=${planningRequest.length} requestPreview=${clipLogText(planningRequest)}`);
            currentStep = "send_planning_message";
            const planResponseRaw = await sendPlanningMessage(this.enhancedAIService, planningHistory, maxTokens, tokenUsageThreshold);
            console.log(`${TAG} generateExecutionPlan rawResponse length=${planResponseRaw.length} preview=${clipLogText(planResponseRaw)}`);
            currentStep = "sanitize_plan_response";
            const planResponse = removeThinkingContent(String(planResponseRaw ?? "").trim());
            console.log(`${TAG} generateExecutionPlan sanitizedResponse length=${planResponse.length} preview=${clipLogText(planResponse)}`);
            currentStep = "parse_execution_graph";
            const graph = (0, plan_parser_1.parseExecutionGraph)(planResponse);
            console.log(`${TAG} generateExecutionPlan parsedGraph taskCount=${Array.isArray(graph?.tasks) ? graph.tasks.length : 0} hasFinalSummary=${Boolean(graph?.finalSummaryInstruction)}`);
            return graph;
        }
        catch (e) {
            console.log(`${TAG} generate plan error step=${currentStep} detail=${toErrorDetail(e)}`);
            return null;
        }
    }
}
exports.PlanModeManager = PlanModeManager;
