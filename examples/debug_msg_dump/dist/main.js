"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.onPromptHistory = onPromptHistory;
exports.onPromptFinalize = onPromptFinalize;
const DUMP_DIR = "/sdcard/Download/Operit/debug_msg_dump/dumps/";
function ensureDumpDir() {
    try {
        Tools.Files.mkdir(DUMP_DIR, true);
    }
    catch (_error) {
    }
}
function buildDump(tag, input) {
    const payload = input.eventPayload || {};
    const stage = String(payload.stage || input.eventName || "unknown");
    const history = payload.preparedHistory || payload.chatHistory || [];
    const systemPrompt = String(payload.systemPrompt || "");
    const toolPrompt = String(payload.toolPrompt || "");
    const lines = [];
    lines.push("========================================");
    lines.push(`  DEBUG MSG DUMP - ${tag}`);
    lines.push(`  Time: ${new Date().toLocaleString()}`);
    lines.push(`  Stage: ${stage}`);
    lines.push(`  Event: ${input.eventName}`);
    lines.push("========================================");
    lines.push("");
    lines.push("╔══════════════════════════════════════╗");
    lines.push("║         SYSTEM PROMPT                ║");
    lines.push("╚══════════════════════════════════════╝");
    lines.push(`Length: ${systemPrompt.length} chars`);
    lines.push("");
    lines.push(systemPrompt);
    lines.push("");
    lines.push("╔══════════════════════════════════════╗");
    lines.push("║         TOOL PROMPT                  ║");
    lines.push("╚══════════════════════════════════════╝");
    lines.push(`Length: ${toolPrompt.length} chars`);
    lines.push("");
    lines.push(toolPrompt);
    lines.push("");
    lines.push("╔══════════════════════════════════════╗");
    lines.push(`║    MESSAGES (Total: ${history.length})`);
    lines.push("╚══════════════════════════════════════╝");
    lines.push("");
    history.forEach((message, index) => {
        const kind = message.kind || "N/A";
        const content = String(message.content || "");
        lines.push(`┌─── Message #${index + 1} ───┐`);
        lines.push(`│ Kind: ${kind}`);
        if (message.toolName) {
            lines.push(`│ ToolName: ${message.toolName}`);
        }
        if (message.metadata) {
            try {
                lines.push(`│ Metadata: ${JSON.stringify(message.metadata)}`);
            }
            catch (_error) {
                lines.push("│ Metadata: [stringify error]");
            }
        }
        lines.push(`│ Content Length: ${content.length} chars`);
        lines.push("└──────────────────┘");
        lines.push(content);
        lines.push("");
    });
    lines.push("========== END OF DUMP ==========");
    return lines.join("\n");
}
function buildTimestamp() {
    return new Date().toISOString().replace(/[:.]/g, "-");
}
function registerToolPkg() {
    ToolPkg.registerPromptHistoryHook({
        id: "debug_dump_history",
        function: onPromptHistory,
    });
    ToolPkg.registerPromptFinalizeHook({
        id: "debug_dump_finalize",
        function: onPromptFinalize,
    });
    ensureDumpDir();
    return true;
}
function onPromptHistory(input) {
    const stage = String(input.eventPayload?.stage || input.eventName || "");
    if (stage !== "after_prepare_history") {
        return null;
    }
    try {
        const text = buildDump("HISTORY (after B pack processing)", input);
        const path = `${DUMP_DIR}history_${buildTimestamp()}.txt`;
        Tools.Files.write(path, text);
        console.log(`[msg_dump] history saved to ${path}`);
    }
    catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        console.log(`[msg_dump] history write error: ${message}`);
    }
    return null;
}
function onPromptFinalize(input) {
    try {
        const text = buildDump("FINALIZE (final to model)", input);
        const path = `${DUMP_DIR}finalize_${buildTimestamp()}.txt`;
        Tools.Files.write(path, text);
        console.log(`[msg_dump] finalize saved to ${path}`);
    }
    catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        console.log(`[msg_dump] finalize write error: ${message}`);
    }
    return null;
}
