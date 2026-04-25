"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createPromptTurn = createPromptTurn;
exports.normalizePromptTurnList = normalizePromptTurnList;
exports.toKotlinPromptTurnList = toKotlinPromptTurnList;
const PromptTurnKindClass = Java.type("com.ai.assistance.operit.core.chat.hooks.PromptTurnKind");
function createPromptTurn(kind, content, toolName, metadata) {
    const turn = {
        kind,
        content: String(content ?? ""),
    };
    if (typeof toolName === "string" && toolName.length > 0) {
        turn.toolName = toolName;
    }
    if (metadata && typeof metadata === "object" && !Array.isArray(metadata)) {
        turn.metadata = metadata;
    }
    return turn;
}
function normalizePromptTurnList(value) {
    if (!Array.isArray(value)) {
        return [];
    }
    const turns = [];
    for (const item of value) {
        if (!item || typeof item !== "object") {
            continue;
        }
        const record = item;
        const kind = normalizePromptTurnKind(record.kind);
        if (!kind) {
            continue;
        }
        turns.push(createPromptTurn(kind, String(record.content ?? ""), typeof record.toolName === "string" ? record.toolName : undefined, isJsonObject(record.metadata) ? record.metadata : undefined));
    }
    return turns;
}
function toKotlinPromptTurnList(history) {
    return (history || []).map((turn) => Java.newInstance("com.ai.assistance.operit.core.chat.hooks.PromptTurn", resolvePromptTurnKind(turn.kind), String(turn.content ?? ""), typeof turn.toolName === "string" ? turn.toolName : null, toJavaJsonObject(turn.metadata)));
}
function normalizePromptTurnKind(kind) {
    const normalized = String(kind ?? "").trim().toUpperCase();
    switch (normalized) {
        case "SYSTEM":
        case "USER":
        case "ASSISTANT":
        case "TOOL_CALL":
        case "TOOL_RESULT":
        case "SUMMARY":
            return normalized;
        default:
            return null;
    }
}
function resolvePromptTurnKind(kind) {
    switch (kind) {
        case "SYSTEM":
            return PromptTurnKindClass.SYSTEM;
        case "ASSISTANT":
            return PromptTurnKindClass.ASSISTANT;
        case "TOOL_CALL":
            return PromptTurnKindClass.TOOL_CALL;
        case "TOOL_RESULT":
            return PromptTurnKindClass.TOOL_RESULT;
        case "SUMMARY":
            return PromptTurnKindClass.SUMMARY;
        case "USER":
        default:
            return PromptTurnKindClass.USER;
    }
}
function isJsonObject(value) {
    return !!value && typeof value === "object" && !Array.isArray(value);
}
function toJavaJsonObject(value) {
    if (!value) {
        return {};
    }
    const map = {};
    for (const [key, item] of Object.entries(value)) {
        map[String(key)] = toJavaValue(item);
    }
    return map;
}
function toJavaValue(value) {
    if (value === undefined || value === null) {
        return null;
    }
    if (Array.isArray(value)) {
        return value.map((item) => toJavaValue(item));
    }
    if (typeof value === "object") {
        return toJavaJsonObject(value);
    }
    return value;
}
